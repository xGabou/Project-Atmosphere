package net.Gabou.projectatmosphere.modules.storm.manager;

import net.Gabou.projectatmosphere.modules.storm.forecast.StormForecast;
import net.Gabou.projectatmosphere.modules.storm.spike.StormSpikeManager;
import net.Gabou.projectatmosphere.modules.storm.util.DailyStormGenerator;
import net.Gabou.projectatmosphere.modules.storm.util.StormProfileManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Objects;

public class StormManager {
    private static BlockPos lastCenter;
    public static final int radiusBlocks = 250;

    public static void init(ServerLevel world, BlockPos center, int radius) {
        lastCenter = center;

        Map<ResourceLocation, double[]> forecasts =
                StormForecast.generateStormForecastAround(world, lastCenter, radius);

        if (forecasts.isEmpty()) {
            Objects.requireNonNull(world.getServer())
                    .sendSystemMessage(Component.literal(
                            "WARNING: No biomes found for storm forecasting around " + center
                    ));
            return;
        }

        forecasts.forEach((biome, week) -> {
            if (!StormProfileManager.hasWeeklyForecast(biome)) {
                StormProfileManager.putWeeklyForecast(biome, week);
            }
        });

        DailyStormGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    public static void onPlayerJoined(ServerLevel world, BlockPos center) {
        init(world, center, radiusBlocks);
    }

    public static double getCurrentStormIntensity(ResourceLocation biome, long worldTick) {
        return StormProfileManager.getCurrentStormIntensity(biome, worldTick);
    }

    public static double[] getWeeklyForecast(ResourceLocation biome) {
        return StormProfileManager.getWeeklyForecast(biome);
    }

    public static void onPrecomputeProfiles(Level world) {
        DailyStormGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    public static void onSwapProfiles(Level world) {
        for (String key : StormProfileManager.getAllBiomeKeys()) {
            ResourceLocation biome = new ResourceLocation(key);
            double[] tom = StormProfileManager.getTomorrowProfile(biome);
            if (tom != null) {
                StormProfileManager.putDayProfile(biome, tom);
            }
        }
        DailyStormGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    public static void onSeasonChange(ServerLevel world, BlockPos center) {
        regenerateForecast(world, center);
    }

    public static double randomStormSpike(ResourceLocation biome, int d) {
        return StormSpikeManager.randomStormSpike(biome, d);
    }

    private static void regenerateForecast(ServerLevel world, BlockPos center) {
        StormProfileManager.clearAll();
        init(world, center, radiusBlocks);
    }

    public static void clearForecastCache() {
        StormProfileManager.clearAll();
    }
}
