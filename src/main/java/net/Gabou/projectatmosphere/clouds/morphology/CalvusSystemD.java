package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Bubble;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Compact;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Component;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Recipe;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Role;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * T260.4 calvus system (candidate D; experimental) = T260.3 C25 with the lower cloud field refined ({@link Builder#lowerField}).
 * Everything else is a byte copy of CalvusSystemC (C25 stays frozen as the accepted T260.3 baseline). T260.3 notes:
 * T260.3 calvus system layout (candidate C; experimental). The cell anatomy ({@code cell}, {@code lumps}) is T260.2 B
 * and the primary uses B's RNG streams; only the system layout changes:
 * <ul>
 * <li>Secondary 1 slightly overlaps the primary's footprint (0.92-1.02 x the radius sum; B 1.00-1.12) with up to
 * 20 degrees of azimuth freedom; secondary 2 keeps B's triangle (60-100 degrees off the axis, 0.97-1.09).</li>
 * <li>Low growth beyond secondary 1: a tail cell (0.30-0.45 H) continuing from secondary 1 with a bend of up to 23
 * degrees, and on 80 % of seeds a second low cell (0.20-0.30 H) forking off secondary 1 50-75 degrees from the tail, so
 * the low growth fans out (a single line read as a capsule from below) and the primary stays toward one end.</li>
 * <li>Outer growth arc: 3-5 medium-low turret cells (0.20-0.38 H) on a 150-200 degree arc around the flanking side,
 * and beyond it either an outer row of 2-3 thin low cells (0.12-0.22 H; {@link #build}, candidate C25) or 2-3 broad
 * growth regions, large-footprint low cells (r 0.20-0.26 H, 0.28-0.42 H, six turrets; {@link #buildBroad}, the broader
 * alternative C26), both clear of the secondary-1 direction. Every arc cell's turret footprint overlaps its nearest
 * neighbour (cores at most 1.15 x the radius sum apart), so the attached group is a broad lobed fan in plan, wide from
 * every side, joined by turrets rather than mounds. Arc cells carry only a small, low mound cluster.</li>
 * <li>A flank cell (0.34-0.50 H) in the valley between the primary and secondary 1 on the open side of the triangle, and
 * a far shoulder (0.28-0.42 H) beyond the primary opposite secondary 1: stepped flanks on both sides of the primary.</li>
 * <li>Lower cloud field: mound clusters around the tall cells (the primary's on the flanking-line side), one low cluster
 * under each low cell on the line side (none behind the primary), and valley clusters only between the primary and its
 * direct neighbours; each cluster has its own height scale (no sheet, no chain).</li>
 * <li>The primary's lumps are culled against the primary alone and its tier-2 lumps use their own stream, so the
 * isolated primary is identical whatever the layout around it.</li>
 * </ul>
 * Every added cell is a real turret cluster with the {@code cell} anatomy, overlapping its neighbour (no bridges). New
 * layout draws use their own RNG streams ({@code rng(60)} and up). Composition rules are unchanged (smooth inside
 * attachment groups with the same link blend, hard union between groups, 70-block clearance for detached cells).
 */
final class CalvusSystemD {
    private CalvusSystemD() {}

    static final float GAP = 70;

    /** T260.3 candidate C (C25): outer row of thin low cells. Meets every T260.3 invariant. */
    static Recipe build(long seed) { return new Builder(seed, false).build(); }

    /**
     * T260.3 broader alternative (C26): broad growth regions instead of the thin outer row. Wider (every seed's broadest
     * view inside the photographic range) but its low shoulders expose the pre-existing base-streak aliasing to the
     * fast/exact traversal check (see t260-calvus-system-layout.md).
     */
    static Recipe buildBroad(long seed) { return new Builder(seed, true).build(); }

    static final class Builder {
        // Layout parameters (final values documented in t260-calvus-system-layout.md).
        static float S1_NEAR = .92f, S2_NEAR = .97f, S2_SWING = 60, FLANK_ANGLE = 35, TAIL_NEAR = .88f, TAIL2_P = .80f;
        static boolean FAR_SHOULDER = true, ARC = true, ARC2 = true;
        static float ARC_R = .9f, BROAD_R = .20f;
        static float CROWN_JITTER = .35f; // near-summit azimuth jitter (B: .35 rad = +-10 deg)
        static float FLANK_H = .34f, FAR_H = .28f, TAIL_H = .30f, LINK_BLEND = 55, TAIL_R = .14f;
        static boolean FLANKS = true, VALLEYS = true;
        /** T260.4: outer-row cells fill the notch between two adjacent arc cells instead of extending a spoke (D2). */
        static boolean NOTCH = true;
        static float NOTCH_OUT = .6f; // radial step of a notch cell beyond the arc cells' mean radius, in its core radii

        final long seed;
        final List<Component> comps = new ArrayList<>();
        final List<Bubble> bubbles = new ArrayList<>();
        final List<float[]> meta = new ArrayList<>(); // per component: {crownStart, crownEnd, softCrown}
        final float hp;

        final boolean broadRegions;

        Builder(long seed, boolean broadRegions) {
            this.seed = seed;
            this.broadRegions = broadRegions;
            hp = 1050 * (.94f + .12f * new Random(seed ^ 0x51A7L).nextFloat());
        }

        Random rng(int purpose) { return new Random(seed * 0x9E3779B97F4A7C15L + purpose * 0x632BE59BD9B4E019L); }

        Recipe build() {
            Random lay = rng(1), org = rng(60);
            float theta = (float) (lay.nextFloat() * Math.PI);
            float ax = (float) Math.cos(theta), az = (float) Math.sin(theta);
            int side = lay.nextBoolean() ? 1 : -1;
            float rp = hp * (.30f + .04f * lay.nextFloat());
            float leanX = hp * (.14f + .10f * lay.nextFloat()), leanZ = hp * (.10f * (lay.nextFloat() * 2 - 1));
            float crownR = 1.15f * hp * (.16f + .03f * lay.nextFloat());
            cell("primary", Role.PRIMARY, 0, 0, 0, hp, rp * 1.35f, crownR, leanX, leanZ, 30, 6 + lay.nextInt(2), true, rng(10));
            List<float[]> cells = new ArrayList<>(); // attached cells: {x, z, radius, height}
            cells.add(new float[]{0, 0, rp, hp});
            // Secondary 1 stands on the primary's base footprint (B: 1.00-1.12 x the radius sum, touching).
            float h1 = hp * (.66f + .14f * lay.nextFloat()), r1 = hp * (.18f + .04f * lay.nextFloat());
            float d1 = (rp + r1) * (S1_NEAR + .10f * lay.nextFloat());
            float a1 = (float) Math.atan2(az * side, ax * side) + (org.nextFloat() * 2 - 1) * .35f;
            float s1x = (float) Math.cos(a1) * d1, s1z = (float) Math.sin(a1) * d1;
            cell("secondary_1", Role.SECONDARY, 0, s1x, s1z, h1, r1 * 1.3f, 1.15f * r1 * .62f, leanX * .5f, leanZ * .4f, 28, 4, false, rng(11));
            cells.add(new float[]{s1x, s1z, r1, h1});
            // Secondary 2 on the other side of the primary, swung 25-65 degrees off the axis (B: 60-100, a corner).
            float h2 = hp * (.44f + .16f * lay.nextFloat()), r2 = hp * (.14f + .04f * lay.nextFloat());
            float rot = (float) Math.toRadians((lay.nextFloat() * 2 - 1) * 40);
            float swing = (float) Math.toRadians(S2_SWING + 40 * Math.abs(rot) / Math.toRadians(40)) * (rot >= 0 ? 1 : -1);
            float bx = -(float) Math.cos(a1), bz = -(float) Math.sin(a1);
            float cr = (float) Math.cos(swing), sr = (float) Math.sin(swing);
            float d2 = (rp + r2) * (S2_NEAR + .12f * lay.nextFloat());
            float s2x = (bx * cr - bz * sr) * d2, s2z = (bx * sr + bz * cr) * d2;
            cell("secondary_2", Role.SECONDARY, 0, s2x, s2z, h2, r2 * 1.3f, 1.15f * r2 * .64f, leanX * .4f, leanZ * .3f, 26, 3, false, rng(12));
            cells.add(new float[]{s2x, s2z, r2, h2});
            if (FLANKS) flanks(cells, leanX, leanZ, org);
            lowerField(cells, rng(20));
            int group = 1;
            if (lay.nextFloat() < .8f) {
                float h3 = hp * (.32f + .16f * lay.nextFloat()), r3 = hp * (.11f + .04f * lay.nextFloat());
                int ps = lay.nextBoolean() ? 1 : -1;
                float dirx = -az * ps + ax * .3f * side, dirz = ax * ps + az * .3f * side;
                float n = (float) Math.hypot(dirx, dirz);
                placeDetached("developing_cell", Role.SECONDARY, group++, dirx / n, dirz / n, rp + r3 + GAP, h3, r3, 1.15f * r3 * .7f, 28, rng(13));
            }
            int assoc = 2 + lay.nextInt(3);
            for (int i = 0; i < assoc; i++) {
                float ang = (float) (lay.nextFloat() * Math.PI * 2);
                float h = hp * (.13f + .16f * lay.nextFloat()), r = hp * (.075f + .05f * lay.nextFloat());
                placeDetached("cumulus_" + (i + 1), Role.ASSOCIATED, group++, (float) Math.cos(ang), (float) Math.sin(ang), hp * (.9f + .5f * lay.nextFloat()), h, r, 1.15f * r * .72f, 22, rng(30 + i));
            }
            lumps(rng(40));
            return new Recipe("calvus_system_d", seed, List.copyOf(comps), List.copyOf(bubbles), 0, 8, LINK_BLEND, hp, 1, 0);
        }

        /**
         * Stepped flanking line: a tail cell beyond secondary 1 (0.30-0.45 H) and, on some seeds, a lower second step
         * (0.18-0.28 H) beyond it, so the outline descends from the primary through secondary 1 in steps and the primary
         * stays toward one end of the system. One mid-height flank cell (0.34-0.50 H) stands in the valley between the
         * primary and secondary 1 on the side away from secondary 2 (the open side of the triangle). All are real turret
         * clusters with the {@code cell} anatomy, linked by the group blend; none is a horizontal bridge.
         */
        void flanks(List<float[]> cells, float leanX, float leanZ, Random org) {
            float[] p = cells.get(0), s1 = cells.get(1), s2 = cells.get(2);
            float a1 = (float) Math.atan2(s1[1], s1[0]);
            // Flank: side of the primary-s1 axis away from secondary 2.
            float cross = (float) (Math.cos(a1) * s2[1] - Math.sin(a1) * s2[0]);
            float off = (cross > 0 ? -1 : 1) * (float) Math.toRadians(FLANK_ANGLE + 15 * org.nextFloat());
            float rf = hp * (.13f + .04f * org.nextFloat()), hf = hp * (FLANK_H + .16f * org.nextFloat());
            float df = (p[2] + rf) * (.85f + .15f * org.nextFloat());
            float fx = (float) Math.cos(a1 + off) * df, fz = (float) Math.sin(a1 + off) * df;
            cell("flank", Role.SECONDARY, 0, fx, fz, hf, rf * 1.3f, 1.15f * rf * .66f, leanX * .3f, leanZ * .2f, 26, 3, false, rng(71));
            cells.add(new float[]{fx, fz, rf, hf});
            if (FAR_SHOULDER) {
                // Far shoulder: beyond the primary opposite secondary 1, turned away from secondary 2, lower than the
                // primary's own outer shoulders' reach, so the primary has a stepped flank on both sides.
                float fa = a1 + (float) Math.PI + (cross > 0 ? -1 : 1) * (float) Math.toRadians(20 + 25 * org.nextFloat());
                float rs = hp * (.12f + .04f * org.nextFloat()), hs = hp * (FAR_H + .14f * org.nextFloat());
                float ds = (p[2] + rs) * (.90f + .15f * org.nextFloat());
                float sx = (float) Math.cos(fa) * ds, sz = (float) Math.sin(fa) * ds;
                cell("far_shoulder", Role.SECONDARY, 0, sx, sz, hs, rs * 1.3f, 1.15f * rs * .66f, leanX * .3f, leanZ * .2f, 26, 3, false, rng(72));
                cells.add(new float[]{sx, sz, rs, hs});
            }
            // Tail beyond secondary 1, bending gently.
            float ta = a1 + (org.nextFloat() * 2 - 1) * .40f;
            float rt = hp * (TAIL_R + .04f * org.nextFloat()), ht = hp * (TAIL_H + .15f * org.nextFloat());
            // Placed from secondary 1 (not by polar angle around the primary, which turned the bend into a lateral gap).
            float dt = (s1[2] + rt) * (TAIL_NEAR + .12f * org.nextFloat());
            float tx = s1[0] + (float) Math.cos(ta) * dt, tz = s1[1] + (float) Math.sin(ta) * dt;
            cell("tail", Role.SECONDARY, 0, tx, tz, ht, rt * 1.3f, 1.15f * rt * .66f, leanX * .25f, leanZ * .2f, 26, 3, false, rng(80));
            cells.add(new float[]{tx, tz, rt, ht});
            if (org.nextFloat() < TAIL2_P) {
                // A second, lower cell forks off secondary 1 on the other side of the tail's bend (50-75 degrees from the
                // tail): the low growth fans out from secondary 1 instead of extending one line (a line seen from below
                // is a capsule).
                float side2 = ta >= a1 ? -1 : 1;
                float ub = ta + side2 * (float) Math.toRadians(50 + 25 * org.nextFloat());
                float ru = hp * (TAIL_R - .02f + .04f * org.nextFloat()), hu = hp * (TAIL_H * .65f + .10f * org.nextFloat());
                float du = (s1[2] + ru) * (TAIL_NEAR + .12f * org.nextFloat());
                float ux = s1[0] + (float) Math.cos(ub) * du, uz = s1[1] + (float) Math.sin(ub) * du;
                cell("tail_2", Role.SECONDARY, 0, ux, uz, hu, ru * 1.3f, 1.15f * ru * .66f, leanX * .2f, leanZ * .2f, 26, 2, false, rng(81));
                cells.add(new float[]{ux, uz, ru, hu});
            }
            if (ARC) arc(cells, a1, leanX, leanZ, org);
        }

        /** Outer growth arc and outer row (see the class comment). */
        void arc(List<float[]> cells, float a1, float leanX, float leanZ, Random org) {
            float reach = 0;
            for (float[] c : cells) reach = Math.max(reach, (float) Math.hypot(c[0], c[1]) * .55f + c[2] * .3f);
            int n = 3 + org.nextInt(3);
            float span = (float) Math.toRadians(150 + 50 * org.nextFloat());
            List<float[]> arcs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                float a = a1 - span / 2 + span * (i + .5f) / n + (org.nextFloat() - .5f) * span / n * .5f;
                float rc = hp * (.12f + .05f * org.nextFloat()), hc = hp * (.20f + .18f * org.nextFloat());
                float d = (reach + cells.get(0)[2] * ARC_R) * (.88f + .24f * org.nextFloat());
                arcCell(cells, "arc_" + (i + 1), a, d, rc, hc, leanX, leanZ, rng(90 + i), 3);
                arcs.add(cells.get(cells.size() - 1));
            }
            boolean[] notchUsed = new boolean[Math.max(0, n - 1)];
            if (ARC2 && !broadRegions) {
                // Outer row: 2-3 lower cells (0.12-0.22 H) beyond the arc, each overlapping its nearest cell (chained
                // real overlap), so width comes from turrets, not mounds.
                int m = 2 + org.nextInt(2);
                float span2 = span * (.55f + .25f * org.nextFloat());
                for (int i = 0; i < m; i++) {
                    float a = a1 - span2 / 2 + span2 * (i + .5f) / m + (org.nextFloat() - .5f) * span2 / m * .6f;
                    // Clear of the secondary-1 direction (it would line up with secondary 1 and the tail and hide the
                    // notch between secondary 1 and the primary).
                    float off = (float) Math.IEEEremainder(a - a1, Math.PI * 2), clear = (float) Math.toRadians(30);
                    if (Math.abs(off) < clear) a = a1 + (off >= 0 ? clear : -clear);
                    float rc = hp * (.10f + .04f * org.nextFloat()), hc = hp * (.12f + .10f * org.nextFloat());
                    float d = (reach + cells.get(0)[2] * ARC_R) * 1.55f * (.9f + .2f * org.nextFloat());
                    if (NOTCH && notchUsed.length > 0) {
                        // T260.4: radially beyond one arc cell, the outer cell ended a descending spoke (primary -> arc
                        // -> outer cell), the low tapering tongue of the back / low_angle views. Instead it takes the
                        // free notch between the two adjacent arc cells nearest its azimuth, just outside their mean
                        // radius: the rim broadens instead of extending.
                        int best = -1;
                        float bestOff = Float.MAX_VALUE;
                        for (int k = 0; k + 1 < n; k++) {
                            if (notchUsed[k]) continue;
                            float[] p = arcs.get(k), q = arcs.get(k + 1);
                            float mid = (float) Math.atan2(p[1] + q[1], p[0] + q[0]);
                            float dev = Math.abs((float) Math.IEEEremainder(a - mid, Math.PI * 2));
                            if (dev < bestOff) { bestOff = dev; best = k; }
                        }
                        if (best >= 0) {
                            notchUsed[best] = true;
                            float[] p = arcs.get(best), q = arcs.get(best + 1);
                            a = (float) Math.atan2(p[1] + q[1], p[0] + q[0]);
                            d = .5f * ((float) Math.hypot(p[0], p[1]) + (float) Math.hypot(q[0], q[1])) + NOTCH_OUT * rc;
                        }
                    }
                    arcCell(cells, "arc_outer_" + (i + 1), a, d, rc, hc, leanX, leanZ, rng(96 + i), 3);
                }
            }
            if (ARC2 && broadRegions) {
                // Broad growth regions beyond the arc: 2-3 large-footprint low cells (r 0.20-0.26 H, 0.28-0.42 H, six
                // turrets) on the flanking side, clear of the secondary-1 direction, each overlapping its nearest cell.
                // Massive domed shoulders add width where thin low cells read as tongues.
                int m = 2 + org.nextInt(2);
                float span2 = span * (.60f + .25f * org.nextFloat());
                for (int i = 0; i < m; i++) {
                    float a = a1 - span2 / 2 + span2 * (i + .5f) / m + (org.nextFloat() - .5f) * span2 / m * .6f;
                    float off = (float) Math.IEEEremainder(a - a1, Math.PI * 2), clear = (float) Math.toRadians(30);
                    if (Math.abs(off) < clear) a = a1 + (off >= 0 ? clear : -clear);
                    float rc = hp * (BROAD_R + .06f * org.nextFloat()), hc = hp * (.28f + .14f * org.nextFloat());
                    float d = (reach + cells.get(0)[2] * ARC_R) * 1.45f * (.9f + .2f * org.nextFloat());
                    arcCell(cells, "broad_" + (i + 1), a, d, rc, hc, leanX, leanZ, rng(96 + i), 5);
                }
            }
        }

        /**
         * One arc cell at polar (a, d). Its turret footprint (about 1.3 x the core radius) must overlap the nearest existing
         * cell: cores at most 1.15 x the radius sum apart, so it joins the group through its own turrets, not through mounds.
         */
        void arcCell(List<float[]> cells, String name, float a, float d, float rc, float hc, float leanX, float leanZ, Random r, int turrets) {
            float x = (float) Math.cos(a) * d, z = (float) Math.sin(a) * d;
            float[] near = null;
            float best = Float.MAX_VALUE;
            for (float[] c : cells) {
                float g = (float) Math.hypot(c[0] - x, c[1] - z) - c[2] - rc;
                if (g < best) { best = g; near = c; }
            }
            float dn = (float) Math.hypot(near[0] - x, near[1] - z), want = 1.15f * (near[2] + rc);
            if (dn > want) { x = near[0] + (x - near[0]) * want / dn; z = near[1] + (z - near[1]) * want / dn; }
            cell(name, Role.SECONDARY, 0, x, z, hc, rc * 1.3f, 1.15f * rc * .66f, leanX * .2f, leanZ * .2f, 26, turrets, false, r);
            cells.add(new float[]{x, z, rc, hc, 1}); // flag: small, low mound cluster only
        }

        void placeDetached(String name, Role role, int group, float dx, float dz, float start, float h, float r, float rc, float blend, Random random) {
            long state = random.nextLong();
            for (float dist = start; dist < 3200; dist += 25) {
                int mark = bubbles.size();
                cell(name, role, group, dx * dist, dz * dist, h, r * 1.25f, rc, h * .08f * dx, h * .08f * dz, blend, 2, false, new Random(state));
                if (clear(mark, group)) return;
                bubbles.subList(mark, bubbles.size()).clear();
                comps.remove(comps.size() - 1);
                meta.remove(meta.size() - 1);
            }
        }

        boolean clear(int mark, int group) {
            for (int i = mark; i < bubbles.size(); i++) {
                Bubble a = bubbles.get(i);
                for (int j = 0; j < mark; j++) {
                    Bubble b = bubbles.get(j);
                    if (comps.get(b.comp()).group() == group) continue;
                    float d = (float) Math.sqrt(sq(a.x() - b.x()) + sq(a.y() - b.y()) + sq(a.z() - b.z())) - a.maxR() * 1.18f - b.maxR() * 1.18f;
                    if (d < GAP) return false;
                }
            }
            return true;
        }

        /** T260.2 B cell anatomy, verbatim (see {@link CalvusSystemB}). */
        int cell(String name, Role role, int group, float x0, float z0, float h, float baseR, float crownR, float leanX, float leanZ,
                 float blend, int extra, boolean primary, Random r) {
            int comp = comps.size();
            comps.add(new Component(name, role, group, blend, x0, z0, h));
            int crownStart = -1, crownEnd = -1;
            float golden = (float) (Math.PI * (3 - Math.sqrt(5)));
            float a0 = (float) (r.nextFloat() * Math.PI * 2);
            for (int k = 0; k <= extra; k++) {
                float u = extra == 0 ? 0 : k / (float) extra;
                float th = k == 0 ? h : h * (.86f - .50f * u) * (.92f + .16f * r.nextFloat());
                boolean outer = primary && k >= 4;
                float tr = crownR * (k == 0 ? 1 : (1.0f + .25f * u) * (.85f + .3f * r.nextFloat()));
                if (outer) {
                    th = h * (.58f - .28f * (k - 4) / Math.max(1f, extra - 4)) * (.9f + .2f * r.nextFloat());
                    tr = crownR * (.78f + .18f * r.nextFloat());
                }
                float az = a0 + k * golden + (r.nextFloat() - .5f) * .7f;
                float dist = k == 0 ? 0 : .85f * (crownR * .75f + (baseR - tr - crownR * .75f) * (float) Math.sqrt(u)) * (.85f + .3f * r.nextFloat());
                dist = Math.max(dist, k == 0 ? 0 : crownR * .7f);
                if (outer) dist = Math.max(dist, crownR * (2.1f + .5f * r.nextFloat()));
                if (k >= 1 && k <= 3 && extra >= 5) {
                    th = h * (.81f + .07f * r.nextFloat() - (k - 1) * .04f);
                    tr = crownR * (.55f + .08f * r.nextFloat());
                    dist = crownR * (1.70f + .20f * r.nextFloat());
                    az = a0 + (k - 1) * (float) (Math.PI * 2 / 3) + (r.nextFloat() - .5f) * (primary ? CROWN_JITTER : .35f);
                }
                th = Math.max(th, tr * 1.6f);
                float bx = x0 + (float) Math.cos(az) * dist, bz = z0 + (float) Math.sin(az) * dist;
                float lf = k == 0 ? 1 : .45f * (th / h);
                float wx = 0, wz = 0, prevRad = 0;
                int n = Math.max(2, Math.round((th - tr * .4f) / (tr * .62f)));
                for (int i = 0; i < n; i++) {
                    float t = n == 1 ? 1 : i / (float) (n - 1);
                    boolean upper = i >= n - 2;
                    float rad = tr * (1.12f - .22f * t) * (.9f + .2f * r.nextFloat());
                    if (i > 0 && upper) rad = Math.max(rad, prevRad * .95f);
                    prevRad = rad;
                    float ry = rad * (.74f + .12f * r.nextFloat());
                    float y = ry * .45f + (th - ry - ry * .45f) * t;
                    if (!upper) {
                        float ang = (float) (r.nextFloat() * Math.PI * 2);
                        wx = wx * .5f + (float) Math.cos(ang) * .20f * rad;
                        wz = wz * .5f + (float) Math.sin(ang) * .20f * rad;
                    }
                    float lt = (float) Math.pow(Math.max(0, y) / h, 1.5) * lf;
                    float cx = bx + leanX * lt + wx, cz = bz + leanZ * lt + wz;
                    boolean top = i == n - 1;
                    if (k == 0 && i == n - 2) crownStart = bubbles.size();
                    bubbles.add(new Bubble(cx, y, cz, rad * (.88f + .24f * r.nextFloat()), ry, rad * (.88f + .24f * r.nextFloat()), comp, top && (primary || role == Role.SECONDARY) ? 4 : 0));
                    if (k == 0 && top) crownEnd = bubbles.size();
                }
            }
            meta.add(new float[]{crownStart, crownEnd, primary ? 1 : 0});
            return comp;
        }

        /**
         * T260.4 lower cloud field. C25's clusters and draws (same stream, same cluster and bubble counts) with two
         * placement rules that remove the low tongues: (1) mounds of every cell except the primary lean toward the
         * attached group's interior (footprint-weighted centroid) instead of a random azimuth; (2) every mound of a
         * non-primary cell stays inside that cell's own base: horizontal radius at most 0.8 x the reach and centre
         * pulled in until its edge is within the reach (low cells 1.05 r: under their turrets; tall secondaries 1.45 r:
         * a skirt just past the 1.3 r turret footprint). In C25 a low cell's mounds (0.09-0.16 H) were as wide as the
         * cell and reached up to ~1.5 r beyond it, so the outermost low cells carried a smooth, turret-less lower-mass
         * lobe past their turrets (the tongues of seeds 20262741 and 20266607).
         */
        void lowerField(List<float[]> cells, Random r) {
            int comp = comps.size();
            comps.add(new Component("lower_mass", Role.LOWER_MASS, 0, 32, 0, 0, hp * .2f));
            List<float[]> clusters = new ArrayList<>(); // {x, z, spread, height, cellX, cellZ, reach (< 0: unclamped)}
            float lineDir = (float) Math.atan2(cells.get(1)[1], cells.get(1)[0]);
            float gx = 0, gz = 0, gw = 0;
            for (float[] c : cells) { float w = c[2] * c[2]; gx += c[0] * w; gz += c[1] * w; gw += w; }
            gx /= gw; gz /= gw;
            for (float[] c : cells) {
                float weight = Math.min(1, .45f + c[3] / hp);
                boolean prim = c == cells.get(0);
                boolean low = c[3] < hp * .5f;
                int k = low ? 1 : 1 + Math.round(weight * (1 + r.nextFloat()));
                if (low && c[0] * Math.cos(lineDir) + c[1] * Math.sin(lineDir) < 0) k = 0;
                boolean arcCell = c.length > 4 && c[4] > 0;
                float inward = (float) Math.atan2(gz - c[1], gx - c[0]);
                for (int i = 0; i < k; i++) {
                    float a = (float) (r.nextFloat() * Math.PI * 2), d = c[2] * (.7f + .7f * r.nextFloat());
                    if (prim) a = lineDir + (a / (float) Math.PI - 1) * (float) Math.toRadians(100);
                    else a = inward + (a / (float) Math.PI - 1) * (float) Math.toRadians(low ? 50 : 90);
                    float hs = .10f + .16f * r.nextFloat(), cr = c[2] * (.7f + .4f * r.nextFloat());
                    if (low) {
                        hs = Math.min(hs, (arcCell ? .35f : .55f) * c[3] / hp);
                        d *= .35f;
                        cr *= arcCell ? .8f : 1.2f;
                    }
                    clusters.add(new float[]{c[0] + (float) Math.cos(a) * d, c[1] + (float) Math.sin(a) * d, cr, hs,
                            c[0], c[1], prim ? -1 : (low ? 1.05f : 1.45f) * c[2]});
                }
            }
            if (VALLEYS) for (int i = 0; i < 1; i++) for (int j = i + 1; j < cells.size(); j++) {
                float[] a = cells.get(i), b = cells.get(j);
                if (Math.hypot(a[0] - b[0], a[1] - b[1]) > (a[2] + b[2]) * 1.9f) continue;
                float t = .4f + .2f * r.nextFloat();
                float mx = a[0] + (b[0] - a[0]) * t, mz = a[1] + (b[1] - a[1]) * t;
                clusters.add(new float[]{mx, mz, Math.min(a[2], b[2]) * (.75f + .3f * r.nextFloat()), .12f + .16f * r.nextFloat(), 0, 0, -1});
            }
            for (float[] c : clusters) {
                int n = 4 + r.nextInt(4);
                for (int i = 0; i < n; i++) {
                    float a = (float) (r.nextFloat() * Math.PI * 2), d = c[2] * (float) Math.sqrt(r.nextFloat());
                    float rad = hp * (.09f + .07f * r.nextFloat());
                    float top = hp * c[3] * (.7f + .5f * r.nextFloat());
                    float ry = Math.max(rad * .5f, Math.min(rad * .9f, top * .6f));
                    float y = Math.max(ry * .2f, top - ry);
                    float bx = c[0] + (float) Math.cos(a) * d, bz = c[1] + (float) Math.sin(a) * d;
                    float rx = rad * (.85f + .3f * r.nextFloat()), rz = rad * (.85f + .3f * r.nextFloat());
                    if (c[6] > 0) {
                        float m = Math.max(rx, rz), cap = .8f * c[6];
                        if (m > cap) { float s = cap / m; rx *= s; rz *= s; ry *= s; m = cap; }
                        float ox = bx - c[4], oz = bz - c[5], od = (float) Math.hypot(ox, oz), lim = Math.max(0, c[6] - m);
                        if (od > lim) { bx = c[4] + ox / od * lim; bz = c[5] + oz / od * lim; }
                    }
                    bubbles.add(new Bubble(bx, y, bz, rx, ry, rz, comp, 0));
                }
            }
            meta.add(new float[]{-1, -1, 0});
        }

        /**
         * T260.2 B lumps (tier 1, crown tier 3, tier 2 on active upper towers). One change: the primary's lumps are culled
         * against the primary alone (B culled them against every neighbour), so the primary's anatomy no longer depends
         * on where the layout puts the other cells. Lumps buried inside a neighbour stay invisible in the full system.
         */
        void lumps(Random r) {
            List<Bubble> macro = new ArrayList<>(bubbles);
            Compact all = new Compact(new Recipe("", 0, comps, macro, 0, 0, 0, 0, 1, 0), macro, -1);
            List<Bubble> primaryOnly = new ArrayList<>();
            for (Bubble b : macro) if (comps.get(b.comp()).role() == Role.PRIMARY) primaryOnly.add(b);
            Compact prim = new Compact(new Recipe("", 0, comps, primaryOnly, 0, 0, 0, 0, 1, 0), primaryOnly, -1);
            List<Bubble> tier1 = new ArrayList<>();
            for (int i = 0; i < macro.size(); i++) {
                Bubble p = macro.get(i);
                float[] m = meta.get(p.comp());
                boolean crown = m[2] > 0 && i >= m[0] && i < m[1];
                boolean mass = comps.get(p.comp()).role() == Role.LOWER_MASS;
                float r0 = Math.max(p.rx(), p.rz());
                int count = Math.round((crown ? 5 : mass ? 9 : 7) + (crown ? 3 : 7) * Math.min(1.5f, r0 / 220f));
                for (int k = 0; k < count * 3 && count > 0; k++) {
                    double el = Math.asin(Math.min(1, mass ? .2 + .8 * r.nextDouble() : -.25 + 1.2 * r.nextDouble()));
                    double az = r.nextDouble() * Math.PI * 2;
                    float dx = (float) (Math.cos(el) * Math.cos(az)), dy = (float) Math.sin(el), dz = (float) (Math.cos(el) * Math.sin(az));
                    float sx = p.x() + p.rx() * dx, sy = p.y() + p.ry() * dy, sz = p.z() + p.rz() * dz;
                    if (sy < 6) continue;
                    float lr = r0 * (crown ? .36f + .10f * r.nextFloat() : .30f + .14f * r.nextFloat());
                    boolean isPrimary = comps.get(p.comp()).role() == Role.PRIMARY;
                    if ((isPrimary ? prim : all).distance(sx, sy, sz) < -.12f * lr) continue;
                    if (creased(isPrimary ? primaryOnly : macro, isPrimary ? primaryOnly.indexOf(p) : i, sx, sy, sz, .45f * lr)) continue;
                    float f = crown ? .80f : .90f;
                    tier1.add(new Bubble(p.x() + (sx - p.x()) * f, p.y() + (sy - p.y()) * f, p.z() + (sz - p.z()) * f, lr * (.9f + .2f * r.nextFloat()),
                            lr * (.82f + .14f * r.nextFloat()), lr * (.9f + .2f * r.nextFloat()), p.comp(), crown ? 3 : 1));
                    if (--count == 0) break;
                }
            }
            bubbles.addAll(tier1);
            List<Bubble> tier2 = new ArrayList<>();
            Random own = rng(41); // the primary's tier-2 lumps: own stream, independent of how many cells precede them
            for (Bubble l : tier1) {
                if (l.tier() != 1) continue;
                Component c = comps.get(l.comp());
                if (c.role() == Role.LOWER_MASS || l.y() < c.top() * .42f) continue;
                Random q = c.role() == Role.PRIMARY ? own : r;
                int count = 2 + q.nextInt(3);
                for (int k = 0; k < count; k++) {
                    double el = Math.asin(.05 + .9 * q.nextDouble()), az = q.nextDouble() * Math.PI * 2;
                    float dx = (float) (Math.cos(el) * Math.cos(az)), dy = (float) Math.sin(el), dz = (float) (Math.cos(el) * Math.sin(az));
                    float sx = l.x() + l.rx() * dx, sy = l.y() + l.ry() * dy, sz = l.z() + l.rz() * dz;
                    float sr = l.rx() * (.34f + .12f * q.nextFloat());
                    if ((c.role() == Role.PRIMARY ? prim : all).distance(sx, sy, sz) < -.6f * sr) continue;
                    tier2.add(new Bubble(l.x() + (sx - l.x()) * .8f, l.y() + (sy - l.y()) * .8f, l.z() + (sz - l.z()) * .8f, sr, sr * .85f, sr, l.comp(), 2));
                }
            }
            bubbles.addAll(tier2);
        }

        static boolean creased(List<Bubble> macro, int self, float x, float y, float z, float margin) {
            for (int j = 0; j < macro.size(); j++) {
                if (j == self) continue;
                Bubble b = macro.get(j);
                if (Math.abs(ConvectiveSystemT260.ellipsoid(x - b.x(), y - b.y(), z - b.z(), b.rx(), b.ry(), b.rz())) < margin) return true;
            }
            return false;
        }

        static float sq(float v) { return v * v; }
    }
}
