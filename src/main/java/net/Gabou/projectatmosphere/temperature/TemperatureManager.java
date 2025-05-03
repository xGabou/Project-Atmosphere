package net.Gabou.projectatmosphere.temperature;

import net.Gabou.projectatmosphere.temperature.forcast.TemperatureForecast;
import net.Gabou.projectatmosphere.temperature.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class TemperatureManager {

    private static Map<ResourceLocation, float[][]> forecasts = new HashMap<>();
    /**
     * Set the forecasts map. This is used to set the forecasts after they are generated.
     * @param forecasts The forecasts map to set.
     */
    public static void setForecasts(Map<ResourceLocation, float[][]> forecasts) {
        TemperatureManager.forecasts = forecasts;
    }

    /**
     * Get the forecasts map. This is used to get the forecasts for a given biome.
     * @return The forecasts map.
     */
    public static Map<ResourceLocation, float[][]> getForecasts() {
        return forecasts;
    }

    /**
     * INITIAL SETUP (call this once, e.g. in your mod's setup or on player login):
     * 1) Load any saved forecasts
     * 2) Generate + store fresh weekly forecasts around the player
     * 3) Schedule the daily profile generator for next midnight
     */
    public static void init(Level world, BlockPos center, int radiusBlocks) {
        // 1) Load from disk (async)
        ForecastStorageManager.loadAll();

        // 2) Generate & cache weekly forecasts for surrounding biomes
        setForecasts(TemperatureForecast.generateForecastAround(world, center, radiusBlocks));

        // (ForecastStorageManager.putForecast is already called inside generateForecastAround)

        // 3) Schedule daily profile build at next midnight
        DailyProfileGenerator.scheduleGenerationForAllBiomes(world);
    }

    /**
     * MIDNIGHT TICK HOOK (call this in your tick handler when world.getDayTime()%24000 == 0):
     * Re-schedule the daily profile generation so that day-profiles are built for the new day.
     */
    public static void onMidnight(Level world) {
        DailyProfileGenerator.scheduleGenerationForAllBiomes(world);
    }

    /**
     * REAL-TIME TEMPERATURE (use this everywhere you need the current temp):
     * Reads the pre-generated day-profile and returns the temp at the current tick
     */
    public static float getCurrentTemperature(Level world, BlockPos pos) {
        ResourceLocation biome = world.getBiome(pos).unwrapKey().get().location();
        long tickInDay = world.getDayTime() % 24000L;
        return TemperatureProfileManager.getCurrentTemperature(biome, tickInDay);
    }

    /**
     * WEEKLY FORECAST (for commands / UIs):
     * Returns the [7][2] min/max table for the given biome.
     */
    public static float[][] getWeeklyForecast(ResourceLocation biome) {
        return ForecastStorageManager.getForecast(biome);
    }
}
