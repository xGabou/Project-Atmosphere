// net/Gabou/projectatmosphere/humidity/util/DailyHumidityGenerator.java
package net.Gabou.projectatmosphere.humidity.util;

import net.Gabou.projectatmosphere.humidity.util.HumidityProfileManager;
import net.Gabou.projectatmosphere.humidity.util.HumidityStorageManager;
import net.Gabou.projectatmosphere.humidity.forecast.HumidityForecast;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class DailyHumidityGenerator {
    public static void scheduleGenerationForTodayAndTomorrow(Level world) {
        AsyncAtmosphereService.runAsync(AsyncAtmosphereService.Branch.HUMIDITY,() -> {
            long now = world.getDayTime();
            for (String key : HumidityStorageManager.getAllBiomeKeys()) {
                ResourceLocation biome = new ResourceLocation(key);
                boolean hasToday = HumidityProfileManager.hasDayProfile(biome);
                boolean hasTomorrow = HumidityProfileManager.hasTomorrowProfile(biome);
                if (hasToday && hasTomorrow) continue;

                float[][] week = HumidityStorageManager.getForecast(biome);
                int todayIndex = (int)((now / 24000L) % 7);
                if (!hasToday) {
                    float[] today = buildDailyCurve(week[todayIndex]);
                    HumidityProfileManager.putDayProfile(biome, today);
                }
                if (!hasTomorrow) {
                    float[] tomorrow = buildDailyCurve(week[(todayIndex+1)%7]);
                    HumidityProfileManager.putTomorrowProfile(biome, tomorrow);
                }
            }
        });
    }

    private static float[] buildDailyCurve(float[] minMax) {
        // TODO: build a 240-step humidity curve between minMax[0] and minMax[1]
        return new float[240];
    }
}
