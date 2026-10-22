package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import static net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.*;

/**
 * T257 candidate A (REJECTED 9 Oct 2026: smooth vertical blob + thin horizontal spike), frozen for
 * identical-pose comparison only. Continuous convective growth field. Experimental fixture geometry only; no production,
 * save or network authority. GPU mirror: cloud_convective_growth_field_a.glsl (keep in step).
 *
 * The storm body is ONE swept volume around one growth spine: sample height gives the spine
 * parameter, the spine gives tilt/shear/curvature, and a height-dependent cross section (ellipse,
 * two low-order radial modes, turret displacements, billow creases) gives the surface. Turrets
 * displace that one surface; they are never solids of their own. The canopy is an independent
 * 2.5D sheet joined by a single smooth union. Evaluation cost is constant: one spine, one cross
 * section, a fixed turret table, one canopy - no list of growth structures is ever traversed.
 */
public final class ConvectiveGrowthFieldA {
    public static final int VERSION = 1;
    public static final int MAX_TURRETS = 8;
    /** Header texels before the turret table. */
    static final int HEADER_TEXELS = 22;
    /** Billow offset: cells - 0.62 lies in [-0.62, 0.38], so 0.62*amp bounds |billow|. */
    static final float BILLOW_MEAN = .62f;
    private ConvectiveGrowthFieldA() {}

    /** Broad radial displacement of the body surface, centred at height y and azimuth theta (blocks). */
    public record Turret(float y, float theta, float amplitude, float width) {}

