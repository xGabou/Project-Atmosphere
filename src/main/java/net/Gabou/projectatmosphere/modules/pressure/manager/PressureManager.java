// src/main/java/net/Gabou/projectatmosphere/modules/pressure/manager/PressureManager.java
package net.Gabou.projectatmosphere.modules.pressure.manager;

import net.Gabou.projectatmosphere.modules.pressure.forecast.PressureForecast;
import net.Gabou.projectatmosphere.modules.pressure.util.DailyPressureGenerator;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureProfileManager;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureStorageManager;
import net.Gabou.projectatmosphere.modules.storm.manager.StormManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static net.Gabou.projectatmosphere.ProjectAtmosphere.DEFAULT_RADIUS;

public class PressureManager {
    /** Radius (in blocks) used to scan for biomes */


    private static BlockPos lastCenter;

    /** Initialize weekly forecast & schedule daily curves */
    public static void init(Level world, BlockPos center) {
        lastCenter = center;
        Map<ResourceLocation, float[]> raw = new PressureForecast().generateForecastAround(world, center, DEFAULT_RADIUS);
        if (raw.isEmpty()) {
            Objects.requireNonNull(world.getServer())
                    .sendSystemMessage(Component.literal(
                            "CRITICAL ERROR: No biomes found for pressure forecast!"));
            return;
        }
        // Store as [7][2] trivial min/max (identical endpoints)
        raw.forEach((biome, week) -> {
            if (!PressureProfileManager.hasWeeklyForecast(biome)) {
                float[][] weekRange = new float[7][2];
                for (int d = 0; d < 7; d++) {
                    weekRange[d][0] = week[d];
                    weekRange[d][1] = week[d];
                }
                PressureProfileManager.putWeeklyForecast(biome, weekRange);
            }
        });
        DailyPressureGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    /** Called when a player (re)enters to regenerate missing forecasts around them */
    public static void onPlayerJoined(Level world, BlockPos center) {
        init(world, center);
    }

    /** Called at tick 18000 to precompute both today & tomorrow */
    public static void onPrecomputeProfiles(Level world) {
        DailyPressureGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    /** Called at tick 21000 (3 AM) to swap tomorrow→today, then precompute next tomorrow */
    public static void onSwapProfiles(Level world) {
        for (String key : PressureProfileManager.getAllBiomeKeys()) {
            ResourceLocation biome = new ResourceLocation(key);
            float[] tom = PressureProfileManager.getTomorrowProfile(biome);
            if (tom != null) {
                PressureProfileManager.putDayProfile(biome, tom);
            }
        }
        DailyPressureGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    /** Clears all cached profiles (will regenerate on next init) */
    public static void clearForecastCache(ServerLevel world) {

        PressureProfileManager.clearAll();
        PressureStorageManager.clearCache(world);
    }

    public static void onSeasonChange(ServerLevel world) {
        onRegenerate(world, world.players());

    }

    public static void onRegenerate(ServerLevel world, List< ServerPlayer > players) {
            clearForecastCache(world);
            init(world, world.getSharedSpawnPos());
            for (Player player : players) {
                BlockPos pos = player.blockPosition();
                PressureManager.onPlayerJoined(world, pos);
            }


    }
}
