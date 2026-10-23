package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.ConnectionsT26012.Connection;
import net.Gabou.projectatmosphere.clouds.morphology.MulticellSystemT26011.Group;
import net.Gabou.projectatmosphere.clouds.morphology.MulticellSystemT26011.Plan;
import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.CellSpec;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2607.noise;
import static net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2607.stream;

/**
 * T260.13 experimental prototype (never production): a DEDICATED LOWER-CLOUD-MASS component between two neighbouring
 * T260.11 groups. Unlike T260.12 (connections made of small convective cells) it is its own geometry: a footprint that
 * grows outward from the lower flanks of the groups, a variable thickness with large folds, and an uneven base.
 * <p>
 * Inputs from T260.12: which pair, and its persistent strength A(t) (environment, base compatibility, maturity, shear,
 * separation). Growth follows A(t), so the mass is the same at every request of the same time and grows / dissipates
 * gradually. Pairs that T260.12 never connects get nothing (independent groups stay independent).
 * <p>
 * The roots sit in the closest live cells of the two groups at the requested time, inside their lower masses, so the
 * component starts IN the towers' bases and grows into the gap. The field is a pseudo-distance in model metres (y from
 * the model base); the preview smooth-unions it with the two groups' exact region fields in its own region volume.
 * <ul>
 *   <li>A - one broad, strongly irregular mass along a bent path: warped, noisy outline (+-50 %), one carved bay,
 *   thickness 0.3-1 of its maximum with large folds, uneven base;</li>
 *   <li>B - several overlapping lower masses (4-6 pads) with their own sizes, elongations, thicknesses and bases,
 *   activating at their own strength levels (near the groups first), leaving partial gaps;</li>
 *   <li>C - growth tongues from BOTH groups along curved paths bending to opposite sides, growing at different rates
 *   (asymmetric), thick at the root (merged into the tower base), thinner and lobed toward the tip, meeting off-centre.</li>
 * </ul>
 */
public final class LowerMassT26013 {
    private LowerMassT26013() {}

    public enum Variant { A, B, C }

    static final long S_LM = 0x10A52613L;

    /** One labelled component box (model metres) for the diagnostic frames. */
    public record Part(String label, float[] box) {}

    /** The resolved component at one time: pair, strength, roots, frame, and its parts. */
    public static final class Spec {
        public final Variant variant;
        public final int a, b;
        public final float strength, t;
        final float ax, az, ra, bx, bz, rb, d, ux, uz, vx, vz, yb0;
        final long seed;
        final List<float[]> pads = new ArrayList<>();   // B: {cx, cz, R, elong, angle, T, yb, seed, grow}
        final float[] tongues = new float[16];           // C: per tongue {rootX, rootZ, dirX, dirZ, L, side, w0, T0}
        float bend, wmax, tmax, bayX, bayZ, bayR, reach;  // A
        public final List<Part> parts = new ArrayList<>();
        private Bounds bounds;

        Spec(Variant v, int a, int b, float strength, float t, float[] nr, float yb0, long seed) {
            variant = v; this.a = a; this.b = b; this.strength = strength; this.t = t;
            ax = nr[0]; az = nr[1]; ra = nr[2]; bx = nr[3]; bz = nr[4]; rb = nr[5];
            float dx = bx - ax, dz = bz - az;
            d = Math.max(1, (float) Math.hypot(dx, dz));
            ux = dx / d; uz = dz / d; vx = -uz; vz = ux;
            this.yb0 = yb0;
            this.seed = seed;
        }

        public Bounds bounds() { return bounds; }
        /** Centre of the component and pair axis (metres), for the close inspection view. */
        public float[] centre() {
            float[] c = {0, 0, 0};
            for (Part p : parts) { c[0] += .5f * (p.box[0] + p.box[3]); c[1] += .5f * (p.box[1] + p.box[4]); c[2] += .5f * (p.box[2] + p.box[5]); }
            int n = Math.max(1, parts.size());
            return new float[]{c[0] / n, c[1] / n, c[2] / n, ux, uz};
        }
        public String describe() {
            return String.format(Locale.ROOT, "variant=%s pair=G%d-G%d strength=%.2f rootDistance=%.1fkm edgeGap=%.1fkm roots r=%.0f/%.0f parts=%d", variant, a, b, strength, d / 1000,
                    (d - ra - rb) / 1000, ra, rb, parts.size());
        }
    }

