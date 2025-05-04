package net.Gabou.projectatmosphere.temperature.variation;

import java.util.Random;

public class VariationGenerator {

    private static final Random RANDOM = new Random();

    /**
     * Applies daily-level natural variations to an existing 7-day forecast.
     * These simulate unpredictable changes like wind shifts or small cold fronts.
     *
     * @param week Original 7-day forecast (float[7][2], where [i][0] is min and [i][1] is max)
     * @return A new forecast array with daily fluctuations applied
     */
    public static float[][] applyVariationToWeek(float[][] week) {
        float[][] newWeek = new float[7][2];
        for (int i = 0; i < 7; i++) {
            newWeek[i][0] = week[i][0];
            newWeek[i][1] = week[i][1];
        }


        // Apply local fluctuations to each day
        for (int i = 0; i < 7; i++) {
            if (RANDOM.nextFloat() < 0.9f) { // 90% chance to apply natural variation
                float delta = getNaturalDailyVariation();

                newWeek[i][0] += delta;
                newWeek[i][1] += delta;

                // Smooth neighbors slightly
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

        return newWeek;
    }

    /**
     * Picks a weighted variation offset (in °C) biased toward small changes.
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