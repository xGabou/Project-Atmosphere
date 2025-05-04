package net.Gabou.projectatmosphere.modules.storm.manager;

import net.Gabou.projectatmosphere.modules.storm.forecast.StormForecast;
import net.Gabou.projectatmosphere.modules.storm.spike.StormSpikeManager;
import net.Gabou.projectatmosphere.modules.storm.util.DailyStormGenerator;
import net.Gabou.projectatmosphere.modules.storm.util.StormProfileManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Objects;

public class StormManager {

    /** Last center used for sampling biomes */
    private static BlockPos lastCenter;
    /** How far (in blocks) to scan around the player/world spawn */
    public static final int radiusBlocks = 250;

    /**
     * Initialize: generate weekly storm forecasts and schedule daily curves
     */
    public static void init(Level world, BlockPos center, int radius) {
        lastCenter = center;
        // Generate a 7-day storm forecast per biome (e.g. daily mean intensities)
        Map<ResourceLocation, double[]> forecasts =
                StormForecast.generateStormForecastAround(world, lastCenter, radius);

        if (forecasts.isEmpty()) {
            // No overworld biomes, or something went wrong
            Objects.requireNonNull(world.getServer())
                    .sendSystemMessage(Component.literal(
                            "WARNING: No biomes found for storm forecasting around " + center
                    ));
            return;
        }

        // Store weekly forecast into the profile manager
        forecasts.forEach((biome, week) -> {
            if (!StormProfileManager.hasWeeklyForecast(biome)) {
                StormProfileManager.putWeeklyForecast(biome, week);
            }
        });

        // Now kick off async generation of today & tomorrow curves
        DailyStormGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    /** Hook into player join so each player gets forecasts generated around them */
    public static void onPlayerJoined(Level world, BlockPos center) {
        init(world, center, radiusBlocks);
    }

    /** Returns current storm intensity for a biome at this tick */
    public static double getCurrentStormIntensity(ResourceLocation biome, long worldTick) {
        return StormProfileManager.getCurrentStormIntensity(biome, worldTick);
    }

    /** Exposes raw 7-day weekly forecast for UI/commands */
    public static double[] getWeeklyForecast(ResourceLocation biome) {
        return StormProfileManager.getWeeklyForecast(biome);
    }

    /** Called at tick X (e.g. midday) to precompute both today & tomorrow */
    public static void onPrecomputeProfiles(Level world) {
        DailyStormGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    /** Called at tick Y (e.g. 3 AM) to flip tomorrow → today and queue next day */
    public static void onSwapProfiles(Level world) {
        for (String key : StormProfileManager.getAllBiomeKeys()) {
            ResourceLocation biome = new ResourceLocation(key);
            double[] tom = StormProfileManager.getTomorrowProfile(biome);
            if (tom != null) {
                StormProfileManager.putDayProfile(biome, tom);
            }
        }
        // Immediately kick off generation for the *new* tomorrow
        DailyStormGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    /** Called on season change to fully regenerate storm forecasts */
    public static void onSeasonChange(Level world, BlockPos center) {
        regenerateForecast(world, center);
    }
    public static double randomStormSpike(ResourceLocation biome,int d)
    {
        return StormSpikeManager.randomStormSpike(biome,d);
    }

    private static void regenerateForecast(Level world, BlockPos center) {
        // If you have a cache for storm samples, clear it here
        // StormForecastStorageManager.clearCache();
        StormProfileManager.clearAll();
        init(world, center, radiusBlocks);
    }

    /** Clears and re-runs forecasts around the given point */
    public static void clearForecastCache() {
        StormProfileManager.clearAll();
    }
}
