package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class AtmosphericStateRegistry {
    private static final Map<BiomeInstanceKey, RegionAtmosphereState> STATES = new ConcurrentHashMap<>();
    private static final Map<BiomeInstanceKey, List<BiomeInstanceKey>> NEIGHBORS = new ConcurrentHashMap<>();
    private static final double NEIGHBOR_RADIUS_SQR = 256.0 * 256.0;

    private AtmosphericStateRegistry() {
    }

    public static RegionAtmosphereState initializeState(BiomeInstanceKey key, BiomeForecast forecast) {
        RegionAtmosphereState state = RegionAtmosphereState.fromForecast(key, forecast);
        STATES.put(key, state);
        return state;
    }

    public static RegionAtmosphereState getState(BiomeInstanceKey key) {
        if (key == null) {
            return null;
        }
        return STATES.get(key);
    }

    public static Collection<RegionAtmosphereState> getStates() {
        return STATES.values();
    }

    public static boolean isEmpty() {
        return STATES.isEmpty();
    }

    public static void clear() {
        STATES.clear();
        NEIGHBORS.clear();
    }

    public static void rebuildNeighbors() {
        List<BiomeInstanceKey> keys = new ArrayList<>(STATES.keySet());
        Map<BiomeInstanceKey, List<BiomeInstanceKey>> rebuilt = new HashMap<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            BiomeInstanceKey a = keys.get(i);
            if (a == null || a.samplePos() == null) {
                continue;
            }
            List<BiomeInstanceKey> listA = rebuilt.computeIfAbsent(a, k -> new ArrayList<>());
            for (int j = i + 1; j < keys.size(); j++) {
                BiomeInstanceKey b = keys.get(j);
                if (b == null || b.samplePos() == null) {
                    continue;
                }
                double dist = a.samplePos().distToCenterSqr(b.samplePos().getX(), b.samplePos().getY(), b.samplePos().getZ());
                if (dist <= NEIGHBOR_RADIUS_SQR) {
                    listA.add(b);
                    rebuilt.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
                }
            }
        }

        NEIGHBORS.clear();
        rebuilt.forEach((key, neighbors) -> NEIGHBORS.put(key, List.copyOf(neighbors)));
    }

    public static List<BiomeInstanceKey> getNeighbors(BiomeInstanceKey key) {
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

    public static List<RegionAtmosphereState> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(STATES.values()));
    }
}
