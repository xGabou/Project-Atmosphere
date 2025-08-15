package net.Gabou.projectatmosphere.manager;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.tornado.GlassDamageManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.data.TornadoStorageManager;
import net.Gabou.projectatmosphere.modules.tornado.TornadoProbabilityManager;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

import net.Gabou.projectatmosphere.wind.FloatRange;
import net.Gabou.projectatmosphere.wind.WindEngine;
import net.Gabou.projectatmosphere.wind.WindForecast;
import net.Gabou.projectatmosphere.wind.WindForecastPart;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

public class ForecastOrchestrator {
    private static final int MIN_DISTANCE_BETWEEN_CENTERS = ForecastGenerator.RADIUS / 2;
    private static long lastTornadoCheckTick = 0;


    private static Map<UUID, Set<BiomeInstanceKey>> activePlayerBiomeKeys = new HashMap<>();



    /**
     * Called when the server starts
     */
    public static boolean onServerStart(ServerLevel level) {
        ForecastDataStorage.loadAll(level);
        TornadoStorageManager.load(level);


        if (ForecastDataStorage.hasCenterData() && ForecastDataStorage.hasForecastData()) {
            try {
                ForecastGenerator.generateForecastForSavedRegion(level);
                return true;
            } catch (Exception e) {
                ProjectAtmosphere.LOGGER.error("[Atmosphere] Failed to load saved forecast data. Regenerating from spawn...", e);

                ForecastDataStorage.clearAll(level);
                ForecastGenerator.clearForecasts();
                ForecastGenerator.generateForecastForRegion(level.getSharedSpawnPos(), level);
                return true;
            }
        }


        if (!ForecastDataStorage.playerData.isEmpty()) {
            for (BlockPos pos : ForecastDataStorage.playerData.values()) {
                ForecastGenerator.generateForecastForRegion(pos, level);
            }
        } else {
            ForecastGenerator.generateForecastForRegion(level.getSharedSpawnPos(), level);
        }

        return true;
    }


    /**
     * Called when the server stops
     */
    public static void onServerStop(ServerLevel level) {
        ForecastDataStorage.saveAll(level);
        TornadoStorageManager.save(level);
        ForecastGenerator.clearForecasts();
    }

    /**
     * Called when a player logs in
     */
    public static void onPlayerLogin(ServerPlayer player, ServerLevel level) {
        UUID uuid = player.getUUID();
        BlockPos playerPos = player.blockPosition();
        getNearbyBiomeKeys(level,player,500);
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
        boolean needsRegen = false;


        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : ForecastGenerator.getForecastMap().entrySet()) {
            BiomeForecast forecast = entry.getValue();

            boolean tempInvalid = forecast.getTemperature() == null || forecast.getTemperature().length < 2;
            boolean humidityInvalid = forecast.getHumidity() == null || forecast.getHumidity().length < 2;
            boolean pressureInvalid = forecast.getPressure() == null || forecast.getPressure().length < 2;
            boolean stormInvalid = forecast.getStormChance() == null || forecast.getStormChance().length < 2;
            boolean windInvalid = forecast.getWind() == null || forecast.getWind().length < 2;

            if (tempInvalid || humidityInvalid || pressureInvalid || stormInvalid || windInvalid) {
                needsRegen = true;
                break;
            }
        }

        if (needsRegen || ForecastGenerator.getForecastMap().isEmpty()) {

            BlockPos spawn = level.getSharedSpawnPos();
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Weekly forecast data missing or invalid. Regenerating forecast from spawn...");
            ForecastGenerator.generateForecastForRegion(spawn, level);
        }


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
    public static WindVector getCurrentWind(BiomeInstanceKey key, long tick) {
        return ForecastGenerator.getWindValue(key, tick);
    }

    public static float getCurrentStormChance(BiomeInstanceKey key, long tick) {
        return ForecastGenerator.getStormChanceValue(key, tick);
    }

    public static void tick(ServerLevel level) {
        AsyncAtmosphereService.runStorm(() -> {
                    GlassDamageManager.tick(level);
                    ForecastGenerator.tickSandstormScheduler(level);
                    long now = level.getGameTime();
                    if (now - lastTornadoCheckTick >= (long) (AtmoCommonConfig.TORNADO_CHECK_INTERVAL_SEC.get().floatValue() * 20f) && !level.players().isEmpty()) {
                        lastTornadoCheckTick = now;
                        TornadoProbabilityManager.onScheduledCheck(level);
                    }
                }
        );

    }

    public static Set<BiomeInstanceKey> getActiveBiomeKeys(ServerLevel level) {
        return activePlayerBiomeKeys.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }


    public static Set<BiomeInstanceKey> getActiveBiomeKeysForPlayer(ServerLevel level, ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (activePlayerBiomeKeys.containsKey(uuid)) {
            return activePlayerBiomeKeys.get(uuid);

        }
        return Collections.emptySet();

    }

    public static void getNearbyBiomeKeys(ServerLevel level, ServerPlayer player, double radius) {
        Vec3 center = Vec3.atCenterOf(player.blockPosition());
        double radiusSq = radius * radius;
        Set<BiomeInstanceKey> get = getActiveBiomeKeysForPlayer(level, player);
        if (!get.isEmpty()) {
            return;
        }
        get = ForecastGenerator.getForecastMap().keySet().stream()
                .filter(key -> key.samplePos() != null &&
                        key.samplePos().distToCenterSqr(center.x, center.y, center.z) <= radiusSq)
                .collect(Collectors.toSet());
        activePlayerBiomeKeys.put(player.getUUID(),get);
    }

    public static void clearActiveBiomeKeysForPlayer(ServerPlayer player) {
        activePlayerBiomeKeys.remove(player.getUUID());
    }
    public static void clearActiveBiomeKeys() {
        activePlayerBiomeKeys.clear();
    }

    public static void generateWindForecast(BiomeInstanceKey key, ServerLevel level) {
        java.util.EnumMap<WindForecastPart, FloatRange> base = new java.util.EnumMap<>(WindForecastPart.class);
        java.util.EnumMap<WindForecastPart, FloatRange> gust = new java.util.EnumMap<>(WindForecastPart.class);
        java.util.EnumMap<WindForecastPart, Float> prob = new java.util.EnumMap<>(WindForecastPart.class);
        java.util.EnumMap<WindForecastPart, FloatRange> dir = new java.util.EnumMap<>(WindForecastPart.class);
        for (WindForecastPart part : WindForecastPart.values()) {
            base.put(part, new FloatRange(0f, 1f));
            gust.put(part, new FloatRange(0f, 1f));
            prob.put(part, 0f);
            dir.put(part, new FloatRange(0f, 360f));
        }
        WindEngine.putForecast(key, new WindForecast(base, gust, prob, dir));
    }


}
