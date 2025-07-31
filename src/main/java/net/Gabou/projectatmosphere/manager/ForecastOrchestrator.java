package net.Gabou.projectatmosphere.manager;

import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ForecastOrchestrator {
    private static final int MIN_DISTANCE_BETWEEN_CENTERS = ForecastGenerator.RADIUS / 2;

    /**
     * Called when the server starts
     */
    public static boolean onServerStart(ServerLevel level) {
        ForecastDataStorage.loadAll(level);
        if (!ForecastDataStorage.playerData.isEmpty()) {
            for (BlockPos pos : ForecastDataStorage.playerData.values()) {
                ForecastGenerator.generateForecastForRegion(pos, level);
            }
        } else {
            // If no centers exist, create a default one at the origin
            ForecastGenerator.generateForecastForRegion(level.getSharedSpawnPos(), level);
        }
        return true;
    }

    /**
     * Called when the server stops
     */
    public static void onServerStop(ServerLevel level) {
        ForecastDataStorage.saveAll(level);
        ForecastGenerator.clearForecasts();
    }

    /**
     * Called when a player logs in
     */
    public static void onPlayerLogin(ServerPlayer player, ServerLevel level) {
        UUID uuid = player.getUUID();
        BlockPos playerPos = player.blockPosition();

        if (!ForecastDataStorage.playerData.containsKey(uuid)) {
            boolean shouldGenerate = true;
            for (BlockPos center : ForecastDataStorage.playerData.values()) {
                if (center.distManhattan(playerPos) < MIN_DISTANCE_BETWEEN_CENTERS) {
                    shouldGenerate = false;
                    break;
                }
            }

            if (shouldGenerate) {
                ForecastDataStorage.playerData.put(uuid, playerPos);
                SimpleCloudsCompat.doInitialGenWithWeather(playerPos.getX(), playerPos.getZ(), level);
            }
        } else {
            SimpleCloudsCompat.isInit = true;
        }
    }

    /**
     * Used to manually trigger regeneration
     */
    public static void regenerateAround(ServerLevel level, BlockPos pos) {
        ForecastGenerator.generateForecastForRegion(pos, level);
        DailyForecastGenerator.scheduleGenerationForTodayAndTomorrow(level);
    }

    /**
     * Called when `/atmo regen` is used
     */
    public static void clearAndRegenerate(ServerLevel level, Set<BlockPos> centers) {
        ForecastGenerator.clearForecasts();
        ForecastDataStorage.playerData.clear();

        for (BlockPos center : centers) {
            ForecastDataStorage.playerData.put(UUID.randomUUID(), center);
            ForecastGenerator.generateForecastForRegion(center, level);
        }

        DailyForecastGenerator.scheduleGenerationForTodayAndTomorrow(level);
    }

    /**
     * Called on profile swap (e.g. midnight transition)
     */
    public static void onSwapDay(ServerLevel level) {
        ForecastGenerator.swapToTomorrow();
        DailyForecastGenerator.scheduleGenerationForTodayAndTomorrow(level);
    }

    /**
     * Called during manual command-triggered regeneration
     */
    public static void updateForecast(ServerLevel level, BlockPos center) {
        ForecastGenerator.generateForecastForRegion(center, level);
        DailyForecastGenerator.scheduleGenerationForTodayAndTomorrow(level);
    }

//    /** Called when Forge registers commands */
//    public static void onRegisterCommands(RegisterCommandsEvent event) {
//        ForecastDebugCommands.register(event.getDispatcher());
//    }

    /**
     * Get temperature for any biome
     */
    public static float getCurrentTemperature(BiomeInstanceKey key, long tick) {
        return ForecastGenerator.getTemperatureValue(key, tick);
    }

    /**
     * Get humidity for any biome
     */
    public static float getCurrentHumidity(BiomeInstanceKey key, long tick) {
        return ForecastGenerator.getHumidityValue(key, tick);
    }

    /**
     * Get pressure for any biome
     */
    public static float getCurrentPressure(BiomeInstanceKey key, long tick) {
        return ForecastGenerator.getPressureValue(key, tick);
    }

    /**
     * Get wind for any biome
     */
    public static WindVector getCurrentWind(BiomeInstanceKey key) {
        return ForecastGenerator.getWindValue(key);
    }

    public static float getCurrentStormChance(BiomeInstanceKey key, long tick) {
        return ForecastGenerator.getStormChanceValue(key, tick);
    }



}