    /**
     * PROTOTYPE OVERRIDE (T260.13 geometry study only): the pair's strength curve ignores T260.12's cloud-base
     * compatibility term, and the pair is the best SEPARATED live neighbour pair at T_REF. Under the full T260.12 rules
     * no separated pair of 2609196 connects (their bases differ by ~240 m); this must be revisited before generalising.
     */
    public static final boolean PROTOTYPE_IGNORE_BASE = true;

    /** System time at which the prototype pair is chosen (the system's "mature" stage); the choice never changes. */
    public static final float T_REF = 75;
    static final float DT = .5f, T0 = -14;

    /** One candidate pair with its strength curve (T260.12 formula, maturity from the groups' LIVE cells). */
    record Pair(int a, int b, float threshold, float[] strength, long seed) {
        float at(float t) { return strength[Math.max(0, Math.min(strength.length - 1, Math.round((t - T0) / DT)))]; }
    }

    /**
     * The lower mass of the system's prototype pair at time t, or null (no suitable pair, or not formed / already
     * dissipated at t). The pair is chosen once per system (best bridgeable pair at {@link #T_REF}); its roots are fixed
     * where the two groups were nearest when the connection formed, so the component grows and dissipates in place.
     */
    public static Spec spec(Plan p, List<Connection> unused, float t, Variant v) {
        Pair best = choose(p);
        if (best == null) return null;
        float A = best.at(t);
        if (A < .05f) return null;
        int formed = -1;
        for (int k = 0; k < best.strength().length; k++) if (best.strength()[k] > .1f) { formed = k; break; }
        float[] nr = frame(p, best.a(), best.b(), T0 + formed * DT);
        if (nr == null) return null;
        float yb0 = .5f * (ConnectionsT26012.baseOffset(p, p.groups().get(best.a())) + ConnectionsT26012.baseOffset(p, p.groups().get(best.b()))) + 70;
        Spec s = new Spec(v, best.a(), best.b(), A, t, nr, yb0, PersistentConvectionT2608.mix(best.seed() ^ S_LM, v.ordinal() + 1));
        build(s);
        return s;
    }

    private static final java.util.Map<Long, java.util.Optional<Pair>> CHOSEN = new java.util.concurrent.ConcurrentHashMap<>();

