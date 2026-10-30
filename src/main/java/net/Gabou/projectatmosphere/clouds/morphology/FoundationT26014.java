package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.MulticellSystemT26011.Group;
import net.Gabou.projectatmosphere.clouds.morphology.MulticellSystemT26011.Plan;
import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.CellSpec;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2607.noise;
import static net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2607.stream;
import static net.Gabou.projectatmosphere.clouds.morphology.VerticalGrowthT26010.smooth;

/**
 * T260.14 experimental prototype (never production): a SHARED LOWER CONVECTIVE FOUNDATION under organised
 * neighbouring T260.11 groups. T260.13 joined one pair with one lower mass; here the organisation of the whole system
 * decides which groups share a foundation, and the foundation is built from many overlapping broad masses:
 * <ul>
 *   <li><b>organisation graph</b>: every neighbouring pair gets a persistent strength A(t) (T260.12 / T260.13 inputs:
 *   measured cell gap, live maturity, moisture, shear, seeded threshold, gradual growth / decay). Base compatibility is
 *   SOFT here (differing bases slope the foundation instead of forbidding it). Pairs that never pass their threshold
 *   stay independent; groups without an active pair get no foundation (they stay exactly T260.11);</li>
 *   <li><b>skirts</b>: every organised group spreads a broad lower mass from its own footprint (radius from footprint,
 *   moisture and organisation; pushed and stretched downshear), thick at the tower so the tower rises out of it;</li>
 *   <li><b>links</b>: 2-4 overlapping masses along a bent path between the two footprints of an active pair, near the
 *   groups first, the middle last (low strength leaves openings);</li>
 *   <li><b>shelves</b>: 0-2 seeded masses on the outer / downshear side of well-organised groups (the foundation is
 *   broader than the line of towers, with bays between them);</li>
 *   <li><b>coherent base</b>: one sloped base field interpolated from the organised groups' own condensation levels
 *   (+-50 m relief), shared by every mass, so the foundation never steps between masses and meets each tower base at
 *   its level.</li>
 * </ul>
 * Each mass: warped noisy outline (+-38 %), domed thickness that never thins to a sheet at the rim (rim keeps 30-35 % of
 * the thickness, rounded by a 140 m smooth max), folds (+-20 %), and own-scale billows on the upper surface. The masses
 * smooth-union with a 260 m blend. Pseudo-distance in model metres (y from the model base).
 */
public final class FoundationT26014 {
    private FoundationT26014() {}

    static final long S_FND = 0x0F0D2614L, S_EDGE = 0x0EDE2614L, S_SHELF = 0x05E1F264L;
    static final float DT = .5f, T0 = -14;
    /** Strength below which a pair is inactive (no foundation between the two groups). */
    public static final float ACTIVE = .05f;
    /** Smooth-union blend between foundation masses (model metres). */
    static final float MASS_BLEND = 260, DOME_BLEND = 180;
    /** Coverage below which the foundation opens (bays, holes between masses). */
    static final float HOLE = -.05f;
    /** Ellipsoid centre depth below the base, in units of the mass thickness (flanks slope outward to the base). */
    static final float CORE = .35f;

    /** One labelled component box (model metres) for the diagnostic frames. */
    public record Part(String label, float[] box) {}

    /** A persistent organisation link between two groups. */
    record Edge(int a, int b, float threshold, float pot0, float[] strength, long seed) {
        float at(float t) { return strength[Math.max(0, Math.min(strength.length - 1, Math.round((t - T0) / DT)))]; }
        float formed() { for (int k = 0; k < strength.length; k++) if (strength[k] > ACTIVE) return T0 + k * DT; return Float.NaN; }
    }

    /** The system's organisation (all neighbouring pairs and their strength histories); cached per seed. */
    record Org(Plan plan, List<Edge> edges, List<List<CellSpec>> cells, List<PersistentConvectionT2608.Scenario> scen) {}

    private static final Map<Long, Org> ORG = new ConcurrentHashMap<>();

