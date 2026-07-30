package net.Gabou.projectatmosphere.modules.ocean;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.ocean.influence.AtmosphereFluxInfluence;
import net.Gabou.projectatmosphere.modules.ocean.influence.BasinPressureMemoryInfluence;
import net.Gabou.projectatmosphere.modules.ocean.influence.BasinThermalMemoryInfluence;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Detects and updates ocean basins as long-lived dynamic agents.
 */
public final class OceanBasinManager {
    private static final Map<Integer, OceanBasin> BASINS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final AtomicInteger DETECTION_VERSION = new AtomicInteger();
    private static volatile CompletableFuture<Void> detectionTask = CompletableFuture.completedFuture(null);
    private static final AtomicBoolean READY = new AtomicBoolean(false);

    private OceanBasinManager() {
    }

    public static void initialize(ServerLevel level) {
        READY.set(false);
        BASINS.clear();
        NEXT_ID.set(0);
        detectionTask.cancel(true);
        int version = DETECTION_VERSION.incrementAndGet();
        long sampleTime = level.getGameTime();
        detectionTask = CompletableFuture.supplyAsync(() -> detectBasins(sampleTime), AsyncAtmosphereService.getWeatherExecutor())
                .thenAccept(basins -> {
                    if (version != DETECTION_VERSION.get()) {
                        return;
                    }
                    BASINS.clear();
                    for (OceanBasin basin : basins) {
                        BASINS.put(basin.getId(), basin);
                    }
                    READY.set(true);
                    if (AtmoCommonConfig.DEBUG_MODE.get())
                        ProjectAtmosphere.LOGGER.info("[Ocean] Detected {} basins", basins.size());
                })
                .exceptionally(ex -> {
                    ProjectAtmosphere.LOGGER.error("[Ocean] Failed to detect basins", ex);
                    READY.set(false);
                    return null;
                });
    }

