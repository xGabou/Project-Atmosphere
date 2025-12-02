package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.modules.core.ForecastRegion;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.modules.region.ForecastRegionId;
import net.Gabou.projectatmosphere.modules.region.RegionAdapters;
import net.Gabou.projectatmosphere.modules.region.RegionForecastOrchestrator;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AtmosphericStateRegistry {
    private static final Map<ForecastRegionId, RegionAtmosphereState> STATES = new ConcurrentHashMap<>();
    private static final Map<ForecastRegionId, List<ForecastRegionId>> NEIGHBORS = new ConcurrentHashMap<>();
    private static final Map<BiomeInstanceKey, ForecastRegionId> LEGACY_INDEX = new ConcurrentHashMap<>();
    private static final Set<ForecastRegionId> ACTIVE = ConcurrentHashMap.newKeySet();

    private AtmosphericStateRegistry() {
    }

    public static Set<ForecastRegionId> getActiveStates() {
        return ACTIVE;
    }

    public static void rebuildActiveStates(ServerLevel level) {
        ACTIVE.clear();
        int radius = 1000;
        int r2 = radius * radius;

        for (ServerPlayer player : level.players()) {
            BlockPos p = player.blockPosition();
            for (RegionAtmosphereState state : STATES.values()) {
                BlockPos anchor = state.getPosition();
                if (anchor == null) {
                    continue;
                }
                double dx = anchor.getX() - p.getX();
                double dz = anchor.getZ() - p.getZ();
                if ((dx * dx + dz * dz) <= r2) {
                    ACTIVE.add(state.getRegionId());
                }
            }
        }
    }

    public static void replaceActiveStates(Set<ForecastRegionId> next) {
        ACTIVE.clear();
        if (next != null) {
            ACTIVE.addAll(next);
        }
    }

    public static RegionAtmosphereState initializeState(ForecastRegionId id, ForecastRegion forecast) {
        forecast.finalizeAggregation();
        RegionAtmosphereState state = RegionAtmosphereState.fromForecast(id, forecast);
        STATES.put(id, state);
        indexLegacyKeys(forecast);
        return state;
    }

    public static RegionAtmosphereState initializeState(BiomeInstanceKey key, ForecastRegion forecast) {
        ForecastRegionId regionId = RegionAdapters.fromBiomeKey(key,
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld")));
        indexLegacyKeys(forecast);
        return initializeState(regionId, forecast);
    }

    private static void indexLegacyKeys(ForecastRegion region) {
        ForecastRegionId regionId = RegionAdapters.fromRegionInstance(region.getKey(),
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld")));
        for (BiomeInstanceKey sample : region.getSamples()) {
            if (sample != null) {
                LEGACY_INDEX.put(sample, regionId);
            }
        }
    }

    public static RegionAtmosphereState getState(ForecastRegionId key) {
        if (key == null) {
            return null;
        }
        return STATES.get(key);
    }

    public static RegionAtmosphereState getState(BiomeInstanceKey biomeKey) {
        ForecastRegionId key = resolveRegionKey(biomeKey);
        if (key == null) {
            return null;
        }
        return STATES.get(key);
    }

    public static Collection<RegionAtmosphereState> getStates() {
        return STATES.values();
    }

    public static Map<ForecastRegionId, RegionAtmosphereState> getStatesAsMap() {
        return STATES;
    }

    public static Map<ForecastRegionId, List<ForecastRegionId>> getNeighborsAsMap() {
        return NEIGHBORS;
    }

    /**
     * Legacy compatibility view mapping biome sample keys to their owning region states.
     * This should be used only by code paths that have not yet been converted to region keys.
     */
    public static Map<BiomeInstanceKey, RegionAtmosphereState> getLegacyBiomeStateIndex() {
        Map<BiomeInstanceKey, RegionAtmosphereState> map = new HashMap<>(LEGACY_INDEX.size());
        LEGACY_INDEX.forEach((biomeKey, regionKey) -> {
            RegionAtmosphereState state = STATES.get(regionKey);
            if (state != null) {
                map.put(biomeKey, state);
            }
        });
        return map;
    }

    public static boolean isEmpty() {
        return STATES.isEmpty();
    }

    public static void clear() {
        STATES.clear();
        NEIGHBORS.clear();
        LEGACY_INDEX.clear();
        ACTIVE.clear();
    }

    /**
     * Recomputes region neighbors using grid adjacency. Regions only link to the 8
     * immediate surrounding cells to keep mixing stable and predictable.
     */
    public static void rebuildNeighbors() {
        Map<ForecastRegionId, List<ForecastRegionId>> rebuilt = new HashMap<>(STATES.size());
        for (ForecastRegionId key : STATES.keySet()) {
            List<ForecastRegionId> neighbors = new ArrayList<>(8);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    ForecastRegionId neighbor = new ForecastRegionId(key.rx() + dx, key.rz() + dz, key.dimension());
                    if (STATES.containsKey(neighbor)) {
                        neighbors.add(neighbor);
                    }
                }
            }
            if (!neighbors.isEmpty()) {
                rebuilt.put(key, List.copyOf(neighbors));
            }
        }
        NEIGHBORS.clear();
        NEIGHBORS.putAll(rebuilt);
    }

    public static List<ForecastRegionId> getNeighbors(ForecastRegionId key) {
        return NEIGHBORS.getOrDefault(key, List.of());
    }

    public static List<BiomeInstanceKey> getBiomeNeighbors(BiomeInstanceKey biomeKey) {
        ForecastRegionId regionKey = resolveRegionKey(biomeKey);
        if (regionKey == null) {
            return List.of();
        }
        List<ForecastRegionId> regionNeighbors = getNeighbors(regionKey);
        if (regionNeighbors.isEmpty()) {
            return List.of();
        }
        List<BiomeInstanceKey> result = new ArrayList<>();
        LEGACY_INDEX.forEach((legacyKey, region) -> {
            if (regionNeighbors.contains(region)) {
                result.add(legacyKey);
            }
        });
        return result;
    }

    public static Optional<RegionAtmosphereState> getRandomState(RandomSource random) {
        if (STATES.isEmpty()) {
            return Optional.empty();
        }
        List<RegionAtmosphereState> list = new ArrayList<>(STATES.values());
        return Optional.of(list.get(random.nextInt(list.size())));
    }

    public static Set<BiomeInstanceKey> getActiveBiomeKeys() {
        Set<BiomeInstanceKey> activeBiomes = new HashSet<>();
        LEGACY_INDEX.forEach((biomeKey, regionKey) -> {
            if (ACTIVE.contains(regionKey)) {
                activeBiomes.add(biomeKey);
            }
        });
        return activeBiomes;
    }

    public static RegionAtmosphereState findNearest(double x, double z) {
        RegionAtmosphereState nearest = null;
        double best = Double.MAX_VALUE;
        for (RegionAtmosphereState state : STATES.values()) {
            double dist = state.distanceTo(x, z);
            if (dist < best) {
                best = dist;
                nearest = state;
            }
        }
        return nearest;
    }

    @Unmodifiable
    public static List<RegionAtmosphereState> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(STATES.values()));
    }

    public static RegionInstanceKey resolveRegionKey(BiomeInstanceKey biomeKey) {
        if (biomeKey == null) {
            return null;
        }
        RegionInstanceKey mapped = LEGACY_INDEX.get(biomeKey);
        if (mapped != null) {
            return mapped;
        }
        if (biomeKey.samplePos() == null) {
            return null;
        }
        return RegionInstanceKey.from(biomeKey.samplePos());
    }
}
