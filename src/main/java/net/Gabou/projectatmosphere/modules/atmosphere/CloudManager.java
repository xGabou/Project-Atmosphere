package net.Gabou.projectatmosphere.modules.atmosphere;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.async.PoolType;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.manager.SimpleCloudSpawner;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.util.ICloudRegionId;
import net.Gabou.projectatmosphere.util.WeatherSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.Gabou.projectatmosphere.manager.SimpleCloudSpawner.calculateDewPoint;
import static net.Gabou.projectatmosphere.manager.SimpleCloudSpawner.determineCloudSeverity;

/**
 * Region-driven cloud controller that mirrors SimpleClouds regions instead of storing per-biome state.
 * Each CloudRegion keeps its own CloudData, samples the biomes under its footprint, and feeds the
 * atmospheric registry by projecting contributions back into the relevant RegionAtmosphereState entries.
 */
public final class CloudManager {
    private static final Map<Integer, RegionCloudData> REGION_DATA = new ConcurrentHashMap<>();
    private static final Set<RegionInstanceKey> ACTIVE_REGIONS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final AtomicBoolean REGION_SCAN_IN_FLIGHT = new AtomicBoolean();
    private static final AtomicBoolean SPAWN_SCAN_IN_FLIGHT = new AtomicBoolean();

    private static final int REGION_SAMPLE_INTERVAL_TICKS = 40;
    private static final int SPAWN_ATTEMPT_INTERVAL_TICKS = 200;
    private static final int MAX_SPAWN_SCAN_ATTEMPTS = 6;

    private static final float HUMIDITY_MIN_FOR_THICKNESS = 0.65f;
    private static final float HUMIDITY_MAX_FOR_THICKNESS = 1.05f;
    private static final float RAIN_BIRTH_THRESHOLD = 0.9f;

    private static final float THICKNESS_GROWTH_RATE = 0.25f;
    private static final float THICKNESS_DECAY_RATE = 0.08f;
    private static final float RAIN_GROW_RATE = 0.04f;
    private static final float RAIN_DECAY_RATE = 0.02f;

    private static final float REGION_GROWTH_RATE = 0.0125f;
    private static final float REGION_SHRINK_RATE = 0.02f;

    private static final float PASSIVE_CLOUD_DECAY = 0.03f;
    private static final float PASSIVE_RAIN_DECAY = 0.02f;

    private static final float IDEAL_TEMPERATURE_C = 18f;
    private static final float TEMPERATURE_RANGE = 28f;

    private static final float HUMIDITY_SPAWN_THRESHOLD_PERCENT = 82f;

    private static final RandomSource SPAWN_RANDOM = RandomSource.create();

    private static long lastRegionSampleTick = 0L;
    private static long lastSpawnTick = 0L;

    private CloudManager() {
    }

    public static void initialize(ServerLevel level) {
        REGION_DATA.clear();
        ACTIVE_REGIONS.clear();
        lastRegionSampleTick = 0L;
        lastSpawnTick = 0L;
        REGION_SCAN_IN_FLIGHT.set(false);
        SPAWN_SCAN_IN_FLIGHT.set(false);
    }

    public static void update(ServerLevel level) {
        if (!SimpleCloudsCompat.getIsInit()) {
            fadeInactiveRegions(Collections.emptySet());
            return;
        }

        CloudGenerator generator = SimpleCloudsCompat.generator;
        if (generator == null) {
            fadeInactiveRegions(Collections.emptySet());
            return;
        }

        long tick = level.getGameTime();

        if (tick - lastRegionSampleTick >= REGION_SAMPLE_INTERVAL_TICKS) {
            lastRegionSampleTick = tick;
            if (generator.getClouds().isEmpty()) {
                REGION_DATA.clear();
                applyBiomeContributions(Collections.emptyMap());
            } else {
                startRegionScan(level, generator);
            }
        }

        if (tick - lastSpawnTick >= SPAWN_ATTEMPT_INTERVAL_TICKS) {
            lastSpawnTick = tick;
            scheduleSpawnAttempt(level, generator);
        }
    }

