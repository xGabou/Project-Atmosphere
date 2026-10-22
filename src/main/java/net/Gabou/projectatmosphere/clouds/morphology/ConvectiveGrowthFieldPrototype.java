package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import static net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.*;

/**
 * T257 candidate B: continuous convective growth field. Experimental fixture geometry only; no
 * production, save or network authority. GPU mirror: cloud_convective_growth_field.glsl.
 *
 * Candidate A (ConvectiveGrowthFieldA) was rejected as a smooth vertical blob with a thin spike:
 * proportions came from the legacy radius (tower W/H 0.45), turrets were Gaussian swells of 9-14 px
 * at gallery scale, the crown was one smooth dome and the canopy an edge-on plank. B sizes everything
 * from the storm depth H using photographic proportions (base and body about as wide as the tower is
 * tall, anvil ~0.4 H thick at the tower), and builds the cauliflower from LARGE hemispherical surface
 * displacements on two continuous surfaces: the swept column (side turrets) and an ellipsoidal crown
 * cap (crown heads, which break the top silhouette). The anvil is an independent broad sheet whose
 * underside descends into the upper tower over ~one H. Evaluation is constant cost: one spine, one
 * cross section, one crown, bounded turret tables, one canopy.
 */
public final class ConvectiveGrowthFieldPrototype {
    public static final int VERSION = 3;
    public static final int MAX_TURRETS = 16;
    static final int HEADER_TEXELS = 24;
    /**
     * Baked surface-displacement map (rows 1..MAP_H of the fixture texture): R = column displacement
     * over (azimuth, s), G = crown displacement over (azimuth, elevation). Every head field depends only
     * on those two surface coordinates, so one bilinear lookup replaces ~27 head evaluations per sample.
     */
    public static final int MAP_W = 512, MAP_H = 256;
    /**
     * Conservative occupancy grid over the whole bounds (GRID^3 voxels, four per texel, rows after the
     * map). A voxel is empty only if the full field at its centre minus GRID_LIPSCHITZ x half-diagonal
     * is at least feather/2: then no point inside can have density. Empty samples cost one fetch.
     */
    public static final int GRID = 64, GRID_ROWS = GRID * GRID * GRID / 4 / MAP_W;
    public static final float GRID_LIPSCHITZ = 6.0f;
    private ConvectiveGrowthFieldPrototype() {}

    /** Side turret: radial hemispherical displacement of the swept column (blocks). */
    public record Turret(float y, float theta, float amplitude, float width) {}
    /** Crown head: displacement of the crown cap around unit direction (ux,uy,uz), amplitude in radii. */
    public record CrownHead(float ux, float uy, float uz, float amplitude) {}

    /**
     * T257 diagnostic decomposition at identical seeds and cameras. FULL is candidate B v3 unchanged
     * (bit-identical fixture texture). MESO keeps the core plus the mesoscale heads and shoulders: side
     * turrets and flanking line, crown heads, the first head-cell octave and the canopy's planform edge
     * modes. CORE keeps only the swept body (sections, modes, wall lowering), the crown cap ellipsoid and
     * the plain canopy planform. Every random draw is identical; only displacements are withheld.
     */
    public enum Detail { FULL, MESO, CORE }

    public static Program compile(String type, CloudShapeProfile shape, long seed) {
        return compile(type, shape, seed, Detail.FULL);
    }

