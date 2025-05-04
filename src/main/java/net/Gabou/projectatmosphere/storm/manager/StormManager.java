// net/Gabou/projectatmosphere/storm/manager/StormManager.java
package net.Gabou.projectatmosphere.storm.manager;

import net.Gabou.projectatmosphere.storm.spike.StormSpikeManager;
import net.Gabou.projectatmosphere.storm.util.DailyStormGenerator;
import net.Gabou.projectatmosphere.storm.util.StormProfileManager;
import net.Gabou.projectatmosphere.storm.util.StormStorageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Map;

public class StormManager {
    public static final int RADIUS = 250;

    public static void init(Level world, BlockPos center) {
        Map<ResourceLocation, double[]> forecasts =
                StormSpikeManager.generateForecastAround(world, center, RADIUS);

        forecasts.forEach((biome, week) -> {
            if (!StormProfileManager.hasWeeklyForecast(biome)) {
                StormProfileManager.putWeeklyForecast(biome, week);
            }
        });

        DailyStormGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    public static double getCurrentSpike(ResourceLocation biome, long worldTick) {
        return StormProfileManager.getCurrentSpike(biome, worldTick);
    }

    public static void clearForecastCache(Level world, BlockPos center) {
        StormStorageManager.clearCache();
        StormProfileManager.clearAll();
        init(world, center);
    }

    public static void onSwapProfiles(Level world) {
        StormProfileManager.swapTomorrowToToday();
        DailyStormGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }
}
