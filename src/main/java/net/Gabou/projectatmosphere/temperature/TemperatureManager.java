package net.Gabou.projectatmosphere.temperature;

import net.Gabou.projectatmosphere.temperature.event.SeasonTracker;
import net.Gabou.projectatmosphere.temperature.forecast.TemperatureForecast;
import net.Gabou.projectatmosphere.temperature.util.DailyProfileGenerator;
import net.Gabou.projectatmosphere.temperature.util.ForecastStorageManager;
import net.Gabou.projectatmosphere.temperature.util.TemperatureProfileManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import sereneseasons.api.season.Season;

import java.util.Map;
import java.util.Objects;

public class TemperatureManager {

    /** The radius of the area to scan for biomes when generating forecasts */
    public static final int radiusBlocks = 250;
    /** Initialize: generate weekly forecasts and schedule daily curves */
    public static void init(Level world, BlockPos center, int radiusBlocks) {
        Map<ResourceLocation, float[][]> forecasts =
                TemperatureForecast.generateForecastAround(world, center, radiusBlocks);
        //TODO test this
        if(forecasts.isEmpty()) {
            Objects.requireNonNull(world.getServer()).sendSystemMessage(Component.literal("CRITICAL ERROR: No biomes found in the area! \n Use command /temperature regenerate to force a new forecast.\n or make sure you are in the overworld."));
            return;
        }
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

    public static void onSeasonChange(Level world,BlockPos center) {
        regenerateForecast(world,center);
    }

    private static void regenerateForecast(Level world,BlockPos center) {
        ForecastStorageManager.clearCache();
        TemperatureProfileManager.clearAll();
        TemperatureManager.init(world, center,
                radiusBlocks);
    }

    public static void clearForecastCache(Level world, BlockPos center) {
        regenerateForecast(world,center);
    }
}