    public static Program compile(String type, CloudShapeProfile shape, long seed, Detail detail) {
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
        float h = shape.getTopOffset() * 4, base = shape.getBaseOffset() * 2;
        float tilt = shape.getVerticalTilt(), shear = shape.getWindShearStrength(), narrow = shape.getTowerNarrowing();
        float spread = shape.getAnvilSpread(), strength = shape.getLobeStrength(), wall = shape.getStormWallStrength();
        float split = shape.getCellSplitStrength();
        var rng = new SplittableRandom(seed);
        boolean sup = kind == Archetype.SUPERCELL, cal = kind == Archetype.CALVUS, cap = kind == Archetype.CAPILLATUS;
        float H = h * (cal ? 1.55f : cap ? 1.26f : 1.16f), yBase = -base;
        float side = rng.nextBoolean() ? 1 : -1;
        float lean = sup ? .75f * tilt + .30f * shear : .30f * tilt + .08f * shear;
        float shearBend = (sup ? .14f : .08f) * shear;
        float tiltX = lean - shearBend * .5f, shearX = shearBend, curveX = (float) rng.nextDouble(-.02, .02);
        float bow = sup ? .09f : .02f;
        float tiltZ = side * bow * (float) rng.nextDouble(.6, 1.0), shearZ = -tiltZ * .6f, curveZ = (float) rng.nextDouble(-.01, .01);

        // Half-widths as fractions of H (photographic proportions: tower width ~0.8-1.0 H).
        float k0, k1, k2, k3, sc, crownR, crownH;
        if (cal) { k0 = .44f; k1 = .42f; k2 = .35f; k3 = .29f; sc = .78f; crownR = .31f; crownH = .24f; }
        else if (cap) { k0 = .55f; k1 = .52f; k2 = .45f; k3 = .40f; sc = .78f; crownR = .42f; crownH = .20f; }
        else { k0 = .60f; k1 = .57f; k2 = .47f; k3 = .41f; sc = .80f; crownR = .38f; crownH = .31f; } // tall overshooting crown
        k2 -= .04f * narrow; k3 -= .04f * narrow; crownR -= .03f * narrow;
        float flank = sup ? (float) Math.atan2(side * .35f, -1f) + (float) rng.nextDouble(-.12, .12)
                : (float) rng.nextDouble(-Math.PI, Math.PI);
        float aspectLow = sup ? 1.40f + .20f * split : 1.10f + .10f * split, aspectHigh = 1.05f;
        float orientLow = flank, orientHigh = sup ? 0f : flank + (float) rng.nextDouble(-.6, .6);
        float m1Low = sup ? .14f : .05f, m1High = cal ? .05f : .08f;
        float d1Low = flank, d1High = (float) rng.nextDouble(-.25, .25);
        float m3Low = .04f + .02f * strength, m3High = .06f * (.6f + .5f * strength);
        float p3Low = (float) rng.nextDouble(0, 2 * Math.PI), p3High = p3Low + (float) rng.nextDouble(1.2, 2.6);
        float wallDepth = sup ? H * .10f * wall : 0, wallR = H * .18f;
        float wallX = (float) Math.cos(flank) * H * .10f, wallZ = (float) Math.sin(flank) * H * .10f;
        float feather = Math.max(2, H * .02f), kBase = H * .03f, kCrown = H * .10f, merge = H * .16f; // soft tower-to-anvil shoulder

        // Side turrets: large heads stacked up the tower; each top stays below the crown centre.
        List<Turret> sideTurrets = new ArrayList<>();
        int sideCount = cal ? 8 : cap ? 7 : 4;
        float sLo = sup ? .40f : .26f;
        for (int i = 0; i < sideCount; i++) {
            float w = H * (float) rng.nextDouble(.15, .21);
            float sMax = sc - .9f * w / H;
            float s = sLo + (sMax - sLo) * (i + (float) rng.nextDouble(.1, .9)) / sideCount;
            float theta = sup ? flank + (float) (i * 1.7 - 2.6 + rng.nextDouble(-.3, .3))
                    : (float) (i * 2.39996323 + rng.nextDouble(-.4, .4));
            float amp = H * (float) rng.nextDouble(.07, .12) * (.8f + .3f * strength);
            sideTurrets.add(new Turret(yBase + H * s, theta, amp, w));
        }
        if (sup) {
            // Flanking line: heads stepping down and outward along the elongated inflow flank.
            for (int i = 0; i < 4; i++) {
                float w = H * (.24f - .02f * i); // wide heads: the stretched flank section amplifies their slope
                float s = .40f - .085f * i + (float) rng.nextDouble(-.015, .015);
                sideTurrets.add(new Turret(yBase + H * s, flank + (float) rng.nextDouble(-.10, .10), H * (.12f - .012f * i), w));
            }
        }
        // Crown heads: rounded turrets on the crown cap, biased to the upper hemisphere.
        List<CrownHead> crown = new ArrayList<>();
        int crownCount = cal ? 6 : cap ? 5 : 4;
        float crownAmpLo = cal ? .16f : .10f, crownAmpHi = cal ? .26f : .18f;
        for (int i = 0; i < crownCount; i++) {
            float az = (float) (i * 2.39996323 + rng.nextDouble(-.5, .5));
            float el = i == 0 ? (float) rng.nextDouble(1.2, 1.45) : (float) rng.nextDouble(.25, 1.15); // one head near the summit
            crown.add(new CrownHead((float) (Math.cos(el) * Math.cos(az)), (float) Math.sin(el), (float) (Math.cos(el) * Math.sin(az)),
                    (float) rng.nextDouble(crownAmpLo, crownAmpHi)));
        }
        float crownSize = cal ? .62f : .70f;

        // Canopy: broad independent sheet anchored over the upper spine.
        boolean canopy = !cal;
        float rootX = H * (tiltX * sc + shearX * sc * sc + curveX * sc * sc * sc);
        float rootZ = H * (tiltZ * sc + shearZ * sc * sc + curveZ * sc * sc * sc);
        float topY = yBase + H * (cap ? .99f : .90f);
        float length = H * (cap ? 1.42f + .20f * spread : 1.85f + .30f * spread), back = H * (cap ? .45f : .40f);
        float w0 = H * (cap ? .55f : .60f), w1 = H * (cap ? .80f + .10f * spread : .95f + .10f * spread);
        float topWave = H * (cap ? .035f : .025f);
        float tRoot = H * (cap ? .65f : .60f), tEdge = H * (cap ? .38f : .34f), lFall = H * (cap ? 1.0f : 1.25f);
        // lift < 0: the anvil top arches down toward its leading edge (mushroom curvature, not a beam).
        float dome = H * .05f, lift = -H * .07f, edgeAmp = cap ? .13f : .11f;
        float fibreAmp = cap ? .07f : .05f, fibreFreq = (float) (2 * Math.PI / (H * (float) rng.nextDouble(.34, .44)));
        float streakAmp = cap ? .12f : .09f, streakFreq = (float) (2 * Math.PI / (H * .22f));
        float dirAngle = (float) rng.nextDouble(-.12, .12) + side * (sup ? .10f : .04f);
        float bend = H * (float) rng.nextDouble(-.12, .12) + side * H * (sup ? .16f : .05f), phase = (float) rng.nextDouble(0, 2 * Math.PI);

        // Cellular cauliflower: jittered head cells tile the whole tower surface (azimuth x height) and the
        // crown cap (azimuth x elevation), so every view's limb carries heads with creases between them.
        float cellH = H * (cal ? .22f : cap ? .20f : .19f);
        float cellAmp = H * (cal ? .14f : .11f) * (.8f + .3f * strength);
        float midR = H * (k1 + k2) * .5f;
        float cellN = Math.max(5, Math.round((float) (2 * Math.PI * midR / cellH)));
        float crownCellAmp = cal ? .30f : cap ? .20f : .24f; // fraction of the crown radius
        float crownCellsAz = cal ? 6 : 7, crownCellsEl = 3;
        float cellSeed = (float) (rng.nextInt(1 << 20));
        // Second, smaller head octave riding on the first: multiscale cauliflower, not one tier of heads.
        float cellAmp2 = cellAmp * .18f, crownCellAmp2 = crownCellAmp * .22f; // small heads: shallow enough to keep the field Lipschitz
        float sideMax = 0, crownMax = 0;
        for (var t : sideTurrets) sideMax = Math.max(sideMax, t.amplitude());
        for (var c : crown) crownMax = Math.max(crownMax, c.amplitude());
        var f = new float[(HEADER_TEXELS + sideTurrets.size() + crown.size()) * 4];
        put(f, 0, H, yBase, kind.ordinal(), feather);
        put(f, 1, sideTurrets.size(), crown.size(), canopy ? 1 : 0, canopy ? 2 : 1);
        put(f, 6, tiltX, tiltZ, shearX, shearZ);
        put(f, 7, curveX, curveZ, sc, kBase);
        put(f, 8, H * k0, H * k1, H * k2, H * k3);
        put(f, 9, H * crownR, H * crownH, kCrown, merge);
        put(f, 10, aspectLow, aspectHigh, orientLow, orientHigh);
        put(f, 11, m1Low, m1High, d1Low, d1High);
        put(f, 12, m3Low, m3High, p3Low, p3High);
        put(f, 13, wallX, wallZ, wallR, wallDepth);
        put(f, 14, sideMax, crownMax, crownSize, VERSION);
        put(f, 15, rootX, topY, rootZ, length);
        put(f, 16, (float) Math.cos(dirAngle), (float) Math.sin(dirAngle), back, bend);
        put(f, 17, w0, w1, tRoot, tEdge);
        put(f, 18, lFall, dome, lift, edgeAmp);
        put(f, 19, fibreAmp, fibreFreq, streakAmp, streakFreq);
        put(f, 20, phase, topWave, 0, 0);
        put(f, 21, cellN, cellH, cellAmp, .58f * cellH); // radius < spacing: deep creases between heads
        put(f, 22, crownCellsAz, crownCellsEl, crownCellAmp, cellSeed);
        put(f, 23, cellAmp2, crownCellAmp2, 0, 0);
        int o = HEADER_TEXELS;
        for (var t : sideTurrets) put(f, o++, t.y(), t.theta(), t.amplitude(), t.width());
        for (var c : crown) put(f, o++, c.ux(), c.uy(), c.uz(), c.amplitude());
        if (detail != Detail.FULL) {
            // Diagnostic arms only; the level code also separates the texture signature from FULL.
            f[23 * 4 + 2] = detail.ordinal();
            f[19 * 4] = 0; f[19 * 4 + 2] = 0; f[20 * 4 + 1] = 0; // fibres, underside streaks, top wave
            if (detail == Detail.CORE) f[18 * 4 + 3] = 0; // canopy planform edge modes
        }
        return new Program(kind, f, sideTurrets, crown);
    }

