package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.Locale;

/**
 * T150. Decides whether a benchmark pose can possibly contain the storm it
 * claims to measure.
 *
 * <p>A descriptor count above zero is not enough. Three separate measurement
 * runs have been corrupted by a pose that held its descriptors and still
 * rendered an empty sky: T142's {@code PLAY_NEAR}, whose camera sits past the
 * cloud render distance; the T138 resolution ladder, whose fixture decayed
 * mid-set; and two of three T147 runs at {@code PLAY_VIS_NEAR}. Every one of
 * them was found by reading counters after the fact, which is exactly the
 * failure mode a guard has to remove.
 *
 * <p>This is the cheap geometric half of that guard: it needs no GPU work and
 * so can run before a cell begins. It answers whether the storm's bounding
 * cylinder is resident, inside the distance the cloud pass will actually march,
 * and projected into the camera's frustum at more than a token screen
 * footprint. The authoritative half - whether the march really produced cloud
 * samples - is a counter readback the driver takes once per pose; a pose must
 * pass both.
 */
final class StormFixtureVisibility {

    /**
     * Smallest projected footprint a storm pose may have, as a fraction of the
     * frame's half-height subtended by the storm's angular radius. A severe
     * system at the far end of the shipped 2000-block render distance still
     * subtends far more than this; the threshold exists to reject a storm that
     * is technically in frustum but occupies a handful of pixels, not to grade
     * framing.
     */
    static final double MINIMUM_PROJECTED_FRACTION = 0.02D;

    private StormFixtureVisibility() {
    }

    /**
     * One verdict. {@code valid} is the conjunction; the individual flags exist
     * so a rejection says which condition failed rather than only that one did.
     */
    record Verdict(
            boolean descriptorsPresent,
            boolean withinRenderDistance,
            boolean inFrustum,
            double nearestDistanceBlocks,
            double projectedFraction,
            boolean valid,
            String reason
    ) {
        String format() {
            return String.format(Locale.ROOT,
                    "descriptors=%s withinRenderDistance=%s inFrustum=%s"
                            + " nearestDistance=%.1f projectedFraction=%.4f valid=%s reason=%s",
                    descriptorsPresent, withinRenderDistance, inFrustum,
                    nearestDistanceBlocks, projectedFraction, valid, reason);
        }
    }

