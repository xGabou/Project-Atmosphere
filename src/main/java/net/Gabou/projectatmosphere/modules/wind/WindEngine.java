package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.RegionIdCodec;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central wind orchestrator. Maintains forecasts and runtime states for both high and low wind layers.
 */
public final class WindEngine {
    private static final Map<RegionInstanceKey, WindForecast> FORECASTS = new HashMap<>();
    private static final Map<RegionInstanceKey, WindRuntimeState> STATES = new HashMap<>();

    private WindEngine() { }

    public static void rebuildFromForecasts(Map<BiomeInstanceKey, BiomeForecast> biomeForecasts) {
        FORECASTS.clear();
        biomeForecasts.forEach((key, forecast) -> {
            RegionInstanceKey id = RegionIdCodec.ofBlockPos(key.samplePos());
            FORECASTS.put(id, WindForecast.fromBiomeForecast(forecast));
        });
    }

    public static void tick(ServerLevel level, Set<BiomeInstanceKey> activeKeys) {
        long now = level.getGameTime();
        for (BiomeInstanceKey key : activeKeys) {
            RegionInstanceKey regionId = RegionIdCodec.ofBlockPos(key.samplePos());
            WindForecast forecast = FORECASTS.get(regionId);
            if (forecast == null) {
                continue;
            }
            WindRuntimeState runtime = STATES.computeIfAbsent(regionId, k -> new WindRuntimeState());
            float stormChance = ForecastOrchestrator.getCurrentStormChance(key, now);

            WindVector high = HighWindModel.sample(forecast, runtime, now);
            WindVector low = LowWindModel.sample(forecast, runtime, now, stormChance);

            RegionAtmosphereState state = AtmosphericStateRegistry.getState(regionId);
            if (state != null) {
                state.setWind(high);
            }

            WindVector.WindSample sample = new WindVector.WindSample(Math.max(low.baseSpeed(), low.gustSpeed()),
                    (float) Math.toDegrees(low.angleRadians()));
            RegionInstanceKey regionKey = RegionInstanceKey.from(key.samplePos());
            WindVector.set(regionKey, sample.speedMps(), sample.directionDeg());
        }
    }

    public static WindVector getCurrentHighWindVector(BiomeInstanceKey key, long worldTime) {
        RegionInstanceKey regionId = RegionIdCodec.ofBlockPos(key.samplePos());
        return getCurrentHighWindVector(regionId, worldTime);
    }

    public static WindVector getCurrentHighWindVector(RegionInstanceKey regionId, long worldTime) {
        WindRuntimeState runtime = STATES.computeIfAbsent(regionId, k -> new WindRuntimeState());
        WindForecast forecast = FORECASTS.get(regionId);
        if (forecast == null) {
            return WindVector.fromBase(0f, 0f);
        }
        return HighWindModel.sample(forecast, runtime, worldTime);
    }

    public static WindVector getCurrentLowWindVector(BiomeInstanceKey key, long worldTime) {
        RegionInstanceKey regionId = RegionIdCodec.ofBlockPos(key.samplePos());
        return getCurrentLowWindVector(regionId, worldTime, ForecastOrchestrator.getCurrentStormChance(key, worldTime));
    }

    public static WindVector getCurrentLowWindVector(RegionInstanceKey regionId, long worldTime, float stormChance) {
        WindRuntimeState runtime = STATES.computeIfAbsent(regionId, k -> new WindRuntimeState());
        WindForecast forecast = FORECASTS.get(regionId);
        if (forecast == null) {
            return WindVector.fromBase(0f, 0f);
        }
        return LowWindModel.sample(forecast, runtime, worldTime, stormChance);
    }

    public static boolean isGustActive(BiomeInstanceKey key) {
        WindRuntimeState runtime = STATES.get(RegionIdCodec.ofBlockPos(key.samplePos()));
        return runtime != null && runtime.isGustActive();
    }

    public static TornadoWindModel.TornadoForces getCurrentTornadoForce(Vec3 position) {
        return TornadoWindModel.compute(position);
    }
}
