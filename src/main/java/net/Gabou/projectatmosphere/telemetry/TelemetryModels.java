package net.Gabou.projectatmosphere.telemetry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable POJOs describing telemetry records. These are intentionally small and
 * serialization-friendly so they can be written as JSON Lines during export.
 */
public final class TelemetryModels {

    private TelemetryModels() {
    }

    public static final class SessionHeader {
        public final String sessionId;
        public final String projectAtmosphereVersion;
        public final String minecraftVersion;
        public final String loader;
        public final String configHash;
        public final Map<String, Object> selectedConfigValues;
        public final List<String> detectedCompatMods;
        public final String worldIdHash;
        public final String telemetryVersion;

        public SessionHeader(String projectAtmosphereVersion,
                             String minecraftVersion,
                             String loader,
                             String configHash,
                             Map<String, Object> selectedConfigValues,
                             List<String> detectedCompatMods,
                             String worldIdHash,
                             String telemetryVersion) {
            this(UUID.randomUUID().toString(), projectAtmosphereVersion, minecraftVersion, loader,
                    configHash, selectedConfigValues, detectedCompatMods, worldIdHash, telemetryVersion);
        }

        public SessionHeader(String sessionId,
                             String projectAtmosphereVersion,
                             String minecraftVersion,
                             String loader,
                             String configHash,
                             Map<String, Object> selectedConfigValues,
                             List<String> detectedCompatMods,
                             String worldIdHash,
                             String telemetryVersion) {
            this.sessionId = sessionId;
            this.projectAtmosphereVersion = projectAtmosphereVersion;
            this.minecraftVersion = minecraftVersion;
            this.loader = loader;
            this.configHash = configHash;
            this.selectedConfigValues = selectedConfigValues;
            this.detectedCompatMods = detectedCompatMods;
            this.worldIdHash = worldIdHash;
            this.telemetryVersion = telemetryVersion;
        }
    }

    public static final class PlayerExperienceSample {
        public final long gameDay;
        public final long timeOfDay;
        public final long realTimeSecondsSinceStart;
        public final String dimensionId;
        public final int positionChunkX;
        public final int positionChunkZ;
        public final String biomeId;
        public final float temperature;
        public final float humidity;
        public final float pressure;
        public final float windStrength;
        public final float windDirection;
        public final boolean vanillaIsRaining;
        public final boolean vanillaIsThundering;
        public final String paPrecipitationState;
        public final boolean temperatureOutOfExpectedRange;
        public final boolean precipitationStuck;
        public final boolean suddenJumpDetected;

        public PlayerExperienceSample(long gameDay, long timeOfDay, long realTimeSecondsSinceStart,
                                      String dimensionId, int positionChunkX, int positionChunkZ, String biomeId,
                                      float temperature, float humidity, float pressure,
                                      float windStrength, float windDirection,
                                      boolean vanillaIsRaining, boolean vanillaIsThundering, String paPrecipitationState,
                                      boolean temperatureOutOfExpectedRange,
                                      boolean precipitationStuck,
                                      boolean suddenJumpDetected) {
            this.gameDay = gameDay;
            this.timeOfDay = timeOfDay;
            this.realTimeSecondsSinceStart = realTimeSecondsSinceStart;
            this.dimensionId = dimensionId;
            this.positionChunkX = positionChunkX;
            this.positionChunkZ = positionChunkZ;
            this.biomeId = biomeId;
            this.temperature = temperature;
            this.humidity = humidity;
            this.pressure = pressure;
            this.windStrength = windStrength;
            this.windDirection = windDirection;
            this.vanillaIsRaining = vanillaIsRaining;
            this.vanillaIsThundering = vanillaIsThundering;
            this.paPrecipitationState = paPrecipitationState;
            this.temperatureOutOfExpectedRange = temperatureOutOfExpectedRange;
            this.precipitationStuck = precipitationStuck;
            this.suddenJumpDetected = suddenJumpDetected;
        }
    }

    public static final class DominantBiomeOccupancy {
        public final long gameDay;
        public final List<OccupiedChunk> topOccupiedChunks;

