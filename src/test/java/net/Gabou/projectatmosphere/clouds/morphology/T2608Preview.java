package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.CellSpec;
import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.Scenario;
import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.SystemState;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * T260.8 quick preview: the three controlled scenarios rendered with the T260.6 CPU preview path (frozen T260.5 neutral
 * light bake, exposure 3.2). Every scenario is baked on ONE fixed domain (union of all requested times) with fixed
 * cameras, so apparent growth is never camera motion.
 * <pre>
 * stages out scenario seed times views [scale]     times "0,10,20": chronological stages
 * anim   out scenario seed t0 t1 dt view [scale]   frame sequence + billow continuity (pops) and pixel change
 * smoke  scenario seed t                           determinism + translation invariance of the field
 * </pre>
 * Scenarios: single (S1 one cell), pair (S2 two cells merging), three (S3 three-cell organisation).
 * Views: three_quarter, side.
 */
public final class T2608Preview {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "stages" -> stages(Path.of(args[1]), args[2], Long.parseLong(args[3]), floats(args[4]), args[5].split(","), args.length > 6 ? Double.parseDouble(args[6]) : .8);
            case "anim" -> anim(Path.of(args[1]), args[2], Long.parseLong(args[3]), Float.parseFloat(args[4]), Float.parseFloat(args[5]), Float.parseFloat(args[6]), args[7],
                    args.length > 8 ? Double.parseDouble(args[8]) : .4);
            case "smoke" -> smoke(args[1], Long.parseLong(args[2]), Float.parseFloat(args[3]));
            case "compare" -> compare(Path.of(args[1]), args[2], Long.parseLong(args[3]), floats(args[4]), args[5].split(","), args[6].split(","), args.length > 7 ? Double.parseDouble(args[7]) : .6);
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    static float[] floats(String s) { String[] p = s.split(","); float[] f = new float[p.length]; for (int i = 0; i < p.length; i++) f[i] = Float.parseFloat(p[i]); return f; }

    // ---------------------------------------------------------------- scenarios (controlled growth parameters)

