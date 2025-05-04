// net/Gabou/projectatmosphere/humidity/manager/HumidityManager.java
package net.Gabou.projectatmosphere.modules.humidity.manager;

import net.Gabou.projectatmosphere.modules.humidity.forecast.HumidityForecast;
import net.Gabou.projectatmosphere.modules.humidity.util.DailyHumidityGenerator;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityProfileManager;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityStorageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;

public class HumidityManager {
    public static final int RADIUS = 250;

    /** Called on server spawn or when regenerating around a player. */
    public static void init(ServerLevel world, BlockPos center) {
        // 1) generate or load weekly forecasts
        Map<ResourceLocation, float[][]> forecasts =
                HumidityForecast.generateForecastAround(world, center, RADIUS);

        // 2) cache into profiles
        forecasts.forEach((biome, week) -> {
            if (!HumidityProfileManager.hasWeeklyForecast(biome)) {
                HumidityProfileManager.putWeeklyForecast(biome, week);
            }
        });

        // 3) schedule daily curve generation
        DailyHumidityGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    public static void onPlayerJoined(ServerLevel world, BlockPos center) {
        init(world, center);
    }
    public static void onPrecomputeProfiles(ServerLevel world) {
        DailyHumidityGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }


    /** Returns current real-time humidity % at this tick. */
    public static float getCurrentHumidity(ResourceLocation biome, long worldTick) {
        return HumidityProfileManager.getCurrentHumidity(biome, worldTick);
    }

    /** Exposes the raw 7×2 weekly humidity forecast. */
    public static float[][] getWeeklyForecast(ResourceLocation biome) {
        return HumidityProfileManager.getWeeklyForecast(biome);
    }

    /** Clears all cached humidity data and regenerates around center. */
    public static void clearForecastCache(ServerLevel world, BlockPos center) {
        HumidityStorageManager.clearCache();
        HumidityProfileManager.clearAll();
        init(world, center);
    }

    /** Swap tomorrow→today profiles at day boundary. */
    public static void onSwapProfiles(ServerLevel world) {
        HumidityProfileManager.swapTomorrowToToday();
        DailyHumidityGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }
}
