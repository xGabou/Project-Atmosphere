package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
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

    public static void rebuildFromRegions(Map<RegionInstanceKey, ForecastRegion> regionForecasts) {
        FORECASTS.clear();
        regionForecasts.forEach((key, forecast) -> FORECASTS.put(key, WindForecast.fromRegionForecast(forecast)));
    }

    public static void tick(ServerLevel level, Set<RegionInstanceKey> activeKeys) {
        long now = level.getGameTime();
        for (RegionInstanceKey regionId : activeKeys) {
            if (regionId == null) {
                continue;
            }
            WindForecast forecast = FORECASTS.get(regionId);
            if (forecast == null) {
                continue;
            }
            WindRuntimeState runtime = STATES.computeIfAbsent(regionId, k -> new WindRuntimeState());
            float stormChance = ForecastOrchestrator.getCurrentStormChance(regionId, now);

            WindVector high = HighWindModel.sample(forecast, runtime, now);
            WindVector low = LowWindModel.sample(forecast, runtime, now, stormChance);

            RegionAtmosphereState state = AtmosphericStateRegistry.getState(regionId);
            if (state != null) {
                state.setWind(low);
            }

            WindVector.WindSample sample = new WindVector.WindSample(low.baseSpeed(),
                    (float) Math.toDegrees(low.angleRadians()));
            WindVector.set(regionId, sample.speedMps(), sample.directionDeg());
        }
    }

    public static WindVector getCurrentHighWindVector(RegionInstanceKey regionId, long worldTime) {
        WindRuntimeState runtime = STATES.computeIfAbsent(regionId, k -> new WindRuntimeState());
        WindForecast forecast = FORECASTS.get(regionId);
        if (forecast == null) {
            return WindVector.fromBase(0f, 0f);
        }
        return HighWindModel.sample(forecast, runtime, worldTime);
    }

    public static WindVector getCurrentLowWindVector(RegionInstanceKey regionId, long worldTime, float stormChance) {
        WindRuntimeState runtime = STATES.computeIfAbsent(regionId, k -> new WindRuntimeState());
        WindForecast forecast = FORECASTS.get(regionId);
        if (forecast == null) {
            return WindVector.fromBase(0f, 0f);
        }
        return LowWindModel.sample(forecast, runtime, worldTime, stormChance);
    }


    public static TornadoWindModel.TornadoForces getCurrentTornadoForce(Vec3 position) {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return null;
        }
        return TornadoWindModel.compute(position);
    }
}
