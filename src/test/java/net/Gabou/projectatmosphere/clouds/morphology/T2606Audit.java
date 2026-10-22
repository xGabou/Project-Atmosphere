package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Recipe;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * T260.6 exhaustive audit of the sparse-brick conservative skip (the frozen {@link SparseBrickVolume} is not modified).
 *
 * <p>The bake classifies a grid cell (8^3 voxels, 9^3 samples with the shared apron) without evaluating its samples when
 * the value at the cell centre satisfies |d| >= L * r + cut + scale (L = 1.5, r = the centre-to-corner distance,
 * cut = feather / 2, scale = one code step): EMPTY when positive, FULL when negative. The shortcut is correct iff every
 * sample of every skipped cell quantises to >= cut (EMPTY) or <= -cut (FULL), i.e. iff the cell would have received the
 * same state had its samples been evaluated. This audit replays the bake's own decision for every cell (same box, same
 * {@code field.local(box)} evaluator, same centre value, same threshold) and, for every skipped cell, evaluates and
 * quantises all 729 samples exactly as {@code classify} does. It also checks the recorded cell state equals the skip
 * decision (the audited volume is the baked one).
 *
 * <p>Validation of the auditor itself:
 * <ul>
 * <li>{@code frozen}: on D and A it must report exactly what the frozen {@link SparseBrickVolume#auditCoarseSkips} reports
 * (that one evaluates the unculled global field);</li>
 * <li>{@code control}: a deliberately broken field (distance x 2.5 outside, Lipschitz 2.5 > 1.5) must be caught;</li>
 * <li>{@code local}: on random lattice samples, the quantised value of the per-cell local evaluator must equal the
 * global field's (the culling the bake relies on).</li>
 * </ul>
 * Args: audit seeds(comma) arms(comma) | frozen seed arms | control seed arm | local seed arms samples
 */
public final class T2606Audit {
    static final float L = 1.5f;

    public static void main(String[] args) {
        switch (args[0]) {
            case "audit" -> { for (String s : args[1].split(",")) for (String a : args[2].split(",")) audit(Long.parseLong(s), a); }
            case "frozen" -> { for (String a : args[2].split(",")) frozen(Long.parseLong(args[1]), a); }
            case "control" -> control(Long.parseLong(args[1]), args[2]);
            case "local" -> { for (String a : args[2].split(",")) local(Long.parseLong(args[1]), a, Integer.parseInt(args[3])); }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    /** Result of auditing one volume: {cells, skippedEmpty, skippedFull, samplesChecked, badCells, badSamples, stateMismatches, worst}. */
    record Result(int cells, int skippedEmpty, int skippedFull, long samples, int badCells, long badSamples, int stateMismatch, float worstMargin, double ms, int badEmpty, int badFull, float worstEmpty, float worstFull) {}

    static Result run(SparseBrickVolume v, SparseBrickVolume.Field field) {
        long t0 = System.nanoTime();
        int[] g = v.grid();
        int gx = g[0], gy = g[1], gz = g[2];
        Bounds b = v.bounds();
        float voxel = v.voxel(), feather = v.feather(), band = feather * 2.5f, scale = 2 * band / SparseBrickVolume.CODE_MAX, offset = -band;
        float cell = SparseBrickVolume.B * voxel, half = cell * .5f, radius = half * (float) Math.sqrt(3), cut = feather * .5f;
        float threshold = L * radius + cut + scale;
        int S = SparseBrickVolume.S;
        AtomicLong samples = new AtomicLong(), badSamples = new AtomicLong();
        int[] se = new int[gx * gy * gz], sf = new int[se.length], bad = new int[se.length], mis = new int[se.length];
        float[] worst = new float[se.length];
        int[] be = new int[se.length], bf = new int[se.length];
        IntStream.range(0, se.length).parallel().forEach(c -> {
            int bi = c % gx, bj = (c / gx) % gy, bk = c / (gx * gy);
            float x0 = b.minX() + bi * cell, y0 = b.minY() + bj * cell, z0 = b.minZ() + bk * cell;
            SparseBrickVolume.Field f = field.local(new Bounds(x0 - band, y0 - band, z0 - band, x0 + cell + band, y0 + cell + band, z0 + cell + band));
            float centre = f.distance(x0 + half, y0 + half, z0 + half);
            boolean empty = centre >= threshold, full = centre <= -threshold;
            worst[c] = Float.MAX_VALUE;
            if (!empty && !full) return;
            int state = v.cellState(bi, bj, bk);
            if (empty && state != SparseBrickVolume.EMPTY || full && state != SparseBrickVolume.FULL) mis[c] = 1;
            if (empty) se[c] = 1; else sf[c] = 1;
            long n = 0, nb = 0;
            float w = Float.MAX_VALUE;
            for (int k = 0; k < S; k++) for (int j = 0; j < S; j++) for (int i = 0; i < S; i++) {
                float d = f.distance(x0 + i * voxel, y0 + j * voxel, z0 + k * voxel);
                float clamped = Math.max(-band, Math.min(band, d));
                int q = Math.max(0, Math.min(SparseBrickVolume.CODE_MAX, Math.round((clamped - offset) / scale)));
                float val = q * scale + offset;
                float margin = empty ? val - cut : -cut - val; // >= 0 when the sample agrees with the skipped state
                w = Math.min(w, margin);
                n++;
                if (margin < 0) nb++;
            }
            worst[c] = w;
            samples.addAndGet(n);
            if (nb > 0) { bad[c] = 1; badSamples.addAndGet(nb); if (empty) be[c] = 1; else bf[c] = 1; }
        });
        float wm = Float.MAX_VALUE, we = Float.MAX_VALUE, wf = Float.MAX_VALUE;
        for (int c = 0; c < worst.length; c++) {
            wm = Math.min(wm, worst[c]);
            if (se[c] == 1) we = Math.min(we, worst[c]);
            if (sf[c] == 1) wf = Math.min(wf, worst[c]);
        }
        return new Result(se.length, Arrays.stream(se).sum(), Arrays.stream(sf).sum(), samples.get(), Arrays.stream(bad).sum(), badSamples.get(),
                Arrays.stream(mis).sum(), wm, (System.nanoTime() - t0) / 1e6, Arrays.stream(be).sum(), Arrays.stream(bf).sum(), we, wf);
    }

    static void audit(long seed, String arm) {
        Recipe r = ConvectiveSystemT260.calvusD(seed);
        for (boolean iso : new boolean[]{false, true}) {
            var p = T2606Preview.arm(r, arm, iso);
            Result x = run(p.volume(), p.field());
            System.out.printf(Locale.ROOT, "T2606_AUDIT seed=%d arm=%s volume=%s signature=%s cells=%d skippedEmpty=%d skippedFull=%d samplesChecked=%d badCells=%d badEmpty=%d badFull=%d badSamples=%d stateMismatch=%d worstMarginBlocks=%.3f worstEmptyMargin=%.3f worstFullMargin=%.3f auditMs=%.0f%n",
                    seed, arm, iso ? "isolated" : "full", Long.toHexString(p.signature()), x.cells, x.skippedEmpty, x.skippedFull, x.samples, x.badCells, x.badEmpty, x.badFull, x.badSamples, x.stateMismatch, x.worstMargin, x.worstEmpty, x.worstFull, x.ms);
        }
    }

    static void frozen(long seed, String arm) {
        Recipe r = ConvectiveSystemT260.calvusD(seed);
        var p = T2606Preview.arm(r, arm, false);
        long t0 = System.nanoTime();
        int frozen = p.volume().auditCoarseSkips(p.field());
        double fMs = (System.nanoTime() - t0) / 1e6;
        Result x = run(p.volume(), p.field());
        System.out.printf(Locale.ROOT, "T2606_AUDIT_VALIDATION seed=%d arm=%s frozenAuditMismatchedCells=%d frozenMs=%.0f thisAuditBadCells=%d skipped=%d thisMs=%.0f%n",
                seed, arm, frozen, fMs, x.badCells, x.skippedEmpty + x.skippedFull, x.ms);
    }

    /** Negative control: outside distances scaled x 2.5 break the Lipschitz assumption; the audit must catch it. */
    static void control(long seed, String arm) {
        Recipe r = ConvectiveSystemT260.calvusD(seed);
        var base = ConvectiveVolumeT2606.field(r, ConvectiveVolumeT2606.Arms.parse(arm), null, 30, 12);
        SparseBrickVolume.Field broken = new SparseBrickVolume.Field() {
            public float distance(float x, float y, float z) { float d = base.distance(x, y, z); return d > 0 ? d * 2.5f : d; }
            public SparseBrickVolume.Field local(Bounds box) {
                var l = base.local(box);
                return new SparseBrickVolume.Field() {
                    public float distance(float x, float y, float z) { float d = l.distance(x, y, z); return d > 0 ? d * 2.5f : d; }
                };
            }
        };
        var v = new SparseBrickVolume(broken, ConvectiveVolumeT2606.domain(r, ConvectiveVolumeT2606.Arms.parse(arm), null, 30), 6, 12, L, .25f);
        Result x = run(v, broken);
        // On D the frozen auditor (global field) is affordable: both auditors must flag the same cells.
        int frozen = arm.equals("D") ? v.auditCoarseSkips(broken) : -1;
        System.out.printf(Locale.ROOT, "T2606_AUDIT_CONTROL seed=%d arm=%s brokenField=outside_x2.5 skippedEmpty=%d badCells=%d badSamples=%d worstMarginBlocks=%.3f frozenAuditBadCells=%d (must be > 0 bad cells)%n",
                seed, arm, x.skippedEmpty, x.badCells, x.badSamples, x.worstMargin, frozen);
    }

    /** Local (per-cell culled) evaluator vs global field on random lattice samples: quantised codes and occupancy. */
    static void local(long seed, String arm, int n) {
        Recipe r = ConvectiveSystemT260.calvusD(seed);
        var p = T2606Preview.arm(r, arm, false);
        var v = p.volume();
        var field = p.field();
        Bounds b = v.bounds();
        float voxel = v.voxel(), feather = v.feather(), band = feather * 2.5f, scale = 2 * band / SparseBrickVolume.CODE_MAX, offset = -band;
        float cell = SparseBrickVolume.B * voxel;
        int[] g = v.grid();
        java.util.Random q = new java.util.Random(seed ^ 0x2606);
        long[] codeDiff = new long[1], occDiff = new long[1], near = new long[1];
        int maxCode = 0;
        for (int s = 0; s < n; s++) {
            int bi = q.nextInt(g[0]), bj = q.nextInt(g[1]), bk = q.nextInt(g[2]);
            float x0 = b.minX() + bi * cell, y0 = b.minY() + bj * cell, z0 = b.minZ() + bk * cell;
            // Bias half the samples to cells that hold material (where culling could matter).
            if (s % 2 == 0 && v.cellState(bi, bj, bk) == SparseBrickVolume.EMPTY) { s--; continue; }
            var f = field.local(new Bounds(x0 - band, y0 - band, z0 - band, x0 + cell + band, y0 + cell + band, z0 + cell + band));
            float x = x0 + q.nextInt(9) * voxel, y = y0 + q.nextInt(9) * voxel, z = z0 + q.nextInt(9) * voxel;
            float dl = f.distance(x, y, z), dg = field.distance(x, y, z);
            int cl = Math.max(0, Math.min(SparseBrickVolume.CODE_MAX, Math.round((Math.max(-band, Math.min(band, dl)) - offset) / scale)));
            int cg = Math.max(0, Math.min(SparseBrickVolume.CODE_MAX, Math.round((Math.max(-band, Math.min(band, dg)) - offset) / scale)));
            if (cl != cg) { codeDiff[0]++; maxCode = Math.max(maxCode, Math.abs(cl - cg)); }
            if ((cl * scale + offset < feather * .5f) != (cg * scale + offset < feather * .5f)) occDiff[0]++;
            if (Math.abs(dg) < band) near[0]++;
        }
        System.out.printf(Locale.ROOT, "T2606_AUDIT_LOCAL seed=%d arm=%s samples=%d withinBand=%d codeDiffers=%d maxCodeDiff=%d (%.3f blocks) occupancyDiffers=%d%n",
                seed, arm, n, near[0], codeDiff[0], maxCode, maxCode * scale, occDiff[0]);
    }
}