    /**
     * Evaluates a pose against the resolved fixture.
     *
     * @param descriptorCount    live descriptor count for the adopted system
     * @param centreX            fixture centre, world X
     * @param centreZ            fixture centre, world Z
     * @param baseY              fixture base height
     * @param topY               fixture top height
     * @param horizontalRadius   fixture horizontal radius in blocks
     * @param cameraX            camera position
     * @param cameraY            camera position
     * @param cameraZ            camera position
     * @param yawDegrees         camera yaw, Minecraft convention
     * @param pitchDegrees       camera pitch, Minecraft convention
     * @param verticalFovDegrees vertical field of view
     * @param renderDistance     the distance the cloud pass marches to
     */
    static Verdict evaluate(
            int descriptorCount,
            double centreX, double centreZ, double baseY, double topY, double horizontalRadius,
            double cameraX, double cameraY, double cameraZ,
            double yawDegrees, double pitchDegrees,
            double verticalFovDegrees,
            double renderDistance) {

        boolean descriptorsPresent = descriptorCount > 0;

        // Bounding sphere of the storm's cylinder. Conservative in the
        // direction that matters: it can only make a storm look more visible,
        // so a rejection is never a false one.
        double centreY = (baseY + topY) * 0.5D;
        double halfHeight = Math.max(1.0D, (topY - baseY) * 0.5D);
        double boundRadius = Math.hypot(Math.max(1.0D, horizontalRadius), halfHeight);

        double dx = centreX - cameraX;
        double dy = centreY - cameraY;
        double dz = centreZ - cameraZ;
        double centreDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Range is measured against the storm's actual cylinder, not the
        // bounding sphere. The sphere bulges below the base, and at a camera
        // sitting under the storm that bulge is what decides the test: at
        // PLAY_NEAR it puts the nearest point at 1920 blocks and passes, while
        // the cylinder - the shape the march can actually hit - is 2010 blocks
        // away and past the 2000-block render distance. That difference is the
        // whole defect this guard exists to catch.
        double horizontalGap = Math.max(0.0D,
                Math.hypot(cameraX - centreX, cameraZ - centreZ)
                        - Math.max(1.0D, horizontalRadius));
        double verticalGap = Math.max(0.0D,
                Math.max(baseY - cameraY, cameraY - topY));
        double nearest = Math.hypot(horizontalGap, verticalGap);

        boolean withinRenderDistance = nearest <= renderDistance;

        // Angle between the view direction and the direction to the storm
        // centre, against the frustum's own half-angle widened by the storm's
        // angular radius. This is the standard cone test and is exact enough
        // for a guard: a storm whose centre is outside the cone by more than
        // its own angular radius cannot put anything on screen.
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double viewX = -Math.sin(yaw) * Math.cos(pitch);
        double viewY = -Math.sin(pitch);
        double viewZ = Math.cos(yaw) * Math.cos(pitch);

        double angularRadius = centreDistance <= boundRadius
                ? Math.PI
                : Math.asin(Math.min(1.0D, boundRadius / Math.max(centreDistance, 1.0E-6D)));
        double halfFov = Math.toRadians(verticalFovDegrees) * 0.5D;
        // Widen to the frame diagonal so a storm at the corner is not rejected.
        double halfCone = Math.min(Math.PI, halfFov * 1.9D);

        boolean inFrustum;
        double projectedFraction;
        if (centreDistance <= boundRadius) {
            // The camera is inside the bounding sphere: the storm surrounds it.
            inFrustum = true;
            projectedFraction = 1.0D;
        } else {
            double cosAngle = (dx * viewX + dy * viewY + dz * viewZ)
                    / Math.max(centreDistance, 1.0E-6D);
            double angle = Math.acos(Math.max(-1.0D, Math.min(1.0D, cosAngle)));
            inFrustum = angle - angularRadius <= halfCone;
            projectedFraction = Math.min(1.0D, angularRadius / halfFov);
        }

        boolean footprintSufficient = projectedFraction >= MINIMUM_PROJECTED_FRACTION;

        String reason;
        if (!descriptorsPresent) {
            reason = "no_descriptors";
        } else if (!withinRenderDistance) {
            reason = "beyond_render_distance";
        } else if (!inFrustum) {
            reason = "outside_frustum";
        } else if (!footprintSufficient) {
            reason = "projected_footprint_too_small";
        } else {
            reason = "ok";
        }

        boolean valid = descriptorsPresent && withinRenderDistance && inFrustum && footprintSufficient;
        return new Verdict(descriptorsPresent, withinRenderDistance, inFrustum,
                nearest, projectedFraction, valid, reason);
    }

    /**
     * The authoritative half. A pose is only accepted once the march itself has
     * produced cloud density samples, which no geometric test can stand in for:
     * the corrupted {@code PLAY_VIS_NEAR} cells were inside the render distance
     * and inside the frustum and still marched nothing but empty space.
     *
     * @param cloudDensityCalls density evaluations summed over the target
     * @param marchedPixels     the cloud target's pixel count
     */
    static boolean renderedStormConfirmed(double cloudDensityCalls, int marchedPixels) {
        if (marchedPixels <= 0) {
            return false;
        }
        // One percent of pixels having taken a single density sample. Measured
        // separation is enormous - a failing cell reports exactly zero and the
        // thinnest passing pose, FAR, reports 4.88 per pixel - so the threshold
        // is placed well clear of both rather than tuned.
        return cloudDensityCalls >= marchedPixels * 0.01D;
    }
}
