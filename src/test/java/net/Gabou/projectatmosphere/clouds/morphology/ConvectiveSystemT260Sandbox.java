package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Recipe;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * T260 phase 1 sparse-brick feasibility (CPU half): addressing, packing, seams, conservative skipping, interval boxes,
 * occupancy ladder (bricks, memory, bake time) and incremental update cost against a fresh bake. Writes JSON evidence.
 */
public final class ConvectiveSystemT260Sandbox {
    static int failures;
    static final List<String> json = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/t260-sandbox");
        Files.createDirectories(out);
        float voxel = 6, feather = 12;
        // Occupancy ladder: count x radius range, increasing occupied volume and surface.
        int[][] ladder = {{8, 120, 200}, {24, 120, 220}, {48, 140, 240}, {96, 140, 260}, {160, 150, 280}, {260, 160, 300}};
        for (int[] l : ladder) {
            Recipe r = ConvectiveSystemT260.placeholder(l[0], l[1], l[2], 20260 + l[0]);
            var p = new ConvectiveSystemT260.Program(r, null, voxel, feather, .25f);
            var v = p.volume();
            var s = v.stats();
            int[] g = v.grid();
            long denseSamples = (long) (g[0] * 8 + 1) * (g[1] * 8 + 1) * (g[2] * 8 + 1);
            double occupied = occupiedFraction(p, 400_000, 7);
            String line = String.format(Locale.ROOT,
                    "{\"recipe\":\"%s\",\"voxel\":%.1f,\"feather\":%.1f,\"grid\":[%d,%d,%d],\"gridCells\":%d,\"empty\":%d,\"full\":%d,\"bricks\":%d,"
                            + "\"brickFraction\":%.4f,\"occupiedVolumeFraction\":%.4f,\"coarseSkipped\":%d,\"sampleEvaluations\":%d,\"bakeMs\":%.1f,"
                            + "\"textureBytes\":%d,\"textureRows\":%d,\"cpuBytes\":%d,\"denseEquivalentSamples\":%d,\"denseT258Bytes\":%d,\"boxes\":%d}",
                    r.kind(), voxel, feather, g[0], g[1], g[2], s.gridCells(), s.emptyCells(), s.fullCells(), s.bricks(),
                    s.bricks() / (double) s.gridCells(), occupied, s.coarseSkipped(), s.sampleEvaluations(), s.bakeMs(),
                    s.textureBytes(), v.textureHeight(), s.cpuBytes(), denseSamples, denseSamples * 16, v.boxCount());
            System.out.println("T260_LADDER " + line);
            json.add(line);
            correctness(r, p, l[0] <= 48);
        }
        updateCost(voxel, feather);
        Files.writeString(out.resolve("t260-phase1-cpu.json"), "[\n" + String.join(",\n", json) + "\n]\n");
        System.out.println(failures == 0 ? "T260_SANDBOX PASS" : "T260_SANDBOX FAIL failures=" + failures);
        if (failures != 0) System.exit(1);
    }

    static double occupiedFraction(ConvectiveSystemT260.Program p, int n, long seed) {
        Random random = new Random(seed);
        Bounds b = p.bounds();
        int hit = 0;
        for (int i = 0; i < n; i++) {
            float x = b.minX() + (b.maxX() - b.minX()) * random.nextFloat(), y = b.minY() + (b.maxY() - b.minY()) * random.nextFloat(), z = b.minZ() + (b.maxZ() - b.minZ()) * random.nextFloat();
            if (p.density(x, y, z, StormMorphologyPrototype.Scale.MESO) > .5f) hit++;
        }
        return hit / (double) n;
    }

    static void correctness(Recipe r, ConvectiveSystemT260.Program p, boolean audit) {
        var v = p.volume();
        var field = p.field();
        Random random = new Random(r.seed() ^ 0x5DEECE66DL);
        Bounds b = v.bounds();
        float voxel = v.voxel(), feather = v.feather(), band = feather * 2.5f, scale = 2 * band / SparseBrickVolume.CODE_MAX;
        // 1. Packed-texture decode == code-array mirror (addressing + packing), everywhere.
        float packErr = 0;
        for (int i = 0; i < 300_000; i++) {
            float x = b.minX() + (b.maxX() - b.minX()) * random.nextFloat(), y = b.minY() + (b.maxY() - b.minY()) * random.nextFloat(), z = b.minZ() + (b.maxZ() - b.minZ()) * random.nextFloat();
            packErr = Math.max(packErr, Math.abs(v.density(x, y, z) - v.densityFromTexture(x, y, z)));
        }
        check("pack_decode_" + r.kind(), packErr <= 1e-5f, "maxAbs=" + packErr);
        // 2. Against a dense reference lattice (direct field evaluation at the 8 corners, same quantisation):
        //    exact classification means the sparse volume equals the dense one everywhere.
        float denseErr = 0;
        for (int i = 0; i < 120_000; i++) {
            float x = b.minX() + (b.maxX() - b.minX() - voxel) * random.nextFloat(), y = b.minY() + (b.maxY() - b.minY() - voxel) * random.nextFloat(), z = b.minZ() + (b.maxZ() - b.minZ() - voxel) * random.nextFloat();
            float gx = (x - b.minX()) / voxel, gy = (y - b.minY()) / voxel, gz = (z - b.minZ()) / voxel;
            int i0 = (int) Math.floor(gx), j0 = (int) Math.floor(gy), k0 = (int) Math.floor(gz);
            float fx = gx - i0, fy = gy - j0, fz = gz - k0;
            float[] c = new float[8];
            for (int n = 0; n < 8; n++) {
                float d = field.distance(b.minX() + (i0 + (n & 1)) * voxel, b.minY() + (j0 + ((n >> 1) & 1)) * voxel, b.minZ() + (k0 + (n >> 2)) * voxel);
                c[n] = Math.max(0, Math.min(SparseBrickVolume.CODE_MAX, Math.round((Math.max(-band, Math.min(band, d)) + band) / scale)));
            }
            float m0 = mix(c[0], c[2], fy), m1 = mix(c[1], c[3], fy), m2 = mix(c[4], c[6], fy), m3 = mix(c[5], c[7], fy);
            float code = mix(mix(m0, m1, fx), mix(m2, m3, fx), fz);
            float dense = Math.max(0, Math.min(1, .5f - (code * scale - band) / feather));
            denseErr = Math.max(denseErr, Math.abs(dense - v.density(x, y, z)));
        }
        check("dense_equivalence_" + r.kind(), denseErr <= 1e-4f, "maxAbs=" + denseErr);
        // 3. Seams: points straddling brick faces (x = cell boundary +- 1e-3 voxel) are continuous.
        float seam = 0;
        float cell = SparseBrickVolume.B * voxel;
        int[] grid = v.grid();
        for (int i = 0; i < 60_000; i++) {
            int axis = random.nextInt(3);
            float x = b.minX() + (b.maxX() - b.minX()) * random.nextFloat(), y = b.minY() + (b.maxY() - b.minY()) * random.nextFloat(), z = b.minZ() + (b.maxZ() - b.minZ()) * random.nextFloat();
            int face = 1 + random.nextInt(Math.max(1, grid[axis] - 1));
            float e = voxel * 1e-3f;
            float[] p0 = {x, y, z}, p1 = {x, y, z};
            float base = axis == 0 ? b.minX() : axis == 1 ? b.minY() : b.minZ();
            p0[axis] = base + face * cell - e;
            p1[axis] = base + face * cell + e;
            seam = Math.max(seam, Math.abs(v.densityFromTexture(p0[0], p0[1], p0[2]) - v.densityFromTexture(p1[0], p1[1], p1[2])));
        }
        // Max density gradient: 1/feather per block * |grad d| (<= ~1.5) * 2e-3 voxel.
        check("seams_" + r.kind(), seam <= 2.5e-3f, "maxJump=" + seam);
        // 4. Conservative skipping: every coarse skip re-evaluated sample by sample.
        if (audit) {
            int wrong = v.auditCoarseSkips(field);
            check("coarse_skip_conservative_" + r.kind(), wrong == 0, "misclassified=" + wrong);
        }
        // 5. Interval boxes cover every non-empty cell; outside them density is exactly 0.
        int uncovered = 0;
        for (int k = 0; k < grid[2]; k++) for (int j = 0; j < grid[1]; j++) for (int i = 0; i < grid[0]; i++) {
            if (v.cellState(i, j, k) == SparseBrickVolume.EMPTY) continue;
            float cx = b.minX() + (i + .5f) * cell, cy = b.minY() + (j + .5f) * cell, cz = b.minZ() + (k + .5f) * cell;
            boolean in = false;
            for (int n = 0; n < v.boxCount(); n++) {
                float[] bx = v.box(n);
                in |= cx >= bx[0] && cx <= bx[3] && cy >= bx[1] && cy <= bx[4] && cz >= bx[2] && cz <= bx[5];
            }
            if (!in) uncovered++;
        }
        check("boxes_cover_" + r.kind(), uncovered == 0, "uncoveredCells=" + uncovered + " boxes=" + v.boxCount());
    }

    /** Developing system: the tallest placeholder grows in steps; incremental update vs fresh bake. */
    static void updateCost(float voxel, float feather) {
        Recipe base = ConvectiveSystemT260.placeholder(48, 140, 240, 20260 + 48);
        var program = new ConvectiveSystemT260.Program(base, null, voxel, feather, .25f);
        var v = program.volume();
        int target = 0;
        for (int n = 1; n < base.bubbles().size(); n++) if (base.bubbles().get(n).y() + base.bubbles().get(n).ry() > base.bubbles().get(target).y() + base.bubbles().get(target).ry()) target = n;
        Recipe current = base;
        float band = feather * 2.5f;
        for (int step = 1; step <= 8; step++) {
            var old = current.bubbles().get(target);
            float grow = 1.04f;
            var next = new ConvectiveSystemT260.Bubble(old.x(), old.y() + old.ry() * .03f, old.z(), old.rx() * grow, old.ry() * grow, old.rz() * grow, old.comp(), old.tier());
            List<ConvectiveSystemT260.Bubble> list = new ArrayList<>(current.bubbles());
            list.set(target, next);
            current = new Recipe(current.kind(), current.seed(), current.components(), list, current.baseY(), current.baseBlend(), current.linkBlend(), current.primaryHeight(), current.windX(), current.windZ());
            float m = band + 40;
            Bounds region = new Bounds(Math.min(old.x() - old.rx(), next.x() - next.rx()) - m, Math.min(old.y() - old.ry(), next.y() - next.ry()) - m,
                    Math.min(old.z() - old.rz(), next.z() - next.rz()) - m, Math.max(old.x() + old.rx(), next.x() + next.rx()) + m,
                    Math.max(old.y() + old.ry(), next.y() + next.ry()) + m, Math.max(old.z() + old.rz(), next.z() + next.rz()) + m);
            var field = new ConvectiveSystemT260.Field(current, null, band);
            var u = v.update(field, region);
            var dirty = v.drainDirty();
            long dirtyTexels = 0;
            if (dirty != null) for (int[] d : dirty) dirtyTexels += d[1] - d[0];
            // Fresh bake on the SAME lattice: densities must match the incrementally updated volume.
            long t0 = System.nanoTime();
            var fresh = new SparseBrickVolume(field, v.bounds(), voxel, feather, 1.5f, .25f);
            double freshMs = (System.nanoTime() - t0) / 1e6;
            Random random = new Random(step);
            float err = 0;
            Bounds bb = v.bounds();
            for (int i = 0; i < 200_000; i++) {
                float x = region.minX() + (region.maxX() - region.minX()) * random.nextFloat(), y = Math.max(bb.minY(), region.minY()) + (Math.min(bb.maxY(), region.maxY()) - Math.max(bb.minY(), region.minY())) * random.nextFloat(), z = region.minZ() + (region.maxZ() - region.minZ()) * random.nextFloat();
                err = Math.max(err, Math.abs(v.densityFromTexture(x, y, z) - fresh.densityFromTexture(x, y, z)));
            }
            for (int i = 0; i < 100_000; i++) {
                float x = bb.minX() + (bb.maxX() - bb.minX()) * random.nextFloat(), y = bb.minY() + (bb.maxY() - bb.minY()) * random.nextFloat(), z = bb.minZ() + (bb.maxZ() - bb.minZ()) * random.nextFloat();
                err = Math.max(err, Math.abs(v.densityFromTexture(x, y, z) - fresh.densityFromTexture(x, y, z)));
            }
            check("update_equals_fresh_step" + step, err <= 1e-5f, "maxAbs=" + err);
            String line = String.format(Locale.ROOT,
                    "{\"update_step\":%d,\"cellsVisited\":%d,\"bricksBefore\":%d,\"bricksAfter\":%d,\"bricksWritten\":%d,\"bricksFreed\":%d,\"indexTexelsDirty\":%d,"
                            + "\"uploadTexels\":%d,\"uploadBytes\":%d,\"dirtyRanges\":%d,\"dirtyTexels\":%d,\"updateCpuMs\":%.2f,\"freshBakeMs\":%.1f,\"reallocated\":%s}",
                    step, u.cellsVisited(), u.bricksBefore(), u.bricksAfter(), u.bricksWritten(), u.bricksFreed(), u.indexTexelsDirty(),
                    u.uploadTexels(), u.uploadTexels() * 16, dirty == null ? -1 : dirty.size(), dirtyTexels, u.cpuMs(), freshMs, u.reallocated());
            System.out.println("T260_UPDATE " + line);
            json.add(line);
        }
    }

    static void check(String name, boolean ok, String detail) {
        System.out.println("T260_CHECK " + name + " " + (ok ? "PASS" : "FAIL") + " " + detail);
        json.add(String.format(Locale.ROOT, "{\"check\":\"%s\",\"pass\":%s,\"detail\":\"%s\"}", name, ok, detail));
        if (!ok) failures++;
    }

    static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