    static Org organise(Plan p) {
        return ORG.computeIfAbsent(p.seed(), k -> {
            List<List<CellSpec>> cells = new ArrayList<>();
            List<PersistentConvectionT2608.Scenario> scen = new ArrayList<>();
            for (Group g : p.groups()) { var sc = MulticellSystemT26011.scenario(g); scen.add(sc); cells.add(sc.cells); }
            List<Edge> es = new ArrayList<>();
            int n = p.groups().size();
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
                Group a = p.groups().get(i), b = p.groups().get(j);
                if (Math.hypot(a.x() - b.x(), a.z() - b.z()) > 2.4f * p.spacing()) continue; // never neighbours
                Edge e = curve(p, i, j, cells);
                if (!Float.isNaN(e.formed())) es.add(e);
            }
            return new Org(p, List.copyOf(es), cells, scen);
        });
    }

    /** Pair strength integrated over system time (T260.12 formula; live maturity; soft base compatibility). */
    static Edge curve(Plan p, int i, int j, List<List<CellSpec>> cells) {
        Group a = p.groups().get(i), b = p.groups().get(j);
        var e = p.env();
        int steps = Math.round((MulticellSystemT26011.T_END - T0) / DT) + 1;
        Random q = stream(p.seed(), S_EDGE, i, j);
        float threshold = .22f + .38f * q.nextFloat();
        float dBase = Math.abs(ConnectionsT26012.baseOffset(p, a) - ConnectionsT26012.baseOffset(p, b));
        float base = 1 - .45f * smooth(100, 550, dBase);
        float moist = .35f + .65f * .5f * (a.env().moist() + b.env().moist());
        float axis = (float) Math.atan2(b.z() - a.z(), b.x() - a.x());
        float shear = 1 - .4f * e.shear() * Math.abs((float) Math.cos(axis - e.shearAz()));
        float pot0 = base * moist * shear * (.85f + .3f * q.nextFloat());
        float[] s = new float[steps];
        float A = 0;
        for (int k = 0; k < steps; k++) {
            float t = T0 + k * DT;
            float gap = gap(p, a, cells.get(i), b, cells.get(j), t);
            float prox = Float.isNaN(gap) ? 0 : 1 - smooth(-.1f * p.spacing(), .75f * p.spacing(), gap);
            float m = (float) Math.pow(Math.min(LowerMassT26013.liveMaturity(a, cells.get(i), t), LowerMassT26013.liveMaturity(b, cells.get(j), t)), .7);
            float pot = pot0 * prox * m;
            A += DT * (pot > threshold ? (pot - threshold) / (1 - threshold) / ConnectionsT26012.T_GROW : -1 / ConnectionsT26012.T_DECAY);
            A = Math.max(0, Math.min(1, A));
            s[k] = A;
        }
        return new Edge(i, j, threshold, pot0, s, PersistentConvectionT2608.mix(p.seed() ^ S_EDGE, (long) i * 64 + j + 1));
    }

    /** Measured gap between the closest live cells of two groups at system time t (NaN when either has none). */
    static float gap(Plan p, Group a, List<CellSpec> ca, Group b, List<CellSpec> cb, float t) {
        var e = p.env();
        float ta = t - a.birth(), tb = t - b.birth(), best = Float.NaN;
        for (CellSpec x : ca) {
            if (ta < x.birth || ta > x.decay + x.tauD) continue;
            float xx = a.x() + x.x0 + (x.vx - e.windX()) * ta, xz = a.z() + x.z0 + (x.vz - e.windZ()) * ta, xr = PersistentConvectionT2608.Scenario.baseR(x, ta);
            for (CellSpec y : cb) {
                if (tb < y.birth || tb > y.decay + y.tauD) continue;
                float yx = b.x() + y.x0 + (y.vx - e.windX()) * tb, yz = b.z() + y.z0 + (y.vz - e.windZ()) * tb, yr = PersistentConvectionT2608.Scenario.baseR(y, tb);
                float g = (float) Math.hypot(yx - xx, yz - xz) - xr - yr;
                if (Float.isNaN(best) || g < best) best = g;
            }
        }
        return best;
    }

    /** Footprint of a group from its live cells: {centroid x, z, radius}, or null. */
    static float[] footprint(Plan p, Group g, List<CellSpec> cells, float t) {
        var e = p.env();
        float tau = t - g.birth(), sx = 0, sz = 0, sw = 0;
        List<float[]> live = new ArrayList<>();
        for (CellSpec c : cells) {
            if (tau < c.birth || tau > c.decay + c.tauD) continue;
            float x = g.x() + c.x0 + (c.vx - e.windX()) * tau, z = g.z() + c.z0 + (c.vz - e.windZ()) * tau, r = PersistentConvectionT2608.Scenario.baseR(c, tau);
            live.add(new float[]{x, z, r});
            sx += x * r; sz += z * r; sw += r;
        }
        if (live.isEmpty()) return null;
        float cx = sx / sw, cz = sz / sw, R = 0;
        for (float[] c : live) R = Math.max(R, (float) Math.hypot(c[0] - cx, c[1] - cz) + c[2]);
        return new float[]{cx, cz, R};
    }

    // ---------------------------------------------------------------- resolved foundation at one time

    /** One foundation mass. kind 0 = skirt, 1 = link, 2 = shelf. */
    static final class Mass {
        final int kind;
        final String label;
        final float cx, cz, R, elong, ca, sa, T, rim, rb;
        final long seed;
        /** Cumuliform domes on the upper surface: {x, z, height of the centre above the base, radius} (persistent, seeded). */
        final float[] domes;
        Mass(int kind, String label, float cx, float cz, float R, float elong, float ang, float T, float rim, long seed) {
            this.kind = kind; this.label = label; this.cx = cx; this.cz = cz; this.R = R; this.elong = elong;
            ca = (float) Math.cos(ang); sa = (float) Math.sin(ang); this.T = T; this.rim = rim; this.seed = seed;
            rb = 1.75f * R * elong + 150; // outline <= 1.38 R in the squashed frame, plus the rounding
            Random q = new Random(seed ^ 0xD0E5D0E5L);
            int n = Math.max(4, Math.min(28, Math.round(1.2f * R * R * elong / (900f * 900f)))), nf = Math.max(3, n / 2);
            domes = new float[4 * (n + nf)];
            for (int k = 0; k < n + nf; k++) {
                boolean flank = k >= n; // flank lobes: low, on the outer slope (no smooth wall down to the base)
                float u = flank ? .78f + .2f * q.nextFloat() : .85f * (float) Math.sqrt(q.nextFloat()), a = (float) (q.nextFloat() * 2 * Math.PI);
                float lu = u * (float) Math.cos(a) * R * elong, lv = u * (float) Math.sin(a) * R;
                float top = profile(u) * T;
                float r = flank ? (.16f + .14f * q.nextFloat()) * Math.min(T, R) : (.22f + .2f * q.nextFloat()) * Math.min(T, R) * (.7f + .3f * profile(u));
                domes[4 * k] = cx + lu * ca - lv * sa;
                domes[4 * k + 1] = cz + lu * sa + lv * ca;
                domes[4 * k + 2] = flank ? Math.max(.6f * r, top * (.35f + .3f * q.nextFloat())) : top * (.55f + .3f * q.nextFloat());
                domes[4 * k + 3] = r;
            }
        }
    }

    /** Height fraction of a mass at normalised radius u: an ellipsoid whose centre lies below the base (sloped flanks). */
    static float profile(float u) { float c = CORE; return (float) Math.max(0, (Math.sqrt(Math.max(0, (1 + c) * (1 + c) - u * u * (1 + c) * (1 + c))) - c) / 1); }

    public static final class Spec {
        public final float t;
        public final long seed;
        /** 14 = T260.14; 15 = T260.15 vertical anatomy (underside relief, sleeves, compact outer extensions). */
        public final int version;
        final List<Mass> masses = new ArrayList<>();
        /** T260.15 descending underside masses: {x, z, a, b, cos, sin, depth below the base, vertical semi-axis}. */
        final List<float[]> lobes = new ArrayList<>();
        /** T260.15 broad underside depressions: {x, z, a, b, cos, sin, vertical semi-axis (centre .4 c below the base)}. */
        final List<float[]> bays = new ArrayList<>();
        /** T260.15 outer extensions (whole chain): {length, width, thickness} model metres, for the aspect report. */
        public final List<float[]> extents = new ArrayList<>();
        /** T260.18 A deep hanging masses: {x, z, a, b, cos, sin, depth below the base, vertical semi-axis, leanX, leanZ}. */
        final List<float[]> hang = new ArrayList<>();
        /** T260.18 B: per organised group {x, z, R, drop} (the base descends under the towers). */
        final List<float[]> cores = new ArrayList<>();
        /** T260.18 E lowered regions, per organised group: {x, z, a, b, cos, sin, drop}. */
        final List<float[]> drops = new ArrayList<>();
        /** T260.18 C tower roots. */
        final List<Root> roots = new ArrayList<>();
        /** Organised groups: {index, x, z, R, base, organisation}. */
        final List<float[]> anchors = new ArrayList<>();
        /** Active links: {a, b, strength}. */
        public final List<float[]> links = new ArrayList<>();
        public final List<Part> parts = new ArrayList<>();
        public int complexes, independent;
        float ybMin = Float.MAX_VALUE, ybMax = -Float.MAX_VALUE, topMax = -Float.MAX_VALUE;
        Bounds bounds;
        float[] focus; // {x, y, z, axisX, axisZ}

        Spec(float t, long seed, int version) { this.t = t; this.seed = seed; this.version = version; }

        public Bounds bounds() { return bounds; }
        /** Inspection target: the strongest active link's centre and axis (metres). */
        public float[] focus() { return focus; }
        public int[] groups() { int[] g = new int[anchors.size()]; for (int i = 0; i < g.length; i++) g[i] = (int) anchors.get(i)[0]; return g; }
        public String describe() {
            StringBuilder sb = new StringBuilder();
            for (float[] l : links) sb.append(String.format(Locale.ROOT, " G%d-G%d:%.2f", (int) l[0], (int) l[1], l[2]));
            int[] k = new int[4];
            for (Mass m : masses) k[m.kind]++;
            String v15 = "";
            if (version >= 15) {
                StringBuilder ex = new StringBuilder();
                for (float[] e : extents) ex.append(String.format(Locale.ROOT, " %.1f/%.1f", e[0] / e[1], e[0] / e[2]));
                v15 = String.format(Locale.ROOT, " sleeves=%d lobes=%d bays=%d outerAspect(plan/thick)=[%s]", k[3], lobes.size(), bays.size(), ex.toString().trim());
            }
            if (version > 15) {
                float dmax = 0, dmin = Float.MAX_VALUE;
                for (float[] h : hang) { dmax = Math.max(dmax, h[6]); dmin = Math.min(dmin, h[6]); }
                float dd = 0;
                for (float[] w : drops) dd = Math.max(dd, w[6]);
                v15 += String.format(Locale.ROOT, " T18%s hang=%d depth=%.0f..%.0fm cores=%d roots=%d drops=%d drop<=%.0fm", variant18(version), hang.size(), hang.isEmpty() ? 0 : dmin, dmax, cores.size(), roots.size(), drops.size(), DROP_MOD * dd);
            }
            return String.format(Locale.ROOT, "v%d organised=%d complexes=%d independentLive=%d skirts=%d links=%d %s=%d base=%.0f..%.0fm top<=%.0fm activeLinks=[%s]%s",
                    version, anchors.size(), complexes, independent, k[0], k[1], version >= 15 ? "outer" : "shelves", k[2], ybMin, ybMax, topMax, sb.toString().trim(), v15);
        }
    }

    /** The system's foundation at time t, or null (no organised pair: the system stays exactly T260.11). */
    public static Spec spec(Plan p, float t) { return spec(p, t, 14); }

    /** As {@link #spec(Plan, float)}; version 15 adds the T260.15 vertical anatomy (T260.14 is unchanged at 14). */
    public static Spec spec(Plan p, float t, int version) {
        Org o = organise(p);
        var env = p.env();
        int n = p.groups().size();
        float[][] fp = new float[n][];
        for (int i = 0; i < n; i++) fp[i] = footprint(p, p.groups().get(i), o.cells().get(i), t);
        float[] org = new float[n];
        List<Edge> active = new ArrayList<>();
        for (Edge e : o.edges()) {
            float A = e.at(t);
            if (A < ACTIVE || fp[e.a()] == null || fp[e.b()] == null) continue;
            active.add(e);
            org[e.a()] = Math.max(org[e.a()], A);
            org[e.b()] = Math.max(org[e.b()], A);
        }
        if (active.isEmpty()) return null;
        Spec s = new Spec(t, PersistentConvectionT2608.mix(p.seed() ^ S_FND, 1), version);
        boolean v15 = version >= 15;
        // Complexes (connected components of the active links) and independent live groups.
        int[] comp = new int[n];
        java.util.Arrays.fill(comp, -1);
        for (int i = 0; i < n; i++) {
            if (org[i] == 0 || comp[i] >= 0) continue;
            java.util.ArrayDeque<Integer> st = new java.util.ArrayDeque<>();
            st.push(i); comp[i] = s.complexes;
            while (!st.isEmpty()) {
                int u = st.pop();
                for (Edge e : active) {
                    int v = e.a() == u ? e.b() : e.b() == u ? e.a() : -1;
                    if (v >= 0 && comp[v] < 0) { comp[v] = s.complexes; st.push(v); }
                }
            }
            s.complexes++;
        }
        for (int i = 0; i < n; i++) if (fp[i] != null && org[i] == 0) s.independent++;
        // Complex centroids (outer side of each group).
        float[][] cc = new float[s.complexes][3];
        for (int i = 0; i < n; i++) if (comp[i] >= 0) { cc[comp[i]][0] += fp[i][0]; cc[comp[i]][1] += fp[i][1]; cc[comp[i]][2]++; }
        float shAz = env.shearAz(), sh = env.shear();
        for (int i = 0; i < n; i++) {
            if (org[i] == 0) continue;
            Group g = p.groups().get(i);
            float moist = g.env().moist(), m = smooth(ACTIVE, .6f, org[i]);
            float base = ConnectionsT26012.baseOffset(p, g) + 20;
            s.anchors.add(new float[]{i, fp[i][0], fp[i][1], fp[i][2], base, m});
            Random q = stream(g.seed(), S_FND, 0, 0);
            float R = fp[i][2];
            // Skirt: broad lower mass of the group, pushed and stretched downshear.
            float off = (.12f + .14f * q.nextFloat()) * R * sh;
            float sR = R * (1.25f + .55f * moist) * (.62f + .38f * m);
            float sT = (2600 + 1800 * moist) * (.6f + .4f * m) * (.85f + .3f * q.nextFloat()) * (v15 ? .85f : 1); // T260.15: the towers' sleeves rise above it
            if (rooted(version)) sT *= LENS_C; // T260.18 C: a thinner lens, the tower roots carry the height around the towers
            s.masses.add(new Mass(0, "skirt G" + i, fp[i][0] + off * (float) Math.cos(shAz), fp[i][1] + off * (float) Math.sin(shAz), sR,
                    1 + (v15 ? .22f : .45f) * sh * (.5f + q.nextFloat()), shAz + (q.nextFloat() - .5f) * .6f, sT, .3f, PersistentConvectionT2608.mix(g.seed() ^ S_FND, 1)));
            // Shelves: outer / downshear side of well-organised groups.
            float[] c = cc[comp[i]];
            float ox = fp[i][0] - c[0] / c[2], oz = fp[i][1] - c[1] / c[2], ol = (float) Math.hypot(ox, oz);
            float dirX = (ol > 1 ? ox / ol : 0) + .8f * sh * (float) Math.cos(shAz), dirZ = (ol > 1 ? oz / ol : 0) + .8f * sh * (float) Math.sin(shAz);
            float dl = (float) Math.hypot(dirX, dirZ);
            if (v15) { outer(s, i, g, fp[i], R, sR, sT, m, moist, dl > .05f ? (float) Math.atan2(dirZ, dirX) : Float.NaN); continue; }
            int shelves = q.nextFloat() < .7f ? 1 + (q.nextFloat() < .4f ? 1 : 0) : 0;
            for (int k = 0; k < shelves; k++) {
                float level = .35f + .3f * q.nextFloat();
                float ang = (dl > .05f ? (float) Math.atan2(dirZ, dirX) : (float) (q.nextFloat() * 2 * Math.PI)) + (q.nextFloat() - .5f) * 1.9f;
                float dist = (.85f + .45f * q.nextFloat()) * sR, mR = (.42f + .28f * q.nextFloat()) * R * (1 + .3f * moist);
                float mT = (1400 + 1400 * q.nextFloat()) * (.6f + .4f * moist);
                long ms = PersistentConvectionT2608.mix(g.seed() ^ S_SHELF, k + 1);
                if (m < level) continue;
                float grow = smooth(level, level + .3f, m);
                s.masses.add(new Mass(2, "shelf G" + i + "." + k, fp[i][0] + dist * (float) Math.cos(ang), fp[i][1] + dist * (float) Math.sin(ang),
                        mR * (.6f + .4f * grow), 1.3f + .8f * q.nextFloat(), ang + (float) Math.PI / 2 + (q.nextFloat() - .5f) * .8f, mT * (.65f + .35f * grow), .35f, ms));
            }
        }
        // Links: overlapping masses along a bent path, near the groups first.
        float bestA = -1;
        for (Edge e : active) {
            float A = e.at(t);
            float[] fa = fp[e.a()], fb = fp[e.b()];
            float dx = fb[0] - fa[0], dz = fb[1] - fa[1], d = Math.max(1, (float) Math.hypot(dx, dz));
            float ux = dx / d, uz = dz / d, vx = -uz, vz = ux, rAvg = .5f * (fa[2] + fb[2]);
            float moist = .5f * (p.groups().get(e.a()).env().moist() + p.groups().get(e.b()).env().moist());
            s.links.add(new float[]{e.a(), e.b(), A});
            Random q = stream(e.seed(), S_EDGE, 1, 0);
            float bend = (q.nextFloat() - .5f) * .5f * d;
            int nk = 2 + (d > 1.6f * rAvg ? 1 : 0) + (q.nextFloat() < .4f ? 1 : 0);
            for (int k = 0; k < nk; k++) {
                float f = Math.max(.08f, Math.min(.92f, (k + .5f) / nk + (q.nextFloat() - .5f) * .16f));
                float lat = (float) q.nextGaussian() * .16f * d + bend * 4 * f * (1 - f);
                float cx = fa[0] + ux * f * d + vx * lat, cz = fa[1] + uz * f * d + vz * lat;
                float R = (.2f + .14f * q.nextFloat()) * d + .3f * rAvg;
                float elong = v15 ? 1.05f + .45f * q.nextFloat() : 1.2f + q.nextFloat(), ang = (float) Math.atan2(uz, ux) + (q.nextFloat() - .5f) * 1.1f;
                float T = (2000 + 2000 * q.nextFloat()) * (.6f + .4f * moist);
                float level = Math.min(.85f, .08f + .8f * Math.min(f, 1 - f) + .15f * q.nextFloat());
                long ms = PersistentConvectionT2608.mix(e.seed() ^ S_FND, k + 1);
                if (A < level) continue; // not formed yet: an opening between the masses
                float grow = smooth(level, level + .3f, A);
                s.masses.add(new Mass(1, "link G" + e.a() + "-G" + e.b() + "." + k, cx, cz, R * (.55f + .45f * grow), elong, ang, T * (.6f + .4f * grow), .35f, ms));
            }
            if (A > bestA) {
                bestA = A;
                s.focus = new float[]{fa[0] + .5f * dx + vx * bend, 0, fa[1] + .5f * dz + vz * bend, ux, uz};
            }
        }
        if (v15) {
            if (rooted(version)) roots(s, p, o, fp, org, t); else sleeves(s, p, o, fp, org, active, t);
            relief(s);
            if (version == V18A) { s.lobes.clear(); hanging(s, p); }
            if (version == V18B) { s.bays.clear(); recesses(s); cores(s); }
            if (version == V18D) { s.lobes.clear(); clusters(s, p, false); }
            if (version == V18E) { s.bays.clear(); recesses(s); lowered(s, p); }
            if (d2(version)) { s.lobes.clear(); clusters(s, p, true); }
            if (version == V18F) { s.lobes.clear(); s.bays.clear(); recesses(s); lowered(s, p); clusters(s, p, true); }
        }
        // Bounds and component boxes.
        float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (float[] a : s.anchors) { s.ybMin = Math.min(s.ybMin, a[4]); s.ybMax = Math.max(s.ybMax, a[4]); }
        s.ybMin -= 60; s.ybMax += 60;
        if (v15) { float dep = 0; for (float[] l : s.lobes) dep = Math.max(dep, l[6]); s.ybMin -= 80 + dep + 140; } // undulation, lobes, underside warp / billows
        if (version == V18A) { float dep = 0; for (float[] h : s.hang) dep = Math.max(dep, h[6] + 2 * HANG_LUMP); s.ybMin -= dep + 120; } // + the A underside billows
        if (version == V18B) { float dep = 0; for (float[] c : s.cores) dep = Math.max(dep, c[3]); s.ybMin -= dep; s.ybMax += LIFT_B; }
        if (version == V18D || d2(version) || version == V18F) { float dep = 0; for (float[] h : s.hang) dep = Math.max(dep, h[6] + 2 * LUMP_D); s.ybMin -= dep + 120; }
        if (version == V19) { float thick = 0; for (Mass m : s.masses) thick = Math.max(thick, m.T); s.ybMin -= SAG * thick; } // S7a sag
        if (version == V18E || version == V18F) { float dep = 0; for (float[] w : s.drops) dep = Math.max(dep, w[6]); s.ybMin -= DROP_MOD * dep + 160; } // + the billows
        for (Root r : s.roots) {
            s.topMax = Math.max(s.topMax, r.y0 + r.H + 200);
            minX = Math.min(minX, r.bx - r.bR); maxX = Math.max(maxX, r.bx + r.bR); minZ = Math.min(minZ, r.bz - r.bR); maxZ = Math.max(maxZ, r.bz + r.bR);
            s.parts.add(new Part(r.label, new float[]{r.bx - r.bR, r.y0, r.bz - r.bR, r.bx + r.bR, r.y0 + r.H, r.bz + r.bR}));
        }
        for (Mass m : s.masses) {
            s.topMax = Math.max(s.topMax, s.ybMax + 1.35f * m.T);
            minX = Math.min(minX, m.cx - m.rb); maxX = Math.max(maxX, m.cx + m.rb); minZ = Math.min(minZ, m.cz - m.rb); maxZ = Math.max(maxZ, m.cz + m.rb);
            float r = m.R * m.elong;
            s.parts.add(new Part(m.label, new float[]{m.cx - r, s.ybMin, m.cz - r, m.cx + r, s.ybMax + m.T, m.cz + r}));
        }
        s.topMax += 250; // billows
        s.bounds = new Bounds(minX - 300, s.ybMin - 300, minZ - 300, maxX + 300, s.topMax + 300, maxZ + 300);
        if (s.focus != null) s.focus[1] = .5f * (s.ybMin + s.ybMax) + 700;
        return s;
    }

    // ---------------------------------------------------------------- T260.15 vertical anatomy

    static final long S_OUTER = 0x0E7E2615L, S_SLEEVE = 0x05EE2615L, S_LOBE = 0x010BE615L, S_BAY = 0x0BA72615L;
    /** Smooth union of the descending underside masses; smooth max of the underside depressions (model metres). */
    static final float LOBE_BLEND = 200, BAY_BLEND = 240;

    /**
     * T260.15 outer extension of an organised group: instead of one long thin shelf, a short chain of 2-3 overlapping
     * masses that shrink outward (thick near the skirt, irregular lateral steps). The main one lies on the outer /
     * downshear side; a second, smaller one (35 %) leaves at an angle, so the outline is asymmetric. Whole-chain
     * length beyond the skirt rim / width / thickness go to {@link Spec#extents}.
     */
    static void outer(Spec s, int i, Group g, float[] fp, float R, float sR, float sT, float m, float moist, float dir) {
        Random q = stream(g.seed(), S_OUTER, 0, 0);
        int n = Float.isNaN(dir) ? (q.nextFloat() < .5f ? 1 : 0) : 1 + (q.nextFloat() < .35f ? 1 : 0);
        if (Float.isNaN(dir)) dir = (float) (q.nextFloat() * 2 * Math.PI);
        for (int k = 0; k < n; k++) {
            // every draw first: the chain is never re-drawn while the organisation grows (persistent shape)
            float level = .3f + .3f * q.nextFloat();
            float ang = dir + (k == 0 ? (q.nextFloat() - .5f) * .9f : (q.nextBoolean() ? 1 : -1) * (1.2f + .8f * q.nextFloat()));
            float R1 = (.36f + .16f * q.nextFloat()) * R * (1 + .25f * moist) * (k == 0 ? 1 : .75f);
            float T1 = sT * (.62f + .25f * q.nextFloat()) * (k == 0 ? 1 : .8f);
            float dist = (.72f + .18f * q.nextFloat()) * sR; // starts inside the skirt rim, steps out past it
            int nSub = 2 + (q.nextFloat() < .5f ? 1 : 0);
            float[][] sub = new float[nSub][];
            for (int j = 0; j < nSub; j++) sub[j] = new float[]{q.nextFloat(), q.nextFloat(), q.nextFloat(), q.nextFloat()};
            if (m < level) continue; // not organised enough yet
            float grow = smooth(level, level + .3f, m);
            float ux = (float) Math.cos(ang), uz = (float) Math.sin(ang), along = dist, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, wMax = 0, tMax = 0;
            for (int j = 0; j < nSub; j++) {
                float rj = R1 * (float) Math.pow(.72f, j) * (.6f + .4f * grow), tj = T1 * (float) Math.pow(.8f, j) * (.65f + .35f * grow);
                float lat = (sub[j][0] - .5f) * .7f * R1, el = 1 + .3f * sub[j][1];
                if (j > 0) along += (.6f + .3f * sub[j][2]) * R1 * (float) Math.pow(.72f, j - .5f);
                float cx = fp[0] + ux * along - uz * lat, cz = fp[1] + uz * along + ux * lat;
                s.masses.add(new Mass(2, "outer G" + i + "." + k + "." + j, cx, cz, rj, el, ang + (sub[j][3] - .5f) * 1.2f, tj, .35f,
                        PersistentConvectionT2608.mix(g.seed() ^ S_OUTER, 16L * k + j + 1)));
                lo = Math.min(lo, along - rj * el); hi = Math.max(hi, along + rj * el);
                wMax = Math.max(wMax, 2 * rj + Math.abs(lat)); tMax = Math.max(tMax, tj);
            }
            // the part beyond the skirt rim (what reads as an extension): length, width, thickness
            s.extents.add(new float[]{Math.max(1, hi - Math.max(lo, sR)), wMax, tMax});
        }
    }

    /** One candidate tower of a group for a sleeve. */
    private static final class Tower {
        long seed; String name; float x, z, top, w, life, face, score; int nb;
    }

    /**
     * T260.15 vertical integration: around the lower and middle trunk of the strongest live towers of every organised
     * group, a tall mass (the same domed vocabulary) rises to 34-55 % of the tower top, offset outward so it bulges out
     * of the trunk as a convective shoulder. Height from the group's organisation, the tower's life (growth,
     * dissipation), the local tower density and whether it faces a linked neighbour; never above 55 % of the top (the
     * crown and upper tower stay free). 2-4 towers per group, at least 0.6 x their summed widths apart.
     */
    static void sleeves(Spec s, Plan p, Org o, float[][] fp, float[] org, List<Edge> active, float t) {
        var env = p.env();
        int n = p.groups().size();
        float[] nbx = new float[n], nbz = new float[n];
        for (Edge e : active) {
            float A = e.at(t), dx = fp[e.b()][0] - fp[e.a()][0], dz = fp[e.b()][1] - fp[e.a()][1], d = Math.max(1, (float) Math.hypot(dx, dz));
            nbx[e.a()] += A * dx / d; nbz[e.a()] += A * dz / d;
            nbx[e.b()] -= A * dx / d; nbz[e.b()] -= A * dz / d;
        }
        for (int i = 0; i < n; i++) {
            if (org[i] == 0) continue;
            Group g = p.groups().get(i);
            float tau = t - g.birth();
            var st = o.scen().get(i).at(tau);
            float gx = g.x() - env.windX() * tau, gz = g.z() - env.windZ() * tau, m = smooth(ACTIVE, .6f, org[i]);
            float nl = (float) Math.hypot(nbx[i], nbz[i]);
            List<Tower> cand = new ArrayList<>();
            for (var c : st.geoms) {
                float top = c.st[0], dd = c.st[2], em = c.st[3];
                if (top < 3500 || dd > .75f) continue;
                Tower w = new Tower();
                float y0 = .15f * top;
                w.seed = c.spec.seed; w.name = c.spec.name; w.top = top;
                w.x = gx + c.trunk.axisX(y0); w.z = gz + c.trunk.axisZ(y0); w.w = c.trunk.width(y0);
                float ox = w.x - fp[i][0], oz = w.z - fp[i][1], ol = (float) Math.hypot(ox, oz);
                w.face = nl > 1e-3f && ol > 1 ? Math.max(0, (ox * nbx[i] + oz * nbz[i]) / (ol * nl)) * Math.min(1, nl) : 0;
                w.life = (1 - dd) * em;
                w.score = top * w.life * (1 + .6f * w.face);
                cand.add(w);
            }
            for (Tower w : cand) for (var c : st.geoms) {
                float y0 = .15f * c.st[0];
                if (c.spec.seed != w.seed && c.st[0] > 1500 && Math.hypot(gx + c.trunk.axisX(y0) - w.x, gz + c.trunk.axisZ(y0) - w.z) < 2.2f * w.w) w.nb++;
            }
            cand.sort((a, b) -> a.score != b.score ? Float.compare(b.score, a.score) : Long.compare(a.seed, b.seed));
            int max = 2 + (m > .3f ? 1 : 0) + (m > .7f ? 1 : 0);
            List<Tower> chosen = new ArrayList<>();
            for (Tower w : cand) {
                if (chosen.size() >= max) break;
                boolean apart = true;
                for (Tower c : chosen) apart &= Math.hypot(w.x - c.x, w.z - c.z) > .6f * (w.w + c.w);
                if (apart) chosen.add(w);
            }
            for (Tower w : chosen) {
                Random q = stream(w.seed, S_SLEEVE, 0, 0);
                float f = Math.min(.55f, (.34f + .1f * m + .04f * Math.min(3, w.nb) + .1f * w.face + .04f * q.nextFloat()) * (.55f + .45f * w.life));
                float ox = w.x - fp[i][0], oz = w.z - fp[i][1], ol = (float) Math.hypot(ox, oz);
                if (ol < .25f * w.w) {
                    ox = nl > 1e-3f ? -nbx[i] : (float) Math.cos(env.shearAz());
                    oz = nl > 1e-3f ? -nbz[i] : (float) Math.sin(env.shearAz());
                    ol = Math.max(1e-3f, (float) Math.hypot(ox, oz));
                }
                ox /= ol; oz /= ol;
                float off = (.35f + .2f * q.nextFloat()) * w.w;
                float R = w.w * (1 + .3f * q.nextFloat()) * (.75f + .25f * m), el = 1.05f + .25f * q.nextFloat();
                float ang = (float) Math.atan2(oz, ox) + (q.nextFloat() - .5f) * .6f;
                // never above 55 % of the top of any comparable tower it reaches (>= 75 % of this one: a tall neighbour keeps
                // its upper tower too; the small 3.5-5 km cells already stand inside the skirts, as in T260.14)
                float top = f * w.top, sx = w.x + ox * off, sz = w.z + oz * off;
                for (Tower c : cand) if (c.top >= .75f * w.top && Math.hypot(c.x - sx, c.z - sz) < 1.3f * R * el + c.w) top = Math.min(top, .55f * c.top);
                s.masses.add(new Mass(3, "sleeve G" + i + " " + w.name, sx, sz, R, el, ang, top, .35f,
                        PersistentConvectionT2608.mix(w.seed ^ S_SLEEVE, 1)));
            }
        }
    }

    /** Relief character (0 = smooth underside region, 1 = strongly relieved), km-scale and seeded. */
    static float character(Spec s, float x, float z) { return smooth(-.3f, .35f, noise(x / 6000, 3.5f, z / 6000, s.seed + 41)); }

    /**
     * T260.15 underside relief, placed per lower mass (skirt, link, outer; never under a sleeve alone): descending
     * rounded masses (0-8 per mass, spacing 1.5-2.8 km, broad flattened ellipsoids 0.3-1.3 km across, 60-440 m below
     * the base, half of them with a smaller offset companion: irregular, never a tentacle) and broad depressions
     * (45-60 % of the masses, 0.9-2 km, raising the base by up to ~340 m, never within a footprint radius of a group).
     * Their number, size and depth follow the relief character, so some regions keep a smooth base.
     */
    static void relief(Spec s) {
        List<Mass> lower = new ArrayList<>();
        for (Mass m : s.masses) if (m.kind != 3) lower.add(m);
        for (Mass m : lower) {
            Random q = new Random(m.seed ^ S_LOBE);
            float spacing = 1500 + 1300 * q.nextFloat();
            int n = Math.min(8, Math.round((float) Math.PI * m.R * m.R * m.elong / (spacing * spacing)));
            for (int k = 0; k < n; k++) {
                float u = .72f * (float) Math.sqrt(q.nextFloat()), a = (float) (q.nextFloat() * 2 * Math.PI);
                float al = 450 + 850 * q.nextFloat(), ratio = .55f + .45f * q.nextFloat(), ang = (float) (q.nextFloat() * Math.PI), depF = q.nextFloat();
                float subP = q.nextFloat(), subA = (float) (q.nextFloat() * 2 * Math.PI), subS = .5f + .2f * q.nextFloat(), keep = q.nextFloat();
                float lu = u * (float) Math.cos(a) * m.R * m.elong, lv = u * (float) Math.sin(a) * m.R;
                float x = m.cx + lu * m.ca - lv * m.sa, z = m.cz + lu * m.sa + lv * m.ca;
                float rc = character(s, x, z);
                if (keep > 1.3f * rc) continue; // smooth region: no descending mass here
                al *= .7f + .3f * rc;
                float bl = al * ratio, dep = Math.min(Math.min((110 + 330 * depF) * (.55f + .45f * rc), .3f * bl), .3f * m.T);
                s.lobes.add(new float[]{x, z, al, bl, (float) Math.cos(ang), (float) Math.sin(ang), dep, 1.5f * dep + 80});
                if (subP < .5f) {
                    float sa = al * subS, sb = bl * subS, sd = .65f * dep, off = (.55f + .25f * subS) * al;
                    s.lobes.add(new float[]{x + off * (float) Math.cos(subA), z + off * (float) Math.sin(subA), Math.max(sa, sb), Math.min(sa, sb),
                            (float) Math.cos(ang + .7f), (float) Math.sin(ang + .7f), sd, 1.5f * sd + 80});
                }
            }
            Random b = new Random(m.seed ^ S_BAY);
            float pb = b.nextFloat(), u = .35f + .35f * b.nextFloat(), a = (float) (b.nextFloat() * 2 * Math.PI);
            float ab = 900 + 1100 * b.nextFloat(), ratio = .5f + .4f * b.nextFloat(), ang = (float) (b.nextFloat() * Math.PI), cb = 220 + 340 * b.nextFloat();
            if (pb > (m.kind == 1 ? .6f : .45f)) continue;
            float lu = u * (float) Math.cos(a) * m.R * m.elong, lv = u * (float) Math.sin(a) * m.R;
            float x = m.cx + lu * m.ca - lv * m.sa, z = m.cz + lu * m.sa + lv * m.ca;
            boolean clear = true;
            for (float[] an : s.anchors) clear &= Math.hypot(x - an[1], z - an[2]) > an[3] + .5f * ab;
            if (clear) s.bays.add(new float[]{x, z, ab, ab * ratio, (float) Math.cos(ang), (float) Math.sin(ang), cb});
        }
    }

    // ---------------------------------------------------------------- field

    static float fbm(float x, float z, float scale, long seed) { return LowerMassT26013.fbm(x, z, scale, seed); }

    /** Shared, sloped cloud base: the organised groups' bases interpolated by footprint-scaled distance, +-50 m relief. */
    static float base(Spec s, float x, float z) {
        float sw = 0, sb = 0;
        for (float[] a : s.anchors) {
            float dd = (float) Math.hypot(x - a[1], z - a[2]) / Math.max(500, a[3]);
            float w = 1 / (1e-3f + dd * dd * dd * dd);
            sw += w; sb += w * a[4];
        }
        return sb / sw + 35 * noise(x / 1700, .5f, z / 1700, s.seed + 11) + 15 * noise(x / 650, 1.5f, z / 650, s.seed + 12);
    }

    /** The groups' bases interpolated as in {@link #base} without its noise (reference plane for the metrics). */
    static float baseIdw(Spec s, float x, float z) {
        float sw = 0, sb = 0;
        for (float[] a : s.anchors) {
            float dd = (float) Math.hypot(x - a[1], z - a[2]) / Math.max(500, a[3]);
            float w = 1 / (1e-3f + dd * dd * dd * dd);
            sw += w; sb += w * a[4];
        }
        return sb / sw;
    }

    /** Pseudo-distance of the foundation (positive outside), model metres; y from the model base. */
    public static SparseBrickVolume.Field field(Spec s) {
        if (s.version > 15) return field18(s);
        if (s.version >= 15) return field15(s);
        return (x, y, z) -> {
            // No early-out above / below: a small lower bound there is discontinuous with the full evaluation (which can
            // be far larger away from every mass), fools the coarse skip and, smooth-blended with a tower, draws fillets.
            float yb = base(s, x, z);
            // One shared 3D domain warp (km-scale, weaker vertically): flanks are never a plan outline extruded upward.
            float x0 = x, z0 = z;
            x = x0 + 260 * noise(x0 / 1700, y / 900, z0 / 1700, s.seed + 31);
            z = z0 + 260 * noise(x0 / 1700, y / 900, z0 / 1700, s.seed + 32);
            float yw = y + 140 * noise(x0 / 1500, y / 1500, z0 / 1500, s.seed + 33) * smooth(yb, yb + 400, y);
            float d = masses(s, x, z, yw, yb);
            // Openings and bays: a low-frequency coverage field carves the foundation where it is weak, never near a
            // tower (the towers always stand in their own lower mass).
            float near = 0;
            for (float[] a : s.anchors) near = Math.max(near, 1 - smooth(.6f * a[3], 1.15f * a[3], (float) Math.hypot(x0 - a[1], z0 - a[2])));
            // 3D coverage (a plan-view carve would extrude vertical walls); gradient of the carve term <= ~1.2.
            float cov = noise(x0 / 2600, y / 2000, z0 / 2600, s.seed + 21) + .4f * noise(x0 / 1100, y / 900, z0 / 1100, s.seed + 22) + 1.2f * near;
            d = ConvectiveSystemT260.smax(d, (HOLE - cov) * 450, 220);
            // Cumuliform billows over the upper surface AND the flanks (flat base), fading away from the surface.
            if (d < 900) d += (140 * noise(x / 900, y / 900, z / 900, s.seed * 7 + 1) + 55 * noise(x / 380, y / 380, z / 380, s.seed * 13 + 5))
                    * smooth(yb - 20, yb + 160, y) * (1 - smooth(500, 900, d));
            // Coherent, flat (but noisy) cloud base shared by every mass.
            return ConvectiveSystemT260.smax(d, yb - y, 120);
        };
    }

    /** Smooth union of every mass (domes included) at the warped point; yw = warped height, yb = the shared base. */
    static float masses(Spec s, float x, float z, float yw, float yb) {
        float d = 1e9f;
        for (Mass m : s.masses) {
            float dx = x - m.cx, dz = z - m.cz;
            float hd = (float) Math.hypot(dx, dz);
            // Lower bound of this mass's distance, domes included (rr >= hd / elong, horizontal semi-axis <= 1.43 R, domes
            // within 1.28 R elong, min semi-axis >= min(0.62 R, 0.43 T); the dome blend may lower it by DOME_BLEND / 4).
            float rmax = 1.43f * m.R; // >= 1.035 x 1.32 R (ellipsoid) and the domes' 1.28 R
            if (hd / m.elong > rmax && (hd / m.elong - rmax) / rmax * Math.min(.62f * m.R, .43f * m.T) > d + MASS_BLEND + DOME_BLEND) continue;
            float lu = (dx * m.ca + dz * m.sa) / m.elong, lv = -dx * m.sa + dz * m.ca;
            float rr = (float) Math.hypot(lu, lv);
            // Outline and folds with wavelengths that scale with the mass (a fixed wavelength under a radius-scaled
            // amplitude made km-wide masses far steeper than a distance field may be).
            float wx = x + .2f * m.R * fbm(x, z, m.R, m.seed + 1), wz = z + .2f * m.R * fbm(x, z, m.R, m.seed + 2);
            float Rn = m.R * (1 + .32f * fbm(wx, wz, 1.1f * m.R, m.seed + 3));
            float Tn = m.T * (.84f + .18f * fbm(x, z, Math.max(3000, 1.2f * m.R), m.seed + 4) + .07f * noise(x / 1200, 2.5f, z / 1200, m.seed + 5));
            // Half-ellipsoid on the shared base: rounded sides down to the base (no extruded rim, no thin sheet).
            // (q - 1) min(Rn, Tn) never overestimates the distance (gradient <= 1 for fixed Rn, Tn).
            // Ellipsoid centred CORE x thickness below the base, scaled so it still reaches Rn at the base and Tn at the top.
            float sv = (1 + CORE) * Tn, sh = Rn * (1 + CORE) / (float) Math.sqrt((1 + CORE) * (1 + CORE) - CORE * CORE);
            float qx = rr / sh, qy = (yw - yb + CORE * Tn) / sv;
            float de = ((float) Math.sqrt(qx * qx + qy * qy) - 1) * Math.min(sh, sv);
            // Rounded cumuliform domes on the upper surface (the towers' own vocabulary, not noise).
            for (int k = 0; k < m.domes.length; k += 4) {
                float ddx = x - m.domes[k], ddz = z - m.domes[k + 1], r = m.domes[k + 3];
                float h2 = ddx * ddx + ddz * ddz;
                if (h2 > (r + de + DOME_BLEND) * (r + de + DOME_BLEND) && de > -r) continue;
                float dy = yw - yb - m.domes[k + 2];
                de = ConvectiveSystemT260.smin(de, (float) Math.sqrt(h2 + dy * dy) - r, DOME_BLEND);
            }
            d = ConvectiveSystemT260.smin(d, de, MASS_BLEND);
        }
        return d;
    }

    /**
     * T260.15 field: the T260.14 masses (sleeves included) on a gently undulating base, then the underside anatomy -
     * depressions carved upward, descending masses smooth-joined below, a weak underside warp and secondary billows
     * scaled by the relief character - and only then the coverage openings (they cut the underside masses too, so no
     * mass ever hangs under an opening).
     */
    static SparseBrickVolume.Field field15(Spec s) {
        return (x, y, z) -> {
            float yb = base(s, x, z) + 70 * noise(x / 4200, 4.5f, z / 4200, s.seed + 13);
            float x0 = x, z0 = z;
            x = x0 + 260 * noise(x0 / 1700, y / 900, z0 / 1700, s.seed + 31);
            z = z0 + 260 * noise(x0 / 1700, y / 900, z0 / 1700, s.seed + 32);
            float yw = y + 140 * noise(x0 / 1500, y / 1500, z0 / 1500, s.seed + 33) * smooth(yb, yb + 400, y);
            float d = masses(s, x, z, yw, yb);
            // Underside: rounded base cap on a weakly warped height (no ruler-flat plane).
            float yu = y + 60 * noise(x0 / 1100, y / 700, z0 / 1100, s.seed + 34) * (1 - smooth(yb, yb + 300, y));
            d = ConvectiveSystemT260.smax(d, yb - yu, 120);
            for (float[] b : s.bays) {
                float dx = x - b[0], dz = z - b[1], mn = Math.min(b[3], b[6]);
                if (((float) Math.hypot(dx, dz) / b[2] - 1) * mn >= BAY_BLEND - d) continue; // smax(d, -bay) == d there
                float lu = (dx * b[4] + dz * b[5]) / b[2], lv = (-dx * b[5] + dz * b[4]) / b[3], ly = (yu - yb + .4f * b[6]) / b[6];
                d = ConvectiveSystemT260.smax(d, -((float) Math.sqrt(lu * lu + lv * lv + ly * ly) - 1) * mn, BAY_BLEND);
            }
            for (float[] l : s.lobes) {
                float dx = x - l[0], dz = z - l[1], mn = Math.min(l[3], l[7]);
                if (((float) Math.hypot(dx, dz) / l[2] - 1) * mn >= d + LOBE_BLEND) continue; // smin(d, lobe) == d there
                float lu = (dx * l[4] + dz * l[5]) / l[2], lv = (-dx * l[5] + dz * l[4]) / l[3], ly = (yu - (yb - l[6] + l[7])) / l[7];
                d = ConvectiveSystemT260.smin(d, ((float) Math.sqrt(lu * lu + lv * lv + ly * ly) - 1) * mn, LOBE_BLEND);
            }
            float near = 0;
            for (float[] a : s.anchors) near = Math.max(near, 1 - smooth(.6f * a[3], 1.15f * a[3], (float) Math.hypot(x0 - a[1], z0 - a[2])));
            // the outer extensions are never cut into isolated fragments either (an opening may still pass beside them)
            for (Mass m : s.masses) if (m.kind == 2) near = Math.max(near, 1 - smooth(.5f * m.R, 1.05f * m.R, (float) Math.hypot(x0 - m.cx, z0 - m.cz)));
            float cov = noise(x0 / 2600, y / 2000, z0 / 2600, s.seed + 21) + .4f * noise(x0 / 1100, y / 900, z0 / 1100, s.seed + 22) + 1.2f * near;
            d = ConvectiveSystemT260.smax(d, (HOLE - cov) * 450, 220);
            if (d < 900) {
                float up = smooth(yb - 20, yb + 160, y);
                d += (140 * noise(x / 900, y / 900, z / 900, s.seed * 7 + 1) + 55 * noise(x / 380, y / 380, z / 380, s.seed * 13 + 5)) * up * (1 - smooth(500, 900, d));
                // secondary underside billowing, only where the relief character allows it
                d += character(s, x0, z0) * (50 * noise(x / 700, y / 600, z / 700, s.seed * 7 + 3) + 20 * noise(x / 280, y / 280, z / 280, s.seed * 13 + 7))
                        * (1 - up) * (1 - smooth(300, 700, d));
            }
            return d;
        };
    }

    // ---------------------------------------------------------------- T260.18 foundation geometry variants

    /** T260.18 variants (T260.15 anatomy plus one large-scale geometry change each). */
    public static final int V18A = 181, V18B = 182, V18C = 183, V18D = 184, V18E = 185, V18D2 = 186, V18F = 187;
    /**
     * Stabilisation S7a (RC-13): D2 with a base that follows its masses. Every earlier version cuts all masses with ONE
     * shared plane ({@code smax(d, yb - y, 120)}, +-50 m relief), so the underside is flat by construction (normals SD
     * 0.10-0.19 vs 0.24-0.41 on the flanks, root-cause audit). Here the cut sags under each mass by up to
     * {@link #SAG} of its thickness, smoothly over its footprint, so the rounded bottom of the mass shows.
     */
    public static final int V19 = 191;
    static final float SAG = .3f;

    /** D2 and the variants built on it. */
    static boolean d2(int version) { return version == V18D2 || version == V19; }
    static final long S_HANG = 0x0A4A2618L, S_RECESS = 0x0BEC2618L, S_CORE = 0x0C0E2618L, S_ROOT = 0x0B0072618L;
    /** A: smooth union of a hanging mass with the foundation (a broad fillet into the base, never a neck). */
    static final float HANG_BLEND = 450, HANG_LUMP = 110;
    /** B: largest rise of the base in the open regions (model metres). */
    static final float LIFT_B = 1150;
    /** C: thickness factor of the skirt lens (the roots carry the height around the towers); blend of the roots. */
    static final float LENS_C = .62f, ROOT_BLEND = 380;

    /** D: broader fillet of the compound hanging masses; their surface lumps (two octaves). E: peak of the drop modulation. */
    static final float BLEND_D = 620, LUMP_D = 190, DROP_MOD = 1.15f;
    static final long S_CLUSTER = 0x0C1D2618L, S_DROP = 0x0D40E2618L;

    static String variant18(int version) { return version == V18A ? "A" : version == V18B ? "B" : version == V18C ? "C" : version == V18D ? "D" : version == V18E ? "E" : version == V18D2 ? "D2" : version == V18F ? "F" : version == V19 ? "S7a" : "?"; }

    /** The variants whose towers grow from roots on a thinner lens (C, and the combined D / E). */
    static boolean rooted(int version) { return version == V18C || version == V18D || version == V18E || d2(version) || version == V18F; }

    /**
     * Vertical semi-axis of a hanging mass: its top lies inside the foundation (at least 260 m and half the depth above the
     * base) and the mass is never flatter than 2.2 : 1, so a broad mass ends in a rounded bottom, not a flat slab.
     */
    static float hangC(float a, float dep) { return Math.max(.5f * (dep + Math.max(260, .5f * dep)), a / 2.2f); }

    /** T260.15 / T260.18 coverage (openings where it falls below {@link #HOLE}), at the unwarped point. */
    static float coverage(Spec s, float x0, float y, float z0) {
        float near = 0;
        for (float[] a : s.anchors) near = Math.max(near, 1 - smooth(.6f * a[3], 1.15f * a[3], (float) Math.hypot(x0 - a[1], z0 - a[2])));
        for (Mass m : s.masses) if (m.kind == 2) near = Math.max(near, 1 - smooth(.5f * m.R, 1.05f * m.R, (float) Math.hypot(x0 - m.cx, z0 - m.cz)));
        return noise(x0 / 2600, y / 2000, z0 / 2600, s.seed + 21) + .4f * noise(x0 / 1100, y / 900, z0 / 1100, s.seed + 22) + 1.2f * near;
    }

    /** Proximity of (x, z) to an organised group's towers: 1 inside .4 R, 0 beyond 1.5 R. */
    static float coreWeight(Spec s, float x, float z) {
        float w = 0;
        for (float[] a : s.anchors) w = Math.max(w, 1 - smooth(.4f * a[3], 1.5f * a[3], (float) Math.hypot(x - a[1], z - a[2])));
        return w;
    }

    /**
     * T260.18 A: the T260.15 underside lobes (60-440 m) are replaced by DEEP ASYMMETRIC HANGING MASSES. Per lower mass
     * (skirt, link, outer) 1-6 masses, placed apart from each other, with a size drawn from a skewed distribution (a few
     * large, more small: horizontal semi-axis 0.6-2 km), a depth below the base of 0.38-1.5 km (deeper under the
     * towers and in relieved regions, never deeper than 1.3 x the narrow semi-axis: no spike), an elongation and
     * orientation of their own, a downshear lean (the lower part displaced, 0.1-0.4 per metre) and 0-2 offset companions of
     * other sizes and depths, so no two hanging masses repeat. Only where the foundation is covered with margin (never
     * under an opening).
     */
    static void hanging(Spec s, Plan p) {
        float shAz = p.env().shearAz();
        for (Mass m : s.masses) {
            if (m.kind == 3) continue;
            Random q = new Random(m.seed ^ S_HANG);
            int n = Math.max(1, Math.min(6, Math.round((float) Math.PI * m.R * m.R * m.elong / (2600f * 2600f))));
            int placed = 0;
            for (int k = 0; k < n + 3 && placed < n; k++) {
                // every draw first (the set never re-draws while the system grows)
                float u = .78f * (float) Math.sqrt(q.nextFloat()), an = (float) (q.nextFloat() * 2 * Math.PI), sz = (float) Math.pow(q.nextFloat(), 1.7);
                float dq = q.nextFloat(), ratio = .5f + .45f * q.nextFloat(), ang = (float) (q.nextFloat() * Math.PI);
                float leanA = shAz + (q.nextFloat() - .5f) * 1.6f, leanK = .1f + .3f * q.nextFloat(), keep = q.nextFloat(), ns = q.nextFloat();
                float[][] sub = new float[2][];
                for (int j = 0; j < 2; j++) sub[j] = new float[]{q.nextFloat(), q.nextFloat(), q.nextFloat(), q.nextFloat(), q.nextFloat()};
                float lu = u * (float) Math.cos(an) * m.R * m.elong, lv = u * (float) Math.sin(an) * m.R;
                float x = m.cx + lu * m.ca - lv * m.sa, z = m.cz + lu * m.sa + lv * m.ca;
                float core = coreWeight(s, x, z), rc = Math.max(core, character(s, x, z));
                if (keep > .45f + .9f * rc) continue; // smooth regions keep fewer hanging masses
                float a = 600 + 1400 * sz, b = a * ratio;
                // never under an opening: the foundation must be there, with margin, over the whole hanging mass
                float yb = base(s, x, z);
                boolean covered = true;
                for (int j = 0; j < 5 && covered; j++) {
                    float px = x + (j == 0 ? 0 : (j < 3 ? 1 : -1) * (j % 2 == 0 ? a : 0)), pz = z + (j == 0 ? 0 : (j < 3 ? 1 : -1) * (j % 2 == 1 ? a : 0));
                    covered = coverage(s, px, yb, pz) > HOLE + .35f;
                }
                if (!covered) continue;
                boolean apart = true;
                for (float[] h : s.hang) apart &= Math.hypot(x - h[0], z - h[1]) > .75f * (a + h[2]);
                if (!apart) continue;
                float lx = leanK * (float) Math.cos(leanA), lz = leanK * (float) Math.sin(leanA);
                float dep = Math.min(Math.min(1500, 1.3f * b), Math.max(380, (.45f + .75f * dq) * a * (.55f + .45f * rc)));
                s.hang.add(new float[]{x, z, a, b, (float) Math.cos(ang), (float) Math.sin(ang), dep, hangC(a, dep), lx, lz});
                int nsub = ns < .2f ? 0 : ns < .6f ? 1 : 2;
                for (int j = 0; j < nsub; j++) {
                    float[] w = sub[j];
                    float oa = (float) (w[0] * 2 * Math.PI), off = (.45f + .35f * w[1]) * a, sc = .35f + .35f * w[2];
                    float sa = a * sc, sb = sa * (.5f + .45f * w[4]), sd = Math.min(1.3f * sb, Math.max(300, (.4f + .55f * w[3]) * dep));
                    float sang = ang + (w[4] - .5f) * 2;
                    s.hang.add(new float[]{x + off * (float) Math.cos(oa), z + off * (float) Math.sin(oa), sa, sb, (float) Math.cos(sang), (float) Math.sin(sang), sd, hangC(sa, sd), lx, lz});
                }
                placed++;
            }
        }
    }

    /**
     * T260.18 B: deep recesses replacing the T260.15 depressions. Per lower mass 0-3 broad bays (1.2-3.2 km, elongated,
     * oriented at random) carved 0.36-0.9 km up into the mass; where the mass is thinner the recess opens through (an
     * open bay). Never within 0.8 footprint radii of a group (the towers keep their own lower mass).
     */
    static void recesses(Spec s) {
        for (Mass m : s.masses) {
            if (m.kind == 3) continue;
            Random b = new Random(m.seed ^ S_RECESS);
            int n = (m.kind == 1 ? 1 : 0) + (b.nextFloat() < .6f ? 1 : 0) + (m.R > 4000 && b.nextFloat() < .5f ? 1 : 0);
            for (int k = 0; k < 3; k++) {
                float u = .3f + .45f * b.nextFloat(), a = (float) (b.nextFloat() * 2 * Math.PI), ab = 1200 + 2000 * b.nextFloat(), ratio = .45f + .45f * b.nextFloat();
                float ang = (float) (b.nextFloat() * Math.PI), cb = 600 + 900 * b.nextFloat();
                if (k >= n) continue;
                float lu = u * (float) Math.cos(a) * m.R * m.elong, lv = u * (float) Math.sin(a) * m.R;
                float x = m.cx + lu * m.ca - lv * m.sa, z = m.cz + lu * m.sa + lv * m.ca;
                boolean clear = true;
                for (float[] an : s.anchors) clear &= Math.hypot(x - an[1], z - an[2]) > .8f * an[3] + .4f * ab;
                if (clear) s.bays.add(new float[]{x, z, ab, ab * ratio, (float) Math.cos(ang), (float) Math.sin(ang), cb});
            }
        }
    }

    /** T260.18 B: the base descends 200-500 m under each organised group's towers (moist inflow), seeded per group. */
    static void cores(Spec s) {
        for (float[] a : s.anchors) s.cores.add(new float[]{a[1], a[2], a[3], (200 + 300 * new Random(PersistentConvectionT2608.mix(s.seed ^ S_CORE, (long) a[0] + 1)).nextFloat()) * (.5f + .5f * a[5])});
    }

    /**
     * T260.18 B: offset of the cloud base (model metres, + = higher). Away from the towers a km-scale field raises the
     * base by up to {@link #LIFT_B} (sloped, never a terrace: the field almost never saturates); under the towers the base
     * descends by the group's drop.
     */
    static float lift18(Spec s, float x, float z) {
        float n = noise(x / 5200, 6.5f, z / 5200, s.seed + 61) + .45f * noise(x / 2300, 7.5f, z / 2300, s.seed + 62);
        float up = LIFT_B * smooth(-.15f, 1.05f, n), w = 0, sw = 0, sd = 0;
        for (float[] c : s.cores) {
            float cw = 1 - smooth(.35f * c[2], 1.25f * c[2], (float) Math.hypot(x - c[0], z - c[1]));
            w = 1 - (1 - w) * (1 - cw); sw += cw; sd += cw * c[3]; // probabilistic union: no kink where two cores meet
        }
        // the drop is the weighted mean of the overlapping groups' drops (taking the nearest one's would step between them)
        return up * (1 - w) - (sw > 0 ? w * sd / sw : 0);
    }

    /**
     * T260.18 D: BROAD COMPOUND HANGING MASSES (A refined for shape). Per lower mass 1-4 clusters, each a broad primary
     * mass (horizontal semi-axis 1.2-2.8 km, 200-1050 m below the base, never deeper than 0.48 x its narrow semi-axis: a
     * low, rounded bulge, never a pendant) with 2-4 overlapping companions of other sizes, elongations, orientations and
     * depths, so every cluster has its own irregular outline. No lean (a leaning deep mass read as a finger in A). Deeper on
     * the downshear side of the nearest group and in relieved regions, shallower upshear: the lower cloud develops
     * asymmetrically. Only where the foundation is covered with margin (never under an opening).
     */
    static void clusters(Spec s, Plan p, boolean deep) {
        float shAz = p.env().shearAz(), shx = (float) Math.cos(shAz), shz = (float) Math.sin(shAz);
        List<float[]> prim = new ArrayList<>();
        for (Mass m : s.masses) {
            if (m.kind == 3) continue;
            Random q = new Random(m.seed ^ S_CLUSTER);
            int n = Math.max(1, Math.min(4, Math.round((float) Math.PI * m.R * m.R * m.elong / (3400f * 3400f))));
            int placed = 0;
            for (int k = 0; k < n + 3 && placed < n; k++) {
                // every draw first (the set never re-draws while the system grows)
                float u = .7f * (float) Math.sqrt(q.nextFloat()), an = (float) (q.nextFloat() * 2 * Math.PI), sz = (float) Math.pow(q.nextFloat(), 1.5);
                float dq = q.nextFloat(), ratio = .55f + .4f * q.nextFloat(), ang = (float) (q.nextFloat() * Math.PI), keep = q.nextFloat(), ns = q.nextFloat();
                float[][] sub = new float[4][];
                for (int j = 0; j < 4; j++) sub[j] = new float[]{q.nextFloat(), q.nextFloat(), q.nextFloat(), q.nextFloat(), q.nextFloat()};
                float lu = u * (float) Math.cos(an) * m.R * m.elong, lv = u * (float) Math.sin(an) * m.R;
                float x = m.cx + lu * m.ca - lv * m.sa, z = m.cz + lu * m.sa + lv * m.ca;
                float core = coreWeight(s, x, z), rc = Math.max(core, character(s, x, z));
                if (keep > .5f + .8f * rc) continue; // smooth regions keep a level base
                float a = deep ? 1400 + 1800 * sz : 1200 + 1600 * sz, b = a * ratio;
                float yb = base(s, x, z);
                boolean covered = true;
                for (int j = 0; j < 5 && covered; j++) {
                    float px = x + (j == 0 ? 0 : (j < 3 ? 1 : -1) * (j % 2 == 0 ? a : 0)), pz = z + (j == 0 ? 0 : (j < 3 ? 1 : -1) * (j % 2 == 1 ? a : 0));
                    covered = coverage(s, px, yb, pz) > HOLE + .35f;
                }
                if (!covered) continue;
                boolean apart = true;
                for (float[] h : prim) apart &= Math.hypot(x - h[0], z - h[1]) > .7f * (a + h[2]);
                if (!apart) continue;
                // asymmetry: the downshear side of the nearest organised group hangs deeper
                float best = Float.MAX_VALUE, side = 0;
                for (float[] g : s.anchors) {
                    float gx = x - g[1], gz = z - g[2], gd = (float) Math.hypot(gx, gz);
                    if (gd / g[3] < best) { best = gd / g[3]; side = gd > 1 ? (gx * shx + gz * shz) / gd : 0; }
                }
                float asym = 1 + .45f * side * (1 - smooth(1.5f, 3, best));
                float dep = deep ? Math.max(300, Math.min(Math.min(1700, .7f * b), (450 + 900 * dq) * (.55f + .45f * rc) * asym))
                        : Math.max(200, Math.min(Math.min(1050, .48f * b), (280 + 620 * dq) * (.55f + .45f * rc) * asym));
                prim.add(new float[]{x, z, a});
                s.hang.add(new float[]{x, z, a, b, (float) Math.cos(ang), (float) Math.sin(ang), dep, hangD(a, dep), 0, 0});
                int nsub = 2 + (ns < .5f ? 0 : ns < .85f ? 1 : 2);
                for (int j = 0; j < nsub; j++) {
                    float[] w = sub[j];
                    float oa = an + (float) (j * 2 * Math.PI / nsub + (w[0] - .5f) * 1.6f), off = (.35f + .4f * w[1]) * a, sc = .35f + .35f * w[2];
                    float sa = a * sc, sb = sa * (.5f + .45f * w[4]), sd = Math.max(150, Math.min((deep ? .7f : .48f) * sb, (.45f + .6f * w[3]) * dep));
                    float sang = ang + (w[4] - .5f) * 2.4f;
                    s.hang.add(new float[]{x + off * (float) Math.cos(oa), z + off * (float) Math.sin(oa), sa, sb, (float) Math.cos(sang), (float) Math.sin(sang), sd, hangD(sa, sd), 0, 0});
                }
                placed++;
            }
        }
    }

    /** D: vertical semi-axis of a broad hanging mass - its top well inside the foundation, never flatter than 2.6 : 1. */
    static float hangD(float a, float dep) { return Math.max(.5f * (dep + Math.max(300, .5f * dep)), a / 2.6f); }

    /**
     * T260.18 E: ASYMMETRIC LOWERED BASE. Per organised group one broad region downshear of its towers (the moist inflow
     * / precipitation side) where the cloud base descends continuously by 300-1000 m (deepest inside, a ramp over
     * the outer three quarters: never a step or a terrace), along an outline warped by a km-scale field; the masses
     * descend by a third of it, so the lowered portion is also thicker. Upshear and between groups the base stays level
     * (recesses as in B carve it upward away from the towers).
     */
    static void lowered(Spec s, Plan p) {
        var env = p.env();
        float shAz = env.shearAz(), sh = env.shear();
        for (float[] a : s.anchors) {
            Group g = p.groups().get((int) a[0]);
            Random q = new Random(PersistentConvectionT2608.mix(s.seed ^ S_DROP, (long) a[0] + 1));
            float dir = shAz + (q.nextFloat() - .5f) * 1.2f, off = (.2f + .3f * q.nextFloat()) * a[3] * (.5f + .5f * Math.min(1, sh));
            float A = (.9f + .45f * q.nextFloat()) * a[3], B = A * (.5f + .3f * q.nextFloat());
            float drop = (300 + 450 * q.nextFloat()) * (.5f + .5f * a[5]) * (.8f + .4f * g.env().moist());
            s.drops.add(new float[]{a[1] + off * (float) Math.cos(dir), a[2] + off * (float) Math.sin(dir), A, B, (float) Math.cos(dir), (float) Math.sin(dir), drop});
        }
    }

    /** E: descent of the cloud base at (x, z), model metres (>= 0). */
    static float drop18(Spec s, float x, float z) {
        float w = 0, sw = 0, sd = 0;
        // outline warp (shared by every region): the lowered portions never have an elliptical rim
        float wx = x + 900 * noise(x / 3800, 8.5f, z / 3800, s.seed + 71), wz = z + 900 * noise(x / 3800, 9.5f, z / 3800, s.seed + 72);
        for (float[] r : s.drops) {
            float dx = wx - r[0], dz = wz - r[1];
            float lu = (dx * r[4] + dz * r[5]) / r[2], lv = (-dx * r[5] + dz * r[4]) / r[3];
            float rw = 1 - smooth(.25f, 1, (float) Math.sqrt(lu * lu + lv * lv));
            w = 1 - (1 - w) * (1 - rw); sw += rw; sd += rw * r[6]; // probabilistic union, weighted drop (no kink, no step)
        }
        if (sw <= 0) return 0;
        // broad bulges and hollows inside the lowered portion (two km-scale octaves, 0.45-1.15 of the drop): no plateau
        float mod = Math.max(.45f, Math.min(DROP_MOD, .82f + .28f * noise(x / 2600, 10.5f, z / 2600, s.seed + 73) + .14f * noise(x / 1100, 11.5f, z / 1100, s.seed + 74)));
        return w * sd / sw * mod;
    }

    /** T260.18 C: one tower root (ellipsoids {x, z, y centre above y0, a, b, c, cos, sin}, y0 = the group's base level). */
    static final class Root {
        final String label;
        final float y0, H, bx, bz, bR, lb;
        final float[] el;
        Root(String label, float y0, float H, float bx, float bz, float[] el) {
            this.label = label; this.y0 = y0; this.H = H; this.bx = bx; this.bz = bz; this.el = el;
            float r = 0, l = 1;
            for (int k = 0; k < el.length; k += 8) {
                float amax = Math.max(el[k + 3], el[k + 4]), mn = Math.min(Math.min(el[k + 3], el[k + 4]), el[k + 5]);
                r = Math.max(r, (float) Math.hypot(el[k] - bx, el[k + 1] - bz) + amax);
                l = Math.min(l, mn / Math.max(amax, el[k + 5]));
            }
            bR = r; lb = l;
        }
    }

    /**
     * T260.18 C: INTEGRATED TOWER ROOTS replacing the sleeves. Around the 2-5 strongest live towers of every organised
     * group a root spreads from the trunk down into the foundation: three stacked flattened masses that follow the trunk
     * axis and widen downward (1.7-2.4 trunk radii at the base, the trunk's own width at 0.6 H; height H = 17-37 % of the
     * top scaled by the tower's life, at most 36 %), each offset a little to its own side, plus 2-4 uneven buttress
     * masses at the foot (seeded azimuths, sizes, heights). Together with a thinner skirt lens (x {@link #LENS_C}), the
     * towers grow out of the foundation instead of standing on top of it.
     */
    static void roots(Spec s, Plan p, Org o, float[][] fp, float[] org, float t) {
        var env = p.env();
        int n = p.groups().size();
        for (int i = 0; i < n; i++) {
            if (org[i] == 0) continue;
            Group g = p.groups().get(i);
            float tau = t - g.birth();
            var st = o.scen().get(i).at(tau);
            float gx = g.x() - env.windX() * tau, gz = g.z() - env.windZ() * tau, m = smooth(ACTIVE, .6f, org[i]);
            float y0 = ConnectionsT26012.baseOffset(p, g);
            List<float[]> cand = new ArrayList<>(); // {index in geoms, score, x, z, w}
            for (int k = 0; k < st.geoms.size(); k++) {
                var c = st.geoms.get(k);
                float top = c.st[0], dd = c.st[2], em = c.st[3];
                if (top < 3000 || dd > .8f) continue;
                float h0 = .1f * top;
                cand.add(new float[]{k, top * (1 - dd) * em, gx + c.trunk.axisX(h0), gz + c.trunk.axisZ(h0), c.trunk.width(h0)});
            }
            cand.sort((a, b) -> a[1] != b[1] ? Float.compare(b[1], a[1]) : Long.compare(st.geoms.get((int) a[0]).spec.seed, st.geoms.get((int) b[0]).spec.seed));
            int max = 2 + (m > .3f ? 1 : 0) + (m > .6f ? 1 : 0) + (m > .85f ? 1 : 0);
            List<float[]> chosen = new ArrayList<>();
            for (float[] w : cand) {
                if (chosen.size() >= max) break;
                boolean apart = true;
                for (float[] c : chosen) apart &= Math.hypot(w[2] - c[2], w[3] - c[3]) > .5f * (w[4] + c[4]);
                if (apart) chosen.add(w);
            }
            for (float[] w : chosen) {
                var c = st.geoms.get((int) w[0]);
                float top = c.st[0], life = (1 - c.st[2]) * c.st[3];
                Random q = stream(c.spec.seed, S_ROOT, 0, 0);
                float H = Math.max(900, Math.min(.36f * top, (.17f + .1f * q.nextFloat() + .1f * m) * top * (.6f + .4f * life)));
                float rb = w[4] * (1.7f + .7f * q.nextFloat()) * (.8f + .2f * m);
                List<float[]> el = new ArrayList<>();
                // stacked masses: base, lower, upper (each centred on the trunk axis at its height, offset to its own side)
                float[][] lv = {{0, .38f}, {.3f, .35f}, {.62f, .3f}};
                for (int j = 0; j < 3; j++) {
                    float h = lv[j][0] * H, cv = lv[j][1] * H, oa = (float) (q.nextFloat() * 2 * Math.PI), off = j == 0 ? 0 : .12f * rb * q.nextFloat();
                    float rt = c.trunk.width(Math.max(50, h)), a = j == 0 ? rb : j == 1 ? .62f * rb + .38f * rt : 1.1f * rt + .12f * rb;
                    float b = a * (.78f + .2f * q.nextFloat()), ang = (float) (q.nextFloat() * Math.PI);
                    el.add(new float[]{gx + c.trunk.axisX(Math.max(50, h)) + off * (float) Math.cos(oa), gz + c.trunk.axisZ(Math.max(50, h)) + off * (float) Math.sin(oa), h,
                            a, b, cv, (float) Math.cos(ang), (float) Math.sin(ang)});
                }
                // buttresses at the foot (uneven azimuths and sizes)
                int nb = 2 + (q.nextFloat() < .6f ? 1 : 0) + (q.nextFloat() < .3f ? 1 : 0);
                float az0 = (float) (q.nextFloat() * 2 * Math.PI);
                for (int j = 0; j < nb; j++) {
                    float az = az0 + j * (float) (2 * Math.PI) / nb + (q.nextFloat() - .5f) * 1.4f, dist = (.6f + .35f * q.nextFloat()) * rb;
                    float a = (.32f + .28f * q.nextFloat()) * rb, b = a * (.5f + .35f * q.nextFloat()), yc = (.05f + .15f * q.nextFloat()) * H, cv = (.22f + .2f * q.nextFloat()) * H;
                    el.add(new float[]{w[2] + dist * (float) Math.cos(az), w[3] + dist * (float) Math.sin(az), yc, a, b, cv, (float) Math.cos(az), (float) Math.sin(az)});
                }
                float[] flat = new float[8 * el.size()];
                for (int j = 0; j < el.size(); j++) System.arraycopy(el.get(j), 0, flat, 8 * j, 8);
                s.roots.add(new Root("root G" + i + " " + c.spec.name, y0, H, w[2], w[3], flat));
            }
        }
    }

    /** Smooth union of the roots at the warped point (d = the distance so far, for the bound skip). */
    static float roots(Spec s, float x, float z, float yw, float d) {
        for (Root r : s.roots) {
            float yr = yw - r.y0, hd = (float) Math.hypot(x - r.bx, z - r.bz);
            // lower bound of the root: every element's pseudo-distance >= max(hd - bR, yr - H) x lb; the smin chain of n
            // elements may lower the minimum by (n - 1) x blend / 4
            float lo = Math.max(hd - r.bR, yr - r.H) * r.lb - r.el.length / 32f * ROOT_BLEND;
            if (lo > d + ROOT_BLEND) continue;
            float[] e = r.el;
            float dr = 1e9f;
            for (int k = 0; k < e.length; k += 8) {
                float dx = x - e[k], dz = z - e[k + 1];
                float lu = (dx * e[k + 6] + dz * e[k + 7]) / e[k + 3], lv = (-dx * e[k + 7] + dz * e[k + 6]) / e[k + 4], ly = (yr - e[k + 2]) / e[k + 5];
                float mn = Math.min(Math.min(e[k + 3], e[k + 4]), e[k + 5]);
                dr = ConvectiveSystemT260.smin(dr, ((float) Math.sqrt(lu * lu + lv * lv + ly * ly) - 1) * mn, ROOT_BLEND);
            }
            d = ConvectiveSystemT260.smin(d, dr, ROOT_BLEND);
        }
        return d;
    }

    /**
     * T260.18 field: the T260.15 field with one large-scale change per variant - A: deep hanging masses smooth-joined
     * under the base (instead of the T260.15 lobes) with underside billows that grow with the depth; B: the uneven base
     * ({@link #lift18}; the masses rise by half of it, so raised portions are also thinner) and deep recesses; C: the
     * tower roots on a thinner lens. Openings, billows and the tower protection are the T260.15 ones.
     */
    /**
     * S7a: depth (model metres) by which the base cut sags below the shared base under the masses. Each mass contributes
     * {@code SAG * T} at its centre, falling smoothly to 0 at its outline ((1 - r^2)^2 on its elongated footprint);
     * overlapping masses blend by weight and their coverage is a probabilistic union, so there is no max-crease between
     * neighbours (T260.18 lesson). Horizontal slope <= ~SAG * T / R, well inside the field's Lipschitz budget.
     */
    static float sag19(Spec s, float x, float z) {
        float sw = 0, sd = 0, miss = 1;
        for (Mass m : s.masses) {
            float dx = x - m.cx, dz = z - m.cz;
            float lu = (dx * m.ca + dz * m.sa) / m.elong, lv = -dx * m.sa + dz * m.ca;
            float q = (lu * lu + lv * lv) / (m.R * m.R);
            if (q >= 1) continue;
            float b = (1 - q) * (1 - q);
            sw += b; sd += b * SAG * m.T; miss *= 1 - b;
        }
        return sw > 0 ? sd / sw * (1 - miss) : 0;
    }

    static SparseBrickVolume.Field field18(Spec s) {
        boolean va = s.version == V18A, vb = s.version == V18B, vc = rooted(s.version), vd = s.version == V18D || d2(s.version) || s.version == V18F, ve = s.version == V18E || s.version == V18F;
        boolean sag = s.version == V19;
        boolean hangs = va || vd;
        float hb = vd ? BLEND_D : HANG_BLEND, hl = vd ? LUMP_D : HANG_LUMP;
        return (x, y, z) -> {
            float yb0 = base(s, x, z) + 70 * noise(x / 4200, 4.5f, z / 4200, s.seed + 13);
            float dr = ve ? drop18(s, x, z) : 0;
            float cut = sag ? sag19(s, x, z) : 0; // S7a: the base cut sags under the masses
            float off = vb ? lift18(s, x, z) : 0, yb = yb0 + off - dr, ym = yb0 + .5f * off - dr / 3;
            float x0 = x, z0 = z;
            x = x0 + 260 * noise(x0 / 1700, y / 900, z0 / 1700, s.seed + 31);
            z = z0 + 260 * noise(x0 / 1700, y / 900, z0 / 1700, s.seed + 32);
            float yw = y + 140 * noise(x0 / 1500, y / 1500, z0 / 1500, s.seed + 33) * smooth(ym, ym + 400, y);
            float d = masses(s, x, z, yw, ym);
            if (vc) d = roots(s, x, z, yw, d);
            float yu = y + 60 * noise(x0 / 1100, y / 700, z0 / 1100, s.seed + 34) * (1 - smooth(yb, yb + 300, y));
            d = ConvectiveSystemT260.smax(d, yb - cut - yu, 120);
            for (float[] b : s.bays) {
                float dx = x - b[0], dz = z - b[1], mn = Math.min(b[3], b[6]);
                if (((float) Math.hypot(dx, dz) / b[2] - 1) * mn >= BAY_BLEND - d) continue;
                float lu = (dx * b[4] + dz * b[5]) / b[2], lv = (-dx * b[5] + dz * b[4]) / b[3], ly = (yu - yb + .4f * b[6]) / b[6];
                d = ConvectiveSystemT260.smax(d, -((float) Math.sqrt(lu * lu + lv * lv + ly * ly) - 1) * mn, BAY_BLEND);
            }
            for (float[] l : s.lobes) {
                float dx = x - l[0], dz = z - l[1], mn = Math.min(l[3], l[7]);
                if (((float) Math.hypot(dx, dz) / l[2] - 1) * mn >= d + LOBE_BLEND) continue;
                float lu = (dx * l[4] + dz * l[5]) / l[2], lv = (-dx * l[5] + dz * l[4]) / l[3], ly = (yu - (yb - l[6] + l[7])) / l[7];
                d = ConvectiveSystemT260.smin(d, ((float) Math.sqrt(lu * lu + lv * lv + ly * ly) - 1) * mn, LOBE_BLEND);
            }
            d = ConvectiveSystemT260.smax(d, (HOLE - coverage(s, x0, y, z0)) * 450, 220);
            if (hangs) {
                // after the openings (the masses are placed only where the foundation is covered: none hangs under a hole,
                // and no opening slices one into flanges)
                float lump = Float.NaN;
                for (float[] h : s.hang) {
                    float yc = yb - h[6] + h[7], dx = x - h[0] - h[8] * (yc - yu), dz = z - h[1] - h[9] * (yc - yu);
                    float mn = Math.min(h[3], h[7]);
                    float lu = (dx * h[4] + dz * h[5]) / h[2], lv = (-dx * h[5] + dz * h[4]) / h[3], ly = (yu - yc) / h[7];
                    float dh = ((float) Math.sqrt(lu * lu + lv * lv + ly * ly) - 1) * mn;
                    if (dh - hl >= d + hb) continue;
                    // irregular lumps on the hanging mass (one shared noise per sample, amplitude <= hl; D: two octaves)
                    if (Float.isNaN(lump)) lump = vd ? .45f * noise(x0 / 700, y / 500, z0 / 700, s.seed + 51) + .55f * noise(x0 / 1600, y / 1100, z0 / 1600, s.seed + 52)
                            : noise(x0 / 700, y / 500, z0 / 700, s.seed + 51);
                    dh += Math.min(hl, .12f * mn) * lump;
                    d = ConvectiveSystemT260.smin(d, dh, hb);
                }
            }
            if (d < 900) {
                float up = smooth(yb - 20, yb + 160, y);
                d += (140 * noise(x / 900, y / 900, z / 900, s.seed * 7 + 1) + 55 * noise(x / 380, y / 380, z / 380, s.seed * 13 + 5)) * up * (1 - smooth(500, 900, d));
                // underside billowing: the relief character, and (A) everywhere on the hanging masses, growing with the depth
                float ch = hangs ? Math.max(character(s, x0, z0), 1.6f * smooth(80, 700, yb - y)) : ve ? Math.max(character(s, x0, z0), 1.4f * smooth(100, 700, dr)) : character(s, x0, z0);
                d += ch * (50 * noise(x / 700, y / 600, z / 700, s.seed * 7 + 3) + 20 * noise(x / 280, y / 280, z / 280, s.seed * 13 + 7))
                        * (1 - up) * (1 - smooth(300, 700, d));
            }
            return d;
        };
    }
}
