package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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

    public static CompoundTag savePersistentState() {
        CompoundTag root = new CompoundTag();
        ListTag states = new ListTag();
        for (Map.Entry<RegionInstanceKey, WindRuntimeState> entry : STATES.entrySet()) {
            RegionInstanceKey key = entry.getKey();
            WindRuntimeState state = entry.getValue();
            if (key == null || state == null) {
                continue;
            }
            CompoundTag stateTag = new CompoundTag();
            stateTag.put("Region", saveRegionKey(key));
            stateTag.put("State", state.save());
            states.add(stateTag);
        }
        root.put("States", states);
        return root;
    }

    public static void loadPersistentState(CompoundTag root) {
        STATES.clear();
        if (root == null || root.isEmpty()) {
            return;
        }
        ListTag states = root.getList("States", Tag.TAG_COMPOUND);
        for (int i = 0; i < states.size(); i++) {
            CompoundTag stateTag = states.getCompound(i);
            RegionInstanceKey key = loadRegionKey(stateTag.getCompound("Region"));
            if (key == null || !FORECASTS.containsKey(key)) {
                continue;
            }
            STATES.put(key, WindRuntimeState.load(stateTag.getCompound("State")));
        }
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

    private static CompoundTag saveRegionKey(RegionInstanceKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("RegionX", key.regionX());
        tag.putInt("RegionZ", key.regionZ());
        tag.putInt("RegionSize", key.regionSize());
        return tag;
    }

    private static RegionInstanceKey loadRegionKey(CompoundTag tag) {
        if (tag == null || !tag.contains("RegionX", Tag.TAG_INT) || !tag.contains("RegionZ", Tag.TAG_INT)) {
            return null;
        }
        int size = tag.contains("RegionSize", Tag.TAG_INT) ? tag.getInt("RegionSize") : RegionInstanceKey.DEFAULT_REGION_SIZE;
        return new RegionInstanceKey(tag.getInt("RegionX"), tag.getInt("RegionZ"), size);
    }
}
