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

        float supersaturation = Math.max(0f, clampedHumidity - Math.max(clampedTarget, 0.62f));
        float condensation = supersaturation * (0.018f + clampedCloudCover * 0.032f);
        condensation = Math.min(condensation, clampedHumidity);

        float reEvaporation = Math.max(0f, 0.28f - clampedCloudCover) * clampedCloudWater * 0.012f;
        float precipitationDraw = clampedRain * Math.min(clampedCloudWater, 0.025f + clampedRain * 0.045f);
        float excessWaterDrain = Math.max(0f, clampedCloudWater - 1.0f) * (0.035f + clampedRain * 0.055f);

        return new CloudWaterExchange(
                -condensation + reEvaporation,
                condensation - reEvaporation - precipitationDraw - excessWaterDrain,
                condensation,
                reEvaporation,
                precipitationDraw + excessWaterDrain
        );
    }
}
