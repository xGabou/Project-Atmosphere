package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.Scenario;
import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.SystemState;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * T260.9 quick preview at CONSISTENT WORLD SCALE: C4 (T260.8 three_free, VARIANT 4) against the T260.9 system-scale
 * model (VARIANT 4 cells), same seeds and stages. Per view every system is rendered with the SAME camera distance and
 * field of view (fitted once to the largest system), centred on its own extent, so differences in height and width are
 * real on screen. The gameplay view is a player on the ground at a fixed distance with a fixed lens.
 * <pre>
 * world out seeds times models views [scale]   models: c4, t9; views: side, three_quarter, gameplay
 * </pre>
 */
public final class T2609Preview {
    /** Gameplay view: player eye height below the cloud base, horizontal distance from the system centre, vertical fov. */
    static final double GAME_EYE = -160, GAME_DIST = 6000, GAME_FOV = 70;

    public static void main(String[] args) throws Exception {
        if (args[0].equals("plan")) { for (String sd : args[1].split(",")) plan(Long.parseLong(sd)); return; }
        if (args[0].equals("lip")) { lip(Long.parseLong(args[1]), Float.parseFloat(args[2])); return; }
        if (args[0].equals("skips")) { skips(Long.parseLong(args[1]), Float.parseFloat(args[2])); return; }
        if (!args[0].equals("world")) throw new IllegalArgumentException(args[0]);
        world(Path.of(args[1]), args[2].split(","), T2608Preview.floats(args[3]), args[4].split(","), args[5].split(","), args.length > 6 ? Double.parseDouble(args[6]) : .6);
    }

    /** Diagnostic: largest |f(a) - f(b)| / |a - b| on 6-block pairs near the surface, with the nearest cells. */
    static void lip(long seed, float t) {
        Scenario s = scenario("t9", seed);
        SystemState st = s.at(t);
        var f = st.field();
        Bounds b = st.bounds();
        java.util.Random r = new java.util.Random(3);
        float worst = 0; float[] at = null;
        int over = 0, n = 0;
        for (int i = 0; i < 400000; i++) {
            float x = b.minX() + (b.maxX() - b.minX()) * r.nextFloat(), y = b.minY() + (b.maxY() - b.minY()) * r.nextFloat(), z = b.minZ() + (b.maxZ() - b.minZ()) * r.nextFloat();
            float d0 = f.distance(x, y, z);
            if (Math.abs(d0) > 120) continue;
            float dx = (r.nextFloat() - .5f) * 12, dy = (r.nextFloat() - .5f) * 12, dz = (r.nextFloat() - .5f) * 12, l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float g = Math.abs(f.distance(x + dx, y + dy, z + dz) - d0) / l;
            n++;
            if (g > 2.5f) over++;
            if (g > worst) { worst = g; at = new float[]{x, y, z, d0}; }
        }
        System.out.printf(Locale.ROOT, "T2609_LIP seed=%d t=%.0f pairs=%d over2.5=%d worst=%.2f at %.0f %.0f %.0f d=%.1f%n", seed, t, n, over, worst, at[0], at[1], at[2], at[3]);
        for (var g : st.geoms) {
            float dd = g.distance(at[0], at[1], at[2], 400);
            if (dd < 200) System.out.printf(Locale.ROOT, "  near %s dd=%.1f erode=%.2f emergeOff=%.1f top=%.0f R=%.0f daughter=%s%n", g.spec.name, dd, g.erode, g.emergeOff, g.st[0], g.st[1], g.spec.daughter);
        }
    }

