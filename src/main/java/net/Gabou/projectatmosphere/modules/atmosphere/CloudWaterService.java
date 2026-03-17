package net.Gabou.projectatmosphere.modules.atmosphere;

import net.minecraft.util.Mth;

/**
 * Minimal Stage 4 cloud-water layer. It keeps cloud moisture explicit without
 * replacing the humidity budget as the main driver.
 */
public final class CloudWaterService {
    private CloudWaterService() {
    }

    public static CloudWaterExchange compute(float humidity,
                                             float targetHumidity,
                                             float cloudWater,
                                             float cloudCover,
                                             float rainIntensity) {
        float clampedHumidity = Mth.clamp(humidity, 0f, 1.2f);
        float clampedTarget = Mth.clamp(targetHumidity, 0f, 1.2f);
        float clampedCloudWater = Mth.clamp(cloudWater, 0f, 1.2f);
        float clampedCloudCover = Mth.clamp(cloudCover, 0f, 1f);
        float clampedRain = Mth.clamp(rainIntensity, 0f, 1f);

        float supersaturation = Math.max(0f, clampedHumidity - Math.max(clampedTarget, 0.55f));
        float condensation = supersaturation * (0.04f + clampedCloudCover * 0.06f);
        condensation = Math.min(condensation, clampedHumidity);

        float reEvaporation = Math.max(0f, 0.2f - clampedCloudCover) * clampedCloudWater * 0.02f;
        float precipitationDraw = clampedRain * Math.min(clampedCloudWater, 0.015f + clampedRain * 0.02f);

        return new CloudWaterExchange(
                -condensation + reEvaporation,
                condensation - reEvaporation - precipitationDraw,
                condensation,
                reEvaporation,
                precipitationDraw
        );
    }
}