    /** Best bridgeable pair at T_REF: both groups live, connected (A > 0.3), measured gap 0..0.7 spacing preferred. */
    static Pair choose(Plan p) {
        return CHOSEN.computeIfAbsent(p.seed(), k -> {
            Pair best = null;
            float bestScore = 0;
            int n = p.groups().size();
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
                float[] fr = footprints(p, i, j, T_REF);
                if (fr == null) continue;
                // Group-level separation: centroid distance against the two footprint radii.
                float gap = (float) Math.hypot(fr[3] - fr[0], fr[4] - fr[1]) - fr[2] - fr[5];
                if (gap > 1.2f * p.spacing()) continue;
                Pair c = curve(p, i, j);
                float a = c.at(T_REF);
                if (a < .3f) continue;
                // Separated neighbours only (a merged pair already reads as one mass); nearer is better.
                if (gap < 0 || gap > .7f * p.spacing()) continue;
                float score = a + 1 - gap / p.spacing();
                if (score > bestScore) { bestScore = score; best = c; }
            }
            return java.util.Optional.ofNullable(best);
        }).orElse(null);
    }

    /** T260.12 pair strength, integrated over system time, with maturity taken from the groups' live cells. */
    static Pair curve(Plan p, int i, int j) {
        Group a = p.groups().get(i), b = p.groups().get(j);
        var e = p.env();
        int steps = Math.round((MulticellSystemT26011.T_END - T0) / DT) + 1;
        Random q = stream(p.seed(), ConnectionsT26012.S_PAIR, i, j);
        float threshold = .22f + .38f * q.nextFloat();
        float base = PROTOTYPE_IGNORE_BASE ? 1 : 1 - VerticalGrowthT26010.smooth(70, 300, Math.abs(ConnectionsT26012.baseOffset(p, a) - ConnectionsT26012.baseOffset(p, b)) / .6f);
        float moist = .35f + .65f * .5f * (a.env().moist() + b.env().moist());
        float axis = (float) Math.atan2(b.z() - a.z(), b.x() - a.x());
        float shear = 1 - .4f * e.shear() * Math.abs((float) Math.cos(axis - e.shearAz()));
        float pot0 = base * moist * shear * (.85f + .3f * q.nextFloat());
        List<CellSpec> ca = MulticellSystemT26011.scenario(a).cells, cb = MulticellSystemT26011.scenario(b).cells;
        float[] s = new float[steps];
        float A = 0;
        for (int k = 0; k < steps; k++) {
            float t = T0 + k * DT;
            float[] nr = nearest(p, i, j, t);
            float gap = nr == null ? Float.MAX_VALUE : (float) Math.hypot(nr[3] - nr[0], nr[4] - nr[1]) - nr[2] - nr[5];
            float prox = nr == null ? 0 : 1 - VerticalGrowthT26010.smooth(-.1f * p.spacing(), .75f * p.spacing(), gap);
            float m = (float) Math.pow(Math.min(liveMaturity(a, ca, t), liveMaturity(b, cb, t)), .7);
            float pot = pot0 * prox * m;
            A += DT * (pot > threshold ? (pot - threshold) / (1 - threshold) / ConnectionsT26012.T_GROW : -1 / ConnectionsT26012.T_DECAY);
            A = Math.max(0, Math.min(1, A));
            s[k] = A;
        }
        return new Pair(i, j, threshold, s, PersistentConvectionT2608.mix(p.seed() ^ ConnectionsT26012.S_PAIR, (long) i * 64 + j + 1));
    }

    /** Maturity from live, not-yet-dissolving cells (0 once the group's cells are gone). */
    static float liveMaturity(Group g, List<CellSpec> cells, float t) {
        float tau = t - g.birth();
        int live = 0;
        for (CellSpec c : cells) if (tau >= c.birth && tau < c.decay) live++;
        return VerticalGrowthT26010.smooth(6, 20, tau) * Math.min(1, live / 2f) * (g.failed() ? .45f : 1);
    }

    /**
     * Group footprints at time t: {centroidA x, z, radiusA, centroidB x, z, radiusB} from the live cells (radius = the
     * farthest live cell edge from the centroid), or null when either group has no live cell.
     */
    static float[] footprints(Plan p, int i, int j, float t) {
        float[] fa = footprint(p, p.groups().get(i), t), fb = footprint(p, p.groups().get(j), t);
        return fa == null || fb == null ? null : new float[]{fa[0], fa[1], fa[2], fb[0], fb[1], fb[2]};
    }

    static float[] footprint(Plan p, Group g, float t) {
        var e = p.env();
        float tau = t - g.birth(), sx = 0, sz = 0, sw = 0;
        List<CellSpec> cells = MulticellSystemT26011.scenario(g).cells;
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

    /**
     * The component's frame: roots at the two groups' live-cell centroids (inside their lower masses), radii = their
     * footprint radii: {rootA x, z, RA, rootB x, z, RB}. The lower mass is sized to the groups it joins, so it spreads
     * under and beyond their bases instead of filling only the gap between their flanks.
     */
    static float[] frame(Plan p, int i, int j, float t) {
        return footprints(p, i, j, t);
    }

    /** Closest pair of live cells of groups i and j at system time t: {ax, az, ra, bx, bz, rb} (metres, steering frame). */
    static float[] nearest(Plan p, int i, int j, float t) {
        Group a = p.groups().get(i), b = p.groups().get(j);
        var e = p.env();
        List<CellSpec> ca = MulticellSystemT26011.scenario(a).cells, cb = MulticellSystemT26011.scenario(b).cells;
        float best = Float.MAX_VALUE;
        float[] out = null;
        float ta = t - a.birth(), tb = t - b.birth();
        for (CellSpec x : ca) {
            if (ta < x.birth || ta > x.decay + x.tauD) continue;
            float xx = a.x() + x.x0 + (x.vx - e.windX()) * ta, xz = a.z() + x.z0 + (x.vz - e.windZ()) * ta, xr = PersistentConvectionT2608.Scenario.baseR(x, ta);
            for (CellSpec y : cb) {
                if (tb < y.birth || tb > y.decay + y.tauD) continue;
                float yx = b.x() + y.x0 + (y.vx - e.windX()) * tb, yz = b.z() + y.z0 + (y.vz - e.windZ()) * tb, yr = PersistentConvectionT2608.Scenario.baseR(y, tb);
                float g = (float) Math.hypot(yx - xx, yz - xz) - xr - yr;
                if (g < best) { best = g; out = new float[]{xx, xz, xr, yx, yz, yr}; }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- construction (seeded, from the strength)

    static void build(Spec s) {
        Random g = stream(s.seed, S_LM, 0, 0);
        float A = s.strength, rAvg = .5f * (s.ra + s.rb);
        float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE, maxY = 0;
        switch (s.variant) {
            case A -> {
                s.bend = (g.nextFloat() - .5f) * .55f * s.d;
                s.wmax = .75f * rAvg + .22f * s.d;
                s.tmax = 1600 + 900 * g.nextFloat();
                s.reach = .05f + .5f * (float) Math.pow(A, .7);
                float bf = .3f + .4f * g.nextFloat(), side = g.nextFloat() < .5f ? -1 : 1;
                s.bayX = s.ax + s.ux * bf * s.d + s.vx * side * .75f * s.wmax + s.vx * s.bend * 4 * bf * (1 - bf);
                s.bayZ = s.az + s.uz * bf * s.d + s.vz * side * .75f * s.wmax + s.vz * s.bend * 4 * bf * (1 - bf);
                s.bayR = (.35f + .25f * g.nextFloat()) * s.wmax;
                float w = 2.2f * s.wmax; // outline reaches 1.5 x wmax plus the 0.35 x wmax warp
                for (float f = 0; f <= 1.001f; f += .25f) {
                    float px = s.ax + s.ux * f * s.d + s.vx * s.bend * 4 * f * (1 - f), pz = s.az + s.uz * f * s.d + s.vz * s.bend * 4 * f * (1 - f);
                    minX = Math.min(minX, px - w); maxX = Math.max(maxX, px + w); minZ = Math.min(minZ, pz - w); maxZ = Math.max(maxZ, pz + w);
                }
                maxY = s.yb0 + 1.5f * s.tmax + 300;
                s.parts.add(new Part("A mass", new float[]{minX, s.yb0 - 200, minZ, maxX, s.yb0 + s.tmax, maxZ}));
            }
            case B -> {
                int n = 4 + (int) (g.nextFloat() * 2.99f);
                for (int k = 0; k < n; k++) {
                    float f = Math.max(.02f, Math.min(.98f, (k + .5f) / n + (g.nextFloat() - .5f) * .14f));
                    float lat = (float) g.nextGaussian() * .3f * s.d;
                    float cx = s.ax + s.ux * f * s.d + s.vx * lat, cz = s.az + s.uz * f * s.d + s.vz * lat;
                    float R = (.14f + .16f * g.nextFloat()) * s.d + .42f * rAvg;
                    float elong = 1 + 1.3f * g.nextFloat(), ang = (float) (g.nextFloat() * Math.PI);
                    float T = 800 + 1500 * g.nextFloat(), yb = s.yb0 + (g.nextFloat() - .5f) * 260;
                    float level = Math.min(.9f, .08f + .8f * Math.min(f, 1 - f) + .15f * g.nextFloat());
                    long ps = PersistentConvectionT2608.mix(s.seed, k + 11);
                    if (A < level) continue; // this mass has not formed (partial gaps)
                    float grow = .35f + .65f * VerticalGrowthT26010.smooth(level, level + .3f, A);
                    s.pads.add(new float[]{cx, cz, R * grow, elong, ang, T * (.5f + .5f * grow), yb, ps, grow});
                    float w = 2.1f * R * elong; // 1.35 R noisy outline + 0.3 R warp
                    minX = Math.min(minX, cx - w); maxX = Math.max(maxX, cx + w); minZ = Math.min(minZ, cz - w); maxZ = Math.max(maxZ, cz + w);
                    maxY = Math.max(maxY, yb + 1.4f * T + 300);
                    s.parts.add(new Part("B pad " + k, new float[]{cx - R * elong, yb - 150, cz - R * elong, cx + R * elong, yb + T, cz + R * elong}));
                }
            }
            case C -> {
                // Tongue 0 from group a, tongue 1 from group b; different growth rates (asymmetric meeting).
                float rateA = 1.1f + .4f * g.nextFloat(), rateB = .65f + .3f * g.nextFloat();
                if (g.nextFloat() < .5f) { float tmp = rateA; rateA = rateB; rateB = tmp; }
                float sideA = g.nextFloat() < .5f ? -1 : 1;
                for (int k = 0; k < 2; k++) {
                    float rx = k == 0 ? s.ax : s.bx, rz = k == 0 ? s.az : s.bz, rr = k == 0 ? s.ra : s.rb;
                    float dirX = k == 0 ? s.ux : -s.ux, dirZ = k == 0 ? s.uz : -s.uz;
                    float rate = k == 0 ? rateA : rateB;
                    // Root inside the tower base; length grows with the strength.
                    float rootX = rx + dirX * .45f * rr, rootZ = rz + dirZ * .45f * rr;
                    float L = Math.max(200, Math.min(1, A * rate) * (.55f + .2f * g.nextFloat()) * (s.d + .55f * (s.ra + s.rb)));
                    float side = k == 0 ? sideA : -sideA; // bend to opposite sides: they meet offset, not head-on
                    float w0 = .55f * rr + .1f * s.d, T0 = 1500 + 900 * g.nextFloat();
                    int o = k * 8;
                    s.tongues[o] = rootX; s.tongues[o + 1] = rootZ; s.tongues[o + 2] = dirX; s.tongues[o + 3] = dirZ;
                    s.tongues[o + 4] = L; s.tongues[o + 5] = side * (.12f + .1f * g.nextFloat()) * L; s.tongues[o + 6] = w0; s.tongues[o + 7] = T0;
                    float ex = rootX + dirX * L, ez = rootZ + dirZ * L, w = 2.3f * w0 + Math.abs(s.tongues[o + 5]); // 1.3 w0 + 0.57 w0 warp
                    float tminX = Math.min(rootX, ex) - w, tmaxX = Math.max(rootX, ex) + w, tminZ = Math.min(rootZ, ez) - w, tmaxZ = Math.max(rootZ, ez) + w;
                    minX = Math.min(minX, tminX); maxX = Math.max(maxX, tmaxX); minZ = Math.min(minZ, tminZ); maxZ = Math.max(maxZ, tmaxZ);
                    maxY = Math.max(maxY, s.yb0 + 1.5f * T0 + 300);
                    s.parts.add(new Part("C tongue " + (k == 0 ? "G" + s.a : "G" + s.b), new float[]{tminX, s.yb0 - 200, tminZ, tmaxX, s.yb0 + T0, tmaxZ}));
                }
            }
        }
        s.bounds = s.parts.isEmpty() ? null : new Bounds(minX - 400, s.yb0 - 500, minZ - 400, maxX + 400, maxY, maxZ + 400);
    }

    // ---------------------------------------------------------------- field (model metres, pseudo-distance)

    static float fbm(float x, float z, float scale, long seed) {
        float q = 1 / scale;
        return (noise(x * q, 0.37f, z * q, seed) + .5f * noise(2.03f * x * q, 1.71f, 2.03f * z * q, seed * 31 + 7)
                + .25f * noise(4.1f * x * q, 2.9f, 4.1f * z * q, seed * 17 + 3)) / 1.75f;
    }

    /** Pseudo-distance of the lower mass (positive outside), in model metres; y measured from the model base. */
    public static SparseBrickVolume.Field field(Spec s) {
        return (x, y, z) -> {
            float d = switch (s.variant) {
                case A -> massA(s, x, y, z);
                case B -> massB(s, x, y, z);
                case C -> massC(s, x, y, z);
            };
            // Billowed upper surface, flatter base (surface texture of the component itself).
            // Billows at the component's own scale (km-wide mass: ~120 m bulges on 600 m, 45 m on 230 m), flat base.
            if (d < 600) d += (120 * noise(x / 600, y / 600, z / 600, s.seed * 7 + 1) + 45 * noise(x / 230, y / 230, z / 230, s.seed * 13 + 5))
                    * VerticalGrowthT26010.smooth(s.yb0 - 50, s.yb0 + 260, y);
            return d;
        };
    }

    static float massA(Spec s, float x, float y, float z) {
        float px = x - s.ax, pz = z - s.az;
        float fr = (px * s.ux + pz * s.uz) / s.d, f = Math.max(0, Math.min(1, fr));
        float cx = s.ax + s.ux * f * s.d + s.vx * s.bend * 4 * f * (1 - f), cz = s.az + s.uz * f * s.d + s.vz * s.bend * 4 * f * (1 - f);
        float lat = (float) Math.hypot(x - cx, z - cz);
        float overshoot = (fr < 0 ? -fr : fr > 1 ? fr - 1 : 0) * s.d;
        // Warped coordinates for the outline: strongly irregular, no repeated lobes.
        float wx = x + .35f * s.wmax * fbm(x, z, 1500, s.seed + 1), wz = z + .35f * s.wmax * fbm(x, z, 1500, s.seed + 2);
        float W = s.wmax * (.55f + .45f * (float) Math.sin(Math.PI * f)) * (1 + .5f * fbm(wx, wz, 1700, s.seed + 3));
        float c = W - lat - overshoot;
        c = Math.min(c, (float) Math.hypot(x - s.bayX, z - s.bayZ) - s.bayR);             // carved bay (concavity)
        c -= Math.max(0, Math.min(f, 1 - f) - s.reach) * 2 * s.d;                         // grows from both roots inward
        float T = s.tmax * (.3f + .7f * (.5f + .5f * fbm(x, z, 2200, s.seed + 4))) * VerticalGrowthT26010.smooth(0, .45f * s.wmax, c);
        float yb = s.yb0 + 110 * noise(x / 1800, 0.5f, z / 1800, s.seed + 5) + 55 * noise(x / 700, 1.5f, z / 700, s.seed + 6);
        float top = yb + T + .22f * s.tmax * noise(x / 1100, 2.5f, z / 1100, s.seed + 7) * VerticalGrowthT26010.smooth(0, .3f * s.wmax, c);
        return ConvectiveSystemT260.smax(-c, Math.max(yb - y, y - top), 120);
    }

    static float massB(Spec s, float x, float y, float z) {
        float d = 1e9f;
        for (float[] p : s.pads) {
            float dx = x - p[0], dz = z - p[1], ca = (float) Math.cos(p[4]), sa = (float) Math.sin(p[4]);
            float lu = (dx * ca + dz * sa) / p[3], lv = -dx * sa + dz * ca;
            float rr = (float) Math.hypot(lu, lv);
            long ps = (long) p[7];
            float wx = x + .3f * p[2] * fbm(x, z, 900, ps + 1), wz = z + .3f * p[2] * fbm(x, z, 900, ps + 2);
            float R = p[2] * (1 + .35f * fbm(wx, wz, 1000, ps + 3));
            float dh = rr - R;
            float yb = p[6] + 70 * noise(x / 1000, .5f, z / 1000, ps + 4);
            float dome = (float) Math.pow(Math.max(0, 1 - (rr / Math.max(1, R)) * (rr / Math.max(1, R))), .6);
            float top = yb + p[5] * dome * (.75f + .25f * noise(x / 800, 1.5f, z / 800, ps + 5)) + .2f * p[5] * noise(x / 650, 2.5f, z / 650, ps + 6);
            float dk = ConvectiveSystemT260.smax(dh, Math.max(yb - y, y - top), 100);
            d = ConvectiveSystemT260.smin(d, dk, 150);
        }
        return d;
    }

    static float massC(Spec s, float x, float y, float z) {
        float d = 1e9f;
        for (int k = 0; k < 2; k++) {
            int o = k * 8;
            float rx = s.tongues[o], rz = s.tongues[o + 1], dx = s.tongues[o + 2], dz = s.tongues[o + 3], L = s.tongues[o + 4], bend = s.tongues[o + 5];
            float w0 = s.tongues[o + 6], T0 = s.tongues[o + 7];
            float tr = ((x - rx) * dx + (z - rz) * dz) / L, t = Math.max(0, Math.min(1, tr));
            float vx = -dz, vz = dx;
            float cx = rx + dx * t * L + vx * bend * 4 * t * (1 - t), cz = rz + dz * t * L + vz * bend * 4 * t * (1 - t);
            long ts = s.seed + 101 * (k + 1);
            // Lobed, increasingly ragged toward the tip (finger-like growth front).
            float amp = (.12f + .45f * t) * w0;
            float wx = x + amp * fbm(x, z, 700, ts + 1), wz = z + amp * fbm(x, z, 700, ts + 2);
            float lat = (float) Math.hypot(wx - cx, wz - cz);
            float w = w0 * (1 - .55f * t) * (1 + .3f * fbm(x, z, 1200, ts + 3));
            float dh = lat - w + (tr > 1 ? (tr - 1) * L : 0) + (tr < 0 ? -tr * L : 0);
            float T = T0 * (1 - .5f * t) * (.7f + .3f * noise(x / 1000, .5f, z / 1000, ts + 4));
            float yb = s.yb0 - 60 * (1 - t) + 90 * noise(x / 1500, 1.5f, z / 1500, ts + 5);
            float prof = (float) Math.pow(Math.max(0, 1 - (lat / Math.max(1, w)) * (lat / Math.max(1, w))), .5);
            float top = yb + T * prof + .2f * T0 * noise(x / 900, 2.5f, z / 900, ts + 6);
            float dk = ConvectiveSystemT260.smax(dh, Math.max(yb - y, y - top), 110);
            d = ConvectiveSystemT260.smin(d, dk, 220);
        }
        return d;
    }
}
