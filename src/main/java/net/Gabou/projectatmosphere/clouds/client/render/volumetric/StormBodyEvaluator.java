package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/**
 * T240. CPU mirror of the continuous storm body's signed distance
 * ({@code stormBodyGroupField} in cloud_atmosphere_volume.fsh), evaluated from
 * a {@link StormBodySkeleton} block. Same equations, same constants; the
 * camera-density path and the morphology sandboxes read the body through it.
 *
 * <p>The body (T239, accepted):
 * <ul>
 * <li>base - a flat-bottomed slab, 0.75 of the BASE lobes' height, footprint a
 * superellipse (n = 3) over the separate upwind / downwind / left / right
 * reach, radius scale 1 - 0.20 t^2, corners rounded by 0.18 of its height;</li>
 * <li>updraft - one column along the leaning tower axis, radius
 * Rt * (1 + 0.30 (1 - smoothstep(-0.4, 0.25, t)) + 0.30 smoothstep(0.5, 1.2, t)),
 * t from the base crown (0) to the deck bottom (1);</li>
 * <li>deck - one flat-topped sheet over u = (w - deckLo) / length: thickness
 * span * (0.85 - 0.45 u), top sinking 0.05 span downwind, half-width fanning
 * from 1.3 Rt to the deck half-width by u = 0.45, root and tip rounded,
 * centreline drifting to the mean cross offset;</li>
 * <li>joins - smooth minimum base|column, then |deck, k = 0.6 Rt.</li>
 * </ul>
 * T240 over T239, for a true distance (the march's safe advance and the
 * shared field's dilation rely on it): the base term is divided by its
 * analytic gradient magnitude, and the deck footprint - T239's half-width
 * profile W(u) - is the union of DECK_DISCS discs of radius W(u_i) along the
 * deck axis (following the cross drift), an exact distance where T239's
 * clamped-u box overestimated beyond the deck's ends (the anatomy sandbox
 * measured gradients up to 8.4 there).
 */
public final class StormBodyEvaluator {
    /** Regions (mode 17 bits): base, transition, updraft, anvil deck. */
    public static final int REGION_BASE = 1;
    public static final int REGION_TRANSITION = 2;
    public static final int REGION_UPDRAFT = 4;
    public static final int REGION_ANVIL = 8;

    /**
     * Conservative Lipschitz bounds of the body distance, horizontal and
     * vertical, published to the shared-field and rain-gate builds (which add
     * their own 1.2 headroom). Checked numerically by the geometry sandbox.
     */
    public static final double LIPSCHITZ_HORIZONTAL = 1.4D;
    public static final double LIPSCHITZ_VERTICAL = 1.7D;

    private StormBodyEvaluator() {
    }

    /** Mutable result so a caller evaluating many points allocates nothing. */
    public static final class Sample {
        public double distance = 1.0E9D;
        public double band;
        public double strength;
        public double height01;
        public int region;
        public boolean interiorFill;
    }

