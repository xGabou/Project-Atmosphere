package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2607.Deform;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2607.Elem;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2607.noise;
import static net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2607.smooth;
import static net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2607.stream;

/**
 * T260.8 experimental prototype: persistent convective cells and identity-preserving merging (test sources only).
 * <p>
 * A system is a list of persistent cumulus cells ({@link CellSpec}: stable id, own seed, growth potential, seeded
 * anatomy). Nothing is a template: the geometry of a cell at time t is reconstructed from its spec and the
 * deterministic growth history up to t. The same (spec, t) always gives the same cloud.
 * <ul>
 *   <li><b>Trunk.</b> A growth column (T260.6 B style: azimuthal harmonics, upper expansion, shoulders, convex base)
 *   whose profile is a function of the ABSOLUTE height above the cloud base in the cell's own frame, referred to the
 *   cell's potential (growth-invariant) height. The lower part therefore stays where it is while the tower rises out
 *   of it; nothing is a fraction of the current height except the rising dome.</li>
 *   <li><b>Persistent billows.</b> Every billow slot is fixed once per cell (own stream per candidate, Poisson spacing
 *   in a reference cylinder, accepted in candidate order): absolute height and azimuth in the cell frame. A billow is
 *   born when the rising top first passes its height, grows from nothing, is active while young and sinks / broadens /
 *   softens with age. Growth never re-places a billow; it only ages it. Shape = T260.7 R1 compound billow with
 *   continuous activity weights (no discrete lobe count switch).</li>
 *   <li><b>Merging.</b> Cells are never deleted or rebuilt. An attachment value alpha per cell pair integrates over
 *   time from the cells' proximity (attach / detach rates). The composed field keeps every cell's own distance and adds
 *   (a) a pair blend whose radius grows with alpha and is strongest low in the cloud (depends on both shapes: it fills
 *   only where the surfaces are close) and (b) connecting material: billow-like elements with their own pair identity
 *   that grow out of a parent flank toward the other cell, each appearing at its own alpha threshold. A strongly
 *   attached pair may seed a daughter cell (new growth at the junction, own identity derived from the pair).</li>
 *   <li><b>Interaction.</b> Attachment raises the vertical potential of the deeper partner (dominance) and the
 *   horizontal potential of the shallower one (spreading lower mass). Weak cells may dissipate (erosion in the cell
 *   frame).</li>
 * </ul>
 * Coordinates: system frame, cloud base at {@link #BASE_Y}. Every noise / feature lives in a cell-local or pair-local
 * frame, so the system can translate with the wind without features sliding.
 */
public final class PersistentConvectionT2608 {
    private PersistentConvectionT2608() {}

    static final long S_TRUNK = 0x7A0C2608L, S_BSLOT = 0xB5102608L, S_BSHAPE = 0xB5A92608L, S_BLOBE = 0xB10B2608L,
            S_BRIDGE = 0xB41D2608L, S_DAUGHTER = 0xDA062608L, S_ERODE = 0xE40D2608L, S_RELIEF = 0x4E1F2608L, S_ACT = 0xAC712608L, S_T2 = 0x72B2608L;

    public static float BASE_Y = 0, BASE_BLEND = 8, BAND = 30;
    public static float BILLOW_R = .40f, SPACING = 1.1f, PROT = .70f, UP = .35f, SHOULDER = .26f, APEX = .22f;
    public static float HCAL = 780, KMAX = 140, INFL = 1.2f, T_ATTACH = 7, T_DETACH = 14, AGE_T = 7, BORN_T = 4.5f, DT = .25f;
    public static float BOOST_H = .22f, BOOST_R = .22f, DAUGHTER_AT = .6f, DAUGHTER_P = .85f;
    public static int BRIDGES = 16;
    public static float T2_R = .34f, BASE_WIDE = .28f;

    // T260.8 review iteration: 0 = reviewed T260.8 (frozen), 1 = C1 multi-scale growth geometry + varied base,
    // 2 = C2 = C1 + sub-thermals and subordinate lower masses inside every cell (reference only, not selected),
    // 3 = C3 = C1 + crown development + regional surface character (review 2, not accepted),
    // 4 = C4 = C1 + organic upper-tower growth + regional character where quiet is broad, not smooth (review 3).
    public static int VARIANT = 0;
    static final long S_TOWER = 0x70E426A0L;
    static final long S_CROWN = 0xC4013608L, S_REG = 0x4E613608L, S_UP = 0x0B404608L;
    /** C4: amplitude of the broad (150-block) folds that replace fine relief in quiet regions. */
    public static float QUIET_A = 24;
    /** T260.10: tower-scale warp amplitude of deep cells (fraction of the potential radius). */
    public static float DEEP_WARP = .09f, DEEP_DAUGHTER_P = .5f, DEEP_FLOOR = 240, DEEP_RELIEF = .045f;
    static final long S_MACRO = 0x3AC02608L, S_WARP = 0x3A4B2608L, S_FINE = 0xF14E2608L, S_BASE = 0xBA5E2608L, S_SUB = 0x5B002608L;
    /** C1: macro tower bulges (fraction of the radius), meso warp (amplitude, scale), fine billow-noise amplitude, base. */
    public static float MAC_AMP = .24f, WARP_A = 44, WARP_L = 175, WARP2_A = 17, WARP2_L = 72, FINE_A = 17, BASE_K = 18, BASE_VAR = 12, BASE_CORE = 16;