    private static void put(float[] f, int texel, float a, float b, float c, float d) {
        f[texel * 4] = a; f[texel * 4 + 1] = b; f[texel * 4 + 2] = c; f[texel * 4 + 3] = d;
    }

    public static final class Program implements StormGeometryProgram {
        private final Archetype kind;
        private final float[] p;
        private final List<Turret> turrets;
        private final List<CrownHead> crown;
        private final Bounds bodyBounds, canopyBounds, bounds;
        private final long signature;
        private final float[] texture;

        private Program(Archetype kind, float[] packed, List<Turret> turrets, List<CrownHead> crown) {
            this.kind = kind;
            this.p = packed;
            this.turrets = List.copyOf(turrets);
            this.crown = List.copyOf(crown);
            if (turrets.size() + crown.size() > MAX_TURRETS) throw new IllegalArgumentException("Turret tables are bounded");
            // Outside a component box its distance exceeds feather/2 + merge: the box is exact for density.
            float margin = feather() + v(9, 3) + 2;
            float minX = 1e9f, maxX = -1e9f, minZ = 1e9f, maxZ = -1e9f, gMax = 0;
            for (int i = 0; i <= 64; i++) {
                float s = i / 64f;
                minX = Math.min(minX, spineX(s)); maxX = Math.max(maxX, spineX(s));
                minZ = Math.min(minZ, spineZ(s)); maxZ = Math.max(maxZ, spineZ(s));
                float gx = v(6, 0) + 2 * v(6, 2) * s + 3 * v(7, 0) * s * s, gz = v(6, 1) + 2 * v(6, 3) * s + 3 * v(7, 1) * s * s;
                gMax = Math.max(gMax, gx * gx + gz * gz);
            }
            float rMax = Math.max(Math.max(v(8, 0), v(8, 1)), Math.max(v(8, 2), v(8, 3)));
            float reach = (rMax * radialBoundFactor() + columnDisplacementMax()) * (float) Math.sqrt(Math.max(v(10, 0), v(10, 1))) * (float) Math.sqrt(1 + gMax);
            float crownReach = v(9, 0) * (1 + crownDisplacementMax());
            float sc = v(7, 2), cx = spineX(sc), cz = spineZ(sc), yc = baseY() + height() * sc;
            bodyBounds = new Bounds(Math.min(minX - reach, cx - crownReach) - margin, baseY() - v(13, 3) - margin,
                    Math.min(minZ - reach, cz - crownReach) - margin,
                    Math.max(maxX + reach, cx + crownReach) + margin, yc + v(9, 1) * (1 + crownDisplacementMax()) + margin,
                    Math.max(maxZ + reach, cz + crownReach) + margin);
            if (v(1, 2) > .5f) {
                float L = v(15, 3), back = v(16, 2), w = v(17, 1) * (1 + v(18, 3)) + Math.abs(v(16, 3)), ext = L * (1 + v(19, 0));
                float dx = v(16, 0), dz = v(16, 1);
                float ax = v(15, 0) + dx * ext, az = v(15, 2) + dz * ext, bx = v(15, 0) - dx * back, bz = v(15, 2) - dz * back;
                float wx = Math.abs(dz) * w, wz = Math.abs(dx) * w;
                float top = v(15, 1) + v(18, 1) + Math.max(0, v(18, 2)) + v(20, 1);
                canopyBounds = new Bounds(Math.min(ax, bx) - wx - margin, v(15, 1) + Math.min(0, v(18, 2)) - v(20, 1) - v(17, 2) - margin, Math.min(az, bz) - wz - margin,
                        Math.max(ax, bx) + wx + margin, top + margin, Math.max(az, bz) + wz + margin);
            } else {
                canopyBounds = null;
            }
            bounds = canopyBounds == null ? bodyBounds : join(bodyBounds, canopyBounds);
            put(p, 2, bodyBounds.minX(), bodyBounds.minY(), bodyBounds.minZ(), 0);
            put(p, 3, bodyBounds.maxX(), bodyBounds.maxY(), bodyBounds.maxZ(), 0);
            Bounds c = canopyBounds == null ? new Bounds(0, 0, 0, -1, -1, -1) : canopyBounds;
            put(p, 4, c.minX(), c.minY(), c.minZ(), 0);
            put(p, 5, c.maxX(), c.maxY(), c.maxZ(), 0);
            signature = hash(p);
            texture = new float[MAP_W * (MAP_H + 1 + GRID_ROWS) * 4];
            System.arraycopy(p, 0, texture, 0, p.length);
            float H = height(), yBase = baseY();
            for (int j = 0; j < MAP_H; j++) {
                float s = j / (float) (MAP_H - 1), el = -(float) Math.PI * .5f + (float) Math.PI * j / (MAP_H - 1);
                float r0 = profile(s), y = yBase + H * s, ce = (float) Math.cos(el), se = (float) Math.sin(el);
                for (int i = 0; i < MAP_W; i++) {
                    float theta = -(float) Math.PI + 2 * (float) Math.PI * i / MAP_W;
                    int o = ((j + 1) * MAP_W + i) * 4;
                    texture[o] = columnDisplacement(theta, y, s, r0);
                    texture[o + 1] = crownDisplacement(ce * (float) Math.cos(theta), se, ce * (float) Math.sin(theta));
                }
            }
            bakeGrid();
        }
        @Override public float[] textureData() { return texture.clone(); }
        @Override public int textureWidth() { return MAP_W; }
        @Override public int textureHeight() { return MAP_H + 1 + GRID_ROWS; }

