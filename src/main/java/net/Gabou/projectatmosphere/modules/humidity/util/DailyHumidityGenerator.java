// src/main/java/net/Gabou/projectatmosphere/modules/humidity/util/DailyHumidityGenerator.java
package net.Gabou.projectatmosphere.modules.humidity.util;

import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class DailyHumidityGenerator {

    public static void scheduleGenerationForTodayAndTomorrow(Level world) {
            long now = world.getDayTime();
            int todayIndex = (int)((now / 24000L) % 7);

            for (BiomeInstanceKey key : HumidityStorageManager.getAllBiomeKeys()) {
                boolean hasToday = HumidityProfileManager.hasDayProfile(key);
                boolean hasTomorrow = HumidityProfileManager.hasTomorrowProfile(key);
                if (hasToday && hasTomorrow) continue;

                float[][] week = HumidityStorageManager.getForecast(key);

                if (!hasToday) {
                    float[] today = buildDailyCurve(week[todayIndex]);
                    HumidityProfileManager.putDayProfile(key, today);
                }
                if (!hasTomorrow) {
                    float[] tomorrow = buildDailyCurve(week[(todayIndex + 1) % 7]);
                    HumidityProfileManager.putTomorrowProfile(key, tomorrow);
                }
            }
    }

    private static float[] buildDailyCurve(float[] minMax) {
        float min = minMax[0];
        float max = minMax[1];
        float[] curve = new float[240];

        for (int i = 0; i < 240; i++) {
            float t = i / 239f;
            float factor;

            if (t < 0.25f) {
                factor = 1f - (float) Math.pow(t * 4f, 0.8); // steep morning drop
            } else if (t < 0.75f) {
                factor = 0.1f + 0.9f * (1f - (float) Math.sin(Math.PI * (t - 0.25f) / 0.5f)); // midday dry
            } else {
                factor = 0.1f + (float) Math.pow((t - 0.75f) * 4f, 0.8); // evening rise
            }

            curve[i] = min + (max - min) * (1f - factor);
        }

        return curve;
    }
}