    private static void startRegionScan(ServerLevel level, CloudGenerator generator) {
        if (!REGION_SCAN_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }

        List<RegionDescriptor> descriptors = snapshotRegions(generator);
        if (descriptors.isEmpty()) {
            REGION_SCAN_IN_FLIGHT.set(false);
            applyBiomeContributions(Collections.emptyMap());
            return;
        }

        AsyncAtmosphereService.runWithCallback(
                PoolType.WEATHER,
                () -> computeSamples(descriptors),
                samples -> {
                    try {
                        applySamples(level, samples);
                    } finally {
                        REGION_SCAN_IN_FLIGHT.set(false);
                    }
                }
        );
    }

    private static List<RegionDescriptor> snapshotRegions(CloudGenerator generator) {
        List<CloudRegion> clouds = generator.getClouds();
        List<RegionDescriptor> descriptors = new ArrayList<>(clouds.size());
        for (CloudRegion region : clouds) {
            if (region == null) continue;
            int id = extractId(region);
            descriptors.add(new RegionDescriptor(
                    id,
                    region.getCloudTypeId(),
                    region.getWorldX(),
                    region.getWorldZ(),
                    (float) region.getRadius()
            ));
        }
        return descriptors;
    }

    private static List<RegionSample> computeSamples(List<RegionDescriptor> descriptors) {
        List<RegionAtmosphereState> states = AtmosphericStateRegistry.snapshot();
        List<RegionSample> samples = new ArrayList<>(descriptors.size());
        for (RegionDescriptor descriptor : descriptors) {
            samples.add(sampleRegion(descriptor, states));
        }
        return samples;
    }

    private static RegionSample sampleRegion(RegionDescriptor descriptor, List<RegionAtmosphereState> states) {
        double radius = Math.max(16d, descriptor.radius());
        double radiusSq = radius * radius;
        double centerX = descriptor.worldX();
        double centerZ = descriptor.worldZ();

        float humiditySum = 0f;
        float temperatureSum = 0f;
        float pressureSum = 0f;
        float rainSum = 0f;
        float weightSum = 0f;

        List<RegionSample.Footprint> footprint = new ArrayList<>();

        for (RegionAtmosphereState state : states) {
            double dist = state.distanceTo(centerX, centerZ);
            if (dist > radius) {
                continue;
            }

            float weight = 1f - (float) (dist * dist / radiusSq);
            weight = Mth.clamp(weight, 0.05f, 1f);

            humiditySum += state.getHumidity() * weight;
            temperatureSum += state.getTemperature() * weight;
            pressureSum += state.getPressure() * weight;
            rainSum += state.getRainIntensity() * weight;
            weightSum += weight;

            footprint.add(new RegionSample.Footprint(state.getKey(), weight));
        }

        if (weightSum <= 0f) {
            return new RegionSample(
                    descriptor.id(),
                    descriptor.cloudType(),
                    descriptor.worldX(),
                    descriptor.worldZ(),
                    descriptor.radius(),
                    0f,
                    0f,
                    0f,
                    1013.25f,
                    0f,
                    0f,
                    List.of()
            );
        }

        float humidityAvg = humiditySum / weightSum;
        float humidityPercent = humidityAvg * 100f;
        float temperatureAvg = temperatureSum / weightSum;
        float pressureAvg = pressureSum / weightSum;
        float rainAvg = rainSum / weightSum;
        float dewPoint = calculateDewPoint(temperatureAvg, humidityPercent);

        return new RegionSample(
                descriptor.id(),
                descriptor.cloudType(),
                descriptor.worldX(),
                descriptor.worldZ(),
                descriptor.radius(),
                humidityAvg,
                humidityPercent,
                temperatureAvg,
                pressureAvg,
                rainAvg,
                dewPoint,
                footprint
        );
    }

    private static void applySamples(ServerLevel level, List<RegionSample> samples) {
        CloudGenerator generator = SimpleCloudsCompat.generator;
        if (generator == null) {
            REGION_DATA.clear();
            fadeInactiveRegions(Collections.emptySet());
            return;
        }

        Map<Integer, CloudRegion> regionIndex = indexCloudRegions(generator);
        Map<RegionInstanceKey, BiomeContribution> contributions = new HashMap<>();
        Set<Integer> observed = new HashSet<>();

        for (RegionSample sample : samples) {
            CloudRegion region = regionIndex.get(sample.id());
            if (region == null) {
                continue;
            }
            observed.add(sample.id());

            RegionCloudData data = REGION_DATA.computeIfAbsent(sample.id(), ignored -> new RegionCloudData());
            data.updateFromSample(sample, region);

            for (RegionSample.Footprint footprint : sample.footprint()) {
                if (footprint.weight() <= 0f) {
                    continue;
                }
                contributions
                        .computeIfAbsent(footprint.key(), key -> new BiomeContribution())
                        .add(footprint.weight(), data.getThickness(), data.getRainIntensity());
            }
        }

        pruneMissingRegions(observed);
        applyBiomeContributions(contributions);
    }

