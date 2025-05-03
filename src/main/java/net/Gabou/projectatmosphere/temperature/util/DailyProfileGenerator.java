package net.Gabou.projectatmosphere.temperature.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class DailyProfileGenerator {

    public static void scheduleGenerationForAllBiomes(Level world) {
        AsyncTemperatureService.runAsync(() -> {
            long worldTick = world.getDayTime();
            for (String key : ForecastStorageManager.getAllBiomeKeys()) {
                ResourceLocation biome = new ResourceLocation(key);
                generateDayProfile(biome, world, worldTick);
            }
        });
    }

    private static void generateDayProfile(
            ResourceLocation biome, Level world, long worldTick) {

        float[][] week = ForecastStorageManager.getForecast(biome);
        if (week == null) return;

        int todayIndex = (int)((worldTick / 24000L) % 7);
        float min = week[todayIndex][0], max = week[todayIndex][1];
        float[] dayProfile = new float[240];

        for (int i = 0; i < 240; i++) {
            float theta = (i / 239f) * (float)Math.PI;
            float factor = (1 - (float)Math.cos(theta)) * 0.5f;
            dayProfile[i] = min + (max - min) * factor;
        }

        TemperatureProfileManager.putDayProfile(biome, dayProfile);
    }
}
