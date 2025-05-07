package net.Gabou.projectatmosphere.modules.storm.manager;

import net.Gabou.projectatmosphere.command.SpawnCloudCommand;
import net.Gabou.projectatmosphere.modules.storm.forecast.StormForecast;
import net.Gabou.projectatmosphere.modules.storm.spike.StormSpikeManager;
import net.Gabou.projectatmosphere.modules.storm.util.DailyStormGenerator;
import net.Gabou.projectatmosphere.modules.storm.util.StormProfileManager;
import net.Gabou.projectatmosphere.modules.storm.util.StormStorageManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

public class StormManager {

    public static void init(ServerLevel world, BlockPos center) {

            try {
                    StormForecast.generateStormForecastAround(world, center, DEFAULT_RADIUS);

            DailyStormGenerator.scheduleGenerationForTodayAndTomorrow(world);
            } catch (Exception e) {
                LOGGER.error("Failed to generate storm forecast around " + center, e);
            }


    }

    public static void onPlayerJoined(ServerLevel world, BlockPos center) {
        init(world, center);
    }

    public static float getCurrentStormIntensity(BiomeInstanceKey biome, long worldTick) {
        return StormProfileManager.getCurrentStormIntensity(biome, worldTick);
    }

    public static float[] getWeeklyForecast(BiomeInstanceKey biome) {
        return StormProfileManager.getWeeklyForecast(biome);
    }

    public static void onPrecomputeProfiles(Level world) {

        DailyStormGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    public static void onSwapProfiles(Level world) {
        for (BiomeInstanceKey key : StormProfileManager.getAllBiomeKeys()) {
            float[] tom = StormProfileManager.getTomorrowProfile(key);
            if (tom != null) {
                StormProfileManager.putDayProfile(key, tom);
            }
        }
        DailyStormGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    public static void onSeasonChange(ServerLevel world) {
        onRegenerate(world, world.players());
    }

    public static float randomStormSpike(BiomeInstanceKey biome, int d) {
        return StormSpikeManager.randomStormSpike(biome, d);
    }



    public static void clearForecastCache(ServerLevel world) {
        StormProfileManager.clearAll();
        StormStorageManager.clearCache(world);
    }

    public static void onRegenerate(ServerLevel world, List<ServerPlayer> players) {
        clearForecastCache(world);
        init(world, world.getSharedSpawnPos());
        for (Player player : players) {
            BlockPos pos = player.blockPosition();
            StormManager.onPlayerJoined(world, pos);
        }

    }
    public static void onRegisterCommands(RegisterCommandsEvent event){
        //StormCommand.register(event.getDispatcher());
        SpawnCloudCommand.register(event.getDispatcher());
    }
}
