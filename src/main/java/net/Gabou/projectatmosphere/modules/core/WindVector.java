package net.Gabou.projectatmosphere.modules.core;

import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public record WindVector(float baseSpeed, float angleRadians, float gustSpeed) {
    private static final Map<RegionInstanceKey, WindSample> CURRENT = new HashMap<>();
    private static final float MAX_WIND_MIX_FACTOR = 0.05f;
    private static final float MAX_TEMP_MIX_DELTA = 5f;
    private static final float MAX_HUMIDITY_MIX_DELTA = 0.08f;
    private static final float MAX_PRESSURE_MIX_DELTA = 6f;

    // ---------------------------------------------------------------------
    // Vector math
    // ---------------------------------------------------------------------
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

    // ---------------------------------------------------------------------
    // Runtime update
    // ---------------------------------------------------------------------
    public static void update(ServerLevel level) {
        if (AtmosphericStateRegistry.getActiveStates().isEmpty()) return;
        Map<RegionInstanceKey, RegionAtmosphereState> states = AtmosphericStateRegistry.getStatesAsMap();
        Set<RegionInstanceKey> activeKeys = AtmosphericStateRegistry.getActiveStates();
        Map<RegionInstanceKey, List<RegionInstanceKey>> neighborsMap = AtmosphericStateRegistry.getNeighborsAsMap();

        Map<RegionInstanceKey, Delta> deltas = new HashMap<>();

        // process only active states
        for (RegionInstanceKey key : activeKeys) {
            RegionAtmosphereState state = states.get(key);
            if (state == null) continue;

            float strength = state.getWindStrength();
            if (strength <= 0.01f) continue;

            List<RegionInstanceKey> neighbors = neighborsMap.getOrDefault(key, List.of());
            if (neighbors.isEmpty()) continue;

            Delta delta = deltas.computeIfAbsent(key, k -> new Delta());

            for (RegionInstanceKey neighborKey : neighbors) {
                // Only mix with active ones OR all?  Choose.
                RegionAtmosphereState neighbor = states.get(neighborKey);
                if (neighbor == null) continue;

                float mixingFactor = Mth.clamp(0.02f * strength, 0f, MAX_WIND_MIX_FACTOR);
                float humidityFactor = Mth.clamp(0.03f * strength, 0f, MAX_WIND_MIX_FACTOR);
                float pressureFactor = Mth.clamp(0.05f * strength, 0f, MAX_WIND_MIX_FACTOR);

                float tempDelta = (neighbor.getTemperature() - state.getTemperature()) * mixingFactor;
                float pressureDelta = (neighbor.getPressure() - state.getPressure()) * pressureFactor;

                delta.temperature += Mth.clamp(tempDelta, -MAX_TEMP_MIX_DELTA, MAX_TEMP_MIX_DELTA);
                delta.pressure += Mth.clamp(pressureDelta, -MAX_PRESSURE_MIX_DELTA, MAX_PRESSURE_MIX_DELTA);
            }
        }

        // Apply deltas to only active states
        for (var entry : deltas.entrySet()) {
            RegionInstanceKey key = entry.getKey();
            RegionAtmosphereState state = states.get(key);
            if (state == null) continue;

            Delta d = entry.getValue();
            state.adjustTemperature(Mth.clamp(d.temperature, -MAX_TEMP_MIX_DELTA, MAX_TEMP_MIX_DELTA));
            state.adjustPressure(Mth.clamp(d.pressure, -MAX_PRESSURE_MIX_DELTA, MAX_PRESSURE_MIX_DELTA));
        }

        // Wind jitter for active states
        for (RegionInstanceKey key : activeKeys) {
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


    public static void set(RegionInstanceKey key, float effectiveSpeed, float directionDeg) {
        CURRENT.put(key, new WindSample(effectiveSpeed, directionDeg));
    }

    // ---------------------------------------------------------------------
    // Sampling
    // ---------------------------------------------------------------------
    public static WindSample getOrFallback(RegionInstanceKey key) {
        return CURRENT.computeIfAbsent(key, k -> randomSample(new Random()));
    }

    private static WindSample randomSample(Random rng) {
        float speed = rng.nextFloat();
        float dir = rng.nextFloat() * 360f;
        return new WindSample(speed, dir);
    }

    public record WindSample(float speedMps, float directionDeg) { }

    public static float estimateHumidityTransport(RegionInstanceKey key) {
        if (key == null) {
            return 0f;
        }
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
        if (state == null) {
            return 0f;
        }
        Map<RegionInstanceKey, RegionAtmosphereState> states = AtmosphericStateRegistry.getStatesAsMap();
        Map<RegionInstanceKey, List<RegionInstanceKey>> neighborsMap = AtmosphericStateRegistry.getNeighborsAsMap();
        return estimateHumidityTransport(key, state, states, neighborsMap);
    }

    public static float estimateHumidityTransport(RegionInstanceKey key,
                                                  RegionAtmosphereState state,
                                                  Map<RegionInstanceKey, RegionAtmosphereState> states,
                                                  Map<RegionInstanceKey, List<RegionInstanceKey>> neighborsMap) {
        if (key == null || state == null) {
            return 0f;
        }
        float strength = state.getWindStrength();
        if (strength <= 0.01f) {
            return 0f;
        }
        List<RegionInstanceKey> neighbors = neighborsMap.getOrDefault(key, List.of());
        if (neighbors.isEmpty()) {
            return 0f;
        }
        float humidityFactor = Mth.clamp(0.03f * strength, 0f, MAX_WIND_MIX_FACTOR);
        float total = 0f;
        for (RegionInstanceKey neighborKey : neighbors) {
            RegionAtmosphereState neighbor = states.get(neighborKey);
            if (neighbor == null) {
                continue;
            }
            float humidityDelta = (neighbor.getHumidity() - state.getHumidity()) * humidityFactor;
            total += Mth.clamp(humidityDelta, -MAX_HUMIDITY_MIX_DELTA, MAX_HUMIDITY_MIX_DELTA);
        }
        return Mth.clamp(total, -MAX_HUMIDITY_MIX_DELTA, MAX_HUMIDITY_MIX_DELTA);
    }

    private static final class Delta {
        private float temperature;
        private float humidity;
        private float pressure;
    }
}
