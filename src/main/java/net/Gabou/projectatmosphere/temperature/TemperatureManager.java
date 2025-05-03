package net.Gabou.projectatmosphere.temperature;

import net.Gabou.projectatmosphere.temperature.event.SeasonTracker;
import net.Gabou.projectatmosphere.temperature.forcast.TemperatureForecast;
import net.Gabou.projectatmosphere.temperature.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class TemperatureManager {

    // Keep track of world-based day indices (0 to 6)
    private static final Map<Integer, Integer> WEEKDAY_INDEX = new HashMap<>();


    /**
     * INITIAL SETUP (call this once, e.g. in your mod's setup or on player login):
     * 1) Load any saved forecasts
     * 2) Generate + store fresh weekly forecasts around the player
     * 3) Schedule the daily profile generator for next midnight
     */
    public static void init(Level world, BlockPos center, int radiusBlocks) {
        SeasonTracker.tick(world);
        // 1) Load from disk (async)
        ForecastStorageManager.loadAll();

        // 2) Generate & cache weekly forecasts for surrounding biomes
       TemperatureForecast.generateForecastAround(world, center, radiusBlocks);

        // (ForecastStorageManager.putForecast is already called inside generateForecastAround)

        // 3) Schedule daily profile build at next midnight
        DailyProfileGenerator.scheduleGenerationForAllBiomes(world);
    }

    /**
     * MIDNIGHT TICK HOOK (call this in your tick handler when world.getDayTime()%24000 == 0):
     * Re-schedule the daily profile generation so that day-profiles are built for the new day.
     */
    public static void onMidnight(Level world) {
        //if (!ProjectAtmosphereConfig.enableTemperatureSystem()) return;
        SeasonTracker.tick(world);
        DailyProfileGenerator.scheduleGenerationForAllBiomes(world);
    }
    public static void regenerateWeeklyForecast(Level world) {
        BlockPos center = world.getSharedSpawnPos(); // or player position if needed
        int radius = 250; // or from config
        TemperatureForecast.generateForecastAround(world, center, radius);
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


    /**
     * Call this at 3:00 AM every day.
     */
    private static void advanceDay(Level world) {
        int worldId = world.dimension().location().hashCode(); // world-specific ID
        int current = WEEKDAY_INDEX.getOrDefault(worldId, 0);
        int next = (current + 1) % 7;

        WEEKDAY_INDEX.put(worldId, next);

        if (next == 0) {
            // It's the last day, regenerate forecast
            regenerateWeeklyForecast(world);
        }
    }

    public static int getDayIndex(Level world) {
        int worldId = world.dimension().location().hashCode(); // world-specific ID
        return WEEKDAY_INDEX.getOrDefault(worldId, 0);
    }

    public static void reset(Level world) {
        WEEKDAY_INDEX.remove(world.dimension().location().hashCode());
    }
}