    static Scenario scenario(String name, long seed) {
        Random g = new Random(seed);
        Scenario s = new Scenario(name, seed);
        switch (name) {
            case "single" -> {
                CellSpec a = s.add("A");
                a.birth = -3; a.hPot = 1080 + 80 * g.nextFloat(); a.rPot = 340 + 30 * g.nextFloat(); a.tauH = 20; a.tauR = 22; a.vigor = .05f;
            }
            case "pair" -> {
                CellSpec a = s.add("A"), b = s.add("B");
                a.birth = -3; a.x0 = -560; a.z0 = -60; a.vx = 6; a.hPot = 980 + 60 * g.nextFloat(); a.rPot = 410 + 30 * g.nextFloat(); a.tauH = 20; a.tauR = 17; a.vigor = .05f;
                b.birth = -8; b.x0 = 520; b.z0 = 70; b.vx = -5; b.hPot = 540 + 60 * g.nextFloat(); b.rPot = 380 + 25 * g.nextFloat(); b.tauH = 14; b.tauR = 18; b.vigor = 0;
            }
            case "three" -> {
                // Roles come from each cell's own potentials (deep / broad / weak); placement and timing from the seed.
                CellSpec d = s.add("D"), w = s.add("W"), k = s.add("S");
                float th1 = (float) (g.nextFloat() * Math.PI * 2), th2 = th1 + (float) Math.toRadians(100 + 60 * g.nextFloat());
                float r1 = 760 + 80 * g.nextFloat(), r2 = 820 + 100 * g.nextFloat();
                d.birth = -2 - 3 * g.nextFloat(); d.hPot = 950 + 150 * g.nextFloat(); d.rPot = 400 + 60 * g.nextFloat(); d.tauH = 18 + 4 * g.nextFloat(); d.tauR = 17; d.vigor = .06f;
                w.birth = -10 - 4 * g.nextFloat(); w.x0 = r1 * (float) Math.cos(th1); w.z0 = r1 * (float) Math.sin(th1);
                w.hPot = 380 + 100 * g.nextFloat(); w.rPot = 450 + 60 * g.nextFloat(); w.tauH = 12; w.tauR = 14; w.vigor = -.02f;
                k.birth = -6 - 4 * g.nextFloat(); k.x0 = r2 * (float) Math.cos(th2); k.z0 = r2 * (float) Math.sin(th2);
                k.hPot = 260 + 80 * g.nextFloat(); k.rPot = 200 + 40 * g.nextFloat(); k.tauH = 10; k.tauR = 12; k.vigor = 0;
                k.decay = 18 + 8 * g.nextFloat(); k.tauD = 14;
                // Weak low-level convergence toward the deep cell.
                for (CellSpec c : new CellSpec[]{w, k}) { float n = (float) Math.hypot(c.x0, c.z0); c.vx = -3.2f * c.x0 / n; c.vz = -3.2f * c.z0 / n; }
            }
            case "three_free" -> {
                // No assigned roles: every cell draws its potential, size, timing and fate from the same distribution
                // (deep cells rarer); dominance, spreading and decay emerge from the draws and the interactions.
                float th = (float) (g.nextFloat() * Math.PI * 2);
                for (int i = 0; i < 3; i++) {
                    CellSpec c = s.add("C" + (i + 1));
                    float q = g.nextFloat(), u = g.nextFloat(), w = g.nextFloat(), b = g.nextFloat(), f = g.nextFloat(), fd = g.nextFloat();
                    float ang = th + (float) Math.toRadians(i * (105 + 40 * g.nextFloat())), rad = i == 0 ? 0 : 650 + 300 * g.nextFloat();
                    c.x0 = rad * (float) Math.cos(ang); c.z0 = rad * (float) Math.sin(ang);
                    c.hPot = 280 + 870 * (float) Math.pow(q, 1.6);
                    c.rPot = 220 + 240 * u + 120 * (1 - q);
                    c.tauH = 10 + 12 * q; c.tauR = 13 + 8 * w;
                    c.birth = -2 - 10 * b;
                    c.vigor = .12f * (q - .4f);
                    if (c.hPot < 450 && f < .6f) { c.decay = 16 + 14 * fd; c.tauD = 14; }
                    if (rad > 0) { c.vx = -3f * c.x0 / rad; c.vz = -3f * c.z0 / rad; }
                }
            }
            case "system" -> { return SystemGrowthT2609.build(seed, 46); } // T260.9 system-scale growth
            case "deep" -> { return VerticalGrowthT26010.build(seed, 46); } // T260.10 vertical development
            default -> throw new IllegalArgumentException(name);
        }
        return s.prepare();
    }

    // ---------------------------------------------------------------- framing