    /** Evaluates the body of one skeleton block at a point. Returns false for an invalid block. */
    public static boolean evaluate(float[] s, int o, double x, double y, double z, Sample out) {
        if (s[o + StormBodySkeleton.VALID] < 0.0F) {
            out.distance = 1.0E9D;
            out.region = 0;
            out.interiorFill = false;
            return false;
        }
        double windX = s[o + StormBodySkeleton.WIND_X];
        double windZ = s[o + StormBodySkeleton.WIND_Z];
        double relX = x - s[o + StormBodySkeleton.ORIGIN_X];
        double relZ = z - s[o + StormBodySkeleton.ORIGIN_Z];
        double w = relX * windX + relZ * windZ;
        double c = -relX * windZ + relZ * windX;
        double floorY = s[o + StormBodySkeleton.FLOOR_Y];
        double baseHeight = s[o + StormBodySkeleton.BASE_HEIGHT];
        double deckMid = s[o + StormBodySkeleton.DECK_MID];
        double band = s[o + StormBodySkeleton.BAND];
        double towerRadius = s[o + StormBodySkeleton.TOWER_RADIUS];

        // Base.
        double baseT = clamp01((y - floorY) / baseHeight);
        double baseScale = 1.0D - 0.20D * baseT * baseT;
        double extentW = w > 0.0D ? s[o + StormBodySkeleton.BASE_DOWN] : s[o + StormBodySkeleton.BASE_UP];
        double extentC = c > 0.0D ? s[o + StormBodySkeleton.BASE_RIGHT] : s[o + StormBodySkeleton.BASE_LEFT];
        double baseH = baseHorizontal(Math.abs(w) / extentW, Math.abs(c) / extentC,
                extentW, extentC, baseScale);
        double baseV = Math.max(floorY - y, y - (floorY + baseHeight));
        double dBase = roundedBox2(baseH, baseV, 0.18D * baseHeight);

        // Updraft column.
        double loY = s[o + StormBodySkeleton.TOWER_LO_Y];
        double hiY = s[o + StormBodySkeleton.TOWER_HI_Y];
        double axisT = clamp01((y - loY) / Math.max(hiY - loY, 1.0D));
        double axisX = lerp(s[o + StormBodySkeleton.TOWER_LO_X], s[o + StormBodySkeleton.TOWER_HI_X], axisT);
        double axisZ = lerp(s[o + StormBodySkeleton.TOWER_LO_Z], s[o + StormBodySkeleton.TOWER_HI_Z], axisT);
        double deckBottom = s[o + StormBodySkeleton.DECK_BOTTOM];
        double deckTop = s[o + StormBodySkeleton.DECK_TOP];
        double crown = floorY + baseHeight;
        double columnT = (y - crown) / Math.max(deckBottom - crown, 1.0D);
        double columnScale = 1.0D
                + 0.30D * (1.0D - smoothstep(-0.4D, 0.25D, columnT))
                + 0.30D * smoothstep(0.5D, 1.2D, columnT);
        double dColumn = Math.max(Math.hypot(x - axisX, z - axisZ) - towerRadius * columnScale,
                Math.max(floorY + 0.10D * baseHeight - y, y - deckMid));

        // Deck sheet.
        double deckLo = s[o + StormBodySkeleton.DECK_LO];
        double deckHi = s[o + StormBodySkeleton.DECK_HI];
        double deckLength = Math.max(deckHi - deckLo, 1.0D);
        double ud = clamp01((w - deckLo) / deckLength);
        double span = Math.max(deckTop - deckBottom, 20.0D);
        double thickness = span * (0.85D - 0.45D * ud);
        double top = deckTop - 0.05D * span * ud;
        double bottom = top - thickness;
        double crossMean = s[o + StormBodySkeleton.DECK_CROSS_MEAN];
        double dFootprint = deckFootprint(s, o, w, c, deckLo, deckHi, crossMean);
        double dVertical = Math.max(bottom - y, y - top);
        double deckRound = Math.min(0.20D * thickness, StormBodySkeleton.DECK_ROUND_MAX);
        double dDeck = roundedBox2(dFootprint, dVertical, deckRound);

        // Joins.
        double k = 0.6D * towerRadius;
        double baseWeight = sminWeight(dBase, dColumn, k);
        double lower = smin(dBase, dColumn, k, baseWeight);
        double lowerWeight = sminWeight(lower, dDeck, k);
        double d = smin(lower, dDeck, k, lowerWeight);

        double heightUpdraft = clamp01((y - floorY) / Math.max(deckMid - floorY, 1.0D));
        double heightDeck = clamp01((y - bottom) / Math.max(thickness, 1.0D));
        if (lowerWeight < 0.15D) {
            out.region = REGION_ANVIL;
            out.height01 = heightDeck;
        } else if (lowerWeight < 0.85D) {
            out.region = REGION_TRANSITION;
            out.height01 = lerp(heightDeck, heightUpdraft, lowerWeight);
        } else if (baseWeight >= 0.5D) {
            out.region = REGION_BASE;
            out.height01 = baseT;
        } else {
            out.region = REGION_UPDRAFT;
            out.height01 = heightUpdraft;
        }
        double strengthLower = lerp(s[o + StormBodySkeleton.STRENGTH_TOWER],
                s[o + StormBodySkeleton.STRENGTH_BASE], baseWeight);
        out.strength = lerp(s[o + StormBodySkeleton.STRENGTH_ANVIL], strengthLower, lowerWeight);
        out.band = lerp(Math.min(band, 0.30D * thickness), band, lowerWeight);
        out.distance = d;
        out.interiorFill = out.region != REGION_ANVIL && d < -out.band;
        return true;
    }

    private static final ThreadLocal<float[]> SCRATCH = ThreadLocal.withInitial(
            () -> new float[StormLobeSpatialIndex.DESCRIPTOR_FLOATS]);
    private static final ThreadLocal<Sample> SCRATCH_SAMPLE = ThreadLocal.withInitial(Sample::new);

