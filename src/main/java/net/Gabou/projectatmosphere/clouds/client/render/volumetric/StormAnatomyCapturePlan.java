package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

/**
 * T258 capture planning only (pure; never changes geometry, renderer settings or weather). Poses are
 * relative to the fixture origin (base at y = 0; the native harness places it at y = 1400).
 *
 * <p>The T257 view table is reused unchanged, but the containment fit uses the EFFECTIVE vertical
 * lens: the options FOV times the player's FOV modifier (spectator flight adds x1.1, so 110 renders
 * as 121). T257 fitted with the option alone; historical galleries keep that framing, T258 does not.
 *
 * <p>Close views ("z4_" + base) keep the base camera position, aim at a fixed fraction of the shared
 * body bounds, and narrow the options FOV so the effective lens is four times tighter.
 */
public final class StormAnatomyCapturePlan {
    private StormAnatomyCapturePlan() {}
    public static final double FLOOR_Y = 650 - 1400;
    public static final java.util.List<String> FULL_VIEWS = java.util.List.of("side", "opposite_side", "front", "back", "above", "below",
            "low_angle", "far", "close", "upwind", "downwind", "crosswind_left", "crosswind_right", "tower", "transition", "canopy_anvil",
            "updraft_oblique", "portrait", "portrait_front");
    public static final java.util.List<String> ZOOM_VIEWS = java.util.List.of("side", "portrait_front", "above", "transition");
    public static final double ZOOM = 4;

    /** T257 base pose (camera xyz, aim xyz) relative to the fixture origin, before fitting. */
    public static double[] basePose(String view) {
        double cx = 650, cy = 390, cz = 0, d = view.startsWith("attribution_") ? 3200 : 2200;
        double x = cx, y = cy, z = cz + d;
        switch (view) {
            case "opposite_side" -> z = cz - d;
            case "front" -> { x = cx - d; z = cz; }
            case "back" -> { x = cx + 2 * d; z = cz; }
            case "above" -> { y = cy + d; z = cz + .2 * d; }
            case "below" -> { y = FLOOR_Y; z = cz + .4 * d; }
            case "low_angle" -> { x = cx - .707 * d; y = FLOOR_Y; z = cz + .707 * d; }
            case "far" -> z = cz + 2 * d;
            case "portrait" -> z = cz + .55 * d;
            case "portrait_front" -> { x = cx - .55 * d; z = cz; }
            case "close" -> z = cz + .75 * d;
            case "attribution_intervals", "attribution_exact" -> { x = cx - d; z = cz; }
            case "updraft_oblique" -> { x = cx - .707 * d; y = cy + .3 * d; z = cz + .707 * d; }
            case "upwind" -> { x = cx - d; y = cy + .12 * d; z = cz; }
            case "downwind" -> { x = cx + 2 * d; y = cy + .12 * d; z = cz; }
            case "crosswind_left" -> { y = cy + .15 * d; z = cz - d; }
            case "crosswind_right" -> { y = cy + .15 * d; z = cz + d; }
            case "tower" -> { cx = 180; cy = 350; x = cx - .50 * d; y = cy + .08 * d; z = cz + .866 * d; }
            case "transition" -> { cy = 650; x = cx - .707 * d; y = cy + .15 * d; z = cz + .707 * d; }
            case "canopy_anvil" -> { cy = 700; x = cx + .707 * 2 * d; y = cy + .4 * d; z = cz + .707 * 2 * d; }
            default -> { if (!view.equals("side")) throw new IllegalArgumentException("Unknown view " + view); }
        }
        return new double[]{x, y, z, cx, cy, cz};
    }

    /** Effective vertical FOV in degrees: options FOV x player FOV modifier. */
    public static double effectiveFov(double optionFov, double modifier) { return optionFov * modifier; }

    /** Fitted pose containing both candidates' bounds with the effective lens (88 % margin, camera floor). */
    public static double[] pose(String view, Bounds a, Bounds b, double effectiveFov, double aspect) {
        return StormMorphologyCapturePlan.fit(basePose(view), a, b, 0, 0, 0, effectiveFov, aspect, FLOOR_Y);
    }

    /** Close view: same camera as the fitted base pose, aim at a feature point of the shared body box. */
    public static double[] zoomPose(String base, Bounds a, Bounds b, double effectiveFov, double aspect) {
        double[] p = pose(base, a, b, effectiveFov, aspect);
        double minX = Math.min(a.minX(), b.minX()), maxX = Math.max(a.maxX(), b.maxX());
        double minY = Math.min(a.minY(), b.minY()), maxY = Math.max(a.maxY(), b.maxY());
        double minZ = Math.min(a.minZ(), b.minZ()), maxZ = Math.max(a.maxZ(), b.maxZ());
        double fy = switch (base) { case "above" -> .55; case "transition" -> .62; default -> .58; };
        // Upper tower / crown, on the storm's vertical axis (x/z at the origin-centred column).
        p[3] = .5 * (Math.max(minX, -400) + Math.min(maxX, 400));
        p[4] = minY + fy * (maxY - minY);
        p[5] = .5 * (Math.max(minZ, -400) + Math.min(maxZ, 400));
        if (base.equals("above")) { p[3] = 0; p[5] = 0; p[4] = minY + .5 * (maxY - minY); }
        return p;
    }

    /** Options FOV giving the requested zoom against the effective base lens (integer option, 30..110). */
    public static int zoomOptionFov(double effectiveBaseFov, double modifier, double zoom) {
        double t = Math.tan(Math.toRadians(effectiveBaseFov) * .5) / zoom;
        return (int) Math.max(30, Math.min(110, Math.round(Math.toDegrees(2 * Math.atan(t)) / modifier)));
    }
    /** Achieved zoom for an integer options FOV. */
    public static double achievedZoom(double effectiveBaseFov, double modifier, int optionFov) {
        return Math.tan(Math.toRadians(effectiveBaseFov) * .5) / Math.tan(Math.toRadians(optionFov * modifier) * .5);
    }
}
