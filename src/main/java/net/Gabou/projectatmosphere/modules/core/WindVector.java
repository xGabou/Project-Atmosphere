package net.Gabou.projectatmosphere.modules.core;

import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

public record WindVector(float baseSpeed, float angleRadians, float gustSpeed) {
    private static final Map<BiomeInstanceKey, WindSample> CURRENT = new HashMap<>();

    public WindVector add(WindVector other) {
        return new WindVector(
                this.baseSpeed + other.baseSpeed,
                this.angleRadians + other.angleRadians,
                this.gustSpeed + other.gustSpeed
        );
    }

    public WindVector subtract(WindVector other) {
        return new WindVector(
                this.baseSpeed - other.baseSpeed,
                this.angleRadians - other.angleRadians,
                this.gustSpeed - other.gustSpeed
        );
    }

    public WindVector divide(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than zero");
        }
        return new WindVector(
                this.baseSpeed / count,
                this.angleRadians / count,
                this.gustSpeed / count
        );
    }

    public static WindVector fromBase(float baseSpeed, float angleRadians) {
        return new WindVector(baseSpeed, angleRadians, baseSpeed);
    }

    public static void update(ServerLevel level) {
        if (AtmosphericStateRegistry.getActiveStates().isEmpty()) return;
        Map<BiomeInstanceKey,RegionAtmosphereState> states = AtmosphericStateRegistry.getStatesAsMap();
        Set<BiomeInstanceKey> activeKeys = AtmosphericStateRegistry.getActiveStates();
        Map<BiomeInstanceKey,List<BiomeInstanceKey>> neighborsMap = AtmosphericStateRegistry.getNeighborsAsMap();

        Map<BiomeInstanceKey, Delta> deltas = new HashMap<>();

        // process only active states
        for (BiomeInstanceKey key : activeKeys) {
            RegionAtmosphereState state = states.get(key);
            if (state == null) continue;

            float strength = state.getWindStrength();
            if (strength <= 0.01f) continue;

            List<BiomeInstanceKey> neighbors = neighborsMap.getOrDefault(key, List.of());
            if (neighbors.isEmpty()) continue;

            Delta delta = deltas.computeIfAbsent(key, k -> new Delta());

            for (BiomeInstanceKey neighborKey : neighbors) {
                // Only mix with active ones OR all?  Choose.
                RegionAtmosphereState neighbor = states.get(neighborKey);
                if (neighbor == null) continue;

                float mixingFactor = 0.02f * strength;
                delta.temperature += (neighbor.getTemperature() - state.getTemperature()) * mixingFactor;
                delta.humidity += (neighbor.getHumidity() - state.getHumidity()) * (0.03f * strength);
                delta.pressure += (neighbor.getPressure() - state.getPressure()) * (0.05f * strength);
            }
        }

        // Apply deltas to only active states
        for (var entry : deltas.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            RegionAtmosphereState state = states.get(key);
            if (state == null) continue;

            Delta d = entry.getValue();
            state.adjustTemperature(d.temperature);
            state.adjustHumidity(d.humidity);
            state.adjustPressure(d.pressure);
        }

        // Wind jitter for active states
        for (BiomeInstanceKey key : activeKeys) {
            RegionAtmosphereState state = states.get(key);
            if (state == null) continue;

            WindVector wind = state.getWind();
            if (wind == null) continue;

            float jitter = (level.random.nextFloat() - 0.5f) * 0.02f;
            float speed = Math.max(0f, wind.baseSpeed() + jitter);
            float angle = wind.angleRadians() + (level.random.nextFloat() - 0.5f) * 0.01f;

            state.setWind(new WindVector(speed, angle, Math.max(speed, wind.gustSpeed())));
        }
    }


    public static void set(BiomeInstanceKey key, float effectiveSpeed, float directionDeg) {
        CURRENT.put(key, new WindSample(effectiveSpeed, directionDeg));
    }

    public static WindSample getOrFallback(BiomeInstanceKey key) {
        return CURRENT.computeIfAbsent(key, k -> randomSample(new Random()));
    }

    private static WindSample randomSample(Random rng) {
        float speed = rng.nextFloat();
        float dir = rng.nextFloat() * 360f;
        return new WindSample(speed, dir);
    }

    public record WindSample(float speedMps, float directionDeg) { }

    private static final class Delta {
        private float temperature;
        private float humidity;
        private float pressure;
    }
}