        private void bakeGrid() {
            var b = bounds;
            float sx = (b.maxX() - b.minX()) / GRID, sy = (b.maxY() - b.minY()) / GRID, sz = (b.maxZ() - b.minZ()) / GRID;
            float half = .5f * (float) Math.sqrt(sx * sx + sy * sy + sz * sz), cut = feather() * .5f;
            for (int k = 0; k < GRID; k++) for (int j = 0; j < GRID; j++) for (int i = 0; i < GRID; i++) {
                float d = referenceDistance(b.minX() + (i + .5f) * sx, b.minY() + (j + .5f) * sy, b.minZ() + (k + .5f) * sz);
                int voxel = (k * GRID + j) * GRID + i;
                texture[(MAP_W * (MAP_H + 1)) * 4 + voxel] = d - GRID_LIPSCHITZ * half >= cut ? 0 : 1;
            }
        }
        /** Grid lookup (GLSL mirror): false = the voxel is certainly empty. */
        boolean gridOccupied(float x, float y, float z) {
            var b = bounds;
            int i = Math.min(GRID - 1, Math.max(0, (int) Math.floor((x - b.minX()) / (b.maxX() - b.minX()) * GRID)));
            int j = Math.min(GRID - 1, Math.max(0, (int) Math.floor((y - b.minY()) / (b.maxY() - b.minY()) * GRID)));
            int k = Math.min(GRID - 1, Math.max(0, (int) Math.floor((z - b.minZ()) / (b.maxZ() - b.minZ()) * GRID)));
            return texture[(MAP_W * (MAP_H + 1)) * 4 + (k * GRID + j) * GRID + i] > .5f;
        }