    private static Map<Integer, CloudRegion> indexCloudRegions(CloudGenerator generator) {
        Map<Integer, CloudRegion> index = new HashMap<>();
        for (CloudRegion region : generator.getClouds()) {
            if (region == null) continue;
            index.put(extractId(region), region);
        }
        return index;
    }

    private static void pruneMissingRegions(Set<Integer> observed) {
        if (observed.isEmpty()) {
            REGION_DATA.clear();
            return;
        }
        REGION_DATA.keySet().removeIf(id -> !observed.contains(id));
    }

    private static void applyBiomeContributions(Map<RegionInstanceKey, BiomeContribution> contributions) {
        if (contributions.isEmpty()) {
            fadeInactiveRegions(Collections.emptySet());
            return;
        }

        Set<RegionInstanceKey> touched = new HashSet<>();
        contributions.forEach((key, value) -> {
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
            if (state == null) {
                return;
            }
            float cover = Mth.clamp(value.cloudCover, 0f, 1f);
            float rain = Mth.clamp(value.rainIntensity, 0f, 1f);
            state.setCloudCover(cover);
            state.setRainIntensity(rain);
            touched.add(key);
            ACTIVE_REGIONS.add(key);
        });

        fadeInactiveRegions(touched);
    }

    private static void fadeInactiveRegions(Set<RegionInstanceKey> refreshed) {
        if (ACTIVE_REGIONS.isEmpty()) {
            return;
        }
        List<RegionInstanceKey> toRemove = new ArrayList<>();
        for (RegionInstanceKey key : ACTIVE_REGIONS) {
            if (refreshed.contains(key)) {
                continue;
            }
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
            if (state == null) {
                toRemove.add(key);
                continue;
            }
            float newCover = Math.max(0f, state.getCloudCover() - PASSIVE_CLOUD_DECAY);
            float newRain = Math.max(0f, state.getRainIntensity() - PASSIVE_RAIN_DECAY);
            state.setCloudCover(newCover);
            state.setRainIntensity(newRain);
            if (newCover <= 0.001f && newRain <= 0.001f) {
                toRemove.add(key);
            }
        }
        toRemove.forEach(ACTIVE_REGIONS::remove);
    }

    private static void scheduleSpawnAttempt(ServerLevel level, CloudGenerator generator) {
        if (!SimpleCloudsCompat.getIsInit() || SPAWN_SCAN_IN_FLIGHT.get()) {
            return;
        }

        CloudSpawningConfig config = SimpleCloudsCompat.spawnConfig;
        if (config == null) {
            return;
        }

        int remaining = config.getMaxInitialRegions() - generator.getClouds().size();
        if (remaining <= 0) {
            return;
        }

        List<SpawnRegion> regions = List.copyOf(generator.getSpawnRegions());
        if (regions.isEmpty()) {
            return;
        }

        if (!SPAWN_SCAN_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }

        AsyncAtmosphereService.runWithCallback(
                PoolType.WEATHER,
                () -> new SpawnSearchResult(findSpawnCandidate(level, regions, config, remaining)),
                result -> {
                    try {
                        if (result.candidate() != null) {
                            spawnCandidate(level, generator, result.candidate());
                        }
                    } finally {
                        SPAWN_SCAN_IN_FLIGHT.set(false);
                    }
                }
        );
    }

