package net.Gabou.projectatmosphere.manager;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.CloudManager;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneManager;
import net.Gabou.projectatmosphere.modules.atmosphere.RainSystem;
import net.Gabou.projectatmosphere.modules.atmosphere.SunlightController;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.tornado.GlassDamageManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.data.TornadoStorageManager;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneState;
import net.Gabou.projectatmosphere.modules.tornado.TornadoProbabilityManager;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneManager;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

import net.Gabou.projectatmosphere.modules.wind.FloatRange;
import net.Gabou.projectatmosphere.modules.wind.WindEngine;
import net.Gabou.projectatmosphere.modules.wind.WindForecast;
import net.Gabou.projectatmosphere.modules.wind.WindForecastPart;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

public class ForecastOrchestrator {
    private static final int MIN_DISTANCE_BETWEEN_CENTERS = ForecastGenerator.RADIUS / 2;
    private static long lastTornadoCheckTick = 0;

    // Global regeneration gate: when true, dependents should skip or defer work
    private static volatile boolean REGENERATING = false;
    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> POST_REGEN_QUEUE = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public static boolean isRegenerating() {
        return REGENERATING;
    }

    public static void runAfterRegen(Runnable task) {
        if (task == null) return;
        if (REGENERATING) {
            POST_REGEN_QUEUE.add(task);
        } else {
            try {
                task.run();
            } catch (Throwable ignored) { }
        }
    }


    private static Map<UUID, Set<BiomeInstanceKey>> activePlayerBiomeKeys = new HashMap<>();

    private static final boolean sandStormLoaded = CompatHandler.isSandStormsLoaded();

