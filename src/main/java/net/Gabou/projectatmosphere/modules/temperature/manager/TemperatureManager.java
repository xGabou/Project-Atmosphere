package net.Gabou.projectatmosphere.modules.temperature.manager;



import net.Gabou.projectatmosphere.modules.temperature.core.TemperatureProvider;
import net.Gabou.projectatmosphere.modules.temperature.forecast.TemperatureForecast;
import net.Gabou.projectatmosphere.modules.temperature.util.DailyProfileGenerator;
import net.Gabou.projectatmosphere.modules.temperature.util.ForecastStorageManager;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Objects;

public class TemperatureManager{


    private static final int DEFAULT_RADIUS = 150;
    private static BlockPos lastCenter = BlockPos.ZERO;

    /** Called once on server startup to generate initial forecast around spawn. */
    public static void initTemperatureForServer(ServerLevel server, BlockPos spawn) {
        init(server, spawn);
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
        lastCenter = center;

        Map<ResourceLocation, float[][]> forecasts =
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
    }

    /** Clears the cached temperature profiles (weekly + daily). */
    public static void clearForecastCache(ServerLevel world) {
        TemperatureProfileManager.clearAll();
        ForecastStorageManager.clearCache(world);
    }

    public static BlockPos getLastCenter() {
        return lastCenter;
    }
    public static float getCurrentTemperature(ResourceLocation biome, long worldTick) {
        return TemperatureProfileManager.getCurrentTemperature(biome, worldTick);
    }

    public static float[][] getWeeklyForecast(ResourceLocation biome) {
        return TemperatureProfileManager.getWeeklyForecast(biome);
    }

    public static void onSwapProfiles(Level world) {
        for (String key : TemperatureProfileManager.getAllBiomeKeys()) {
            ResourceLocation biome = new ResourceLocation(key);
            float[] tomorrow = TemperatureProfileManager.getTomorrowProfile(biome);
            if (tomorrow != null) {
                TemperatureProfileManager.putDayProfile(biome, tomorrow);
            }
        }
        DailyProfileGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    public static void onPrecomputeProfiles(Level world) {
        DailyProfileGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    public static void onSeasonChange(ServerLevel world) {
        //TODO make sure we dont stack forecasts
        for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            onPlayerJoined(world, player.blockPosition());
        }
    }



}