package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/**
 * Pure reference contract for the shader's locally-supported rain shafts.
 * Keeping the acceptance and quadrature rules here gives the standalone
 * validation harness deterministic coverage without touching render state.
 */
final class VolumetricPrecipitationModel {
    /** Render-thread CPU mirror of the shader storm composition. */
    private static final StormFieldSampler STORM_FIELD = StormFieldSampler.production();

    private static final double FIRST_GAUSS_SAMPLE = 0.21132486540518713D;
    private static final double SECOND_GAUSS_SAMPLE = 0.7886751345948129D;

    private VolumetricPrecipitationModel() {
    }

    static boolean rainEligible(double maxPrecipitation, double localPrecipitation, double localSupport) {
        return maxPrecipitation > 0.02D
                && localPrecipitation > 0.02D
                && localSupport > 0.01D;
    }

    static double stormAttachmentY(
            StormRenderSnapshot snapshot,
            double worldX,
            double worldZ,
            double fallbackY
    ) {
        return ClientCloudVisualDensity.sampleStormUnderside(
                snapshot, worldX, worldZ, fallbackY
        );
    }

    /** Rain support is the visible union sampled inside the local BASE column. */
    static double stormBodySupport(
            StormRenderSnapshot snapshot,
            double worldX,
            double worldZ,
            double fallbackY
    ) {
        if (snapshot == null || snapshot.descriptorCount() == 0) {
            return 0.0D;
        }
        StormLobeDescriptor[] descriptors = snapshot.descriptorsUnsafe();
        double supportY = StormLobeEvaluator.localBaseSupportHeightAt(
                descriptors, worldX, worldZ
        );
        if (!Double.isFinite(supportY)) {
            return 0.0D;
        }
        // Rain support comes from the same final storm density the frame
        // displays, so precipitation can never attach to a coverage envelope
        // the noise stages did not fill.
        return STORM_FIELD.densityAt(descriptors, worldX, supportY, worldZ);
    }

    static double shaftDensity(
            double localSupport,
            double localPrecipitation,
            double maxPrecipitation,
            double localBaseY,
            double worldY,
            double maxDepth,
            double streaks
    ) {
        if (!rainEligible(maxPrecipitation, localPrecipitation, localSupport)
                || worldY >= localBaseY) {
            return 0.0D;
        }
        double depth01 = (localBaseY - worldY) / Math.max(1.0D, maxDepth);
        if (depth01 >= 1.0D) {
            return 0.0D;
        }
        double tail = 1.0D - smoothstep(0.72D, 1.0D, depth01);
        return clamp01(localSupport)
                * clamp01(localPrecipitation)
                * tail
                * mix(0.06D, 0.14D, clamp01(localPrecipitation))
                * clamp01(streaks);
    }

    /** Compatibility overload for existing CPU callers without a frame maximum. */
    static double shaftDensity(
            double localSupport,
            double localPrecipitation,
            double localBaseY,
            double worldY,
            double maxDepth,
            double streaks
    ) {
        return shaftDensity(
                localSupport,
                localPrecipitation,
                localPrecipitation,
                localBaseY,
                worldY,
                maxDepth,
                streaks
        );
    }

    /** Two-point Gauss integration with fixed, frame-independent positions. */
    static double integrateCoarseSegment(
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ,
            SampleFunction sampler
    ) {
        double first = sampler.sample(
                mix(startX, endX, FIRST_GAUSS_SAMPLE),
                mix(startY, endY, FIRST_GAUSS_SAMPLE),
                mix(startZ, endZ, FIRST_GAUSS_SAMPLE)
        );
        double second = sampler.sample(
                mix(startX, endX, SECOND_GAUSS_SAMPLE),
                mix(startY, endY, SECOND_GAUSS_SAMPLE),
                mix(startZ, endZ, SECOND_GAUSS_SAMPLE)
        );
        return (first + second) * 0.5D;
    }

    static boolean clearAirMaySkip(
            boolean bodySupport,
            boolean rainSupport,
            boolean funnelSupport,
            boolean directStormSupport
    ) {
        return !bodySupport && !rainSupport && !funnelSupport && !directStormSupport;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / Math.max(1.0E-9D, edge1 - edge0));
        return t * t * (3.0D - 2.0D * t);
    }

    private static double mix(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    @FunctionalInterface
    interface SampleFunction {
        double sample(double x, double y, double z);
    }
}