        /** Bilinear lookup, wrapping in azimuth and clamping in the second coordinate (GLSL mirror). */
        float sampleMap(int lane, float theta, float v01) {
            float fx = (theta + (float) Math.PI) / (2 * (float) Math.PI) * MAP_W, fy = clamp(v01, 0, 1) * (MAP_H - 1);
            int x0 = (int) Math.floor(fx), y0 = Math.min((int) Math.floor(fy), MAP_H - 2);
            float tx = fx - x0, ty = fy - y0;
            int xa = Math.floorMod(x0, MAP_W), xb = Math.floorMod(x0 + 1, MAP_W);
            float a = texture[((y0 + 1) * MAP_W + xa) * 4 + lane], b = texture[((y0 + 1) * MAP_W + xb) * 4 + lane];
            float c = texture[((y0 + 2) * MAP_W + xa) * 4 + lane], d = texture[((y0 + 2) * MAP_W + xb) * 4 + lane];
            return mix(mix(a, b, tx), mix(c, d, tx), ty);
        }

        private float v(int texel, int lane) { return p[texel * 4 + lane]; }
        public Detail detail() { return Detail.values()[(int) v(23, 2)]; }
        /** Packed parameter (diagnostic readers only). */
        float param(int texel, int lane) { return v(texel, lane); }
        public Archetype kind() { return kind; }
        public List<Turret> turrets() { return turrets; }
        public List<CrownHead> crownHeads() { return crown; }
        public Bounds bodyBounds() { return bodyBounds; }
        public Bounds canopyBounds() { return canopyBounds; }
        public boolean hasCanopy() { return canopyBounds != null; }
        public float feather() { return v(0, 3); }
        public float radius() { return v(8, 0); }
        public float height() { return v(0, 0); }
        public float baseY() { return v(0, 1); }
        public int version() { return VERSION; }
        public boolean growthDefined() { return true; }
        public Bounds bounds() { return bounds; }
        public float[] packed() { return p.clone(); }
        public long signature() { return signature; }
        public float axisX(float t) { return spineX(clamp(t, 0, 1)); }
        public float spineX(float s) { float H = v(0, 0); return H * (v(6, 0) * s + v(6, 2) * s * s + v(7, 0) * s * s * s); }
        public float spineZ(float s) { float H = v(0, 0); return H * (v(6, 1) * s + v(6, 3) * s * s + v(7, 1) * s * s * s); }
        /** Upper bounds of the surface displacements (column blocks, crown radii). */
        float columnDisplacementMax() { return Math.max(v(14, 0), v(21, 2)) + v(23, 0); }
        float crownDisplacementMax() { return Math.max(v(14, 1), v(22, 2)) + v(23, 1); }
        private float radialBoundFactor() { return 1 + Math.max(Math.abs(v(11, 0)), Math.abs(v(11, 1))) + Math.max(Math.abs(v(12, 0)), Math.abs(v(12, 1))); }

        float profile(float s) {
            if (s < .20f) return mix(v(8, 0), v(8, 1), smooth(0, .20f, s));
            if (s < .50f) return mix(v(8, 1), v(8, 2), smooth(.20f, .50f, s));
            return mix(v(8, 2), v(8, 3), smooth(.50f, .74f, s));
        }

        /** Swept column with side turrets, flat base and wall-cloud lowering; capped at the crown centre. */
        public float columnDistance(float x, float y, float z) {
            float H = v(0, 0), yBase = v(0, 1), sc = v(7, 2);
            float s = clamp((y - yBase) / H, 0, 1);
            float qx = x - spineX(s), qz = z - spineZ(s);
            float gx = v(6, 0) + 2 * v(6, 2) * s + 3 * v(7, 0) * s * s;
            float gz = v(6, 1) + 2 * v(6, 3) * s + 3 * v(7, 1) * s * s;
            float G = gx * gx + gz * gz;
            if (G > 1e-8f) {
                float inv = 1 / (float) Math.sqrt(G), nx = gx * inv, nz = gz * inv;
                float qa = (qx * nx + qz * nz) / (float) Math.sqrt(1 + G), qp = -nz * qx + nx * qz;
                qx = nx * qa - nz * qp; qz = nz * qa + nx * qp;
            }
            float t = smooth(.10f, .70f, s);
            float aspect = mix(v(10, 0), v(10, 1), t), orient = mix(v(10, 2), v(10, 3), t);
            float co = (float) Math.cos(orient), so = (float) Math.sin(orient);
            float u = qx * co + qz * so, w = -qx * so + qz * co, sa = (float) Math.sqrt(aspect);
            float rho = (float) Math.sqrt((u / sa) * (u / sa) + (w * sa) * (w * sa));
            float theta = (float) Math.atan2(qz, qx);
            float r0 = profile(s);
            float tm = smooth(.15f, .85f, s);
            float m = 1 + mix(v(11, 0), v(11, 1), tm) * (float) Math.cos(theta - mix(v(11, 2), v(11, 3), tm))
                    + mix(v(12, 0), v(12, 1), tm) * (float) Math.cos(3 * theta - mix(v(12, 2), v(12, 3), tm));
            float turret = sampleMap(0, theta, s);
            float dSide = rho - (r0 * m + turret);
            float wx = x - v(13, 0), wz = z - v(13, 1), wr = v(13, 2);
            float baseY = yBase - v(13, 3) * (float) Math.exp(-(wx * wx + wz * wz) / (wr * wr));
            float d = smax(dSide, baseY - y, v(7, 3));
            return Math.max(d, y - (yBase + H * sc));
        }

