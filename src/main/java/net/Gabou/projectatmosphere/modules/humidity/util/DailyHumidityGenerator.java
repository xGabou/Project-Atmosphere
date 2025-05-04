// src/main/java/net/Gabou/projectatmosphere/modules/humidity/util/DailyHumidityGenerator.java
package net.Gabou.projectatmosphere.modules.humidity.util;

import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class DailyHumidityGenerator {

    public static void scheduleGenerationForTodayAndTomorrow(Level world) {
        AsyncAtmosphereService.runHumidity(() -> {
            long now = world.getDayTime();
            int todayIndex = (int)((now / 24000L) % 7);

            for (String key : HumidityStorageManager.getAllBiomeKeys()) {
                ResourceLocation biome = new ResourceLocation(key);

                boolean hasToday = HumidityProfileManager.hasDayProfile(biome);
                boolean hasTomorrow = HumidityProfileManager.hasTomorrowProfile(biome);
                if (hasToday && hasTomorrow) continue;

                float[][] week = HumidityStorageManager.getForecast(biome);

                if (!hasToday) {
                    float[] today = buildDailyCurve(week[todayIndex]);
                    HumidityProfileManager.putDayProfile(biome, today);
                }
                if (!hasTomorrow) {
                    float[] tomorrow = buildDailyCurve(week[(todayIndex + 1) % 7]);
                    HumidityProfileManager.putTomorrowProfile(biome, tomorrow);
                }
            }
        });
    }

    private static float[] buildDailyCurve(float[] minMax) {
        // simple cosine‐based interpolation between minMax[0]→minMax[1]
        float min = minMax[0];
        float max = minMax[1];
        float[] curve = new float[240];
        for (int i = 0; i < 240; i++) {
            // map i∈[0,239] to θ∈[0,π]
            float θ = ((i / 239f) * (float)Math.PI);
            float factor = (1 - (float)Math.cos(θ)) * 0.5f;
            curve[i] = min + (max - min) * factor;
        }
        return curve;
    }
}