    private static SpawnCandidate findSpawnCandidate(ServerLevel level, List<SpawnRegion> regions, CloudSpawningConfig config, int remaining) {
        if (regions.isEmpty()) {
            return null;
        }

        List<BlockPos> players = level.players().stream().map(ServerPlayer::blockPosition).toList();
        if (players.isEmpty()) {
            return null;
        }
        final double MAX_SPAWN_DIST_SQ = 20000d * 20000d;

        long tick = level.getGameTime();
        SpawnCandidate best = null;
        float bestScore = 0f;

        int attempts = Math.min(MAX_SPAWN_SCAN_ATTEMPTS, Math.max(1, remaining));
        for (int i = 0; i < attempts; i++) {
            SpawnRegion region = regions.get(SPAWN_RANDOM.nextInt(regions.size()));
            Vector2i point = SpawnRegion.getRandomPointInRegion(region, SPAWN_RANDOM);
            int radius = BiasedToBottomInt.of(SimpleCloudsCompat.MIN_RADIUS, SimpleCloudsCompat.MAX_RADIUS).sample(SPAWN_RANDOM);

            boolean tooFar = true;
            for (BlockPos playerPos : players) {
                double dx = point.x - playerPos.getX();
                double dz = point.y - playerPos.getZ();
                if ((dx * dx + dz * dz) <= MAX_SPAWN_DIST_SQ) {
                    tooFar = false;
                    break;
                }
            }
            if (tooFar) {
                continue;
            }

            Set<BiomeInstanceKey> keys = WeatherSampler.sampleBiomesInArea(point.x, point.y, radius, level);
            WeatherSampler.WeatherStats stats = WeatherSampler.computeWeatherStats(keys, level, tick);
            if (stats == null) {
                continue;
            }

            float humidityPercent = stats.humidity();
            if (humidityPercent < HUMIDITY_SPAWN_THRESHOLD_PERCENT) {
                continue;
            }

            float dewPoint = calculateDewPoint(stats.temperature(), humidityPercent);
            int severity = determineCloudSeverity(
                    stats.temperature(),
                    humidityPercent,
                    stats.pressure(),
                    dewPoint,
                    stats.stormFactor(),
                    level
            );
            if (severity <= 0) {
                continue;
            }

            boolean freezing = stats.temperature() <= 0f;
            String cloudId = selectCloudId(severity, freezing);

            float humidityNorm = humidityPercent / 100f;
            float score = humidityNorm * 0.6f + (severity / 7f) * 0.4f;
            if (freezing && severity >= 6) {
                score += 0.15f;
            }

            if (best == null || score > bestScore) {
                bestScore = score;
                best = new SpawnCandidate(stats, radius, cloudId);
            }
        }
        return best;
    }

    private static void spawnCandidate(ServerLevel level, CloudGenerator generator, SpawnCandidate candidate) {
        if (candidate == null || SimpleCloudsCompat.spawnConfig == null) {
            return;
        }

        BiomeInstanceKey key = new BiomeInstanceKey(candidate.stats().dominantBiome(), candidate.stats().pos());
        if (generator.getCloudAtWorldPosition(key.samplePos().getX(), key.samplePos().getZ()) != null) {
            return;
        }

        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(SimpleCloudsMod.MODID, candidate.cloudId());
        CloudSpawningConfig.Info info = SimpleCloudsCompat.spawnConfig.getWeightInfo(rl);
        if (info == null) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Unknown cloud type: {}", candidate.cloudId());
            return;
        }

        Optional<CloudRegion> dummy = SimpleCloudsCompat.createRegion(
                info,
                key,
                level,
                level.random,
                candidate.stats().windVector(),
                generator
        );
        if (dummy.isEmpty()) {
            return;
        }

        CloudRegion region = dummy.get();
        region.setRadius(candidate.radius());