        /** Analytic column displacement (baked into the map): turret table, then two head octaves. */
        float columnDisplacement(float theta, float y, float s, float r0) {
            if (detail() == Detail.CORE) return 0;
            float yBase = baseY(), sc = v(7, 2), turret = 0;
            int count = (int) (v(1, 0) + .5f);
            for (int i = 0; i < count; i++) {
                int o = (HEADER_TEXELS + i) * 4;
                float dy = (y - p[o]) / (p[o + 3] * .9f);
                if (Math.abs(dy) >= 1) continue;
                float da = wrap(theta - p[o + 1]) * r0 / p[o + 3];
                turret = Math.max(turret, p[o + 2] * hemi(da * da + dy * dy));
            }
            float band = smooth(.04f, .30f, s) * (1 - smooth(sc - .14f, sc - .01f, s)); // soft ramps keep the vertical slope bounded
            if (band > 0) {
                float n = v(21, 0), cellH = v(21, 1);
                int seed = (int) v(22, 3);
                turret = Math.max(turret, band * columnCells(theta, y - yBase, r0, n, cellH, v(21, 2), v(21, 3), seed));
                if (detail() == Detail.FULL) turret += band * columnCells(theta, y - yBase, r0, 2 * n, .5f * cellH, v(23, 0), .5f * v(21, 3), seed ^ 0x27d4eb2d);
            }
            return turret;
        }

        /** Analytic crown displacement (baked into the map) at unit direction u; crown radii. */
        float crownDisplacement(float ux, float uy, float uz) {
            if (detail() == Detail.CORE) return 0;
            float D = 0, size = v(14, 2);
            int start = HEADER_TEXELS + (int) (v(1, 0) + .5f), count = (int) (v(1, 1) + .5f);
            for (int i = 0; i < count; i++) {
                int o = (start + i) * 4;
                float cx = ux - p[o], cy = uy - p[o + 1], cz = uz - p[o + 2];
                float t2 = (cx * cx + cy * cy + cz * cz) / (size * size);
                if (t2 >= 1) continue;
                D = Math.max(D, p[o + 3] * hemi(t2));
            }
            int seed = (int) v(22, 3) ^ 0x5bd1e995;
            D = Math.max(D, crownCells(ux, uy, uz, v(22, 0), v(22, 1), v(22, 2), seed));
            if (detail() != Detail.FULL) return D;
            return D + crownCells(ux, uy, uz, 2 * v(22, 0), 2 * v(22, 1), v(23, 1), seed ^ 0x27d4eb2d);
        }

        /** Ellipsoidal crown cap displaced by rounded heads (surface displacement, not solids). */
        public float crownDistance(float x, float y, float z) {
            float H = v(0, 0), sc = v(7, 2), rc = v(9, 0), hc = v(9, 1);
            float ex = (x - spineX(sc)) / rc, ey = (y - (v(0, 1) + H * sc)) / hc, ez = (z - spineZ(sc)) / rc;
            float n = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
            float D = 0;
            if (n > 1e-4f) {
                float az = (float) Math.atan2(ez, ex), el = (float) Math.asin(clamp(ey / n, -1, 1));
                D = sampleMap(1, az, el / (float) Math.PI + .5f);
            }
            return (n - 1 - D) * Math.min(rc, hc);
        }

        /** Max of rounded heads over the 3x3 jittered cells around (azimuth, height); blocks. */
        /** Rows are staggered by half a cell (brick pattern) so heads never line up into tiers. */
        float columnCells(float theta, float height, float r0, float n, float cellH, float amp, float rad, int seed) {
            float fu0 = theta / (float) (2 * Math.PI) * n, fv = height / cellH;
            int iv = (int) Math.floor(fv), ni = (int) n;
            float arc = (float) (2 * Math.PI) * r0 / n, best = 0;
            for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                int j = iv + dj;
                float fu = fu0 - .5f * (j & 1);
                int i = (int) Math.floor(fu) + di;
                int h = cellHash(Math.floorMod(i, ni), j, seed);
                float ju = .5f + .6f * (unit(h) - .5f), jv = .5f + .6f * (unit(h >>> 8) - .5f);
                float du = (fu - (i + ju)) * arc, dv = (fv - (j + jv)) * cellH;
                float r = rad * (.80f + .40f * unit(h >>> 16));
                float t2 = (du * du + dv * dv) / (r * r);
                if (t2 >= 1) continue;
                best = Math.max(best, amp * (.55f + .45f * unit(h >>> 24)) * hemi(t2));
            }
            return best;
        }

