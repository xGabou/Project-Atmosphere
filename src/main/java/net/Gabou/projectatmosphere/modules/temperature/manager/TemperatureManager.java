package net.Gabou.projectatmosphere.modules.temperature.manager;



import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.storm.manager.StormManager;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommands;
import net.Gabou.projectatmosphere.modules.temperature.core.TemperatureProvider;
import net.Gabou.projectatmosphere.modules.temperature.forecast.TemperatureForecast;
import net.Gabou.projectatmosphere.modules.temperature.util.DailyProfileGenerator;
import net.Gabou.projectatmosphere.modules.temperature.util.ForecastStorageManager;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static net.Gabou.projectatmosphere.ProjectAtmosphere.DEFAULT_RADIUS;

public class TemperatureManager{


    private static BlockPos lastCenter = BlockPos.ZERO;

    /** Called once on server startup to generate initial forecast around spawn. */
    public static void initTemperatureForServer(ServerLevel server, BlockPos spawn) {
        //init(server, spawn);
    }



    /** Called when a player joins; triggers forecast generation around them. */
    public static void onPlayerJoined(ServerLevel world, BlockPos playerPos) {
        init(world, playerPos);
    }

    /**
     * Central forecast generation method.
     * If biomes near position have not been cached, they will be generated and persisted.
     */
    private static void init(ServerLevel world, BlockPos center) {
        AsyncAtmosphereService.runTemperature(() -> {
            lastCenter = center;

            Map<BiomeInstanceKey, float[][]> forecasts =
                    TemperatureForecast.generateForecastAround(world, center, DEFAULT_RADIUS);

            if (forecasts.isEmpty()) {
                world.getServer().sendSystemMessage(Component.literal("""
                ⚠️ CRITICAL ERROR: No biomes found in the area!
                Use /temperature regenerate to force a new forecast.
                Or make sure you're in the Overworld.
            """));
                return;
            }

            // Only store new forecasts that are not already present
            forecasts.forEach((biome, week) -> {
                if (!TemperatureProfileManager.hasWeeklyForecast(biome)) {
                    TemperatureProfileManager.putWeeklyForecast(biome, week);
                }
            });

            DailyProfileGenerator.scheduleGenerationForTodayAndTomorrow(world);
        });

    }

    /** Clears the cached temperature profiles (weekly + daily). */
    public static void clearForecastCache(ServerLevel world) {
        TemperatureProfileManager.clearAll();
        ForecastStorageManager.clearCache(world);
    }

    public static BlockPos getLastCenter() {
        return lastCenter;
    }
    public static float getCurrentTemperature(BiomeInstanceKey biome, long worldTick) {
        return TemperatureProfileManager.getCurrentTemperature(biome, worldTick);
    }

    public static float[][] getWeeklyForecast(BiomeInstanceKey biome) {
        return TemperatureProfileManager.getWeeklyForecast(biome);
    }

    public static void onSwapProfiles(Level world) {
        AsyncAtmosphereService.runTemperature(() -> {
        for (BiomeInstanceKey key : TemperatureProfileManager.getAllBiomeKeys()) {
            float[] tomorrow = TemperatureProfileManager.getTomorrowProfile(key);
            if (tomorrow != null) {
                TemperatureProfileManager.putDayProfile(key, tomorrow);
            }
        }
        DailyProfileGenerator.scheduleGenerationForTodayAndTomorrow(world);
        });
    }

    public static void onPrecomputeProfiles(Level world) {
        AsyncAtmosphereService.runTemperature(() ->DailyProfileGenerator.scheduleGenerationForTodayAndTomorrow(world));

    }

    public static void onSeasonChange(ServerLevel world) {
        //TODO make sure we dont stack forecasts
        onRegenerate(world, world.players());
    }


    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TemperatureCommands.register(event.getDispatcher());
    }

    public static void onRegenerate(ServerLevel world, List<ServerPlayer> players) {
        clearForecastCache(world);
        //init(world, world.getSharedSpawnPos());
        for (Player player : players) {
            BlockPos pos = player.blockPosition();
            onPlayerJoined(world, pos);
        }

    }

    public static void updateForecastAround(ServerLevel world, BlockPos center) {
        init(world, center);
    }
}