    public static Program compile(String type, CloudShapeProfile shape, long seed) {
        Archetype kind = switch (type) {
            case "cumulonimbus_calvus" -> Archetype.CALVUS;
            case "cumulonimbus_capillatus" -> Archetype.CAPILLATUS;
            case "supercell" -> Archetype.SUPERCELL;
            default -> throw new IllegalArgumentException("Not a storm: " + type);
        };
        for (float value : new float[]{shape.getBaseRadius(), shape.getTopOffset(), shape.getBaseOffset(), shape.getVerticalTilt(),
                shape.getWindShearStrength(), shape.getTowerNarrowing(), shape.getAnvilSpread(), shape.getLobeStrength(),
                shape.getCellSplitStrength(), shape.getStormWallStrength(), shape.getBaseFlattening(), shape.getEdgeRaggedness()}) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite growth descriptor");
        }
        // Same legacy fixture adapter as T255/T256 (R=2.5 baseRadius, H=4 topOffset), so the
        // comparison isolates representation. Depth multipliers are family development, not scale.
        float r = shape.getBaseRadius() * 2.5f, h = shape.getTopOffset() * 4, base = shape.getBaseOffset() * 2;
        float tilt = shape.getVerticalTilt(), shear = shape.getWindShearStrength(), narrow = shape.getTowerNarrowing();
        float spread = shape.getAnvilSpread(), strength = shape.getLobeStrength(), wall = shape.getStormWallStrength();
        float split = shape.getCellSplitStrength(), ragged = shape.getEdgeRaggedness();
        var rng = new SplittableRandom(seed);
        var f = new float[HEADER_TEXELS * 4];
        float depth = kind == Archetype.CALVUS ? 1.55f : kind == Archetype.CAPILLATUS ? 1.26f : 1.16f;
        float H = h * depth, yBase = -base;
        // Spine: x/z(s) = H*(tilt*s + shear*s^2 + curve*s^3). Supercell leans harder and bows crosswind.
        float side = rng.nextBoolean() ? 1 : -1; // inflow/flank side, crosswind
        float lean = kind == Archetype.SUPERCELL ? 1.15f * tilt + .22f * shear : .55f * tilt + .10f * shear;
        float shearBend = (kind == Archetype.SUPERCELL ? .20f : .12f) * shear;
        float tiltX = lean - shearBend * .5f, shearX = shearBend, curveX = (float) rng.nextDouble(-.03, .03);
        float bow = kind == Archetype.SUPERCELL ? .10f : .035f;
        float tiltZ = side * bow * (float) rng.nextDouble(.6, 1.0), shearZ = -tiltZ * .6f, curveZ = (float) rng.nextDouble(-.02, .02);
        // Height profile knots (fractions of r). Broad rooted lower body, strong mid, crown.
        float rBase, rLow, rMid, rUp, rCrown, crownStart;
        switch (kind) {
            case CALVUS -> { rBase = .74f; rLow = .68f; rMid = .52f - .05f * narrow; rUp = .50f; rCrown = .58f; crownStart = .85f; }
            case CAPILLATUS -> { rBase = .82f; rLow = .80f; rMid = .62f - .06f * narrow; rUp = .60f; rCrown = .60f; crownStart = .86f; }
            default -> { rBase = .92f; rLow = .86f; rMid = .58f - .05f * narrow; rUp = .54f; rCrown = .50f; crownStart = .86f; }
        }
        // Tower narrowing pinches the mid and upper column (control response, all families).
        rMid -= .06f * narrow; rUp -= .05f * narrow;
        // Flank axis: supercell inflow/flanking side (upwind and crosswind), others seed-oriented.
        float flank = kind == Archetype.SUPERCELL ? (float) Math.atan2(side * .35f, -1f) + (float) rng.nextDouble(-.12, .12)
                : (float) rng.nextDouble(-Math.PI, Math.PI);
        float aspectLow = kind == Archetype.SUPERCELL ? 1.45f + .25f * split : 1.12f + .12f * split, aspectHigh = kind == Archetype.CALVUS ? 1.04f : 1.10f;
        float orientLow = flank, orientHigh = kind == Archetype.CALVUS ? flank + (float) rng.nextDouble(-.8, .8) : 0f;
        // k=1: lower body bulges to the inflow/flank side; crown leans downwind (upper spread).
        float m1Low = kind == Archetype.SUPERCELL ? .22f : .08f, m1High = kind == Archetype.CALVUS ? .07f : kind == Archetype.CAPILLATUS ? .10f : .12f;
        float d1Low = flank, d1High = (float) rng.nextDouble(-.25, .25);
        // k=3 lobing whose phase twists with height: continuous shoulders, not stacked rings.
        float m3Low = .05f + .03f * strength, m3High = (kind == Archetype.CAPILLATUS ? .05f : .09f) * (.6f + .5f * strength);
        float p3Low = (float) rng.nextDouble(0, 2 * Math.PI), p3High = p3Low + (float) rng.nextDouble(1.2, 2.6);
        // Wall cloud: low-level lowering of the base under the updraft's inflow side (supercell only).
        float wallR = r * .40f, wallDepth = kind == Archetype.SUPERCELL ? H * .12f * wall : 0;
        float wallX = (float) Math.cos(flank) * r * .30f, wallZ = (float) Math.sin(flank) * r * .30f;
        // Billow creases (cauliflower): integer azimuth frequency keeps the field continuous.
        float billowAmp = (kind == Archetype.CALVUS ? .13f : kind == Archetype.CAPILLATUS ? .12f : .13f) * (.7f + .4f * ragged);
        float billowTheta = kind == Archetype.CALVUS ? 4 : 5;
        float meanR = r * (rLow + rMid + rUp) / 3;
        float billowS = Math.max(3, Math.round(H / (float) (1.7 * Math.PI * meanR / billowTheta)));
        float billowPhase = (float) rng.nextDouble(0, 2 * Math.PI);
        float billowTop = kind == Archetype.CAPILLATUS ? .78f : kind == Archetype.SUPERCELL ? .88f : .97f; // glaciated tops smooth out
        float feather = Math.max(2, r * .055f);