        SimpleCloudsCompat.spawnCloudInBiome(
                candidate.cloudId(),
                key,
                level,
                region,
                candidate.stats().windVector()
        );
    }

    private static String selectCloudId(int severity, boolean freezing) {
        if (severity > 5 && freezing) {
            return CloudLibrary.getSnowstormCloudId();
        }
        String cloudId = CloudLibrary.getCloudIdFromSeverity(severity);
        if (CloudLibrary.isThunderCloud(cloudId) && freezing) {
            return CloudLibrary.getCloudIdFromSeverity(5);
        }
        return cloudId;
    }

    private static int extractId(CloudRegion region) {
        if (region instanceof ICloudRegionId accessor) {
            return accessor.projectatmosphere$getId();
        }
        return System.identityHashCode(region);
    }

    private static final class RegionCloudData {
        private float thickness;
        private float rainIntensity;
        private float lastHumidity;
        private int dryTicks;

        void updateFromSample(RegionSample sample, CloudRegion region) {
            lastHumidity = sample.avgHumidityNorm();

            if (lastHumidity < HUMIDITY_MIN_FOR_THICKNESS) {
                dryTicks++;
            } else {
                dryTicks = Math.max(0, dryTicks - 1);
            }

            updateThickness(sample.avgHumidityNorm());
            updateRainIntensity(sample.avgHumidityNorm());
            adjustRadius(sample, region);
        }

        float getThickness() {
            return thickness;
        }

        float getRainIntensity() {
            return rainIntensity;
        }

        private void updateThickness(float humidity) {
            float normalized = (humidity - HUMIDITY_MIN_FOR_THICKNESS) / (HUMIDITY_MAX_FOR_THICKNESS - HUMIDITY_MIN_FOR_THICKNESS);
            float target = Mth.clamp(normalized, 0f, 1f);
            float delta = target - thickness;
            if (Math.abs(delta) < 0.001f) {
                return;
            }

            if (delta > 0f) {
                thickness = Mth.clamp(thickness + delta * THICKNESS_GROWTH_RATE, 0f, 1f);
            } else {
                float drynessFactor = Math.min(1f, dryTicks / 200f);
                float rate = THICKNESS_DECAY_RATE + drynessFactor * THICKNESS_DECAY_RATE;
                thickness = Mth.clamp(thickness + delta * rate, 0f, 1f);
            }
        }

        private void updateRainIntensity(float humidity) {
            float desired;
            if (humidity >= RAIN_BIRTH_THRESHOLD && thickness > 0.35f) {
                float humidityFactor = (humidity - RAIN_BIRTH_THRESHOLD) / (1f - RAIN_BIRTH_THRESHOLD);
                desired = Mth.clamp(humidityFactor * thickness, 0f, 1f);
            } else {
                desired = 0f;
            }

            float delta = desired - rainIntensity;
            if (Math.abs(delta) < 0.001f) {
                rainIntensity = desired;
                return;
            }

            float step = delta > 0 ? RAIN_GROW_RATE : RAIN_DECAY_RATE;
            float adjustment = Mth.clamp(delta, -step, step);
            rainIntensity = Mth.clamp(rainIntensity + adjustment, 0f, 1f);
        }

        private void adjustRadius(RegionSample sample, CloudRegion region) {
            float severityBias = CloudLibrary.getSeverityFromRessourceLocation(sample.cloudType()) / 7f;
            float humidityScore = (sample.avgHumidityNorm() - HUMIDITY_MIN_FOR_THICKNESS) / (HUMIDITY_MAX_FOR_THICKNESS - HUMIDITY_MIN_FOR_THICKNESS);
            float temperaturePenalty = 1f - Mth.clamp((sample.avgTemperature() - IDEAL_TEMPERATURE_C) / TEMPERATURE_RANGE, 0f, 1f);

            float growthScore = humidityScore * 0.7f + temperaturePenalty * 0.3f;
            growthScore += (severityBias - 0.5f) * 0.15f;

            float radius = (float) region.getRadius();
            float delta;
            if (growthScore >= 0.5f) {
                delta = (growthScore - 0.5f) * REGION_GROWTH_RATE * Math.max(radius, SimpleCloudsCompat.MIN_RADIUS);
            } else {
                delta = (growthScore - 0.5f) * REGION_SHRINK_RATE * Math.max(radius, SimpleCloudsCompat.MIN_RADIUS);
            }

            float newRadius = Mth.clamp(radius + delta, SimpleCloudsCompat.MIN_RADIUS, SimpleCloudsCompat.MAX_RADIUS);
            if (dryTicks > 400 && thickness < 0.25f) {
                newRadius = Math.max(SimpleCloudsCompat.MIN_RADIUS, newRadius - REGION_SHRINK_RATE * radius);
            }
            region.setRadius(newRadius);
        }
    }

    private static final class BiomeContribution {
        float cloudCover;
        float rainIntensity;

        void add(float weight, float regionThickness, float regionRain) {
            cloudCover += weight * regionThickness;
            rainIntensity += weight * regionRain;
        }
    }

    private record RegionDescriptor(int id, ResourceLocation cloudType, double worldX, double worldZ, float radius) {
    }

    private record RegionSample(
            int id,
            ResourceLocation cloudType,
            double worldX,
            double worldZ,
            float radius,
            float avgHumidityNorm,
            float avgHumidityPercent,
            float avgTemperature,
            float avgPressure,
            float avgRainIntensity,
            float dewPoint,
            List<Footprint> footprint
    ) {
        record Footprint(RegionInstanceKey key, float weight) {
        }
    }

    private record SpawnCandidate(WeatherSampler.WeatherStats stats, int radius, String cloudId) {
    }

    private record SpawnSearchResult(SpawnCandidate candidate) {
    }
}