    /**
     * The nearest storm body of a descriptor set at a point: the skeletons are
     * rebuilt from the descriptors exactly as the upload builds them, then the
     * group with the smallest distance wins (the shader's group union differs
     * from this only where two storms' bands overlap). Returns false when no
     * group forms a body.
     */
    public static boolean nearestAt(StormLobeDescriptor[] lobes, double x, double y, double z,
            Sample out) {
        if (lobes == null || lobes.length == 0) {
            return false;
        }
        float[] texels = SCRATCH.get();
        int count = Math.min(lobes.length, StormLobeSpatialIndex.MAX_LOBES);
        java.util.Arrays.fill(texels, 0.0F);
        for (int index = 0; index < count; index++) {
            lobes[index].writeTexels(texels, index * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR);
        }
        StormBodySkeleton.writeAll(texels, count);
        Sample candidate = SCRATCH_SAMPLE.get();
        boolean any = false;
        out.distance = 1.0E9D;
        for (int slot = 0; slot < StormLobeSpatialIndex.MAX_GROUPS; slot++) {
            if (!evaluate(texels, StormBodySkeleton.offset(slot), x, y, z, candidate)) {
                continue;
            }
            if (!any || candidate.distance < out.distance) {
                out.distance = candidate.distance;
                out.band = candidate.band;
                out.strength = candidate.strength;
                out.height01 = candidate.height01;
                out.region = candidate.region;
                out.interiorFill = candidate.interiorFill;
                any = true;
            }
        }
        return any;
    }

    /** Coverage envelope of a sample (the shader's stormEnvelopeFromDistance). */
    public static double coverage(Sample sample) {
        return StormLobeEvaluator.envelopeFromDistance(sample.distance, sample.band, sample.strength);
    }

    /**
     * The deck footprint's distance: the union of DECK_DISCS discs along the
     * deck axis, disc i at along-wind position spanning [deckLo + r_0,
     * deckHi - r_last] and cross offset crossMean * u_i, radius r_i =
     * W(u_i) (row C of the skeleton).
     */
    static double deckFootprint(float[] s, int o, double w, double c,
            double deckLo, double deckHi, double crossMean) {
        int last = StormBodySkeleton.DECK_DISCS - 1;
        double first = s[o + StormBodySkeleton.DECK_RADIUS_0];
        double end = s[o + StormBodySkeleton.DECK_RADIUS_0 + last];
        double start = deckLo + first;
        double travel = Math.max(deckHi - end - start, 0.0D);
        double best = 1.0E9D;
        for (int disc = 0; disc <= last; disc++) {
            double u = disc / (double) last;
            double dw = w - (start + u * travel);
            double dc = c - crossMean * u;
            best = Math.min(best, Math.sqrt(dw * dw + dc * dc)
                    - s[o + StormBodySkeleton.DECK_RADIUS_0 + disc]);
        }
        return best;
    }

    static double deckHalfWidth(double ud, double towerRadius, double halfWidthMax) {
        double halfWidth = lerp(1.3D * towerRadius, halfWidthMax, smoothstep(0.0D, 0.45D, ud));
        double tip = smoothstep(0.78D, 1.0D, ud);
        double root = 1.0D - smoothstep(0.0D, 0.15D, ud);
        return halfWidth * Math.sqrt(Math.max(1.0D - 0.85D * tip * tip, 0.0D))
                * Math.sqrt(Math.max(1.0D - 0.6D * root * root, 0.0D));
    }

    /**
     * First-order horizontal distance to the superellipse level r3 = scale,
     * in blocks: (r3 - scale) / |grad r3|. Deep inside (r3 -> 0) the gradient
     * vanishes, so the value is bounded by the footprint's own size.
     */
    static double baseHorizontal(double qx, double qy, double extentW, double extentC, double scale) {
        double r3 = Math.cbrt(qx * qx * qx + qy * qy * qy);
        double limit = -scale * Math.max(extentW, extentC);
        if (r3 < 1.0E-4D) {
            return limit;
        }
        double inv = 1.0D / (r3 * r3);
        double gw = qx * qx * inv / extentW;
        double gc = qy * qy * inv / extentC;
        double gradient = Math.max(Math.sqrt(gw * gw + gc * gc), 1.0E-6D);
        return Math.max((r3 - scale) / gradient, limit);
    }

    static double roundedBox2(double a, double b, double round) {
        double ra = a + round;
        double rb = b + round;
        double outside = Math.hypot(Math.max(ra, 0.0D), Math.max(rb, 0.0D));
        return outside + Math.min(Math.max(ra, rb), 0.0D) - round;
    }

    static double roundedBox3(double a, double b, double c, double round) {
        double ra = a + round;
        double rb = b + round;
        double rc = c + round;
        double ox = Math.max(ra, 0.0D);
        double oy = Math.max(rb, 0.0D);
        double oz = Math.max(rc, 0.0D);
        return Math.sqrt(ox * ox + oy * oy + oz * oz)
                + Math.min(Math.max(ra, Math.max(rb, rc)), 0.0D) - round;
    }

    static double sminWeight(double a, double b, double k) {
        return clamp01(0.5D + 0.5D * (b - a) / k);
    }

    static double smin(double a, double b, double k, double weightA) {
        return lerp(b, a, weightA) - k * weightA * (1.0D - weightA);
    }

    static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0D - 2.0D * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
