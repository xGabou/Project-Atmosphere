package net.Gabou.projectatmosphere.modules.temperature.variation;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig;

import java.util.Random;

public class VariationGenerator {

    private static final Random RANDOM = new Random();

    /**
     * Applies daily-level natural variations to an existing 7-day forecast.
     * These simulate unpredictable changes like wind shifts or small cold fronts.
     *
     * @param week Original 7-day forecast (float[7][2], where [i][0] is min and [i][1] is max)
     * @param clamp Seasonal clamp applied after variations (nullable)
     * @return A new forecast array with daily fluctuations applied
     */
    public static float[][] applyVariationToWeek(float[][] week, BiomeTempConfig.DailyRange clamp) {
        float[][] newWeek = new float[7][2];
        for (int i = 0; i < 7; i++) {
            newWeek[i][0] = week[i][0];
            newWeek[i][1] = week[i][1];
        }

        for (int i = 0; i < 7; i++) {
            if (RANDOM.nextFloat() < 0.9f) {
                float delta = getNaturalDailyVariation() * Math.max(0.0f, AtmoCommonConfig.FORECAST_DEVIATION_MULTIPLIER.get().floatValue());

                newWeek[i][0] += delta;
                newWeek[i][1] += delta;

                if (i > 0) {
                    newWeek[i - 1][0] += delta * 0.3f;
                    newWeek[i - 1][1] += delta * 0.3f;
                }
                if (i < 6) {
                    newWeek[i + 1][0] += delta * 0.3f;
                    newWeek[i + 1][1] += delta * 0.3f;
                }
            }
        }

        if (clamp != null) {
            float min = clamp.minMin();
            float max = clamp.maxMax();
            for (int i = 0; i < 7; i++) {
                newWeek[i][0] = clampValue(newWeek[i][0], min, max);
                newWeek[i][1] = clampValue(newWeek[i][1], min, max);
                if (newWeek[i][0] > newWeek[i][1]) {
                    float midpoint = (newWeek[i][0] + newWeek[i][1]) * 0.5f;
                    newWeek[i][0] = midpoint;
                    newWeek[i][1] = midpoint;
                }
            }
        }

        return newWeek;
    }

    private static float clampValue(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * Picks a weighted variation offset (in Â°C) biased toward small changes.
     * Values between -4 and +4, with 0.5-2 being most common.
     */
    private static float getNaturalDailyVariation() {
        float[] weightedPool = new float[] {
                -0.5f, -1f, -1f, -2f, -2f, -2f, -3f, -3f, -4f,
                0.5f,  1f,  1f,  2f,  2f,  2f,  3f,  3f,  4f
        };
        int index = RANDOM.nextInt(weightedPool.length);
        return weightedPool[index];
    }
}
