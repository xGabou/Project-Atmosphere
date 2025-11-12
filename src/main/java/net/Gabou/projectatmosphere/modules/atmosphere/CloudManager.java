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
    private static final int RESPAWN_COOLDOWN_TICKS = 20 * 10; // 10 seconds
    private static final float HUMIDITY_SPAWN_THRESHOLD = 0.82f;
    private static final float THICKNESS_MIN_HUMIDITY = 0.72f;
    private static final float THICKNESS_MAX_HUMIDITY = 0.95f;
    private static final float THICKNESS_GROWTH_RATE = 0.0003f;
    private static final float THICKNESS_BASE_DECAY_RATE = 0.00002f;
    private static final float THICKNESS_EXTREME_DECAY_RATE = 0.00013f;
    private static final float RAIN_RAMP_HUMIDITY = HUMIDITY_SPAWN_THRESHOLD + 0.05f;
    private static final float RAIN_CHANGE_RATE = 0.0025f;

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

            if (data == null && humidity >= HUMIDITY_SPAWN_THRESHOLD) {
                data = new CloudData();
                CLOUDS.put(key, data);
            }

            if (data == null) {
                state.setCloudCover(Math.max(0f, state.getCloudCover() - 0.01f));
                state.setRainIntensity(Math.max(0f, state.getRainIntensity() - 0.01f));
                continue;
            }

            data.tickCooldown();
            data.ticksAlive++;
            data.updateThickness(humidity);

            if (humidity >= HUMIDITY_SPAWN_THRESHOLD && !data.spawnedVisual && data.canSpawn()) {
                if (spawnVisual(level, state)) {
                    data.spawnedVisual = true;
                    data.ticksAlive = 0;
                }
            }

            if (data.spawnedVisual && data.thickness <= 0.06f && humidity < HUMIDITY_SPAWN_THRESHOLD) {
                data.spawnedVisual = false;
                data.resetCooldown();
            }

            if (!data.spawnedVisual && data.canSpawn() && data.thickness <= 0.01f && humidity < HUMIDITY_SPAWN_THRESHOLD - 0.04f) {
                CLOUDS.remove(key);
                state.setCloudCover(0f);
                state.setRainIntensity(0f);
                continue;
            }

            if (data.spawnedVisual) {
                data.updateRainIntensity(humidity);
            } else {
                data.rainIntensity = Math.max(0f, data.rainIntensity - RAIN_CHANGE_RATE);
            }

            state.setCloudCover(data.thickness);
            state.setRainIntensity(data.rainIntensity);
        }
    }

    private static boolean spawnVisual(ServerLevel level, RegionAtmosphereState state) {
        if (!SimpleCloudsCompat.getIsInit()) {
            return false;
        }
        String cloudId = CloudLibrary.getCloudIdFromSeverity(Mth.clamp(Math.round(state.getHumidity() * 7f), 1, 7));
        WindVector wind = state.getWind();
        if (wind == null) {
            wind = WindVector.fromBase(1f, 0f);
        }
        return SimpleCloudsCompat.spawnCloudInBiome(cloudId, state.getKey(), level, null, wind) != null;
    }

    private static final class CloudData {
        private float thickness;
        private long ticksAlive;
        private float rainIntensity;
        private boolean spawnedVisual;
        private int respawnCooldown;

        private void updateThickness(float humidity) {
            float target = (humidity - THICKNESS_MIN_HUMIDITY) / (THICKNESS_MAX_HUMIDITY - THICKNESS_MIN_HUMIDITY);
            target = Mth.clamp(target, 0f, 1f);
            float delta = target - thickness;

            if (delta > 0f) {
                float increase = Math.min(delta, THICKNESS_GROWTH_RATE);
                thickness = Mth.clamp(thickness + increase, 0f, 1f);
            } else if (delta < 0f) {
                float dryness = Mth.clamp((HUMIDITY_SPAWN_THRESHOLD - humidity) / HUMIDITY_SPAWN_THRESHOLD, 0f, 1f);
                float maxLoss = THICKNESS_BASE_DECAY_RATE + dryness * THICKNESS_EXTREME_DECAY_RATE;
                float decrease = Math.max(delta, -maxLoss);
                thickness = Mth.clamp(thickness + decrease, 0f, 1f);
            }
        }

        private void updateRainIntensity(float humidity) {
            float desiredRain = 0f;
            if (humidity >= RAIN_RAMP_HUMIDITY && thickness > 0.4f) {
                float humidityFactor = (humidity - RAIN_RAMP_HUMIDITY) / (1f - RAIN_RAMP_HUMIDITY);
                desiredRain = Mth.clamp(humidityFactor, 0f, 1f) * thickness;
            }

            if (desiredRain > rainIntensity) {
                rainIntensity = Math.min(desiredRain, rainIntensity + RAIN_CHANGE_RATE);
            } else {
                rainIntensity = Math.max(desiredRain, rainIntensity - RAIN_CHANGE_RATE * 0.5f);
            }
        }

        private void tickCooldown() {
            if (respawnCooldown > 0) {
                respawnCooldown--;
            }
        }

        private void resetCooldown() {
            respawnCooldown = RESPAWN_COOLDOWN_TICKS;
        }

        private boolean canSpawn() {
            return respawnCooldown <= 0;
        }
    }
}
