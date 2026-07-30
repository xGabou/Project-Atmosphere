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
    private static final VolumetricQualityProfile LOW =
            new VolumetricQualityProfile(24, 0.25F, 3, 1, 0, 256, false, 8, false);
    private static final VolumetricQualityProfile LOW_24 =
            new VolumetricQualityProfile(32, 0.375F, 4, 2, 1, 384, true, 6, false);
    private static final VolumetricQualityProfile MEDIUM =
            new VolumetricQualityProfile(40, 0.50F, 5, 3, 1, 512, true, 4, true);
    private static final VolumetricQualityProfile HIGH =
            new VolumetricQualityProfile(64, 0.50F, 6, 3, 1, 512, true, 2, true);
    private static final VolumetricQualityProfile ULTRA =
            new VolumetricQualityProfile(96, 0.75F, 6, 3, 2, 512, true, 1, true);

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
