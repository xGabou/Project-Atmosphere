package net.Gabou.projectatmosphere.manager;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.client.loading.ForecastLoadingStage;
import net.Gabou.projectatmosphere.client.loading.IntegratedForecastLoadingBridge;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericUpdateScheduler;
import net.Gabou.projectatmosphere.modules.atmosphere.CloudManager;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneManager;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.ocean.OceanBasinManager;
import net.Gabou.projectatmosphere.modules.tornado.GlassDamageManager;
import net.Gabou.projectatmosphere.modules.weather.RegionalWeatherPhase;
import net.Gabou.projectatmosphere.modules.weather.ServerWeatherStateResolver;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.region.RegionForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.region.RegionOrchestratorBootstrap;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.data.TornadoStorageManager;
import net.Gabou.projectatmosphere.modules.tornado.TornadoProbabilityManager;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

import net.Gabou.projectatmosphere.modules.wind.WindEngine;
import net.Gabou.projectatmosphere.network.ForecastLoadingStatusPacket;
import net.Gabou.projectatmosphere.network.NetworkHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;
import net.minecraftforge.network.PacketDistributor;

public class ForecastOrchestrator {
    private static final int MIN_DISTANCE_BETWEEN_CENTERS = ForecastGenerator.RADIUS / 2;
    private static long lastTornadoCheckTick = 0;
    private static final WindVector SAFE_DEFAULT_WIND = WindVector.fromBase(1f, 0f);

    // Global regeneration gate: when true, dependents should skip or defer work
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

    private static Map<UUID, Set<RegionInstanceKey>> activePlayerRegions = new HashMap<>();

    private static final boolean sandStormLoaded = CompatHandler.isSandStormsLoaded();

    /**
     * Called when the server starts
     */
    public static boolean onServerStart(ServerLevel level) {
        IntegratedForecastLoadingBridge.update(
                ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                "Loading atmosphere systems",
                0.1F,
                "server_start_begin"
        );
        ForecastDataStorage.loadAll(level);
        TornadoStorageManager.load(level);
        ForecastGenerator.seed = level.getSeed();
        REGION_ORCHESTRATOR = RegionOrchestratorBootstrap.bootstrap(level);

        if (ForecastDataStorage.hasForecastData()) {
            try {
                if (ForecastDataStorage.hasRegionForecastData()) {
                    IntegratedForecastLoadingBridge.update(
                            ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                            "Loading saved forecast regions",
                            0.18F,
                            "server_start_saved_regions"
                    );
                    WindEngine.rebuildFromRegions(ForecastGenerator.getRegionForecasts());
                } else {
                    IntegratedForecastLoadingBridge.update(
                            ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                            "Rebuilding saved forecast regions",
                            0.18F,
                            "server_start_rebuild_saved_regions"
                    );
                    ForecastGenerator.generateForecastForSavedRegion(level);
                    WindEngine.rebuildFromRegions(ForecastGenerator.getRegionForecasts());
                }
                IntegratedForecastLoadingBridge.update(
                        ForecastLoadingStage.PREPARING_WEATHER_SYSTEMS,
                        "Initializing weather systems",
                        0.88F,
                        "server_start_initialize_systems"
                );
                initializeDynamicSystems(level);
                return true;
            } catch (Exception e) {
                ProjectAtmosphere.LOGGER.error("[Atmosphere] Failed to load saved forecast data. Regenerating from spawn...", e);

                ForecastDataStorage.clearAll(level);
                ForecastGenerator.clearForecasts();
                IntegratedForecastLoadingBridge.update(
                        ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                        "Rebuilding forecast data from spawn",
                        0.22F,
                        "server_start_regenerate_spawn"
                );
                ForecastGenerator.generateForecastForRegion(level.getSharedSpawnPos(), level);
                WindEngine.rebuildFromRegions(ForecastGenerator.getRegionForecasts());
                IntegratedForecastLoadingBridge.update(
                        ForecastLoadingStage.PREPARING_WEATHER_SYSTEMS,
                        "Initializing weather systems",
                        0.88F,
                        "server_start_initialize_systems"
                );
                initializeDynamicSystems(level);
                return true;
            }
        }


        if (!ForecastDataStorage.playerData.isEmpty()) {
            int totalCenters = ForecastDataStorage.playerData.size();
            int centerIndex = 0;
            for (BlockPos pos : ForecastDataStorage.playerData.values()) {
                centerIndex++;
                IntegratedForecastLoadingBridge.update(
                        ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                        "Designing forecast region " + centerIndex + " / " + totalCenters,
                        0.18F + (0.5F * centerIndex / Math.max(1.0F, (float) totalCenters)),
                        "server_start_player_region_" + centerIndex
                );
                ForecastGenerator.generateForecastForRegion(pos, level);
            }
        } else {
            IntegratedForecastLoadingBridge.update(
                    ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                    "Designing forecast region 1 / 1",
                    0.24F,
                    "server_start_spawn_region"
            );
            ForecastGenerator.generateForecastForRegion(level.getSharedSpawnPos(), level);
        }
        WindEngine.rebuildFromRegions(ForecastGenerator.getRegionForecasts());
        IntegratedForecastLoadingBridge.update(
                ForecastLoadingStage.PREPARING_WEATHER_SYSTEMS,
                "Initializing weather systems",
                0.88F,
                "server_start_initialize_systems"
        );
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
    }