    static Bounds union(Scenario s, float[] times) {
        float[] b = {1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f};
        for (float t : times) {
            Bounds q = s.at(t).bounds();
            b[0] = Math.min(b[0], q.minX()); b[1] = Math.min(b[1], q.minY()); b[2] = Math.min(b[2], q.minZ());
            b[3] = Math.max(b[3], q.maxX()); b[4] = Math.max(b[4], q.maxY()); b[5] = Math.max(b[5], q.maxZ());
        }
        return new Bounds(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    /** Occupied extent of baked volumes (density > 0.01 on a 12-block grid): the framing box. */
    static Bounds occupied(java.util.List<SparseBrickVolume> vs) {
        float[] b = {1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f};
        for (SparseBrickVolume v : vs) {
            Bounds d = v.bounds();
            int nx = (int) ((d.maxX() - d.minX()) / 12), ny = (int) ((d.maxY() - d.minY()) / 12), nz = (int) ((d.maxZ() - d.minZ()) / 12);
            float[][] part = new float[nz][];
            java.util.stream.IntStream.range(0, nz).parallel().forEach(k -> {
                float[] q = {1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f};
                for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                    float x = d.minX() + i * 12, y = d.minY() + j * 12, z = d.minZ() + k * 12;
                    if (v.density(x, y, z) <= .01f) continue;
                    q[0] = Math.min(q[0], x); q[1] = Math.min(q[1], y); q[2] = Math.min(q[2], z);
                    q[3] = Math.max(q[3], x); q[4] = Math.max(q[4], y); q[5] = Math.max(q[5], z);
                }
                part[k] = q;
            });
            for (float[] q : part) for (int k = 0; k < 3; k++) { b[k] = Math.min(b[k], q[k]); b[k + 3] = Math.max(b[k + 3], q[k + 3]); }
        }
        return new Bounds(b[0] - 12, b[1] - 12, b[2] - 12, b[3] + 12, b[4] + 12, b[5] + 12);
    }

    static Bounds domain(Bounds c) {
        float m = 60;
        return new Bounds(c.minX() - m, PersistentConvectionT2608.BASE_Y - 40, c.minZ() - m, c.maxX() + m, c.maxY() + m, c.maxZ() + m);
    }

    /** Fixed camera fitted to the content box: {eye xyz, target xyz, vertical fov}. side = looking along +z. */
    static double[] camera(String view, Bounds c, int W, int H) {
        // Eye on the sunlit side (the neutral sun shines from -x / +z): side looks along -z, three-quarter is rotated 35 deg.
        double az = Math.toRadians(view.equals("side") ? 0 : 35), el = Math.toRadians(view.equals("side") ? 4 : 11), fov = 42;
        double tx = (c.minX() + c.maxX()) * .5, tz = (c.minZ() + c.maxZ()) * .5, ty = c.minY() + (c.maxY() - c.minY()) * .42;
        double dx = -Math.sin(az) * Math.cos(el), dy = Math.sin(el), dz = Math.cos(az) * Math.cos(el); // eye direction from target
        double tyH = Math.tan(Math.toRadians(fov) / 2), txH = tyH * W / H;
        double[] fwd = T2606Preview.norm(new double[]{-dx, -dy, -dz});
        double[] right = T2606Preview.norm(T2606Preview.cross(fwd, new double[]{0, 1, 0})), up = T2606Preview.cross(right, fwd);
        double dist = 200;
        for (int it = 0; it < 400; it++) {
            boolean ok = true;
            double ex = tx + dx * dist, ey = ty + dy * dist, ez = tz + dz * dist;
            for (int k = 0; k < 8 && ok; k++) {
                double px = (k & 1) == 0 ? c.minX() : c.maxX(), py = (k & 2) == 0 ? c.minY() : c.maxY(), pz = (k & 4) == 0 ? c.minZ() : c.maxZ();
                double vx = px - ex, vy = py - ey, vz = pz - ez;
                double f = vx * fwd[0] + vy * fwd[1] + vz * fwd[2];
                if (f <= 1) { ok = false; break; }
                double sx = (vx * right[0] + vy * right[1] + vz * right[2]) / f, sy = (vx * up[0] + vy * up[1] + vz * up[2]) / f;
                if (Math.abs(sx) > txH * .94 || Math.abs(sy) > tyH * .92) ok = false;
            }
            if (ok) break;
            dist *= 1.03;
        }
        return new double[]{tx + dx * dist, ty + dy * dist, tz + dz * dist, tx, ty, tz, fov};
    }

    // ---------------------------------------------------------------- commands

    static void stages(Path out, String name, long seed, float[] times, String[] views, double scale) throws Exception {
        Files.createDirectories(out);
        int W = (int) Math.round(1200 * scale), H = (int) Math.round(675 * scale);
        Scenario s = scenario(name, seed);
        Bounds content = union(s, times), dom = domain(content);
        java.util.List<SparseBrickVolume> vols = new java.util.ArrayList<>();
        java.util.List<String> logs = new java.util.ArrayList<>();
        for (int si = 0; si < times.length; si++) {
            float t = times[si];
            long t0 = System.nanoTime();
            SystemState st = s.at(t);
            double stateMs = (System.nanoTime() - t0) / 1e6;
            long t1 = System.nanoTime();
            SparseBrickVolume v = PersistentConvectionT2608.bake(st, dom);
            double bakeMs = (System.nanoTime() - t1) / 1e6;
            vols.add(v);
            logs.add(String.format(Locale.ROOT, "T2608_STAGE scenario=%s seed=%d stage=%d t=%.1f stateMs=%.0f bakeMs=%.0f textureMB=%.1f signature=%s billows=%d%s", name, seed, si, t,
                    stateMs, bakeMs, v.stats().textureBytes() / 1048576.0, Long.toHexString(v.signature()), st.billowCount(), st.describe()));
        }
        Bounds frame = FRAME != null ? FRAME : occupied(vols);
        System.out.printf(Locale.ROOT, "T2608_FRAME_BOX %.0f %.0f %.0f %.0f %.0f %.0f%n", frame.minX(), frame.minY(), frame.minZ(), frame.maxX(), frame.maxY(), frame.maxZ());
        for (int si = 0; si < times.length; si++) {
            long t2 = System.nanoTime();
            var light = new T2606Preview.LightLattice(vols.get(si));
            double lightMs = (System.nanoTime() - t2) / 1e6;
            System.out.println(logs.get(si) + String.format(Locale.ROOT, " lightMs=%.0f", lightMs));
            for (String view : views) {
                var img = T2606Preview.march(null, vols.get(si), light, camera(view, frame, W, H), W, H, "lit");
                T2606Preview.write(out.resolve(name + "_" + seed + "_stage" + si + "_" + view + ".png"), img, W, H, "lit");
            }
        }
    }

    /**
     * Review iteration: the same scenario / seed / stages for several geometry variants (0 = reviewed T260.8, 1 = C1,
     * 2 = C2), baked on ONE domain and framed by ONE camera per view (union of every variant's occupancy), so the
     * columns compare directly. Views: three_quarter, side, close (crown close-up around the highest occupied point).
     */
    static void compare(Path out, String name, long seed, float[] times, String[] variants, String[] views, double scale) throws Exception {
        Files.createDirectories(out);
        int W = (int) Math.round(1200 * scale), H = (int) Math.round(675 * scale);
        int[] vs = new int[variants.length];
        for (int i = 0; i < vs.length; i++) vs[i] = Integer.parseInt(variants[i]);
        float[] b = {1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f};
        for (int v : vs) {
            PersistentConvectionT2608.VARIANT = v;
            Bounds u = union(scenario(name, seed), times);
            b[0] = Math.min(b[0], u.minX()); b[1] = Math.min(b[1], u.minY()); b[2] = Math.min(b[2], u.minZ());
            b[3] = Math.max(b[3], u.maxX()); b[4] = Math.max(b[4], u.maxY()); b[5] = Math.max(b[5], u.maxZ());
        }
        Bounds dom = domain(new Bounds(b[0], b[1], b[2], b[3], b[4], b[5]));
        java.util.List<SparseBrickVolume> vols = new java.util.ArrayList<>();
        java.util.List<String> logs = new java.util.ArrayList<>();
        for (int v : vs) {
            PersistentConvectionT2608.VARIANT = v;
            Scenario s = scenario(name, seed);
            for (int si = 0; si < times.length; si++) {
                long t0 = System.nanoTime();
                SystemState st = s.at(times[si]);
                double stateMs = (System.nanoTime() - t0) / 1e6;
                long t1 = System.nanoTime();
                SparseBrickVolume vol = PersistentConvectionT2608.bake(st, dom);
                double bakeMs = (System.nanoTime() - t1) / 1e6;
                vols.add(vol);
                logs.add(String.format(Locale.ROOT, "T2608_CMP scenario=%s seed=%d variant=%d stage=%d t=%.1f cells=%d stateMs=%.0f bakeMs=%.0f textureMB=%.1f signature=%s billows=%d%s", name, seed, v, si,
                        times[si], st.geoms.size(), stateMs, bakeMs, vol.stats().textureBytes() / 1048576.0, Long.toHexString(vol.signature()), st.billowCount(), st.describe()));
            }
        }
        PersistentConvectionT2608.VARIANT = 0;
        Bounds frame = occupied(vols);
        float[] top = highest(vols.subList(0, vols.size()));
        float cr = 460;
        Bounds close = new Bounds(top[0] - cr, Math.max(frame.minY(), top[1] - 900), top[2] - cr, top[0] + cr, top[1] + 24, top[2] + cr);
        // Tower-to-crown transition: the upper trunk and the crown base below the highest point, side-on.
        Bounds trans = new Bounds(top[0] - 380, Math.max(frame.minY(), top[1] - 820), top[2] - 380, top[0] + 380, top[1] + 24, top[2] + 380);
        System.out.printf(Locale.ROOT, "T2608_FRAME_BOX %.0f %.0f %.0f %.0f %.0f %.0f close@%.0f,%.0f,%.0f%n", frame.minX(), frame.minY(), frame.minZ(), frame.maxX(), frame.maxY(), frame.maxZ(), top[0], top[1], top[2]);
        int n = 0;
        for (int v : vs) for (int si = 0; si < times.length; si++, n++) {
            SparseBrickVolume vol = vols.get(n);
            var light = new T2606Preview.LightLattice(vol);
            System.out.println(logs.get(n));
            for (String view : views) {
                double[] cam = view.equals("close") ? camera("three_quarter", close, W, H)
                        : view.equals("transition") ? outside(camera("side", trans, W, H), frame) : camera(view, frame, W, H);
                var img = T2606Preview.march(null, vol, light, cam, W, H, "lit");
                T2606Preview.write(out.resolve(name + "_" + seed + "_v" + v + "_stage" + si + "_" + view + ".png"), img, W, H, "lit");
            }
        }
    }

    /** Moves a camera back along its view line until the eye is outside the box, narrowing the fov to keep the framing. */
    static double[] outside(double[] cam, Bounds b) {
        double dx = cam[0] - cam[3], dy = cam[1] - cam[4], dz = cam[2] - cam[5], d0 = Math.sqrt(dx * dx + dy * dy + dz * dz), d = d0;
        for (int i = 0; i < 400; i++) {
            double ex = cam[3] + dx / d0 * d, ey = cam[4] + dy / d0 * d, ez = cam[5] + dz / d0 * d;
            if (ex < b.minX() - 20 || ex > b.maxX() + 20 || ey > b.maxY() + 20 || ez < b.minZ() - 20 || ez > b.maxZ() + 20) break;
            d *= 1.05;
        }
        double fov = Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(cam[6]) / 2) * d0 / d));
        return new double[]{cam[3] + dx / d0 * d, cam[4] + dy / d0 * d, cam[5] + dz / d0 * d, cam[3], cam[4], cam[5], fov};
    }

    /** Highest occupied point (density > 0.01, 12-block grid) over the volumes: {x, y, z}. */
    static float[] highest(java.util.List<SparseBrickVolume> vs) {
        float[] best = {0, -1e9f, 0};
        for (SparseBrickVolume v : vs) {
            Bounds d = v.bounds();
            int nx = (int) ((d.maxX() - d.minX()) / 12), ny = (int) ((d.maxY() - d.minY()) / 12), nz = (int) ((d.maxZ() - d.minZ()) / 12);
            for (int j = ny - 1; j >= 0; j--) {
                float y = d.minY() + j * 12;
                if (y <= best[1]) break;
                boolean hit = false;
                for (int k = 0; k < nz && !hit; k++) for (int i = 0; i < nx && !hit; i++) {
                    float x = d.minX() + i * 12, z = d.minZ() + k * 12;
                    if (v.density(x, y, z) > .01f) { best = new float[]{x, y, z}; hit = true; }
                }
                if (hit) break;
            }
        }
        return best;
    }

    /** Optional fixed framing box (env PA_T2608_FRAME="x0,y0,z0,x1,y1,z1"), e.g. to compare seeds at one absolute scale. */
    static final Bounds FRAME = System.getenv("PA_T2608_FRAME") == null ? null : box(floats(System.getenv("PA_T2608_FRAME")));
    static Bounds box(float[] f) { return new Bounds(f[0], f[1], f[2], f[3], f[4], f[5]); }

    static void anim(Path out, String name, long seed, float ta, float tb, float dt, String view, double scale) throws Exception {
        Files.createDirectories(out);
        int W = (int) Math.round(1200 * scale), H = (int) Math.round(675 * scale);
        Scenario s = scenario(name, seed);
        int n = Math.round((tb - ta) / dt) + 1;
        float[] times = new float[n];
        for (int i = 0; i < n; i++) times[i] = ta + i * dt;
        Bounds content = union(s, times), dom = domain(content);
        java.util.List<SparseBrickVolume> vols = new java.util.ArrayList<>();
        java.util.List<SystemState> states = new java.util.ArrayList<>();
        double bakeSum = 0;
        for (float t : times) {
            SystemState st = s.at(t);
            long t1 = System.nanoTime();
            vols.add(PersistentConvectionT2608.bake(st, dom));
            bakeSum += (System.nanoTime() - t1) / 1e6;
            states.add(st);
        }
        java.util.List<SparseBrickVolume> some = new java.util.ArrayList<>();
        for (int i = 0; i < n; i += 4) some.add(vols.get(i));
        some.add(vols.get(n - 1));
        double[] cam = camera(view, FRAME != null ? FRAME : occupied(some), W, H);
        Map<Long, float[]> prev = null;
        float[] prevImg = null;
        int pops = 0, jumps = 0;
        for (int i = 0; i < n; i++) {
            float t = times[i];
            SystemState st = states.get(i);
            SparseBrickVolume v = vols.get(i);
            double bakeMs = 0;
            var light = new T2606Preview.LightLattice(v);
            var img = T2606Preview.march(null, v, light, cam, W, H, "lit");
            T2606Preview.write(out.resolve(String.format(Locale.ROOT, "%s_%d_%s_f%03d.png", name, seed, view, i)), img, W, H, "lit");
            // Billow continuity: identity -> {x, y, z, rt, cell drift x, z}.
            Map<Long, float[]> cur = new HashMap<>();
            for (var g : st.geoms) for (float[] b : g.info) cur.put(g.spec.id * 1_000_003L + (long) b[0], new float[]{b[1], b[2], b[3], b[4], g.st[4], g.st[5]});
            int appear = 0, vanish = 0, jump = 0;
            float maxMove = 0;
            if (prev != null) {
                for (var e : cur.entrySet()) {
                    float[] c = e.getValue(), p = prev.get(e.getKey());
                    if (p == null) { if (c[3] > 12) appear++; continue; }
                    float mx = (c[0] - c[4]) - (p[0] - p[4]), my = c[1] - p[1], mz = (c[2] - c[5]) - (p[2] - p[5]);
                    float mv = (float) Math.sqrt(mx * mx + my * my + mz * mz) / Math.max(dt, 1e-3f);
                    float lim = Math.max(10, .4f * c[3]);
                    maxMove = Math.max(maxMove, mv / Math.max(1, c[3]));
                    if (Math.abs(c[3] - p[3]) > Math.max(8, .35f * p[3]) || mv * dt > lim) jump++;
                }
                for (var e : prev.entrySet()) if (!cur.containsKey(e.getKey()) && e.getValue()[3] > 12) vanish++;
            }
            pops += appear + vanish;
            jumps += jump;
            prev = cur;
            String diff = "";
            if (prevImg != null) {
                double sum = 0; int cnt = 0, big = 0;
                for (int k = 0; k < img.rgb().length; k++) {
                    if (img.alpha()[k] < .5f) continue;
                    double d = Math.abs(img.rgb()[k] - prevImg[k]) * 255;
                    sum += d; cnt++; if (d > 16) big++;
                }
                diff = String.format(Locale.ROOT, " meanDiff=%.1f over16=%.1f%%", sum / Math.max(1, cnt), 100.0 * big / Math.max(1, cnt));
            }
            prevImg = img.rgb();
            System.out.printf(Locale.ROOT, "T2608_FRAME scenario=%s seed=%d frame=%d t=%.2f bakeMs=%.0f(see mean) billows=%d appearBig=%d vanishBig=%d jumps=%d maxRelMove=%.2f%s%n", name, seed, i, t, bakeMs,
                    st.billowCount(), appear, vanish, jump, maxMove, diff);
        }
        System.out.printf(Locale.ROOT, "T2608_ANIM scenario=%s seed=%d frames=%d dt=%.2f pops=%d jumps=%d meanBakeMs=%.0f%n", name, seed, n, dt, pops, jumps, bakeSum / n);
    }

    /** Determinism (two bakes, same signature) and translation invariance (field shifted with every cell). */
    static void smoke(String name, long seed, float t) {
        if (System.getenv("PA_T2608_VARIANT") != null) PersistentConvectionT2608.VARIANT = Integer.parseInt(System.getenv("PA_T2608_VARIANT"));
        Scenario s = scenario(name, seed);
        SystemState st = s.at(t);
        Bounds content = st.bounds(), dom = domain(content);
        SparseBrickVolume va = PersistentConvectionT2608.bake(st, dom);
        long a = va.signature(), b = PersistentConvectionT2608.bake(scenario(name, seed).at(t), dom).signature();
        int badSkips = va.auditCoarseSkips(st.field());
        // Translation: every cell origin moved by (dx, dz); the field must be the same at the moved point.
        float dx = 1234.5f, dz = -777.25f;
        Scenario moved = scenario(name, seed);
        Scenario m2 = new Scenario(name, seed);
        m2.tEnd = moved.tEnd;
        m2.daughterDecay = moved.daughterDecay;
        for (CellSpec c : moved.cells) {
            if (c.daughter) continue;
            CellSpec k = m2.add(c.name);
            copy(c, k);
            k.x0 += dx; k.z0 += dz;
        }
        m2.prepare();
        var f0 = st.field();
        var f1 = m2.at(t).field();
        Random r = new Random(7);
        float maxDiff = 0;
        int near = 0;
        for (int i = 0; i < 40000; i++) {
            float x = content.minX() + (content.maxX() - content.minX()) * r.nextFloat(), y = content.minY() + (content.maxY() - content.minY()) * r.nextFloat(),
                    z = content.minZ() + (content.maxZ() - content.minZ()) * r.nextFloat();
            float d0 = f0.distance(x, y, z);
            if (Math.abs(d0) > 30) continue;
            near++;
            maxDiff = Math.max(maxDiff, Math.abs(d0 - f1.distance(x + dx, y, z + dz)));
        }
        System.out.printf(Locale.ROOT, "T2608_SMOKE scenario=%s variant=%d seed=%d t=%.1f deterministic=%s (%s) translatedBandSamples=%d maxAbsDiff=%.4f blocks badCoarseSkips=%d%n", name,
                PersistentConvectionT2608.VARIANT, seed, t, a == b, Long.toHexString(a), near, maxDiff, badSkips);
    }

    /** Copies the controlled parameters (identity differs only by the scenario-assigned id, which is the same here). */
    static void copy(CellSpec c, CellSpec k) {
        k.x0 = c.x0; k.z0 = c.z0; k.vx = c.vx; k.vz = c.vz; k.birth = c.birth; k.h0 = c.h0; k.r0 = c.r0; k.hPot = c.hPot; k.rPot = c.rPot;
        k.tauH = c.tauH; k.tauR = c.tauR; k.powH = c.powH; k.decay = c.decay; k.tauD = c.tauD; k.vigor = c.vigor;
        k.envLeanAz = c.envLeanAz; k.envLean = c.envLean; k.dissolve = c.dissolve;
        k.deep = c.deep; k.calH = c.calH; k.calW = c.calW; k.hCeil = c.hCeil; k.kScale = c.kScale; k.plumeDn = c.plumeDn; k.plumeL = c.plumeL;
    }
}
