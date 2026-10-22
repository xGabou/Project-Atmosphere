package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Bubble;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Compact;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Component;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Recipe;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * T260.2 refined cumulonimbus calvus system (candidate B; experimental). Same layout RNG streams as the frozen T260.1
 * A baseline ({@link ConvectiveSystemT260#calvusA}) so before/after pairs show the same system. Changes against A:
 * <ul>
 * <li>Broad massive growth: major turrets 1.3-1.4x wider, overlapping (centre spacing about one turret radius) and
 * merging with a larger in-cell blend, so a cell reads as one massive cumuliform body with distinguishable domes.</li>
 * <li>No head-on-neck: the upper thermals of every turret stop wandering and never shrink into a neck below the crown.</li>
 * <li>Primary crown: a cluster of overlapping domes (summit + two sub-summits 0.90-0.96 H at spread azimuths), part of
 * the crown mass, so summits and notches are visible from most directions without free-standing peaks.</li>
 * <li>Lower cloud field: separate mound clusters of different heights around the attached cell bases (no along-axis
 * chain), cells arranged as a triangle rather than a line, so the lower mass is broad, irregular and not a streak.</li>
 * </ul>
 * Composition rules are unchanged (smooth inside attachment groups, hard union between groups, clearance for detached).
 */
final class CalvusSystemB {
    private CalvusSystemB() {}

    static final float GAP = 70;

    static Recipe build(long seed) { return new Builder(seed).build(); }

    static final class Builder {
        final long seed;
        final List<Component> comps = new ArrayList<>();
        final List<Bubble> bubbles = new ArrayList<>();
        final List<float[]> meta = new ArrayList<>(); // per component: {crownStart, crownEnd, softCrown}
        final float hp;

        Builder(long seed) {
            this.seed = seed;
            hp = 1050 * (.94f + .12f * new Random(seed ^ 0x51A7L).nextFloat());
        }

        Random rng(int purpose) { return new Random(seed * 0x9E3779B97F4A7C15L + purpose * 0x632BE59BD9B4E019L); }

        Recipe build() {
            Random lay = rng(1);
            float theta = (float) (lay.nextFloat() * Math.PI);
            float ax = (float) Math.cos(theta), az = (float) Math.sin(theta);
            int side = lay.nextBoolean() ? 1 : -1;
            float rp = hp * (.30f + .04f * lay.nextFloat());
            float leanX = hp * (.14f + .10f * lay.nextFloat()), leanZ = hp * (.10f * (lay.nextFloat() * 2 - 1));
            float crownR = 1.15f * hp * (.16f + .03f * lay.nextFloat());
            cell("primary", Role.PRIMARY, 0, 0, 0, hp, rp * 1.35f, crownR, leanX, leanZ, 30, 6 + lay.nextInt(2), true, rng(10));
            float h1 = hp * (.66f + .14f * lay.nextFloat()), r1 = hp * (.18f + .04f * lay.nextFloat());
            float d1 = (rp + r1) * (1.0f + .12f * lay.nextFloat());
            float s1x = ax * d1 * side, s1z = az * d1 * side;
            cell("secondary_1", Role.SECONDARY, 0, s1x, s1z, h1, r1 * 1.3f, 1.15f * r1 * .62f, leanX * .5f, leanZ * .4f, 28, 4, false, rng(11));
            float h2 = hp * (.44f + .16f * lay.nextFloat()), r2 = hp * (.14f + .04f * lay.nextFloat());
            float rot = (float) Math.toRadians((lay.nextFloat() * 2 - 1) * 40);
            // Triangle, not a line: secondary 2 swings 60-100 degrees off the axis (side chosen by the A rotation sign).
            float swing = (float) Math.toRadians(60 + 40 * Math.abs(rot) / Math.toRadians(40)) * (rot >= 0 ? 1 : -1);
            float bx = -ax * side, bz = -az * side;
            float cr = (float) Math.cos(swing), sr = (float) Math.sin(swing);
            float d2 = (rp + r2) * (1.05f + .15f * lay.nextFloat());
            float s2x = (bx * cr - bz * sr) * d2, s2z = (bx * sr + bz * cr) * d2;
            cell("secondary_2", Role.SECONDARY, 0, s2x, s2z, h2, r2 * 1.3f, 1.15f * r2 * .64f, leanX * .4f, leanZ * .3f, 26, 3, false, rng(12));
            lowerField(new float[][]{{0, 0, rp, .55f}, {s1x, s1z, r1, 1f}, {s2x, s2z, r2, .9f}}, rng(20));
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
            return new Recipe("calvus_system_b", seed, List.copyOf(comps), List.copyOf(bubbles), 0, 8, 55, hp, 1, 0);
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

        /**
         * A cell as a cluster of broad, overlapping rising turrets around a leaning main updraft. Turret k (1..extra)
         * shrinks in height outward; the first two are major turrets that overlap the main one (spacing about one radius)
         * so the cell is massive, with notches only where dome tops separate.
         */
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
                    tr = crownR * (.78f + .18f * r.nextFloat()); // narrower shoulders keep cauliflower texture
                }
                // Outer turrets pulled 15 % closer than A: one broad cell body, not separate pinnacles.
                float az = a0 + k * golden + (r.nextFloat() - .5f) * .7f;
                float dist = k == 0 ? 0 : .85f * (crownR * .75f + (baseR - tr - crownR * .75f) * (float) Math.sqrt(u)) * (.85f + .3f * r.nextFloat());
                dist = Math.max(dist, k == 0 ? 0 : crownR * .7f);
                // Outer shoulders of the primary stand outside the near-summit ring: the outline steps down outward
                // (sloping dome shoulders) instead of rising as a vertical wall.
                if (outer) dist = Math.max(dist, crownR * (2.1f + .5f * r.nextFloat()));
                if (k >= 1 && k <= 3 && extra >= 5) {
                    // Three near-summit turrets about 120 degrees apart (one stands clear of the summit from every side),
                    // broader than A and far enough out to leave a notch below the summit.
                    th = h * (.81f + .07f * r.nextFloat() - (k - 1) * .04f);
                    tr = crownR * (.55f + .08f * r.nextFloat());
                    dist = crownR * (1.70f + .20f * r.nextFloat());
                    az = a0 + (k - 1) * (float) (Math.PI * 2 / 3) + (r.nextFloat() - .5f) * .35f;
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
                    if (i > 0 && upper) rad = Math.max(rad, prevRad * .95f); // no neck under the top dome
                    prevRad = rad;
                    float ry = rad * (.74f + .12f * r.nextFloat());
                    float y = ry * .45f + (th - ry - ry * .45f) * t;
                    if (!upper) { // the top two thermals sit on the stack (wander caused the T260.1 head-on-neck)
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
         * Lower cloud field: mound clusters around each attached cell base (azimuths random, not along the axis), each
         * with its own height scale, plus one free cluster on the far side of secondary 1 (primary off-centre).
         * {x, z, radius, weight} per cell.
         */
        void lowerField(float[][] cells, Random r) {
            int comp = comps.size();
            comps.add(new Component("lower_mass", Role.LOWER_MASS, 0, 32, 0, 0, hp * .2f));
            List<float[]> clusters = new ArrayList<>();
            for (float[] c : cells) {
                int k = 1 + Math.round(c[3] * (1 + r.nextFloat()));
                for (int i = 0; i < k; i++) {
                    float a = (float) (r.nextFloat() * Math.PI * 2), d = c[2] * (.7f + .7f * r.nextFloat());
                    clusters.add(new float[]{c[0] + (float) Math.cos(a) * d, c[1] + (float) Math.sin(a) * d, c[2] * (.7f + .4f * r.nextFloat()), .10f + .16f * r.nextFloat()});
                }
            }
            float[] s1 = cells[1];
            float fx = s1[0] * 1.55f, fz = s1[1] * 1.55f;
            clusters.add(new float[]{fx, fz, s1[2] * 1.1f, .12f + .10f * r.nextFloat()});
            for (float[] c : clusters) {
                int n = 4 + r.nextInt(4);
                for (int i = 0; i < n; i++) {
                    float a = (float) (r.nextFloat() * Math.PI * 2), d = c[2] * (float) Math.sqrt(r.nextFloat());
                    float rad = hp * (.09f + .07f * r.nextFloat());
                    float top = hp * c[3] * (.7f + .5f * r.nextFloat());
                    float ry = Math.max(rad * .5f, Math.min(rad * .9f, top * .6f));
                    float y = Math.max(ry * .2f, top - ry);
                    bubbles.add(new Bubble(c[0] + (float) Math.cos(a) * d, y, c[1] + (float) Math.sin(a) * d,
                            rad * (.85f + .3f * r.nextFloat()), ry, rad * (.85f + .3f * r.nextFloat()), comp, 0));
                }
            }
            meta.add(new float[]{-1, -1, 0});
        }

        /** Same exposure- and crease-guarded lumps as A (tier 1, crown tier 3, tier 2 on active upper towers). */
        void lumps(Random r) {
            List<Bubble> macro = new ArrayList<>(bubbles);
            Compact all = new Compact(new Recipe("", 0, comps, macro, 0, 0, 0, 0, 1, 0), macro, -1);
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
                    if (all.distance(sx, sy, sz) < -.12f * lr) continue;
                    if (creased(macro, i, sx, sy, sz, .45f * lr)) continue;
                    float f = crown ? .80f : .90f;
                    tier1.add(new Bubble(p.x() + (sx - p.x()) * f, p.y() + (sy - p.y()) * f, p.z() + (sz - p.z()) * f, lr * (.9f + .2f * r.nextFloat()),
                            lr * (.82f + .14f * r.nextFloat()), lr * (.9f + .2f * r.nextFloat()), p.comp(), crown ? 3 : 1));
                    if (--count == 0) break;
                }
            }
            bubbles.addAll(tier1);
            List<Bubble> tier2 = new ArrayList<>();
            for (Bubble l : tier1) {
                if (l.tier() != 1) continue;
                Component c = comps.get(l.comp());
                if (c.role() == Role.LOWER_MASS || l.y() < c.top() * .42f) continue;
                int count = 2 + r.nextInt(3);
                for (int k = 0; k < count; k++) {
                    double el = Math.asin(.05 + .9 * r.nextDouble()), az = r.nextDouble() * Math.PI * 2;
                    float dx = (float) (Math.cos(el) * Math.cos(az)), dy = (float) Math.sin(el), dz = (float) (Math.cos(el) * Math.sin(az));
                    float sx = l.x() + l.rx() * dx, sy = l.y() + l.ry() * dy, sz = l.z() + l.rz() * dz;
                    float sr = l.rx() * (.34f + .12f * r.nextFloat());
                    if (all.distance(sx, sy, sz) < -.6f * sr) continue;
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
