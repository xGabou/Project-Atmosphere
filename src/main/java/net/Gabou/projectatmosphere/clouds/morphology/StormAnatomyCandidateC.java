package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import static net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.*;

/**
 * T258 candidate C: hierarchical storm anatomy. Experimental fixture geometry only; no production,
 * save or network authority. GPU mirror: cloud_storm_anatomy_c.glsl (keep in step).
 *
 * B v3 failed because one radial height field per surface can only scallop a smooth core: heads
 * max-merged into an envelope, the core owned every outline and nothing could overhang. C builds the
 * storm from separately shaped growth structures, smooth-unioned in hierarchy order with a blend
 * radius per level, so the junction between two structures stays a real notch:
 * <ul>
 * <li>MACRO (C0): a broad, low, flat-based supporting mass and a narrow connective updraft trunk.</li>
 * <li>MESO major (C1): the dominant updraft's rising bubbles, secondary towers rising from the lower
 * mass at their own points and heights, expanding shoulders, the upper convective crown, and for the
 * supercell the overshooting top, the stepped flanking line and the wall cloud.</li>
 * <li>MESO secondary (C2): smaller turrets attached to selected major heads, rejected where they
 * would fill a valley between two major structures.</li>
 * <li>MICRO and canopy (C3): small cauliflower bumps on exposed upper surfaces, and an independent
 * canopy sheet whose planform is a hierarchy of lobes (root collar, primary downwind expansion,
 * lateral fan, secondary outflow, fibrous perimeter fingers) with thickness falling toward the
 * perimeter, a lobe-dependent top and an underside that funnels into the updraft.</li>
 * </ul>
 * The hierarchy is evaluated once on the CPU and baked into a precomputed distance volume plus a
 * conservative occupancy grid, both in the fixture texture. Per GPU sample the cost is constant: one
 * occupancy fetch and, in occupied cells, two texel fetches for a trilinear distance. Every level uses
 * the same bounds and grid, so C0..C3 are framed by identical cameras.
 */
public final class StormAnatomyCandidateC {
    public static final int VERSION = 1;
    /** Texture width (texels); header in row 0, distance volume from row 1, occupancy grid after it. */
    public static final int TEX_W = 2048;
    public static final int HEADER_TEXELS = 8;
    /** Distance-volume budget (samples) and coarse occupancy grid resolution (cells per axis). */
    public static final int MAX_SAMPLES = 2_000_000, GRID = 64;
    private StormAnatomyCandidateC() {}

    /** C0 supporting core, C1 + major growth, C2 + secondary growth, C3 complete body and canopy. */
    public enum Level { C0, C1, C2, C3 }
    public enum Role { CORE, TRUNK, UPDRAFT, TOWER, TOWER_HEAD, SHOULDER, CROWN_HEAD, OVERSHOOT, FLANK, WALL, SECONDARY, MICRO,
        CANOPY_ROOT, CANOPY_PRIMARY, CANOPY_FAN, CANOPY_OUTFLOW, CANOPY_FIBRE, CANOPY_POUCH }
    /** ELLIPSOID/ROUND_CONE are body solids; LOBE is a 2D canopy planform lobe. */
    public enum Shape { ELLIPSOID, ROUND_CONE, LOBE }
    public static final Set<Role> CANOPY_CORE_ROLES = EnumSet.of(Role.CANOPY_ROOT, Role.CANOPY_PRIMARY);

    /**
     * One growth structure. ELLIPSOID p = (cx, cy, cz, rx, ry, rz, yaw); ROUND_CONE p = (ax, ay, az, ra,
     * bx, by, bz, rb); LOBE p = (cx, topOffset, cz, rAlong, thicknessScale, rAcross, yaw), POUCH lobes use
     * p[1] as the downward amplitude. blend = smooth-union radius against everything added before it.
     * group = index of the major structure it belongs to (diagnostics), -1 for none.
     */
    public record Element(Role role, Level level, Shape shape, float[] p, float blend, int group) {
        public boolean canopy() { return shape == Shape.LOBE; }
        public boolean major() { return level == Level.C1 && shape != Shape.LOBE && role != Role.WALL; }
        /** Conservative axis-aligned extent {minX, minY, minZ, maxX, maxY, maxZ} of a body solid. */
        float[] extent() {
            if (shape == Shape.ROUND_CONE) {
                float r = Math.max(p[3], p[7]);
                return new float[]{Math.min(p[0], p[4]) - r, Math.min(p[1], p[5]) - r, Math.min(p[2], p[6]) - r,
                        Math.max(p[0], p[4]) + r, Math.max(p[1], p[5]) + r, Math.max(p[2], p[6]) + r};
            }
            float c = (float) Math.cos(p[6]), s = (float) Math.sin(p[6]);
            float ex = (float) Math.sqrt(sq(p[3] * c) + sq(p[5] * s)), ez = (float) Math.sqrt(sq(p[3] * s) + sq(p[5] * c));
            return new float[]{p[0] - ex, p[1] - p[4], p[2] - ez, p[0] + ex, p[1] + p[4], p[2] + ez};
        }
    }

    public static Program compile(String type, CloudShapeProfile shape, long seed) {
        return compile(type, shape, seed, Level.C3);
    }

