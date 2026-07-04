package net.Gabou.projectatmosphere.clouds.field.runtime;

import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldBackendAdapter;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSource;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSourceSnapshot;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSourceType;
import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Collects real PA backend cloud state and converts it to CloudField sources.
 * This is a read-only bridge over existing region/cluster state.
 */
public final class CloudFieldBackendSourceCollector {
    private static final int MAX_WEATHER_SUMMARY_SOURCES = 4;
    private static final float MIN_FALLBACK_HUMIDITY = 0.62F;
    private static final float MIN_FALLBACK_CLOUD_POTENTIAL = 0.22F;
    private static final float CLOUD_DRIFT_SCALE = 0.035F;

    private final CloudFieldBackendAdapter adapter;
    private final ConcurrentMap<ResourceKey<Level>, DebugInfo> lastDebugByLevel = new ConcurrentHashMap<>();

    public CloudFieldBackendSourceCollector(CloudFieldBackendAdapter adapter) {
        this.adapter = adapter == null ? new CloudFieldBackendAdapter() : adapter;
    }

    public static CloudFieldBackendSourceCollector createDefault() {
        return new CloudFieldBackendSourceCollector(new CloudFieldBackendAdapter());
    }

    public CloudFieldSourceSnapshot collect(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Collection<CloudRegionState> allRegions = CloudRegionStateStore.getAll(level);
        Collection<CloudRegionState> activeRegions = CloudRegionStateStore.getActiveRegions(level);
        List<CloudFieldSource> sources = new ArrayList<>();
        Set<RegionInstanceKey> representedRegionKeys = representedRegionKeys(allRegions);
        Set<RegionInstanceKey> sampledKeys = sampledWeatherKeys(level);
        Map<RegionInstanceKey, ForecastRegion> forecasts = ForecastGenerator.getRegionForecasts();

        int sampledClusterCount = 0;
        int activeClusterCountTotal = 0;
        int regionSourceCount = 0;
        int rejectedCandidates = 0;
        int rejectedNoRegionState = 0;
        int rejectedNoAtmosphereState = 0;
        int rejectedHumidityTooLow = 0;
        int rejectedCloudCoverTooLow = 0;
        int rejectedDensityTooLow = 0;
        int rejectedRadiusTooSmall = 0;
        int rejectedInvalidBaseTop = 0;
        int rejectedWrongDimension = 0;
        int rejectedClusterInactive = 0;
        int rejectedSourceDuplicate = 0;
        int rejectedOther = 0;
        int weatherRegionsWithoutCloudRegion = 0;

        for (CloudRegionState region : activeRegions) {
            if (region == null) {
                rejectedCandidates++;
                rejectedOther++;
                continue;
            }
            if (!region.getDimension().equals(level.dimension())) {
                rejectedCandidates++;
                rejectedWrongDimension++;
                continue;
            }
            if (!region.isActive()) {
                rejectedCandidates++;
                rejectedNoRegionState++;
                continue;
            }

            for (CloudClusterState cluster : region.getClusters()) {
                sampledClusterCount++;
                if (cluster == null || !cluster.isActive()) {
                    rejectedCandidates++;
                    rejectedClusterInactive++;
                    continue;
                }
                if (!cluster.getDimension().equals(level.dimension())) {
                    rejectedCandidates++;
                    rejectedWrongDimension++;
                    continue;
                }
                activeClusterCountTotal++;
            }

            CloudFieldSource source = adapter.fromRegion(region);
            if (source.isUsable()) {
                sources.add(source);
                regionSourceCount++;
            } else {
                rejectedCandidates++;
                rejectedOther++;
            }
        }

        int weatherFallbackCandidates = 0;
        int weatherFallbackCreated = 0;
        if (regionSourceCount == 0) {
            for (RegionInstanceKey key : sampledKeys.stream().sorted(regionKeyComparator()).toList()) {
                if (weatherFallbackCreated >= MAX_WEATHER_SUMMARY_SOURCES) {
                    break;
                }
                weatherFallbackCandidates++;
                boolean hasCloudRegion = representedRegionKeys.contains(key);
                if (!hasCloudRegion) {
                    weatherRegionsWithoutCloudRegion++;
                }
                if (hasCloudRegion) {
                    rejectedCandidates++;
                    rejectedSourceDuplicate++;
                    continue;
                }

                RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
                ForecastRegion forecast = forecasts.get(key);
                if (state == null && forecast == null) {
                    rejectedCandidates++;
                    rejectedNoRegionState++;
                    rejectedNoAtmosphereState++;
                    continue;
                }
                CloudFieldSource source = weatherSummarySource(level, key, state, forecast);
                if (source == null) {
                    rejectedCandidates++;
                    rejectedOther++;
                    continue;
                }
                if (source.humidityInfluence() < MIN_FALLBACK_HUMIDITY) {
                    rejectedCandidates++;
                    rejectedHumidityTooLow++;
                    continue;
                }
                if (source.coverage() < MIN_FALLBACK_CLOUD_POTENTIAL) {
                    rejectedCandidates++;
                    rejectedCloudCoverTooLow++;
                    continue;
                }
                if (source.density() <= 0.001F) {
                    rejectedCandidates++;
                    rejectedDensityTooLow++;
                    continue;
                }
                if (source.radius() <= 1.0F) {
                    rejectedCandidates++;
                    rejectedRadiusTooSmall++;
                    continue;
                }
                if (source.topY() <= source.baseY()) {
                    rejectedCandidates++;
                    rejectedInvalidBaseTop++;
                    continue;
                }
                if (!source.isUsable()) {
                    rejectedCandidates++;
                    rejectedOther++;
                    continue;
                }
                sources.add(source);
                weatherFallbackCreated++;
            }
        }

        DebugInfo debugInfo = new DebugInfo(
                playerPositionText(level),
                level.dimension().location().toString(),
                allRegions.size(),
                activeRegions.size(),
                sampledClusterCount,
                activeClusterCountTotal,
                regionSourceCount,
                sampledKeys.size(),
                weatherRegionsWithoutCloudRegion,
                weatherFallbackCandidates,
                weatherFallbackCreated,
                rejectedCandidates,
                rejectedNoRegionState,
                rejectedNoAtmosphereState,
                rejectedHumidityTooLow,
                rejectedCloudCoverTooLow,
                rejectedDensityTooLow,
                rejectedRadiusTooSmall,
                rejectedInvalidBaseTop,
                rejectedWrongDimension,
                rejectedClusterInactive,
                rejectedSourceDuplicate,
                rejectedOther,
                sources.size()
        );
        lastDebugByLevel.put(level.dimension(), debugInfo);

        return CloudFieldSourceSnapshot.of(
                sources,
                level.getGameTime(),
                level.dimension().location().toString(),
                "pa-native-active-regions"
        );
    }