        // Mesoscale turrets: displacement of the one surface, never independent solids.
        List<Turret> turrets = new ArrayList<>();
        switch (kind) {
            case CALVUS -> {
                // Vigorous integrated shoulders all the way up; crown turrets largest.
                int n = 8;
                for (int i = 0; i < n; i++) {
                    float s = .30f + .60f * (i + (float) rng.nextDouble(.15, .85)) / n;
                    float theta = (float) (i * 2.39996323 + rng.nextDouble(-.4, .4));
                    float amp = r * (float) rng.nextDouble(.24, .36) * (.75f + .5f * s) * (.8f + .3f * strength);
                    turrets.add(new Turret(yBase + H * s, theta, amp, r * (float) rng.nextDouble(.40, .52)));
                }
            }
            case CAPILLATUS -> {
                int n = 6;
                for (int i = 0; i < n; i++) {
                    float s = .26f + .56f * (i + (float) rng.nextDouble(.15, .85)) / n;
                    float theta = (float) (i * 2.39996323 + rng.nextDouble(-.4, .4));
                    float amp = r * (float) rng.nextDouble(.20, .30) * (.8f + .3f * strength);
                    turrets.add(new Turret(yBase + H * s, theta, amp, r * (float) rng.nextDouble(.40, .52)));
                }
            }
            default -> {
                // Main updraft shoulders on the inflow half, then a flanking line stepping down the flank.
                for (int i = 0; i < 4; i++) {
                    float s = .50f + .34f * (i + (float) rng.nextDouble(.2, .8)) / 4;
                    float theta = flank + (float) (i * 1.9 - 2.4 + rng.nextDouble(-.3, .3));
                    float amp = r * (float) rng.nextDouble(.20, .30) * (.8f + .3f * strength);
                    turrets.add(new Turret(yBase + H * s, theta, amp, r * (float) rng.nextDouble(.34, .44)));
                }
                for (int i = 0; i < 3; i++) {
                    float s = .44f - .11f * i + (float) rng.nextDouble(-.02, .02);
                    float theta = flank + (float) rng.nextDouble(-.12, .12);
                    float amp = r * (.40f - .07f * i) * (.8f + .3f * strength);
                    turrets.add(new Turret(yBase + H * s, theta, amp, r * (.36f - .03f * i)));
                }
            }
        }
        // Canopy: independent sheet anchored at the upper-spine exit.
        boolean canopy = kind != Archetype.CALVUS;
        float sRoot = crownStart - .04f;
        float rootX = H * (tiltX * sRoot + shearX * sRoot * sRoot + curveX * sRoot * sRoot * sRoot);
        float rootZ = H * (tiltZ * sRoot + shearZ * sRoot * sRoot + curveZ * sRoot * sRoot * sRoot);
        // Capillatus: glaciated crown becomes the canopy root. Supercell: overshooting crown above the anvil.
        float canopyTop = yBase + H * (kind == Archetype.SUPERCELL ? .93f : 1.01f);
        float length = kind == Archetype.CAPILLATUS ? r * (1.9f + 1.1f * spread) : r * (2.4f + 1.2f * spread);
        float back = kind == Archetype.CAPILLATUS ? r * .55f : r * .95f; // supercell back-sheared overhang
        float dirAngle = (float) rng.nextDouble(-.12, .12) + side * (kind == Archetype.SUPERCELL ? .10f : .04f);
        float w0 = r * (kind == Archetype.CAPILLATUS ? .78f : .95f), w1 = r * (kind == Archetype.CAPILLATUS ? 1.25f + .40f * spread : 1.55f + .45f * spread);
        // Anvil depth is a fraction of the storm depth: thick root over the crown, thinning outward.
        float t0 = H * (kind == Archetype.CAPILLATUS ? .30f : .32f), t1 = H * (kind == Archetype.CAPILLATUS ? .11f : .13f);
        float lift = H * .035f, bend = r * (float) rng.nextDouble(-.35, .35) + side * r * .15f;
        float fibreAmp = kind == Archetype.CAPILLATUS ? .06f : .045f, fibreFreq = (float) (2 * Math.PI / (r * (float) rng.nextDouble(1.1, 1.4)));
        float edgeAmp = .10f, streakAmp = kind == Archetype.CAPILLATUS ? .24f : .18f;
        float streakFreq = (float) (2 * Math.PI / (r * .62f)), canopyPhase = (float) rng.nextDouble(0, 2 * Math.PI);
        float merge = r * .20f, baseRound = r * .22f;

