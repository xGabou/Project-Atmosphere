package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central wind orchestrator. Maintains forecasts and runtime states for both high and low wind layers.
 */
public final class WindEngine {
    private static final Map<BiomeInstanceKey, WindForecast> FORECASTS = new HashMap<>();
    private static final Map<BiomeInstanceKey, WindRuntimeState> STATES = new HashMap<>();

    private WindEngine() { }

    public static void rebuildFromForecasts(Map<BiomeInstanceKey, BiomeForecast> biomeForecasts) {
        FORECASTS.clear();
        biomeForecasts.forEach((key, forecast) -> FORECASTS.put(key, WindForecast.fromBiomeForecast(forecast)));
    }

    public static void tick(ServerLevel level, Set<BiomeInstanceKey> activeKeys) {
        long now = level.getGameTime();
        for (BiomeInstanceKey key : activeKeys) {
            WindForecast forecast = FORECASTS.get(key);
            if (forecast == null) {
                continue;
            }
            WindRuntimeState runtime = STATES.computeIfAbsent(key, k -> new WindRuntimeState());
            float stormChance = ForecastOrchestrator.getCurrentStormChance(key, now);

            WindVector high = HighWindModel.sample(forecast, runtime, now);
            WindVector low = LowWindModel.sample(forecast, runtime, now, stormChance);

            RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
            if (state != null) {
                state.setWind(high);
            }

            WindVector.WindSample sample = new WindVector.WindSample(Math.max(low.baseSpeed(), low.gustSpeed()),
                    (float) Math.toDegrees(low.angleRadians()));
            WindVector.set(key, sample.speedMps(), sample.directionDeg());
        }
    }

    public static WindVector getCurrentHighWindVector(BiomeInstanceKey key, long worldTime) {
        WindRuntimeState runtime = STATES.computeIfAbsent(key, k -> new WindRuntimeState());
        WindForecast forecast = FORECASTS.get(key);
        if (forecast == null) {
            return WindVector.fromBase(0f, 0f);
        }
        return HighWindModel.sample(forecast, runtime, worldTime);
    }

    public static WindVector getCurrentLowWindVector(BiomeInstanceKey key, long worldTime) {
        WindRuntimeState runtime = STATES.computeIfAbsent(key, k -> new WindRuntimeState());
        WindForecast forecast = FORECASTS.get(key);
        if (forecast == null) {
            return WindVector.fromBase(0f, 0f);
        }
        float stormChance = ForecastOrchestrator.getCurrentStormChance(key, worldTime);
        return LowWindModel.sample(forecast, runtime, worldTime, stormChance);
    }

    public static boolean isGustActive(BiomeInstanceKey key) {
        WindRuntimeState runtime = STATES.get(key);
        return runtime != null && runtime.isGustActive();
    }

    public static TornadoWindModel.TornadoForces getCurrentTornadoForce(Vec3 position) {
        return TornadoWindModel.compute(position);
    }
}
