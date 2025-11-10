package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CloudManager {
    private static final Map<BiomeInstanceKey, CloudData> CLOUDS = new ConcurrentHashMap<>();

    private CloudManager() {
    }

    public static void initialize(ServerLevel level) {
        CLOUDS.clear();
    }

    public static void update(ServerLevel level) {
        if (AtmosphericStateRegistry.isEmpty()) {
            return;
        }

        for (RegionAtmosphereState state : AtmosphericStateRegistry.getStates()) {
            BiomeInstanceKey key = state.getKey();
            CloudData data = CLOUDS.get(key);
            float humidity = state.getHumidity();

            if (humidity > 0.8f) {
                if (data == null) {
                    data = new CloudData();
                    CLOUDS.put(key, data);
                    spawnVisual(level, state, data);
                }
                data.ticksAlive++;
                data.thickness = Mth.clamp(data.thickness + 0.01f * (humidity - 0.75f), 0f, 1f);
            } else if (data != null) {
                data.ticksAlive++;
                data.thickness = Math.max(0f, data.thickness - 0.02f);
                if (data.thickness <= 0.05f) {
                    CLOUDS.remove(key);
                    state.setCloudCover(0f);
                    state.setRainIntensity(0f);
                    continue;
                }
            }

            if (data != null) {
                if (data.ticksAlive > 200 && humidity > 0.85f) {
                    float rain = Mth.clamp((humidity - 0.75f) * 2f, 0f, 1f);
                    data.rainIntensity = Math.max(data.rainIntensity, rain);
                } else {
                    data.rainIntensity = Math.max(0f, data.rainIntensity - 0.02f);
                }
                state.setCloudCover(data.thickness);
                state.setRainIntensity(data.rainIntensity);
            } else {
                state.setCloudCover(Math.max(0f, state.getCloudCover() - 0.015f));
                state.setRainIntensity(0f);
            }
        }
    }

    private static void spawnVisual(ServerLevel level, RegionAtmosphereState state, CloudData data) {
        if (data.spawnedVisual || !SimpleCloudsCompat.getIsInit()) {
            return;
        }
        String cloudId = CloudLibrary.getCloudIdFromSeverity(Mth.clamp(Math.round(state.getHumidity() * 7f), 1, 7));
        WindVector wind = state.getWind();
        if (wind == null) {
            wind = WindVector.fromBase(1f, 0f);
        }
        SimpleCloudsCompat.spawnCloudInBiome(cloudId, state.getKey(), level, null, wind);
        data.spawnedVisual = true;
    }

    private static final class CloudData {
        private float thickness;
        private long ticksAlive;
        private float rainIntensity;
        private boolean spawnedVisual;
    }
}