        /** Crown cap heads on jittered (azimuth, elevation) cells; fraction of the crown radius. */
        float crownCells(float ux, float uy, float uz, float naz, float nel, float amp, int seed) {
            float az = (float) Math.atan2(uz, ux), el = (float) Math.asin(clamp(uy, -1, 1));
            float fu = az / (float) (2 * Math.PI) * naz, fv = (el + .35f) / ((float) Math.PI * .5f + .35f) * nel;
            int iu = (int) Math.floor(fu), iv = (int) Math.floor(fv), ni = (int) naz;
            float cellAz = (float) (2 * Math.PI) / naz, cellEl = ((float) Math.PI * .5f + .35f) / nel, ce = (float) Math.cos(el), best = 0;
            for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                int i = iu + di, j = iv + dj;
                int h = cellHash(Math.floorMod(i, ni), j, seed);
                float ju = .5f + .6f * (unit(h) - .5f), jv = .5f + .6f * (unit(h >>> 8) - .5f);
                float du = (fu - (i + ju)) * cellAz * ce, dv = (fv - (j + jv)) * cellEl;
                float r = .60f * cellEl * (.80f + .40f * unit(h >>> 16));
                float t2 = (du * du + dv * dv) / (r * r);
                if (t2 >= 1) continue;
                best = Math.max(best, amp * (.55f + .45f * unit(h >>> 24)) * hemi(t2));
            }
            return best;
        }

        public float bodyDistance(float x, float y, float z) {
            return smin(columnDistance(x, y, z), crownDistance(x, y, z), v(9, 2));
        }

        /** Broad anvil sheet: irregular fan planform, thick root descending into the tower, rounded thick edges. */
        public float canopyDistance(float x, float y, float z) {
            if (canopyBounds == null || !canopyBounds.contains(x, y, z)) return 1e6f;
            return canopyEval(x, y, z);
        }
        float canopyEval(float x, float y, float z) {
            float L = v(15, 3), back = v(16, 2), bend = v(16, 3), phase = v(20, 0);
            float dx = x - v(15, 0), dz = z - v(15, 2);
            float a = dx * v(16, 0) + dz * v(16, 1);
            float an = clamp(a / L, 0, 1);
            float c = -dx * v(16, 1) + dz * v(16, 0) - bend * an * an;
            float width = mix(v(17, 0), v(17, 1), (float) Math.pow(an, .6f));
            width *= 1 + v(18, 3) * (.45f * (float) Math.sin(a / L * 6.9115f + phase) + .30f * (float) Math.sin(a / L * 16.022f + 2 * phase)
                    + .25f * (float) Math.sin(a / L * 27.5f + 3.1f * phase));
            float ff = v(19, 1);
            float fib = .5f * (float) Math.sin(c * ff + phase) + .3f * (float) Math.sin(c * ff * 2.37f + 1.3f * phase)
                    + .2f * (float) Math.sin(c * ff * 4.13f + 2.1f * phase);
            float lEff = L * (1 + v(19, 0) * fib);
            float tipStart = .55f * L;
            float dp = a > tipStart ? ellipse(a - tipStart, c, Math.max(lEff - tipStart, 1), width)
                    : a < 0 ? ellipse(a, c, back, width) : Math.abs(c) - width; // same width at a = 0: continuous
            float dax = (float) Math.sqrt(dx * dx + dz * dz);
            float cw = clamp(Math.abs(c) / Math.max(width, 1), 0, 1);
            // Lens edge: thickness falls to zero at the planform boundary over 0.4 H, so the underside
            // rises to meet the top in a rounded edge instead of ending in a vertical face.
            float lens = .2f + .8f * (((float) Math.sqrt(clamp(-dp / (.40f * v(0, 0)), 0, 1) + .1f) - .31622777f) / .73258877f); // finite slope; thick rounded rim, never a razor edge
            float thick = mix(v(17, 2), v(17, 3), smooth(0, 1, dax / v(18, 0))) * (1 - .35f * cw * cw) * lens;
            float top = canopyTop(an, dax) + v(20, 1) * lens * (float) Math.sin(a / L * 9.4f + phase) * (float) Math.sin(c / Math.max(width, 1) * 4.7f + 1.9f * phase);
            float streak = .5f + .5f * (float) Math.sin(c * v(19, 3) + phase + .9f * (float) Math.sin(a * 3.3f / L));
            float bottom = top - thick + thick * v(19, 2) * streak * smooth(.30f, .80f, an);
            float dv = Math.max(y - top, bottom - y);
            return Math.max(dp, smax(dv, dp, Math.max(6, thick * .35f)));
        }

        float canopyTop(float an, float dax) {
            return v(15, 1) + v(18, 2) * an * an + v(18, 1) * (float) Math.exp(-(dax * dax) / (.2f * v(18, 0) * v(18, 0)));
        }

        /**
         * Exact-for-density gated evaluation. Canopy: a trig-free lower bound at or beyond feather/2 +
         * 1.25 merge cannot change the union's density, so it returns 1e6. Body: a trig-free lower bound
         * of smin(column, crown) skips the body when it cannot reach the canopy union or the density;
         * otherwise the nearer surface is evaluated in full and the other only if it can enter the blend.
         */
        public float distance(float x, float y, float z) {
            float c = canopyGated(x, y, z);
            if (!bodyBounds.contains(x, y, z)) return c;
            float kc = v(9, 2), km = v(9, 3), cut = feather() * .5f + km * .25f;
            // Deep interior: an upper bound at or below -feather/2 already gives density 1 exactly
            // (union <= min(column, crown)), so no head field is evaluated inside opaque cloud.
            if (Math.min(columnUpperBound(x, y, z), crownUpperBound(x, y, z)) <= -feather() * .5f) return -feather() * .5f;
            float colLb = columnLowerBound(x, y, z), crLb = crownLowerBound(x, y, z);
            float bodyLb = Math.min(colLb, crLb) - kc * .25f;
            if (bodyLb >= c + km || (bodyLb >= cut && c >= cut)) return c;
            float body;
            if (colLb <= crLb) {
                float col = columnDistance(x, y, z);
                body = crLb >= col + kc ? col : smin(col, crownDistance(x, y, z), kc);
            } else {
                float cr = crownDistance(x, y, z);
                body = colLb >= cr + kc ? cr : smin(columnDistance(x, y, z), cr, kc);
            }
            return smin(body, c, km);
        }
        float canopyGated(float x, float y, float z) {
            if (canopyBounds == null || !canopyBounds.contains(x, y, z)) return 1e6f;
            float L = v(15, 3), bend = v(16, 3);
            float dx = x - v(15, 0), dz = z - v(15, 2);
            float a = dx * v(16, 0) + dz * v(16, 1), an = clamp(a / L, 0, 1);
            float dax = (float) Math.sqrt(dx * dx + dz * dz);
            float top = canopyTop(an, dax), wave = v(20, 1);
            float thickMax = mix(v(17, 2), v(17, 3), smooth(0, 1, dax / v(18, 0)));
            float lb = Math.max(y - top - wave, top - wave - thickMax - y);
            if (a >= 0 && a <= .55f * L) {
                float c = -dx * v(16, 1) + dz * v(16, 0) - bend * an * an;
                lb = Math.max(lb, Math.abs(c) - mix(v(17, 0), v(17, 1), (float) Math.pow(an, .6f)) * (1 + v(18, 3)));
            }
            return lb >= feather() * .5f + 1.25f * v(9, 3) ? 1e6f : canopyEval(x, y, z);
        }
        /** Trig-free: stretched radius >= |q| / (sqrt(1+G) sqrt(max aspect)); modes and displacements bounded. */
        float columnLowerBound(float x, float y, float z) {
            float H = v(0, 0), yBase = v(0, 1), sc = v(7, 2);
            float s = clamp((y - yBase) / H, 0, 1);
            float qx = x - spineX(s), qz = z - spineZ(s);
            float gx = v(6, 0) + 2 * v(6, 2) * s + 3 * v(7, 0) * s * s, gz = v(6, 1) + 2 * v(6, 3) * s + 3 * v(7, 1) * s * s;
            float rhoLb = (float) Math.sqrt(qx * qx + qz * qz) / ((float) Math.sqrt(1 + gx * gx + gz * gz) * (float) Math.sqrt(Math.max(v(10, 0), v(10, 1))));
            float side = rhoLb - (profile(s) * radialBoundFactor() + columnDisplacementMax());
            return Math.max(Math.max(side, yBase - v(13, 3) - y), y - (yBase + H * sc));
        }
