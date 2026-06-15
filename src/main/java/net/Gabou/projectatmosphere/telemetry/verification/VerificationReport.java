package net.Gabou.projectatmosphere.telemetry.verification;

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
    private final MorphologySection morphology;
    private final EvolutionSection evolution;
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
            MorphologySection morphology,
            EvolutionSection evolution,
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
        this.morphology = morphology;
        this.evolution = evolution;
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

    public MorphologySection morphology() {
        return morphology;
    }

    public EvolutionSection evolution() {
        return evolution;
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
            Float forecastHumidity,
            Float forecastPressureHpa
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
            float transitionBlend
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