    /** Diagnostic: coarse cells whose centre says EMPTY (Lipschitz 2.5) although a sample is inside the band. */
    static void skips(long seed, float t) {
        Scenario s = scenario("t9", seed);
        SystemState st = s.at(t);
        var f = st.field();
        Bounds dom = T2608Preview.domain(st.bounds());
        float cell = 48, half = 24, thr = 2.5f * half * (float) Math.sqrt(3) + 6;
        int gx = (int) Math.ceil((dom.maxX() - dom.minX()) / cell), gy = (int) Math.ceil((dom.maxY() - dom.minY()) / cell), gz = (int) Math.ceil((dom.maxZ() - dom.minZ()) / cell);
        float mx = dom.minX() - (gx * cell - (dom.maxX() - dom.minX())) * .5f, my = dom.minY() - (gy * cell - (dom.maxY() - dom.minY())) * .5f, mz = dom.minZ() - (gz * cell - (dom.maxZ() - dom.minZ())) * .5f;
        java.util.concurrent.atomic.AtomicInteger shown = new java.util.concurrent.atomic.AtomicInteger();
        java.util.stream.IntStream.range(0, gx * gy * gz).parallel().forEach(c -> {
            int bi = c % gx, bj = (c / gx) % gy, bk = c / (gx * gy);
            float x0 = mx + bi * cell, y0 = my + bj * cell, z0 = mz + bk * cell;
            float centre = f.distance(x0 + half, y0 + half, z0 + half);
            float local = f.local(new Bounds(x0 - 30, y0 - 30, z0 - 30, x0 + cell + 30, y0 + cell + 30, z0 + cell + 30)).distance(x0 + half, y0 + half, z0 + half);
            if (Math.max(centre, local) < thr) return;
            float min = 1e9f; float[] at = null;
            for (int k = 0; k <= 8; k++) for (int j = 0; j <= 8; j++) for (int i = 0; i <= 8; i++) {
                float d = f.distance(x0 + i * 6, y0 + j * 6, z0 + k * 6);
                if (d < min) { min = d; at = new float[]{x0 + i * 6, y0 + j * 6, z0 + k * 6}; }
            }
            if (min >= 6 || shown.incrementAndGet() > 6) return;
            StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "BAD local=%.1f centre=%.1f min=%.1f at %.0f %.0f %.0f |", local, centre, min, at[0], at[1], at[2]));
            for (var g : st.geoms) {
                float dc = g.distance(x0 + half, y0 + half, z0 + half, 42), ds = g.distance(at[0], at[1], at[2], 42);
                if (ds < 150) b.append(String.format(Locale.ROOT, " %s(centre %.1f sample %.1f erode %.2f emerge %.1f)", g.spec.name, dc, ds, g.erode, g.emergeOff));
            }
            System.out.println(b);
        });
        System.out.println("shown=" + shown.get());
    }

    /** Environment and the developed cell list of one T260.9 system (no bake). */
    static void plan(long seed) {
        Scenario s = scenario("t9", seed);
        System.out.println("T2609_PLAN seed=" + seed + " " + SystemGrowthT2609.env(seed).describe() + " cells=" + s.cells.size());
        for (var c : s.cells) System.out.printf(Locale.ROOT, "  %s birth=%.1f hPot=%.0f rPot=%.0f decay=%.0f lean=%.2f%s%n", c.name, c.birth, c.hPot, c.rPot, Math.min(c.decay, 999), c.envLean, c.daughter ? " daughter" : "");
        float[] b = {1e9f, -1e9f, 1e9f, -1e9f};
        for (float t : new float[]{22, 44}) {
            var st = s.at(t);
            System.out.printf(Locale.ROOT, "  t=%.0f live=%d bounds x %.0f..%.0f z %.0f..%.0f top %.0f%n", t, st.geoms.size(), st.bounds().minX(), st.bounds().maxX(), st.bounds().minZ(), st.bounds().maxZ(), st.bounds().maxY());
        }
    }

    static Scenario scenario(String model, long seed) {
        PersistentConvectionT2608.VARIANT = 4;
        return switch (model) {
            case "c4" -> T2608Preview.scenario("three_free", seed);
            case "t9" -> SystemGrowthT2609.build(seed, 46);
            default -> throw new IllegalArgumentException(model);
        };
    }

    record Item(String model, long seed, int stage, SparseBrickVolume vol, Bounds occ) {}

    static void world(Path out, String[] seeds, float[] times, String[] models, String[] views, double scale) throws Exception {
        Files.createDirectories(out);
        int W = (int) Math.round(1200 * scale), H = (int) Math.round(675 * scale);
        List<Item> items = new ArrayList<>();
        for (String model : models) for (String sd : seeds) {
            long seed = Long.parseLong(sd);
            Scenario s = scenario(model, seed);
            Bounds dom = T2608Preview.domain(T2608Preview.union(s, times));
            String env = model.equals("t9") ? " env[" + SystemGrowthT2609.env(seed).describe() + "]" : "";
            for (int si = 0; si < times.length; si++) {
                SystemState st = s.at(times[si]);
                long t1 = System.nanoTime();
                SparseBrickVolume v = PersistentConvectionT2608.bake(st, dom);
                double ms = (System.nanoTime() - t1) / 1e6;
                Bounds occ = T2608Preview.occupied(List.of(v));
                items.add(new Item(model, seed, si, v, occ));
                System.out.printf(Locale.ROOT, "T2609_SYSTEM model=%s seed=%d stage=%d t=%.0f cells=%d width=%.0f depth=%.0f height=%.0f bakeMs=%.0f textureMB=%.1f signature=%s%s%n", model, seed, si, times[si],
                        st.geoms.size(), occ.maxX() - occ.minX(), occ.maxZ() - occ.minZ(), occ.maxY() - PersistentConvectionT2608.BASE_Y, ms, v.stats().textureBytes() / 1048576.0,
                        Long.toHexString(v.signature()), env);
            }
        }
        PersistentConvectionT2608.VARIANT = 0;
        // One distance per view, fitted to the largest system: every image shares the world scale.
        double maxH = 0;
        for (Item it : items) maxH = Math.max(maxH, it.occ().maxY() - PersistentConvectionT2608.BASE_Y);
        double ty = PersistentConvectionT2608.BASE_Y + .45 * maxH;
        for (String view : views) {
            double dist = 0;
            if (!view.equals("gameplay")) for (Item it : items) dist = Math.max(dist, fit(view, it.occ(), ty, W, H));
            System.out.printf(Locale.ROOT, "T2609_VIEW %s distance=%.0f fov=%.0f targetY=%.0f%n", view, view.equals("gameplay") ? GAME_DIST : dist, view.equals("gameplay") ? GAME_FOV : 42.0, ty);
            for (Item it : items) {
                Bounds o = it.occ();
                double cx = (o.minX() + o.maxX()) * .5, cz = (o.minZ() + o.maxZ()) * .5;
                double[] cam = view.equals("gameplay") ? gameplay(cx, cz) : pose(view, cx, ty, cz, dist);
                int w = view.equals("gameplay") ? (int) Math.round(W * 1.5) : W, h = view.equals("gameplay") ? (int) Math.round(H * 1.5) : H;
                var light = new T2606Preview.LightLattice(it.vol());
                var img = T2606Preview.march(null, it.vol(), light, cam, w, h, "lit");
                T2606Preview.write(out.resolve(it.model() + "_" + it.seed() + "_stage" + it.stage() + "_" + view + ".png"), img, w, h, "lit");
            }
        }
    }

    static double[] dir(String view) {
        double az = Math.toRadians(view.equals("side") ? 0 : 35), el = Math.toRadians(view.equals("side") ? 4 : 11);
        return new double[]{-Math.sin(az) * Math.cos(el), Math.sin(el), Math.cos(az) * Math.cos(el)};
    }

    static double[] pose(String view, double tx, double ty, double tz, double dist) {
        double[] d = dir(view);
        return new double[]{tx + d[0] * dist, ty + d[1] * dist, tz + d[2] * dist, tx, ty, tz, 42};
    }

    /** Smallest camera distance at which the box (centred horizontally, fixed target height) fits the frame. */
    static double fit(String view, Bounds c, double ty, int W, int H) {
        double[] d = dir(view);
        double tx = (c.minX() + c.maxX()) * .5, tz = (c.minZ() + c.maxZ()) * .5;
        double tyH = Math.tan(Math.toRadians(42) / 2), txH = tyH * W / H;
        double[] fwd = T2606Preview.norm(new double[]{-d[0], -d[1], -d[2]});
        double[] right = T2606Preview.norm(T2606Preview.cross(fwd, new double[]{0, 1, 0})), up = T2606Preview.cross(right, fwd);
        double dist = 200;
        for (int it = 0; it < 600; it++) {
            boolean ok = true;
            double ex = tx + d[0] * dist, ey = ty + d[1] * dist, ez = tz + d[2] * dist;
            for (int k = 0; k < 8 && ok; k++) {
                double px = (k & 1) == 0 ? c.minX() : c.maxX(), py = (k & 2) == 0 ? c.minY() : c.maxY(), pz = (k & 4) == 0 ? c.minZ() : c.maxZ();
                double vx = px - ex, vy = py - ey, vz = pz - ez, f = vx * fwd[0] + vy * fwd[1] + vz * fwd[2];
                if (f <= 1) { ok = false; break; }
                double sx = (vx * right[0] + vy * right[1] + vz * right[2]) / f, sy = (vx * up[0] + vy * up[1] + vz * up[2]) / f;
                if (Math.abs(sx) > txH * .95 || Math.abs(sy) > tyH * .93) ok = false;
            }
            if (ok) return dist;
            dist *= 1.02;
        }
        return dist;
    }

    /** Player on the ground, GAME_DIST from the system centre on the sunlit side, looking at the system. */
    static double[] gameplay(double cx, double cz) {
        double ex = cx, ez = cz + GAME_DIST, ey = PersistentConvectionT2608.BASE_Y + GAME_EYE;
        return new double[]{ex, ey, ez, cx, PersistentConvectionT2608.BASE_Y + 420, cz, GAME_FOV};
    }
}
