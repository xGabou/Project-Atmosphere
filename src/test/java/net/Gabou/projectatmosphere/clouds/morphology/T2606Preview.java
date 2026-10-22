package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.client.render.volumetric.ConvectiveSystemCapturePlan;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.StormAnatomyCapturePlan;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Recipe;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * T260.6 CPU preview and checks (sandbox; the native capture is the evidence). Renders a reconstruction arm at the
 * native T260.5 cameras (T260.4 framing: C25 + D extremes; crown close-ups aimed at D's crown) with the frozen T260.5
 * neutral lighting: the same {@link NeutralLightVolume} bake, the same trilinear decode, isotropic single scattering,
 * exposure 3.2, composited front to back like the fixture march (converged step, no jitter).
 * <pre>
 * verify seeds(comma) arms(comma)                     D-mode signature == D, structure, coarse-skip audit, bake stats
 * render out seeds arms views [scale] [modes]         modes: lit,flat,density,direct,ambient,od (default lit,flat)
 * </pre>
 */
public final class T2606Preview {
    static final float EXT = .055f;
    static final int BG = 0x10151d;

    public static void main(String[] args) throws Exception {
        // Optional leading "set:NAME=value,NAME=value" overrides ConvectiveVolumeT2606 tunables (parameter sweeps).
        if (args[0].startsWith("set:")) {
            for (String kv : args[0].substring(4).split(",")) {
                String[] p = kv.split("=");
                ConvectiveVolumeT2606.class.getField(p[0]).setFloat(null, Float.parseFloat(p[1]));
                System.out.println("T2606_SET " + p[0] + "=" + p[1]);
            }
            args = Arrays.copyOfRange(args, 1, args.length);
        }
        switch (args[0]) {
            case "audit" -> T2606Audit.main(Arrays.copyOfRange(args, 1, args.length)); // audit <audit|frozen|control|local> ...
            case "metrics" -> T2606Metrics.main(Arrays.copyOfRange(args, 1, args.length));
            case "update" -> update(Long.parseLong(args[1]), args[2], Integer.parseInt(args[3]));
            case "support" -> support(Long.parseLong(args[1]), args[2]);
            case "structure" -> { for (String x : args[1].split(",")) structure(Long.parseLong(x)); }
            case "cost" -> cost(args[1].split(","), args[2].split(","), Integer.parseInt(args[3]));
            case "bench" -> bench(Long.parseLong(args[1]), args[2]);
            case "verify" -> verify(args[1].split(","), args[2].split(","));
            case "render" -> render(Path.of(args[1]), args[2].split(","), args[3].split(","), args[4].split(","),
                    args.length > 5 ? Double.parseDouble(args[5]) : .5, args.length > 6 ? args[6] : "lit,flat");
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    static ConvectiveVolumeT2606.Program arm(Recipe r, String arm, boolean iso) {
        boolean[] only = null;
        if (iso) {
            only = new boolean[r.components().size()];
            for (int c = 0; c < only.length; c++) only[c] = r.components().get(c).role() == ConvectiveSystemT260.Role.PRIMARY;
        }
        return new ConvectiveVolumeT2606.Program(r, ConvectiveVolumeT2606.Arms.parse(arm), only, 6, 12, .25f);
    }

    static void verify(String[] seeds, String[] arms) {
        for (String ss : seeds) {
            long seed = Long.parseLong(ss);
            Recipe r = ConvectiveSystemT260.calvusD(seed);
            var s = new ConvectiveVolumeT2606.Structure(r);
            int lumps = 0;
            for (int i = 0; i < r.bubbles().size(); i++) if (s.parentOf(i) >= 0) lumps++;
            long t0 = System.nanoTime();
            var d = new ConvectiveSystemT260.Program(r, null, 6, 12, .25f);
            double dMs = (System.nanoTime() - t0) / 1e6;
            System.out.printf(Locale.ROOT, "T2606_STRUCT seed=%d bubbles=%d turrets=%d lumps=%d dSignature=%s dBakeMs=%.0f counts=%s%n", seed, r.bubbles().size(),
                    s.turretCount(), lumps, Long.toHexString(d.signature()), dMs, Arrays.toString(d.volume().counts()));
            for (String a : arms) {
                t0 = System.nanoTime();
                var p = arm(r, a, false);
                double ms = (System.nanoTime() - t0) / 1e6;
                // The coarse-skip audit re-evaluates every skipped cell sample by sample (slow): PA_T2606_AUDIT=1.
                int audit = "1".equals(System.getenv("PA_T2606_AUDIT")) ? p.volume().auditCoarseSkips(p.field()) : -1;
                var st = p.volume().stats();
                System.out.printf(Locale.ROOT, "T2606_ARM seed=%d arm=%s signature=%s equalsD=%s bakeMs=%.0f fieldMs=%.0f counts=%s textureMB=%.1f coarseAuditMismatches=%d bounds=%s%n",
                        seed, a, Long.toHexString(p.signature()), p.signature() == d.signature(), ms, p.fieldMs(), Arrays.toString(p.volume().counts()),
                        st.textureBytes() / 1048576.0, audit, p.bounds());
            }
        }
    }

    /**
     * Update cost of a developing system: ConvectiveSystemT260.developStep grows the tallest cell; the arm's field is
     * rebuilt over the original anatomy and the volume is updated incrementally inside the changed region (grown by the
     * growth-column and bulge margin); the light is rebaked. Each step is checked against a fresh bake (signature).
     */
    static void update(long seed, String arm, int steps) {
        Recipe r = ConvectiveSystemT260.calvusD(seed);
        var arms = ConvectiveVolumeT2606.Arms.parse(arm);
        var structure = new ConvectiveVolumeT2606.Structure(r);
        var p = arm(r, arm, false);
        var v = p.volume();
        Recipe cur = r;
        float maxR = 0;
        for (var b : r.bubbles()) if (b.tier() == 0 || b.tier() == 4) maxR = Math.max(maxR, Math.max(b.rx(), b.rz()));
        for (int step = 1; step <= steps; step++) {
            var next = ConvectiveSystemT260.developStep(cur, step);
            long t0 = System.nanoTime();
            var field = ConvectiveVolumeT2606.field(new ConvectiveVolumeT2606.Structure(next.recipe(), structure), arms, null, 30, 12);
            double fieldMs = (System.nanoTime() - t0) / 1e6;
            // A grown thermal reshapes its whole turret column (and so the bulges seated on it): widen the region.
            float m = (arms.growth() ? .9f * maxR : 0) + 60;
            Bounds g = next.region();
            Bounds region = new Bounds(g.minX() - m, g.minY() - m, g.minZ() - m, g.maxX() + m, g.maxY() + m, g.maxZ() + m);
            var u = v.update(field, region);
            long t1 = System.nanoTime();
            var light = new NeutralLightVolume(v, EXT);
            double lightMs = (System.nanoTime() - t1) / 1e6;
            // Same lattice as the updated volume (the original domain), so samples coincide.
            var fresh = new SparseBrickVolume(field, ConvectiveVolumeT2606.domain(r, arms, null, 30), 6, 12, 1.5f, .25f);
            boolean same = true;
            Bounds bb = fresh.bounds();
            java.util.Random q = new java.util.Random(step);
            int mism = 0;
            for (int k = 0; k < 400000; k++) {
                float x = bb.minX() + q.nextFloat() * (bb.maxX() - bb.minX()), y = bb.minY() + q.nextFloat() * (bb.maxY() - bb.minY()), z = bb.minZ() + q.nextFloat() * (bb.maxZ() - bb.minZ());
                if (Math.abs(v.density(x, y, z) - fresh.density(x, y, z)) > 1e-6f) mism++;
            }
            System.out.printf(Locale.ROOT, "T2606_UPDATE seed=%d arm=%s step=%d fieldMs=%.0f updateCpuMs=%.0f cells=%d bricksWritten=%d uploadTexels=%d reallocated=%s lightRebakeMs=%.0f freshBakeMs=%.0f densityMismatchVsFresh=%d/400000%n",
                    seed, arm, step, fieldMs, u.cpuMs(), u.cellsVisited(), u.bricksWritten(), u.uploadTexels(), u.reallocated(), lightMs, fresh.stats().bakeMs(), mism);
            cur = next.recipe();
        }
    }

    /** Where a C arm's material support differs from its geometry twin: by the larger density of the two. */
    static void support(long seed, String arm) {
        Recipe r = ConvectiveSystemT260.calvusD(seed);
        long t0 = System.nanoTime();
        var c = arm(r, arm, false).volume();
        double cMs = (System.nanoTime() - t0) / 1e6;
        String twin = arm.replace("C", "");
        var g = arm(r, twin.isEmpty() ? "D" : twin, false).volume();
        Bounds b = c.bounds();
        float st = 3;
        long[] h = new long[6];
        long occ = 0;
        for (float z = b.minZ() + .13f; z < b.maxZ(); z += st) for (float y = b.minY() + .61f; y < b.maxY(); y += st) for (float x = b.minX() + .37f; x < b.maxX(); x += st) {
            float p = c.density(x, y, z), q = g.bounds().contains(x, y, z) ? g.density(x, y, z) : 0;
            if (p > 0 || q > 0) occ++;
            if ((p > 0) == (q > 0)) continue;
            float m = Math.max(p, q);
            h[m < 1e-3f ? 0 : m < 1e-2f ? 1 : m < .05f ? 2 : m < .2f ? 3 : m < .5f ? 4 : 5]++;
        }
        System.out.printf(Locale.ROOT, "T2606_SUPPORT seed=%d arm=%s twin=%s occupied=%d mismatch[<1e-3, <1e-2, <0.05, <0.2, <0.5, >=0.5]=%s bakeMs=%.0f%n", seed, arm, twin, occ, Arrays.toString(h), cMs);
    }

    /**
     * Structural attribution (no geometry change): for the primary cell's turrets, the shape inputs B inherits from D
     * (axis lean, height, radius, spacing) and what the growth column does with them (radius variation along the height);
     * for the growth-dome population, size spread, spacing regularity (nearest-neighbour CV; a Poisson-disc pattern is
     * regular) and orientation.
     */
    static void structure(long seed) {
        Recipe r = ConvectiveSystemT260.calvusD(seed);
        var s = new ConvectiveVolumeT2606.Structure(r);
        var cols = ConvectiveVolumeT2606.columns(s);
        var pops = ConvectiveVolumeT2606.population(r, cols);
        float hp = r.primaryHeight();
        StringBuilder t = new StringBuilder();
        List<double[]> axes = new ArrayList<>();
        List<Double> heights = new ArrayList<>(), radii = new ArrayList<>(), rvar = new ArrayList<>();
        for (int k = 0; k < s.turretCount(); k++) {
            if (r.components().get(s.turretComp(k)).role() != ConvectiveSystemT260.Role.PRIMARY) continue;
            var c = cols[k];
            int n = c.ky.length;
            double lx = c.kx[n - 1] - c.kx[0], lz = c.kz[n - 1] - c.kz[0], ly = c.ky[n - 1] - c.ky[0];
            double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
            if (n > 1) axes.add(new double[]{lx / len, ly / len, lz / len});
            heights.add((double) c.yTop / hp);
            double mean = 0;
            for (float a : c.ka) mean += a;
            mean /= n;
            radii.add(mean);
            // Radius variation of the column's side profile (r0 sampled every 6 blocks between the base and the dome).
            List<Double> prof = new ArrayList<>();
            for (float y = Math.max(c.yb, 0) + 6; y < c.yc; y += 6) prof.add((double) c.r0(y));
            rvar.add(prof.size() < 2 ? 0 : sd(prof) / mean(prof));
            t.append(String.format(Locale.ROOT, "{\"thermals\":%d,\"topOverH\":%.3f,\"meanRadius\":%.0f,\"leanDeg\":%.1f,\"leanAzDeg\":%.0f}",
                    n, c.yTop / hp, mean, n > 1 ? Math.toDegrees(Math.acos(ly / len)) : 0, Math.toDegrees(Math.atan2(lz, lx)))).append(',');
        }
        // Pairwise angle between turret axes (parallel columns = small angles).
        List<Double> ang = new ArrayList<>();
        for (int i = 0; i < axes.size(); i++) for (int j = i + 1; j < axes.size(); j++) {
            double[] a = axes.get(i), b = axes.get(j);
            ang.add(Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2])))));
        }
        // Dome population over the whole system.
        List<Double> r1 = new ArrayList<>(), r2 = new ArrayList<>(), up = new ArrayList<>(), nn = new ArrayList<>();
        List<float[]> all1 = new ArrayList<>();
        int perCol = 0, cols1 = 0;
        for (var pl : pops) {
            int c1 = 0;
            for (float[] q : pl) {
                if (q[8] == 1) { r1.add((double) q[7]); all1.add(q); c1++; } else r2.add((double) q[7]);
                up.add((double) q[4]);
            }
            if (c1 > 0) { perCol += c1; cols1++; }
        }
        for (float[] a : all1) {
            double best = 1e18;
            for (float[] b : all1) {
                if (a == b) continue;
                double d = (a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]);
                best = Math.min(best, d);
            }
            nn.add(Math.sqrt(best) / a[7]);
        }
        System.out.printf(Locale.ROOT, "T2606_STRUCTURE {\"seed\":%d,\"primaryTurrets\":%d,\"topOverH\":{\"mean\":%.3f,\"cv\":%.3f},\"meanRadius\":{\"mean\":%.0f,\"cv\":%.3f},"
                        + "\"axisPairAngleDeg\":{\"p10\":%.1f,\"median\":%.1f,\"p90\":%.1f},\"columnRadiusVariationCV\":{\"median\":%.3f,\"max\":%.3f},"
                        + "\"domes\":{\"tier1\":%d,\"tier2\":%d,\"tier1PerColumn\":%.1f,\"tier1RadiusCV\":%.3f,\"tier2RadiusCV\":%.3f,\"tier1RadiusP10P90\":[%.0f,%.0f],"
                        + "\"nearestNeighbourOverRadius\":{\"median\":%.2f,\"cv\":%.3f},\"axisUpComponent\":{\"median\":%.2f,\"p10\":%.2f}},\"turrets\":[%s]}%n",
                seed, heights.size(), mean(heights), sd(heights) / mean(heights), mean(radii), sd(radii) / mean(radii),
                pct(ang, .1), pct(ang, .5), pct(ang, .9), pct(rvar, .5), pct(rvar, 1),
                r1.size(), r2.size(), perCol / (double) Math.max(1, cols1), sd(r1) / mean(r1), sd(r2) / mean(r2), pct(r1, .1), pct(r1, .9),
                pct(nn, .5), sd(nn) / mean(nn), pct(up, .5), pct(up, .1), t.length() > 0 ? t.substring(0, t.length() - 1) : "");
    }

    static double mean(List<Double> v) { return v.stream().mapToDouble(Double::doubleValue).average().orElse(0); }
    static double sd(List<Double> v) { double m = mean(v); return Math.sqrt(v.stream().mapToDouble(x -> (x - m) * (x - m)).average().orElse(0)); }
    static double pct(List<Double> v, double f) {
        if (v.isEmpty()) return 0;
        List<Double> s = new ArrayList<>(v);
        s.sort(Double::compare);
        return s.get(Math.min(s.size() - 1, (int) Math.floor(f * (s.size() - 1))));
    }

    static long cpuNanos() {
        return ((com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime();
    }

    /**
     * Per-candidate CPU cost, measured synchronously (nothing hidden off-thread; the bake's own parallel streams are
     * reported as wall time and as total process CPU time): field derivation, geometry bake, light bake, memory, and the
     * incremental growth update decomposed into field rebuild, update region (cells visited), samples evaluated per
     * cell and time per sample, and bricks actually rewritten. Each bake is repeated {@code reps} times (median).
     */
    static void cost(String[] seeds, String[] arms, int reps) {
        for (String ss : seeds) {
            long seed = Long.parseLong(ss);
            Recipe r = ConvectiveSystemT260.calvusD(seed);
            for (String arm : arms) {
                var a = ConvectiveVolumeT2606.Arms.parse(arm);
                double[] wall = new double[reps], cpu = new double[reps], lw = new double[reps], lc = new double[reps], fw = new double[reps];
                ConvectiveVolumeT2606.Program p = null;
                NeutralLightVolume light = null;
                for (int k = 0; k < reps; k++) {
                    System.gc();
                    long w0 = System.nanoTime(), c0 = cpuNanos();
                    p = arm(r, arm, false);
                    wall[k] = (System.nanoTime() - w0) / 1e6; cpu[k] = (cpuNanos() - c0) / 1e6; fw[k] = p.fieldMs();
                    long w1 = System.nanoTime(), c1 = cpuNanos();
                    light = new NeutralLightVolume(p.volume(), EXT);
                    lw[k] = (System.nanoTime() - w1) / 1e6; lc[k] = (cpuNanos() - c1) / 1e6;
                }
                var v = p.volume();
                int elems = 0;
                for (var g : p.field().groups) for (var c : g) {
                    if (c.flat() != null) elems += c.flat().length;
                    else for (var t : c.turrets()) elems += t.stack().length + t.lumps().length;
                }
                for (var d : p.field().detail) elems += d.length;
                long lightBytes = (long) (light.rows(v.textureHeight()) - v.textureHeight()) * SparseBrickVolume.TEX_W * 16;
                // Update: three growth steps (incremental, synchronous), decomposed.
                var structure = new ConvectiveVolumeT2606.Structure(r);
                Recipe cur = r;
                float maxR = 0;
                for (var b : r.bubbles()) if (b.tier() == 0 || b.tier() == 4) maxR = Math.max(maxR, Math.max(b.rx(), b.rz()));
                StringBuilder up = new StringBuilder();
                for (int step = 1; step <= 3; step++) {
                    var next = ConvectiveSystemT260.developStep(cur, step);
                    long w0 = System.nanoTime(), c0 = cpuNanos();
                    var field = ConvectiveVolumeT2606.field(new ConvectiveVolumeT2606.Structure(next.recipe(), structure), a, null, 30, 12);
                    double fieldMs = (System.nanoTime() - w0) / 1e6;
                    float m = (a.growth() ? .9f * maxR : 0) + 60;
                    Bounds g = next.region();
                    var u = v.update(field, new Bounds(g.minX() - m, g.minY() - m, g.minZ() - m, g.maxX() + m, g.maxY() + m, g.maxZ() + m));
                    double totalWall = (System.nanoTime() - w0) / 1e6, totalCpu = (cpuNanos() - c0) / 1e6;
                    long l0 = System.nanoTime();
                    new NeutralLightVolume(v, EXT);
                    double relight = (System.nanoTime() - l0) / 1e6;
                    if (up.length() > 0) up.append(',');
                    up.append(String.format(Locale.ROOT, "{\"step\":%d,\"fieldRebuildMs\":%.0f,\"brickUpdateMs\":%.0f,\"totalWallMs\":%.0f,\"totalCpuMs\":%.0f,\"cellsVisited\":%d,\"bricksWritten\":%d,\"uploadTexels\":%d,\"lightRebakeMs\":%.0f}",
                            step, fieldMs, u.cpuMs(), totalWall, totalCpu, u.cellsVisited(), u.bricksWritten(), u.uploadTexels(), relight));
                    cur = next.recipe();
                }
                java.util.Arrays.sort(wall); java.util.Arrays.sort(cpu); java.util.Arrays.sort(lw); java.util.Arrays.sort(lc); java.util.Arrays.sort(fw);
                var st = v.stats();
                System.out.printf(Locale.ROOT, "T2606_COST {\"seed\":%d,\"arm\":\"%s\",\"reps\":%d,\"geometryBakeWallMs\":%.0f,\"geometryBakeCpuMs\":%.0f,\"fieldDerivationMs\":%.0f,"
                                + "\"lightBakeWallMs\":%.0f,\"lightBakeCpuMs\":%.0f,\"elements\":%d,\"cells\":%s,\"evaluatedCells\":%d,\"sampleEvaluations\":%d,"
                                + "\"volumeTextureMB\":%.1f,\"lightRowsMB\":%.1f,\"cpuVolumeMB\":%.1f,\"update\":[%s]}%n",
                        seed, arm, reps, wall[reps / 2], cpu[reps / 2], fw[reps / 2], lw[reps / 2], lc[reps / 2], elems, java.util.Arrays.toString(v.counts()), st.evaluatedCells(),
                        st.sampleEvaluations(), st.textureBytes() / 1048576.0, lightBytes / 1048576.0, v.cpuBytes() / 1048576.0, up);
            }
        }
    }

    /** Splits the bake cost: local() culling vs distance evaluation, over the bake's own cell boxes (single thread). */
    static void bench(long seed, String arm) {
        Recipe r = ConvectiveSystemT260.calvusD(seed);
        var a = ConvectiveVolumeT2606.Arms.parse(arm);
        var f = ConvectiveVolumeT2606.field(r, a, null, 30, 12);
        Bounds dom = ConvectiveVolumeT2606.domain(r, a, null, 30);
        java.util.Random q = new java.util.Random(1);
        long tl = 0, td = 0; int n = 0; double sink = 0; long evals = 0;
        ConvectiveVolumeT2606.COUNT = true;
        for (int i = 0; i < 400; i++) {
            // Cells at D bubble surfaces (where the bake actually evaluates samples).
            var bb = r.bubbles().get(q.nextInt(r.bubbles().size()));
            double th = q.nextDouble() * Math.PI * 2, el = Math.asin(q.nextDouble() * 2 - 1);
            float x = bb.x() + bb.rx() * (float) (Math.cos(el) * Math.cos(th)) - 54, y = bb.y() + bb.ry() * (float) Math.sin(el) - 54, z = bb.z() + bb.rz() * (float) (Math.cos(el) * Math.sin(th)) - 54;
            long t0 = System.nanoTime();
            var loc = f.local(new Bounds(x - 30, y - 30, z - 30, x + 78, y + 78, z + 78));
            long t1 = System.nanoTime();
            for (int k = 0; k < 729; k++) { sink += loc.distance(x + (k % 9) * 6, y + (k / 9 % 9) * 6, z + (k / 81) * 6); evals++; }
            long t2 = System.nanoTime();
            tl += t1 - t0; td += t2 - t1; n++;
        }
        System.out.printf(Locale.ROOT, "T2606_BENCH arm=%s cells=%d localUsPerCell=%.1f distanceUsPerSample=%.2f colPerSample=%.1f ellPerSample=%.1f sink=%.1f%n", arm, n, tl / 1e3 / n, td / 1e3 / evals,
                ConvectiveVolumeT2606.nCol / (double) evals, ConvectiveVolumeT2606.nEll / (double) evals, sink);
    }

    // ------------------------------------------------------------------ rendering

    /** Camera of a T260.5 view: {pose(6), fov}. */
    static double[] camera(String view, long seed, Bounds frame) {
        boolean zoom = view.startsWith("z4_");
        String pose = view.replace("iso_", "").replace("z4_", "");
        float[] pts = ConvectiveSystemT260Gallery.unionExtremes(seed, "CD");
        double modifier = 1.1, saved = 110;
        int optionFov = zoom ? (int) saved : ConvectiveSystemCapturePlan.optionFov(pose, (int) saved);
        double effective = optionFov * modifier;
        double[] rel = ConvectiveSystemCapturePlan.pose(pose, frame, pts, effective, 1200 / 675.0);
        int shotFov = optionFov;
        if (zoom) {
            rel = ConvectiveSystemCapturePlan.zoomPose(pose, frame, rel, ConvectiveSystemT260.calvusD(seed));
            shotFov = StormAnatomyCapturePlan.zoomOptionFov(effective, modifier, StormAnatomyCapturePlan.ZOOM);
        }
        double[] out = Arrays.copyOf(rel, 7);
        out[6] = shotFov * modifier;
        return out;
    }

    record Lit(float[] alpha, float[] rgb) {}

    /** Neutral light lattice decoded from the appended texture rows exactly as paNeutralBaked reads them. */
    static final class LightLattice {
        final float[] tex;
        final float minX, minY, minZ, sp;
        final int nx, ny, nz, base, nxp;
        LightLattice(SparseBrickVolume v) {
            var light = new NeutralLightVolume(v, EXT);
            tex = light.appendTo(v.texture());
            int w = SparseBrickVolume.TEX_W, rows = tex.length / (w * 4), h = (rows - 1) * w * 4;
            minX = tex[h]; minY = tex[h + 1]; minZ = tex[h + 2]; sp = tex[h + 3];
            nx = (int) (tex[h + 4] + .5f); ny = (int) (tex[h + 5] + .5f); nz = (int) (tex[h + 6] + .5f); base = (int) (tex[h + 7] + .5f);
            nxp = (nx + 3) / 4;
        }
        float[] decode(float v) { float hi = (float) Math.floor(v / 4096f); return new float[]{hi / 4095f * 8f, (v - hi * 4096f) / 4095f * .16f}; }
        float[] row(int t, int i, float fx) {
            int lane = i & 3;
            float a = tex[t * 4 + lane], b = lane == 3 ? tex[(t + 1) * 4] : tex[t * 4 + lane + 1];
            float[] p = decode(a), q = decode(b);
            return new float[]{p[0] + (q[0] - p[0]) * fx, p[1] + (q[1] - p[1]) * fx};
        }
        float[] at(float x, float y, float z) {
            float gx = (x - minX) / sp, gy = (y - minY) / sp, gz = (z - minZ) / sp;
            int cx = Math.max(0, Math.min(nx - 2, (int) Math.floor(gx))), cy = Math.max(0, Math.min(ny - 2, (int) Math.floor(gy))), cz = Math.max(0, Math.min(nz - 2, (int) Math.floor(gz)));
            float fx = clamp(gx - cx), fy = clamp(gy - cy), fz = clamp(gz - cz);
            int t = base + (cz * ny + cy) * nxp + cx / 4;
            float[] v00 = row(t, cx, fx), v10 = row(t + nxp, cx, fx), v01 = row(t + ny * nxp, cx, fx), v11 = row(t + ny * nxp + nxp, cx, fx);
            float[] o = new float[2];
            for (int k = 0; k < 2; k++) {
                float a = v00[k] + (v10[k] - v00[k]) * fy, b = v01[k] + (v11[k] - v01[k]) * fy;
                o[k] = a + (b - a) * fz;
            }
            return o;
        }
    }

    static float clamp(float v) { return Math.max(0, Math.min(1, v)); }

    static Lit march(StormGeometryProgram p, SparseBrickVolume v, LightLattice light, double[] cam, int W, int H, String mode) {
        Bounds b = v.bounds();
        double[] o = {cam[0], cam[1], cam[2]};
        double[] fwd = norm(new double[]{cam[3] - cam[0], cam[4] - cam[1], cam[5] - cam[2]});
        double[] right = norm(cross(fwd, new double[]{0, 1, 0})), up = cross(right, fwd);
        double ty = Math.tan(Math.toRadians(cam[6]) / 2), tx = ty * W / H;
        float[] alpha = new float[W * H], rgb = new float[W * H];
        float step = 1.5f;
        IntStream.range(0, H).parallel().forEach(j -> {
            for (int i = 0; i < W; i++) {
                double sx = (2 * (i + .5) / W - 1) * tx, sy = (1 - 2 * (j + .5) / H) * ty;
                double[] d = norm(new double[]{fwd[0] + right[0] * sx + up[0] * sy, fwd[1] + right[1] * sx + up[1] * sy, fwd[2] + right[2] * sx + up[2] * sy});
                double[] sl = ConvectiveSystemT260Gallery.slab(o, d, b);
                if (sl == null) continue;
                double trans = 1, acc = 0;
                for (double t = sl[0] + step * .5; t < sl[1] && trans > 1e-3; t += step) {
                    float x = (float) (o[0] + d[0] * t), y = (float) (o[1] + d[1] * t), z = (float) (o[2] + d[2] * t);
                    float dens = v.density(x, y, z);
                    if (dens <= .0008f) continue;
                    double st = Math.exp(-dens * EXT * step);
                    float rad;
                    if (mode.equals("flat")) rad = 0;
                    else if (mode.equals("density")) rad = dens;
                    else {
                        float[] l = light.at(x, y, z);
                        float direct = (float) (Math.PI / (4 * Math.PI) * Math.exp(-l[0]));
                        rad = switch (mode) {
                            case "direct" -> 3.2f * direct;
                            case "ambient" -> 3.2f * l[1];
                            case "od" -> (float) (Math.log(1 + l[0]) / Math.log(65));
                            default -> 3.2f * (direct + l[1]);
                        };
                    }
                    acc += trans * rad * (1 - st);
                    trans *= st;
                }
                alpha[j * W + i] = (float) (1 - trans);
                rgb[j * W + i] = (float) acc;
            }
        });
        return new Lit(alpha, rgb);
    }

    static void render(Path out, String[] seeds, String[] arms, String[] views, double scale, String modes) throws Exception {
        Files.createDirectories(out);
        int W = (int) Math.round(1200 * scale), H = (int) Math.round(675 * scale);
        for (String ss : seeds) {
            long seed = Long.parseLong(ss);
            Recipe r = ConvectiveSystemT260.calvusD(seed);
            Bounds frame = new ConvectiveSystemT260.Program(r, null, 6, 12, .25f).bounds();
            for (String a : arms) {
                for (boolean iso : new boolean[]{false, true}) {
                    boolean any = false;
                    for (String v : views) any |= v.startsWith("iso_") == iso;
                    if (!any) continue;
                    long t0 = System.nanoTime();
                    var p = arm(r, a, iso);
                    var light = new LightLattice(p.volume());
                    System.out.printf(Locale.ROOT, "T2606_PREVIEW seed=%d arm=%s iso=%s bakeMs=%.0f signature=%s%n", seed, a, iso, (System.nanoTime() - t0) / 1e6, Long.toHexString(p.signature()));
                    for (String view : views) {
                        if (view.startsWith("iso_") != iso) continue;
                        double[] cam = camera(view, seed, frame);
                        for (String m : modes.split(",")) {
                            Lit img = march(p, p.volume(), light, cam, W, H, m.equals("flat") ? "flat" : m);
                            write(out.resolve(a + "_" + seed + "_" + view + "_" + m + ".png"), img, W, H, m);
                            if (m.equals("flat")) writeAlpha(out.resolve(a + "_" + seed + "_" + view + "_alpha.bin"), img.alpha());
                        }
                    }
                }
            }
        }
    }

    static void write(Path f, Lit img, int W, int H, String mode) throws Exception {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int br = BG >> 16 & 255, bg = BG >> 8 & 255, bb = BG & 255;
        for (int k = 0; k < img.alpha.length; k++) {
            float a = img.alpha[k], T = 1 - a;
            int r, g, b;
            if (mode.equals("flat")) {
                // Flat neutral: the T260.5 flat palette (uniform pale blue-grey) over the background.
                r = Math.round(0x9a * a + br * T); g = Math.round(0xa4 * a + bg * T); b = Math.round(0xb8 * a + bb * T);
            } else {
                float c = img.rgb[k];
                if (mode.equals("density") || mode.equals("od")) c = a > 0 ? c / Math.max(a, 1e-4f) * a : 0;
                int v = Math.min(255, Math.round(c * 255));
                r = Math.min(255, v + Math.round(br * T)); g = Math.min(255, v + Math.round(bg * T)); b = Math.min(255, v + Math.round(bb * T));
            }
            im.setRGB(k % W, k / W, r << 16 | g << 8 | b);
        }
        ImageIO.write(im, "png", f.toFile());
    }

    static void writeAlpha(Path f, float[] a) throws Exception {
        var buf = java.nio.ByteBuffer.allocate(a.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (float x : a) buf.putFloat(x);
        Files.write(f, buf.array());
    }

    static double[] norm(double[] v) { double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); return new double[]{v[0] / n, v[1] / n, v[2] / n}; }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
}
