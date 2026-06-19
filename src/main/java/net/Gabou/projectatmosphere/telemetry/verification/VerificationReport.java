package net.Gabou.projectatmosphere.telemetry.verification;

import net.Gabou.projectatmosphere.clouds.backend.CloudMigrationDirection;
import net.Gabou.projectatmosphere.clouds.backend.CloudMigrationStatus;
import net.Gabou.projectatmosphere.clouds.backend.CloudVisualBackend;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.modules.weathercell.WeatherCellType;
import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VerificationReport {
    private final long gameTime;
    private final long worldTime;
    private final ForecastSection forecast;
    private final AtmosphereSection atmosphere;
    private final WindSection wind;
    private final SeasonSection season;
    private final WeatherCellSection weatherCells;
    private final CloudSection clouds;
    private final CloudBackendSection cloudBackend;
    private final PaNativeBackendSection paNativeBackend;
    private final MorphologySection morphology;
    private final EvolutionSection evolution;
    private final NearestNativeCloud nearestNativeCloud;
    private final PersistenceSection persistence;
    private final List<String> issues;

    public VerificationReport(
            long gameTime,
            long worldTime,
            ForecastSection forecast,
            AtmosphereSection atmosphere,
            WindSection wind,
            SeasonSection season,
            WeatherCellSection weatherCells,
            CloudSection clouds,
            CloudBackendSection cloudBackend,
            PaNativeBackendSection paNativeBackend,
            MorphologySection morphology,
            EvolutionSection evolution,
            NearestNativeCloud nearestNativeCloud,
            PersistenceSection persistence,
            List<String> issues
    ) {
        this.gameTime = gameTime;
        this.worldTime = worldTime;
        this.forecast = forecast;
        this.atmosphere = atmosphere;
        this.wind = wind;
        this.season = season;
        this.weatherCells = weatherCells;
        this.clouds = clouds;
        this.cloudBackend = cloudBackend;
        this.paNativeBackend = paNativeBackend;
        this.morphology = morphology;
        this.evolution = evolution;
        this.nearestNativeCloud = nearestNativeCloud;
        this.persistence = persistence;
        this.issues = List.copyOf(issues);
    }

    public long gameTime() {
        return gameTime;
    }

    public long worldTime() {
        return worldTime;
    }

    public ForecastSection forecast() {
        return forecast;
    }

    public AtmosphereSection atmosphere() {
        return atmosphere;
    }

    public WindSection wind() {
        return wind;
    }

    public SeasonSection season() {
        return season;
    }

    public WeatherCellSection weatherCells() {
        return weatherCells;
    }

    public CloudSection clouds() {
        return clouds;
    }

    public CloudBackendSection cloudBackend() {
        return cloudBackend;
    }

    public PaNativeBackendSection paNativeBackend() {
        return paNativeBackend;
    }

    public MorphologySection morphology() {
        return morphology;
    }

    public EvolutionSection evolution() {
        return evolution;
    }

    public NearestNativeCloud nearestNativeCloud() {
        return nearestNativeCloud;
    }

    public PersistenceSection persistence() {
        return persistence;
    }

    public List<String> issues() {
        return issues;
    }

    public record ForecastSection(
            VerificationStatus status,
            int regionCount,
            int loadedRegionCount,
            RegionInstanceKey currentRegion,
            float temperatureC,
            float humidity,
            float pressureHpa,
            float windDirectionDeg,
            float windSpeedMps,
            boolean missingForecast,
            boolean invalidForecast,
            boolean missingRegion,
            boolean emptyForecastData
    ) {
    }

    public record AtmosphereSection(
            VerificationStatus status,
            float liveTemperatureC,
            float liveHumidity,
            float livePressureHpa,
            float cloudWater,
            float cloudCover,
            float rainIntensity,
            float windStrength,
            float windDirectionDeg,
            Float sunlight,
            Float forecastTemperatureC,
            Float baseForecastTemperatureC,
            Float effectiveForecastTemperatureC,
            Float deltaToBaseForecastC,
            Float deltaToEffectiveForecastC,
            Float schedulerTemperatureDeltaC,
            Float baseRelaxTemperatureDeltaC,
            Float seasonalDriftTemperatureDeltaC,
            Float forecastHumidity,
            Float forecastPressureHpa,
            Float pressureTargetHpa,
            Float forecastPressureCurrentSampleHpa,
            Float liveStateRawPressureTargetHpa,
            Float effectivePressureTargetHpa,
            String pressureTargetSource,
            Integer pressureTargetDayIndex,
            Integer currentForecastDayIndex,
            Boolean pressureTargetUsesCurrentForecastDay,
            Boolean day0PressureTargetProfileActive,
            Boolean stalePressureTargetDetected,
            Float stalePressureTargetCorrectionDelta,
            String pressureAnomalyClassification,
            Float normalPressureReferenceHpa,
            Float pressureDeltaToForecastHpa,
            Float pressureDeltaToNormalHpa,
            Float schedulerPressureDelta,
            Float forecastRecoveryPressureDelta,
            Float pressureGuardDelta,
            Float baseRelaxPressureDelta,
            Float rainPressureDelta,
            Float windPressureMixDelta,
            Float oceanFlux,
            Float oceanPressureInfluence,
            Float cyclonePressureInfluence,
            Float stormPressureSupport,
            Float thunderstormSupport,
            Float seasonPressureOffsetHpa,
            Float seasonTemperatureOffsetC,
            Boolean pressureRecoveryEligible,
            Boolean cycloneSeedEligible,
            Float cycloneSeedSupport,
            Float cycloneIntensificationSupport,
            Float cycloneSevereSupport,
            Boolean unsupportedLowRecoveryActive,
            Float unsupportedLowRecoveryDelta,
            Float unsupportedLowRecoveryCapPerDay,
            Float supportResistance
    ) {
    }

    public record WindSection(
            VerificationStatus status,
            boolean runtimeExists,
            float speedMps,
            float directionDeg,
            float gustStrength,
            float gustModifier,
            boolean gustActive,
            boolean persistencePresent,
            boolean missingRuntime,
            boolean invalidState
    ) {
    }

    public record SeasonSection(
            VerificationStatus status,
            String providerId,
            SeasonStage stage,
            float progress,
            float temperatureOffset,
            float driftTemperatureOffset,
            float driftPressureOffset,
            boolean driftInitialized,
            boolean driftPersistencePresent
    ) {
    }

    public record WeatherCellSection(
            VerificationStatus status,
            int totalActive,
            int rainCellCount,
            int thunderstormCount,
            int supercellCount,
            NearestWeatherCell nearest
    ) {
    }

    public record NearestWeatherCell(
            WeatherCellType type,
            double distanceMeters,
            float intensity,
            float radius,
            float evolutionScore,
            float severeEvolutionScore,
            RegionInstanceKey region
    ) {
    }

    public record CloudSection(
            VerificationStatus status,
            int activeRegionCount,
            int activeClusterCount,
            NearestCloud nearest
    ) {
    }

    public record CloudBackendSection(
            CloudVisualBackend currentVisualBackend,
            CloudVisualBackend lastVisualBackend,
            boolean simpleCloudsLoaded,
            int paNativeCloudsStored,
            int paNativeCloudsRendered,
            int bridgeSnapshotsStored,
            CloudMigrationDirection lastMigrationDirection,
            CloudMigrationStatus migrationStatus,
            boolean duplicateVisualCloudRisk
    ) {
    }

    public record PaNativeBackendSection(
            boolean paNativeEnabled,
            int paNativeCloudsStored,
            int paNativeCloudsSynced,
            int paNativeCloudsRendered,
            boolean nativeCloudSyncActive,
            boolean nativeCloudRenderActive,
            boolean nativeCloudShadowActive,
            boolean fallbackDarkeningActive,
            boolean lightingMetadataActive,
            boolean dhMetadataActive
    ) {
    }

    public record NearestCloud(
            String cloudTypeId,
            double distanceMeters,
            float cloudCoverContribution,
            float cloudWaterContribution
    ) {
    }

    public record MorphologySection(
            VerificationStatus status,
            EnumMap<CloudMorphologyFamily, Integer> countsByFamily,
            int missingAssignments,
            int unknownAssignments,
            int fallbackAssignments
    ) {
    }

    public record EvolutionSection(
            VerificationStatus status,
            Map<String, Integer> countsByType,
            NearestEvolvingCloud nearest
    ) {
    }

    public record NearestEvolvingCloud(
            String cloudTypeId,
            CloudMorphologyFamily morphologyFamily,
            float density,
            float radius,
            float coverage,
            int cloudTypeTicks,
            String previousCloudTypeId,
            float transitionBlend,
            float rawRadius,
            float renderedRadius,
            float targetRadius,
            String radiusCap,
            float radiusDelta,
            float growthRate,
            String growthBlockedReason,
            float rawCoverage,
            float renderedCoverage,
            float targetCoverage,
            float rawDensity,
            float renderedDensity,
            float targetDensity,
            float evolutionSupport,
            float growthSupport,
            String growthPhase,
            String migrationSource,
            String bridgeSnapshotId,
            String cloudWorldPosition,
            String previousCloudPosition,
            String cloudVelocity,
            float cloudDriftSpeed,
            float cloudWindCoupling,
            String cloudMotionSource,
            long lastMotionTick,
            long lastGrowthTick,
            long lastRenderSnapshotTick,
            int shapeSeed,
            boolean morphologyNoiseStable,
            String renderBounds,
            String lodTier
    ) {
    }

    public record NearestNativeCloud(
            String cloudId,
            String type,
            String position,
            String previousPosition,
            String velocity,
            float windCoupling,
            boolean motionActive,
            String motionBlockedReason,
            float radius,
            float targetRadius,
            float renderedRadius,
            float radiusCap,
            float coverage,
            float targetCoverage,
            float density,
            float targetDensity,
            float growthRate,
            boolean growthActive,
            String growthBlockedReason,
            int age,
            int lifetime,
            long lastStateTick,
            long lastSyncTick,
            long lastRenderSnapshotTick,
            String renderBounds
    ) {
    }

    public record PersistenceSection(
            VerificationStatus status,
            VerificationStatus forecast,
            VerificationStatus atmosphere,
            VerificationStatus seasonalDrift,
            VerificationStatus wind,
            VerificationStatus weatherCells,
            VerificationStatus cloudRegions
    ) {
    }

    public static Map<String, Integer> emptyEvolutionCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("vapor_cluster", 0);
        counts.put("cumulus_humilis", 0);
        counts.put("cumulus_mediocris", 0);
        counts.put("cumulus_congestus", 0);
        counts.put("cumulonimbus_calvus", 0);
        counts.put("cumulonimbus_capillatus", 0);
        counts.put("stratus_nebulosus", 0);
        counts.put("stratocumulus", 0);
        counts.put("nimbostratus", 0);
        counts.put("cirrus", 0);
        return counts;
    }

    public static EnumMap<CloudMorphologyFamily, Integer> emptyMorphologyCounts() {
        EnumMap<CloudMorphologyFamily, Integer> counts = new EnumMap<>(CloudMorphologyFamily.class);
        for (CloudMorphologyFamily family : CloudMorphologyFamily.values()) {
            counts.put(family, 0);
        }
        return counts;
    }
}
