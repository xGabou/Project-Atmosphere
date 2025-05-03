package net.Gabou.projectatmosphere.temperature.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class DailyProfileGenerator {

    /**
     * Called at midnight (tick 0) to regenerate the 240-entry
     * daily temperature curve for every biome we have a forecast for.
     */
    public static void scheduleGenerationForAllBiomes(Level world) {
        AsyncTemperatureService.runAsync(() -> {
            // Capture the current world tick position so inner tasks remain consistent
            long worldTick = world.getDayTime();
            for (String biomeKey : ForecastStorageManager.getAllBiomeKeys()) {
                ResourceLocation biome = new ResourceLocation(biomeKey);
                generateDayProfile(biome, world, worldTick);
            }
        });
    }

    /**
     * Generates the 240-entry (every 100 ticks = ~10 minutes) day profile
     * based on today's min/max from the weekly forecast.
     *
     * @param biome     the biome ID
     * @param world     the world to sample forecasts from
     * @param worldTick the absolute world tick at generation time
     */
    private static void generateDayProfile(ResourceLocation biome, Level world, long worldTick) {
        float[][] week = ForecastStorageManager.getForecast(biome);
        if (week == null) return;

        // Determine which day index in the 7-day forecast
        long daysSinceEpoch = worldTick / 24000L;
        int todayIndex = (int)(daysSinceEpoch % 7);

        float min = week[todayIndex][0];
        float max = week[todayIndex][1];
        float[] dayProfile = new float[240]; // 240 intervals of 100 ticks

        // Cosine interpolation from min (3am) → max (3pm) → back to min (3am next day)
        for (int i = 0; i < 240; i++) {
            // map i ∈ [0,239] → θ ∈ [0, π]
            float theta = (i / 239f) * (float)Math.PI;
            // value ∈ [0..1], 0@0, 1@π/2, 0@π
            float factor = (1 - (float)Math.cos(theta)) * 0.5f;
            dayProfile[i] = min + (max - min) * factor;
        }

        TemperatureProfileManager.putDayProfile(biome, dayProfile);
    }
}
