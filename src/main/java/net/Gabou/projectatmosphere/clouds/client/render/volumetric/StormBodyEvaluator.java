package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/**
 * T240. CPU mirror of the continuous storm body's signed distance
 * ({@code stormBodyGroupField} in cloud_atmosphere_volume.fsh), evaluated from
 * a {@link StormBodySkeleton} block. Same equations, same constants; the
 * camera-density path and the morphology sandboxes read the body through it.
 *
 * <p>The body (T239 accepted, T241 shape polish):
 * <ul>
 * <li>base - a flat-bottomed slab, 0.75 of the BASE lobes' height, footprint a
 * superellipse (n = 3) over the separate upwind / downwind / left / right
 * reach (scaled by the skeleton to the tower's 1.3 Rt foot), radius scale
 * 1 - 0.20 t^2, corners rounded by 0.18 of its height;</li>
 * <li>updraft - one column along the leaning tower axis with no waist: radius
 * Rt * (1.30 + 0.15 smoothstep(0.3, 1.2, t)), t from the base crown (0) to the
 * deck bottom (1), rising into the deck to 0.25 of its span below the top,
 * carrying cauliflower turrets - a staggered sphere lattice (5 around, rows
 * 0.8 Rt apart, radius 0.42 Rt) smooth-unioned at 0.12 Rt;</li>
 * <li>deck - a thin flat-topped sheet over u = (w - deckLo) / length:
 * thickness span * (0.38 - 0.18 u), top sinking 0.05 span downwind; in plan an
 * exact rounded trapezoid (wedge), Rt wide at the upwind root and
 * max(1.1 deck half-width, 1.2 Rt) at the downwind edge, following the cross
 * drift; its underside lowered by 0.22 of the crown-to-deck span at the
 * column and curving up over 1.2 Rt past it;</li>
 * <li>joins - smooth minimum base|column, then |deck, k = 0.6 Rt.</li>
 * </ul>
 * The base term is divided by its analytic gradient magnitude and the
 * turrets and the wedge are exact, so the result is a first-order distance
 * (the march's safe advance and the shared field's dilation rely on it).
 * Every interior (anvil included) takes the structural-continuity fill.
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

    /**
     * Cauliflower turrets on the column: a staggered sphere lattice, TURRET_AROUND
     * per row, rows TURRET_SPACING Rt apart, radius TURRET_RADIUS Rt, centred
     * just inside the column surface, smooth-unioned at TURRET_SMOOTH Rt.
     */
    static final int TURRET_AROUND = 5;
    static final double TURRET_SPACING = 0.80D;
    static final double TURRET_RADIUS = 0.42D;
    static final double TURRET_SMOOTH = 0.12D;
    /** The column rises into the deck to a quarter of the deck span below its top. */
    static final double COLUMN_TOP_BELOW_DECK_TOP = 0.25D;
    /** Deck thickness over the deck span: 0.38 at the root falling to 0.20 at the tip. */
    static final double DECK_ROOT_THICKNESS = 0.38D;
    static final double DECK_THICKNESS_FALL = 0.18D;
    /** The deck underside's root drop (fraction of crown-to-deck) and its reach past the column (Rt). */
    static final double ROOT_DROP = 0.22D;
    static final double ROOT_REACH = 1.2D;

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

        // Updraft column: no waist, carrying the cauliflower turret lattice.
        double loY = s[o + StormBodySkeleton.TOWER_LO_Y];
        double hiY = s[o + StormBodySkeleton.TOWER_HI_Y];
        double axisT = clamp01((y - loY) / Math.max(hiY - loY, 1.0D));
        double axisX = lerp(s[o + StormBodySkeleton.TOWER_LO_X], s[o + StormBodySkeleton.TOWER_HI_X], axisT);
        double axisZ = lerp(s[o + StormBodySkeleton.TOWER_LO_Z], s[o + StormBodySkeleton.TOWER_HI_Z], axisT);
        double deckBottom = s[o + StormBodySkeleton.DECK_BOTTOM];
        double deckTop = s[o + StormBodySkeleton.DECK_TOP];
        double span = Math.max(deckTop - deckBottom, 20.0D);
        double crown = floorY + baseHeight;
        double columnSpan = Math.max(deckBottom - crown, 1.0D);
        double columnT = (y - crown) / columnSpan;
        double fromAxisX = x - axisX;
        double fromAxisZ = z - axisZ;
        double radial = Math.hypot(fromAxisX, fromAxisZ);
        double dColumn = Math.max(radial - towerRadius * columnScale(columnT),
                Math.max(floorY + 0.10D * baseHeight - y, y - (deckTop - COLUMN_TOP_BELOW_DECK_TOP * span)));
        double dTurrets = turrets(x, y, z, axisX, axisZ, Math.atan2(fromAxisZ, fromAxisX),
                towerRadius, crown, columnSpan);
        double turretK = TURRET_SMOOTH * towerRadius;
        dColumn = smin(dColumn, dTurrets, turretK, sminWeight(dColumn, dTurrets, turretK));

        // Deck sheet: thin, a wedge in plan, its underside lowered at the root.
        double deckLo = s[o + StormBodySkeleton.DECK_LO];
        double deckHi = s[o + StormBodySkeleton.DECK_HI];
        double deckLength = Math.max(deckHi - deckLo, 1.0D);
        double ud = clamp01((w - deckLo) / deckLength);
        double thickness = span * (DECK_ROOT_THICKNESS - DECK_THICKNESS_FALL * ud);
        double top = deckTop - 0.05D * span * ud;
        double bottom = top - thickness;
        double crossMean = s[o + StormBodySkeleton.DECK_CROSS_MEAN];
        double dFootprint = deckWedge(w, c, deckLo, deckHi, crossMean,
                towerRadius, s[o + StormBodySkeleton.DECK_HALF_WIDTH]);
        double deckRound = Math.min(0.20D * thickness, StormBodySkeleton.DECK_ROUND_MAX);
        double rootReach = ROOT_REACH * towerRadius;
        double rootT = clamp01(Math.max(radial - towerRadius, 0.0D) / rootReach);
        double rootDrop = ROOT_DROP * Math.max(deckBottom - crown, 0.0D);
        double rootSlope = rootDrop * 1.5D / rootReach;
        double deckUnder = (bottom - rootDrop * (1.0D - rootT * rootT * (3.0D - 2.0D * rootT)) - y)
                / Math.sqrt(1.0D + rootSlope * rootSlope);
        double dDeck = roundedBox2(dFootprint, Math.max(deckUnder, y - top), deckRound);

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
        out.interiorFill = d < -out.band;
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

    /** Column radius scale over the crown-to-deck coordinate: no waist, a little wider into the deck. */
    static double columnScale(double columnT) {
        return 1.30D + 0.15D * smoothstep(0.3D, 1.2D, columnT);
    }

    /**
     * Distance to the nearest four turret spheres: the two lattice rows
     * around y and the two lattice angles around theta in each. The picked set
     * only changes where its dropped and added spheres are equidistant, so the
     * minimum stays continuous.
     */
    static double turrets(double x, double y, double z, double axisX, double axisZ, double theta,
            double towerRadius, double crown, double columnSpan) {
        double spacing = TURRET_SPACING * towerRadius;
        int rows = Math.max((int) (columnSpan / spacing), 1);
        int row0 = (int) Math.floor((y - crown) / spacing - 0.5D);
        double step = 2.0D * Math.PI / TURRET_AROUND;
        double best = 1.0E9D;
        for (int rowPick = 0; rowPick < 2; rowPick++) {
            int row = Math.max(0, Math.min(rows - 1, row0 + rowPick));
            double rowY = crown + (row + 0.5D) * spacing;
            double rowRadius = towerRadius * (1.28D + 0.15D * smoothstep(0.3D, 1.2D, (rowY - crown) / columnSpan));
            double rowOffset = row * 0.5D * step + 0.9D * (row % 3);
            double around = Math.floor((theta - rowOffset) / step);
            for (int anglePick = 0; anglePick < 2; anglePick++) {
                double angle = rowOffset + (around + anglePick) * step;
                double dx = x - (axisX + rowRadius * Math.cos(angle));
                double dy = y - rowY;
                double dz = z - (axisZ + rowRadius * Math.sin(angle));
                best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz) - TURRET_RADIUS * towerRadius);
            }
        }
        return best;
    }

    /** Widest downwind half-width of the deck wedge. */
    static double wedgeTipHalf(double towerRadius, double deckHalfWidth) {
        return Math.max(1.1D * deckHalfWidth, 1.2D * towerRadius);
    }

    /**
     * The deck footprint: an exact rounded trapezoid (wedge) along the deck
     * axis, half-width Rt at the upwind root and {@link #wedgeTipHalf} at the
     * downwind edge, corners rounded 0.5 Rt, its cross coordinate sheared by
     * the cross drift crossMean * u.
     */
    static double deckWedge(double w, double c, double deckLo, double deckHi, double crossMean,
            double towerRadius, double deckHalfWidth) {
        double deckLength = Math.max(deckHi - deckLo, 1.0D);
        double ud = clamp01((w - deckLo) / deckLength);
        double halfLength = 0.5D * deckLength;
        double corner = 0.5D * towerRadius;
        double qx = Math.abs(c - crossMean * ud);
        double qy = w - (deckLo + halfLength);
        double r1 = towerRadius - corner;
        double r2 = wedgeTipHalf(towerRadius, deckHalfWidth) - corner;
        double he = Math.max(halfLength - corner, 1.0D);
        double k2x = r2 - r1;
        double k2y = 2.0D * he;
        double cax = qx - Math.min(qx, qy < 0.0D ? r1 : r2);
        double cay = Math.abs(qy) - he;
        double t = Math.max(0.0D, Math.min(1.0D, ((r2 - qx) * k2x + (he - qy) * k2y) / (k2x * k2x + k2y * k2y)));
        double cbx = qx - r2 + k2x * t;
        double cby = qy - he + k2y * t;
        double sign = (cbx < 0.0D && cay < 0.0D) ? -1.0D : 1.0D;
        return sign * Math.sqrt(Math.min(cax * cax + cay * cay, cbx * cbx + cby * cby)) - corner;
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
