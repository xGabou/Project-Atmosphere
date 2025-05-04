package net.Gabou.projectatmosphere.modules.wind.manager;

import net.Gabou.projectatmosphere.modules.wind.forecast.WindForecast;
import net.Gabou.projectatmosphere.modules.wind.util.WindProfileManager;
import net.Gabou.projectatmosphere.modules.wind.util.WindStorageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Map;

public class WindManager {

    public static final int WIND_SPEED = 10;  // Default speed in m/s

    public static void init(ServerLevel world, BlockPos center) {
        Map<ResourceLocation, float[][]> forecast =
                WindForecast.generateForecastAround(world, center, 250);

        forecast.forEach((biome, week) -> {
            if (!WindProfileManager.hasWeeklyForecast(biome)) {
                WindProfileManager.putWeeklyForecast(biome, week);
            }
        });

        WindProfileManager.generateTodayAndTomorrowProfiles(world);
    }

    public static void onPlayerJoined(ServerLevel world, BlockPos pos) {
        init(world, pos);
    }

    public static float[][] getWeeklyForecast(ResourceLocation biome) {
        return WindProfileManager.getWeeklyForecast(biome);
    }

    public static float getCurrentWind(ResourceLocation biome, long worldTick) {
        return WindProfileManager.getCurrentWindSpeed(biome, worldTick);
    }

    public static void onPrecomputeProfiles(ServerLevel world) {
        WindProfileManager.generateTodayAndTomorrowProfiles(world);
    }

    public static void onSwapProfiles(ServerLevel world) {
        for (String key : WindProfileManager.getAllBiomeKeys()) {
            ResourceLocation biome = new ResourceLocation(key);
            float[] tomorrow = WindProfileManager.getTomorrowProfile(biome);
            if (tomorrow != null) {
                WindProfileManager.putDayProfile(biome, tomorrow);
            }
        }
        WindProfileManager.generateTodayAndTomorrowProfiles(world);
    }


    public static void onSeasonChange(ServerLevel world) {
        onRegenerate(world, world.players());
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {

    }

    public static void onRegenerate(ServerLevel world, List<ServerPlayer> players) {
        WindProfileManager.clearAll();
        init(world, world.getSharedSpawnPos());

    }
}