    /**
     * Called when the server starts
     */
    public static boolean onServerStart(ServerLevel level) {
        ForecastDataStorage.loadAll(level);
        TornadoStorageManager.load(level);
        ForecastGenerator.seed = level.getSeed();

        if (ForecastDataStorage.hasCenterData() && ForecastDataStorage.hasForecastData()) {
            try {
                ForecastGenerator.generateForecastForSavedRegion(level);
                initializeDynamicSystems(level);
                return true;
            } catch (Exception e) {
                ProjectAtmosphere.LOGGER.error("[Atmosphere] Failed to load saved forecast data. Regenerating from spawn...", e);

                ForecastDataStorage.clearAll(level);
                ForecastGenerator.clearForecasts();
                ForecastGenerator.generateForecastForRegion(level.getSharedSpawnPos(), level);
                initializeDynamicSystems(level);
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

        initializeDynamicSystems(level);
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
        getNearbyBiomeKeys(level, player, 500);
        long start = System.nanoTime();
        if (!ForecastDataStorage.playerData.containsKey(uuid)) {
            boolean shouldGenerate = true;
            for (BlockPos center : ForecastDataStorage.playerData.values()) {
                if (center.distManhattan(playerPos) < MIN_DISTANCE_BETWEEN_CENTERS) {
                    shouldGenerate = false;
                    break;
                }
            }

            if (shouldGenerate) {
                ProjectAtmosphere.LOGGER.info("[Atmosphere] Player " + player.getName().getString());
                ForecastDataStorage.playerData.put(uuid, playerPos);
                SimpleCloudsCompat.doInitialGenWithWeather(playerPos.getX(), playerPos.getZ(), level);
            }

        }
        SimpleCloudsCompat.setIsInit(true);

        long end = System.nanoTime();
        long durationMs = (end - start) / 1_000_000;
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Forecast data prepared for player {} in {} ms", player.getName().getString(), durationMs);

    }

    /**
     * Used to manually trigger regeneration
     */
    public static void regenerateAround(ServerLevel level, BlockPos pos) {
        updateForecast(level, pos);
    }

    /**
     * Called when `/atmo regen` is used
     */
    public static void clearAndRegenerate(ServerLevel level) {
        REGENERATING = true;
        try {
            ForecastGenerator.clearForecasts();
            clearActiveBiomeKeys();
            ForecastDataStorage.playerData.clear();
            List<ServerPlayer> players = AsyncAtmosphereService.callOnMainThread(level::players);
            Set<BlockPos> centers = new HashSet<>();
            for (Player player : players) {
                BlockPos center = player.blockPosition();
                boolean tooClose = false;
                ForecastDataStorage.playerData.put(player.getUUID(), center);
                for (BlockPos existingCenter : centers) {
                    if (existingCenter.distManhattan(center) < MIN_DISTANCE_BETWEEN_CENTERS) {
                        tooClose = true;
                        break;
                    }
                }
                if (!tooClose) {
                    centers.add(center);
                }
            }
            for (BlockPos center : centers) {
                ForecastGenerator.generateForecastForRegion(center, level);
            }

            ForecastGenerator.getForecastMap().forEach((key, forecast) -> {
                AtmosphericStateRegistry.initializeState(key, forecast);
                generateWindForecast(key, forecast);
            });
            initializeDynamicSystems(level);
        } finally {
            REGENERATING = false;
            Runnable r;
            while ((r = POST_REGEN_QUEUE.poll()) != null) {
                try { r.run(); } catch (Throwable ignored) { }
            }
        }
    }

    /**
     * Called on profile swap (e.g. midnight transition)
     */
    public static void onSwapDay(ServerLevel level) {
        if (ForecastGenerator.getForecastMap().isEmpty()) {
            BlockPos spawn = level.getSharedSpawnPos();
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Weekly forecast data missing. Regenerating forecast from spawn...");
            ForecastGenerator.generateForecastForRegion(spawn, level);
            ForecastGenerator.getForecastMap().forEach((key, forecast) -> {
                AtmosphericStateRegistry.initializeState(key, forecast);
                generateWindForecast(key, forecast);
            });
            initializeDynamicSystems(level);
            return;
        }

        CycloneManager.onMidnight(level);
        AtmosphericStateRegistry.rebuildNeighbors();
    }


    /**
     * Called during manual command-triggered regeneration
     */
    public static void updateForecast(ServerLevel level, BlockPos center) {
        ForecastGenerator.generateForecastForRegion(center, level);
        ForecastGenerator.getForecastMap().forEach((key, forecast) -> {
            AtmosphericStateRegistry.initializeState(key, forecast);
            generateWindForecast(key, forecast);
        });
        initializeDynamicSystems(level);
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
        var state = AtmosphericStateRegistry.getState(key);
        if (state == null) {
            return 0f;
        }

        float rain = Math.min(1f, state.getRainIntensity());
        float cloud = state.getCloudCover();
        float wind = Math.min(1f, state.getWindStrength() / 18f);
        float lowPressure = Math.min(1f, Math.max(0f, (1013.25f - state.getPressure()) / 55f));

        float combined = (rain * 0.45f)
                + (cloud * 0.3f)
                + (wind * 0.15f)
                + (lowPressure * 0.1f);

        return Math.max(0f, Math.min(1f, combined));
    }

    public static void tick(ServerLevel level) {
        GlassDamageManager.tick(level);
        if (sandStormLoaded)
            ForecastGenerator.tickSandstormScheduler(level);

        SunlightController.update(level);
        CycloneManager.update(level);
        WindVector.update(level);
        CloudManager.update(level);
        RainSystem.update(level);

        long now = level.getGameTime();
        if (now - lastTornadoCheckTick >= (long) (AtmoCommonConfig.TORNADO_CHECK_INTERVAL_SEC.get().floatValue() * 20f) && !level.players().isEmpty()) {
            lastTornadoCheckTick = now;
            if (REGENERATING) {
                runAfterRegen(() -> AsyncAtmosphereService.runStorm(() -> TornadoProbabilityManager.onScheduledCheck(level)));
            } else {
                ProjectAtmosphere.LOGGER.info("[Atmosphere] Checking for tornadoes...");
                AsyncAtmosphereService.runStorm(() -> TornadoProbabilityManager.onScheduledCheck(level));
            }
        }

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
        activePlayerBiomeKeys.put(player.getUUID(), get);
    }

    public static void clearActiveBiomeKeysForPlayer(ServerPlayer player) {
        activePlayerBiomeKeys.remove(player.getUUID());
    }

    public static void clearActiveBiomeKeys() {
        activePlayerBiomeKeys.clear();
    }

    private static void initializeDynamicSystems(ServerLevel level) {
        AtmosphericStateRegistry.rebuildNeighbors();
        CloudManager.initialize(level);
        CycloneManager.initialize(level);
    }

    public static void generateWindForecast(BiomeInstanceKey key, BiomeForecast forecast) {
        var state = AtmosphericStateRegistry.getState(key);
        WindVector today = state != null ? state.getWind() : null;
        if (today == null) {
            WindVector[] week = forecast.getWind();
            if (week != null && week.length > 0) {
                today = week[0];
            }
        }
        if (today == null) {
            return;
        }

        float baseMax = today.baseSpeed();
        float gustMax = today.gustSpeed();
        float dirDeg = (float) Math.toDegrees(today.angleRadians());
        float gustProbValue = gustMax > baseMax ? 0.3f : 0f;

        java.util.EnumMap<WindForecastPart, FloatRange> base = new java.util.EnumMap<>(WindForecastPart.class);
        java.util.EnumMap<WindForecastPart, FloatRange> gust = new java.util.EnumMap<>(WindForecastPart.class);
        java.util.EnumMap<WindForecastPart, Float> prob = new java.util.EnumMap<>(WindForecastPart.class);
        java.util.EnumMap<WindForecastPart, FloatRange> dir = new java.util.EnumMap<>(WindForecastPart.class);

        float[] stageScale = {0.3f, 1.0f, 0.8f, 0.6f, 0.4f, 0.2f};
        WindForecastPart[] parts = WindForecastPart.values();
        for (int i = 0; i < parts.length; i++) {
            float scale = stageScale[i];
            base.put(parts[i], new FloatRange(0f, baseMax * scale));
            gust.put(parts[i], new FloatRange(0f, gustMax * scale));
            prob.put(parts[i], gustProbValue);
            dir.put(parts[i], new FloatRange(dirDeg - 15f, dirDeg + 15f));
        }

        WindEngine.putForecast(key, new WindForecast(base, gust, prob, dir));
    }


    public static HurricaneState getActiveHurricane() {
        var hurricanes = HurricaneManager.getActiveHurricanes();
        if (hurricanes.isEmpty()) {
            return null;
        }

        var h = hurricanes.get(0);
        return new HurricaneState(h.position.x, h.position.z, h.radius, h.radius * 0.5);
    }

}