/** Upper bound: |q'| <= |q|, stretched radius <= |q'| sqrt(max aspect), displacements >= 0, smax <= max + k/4. */
        float columnUpperBound(float x, float y, float z) {
            float H = v(0, 0), yBase = v(0, 1), sc = v(7, 2);
            float s = clamp((y - yBase) / H, 0, 1);
            float qx = x - spineX(s), qz = z - spineZ(s);
            float mMin = 1 - Math.max(Math.abs(v(11, 0)), Math.abs(v(11, 1))) - Math.max(Math.abs(v(12, 0)), Math.abs(v(12, 1)));
            float side = (float) Math.sqrt(qx * qx + qz * qz) * (float) Math.sqrt(Math.max(v(10, 0), v(10, 1))) - profile(s) * mMin;
            return Math.max(Math.max(side, yBase - y) + v(7, 3) * .25f, y - (yBase + H * sc));
        }
        float crownUpperBound(float x, float y, float z) {
            float H = v(0, 0), sc = v(7, 2), rc = v(9, 0), hc = v(9, 1);
            float ex = (x - spineX(sc)) / rc, ey = (y - (v(0, 1) + H * sc)) / hc, ez = (z - spineZ(sc)) / rc;
            return ((float) Math.sqrt(ex * ex + ey * ey + ez * ez) - 1) * Math.min(rc, hc);
        }
        float crownLowerBound(float x, float y, float z) {
            float H = v(0, 0), sc = v(7, 2), rc = v(9, 0), hc = v(9, 1);
            float ex = (x - spineX(sc)) / rc, ey = (y - (v(0, 1) + H * sc)) / hc, ez = (z - spineZ(sc)) / rc;
            return ((float) Math.sqrt(ex * ex + ey * ey + ez * ez) - 1 - crownDisplacementMax()) * Math.min(rc, hc);
        }
        /** Ungated reference: identical field without component bounds. */
        public float referenceDistance(float x, float y, float z) {
            return smin(bodyDistance(x, y, z), canopyBounds == null ? 1e6f : canopyEval(x, y, z), v(9, 3));
        }
        public float density(float x, float y, float z, Scale scale) {
            if (!bounds.contains(x, y, z) || !gridOccupied(x, y, z)) return 0;
            return clamp(.5f - distance(x, y, z) / feather(), 0, 1);
        }
    }

    /** Integer cell hash, bit-identical to the GLSL uint version. */
    static int cellHash(int i, int j, int seed) {
        int h = i * 374761393 + j * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }
    static float unit(int h) { return (h & 255) / 255f; }

    /** Hemispherical head profile of t^2: 1 at the centre, 0 at t = 1, steep but finite-slope sides. */
    static float hemi(float t2) {
        float e = .12f; // soft foot: heads merge into the surface instead of sitting on it like balls
        return ((float) Math.sqrt(Math.max(0, 1 - t2) + e) - (float) Math.sqrt(e)) / ((float) Math.sqrt(1 + e) - (float) Math.sqrt(e));
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
