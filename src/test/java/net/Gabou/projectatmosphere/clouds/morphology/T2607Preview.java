package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Recipe;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * T260.7 quick preview (Level 1): the frozen T260.6 B (its own class, untouched) against the T260.7 candidates, rendered
 * with the T260.6 CPU preview path (frozen T260.5 neutral light bake, exposure 3.2) at three cameras.
 * <pre>
 * parity seeds                                   B (T2606) signature == T2607 with every switch off
 * render out seeds arms views [scale]            arms: B (frozen T2606), V1, V2, V3, suffix P = persistent slots
 * grow out seed arms stages stepsPerStage views  same-seed growth stages (developStep), fixed domain and cameras
 * </pre>
 * Views: side, three_quarter (side rotated 45 deg about the system, raised), close (z4_side crown close-up), or any
 * T260.5 view name.
 */
public final class T2607Preview {
    public static void main(String[] args) throws Exception {
        if (args[0].startsWith("set:")) {
            for (String kv : args[0].substring(4).split(",")) {
                String[] p = kv.split("=");
                var f = ConvectiveVolumeT2607.class.getField(p[0]);
                if (f.getType() == boolean.class) f.setBoolean(null, Boolean.parseBoolean(p[1])); else f.setFloat(null, Float.parseFloat(p[1]));
                System.out.println("T2607_SET " + p[0] + "=" + p[1]);
            }
            args = Arrays.copyOfRange(args, 1, args.length);
        }
        switch (args[0]) {
            case "parity" -> parity(args[1].split(","));
            case "render" -> render(Path.of(args[1]), args[2].split(","), args[3].split(","), args[4].split(","), args.length > 5 ? Double.parseDouble(args[5]) : .5);
            case "grow" -> grow(Path.of(args[1]), Long.parseLong(args[2]), args[3].split(","), Integer.parseInt(args[4]), Integer.parseInt(args[5]), args[6].split(","),
                    args.length > 7 ? Double.parseDouble(args[7]) : .5);
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    static void parity(String[] seeds) {
        for (String ss : seeds) {
            long seed = Long.parseLong(ss);
            Recipe r = ConvectiveSystemT260.calvusD(seed);
            long t0 = System.nanoTime();
            var b = new ConvectiveVolumeT2606.Program(r, ConvectiveVolumeT2606.Arms.parse("B"), null, 6, 12, .25f);
            double bMs = (System.nanoTime() - t0) / 1e6;
            var a = ConvectiveVolumeT2607.configure("B");
            t0 = System.nanoTime();
            var c = new ConvectiveVolumeT2607.Program(r, a, null, 6, 12, .25f);
            double cMs = (System.nanoTime() - t0) / 1e6;
            System.out.printf(Locale.ROOT, "T2607_PARITY seed=%d frozenB=%s copyB=%s equal=%s bakeMs=%.0f/%.0f%n", seed, Long.toHexString(b.signature()),
                    Long.toHexString(c.signature()), b.signature() == c.signature(), bMs, cMs);
        }
    }

    /** Bakes one arm (B = the frozen T2606 class). */
    static StormGeometryProgram bake(Recipe r, String arm) {
        if (arm.equals("B")) return new ConvectiveVolumeT2606.Program(r, ConvectiveVolumeT2606.Arms.parse("B"), null, 6, 12, .25f);
        return new ConvectiveVolumeT2607.Program(r, ConvectiveVolumeT2607.configure(arm), null, 6, 12, .25f);
    }

    static SparseBrickVolume volume(StormGeometryProgram p) {
        return p instanceof ConvectiveVolumeT2606.Program b ? b.volume() : ((ConvectiveVolumeT2607.Program) p).volume();
    }

    /** T260.5 camera rules fitted to an arbitrary (transformed) recipe's silhouette extremes. */
    static double[] camera(String view, Recipe r, Bounds frame) {
        String v = view.equals("close") ? "z4_side" : view.equals("three_quarter") ? "side" : view;
        boolean zoom = v.startsWith("z4_");
        String pose = v.replace("z4_", "");
        float[] pts = net.Gabou.projectatmosphere.clouds.client.render.volumetric.ConvectiveSystemCapturePlan.extremes(r);
        double modifier = 1.1, saved = 110;
        int optionFov = zoom ? (int) saved : net.Gabou.projectatmosphere.clouds.client.render.volumetric.ConvectiveSystemCapturePlan.optionFov(pose, (int) saved);
        double effective = optionFov * modifier;
        double[] rel = net.Gabou.projectatmosphere.clouds.client.render.volumetric.ConvectiveSystemCapturePlan.pose(pose, frame, pts, effective, 1200 / 675.0);
        int shotFov = optionFov;
        if (zoom) {
            rel = net.Gabou.projectatmosphere.clouds.client.render.volumetric.ConvectiveSystemCapturePlan.zoomPose(pose, frame, rel, r);
            shotFov = net.Gabou.projectatmosphere.clouds.client.render.volumetric.StormAnatomyCapturePlan.zoomOptionFov(effective, modifier, net.Gabou.projectatmosphere.clouds.client.render.volumetric.StormAnatomyCapturePlan.ZOOM);
        }
        double[] c = Arrays.copyOf(rel, 7);
        c[6] = shotFov * modifier;
        if (view.equals("three_quarter")) {
            double dx = c[0] - c[3], dz = c[2] - c[5], ang = Math.toRadians(45), d = Math.hypot(dx, dz);
            double ex = c[3] + dx * Math.cos(ang) - dz * Math.sin(ang), ez = c[5] + dx * Math.sin(ang) + dz * Math.cos(ang);
            c[0] = ex; c[2] = ez; c[1] = c[1] + .18 * d;
        }
        return c;
    }

    static double[] camera(String view, long seed, Bounds frame) {
        if (view.equals("close")) return T2606Preview.camera("z4_side", seed, frame);
        if (!view.equals("three_quarter")) return T2606Preview.camera(view, seed, frame);
        double[] c = T2606Preview.camera("side", seed, frame);
        double dx = c[0] - c[3], dz = c[2] - c[5], ang = Math.toRadians(45), d = Math.hypot(dx, dz);
        double ex = c[3] + dx * Math.cos(ang) - dz * Math.sin(ang), ez = c[5] + dx * Math.sin(ang) + dz * Math.cos(ang);
        double[] o = c.clone();
        o[0] = ex; o[2] = ez; o[1] = c[1] + .18 * d;
        return o;
    }

    static void render(Path out, String[] seeds, String[] arms, String[] views, double scale) throws Exception {
        Files.createDirectories(out);
        int W = (int) Math.round(1200 * scale), H = (int) Math.round(675 * scale);
        for (String ss : seeds) {
            long seed = Long.parseLong(ss);
            Recipe d = ConvectiveSystemT260.calvusD(seed);
            Bounds dFrame = new ConvectiveSystemT260.Program(d, null, 6, 12, .25f).bounds();
            // PA_T2607_COMMON=1: every arm of the seed is framed on the union of all its systems (absolute scale).
            Recipe union = null;
            Bounds uFrame = null;
            if ("1".equals(System.getenv("PA_T2607_COMMON"))) {
                List<Recipe> all = new ArrayList<>();
                all.add(d);
                for (String a0 : arms) if (a0.indexOf('S') > 0) all.add(SystemDiversityT2607.apply(d, a0.contains("=") ? SystemDiversityT2607.Character.valueOf(a0.substring(a0.indexOf('=') + 1)) : SystemDiversityT2607.character(seed)));
                List<ConvectiveSystemT260.Component> cs = new ArrayList<>();
                List<ConvectiveSystemT260.Bubble> bs = new ArrayList<>();
                float x0 = 1e9f, y0 = 1e9f, z0 = 1e9f, x1 = -1e9f, y1 = -1e9f, z1 = -1e9f;
                for (Recipe q : all) {
                    int off = cs.size();
                    cs.addAll(q.components());
                    for (var b : q.bubbles()) bs.add(new ConvectiveSystemT260.Bubble(b.x(), b.y(), b.z(), b.rx(), b.ry(), b.rz(), b.comp() + off, b.tier()));
                    Bounds fb = new ConvectiveSystemT260.Program(q, null, 6, 12, .25f).bounds();
                    x0 = Math.min(x0, fb.minX()); y0 = Math.min(y0, fb.minY()); z0 = Math.min(z0, fb.minZ());
                    x1 = Math.max(x1, fb.maxX()); y1 = Math.max(y1, fb.maxY()); z1 = Math.max(z1, fb.maxZ());
                }
                union = new Recipe(d.kind(), d.seed(), cs, bs, d.baseY(), d.baseBlend(), d.linkBlend(), d.primaryHeight(), d.windX(), d.windZ());
                uFrame = new Bounds(x0, y0, z0, x1, y1, z1);
            }
            for (String a0 : arms) {
                // Suffix S (optionally S=CHARACTER): the T260.7 system-diversity transform, framed on its own extent.
                String a = a0;
                Recipe r = d;
                Bounds frame = dFrame;
                String ch = "";
                int si = a0.indexOf('S');
                if (si > 0) {
                    a = a0.substring(0, si);
                    var c = a0.contains("=") ? SystemDiversityT2607.Character.valueOf(a0.substring(a0.indexOf('=') + 1)) : SystemDiversityT2607.character(seed);
                    r = SystemDiversityT2607.apply(d, c);
                    frame = new ConvectiveSystemT260.Program(r, null, 6, 12, .25f).bounds();
                    ch = c.name();
                }
                long t0 = System.nanoTime();
                var p = bake(r, a);
                double bakeMs = (System.nanoTime() - t0) / 1e6;
                var v = volume(p);
                long t1 = System.nanoTime();
                var light = new T2606Preview.LightLattice(v);
                double lightMs = (System.nanoTime() - t1) / 1e6;
                Bounds vb = v.bounds();
                System.out.printf(Locale.ROOT, "T2607_PREVIEW seed=%d arm=%s character=%s bakeMs=%.0f lightMs=%.0f signature=%s textureMB=%.1f counts=%s cells=%d extentXZ=%.0fx%.0f height=%.0f%n", seed, a0, ch, bakeMs, lightMs,
                        Long.toHexString(p.signature()), v.stats().textureBytes() / 1048576.0, Arrays.toString(v.counts()), r.components().size(),
                        vb.maxX() - vb.minX(), vb.maxZ() - vb.minZ(), vb.maxY());
                String tag = a0.replace("=", "-");
                for (String view : views) {
                    double[] cam = union != null && !view.equals("close") ? camera(view, union, uFrame) : r == d ? camera(view, seed, frame) : camera(view, r, frame);
                    var img = T2606Preview.march(p, v, light, cam, W, H, "lit");
                    T2606Preview.write(out.resolve(tag + "_" + seed + "_" + view + ".png"), img, W, H, "lit");
                }
            }
        }
    }

    /**
     * Same-seed growth: stage s applies developStep s x stepsPerStage times to the frozen D recipe (the tallest cell's
     * upper third rises and widens); each stage is baked fresh on the final stage's domain with fixed cameras. Billow
     * persistence: share of the tier-1 domes of stage s that still exist at stage s + 1 (same position within 0.15 of
     * their radius, radius within 15 %), over the whole system and over the growing cell.
     */
    static void grow(Path out, long seed, String[] arms, int stages, int per, String[] views, double scale) throws Exception {
        Files.createDirectories(out);
        int W = (int) Math.round(1200 * scale), H = (int) Math.round(675 * scale);
        Recipe r0 = ConvectiveSystemT260.calvusD(seed);
        Bounds frame = new ConvectiveSystemT260.Program(r0, null, 6, 12, .25f).bounds();
        List<Recipe> rs = new ArrayList<>();
        Recipe cur = r0;
        rs.add(cur);
        for (int s = 1; s < stages; s++) {
            for (int k = 0; k < per; k++) cur = ConvectiveSystemT260.developStep(cur, (s - 1) * per + k + 1).recipe();
            rs.add(cur);
        }
        var template = new ConvectiveVolumeT2607.Structure(r0);
        for (String a : arms) {
            var arms7 = ConvectiveVolumeT2607.configure(a.equals("B") ? "B" : a);
            Bounds dom = ConvectiveVolumeT2607.domain(rs.get(stages - 1), arms7, null, 30);
            Bounds d0 = ConvectiveVolumeT2607.domain(r0, arms7, null, 30);
            dom = new Bounds(Math.min(dom.minX(), d0.minX()), Math.min(dom.minY(), d0.minY()), Math.min(dom.minZ(), d0.minZ()),
                    Math.max(dom.maxX(), d0.maxX()), Math.max(dom.maxY(), d0.maxY()) + 40, Math.max(dom.maxZ(), d0.maxZ()));
            List<List<float[]>> prev = null;
            float[][] prevImg = null;
            for (int s = 0; s < stages; s++) {
                var st = new ConvectiveVolumeT2607.Structure(rs.get(s), template);
                long t0 = System.nanoTime();
                var p = new ConvectiveVolumeT2607.Program(st, arms7, dom, 6, 12, .25f);
                double bakeMs = (System.nanoTime() - t0) / 1e6;
                var light = new T2606Preview.LightLattice(p.volume());
                var cols = ConvectiveVolumeT2607.columns(st);
                var pops = ConvectiveVolumeT2607.population(rs.get(s), cols);
                String persist = "";
                if (prev != null) persist = persistence(prev, pops, st);
                prev = pops;
                float[][] imgs = new float[views.length][];
                StringBuilder diff = new StringBuilder();
                for (int vi = 0; vi < views.length; vi++) {
                    var img = T2606Preview.march(p, p.volume(), light, camera(views[vi], seed, frame), W, H, "lit");
                    T2606Preview.write(out.resolve(a + "_" + seed + "_stage" + s + "_" + views[vi] + ".png"), img, W, H, "lit");
                    imgs[vi] = img.rgb();
                    if (prevImg != null) {
                        double sum = 0; int n = 0, big = 0;
                        for (int k = 0; k < img.rgb().length; k++) {
                            if (img.alpha()[k] < .5f) continue;
                            double dlt = Math.abs(img.rgb()[k] - prevImg[vi][k]) * 255;
                            sum += dlt; n++; if (dlt > 16) big++;
                        }
                        diff.append(String.format(Locale.ROOT, " %s:meanDiff=%.1f,over16=%.1f%%", views[vi], sum / Math.max(1, n), 100.0 * big / Math.max(1, n)));
                    }
                }
                prevImg = imgs;
                int growing = target(rs.get(s));
                System.out.printf(Locale.ROOT, "T2607_GROW seed=%d arm=%s stage=%d top=%.0f bakeMs=%.0f%s%s%n", seed, a, s, rs.get(s).components().get(growing).top(), bakeMs, persist, diff);
            }
        }
    }

    static int target(Recipe r) {
        int t = 0;
        for (int c = 1; c < r.components().size(); c++) if (r.components().get(c).top() > r.components().get(t).top()) t = c;
        return t;
    }

    static String persistence(List<List<float[]>> a, List<List<float[]>> b, ConvectiveVolumeT2607.Structure st) {
        int keep = 0, total = 0, keepG = 0, totalG = 0;
        int growing = target(st.recipe);
        for (int t = 0; t < a.size(); t++) {
            boolean g = st.turretComp(t) == growing;
            for (float[] p : a.get(t)) {
                if (p[8] != 1) continue;
                boolean found = false;
                for (float[] q : b.get(t)) {
                    if (q[8] != 1) continue;
                    float dx = p[0] - q[0], dy = p[1] - q[1], dz = p[2] - q[2], tol = .15f * p[7];
                    if (dx * dx + dy * dy + dz * dz < tol * tol * 4 * 4 && Math.abs(q[7] - p[7]) < .15f * p[7]) {
                        // Growth moves domes with their column: accept a displacement up to 4 x tol (the rise per stage).
                        found = true; break;
                    }
                }
                total++; if (found) keep++;
                if (g) { totalG++; if (found) keepG++; }
            }
        }
        return String.format(Locale.ROOT, " persist=%.1f%% (%d/%d) growingCell=%.1f%% (%d/%d)", 100.0 * keep / Math.max(1, total), keep, total, 100.0 * keepG / Math.max(1, totalG), keepG, totalG);
    }
}
