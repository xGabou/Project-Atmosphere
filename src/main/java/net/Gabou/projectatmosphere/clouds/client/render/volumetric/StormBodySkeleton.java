package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/**
 * T240. The continuous storm body's per-storm parameters, derived once on the
 * CPU from a storm group's descriptor rows and uploaded as two extra rows of
 * the descriptor texture (rows {@code MAX_LOBES + 2 * groupSlot} and the one
 * after it). The shader evaluates the body from these ten texels instead of
 * re-reading the group's lobes for every sample.
 *
 * <p>The descriptors are skeleton data only (T239):
 * <ul>
 * <li>frame - origin at the lower updraft (the lowest TOWER centre), the wind
 * axis from the tower centroid to the radius-weighted ANVIL centroid, the
 * cross axis perpendicular to it;</li>
 * <li>base - storm floor (lowest BASE base), 0.75 of the BASE lobes' height,
 * the BASE lobes' separate upwind / downwind / left / right reach;</li>
 * <li>updraft - the lowest and highest TOWER centres and heights (the axis
 * leans between them) and 0.62 of their mean radius;</li>
 * <li>deck - the ANVIL lobes' along-wind reach (0.6 R upwind, 1.2 R downwind),
 * cross half-width (|c| + 1.4 minor), radius-weighted cross drift, and the
 * deck's bottom / top;</li>
 * <li>strengths per region, the physical edge band, and a conservative world
 * AABB that encloses the body and its band (bounds, culling, tiles).</li>
 * </ul>
 * A group without all three of BASE, TOWER and ANVIL gets an invalid block
 * (valid flag -1); the shader then treats the group as empty.
 *
 * <p>Row layout (5 RGBA texels per row, 40 floats per group):
 * <pre>
 *  A.0 originX originZ windX windZ      B.0 deckLo deckHi deckHalfWidth deckCrossMean
 *  A.1 floorY baseHeight deckMid band   B.1 deckBottom deckTop strengthBase strengthTower
 *  A.2 baseUp baseDown baseLeft baseRight B.2 strengthAnvil ownershipReach 0 0
 *  A.3 towerLoX towerLoZ towerHiX towerHiZ B.3 aabbMinX aabbMinZ aabbMaxX aabbMaxZ
 *  A.4 towerLoY towerHiY towerRadius valid B.4 aabbMinY aabbMaxY 0 0
 * </pre>
 * Keep in step with {@code stormBody*} in cloud_atmosphere_volume.fsh and with
 * {@link StormBodyEvaluator}.
 */
public final class StormBodySkeleton {
    public static final int ROWS_PER_GROUP = 2;
    public static final int FLOATS_PER_GROUP =
            ROWS_PER_GROUP * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR;

    public static final int ORIGIN_X = 0;
    public static final int ORIGIN_Z = 1;
    public static final int WIND_X = 2;
    public static final int WIND_Z = 3;
    public static final int FLOOR_Y = 4;
    public static final int BASE_HEIGHT = 5;
    public static final int DECK_MID = 6;
    public static final int BAND = 7;
    public static final int BASE_UP = 8;
    public static final int BASE_DOWN = 9;
    public static final int BASE_LEFT = 10;
    public static final int BASE_RIGHT = 11;
    public static final int TOWER_LO_X = 12;
    public static final int TOWER_LO_Z = 13;
    public static final int TOWER_HI_X = 14;
    public static final int TOWER_HI_Z = 15;
    public static final int TOWER_LO_Y = 16;
    public static final int TOWER_HI_Y = 17;
    public static final int TOWER_RADIUS = 18;
    public static final int VALID = 19;
    public static final int DECK_LO = 20;
    public static final int DECK_HI = 21;
    public static final int DECK_HALF_WIDTH = 22;
    public static final int DECK_CROSS_MEAN = 23;
    public static final int DECK_BOTTOM = 24;
    public static final int DECK_TOP = 25;
    public static final int STRENGTH_BASE = 26;
    public static final int STRENGTH_TOWER = 27;
    public static final int STRENGTH_ANVIL = 28;
    public static final int OWNERSHIP_REACH = 29;
    public static final int AABB_MIN_X = 32;
    public static final int AABB_MIN_Z = 33;
    public static final int AABB_MAX_X = 34;
    public static final int AABB_MAX_Z = 35;
    public static final int AABB_MIN_Y = 36;
    public static final int AABB_MAX_Y = 37;

