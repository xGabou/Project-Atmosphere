package net.Gabou.projectatmosphere.temperature;

import net.Gabou.projectatmosphere.temperature.event.SeasonTracker;
import net.Gabou.projectatmosphere.temperature.forecast.TemperatureForecast;
import net.Gabou.projectatmosphere.temperature.util.DailyProfileGenerator;
import net.Gabou.projectatmosphere.temperature.util.TemperatureProfileManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Map;

public class TemperatureManager {

    /** Initialize: generate weekly forecasts and schedule daily curves */
    public static void init(Level world, BlockPos center, int radiusBlocks) {
        SeasonTracker.init(world);
        Map<ResourceLocation, float[][]> forecasts =
                TemperatureForecast.generateForecastAround(world, center, radiusBlocks);
        forecasts.forEach(TemperatureProfileManager::putWeeklyForecast);
        DailyProfileGenerator.scheduleGenerationForAllBiomes(world);
    }

    /** Called at 3 AM to build the new day’s profile */
    public static void onMidnight(Level world) {
        DailyProfileGenerator.scheduleGenerationForAllBiomes(world);
    }

    /** Optional: called at 3 PM if you need a “peak day” hook */
    public static void onPeakDay(Level world) {
        // e.g. custom logic on hottest point
    }

    /** Returns the current real-time temperature for the biome at this tick */
    public static float getCurrentTemperature(ResourceLocation biome, long worldTick) {
        return TemperatureProfileManager.getCurrentTemperature(biome, worldTick);
    }

    /** Exposes the cached 7×2 weekly forecast for commands/UI */
    public static float[][] getWeeklyForecast(ResourceLocation biome) {
        return TemperatureProfileManager.getWeeklyForecast(biome);
    }
}
