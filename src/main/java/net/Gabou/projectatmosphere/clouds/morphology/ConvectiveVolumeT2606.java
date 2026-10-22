package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Bubble;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Component;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Recipe;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Role;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Scale;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * T260.6 convective volume reconstruction (experimental fixture only, never production). Every arm is a transform of the
 * FROZEN candidate D recipe ({@link CalvusSystemD}, untouched): same seeds, same cells, turrets, thermals and lumps, same
 * components and attachment groups, same condensation base. Three independent mechanisms can be switched on:
 * <ul>
 * <li><b>A merge</b>: hierarchical, scale-relative blending. D folds every bubble of a component with one small constant
 * radius (26-30 blocks for 60-250 block thermals, ~9 for lumps): hard sphere intersections and perched lumps. A blends
 * the thermals of one turret with {@link #STACK_K} x their radius (a continuous column), seats each lump into its own
 * turret with {@link #LUMP_K} x its radius (a bulge, not a ball), and only then joins the turrets of a cell with
 * {@link #TURRET_K} x the turret radius, so the valleys between turrets stay open.</li>
 * <li><b>B growth</b>: each turret's stack of ellipsoids becomes one coherent growth column fitted to it (same knots: base,
 * lean, wander, radii and dome height): a height-dependent cross-section with low-order azimuthal harmonics that twist
 * with height, asymmetric upper expansion toward the shear side, sheared asymmetric dome, irregular shoulders, and soft
 * growth pulses at the old thermal joints. D's lumps are re-seated on the new surface along their own direction.</li>
 * <li><b>C density</b>: structured interior density through the existing brick code (no shader change): the stored value
 * is a pseudo-distance whose renderer decode clamp(0.5 - s/feather) equals ramp(d) x m(x), with m a smooth field: an
 * adiabatic increase with height above the base, edge entrainment on lateral and lower surfaces (tops stay crisp), and
 * two octaves of low-frequency value noise. m returns to 1 below {@link #DEEP_1} blocks (optically invisible depth).</li>
 * </ul>
 * With every mechanism off the field is evaluated in D's exact fold order with D's own functions, so the bake must
 * reproduce D's signature bit for bit (checked by the sandbox and the native harness).
 */
public final class ConvectiveVolumeT2606 {
    private ConvectiveVolumeT2606() {}

    public static final int VERSION = 1;
    /** Floor of the stored value (blocks): below the band (30), above the coarse FULL-skip threshold (68.4). */
    static final float FULL_SKIP_FLOOR = 60f;

    // ---------------------------------------------------------------- tunables (experiment parameters, reported)
    /** A: thermal-to-thermal blend inside one turret, x the incoming thermal's horizontal radius. */
    public static float STACK_K = .35f;
    /** A: turret-to-turret blend inside one cell, x the incoming turret's mean radius (never below the D blend). */
    public static float TURRET_K = .10f;
    /** A: lump-into-turret blend, x the lump radius (tier 1 / crown tier 3), and tier-2 lump blend. */
    public static float LUMP_K = .28f, LUMP2_K = .25f;
    /** A: cell-to-cell link blend inside the attached group (D: 55). */
    public static float LINK_K = 55f;
    /** B: cross-section harmonics (relative amplitude of m=2 and m=3), twist over the height (rad). */
    public static float H2 = .14f, H3 = .09f, TWIST = .9f;
    /** B: asymmetric upper expansion (relative), sheared apex (x top radius), shoulder amplitude (relative). */
    public static float E_UP = .18f, APEX = .28f, SHOULDER = .28f;
    /** B: soft growth pulse at the old thermal joints (relative pinch; D's sphere stack pinches 15-30 %). */
    public static float PULSE = .06f;
    /** B: flank convexity (relative narrowing toward the base over the lowest 40 % of the column). */
    public static float TAPER = .12f;
    /** B: lumps as oblate bulges oriented along their growth direction: normal and tangential semi-axes (x D's lump
     * radius) and the fraction of the normal semi-axis that protrudes beyond the parent surface. */
    public static float LUMP_N = .65f, LUMP_T = 1.2f, PROTRUDE = .75f;
    /** B: growth-bulge population replacing D's lumps (1) or D's lumps re-seated as oblate bulges (0). */
    public static float BULGES = 1f;
    /** B: bulge radius (x local column radius at the top; 0.65 of it at the base), centre spacing (x mean radius),
     * oblateness (normal / tangential), protrusion, upward lean of the bulge axis, per-column cap, tier-2 size. */
    public static float BULGE_FLOOR = .25f;
    public static float BULGE_R = .40f, BULGE_SPACING = 1.1f, BULGE_N = .62f, BULGE_PROT = .70f, BULGE_UP = .35f, BULGE_MAX = 90f, BULGE2_R = .36f, BULGE2_MAX = 10f;
    /** C: base density fraction at the condensation level, height of full density (x primary height). */
    public static float P0 = .66f, PH = .70f;
    /** C: lateral/lower edge entrainment depth (fraction) and e-folding depth (blocks). */
    public static float ENTRAIN = .34f, ENTRAIN_L = 24f;
    /** C: low-frequency noise amplitude and scales (blocks); density floor; fade-out depths (blocks). */
    public static float NOISE = .12f, NOISE_S1 = 230f, NOISE_S2 = 100f, M_MIN = .42f, DEEP_0 = 70f, DEEP_1 = 110f;

    /** Which mechanisms are active. */
    public record Arms(boolean merge, boolean growth, boolean density) {
        public static Arms parse(String s) {
            return new Arms(s.contains("A") && !s.equals("D"), s.contains("B"), s.contains("C"));
        }
        public String name() {
            String n = (merge ? "A" : "") + (growth ? "B" : "") + (density ? "C" : "");
            return n.isEmpty() ? "D" : n;
        }
    }

    // ---------------------------------------------------------------- structure of a D recipe

    /**
     * D's anatomy recovered from its recipe without re-running the builder: turrets (thermal stacks; a turret of a
     * PRIMARY/SECONDARY cell ends with its tier-4 dome, an ASSOCIATED cumulus restarts when the height drops, every
     * lower-mass mound is its own one-thermal turret) and the exact parent of every lump (lumps are generated in parent
     * order with their centre at exactly 0.9 / 0.8 of the parent's normalised radius).
     */
    public static final class Structure {
        public final Recipe recipe;
        final int[] turretOf;      // per bubble: turret of a macro bubble; a lump's parent turret
        final int[] parent;        // per bubble: lump parent bubble (-1 for macro)
        final List<int[]> turrets = new ArrayList<>(); // macro bubble indices, base to top
        final List<Integer> turretComp = new ArrayList<>();

        public Structure(Recipe r) {
            recipe = r;
            List<Bubble> b = r.bubbles();
            int n = b.size();
            turretOf = new int[n];
            parent = new int[n];
            java.util.Arrays.fill(turretOf, -1);
            java.util.Arrays.fill(parent, -1);
            int lastComp = -1;
            List<Integer> current = null;
            float lastY = 0;
            boolean closed = true;
            int macroEnd = 0;
            for (int i = 0; i < n; i++) {
                Bubble x = b.get(i);
                if (x.tier() != 0 && x.tier() != 4) break;
                macroEnd = i + 1;
                Role role = r.components().get(x.comp()).role();
                boolean start = x.comp() != lastComp || closed || role == Role.LOWER_MASS
                        || (role == Role.ASSOCIATED && x.y() < lastY);
                if (start) {
                    if (current != null) close(current);
                    current = new ArrayList<>();
                    turretComp.add(x.comp());
                }
                current.add(i);
                turretOf[i] = turrets.size();
                closed = x.tier() == 4;
                lastComp = x.comp();
                lastY = x.y();
            }
            if (current != null) close(current);
            // Tier 1 / 3 lumps: parents in macro order.
            int p = 0, firstTier2 = -1;
            for (int i = macroEnd; i < n; i++) {
                Bubble l = b.get(i);
                if (l.tier() == 2) { firstTier2 = i; break; }
                float f = l.tier() == 3 ? .80f : .90f;
                while (p < macroEnd && !(b.get(p).comp() == l.comp() && Math.abs(k0(l, b.get(p)) - f) < 2e-3f)) p++;
                if (p >= macroEnd) throw new IllegalStateException("T2606: no parent for lump " + i);
                parent[i] = p;
                turretOf[i] = turretOf[p];
            }
            if (firstTier2 >= 0) {
                int q = macroEnd;
                for (int i = firstTier2; i < n; i++) {
                    Bubble l = b.get(i);
                    while (q < firstTier2 && !(b.get(q).tier() == 1 && b.get(q).comp() == l.comp() && Math.abs(k0(l, b.get(q)) - .8f) < 2e-3f)) q++;
                    if (q >= firstTier2) throw new IllegalStateException("T2606: no parent for tier-2 lump " + i);
                    parent[i] = q;
                    turretOf[i] = turretOf[q];
                }
            }
        }

        /**
         * A developed recipe keeps every bubble's index (ConvectiveSystemT260.developStep only rewrites bubbles in place),
         * so its anatomy is the template's: same turrets, same lump parents.
         */
        public Structure(Recipe r, Structure template) {
            if (r.bubbles().size() != template.recipe.bubbles().size()) throw new IllegalArgumentException("T2606: bubble count changed");
            recipe = r;
            turretOf = template.turretOf.clone();
            parent = template.parent.clone();
            turrets.addAll(template.turrets);
            turretComp.addAll(template.turretComp);
        }

        private void close(List<Integer> t) { turrets.add(t.stream().mapToInt(Integer::intValue).toArray()); }

        private static float k0(Bubble l, Bubble p) {
            float ax = (l.x() - p.x()) / p.rx(), ay = (l.y() - p.y()) / p.ry(), az = (l.z() - p.z()) / p.rz();
            return (float) Math.sqrt(ax * ax + ay * ay + az * az);
        }

        public int turretCount() { return turrets.size(); }
        public int[] turret(int t) { return turrets.get(t).clone(); }
        public int turretComp(int t) { return turretComp.get(t); }
        public int parentOf(int bubble) { return parent[bubble]; }
        public int turretOf(int bubble) { return turretOf[bubble]; }
    }

    // ---------------------------------------------------------------- B: growth column

    /** One turret as a coherent asymmetric growth column fitted to its D thermal stack. */
    public static final class Column {
        final float[] ky, kx, kz, ka;   // knots: thermal centre height, axis xz, horizontal radius
        final float yb, yc, hcap, yTop, aTop, ry0;
        final float a2, th2, a3, th3, twist, e, psi, cpsi, spsi, pulse;
        final float[] sh;               // shoulders: y, phi, amp, sigmaUp, sigmaDown, sigmaPhi
        final float[] box;              // AABB
        final float rMax;
        // Axis and radius profile tabulated every TAB blocks from yb to yTop (linear lookup; evaluation speed only).
        static final float TAB = 2f;
        private float[] tabX, tabZ, tabR;
        // Per-height cross-section table (stride ST = 9 + shoulders): axis x, axis z, r0, cos/sin of the m=2 and m=3
        // harmonic phases (twist included), upper-expansion factor, radius bound, then each shoulder's vertical weight.
        private float[] tabS;
        private int st;
        private float kappa;
        private float[] shc, shs;

        Column(List<Bubble> thermals, Random r) {
            int n = thermals.size();
            ky = new float[n]; kx = new float[n]; kz = new float[n]; ka = new float[n];
            for (int i = 0; i < n; i++) {
                Bubble t = thermals.get(i);
                ky[i] = t.y(); kx[i] = t.x(); kz[i] = t.z(); ka[i] = (t.rx() + t.rz()) * .5f;
            }
            Bubble first = thermals.get(0), last = thermals.get(n - 1);
            yb = first.y() - first.ry();
            ry0 = first.ry();
            yc = last.y();
            hcap = last.ry();
            yTop = yc + hcap;
            aTop = ka[n - 1];
            float lx = kx[n - 1] - kx[0], lz = kz[n - 1] - kz[0];
            float lean = (float) Math.hypot(lx, lz);
            float base = lean > .15f * aTop ? (float) Math.atan2(lz, lx) : (float) (r.nextFloat() * Math.PI * 2);
            psi = base + (r.nextFloat() - .5f) * 1.2f;
            cpsi = (float) Math.cos(psi); spsi = (float) Math.sin(psi);
            e = E_UP * (.7f + .6f * r.nextFloat());
            a2 = H2 * (.6f + .8f * r.nextFloat());
            th2 = (float) (r.nextFloat() * Math.PI);
            a3 = H3 * (.6f + .8f * r.nextFloat());
            th3 = (float) (r.nextFloat() * Math.PI * 2);
            twist = TWIST * (r.nextFloat() * 2 - 1);
            pulse = PULSE;
            int count = n >= 5 ? 2 + r.nextInt(2) : n >= 3 ? 1 + r.nextInt(2) : n == 2 ? r.nextInt(2) : (r.nextFloat() < .5f ? 1 : 0);
            sh = new float[count * 6];
            float lo = Math.max(yb, 0), span = yc - lo;
            float shMax = 0;
            for (int s = 0; s < count; s++) {
                float y = lo + (.30f + .45f * r.nextFloat()) * span;
                if (n == 1) y = lo + (.45f + .35f * r.nextFloat()) * span;
                float rr = r0(y);
                sh[s * 6] = y;
                sh[s * 6 + 1] = (float) (r.nextFloat() * Math.PI * 2);
                sh[s * 6 + 2] = SHOULDER * (.7f + .6f * r.nextFloat()) * rr;
                sh[s * 6 + 3] = .55f * rr;
                sh[s * 6 + 4] = .35f * rr;
                sh[s * 6 + 5] = .5f;
                shMax = Math.max(shMax, sh[s * 6 + 2]);
            }
            float amax = 0;
            for (float a : ka) amax = Math.max(amax, a);
            rMax = amax * (1 + a2 + a3 + e) + shMax + APEX * aTop;
            float x0 = 1e9f, x1 = -1e9f, z0 = 1e9f, z1 = -1e9f;
            for (int i = 0; i < n; i++) { x0 = Math.min(x0, kx[i]); x1 = Math.max(x1, kx[i]); z0 = Math.min(z0, kz[i]); z1 = Math.max(z1, kz[i]); }
            box = new float[]{x0 - rMax, yb, z0 - rMax, x1 + rMax, yTop, z1 + rMax};
            int nt = (int) Math.ceil((yTop - yb) / TAB) + 2;
            float[] tx = new float[nt], tz = new float[nt], tr = new float[nt];
            for (int i = 0; i < nt; i++) { float y = yb + i * TAB; tx[i] = axisXExact(y); tz[i] = axisZExact(y); tr[i] = r0Exact(y); }
            tabX = tx; tabZ = tz; tabR = tr;
            int ns = sh.length / 6;
            st = 9 + ns;
            kappa = 2f / (.5f * .5f); // von Mises equivalent of the 0.5 rad azimuthal Gaussian
            shc = new float[ns]; shs = new float[ns];
            for (int k = 0; k < ns; k++) { shc[k] = (float) Math.cos(sh[k * 6 + 1]); shs[k] = (float) Math.sin(sh[k * 6 + 1]); }
            float[] t = new float[nt * st];
            for (int i = 0; i < nt; i++) {
                float y = Math.min(yb + i * TAB, yc), u = (y - yb) / (yTop - yb);
                int o = i * st;
                float al = th2 + twist * u, be = th3 - .7f * twist * u;
                t[o] = tx[i]; t[o + 1] = tz[i]; t[o + 2] = r0Exact(y);
                t[o + 3] = (float) Math.cos(2 * al); t[o + 4] = (float) Math.sin(2 * al);
                t[o + 5] = (float) Math.cos(3 * be); t[o + 6] = (float) Math.sin(3 * be);
                t[o + 7] = e * smooth(.45f, .92f, u);
                float bound = t[o + 2] * (1 + a2 + a3 + t[o + 7]);
                for (int k = 0; k < ns; k++) {
                    float dy = y - sh[k * 6], sig = dy > 0 ? sh[k * 6 + 3] : sh[k * 6 + 4];
                    t[o + 9 + k] = sh[k * 6 + 2] * (float) Math.exp(-(dy / sig) * (dy / sig));
                    bound += t[o + 9 + k];
                }
                t[o + 8] = bound;
            }
            tabS = t;
        }

        /** Interpolated table row at height y (clamped to the dome base) into {@code row}. */
        private void row(float y, float[] row) {
            float g = (Math.min(y, yc) - yb) / TAB;
            int i = Math.max(0, Math.min((int) g, tabS.length / st - 2));
            float f = Math.max(0, Math.min(1, g - i));
            int a = i * st, b = a + st;
            for (int k = 0; k < st; k++) row[k] = tabS[a + k] + (tabS[b + k] - tabS[a + k]) * f;
        }

        private float tab(float[] t, float y) {
            float g = (y - yb) / TAB;
            if (g <= 0) return t[0];
            int i = (int) g;
            if (i >= t.length - 1) return t[t.length - 1];
            float f = g - i;
            return t[i] + (t[i + 1] - t[i]) * f;
        }

        /** Knot interval of height y (clamped) and its fraction; -1 below the first knot, n-1 above the last. */
        private int seg(float y) {
            int n = ky.length;
            if (y <= ky[0]) return -1;
            for (int i = 0; i + 1 < n; i++) if (y < ky[i + 1]) return i;
            return n - 1;
        }

        /** Cubic Hermite through knots with Catmull-Rom tangents (zero tangent at both ends). */
        private float hermite(float[] v, float y) {
            int n = ky.length, i = seg(y);
            if (i < 0) return v[0];
            if (i >= n - 1) return v[n - 1];
            float h = ky[i + 1] - ky[i], t = (y - ky[i]) / h;
            float m0 = i == 0 ? 0 : (v[i + 1] - v[i - 1]) / (ky[i + 1] - ky[i - 1]) * h;
            float m1 = i + 2 >= n ? 0 : (v[i + 2] - v[i]) / (ky[i + 2] - ky[i]) * h;
            float t2 = t * t, t3 = t2 * t;
            return (2 * t3 - 3 * t2 + 1) * v[i] + (t3 - 2 * t2 + t) * m0 + (-2 * t3 + 3 * t2) * v[i + 1] + (t3 - t2) * m1;
        }

        /** Axis radius profile with the soft growth pulse (zero at the knots, maximal midway). */
        float r0(float y) { return tabR == null ? r0Exact(y) : tab(tabR, y); }
        float axisX(float y) { return tabX == null ? axisXExact(y) : tab(tabX, y); }
        float axisZ(float y) { return tabZ == null ? axisZExact(y) : tab(tabZ, y); }

        float r0Exact(float y) {
            float r = hermite(ka, Math.min(y, yc));
            float lo = Math.max(yb, 0);
            if (TAPER > 0 && y < yc) r *= 1 - TAPER * (1 - smooth(lo, lo + .4f * (yc - lo), y)); // convex flank: narrower at the base
            if (y < ky[0]) { float q = (ky[0] - y) / ry0; r *= (float) Math.sqrt(Math.max(0, 1 - q * q)); } // rounded base
            int i = seg(y);
            if (i >= 0 && i < ky.length - 1) {
                float t = (y - ky[i]) / (ky[i + 1] - ky[i]);
                float s = (float) Math.sin(Math.PI * t);
                r *= 1 - pulse * s * s;
            }
            return r;
        }

        float axisXExact(float y) {
            float x = hermite(kx, Math.min(y, yc));
            if (y > yc) { float q = (y - yc) / hcap; x += cpsi * APEX * aTop * q * q; }
            return x;
        }

        float axisZExact(float y) {
            float z = hermite(kz, Math.min(y, yc));
            if (y > yc) { float q = (y - yc) / hcap; z += spsi * APEX * aTop * q * q; }
            return z;
        }

        /** Cross-section radius at height y (clamped to the dome base) and azimuth phi; rho damps the azimuthal terms near the axis. */
        float radius(float y, float phi, float rho) {
            float[] w = new float[st];
            row(y, w);
            return radius(w, (float) Math.cos(phi), (float) Math.sin(phi), rho);
        }

        /** Same, from a table row and the direction cosines of the azimuth. */
        private float radius(float[] w, float c, float s, float rho) {
            float r = w[2];
            float damp = smooth(.15f * r, .6f * r, rho);
            float c2 = c * c - s * s, s2 = 2 * c * s, c3 = c * (4 * c * c - 3), s3 = s * (3 - 4 * s * s);
            float cp = Math.max(0, c * cpsi + s * spsi);
            float ang = a2 * (c2 * w[3] + s2 * w[4]) + a3 * (c3 * w[5] + s3 * w[6]) + w[7] * cp * (float) Math.sqrt(cp);
            float bump = 0;
            for (int k = 0; k < st - 9; k++) {
                float sv = w[9 + k];
                if (sv < 1e-3f) continue;
                float cd = c * shc[k] + s * shs[k];
                bump += sv * (float) Math.exp(kappa * (cd - 1));
            }
            return r * (1 + damp * ang) + damp * bump;
        }

        /** Implicit side function: horizontal distance from the (sheared) axis minus the cross-section radius. */
        private float side(float x, float y, float z, float[] unused) {
            float g = (Math.min(y, yc) - yb) / TAB;
            int i = Math.max(0, Math.min((int) g, tabS.length / st - 2));
            float f = Math.max(0, Math.min(1, g - i));
            int a = i * st, b = a + st;
            float[] t = tabS;
            float dx = x - (t[a] + (t[b] - t[a]) * f), dz = z - (t[a + 1] + (t[b + 1] - t[a + 1]) * f);
            float rho = (float) Math.sqrt(dx * dx + dz * dz);
            float c = rho > 1e-4f ? dx / rho : 1, s = rho > 1e-4f ? dz / rho : 0;
            float r = t[a + 2] + (t[b + 2] - t[a + 2]) * f;
            float damp = smooth(.15f * r, .6f * r, rho);
            float c2 = c * c - s * s, s2 = 2 * c * s, c3 = c * (4 * c * c - 3), s3 = s * (3 - 4 * s * s);
            float cp = Math.max(0, c * cpsi + s * spsi);
            float ang = a2 * (c2 * (t[a + 3] + (t[b + 3] - t[a + 3]) * f) + s2 * (t[a + 4] + (t[b + 4] - t[a + 4]) * f))
                    + a3 * (c3 * (t[a + 5] + (t[b + 5] - t[a + 5]) * f) + s3 * (t[a + 6] + (t[b + 6] - t[a + 6]) * f))
                    + (t[a + 7] + (t[b + 7] - t[a + 7]) * f) * cp * (float) Math.sqrt(cp);
            float bump = 0;
            for (int k = 0; k < st - 9; k++) {
                float sv = t[a + 9 + k] + (t[b + 9 + k] - t[a + 9 + k]) * f;
                if (sv < 1e-3f) continue;
                bump += sv * (float) Math.exp(kappa * (c * shc[k] + s * shs[k] - 1));
            }
            return rho - (r * (1 + damp * ang) + damp * bump);
        }

        /** Cheap lower bound of the side distance (radius bound of the row, ~gradient-normalised). */
        float lowerSide(float x, float y, float z) {
            float g = (Math.min(Math.max(y, yb), yc) - yb) / TAB;
            int i = Math.max(0, Math.min((int) g, tabS.length / st - 1)), o = i * st;
            float dx = x - tabS[o], dz = z - tabS[o + 1];
            float rho = (float) Math.sqrt(dx * dx + dz * dz);
            return (rho - tabS[o + 8] - 2 * TAB) / 1.6f;
        }

        /** Approximate signed distance (gradient-normalised side, ellipsoid-bound dome, flat-bottom guard). */
        public float distance(float x, float y, float z) {
            // Outside the AABB by more than 120 blocks: the box distance is a valid lower bound (classification only).
            float ox = Math.max(Math.max(box[0] - x, x - box[3]), 0), oy = Math.max(Math.max(box[1] - y, y - box[4]), 0), oz = Math.max(Math.max(box[2] - z, z - box[5]), 0);
            float out = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (out > 120) return out;
            float d;
            float[] w = null;
            if (y > yc) {
                float dx = x - axisX(y), dz = z - axisZ(y);
                float rho = (float) Math.sqrt(dx * dx + dz * dz);
                // Dome radius at this azimuth = the side radius at yc (side() returns rho - radius).
                float rp = rho - side(axisX(yc) + dx, yc, axisZ(yc) + dz, null);
                d = ConvectiveSystemT260.ellipsoid(dx, y - yc, dz, rp, hcap, rp);
            } else {
                float lb = lowerSide(x, y, z);
                if (lb > 60) return Math.max(lb, yb - y);
                // Tetrahedral stencil (4 evaluations): value = their mean, gradient from their differences.
                float h = 1.5f;
                float a = side(x + h, y - h, z - h, w), b = side(x - h, y - h, z + h, w), c = side(x - h, y + h, z - h, w), e = side(x + h, y + h, z + h, w);
                float f = (a + b + c + e) * .25f;
                if (Math.abs(f) > 60) return Math.max(f * .5f, yb - y); // beyond the band: a conservative value suffices
                float gx = (a - b - c + e) / (4 * h), gy = (-a - b + c + e) / (4 * h), gz = (-a + b - c + e) / (4 * h);
                float g = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
                d = f / Math.max(g, .5f);
            }
            return Math.max(d, yb - y);
        }
    }

    /** Outward unit normal of a column (central differences of its distance). */
    static float[] normal(Column c, float x, float y, float z) {
        float h = 2f;
        float gx = c.distance(x + h, y, z) - c.distance(x - h, y, z), gy = c.distance(x, y + h, z) - c.distance(x, y - h, z), gz = c.distance(x, y, z + h) - c.distance(x, y, z - h);
        float n = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
        return n < 1e-6f ? new float[]{0, 1, 0} : new float[]{gx / n, gy / n, gz / n};
    }

    /**
     * B growth bulges of one column: oblate domes packed on its surface (Poisson-disc, centres ~1 mean radius apart, so
     * neighbours overlap), placed where growth is active (upper part, upward-facing surfaces; a sparse older population
     * lower down), sized with the local column radius, axis leaning upward; active tier-1 domes carry 2-4 smaller domes
     * on their upper side. Rows: {cx, cy, cz, ux, uy, uz, rn, rt, tier}.
     */
    static List<float[]> bulges(Column c, Random r) {
        List<float[]> out = new ArrayList<>();
        float lo = Math.max(c.yb, 0), span = c.yTop - lo;
        float rmax = 0;
        for (float a : c.ka) rmax = Math.max(rmax, a);
        List<float[]> acc = new ArrayList<>(); // surface point xyz, radius, u
        int fails = 0, max = Math.round(BULGE_MAX);
        // Only spacing rejections count as failures (activity / area rejections are the sampling density, not saturation).
        for (int cand = 0; cand < 12000 && fails < 300 && acc.size() < max; cand++) {
            float y = lo + span * r.nextFloat(), phi = (float) (r.nextFloat() * Math.PI * 2);
            float sx, sz;
            if (y > c.yc) {
                float rp = c.radius(c.yc, phi, 1e9f), q = (y - c.yc) / c.hcap, rho = rp * (float) Math.sqrt(Math.max(0, 1 - q * q));
                sx = c.axisX(y) + rho * (float) Math.cos(phi); sz = c.axisZ(y) + rho * (float) Math.sin(phi);
            } else {
                float rr = c.radius(y, phi, 1e9f);
                if (r.nextFloat() > rr / (rmax * 1.5f)) continue; // side area is proportional to the radius
                sx = c.axisX(y) + rr * (float) Math.cos(phi); sz = c.axisZ(y) + rr * (float) Math.sin(phi);
            }
            float u = (y - lo) / span;
            float[] n = normal(c, sx, y, sz);
            float w = BULGE_FLOOR + (1 - BULGE_FLOOR) * smooth(.25f, .9f, u) * (.4f + .6f * Math.max(0, n[1]));
            if (r.nextFloat() > w) continue;
            // Large fresh domes high up; broad, shallow swells on the older flanks (never small isolated pimples).
            float rad = BULGE_R * c.r0(Math.min(y, c.yc)) * (.7f + .3f * u) * (.6f + .8f * r.nextFloat());
            boolean ok = true;
            for (float[] a : acc) {
                float dx = a[0] - sx, dy = a[1] - y, dz = a[2] - sz, need = BULGE_SPACING * .5f * (a[3] + rad);
                if (dx * dx + dy * dy + dz * dz < need * need) { ok = false; break; }
            }
            if (!ok) { fails++; continue; }
            fails = 0;
            acc.add(new float[]{sx, y, sz, rad, u, n[0], n[1], n[2]});
        }
        for (float[] a : acc) {
            float ux = a[5], uy = a[6] + BULGE_UP, uz = a[7];
            float un = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= un; uy /= un; uz /= un;
            // Older, lower bulges are shallower (protrusion grows with the activity height u).
            float rt = a[3], rn = BULGE_N * rt, c0 = (BULGE_PROT * (.4f + .6f * smooth(.2f, .75f, a[4])) - 1) * rn;
            float[] b1 = {a[0] + ux * c0, a[1] + uy * c0, a[2] + uz * c0, ux, uy, uz, rn, rt, 1};
            out.add(b1);
        }
        int n1 = out.size();
        for (int i = 0; i < n1; i++) {
            float[] b1 = out.get(i);
            float u = acc.get(i)[4];
            if (u < .3f) continue;
            Elem parent = new Elem(b1[0], b1[1], b1[2], b1[3], b1[4], b1[5], b1[6], b1[7], 0);
            float[] f = parent.frame;
            // Tier 2: Poisson-packed over the dome's upper surface (within 70 degrees of its axis), count by area.
            float r2 = BULGE2_R * b1[7];
            int want = Math.min(Math.round(BULGE2_MAX), Math.max(2, Math.round(2.2f * (b1[7] / r2) * (b1[7] / r2) * (.5f + .5f * smooth(.3f, .8f, u)))));
            List<float[]> placed = new ArrayList<>();
            for (int cand = 0; cand < want * 30 && placed.size() < want; cand++) {
                float tilt = (float) Math.acos(1 - r.nextFloat() * (1 - Math.cos(Math.toRadians(70)))), az = (float) (r.nextFloat() * Math.PI * 2);
                float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt), ca = (float) Math.cos(az), sa = (float) Math.sin(az);
                float dx = f[0] * ct + (f[3] * ca + f[6] * sa) * st, dy = f[1] * ct + (f[4] * ca + f[7] * sa) * st, dz = f[2] * ct + (f[5] * ca + f[8] * sa) * st;
                if (dy < -.2f) continue; // growth faces up and out, never down
                float lo2 = 0, hi2 = Math.max(b1[6], b1[7]) * 1.5f;
                for (int it = 0; it < 24; it++) {
                    float m = (lo2 + hi2) * .5f;
                    if (parent.distance(b1[0] + dx * m, b1[1] + dy * m, b1[2] + dz * m) < 0) lo2 = m; else hi2 = m;
                }
                float ts = (lo2 + hi2) * .5f, sx = b1[0] + dx * ts, sy = b1[1] + dy * ts, sz = b1[2] + dz * ts;
                float rt = r2 * (.7f + .6f * r.nextFloat());
                boolean ok = true;
                for (float[] q : placed) {
                    float ex = q[0] - sx, ey = q[1] - sy, ez = q[2] - sz, need = BULGE_SPACING * .5f * (q[3] + rt);
                    if (ex * ex + ey * ey + ez * ez < need * need) { ok = false; break; }
                }
                if (!ok) continue;
                placed.add(new float[]{sx, sy, sz, rt});
                float uy = dy + .25f, un = (float) Math.sqrt(dx * dx + uy * uy + dz * dz);
                float nx = dx / un, ny = uy / un, nz = dz / un;
                float rn = BULGE_N * rt, c0 = (BULGE_PROT - 1) * rn;
                out.add(new float[]{sx + nx * c0, sy + ny * c0, sz + nz * c0, nx, ny, nz, rn, rt, 2});
            }
        }
        return out;
    }

    static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    // ---------------------------------------------------------------- field

    /** Bench instrumentation (single-threaded sandbox only): evaluated columns / ellipsoids, skipped elements. */
    public static boolean COUNT = false;
    /** Single-component shortcut (measured: no gain, the lower mass touches every cell; kept off). */
    static boolean TWO_PASS = false;
    public static long nCol, nEll, nSkip;

    /** One fold element: an ellipsoid or a growth column, with its blend radius. */
    static final class Elem {
        final float x, y, z, rx, ry, rz, k;
        final Column col;
        final float[] box;
        final float[] frame; // oriented oblate lump: normal u, tangents t1 t2 (null: axis-aligned ellipsoid)
        Elem(Bubble b, float k) {
            x = b.x(); y = b.y(); z = b.z(); rx = b.rx(); ry = b.ry(); rz = b.rz(); this.k = k; col = null; frame = null;
            box = new float[]{x - rx, y - ry, z - rz, x + rx, y + ry, z + rz};
        }
        Elem(Column c, float k) { x = y = z = rx = ry = rz = 0; this.k = k; col = c; box = c.box; frame = null; }
        /** Oblate bulge centred at (cx, cy, cz), normal semi-axis rn along unit u, tangential semi-axis rt. */
        Elem(float cx, float cy, float cz, float ux, float uy, float uz, float rn, float rt, float k) {
            x = cx; y = cy; z = cz; rx = rt; ry = rn; rz = rt; this.k = k; col = null;
            float ax = Math.abs(ux) < .9f ? 1 : 0, ay = 1 - ax;
            float t1x = uy * 0 - uz * ay, t1y = uz * ax - ux * 0, t1z = ux * ay - uy * ax;
            float n1 = (float) Math.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
            t1x /= n1; t1y /= n1; t1z /= n1;
            float t2x = uy * t1z - uz * t1y, t2y = uz * t1x - ux * t1z, t2z = ux * t1y - uy * t1x;
            frame = new float[]{ux, uy, uz, t1x, t1y, t1z, t2x, t2y, t2z};
            float m = Math.max(rn, rt);
            box = new float[]{x - m, y - m, z - m, x + m, y + m, z + m};
        }
        float distance(float px, float py, float pz) {
            if (COUNT) { if (col != null) nCol++; else nEll++; }
            if (col != null) return col.distance(px, py, pz);
            float dx = px - x, dy = py - y, dz = pz - z;
            if (frame == null) return ConvectiveSystemT260.ellipsoid(dx, dy, dz, rx, ry, rz);
            float a = dx * frame[0] + dy * frame[1] + dz * frame[2];
            float b = dx * frame[3] + dy * frame[4] + dz * frame[5];
            float c = dx * frame[6] + dy * frame[7] + dz * frame[8];
            return ConvectiveSystemT260.ellipsoid(b, a, c, rx, ry, rz);
        }
        /** Cheap lower bound of {@link #distance} for columns (box distance); -inf for ellipsoids (cheap anyway). */
        float lower(float px, float py, float pz) {
            if (col == null) {
                float dx = px - x, dy = py - y, dz = pz - z, m = Math.max(rx, Math.max(ry, rz));
                return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - m;
            }
            float ox = Math.max(Math.max(box[0] - px, px - box[3]), 0), oy = Math.max(Math.max(box[1] - py, py - box[4]), 0), oz = Math.max(Math.max(box[2] - pz, pz - box[5]), 0);
            float bd = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
            return py <= col.yc && py >= col.yb ? Math.max(bd, col.lowerSide(px, py, pz)) : bd;
        }
        /** True when this element certainly lies at least t beyond the point (sqrt-free for ellipsoids). */
        boolean beyond(float px, float py, float pz, float t) {
            if (col != null) return lower(px, py, pz) >= t;
            float dx = px - x, dy = py - y, dz = pz - z, m = Math.max(rx, Math.max(ry, rz)) + t;
            return t + Math.max(rx, Math.max(ry, rz)) > 0 && dx * dx + dy * dy + dz * dz >= m * m;
        }
        boolean near(Bounds b, float reach) {
            return !(box[0] - reach > b.maxX() || box[3] + reach < b.minX() || box[1] - reach > b.maxY() || box[4] + reach < b.minY()
                    || box[2] - reach > b.maxZ() || box[5] + reach < b.minZ());
        }
    }

    /** Turret: its stack (thermals, or one column), its lumps, and the blend that joins it to the cell. */
    record Turret(Elem[] stack, Elem[] lumps, float k) {}
    /** Component: D-order flat fold ({@code flat}) or hierarchical turrets. */
    record Comp(Elem[] flat, Turret[] turrets) {
        /** Largest blend an element of this component can still receive (its own k plus its turret's). */
        float kmax() {
            float m = 0;
            if (flat != null) for (Elem e : flat) m = Math.max(m, e.k);
            else for (Turret t : turrets) { for (Elem e : t.stack()) m = Math.max(m, e.k + t.k()); for (Elem e : t.lumps()) m = Math.max(m, e.k + t.k()); }
            return m;
        }
        /** Cheap lower bound of the component's distance at a point. */
        float lower(float x, float y, float z) {
            float m = Float.MAX_VALUE;
            if (flat != null) for (Elem e : flat) m = Math.min(m, e.lower(x, y, z));
            else for (Turret t : turrets) { for (Elem e : t.stack()) m = Math.min(m, e.lower(x, y, z)); for (Elem e : t.lumps()) m = Math.min(m, e.lower(x, y, z)); }
            return m;
        }
    }

    /** Composed distance over groups -> components -> (turrets ->) elements; optionally density-remapped. */
    public static final class Field implements SparseBrickVolume.Field {
        final Comp[][] groups;
        final Elem[][] detail; // per group: growth bulges seated on the merged mass (B population), may be empty
        final float link, baseY, baseBlend, reach, hp, feather, band;
        final Arms arms;
        final long seed;

        Field(Comp[][] groups, Elem[][] detail, float link, float baseY, float baseBlend, float reach, float hp, float feather, Arms arms, long seed) {
            this.groups = groups; this.detail = detail; this.link = link; this.baseY = baseY; this.baseBlend = baseBlend; this.reach = reach;
            this.hp = hp; this.feather = feather; this.arms = arms; this.seed = seed; this.band = feather * 2.5f;
        }

        @Override public SparseBrickVolume.Field local(Bounds box) {
            List<Comp[]> gs = new ArrayList<>();
            List<Elem[]> ds = new ArrayList<>();
            for (int gi = 0; gi < groups.length; gi++) {
                List<Comp> cs = new ArrayList<>();
                for (Comp c : groups[gi]) {
                    Comp k = cull(c, box);
                    if (k != null) cs.add(k);
                }
                List<Elem> dl = new ArrayList<>();
                for (Elem e : detail[gi]) if (e.near(box, band + 2 * e.k + baseBlend + 12)) dl.add(e);
                if (cs.isEmpty() && dl.isEmpty()) continue;
                gs.add(cs.toArray(new Comp[0]));
                ds.add(dl.toArray(new Elem[0]));
            }
            return new Field(gs.toArray(new Comp[0][]), ds.toArray(new Elem[0][]), link, baseY, baseBlend, reach, hp, feather, arms, seed);
        }

        private Comp cull(Comp c, Bounds box) {
            if (c.flat() != null) {
                List<Elem> keep = new ArrayList<>();
                for (Elem e : c.flat()) if (e.near(box, reach)) keep.add(e);
                return keep.isEmpty() ? null : new Comp(keep.toArray(new Elem[0]), null);
            }
            List<Turret> ts = new ArrayList<>();
            for (Turret t : c.turrets()) {
                List<Elem> s = new ArrayList<>(), l = new ArrayList<>();
                for (Elem e : t.stack()) if (e.near(box, reach)) s.add(e);
                for (Elem e : t.lumps()) if (e.near(box, reach)) l.add(e);
                if (!s.isEmpty() || !l.isEmpty()) ts.add(new Turret(s.toArray(new Elem[0]), l.toArray(new Elem[0]), t.k()));
            }
            return ts.isEmpty() ? null : new Comp(null, ts.toArray(new Turret[0]));
        }

        /** The composed (unremapped) signed distance. */
        public float raw(float x, float y, float z) {
            float result = ConvectiveSystemT260.BIG;
            boolean cut = arms.merge() || arms.growth() || arms.density();
            float tail = band + link + baseBlend + 12;
            Comp only = null;
            if (cut && TWO_PASS) {
                // Two passes: when a single component can reach the band here, the link blend cannot act, so that
                // component is evaluated with the tighter cutoff (no link term) and every other component is skipped.
                int near = 0;
                for (Comp[] g : groups) for (Comp c : g) if (c.lower(x, y, z) < tail + 2 * c.kmax()) { near++; only = c; }
                if (near == 0) return ConvectiveSystemT260.BIG;
                if (near == 1) {
                    float d = comp(only, x, y, z, band + baseBlend + 12);
                    if (d >= ConvectiveSystemT260.BIG) return ConvectiveSystemT260.BIG;
                    return ConvectiveSystemT260.smax(d, baseY - y, baseBlend);
                }
            }
            for (int gi = 0; gi < groups.length; gi++) {
                float dg = ConvectiveSystemT260.BIG;
                for (Comp c : groups[gi]) {
                    float dc = comp(c, x, y, z, tail);
                    if (dc >= ConvectiveSystemT260.BIG) continue;
                    dg = dg >= ConvectiveSystemT260.BIG ? dc : ConvectiveSystemT260.smin(dg, dc, link);
                }
                // Growth bulges blend onto the merged mass with their own small radius only (no link / turret term).
                float td = band + baseBlend + 12;
                for (Elem e : detail[gi]) {
                    if (e.beyond(x, y, z, td + 2 * e.k)) continue;
                    float d = e.distance(x, y, z);
                    dg = dg >= ConvectiveSystemT260.BIG ? d : ConvectiveSystemT260.smin(dg, d, e.k);
                }
                result = Math.min(result, dg);
            }
            if (result >= ConvectiveSystemT260.BIG) return ConvectiveSystemT260.BIG;
            return ConvectiveSystemT260.smax(result, baseY - y, baseBlend);
        }

        /**
         * Per-sample cutoff (reconstruction arms only; D-mode keeps D's exact path): an element whose cheap lower bound
         * exceeds band + 2 k + the blends still to come cannot move any value inside the stored band, so it is skipped.
         */
        private float comp(Comp c, float x, float y, float z, float tail) {
            float BIG = ConvectiveSystemT260.BIG;
            boolean cut = arms.merge() || arms.growth() || arms.density();
            if (c.flat() != null) {
                float dc = BIG;
                for (Elem e : c.flat()) {
                    if (cut && e.beyond(x, y, z, tail + 2 * e.k)) continue;
                    if (e.col != null && dc < BIG && e.lower(x, y, z) >= dc + e.k) continue; // smin would return dc exactly
                    float d = e.distance(x, y, z);
                    dc = dc >= BIG ? d : ConvectiveSystemT260.smin(dc, d, e.k);
                }
                return dc;
            }
            float dc = BIG;
            for (Turret t : c.turrets()) {
                float dt = BIG, tt = tail + t.k();
                for (Elem e : t.stack()) {
                    if (e.beyond(x, y, z, tt + 2 * e.k)) continue;
                    if (e.col != null && dt < BIG && e.lower(x, y, z) >= dt + e.k) continue; // smin would return dt exactly
                    float d = e.distance(x, y, z);
                    dt = dt >= BIG ? d : ConvectiveSystemT260.smin(dt, d, e.k);
                }
                for (Elem e : t.lumps()) {
                    if (e.beyond(x, y, z, tt + 2 * e.k)) continue;
                    float d = e.distance(x, y, z); dt = dt >= BIG ? d : ConvectiveSystemT260.smin(dt, d, e.k); }
                if (dt >= BIG) continue;
                dc = dc >= BIG ? dt : ConvectiveSystemT260.smin(dc, dt, t.k());
            }
            return dc;
        }

        @Override public float distance(float x, float y, float z) {
            float d = stored(x, y, z);
            // T260.6 audit: the composed fields of the reconstruction arms are not Lipschitz-1.5 deep inside (oriented
            // ellipsoid bounds, normalised columns), so the bake's coarse FULL skip mis-filled a few interior cells
            // (AB/BC/ABC; never an EMPTY skip). Flooring the value at -FULL_SKIP_FLOOR (beyond the stored band, so no
            // stored code changes) keeps every cell centre above the FULL-skip threshold: interior cells are classified
            // exactly from their samples. D keeps D's exact path.
            return arms.merge() || arms.growth() || arms.density() ? Math.max(d, -FULL_SKIP_FLOOR) : d;
        }

        private float stored(float x, float y, float z) {
            float d = raw(x, y, z);
            if (!arms.density() || d >= feather * .5f) return d;
            float depth = -d;
            if (depth >= DEEP_1) return d + DEEP_1 - feather * .5f;
            float m = interior(x, y, z, depth);
            float ramp = Math.max(0, Math.min(1, .5f - d / feather));
            return feather * (.5f - ramp * m);
        }

        /** C: the interior density factor m(x) at depth (blocks below the surface). */
        public float interior(float x, float y, float z, float depth) {
            float hgt = Math.max(0, (y - baseY) / hp);
            float p = P0 + (1 - P0) * smooth(0, PH, hgt);
            // Outward normal's vertical component from the vertical distance derivative (12-block stencil, smoothed).
            float near = 1 - smooth(45, 60, depth), entr = 1;
            if (near > 0) {
                float ny = (raw(x, y + 8, z) - (-depth)) / 8f; // one-sided vertical derivative of the distance
                float up = Math.max(0, Math.min(1, ny));
                float lateral = 1 - up * up;
                entr = 1 - ENTRAIN * lateral * (float) Math.exp(-depth / ENTRAIN_L) * near;
            }
            float nz = .65f * noise(x / NOISE_S1, y / NOISE_S1, z / NOISE_S1, seed) + .35f * noise(x / NOISE_S2, y / NOISE_S2, z / NOISE_S2, seed ^ 0x5DEECE66DL);
            float m = Math.max(M_MIN, Math.min(1, p * entr * (1 + NOISE * nz)));
            float fade = smooth(DEEP_0, DEEP_1, depth);
            return m + (1 - m) * fade;
        }
    }

    /** Smooth value noise in [-1, 1] (quintic interpolation of lattice hashes). */
    static float noise(float x, float y, float z, long seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        float fx = x - ix, fy = y - iy, fz = z - iz;
        float ux = fx * fx * fx * (fx * (fx * 6 - 15) + 10), uy = fy * fy * fy * (fy * (fy * 6 - 15) + 10), uz = fz * fz * fz * (fz * (fz * 6 - 15) + 10);
        float c000 = hash(ix, iy, iz, seed), c100 = hash(ix + 1, iy, iz, seed), c010 = hash(ix, iy + 1, iz, seed), c110 = hash(ix + 1, iy + 1, iz, seed);
        float c001 = hash(ix, iy, iz + 1, seed), c101 = hash(ix + 1, iy, iz + 1, seed), c011 = hash(ix, iy + 1, iz + 1, seed), c111 = hash(ix + 1, iy + 1, iz + 1, seed);
        float x00 = c000 + (c100 - c000) * ux, x10 = c010 + (c110 - c010) * ux, x01 = c001 + (c101 - c001) * ux, x11 = c011 + (c111 - c011) * ux;
        float y0 = x00 + (x10 - x00) * uy, y1 = x01 + (x11 - x01) * uy;
        return y0 + (y1 - y0) * uz;
    }

    private static float hash(int x, int y, int z, long seed) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + y * 0x8CB92BA72F3D8DD7L + z * 0xD6E8FEB86659FD93L;
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29;
        return ((h >>> 40) / (float) (1L << 24)) * 2 - 1;
    }

    // ---------------------------------------------------------------- build

    /** B: one growth column per turret, each with its own stream (component, turret index within the component). */
    public static Column[] columns(Structure s) {
        Recipe r = s.recipe;
        Column[] cols = new Column[s.turrets.size()];
        int[] perComp = new int[r.components().size()];
        for (int t = 0; t < cols.length; t++) {
            int comp = s.turretComp.get(t);
            List<Bubble> th = new ArrayList<>();
            for (int i : s.turrets.get(t)) th.add(r.bubbles().get(i));
            cols[t] = new Column(th, new Random(r.seed() * 0x9E3779B97F4A7C15L + comp * 1000003L + perComp[comp]++ * 7919L + 0x2606));
        }
        return cols;
    }

    /** B: the growth-dome population of every column (rows: centre xyz, axis xyz, rn, rt, tier). */
    public static List<List<float[]>> population(Recipe r, Column[] cols) {
        List<List<float[]>> pops = new ArrayList<>();
        for (int t = 0; t < cols.length; t++)
            pops.add(bulges(cols[t], new Random(r.seed() * 0x2545F4914F6CDD1DL + t * 0x9E3779B97F4A7C15L + 0x2606B)));
        return pops;
    }

    /** Builds the arm's field from D's recipe; {@code enabled} restricts components (isolated views). */
    public static Field field(Recipe r, Arms arms, boolean[] enabled, float band, float feather) {
        return field(new Structure(r), arms, enabled, band, feather);
    }

    /** Same, over a given anatomy (a developing system reuses its original structure). */
    public static Field field(Structure s, Arms arms, boolean[] enabled, float band, float feather) {
        Recipe r = s.recipe;
        List<Bubble> b = r.bubbles();
        int nb = b.size();
        // B: one column per turret; every lump becomes an oblate bulge along its own growth direction (D's parent-centre ->
        // lump-centre direction), re-seated on the new parent surface (a tier-1/3 lump on its turret's column, a tier-2
        // lump on its re-seated parent bulge).
        Column[] cols = new Column[s.turrets.size()];
        float[][] lg = new float[nb][]; // oriented lump: centre xyz, normal xyz, rn, rt
        if (arms.growth()) {
            cols = columns(s);
            for (int i = 0; i < nb; i++) {
                int p = s.parent[i];
                if (p < 0) continue;
                Bubble l = b.get(i), par = b.get(p);
                float ux = l.x() - par.x(), uy = l.y() - par.y(), uz = l.z() - par.z();
                float len = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
                ux /= len; uy /= len; uz /= len;
                float ox, oy, oz;
                java.util.function.DoubleUnaryOperator inside;
                if (l.tier() == 2) {
                    float[] q = lg[p];
                    ox = q[0]; oy = q[1]; oz = q[2];
                    Elem pe = new Elem(q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], 0);
                    final float fx = ux, fy = uy, fz = uz, gx = ox, gy = oy, gz = oz;
                    inside = t -> pe.distance(gx + fx * (float) t, gy + fy * (float) t, gz + fz * (float) t);
                } else {
                    ox = par.x(); oy = par.y(); oz = par.z();
                    Column c = cols[s.turretOf[p]];
                    final float fx = ux, fy = uy, fz = uz, gx = ox, gy = oy, gz = oz;
                    inside = t -> c.distance(gx + fx * (float) t, gy + fy * (float) t, gz + fz * (float) t);
                }
                float lo = 0, hi = len * 2f;
                for (int g = 0; g < 10 && inside.applyAsDouble(hi) < 0; g++) hi *= 1.5f;
                for (int it = 0; it < 32; it++) {
                    float m = (lo + hi) * .5f;
                    if (inside.applyAsDouble(m) < 0) lo = m; else hi = m;
                }
                float ts = (lo + hi) * .5f, lr = Math.max(l.rx(), l.rz());
                float rn = LUMP_N * lr, rt = LUMP_T * lr, c0 = ts + (PROTRUDE - 1) * rn;
                lg[i] = new float[]{ox + ux * c0, oy + uy * c0, oz + uz * c0, ux, uy, uz, rn, rt};
            }
        }
        // B growth-bulge population (replaces D's lumps when BULGES > 0), per turret.
        boolean pop = arms.growth() && BULGES > 0;
        List<List<float[]>> pops = new ArrayList<>();
        if (pop) pops = population(r, cols);
        final float[][] lgf = lg;
        java.util.function.BiFunction<Integer, Float, Elem> lump = (i, k) -> {
            float[] q = lgf[i];
            return q == null ? new Elem(b.get(i), k) : new Elem(q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], k);
        };
        List<Component> comps = r.components();
        int maxGroup = 0;
        for (Component c : comps) maxGroup = Math.max(maxGroup, c.group());
        float maxK = r.linkBlend();
        List<List<Comp>> groups = new ArrayList<>();
        List<List<Elem>> details = new ArrayList<>();
        for (int g = 0; g <= maxGroup; g++) { groups.add(new ArrayList<>()); details.add(new ArrayList<>()); }
        for (int ci = 0; ci < comps.size(); ci++) {
            if (enabled != null && !enabled[ci]) continue;
            Component c = comps.get(ci);
            Comp built;
            if (!arms.merge()) {
                // D order: macro (thermals, or one column per turret), then tier 1/3 lumps, then tier 2, each with D's k.
                List<Elem> flat = new ArrayList<>();
                boolean[] colDone = new boolean[cols.length];
                for (int i = 0; i < nb; i++) {
                    Bubble x = b.get(i);
                    if (x.comp() != ci) continue;
                    float k = c.blend() * ConvectiveSystemT260.TIER_BLEND[Math.min(x.tier(), ConvectiveSystemT260.TIER_BLEND.length - 1)];
                    if (arms.growth() && s.parent[i] < 0) {
                        int t = s.turretOf[i];
                        if (colDone[t]) continue;
                        colDone[t] = true;
                        flat.add(new Elem(cols[t], c.blend()));
                    } else if (s.parent[i] >= 0) {
                        if (!pop) flat.add(lump.apply(i, k));
                    } else flat.add(new Elem(x, k));
                    maxK = Math.max(maxK, k);
                }
                if (pop) for (int tier = 1; tier <= 2; tier++) for (int t = 0; t < cols.length; t++) {
                    if (s.turretComp.get(t) != ci) continue;
                    float k = c.blend() * ConvectiveSystemT260.TIER_BLEND[tier];
                    for (float[] q : pops.get(t)) if (q[8] == tier) details.get(c.group()).add(new Elem(q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], k));
                }
                if (flat.isEmpty()) continue;
                built = new Comp(flat.toArray(new Elem[0]), null);
            } else {
                List<Turret> ts = new ArrayList<>();
                for (int t = 0; t < s.turrets.size(); t++) {
                    if (s.turretComp.get(t) != ci) continue;
                    int[] th = s.turrets.get(t);
                    List<Elem> stack = new ArrayList<>(), lumps = new ArrayList<>();
                    float mean = 0;
                    for (int i : th) mean += (b.get(i).rx() + b.get(i).rz()) * .5f;
                    mean /= th.length;
                    if (arms.growth()) stack.add(new Elem(cols[t], 0));
                    else for (int i : th) {
                        Bubble x = b.get(i);
                        float k = STACK_K * (x.rx() + x.rz()) * .5f;
                        stack.add(new Elem(x, k));
                        maxK = Math.max(maxK, k);
                    }
                    if (pop) for (float[] q : pops.get(t)) {
                        float k = (q[8] == 2 ? LUMP2_K : LUMP_K) * q[7];
                        details.get(c.group()).add(new Elem(q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], k));
                    }
                    for (int i = 0; i < nb && !pop; i++) {
                        if (s.parent[i] < 0 || s.turretOf[i] != t) continue;
                        Bubble x = b.get(i);
                        float k = (x.tier() == 2 ? LUMP2_K : LUMP_K) * Math.max(x.rx(), x.rz());
                        lumps.add(lump.apply(i, k));
                        maxK = Math.max(maxK, k);
                    }
                    float kt = Math.max(c.blend(), TURRET_K * mean);
                    maxK = Math.max(maxK, kt);
                    ts.add(new Turret(stack.toArray(new Elem[0]), lumps.toArray(new Elem[0]), kt));
                }
                if (ts.isEmpty()) continue;
                built = new Comp(null, ts.toArray(new Turret[0]));
            }
            groups.get(c.group()).add(built);
        }
        List<Comp[]> gs = new ArrayList<>();
        List<Elem[]> ds = new ArrayList<>();
        for (int g = 0; g < groups.size(); g++) {
            if (groups.get(g).isEmpty() && details.get(g).isEmpty()) continue;
            gs.add(groups.get(g).toArray(new Comp[0]));
            ds.add(details.get(g).toArray(new Elem[0]));
        }
        float link = arms.merge() ? LINK_K : r.linkBlend();
        maxK = Math.max(maxK, link);
        // D-mode reach is D's own (band + 2 x max blend + base blend); columns and the density probe add margin.
        float dMax = r.linkBlend();
        for (Component c : comps) dMax = Math.max(dMax, c.blend());
        float reach = !arms.merge() && !arms.growth() && !arms.density() ? band + 2 * dMax + r.baseBlend()
                : band + 2 * maxK + r.baseBlend() + 12;
        return new Field(gs.toArray(new Comp[0][]), ds.toArray(new Elem[0][]), link, r.baseY(), r.baseBlend(), reach, r.primaryHeight(), feather, arms, r.seed());
    }

    /** Domain: D's own domain rule plus the growth columns' radial margin. */
    public static Bounds domain(Recipe r, Arms arms, boolean[] enabled, float band) {
        Bounds d = ConvectiveSystemT260.Program.domain(r, enabled, band);
        if (!arms.growth() && !arms.merge()) return d;
        float m = arms.growth() ? .45f * maxThermal(r, enabled) : 0;
        m += arms.merge() ? LINK_K : 0;
        return new Bounds(d.minX() - m, d.minY(), d.minZ() - m, d.maxX() + m, d.maxY() + (arms.growth() ? 12 : 0) + (arms.merge() ? 24 : 0), d.maxZ() + m);
    }

    private static float maxThermal(Recipe r, boolean[] enabled) {
        float m = 0;
        for (Bubble b : r.bubbles()) if ((enabled == null || enabled[b.comp()]) && (b.tier() == 0 || b.tier() == 4)) m = Math.max(m, Math.max(b.rx(), b.rz()));
        return m;
    }

    // ---------------------------------------------------------------- program

    /** Baked arm. Geometry, framing and crown targets refer to the frozen D recipe it was derived from. */
    public static final class Program implements StormGeometryProgram {
        private final Recipe recipe;
        private final Arms arms;
        private final Field field;
        private final SparseBrickVolume volume;
        private final double fieldMs;

        public Program(Recipe recipe, Arms arms, boolean[] enabled, float voxel, float feather, float headroom) {
            long t0 = System.nanoTime();
            this.recipe = recipe;
            this.arms = arms;
            float band = feather * 2.5f;
            field = ConvectiveVolumeT2606.field(recipe, arms, enabled, band, feather);
            fieldMs = (System.nanoTime() - t0) / 1e6;
            volume = new SparseBrickVolume(field, domain(recipe, arms, enabled, band), voxel, feather, 1.5f, headroom);
        }

        public Recipe recipe() { return recipe; }
        public Arms arms() { return arms; }
        public Field field() { return field; }
        public SparseBrickVolume volume() { return volume; }
        /** CPU time to derive the arm's field from D (structure, columns, lump re-seating), excluding the brick bake. */
        public double fieldMs() { return fieldMs; }

        @Override public float radius() { return (volume.bounds().maxX() - volume.bounds().minX()) * .5f; }
        @Override public float height() { return recipe.primaryHeight(); }
        @Override public float axisX(float fraction) {
            for (Component c : recipe.components()) if (c.role() == Role.PRIMARY || c.role() == Role.PLACEHOLDER) return c.anchorX();
            return 0;
        }
        @Override public float density(float x, float y, float z, Scale scale) {
            if (!volume.bounds().contains(x, y, z)) return 0;
            return volume.density(x, y, z);
        }
        @Override public Bounds bounds() { return volume.bounds(); }
        @Override public float[] packed() { return java.util.Arrays.copyOf(volume.texture(), SparseBrickVolume.HEADER_TEXELS * 4); }
        @Override public long signature() { return volume.signature(); }
        @Override public int version() { return ConvectiveSystemT260.VERSION; }
        @Override public boolean growthDefined() { return true; }
        @Override public float[] textureData() { return volume.texture(); }
        @Override public int textureWidth() { return volume.textureWidth(); }
        @Override public int textureHeight() { return volume.textureHeight(); }
    }
}