    /** Base height kept from the BASE lobes (flatter lower mass). */
    static final double BASE_HEIGHT_FRACTION = 0.75D;
    /** Updraft radius from the mean TOWER descriptor radius. */
    static final double TOWER_RADIUS_FRACTION = 0.62D;
    /**
     * T241. The updraft is at least this fraction of the lower mass's mean
     * reach (a massive tower rising out of it, not a stem on a saucer), and
     * the lower mass is scaled back to the tower's flared foot, at most
     * BASE_FOOT_OF_TOWER Rt of mean reach (asymmetry kept, never below half).
     */
    static final double TOWER_FROM_BASE_FRACTION = 0.48D;
    static final double BASE_FOOT_OF_TOWER = 1.3D;
    static final double DECK_UPWIND_REACH = 0.6D;
    static final double DECK_DOWNWIND_REACH = 1.2D;
    static final double DECK_CROSS_REACH = 1.4D;
    /** Largest column reach over Rt: turret centres at 1.43 Rt plus their 0.42 Rt radius. */
    static final double COLUMN_MAX_SCALE = 1.95D;
    /** Deck corner rounding cap and the AABB pad beyond the band. */
    static final double DECK_ROUND_MAX = 20.0D;
    static final double AABB_PAD = 8.0D;

    private StormBodySkeleton() {
    }

    /** Descriptor-texture row of a group's first skeleton row. */
    public static int firstRow(int groupSlot) {
        return StormLobeSpatialIndex.MAX_LOBES + ROWS_PER_GROUP * groupSlot;
    }

    /** Float offset of a group's skeleton block inside the full descriptor texel array. */
    public static int offset(int groupSlot) {
        return firstRow(groupSlot) * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR;
    }

    /**
     * Writes every group slot's skeleton block from the descriptor rows
     * {@code [0, descriptorCount)} of {@code texels}. Slots without a complete
     * body are written invalid.
     */
    public static void writeAll(float[] texels, int descriptorCount) {
        for (int slot = 0; slot < StormLobeSpatialIndex.MAX_GROUPS; slot++) {
            compute(texels, descriptorCount, slot, texels, offset(slot));
        }
    }

