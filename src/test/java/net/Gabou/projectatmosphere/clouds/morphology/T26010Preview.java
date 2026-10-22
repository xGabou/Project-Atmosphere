package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.Scenario;
import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.SystemState;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * T260.10 quick preview at CONSISTENT WORLD SCALE: T260.9 (before) against T260.10 vertical development (after), same
 * seeds, stages and cameras. Views:
 * <ul>
 *   <li>side / three_quarter: EXACTLY the T260.9 gallery cameras (distance 5249 / 5911, fov 42, target 628 above the
 *   base), so before / after share the world scale; deep towers may leave the frame;</li>
 *   <li>overview: side direction, one distance fitted once to the tallest / widest system of the run, shared by every
 *   image (the tallest systems fit without altering the comparison views);</li>
 *   <li>gameplay: a player on the ground (cloud base = the seed's LCL above the ground), 6 km from the system centre,
 *   70 deg lens pitched 28 deg up.</li>
 * </ul>
 * Lighting: the T260.5 neutral sun / ambient model evaluated on a SPARSE lattice (12 blocks, only around material) so
 * 10 km volumes fit in memory; the march skips empty coarse cells. Preview tooling only, same for both models.
 * <pre>
 * plan seeds times
 * world out seeds times models views [scale]   models: t9, t10; views: side, three_quarter, overview, gameplay
 * </pre>
 */
public final class T26010Preview {
    static final double SIDE_DIST = 5249, TQ_DIST = 5911, FIX_TY = 628, GAME_DIST = 6000, GAME_FOV = 70, GAME_PITCH = 28;

    public static void main(String[] args) throws Exception {
        if (args[0].equals("plan")) { for (String sd : args[1].split(",")) plan(Long.parseLong(sd), T2608Preview.floats(args[2])); return; }
        if (!args[0].equals("world")) throw new IllegalArgumentException(args[0]);
        world(Path.of(args[1]), args[2].split(","), T2608Preview.floats(args[3]), args[4].split(","), args[5].split(","), args.length > 6 ? Double.parseDouble(args[6]) : .6);
    }

    static Scenario scenario(String model, long seed) {
        PersistentConvectionT2608.VARIANT = 4;
        return switch (model) {
            case "t9" -> SystemGrowthT2609.build(seed, 46);
            case "t10" -> VerticalGrowthT26010.build(seed, 46);
            default -> throw new IllegalArgumentException(model);
        };
    }

    static void plan(long seed, float[] times) {
        var e = SystemGrowthT2609.env(seed);
        var pr = VerticalGrowthT26010.profile(e);
        Scenario s = scenario("t10", seed);
        System.out.println("T26010_PLAN seed=" + seed + " " + e.describe() + " " + pr.describe() + " cells=" + s.cells.size());
        for (var c : s.cells) System.out.printf(Locale.ROOT, "  %-8s birth=%5.1f hPot=%5.0f rPot=%5.0f deep=%.2f tauH=%4.1f decay=%5.1f lean=%.2f%s%n", c.name, c.birth, c.hPot, c.rPot, c.deep, c.tauH,
                Math.min(c.decay, 999), c.envLean, c.daughter ? " daughter" : "");
        for (float t : times) {
            SystemState st = s.at(t);
            Bounds b = st.bounds();
            StringBuilder tops = new StringBuilder();
            for (var g : st.geoms) tops.append(String.format(Locale.ROOT, " %s:%.0f%s", g.spec.name, g.st[0], g.st[2] > .05f ? "(dd" + String.format(Locale.ROOT, "%.1f", g.st[2]) + ")" : ""));
            System.out.printf(Locale.ROOT, "  t=%.0f live=%d box x %.0f..%.0f z %.0f..%.0f top %.0f billows=%d |%s%n", t, st.geoms.size(), b.minX(), b.maxX(), b.minZ(), b.maxZ(), b.maxY(), st.billowCount(), tops);
        }
    }

    static void world(Path out, String[] seeds, float[] times, String[] models, String[] views, double scale) throws Exception {
        Files.createDirectories(out);
        int W = (int) Math.round(1200 * scale), H = (int) Math.round(675 * scale);
        // Overview distance: fitted once over every system and stage of the run (analytic content boxes).
        double maxTop = 0;
        List<Bounds> boxes = new ArrayList<>();
        for (String model : models) for (String sd : seeds) {
            Scenario s = scenario(model, Long.parseLong(sd));
            for (float t : times) { Bounds b = s.at(t).bounds(); boxes.add(b); maxTop = Math.max(maxTop, b.maxY() - PersistentConvectionT2608.BASE_Y); }
        }
        double oty = PersistentConvectionT2608.BASE_Y + .45 * maxTop, oDist = 0;
        for (Bounds b : boxes) oDist = Math.max(oDist, T2609Preview.fit("side", b, oty, W, H));
        System.out.printf(Locale.ROOT, "T26010_VIEWS side=%.0f three_quarter=%.0f fov=42 targetY=%.0f | overview(side dir)=%.0f targetY=%.0f | gameplay dist=%.0f fov=%.0f pitch=%.0f%n",
                SIDE_DIST, TQ_DIST, FIX_TY, oDist, oty, GAME_DIST, GAME_FOV, GAME_PITCH);
        try (PrintWriter dims = new PrintWriter(Files.newBufferedWriter(out.resolve("dims.tsv")))) {
            dims.println("model\tseed\tstage\tt\tcells\twidthX\tdepthZ\tbaseAGL\ttopAGL\tverticalDepth\tbakeMs\tvolumeMB");
            for (String model : models) for (String sd : seeds) {
                long seed = Long.parseLong(sd);
                Scenario s = scenario(model, seed);
                var env = SystemGrowthT2609.env(seed);
                var pr = VerticalGrowthT26010.profile(env);
                Bounds dom = T2608Preview.domain(T2608Preview.union(s, times));
                for (int si = 0; si < times.length; si++) {
                    PersistentConvectionT2608.VARIANT = 4;
                    SystemState st = s.at(times[si]);
                    long t1 = System.nanoTime();
                    SparseBrickVolume v = PersistentConvectionT2608.bake(st, dom);
                    double ms = (System.nanoTime() - t1) / 1e6;
                    Bounds occ = T2608Preview.occupied(List.of(v));
                    // occupied() pads by 12 on every side.
                    float w = occ.maxX() - occ.minX() - 24, dz = occ.maxZ() - occ.minZ() - 24, yb = occ.minY() + 12 - PersistentConvectionT2608.BASE_Y, yt = occ.maxY() - 12 - PersistentConvectionT2608.BASE_Y;
                    double mb = v.cpuBytes() / 1048576.0;
                    System.out.printf(Locale.ROOT, "T26010_SYSTEM model=%s seed=%d stage=%d t=%.0f cells=%d width=%.0f depth=%.0f baseAGL=%.0f topAGL=%.0f verticalDepth=%.0f bakeMs=%.0f volumeMB=%.0f bricks=%d signature=%s%n",
                            model, seed, si, times[si], st.geoms.size(), w, dz, pr.baseAgl() + yb, pr.baseAgl() + yt, yt - yb, ms, mb, v.liveBricks(), Long.toHexString(v.signature()));
                    dims.printf(Locale.ROOT, "%s\t%d\t%d\t%.0f\t%d\t%.0f\t%.0f\t%.0f\t%.0f\t%.0f\t%.0f\t%.0f%n", model, seed, si, times[si], st.geoms.size(), w, dz, pr.baseAgl() + yb, pr.baseAgl() + yt, yt - yb, ms, mb);
                    dims.flush();
                    long l1 = System.nanoTime();
                    SparseLight light = new SparseLight(v, T2606Preview.EXT);
                    System.out.printf(Locale.ROOT, "  light cells=%d interior=%d ms=%.0f%n", light.computed, light.interior, (System.nanoTime() - l1) / 1e6);
                    double cx = (occ.minX() + occ.maxX()) * .5, cz = (occ.minZ() + occ.maxZ()) * .5;
                    for (String view : views) {
                        double[] cam;
                        int iw = W, ih = H;
                        switch (view) {
                            case "side", "three_quarter" -> cam = T2609Preview.pose(view, cx, PersistentConvectionT2608.BASE_Y + FIX_TY, cz, view.equals("side") ? SIDE_DIST : TQ_DIST);
                            case "overview" -> {
                                cam = T2609Preview.pose("side", cx, oty, cz, oDist);
                                iw = (int) Math.round(W * 1.5); ih = (int) Math.round(H * 1.5);
                            }
                            case "gameplay" -> {
                                double ey = PersistentConvectionT2608.BASE_Y - pr.baseAgl() + 2, p = Math.toRadians(GAME_PITCH);
                                cam = new double[]{cx, ey, cz + GAME_DIST, cx, ey + Math.tan(p) * GAME_DIST, cz, GAME_FOV};
                                iw = (int) Math.round(W * 1.5); ih = (int) Math.round(H * 1.5);
                            }
                            default -> throw new IllegalArgumentException(view);
                        }
                        long r1 = System.nanoTime();
                        var img = march(v, light, cam, iw, ih);
                        T2606Preview.write(out.resolve(model + "_" + seed + "_stage" + si + "_" + view + ".png"), img, iw, ih, "lit");
                        System.out.printf(Locale.ROOT, "  render %s ms=%.0f%n", view, (System.nanoTime() - r1) / 1e6);
                    }
                }
            }
        }
        PersistentConvectionT2608.VARIANT = 0;
    }

    /** T2606Preview.march (lit mode, exposure 3.2) with the sparse light and empty-coarse-cell skipping. */
    static T2606Preview.Lit march(SparseBrickVolume v, SparseLight light, double[] cam, int W, int H) {
        Bounds b = v.bounds();
        int[] g = v.grid();
        float cell = SparseBrickVolume.B * v.voxel();
        double[] o = {cam[0], cam[1], cam[2]};
        double[] fwd = T2606Preview.norm(new double[]{cam[3] - cam[0], cam[4] - cam[1], cam[5] - cam[2]});
        double[] right = T2606Preview.norm(T2606Preview.cross(fwd, new double[]{0, 1, 0})), up = T2606Preview.cross(right, fwd);
        double ty = Math.tan(Math.toRadians(cam[6]) / 2), tx = ty * W / H;
        float[] alpha = new float[W * H], rgb = new float[W * H];
        float step = 1.5f, EXT = T2606Preview.EXT;
        IntStream.range(0, H).parallel().forEach(j -> {
            for (int i = 0; i < W; i++) {
                double sx = (2 * (i + .5) / W - 1) * tx, sy = (1 - 2 * (j + .5) / H) * ty;
                double[] d = T2606Preview.norm(new double[]{fwd[0] + right[0] * sx + up[0] * sy, fwd[1] + right[1] * sx + up[1] * sy, fwd[2] + right[2] * sx + up[2] * sy});
                double[] sl = ConvectiveSystemT260Gallery.slab(o, d, b);
                if (sl == null) continue;
                double trans = 1, acc = 0;
                for (double t = sl[0] + step * .5; t < sl[1] && trans > 1e-3; t += step) {
                    float x = (float) (o[0] + d[0] * t), y = (float) (o[1] + d[1] * t), z = (float) (o[2] + d[2] * t);
                    int ci = (int) Math.floor((x - b.minX()) / cell), cj = (int) Math.floor((y - b.minY()) / cell), ck = (int) Math.floor((z - b.minZ()) / cell);
                    if (ci >= 0 && cj >= 0 && ck >= 0 && ci < g[0] && cj < g[1] && ck < g[2] && v.cellState(ci, cj, ck) == SparseBrickVolume.EMPTY) {
                        // Jump to the exit of this empty coarse cell (keeps the step phase).
                        double te = 1e30;
                        double[] lo = {b.minX() + ci * cell, b.minY() + cj * cell, b.minZ() + ck * cell};
                        for (int a = 0; a < 3; a++) {
                            if (Math.abs(d[a]) < 1e-9) continue;
                            double bnd = d[a] > 0 ? lo[a] + cell : lo[a];
                            te = Math.min(te, (bnd - o[a]) / d[a]);
                        }
                        if (te > t + step) t += Math.floor((te - t) / step) * step;
                        continue;
                    }
                    float dens = v.density(x, y, z);
                    if (dens <= .0008f) continue;
                    double st = Math.exp(-dens * EXT * step);
                    float[] l = light.at(x, y, z);
                    float direct = (float) (Math.PI / (4 * Math.PI) * Math.exp(-l[0]));
                    float rad = 3.2f * (direct + l[1]);
                    acc += trans * rad * (1 - st);
                    trans *= st;
                }
                alpha[j * W + i] = (float) (1 - trans);
                rgb[j * W + i] = (float) acc;
            }
        });
        return new T2606Preview.Lit(alpha, rgb);
    }

    /**
     * NeutralLightVolume's sun optical depth and ambient (same sun, sky / ground radiance, march steps), evaluated on a
     * 12-block lattice inside every non-empty coarse cell only (5^3 points per 48-block cell); a full cell whose 26
     * neighbours are full is deep interior (od 8, ambient 0).
     */
    static final class SparseLight {
        static final float SP = 12;
        final SparseBrickVolume v;
        final float ext, cell, minX, minY, minZ, maxX, maxY, maxZ;
        final int[] g, slot;
        final float[] pts;
        final float[][] boxes;
        int computed, interior;

        SparseLight(SparseBrickVolume v, float ext) {
            this.v = v; this.ext = ext;
            Bounds b = v.bounds();
            minX = b.minX(); minY = b.minY(); minZ = b.minZ(); maxX = b.maxX(); maxY = b.maxY(); maxZ = b.maxZ();
            g = v.grid();
            cell = SparseBrickVolume.B * v.voxel();
            boxes = new float[v.boxCount()][];
            for (int i = 0; i < boxes.length; i++) boxes[i] = v.box(i);
            int n = g[0] * g[1] * g[2];
            slot = new int[n];
            int c = 0, in = 0;
            for (int k = 0; k < g[2]; k++) for (int j = 0; j < g[1]; j++) for (int i = 0; i < g[0]; i++) {
                int id = (k * g[1] + j) * g[0] + i, s = v.cellState(i, j, k);
                if (s == SparseBrickVolume.EMPTY) { slot[id] = -1; continue; }
                boolean deepIn = s == SparseBrickVolume.FULL;
                for (int dk = -1; dk <= 1 && deepIn; dk++) for (int dj = -1; dj <= 1 && deepIn; dj++) for (int di = -1; di <= 1 && deepIn; di++) {
                    int a = i + di, bb = j + dj, e = k + dk;
                    if (a < 0 || bb < 0 || e < 0 || a >= g[0] || bb >= g[1] || e >= g[2] || v.cellState(a, bb, e) != SparseBrickVolume.FULL) deepIn = false;
                }
                if (deepIn) { slot[id] = -2; in++; continue; }
                slot[id] = c++;
            }
            computed = c; interior = in;
            pts = new float[c * 125 * 2];
            IntStream.range(0, n).parallel().forEach(id -> {
                int s = slot[id];
                if (s < 0) return;
                int i = id % g[0], j = (id / g[0]) % g[1], k = id / (g[0] * g[1]);
                float x0 = minX + i * cell, y0 = minY + j * cell, z0 = minZ + k * cell;
                for (int pk = 0; pk < 5; pk++) for (int pj = 0; pj < 5; pj++) for (int pi = 0; pi < 5; pi++) {
                    float x = x0 + pi * SP, y = y0 + pj * SP, z = z0 + pk * SP;
                    float od = od(x, y, z, NeutralLightVolume.SUN[0], NeutralLightVolume.SUN[1], NeutralLightVolume.SUN[2], 2f, 1.4f, 40f, 72, 8f);
                    float amb = 0;
                    for (int q = 0; q < 6; q++) {
                        double az = Math.toRadians(37.0 + 60.0 * q), el = Math.toRadians((q & 1) == 0 ? 30.0 : -30.0);
                        float dx = (float) (Math.cos(el) * Math.sin(az)), dy = (float) Math.sin(el), dz = (float) (Math.cos(el) * Math.cos(az));
                        amb += (dy > 0 ? NeutralLightVolume.SKY_L : NeutralLightVolume.GROUND_L) * (float) Math.exp(-od(x, y, z, dx, dy, dz, 6f, 2f, 192f, 6, 8f));
                    }
                    int o = (s * 125 + (pk * 5 + pj) * 5 + pi) * 2;
                    pts[o] = Math.min(od, NeutralLightVolume.OD_MAX);
                    pts[o + 1] = Math.min(amb / 6f, NeutralLightVolume.AMBIENT_MAX);
                }
            });
        }

        float[] at(float x, float y, float z) {
            int i = (int) Math.floor((x - minX) / cell), j = (int) Math.floor((y - minY) / cell), k = (int) Math.floor((z - minZ) / cell);
            if (i < 0 || j < 0 || k < 0 || i >= g[0] || j >= g[1] || k >= g[2]) return new float[]{0, NeutralLightVolume.SKY_L};
            int s = slot[(k * g[1] + j) * g[0] + i];
            if (s == -2) return new float[]{NeutralLightVolume.OD_MAX, 0};
            if (s < 0) return new float[]{0, NeutralLightVolume.SKY_L};
            float gx = Math.max(0, Math.min(3.999f, (x - minX - i * cell) / SP)), gy = Math.max(0, Math.min(3.999f, (y - minY - j * cell) / SP)), gz = Math.max(0, Math.min(3.999f, (z - minZ - k * cell) / SP));
            int a = (int) gx, bb = (int) gy, c = (int) gz;
            float fx = gx - a, fy = gy - bb, fz = gz - c;
            float[] r = new float[2];
            for (int q = 0; q < 2; q++) {
                float v000 = p(s, a, bb, c, q), v100 = p(s, a + 1, bb, c, q), v010 = p(s, a, bb + 1, c, q), v110 = p(s, a + 1, bb + 1, c, q);
                float v001 = p(s, a, bb, c + 1, q), v101 = p(s, a + 1, bb, c + 1, q), v011 = p(s, a, bb + 1, c + 1, q), v111 = p(s, a + 1, bb + 1, c + 1, q);
                float x0 = v000 + (v100 - v000) * fx, x1 = v010 + (v110 - v010) * fx, x2 = v001 + (v101 - v001) * fx, x3 = v011 + (v111 - v011) * fx;
                float y0 = x0 + (x1 - x0) * fy, y1 = x2 + (x3 - x2) * fy;
                r[q] = y0 + (y1 - y0) * fz;
            }
            return r;
        }

        private float p(int s, int i, int j, int k, int q) { return pts[(s * 125 + (k * 5 + j) * 5 + i) * 2 + q]; }

        private float od(float x, float y, float z, float dx, float dy, float dz, float first, float growth, float maxStep, int steps, float odCap) {
            float exitT = exit(x, y, z, 1f / dx, 1f / dy, 1f / dz);
            float raw = 0, s = 0, h = first, cap = odCap / ext;
            for (int i = 0; i < steps; i++) {
                if (s >= exitT || raw > cap) break;
                float hh = Math.min(h, exitT - s);
                float m = s + .5f * hh;
                float px = x + dx * m, py = y + dy * m, pz = z + dz * m;
                if (px >= minX && py >= minY && pz >= minZ && px <= maxX && py <= maxY && pz <= maxZ) raw += v.density(px, py, pz) * hh;
                s += hh;
                h = Math.min(h * growth, maxStep);
            }
            return raw * ext;
        }

        private float exit(float x, float y, float z, float ix, float iy, float iz) {
            if (boxes.length == 0) {
                float ex = Math.max((minX - x) * ix, (maxX - x) * ix), ey = Math.max((minY - y) * iy, (maxY - y) * iy), ez = Math.max((minZ - z) * iz, (maxZ - z) * iz);
                return Math.max(Math.min(ex, Math.min(ey, ez)), 0);
            }
            float far = 0;
            for (float[] bx : boxes) {
                float ax = (bx[0] - x) * ix, bxx = (bx[3] - x) * ix, ay = (bx[1] - y) * iy, by = (bx[4] - y) * iy, az = (bx[2] - z) * iz, bz = (bx[5] - z) * iz;
                float a = Math.max(Math.max(Math.min(ax, bxx), Math.min(ay, by)), Math.min(az, bz));
                float e = Math.min(Math.min(Math.max(ax, bxx), Math.max(ay, by)), Math.max(az, bz));
                if (e >= Math.max(a, 0)) far = Math.max(far, e);
            }
            return far;
        }
    }
}
