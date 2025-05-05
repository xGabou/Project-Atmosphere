package net.Gabou.projectatmosphere.modules.storm.manager;

import net.Gabou.projectatmosphere.command.SpawnCloudCommand;
import net.Gabou.projectatmosphere.modules.storm.forecast.StormForecast;
import net.Gabou.projectatmosphere.modules.storm.spike.StormSpikeManager;
import net.Gabou.projectatmosphere.modules.storm.util.DailyStormGenerator;
import net.Gabou.projectatmosphere.modules.storm.util.StormProfileManager;
import net.Gabou.projectatmosphere.modules.storm.util.StormStorageManager;
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

public class StormManager {
    private static BlockPos lastCenter;

    public static void init(ServerLevel world, BlockPos center) {
        AsyncAtmosphereService.runStorm(() -> {
            lastCenter = center;

            Map<ResourceLocation, double[]> forecasts =
                    StormForecast.generateStormForecastAround(world, lastCenter, DEFAULT_RADIUS);

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
        });

    }

    public static void onPlayerJoined(ServerLevel world, BlockPos center) {
        init(world, center);
    }

    public static double getCurrentStormIntensity(ResourceLocation biome, long worldTick) {
        return StormProfileManager.getCurrentStormIntensity(biome, worldTick);
    }

    public static double[] getWeeklyForecast(ResourceLocation biome) {
        return StormProfileManager.getWeeklyForecast(biome);
    }

    public static void onPrecomputeProfiles(Level world) {
        AsyncAtmosphereService.runStorm(() ->
        DailyStormGenerator.scheduleGenerationForTodayAndTomorrow(world));
    }

    public static void onSwapProfiles(Level world) {
        AsyncAtmosphereService.runStorm(() -> {
        for (String key : StormProfileManager.getAllBiomeKeys()) {
            ResourceLocation biome = new ResourceLocation(key);
            double[] tom = StormProfileManager.getTomorrowProfile(biome);
            if (tom != null) {
                StormProfileManager.putDayProfile(biome, tom);
            }
        }
        DailyStormGenerator.scheduleGenerationForTodayAndTomorrow(world);});
    }

    public static void onSeasonChange(ServerLevel world) {
        onRegenerate(world, world.players());
    }

    public static float randomStormSpike(ResourceLocation biome, int d) {
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
