package net.Gabou.projectatmosphere.clouds.client.render.field;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

/**
 * CloudField-specific quality settings. The serialized configuration continues
 * to use the existing quality enum, while the inactive legacy cloud renderer
 * keeps its own values unchanged.
 */
public record CloudFieldQualityProfile(
        int raymarchSteps,
        float resolutionScale,
        int maxCloudFields,
        int detailOctaves,
        int cloudletBudget
) {
    private static final CloudFieldQualityProfile LOW = new CloudFieldQualityProfile(16, 0.50F, 4, 1, 16);
    private static final CloudFieldQualityProfile LOW_24 = new CloudFieldQualityProfile(24, 0.60F, 6, 2, 24);
    private static final CloudFieldQualityProfile MEDIUM = new CloudFieldQualityProfile(32, 0.70F, 8, 2, 32);
    private static final CloudFieldQualityProfile HIGH = new CloudFieldQualityProfile(48, 0.82F, 12, 3, 48);
    private static final CloudFieldQualityProfile ULTRA = new CloudFieldQualityProfile(64, 1.00F, 16, 4, 64);

    public static CloudFieldQualityProfile forQuality(AtmoCommonConfig.CloudRaymarchQuality quality) {
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
}
