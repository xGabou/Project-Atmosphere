package net.Gabou.projectatmosphere.modules.atmosphere;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import sereneseasons.api.season.SeasonHelper;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static net.Gabou.projectatmosphere.manager.SimpleCloudSpawner.calculateDewPoint;
import static net.Gabou.projectatmosphere.manager.SimpleCloudSpawner.determineCloudSeverity;

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
    private static final int DESPAWN_DELAY_TICKS = 20 * 60; // must stay dry for 15s

    private static int counter = 0;



    private CloudManager() {
    }

    public static void initialize(ServerLevel level) {
        CLOUDS.clear();
        counter= 0;
    }

    public static void update(ServerLevel level) {
        if(counter++ % 100 != 0)
            return;
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

            if (data.spawnedVisual) {
                if (humidity < HUMIDITY_SPAWN_THRESHOLD - 0.03f) {
                    data.dryTicks++;
                    if (data.dryTicks >= DESPAWN_DELAY_TICKS) {
                        data.spawnedVisual = false;
                        data.resetCooldown();
                    }
                } else {
                    data.dryTicks = 0;
                }
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
        if (level == null || state == null) return false;
        if (!SimpleCloudsCompat.getIsInit()) return false;

        CloudGenerator generator = SimpleCloudsCompat.generator;
        if (generator == null) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] SimpleClouds generator is null, cannot spawn visual cloud.");
            return false;
        }

        // Prevent duplicate regions
        CloudRegion existing = generator.getCloudAtWorldPosition(
                state.getPosition().getX(),
                state.getPosition().getZ()
        );
        if (existing != null) return false;

        // Gather local atmospheric data
        float temperature = state.getTemperature();
        float humidity = state.getHumidity();
        float pressure = state.getPressure();
        float dewPoint = calculateDewPoint(temperature, humidity);
        float stormFactor = ForecastOrchestrator.getCurrentStormChance(state.getKey(), level.getDayTime() % 24000L); // or derive from your cyclone logic
        // Compute cloud severity
        int severity = determineCloudSeverity(temperature, humidity, pressure, dewPoint, stormFactor, level);
        if (severity <= 0) return false;
        boolean freezing = temperature <= 0.0F;

        boolean snowstorm = severity > 5 && freezing;
        String cloudId;
        if (snowstorm) {
            cloudId = CloudLibrary.getSnowstormCloudId();
        } else {
            cloudId = CloudLibrary.getCloudIdFromSeverity(severity);
            if (CloudLibrary.isThunderCloud(cloudId) && freezing) {
                cloudId = CloudLibrary.getCloudIdFromSeverity(5);
            }
        }

        CloudSpawningConfig config = generator.getSpawnConfig().get();
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(SimpleCloudsMod.MODID, cloudId);
        CloudSpawningConfig.Info info = config.getWeightInfo(rl);
        if (info == null) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Unknown cloud type: {}", cloudId);
            return false;
        }

        // Construct the biome key for the spawn area
        BiomeInstanceKey key = state.getKey();
        WindVector wind = state.getWind() != null ? state.getWind() : WindVector.fromBase(1f, 0f);

        // Optionally pre-create the region object (like trySpawnClouds)
        Optional<CloudRegion> dummyOpt = SimpleCloudsCompat.createRegion(
                info,
                key,
                level,
                level.random,
                wind,
                generator
        );
        if (dummyOpt.isEmpty()) return false;

        CloudRegion dummy = dummyOpt.get();
        dummy.setRadius(200 + (severity * 50)); // optional: scale radius by severity

        // Finally, spawn the visual cloud
        return SimpleCloudsCompat.spawnCloudInBiome(cloudId, key, level, dummy, wind) != null;
    }


    private static final class CloudData {
        private float thickness;
        private long ticksAlive;
        private float rainIntensity;
        private boolean spawnedVisual;
        private int respawnCooldown;

        private int dryTicks = 0;

        private void updateThickness(float humidity) {
            float target = (humidity - THICKNESS_MIN_HUMIDITY) / (THICKNESS_MAX_HUMIDITY - THICKNESS_MIN_HUMIDITY);
            target = Mth.clamp(target, 0f, 1f);
            float delta = target - thickness;

            // smoother transition (less twitch)
            if (Math.abs(delta) < 0.005f) return;

            if (delta > 0f) {
                float increase = delta * 0.05f; // 5% of delta per tick
                thickness = Mth.clamp(thickness + increase, 0f, 1f);
            } else {
                float dryness = Mth.clamp((HUMIDITY_SPAWN_THRESHOLD - humidity) / HUMIDITY_SPAWN_THRESHOLD, 0f, 1f);
                float decay = THICKNESS_BASE_DECAY_RATE + dryness * THICKNESS_EXTREME_DECAY_RATE;
                thickness = Mth.clamp(thickness - decay, 0f, 1f);
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