    /**
     * Returns collector diagnostics from the latest collect pass for this
     * dimension. This is command/debug output only.
     */
    public DebugInfo lastDebugInfo(ServerLevel level) {
        if (level == null) {
            return DebugInfo.empty();
        }
        return lastDebugByLevel.getOrDefault(level.dimension(), DebugInfo.empty());
    }

    private static CloudFieldSource weatherSummarySource(
            ServerLevel level,
            RegionInstanceKey key,
            RegionAtmosphereState state,
            ForecastRegion forecast
    ) {
        long gameTime = level.getGameTime();
        BlockPos keyCenter = key.center();
        Vec3 localCenter = new Vec3(key.regionSize() * 0.5D, 0.0D, key.regionSize() * 0.5D);
        float humidity = state == null
                ? normalizeHumidity(forecast.sampleHumidity(localCenter, gameTime))
                : normalizeHumidity(state.getHumidity());
        float pressure = state == null
                ? forecast.samplePressure(gameTime)
                : state.getPressure();
        float storm = Mth.clamp(ForecastOrchestrator.getCurrentStormChance(key, gameTime), 0.0F, 1.0F);
        float liveCloudCover = state == null ? 0.0F : Mth.clamp(state.getCloudCover(), 0.0F, 1.0F);
        float liveRain = state == null ? 0.0F : Mth.clamp(state.getRainIntensity(), 0.0F, 1.0F);
        float humiditySupport = Mth.clamp((humidity - 0.58F) / 0.34F, 0.0F, 1.0F);
        float lowPressureSupport = Mth.clamp((1012.0F - pressure) / 32.0F, 0.0F, 1.0F);
        float coverage = Mth.clamp(Math.max(
                liveCloudCover,
                storm * 0.45F + humiditySupport * 0.35F + lowPressureSupport * 0.20F + liveRain * 0.20F
        ), 0.0F, 1.0F);
        float density = Mth.clamp(0.20F + humiditySupport * 0.38F + coverage * 0.34F + storm * 0.16F, 0.0F, 0.92F);
        float radius = 140.0F + coverage * 150.0F + storm * 90.0F;
        float sea = level.getSeaLevel();
        float baseY = sea + 88.0F;
        float verticalDevelopment = Mth.clamp(0.12F + storm * 0.55F + Math.max(0.0F, coverage - 0.55F) * 0.28F, 0.0F, 1.0F);
        float topY = baseY + 42.0F + verticalDevelopment * 95.0F;
        WindVector wind = ForecastOrchestrator.getWind(key, gameTime);
        Vec3 velocity = wind == null
                ? Vec3.ZERO
                : new Vec3(
                -Math.sin(wind.angleRadians()) * Math.max(0.0F, wind.baseSpeed()) * CLOUD_DRIFT_SCALE,
                0.0D,
                Math.cos(wind.angleRadians()) * Math.max(0.0F, wind.baseSpeed()) * CLOUD_DRIFT_SCALE
        );
        String sourceId = "weather-summary:" + key.regionX() + ":" + key.regionZ() + ":" + key.regionSize();
        return new CloudFieldSource(
                sourceId,
                CloudFieldSourceType.WEATHER_SUMMARY,
                level.dimension().location().toString(),
                new Vec3(keyCenter.getX(), (baseY + topY) * 0.5D, keyCenter.getZ()),
                radius,
                baseY,
                topY,
                density,
                coverage,
                humidity,
                velocity,
                Mth.clamp(0.55F + humiditySupport * 0.35F + storm * 0.10F, 0.0F, 1.0F),
                0.0F,
                verticalDevelopment,
                storm,
                seedFrom(sourceId),
                0L,
                20L * 60L * 10L,
                0,
                storm > 0.45F ? "cumulus_congestus" : "stratocumulus",
                storm > 0.45F ? "tower" : "cellular_sheet",
                true
        );
    }

