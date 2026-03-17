package net.Gabou.projectatmosphere.telemetry;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.Gabou.projectatmosphere.telemetry.TelemetryModels.*;

/**
 * In-memory telemetry collector honoring the diagnostic storage constraints.
 *
 *  - Bounded buffers for timelines and events.
 *  - No disk IO until explicit export or anomaly flush.
 *  - Stores summaries instead of tick-by-tick state.
 */
public final class TelemetryCollector {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (com.google.gson.JsonSerializer<Instant>) (src, typeOfSrc, context) ->
                    src == null ? null : new JsonPrimitive(src.toString()))
            .registerTypeAdapter(Instant.class, (com.google.gson.JsonDeserializer<Instant>) (json, typeOfT, context) ->
                    json == null || json.isJsonNull() ? null : Instant.parse(json.getAsString()))
            .create();
    private static final int DEFAULT_TIMELINE_CAP = 240; // ~10 Minecraft days if sampled hourly
    private static final int DEFAULT_FORECAST_CAP = 90;  // 3 dominant biomes x 30 days
    private static final int DEFAULT_CLOUD_EVENT_CAP = 1024;
    private static final int DEFAULT_REGION_FORECAST_CAP = 480;
    private static final int DEFAULT_HUMIDITY_BUDGET_CAP = 2048;
    private static final int DEFAULT_ATMOSPHERE_COUPLING_CAP = 2048;
    private static final int DEFAULT_DECISION_CAP = 128;
    private static final int DEFAULT_ANOMALY_CAP = 64;

    private static final TelemetryCollector INSTANCE = new TelemetryCollector();

    private final TelemetryRingBuffer<PlayerExperienceSample> timeline;
    private final TelemetryRingBuffer<DominantBiomeOccupancy> dominantBiomes;
    private final TelemetryRingBuffer<ForecastSnapshot> forecasts;
    private final TelemetryRingBuffer<RegionForecastSample> regionForecasts;
    private final TelemetryRingBuffer<HumidityBudgetSample> humidityBudgets;
    private final TelemetryRingBuffer<AtmosphereCouplingSample> atmosphereCoupling;
    private final TelemetryRingBuffer<CloudEvent> cloudEvents;
    private final TelemetryRingBuffer<PrecipitationDecisionTrace> precipitationDecisions;
    private final TelemetryRingBuffer<AnomalyMarker> anomalies;

    private final SessionHeader header;

    private TelemetryCollector() {
        this.timeline = new TelemetryRingBuffer<>(DEFAULT_TIMELINE_CAP);
        this.dominantBiomes = new TelemetryRingBuffer<>(30); // last 30 days
        this.forecasts = new TelemetryRingBuffer<>(DEFAULT_FORECAST_CAP);
        this.regionForecasts = new TelemetryRingBuffer<>(DEFAULT_REGION_FORECAST_CAP);
        this.humidityBudgets = new TelemetryRingBuffer<>(DEFAULT_HUMIDITY_BUDGET_CAP);
        this.atmosphereCoupling = new TelemetryRingBuffer<>(DEFAULT_ATMOSPHERE_COUPLING_CAP);
        this.cloudEvents = new TelemetryRingBuffer<>(DEFAULT_CLOUD_EVENT_CAP);
        this.precipitationDecisions = new TelemetryRingBuffer<>(DEFAULT_DECISION_CAP);
        this.anomalies = new TelemetryRingBuffer<>(DEFAULT_ANOMALY_CAP);
        this.header = buildHeader();
    }

    public static TelemetryCollector get() {
        return INSTANCE;
    }

    // ----------------------- recorders -----------------------

    public synchronized void recordPlayerSample(PlayerExperienceSample sample) {
        timeline.add(sample);
    }

    public synchronized void recordDominantBiome(DominantBiomeOccupancy entry) {
        dominantBiomes.add(entry);
    }

    public synchronized void recordForecastSnapshot(ForecastSnapshot snapshot) {
        forecasts.add(snapshot);
    }

    public synchronized void recordRegionForecastSample(RegionForecastSample sample) {
        regionForecasts.add(sample);
    }

    public synchronized void recordHumidityBudgetSample(HumidityBudgetSample sample) {
        humidityBudgets.add(sample);
    }

    public synchronized void recordAtmosphereCouplingSample(AtmosphereCouplingSample sample) {
        atmosphereCoupling.add(sample);
    }

    public synchronized void recordCloudEvent(CloudEvent event) {
        cloudEvents.add(event);
    }

    public synchronized void recordDecision(PrecipitationDecisionTrace trace) {
        precipitationDecisions.add(trace);
    }

    public synchronized void recordAnomaly(AnomalyMarker marker) {
        anomalies.add(marker);
    }

    // ----------------------- export helpers -----------------------

    public synchronized SessionHeader getHeader() {
        return header;
    }

    public synchronized TelemetrySnapshot snapshot() {
        List<BiomeAverage> biomeAverages = buildBiomeAverages();
        List<ActiveRegionForecast> activeForecasts = buildActiveRegionForecasts();
        return new TelemetrySnapshot(
                header,
                timeline.snapshot(),
                dominantBiomes.snapshot(),
                forecasts.snapshot(),
                regionForecasts.snapshot(),
                humidityBudgets.snapshot(),
                atmosphereCoupling.snapshot(),
                cloudEvents.snapshot(),
                precipitationDecisions.snapshot(),
                anomalies.snapshot(),
                biomeAverages,
                activeForecasts
        );
    }

    public void writeSnapshot(Path outputDir) throws Exception {
        Files.createDirectories(outputDir);
        TelemetrySnapshot snapshot = snapshot();

        List<Object> atmosphereState = new ArrayList<>(snapshot.biomeAverages());
        atmosphereState.addAll(snapshot.activeRegionForecasts());

        writeJsonLines(outputDir.resolve("session_header.jsonl"), List.of(snapshot.header()));
        writeJsonLines(outputDir.resolve("player_timeline.jsonl"), snapshot.timeline());
        writeJsonLines(outputDir.resolve("dominant_biomes.jsonl"), snapshot.dominantBiomes());
        writeJsonLines(outputDir.resolve("forecast_snapshots.jsonl"), snapshot.forecasts());
        writeJsonLines(outputDir.resolve("region_forecast_samples.jsonl"), snapshot.regionForecastSamples());
        writeJsonLines(outputDir.resolve("humidity_budget.jsonl"), snapshot.humidityBudgetSamples());
        writeJsonLines(outputDir.resolve("atmosphere_coupling.jsonl"), snapshot.atmosphereCouplingSamples());
        writeJsonLines(outputDir.resolve("cloud_events.jsonl"), snapshot.cloudEvents());
        writeJsonLines(outputDir.resolve("precipitation_traces.jsonl"), snapshot.precipitationTraces());
        writeJsonLines(outputDir.resolve("anomalies.jsonl"), snapshot.anomalies());
        writeJsonLines(outputDir.resolve("atmosphere_state.jsonl"), atmosphereState);
    }

    private void writeJsonLines(Path file, List<?> payload) throws Exception {
        if (payload.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        try (var writer = Files.newBufferedWriter(file)) {
            for (Object obj : payload) {
                writer.write(GSON.toJson(obj));
                writer.newLine();
            }
        }
    }

    private SessionHeader buildHeader() {
        String paVersion = ModList.get().getModContainerById(ProjectAtmosphere.MODID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
        String mcVersion = SharedConstants.getCurrentVersion().getName();
        String loader = "Forge " + FMLLoader.versionInfo().forgeVersion();
        Map<String, Object> selectedValues = new HashMap<>();
        selectedValues.put("forceSharedExecutor", AtmoCommonConfig.FORCE_SHARED_EXECUTOR.get());
        selectedValues.put("cloudRenderDistance", AtmoCommonConfig.CLOUD_RENDER_DISTANCE.get());
        selectedValues.put("debugMode", AtmoCommonConfig.DEBUG_MODE.get());

        String configHash = Hashing.sha256()
                .hashString(selectedValues.toString(), StandardCharsets.UTF_8)
                .toString();

        List<String> compatMods = ModList.get().getMods().stream()
                .filter(info -> info.getModId().toLowerCase().contains("cloud") || info.getDisplayName().toLowerCase().contains("weather"))
                .map(info -> info.getModId() + "@" + info.getVersion())
                .toList();

        return new SessionHeader(
                UUID.randomUUID().toString(),
                paVersion,
                mcVersion,
                loader,
                configHash,
                Collections.unmodifiableMap(selectedValues),
                Collections.unmodifiableList(compatMods),
                null,
                "1.3"
        );
    }

    private List<BiomeAverage> buildBiomeAverages() {
        List<RegionAtmosphereState> states = new ArrayList<>(AtmosphericStateRegistry.getStates());
        if (states.isEmpty()) {
            return List.of();
        }
        Map<String, BiomeAverageAccumulator> accumulators = new HashMap<>();
        for (RegionAtmosphereState state : states) {
            if (state == null || state.getDominantBiome() == null) {
                continue;
            }
            String biomeId = state.getDominantBiome().toString();
            BiomeAverageAccumulator acc = accumulators.computeIfAbsent(biomeId, ignored -> new BiomeAverageAccumulator());
            acc.samples++;
            acc.temperature += state.getTemperature();
            acc.humidity += state.getHumidity();
            acc.pressure += state.getPressure();
            acc.cloudCover += state.getCloudCover();
            acc.rainIntensity += state.getRainIntensity();
            WindVector wind = ForecastOrchestrator.getWind(state.getKey(), 0L);
            float speed = wind.baseSpeed();
            acc.windSpeed += speed;
            acc.windX += speed * (float) Math.cos(wind.angleRadians());
            acc.windZ += speed * (float) Math.sin(wind.angleRadians());
        }
        List<BiomeAverage> out = new ArrayList<>(accumulators.size());
        accumulators.forEach((biomeId, acc) -> {
            if (acc.samples <= 0) {
                return;
            }
            Float direction = null;
            if (acc.windX != 0f || acc.windZ != 0f) {
                float heading = (float) Math.toDegrees(Math.atan2(acc.windZ, acc.windX));
                direction = Mth.wrapDegrees(heading);
            }
            out.add(new BiomeAverage(
                    biomeId,
                    acc.samples,
                    acc.temperature / acc.samples,
                    acc.humidity / acc.samples,
                    acc.pressure / acc.samples,
                    acc.windSpeed / acc.samples,
                    direction,
                    acc.cloudCover / acc.samples,
                    acc.rainIntensity / acc.samples
            ));
        });
        out.sort(Comparator.comparing(b -> b.biomeId));
        return out;
    }

    private List<ActiveRegionForecast> buildActiveRegionForecasts() {
        Map<RegionInstanceKey, ForecastRegion> forecasts = ForecastGenerator.getRegionForecasts();
        if (forecasts.isEmpty()) {
            return List.of();
        }
        Set<RegionInstanceKey> targets = new HashSet<>(AtmosphericStateRegistry.getActiveStates());
        if (targets.isEmpty()) {
            targets.addAll(AtmosphericStateRegistry.getStatesAsMap().keySet());
        }
        if (targets.isEmpty()) {
            return List.of();
        }
        List<ActiveRegionForecast> out = new ArrayList<>();
        for (RegionInstanceKey key : targets) {
            ForecastRegion region = forecasts.get(key);
            if (region == null) {
                continue;
            }
            ChannelSummary temperature = summarizeWeek(region.getTemperature());
            ChannelSummary humidity = summarizeWeek(region.getHumidity());
            ChannelSummary pressure = summarizeWeek(region.getPressure());
            WindVector wind = ForecastOrchestrator.getForecastWind(region.getKey(), 0L);
            float windSpeed = Math.max(0f, wind.baseSpeed());
            Float windDirection = Mth.wrapDegrees((float) Math.toDegrees(wind.angleRadians()));
            int anchorChunkX = region.getAnchor() == null ? 0 : region.getAnchor().getX() >> 4;
            int anchorChunkZ = region.getAnchor() == null ? 0 : region.getAnchor().getZ() >> 4;
            String dominantBiome = selectDominantBiome(region.getBiomeWeights());
            out.add(new ActiveRegionForecast(
                    region.getKey().toString(),
                    region.getKey().regionX(),
                    region.getKey().regionZ(),
                    region.getKey().regionSize(),
                    dominantBiome,
                    anchorChunkX,
                    anchorChunkZ,
                    temperature,
                    humidity,
                    pressure,
                    windSpeed,
                    windDirection
            ));
        }
        out.sort(Comparator
                .comparingInt((ActiveRegionForecast f) -> f.regionX)
                .thenComparingInt(f -> f.regionZ));
        return out;
    }

    private ChannelSummary summarizeWeek(float[][] curve) {
        if (curve == null || curve.length == 0) {
            return new ChannelSummary(0f, 0f);
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float[] day : curve) {
            if (day == null || day.length == 0) {
                continue;
            }
            for (float value : day) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        if (min == Float.MAX_VALUE) {
            return new ChannelSummary(0f, 0f);
        }
        return new ChannelSummary(min, max);
    }

    private String selectDominantBiome(Map<ResourceLocation, Integer> weights) {
        if (weights == null || weights.isEmpty()) {
            return "unknown";
        }
        ResourceLocation best = null;
        int bestWeight = Integer.MIN_VALUE;
        for (Map.Entry<ResourceLocation, Integer> entry : weights.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > bestWeight) {
                best = entry.getKey();
                bestWeight = entry.getValue();
            }
        }
        return best == null ? "unknown" : best.toString();
    }

    private static final class BiomeAverageAccumulator {
        int samples;
        float temperature;
        float humidity;
        float pressure;
        float windSpeed;
        float windX;
        float windZ;
        float cloudCover;
        float rainIntensity;
    }

    public record TelemetrySnapshot(SessionHeader header,
                                    List<PlayerExperienceSample> timeline,
                                    List<DominantBiomeOccupancy> dominantBiomes,
                                    List<ForecastSnapshot> forecasts,
                                    List<RegionForecastSample> regionForecastSamples,
                                    List<HumidityBudgetSample> humidityBudgetSamples,
                                    List<AtmosphereCouplingSample> atmosphereCouplingSamples,
                                    List<CloudEvent> cloudEvents,
                                    List<PrecipitationDecisionTrace> precipitationTraces,
                                    List<AnomalyMarker> anomalies,
                                    List<BiomeAverage> biomeAverages,
                                    List<ActiveRegionForecast> activeRegionForecasts) {
    }
}
