package net.Gabou.projectatmosphere.manager;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericUpdateScheduler;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.region.RegionForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.region.RegionOrchestratorBootstrap;
import net.Gabou.projectatmosphere.modules.tornado.GlassDamageManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.data.TornadoStorageManager;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneState;
import net.Gabou.projectatmosphere.modules.tornado.TornadoProbabilityManager;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneManager;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

import net.Gabou.projectatmosphere.modules.wind.WindEngine;

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
    private static final WindVector SAFE_DEFAULT_WIND = WindVector.fromBase(1f, 0f);
    private static volatile boolean REGENERATING = false;
    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> POST_REGEN_QUEUE = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private static RegionForecastOrchestrator REGION_ORCHESTRATOR;

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
        REGION_ORCHESTRATOR = RegionOrchestratorBootstrap.bootstrap(level);
        AtmosphericStateRegistry.clear();

        if (ForecastDataStorage.hasCenterData() && ForecastDataStorage.hasForecastData()) {
            try {
                ForecastGenerator.generateForecastForSavedRegion(level);
                WindEngine.rebuildFromForecasts(ForecastGenerator.getForecastMap());
                initializeDynamicSystems(level);
                return true;
            }
            catch (Exception e) {
                ProjectAtmosphere.LOGGER.error("[Atmosphere] Failed to load saved forecast data. Regenerating from spawn...", e);

                ForecastDataStorage.clearAll(level);
                ForecastGenerator.clearForecasts();
                ForecastGenerator.generateForecastForRegion(level.getSharedSpawnPos(), level);
                WindEngine.rebuildFromForecasts(ForecastGenerator.getForecastMap());
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

        WindEngine.rebuildFromForecasts(ForecastGenerator.getForecastMap());
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
        REGION_ORCHESTRATOR = null;
        AtmosphericStateRegistry.clear();
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

    public static RegionForecastOrchestrator getRegionOrchestrator(ServerLevel level) {
        if (REGION_ORCHESTRATOR == null && level != null) {
            REGION_ORCHESTRATOR = RegionOrchestratorBootstrap.bootstrap(level);
        }
        return REGION_ORCHESTRATOR;
    }

    /**
     * @return the currently bootstrapped region orchestrator, or null if the server has not started yet.
     */
    public static RegionForecastOrchestrator getRegionOrchestrator() {
        return REGION_ORCHESTRATOR;
    }

    public static ForecastRegion getRegionForecast(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        RegionForecastOrchestrator orchestrator = getRegionOrchestrator(level);
        if (orchestrator == null) {
            return null;
        }
        ForecastRegion region = orchestrator.resolve(pos, level.dimension());
        ensureAtmosphericState(region);
        return region;
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
            AtmosphericStateRegistry.clear();
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

            DailyForecastGenerator.scheduleGenerationForTodayAndTomorrow();
            WindEngine.rebuildFromForecasts(ForecastGenerator.getForecastMap());
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
     * Regenerates forecasts on season change without wiping cloud entities or player centers.
     */
    public static void regenerateForSeason(ServerLevel level) {
        if (level == null) {
            return;
        }
        REGENERATING = true;
        try {
            ForecastGenerator.clearForecasts();
            clearActiveBiomeKeys();
            AtmosphericStateRegistry.clear();
            List<BlockPos> centers = new ArrayList<>();
            if (!ForecastDataStorage.playerData.isEmpty()) {
                centers.addAll(ForecastDataStorage.playerData.values());
            } else {
                centers.add(level.getSharedSpawnPos());
            }

            Set<BlockPos> uniqueCenters = new HashSet<>();
            for (BlockPos center : centers) {
                boolean tooClose = false;
                for (BlockPos existingCenter : uniqueCenters) {
                    if (existingCenter.distManhattan(center) < MIN_DISTANCE_BETWEEN_CENTERS) {
                        tooClose = true;
                        break;
                    }
                }
                if (!tooClose) {
                    uniqueCenters.add(center);
                }
            }

            for (BlockPos center : uniqueCenters) {
                ForecastGenerator.generateForecastForRegion(center, level);
            }
            WindEngine.rebuildFromForecasts(ForecastGenerator.getForecastMap());
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
            return;
        }

        ForecastGenerator.swapToTomorrow();
        DailyForecastGenerator.scheduleGenerationForTodayAndTomorrow();
        WindEngine.rebuildFromForecasts(ForecastGenerator.getForecastMap());
        initializeDynamicSystems(level);


    }


    /**
     * Called during manual command-triggered regeneration
     */
    public static void updateForecast(ServerLevel level, BlockPos center) {
        ForecastGenerator.generateForecastForRegion(center, level);
        DailyForecastGenerator.scheduleGenerationForTodayAndTomorrow();
        WindEngine.rebuildFromForecasts(ForecastGenerator.getForecastMap());
        initializeDynamicSystems(level);
    }


    /**
     * Get temperature for any biome
     */
    public static float getCurrentTemperature(BiomeInstanceKey key, long tick) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
        if (state != null) {
            return state.getTemperature();
        }
        ForecastRegion region = resolveRegionForecast(key);
        if (region != null && key != null && key.samplePos() != null) {
            return region.sampleTemperature(Vec3.atCenterOf(key.samplePos()), tick);
        }
        return ForecastGenerator.getTemperatureValue(key, tick);
    }

    public static float getCurrentTemperature(ServerLevel level, BlockPos pos, long tick) {
        if (level == null || pos == null) {
            return 0f;
        }
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(RegionInstanceKey.from(pos));
        if (state != null) {
            return state.getTemperature();
        }
        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(pos, level), pos);
        return getCurrentTemperature(key, tick);
    }

    public static float getCurrentTemperature(RegionInstanceKey regionKey, long tick) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(regionKey);
        if (state != null) {
            return state.getTemperature();
        }
        ForecastRegion region = resolveRegionForecast(regionKey);
        if (region != null) {
            return region.sampleTemperature(Vec3.atCenterOf(regionKey.center()), tick);
        }
        return 0f;
    }

    /**
     * Get humidity for any biome
     */
    public static float getCurrentHumidity(BiomeInstanceKey key, long tick) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
        if (state != null) {
            return state.getHumidity();
        }
        ForecastRegion region = resolveRegionForecast(key);
        if (region != null && key != null && key.samplePos() != null) {
            return region.sampleHumidity(Vec3.atCenterOf(key.samplePos()), tick);
        }
        return ForecastGenerator.getHumidityValue(key, tick);
    }

    public static float getCurrentHumidity(ServerLevel level, BlockPos pos, long tick) {
        if (level == null || pos == null) {
            return 0f;
        }
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(RegionInstanceKey.from(pos));
        if (state != null) {
            return state.getHumidity();
        }
        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(pos, level), pos);
        return getCurrentHumidity(key, tick);
    }

    public static float getCurrentHumidity(RegionInstanceKey regionKey, long tick) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(regionKey);
        if (state != null) {
            return state.getHumidityPercent();
        }
        ForecastRegion region = resolveRegionForecast(regionKey);
        if (region != null) {
            return region.sampleHumidity(Vec3.atCenterOf(regionKey.center()), tick);
        }
        return 0f;
    }

    /**
     * Get pressure for any biome
     */
    public static float getCurrentPressure(BiomeInstanceKey key, long tick) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
        if (state != null) {
            return state.getPressure();
        }
        ForecastRegion region = resolveRegionForecast(key);
        if (region != null) {
            return region.samplePressure(tick);
        }
        return ForecastGenerator.getPressureValue(key, tick);
    }

    public static float getCurrentPressure(ServerLevel level, BlockPos pos, long tick) {
        if (level == null || pos == null) {
            return 0f;
        }
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(RegionInstanceKey.from(pos));
        if (state != null) {
            return state.getPressure();
        }
        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(pos, level), pos);
        return getCurrentPressure(key, tick);
    }

    public static float getCurrentPressure(RegionInstanceKey regionKey, long tick) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(regionKey);
        if (state != null) {
            return state.getPressure();
        }
        ForecastRegion region = resolveRegionForecast(regionKey);
        if (region != null) {
            return region.samplePressure(tick);
        }
        return 0f;
    }

    /**
     * Get wind for any biome
     */
    public static WindVector getCurrentWind(BiomeInstanceKey key, long tick) {
        return getWind(key, tick);
    }

    public static float getCurrentStormChance(BiomeInstanceKey key, long tick) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
        if (state == null) {
            return ForecastGenerator.getStormChanceValue(key, tick);
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

    public static float getCurrentStormChance(RegionInstanceKey regionKey, long tick) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(regionKey);
        if (state != null) {
            float rain = Math.min(1f, state.getRainIntensity());
            float cloud = state.getCloudCover();
            float wind = Math.min(1f, state.getWindStrength() / 18f);
            float lowPressure = Math.min(1f, Math.max(0f, (1013.25f - state.getPressure()) / 55f));
            return Math.max(0f, Math.min(1f, (rain * 0.45f) + (cloud * 0.3f) + (wind * 0.15f) + (lowPressure * 0.1f)));
        }
        ForecastRegion region = resolveRegionForecast(regionKey);
        return region == null ? 0f : region.sampleStorm(tick);
    }

    public static void tick(ServerLevel level) {
        if (isRegenerating()) {
            return;
        }
        GlassDamageManager.tick(level);

        // Ensure we have dynamic region state for anything players are currently influencing.
        Set<BiomeInstanceKey> activeKeys = getActiveBiomeKeys(level);
        ensureRegionStates(level, activeKeys);

        AtmosphericUpdateScheduler.tick(level);
        WindVector.update(level);
        WindEngine.tick(level, activeKeys);

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
        Set<BiomeInstanceKey> active = new HashSet<>(AtmosphericStateRegistry.getActiveBiomeKeys());
        if (active.isEmpty() && !activePlayerBiomeKeys.isEmpty()) {
            activePlayerBiomeKeys.values().forEach(active::addAll);
        }
        return active;
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

    /**
     * Canonical wind selector: dynamic state if available, forecast fallback, then safe default.
     */
    public static WindVector getWind(BiomeInstanceKey key, long tick) {
        if (key == null) {
            return SAFE_DEFAULT_WIND;
        }
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        return selectWind(regionKey, tick);
    }

    /**
     * Canonical wind selector: dynamic state if available, forecast fallback, then safe default.
     */
    public static WindVector getWind(ServerLevel level, BlockPos pos, long tick) {
        if (pos == null) {
            return SAFE_DEFAULT_WIND;
        }
        RegionInstanceKey regionKey = RegionInstanceKey.from(pos);
        if (level != null) {
            // Ensure region is loaded so downstream systems have a chance to initialize state.
            getRegionForecast(level, pos);
        }
        return selectWind(regionKey, tick);
    }

    /**
     * Canonical wind selector: dynamic state if available, forecast fallback, then safe default.
     */
    public static WindVector getWind(RegionInstanceKey regionKey, long tick) {
        return selectWind(regionKey, tick);
    }

    /**
     * Explicit forecast wind access (no dynamic state).
     */
    public static WindVector getForecastWind(BiomeInstanceKey key, long tick) {
        if (key == null) {
            return SAFE_DEFAULT_WIND;
        }
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        return getForecastWind(regionKey, tick);
    }

    /**
     * Explicit forecast wind access (no dynamic state).
     */
    public static WindVector getForecastWind(RegionInstanceKey regionKey, long tick) {
        WindVector forecast = getForecastWindInternal(regionKey, tick);
        return forecast == null ? SAFE_DEFAULT_WIND : forecast;
    }

    private static WindVector selectWind(RegionInstanceKey regionKey, long tick) {
        WindVector dynamic = getDynamicWind(regionKey);
        if (dynamic != null) {
            return dynamic;
        }
        WindVector forecast = getForecastWindInternal(regionKey, tick);
        if (forecast != null) {
            return forecast;
        }
        if (regionKey == null) {
            return SAFE_DEFAULT_WIND;
        }
        WindVector.WindSample sample = WindVector.getOrFallback(regionKey);
        float angle = (float) Math.toRadians(sample.directionDeg());
        return new WindVector(sample.speedMps(), angle, sample.speedMps());
    }

    private static ForecastRegion resolveRegionForecast(BiomeInstanceKey key) {
        if (key == null) {
            return null;
        }
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        if (regionKey == null || REGION_ORCHESTRATOR == null) {
            return null;
        }
        try {
            return REGION_ORCHESTRATOR.ensureLoaded(regionKey);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ForecastRegion resolveRegionForecast(RegionInstanceKey key) {
        if (key == null || REGION_ORCHESTRATOR == null) {
            return null;
        }
        try {
            return REGION_ORCHESTRATOR.ensureLoaded(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static WindVector getDynamicWind(RegionInstanceKey regionKey) {
        if (regionKey == null) {
            return null;
        }
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(regionKey);
        if (state == null) {
            return null;
        }
        WindVector wind = state.getWind();
        return isDynamicAvailable(wind) ? wind : null;
    }

    private static WindVector getForecastWindInternal(RegionInstanceKey regionKey, long tick) {
        if (regionKey == null || REGION_ORCHESTRATOR == null) {
            return null;
        }
        try {
            ForecastRegion region = REGION_ORCHESTRATOR.ensureLoaded(regionKey);
            if (region == null) {
                return null;
            }
            WindVector wind = region.sampleWind(tick);
            return isValidWind(wind) ? wind : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isDynamicAvailable(WindVector wind) {
        if (!isValidWind(wind)) {
            return false;
        }
        return wind.baseSpeed() > 0f || wind.gustSpeed() > 0f;
    }

    private static boolean isValidWind(WindVector wind) {
        if (wind == null) {
            return false;
        }
        return Float.isFinite(wind.baseSpeed())
                && Float.isFinite(wind.gustSpeed())
                && Float.isFinite(wind.angleRadians());
    }

    private static void ensureRegionStates(ServerLevel level, Set<BiomeInstanceKey> activeKeys) {
        if (level == null || activeKeys == null || activeKeys.isEmpty()) {
            return;
        }
        RegionForecastOrchestrator orchestrator = getRegionOrchestrator(level);
        if (orchestrator == null) {
            return;
        }
        boolean added = false;
        Set<RegionInstanceKey> regionKeys = new HashSet<>();
        for (BiomeInstanceKey key : activeKeys) {
            if (key == null || key.samplePos() == null) {
                continue;
            }
            regionKeys.add(RegionInstanceKey.from(key.samplePos()));
        }
        for (RegionInstanceKey regionKey : regionKeys) {
            if (AtmosphericStateRegistry.getState(regionKey) != null) {
                continue;
            }
            ForecastRegion forecast = orchestrator.ensureLoaded(regionKey);
            if (forecast != null) {
                AtmosphericStateRegistry.initializeState(regionKey, forecast);
                added = true;
            }
        }
        if (added) {
            AtmosphericStateRegistry.rebuildNeighbors();
        }
    }

    private static void ensureAtmosphericState(ForecastRegion region) {
        if (region == null) {
            return;
        }
        RegionInstanceKey key = region.getKey();
        if (key == null) {
            return;
        }
        if (AtmosphericStateRegistry.getState(key) != null) {
            return;
        }
        AtmosphericStateRegistry.initializeState(key, region);
        AtmosphericStateRegistry.rebuildNeighbors();
    }

    private static void initializeDynamicSystems(ServerLevel level) {
        AtmosphericStateRegistry.rebuildNeighbors();
        AtmosphericStateRegistry.rebuildActiveStates(level);
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
