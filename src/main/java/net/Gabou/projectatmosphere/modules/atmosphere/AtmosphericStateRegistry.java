package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AtmosphericStateRegistry {
    private static final Map<RegionInstanceKey, RegionAtmosphereState> STATES = new ConcurrentHashMap<>();
    private static final Map<RegionInstanceKey, List<RegionInstanceKey>> NEIGHBORS = new ConcurrentHashMap<>();
    private static final Map<BiomeInstanceKey, RegionInstanceKey> LEGACY_INDEX = new ConcurrentHashMap<>();
    private static final Set<RegionInstanceKey> ACTIVE = ConcurrentHashMap.newKeySet();

    private AtmosphericStateRegistry() {
    }

    public static Set<RegionInstanceKey> getActiveStates() {
        return ACTIVE;
    }

    public static void rebuildActiveStates(ServerLevel level) {
        ACTIVE.clear();
        int radius = 1000;
        int r2 = radius * radius;

        for (ServerPlayer player : level.players()) {
            BlockPos p = player.blockPosition();
            for (RegionAtmosphereState state : STATES.values()) {
                if (state == null) {
                    continue;
                }
                RegionInstanceKey key = state.getRegionId();
                if (key == null) {
                    continue;
                }
                if (key.contains(p) || isWithinRegionRadius(key, p, r2)) {
                    ACTIVE.add(key);
                }
            }
        }
    }

    private static boolean isWithinRegionRadius(RegionInstanceKey key, BlockPos pos, int radiusSquared) {
        int size = key.regionSize();
        int minX = key.regionX() * size;
        int minZ = key.regionZ() * size;
        int maxX = minX + size - 1;
        int maxZ = minZ + size - 1;
        int px = pos.getX();
        int pz = pos.getZ();
        int dx = 0;
        int dz = 0;
        if (px < minX) {
            dx = minX - px;
        } else if (px > maxX) {
            dx = px - maxX;
        }
        if (pz < minZ) {
            dz = minZ - pz;
        } else if (pz > maxZ) {
            dz = pz - maxZ;
        }
        return (dx * dx + dz * dz) <= radiusSquared;
    }

    public static void replaceActiveStates(Set<RegionInstanceKey> next) {
        ACTIVE.clear();
        if (next != null) {
            ACTIVE.addAll(next);
        }
    }

    public static RegionAtmosphereState initializeState(RegionInstanceKey id, ForecastRegion forecast) {
        forecast.finalizeAggregation();
        RegionAtmosphereState state = RegionAtmosphereState.fromForecast(id, forecast);
        STATES.put(id, state);
        indexLegacyKeys(forecast);
        return state;
    }

    private static void indexLegacyKeys(ForecastRegion region) {
        RegionInstanceKey regionId = region.getKey();
        for (BiomeInstanceKey sample : region.getSamples()) {
            if (sample != null) {
                LEGACY_INDEX.put(sample, regionId);
            }
        }
    }

    public static RegionAtmosphereState getState(RegionInstanceKey key) {
        if (key == null) {
            return null;
        }
        return STATES.get(key);
    }

    public static RegionAtmosphereState getState(BiomeInstanceKey biomeKey) {
        RegionInstanceKey key = resolveRegionKey(biomeKey);
        if (key == null) {
            return null;
        }
        return STATES.get(key);
    }

    public static Collection<RegionAtmosphereState> getStates() {
        return STATES.values();
    }

    public static Map<RegionInstanceKey, RegionAtmosphereState> getStatesAsMap() {
        return STATES;
    }

    public static Map<RegionInstanceKey, List<RegionInstanceKey>> getNeighborsAsMap() {
        return NEIGHBORS;
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
        Map<RegionInstanceKey, List<RegionInstanceKey>> rebuilt = new HashMap<>(STATES.size());
        for (RegionInstanceKey key : STATES.keySet()) {
            List<RegionInstanceKey> neighbors = new ArrayList<>(8);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    RegionInstanceKey neighbor = new RegionInstanceKey(key.regionX() + dx, key.regionZ() + dz, key.regionSize());
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

    public static List<RegionInstanceKey> getNeighbors(RegionInstanceKey key) {
        return NEIGHBORS.getOrDefault(key, List.of());
    }

    public static Optional<RegionAtmosphereState> getRandomState(RandomSource random) {
        if (STATES.isEmpty()) {
            return Optional.empty();
        }
        List<RegionAtmosphereState> list = new ArrayList<>(STATES.values());
        return Optional.of(list.get(random.nextInt(list.size())));
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