    private static Set<RegionInstanceKey> representedRegionKeys(Collection<CloudRegionState> regions) {
        Set<RegionInstanceKey> keys = new HashSet<>();
        for (CloudRegionState region : regions) {
            if (region == null) {
                continue;
            }
            if (region.getCurrentRegionKey() != null) {
                keys.add(region.getCurrentRegionKey());
            }
            if (region.getSourceRegionKey() != null) {
                keys.add(region.getSourceRegionKey());
            }
            keys.add(RegionInstanceKey.from(BlockPos.containing(region.getCenter())));
        }
        return keys;
    }

    private static Set<RegionInstanceKey> sampledWeatherKeys(ServerLevel level) {
        Set<RegionInstanceKey> keys = new LinkedHashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player == null) {
                continue;
            }
            keys.add(RegionInstanceKey.from(player.blockPosition()));
            keys.addAll(ForecastOrchestrator.getActiveRegionsForPlayer(level, player));
        }
        return keys;
    }

    private static Comparator<RegionInstanceKey> regionKeyComparator() {
        return Comparator.comparingInt(RegionInstanceKey::regionX)
                .thenComparingInt(RegionInstanceKey::regionZ)
                .thenComparingInt(RegionInstanceKey::regionSize);
    }

    private static String playerPositionText(ServerLevel level) {
        if (level.players().isEmpty()) {
            return "none";
        }
        ServerPlayer player = level.players().get(0);
        BlockPos pos = player.blockPosition();
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static float normalizeHumidity(float humidity) {
        if (!Float.isFinite(humidity)) {
            return 0.0F;
        }
        return humidity > 1.5F ? Mth.clamp(humidity / 100.0F, 0.0F, 1.2F) : Mth.clamp(humidity, 0.0F, 1.2F);
    }

    private static long seedFrom(String sourceId) {
        long value = sourceId == null ? 0L : sourceId.hashCode();
        value ^= 0x9E3779B97F4A7C15L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    public record DebugInfo(
            String playerBlockPosition,
            String dimensionId,
            int sampledRegionCount,
            int activeRegionCount,
            int sampledPaClusterCount,
            int activeClusterCount,
            int regionSourceCount,
            int sampledWeatherRegionCount,
            int weatherRegionsWithoutCloudRegion,
            int weatherFallbackCandidateCount,
            int weatherFallbackCreatedCount,
            int rejectedCandidateCount,
            int rejectedNoRegionState,
            int rejectedNoAtmosphereState,
            int rejectedHumidityTooLow,
            int rejectedCloudCoverTooLow,
            int rejectedDensityTooLow,
            int rejectedRadiusTooSmall,
            int rejectedInvalidBaseTop,
            int rejectedWrongDimension,
            int rejectedClusterInactive,
            int rejectedSourceDuplicate,
            int rejectedOther,
            int finalCollectedSources
    ) {
        static DebugInfo empty() {
            return new DebugInfo("none", "unknown", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
