package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

/**
 * Performance-mode table for the volumetric cloud pipeline. LOW stays on the
 * GL 3.2 baseline (no compute analytics); MEDIUM and above may use the GL 4.3
 * analytics pass when the GPU supports it.
 */
public record VolumetricQualityProfile(
        int raymarchSteps,
        float resolutionScale,
        int lightSteps,
        int scatterOctaves,
        int detailQuality,
        int weatherMapSize,
        boolean temporalEnabled,
        int shadowUpdateInterval,
        boolean analyticsEnabled
) {
    // Rank 1 internal-resolution ladder, from the measured quality/performance
    // frontier in validation/performance-internal-resolution-frontier.md. Every
    // scale here is a point that was actually measured on one fixture across
    // seven poses; none is interpolated. Cost falls as pixels^0.49-0.75, so the
    // ladder is a resolution ladder rather than a step-count one - T136 showed
    // the step budget has weak leverage, and it is left untouched.
    //
    // The frontier's binding constraint is not T098a, which passes at every
    // scale down to 0.125 with a centre-column share of 1.0000 and a 0 px inner
    // sky run. It is silhouette softening: mean displacement against the old
    // 0.75 grows 1.0 -> 2.4 -> 3.5 -> 5.9 px at 0.50 -> 0.25 -> 0.1875 -> 0.125.
    private static final VolumetricQualityProfile LOW =
            new VolumetricQualityProfile(24, 0.125F, 3, 1, 0, 256, false, 8, false);
    private static final VolumetricQualityProfile LOW_24 =
            new VolumetricQualityProfile(32, 0.125F, 4, 2, 1, 384, true, 6, false);
    private static final VolumetricQualityProfile MEDIUM =
            new VolumetricQualityProfile(40, 0.125F, 5, 3, 1, 512, true, 4, true);
    private static final VolumetricQualityProfile HIGH =
            new VolumetricQualityProfile(64, 0.1875F, 6, 3, 1, 512, true, 2, true);
    private static final VolumetricQualityProfile ULTRA =
            new VolumetricQualityProfile(96, 0.25F, 6, 3, 2, 512, true, 1, true);

    public static VolumetricQualityProfile forQuality(AtmoCommonConfig.CloudRaymarchQuality quality) {
        AtmoCommonConfig.CloudRaymarchQuality safeQuality = quality == null
                ? AtmoCommonConfig.CloudRaymarchQuality.MEDIUM
                : quality;
        return switch (safeQuality) {
            case LOW -> LOW;
            case LOW_24 -> LOW_24;
            case MEDIUM -> MEDIUM;
            case HIGH -> HIGH;
            case ULTRA -> ULTRA;
        };
    }

    public VolumetricQualityProfile withResolutionScale(float newResolutionScale) {
        return new VolumetricQualityProfile(
                raymarchSteps,
                newResolutionScale,
                lightSteps,
                scatterOctaves,
                detailQuality,
                weatherMapSize,
                temporalEnabled,
                shadowUpdateInterval,
                analyticsEnabled
        );
    }
}