    public static CompoundTag savePersistentState() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Ready", READY.get());
        tag.putInt("NextId", NEXT_ID.get());
        ListTag basins = new ListTag();
        for (OceanBasin basin : BASINS.values()) {
            if (basin != null) {
                basins.add(basin.savePersistentState());
            }
        }
        tag.put("Basins", basins);
        return tag;
    }

    public static void loadPersistentState(CompoundTag tag) {
        if (tag == null || tag.isEmpty() || !tag.contains("Basins", Tag.TAG_LIST)) {
            return;
        }

        DETECTION_VERSION.incrementAndGet();
        detectionTask.cancel(true);
        BASINS.clear();
        int maxId = -1;
        ListTag basins = tag.getList("Basins", Tag.TAG_COMPOUND);
        for (int i = 0; i < basins.size(); i++) {
            OceanBasin basin = OceanBasin.loadPersistentState(basins.getCompound(i));
            if (basin == null) {
                continue;
            }
            attachInfluences(basin);
            BASINS.put(basin.getId(), basin);
            maxId = Math.max(maxId, basin.getId());
        }
        NEXT_ID.set(Math.max(tag.getInt("NextId"), maxId + 1));
        READY.set(tag.getBoolean("Ready") || !BASINS.isEmpty());
    }

    public static void update(ServerLevel level, Set<RegionInstanceKey> activeRegions) {
        if (!READY.get() || AtmosphericStateRegistry.isEmpty()) {
            return;
        }
        if (activeRegions.isEmpty()) {
            return;
        }
        OceanUpdateContext context = new OceanUpdateContext(level, level.getGameTime(), 0.05f / 3600f);
        for (OceanBasin basin : BASINS.values()) {
            if (!basin.intersects(activeRegions)) {
                basin.tick(context, Collections.emptySet());
                continue;
            }
            basin.tick(context, activeRegions);
        }
    }

    public static float estimateHumidityFlux(RegionInstanceKey key, float currentHumidity) {
        if (!READY.get() || key == null) {
            return 0f;
        }
        float total = 0f;
        for (OceanBasin basin : BASINS.values()) {
            Float rawWeight = basin.getInfluenceWeights().get(key);
            if (rawWeight == null) {
                continue;
            }
            float weight = Mth.clamp(rawWeight, 0f, 1.5f);
            total += AtmosphereFluxInfluence.computeHumidityDelta(
                    basin.getHumidityReservoir(),
                    currentHumidity,
                    weight,
                    basin.getOceanCells().contains(key)
            );
        }
        return total;
    }

    public static float estimatePressureDelta(RegionInstanceKey key, float currentPressure) {
        if (!READY.get() || key == null) {
            return 0f;
        }
        float total = 0f;
        for (OceanBasin basin : BASINS.values()) {
            Float rawWeight = basin.getInfluenceWeights().get(key);
            if (rawWeight == null) {
                continue;
            }
            float weight = Mth.clamp(rawWeight, 0f, 1.5f);
            float targetPressure = basin.getBasePressure() + basin.getPressureOffset();
            total += AtmosphereFluxInfluence.computePressureDelta(targetPressure, currentPressure, weight);
        }
        return total;
    }

    private static List<OceanBasin> detectBasins(long gameTime) {
        Collection<RegionAtmosphereState> states = AtmosphericStateRegistry.snapshot();
        if (states.isEmpty()) {
            return List.of();
        }
        Map<RegionInstanceKey, RegionAtmosphereState> oceanStates = new HashMap<>();
        for (RegionAtmosphereState state : states) {
            if (state.getDominantBiome() != null && OceanBiomeClassifier.isOcean(state.getDominantBiome())) {
                oceanStates.put(state.getKey(), state);
            }
        }
        if (oceanStates.isEmpty()) {
            return List.of();
        }

        Set<RegionInstanceKey> visited = new HashSet<>();
        List<OceanBasin> basins = new ArrayList<>();
        for (RegionAtmosphereState state : oceanStates.values()) {
            if (visited.contains(state.getKey())) {
                continue;
            }
            basins.add(buildBasin(state.getKey(), oceanStates, visited, gameTime));
        }
        return basins;
    }

    private static OceanBasin buildBasin(RegionInstanceKey seed,
                                         Map<RegionInstanceKey, RegionAtmosphereState> oceanStates,
                                         Set<RegionInstanceKey> visited,
                                         long gameTime) {
        Queue<RegionInstanceKey> queue = new ArrayDeque<>();
        queue.add(seed);
        Set<RegionInstanceKey> basinCells = new HashSet<>();
        Map<RegionInstanceKey, Float> influenceWeights = new HashMap<>();
        float sumTemp = 0f;
        float sumHumidity = 0f;
        float sumPressure = 0f;
        float sumWindX = 0f;
        float sumWindZ = 0f;
        float sumGust = 0f;
        int count = 0;
        while (!queue.isEmpty()) {
            RegionInstanceKey key = queue.remove();
            if (!visited.add(key)) {
                continue;
            }
            RegionAtmosphereState state = oceanStates.get(key);
            if (state == null) {
                continue;
            }
            basinCells.add(key);
            influenceWeights.put(key, 1f);
            sumTemp += state.getTemperature();
            sumHumidity += state.getHumidity();
            sumPressure += state.getPressure();
            WindVector wind = ForecastOrchestrator.getWind(key, gameTime);
            sumWindX += Math.sin(wind.angleRadians()) * wind.baseSpeed();
            sumWindZ += Math.cos(wind.angleRadians()) * wind.baseSpeed();
            sumGust += wind.gustSpeed();
            count++;
            for (RegionInstanceKey neighbor : AtmosphericStateRegistry.getNeighbors(key)) {
                if (oceanStates.containsKey(neighbor) && !visited.contains(neighbor)) {
                    queue.add(neighbor);
                } else if (!oceanStates.containsKey(neighbor)) {
                    float weight = influenceWeights.getOrDefault(neighbor, 0f);
                    influenceWeights.put(neighbor, Math.max(weight, 0.55f));
                }
            }
        }
        float baseTemp = count == 0 ? 15f : sumTemp / count;
        float baseHumidity = count == 0 ? 0.9f : sumHumidity / count;
        float basePressure = count == 0 ? 1013.25f : sumPressure / count;
        float deepTemp = baseTemp - 2.5f;
        WindVector windBias = null;
        if (count > 0) {
            float avgX = sumWindX / count;
            float avgZ = sumWindZ / count;
            float speed = Mth.sqrt(avgX * avgX + avgZ * avgZ) * 0.35f;
            float angle = (float) Math.atan2(avgX, avgZ);
            float gust = (sumGust / Math.max(1, count)) * 0.3f;
            windBias = new WindVector(speed, angle, Math.max(speed, gust));
        }
        OceanBasin basin = new OceanBasin(NEXT_ID.getAndIncrement(), basinCells, influenceWeights, baseTemp, baseHumidity, basePressure, deepTemp, windBias);
        attachInfluences(basin);
        return basin;
    }

    private static void attachInfluences(OceanBasin basin) {
        basin.addOceanInfluence(new BasinThermalMemoryInfluence());
        basin.addOceanInfluence(new BasinPressureMemoryInfluence());
        basin.addAtmosphereInfluence(new AtmosphereFluxInfluence());
    }
}
