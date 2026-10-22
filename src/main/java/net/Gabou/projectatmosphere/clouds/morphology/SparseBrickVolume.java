package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * T260 experimental sparse-brick distance volume (never production). GPU mirror: cloud_convective_system_t260.glsl.
 *
 * <p>A global sample lattice (spacing {@code voxel}) is split into grid cells of {@code B}^3 lattice cells. Each grid cell
 * is classified EXACTLY from its (B+1)^3 quantised samples (its own plus the one-sample apron shared with the next cell):
 * EMPTY when every sample is >= feather/2 (trilinear density is then exactly 0), FULL when every sample is <= -feather/2
 * (density exactly 1), otherwise it owns a brick. Neighbouring bricks compute the shared apron from the same global
 * lattice, so sampling is seamless across brick faces. A coarse Lipschitz test at the cell centre skips the sample
 * evaluation of cells that are certainly empty or full; {@link #auditCoarseSkips} re-evaluates them to prove it.
 *
 * <p>Texture (RGBA32F, {@value #TEX_W} wide, linear texel addressing): header, index (4 cells per texel: 0 empty,
 * 1 full, 2+b brick b), brick pool. A brick texel holds 8 samples as 12-bit codes, two per channel (hi*4096+lo, exact
 * in fp32): x samples 3r..3r+3 at k (R,G) and k+1 (B,A) for lattice row j. A trilinear lookup is therefore one index
 * fetch plus two brick fetches (rows j and j+1), the same as T258's grid + x-pair layout.
 */
public final class SparseBrickVolume {
    public static final int VERSION = 1, B = 8, S = B + 1, RUNS = 3, TEXELS_PER_BRICK = RUNS * S * B, TEX_W = 2048;
    public static final int HEADER_TEXELS = 12, CODE_MAX = 4095;
    public static final int EMPTY = 0, FULL = 1;

    /** Distance field. Must be thread-safe. {@link #local} may return a cheaper evaluator valid inside a box. */
    public interface Field {
        float distance(float x, float y, float z);
        default Field local(Bounds box) { return this; }
    }

    public record Stats(int gridCells, int emptyCells, int fullCells, int bricks, int coarseSkipped, int evaluatedCells,
                        long sampleEvaluations, double bakeMs, long textureBytes, long cpuBytes) {}
    public record UpdateStats(int cellsVisited, int bricksBefore, int bricksAfter, int bricksWritten, int bricksFreed,
                              int indexTexelsDirty, long uploadTexels, double cpuMs, boolean reallocated) {}

    private final float minX, minY, minZ, voxel, feather, band, scale, offset, lipschitz;
    private final int gx, gy, gz;
    private final int[] state;            // per grid cell: EMPTY, FULL or 2 + brick
    private short[][] bricks;              // brick -> (S^3) codes, index ((k*S + j)*S + i)
    private int brickCount;                // high-water mark of allocated slots
    private final ArrayDeque<Integer> free = new ArrayDeque<>();
    private int capacity;
    private final int indexStart, brickStart;
    private float[] texture;
    private int texRows;
    private final float[][] boxes = new float[3][];
    private int boxCount;
    private long signature;
    private Stats stats;
    private final List<int[]> dirty = new ArrayList<>(); // texel ranges [start, end) since the last drain
    private boolean fullUpload = true;

    /**
     * @param domain   region that must contain every surface (the lattice covers it, rounded up to whole cells)
     * @param headroom extra brick slots (fraction) kept free for incremental growth without reallocation
     */
    public SparseBrickVolume(Field field, Bounds domain, float voxel, float feather, float lipschitz, float headroom) {
        long t0 = System.nanoTime();
        this.voxel = voxel;
        this.feather = feather;
        this.band = feather * 2.5f;
        this.scale = 2 * band / CODE_MAX;
        this.offset = -band;
        this.lipschitz = lipschitz;
        float cell = B * voxel;
        gx = Math.max(1, (int) Math.ceil((domain.maxX() - domain.minX()) / cell));
        gy = Math.max(1, (int) Math.ceil((domain.maxY() - domain.minY()) / cell));
        gz = Math.max(1, (int) Math.ceil((domain.maxZ() - domain.minZ()) / cell));
        minX = domain.minX() - ((gx * cell) - (domain.maxX() - domain.minX())) * .5f;
        minY = domain.minY() - ((gy * cell) - (domain.maxY() - domain.minY())) * .5f;
        minZ = domain.minZ() - ((gz * cell) - (domain.maxZ() - domain.minZ())) * .5f;
        state = new int[gx * gy * gz];
        short[][] codes = new short[state.length][];
        int[] coarse = new int[state.length];
        long[] evals = new long[state.length];
        IntStream.range(0, state.length).parallel().forEach(c -> {
            Result r = classify(field, c);
            state[c] = r.state;
            codes[c] = r.codes;
            coarse[c] = r.coarse ? 1 : 0;
            evals[c] = r.evaluations;
        });
        int count = 0, empty = 0, full = 0, skipped = 0, evaluated = 0;
        long sampleEvaluations = 0;
        for (int c = 0; c < state.length; c++) {
            if (codes[c] != null) count++;
            else if (state[c] == EMPTY) empty++;
            else full++;
            skipped += coarse[c];
            sampleEvaluations += evals[c];
            if (coarse[c] == 0) evaluated++;
        }
        capacity = Math.max(1, (int) Math.ceil(count * (1 + headroom)));
        bricks = new short[capacity][];
        for (int c = 0; c < state.length; c++) {
            if (codes[c] == null) continue;
            bricks[brickCount] = codes[c];
            state[c] = 2 + brickCount++;
        }
        indexStart = HEADER_TEXELS;
        brickStart = indexStart + (state.length + 3) / 4;
        computeBoxes();
        buildTexture();
        double ms = (System.nanoTime() - t0) / 1e6;
        stats = new Stats(state.length, empty, full, count, skipped, evaluated, sampleEvaluations, ms,
                (long) TEX_W * texRows * 16, cpuBytes());
    }

    private record Result(int state, short[] codes, boolean coarse, long evaluations) {}

    private Result classify(Field field, int c) {
        int bi = c % gx, bj = (c / gx) % gy, bk = c / (gx * gy);
        float cell = B * voxel;
        float x0 = minX + bi * cell, y0 = minY + bj * cell, z0 = minZ + bk * cell;
        float half = cell * .5f, radius = half * (float) Math.sqrt(3);
        float cut = feather * .5f;
        Field f = field.local(new Bounds(x0 - band, y0 - band, z0 - band, x0 + cell + band, y0 + cell + band, z0 + cell + band));
        float centre = f.distance(x0 + half, y0 + half, z0 + half);
        if (centre >= lipschitz * radius + cut + scale) return new Result(EMPTY, null, true, 1);
        if (centre <= -(lipschitz * radius + cut + scale)) return new Result(FULL, null, true, 1);
        short[] codes = new short[S * S * S];
        boolean allEmpty = true, allFull = true;
        for (int k = 0; k < S; k++) for (int j = 0; j < S; j++) for (int i = 0; i < S; i++) {
            float d = f.distance(x0 + i * voxel, y0 + j * voxel, z0 + k * voxel);
            int q = quantise(d);
            codes[(k * S + j) * S + i] = (short) q;
            float v = q * scale + offset;
            allEmpty &= v >= cut;
            allFull &= v <= -cut;
        }
        long evaluations = 1 + S * S * S;
        if (allEmpty) return new Result(EMPTY, null, false, evaluations);
        if (allFull) return new Result(FULL, null, false, evaluations);
        return new Result(-1, codes, false, evaluations);
    }

    private int quantise(float d) {
        float clamped = Math.max(-band, Math.min(band, d));
        return Math.max(0, Math.min(CODE_MAX, Math.round((clamped - offset) / scale)));
    }

    /**
     * Re-evaluates every coarse-skipped cell sample by sample and returns the number whose exact classification
     * differs (must be 0: the skip is conservative). Expensive; sandbox only.
     */
    public int auditCoarseSkips(Field field) {
        float cell = B * voxel, cut = feather * .5f;
        return IntStream.range(0, state.length).parallel().map(c -> {
            int bi = c % gx, bj = (c / gx) % gy, bk = c / (gx * gy);
            float x0 = minX + bi * cell, y0 = minY + bj * cell, z0 = minZ + bk * cell, half = cell * .5f;
            float centre = field.distance(x0 + half, y0 + half, z0 + half);
            float radius = half * (float) Math.sqrt(3);
            boolean skipped = Math.abs(centre) >= lipschitz * radius + cut + scale;
            if (!skipped) return 0;
            for (int k = 0; k < S; k++) for (int j = 0; j < S; j++) for (int i = 0; i < S; i++) {
                float v = quantise(field.distance(x0 + i * voxel, y0 + j * voxel, z0 + k * voxel)) * scale + offset;
                if (state[c] == EMPTY ? v < cut : v > -cut) return 1;
            }
            return 0;
        }).sum();
    }

    // ---------------------------------------------------------------- incremental update

    /**
     * Re-classifies every grid cell touching {@code region} (a box that contains every changed surface, before and
     * after) against the new field. Bricks are freed/allocated from the pool; only changed texels are marked dirty.
     */
    public UpdateStats update(Field field, Bounds region) {
        long t0 = System.nanoTime();
        int before = liveBricks();
        float cell = B * voxel;
        int i0 = clampI((int) Math.floor((region.minX() - minX) / cell) - 1, 0, gx - 1), i1 = clampI((int) Math.floor((region.maxX() - minX) / cell) + 1, 0, gx - 1);
        int j0 = clampI((int) Math.floor((region.minY() - minY) / cell) - 1, 0, gy - 1), j1 = clampI((int) Math.floor((region.maxY() - minY) / cell) + 1, 0, gy - 1);
        int k0 = clampI((int) Math.floor((region.minZ() - minZ) / cell) - 1, 0, gz - 1), k1 = clampI((int) Math.floor((region.maxZ() - minZ) / cell) + 1, 0, gz - 1);
        int ni = i1 - i0 + 1, nj = j1 - j0 + 1, nk = k1 - k0 + 1;
        Result[] results = new Result[ni * nj * nk];
        IntStream.range(0, results.length).parallel().forEach(n -> {
            int i = i0 + n % ni, j = j0 + (n / ni) % nj, k = k0 + n / (ni * nj);
            results[n] = classify(field, (k * gy + j) * gx + i);
        });
        int written = 0, freed = 0;
        boolean reallocated = false;
        java.util.TreeSet<Integer> indexTexels = new java.util.TreeSet<>();
        List<Integer> brickSlots = new ArrayList<>();
        for (int n = 0; n < results.length; n++) {
            int i = i0 + n % ni, j = j0 + (n / ni) % nj, k = k0 + n / (ni * nj);
            int c = (k * gy + j) * gx + i;
            Result r = results[n];
            int old = state[c];
            if (r.codes == null) {
                if (old >= 2) { bricks[old - 2] = null; free.add(old - 2); freed++; }
                if (old != r.state) { state[c] = r.state; indexTexels.add(indexStart + c / 4); }
                continue;
            }
            int slot;
            if (old >= 2) {
                slot = old - 2;
                if (Arrays.equals(bricks[slot], r.codes)) continue;
            } else {
                if (free.isEmpty()) {
                    if (brickCount >= capacity) { growCapacity(); reallocated = true; }
                    slot = brickCount++;
                } else slot = free.poll();
                state[c] = 2 + slot;
                indexTexels.add(indexStart + c / 4);
            }
            bricks[slot] = r.codes;
            brickSlots.add(slot);
            written++;
        }
        computeBoxes();
        long upload;
        if (reallocated) {
            buildTexture();
            upload = (long) TEX_W * texRows;
        } else {
            writeHeader();
            dirty.add(new int[]{0, HEADER_TEXELS});
            for (int t : indexTexels) { writeIndexTexel(t); dirty.add(new int[]{t, t + 1}); }
            for (int slot : brickSlots) {
                writeBrick(slot);
                int s0 = brickStart + slot * TEXELS_PER_BRICK;
                dirty.add(new int[]{s0, s0 + TEXELS_PER_BRICK});
            }
            upload = HEADER_TEXELS + indexTexels.size() + (long) brickSlots.size() * TEXELS_PER_BRICK;
        }
        recomputeSignature();
        double ms = (System.nanoTime() - t0) / 1e6;
        return new UpdateStats(results.length, before, liveBricks(), written, freed, indexTexels.size(), upload, ms, reallocated);
    }

    private void growCapacity() {
        capacity = Math.max(capacity + 64, (int) Math.ceil(capacity * 1.25));
        bricks = Arrays.copyOf(bricks, capacity);
    }

    /** Dirty texel ranges since the last call, merged; null when the whole texture must be (re)uploaded. */
    public List<int[]> drainDirty() {
        if (fullUpload) { fullUpload = false; dirty.clear(); return null; }
        dirty.sort((a, b) -> Integer.compare(a[0], b[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] r : dirty) {
            if (!merged.isEmpty() && r[0] <= merged.get(merged.size() - 1)[1]) merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], r[1]);
            else merged.add(r.clone());
        }
        dirty.clear();
        return merged;
    }

    // ---------------------------------------------------------------- texture

    private void buildTexture() {
        texRows = (brickStart + capacity * TEXELS_PER_BRICK + TEX_W - 1) / TEX_W;
        texture = new float[TEX_W * texRows * 4];
        writeHeader();
        for (int t = indexStart; t < brickStart; t++) writeIndexTexel(t);
        for (int b = 0; b < brickCount; b++) if (bricks[b] != null) writeBrick(b);
        fullUpload = true;
        dirty.clear();
        recomputeSignature();
    }

    private void writeHeader() {
        put(0, minX, minY, minZ, voxel);
        put(1, gx, gy, gz, B);
        put(2, feather, band, boxCount, VERSION);
        put(3, indexStart, brickStart, TEX_W, TEXELS_PER_BRICK);
        for (int b = 0; b < 3; b++) {
            float[] box = b < boxCount ? boxes[b] : new float[]{0, 0, 0, -1, -1, -1};
            put(4 + b * 2, box[0], box[1], box[2], 0);
            put(5 + b * 2, box[3], box[4], box[5], 0);
        }
        put(10, scale, offset, 0, 0);
        put(11, 0, 0, 0, 0);
    }

    private void writeIndexTexel(int t) {
        for (int ch = 0; ch < 4; ch++) {
            int c = (t - indexStart) * 4 + ch;
            texture[t * 4 + ch] = c < state.length ? state[c] : 0;
        }
    }

    private void writeBrick(int slot) {
        short[] s = bricks[slot];
        int base = brickStart + slot * TEXELS_PER_BRICK;
        for (int k = 0; k < B; k++) for (int j = 0; j < S; j++) for (int r = 0; r < RUNS; r++) {
            int t = (base + (k * S + j) * RUNS + r) * 4;
            int x = 3 * r;
            texture[t] = pack(code(s, x, j, k), code(s, x + 1, j, k));
            texture[t + 1] = pack(code(s, x + 2, j, k), code(s, x + 3, j, k));
            texture[t + 2] = pack(code(s, x, j, k + 1), code(s, x + 1, j, k + 1));
            texture[t + 3] = pack(code(s, x + 2, j, k + 1), code(s, x + 3, j, k + 1));
        }
    }

    private static int code(short[] s, int i, int j, int k) { return s[(k * S + j) * S + Math.min(i, B)]; }
    private static float pack(int hi, int lo) { return hi * 4096f + lo; }

    private void put(int texel, float a, float b, float c, float d) {
        texture[texel * 4] = a; texture[texel * 4 + 1] = b; texture[texel * 4 + 2] = c; texture[texel * 4 + 3] = d;
    }

    private void recomputeSignature() {
        long h = 0xcbf29ce484222325L;
        for (float v : texture) h = (h ^ Float.floatToIntBits(v)) * 0x100000001b3L;
        signature = h;
    }

    /** Up to three cell-aligned boxes covering every non-empty cell (greedy minimum-volume splits). */
    private void computeBoxes() {
        int[] all = occupiedBox(0, 0, 0, gx - 1, gy - 1, gz - 1);
        boxCount = 0;
        if (all == null) return;
        List<int[]> list = new ArrayList<>();
        list.add(all);
        while (list.size() < 3) {
            int best = -1;
            int[][] bestPair = null;
            long bestGain = 0;
            for (int n = 0; n < list.size(); n++) {
                int[][] pair = bestSplit(list.get(n));
                if (pair == null) continue;
                long gain = volume(list.get(n)) - volume(pair[0]) - volume(pair[1]);
                if (gain > bestGain) { bestGain = gain; best = n; bestPair = pair; }
            }
            if (best < 0 || bestGain < volume(list.get(best)) / 10) break;
            list.remove(best);
            list.add(bestPair[0]);
            list.add(bestPair[1]);
        }
        float cell = B * voxel;
        for (int[] b : list) boxes[boxCount++] = new float[]{minX + b[0] * cell, minY + b[1] * cell, minZ + b[2] * cell,
                minX + (b[3] + 1) * cell, minY + (b[4] + 1) * cell, minZ + (b[5] + 1) * cell};
    }

    private int[][] bestSplit(int[] box) {
        int[][] best = null;
        long bestVolume = Long.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) for (int p = box[axis]; p < box[axis + 3]; p++) {
            int[] lo = box.clone(), hi = box.clone();
            lo[axis + 3] = p;
            hi[axis] = p + 1;
            int[] a = occupiedBox(lo[0], lo[1], lo[2], lo[3], lo[4], lo[5]), b = occupiedBox(hi[0], hi[1], hi[2], hi[3], hi[4], hi[5]);
            if (a == null || b == null) continue;
            long v = volume(a) + volume(b);
            if (v < bestVolume) { bestVolume = v; best = new int[][]{a, b}; }
        }
        return best;
    }

    private int[] occupiedBox(int i0, int j0, int k0, int i1, int j1, int k1) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1, -1};
        for (int k = k0; k <= k1; k++) for (int j = j0; j <= j1; j++) for (int i = i0; i <= i1; i++) {
            if (state[(k * gy + j) * gx + i] == EMPTY) continue;
            b[0] = Math.min(b[0], i); b[1] = Math.min(b[1], j); b[2] = Math.min(b[2], k);
            b[3] = Math.max(b[3], i); b[4] = Math.max(b[4], j); b[5] = Math.max(b[5], k);
        }
        return b[3] < 0 ? null : b;
    }

    private static long volume(int[] b) { return (long) (b[3] - b[0] + 1) * (b[4] - b[1] + 1) * (b[5] - b[2] + 1); }

    // ---------------------------------------------------------------- GPU mirror

    /** Exact mirror of paCsDensity (same lookups, same interpolation order). */
    public float density(float x, float y, float z) {
        float gxf = (x - minX) / voxel, gyf = (y - minY) / voxel, gzf = (z - minZ) / voxel;
        int ci = clampI((int) Math.floor(gxf), 0, gx * B - 1), cj = clampI((int) Math.floor(gyf), 0, gy * B - 1), ck = clampI((int) Math.floor(gzf), 0, gz * B - 1);
        int bi = ci / B, bj = cj / B, bk = ck / B;
        int s = state[(bk * gy + bj) * gx + bi];
        if (s == EMPTY) return 0;
        if (s == FULL) return 1;
        short[] c = bricks[s - 2];
        int li = ci - bi * B, lj = cj - bj * B, lk = ck - bk * B;
        float fx = clamp(gxf - ci, 0, 1), fy = clamp(gyf - cj, 0, 1), fz = clamp(gzf - ck, 0, 1);
        float a0 = c[(lk * S + lj) * S + li], a1 = c[(lk * S + lj) * S + li + 1];
        float a2 = c[((lk + 1) * S + lj) * S + li], a3 = c[((lk + 1) * S + lj) * S + li + 1];
        float b0 = c[(lk * S + lj + 1) * S + li], b1 = c[(lk * S + lj + 1) * S + li + 1];
        float b2 = c[((lk + 1) * S + lj + 1) * S + li], b3 = c[((lk + 1) * S + lj + 1) * S + li + 1];
        float m0 = mix(a0, b0, fy), m1 = mix(a1, b1, fy), m2 = mix(a2, b2, fy), m3 = mix(a3, b3, fy);
        float code = mix(mix(m0, m1, fx), mix(m2, m3, fx), fz);
        float d = code * scale + offset;
        return clamp(.5f - d / feather, 0, 1);
    }

    /** Same lookup as the GPU but decoded from the packed texture floats (proves the packing). */
    public float densityFromTexture(float x, float y, float z) {
        float gxf = (x - minX) / voxel, gyf = (y - minY) / voxel, gzf = (z - minZ) / voxel;
        int ci = clampI((int) Math.floor(gxf), 0, gx * B - 1), cj = clampI((int) Math.floor(gyf), 0, gy * B - 1), ck = clampI((int) Math.floor(gzf), 0, gz * B - 1);
        int bi = ci / B, bj = cj / B, bk = ck / B;
        int cell = (bk * gy + bj) * gx + bi;
        float sv = texture[(indexStart + cell / 4) * 4 + (cell & 3)];
        if (sv < .5f) return 0;
        if (sv < 1.5f) return 1;
        int brick = (int) (sv + .5f) - 2;
        int li = ci - bi * B, lj = cj - bj * B, lk = ck - bk * B;
        float fx = clamp(gxf - ci, 0, 1), fy = clamp(gyf - cj, 0, 1), fz = clamp(gzf - ck, 0, 1);
        int r = Math.min(li / 3, 2), xi = li - 3 * r;
        int t = brickStart + brick * TEXELS_PER_BRICK + (lk * S + lj) * RUNS + r;
        float[] ak = new float[4], ak1 = new float[4], bk0 = new float[4], bk1 = new float[4];
        decode(t, ak, ak1);
        decode(t + RUNS, bk0, bk1);
        float mk0 = mix(ak[xi], bk0[xi], fy), mk1 = mix(ak[xi + 1], bk0[xi + 1], fy);
        float mz0 = mix(ak1[xi], bk1[xi], fy), mz1 = mix(ak1[xi + 1], bk1[xi + 1], fy);
        float code = mix(mix(mk0, mk1, fx), mix(mz0, mz1, fx), fz);
        float d = code * scale + offset;
        return clamp(.5f - d / feather, 0, 1);
    }

    private void decode(int t, float[] rowK, float[] rowK1) {
        for (int ch = 0; ch < 4; ch++) {
            float v = texture[t * 4 + ch];
            float hi = (float) Math.floor(v / 4096f), lo = v - hi * 4096f;
            float[] row = ch < 2 ? rowK : rowK1;
            row[(ch & 1) * 2] = hi;
            row[(ch & 1) * 2 + 1] = lo;
        }
    }

    // ---------------------------------------------------------------- accessors

    public Bounds bounds() { float cell = B * voxel; return new Bounds(minX, minY, minZ, minX + gx * cell, minY + gy * cell, minZ + gz * cell); }
    public float[] texture() { return texture; }
    public int textureWidth() { return TEX_W; }
    public int textureHeight() { return texRows; }
    public long signature() { return signature; }
    public Stats stats() { return stats; }
    public float voxel() { return voxel; }
    public float feather() { return feather; }
    public int[] grid() { return new int[]{gx, gy, gz}; }
    public int boxCount() { return boxCount; }
    public float[] box(int i) { return boxes[i].clone(); }
    public int capacity() { return capacity; }
    public int liveBricks() { int n = 0; for (int b = 0; b < brickCount; b++) if (bricks[b] != null) n++; return n; }
    public int cellState(int i, int j, int k) { int s = state[(k * gy + j) * gx + i]; return s >= 2 ? 2 : s; }
    public long cpuBytes() { return (long) state.length * 4 + (long) liveBricks() * (S * S * S * 2 + 16) + (long) TEX_W * texRows * 16; }
    public int[] counts() {
        int e = 0, f = 0, b = 0;
        for (int s : state) { if (s == EMPTY) e++; else if (s == FULL) f++; else b++; }
        return new int[]{e, f, b};
    }

    private static int clampI(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }
    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