        // Pack (texel = 4 floats).
        put(f, 0, r, H, yBase, kind.ordinal());
        put(f, 1, feather, turrets.size(), canopy ? 1 : 0, canopy ? 2 : 1);
        // texels 2-5 bounds, filled after
        put(f, 6, tiltX, tiltZ, shearX, shearZ);
        put(f, 7, curveX, curveZ, crownStart, baseRound);
        put(f, 8, r * rBase, r * rLow, r * rMid, r * rUp);
        put(f, 9, r * rCrown, merge, wallR, wallDepth);
        put(f, 10, aspectLow, aspectHigh, orientLow, orientHigh);
        put(f, 11, m1Low, m1High, d1Low, d1High);
        put(f, 12, m3Low, m3High, p3Low, p3High);
        put(f, 13, wallX, wallZ, billowAmp, billowPhase);
        put(f, 14, billowTheta, billowS, billowTop, 0);
        put(f, 15, rootX, canopyTop, rootZ, length);
        put(f, 16, (float) Math.cos(dirAngle), (float) Math.sin(dirAngle), back, bend);
        put(f, 17, w0, w1, t0, t1);
        put(f, 18, lift, fibreAmp, fibreFreq, edgeAmp);
        put(f, 19, streakAmp, streakFreq, canopyPhase, 0);
        // 20-21: derived bound helpers
        float turretMax = 0;
        for (var t : turrets) turretMax = Math.max(turretMax, t.amplitude());
        put(f, 20, turretMax, 0, 0, VERSION);
        float[] packed = new float[(HEADER_TEXELS + turrets.size()) * 4];
        System.arraycopy(f, 0, packed, 0, f.length);
        for (int i = 0; i < turrets.size(); i++) {
            var t = turrets.get(i);
            System.arraycopy(new float[]{t.y(), t.theta(), t.amplitude(), t.width()}, 0, packed, (HEADER_TEXELS + i) * 4, 4);
        }
        return new Program(kind, packed, turrets);
    }

    private static void put(float[] f, int texel, float a, float b, float c, float d) {
        f[texel * 4] = a; f[texel * 4 + 1] = b; f[texel * 4 + 2] = c; f[texel * 4 + 3] = d;
    }

    public static final class Program implements StormGeometryProgram {
        private final Archetype kind;
        private final float[] p;
        private final List<Turret> turrets;
        private final Bounds bodyBounds, canopyBounds, bounds;
        private final long signature;

        private Program(Archetype kind, float[] packed, List<Turret> turrets) {
            this.kind = kind;
            this.p = packed;
            this.turrets = List.copyOf(turrets);
            if (turrets.size() > MAX_TURRETS) throw new IllegalArgumentException("Turret table is bounded");
            // Outside a component box its distance must exceed feather/2 + merge, so the box is exact for density.
            float feather = v(1, 0), margin = feather + v(9, 1) + 2;
            // Body: spine extent plus the largest possible cross-section radius.
            float minX = 1e9f, maxX = -1e9f, minZ = 1e9f, maxZ = -1e9f;
            float gMax = 0;
            for (int i = 0; i <= 64; i++) {
                float s = i / 64f;
                float gx = v(6, 0) + 2 * v(6, 2) * s + 3 * v(7, 0) * s * s, gz = v(6, 1) + 2 * v(6, 3) * s + 3 * v(7, 1) * s * s;
                gMax = Math.max(gMax, gx * gx + gz * gz);
                minX = Math.min(minX, spineX(s)); maxX = Math.max(maxX, spineX(s));
                minZ = Math.min(minZ, spineZ(s)); maxZ = Math.max(maxZ, spineZ(s));
            }
            float rMax = Math.max(Math.max(v(8, 0), v(8, 1)), Math.max(Math.max(v(8, 2), v(8, 3)), v(9, 0)));
            // The horizontal section of a leaning tube is LONGER along the lean by sqrt(1+G).
            float reach = (rMax * radialBoundFactor() + v(20, 0) + BILLOW_MEAN * v(13, 2) * rMax) * stretchBound() * (float) Math.sqrt(1 + gMax) + margin;
            float wallDepth = v(9, 3);
            bodyBounds = new Bounds(minX - reach, v(0, 2) - wallDepth - margin, minZ - reach,
                    maxX + reach, v(0, 2) + v(0, 1) + margin, maxZ + reach);
            if (v(1, 2) > .5f) {
                float L = v(15, 3), back = v(16, 2), w = v(17, 1) * (1 + v(18, 3)) + Math.abs(v(16, 3)), ext = L * (1 + v(18, 1));
                float cx = v(16, 0), cz = v(16, 1);
                float ax = v(15, 0) + cx * ext, az = v(15, 2) + cz * ext, bx = v(15, 0) - cx * back, bz = v(15, 2) - cz * back;
                float wx = Math.abs(cz) * w, wz = Math.abs(cx) * w;
                float top = v(15, 1) + v(18, 0);
                canopyBounds = new Bounds(Math.min(ax, bx) - wx - margin, v(15, 1) - v(17, 2) * 1.55f - margin, Math.min(az, bz) - wz - margin,
                        Math.max(ax, bx) + wx + margin, top + margin, Math.max(az, bz) + wz + margin);
            } else {
                canopyBounds = null;
            }
            Bounds whole = bodyBounds;
            if (canopyBounds != null) whole = join(whole, canopyBounds);
            bounds = whole;
            put(p, 2, bodyBounds.minX(), bodyBounds.minY(), bodyBounds.minZ(), 0);
            put(p, 3, bodyBounds.maxX(), bodyBounds.maxY(), bodyBounds.maxZ(), 0);
            Bounds c = canopyBounds == null ? new Bounds(0, 0, 0, -1, -1, -1) : canopyBounds;
            put(p, 4, c.minX(), c.minY(), c.minZ(), 0);
            put(p, 5, c.maxX(), c.maxY(), c.maxZ(), 0);
            signature = hash(p);
        }

        private float v(int texel, int lane) { return p[texel * 4 + lane]; }
        public Archetype kind() { return kind; }
        public List<Turret> turrets() { return turrets; }
        public Bounds bodyBounds() { return bodyBounds; }
        public Bounds canopyBounds() { return canopyBounds; }
        public boolean hasCanopy() { return canopyBounds != null; }
        public float feather() { return v(1, 0); }
        public float radius() { return v(0, 0); }
        public float height() { return v(0, 1); }
        public float baseY() { return v(0, 2); }
        public int version() { return VERSION; }
        public boolean growthDefined() { return true; }
        public Bounds bounds() { return bounds; }
        public float[] packed() { return p.clone(); }
        public long signature() { return signature; }
        public float axisX(float t) { return spineX(clamp(t, 0, 1)); }

        public float spineX(float s) { float H = v(0, 1); return H * (v(6, 0) * s + v(6, 2) * s * s + v(7, 0) * s * s * s); }
        public float spineZ(float s) { float H = v(0, 1); return H * (v(6, 1) * s + v(6, 3) * s * s + v(7, 1) * s * s * s); }
        /** Upper bound of the angular-mode multiplier: 1 + |m1| + |m3|. */
        private float radialBoundFactor() { return 1 + Math.max(Math.abs(v(11, 0)), Math.abs(v(11, 1))) + Math.max(Math.abs(v(12, 0)), Math.abs(v(12, 1))); }
        /** Ellipse stretch bound: sqrt(max aspect). */
        private float stretchBound() { return (float) Math.sqrt(Math.max(v(10, 0), v(10, 1))); }

        /** Height profile R0(s) and its dome slope dR0/dy. */
        float profile(float s) {
            float rb = v(8, 0), rl = v(8, 1), rm = v(8, 2), ru = v(8, 3), rc = v(9, 0), sc = v(7, 2);
            if (s < .20f) return mix(rb, rl, smooth(0, .20f, s));
            if (s < .50f) return mix(rl, rm, smooth(.20f, .50f, s));
            if (s < .74f) return mix(rm, ru, smooth(.50f, .74f, s));
            if (s < sc) return mix(ru, rc, smooth(.74f, sc, s));
            float u = (s - sc) / (1 - sc);
            return rc * (float) Math.sqrt(Math.max(0, 1 - u * u));
        }
        float domeSlope(float s) {
            float sc = v(7, 2);
            if (s <= sc) return 0;
            float u = Math.min((s - sc) / (1 - sc), .9999f);
            return v(9, 0) * u / ((float) Math.sqrt(1 - u * u) * (1 - sc) * v(0, 1));
        }

        /**
         * Cauliflower creases in [0,1]: the max of two skewed crease lattices with different integer
         * azimuth frequencies and incommensurate vertical frequencies, so cells never tile into rings.
         */
        float billowCells(float theta, float s) {
            float n = v(14, 0), m = v(14, 1), ph = v(13, 3), pi = (float) Math.PI;
            float a = Math.abs((float) Math.sin(theta * n + 2.3f * s + ph) * (float) Math.sin(pi * s * m + theta + ph * 1.7f));
            float b = Math.abs((float) Math.sin(theta * (n + 3) - 3.1f * s + ph * 2.3f) * (float) Math.sin(pi * s * m * 1.37f - 2 * theta + ph * .6f));
            // sqrt(x + 0.01) keeps the crease but bounds its slope; normalised back to [0,1].
            return ((float) Math.sqrt(Math.max(a, b) + .01f) - .1f) / .90499f;
        }

        /** Body distance and a conservative lower bound gate (skip when far from the swept surface). */
        public float bodyDistance(float x, float y, float z, float gateCanopy, boolean gate) {
            float H = v(0, 1), yBase = v(0, 2), feather = v(1, 0), merge = v(9, 1);
            float s = clamp((y - yBase) / H, 0, 1);
            float qx = x - spineX(s), qz = z - spineZ(s);
            float gx = v(6, 0) + 2 * v(6, 2) * s + 3 * v(7, 0) * s * s;
            float gz = v(6, 1) + 2 * v(6, 3) * s + 3 * v(7, 1) * s * s;
            float G = gx * gx + gz * gz;
            float r0 = profile(s);
            if (gate) {
                // Trig-free pre-gate: stretched radius >= |q| / (sqrt(1+G) sqrt(maxAspect)).
                float rhoLb = (float) Math.sqrt(qx * qx + qz * qz) / ((float) Math.sqrt(1 + G) * stretchBound());
                float radUb = r0 * radialBoundFactor() + v(20, 0) + BILLOW_MEAN * v(13, 2) * r0;
                float side0 = rhoLb - radUb; // invLen >= 1/sqrt(10) shrinks only a positive bound
                float lb0 = Math.max(Math.max(side0 > 0 ? side0 * .3162278f : side0, y - (yBase + H)), yBase - v(9, 3) - y);
                float cut0 = feather * .5f + merge * .25f;
                if (lb0 >= gateCanopy + merge || (lb0 >= cut0 && gateCanopy >= cut0)) return 1e6f;
            }
            if (G > 1e-8f) {
                float inv = 1 / (float) Math.sqrt(G), nx = gx * inv, nz = gz * inv;
                float qa = (qx * nx + qz * nz) / (float) Math.sqrt(1 + G), qp = -nz * qx + nx * qz;
                qx = nx * qa - nz * qp; qz = nz * qa + nx * qp;
            }
            float t = smooth(.10f, .80f, s);
            float aspect = mix(v(10, 0), v(10, 1), t), orient = mix(v(10, 2), v(10, 3), t);
            float co = (float) Math.cos(orient), so = (float) Math.sin(orient);
            float u = qx * co + qz * so, w = -qx * so + qz * co, sa = (float) Math.sqrt(aspect);
            float rho = (float) Math.sqrt((u / sa) * (u / sa) + (w * sa) * (w * sa));
            float theta = (float) Math.atan2(qz, qx);
            float slope = Math.min(domeSlope(s), 3);
            float invLen = 1 / (float) Math.sqrt(1 + slope * slope);
            float tm = smooth(.15f, .85f, s);
            float m = 1 + mix(v(11, 0), v(11, 1), tm) * (float) Math.cos(theta - mix(v(11, 2), v(11, 3), tm))
                    + mix(v(12, 0), v(12, 1), tm) * (float) Math.cos(3 * theta - mix(v(12, 2), v(12, 3), tm));
            float rad = r0 * m;
            float fade = 1 - smooth(.90f, 1.0f, s);
            float billowBand = smooth(.08f, .30f, s) * (1 - smooth(v(14, 2) - .14f, v(14, 2), s));
            float billowMax = BILLOW_MEAN * v(13, 2) * r0 * billowBand;
            float wallDepth = v(9, 3);
            float top = y - (yBase + H);
            if (gate) {
                float lb = Math.max(Math.max((rho - rad - v(20, 0) * fade - billowMax) * invLen, top), yBase - wallDepth - y);
                float cut = feather * .5f + merge * .25f;
                if (lb >= gateCanopy + merge || (lb >= cut && gateCanopy >= cut)) return 1e6f;
            }
            float turret = 0;
            int count = (int) (v(1, 1) + .5f);
            for (int i = 0; i < count; i++) {
                int o = (HEADER_TEXELS + i) * 4;
                float dy = (y - p[o]) / (p[o + 3] * .85f);
                if (Math.abs(dy) > 3) continue;
                float dt = wrap(theta - p[o + 1]) * r0 / p[o + 3];
                // Max, not sum: overlapping turrets never build a bulge larger than one turret.
                turret = Math.max(turret, p[o + 2] * (float) Math.exp(-dt * dt - dy * dy));
            }
            float billow = v(13, 2) * r0 * billowBand * (billowCells(theta, s) - BILLOW_MEAN);
            rad += turret * fade + billow;
            float dSide = (rho - rad) * invLen;
            float wx = x - v(13, 0), wz = z - v(13, 1), wr = v(9, 2);
            float baseY = yBase - wallDepth * (float) Math.exp(-(wx * wx + wz * wz) / (wr * wr));
            float d = smax(dSide, baseY - y, v(7, 3));
            return Math.max(d, top);
        }

        /**
         * Independent canopy sheet: fan planform, curved, thinning outward, fibrous edges.
         * gate: cheap lower bound first; at or beyond feather/2 + 1.25 merge the true value cannot
         * change the union's density or the body gate, so 1e6 is returned (exact for density).
         */
        public float canopyDistance(float x, float y, float z) { return canopyDistance(x, y, z, true); }
        public float canopyDistance(float x, float y, float z, boolean gate) {
            if (canopyBounds == null || !canopyBounds.contains(x, y, z)) return 1e6f;
            float L = v(15, 3), back = v(16, 2), bend = v(16, 3);
            float dx = x - v(15, 0), dz = z - v(15, 2);
            float a = dx * v(16, 0) + dz * v(16, 1);
            float an = clamp(a / L, 0, 1);
            float c = -dx * v(16, 1) + dz * v(16, 0) - bend * an * an;
            float w0 = v(17, 0), w1 = v(17, 1);
            float width = mix(w0, w1, (float) Math.pow(an, .7f));
            float phase = v(19, 2);
            if (gate) {
                float topMax = v(15, 1) + v(18, 0) * smooth(0, 1, an);
                float thickMax = mix(v(17, 2), v(17, 3), (float) Math.pow(an, .85f)) * (1 + .55f * (1 - smooth(0, .30f, an)));
                float lb = Math.max(y - topMax, topMax - thickMax - y);
                if (a >= 0 && a <= .55f * L) lb = Math.max(lb, Math.abs(c) - width * (1 + v(18, 3)));
                if (lb >= feather() * .5f + 1.25f * v(9, 1)) return 1e6f;
            }
            width *= 1 + v(18, 3) * (float) Math.sin(a * 2.1f / L * (float) Math.PI + phase) * an;
            float ff = v(18, 2);
            float fib = .5f * (float) Math.sin(c * ff + phase) + .3f * (float) Math.sin(c * ff * 2.37f + 1.3f * phase)
                    + .2f * (float) Math.sin(c * ff * 4.13f + 2.1f * phase + 1.7f * (float) Math.sin(c * ff * .61f));
            float lEff = L * (1 + v(18, 1) * fib);
            float tipStart = .55f * L;
            // Rounded downwind tip and upwind overhang as ellipse metrics: continuous with |c|-width and
            // without the infinite cross-fan slope a sqrt taper has at its end.
            float dp = a > tipStart ? ellipse(a - tipStart, c, Math.max(lEff - tipStart, 1), width)
                    : a < 0 ? ellipse(a, c, back, width) : Math.abs(c) - width;
            float topY = v(15, 1) + v(18, 0) * smooth(0, 1, an);
            float cw = clamp(Math.abs(c) / Math.max(width, 1), 0, 1);
            // Thick body holds most of the way out; the root flares down into the tower (crown grows into the canopy).
            float flare = 1 + .55f * (1 - smooth(0, .30f, an)) * (1 - cw * cw);
            float thick = mix(v(17, 2), v(17, 3), (float) Math.pow(an, .85f)) * (1 - .40f * cw * cw) * flare;
            float streak = .5f + .5f * (float) Math.sin(c * v(19, 1) + phase + .9f * (float) Math.sin(a * 3.3f / L));
            float bottom = topY - thick + thick * v(19, 0) * streak * smooth(.15f, .6f, an);
            float dv = Math.max(y - topY, bottom - y);
            return smax(dp, dv, Math.max(4, thick * .5f));
        }

        public float distance(float x, float y, float z) {
            float c = canopyDistance(x, y, z);
            float b = bodyBounds.contains(x, y, z) ? bodyDistance(x, y, z, c, true) : 1e6f;
            return smin(b, c, v(9, 1));
        }
        /** Ungated reference: identical field without bounds or the lower-bound skip. */
        public float referenceDistance(float x, float y, float z) {
            float c = canopyBounds == null ? 1e6f : canopyDistance(x, y, z, false);
            return smin(bodyDistance(x, y, z, c, false), c, v(9, 1));
        }
        public float density(float x, float y, float z, Scale scale) {
            if (!bounds.contains(x, y, z)) return 0;
            return clamp(.5f - distance(x, y, z) / feather(), 0, 1);
        }
    }

    private static Bounds join(Bounds a, Bounds b) { return new Bounds(Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()), Math.min(a.minZ(), b.minZ()), Math.max(a.maxX(), b.maxX()), Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ())); }
    private static long hash(float[] p) { long hash = 0xcbf29ce484222325L; for (float v : p) hash = (hash ^ Float.floatToIntBits(v)) * 0x100000001b3L; return hash; }
    static float smin(float a, float b, float k) { float t = Math.max(k - Math.abs(a - b), 0) / k; return Math.min(a, b) - t * t * k * .25f; }
    static float smax(float a, float b, float k) { float t = Math.max(k - Math.abs(a - b), 0) / k; return Math.max(a, b) + t * t * k * .25f; }
    static float ellipse(float a, float b, float ra, float rb) { float norm = (float) Math.sqrt((a / ra) * (a / ra) + (b / rb) * (b / rb)); return norm == 0 ? -Math.min(ra, rb) : (norm - 1) * (float) Math.sqrt(a * a + b * b) / norm; }
    static float wrap(float a) { return a - (float) (2 * Math.PI) * (float) Math.floor(a / (float) (2 * Math.PI) + .5f); }
    private static float smooth(float a, float b, float v) { float t = clamp((v - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }
    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
