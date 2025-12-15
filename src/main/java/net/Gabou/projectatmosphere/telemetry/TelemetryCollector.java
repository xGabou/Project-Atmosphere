package net.Gabou.projectatmosphere.telemetry;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.SharedConstants;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final Gson GSON = new GsonBuilder().create();
    private static final int DEFAULT_TIMELINE_CAP = 240; // ~10 Minecraft days if sampled hourly
    private static final int DEFAULT_FORECAST_CAP = 90;  // 3 dominant biomes x 30 days
    private static final int DEFAULT_CLOUD_EVENT_CAP = 256;
    private static final int DEFAULT_DECISION_CAP = 128;
    private static final int DEFAULT_ANOMALY_CAP = 64;

    private static final TelemetryCollector INSTANCE = new TelemetryCollector();

    private final TelemetryRingBuffer<PlayerExperienceSample> timeline;
    private final TelemetryRingBuffer<DominantBiomeOccupancy> dominantBiomes;
    private final TelemetryRingBuffer<ForecastSnapshot> forecasts;
    private final TelemetryRingBuffer<CloudEvent> cloudEvents;
    private final TelemetryRingBuffer<PrecipitationDecisionTrace> precipitationDecisions;
    private final TelemetryRingBuffer<AnomalyMarker> anomalies;

    private final SessionHeader header;

    private TelemetryCollector() {
        this.timeline = new TelemetryRingBuffer<>(DEFAULT_TIMELINE_CAP);
        this.dominantBiomes = new TelemetryRingBuffer<>(30); // last 30 days
        this.forecasts = new TelemetryRingBuffer<>(DEFAULT_FORECAST_CAP);
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

    public synchronized void recordCloudEvent(CloudEvent event) {
        if (event instanceof CloudDied died) {
            removeCloudEvents(died.cloudId());
        }
        cloudEvents.add(event);
    }

    private synchronized void removeCloudEvents(String cloudId) {
        List<CloudEvent> current = new ArrayList<>(cloudEvents.snapshot());
        current.removeIf(ev -> ev.cloudId().equals(cloudId));
        cloudEvents.replaceAll(current);
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
        return new TelemetrySnapshot(
                header,
                timeline.snapshot(),
                dominantBiomes.snapshot(),
                forecasts.snapshot(),
                cloudEvents.snapshot(),
                precipitationDecisions.snapshot(),
                anomalies.snapshot()
        );
    }

    public void writeSnapshot(Path outputDir) throws Exception {
        Files.createDirectories(outputDir);
        TelemetrySnapshot snapshot = snapshot();

        writeJsonLines(outputDir.resolve("session_header.jsonl"), List.of(snapshot.header()));
        writeJsonLines(outputDir.resolve("player_timeline.jsonl"), snapshot.timeline());
        writeJsonLines(outputDir.resolve("dominant_biomes.jsonl"), snapshot.dominantBiomes());
        writeJsonLines(outputDir.resolve("forecast_snapshots.jsonl"), snapshot.forecasts());
        writeJsonLines(outputDir.resolve("cloud_events.jsonl"), snapshot.cloudEvents());
        writeJsonLines(outputDir.resolve("precipitation_traces.jsonl"), snapshot.precipitationTraces());
        writeJsonLines(outputDir.resolve("anomalies.jsonl"), snapshot.anomalies());
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
                "1.0"
        );
    }

    public record TelemetrySnapshot(SessionHeader header,
                                    List<PlayerExperienceSample> timeline,
                                    List<DominantBiomeOccupancy> dominantBiomes,
                                    List<ForecastSnapshot> forecasts,
                                    List<CloudEvent> cloudEvents,
                                    List<PrecipitationDecisionTrace> precipitationTraces,
                                    List<AnomalyMarker> anomalies) {
    }
}