    static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L ^ Long.rotateLeft(b * 0xC2B2AE3D27D4EB4FL, 31);
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
        return h;
    }

    // ================================================================ persistent cell

    /** Fixed identity and growth potential of one cell: what a server would own and a client would rebuild from. */
    public static final class CellSpec {
        public final String name;
        public final long id, seed;
        /** Position at t = 0 in the system frame and own drift (blocks per time unit). */
        public float x0, z0, vx, vz, birth;
        public float h0 = 110, r0 = 105, hPot = 600, rPot = 230, tauH = 18, tauR = 22, powH = 1.8f;
        public float decay = 1e9f, tauD = 12, vigor;
        /** T260.9: growth direction imposed by the environment (shear), applied after the seeded anatomy; NaN = none. */
        public float envLeanAz = Float.NaN, envLean;
        /** T260.9: dissipation thins the whole cell depth away and a fully dissipated cell is removed (wide cells). */
        public boolean dissolve;
        /**
         * T260.10 deep development, all inactive at their defaults (T260.8 / T260.9 bit parity). deep in [0, 1]: how far the
         * cell is a deep convective tower (milder taper, plume spreading, stronger upper expansion, tower-scale warp);
         * calH / calW: glaciation (calvus) onset and band above the base; hCeil: equilibrium-level ceiling of the top;
         * kScale: pair blend scale of the cell (wide towers merge with proportionally wide blends).
         */
        public float deep, calH = Float.NaN, calW = 220, hCeil = Float.NaN, kScale = 1;
        /** T260.10 entraining plume of the environment: undilute depth and dilution length (depth = dn exp(-l / rUpdraft)). */
        public float plumeDn = Float.NaN, plumeL;
        public boolean daughter;
        public long parentA = -1, parentB = -1;
        // Seeded anatomy, drawn once from the cell's own trunk stream (never re-drawn while the cell lives).
        float leanAz, lean, pw, wAmp, wFreq, wPh, taper, narrow, wander, a2, th2, a3, th3, twist, e, psi;
        float[] sh;
        private List<float[]> slots;

        public CellSpec(String name, long id, long seed) { this.name = name; this.id = id; this.seed = seed; }

        /** C4 upper growth: lobes {cos, sin, kappa, amp}, normaliser, asymmetric expansion, rise, broad/narrow, cap. */
        float[] up;
        int nUp;
        float upNorm = 1, upMean, upE, upA, upB, upCap = 1;

        /**
         * C4: the cell's upper-growth field, from its own stream. 1-3 azimuthal lobes (one dominant) mark where the
         * upper tower rises and spreads; the same field widens the upper trunk, shapes the cap and decides how broad or
         * narrow the crown becomes, so the crown grows out of the tower instead of being a dome placed on it.
         */
        void sealUp() {
            Random g = stream(seed, S_UP, 0, 0);
            float u = g.nextFloat();
            nUp = u < .4f ? 1 : u < .8f ? 2 : 3;
            upE = .14f + .26f * g.nextFloat();
            upA = .3f + .5f * g.nextFloat();
            upB = -.12f + .42f * g.nextFloat();
            upCap = .8f + .4f * g.nextFloat();
            float phi0 = (float) (g.nextFloat() * Math.PI * 2);
            up = new float[nUp * 4];
            for (int j = 0; j < nUp; j++) {
                Random q = stream(seed, S_UP, 1 + j, 0);
                float a = q.nextFloat(), b = q.nextFloat(), cc = q.nextFloat();
                float phi = j == 0 ? phi0 : phi0 + (float) Math.PI * (.45f + 1.1f * a);
                up[j * 4] = (float) Math.cos(phi); up[j * 4 + 1] = (float) Math.sin(phi);
                up[j * 4 + 2] = 1.5f + 3.5f * b;
                up[j * 4 + 3] = j == 0 ? 1 : .4f + .5f * cc;
            }
            float mx = 0, sum = 0;
            upNorm = 1;
            for (int i = 0; i < 64; i++) {
                float t = (float) (i * Math.PI * 2 / 64), v = upLobe((float) Math.cos(t), (float) Math.sin(t));
                mx = Math.max(mx, v); sum += v;
            }
            upNorm = 1 / Math.max(1e-3f, mx);
            upMean = sum / 64 * upNorm;
        }

        /** C4 upper-growth field in direction (c, s), in [0, 1]. */
        float upLobe(float c, float s) {
            if (up == null) return 0;
            float v = 0;
            for (int j = 0; j < nUp; j++) v += up[j * 4 + 3] * (float) Math.exp(up[j * 4 + 2] * (c * up[j * 4] + s * up[j * 4 + 1] - 1));
            return v * upNorm;
        }

        CellSpec seal() {
            Random g = stream(seed, S_TRUNK, 0, 0);
            leanAz = (float) (g.nextFloat() * Math.PI * 2);
            lean = .12f + .33f * g.nextFloat();
            pw = 1.3f + g.nextFloat();
            wAmp = .06f + .10f * g.nextFloat();
            wFreq = .7f + .8f * g.nextFloat();
            wPh = (float) (g.nextFloat() * Math.PI * 2);
            taper = (g.nextFloat() - .4f) * .2f;
            narrow = .32f + .18f * g.nextFloat();
            wander = .10f + .12f * g.nextFloat();
            a2 = .14f * (.6f + .8f * g.nextFloat());
            th2 = (float) (g.nextFloat() * Math.PI);
            a3 = .09f * (.6f + .8f * g.nextFloat());
            th3 = (float) (g.nextFloat() * Math.PI * 2);
            twist = .9f * (2 * g.nextFloat() - 1);
            e = .18f * (.7f + .6f * g.nextFloat());
            psi = leanAz + (g.nextFloat() - .5f) * 1.2f;
            int ns = Math.min(5, 1 + Math.round(hPot / 280f));
            sh = new float[ns * 3];
            for (int s = 0; s < ns; s++) {
                sh[s * 3] = (.10f + .75f * (s + g.nextFloat()) / ns) * hPot;
                sh[s * 3 + 1] = (float) (g.nextFloat() * Math.PI * 2);
                sh[s * 3 + 2] = .7f + .6f * g.nextFloat();
            }
            if (!Float.isNaN(envLeanAz)) {
                // The seeded anatomy stays; only the overall growth direction follows the environment (+ own jitter).
                float jitter = psi - leanAz;
                leanAz = envLeanAz; lean = envLean; psi = leanAz + .5f * jitter;
            }
            if (deep > 0) {
                // T260.10: a deep tower keeps a milder taper (no pillar narrowing over many km) and spreads with height
                // (entraining-plume widening); the seeded anatomy stays.
                narrow *= 1 - .6f * deep;
                taper -= .14f * deep;
            }
            slots = null;
            up = null;
            if (VARIANT == 4) sealUp();
            if (deep > 0 && up != null) {
                // T260.10: substantial upper expansion as the deep tower approaches its equilibrium level.
                upE *= 1 + .2f * deep;
                upB += .08f * deep;
            }
            return this;
        }

        /** Dimensionless width profile at absolute height h above the base (growth-invariant: refers to hPot). */
        float shapeW(float h) {
            float u = Math.max(0, h) / hPot;
            float w = 1 + wAmp * (float) Math.sin(2 * Math.PI * wFreq * u + wPh) - taper * (u - .5f);
            // Base-wide, crown-narrow (T259): a broader lower mass, then the narrowing tower.
            return w * (1 + BASE_WIDE * (1 - smooth(0, .7f, u))) * (1 - narrow * smooth(.05f, 1f, u));
        }

        float axisOffX(float h) {
            float u = Math.max(0, h) / hPot;
            float x = (float) Math.cos(leanAz) * lean * rPot * (float) Math.pow(u, pw) + wander * rPot * noise(h / (1.3f * rPot), .37f, .71f, seed ^ S_TRUNK);
            // C1: the growth direction keeps changing (a second, shorter wander), not one smooth lean.
            if (VARIANT >= 1) x += .09f * rPot * smooth(0, .3f * hPot, h) * noise(h / (.5f * rPot), 2.37f, 1.71f, seed ^ S_MACRO);
            return x;
        }

        float axisOffZ(float h) {
            float u = Math.max(0, h) / hPot;
            float z = (float) Math.sin(leanAz) * lean * rPot * (float) Math.pow(u, pw) + wander * rPot * noise(h / (1.3f * rPot), .83f, .19f, (seed ^ S_TRUNK) * 31 + 5);
            if (VARIANT >= 1) z += .09f * rPot * smooth(0, .3f * hPot, h) * noise(h / (.5f * rPot), 4.83f, 3.19f, (seed ^ S_MACRO) * 31 + 5);
            return z;
        }

        /**
         * C1 macro tower irregularity: radius factor offset in direction (c, s) at absolute height h. A coherent field on
         * (azimuth circle x absolute height / rPot), so bulges, indents and asymmetric upper expansion stay fixed in the
         * cell frame while the tower rises through them (growth-invariant). Stronger in the developing upper tower.
         */
        float macro(float c, float s, float h) {
            if (VARIANT < 1) return 0;
            float U = Math.max(0, h) / hPot, hq = h / (.62f * rPot), hq2 = h / (.3f * rPot);
            float amp = MAC_AMP * (.5f + smooth(.25f, .9f, U));
            long ms = seed ^ S_MACRO;
            float n = noise(c * 1.1f + 3.1f, hq, s * 1.1f - 1.7f, ms) + .45f * noise(c * 2.3f + .4f, hq2, s * 2.3f + 5.2f, ms * 7 + 1);
            return amp * n;
        }

        /**
         * C3 regional surface character in [0, 1]: 1 = strong, crisp cauliflower (distinct lobes, deep folds), 0 = broad,
         * partially merged, quiet. A coherent field in the cell frame at fixed scales of the potential radius (growth
         * invariant), shifted by the cell's developmental state: growing cells are more active, dissipating ones quieter.
         */
        float regime(float lx, float ly, float lz, float rate, float dd) {
            float L = .85f * rPot, L2 = .4f * rPot;
            long rs = seed ^ S_REG;
            float n = noise(lx / L, ly / L, lz / L, rs) + .4f * noise(lx / L2, ly / L2, lz / L2, rs * 7 + 3);
            return Math.max(0, Math.min(1, .5f + .8f * n + .2f * (Math.min(1, rate) - .35f) - .6f * dd));
        }

        /**
         * Reference billow slots, fixed for the cell's life: {h, phi, sizeFactor, refRadius, candidate, refWidth, meso,
         * activityRef}. Candidate c always draws from its own stream; acceptance is a Poisson test in a reference
         * cylinder (the cell's potential shape), in candidate order, so it never depends on the current growth state.
         */
        List<float[]> slots() {
            if (slots != null) return slots;
            List<float[]> acc = new ArrayList<>();
            float hMax = hPot * (1 + BOOST_H) + 60, rRef = rPot * (1 + .5f * BOOST_R);
            int fails = 0;
            for (int cand = 0; cand < 30000 && fails < 400; cand++) {
                Random q = stream(seed, S_BSLOT, cand, 0);
                float h = -10 + (hMax + 10) * q.nextFloat(), phi = (float) (q.nextFloat() * Math.PI * 2);
                float w = rRef * shapeW(h);
                float area = q.nextFloat(), sf = .6f + .8f * q.nextFloat();
                boolean meso = q.nextFloat() < .22f;
                if (area > w / (rRef * 1.3f)) continue; // lateral area is proportional to the radius
                float rr = BILLOW_R * w * sf;
                boolean ok = true;
                for (float[] a : acc) {
                    float dphi = Math.abs(a[1] - phi);
                    if (dphi > Math.PI) dphi = (float) (2 * Math.PI) - dphi;
                    float chord = (a[5] + w) * (float) Math.sin(dphi * .5f), dh = a[0] - h, need = SPACING * .5f * (a[3] + rr);
                    if (chord * chord + dh * dh < need * need) { ok = false; break; }
                }
                if (!ok) { fails++; continue; }
                fails = 0;
                float act = ConvectiveVolumeT2607.activity(w * (float) Math.cos(phi), h, w * (float) Math.sin(phi), seed ^ S_ACT, 0, .5f);
                acc.add(new float[]{h, phi, sf, rr, cand, w, meso ? 1 : 0, act});
            }
            slots = acc;
            return acc;
        }
    }

    // ================================================================ scenario: cells + deterministic history

    /** Attachment of two cells: alpha sampled on the scenario grid. */
    static final class Pair {
        final int i, j;
        final long pid;
        final float[] alpha;
        final boolean daughterLink;
        Pair(int i, int j, long pid, int n, boolean daughterLink) { this.i = i; this.j = j; this.pid = pid; alpha = new float[n]; this.daughterLink = daughterLink; }
    }

    public static final class Scenario {
        public final String name;
        public final long seed;
        public final List<CellSpec> cells = new ArrayList<>();
        public float tEnd = 40;
        /** T260.9: junction daughters live as long as their longer-lived parent (off for the T260.8 scenarios). */
        public boolean daughterDecay;
        float tStart;
        int nT;
        final List<Pair> pairs = new ArrayList<>();
        private boolean prepared;

        public Scenario(String name, long seed) { this.name = name; this.seed = seed; }

        public CellSpec add(String name) {
            long id = mix(seed, cells.size() + 1);
            CellSpec c = new CellSpec(name, id, mix(id, 0x5EEDL));
            cells.add(c);
            return c;
        }

        /** Seals every cell, integrates pair attachment over [tStart, tEnd] and derives daughter cells. */
        public Scenario prepare() {
            if (prepared) return this;
            prepared = true;
            for (CellSpec c : cells) c.seal();
            tStart = -1;
            for (CellSpec c : cells) tStart = Math.min(tStart, c.birth - 1);
            nT = (int) Math.ceil((tEnd + 2 - tStart) / DT) + 2;
            int n0 = cells.size();
            for (int i = 0; i < n0; i++) for (int j = i + 1; j < n0; j++) {
                Pair p = new Pair(i, j, mix(cells.get(i).id, cells.get(j).id), nT, false);
                float a = 0;
                for (int k = 0; k < nT; k++) {
                    float t = tStart + k * DT;
                    CellSpec ci = cells.get(i), cj = cells.get(j);
                    if (t >= ci.birth && t >= cj.birth) {
                        float dx = cx(cj, t) - cx(ci, t), dz = cz(cj, t) - cz(ci, t);
                        float gap = (float) Math.hypot(dx, dz) - INFL * (baseR(ci, t) + baseR(cj, t));
                        a += DT * (gap < 0 ? 1 / T_ATTACH : -1 / T_DETACH);
                        a = Math.max(0, Math.min(1, a));
                    }
                    p.alpha[k] = a;
                }
                pairs.add(p);
            }
            if (VARIANT == 2) for (int i = 0; i < n0; i++) members(i);
            for (int i = 0; i < n0; i++) if (cells.get(i).deep > .5f && !cells.get(i).daughter) towers(i);
            // Daughters: new growth seeded at the junction of a strongly attached pair (own identity from the pair).
            int np = pairs.size();
            for (int pi = 0; pi < np; pi++) {
                Pair p = pairs.get(pi);
                if (p.daughterLink) continue;
                int k = 0;
                while (k < nT && p.alpha[k] < DAUGHTER_AT) k++;
                if (k >= nT) continue;
                float tc = tStart + k * DT;
                if (tc > tEnd) continue;
                CellSpec a = cells.get(p.i), b = cells.get(p.j);
                if (dissipation(a, tc) > .2f || dissipation(b, tc) > .2f) continue;
                Random q = stream(p.pid, S_DAUGHTER, 0, 0);
                if (q.nextFloat() > DAUGHTER_P) continue;
                // T260.10: wide deep towers attach far more often; only some junctions start new growth.
                if ((a.deep > 0 || b.deep > 0) && q.nextFloat() > DEEP_DAUGHTER_P) continue;
                long id = mix(p.pid, 0xDA06L);
                CellSpec d = new CellSpec(a.name + "+" + b.name, id, mix(id, 0x5EEDL));
                d.daughter = true; d.parentA = a.id; d.parentB = b.id;
                float ra = baseR(a, tc), rb = baseR(b, tc);
                float ax = cx(a, tc), az = cz(a, tc), bx = cx(b, tc), bz = cz(b, tc);
                float L = (float) Math.hypot(bx - ax, bz - az), ux = (bx - ax) / L, uz = (bz - az) / L;
                float f = ra / (ra + rb) + .12f * (q.nextFloat() - .5f), side = (q.nextFloat() - .5f) * .5f * Math.min(ra, rb);
                d.vx = .5f * (a.vx + b.vx); d.vz = .5f * (a.vz + b.vz);
                float jx = ax + ux * L * f - uz * side, jz = az + uz * L * f + ux * side;
                d.x0 = jx - d.vx * tc; d.z0 = jz - d.vz * tc;
                d.birth = tc;
                d.h0 = 80; d.r0 = 80;
                float hi = Math.max(a.hPot, b.hPot);
                d.hPot = (.42f + .3f * q.nextFloat()) * hi;
                d.rPot = (.55f + .25f * q.nextFloat()) * Math.min(a.rPot, b.rPot);
                d.tauH = 9 + 6 * q.nextFloat(); d.tauR = 12;
                d.vigor = .05f + .1f * q.nextFloat();
                if (daughterDecay && a.decay < 1e8f && b.decay < 1e8f) { d.decay = Math.max(a.decay, b.decay) + 4; d.tauD = 12; }
                d.dissolve = a.dissolve || b.dissolve;
                if (a.deep > 0 || b.deep > 0) {
                    // T260.10: a junction tower of deep parents rises in the same environment (its own, lower potential).
                    CellSpec hp = a.hPot >= b.hPot ? a : b;
                    // Its depth is limited by its own (narrower) updraft in the same environment: no needles.
                    if (!Float.isNaN(hp.plumeDn)) d.hPot = Math.min(d.hPot, Math.max(220, hp.plumeDn * (float) Math.exp(-hp.plumeL / (d.rPot / 1.45f))));
                    d.deep = smooth(1200, 6000, d.hPot);
                    d.plumeDn = hp.plumeDn; d.plumeL = hp.plumeL;
                    d.calH = hp.calH; d.calW = hp.calW; d.hCeil = hp.hCeil;
                    d.tauH = Math.max(d.tauH, hp.tauH * (.6f + .3f * q.nextFloat()));
                    d.tauR = Math.max(d.tauR, .8f * d.tauH + 6);
                    d.kScale = Math.min(a.kScale, b.kScale);
                    d.r0 = Math.max(d.r0, .3f * d.rPot);
                }
                d.seal();
                cells.add(d);
                int di = cells.size() - 1;
                for (int parent : new int[]{p.i, p.j}) {
                    Pair link = new Pair(parent, di, mix(cells.get(parent).id, id), nT, true);
                    for (int kk = 0; kk < nT; kk++) link.alpha[kk] = smooth(0, 5, tStart + kk * DT - tc);
                    pairs.add(link);
                }
            }
            return this;
        }

        /**
         * C2: the internal anatomy of one cell as persistent members of its own (identity derived from the cell, fixed
         * offset in the cell frame, drifting with it, attached from birth): sub-thermals (secondary updrafts of
         * different strength, timing, width and lean: irregular tower profiles, unequal turrets, broad upper masses) and
         * subordinate lower masses (small cumuli on the flanks near the base). Each member is a full persistent cell
         * with its own trunk, billow slots and history; the parent blend (daughter-link profile: strong low, weak high)
         * keeps one connected cloud with distinct upper growth.
         */
        /**
         * T260.10: competing growth regions of a deep cell. Successive thermals rise through and beside the main
         * updraft as persistent sub-towers (identity derived from the cell, fixed offset in the cell frame, own width,
         * potential, timing and outward lean), so a deep tower is a cluster of unequal rising columns and turrets instead
         * of one textured cylinder. Each is linked to its parent from birth (daughter-link blend profile).
         */
        private void towers(int pi) {
            CellSpec p = cells.get(pi);
            Random g = stream(p.seed, S_TOWER, 0, 0);
            int n = 1 + (int) (g.nextFloat() * 2.99f * p.deep);
            float phi0 = (float) (g.nextFloat() * Math.PI * 2);
            for (int k = 0; k < n; k++) {
                Random q = stream(p.seed, S_TOWER, 1 + k, 0);
                float u = q.nextFloat(), v = q.nextFloat(), w = q.nextFloat(), b = q.nextFloat(), tq = q.nextFloat(), la = q.nextFloat(), lj = q.nextFloat(), ps = q.nextFloat();
                long id = mix(p.id, 0x70E4L + k);
                CellSpec c = new CellSpec(p.name + ".t" + (k + 1), id, mix(id, 0x5EEDL));
                c.daughter = true; c.parentA = p.id;
                float phi = phi0 + k * 2.4f + (u - .5f) * 1.2f, dist = (.35f + .3f * w) * p.rPot;
                c.rPot = p.rPot * (.4f + .25f * b);
                // Mostly lower than the main updraft; about one in four overtops it (a newer, stronger thermal).
                c.hPot = p.hPot * (.45f + .62f * v * v);
                if (!Float.isNaN(p.hCeil)) c.hPot = Math.min(c.hPot, p.hCeil);
                c.birth = p.birth + 2 + .9f * p.tauH * tq;
                c.tauH = p.tauH * (.8f + .5f * u); c.tauR = .8f * c.tauH + 6;
                c.h0 = 90; c.r0 = .3f * c.rPot;
                c.vigor = p.vigor + .1f * (w - .4f);
                c.decay = p.decay + 4 * tq; c.tauD = p.tauD;
                c.dissolve = p.dissolve;
                c.deep = smooth(1200, 6000, c.hPot);
                c.calH = p.calH; c.calW = p.calW; c.hCeil = p.hCeil; c.plumeDn = p.plumeDn; c.plumeL = p.plumeL;
                c.vx = p.vx; c.vz = p.vz;
                c.x0 = p.x0 + dist * (float) Math.cos(phi); c.z0 = p.z0 + dist * (float) Math.sin(phi);
                // Diverging growth: leans outward, partly following the environment's shear.
                float ex = (float) Math.cos(phi) * (.3f + .4f * la), ez = (float) Math.sin(phi) * (.3f + .4f * la);
                if (!Float.isNaN(p.envLeanAz)) { ex += (float) Math.cos(p.envLeanAz) * p.envLean * p.rPot / c.rPot * .8f; ez += (float) Math.sin(p.envLeanAz) * p.envLean * p.rPot / c.rPot * .8f; }
                c.envLeanAz = (float) Math.atan2(ez, ex) + (lj - .5f) * .3f;
                c.envLean = Math.min(2.6f, (float) Math.hypot(ex, ez));
                c.seal();
                c.psi = c.leanAz + (ps - .5f) * .8f;
                cells.add(c);
                int ci = cells.size() - 1;
                Pair link = new Pair(pi, ci, mix(p.id, id), nT, true);
                for (int kk = 0; kk < nT; kk++) link.alpha[kk] = smooth(-1, 3, tStart + kk * DT - c.birth);
                pairs.add(link);
            }
        }

        private void members(int pi) {
            CellSpec p = cells.get(pi);
            Random g = stream(p.seed, S_SUB, 0, 0);
            int nSub = p.hPot < 380 ? (g.nextFloat() < .5f ? 1 : 0) : 1 + (int) (g.nextFloat() * (p.rPot > 380 ? 2.99f : 1.99f));
            int nSat = 1 + (int) (g.nextFloat() * 2.99f);
            float phi0 = (float) (g.nextFloat() * Math.PI * 2);
            for (int k = 0; k < nSub + nSat; k++) {
                boolean sat = k >= nSub;
                Random q = stream(p.seed, S_SUB, 1 + k, 0);
                long id = mix(p.id, 0x5B00L + k);
                CellSpec c = new CellSpec(p.name + (sat ? ".l" : ".t") + (k + 1), id, mix(id, 0x5EEDL));
                c.daughter = true; c.parentA = p.id;
                float phi = phi0 + (float) (k * 2.4 + (q.nextFloat() - .5f) * 1.4f), dist, u = q.nextFloat(), v = q.nextFloat(), w = q.nextFloat();
                if (!sat) {
                    dist = (.45f + .35f * u) * p.rPot;
                    c.hPot = p.hPot * (.5f + .68f * v * v); // one in a few overtops the main updraft
                    c.rPot = Math.max(150, p.rPot * (.36f + .26f * w)); // no needles
                    c.birth = p.birth + 1 + 8 * q.nextFloat();
                    c.tauH = p.tauH * (.7f + .6f * q.nextFloat()); c.tauR = 12 + 6 * q.nextFloat();
                    c.h0 = 70; c.r0 = 60;
                    c.vigor = p.vigor + .14f * (q.nextFloat() - .45f);
                } else {
                    dist = (1.05f + .4f * u) * p.rPot;
                    c.hPot = 110 + 200 * v * (.5f + .5f * Math.min(1, p.rPot / 400));
                    c.rPot = 75 + 85 * w;
                    c.birth = p.birth - 3 + 12 * q.nextFloat();
                    c.tauH = 7 + 5 * q.nextFloat(); c.tauR = 10;
                    c.h0 = 45; c.r0 = 45;
                    c.vigor = -.05f + .1f * q.nextFloat();
                    if (q.nextFloat() < .35f) { c.decay = c.birth + 14 + 14 * q.nextFloat(); c.tauD = 10; }
                }
                c.decay = Math.min(c.decay, p.decay + 2 * q.nextFloat()); c.tauD = Math.min(c.tauD, p.tauD);
                c.vx = p.vx; c.vz = p.vz;
                c.x0 = p.x0 + dist * (float) Math.cos(phi); c.z0 = p.z0 + dist * (float) Math.sin(phi);
                c.seal();
                if (!sat) {
                    // A secondary updraft grows away from the main one: its upper mass leans outward (diverging growth).
                    c.leanAz = phi + (q.nextFloat() - .5f) * 1.1f;
                    c.lean = .3f + .35f * q.nextFloat();
                    c.psi = c.leanAz + (q.nextFloat() - .5f) * .8f;
                }
                cells.add(c);
                int ci = cells.size() - 1;
                Pair link = new Pair(pi, ci, mix(p.id, id), nT, true);
                for (int kk = 0; kk < nT; kk++) link.alpha[kk] = smooth(-1, 3, tStart + kk * DT - c.birth);
                pairs.add(link);
            }
        }

        float alpha(Pair p, float t) {
            float g = (t - tStart) / DT;
            int k = Math.max(0, Math.min(nT - 2, (int) Math.floor(g)));
            float f = Math.max(0, Math.min(1, g - k));
            float a = p.alpha[k] + (p.alpha[k + 1] - p.alpha[k]) * f;
            return smooth(0, 1, a);
        }

        /** Index of the cell with this identity, -1 if none. */
        int index(long id) { for (int i = 0; i < cells.size(); i++) if (cells.get(i).id == id) return i; return -1; }

        /** Growth state of cell i at t: {top, R, dissipation, emergence, cx, cz, rate, calvus}; null before birth. */
        float[] state(int i, float t) {
            CellSpec c = cells.get(i);
            float a = t - c.birth;
            if (a < 0) return null;
            float sumH = 0, sumR = 0;
            for (Pair p : pairs) {
                if (p.daughterLink || (p.i != i && p.j != i)) continue;
                int o = p.i == i ? p.j : p.i;
                CellSpec oc = cells.get(o);
                float al = alpha(p, t) * (1 - dissipation(oc, t));
                if (c.hPot >= oc.hPot) sumH += al; else sumR += al;
            }
            float hEff = c.hPot * (1 + BOOST_H * Math.min(1, sumH)), rEff = c.rPot * (1 + BOOST_R * Math.min(1, sumR));
            if (!Float.isNaN(c.hCeil)) hEff = Math.min(hEff, c.hCeil); // T260.10: no growth past the equilibrium level
            float top = top(c, a, hEff), dd = dissipation(c, t);
            top *= 1 - .1f * dd;
            float R = (c.r0 + (rEff - c.r0) * (1 - (float) Math.exp(-a / c.tauR))) * (1 + .08f * dd);
            float rate = (top(c, a + .5f, hEff) - top(c, Math.max(0, a - .5f), hEff)) / (a >= .5f ? 1 : .5f + a) / (c.hPot / c.tauH);
            float cal = Float.isNaN(c.calH) ? smooth(HCAL, HCAL + 220, top) : smooth(c.calH, c.calH + c.calW, top);
            return new float[]{top, R, dd, smooth(0, 4, a), cx(c, t), cz(c, t), rate * (1 - dd), cal};
        }

        static float top(CellSpec c, float a, float hEff) {
            float s = a / c.tauH;
            float g = 1 - (float) Math.exp(-Math.pow(s, c.powH));
            return c.h0 + (hEff - c.h0) * g;
        }
        static float baseR(CellSpec c, float t) { float a = Math.max(0, t - c.birth); return c.r0 + (c.rPot - c.r0) * (1 - (float) Math.exp(-a / c.tauR)); }
        static float cx(CellSpec c, float t) { return c.x0 + c.vx * t; }
        static float cz(CellSpec c, float t) { return c.z0 + c.vz * t; }
        static float dissipation(CellSpec c, float t) { return smooth(c.decay, c.decay + c.tauD, t); }

        /** Running maximum of the top on a grid from the cell's birth to t (exactly t as the last sample). */
        float[] topHistory(int i, float t) {
            CellSpec c = cells.get(i);
            int n = Math.max(2, (int) Math.ceil((t - c.birth) / DT) + 1);
            float[] h = new float[n];
            float m = 0;
            for (int k = 0; k < n; k++) {
                float tk = k == n - 1 ? t : c.birth + k * DT;
                float[] s = state(i, Math.min(tk, t));
                m = Math.max(m, s == null ? 0 : s[0]);
                h[k] = m;
            }
            return h;
        }

        public SystemState at(float t) { prepare(); return new SystemState(this, t); }
    }

    // ================================================================ trunk (growth column in absolute cell heights)

    static final class Trunk {
        static final float TAB = 2f;
        final CellSpec c;
        final float cx, cz, yb, top, yc, hcap, R, domeS, apex, relief, cpsi, spsi, cal;
        final long rseed;
        final float kappa = 8f;
        final float[] tabS, shc, shs, box;
        final int st, ns;
        /** C3 crown: per dome {ox, oz, rh, cos, sin, stretch, height, blend}; null outside C3. */
        float[] dome;
        int nd;
        static final float COLLAR = .6f;

        Trunk(CellSpec c, float cx, float cz, float top, float R, float cal, float relief) {
            this.c = c; this.cx = cx; this.cz = cz; this.top = BASE_Y + top; this.R = R; this.relief = relief; this.cal = cal;
            rseed = stream(c.seed, S_RELIEF, 0, 0).nextLong();
            yb = BASE_Y - 25;
            float hc = .85f * width(this.top - .45f * R);
            // C3: crown proportions differ between seeds (taller or squatter upper growth).
            if (VARIANT == 3) hc *= .95f + .45f * stream(c.seed, S_CROWN, 0, 0).nextFloat();
            if (VARIANT == 4) hc *= c.upCap;
            hcap = Math.max(25, Math.min(hc * (1 - .22f * cal), .7f * (this.top - yb)));
            yc = this.top - hcap;
            domeS = 1; // a dome wider than the column at yc steps out as a shelf (T260.8 first try): calvus broadening is in width()
            apex = APEX * width(yc);
            if (VARIANT == 3) crown();
            cpsi = (float) Math.cos(c.psi); spsi = (float) Math.sin(c.psi);
            ns = c.sh.length / 3;
            st = 9 + ns;
            shc = new float[ns]; shs = new float[ns];
            for (int k = 0; k < ns; k++) { shc[k] = (float) Math.cos(c.sh[k * 3 + 1]); shs[k] = (float) Math.sin(c.sh[k * 3 + 1]); }
            int nt = (int) Math.ceil((yc - yb) / TAB) + 2;
            float[] t = new float[nt * st];
            float x0 = 1e9f, x1 = -1e9f, z0 = 1e9f, z1 = -1e9f, bmax = 0;
            for (int i = 0; i < nt; i++) {
                float y = Math.min(yb + i * TAB, yc), h = y - BASE_Y, U = Math.max(0, h) / c.hPot, u = (y - yb) / (this.top - yb);
                int o = i * st;
                t[o] = cx + c.axisOffX(h); t[o + 1] = cz + c.axisOffZ(h);
                float r = width(y);
                t[o + 2] = r;
                float al = c.th2 + c.twist * U, be = c.th3 - .7f * c.twist * U;
                t[o + 3] = (float) Math.cos(2 * al); t[o + 4] = (float) Math.sin(2 * al);
                t[o + 5] = (float) Math.cos(3 * be); t[o + 6] = (float) Math.sin(3 * be);
                t[o + 7] = c.e * smooth(.45f, .92f, u);
                float bound = r * (1 + c.a2 + c.a3 + t[o + 7] + (VARIANT >= 1 ? 1.6f * MAC_AMP * 1.6f : 0) + (VARIANT == 4 ? c.upE : 0));
                for (int k = 0; k < ns; k++) {
                    float hs = c.sh[k * 3], wr = width(BASE_Y + hs);
                    float amp = SHOULDER * c.sh[k * 3 + 2] * wr * smooth(hs, hs + .8f * R, yc - BASE_Y);
                    float dy = h - hs, sig = dy > 0 ? .55f * wr : .35f * wr;
                    t[o + 9 + k] = amp * (float) Math.exp(-(dy / sig) * (dy / sig));
                    bound += t[o + 9 + k];
                }
                t[o + 8] = bound;
                bmax = Math.max(bmax, bound);
                x0 = Math.min(x0, t[o]); x1 = Math.max(x1, t[o]); z0 = Math.min(z0, t[o + 1]); z1 = Math.max(z1, t[o + 1]);
            }
            tabS = t;
            float m = (bmax + apex) * domeS * (VARIANT == 3 ? 1.45f : 1) + 1.3f * relief + 4;
            float upTop = VARIANT == 3 ? .12f * hcap : VARIANT == 4 ? c.upA * hcap : 0;
            box = new float[]{x0 - m, yb, z0 - m, x1 + m, this.top + 1.3f * relief + 4 + upTop, z1 + m};
        }

        /**
         * C3 crown development: the upper tower is a low collar (the column's own cross-section, so no shelf at yc) out
         * of which one lead dome and 0-3 competing upper growth regions rise, each from its own stream of the cell
         * seed: offset, width, horizontal stretch (asymmetric expansion), height and blend with the rest (uneven
         * merging). About a quarter of the cells get a broad, dominant, offset upper mass instead of a tall lead dome.
         * Secondary domes rise with the cell's maturity (persistent: fixed parameters, continuous growth); a few overtake
         * the lead. Every value is drawn unconditionally (fixed draw order per dome).
         */
        private void crown() {
            Random g = stream(c.seed, S_CROWN, 1, 0);
            float R0 = width(yc), prog = smooth(.15f, .9f, (top - BASE_Y - c.h0) / Math.max(1, c.hPot - c.h0));
            boolean broad = g.nextFloat() < .28f;
            float u = g.nextFloat(), phi0 = (float) (g.nextFloat() * Math.PI * 2);
            nd = 1 + (u < .3f ? 0 : u < .65f ? 1 : u < .9f ? 2 : 3);
            dome = new float[nd * 8];
            for (int j = 0; j < nd; j++) {
                Random q = stream(c.seed, S_CROWN, 2 + j, 0);
                float a = q.nextFloat(), b = q.nextFloat(), cc = q.nextFloat(), d = q.nextFloat(), e = q.nextFloat(), f = q.nextFloat(), gg = q.nextFloat(), h = q.nextFloat();
                float o, rf, st, hf, phi;
                if (j == 0) {
                    phi = phi0;
                    o = broad ? .2f + .15f * a : .08f + .22f * a;
                    rf = broad ? 1.02f : 1f - .45f * o;
                    st = broad ? 1.2f + .25f * b : 1 + .3f * b;
                    hf = broad ? .8f + .1f * cc : 1;
                } else {
                    phi = phi0 + (float) Math.PI * (.55f + .9f * a);
                    o = .38f + .22f * b;
                    rf = Math.max(.3f, Math.min(.62f, 1.08f - o) * (.6f + .4f * cc));
                    hf = e < .15f ? .95f + .13f * d : .45f + .45f * d;
                    hf *= .45f + .55f * prog;
                    st = 1 + .35f * f;
                }
                // Crown anatomy develops with the tower: a cell that is not (yet) towering keeps one centred dome.
                float dev = smooth(380, 700, top - BASE_Y);
                o *= dev; st = 1 + (st - 1) * dev;
                if (j > 0) hf *= dev; else hf = 1 + (hf - 1) * dev;
                float psi = phi + (gg - .5f) * 1.5f;
                // Never wider than the column (an overhanging flat dome reads as a lid / shelf) nor flatter than a lens.
                if (j == 0) rf = Math.min(rf, 1.05f / st);
                rf = Math.min(rf, 1.3f * hf * hcap / (st * R0));
                int k = j * 8;
                dome[k] = R0 * o * (float) Math.cos(phi); dome[k + 1] = R0 * o * (float) Math.sin(phi);
                dome[k + 2] = rf * R0; dome[k + 3] = (float) Math.cos(psi); dome[k + 4] = (float) Math.sin(psi);
                dome[k + 5] = st; dome[k + 6] = hf * hcap;
                // Lead: a wide blend into the collar (no ledge); others: seeded uneven merging.
                dome[k + 7] = (j == 0 ? .45f + .15f * h : .2f + .3f * h) * rf * R0;
            }
        }

        /**
         * C4 asymmetric upper expansion (radius fraction) toward (c, s) at absolute y: the upper-growth lobes spread the
         * upper tower progressively with height, continuing into the cap (same field, no seam at yc).
         */
        float upper(float c, float s, float y) {
            if (this.c.up == null) return 0;
            float u = (y - yb) / Math.max(1, top - yb);
            return this.c.upE * (this.c.upLobe(c, s) - .5f * this.c.upMean) * smooth(.45f, 1f, u);
        }

        /** Upper-tower distance (y above yc): collar + crown domes in C3, the single dome otherwise. No relief. */
        float crownD(float x, float y, float z) {
            float q = Math.max(0, Math.min(1, (y - yc) / hcap));
            float[] w = new float[st];
            row(yc, w);
            float ax = w[0] + cpsi * apex * q * q, az = w[1] + spsi * apex * q * q;
            float dx = x - ax, dz = z - az;
            float rho = (float) Math.sqrt(dx * dx + dz * dz);
            float rp = (rho - side(w[0] + dx, yc, w[1] + dz)) * domeS;
            if (c.up != null) {
                // C4: the cap is the tower's own cross-section closing over; where the upper-growth field is strong the
                // cap rises further from mid-radius outward (competing rising regions, uneven shoulders), so the summit
                // and its shoulders follow the same field that widened the tower below. Continuous at the axis.
                float t = Math.min(1, rho / Math.max(1, rp)), cc = rho > 1e-3f ? dx / rho : 1, ss = rho > 1e-3f ? dz / rho : 0;
                float lift = c.upA * (c.upLobe(cc, ss) - c.upMean) * smooth(0, .65f, t);
                float ry = hcap * Math.max(.4f, 1 + lift);
                return ConvectiveSystemT260.ellipsoid(dx, y - yc, dz, rp, ry, rp);
            }
            if (dome == null) return ConvectiveSystemT260.ellipsoid(dx, y - yc, dz, rp, hcap, rp);
            float d = ConvectiveSystemT260.ellipsoid(dx, y - yc, dz, rp, COLLAR * hcap, rp);
            for (int j = 0; j < nd; j++) {
                int k = j * 8;
                if (dome[k + 6] < 8 || dome[k + 2] < 8) continue; // undeveloped dome (zero size would give NaN)
                float ex = dx - dome[k], ez = dz - dome[k + 1];
                float a = ex * dome[k + 3] + ez * dome[k + 4], b = -ex * dome[k + 4] + ez * dome[k + 3];
                float dj = ConvectiveSystemT260.ellipsoid(a, y - yc, b, dome[k + 2] * dome[k + 5], dome[k + 6], dome[k + 2] / dome[k + 5]);
                d = ConvectiveSystemT260.smin(d, dj, dome[k + 7]);
            }
            return d;
        }

        /** Mean cross-section radius at absolute y (no harmonics). */
        float width(float y) {
            float h = y - BASE_Y;
            float r = R * c.shapeW(h);
            // Calvus: the glaciating upper tower broadens smoothly toward the crown (no step at the dome).
            r *= 1 + .2f * cal * smooth(.55f, 1f, (y - yb) / Math.max(1, top - yb));
            // C4: the seed decides whether the upper tower broadens into a wide crown or stays narrow (continuous with height).
            if (VARIANT == 4) r *= 1 + c.upB * smooth(.5f, 1f, (y - yb) / Math.max(1, top - yb));
            return r * (1 - .12f * (1 - smooth(-25, .35f * R, h))); // convex flank: narrower at the base
        }

        void row(float y, float[] w) {
            float g = (Math.min(Math.max(y, yb), yc) - yb) / TAB;
            int i = Math.max(0, Math.min((int) g, tabS.length / st - 2));
            float f = Math.max(0, Math.min(1, g - i));
            int a = i * st, b = a + st;
            for (int k = 0; k < st; k++) w[k] = tabS[a + k] + (tabS[b + k] - tabS[a + k]) * f;
        }

        float axisX(float y) { float[] w = new float[st]; row(y, w); float x = w[0]; if (y > yc) { float q = Math.min(1, (y - yc) / hcap); x += cpsi * apex * q * q; } return x; }
        float axisZ(float y) { float[] w = new float[st]; row(y, w); float z = w[1]; if (y > yc) { float q = Math.min(1, (y - yc) / hcap); z += spsi * apex * q * q; } return z; }

        /** Cross-section radius at height y (clamped to yc) toward the horizontal unit direction (c, s), off-axis. */
        float radius(float y, float c, float s) {
            float[] w = new float[st];
            row(y, w);
            return radius(w, c, s, 1e9f, Math.min(y, yc) - BASE_Y);
        }

        float radius(float[] w, float c, float s, float rho, float h) {
            float r = w[2];
            float damp = smooth(.15f * r, .6f * r, rho);
            float c2 = c * c - s * s, s2 = 2 * c * s, c3 = c * (4 * c * c - 3), s3 = s * (3 - 4 * s * s);
            float cp = Math.max(0, c * cpsi + s * spsi);
            float ang = this.c.a2 * (c2 * w[3] + s2 * w[4]) + this.c.a3 * (c3 * w[5] + s3 * w[6]) + w[7] * cp * (float) Math.sqrt(cp) + this.c.macro(c, s, h)
                    + upper(c, s, h + BASE_Y);
            float bump = 0;
            for (int k = 0; k < ns; k++) {
                float sv = w[9 + k];
                if (sv < 1e-3f) continue;
                bump += sv * (float) Math.exp(kappa * (c * shc[k] + s * shs[k] - 1));
            }
            return r * (1 + damp * ang) + damp * bump;
        }

        private float side(float x, float y, float z) {
            float g = (Math.min(Math.max(y, yb), yc) - yb) / TAB;
            int i = Math.max(0, Math.min((int) g, tabS.length / st - 2));
            float f = Math.max(0, Math.min(1, g - i));
            int a = i * st, b = a + st;
            float[] t = tabS;
            float dx = x - (t[a] + (t[b] - t[a]) * f), dz = z - (t[a + 1] + (t[b + 1] - t[a + 1]) * f);
            float rho = (float) Math.sqrt(dx * dx + dz * dz);
            float cc = rho > 1e-4f ? dx / rho : 1, s = rho > 1e-4f ? dz / rho : 0;
            float r = t[a + 2] + (t[b + 2] - t[a + 2]) * f;
            float damp = smooth(.15f * r, .6f * r, rho);
            float c2 = cc * cc - s * s, s2 = 2 * cc * s, c3 = cc * (4 * cc * cc - 3), s3 = s * (3 - 4 * s * s);
            float cp = Math.max(0, cc * cpsi + s * spsi);
            float ang = c.a2 * (c2 * (t[a + 3] + (t[b + 3] - t[a + 3]) * f) + s2 * (t[a + 4] + (t[b + 4] - t[a + 4]) * f))
                    + c.a3 * (c3 * (t[a + 5] + (t[b + 5] - t[a + 5]) * f) + s3 * (t[a + 6] + (t[b + 6] - t[a + 6]) * f))
                    + (t[a + 7] + (t[b + 7] - t[a + 7]) * f) * cp * (float) Math.sqrt(cp)
                    + c.macro(cc, s, Math.min(Math.max(y, yb), yc) - BASE_Y) + upper(cc, s, Math.min(Math.max(y, yb), yc));
            float bump = 0;
            for (int k = 0; k < ns; k++) {
                float sv = t[a + 9 + k] + (t[b + 9 + k] - t[a + 9 + k]) * f;
                if (sv < 1e-3f) continue;
                bump += sv * (float) Math.exp(kappa * (cc * shc[k] + s * shs[k] - 1));
            }
            return rho - (r * (1 + damp * ang) + damp * bump);
        }

        float lowerSide(float x, float y, float z) {
            float g = (Math.min(Math.max(y, yb), yc) - yb) / TAB;
            int i = Math.max(0, Math.min((int) g, tabS.length / st - 1)), o = i * st;
            float dx = x - tabS[o], dz = z - tabS[o + 1];
            float rho = (float) Math.sqrt(dx * dx + dz * dz);
            return (rho - tabS[o + 8] - 2 * TAB) / 1.6f - 1.3f * relief;
        }

        /** Approximate signed distance; accurate (not just a bound) out to {@code need} blocks outside. */
        float distance(float x, float y, float z, float need) {
            float ox = Math.max(Math.max(box[0] - x, x - box[3]), 0), oy = Math.max(Math.max(box[1] - y, y - box[4]), 0), oz = Math.max(Math.max(box[2] - z, z - box[5]), 0);
            float out = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (out > need) return out;
            float d;
            if (y > yc) {
                d = crownD(x, y, z);
            } else {
                float lb = lowerSide(x, y, z);
                if (lb > need + 1.3f * relief) return Math.max(lb, yb - y);
                float h = 1.5f;
                float a = side(x + h, y - h, z - h), b = side(x - h, y - h, z + h), cc = side(x - h, y + h, z - h), e = side(x + h, y + h, z + h);
                float f = (a + b + cc + e) * .25f;
                if (Math.abs(f) > 2 * need + 2.6f * relief) return Math.max(f * .5f - (f > 0 ? 1.3f * relief : 0), yb - y);
                float gx = (a - b - cc + e) / (4 * h), gy = (-a - b + cc + e) / (4 * h), gz = (-a + b - cc + e) / (4 * h);
                float g = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
                d = f / Math.max(g, .5f);
            }
            if (relief > 0 && Math.abs(d) < 40 + 1.3f * relief) {
                // Column-local relief: cell frame (base centre), cell identity seed.
                float q1 = 1 / 90f, q2 = 1 / 40f, lx = x - cx, ly = y - BASE_Y, lz = z - cz;
                // Fixed scales (a changing scale would make the texture swim); maturity only weights the broad octave.
                float fine = Math.min(relief, VARIANT >= 1 ? 4 : 9), broad = relief - Math.min(relief, 9), q0 = 1 / 170f;
                d += fine * (.65f * noise(lx * q1, ly * q1, lz * q1, rseed) + .35f * noise(lx * q2, ly * q2, lz * q2, rseed * 31 + 3))
                        + broad * noise(lx * q0, ly * q0, lz * q0, rseed * 17 + 9);
            }
            return Math.max(d, yb - y);
        }

        float[] normal(float x, float y, float z) {
            float h = 3f, n = 200;
            float gx = distance(x + h, y, z, n) - distance(x - h, y, z, n), gy = distance(x, y + h, z, n) - distance(x, y - h, z, n), gz = distance(x, y, z + h, n) - distance(x, y, z - h, n);
            float l = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
            return l < 1e-6f ? new float[]{0, 1, 0} : new float[]{gx / l, gy / l, gz / l};
        }
    }

    // ================================================================ billows (persistent identity, R1 shape)

    /**
     * One billow from its identity stream (seed, sid): T260.7 R1 compound billow with every parameter drawn
     * unconditionally (fixed draw order) and the lobes weighted continuously by activity, so changing activity or size
     * deforms the billow smoothly instead of switching its lobe count.
     */
    static Elem billow(long seed, long sid, float px, float py, float pz, float ux, float uy, float uz, float rn, float rt, float act,
                       float hx, float hy, float hz) {
        Random r = stream(seed, S_BSHAPE, sid, 0);
        float aspect = Math.max(.55f, Math.min(1.8f, (float) Math.exp(ConvectiveVolumeT2607.V_ASPECT * r.nextGaussian())));
        float s1 = (float) Math.sqrt(aspect), s2 = 1 / s1;
        float asym = Math.min(ConvectiveVolumeT2607.V_ASYM * r.nextFloat() * r.nextFloat() * 2, ConvectiveVolumeT2607.V_ASYM);
        float shear = ConvectiveVolumeT2607.V_SHEAR * (2 * r.nextFloat() - 1) * .5f;
        Deform df = new Deform(s1, s2, asym, shear);
        float total = ConvectiveVolumeT2607.V_AMP * (.55f + .45f * act) * (.6f + .8f * r.nextFloat());
        float w0 = r.nextFloat() + .2f, w1 = r.nextFloat() + .2f, w2 = r.nextFloat() + .2f, ws = w0 + w1 + w2;
        float[] w = {w0 / ws, w1 / ws, w2 / ws};
        for (int k = 0; k < 3; k++) {
            float vy = 2 * r.nextFloat() - 1, th = (float) (r.nextFloat() * Math.PI * 2), rr = (float) Math.sqrt(1 - vy * vy);
            df.v[k * 3] = rr * (float) Math.cos(th); df.v[k * 3 + 1] = vy; df.v[k * 3 + 2] = rr * (float) Math.sin(th);
            df.f[k] = 1.6f + 1.8f * r.nextFloat();
            df.ph[k] = (float) (r.nextFloat() * Math.PI * 2);
            df.amp[k] = total * w[k];
            df.ampSum += df.amp[k];
        }
        float rot = (float) (r.nextFloat() * Math.PI * 2);
        float kmul = (float) Math.exp(ConvectiveVolumeT2607.V_KSPREAD * r.nextGaussian());
        kmul *= ConvectiveVolumeT2607.A_KQUIET - (ConvectiveVolumeT2607.A_KQUIET - .7f) * act;
        Random l = stream(seed, S_BLOBE, sid, 0);
        int nMax = 1 + (int) (l.nextFloat() * 2.999f);
        if (rt > 55) nMax = Math.max(nMax, 2);
        int n = 0;
        for (int i = 0; i < 3; i++) {
            float th = (float) (l.nextFloat() * Math.PI * 2), el = (float) Math.asin(-.15f + 1.1f * l.nextFloat());
            float size = ConvectiveVolumeT2607.L_SIZE * (.55f + .75f * l.nextFloat()) * (i == 0 ? 1 : .8f);
            float reach = ConvectiveVolumeT2607.L_REACH * (.75f + .5f * l.nextFloat());
            float la = (float) Math.exp(.35f * l.nextGaussian());
            float flatR = l.nextFloat(), thr = .05f + .26f * i + .12f * l.nextFloat();
            if (i >= nMax) continue;
            float wl = smooth(thr - .14f, thr + .14f, act);
            if (wl < .05f) continue;
            float ce = (float) Math.cos(el);
            float sr = size * rt * (.35f + .65f * wl);
            float flat = .55f + .25f * flatR + .2f * smooth(.25f, .6f, act);
            int o = n * 7;
            df.lobe[o] = ce * (float) Math.cos(th) * reach * rt * wl; df.lobe[o + 1] = (float) Math.sin(el) * reach * rn * wl; df.lobe[o + 2] = ce * (float) Math.sin(th) * reach * rt * wl;
            df.lobe[o + 3] = sr * (float) Math.sqrt(la); df.lobe[o + 4] = sr * flat; df.lobe[o + 5] = sr / (float) Math.sqrt(la);
            df.lobe[o + 6] = sr * (.25f + .3f * (1 - act));
            n++;
        }
        df.lobes = n;
        df.ramp = ConvectiveVolumeT2607.LR_AMP * (.5f + .7f * act);
        df.rseed = l.nextLong();
        float k = Math.max(12f * (float) Math.sqrt(rt / 60f) * kmul, (.08f + .38f * (1 - act) * (1 - act)) * rt);
        return new Elem(px, py, pz, ux, uy, uz, rn, rt, rt, k, df, rot, hx, hy, hz);
    }

    // ================================================================ system state at time t

    /** One live cell's geometry at t. */
    static final class CellGeom {
        final int index;
        final CellSpec spec;
        final Trunk trunk;
        final Elem[] billows;
        final float[][] info; // per billow: id, x, y, z, rt, act, age
        final float erode, emergeOff;
        final long eseed, wseed, fseed, bseed;
        final float[] box;
        final float[] st;

        CellGeom(int index, CellSpec spec, Trunk trunk, Elem[] billows, float[][] info, float erode, float emergeOff, float[] st) {
            this.index = index; this.spec = spec; this.trunk = trunk; this.billows = billows; this.info = info; this.erode = erode; this.emergeOff = emergeOff; this.st = st;
            eseed = stream(spec.seed, S_ERODE, 0, 0).nextLong();
            wseed = stream(spec.seed, S_WARP, 0, 0).nextLong();
            fseed = stream(spec.seed, S_FINE, 0, 0).nextLong();
            bseed = stream(spec.seed, S_BASE, 0, 0).nextLong();
            float[] b = trunk.box.clone();
            for (Elem e : billows) {
                b[0] = Math.min(b[0], e.box[0]); b[1] = Math.min(b[1], e.box[1]); b[2] = Math.min(b[2], e.box[2]);
                b[3] = Math.max(b[3], e.box[3]); b[4] = Math.max(b[4], e.box[4]); b[5] = Math.max(b[5], e.box[5]);
            }
            if (VARIANT >= 1) {
                // Content can move by the warp plus the fine noise; the base can drop by BASE_CORE.
                float m = 1.6f * (WARP_A + WARP2_A) + FINE_A + (VARIANT == 4 ? QUIET_A : 0) + (1.6f * DEEP_WARP + 1.05f * DEEP_RELIEF) * spec.deep * spec.rPot;
                b[0] -= m; b[2] -= m; b[3] += m; b[4] += m; b[5] += m;
                b[1] = Math.min(b[1], BASE_Y - BASE_CORE - 4);
            }
            box = b;
        }

        CellGeom cull(Bounds bx, float reach) {
            List<Elem> keep = new ArrayList<>();
            float wm = VARIANT >= 1 ? 1.6f * (WARP_A + WARP2_A) + (1.6f * DEEP_WARP + 1.05f * DEEP_RELIEF) * spec.deep * spec.rPot : 0; // billows are evaluated at the warped point
            for (Elem e : billows) if (e.near(bx, reach + wm + 2 * e.k)) keep.add(e);
            return new CellGeom(index, spec, trunk, keep.toArray(new Elem[0]), info, erode, emergeOff, st);
        }

        float boxDistance(float x, float y, float z) {
            float ox = Math.max(Math.max(box[0] - x, x - box[3]), 0), oy = Math.max(Math.max(box[1] - y, y - box[4]), 0), oz = Math.max(Math.max(box[2] - z, z - box[5]), 0);
            return (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
        }

        /** Cell distance, accurate out to {@code need} outside (beyond: a lower bound). */
        float distance(float x, float y, float z, float need) {
            float out = boxDistance(x, y, z);
            if (out > need) return out;
            float px = x, py = y, pz = z, lx = x - trunk.cx, ly = y - BASE_Y, lz = z - trunk.cz, warpNeed = 0;
            if (VARIANT >= 1) {
                // Meso: a coherent cell-local warp of the whole cell (trunk AND billows together), so billows stop being
                // ellipsoids stuck on a tube and the tower outline creases and overlaps. Spatially varying strength
                // (uneven development); none at the condensation level.
                float m = .45f + .55f * (.5f + .5f * noise(lx / 430, ly / 430, lz / 430, wseed * 3 + 1));
                if (VARIANT == 3) m *= .7f + .6f * spec.regime(lx, ly, lz, st[6], st[2]); // deeper folds where strong
                if (VARIANT == 4) m *= .9f + .5f * spec.regime(lx, ly, lz, st[6], st[2]); // quiet keeps C1's folds
                float a = WARP_A * m * smooth(-10, 140, ly), q = 1 / WARP_L;
                float wx = lx * q, wy = ly * q, wz = lz * q;
                px += a * noise(wx, wy, wz, wseed);
                py += .55f * a * noise(wx + 7.3f, wy, wz, wseed * 5 + 2);
                pz += a * noise(wx, wy + 3.9f, wz, wseed * 11 + 3);
                // Second, billow-scale octave of the same warp (overlapping, uneven sub-structure).
                float a2 = WARP2_A * m * smooth(-10, 100, ly), q2 = 1 / WARP2_L;
                float vx = lx * q2, vy = ly * q2, vz = lz * q2;
                px += a2 * noise(vx, vy, vz, wseed * 17 + 4);
                py += .7f * a2 * noise(vx + 5.1f, vy, vz, wseed * 19 + 5);
                pz += a2 * noise(vx, vy + 2.7f, vz, wseed * 23 + 6);
                warpNeed = 1.6f * (WARP_A + WARP2_A);
                if (spec.deep > 0) {
                    // T260.10: tower-scale octave (fixed fraction of the cell's potential radius, cell frame): the deep
                    // column bends, overlaps and bulges as a whole instead of being a straight textured cylinder.
                    float a3 = DEEP_WARP * spec.deep * spec.rPot * smooth(-10, 300, ly), q3 = 1 / (1.15f * spec.rPot);
                    float tx = lx * q3, ty = ly * q3, tz = lz * q3;
                    px += a3 * noise(tx, ty, tz, wseed * 29 + 7);
                    py += .5f * a3 * noise(tx + 4.3f, ty, tz, wseed * 31 + 8);
                    pz += a3 * noise(tx, ty + 6.1f, tz, wseed * 37 + 9);
                    warpNeed += 1.6f * DEEP_WARP * spec.deep * spec.rPot + 1.05f * DEEP_RELIEF * spec.deep * spec.rPot;
                }
            }
            float d = trunk.distance(px, py, pz, need + warpNeed);
            for (Elem e : billows) {
                if (e.beyond(px, py, pz, need + warpNeed + 2 * e.k)) continue;
                d = ConvectiveSystemT260.smin(d, e.distance(px, py, pz), e.k);
            }
            if (VARIANT >= 1) {
                float ad = Math.abs(d), win = VARIANT == 4 ? 64 : 36;
                if (ad < win) {
                    // Fine: billow noise (|n|: rounded swells with sharp creases between them), in the warped cell frame,
                    // modulated by height / development and by a broad mask, so it is not a uniform texture. The glaciating
                    // calvus crown is quieter.
                    float hx = (px - trunk.cx), hy = py - BASE_Y, hz = (pz - trunk.cz);
                    float yrel = hy / Math.max(1, trunk.top - BASE_Y);
                    float mask = (.4f + .6f * smooth(.15f, .75f, yrel)) * (1 - (VARIANT == 4 ? .25f : .6f) * trunk.cal * smooth(.75f, .95f, yrel));
                    if (VARIANT == 4) {
                        // Regional character, quiet = less active but not smooth: strong regions get C3's crisp fine relief;
                        // quiet regions keep most of the 78-block octave and gain broad 150-block folds (large overlapping
                        // swells with creases between them: concavities, asymmetric relief).
                        float rg = spec.regime(lx, ly, lz, st[6], st[2]);
                        float q0 = 1 / 150f, q1 = 1 / 78f, q2 = 1 / 36f;
                        float n0 = Math.abs(noise(hx * q0, hy * q0, hz * q0, fseed * 29 + 11));
                        float n1 = Math.abs(noise(hx * q1, hy * q1, hz * q1, fseed)), n2 = Math.abs(noise(hx * q2, hy * q2, hz * q2, fseed * 13 + 5));
                        float a1 = FINE_A * mask * (.7f + .9f * rg * rg), a2 = .45f * FINE_A * mask * (.3f + 1.2f * rg * rg);
                        float a0 = QUIET_A * (1 - rg) * (.6f + .4f * mask);
                        d -= (a0 * (n0 - .3f) + a1 * (n1 - .3f) + a2 * (n2 - .3f)) * smooth(64, 52, ad);
                        mask = 0;
                    } else if (VARIANT == 3) {
                        // Regional character instead of a uniform texture: crisp cauliflower where strong, quiet where broad.
                        float rg = spec.regime(lx, ly, lz, st[6], st[2]);
                        mask *= .4f + 1.3f * rg * rg;
                    } else mask *= .55f + .45f * (.5f + .5f * noise(hx / 300, hy / 300, hz / 300, fseed * 3 + 7));
                    float q1 = 1 / 78f, q2 = 1 / 36f;
                    float n1 = Math.abs(noise(hx * q1, hy * q1, hz * q1, fseed)), n2 = Math.abs(noise(hx * q2, hy * q2, hz * q2, fseed * 13 + 5));
                    d -= FINE_A * mask * ((n1 - .3f) + .45f * (n2 - .3f)) * smooth(36, 28, ad);
                }
                if (spec.deep > 0) {
                    // T260.10: self-similar relief of deep towers. The billows of a km-wide tower are hundreds of blocks
                    // across, so fixed-scale relief leaves them smooth balls; this |n| octave pair at fixed fractions of the
                    // cell's potential radius gives them their own sub-lobes and creases (cell frame: persistent).
                    float A = DEEP_RELIEF * spec.deep * spec.rPot, winD = 64 + 2 * A, ad2 = Math.abs(d);
                    if (ad2 < winD) {
                        float hx = px - trunk.cx, hy = py - BASE_Y, hz = pz - trunk.cz, q1 = 1 / (.3f * spec.rPot), q2 = 1 / (.14f * spec.rPot);
                        float n1 = Math.abs(noise(hx * q1, hy * q1, hz * q1, fseed * 41 + 13)), n2 = Math.abs(noise(hx * q2, hy * q2, hz * q2, fseed * 43 + 17));
                        float yr = hy / Math.max(1, trunk.top - BASE_Y), quiet = 1 - .45f * trunk.cal * smooth(.75f, .95f, yr);
                        d -= A * quiet * smooth(-10, 200, hy) * ((n1 - .3f) + .45f * (n2 - .3f)) * smooth(winD, winD - 12 - A, ad2);
                    }
                }
                // Base: the cell's own condensation level (cell frame, unwarped): gently uneven, slightly lowered under the
                // updraft core, rounded at the edge. Plausible level kept; no rigid slab.
                float rho = (float) Math.sqrt(lx * lx + lz * lz), R = trunk.R;
                float yb = BASE_Y + BASE_VAR * noise(lx / 260, 0.37f, lz / 260, bseed) + .45f * BASE_VAR * noise(lx / 95, 1.91f, lz / 95, bseed * 7 + 1)
                        - BASE_CORE * (float) Math.exp(-(rho * rho) / (.3f * R * R)) * (1 - erode);
                d = ConvectiveSystemT260.smax(d, yb - y, BASE_K);
            }
            // T260.9 (dissolve): thin deeper than any interior. The T260.8 expression is kept verbatim (bit parity).
            float e3 = spec.dissolve ? erode * erode * erode * (1.3f * trunk.R + 60) : erode * erode * erode * .9f * trunk.R;
            // Deep-interior estimates are not distances; a dissolving cell must not expose them as stray fragments.
            // T260.10 deep cells: the clamp starts at the cell's own depth and rises as it dissipates, so a km-wide cell keeps
            // its shape while it shrinks (a flat -60 interior turned its whole remnant into noise iso-surface blocks).
            if (spec.dissolve && erode > 0) d = Math.max(d, spec.deep > 0 ? -(60 + .5f * trunk.R * (1 - erode)) : -60);
            if (erode > 0 && d < need + 2 + e3) {
                // Dissipation in the cell frame: uneven erosion opens irregular gaps (no world anchoring).
                // Broad uneven erosion first (irregular gaps), then the whole cell thins away: a fully dissipated cell is gone,
                // not a gritty slab (T260.8 first try: fine-scale erosion only).
                float le = spec.deep > 0 ? Math.max(130, .45f * spec.rPot) : 130; // deep: broad uneven erosion, not 130-block lumps
                float n = noise(lx / le, ly / le, lz / le, eseed);
                d += erode * (22 + 34 * n) + e3;
            }
            return d + emergeOff;
        }
    }

    /** Pair blend at t: indices into the geometry list. */
    record Link(int a, int b, float alpha, float minTop, boolean daughter, float ks) {}

    public static final class SystemState {
        public final Scenario scenario;
        public final float t;
        final List<CellGeom> geoms = new ArrayList<>();
        final List<Link> links = new ArrayList<>();
        final List<Elem> bridges = new ArrayList<>();
        final List<float[]> bridgeInfo = new ArrayList<>();

        SystemState(Scenario s, float t) {
            this.scenario = s;
            this.t = t;
            int[] gi = new int[s.cells.size()];
            java.util.Arrays.fill(gi, -1);
            for (int i = 0; i < s.cells.size(); i++) {
                CellGeom g = build(s, i, t);
                if (g == null) continue;
                gi[i] = geoms.size();
                geoms.add(g);
            }
            for (Pair p : s.pairs) {
                if (gi[p.i] < 0 || gi[p.j] < 0) continue;
                float a = s.alpha(p, t);
                if (a < 1e-3f) continue;
                CellGeom A = geoms.get(gi[p.i]), B = geoms.get(gi[p.j]);
                float minTop = Math.min(A.trunk.top, B.trunk.top) - BASE_Y;
                links.add(new Link(gi[p.i], gi[p.j], a, minTop, p.daughterLink, Math.min(A.spec.kScale, B.spec.kScale)));
                if (!p.daughterLink) bridges(p, A, B, a, minTop);
            }
        }

        /** Connecting material of one attached pair: own pair identity, grows out of a parent flank toward the other cell. */
        private void bridges(Pair p, CellGeom A, CellGeom B, float alpha, float minTop) {
            float minPot = Math.min(A.spec.hPot, B.spec.hPot);
            for (int m = 0; m < BRIDGES; m++) {
                Random q = stream(p.pid, S_BRIDGE, m, 0);
                float am = .05f + .7f * q.nextFloat(), hq = q.nextFloat(), sm = 2 * q.nextFloat() - 1, tq = .3f + .4f * q.nextFloat(), sf = .55f + .5f * q.nextFloat();
                boolean fromA = q.nextFloat() < .5f;
                float hm = 15 + (float) Math.pow(hq, 1.3f) * .5f * minPot;
                float w = smooth(am, am + .25f, alpha) * (1 - smooth(.5f, .8f, hm / Math.max(1, minTop)));
                if (w < .03f) continue;
                CellGeom S = fromA ? A : B, O = fromA ? B : A;
                float y = BASE_Y + hm;
                float sx = S.trunk.axisX(Math.min(y, S.trunk.yc)), sz = S.trunk.axisZ(Math.min(y, S.trunk.yc));
                float ox = O.trunk.axisX(Math.min(y, O.trunk.yc)), oz = O.trunk.axisZ(Math.min(y, O.trunk.yc));
                float L = (float) Math.hypot(ox - sx, oz - sz);
                if (L < 1) continue;
                float dx = (ox - sx) / L, dz = (oz - sz) / L;
                float rs = S.trunk.radius(y, dx, dz), ro = O.trunk.radius(y, -dx, -dz);
                float gap = L - rs - ro, rmin = Math.min(rs, ro);
                float px = -dz, pz = dx;
                // The element starts seated on the source flank and reaches into the gap as attachment grows.
                float reach = rs + Math.max(gap, 0) * tq * w;
                float lat = sm * .4f * rmin;
                float cx = sx + dx * reach + px * lat, cz = sz + dz * reach + pz * lat;
                float rt = (.22f * rmin + w * Math.max(gap, 0) * .5f) * sf * (.4f + .6f * w);
                if (rt < 4) continue;
                float rn = .72f * rt;
                float ux = px * sm * .6f + dx * .3f * (fromA ? 1 : -1) * 0, uy = .9f, uz = pz * sm * .6f;
                float un = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
                Elem e = billow(p.pid, 1_000_000L + m, cx, y - .25f * rn, cz, ux / un, uy / un, uz / un, rn, rt, .4f, dx, 0, dz);
                bridges.add(e);
                bridgeInfo.add(new float[]{p.pid, m, cx, y, cz, rt, w});
            }
        }

        static CellGeom build(Scenario s, int i, float t) {
            float[] st = s.state(i, t);
            if (st == null) return null;
            CellSpec c = s.cells.get(i);
            if (c.dissolve && st[2] > .995f) return null; // T260.9: fully dissipated = gone
            float top = st[0], R = st[1], dd = st[2], emerge = st[3], cx = st[4], cz = st[5], rate = st[6], cal = st[7];
            float relief = 9 + 16 * smooth(.6f, .1f, Math.min(1, rate)) + 10 * dd; // mature cells: broad swells, not wax (weights the fixed broad octave)
            Trunk tr = new Trunk(c, cx, cz, top, R, cal, relief);
            float[] hist = s.topHistory(i, t);
            List<Elem> els = new ArrayList<>();
            List<float[]> info = new ArrayList<>();
            float[] w = new float[tr.st];
            for (float[] sl : c.slots()) {
                float h = sl[0], phi = sl[1], sf = sl[2], rr = sl[3];
                long cand = (long) sl[4];
                float thr = h + .12f * rr;
                float tb = birthTime(hist, thr, c.birth, t);
                if (Float.isNaN(tb)) continue;
                float age = t - tb, grow = smooth(0, BORN_T, age);
                if (grow < .02f) continue;
                float y = BASE_Y + h, cp = (float) Math.cos(phi), sp = (float) Math.sin(phi);
                float sx, sz, wref;
                float q = 0;
                if (y <= tr.yc) {
                    tr.row(y, w);
                    float r = tr.radius(w, cp, sp, 1e9f, h);
                    sx = w[0] + r * cp; sz = w[1] + r * sp;
                    wref = w[2];
                } else if (tr.dome != null || VARIANT == 4) {
                    // C3: seat on the developed crown along a ray from the crown base (the slot's own direction and
                    // elevation), so billows follow the competing domes instead of one ellipsoid.
                    q = Math.min(.985f, (y - tr.yc) / tr.hcap);
                    tr.row(tr.yc, w);
                    float ce = (float) Math.sqrt(1 - q * q), dxr = cp * ce, dyr = q, dzr = sp * ce;
                    float lo = 0, hi = 1.6f * (w[2] + tr.hcap);
                    for (int it = 0; it < 22; it++) {
                        float m = (lo + hi) * .5f;
                        if (tr.crownD(w[0] + dxr * m, tr.yc + dyr * m, w[1] + dzr * m) < 0) lo = m; else hi = m;
                    }
                    float ts = (lo + hi) * .5f;
                    sx = w[0] + dxr * ts; sz = w[1] + dzr * ts; y = tr.yc + dyr * ts;
                    wref = w[2];
                } else {
                    q = Math.min(.985f, (y - tr.yc) / tr.hcap);
                    tr.row(tr.yc, w);
                    float rp = tr.radius(w, cp, sp, 1e9f, tr.yc - BASE_Y) * tr.domeS, rho = rp * (float) Math.sqrt(1 - q * q);
                    float ax = w[0] + tr.cpsi * tr.apex * q * q, az = w[1] + tr.spsi * tr.apex * q * q;
                    sx = ax + rho * cp; sz = az + rho * sp;
                    y = tr.yc + q * tr.hcap;
                    wref = w[2];
                }
                float[] n = tr.normal(sx, y, sz);
                float youth = (float) Math.exp(-age / AGE_T);
                float crown = cal * smooth(.72f, .95f, h / Math.max(1, top));
                float act = .22f + .5f * sl[7] + c.vigor + .45f * youth * (.35f + .65f * Math.min(1, rate)) - .55f * dd - .7f * crown;
                float reg = VARIANT >= 3 ? c.regime(sx - cx, y - BASE_Y, sz - cz, rate, dd) : .5f;
                float actSize = Math.max(0, Math.min(1, act)); // size never follows the regional boost (no big balls)
                if (VARIANT == 3) act += .38f * (reg - .5f); // strong regions: active, lobed billows; quiet: merged
                // C4: quiet is only mildly less active (fewer lobes), so its billows do not melt into smooth masses.
                if (VARIANT == 4) act += .38f * Math.max(0, reg - .5f) + .12f * Math.min(0, reg - .5f);
                act = Math.max(0, Math.min(1, act));
                float rt = BILLOW_R * wref * sf * (1 + .6f * sl[6] * smooth(.55f, .75f, VARIANT >= 3 ? actSize : act));
                rt = Math.min(rt, .75f * wref) * grow;
                if (rt < 3) continue;
                float obl = .68f + .12f * act, rn = obl * rt;
                float prot = PROT * (.42f + .68f * act);
                float ux = n[0], uy = n[1] + UP * (.4f + .6f * act), uz = n[2];
                float un = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
                ux /= un; uy /= un; uz /= un;
                float c0 = (prot - 1) * rn;
                Elem e = billow(c.seed, cand, sx + ux * c0, y + uy * c0, sz + uz * c0, ux, uy, uz, rn, rt, act, -sp, 0, cp);
                if (VARIANT == 4) crown *= .5f; // C4: a softened crown read as a separate smooth dome
                if (crown > 0) {
                    // Calvus: the glaciating crown softens (broader blends, weaker relief); identity unchanged.
                    e.df.ramp *= 1 - .8f * crown;
                    e = new Elem(e.x, e.y, e.z, e.frame[0], e.frame[1], e.frame[2], e.ry, e.rx, e.rz, Math.min(e.k * (1 + 2.5f * crown), Math.max(e.k, .5f * e.rx)), e.df, 0, e.frame[3], e.frame[4], e.frame[5]);
                }
                if (VARIANT == 4) {
                    // C4: strong regions get C3's tighter blends; quiet regions keep C1's blends (never wider).
                    float strong = Math.max(0, 2 * (reg - .5f)) * (1 - smooth(45, 85, rt));
                    e = new Elem(e.x, e.y, e.z, e.frame[0], e.frame[1], e.frame[2], e.ry, e.rx, e.rz, e.k * (1 - .45f * strong), e.df, 0, e.frame[3], e.frame[4], e.frame[5]);
                }
                if (VARIANT == 3) {
                    // Strong regions: tighter blends = distinct lobes with deep folds; quiet regions: wide blends = merged.
                    e = new Elem(e.x, e.y, e.z, e.frame[0], e.frame[1], e.frame[2], e.ry, e.rx, e.rz, e.k * (1.25f - .65f * reg * (1 - smooth(45, 85, rt))), e.df, 0, e.frame[3], e.frame[4], e.frame[5]);
                }
                els.add(e);
                info.add(new float[]{cand, e.x, e.y, e.z, rt, act, age});
                tier2(c.seed, cand, e, act, els, c.deep > 0 ? 40 : VARIANT >= 3 ? 18 : 4);
            }
            float emergeOff = (1 - emerge) * (c.r0 + 40);
            return new CellGeom(i, c, tr, els.toArray(new Elem[0]), info.toArray(new float[0][]), dd, emergeOff, st);
        }

        /**
         * Persistent tier-2 domes of one billow: up to 6 fixed sub-slots (own stream per billow identity), accepted by
         * angular spacing in slot order (scale-free, so acceptance never changes while the parent grows), each fading
         * in above its own activity threshold. Seated on the parent's surface by bisection along its direction.
         */
        static void tier2(long seed, long cand, Elem parent, float act, List<Elem> out, float minR) {
            if (parent.rx < 14) return;
            float[] f = parent.frame;
            List<float[]> dirs = new ArrayList<>();
            for (int j = 0; j < 6; j++) {
                Random q = stream(seed, S_T2, cand, j);
                float tilt = (float) Math.acos(1 - q.nextFloat() * (1 - Math.cos(Math.toRadians(72)))), az = (float) (q.nextFloat() * Math.PI * 2);
                float sz = T2_R * (.7f + .6f * q.nextFloat()), thr = .5f + .35f * q.nextFloat();
                float ct = (float) Math.cos(tilt), stt = (float) Math.sin(tilt), ca = (float) Math.cos(az), sa = (float) Math.sin(az);
                float dx = f[0] * ct + (f[3] * ca + f[6] * sa) * stt, dy = f[1] * ct + (f[4] * ca + f[7] * sa) * stt, dz = f[2] * ct + (f[5] * ca + f[8] * sa) * stt;
                boolean ok = true;
                for (float[] o : dirs) {
                    float dot = o[0] * ct + o[1] * stt * ca + o[2] * stt * sa; // in the parent frame: (cos tilt, sin tilt cos az, sin tilt sin az)
                    float ang = (float) Math.acos(Math.max(-1, Math.min(1, dot)));
                    if (ang < 1.1f * (o[3] + sz)) { ok = false; break; }
                }
                if (!ok) continue;
                dirs.add(new float[]{ct, stt * ca, stt * sa, sz});
                float w = smooth(thr - .12f, thr + .12f, act);
                if (w < .08f || dy < -.2f) continue;
                float lo = 0, hi = parent.bound;
                for (int it = 0; it < 20; it++) {
                    float m = (lo + hi) * .5f;
                    if (parent.distance(parent.x + dx * m, parent.y + dy * m, parent.z + dz * m) < 0) lo = m; else hi = m;
                }
                float ts = (lo + hi) * .5f, rt = sz * parent.rx * (.4f + .6f * w);
                if (rt < minR) continue; // C3: sub-bake-scale domes only read as round pimples (T260.10 deep cells: under 40)
                float uy = dy + .25f, un = (float) Math.sqrt(dx * dx + uy * uy + dz * dz);
                float nx = dx / un, ny = uy / un, nz = dz / un, rn = .62f * rt, c0 = (.70f - 1) * rn;
                out.add(new Elem(parent.x + dx * ts + nx * c0, parent.y + dy * ts + ny * c0, parent.z + dz * ts + nz * c0, nx, ny, nz, rn, rt, 7f * (float) Math.sqrt(rt / 25f)));
            }
        }

        /** First time the running-max top reached {@code thr} (linear within the grid step); NaN if not yet. */
        static float birthTime(float[] hist, float thr, float birth, float t) {
            int n = hist.length;
            if (hist[n - 1] < thr) return Float.NaN;
            int lo = 0, hi = n - 1;
            if (hist[0] >= thr) return birth;
            while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (hist[m] >= thr) hi = m; else lo = m; }
            float tl = birth + lo * DT, th = hi == n - 1 ? t : birth + hi * DT;
            float f = (thr - hist[lo]) / Math.max(1e-4f, hist[hi] - hist[lo]);
            return tl + (th - tl) * f;
        }

        public Field field() { return new Field(geoms.toArray(new CellGeom[0]), links.toArray(new Link[0]), bridges.toArray(new Elem[0])); }

        /** Content bounds (cells and bridges, no blend margin). */
        public Bounds bounds() {
            float[] b = {1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f};
            for (CellGeom g : geoms) for (int k = 0; k < 3; k++) { b[k] = Math.min(b[k], g.box[k]); b[k + 3] = Math.max(b[k + 3], g.box[k + 3]); }
            for (Elem e : bridges) for (int k = 0; k < 3; k++) { b[k] = Math.min(b[k], e.box[k]); b[k + 3] = Math.max(b[k + 3], e.box[k + 3]); }
            return new Bounds(b[0], Math.max(b[1], BASE_Y - 10), b[2], b[3], b[4], b[5]);
        }

        public int billowCount() { int n = 0; for (CellGeom g : geoms) n += g.billows.length; return n; }

        /** Per-cell summary for logs. */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            for (CellGeom g : geoms) sb.append(String.format(java.util.Locale.ROOT, " %s[id=%s top=%.0f R=%.0f billows=%d act=%.2f dd=%.2f%s]", g.spec.name,
                    Long.toHexString(g.spec.id).substring(0, 6), g.st[0], g.st[1], g.billows.length, meanAct(g), g.st[2], g.spec.daughter ? " daughter" : "")).append(crownInfo(g.trunk));
            for (Link l : links) sb.append(String.format(java.util.Locale.ROOT, " link(%s,%s)=%.2f", geoms.get(l.a()).spec.name, geoms.get(l.b()).spec.name, l.alpha()));
            sb.append(" bridges=").append(bridges.size());
            return sb.toString();
        }

        static String crownInfo(Trunk t) {
            if (t.dome == null) return "";
            StringBuilder b = new StringBuilder(String.format(java.util.Locale.ROOT, "{hcap=%.0f R0=%.0f", t.hcap, t.width(t.yc)));
            for (int j = 0; j < t.nd; j++) b.append(String.format(java.util.Locale.ROOT, " d%d(o=%.0f,%.0f r=%.0f s=%.2f h=%.0f k=%.0f)", j, t.dome[j * 8], t.dome[j * 8 + 1], t.dome[j * 8 + 2], t.dome[j * 8 + 5], t.dome[j * 8 + 6], t.dome[j * 8 + 7]));
            return b.append("}").toString();
        }

        static float meanAct(CellGeom g) { float s = 0; for (float[] i : g.info) s += i[5]; return g.info.length == 0 ? 0 : s / g.info.length; }
    }

    // ================================================================ composed field

    public static final class Field implements SparseBrickVolume.Field {
        final CellGeom[] cells;
        final Link[] links;
        final Elem[] bridges;
        final float[] kmax; // per cell: largest pair blend it takes part in

        /**
         * Interior clamp. T260.10: with deep cells the clamp is deeper so a 48-block coarse cell well inside a km-wide
         * tower can be skipped as FULL (at -60 it never can, and the bake becomes volume- instead of surface-bound).
         * Conservative as before: the skip is audited sample by sample in smoke.
         */
        final float floor;

        Field(CellGeom[] cells, Link[] links, Elem[] bridges) {
            this(cells, links, bridges, deepFloor(cells));
        }

        static float deepFloor(CellGeom[] cells) {
            for (CellGeom g : cells) if (g.spec.deep > 0) return DEEP_FLOOR;
            return ConvectiveVolumeT2607.FULL_SKIP_FLOOR;
        }

        Field(CellGeom[] cells, Link[] links, Elem[] bridges, float floor) {
            this.floor = floor;
            this.cells = cells; this.links = links; this.bridges = bridges;
            kmax = new float[cells.length];
            for (Link l : links) { float k = l.alpha() * KMAX * l.ks(); kmax[l.a()] = Math.max(kmax[l.a()], k); kmax[l.b()] = Math.max(kmax[l.b()], k); }
        }

        static float pairK(Link l, float y) {
            float u = (y - BASE_Y) / Math.max(1, l.minTop());
            return l.alpha() * KMAX * (1 - .8f * smooth(.4f, .85f, u)) * (l.daughter() ? .6f : 1) * l.ks();
        }

        @Override public SparseBrickVolume.Field local(Bounds box) {
            float reach = BAND + 12;
            List<Integer> keep = new ArrayList<>();
            int[] map = new int[cells.length];
            java.util.Arrays.fill(map, -1);
            for (int i = 0; i < cells.length; i++) {
                float r = reach + kmax[i] + 2;
                float[] b = cells[i].box;
                if (b[0] - r > box.maxX() || b[3] + r < box.minX() || b[1] - r > box.maxY() || b[4] + r < box.minY() || b[2] - r > box.maxZ() || b[5] + r < box.minZ()) continue;
                map[i] = keep.size();
                keep.add(i);
            }
            CellGeom[] cs = new CellGeom[keep.size()];
            for (int n = 0; n < cs.length; n++) cs[n] = cells[keep.get(n)].cull(box, reach + kmax[keep.get(n)] + 2);
            List<Link> ls = new ArrayList<>();
            for (Link l : links) if (map[l.a()] >= 0 && map[l.b()] >= 0) ls.add(new Link(map[l.a()], map[l.b()], l.alpha(), l.minTop(), l.daughter(), l.ks()));
            List<Elem> bs = new ArrayList<>();
            for (Elem e : bridges) if (e.near(box, reach + 2 * e.k)) bs.add(e);
            return new Field(cs, ls.toArray(new Link[0]), bs.toArray(new Elem[0]), floor);
        }

        @Override public float distance(float x, float y, float z) {
            return Math.max(raw(x, y, z), -floor);
        }

        public float raw(float x, float y, float z) {
            float BIG = ConvectiveSystemT260.BIG;
            int n = cells.length;
            if (n == 0 && bridges.length == 0) return BIG;
            float[] ds = new float[n];
            float d = BIG;
            for (int i = 0; i < n; i++) {
                // Accuracy needed: the band, plus the largest pair blend this cell takes part in here.
                float need = BAND + 12;
                for (Link l : links) if (l.a() == i || l.b() == i) need = Math.max(need, BAND + 12 + pairK(l, y));
                ds[i] = cells[i].distance(x, y, z, need);
                d = Math.min(d, ds[i]);
            }
            for (Link l : links) {
                float k = pairK(l, y);
                if (k > .5f) d = Math.min(d, ConvectiveSystemT260.smin(ds[l.a()], ds[l.b()], k));
            }
            float td = BAND + 12;
            for (Elem e : bridges) {
                if (e.beyond(x, y, z, td + 2 * e.k)) continue;
                d = ConvectiveSystemT260.smin(d, e.distance(x, y, z), e.k);
            }
            if (d >= BIG) return BIG;
            return VARIANT >= 1 ? ConvectiveSystemT260.smax(d, BASE_Y - BASE_CORE - 6 - y, BASE_BLEND) : ConvectiveSystemT260.smax(d, BASE_Y - y, BASE_BLEND);
        }
    }

    /** Bakes a state on a fixed domain (voxel 6, feather 12, as T260.6/T260.7). */
    public static SparseBrickVolume bake(SystemState s, Bounds domain) {
        // C1/C2: the warp and billow noise raise the field's Lipschitz bound (coarse skips audited in smoke).
        Field f = s.field();
        // T260.10: deep-tower relief and warp steepen the field; the bound rises only when deep cells are present.
        float lip = f.floor > ConvectiveVolumeT2607.FULL_SKIP_FLOOR ? 3f : VARIANT >= 3 ? 2.5f : VARIANT >= 1 ? 2.2f : 1.5f;
        return new SparseBrickVolume(f, domain, 6, 12, lip, .25f);
    }
}
