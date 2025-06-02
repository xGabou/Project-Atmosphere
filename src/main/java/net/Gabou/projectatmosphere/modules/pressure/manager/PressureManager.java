// src/main/java/net/Gabou/projectatmosphere/modules/pressure/manager/PressureManager.java
package net.Gabou.projectatmosphere.modules.pressure.manager;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.humidity.Command.HumidityCommand;
import net.Gabou.projectatmosphere.modules.pressure.Command.PressureCommand;
import net.Gabou.projectatmosphere.modules.pressure.forecast.PressureForecast;
import net.Gabou.projectatmosphere.modules.pressure.util.DailyPressureGenerator;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureProfileManager;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureStorageManager;
import net.Gabou.projectatmosphere.modules.storm.manager.StormManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static net.Gabou.projectatmosphere.ProjectAtmosphere.DEFAULT_RADIUS;
import static net.Gabou.projectatmosphere.ProjectAtmosphere.LOGGER;

public class PressureManager {

    /** Radius (in blocks) used to scan for biomes */

    public static void onServerStopping(ServerLevel world) {
        PressureStorageManager.saveAll(world);
        clearForecastCache(world);
    }

    /** Called on server spawn or when regenerating around a player. */
    public static void init(ServerLevel world, BlockPos center) {

                {
                    try {
                        PressureForecast.generateFullForecast(world, center, DEFAULT_RADIUS);
                    } catch (Exception e) {
                        LOGGER.error("Failed to generate pressure forecast around " + center, e);
                    }
                }



    }


    public static void tickSystem(ServerLevel world) {
        PressureForecast.cleanupInactiveBiomes(world,DEFAULT_RADIUS);
    }



    /** Called when a player (re)enters to regenerate missing forecasts around them */
    public static void onPlayerJoined(ServerLevel world, BlockPos center) {
        init(world, center);
    }

    /** Called at tick 18000 to precompute both today & tomorrow */
    public static void onPrecomputeProfiles(Level world) {

        DailyPressureGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    /** Called at tick 21000 (3 AM) to swap tomorrow→today, then precompute next tomorrow */
    public static void onSwapProfiles(Level world) {
        PressureProfileManager.swapTomorrowToToday();
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

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PressureCommand.register(event.getDispatcher());
    }

    public static void updateForecastAround(ServerLevel world, BlockPos center) {
        init(world, center);
    }
}