    /**
     * Called when a player logs in
     */
    public static void onPlayerLogin(ServerPlayer player, ServerLevel level) {
        UUID uuid = player.getUUID();
        BlockPos playerPos = player.blockPosition();
        sendLoginStage(player, "Collecting nearby forecast regions", 0.16F, "player_login_collect_regions");
        getNearbyRegions(level, player, 1000);
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
                if(ProjectAtmosphere.DEBUG_MODE)
                    ProjectAtmosphere.LOGGER.info("[Atmosphere] Player " + player.getName().getString());
                ForecastDataStorage.playerData.put(uuid, playerPos);
                sendLoginStage(player, "Seeding local weather systems", 0.28F, "player_login_seed_weather");
                SimpleCloudsCompat.doInitialGenWithWeather(playerPos.getX(), playerPos.getZ(), level);
            }

        }
        SimpleCloudsCompat.setIsInit(true);
        sendLoginStage(player, "Forecast regions ready", 0.38F, "player_login_regions_ready");

        long end = System.nanoTime();
        long durationMs = (end - start) / 1_000_000;
        if(ProjectAtmosphere.DEBUG_MODE)
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Forecast data prepared for player {} in {} ms", player.getName().getString(), durationMs);

    }

    /**
     * Used to manually trigger regeneration
     */
    public static void regenerateAround(ServerLevel level, BlockPos pos) {
        updateForecast(level, pos);
    }

    public static RegionForecastOrchestrator getRegionOrchestrator(ServerLevel level) {
        if (REGION_ORCHESTRATOR == null) {
            REGION_ORCHESTRATOR = RegionOrchestratorBootstrap.bootstrap(level);
        }
        return REGION_ORCHESTRATOR;
    }

    public static ForecastRegion getRegionForecast(ServerLevel level, BlockPos pos) {
        return getRegionOrchestrator(level).resolve(pos, level.dimension());
    }

    /**
     * Called when `/atmo regen` is used
     */
    public static void clearAndRegenerate(ServerLevel level) {
        REGENERATING = true;
        try {
            ForecastGenerator.clearForecasts();
            clearActiveRegions();
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

            WindEngine.rebuildFromRegions(ForecastGenerator.getRegionForecasts());
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
            clearActiveRegions();
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
            WindEngine.rebuildFromRegions(ForecastGenerator.getRegionForecasts());
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
        if (ForecastGenerator.getRegionForecasts().isEmpty()) {
            BlockPos spawn = level.getSharedSpawnPos();
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Weekly forecast data missing. Regenerating forecast from spawn...");
            ForecastGenerator.generateForecastForRegion(spawn, level);
            WindEngine.rebuildFromRegions(ForecastGenerator.getRegionForecasts());
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
        WindEngine.rebuildFromRegions(ForecastGenerator.getRegionForecasts());
        initializeDynamicSystems(level);
    }


    /**
     * Get temperature for any biome
     */
    public static float getCurrentTemperature(BiomeInstanceKey key, long tick) {
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        if (regionKey != null) {
            return getCurrentTemperature(regionKey, tick);
        }
        return ForecastGenerator.getTemperatureValue(key, tick);
    }

    /**
     * Region-based temperature sampling. Preferred over biome APIs.
     */
    public static float getCurrentTemperature(ServerLevel level, BlockPos pos, long tick) {
        ForecastRegion region = getRegionForecast(level, pos);
        if (region == null) {
            return 0f;
        }
        return region.sampleTemperature(REGION_ORCHESTRATOR.toRegionLocal(pos), tick);
    }

    /**
     * Get humidity for any biome
     */
    public static float getCurrentHumidity(BiomeInstanceKey key, long tick) {
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        if (regionKey != null) {
            return getCurrentHumidity(regionKey, tick);
        }
        return ForecastGenerator.getHumidityValue(key, tick);
    }

    /**
     * Region-based humidity sampling. Preferred over biome APIs.
     */
    public static float getCurrentHumidity(ServerLevel level, BlockPos pos, long tick) {
        ForecastRegion region = getRegionForecast(level, pos);
        if (region == null) {
            return 0f;
        }
        return region.sampleHumidity(REGION_ORCHESTRATOR.toRegionLocal(pos), tick);
    }

    /**
     * Get pressure for any biome
     */
    public static float getCurrentPressure(BiomeInstanceKey key, long tick) {
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        if (regionKey != null) {
            return getCurrentPressure(regionKey, tick);
        }
        return ForecastGenerator.getPressureValue(key, tick);
    }

    /**
     * Region-based pressure sampling. Preferred over biome APIs.
     */
    public static float getCurrentPressure(ServerLevel level, BlockPos pos, long tick) {
        ForecastRegion region = getRegionForecast(level, pos);
        if (region == null) {
            return 0f;
        }
        return region.samplePressure(tick);
    }

    /**
     * Region-key temperature sampling. Phase 3 target API for runtime consumers.
     */
    public static float getCurrentTemperature(RegionInstanceKey regionKey, long tick) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(regionKey);
        if (state != null) {
            return state.getTemperature();
        }
        ForecastRegion region = ForecastGenerator.getRegionForecasts().get(regionKey);
        if (region != null) {
            return region.sampleTemperature(new Vec3(regionKey.regionSize() / 2.0, 0.0, regionKey.regionSize() / 2.0), tick);
        }
        return 0f;
    }

    /**
     * Region-key humidity sampling. Phase 3 target API for runtime consumers.
     */
    public static float getCurrentHumidity(RegionInstanceKey regionKey, long tick) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(regionKey);
        if (state != null) {
            return state.getHumidityPercent();
        }
        ForecastRegion region = ForecastGenerator.getRegionForecasts().get(regionKey);
        if (region != null) {
            return region.sampleHumidity(new Vec3(regionKey.regionSize() / 2.0, 0.0, regionKey.regionSize() / 2.0), tick);
        }
        return 0f;
    }

    /**
     * Region-key pressure sampling. Phase 3 target API for runtime consumers.
     */
    public static float getCurrentPressure(RegionInstanceKey regionKey, long tick) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(regionKey);
        if (state != null) {
            return state.getPressure();
        }
        ForecastRegion region = ForecastGenerator.getRegionForecasts().get(regionKey);
        if (region != null) {
            return region.samplePressure(tick);
        }
        return 0f;
    }

    /**
     * Get wind for any biome
     */
    @Deprecated
    public static WindVector getCurrentWind(BiomeInstanceKey key, long tick) {
        return getWind(key, tick);
    }

    /**
     * Region-based wind sampling. Preferred over biome APIs.
     */
    @Deprecated
    public static WindVector getCurrentWind(ServerLevel level, BlockPos pos, long tick) {
        return getWind(level, pos, tick);
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
    public static WindVector getForecastWind(ServerLevel level, BlockPos pos, long tick) {
        if (pos == null) {
            return SAFE_DEFAULT_WIND;
        }
        RegionInstanceKey regionKey = RegionInstanceKey.from(pos);
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
        return SAFE_DEFAULT_WIND;
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
        if (regionKey == null) {
            return null;
        }
        ForecastRegion region = ForecastGenerator.getRegionForecasts().get(regionKey);
        if (region == null) {
            return null;
        }
        WindVector[] windWeek = region.getWind();
        if (windWeek == null || windWeek.length == 0) {
            return null;
        }
        WindVector wind = region.sampleWind(tick);
        return isValidWind(wind) ? wind : null;
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

    public static float getCurrentStormChance(BiomeInstanceKey key, long tick) {
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        if (regionKey != null) {
            return getCurrentStormChance(regionKey, tick);
        }
        var state = AtmosphericStateRegistry.getState(key);
        if (state == null) {
            return 0f;
        }
        return computeStormChance(state);
    }

    public static float getCurrentStormChance(RegionInstanceKey regionKey, long tick) {
        var state = AtmosphericStateRegistry.getState(regionKey);
        if (state != null) {
            return computeStormChance(state);
        }
        ForecastRegion region = ForecastGenerator.getRegionForecasts().get(regionKey);
        if (region != null) {
            return region.sampleStorm(tick);
        }
        return 0f;
    }

    private static float computeStormChance(RegionAtmosphereState state) {
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

    /**
     * Region-based storm factor sampling. Preferred over biome APIs.
     */
    public static float getCurrentStormChance(ServerLevel level, BlockPos pos, long tick) {
        ForecastRegion region = getRegionForecast(level, pos);
        if (region == null) {
            return 0f;
        }
        return region.sampleStorm(tick);
    }

    public static RegionalWeatherPhase getWeatherPhase(ServerLevel level, RegionInstanceKey key, long tick) {
        return ServerWeatherStateResolver.resolve(level, key, tick);
    }

    public static void tick(ServerLevel level) {
        if(isRegenerating())
            return;
        GlassDamageManager.tick(level);
        if (sandStormLoaded)
            SandStormManager.tickSandstormScheduler(level);

        AtmosphericUpdateScheduler.tick(level);
        Set<RegionInstanceKey> activeRegions = getActiveRegions(level);
        OceanBasinManager.update(level, activeRegions);
        CycloneManager.update(level);
        WindVector.update(level);
        CloudManager.update(level);
        WindEngine.tick(level, activeRegions);

        long now = level.getGameTime();
        if (now - lastTornadoCheckTick >= (long) (AtmoCommonConfig.TORNADO_CHECK_INTERVAL_SEC.get().floatValue() * 20f) && !level.players().isEmpty()) {
            lastTornadoCheckTick = now;
            if (REGENERATING) {
                runAfterRegen(() -> AsyncAtmosphereService.runStorm(() -> TornadoProbabilityManager.onScheduledCheck(level)));
            } else {
                if(ProjectAtmosphere.DEBUG_MODE)
                    ProjectAtmosphere.LOGGER.info("[Atmosphere] Checking for tornadoes...");
                AsyncAtmosphereService.runStorm(() -> TornadoProbabilityManager.onScheduledCheck(level));
            }
        }

    }
    public static Set<RegionInstanceKey> getActiveRegions(ServerLevel level) {
        Set<RegionInstanceKey> active = new HashSet<>(AtmosphericStateRegistry.getActiveStates());
        if (active.isEmpty() && !activePlayerRegions.isEmpty()) {
            activePlayerRegions.values().forEach(active::addAll);
        }
        return active;
    }

    public static Set<RegionInstanceKey> getActiveRegionsForPlayer(ServerLevel level, ServerPlayer player) {
        Set<RegionInstanceKey> active = new HashSet<>();
        BlockPos pos = player.blockPosition();
        double radiusSq = 1000d * 1000d;
        for (RegionInstanceKey key : AtmosphericStateRegistry.getActiveStates()) {
            if (key == null) {
                continue;
            }
            BlockPos center = key.center();
            if (key.contains(pos) || center.distToCenterSqr(pos.getX(), pos.getY(), pos.getZ()) <= radiusSq) {
                active.add(key);
            }
        }
        return active;
    }


    public static void getNearbyRegions(ServerLevel level, ServerPlayer player, double radius) {
        Vec3 center = Vec3.atCenterOf(player.blockPosition());
        double radiusSq = radius * radius;
        Set<RegionInstanceKey> regions = getActiveRegionsForPlayer(level, player);
        if (!regions.isEmpty()) {
            activePlayerRegions.put(player.getUUID(), regions);
            return;
        }

        regions = ForecastGenerator.getRegionForecasts().keySet().stream()
                .filter(key -> key != null)
                .filter(key -> {
                    BlockPos regionCenter = key.center();
                    return key.contains(player.blockPosition())
                            || regionCenter.distToCenterSqr(center.x, center.y, center.z) <= radiusSq;
                })
                .collect(Collectors.toSet());
        activePlayerRegions.put(player.getUUID(), regions);
    }

    public static void clearActiveRegionsForPlayer(ServerPlayer player) {
        activePlayerRegions.remove(player.getUUID());
    }

    public static void clearActiveRegions() {
        activePlayerRegions.clear();
    }

    private static void initializeDynamicSystems(ServerLevel level) {
        AtmosphericStateRegistry.rebuildNeighbors();
        CloudManager.initialize(level);
        CycloneManager.initialize(level);
        OceanBasinManager.initialize(level);
    }
    private static void sendLoginStage(ServerPlayer player, String subtext, float progress, String source) {
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                ForecastLoadingStatusPacket.status(
                        ForecastLoadingStage.PREPARING_WEATHER_SYSTEMS,
                        null,
                        subtext,
                        progress,
                        source
                )
        );
    }
}