    public static Program compile(String type, CloudShapeProfile shape, long seed, Level level) {
        Archetype kind = switch (type) {
            case "cumulonimbus_calvus" -> Archetype.CALVUS;
            case "cumulonimbus_capillatus" -> Archetype.CAPILLATUS;
            case "supercell" -> Archetype.SUPERCELL;
            default -> throw new IllegalArgumentException("Not a storm: " + type);
        };
        for (float value : new float[]{shape.getBaseRadius(), shape.getTopOffset(), shape.getBaseOffset(), shape.getVerticalTilt(),
                shape.getWindShearStrength(), shape.getTowerNarrowing(), shape.getAnvilSpread(), shape.getLobeStrength(),
                shape.getCellSplitStrength(), shape.getStormWallStrength(), shape.getBaseFlattening(), shape.getEdgeRaggedness()}) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite anatomy descriptor");
        }
        var b = new Builder(kind, shape, seed);
        b.build();
        return new Program(kind, level, seed, b);
    }

    /** Deterministic construction of the element hierarchy (all levels; the level only filters the bake). */
    static final class Builder {
        final Archetype kind;
        final boolean cal, cap, sup;
        final float H, yBase, feather, side, strength, wall, spread, narrow, split;
        final float leanX, leanZ;
        final SplittableRandom rng;
        final int warpSeed;
        final List<Element> elements = new ArrayList<>();
        float topX, topY, topZ;
        /** Canopy sheet scalars: rootX, rootZ, topRoot, length, dirX, dirZ, tRoot, tFar, tMin, dome, sag, funnel. */
        float[] canopy;

        Builder(Archetype kind, CloudShapeProfile shape, long seed) {
            this.kind = kind;
            cal = kind == Archetype.CALVUS; cap = kind == Archetype.CAPILLATUS; sup = kind == Archetype.SUPERCELL;
            float h = shape.getTopOffset() * 4;
            H = h * (cal ? 1.55f : cap ? 1.26f : 1.16f); // same storm depth as B so framing compares like for like
            yBase = -shape.getBaseOffset() * 2;
            feather = Math.max(2, H * .02f);
            rng = new SplittableRandom(seed ^ 0x7258C0DEL);
            side = rng.nextBoolean() ? 1 : -1;
            strength = shape.getLobeStrength();
            wall = shape.getStormWallStrength();
            spread = shape.getAnvilSpread();
            narrow = shape.getTowerNarrowing();
            split = shape.getCellSplitStrength();
            float tilt = shape.getVerticalTilt(), shear = shape.getWindShearStrength();
            // Downshear (+x) lean of the updraft summit over the depth, as a fraction of H.
            leanX = sup ? .22f + .12f * tilt + .08f * shear : cap ? .06f + .05f * tilt + .03f * shear : .04f + .04f * tilt;
            leanZ = side * (sup ? .06f : .02f) * (float) rng.nextDouble(.5, 1.0);
            warpSeed = rng.nextInt(1 << 20);
        }

        float r(double lo, double hi) { return (float) rng.nextDouble(lo, hi); }

        int ellipsoid(Role role, Level level, float cx, float cy, float cz, float rx, float ry, float rz, float yaw, float blend, int group) {
            elements.add(new Element(role, level, Shape.ELLIPSOID, new float[]{cx, cy, cz, rx, ry, rz, yaw}, blend, group));
            return elements.size() - 1;
        }
        int cone(Role role, Level level, float ax, float ay, float az, float ra, float bx, float by, float bz, float rb, float blend, int group) {
            elements.add(new Element(role, level, Shape.ROUND_CONE, new float[]{ax, ay, az, ra, bx, by, bz, rb}, blend, group));
            return elements.size() - 1;
        }
        int lobe(Role role, float cx, float hOff, float cz, float along, float thickScale, float across, float yaw, float blend) {
            elements.add(new Element(role, Level.C3, Shape.LOBE, new float[]{cx, hOff, cz, along, thickScale, across, yaw}, blend, -1));
            return elements.size() - 1;
        }
        /** Spine of the dominant updraft at height fraction s (downshear lean grows with height). */
        float spineX(float s) { return leanX * H * (s * s * .6f + s * .4f - .05f); }
        float spineZ(float s) { return leanZ * H * s; }

        /**
         * A rising chain of overlapping thermal bubbles from height s0 to s1: radius from r0 to r1 with
         * jitter, vertically flattened, laterally offset, so its outline is lumpy and notched, never a tube.
         * Returns the index of the last bubble (the chain's head).
         */
        int bubbleChain(Role role, Level level, float x0, float z0, float x1, float z1, float s0, float s1, float r0, float r1,
                        float blend, int group, float jitter) {
            float s = s0;
            int last = -1;
            for (int n = 0; n < 12; n++) {
                float f = (s1 - s0) <= 0 ? 1 : Math.min(1, (s - s0) / (s1 - s0));
                float rb = H * (r0 + (r1 - r0) * f) * r(.86, 1.12);
                float ry = rb * r(.70, .84);
                float a = r(-Math.PI, Math.PI), j = H * jitter * r(.3, 1.0);
                float cx = x0 + (x1 - x0) * f + (float) Math.cos(a) * j, cz = z0 + (z1 - z0) * f + (float) Math.sin(a) * j;
                float cy = yBase + H * s;
                last = ellipsoid(role, level, cx, cy, cz, rb, ry, rb * r(.85, 1.05), r(0, Math.PI), blend, group < 0 && n == 0 ? elements.size() : group);
                if (group < 0 && n == 0) group = last;
                if (s >= s1 - 1e-4f) break;
                s = Math.min(s1, s + ry * r(1.05, 1.30) / H);
            }
            return last;
        }

        void build() {
            float Y = yBase;
            // ---------------- MACRO C0: supporting lower mass (broad, low, flat based) ----------------
            int pedestal = cal ? 5 : 6;
            float pedR = cal ? .24f : cap ? .28f : .26f;
            float flankAz = sup ? (float) Math.atan2(side * .8, -1) + r(-.15, .15) : r(-Math.PI, Math.PI);
            float stretch = sup ? 1.30f + .25f * split : 1.0f + .15f * split;
            float fx0 = (float) Math.cos(flankAz), fz0 = (float) Math.sin(flankAz);
            for (int i = 0; i < pedestal; i++) {
                float a = (float) (i * 2 * Math.PI / pedestal + r(-.5, .5)), rad = H * pedR * r(.35, 1.0);
                float ox = (float) Math.cos(a) * rad, oz = (float) Math.sin(a) * rad;
                float along = (ox * fx0 + oz * fz0) * stretch, across = -ox * fz0 + oz * fx0;
                ox = along * fx0 - across * fz0; oz = along * fz0 + across * fx0;
                float hr = H * (cal ? r(.24, .31) : cap ? r(.26, .34) : r(.24, .32));
                ellipsoid(Role.CORE, Level.C0, ox, Y + H * r(.08, .12), oz, hr * r(.9, 1.15), H * r(.09, .13), hr * r(.8, 1.05), r(0, Math.PI), H * .10f, -1);
            }
            // Narrow connective trunk: keeps the body continuous without owning the outline.
            float sTop = cal ? r(.78, .84) : cap ? .80f : .80f;
            float c0 = H * (cal ? .14f : cap ? .16f : .19f), c1 = H * ((cal ? .11f : cap ? .12f : .15f) - .015f * narrow);
            int trunk = cone(Role.TRUNK, Level.C0, spineX(.08f), Y + H * .08f, spineZ(.08f), c0, spineX(sTop), Y + H * sTop, spineZ(sTop), c1, H * .08f, -1);
            topX = spineX(sTop); topY = Y + H * sTop; topZ = spineZ(sTop);

            // ---------------- MESO C1: dominant updraft bubbles ----------------
            float u0 = cal ? .27f : cap ? .28f : .32f, u1 = (cal ? .21f : cap ? .23f : .27f) - .015f * narrow;
            List<Integer> heads = new ArrayList<>();
            int updraftHead = bubbleChain(Role.UPDRAFT, Level.C1, spineX(.2f), spineZ(.2f), topX, topZ, .22f, sTop - .03f, u0, u1, H * .055f, -1, .05f);
            // ---------------- MESO C1: upper convective crown ----------------
            float summitR = H * (cal ? r(.16, .19) : cap ? r(.13, .15) : r(.15, .17)) * (.9f + .15f * strength);
            float sa = r(-Math.PI, Math.PI), so = H * r(.02, .05);
            int summit = ellipsoid(Role.CROWN_HEAD, Level.C1, topX + (float) Math.cos(sa) * so, topY + H * (cal ? .07f : .04f), topZ + (float) Math.sin(sa) * so,
                    summitR, summitR * .86f, summitR * r(.9, 1.05), r(0, Math.PI), H * .03f, updraftHead);
            heads.add(summit);
            int crownCount = cal ? 3 : 2;
            float[] crownAz = spacedAngles(crownCount, (float) Math.toRadians(80));
            for (int i = 0; i < crownCount; i++) {
                float a = crownAz[i], rr = H * r(.11, .15) * (.9f + .15f * strength);
                float dist = H * u1 * r(.85, 1.15), dy = -H * r(.03, .14);
                heads.add(ellipsoid(Role.CROWN_HEAD, Level.C1, topX + (float) Math.cos(a) * dist, topY + dy, topZ + (float) Math.sin(a) * dist,
                        rr, rr * r(.80, .92), rr * r(.85, 1.05), r(0, Math.PI), H * .025f, updraftHead));
            }
            // ---------------- MESO C1: secondary towers rising from the lower mass ----------------
            int towers = cal ? 3 : cap ? 3 : 2;
            float[] towerAz = spacedAngles(towers, (float) Math.toRadians(75));
            float[] heights = distinctHeights(towers, cal ? .42f : .34f, cal ? .66f : cap ? .58f : .55f, .08f);
            for (int i = 0; i < towers; i++) {
                float a = towerAz[i], foot = H * r(.18, .27), lean = H * r(.02, .06);
                float fx = (float) Math.cos(a), fz = (float) Math.sin(a), hTop = heights[i];
                float rFoot = r(.15, .19), rTop = r(.13, .17);
                int head = bubbleChain(Role.TOWER, Level.C1, fx * foot, fz * foot, fx * (foot + lean) + spineX(hTop) * .5f, fz * (foot + lean) + spineZ(hTop) * .5f,
                        .16f, hTop, rFoot, rTop, H * .045f, -1, .035f);
                float[] q = elements.get(head).p;
                float hr = q[3] * r(1.08, 1.25);
                // Head wider than the bubble below it: an undercut neck, the cauliflower cue a height field cannot make.
                heads.add(ellipsoid(Role.TOWER_HEAD, Level.C1, q[0] + fx * H * .02f, q[1] + q[4] * .55f, q[2] + fz * H * .02f, hr, hr * r(.78, .90), hr * r(.9, 1.05),
                        r(0, Math.PI), H * .025f, elements.get(head).group()));
            }
            // ---------------- MESO C1: expanding shoulders off the updraft ----------------
            float[] shAz = spacedAngles(2, (float) Math.toRadians(110));
            for (int i = 0; i < 2; i++) {
                float a = shAz[i], s = r(.36, .58), fx = (float) Math.cos(a), fz = (float) Math.sin(a);
                float rr = H * r(.13, .16), out = H * (u0 + (u1 - u0) * (s - .2f) / (sTop - .2f)) * .75f;
                int first = ellipsoid(Role.SHOULDER, Level.C1, spineX(s) + fx * out, Y + H * s, spineZ(s) + fz * out, rr * 1.15f, rr * .78f, rr, -a, H * .04f, elements.size());
                float rr2 = rr * r(.75, .9);
                heads.add(ellipsoid(Role.SHOULDER, Level.C1, spineX(s) + fx * (out + rr * .85f), Y + H * s + rr * .45f, spineZ(s) + fz * (out + rr * .85f),
                        rr2, rr2 * .82f, rr2, -a, H * .03f, first));
            }
            if (cap) {
                // Broad upper expansion of the mature body below the canopy.
                float[] ux = spacedAngles(2, (float) Math.toRadians(120));
                for (int i = 0; i < 2; i++) {
                    float a = ux[i], s = r(.60, .70), fx = (float) Math.cos(a), fz = (float) Math.sin(a), rr = H * r(.14, .17);
                    heads.add(ellipsoid(Role.SHOULDER, Level.C1, spineX(s) + fx * H * u1 * 1.1f, Y + H * s, spineZ(s) + fz * H * u1 * 1.1f,
                            rr * 1.25f, rr * .75f, rr, -a, H * .04f, updraftHead));
                }
            }
            // ---------------- Supercell: flanking line, wall cloud, overshooting top ----------------
            float canopyTop = Y + H * (cap ? 1.0f : .95f);
            if (sup) {
                // Flanking line: irregular spacing, lateral scatter and uneven (not monotone) height steps.
                int nFlank = 4;
                float[] fd = new float[nFlank], fh = new float[nFlank];
                float dd = r(.42, .52), hh0 = r(.56, .64);
                for (int i = 0; i < nFlank; i++) {
                    fd[i] = dd; fh[i] = hh0;
                    dd += r(.18, .34);
                    hh0 = Math.max(.22f, hh0 - r(.03, .15));
                }
                for (int i = 0; i < fd.length; i++) {
                    float d = H * fd[i], hh = fh[i], wob = H * r(-.14, .14);
                    float fx = fx0 * d - fz0 * wob, fz = fz0 * d + fx0 * wob;
                    // Inflow band under the flank: the line grows out of the supporting mass.
                    ellipsoid(Role.CORE, Level.C0, fx * .92f, Y + H * .07f, fz * .92f, H * .21f, H * .075f, H * .16f, -(float) Math.atan2(fz, fx), H * .08f, -1);
                    int head = bubbleChain(Role.FLANK, Level.C1, fx, fz, fx * .86f, fz * .86f, .12f, hh, .16f - .012f * i, .13f - .01f * i, H * .04f, -1, .04f);
                    float[] q = elements.get(head).p;
                    float hr = q[3] * r(1.15, 1.35);
                    heads.add(ellipsoid(Role.TOWER_HEAD, Level.C1, q[0], q[1] + q[4] * .55f, q[2], hr, hr * r(.78, .9), hr, r(0, Math.PI), H * .025f, elements.get(head).group()));
                }
                if (wall > 0) {
                    float wx = spineX(.1f) + fx0 * H * .12f, wz = spineZ(.1f) + fz0 * H * .12f;
                    ellipsoid(Role.WALL, Level.C1, wx, Y - H * .05f * wall, wz, H * .18f, H * .08f, H * .16f, r(0, Math.PI), H * .03f, -1);
                }
                float oR = H * r(.17, .20);
                heads.add(ellipsoid(Role.OVERSHOOT, Level.C1, topX + H * .02f, canopyTop - oR * .30f, topZ, oR, oR * .70f, oR * .92f, r(0, Math.PI), H * .05f, updraftHead));
                // The trunk must reach the overshoot through the anvil root.
                bubbleChain(Role.UPDRAFT, Level.C1, topX, topZ, topX + H * .02f, topZ, sTop, (canopyTop - Y) / H - .12f, u1, u1 * .9f, H * .05f, updraftHead, .02f);
            }

            // ---------------- MESO C2: secondary turrets on selected majors, never into valleys ----------------
            int majorsEnd = elements.size();
            List<Integer> secondaries = new ArrayList<>();
            for (int hid : heads) {
                Element parent = elements.get(hid);
                int want = rng.nextDouble() < (cal ? .80 : .65) ? (rng.nextDouble() < .35 ? 2 : 1) : 0;
                for (int n = 0, tries = 0; n < want && tries < 14; tries++) {
                    float[] q = parent.p;
                    float ox = q[0] - topX, oz = q[2] - topZ;
                    float az = (float) Math.atan2(oz, ox) + r(-1.0, 1.0), el = r(.30, 1.15);
                    if (ox * ox + oz * oz < sq(H * .03f)) az = r(-Math.PI, Math.PI);
                    float dx = (float) (Math.cos(el) * Math.cos(az)), dy = (float) Math.sin(el), dz = (float) (Math.cos(el) * Math.sin(az));
                    float rs = Math.max(q[3], q[5]) * r(.45, .62);
                    // Sunk into the parent: a broad secondary lobe, not a bead on its surface.
                    float cx = q[0] + dx * q[3] * .78f, cy = q[1] + dy * q[4] * .78f, cz = q[2] + dz * q[5] * .78f;
                    boolean reject = false;
                    // Valley guard: another major structure within the turret's radius means it would fill a notch.
                    for (int j = 0; j < majorsEnd && !reject; j++) {
                        Element o = elements.get(j);
                        if (j == hid || !o.major() || o.group() == parent.group()) continue;
                        if (primitive(o, cx, cy, cz) < rs * 1.1f) reject = true;
                    }
                    for (int s : secondaries) {
                        float[] e = elements.get(s).p;
                        if (sq(e[0] - cx) + sq(e[1] - cy) + sq(e[2] - cz) < sq(e[3] + rs)) reject = true;
                    }
                    if (reject || !cal && cy + rs > canopyTop - H * .02f && parent.role() != Role.OVERSHOOT) continue;
                    secondaries.add(ellipsoid(Role.SECONDARY, Level.C2, cx, cy, cz, rs, rs * r(.80, .92), rs * r(.9, 1.05), r(0, Math.PI), H * .012f, parent.group()));
                    n++;
                }
            }
            // ---------------- MICRO C3: small cauliflower bumps on exposed upper surfaces ----------------
            List<Integer> carriers = new ArrayList<>();
            for (int i = 0; i < elements.size(); i++) {
                Role role = elements.get(i).role();
                if (elements.get(i).shape() == Shape.ELLIPSOID && (role == Role.UPDRAFT || role == Role.TOWER || role == Role.FLANK || role == Role.CROWN_HEAD
                        || role == Role.TOWER_HEAD || role == Role.SHOULDER || role == Role.SECONDARY || role == Role.OVERSHOOT)) carriers.add(i);
            }
            for (int hid : carriers) {
                Element e = elements.get(hid);
                float[] q = e.p;
                int count = 2 + rng.nextInt(3);
                for (int n = 0; n < count; n++) {
                    float az = r(-Math.PI, Math.PI), el = r(.15, 1.4);
                    float dx = (float) (Math.cos(el) * Math.cos(az)), dy = (float) Math.sin(el), dz = (float) (Math.cos(el) * Math.sin(az));
                    float rm = H * r(.045, .065);
                    // Deeply sunk lumps (centre well inside the carrier): a cauliflower texture, not pimples.
                    ellipsoid(Role.MICRO, Level.C3, q[0] + dx * q[3] * .76f, q[1] + dy * q[4] * .76f, q[2] + dz * q[5] * .76f, rm, rm * .8f, rm, 0, H * .012f, e.group());
                }
            }
            if (!cal) canopy(canopyTop);
        }

        /** Hierarchical canopy planform lobes plus the sheet scalars (evaluated as one connected sheet). */
        void canopy(float top) {
            float dirA = r(-.15, .15) + side * (sup ? .08f : .03f);
            float dx = (float) Math.cos(dirA), dz = (float) Math.sin(dirA), px = -dz, pz = dx;
            float rootX = topX + H * .03f, rootZ = topZ;
            float length = H * (cap ? 1.15f + .20f * spread : 1.95f + .30f * spread);
            canopy = new float[]{rootX, rootZ, top, length, dx, dz,
                    H * (cap ? .30f : .44f), H * (cap ? .11f : .11f), H * .035f, H * (cap ? .05f : .035f), H * (cap ? .05f : .12f), H * (cap ? .12f : .16f)};
            // Root collar spreading from the upper updraft.
            lobe(Role.CANOPY_ROOT, rootX, 0, rootZ, H * (cap ? .46f : .38f), 1, H * (cap ? .44f : .34f), -dirA, H * .08f);
            // Primary downwind expansion: overlapping lobes, widening (wedge for the supercell), own heights.
            int lobes = cap ? 3 : 5;
            float spacing = length / lobes;
            for (int i = 0; i < lobes; i++) {
                float f = (i + 1f) / lobes;
                float s = spacing * (i + .75f) + H * r(-.04, .04);
                float half = (sup ? H * (.26f + .62f * f) : H * (.38f + .20f * f)) * r(.88, 1.08);
                float lat = H * r(-.09, .09) + side * H * (sup ? .12f : .04f) * f * f;
                lobe(Role.CANOPY_PRIMARY, rootX + dx * s + px * lat, H * r(-.06, .05), rootZ + dz * s + pz * lat, spacing * r(.80, .95), r(.7, 1.3), half,
                        -dirA + r(-.15, .15), H * .07f);
            }
            // Lateral fan: two asymmetric lobes across the shear.
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                float big = sgn == (int) side ? 1f : r(.55, .75);
                float ang = dirA + sgn * (float) Math.toRadians(sup ? r(22, 32) : r(55, 85));
                float dist = H * (sup ? r(1.25, 1.55) : r(.40, .55)) * (.6f + .4f * big);
                lobe(Role.CANOPY_FAN, rootX + (float) Math.cos(ang) * dist, H * r(-.02, .03), rootZ + (float) Math.sin(ang) * dist,
                        H * r(.30, .38) * big, .9f, H * r(.20, .28) * big, -ang, H * .06f);
            }
            if (sup) lobe(Role.CANOPY_OUTFLOW, rootX - dx * H * .36f, -H * .05f, rootZ - dz * H * .36f, H * .24f, .7f, H * .30f, -dirA, H * .06f); // back-sheared anvil
            // Secondary ice outflow regions protruding from the current perimeter.
            for (int i = 0; i < 3; i++) {
                float ang = dirA + r(-2.0, 2.0), edge = edge(ang, rootX, rootZ);
                float rr = H * r(.14, .22);
                lobe(Role.CANOPY_OUTFLOW, rootX + (float) Math.cos(ang) * (edge + rr * .15f), H * r(-.06, 0), rootZ + (float) Math.sin(ang) * (edge + rr * .15f),
                        rr * r(1.0, 1.4), .6f, rr, -ang + r(-.4, .4), H * .05f);
            }
            if (sup) {
                for (int i = 0; i < 4; i++) {
                    float s = H * r(.35, 1.3), lat = H * r(-.35, .35), rr = H * r(.09, .13);
                    lobe(Role.CANOPY_POUCH, rootX + dx * s + px * lat, H * r(.04, .07), rootZ + dz * s + pz * lat, rr, 0, rr, 0, 0);
                }
            }
            // Fibrous perimeter: streaks combed downwind (ice carried by the outflow), frayed at the edge,
            // not radial spikes; thin, so they are optically thin in flat light.
            int fibres = cap ? 20 : 7;
            float arc = (float) Math.toRadians(cap ? 200 : 110);
            for (int i = 0; i < fibres; i++) {
                float ang = dirA + ((i + r(.15, .85)) / fibres - .5f) * arc, edge = edge(ang, rootX, rootZ);
                float len = H * (sup ? r(.12, .20) : r(.08, .18)), wid = H * (sup ? r(.07, .11) : r(.035, .07));
                float c = edge - len * .15f;
                float comb = dirA + .35f * wrap(ang - dirA) + r(-.15, .15);
                float cx = rootX + (float) Math.cos(ang) * c + (float) Math.cos(comb) * len * .45f, cz = rootZ + (float) Math.sin(ang) * c + (float) Math.sin(comb) * len * .45f;
                lobe(Role.CANOPY_FIBRE, cx, -H * r(.0, .04), cz, len, sup ? .45f : .35f, wid, -comb, H * .03f);
            }
        }

        /** Distance from the root to the current planform boundary along a direction. */
        float edge(float ang, float rootX, float rootZ) {
            float cx = (float) Math.cos(ang), cz = (float) Math.sin(ang), t = 0;
            while (t < H * 4 && planform(elements, null, rootX + cx * t, rootZ + cz * t, 0, 0) < 0) t += H * .01f;
            return t;
        }

        float[] spacedAngles(int n, float minSep) {
            float[] a = new float[n];
            for (int tries = 0; ; tries++) {
                for (int i = 0; i < n; i++) a[i] = r(-Math.PI, Math.PI);
                boolean ok = true;
                for (int i = 0; i < n && ok; i++) for (int j = i + 1; j < n && ok; j++) ok = Math.abs(wrap(a[i] - a[j])) >= minSep;
                if (ok || tries > 200) return a;
            }
        }
        float[] distinctHeights(int n, float lo, float hi, float gap) {
            float[] h = new float[n];
            for (int tries = 0; ; tries++) {
                for (int i = 0; i < n; i++) h[i] = r(lo, hi);
                boolean ok = true;
                for (int i = 0; i < n && ok; i++) for (int j = i + 1; j < n && ok; j++) ok = Math.abs(h[i] - h[j]) >= gap;
                if (ok || tries > 200) return h;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Construction primitives (CPU only; the GPU samples the baked result).

    /** Signed distance to one body solid (approximate Euclidean for ellipsoids). */
    static float primitive(Element e, float x, float y, float z) {
        float[] p = e.p;
        if (e.shape == Shape.ELLIPSOID) {
            float qx = x - p[0], qy = y - p[1], qz = z - p[2];
            float c = (float) Math.cos(p[6]), s = (float) Math.sin(p[6]);
            float u = qx * c - qz * s, w = qx * s + qz * c;
            float k0 = (float) Math.sqrt(sq(u / p[3]) + sq(qy / p[4]) + sq(w / p[5]));
            float k1 = (float) Math.sqrt(sq(u / (p[3] * p[3])) + sq(qy / (p[4] * p[4])) + sq(w / (p[5] * p[5])));
            return k1 < 1e-9f ? -Math.min(p[3], Math.min(p[4], p[5])) : k0 * (k0 - 1) / k1;
        }
        return roundCone(x, y, z, p[0], p[1], p[2], p[4], p[5], p[6], p[3], p[7]);
    }

    /** 2D distance to a planform lobe (approximate Euclidean ellipse). */
    static float lobe2d(float[] p, float x, float z) {
        float qx = x - p[0], qz = z - p[2];
        float c = (float) Math.cos(p[6]), s = (float) Math.sin(p[6]);
        float u = qx * c - qz * s, w = qx * s + qz * c;
        float k0 = (float) Math.sqrt(sq(u / p[3]) + sq(w / p[5])), k1 = (float) Math.sqrt(sq(u / (p[3] * p[3])) + sq(w / (p[5] * p[5])));
        return k1 < 1e-9f ? -Math.min(p[3], p[5]) : k0 * (k0 - 1) / k1;
    }

    /**
     * Canopy planform: hierarchical smooth union of lobes (roles filter for diagnostics, null = all).
     * out (optional, length 2) receives the membership-weighted top offset and thickness scale.
     */
    static float planform(List<Element> elements, Set<Role> roles, float x, float z, int unused, float unused2) {
        return planform(elements, roles, x, z, null);
    }
    static float planform(List<Element> elements, Set<Role> roles, float x, float z, float[] out) {
        float d = 1e4f, wSum = 0, hSum = 0, tSum = 0;
        for (Element e : elements) {
            if (e.shape != Shape.LOBE || e.role == Role.CANOPY_POUCH || roles != null && !roles.contains(e.role)) continue;
            float di = lobe2d(e.p, x, z);
            d = d >= 1e4f ? di : smin(d, di, e.blend);
            if (out != null) {
                float w = Math.max(0, Math.min(1, .5f - di / Math.max(e.p[3], e.p[5]) * 1.5f));
                w *= w;
                wSum += w; hSum += w * e.p[1]; tSum += w * e.p[4];
            }
        }
        if (out != null) { out[0] = wSum > 0 ? hSum / wSum : 0; out[1] = wSum > 0 ? tSum / wSum : 1; }
        return d;
    }

    /** Exact round cone between spheres (a, r1) and (b, r2) (Quilez). */
    static float roundCone(float px, float py, float pz, float ax, float ay, float az, float bx, float by, float bz, float r1, float r2) {
        float bax = bx - ax, bay = by - ay, baz = bz - az;
        float l2 = bax * bax + bay * bay + baz * baz, rr = r1 - r2, a2 = l2 - rr * rr, il2 = 1f / l2;
        float pax = px - ax, pay = py - ay, paz = pz - az;
        float y = pax * bax + pay * bay + paz * baz, z = y - l2;
        float xx = sq(pax * l2 - bax * y) + sq(pay * l2 - bay * y) + sq(paz * l2 - baz * y);
        float y2 = y * y * l2, z2 = z * z * l2;
        float k = Math.signum(rr) * rr * rr * xx;
        if (Math.signum(z) * a2 * z2 > k) return (float) Math.sqrt(xx + z2) * il2 - r2;
        if (Math.signum(y) * a2 * y2 < k) return (float) Math.sqrt(xx + y2) * il2 - r1;
        return ((float) Math.sqrt(xx * a2 * il2) + y * rr) * il2 - r1;
    }

    /** Smooth low-frequency value noise (quintic) in [-1, 1]; breaks perfect ellipsoids into organic heads. */
    static float warpNoise(float x, float y, float z, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        float fx = x - ix, fy = y - iy, fz = z - iz;
        float ux = fx * fx * fx * (fx * (fx * 6 - 15) + 10), uy = fy * fy * fy * (fy * (fy * 6 - 15) + 10), uz = fz * fz * fz * (fz * (fz * 6 - 15) + 10);
        float v = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1, dy = (c >> 1) & 1, dz = c >> 2;
            float w = (dx == 1 ? ux : 1 - ux) * (dy == 1 ? uy : 1 - uy) * (dz == 1 ? uz : 1 - uz);
            int h = ConvectiveGrowthFieldPrototype.cellHash(ix + dx + 7919 * (iz + dz), iy + dy, seed);
            v += w * ((h & 0xffff) / 65535f * 2 - 1);
        }
        return v;
    }

    public static final class Program implements StormGeometryProgram {
        private final Archetype kind;
        private final Level level;
        private final long seed;
        private final List<Element> elements;
        private final float[] canopy;
        private final float H, yBase, feather, warpBody, warpCanopy, warpScale;
        private final int warpSeed;
        private final Bounds bounds, bodyBox, canopyBox;
        private final int nx, ny, nz;
        private final float voxel;
        private final float[] sdf;
        private final byte[] grid;
        private final float[] header;
        private final long signature;
        private final int sdfStart, gridStart, texRows;

        private Program(Archetype kind, Level level, long seed, Builder b) {
            this.kind = kind;
            this.level = level;
            this.seed = seed;
            this.elements = List.copyOf(b.elements);
            this.canopy = b.canopy;
            H = b.H; yBase = b.yBase; feather = b.feather; warpSeed = b.warpSeed;
            warpBody = H * .022f; warpCanopy = H * .040f; warpScale = 1f / (H * .38f);
            // Domain from every element of every level: identical bounds and grids for C0..C3.
            float band = feather * 2.5f;
            float[] ext = {1e9f, yBase - H * .12f, 1e9f, -1e9f, -1e9f, -1e9f};
            for (Element e : elements) {
                if (e.canopy()) continue;
                float[] x = e.extent();
                float m = band + e.blend + warpBody * 1.8f;
                for (int a = 0; a < 3; a++) { ext[a] = Math.min(ext[a], x[a] - m); ext[a + 3] = Math.max(ext[a + 3], x[a + 3] + m); }
            }
            ext[1] = Math.max(ext[1], yBase - H * .12f - band);
            float[] ce = canopyExtent();
            if (ce != null) for (int a = 0; a < 3; a++) { ext[a] = Math.min(ext[a], ce[a]); ext[a + 3] = Math.max(ext[a + 3], ce[a + 3]); }
            float volume = (ext[3] - ext[0]) * (ext[4] - ext[1]) * (ext[5] - ext[2]);
            float v = Math.max(6f, (float) Math.cbrt(volume / MAX_SAMPLES));
            voxel = (float) Math.ceil(v * 4) / 4f;
            nx = (int) Math.ceil((ext[3] - ext[0]) / voxel) + 2;
            ny = (int) Math.ceil((ext[4] - ext[1]) / voxel) + 2;
            nz = (int) Math.ceil((ext[5] - ext[2]) / voxel) + 2;
            bounds = new Bounds(ext[0], ext[1], ext[2], ext[0] + (nx - 1) * voxel, ext[1] + (ny - 1) * voxel, ext[2] + (nz - 1) * voxel);
            sdf = new float[nx * ny * nz];
            float[] canopyField = bake(band);
            grid = new byte[GRID * GRID * GRID];
            Bounds[] boxes = bakeGrid(canopyField);
            bodyBox = boxes[0];
            canopyBox = boxes[1];
            sdfStart = TEX_W;
            gridStart = sdfStart + nx * ny * nz;
            texRows = (gridStart + GRID * GRID * GRID / 4 + TEX_W - 1) / TEX_W;
            header = new float[HEADER_TEXELS * 4];
            put(header, 0, nx, ny, nz, voxel);
            put(header, 1, feather, level.ordinal(), VERSION, canopyBox == null ? 1 : 2);
            Bounds bb = bodyBox == null ? new Bounds(0, 0, 0, -1, -1, -1) : bodyBox, cb = canopyBox == null ? new Bounds(0, 0, 0, -1, -1, -1) : canopyBox;
            put(header, 2, bb.minX(), bb.minY(), bb.minZ(), 0);
            put(header, 3, bb.maxX(), bb.maxY(), bb.maxZ(), 0);
            put(header, 4, cb.minX(), cb.minY(), cb.minZ(), 0);
            put(header, 5, cb.maxX(), cb.maxY(), cb.maxZ(), 0);
            put(header, 6, bounds.minX(), bounds.minY(), bounds.minZ(), GRID);
            put(header, 7, sdfStart, gridStart, TEX_W, kind.ordinal());
            long hsh = 0xcbf29ce484222325L;
            for (float x : header) hsh = (hsh ^ Float.floatToIntBits(x)) * 0x100000001b3L;
            for (float x : sdf) hsh = (hsh ^ Float.floatToIntBits(x)) * 0x100000001b3L;
            signature = hsh;
        }

        /** Conservative extent of the canopy sheet (planform lobes, warp, every height term). */
        private float[] canopyExtent() {
            if (canopy == null) return null;
            float m = feather * 2.5f + warpCanopy * 1.8f + H * .08f;
            float[] e = {1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f};
            float hMax = 0, hMin = 0, pouch = 0;
            for (Element el : elements) {
                if (!el.canopy()) continue;
                if (el.role == Role.CANOPY_POUCH) { pouch = Math.max(pouch, el.p[1]); continue; }
                float[] x = el.extent();
                e[0] = Math.min(e[0], x[0] - m); e[2] = Math.min(e[2], x[2] - m);
                e[3] = Math.max(e[3], x[3] + m); e[5] = Math.max(e[5], x[5] + m);
                hMax = Math.max(hMax, el.p[1]); hMin = Math.min(hMin, el.p[1]);
            }
            float top = canopy[2];
            e[1] = top + hMin - canopy[10] * 1.5f - canopy[6] - canopy[11] - pouch - m;
            e[4] = top + hMax + canopy[9] + m;
            return e;
        }

        /** Canopy sheet distance at a point (warped planform; lobe-dependent top; thinning; funnel; pouches). */
        float canopyDistance(float x, float y, float z, Set<Role> roles) {
            float s = warpScale * .8f;
            float wx = x + warpCanopy * warpNoise(x * s, y * s * .3f, z * s, warpSeed ^ 0x6A09E667);
            float wz = z + warpCanopy * warpNoise(x * s + 31.7f, y * s * .3f, z * s, warpSeed ^ 0x3C6EF372);
            float[] mem = new float[2];
            float plan = planform(elements, roles, wx, wz, mem);
            float rootX = canopy[0], rootZ = canopy[1], L = canopy[3];
            float rx = x - rootX, rz = z - rootZ, dRoot = (float) Math.sqrt(rx * rx + rz * rz);
            float along = Math.max(0, rx * canopy[4] + rz * canopy[5]) / L;
            float inside = Math.max(0, -plan);
            float tLocal = canopy[7] + (canopy[6] - canopy[7]) * (float) Math.exp(-sq(dRoot / (.6f * L)));
            // Saturating ramp of the inside depth: thins toward the perimeter without the medial-axis ridges
            // (hip-roof facets) a linear min() of the inside distance leaves on the underside.
            float floor = canopy[8] * Math.min(1, mem[1] * 1.4f); // fibre wisps (low thickness scale) thin below the sheet floor: optically thin
            float thick = floor + Math.max(0, tLocal * mem[1] - floor) * (1 - (float) Math.exp(-inside / (.09f * H)));
            float top = canopy[2] + mem[0] + canopy[9] * (float) Math.exp(-sq(dRoot / (.30f * H))) - canopy[10] * along * along;
            float bottom = top - thick - canopy[11] * (float) Math.exp(-sq(dRoot / (.30f * H)));
            for (Element e : elements) {
                if (e.role != Role.CANOPY_POUCH) continue;
                float q = (sq(x - e.p[0]) + sq(z - e.p[2])) / sq(e.p[3]);
                if (q < 9) bottom -= e.p[1] * (float) Math.exp(-q) * Math.min(1, inside / (H * .1f));
            }
            float dv = Math.max(y - top, bottom - y);
            float k = Math.max(feather * .5f, Math.min(thick * .45f, H * .06f));
            return Math.max(plan, smax(dv, plan, k));
        }

        /** Bakes body and canopy fields, merges them, normalises to a local distance; returns the canopy field. */
        private float[] bake(float band) {
            int n = nx * ny * nz;
            float big = 1e4f;
            float[] body = new float[n], can = new float[n];
            java.util.Arrays.fill(body, big);
            java.util.Arrays.fill(can, big);
            Element wallCloud = null;
            for (Element e : elements) {
                if (e.level.ordinal() > level.ordinal() || e.canopy()) continue;
                if (e.role == Role.WALL) { wallCloud = e; continue; }
                float[] x = e.extent();
                float reach = band + e.blend + warpBody * 1.8f;
                int i0 = Math.max(0, (int) Math.floor((x[0] - reach - bounds.minX()) / voxel)), i1 = Math.min(nx - 1, (int) Math.ceil((x[3] + reach - bounds.minX()) / voxel));
                int j0 = Math.max(0, (int) Math.floor((x[1] - reach - bounds.minY()) / voxel)), j1 = Math.min(ny - 1, (int) Math.ceil((x[4] + reach - bounds.minY()) / voxel));
                int k0 = Math.max(0, (int) Math.floor((x[2] - reach - bounds.minZ()) / voxel)), k1 = Math.min(nz - 1, (int) Math.ceil((x[5] + reach - bounds.minZ()) / voxel));
                for (int k = k0; k <= k1; k++) for (int j = j0; j <= j1; j++) for (int i = i0; i <= i1; i++) {
                    float[] w = warped(bounds.minX() + i * voxel, bounds.minY() + j * voxel, bounds.minZ() + k * voxel);
                    float d = primitive(e, w[0], w[1], w[2]);
                    int o = (k * ny + j) * nx + i;
                    body[o] = body[o] >= big ? d : smin(body[o], d, e.blend);
                }
            }
            if (canopy != null && level == Level.C3) {
                float[] ce = canopyExtent();
                int i0 = Math.max(0, (int) Math.floor((ce[0] - bounds.minX()) / voxel)), i1 = Math.min(nx - 1, (int) Math.ceil((ce[3] - bounds.minX()) / voxel));
                int j0 = Math.max(0, (int) Math.floor((ce[1] - bounds.minY()) / voxel)), j1 = Math.min(ny - 1, (int) Math.ceil((ce[4] - bounds.minY()) / voxel));
                int k0 = Math.max(0, (int) Math.floor((ce[2] - bounds.minZ()) / voxel)), k1 = Math.min(nz - 1, (int) Math.ceil((ce[5] - bounds.minZ()) / voxel));
                final int fi0 = i0, fi1 = i1, fj0 = j0, fj1 = j1;
                java.util.stream.IntStream.rangeClosed(k0, k1).parallel().forEach(k -> {
                    for (int j = fj0; j <= fj1; j++) for (int i = fi0; i <= fi1; i++)
                        can[(k * ny + j) * nx + i] = canopyDistance(bounds.minX() + i * voxel, bounds.minY() + j * voxel, bounds.minZ() + k * voxel, null);
                });
            }
            // Flat convective base (the wall cloud hangs below it), then body + canopy attachment.
            float kBase = H * .02f, kMerge = H * .07f;
            for (int k = 0; k < nz; k++) for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                int o = (k * ny + j) * nx + i;
                float x = bounds.minX() + i * voxel, y = bounds.minY() + j * voxel, z = bounds.minZ() + k * voxel;
                float d = body[o] >= big ? big : smax(body[o], yBase - y, kBase);
                if (wallCloud != null && body[o] < big) {
                    float[] w = warped(x, y, z);
                    d = smin(d, smax(primitive(wallCloud, w[0], w[1], w[2]), yBase - H * .12f - y, kBase), wallCloud.blend);
                }
                sdf[o] = can[o] >= big ? d : d >= big ? can[o] : smin(d, can[o], kMerge);
            }
            // Local normalisation (d / |grad d|): warp and smooth unions distort distance; dividing by the
            // gradient restores a consistent density feather. Then clamp to the band the density needs.
            float[] out = new float[n];
            for (int k = 0; k < nz; k++) for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                int o = (k * ny + j) * nx + i;
                float d = sdf[o];
                if (Math.abs(d) < band * 2 && i > 0 && j > 0 && k > 0 && i < nx - 1 && j < ny - 1 && k < nz - 1) {
                    float gx = sdf[o + 1] - sdf[o - 1], gy = sdf[o + nx] - sdf[o - nx], gz = sdf[o + nx * ny] - sdf[o - nx * ny];
                    float g = (float) Math.sqrt(gx * gx + gy * gy + gz * gz) / (2 * voxel);
                    if (g > .6f && g < 1.8f) d /= g; // thin sheets give a cancelled central difference: leave them unscaled
                }
                out[o] = Math.max(-band, Math.min(band, d));
            }
            System.arraycopy(out, 0, sdf, 0, n);
            return can;
        }

        private float[] warped(float x, float y, float z) {
            float s = warpScale, a = warpBody;
            return new float[]{x + a * warpNoise(x * s, y * s, z * s, warpSeed), y + a * warpNoise(x * s, y * s + 17.3f, z * s, warpSeed ^ 0x2C1B3C6D),
                    z + a * warpNoise(x * s + 31.7f, y * s, z * s, warpSeed ^ 0x51ED27)};
        }

        /** Occupancy grid: a cell is empty iff every sample its trilinear support touches is >= feather/2. */
        private Bounds[] bakeGrid(float[] canopyField) {
            float cut = feather * .5f;
            float cx = (bounds.maxX() - bounds.minX()) / GRID, cy = (bounds.maxY() - bounds.minY()) / GRID, cz = (bounds.maxZ() - bounds.minZ()) / GRID;
            float[] bLo = {1e9f, 1e9f, 1e9f}, bHi = {-1e9f, -1e9f, -1e9f}, cLo = {1e9f, 1e9f, 1e9f}, cHi = {-1e9f, -1e9f, -1e9f};
            for (int k = 0; k < GRID; k++) for (int j = 0; j < GRID; j++) for (int i = 0; i < GRID; i++) {
                int i0 = clampI((int) Math.floor(i * cx / voxel) - 1, 0, nx - 1), i1 = clampI((int) Math.ceil((i + 1) * cx / voxel) + 1, 0, nx - 1);
                int j0 = clampI((int) Math.floor(j * cy / voxel) - 1, 0, ny - 1), j1 = clampI((int) Math.ceil((j + 1) * cy / voxel) + 1, 0, ny - 1);
                int k0 = clampI((int) Math.floor(k * cz / voxel) - 1, 0, nz - 1), k1 = clampI((int) Math.ceil((k + 1) * cz / voxel) + 1, 0, nz - 1);
                float lo = 1e9f, canopyLo = 1e9f;
                for (int kk = k0; kk <= k1; kk++) for (int jj = j0; jj <= j1; jj++) for (int ii = i0; ii <= i1; ii++) {
                    int o = (kk * ny + jj) * nx + ii;
                    lo = Math.min(lo, sdf[o]);
                    canopyLo = Math.min(canopyLo, canopyField[o]);
                }
                boolean occ = lo < cut;
                grid[(k * GRID + j) * GRID + i] = (byte) (occ ? 1 : 0);
                if (!occ) continue;
                boolean can = canopyLo < lo + feather;
                float[] L = can ? cLo : bLo, U = can ? cHi : bHi;
                L[0] = Math.min(L[0], bounds.minX() + i * cx); U[0] = Math.max(U[0], bounds.minX() + (i + 1) * cx);
                L[1] = Math.min(L[1], bounds.minY() + j * cy); U[1] = Math.max(U[1], bounds.minY() + (j + 1) * cy);
                L[2] = Math.min(L[2], bounds.minZ() + k * cz); U[2] = Math.max(U[2], bounds.minZ() + (k + 1) * cz);
            }
            Bounds body = bLo[0] > bHi[0] ? null : new Bounds(bLo[0], bLo[1], bLo[2], bHi[0], bHi[1], bHi[2]);
            Bounds can = cLo[0] > cHi[0] ? null : new Bounds(cLo[0], cLo[1], cLo[2], cHi[0], cHi[1], cHi[2]);
            if (body == null) { body = can; can = null; }
            return new Bounds[]{body, can};
        }

        // ---------------- GPU mirror ----------------
        boolean gridOccupied(float x, float y, float z) {
            var b = bounds;
            int i = clampI((int) Math.floor((x - b.minX()) / (b.maxX() - b.minX()) * GRID), 0, GRID - 1);
            int j = clampI((int) Math.floor((y - b.minY()) / (b.maxY() - b.minY()) * GRID), 0, GRID - 1);
            int k = clampI((int) Math.floor((z - b.minZ()) / (b.maxZ() - b.minZ()) * GRID), 0, GRID - 1);
            return grid[(k * GRID + j) * GRID + i] != 0;
        }
        /** Trilinear distance from the packed volume (texel = x pair at k and k+1; next row in j is +nx). */
        public float sampledDistance(float x, float y, float z) {
            float gx = (x - bounds.minX()) / voxel, gy = (y - bounds.minY()) / voxel, gz = (z - bounds.minZ()) / voxel;
            int i0 = clampI((int) Math.floor(gx), 0, nx - 2), j0 = clampI((int) Math.floor(gy), 0, ny - 2), k0 = clampI((int) Math.floor(gz), 0, nz - 2);
            float fx = clamp(gx - i0, 0, 1), fy = clamp(gy - j0, 0, 1), fz = clamp(gz - k0, 0, 1);
            int o = (k0 * ny + j0) * nx + i0, oz = o + nx * ny;
            float a0 = sdf[o], a1 = sdf[o + 1], a2 = sdf[oz], a3 = sdf[oz + 1];
            float b0 = sdf[o + nx], b1 = sdf[o + nx + 1], b2 = sdf[oz + nx], b3 = sdf[oz + nx + 1];
            float m0 = mix(a0, b0, fy), m1 = mix(a1, b1, fy), m2 = mix(a2, b2, fy), m3 = mix(a3, b3, fy);
            return mix(mix(m0, m1, fx), mix(m2, m3, fx), fz);
        }
        @Override public float density(float x, float y, float z, Scale scale) {
            if (!bounds.contains(x, y, z) || !gridOccupied(x, y, z)) return 0;
            return clamp(.5f - sampledDistance(x, y, z) / feather, 0, 1);
        }

        @Override public float[] textureData() {
            float[] t = new float[TEX_W * texRows * 4];
            System.arraycopy(header, 0, t, 0, header.length);
            for (int k = 0; k < nz; k++) for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                int o = (k * ny + j) * nx + i, ii = Math.min(i + 1, nx - 1), kk = Math.min(k + 1, nz - 1);
                int base = (sdfStart + o) * 4;
                t[base] = sdf[o];
                t[base + 1] = sdf[(k * ny + j) * nx + ii];
                t[base + 2] = sdf[(kk * ny + j) * nx + i];
                t[base + 3] = sdf[(kk * ny + j) * nx + ii];
            }
            for (int v = 0; v < grid.length; v++) t[gridStart * 4 + v] = grid[v];
            return t;
        }
        @Override public int textureWidth() { return TEX_W; }
        @Override public int textureHeight() { return texRows; }

        public Archetype kind() { return kind; }
        public Level level() { return level; }
        public long seed() { return seed; }
        public List<Element> elements() { return elements; }
        public float feather() { return feather; }
        public float voxel() { return voxel; }
        public int[] dims() { return new int[]{nx, ny, nz}; }
        public Bounds bodyBox() { return bodyBox; }
        public Bounds canopyBox() { return canopyBox; }
        public boolean hasCanopy() { return canopy != null; }
        public float baseY() { return yBase; }
        /** Canopy sheet scalars (diagnostics); null for calvus. */
        public float[] canopyParams() { return canopy == null ? null : canopy.clone(); }
        /** Warped canopy planform distance for a role subset (null = all roles); diagnostics only. */
        public float canopyPlanform(float x, float z, Set<Role> roles) {
            float s = warpScale * .8f, y = canopy[2];
            float wx = x + warpCanopy * warpNoise(x * s, y * s * .3f, z * s, warpSeed ^ 0x6A09E667);
            float wz = z + warpCanopy * warpNoise(x * s + 31.7f, y * s * .3f, z * s, warpSeed ^ 0x3C6EF372);
            return planform(elements, roles, wx, wz, null);
        }
        @Override public float radius() { return H * .5f; }
        @Override public float height() { return H; }
        @Override public float axisX(float t) {
            Element trunk = null;
            for (Element e : elements) if (e.role == Role.TRUNK) { trunk = e; break; }
            float[] p = trunk.p;
            float y = yBase + H * clamp(t, 0, 1);
            return mix(p[0], p[4], clamp((y - p[1]) / (p[5] - p[1]), 0, 1));
        }
        @Override public Bounds bounds() { return bounds; }
        @Override public float[] packed() { return header.clone(); }
        @Override public long signature() { return signature; }
        @Override public int version() { return VERSION; }
        @Override public boolean growthDefined() { return true; }
        public float[] sdfCopy() { return sdf.clone(); }

        /** Exact (unbaked) body distance from this level's solids, optionally excluding one element (diagnostics). */
        public float exactBodyDistance(float x, float y, float z, int exclude) {
            float body = 1e4f;
            Element wallCloud = null;
            float[] w = warped(x, y, z);
            for (int idx = 0; idx < elements.size(); idx++) {
                Element e = elements.get(idx);
                if (idx == exclude || e.level.ordinal() > level.ordinal() || e.canopy()) continue;
                if (e.role == Role.WALL) { wallCloud = e; continue; }
                float d = primitive(e, w[0], w[1], w[2]);
                body = body >= 1e4f ? d : smin(body, d, e.blend);
            }
            float d = smax(body, yBase - y, H * .02f);
            if (wallCloud != null) d = smin(d, smax(primitive(wallCloud, w[0], w[1], w[2]), yBase - H * .12f - y, H * .02f), wallCloud.blend);
            return d;
        }
        /** Exact canopy sheet distance (1e4 without a canopy at this level). */
        public float exactCanopyDistance(float x, float y, float z) {
            return canopy == null || level != Level.C3 ? 1e4f : canopyDistance(x, y, z, null);
        }
        /** Distance to one element alone (warped), for overlap diagnostics. */
        public float elementDistance(int idx, float x, float y, float z) {
            float[] w = warped(x, y, z);
            return primitive(elements.get(idx), w[0], w[1], w[2]);
        }
    }

    private static void put(float[] f, int texel, float a, float b, float c, float d) {
        f[texel * 4] = a; f[texel * 4 + 1] = b; f[texel * 4 + 2] = c; f[texel * 4 + 3] = d;
    }
    static float smin(float a, float b, float k) { if (k <= 0) return Math.min(a, b); float t = Math.max(k - Math.abs(a - b), 0) / k; return Math.min(a, b) - t * t * k * .25f; }
    static float smax(float a, float b, float k) { float t = Math.max(k - Math.abs(a - b), 0) / k; return Math.max(a, b) + t * t * k * .25f; }
    static float wrap(float a) { return a - (float) (2 * Math.PI) * (float) Math.floor(a / (float) (2 * Math.PI) + .5f); }
    static float sq(float v) { return v * v; }
    private static int clampI(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }
    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