        public DominantBiomeOccupancy(long gameDay, List<OccupiedChunk> topOccupiedChunks) {
            this.gameDay = gameDay;
            this.topOccupiedChunks = topOccupiedChunks;
        }
    }

    public static final class OccupiedChunk {
        public final int chunkX;
        public final int chunkZ;
        public final long timeSpentSeconds;

        public OccupiedChunk(int chunkX, int chunkZ, long timeSpentSeconds) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.timeSpentSeconds = timeSpentSeconds;
        }
    }

    public static final class ForecastSnapshot {
        public final String biomeId;
        public final int sampleChunkX;
        public final int sampleChunkZ;
        public final int dayIndex;
        public final ChannelSummary temperature;
        public final ChannelSummary humidity;
        public final ChannelSummary pressure;
        public final ChannelSummary stormProbability;
        public final DayCurve curve;
        public final Modifiers modifiers;

        public ForecastSnapshot(String biomeId, int sampleChunkX, int sampleChunkZ, int dayIndex,
                                ChannelSummary temperature, ChannelSummary humidity,
                                ChannelSummary pressure, ChannelSummary stormProbability,
                                DayCurve curve, Modifiers modifiers) {
            this.biomeId = biomeId;
            this.sampleChunkX = sampleChunkX;
            this.sampleChunkZ = sampleChunkZ;
            this.dayIndex = dayIndex;
            this.temperature = temperature;
            this.humidity = humidity;
            this.pressure = pressure;
            this.stormProbability = stormProbability;
            this.curve = curve;
            this.modifiers = modifiers;
        }
    }

    public static final class ChannelSummary {
        public final float min;
        public final float max;

        public ChannelSummary(float min, float max) {
            this.min = min;
            this.max = max;
        }
    }

    public static final class DayCurve {
        public final Float valueAt3am;
        public final Float valueAt9am;
        public final Float valueAt3pm;
        public final Float valueAt9pm;

        public DayCurve(Float valueAt3am, Float valueAt9am, Float valueAt3pm, Float valueAt9pm) {
            this.valueAt3am = valueAt3am;
            this.valueAt9am = valueAt9am;
            this.valueAt3pm = valueAt3pm;
            this.valueAt9pm = valueAt9pm;
        }
    }

    public static final class Modifiers {
        public final boolean spikeActive;
        public final Float spikeMagnitude;
        public final Integer spikeRemainingDays;
        public final boolean localJoltApplied;
        public final String biomeTemplateId;

        public Modifiers(boolean spikeActive, Float spikeMagnitude, Integer spikeRemainingDays,
                         boolean localJoltApplied, String biomeTemplateId) {
            this.spikeActive = spikeActive;
            this.spikeMagnitude = spikeMagnitude;
            this.spikeRemainingDays = spikeRemainingDays;
            this.localJoltApplied = localJoltApplied;
            this.biomeTemplateId = biomeTemplateId;
        }
    }

    public sealed interface CloudEvent permits CloudCreated, CloudTickSummary, CloudEvolved, CloudMerged, CloudDied {
        String cloudId();
    }

    public static final class CloudCreated implements CloudEvent {
        public final String cloudId;
        public final String cloudType;
        public final String spawnReason;
        public final String dimensionId;
        public final int spawnChunkX;
        public final int spawnChunkZ;
        public final float baseAltitude;
        public final float initialIntensity;
        public final boolean precipitable;
        public final String associatedBiomeId;

        public CloudCreated(String cloudId, String cloudType, String spawnReason, String dimensionId,
                            int spawnChunkX, int spawnChunkZ, float baseAltitude, float initialIntensity,
                            boolean precipitable, String associatedBiomeId) {
            this.cloudId = cloudId;
            this.cloudType = cloudType;
            this.spawnReason = spawnReason;
            this.dimensionId = dimensionId;
            this.spawnChunkX = spawnChunkX;
            this.spawnChunkZ = spawnChunkZ;
            this.baseAltitude = baseAltitude;
            this.initialIntensity = initialIntensity;
            this.precipitable = precipitable;
            this.associatedBiomeId = associatedBiomeId;
        }

        @Override
        public String cloudId() {
            return cloudId;
        }
    }

    public static final class CloudTickSummary implements CloudEvent {
        public final String cloudId;
        public final int chunkX;
        public final int chunkZ;
        public final float velocityMagnitude;
        public final boolean precipitationActive;
        public final float intensity;
        public final float lifetimeRemaining;

        public CloudTickSummary(String cloudId, int chunkX, int chunkZ, float velocityMagnitude,
                                boolean precipitationActive, float intensity, float lifetimeRemaining) {
            this.cloudId = cloudId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.velocityMagnitude = velocityMagnitude;
            this.precipitationActive = precipitationActive;
            this.intensity = intensity;
            this.lifetimeRemaining = lifetimeRemaining;
        }

        @Override
        public String cloudId() {
            return cloudId;
        }
    }

    public static final class CloudEvolved implements CloudEvent {
        public final String cloudId;
        public final String fromType;
        public final String toType;
        public final String evolutionReason;
        public final Map<String, Object> parametersBefore;
        public final Map<String, Object> parametersAfter;

        public CloudEvolved(String cloudId, String fromType, String toType, String evolutionReason,
                            Map<String, Object> parametersBefore, Map<String, Object> parametersAfter) {
            this.cloudId = cloudId;
            this.fromType = fromType;
            this.toType = toType;
            this.evolutionReason = evolutionReason;
            this.parametersBefore = parametersBefore;
            this.parametersAfter = parametersAfter;
        }

        @Override
        public String cloudId() {
            return cloudId;
        }
    }

    public static final class CloudMerged implements CloudEvent {
        public final String newCloudId;
        public final List<String> sourceCloudIds;
        public final String resultingType;

        public CloudMerged(String newCloudId, List<String> sourceCloudIds, String resultingType) {
            this.newCloudId = newCloudId;
            this.sourceCloudIds = sourceCloudIds;
            this.resultingType = resultingType;
        }

        @Override
        public String cloudId() {
            return newCloudId;
        }
    }

    public static final class CloudDied implements CloudEvent {
        public final String cloudId;
        public final int endChunkX;
        public final int endChunkZ;
        public final float lifetime;
        public final String deathReason;

        public CloudDied(String cloudId, int endChunkX, int endChunkZ, float lifetime, String deathReason) {
            this.cloudId = cloudId;
            this.endChunkX = endChunkX;
            this.endChunkZ = endChunkZ;
            this.lifetime = lifetime;
            this.deathReason = deathReason;
        }

        @Override
        public String cloudId() {
            return cloudId;
        }
    }

    public static final class PrecipitationDecisionTrace {
        public final Instant timestamp;
        public final String biomeId;
        public final int sampleChunkX;
        public final int sampleChunkZ;
        public final float temperature;
        public final float humidity;
        public final float pressure;
        public final float dewPoint;
        public final float stormProbability;
        public final boolean precipitationAllowed;
        public final float intensity;
        public final List<String> gatesPassed;
        public final List<String> gatesFailed;

        public PrecipitationDecisionTrace(Instant timestamp, String biomeId, int sampleChunkX, int sampleChunkZ,
                                          float temperature, float humidity, float pressure, float dewPoint,
                                          float stormProbability, boolean precipitationAllowed, float intensity,
                                          List<String> gatesPassed, List<String> gatesFailed) {
            this.timestamp = timestamp;
            this.biomeId = biomeId;
            this.sampleChunkX = sampleChunkX;
            this.sampleChunkZ = sampleChunkZ;
            this.temperature = temperature;
            this.humidity = humidity;
            this.pressure = pressure;
            this.dewPoint = dewPoint;
            this.stormProbability = stormProbability;
            this.precipitationAllowed = precipitationAllowed;
            this.intensity = intensity;
            this.gatesPassed = gatesPassed;
            this.gatesFailed = gatesFailed;
        }
    }

    public static final class AnomalyMarker {
        public final Instant timestamp;
        public final String anomalyType;
        public final String relatedSampleId;
        public final Map<String, Object> contextData;

        public AnomalyMarker(Instant timestamp, String anomalyType, String relatedSampleId, Map<String, Object> contextData) {
            this.timestamp = timestamp;
            this.anomalyType = anomalyType;
            this.relatedSampleId = relatedSampleId;
            this.contextData = contextData;
        }
    }
}