    /**
     * Computes one group's block from descriptor rows into {@code out} at
     * {@code outOffset}. Returns false (and writes an invalid block) when the
     * group has no BASE, TOWER or ANVIL member.
     */
    public static boolean compute(float[] texels, int descriptorCount, int groupSlot,
            float[] out, int outOffset) {
        final int stride = StormLobeDescriptor.FLOATS_PER_DESCRIPTOR;
        double floorY = Double.POSITIVE_INFINITY;
        double baseTop = Double.NEGATIVE_INFINITY;
        double deckBottom = Double.POSITIVE_INFINITY;
        double deckTop = Double.NEGATIVE_INFINITY;
        double towerLoX = 0.0D;
        double towerLoZ = 0.0D;
        double towerHiX = 0.0D;
        double towerHiZ = 0.0D;
        double towerLoY = Double.POSITIVE_INFINITY;
        double towerHiY = Double.NEGATIVE_INFINITY;
        double towerRadiusSum = 0.0D;
        double anvilSumX = 0.0D;
        double anvilSumZ = 0.0D;
        double anvilWeight = 0.0D;
        double baseStrength = 0.0D;
        double towerStrength = 0.0D;
        double anvilStrength = 0.0D;
        int baseCount = 0;
        int towerCount = 0;
        int anvilCount = 0;
        for (int index = 0; index < descriptorCount; index++) {
            int o = index * stride;
            float packed = texels[o + 15];
            if (packed < 0.0F) {
                continue;
            }
            int topology = Math.round(packed);
            if (StormLobeDescriptor.unpackGroupSlot(topology) != groupSlot) {
                continue;
            }
            int role = topology % 8;
            double x = texels[o];
            double z = texels[o + 1];
            double baseY = texels[o + 2];
            double topY = texels[o + 3];
            double major = texels[o + 4];
            double strength = clamp01(texels[o + 10]);
            if (role == 0) {
                floorY = Math.min(floorY, baseY);
                baseTop = Math.max(baseTop, topY);
                baseStrength += strength;
                baseCount++;
            } else if (role == 2) {
                double mid = 0.5D * (baseY + topY);
                if (mid < towerLoY) {
                    towerLoY = mid;
                    towerLoX = x;
                    towerLoZ = z;
                }
                if (mid > towerHiY) {
                    towerHiY = mid;
                    towerHiX = x;
                    towerHiZ = z;
                }
                towerRadiusSum += major;
                towerStrength += strength;
                towerCount++;
            } else if (role == 3) {
                deckBottom = Math.min(deckBottom, baseY);
                deckTop = Math.max(deckTop, topY);
                anvilSumX += x * major;
                anvilSumZ += z * major;
                anvilWeight += major;
                anvilStrength += strength;
                anvilCount++;
            }
        }
        java.util.Arrays.fill(out, outOffset, outOffset + FLOATS_PER_GROUP, 0.0F);
        if (baseCount == 0 || towerCount == 0 || anvilCount == 0) {
            out[outOffset + VALID] = -1.0F;
            return false;
        }
        double originX = towerLoX;
        double originZ = towerLoZ;
        double anvilX = anvilSumX / Math.max(anvilWeight, 1.0D);
        double anvilZ = anvilSumZ / Math.max(anvilWeight, 1.0D);
        double offX = anvilX - 0.5D * (towerLoX + towerHiX);
        double offZ = anvilZ - 0.5D * (towerLoZ + towerHiZ);
        double offLength = Math.hypot(offX, offZ);
        double windX = offLength > 1.0D ? offX / offLength : 1.0D;
        double windZ = offLength > 1.0D ? offZ / offLength : 0.0D;
        double crossX = -windZ;
        double crossZ = windX;

        double baseUp = 1.0D;
        double baseDown = 1.0D;
        double baseLeft = 1.0D;
        double baseRight = 1.0D;
        double deckLo = Double.POSITIVE_INFINITY;
        double deckHi = Double.NEGATIVE_INFINITY;
        double deckHalfWidth = 1.0D;
        double deckCrossSum = 0.0D;
        for (int index = 0; index < descriptorCount; index++) {
            int o = index * stride;
            float packed = texels[o + 15];
            if (packed < 0.0F) {
                continue;
            }
            int topology = Math.round(packed);
            if (StormLobeDescriptor.unpackGroupSlot(topology) != groupSlot) {
                continue;
            }
            int role = topology % 8;
            double relX = texels[o] - originX;
            double relZ = texels[o + 1] - originZ;
            double lw = relX * windX + relZ * windZ;
            double lc = relX * crossX + relZ * crossZ;
            double major = texels[o + 4];
            double minor = texels[o + 5];
            if (role == 0) {
                baseUp = Math.max(baseUp, -(lw - major));
                baseDown = Math.max(baseDown, lw + major);
                baseLeft = Math.max(baseLeft, -(lc - minor));
                baseRight = Math.max(baseRight, lc + minor);
            } else if (role == 3) {
                deckLo = Math.min(deckLo, lw - DECK_UPWIND_REACH * major);
                deckHi = Math.max(deckHi, lw + DECK_DOWNWIND_REACH * major);
                deckHalfWidth = Math.max(deckHalfWidth, Math.abs(lc) + DECK_CROSS_REACH * minor);
                deckCrossSum += lc * major;
            }
        }
        double deckCrossMean = deckCrossSum / Math.max(anvilWeight, 1.0D);
        double ownershipReach = Math.max(Math.max(baseUp, baseDown), Math.max(baseLeft, baseRight));
        double baseMeanReach = 0.25D * (baseUp + baseDown + baseLeft + baseRight);
        double towerRadius = Math.max(TOWER_RADIUS_FRACTION * towerRadiusSum / towerCount,
                TOWER_FROM_BASE_FRACTION * baseMeanReach);
        double footScale = Math.max(0.5D, Math.min(1.0D,
                BASE_FOOT_OF_TOWER * towerRadius / Math.max(baseMeanReach, 1.0D)));
        baseUp *= footScale;
        baseDown *= footScale;
        baseLeft *= footScale;
        baseRight *= footScale;
        double baseHeight = Math.max(baseTop - floorY, 1.0D) * BASE_HEIGHT_FRACTION;
        double band = bandFor(towerRadius);
        double deckMid = 0.5D * (deckBottom + deckTop);

        // Conservative AABB: the body's wind-frame rectangle (base, column,
        // deck incl. its cross drift) plus the band, rounding and a pad,
        // rotated into world space.
        // The smooth-minimum joins (k = 0.6 Rt) bulge the union by up to
        // k / 4 beyond its parts.
        double pad = band + 0.15D * towerRadius + DECK_ROUND_MAX + AABB_PAD;
        double columnReach = towerRadius * COLUMN_MAX_SCALE;
        // The deck wedge's own reach: [deckLo, deckHi] along the wind and its
        // widest half-width either side of the cross drift.
        double tipHalf = StormBodyEvaluator.wedgeTipHalf(towerRadius, deckHalfWidth);
        double deckReachLo = deckLo;
        double deckReachHi = deckHi;
        double deckReachCMin = Math.min(0.0D, deckCrossMean) - tipHalf;
        double deckReachCMax = Math.max(0.0D, deckCrossMean) + tipHalf;
        double hiW = (towerHiX - originX) * windX + (towerHiZ - originZ) * windZ;
        double hiC = (towerHiX - originX) * crossX + (towerHiZ - originZ) * crossZ;
        double wMin = Math.min(Math.min(-baseUp, deckReachLo), Math.min(0.0D, hiW) - columnReach) - pad;
        double wMax = Math.max(Math.max(baseDown, deckReachHi), Math.max(0.0D, hiW) + columnReach) + pad;
        double cMin = Math.min(Math.min(-baseLeft, deckReachCMin),
                Math.min(0.0D, hiC) - columnReach) - pad;
        double cMax = Math.max(Math.max(baseRight, deckReachCMax),
                Math.max(0.0D, hiC) + columnReach) + pad;
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            double w = (corner & 1) == 0 ? wMin : wMax;
            double c = (corner & 2) == 0 ? cMin : cMax;
            double x = originX + w * windX + c * crossX;
            double z = originZ + w * windZ + c * crossZ;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        out[outOffset + ORIGIN_X] = (float) originX;
        out[outOffset + ORIGIN_Z] = (float) originZ;
        out[outOffset + WIND_X] = (float) windX;
        out[outOffset + WIND_Z] = (float) windZ;
        out[outOffset + FLOOR_Y] = (float) floorY;
        out[outOffset + BASE_HEIGHT] = (float) baseHeight;
        out[outOffset + DECK_MID] = (float) deckMid;
        out[outOffset + BAND] = (float) band;
        out[outOffset + BASE_UP] = (float) baseUp;
        out[outOffset + BASE_DOWN] = (float) baseDown;
        out[outOffset + BASE_LEFT] = (float) baseLeft;
        out[outOffset + BASE_RIGHT] = (float) baseRight;
        out[outOffset + TOWER_LO_X] = (float) towerLoX;
        out[outOffset + TOWER_LO_Z] = (float) towerLoZ;
        out[outOffset + TOWER_HI_X] = (float) towerHiX;
        out[outOffset + TOWER_HI_Z] = (float) towerHiZ;
        out[outOffset + TOWER_LO_Y] = (float) towerLoY;
        out[outOffset + TOWER_HI_Y] = (float) towerHiY;
        out[outOffset + TOWER_RADIUS] = (float) towerRadius;
        out[outOffset + VALID] = 1.0F;
        out[outOffset + DECK_LO] = (float) deckLo;
        out[outOffset + DECK_HI] = (float) deckHi;
        out[outOffset + DECK_HALF_WIDTH] = (float) deckHalfWidth;
        out[outOffset + DECK_CROSS_MEAN] = (float) deckCrossMean;
        out[outOffset + DECK_BOTTOM] = (float) deckBottom;
        out[outOffset + DECK_TOP] = (float) deckTop;
        out[outOffset + STRENGTH_BASE] = (float) (baseStrength / baseCount);
        out[outOffset + STRENGTH_TOWER] = (float) (towerStrength / towerCount);
        out[outOffset + STRENGTH_ANVIL] = (float) (anvilStrength / anvilCount);
        out[outOffset + OWNERSHIP_REACH] = (float) ownershipReach;
        out[outOffset + AABB_MIN_X] = (float) minX;
        out[outOffset + AABB_MIN_Z] = (float) minZ;
        out[outOffset + AABB_MAX_X] = (float) maxX;
        out[outOffset + AABB_MAX_Z] = (float) maxZ;
        out[outOffset + AABB_MIN_Y] = (float) (floorY - pad);
        out[outOffset + AABB_MAX_Y] = (float) (deckTop + pad);
        return true;
    }

    /** The physical edge band (T239 Task 1): fixed blocks, not a fraction of a lobe. */
    static double bandFor(double towerRadius) {
        return Math.max(24.0D, Math.min(48.0D, 0.20D * towerRadius));
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
