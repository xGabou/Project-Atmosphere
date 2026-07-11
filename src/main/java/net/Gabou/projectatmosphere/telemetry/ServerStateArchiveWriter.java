package net.Gabou.projectatmosphere.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.backend.CloudBackendMigrationSavedData;
import net.Gabou.projectatmosphere.clouds.backend.CloudBackendMigrationState;
import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.cell.sim.CloudCellSimulationManager;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastDataStorage;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericUpdateScheduler;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneManager;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneSnapshot;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.atmosphere.SeasonalAtmosphericDrift;
import net.Gabou.projectatmosphere.modules.atmosphere.WeakLowManager;
import net.Gabou.projectatmosphere.modules.atmosphere.WeakLowState;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.ocean.OceanBasinManager;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.storm.GlobalStormHistoryData;
import net.Gabou.projectatmosphere.modules.weather.StormSeverityScale;
import net.Gabou.projectatmosphere.modules.weather.ServerWeatherStateResolver;
import net.Gabou.projectatmosphere.modules.weather.RegionalWeatherPhase;
import net.Gabou.projectatmosphere.modules.weathercell.WeatherCellManager;
import net.Gabou.projectatmosphere.modules.weathercell.WeatherCellState;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.SharedConstants;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Captures authoritative server state for export.
 */
public final class ServerStateArchiveWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ServerStateArchiveWriter() {
    }

    public static ServerStateSnapshot capture(MinecraftServer server) {
        ServerLevel primaryLevel = resolvePrimaryLevel(server);
        long gameTime = primaryLevel == null ? 0L : primaryLevel.getGameTime();
        long dayTime = primaryLevel == null ? 0L : primaryLevel.getDayTime();
        String primaryDimensionId = primaryLevel == null ? "unknown" : primaryLevel.dimension().location().toString();

        List<ServerLevelSnapshot> levelSnapshots = new ArrayList<>();
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                levelSnapshots.add(captureLevelSnapshot(level));
            }
        }

        List<ForecastCenterExport> forecastCenters = captureForecastCenters();
        List<ForecastRegionExport> forecastRegions = captureForecastRegions(gameTime);
        List<AtmosphereRegionExport> atmosphereRegions = captureAtmosphereRegions(primaryLevel, gameTime);
        List<CycloneExport> cyclones = captureCyclones();
        List<WeakLowExport> weakLows = captureWeakLows();
        List<TornadoExport> tornadoes = new ArrayList<>(captureNativeTornadoes(primaryLevel));
        tornadoes.addAll(SevereWeatherArchiveBridge.captureTornadoes());
        tornadoes.sort(Comparator.comparing(TornadoExport::id));
        List<HurricaneExport> hurricanes = SevereWeatherArchiveBridge.captureHurricanes();
        List<RawNbtSnapshot> globalSnapshots = captureGlobalSnapshots(primaryLevel);

        ServerManifest manifest = new ServerManifest(
                Instant.now().toEpochMilli(),
                primaryDimensionId,
                projectAtmosphereVersion(),
                SharedConstants.getCurrentVersion().getName(),
                "Forge " + FMLLoader.versionInfo().forgeVersion(),
                AtmoCommonConfig.TELEMETRY_ENABLED.get(),
                net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices.isSimpleCloudsLoaded(),
                levelSnapshots.size(),
                forecastCenters.size(),
                forecastRegions.size(),
                atmosphereRegions.size(),
                countCloudRegions(levelSnapshots),
                countWeatherCells(levelSnapshots),
                cyclones.size(),
                weakLows.size(),
                tornadoes.size(),
                hurricanes.size(),
                levelSnapshots.stream().map(ServerLevelSnapshot::summary).toList()
        );

        return new ServerStateSnapshot(
                manifest,
                forecastCenters,
                forecastRegions,
                atmosphereRegions,
                cyclones,
                weakLows,
                tornadoes,
                hurricanes,
                globalSnapshots,
                levelSnapshots
        );
    }

    public static void write(Path outputDir, ServerStateSnapshot snapshot) throws IOException {
        Files.createDirectories(outputDir);
        writeJson(outputDir.resolve("manifest.json"), snapshot.manifest());
        writeJsonLines(outputDir.resolve("forecast_centers.jsonl"), snapshot.forecastCenters());
        writeJsonLines(outputDir.resolve("forecast_regions.jsonl"), snapshot.forecastRegions());
        writeJsonLines(outputDir.resolve("atmosphere_regions.jsonl"), snapshot.atmosphereRegions());
        writeJsonLines(outputDir.resolve("global_state.jsonl"), snapshot.globalState());
        writeJsonLines(outputDir.resolve("cyclones.jsonl"), snapshot.cyclones());
        writeJsonLines(outputDir.resolve("weak_lows.jsonl"), snapshot.weakLows());
        writeJsonLines(outputDir.resolve("tornadoes.jsonl"), snapshot.tornadoes());
        writeJsonLines(outputDir.resolve("hurricanes.jsonl"), snapshot.hurricanes());

        Path levelsDir = outputDir.resolve("levels");
        for (ServerLevelSnapshot level : snapshot.levels()) {
            String dimensionFolder = sanitize(level.summary().dimensionId());
            Path levelDir = levelsDir.resolve(dimensionFolder);
            Files.createDirectories(levelDir);
            writeJson(levelDir.resolve("level.json"), level.summary());
            writeJsonLines(levelDir.resolve("cloud_regions.jsonl"), level.cloudRegions());
            writeJsonLines(levelDir.resolve("weather_cells.jsonl"), level.weatherCells());
            writeJsonLines(levelDir.resolve("cloud_backend_migration.jsonl"), List.of(level.cloudBackendMigration()));
            writeJsonLines(levelDir.resolve("storm_history.jsonl"), List.of(level.stormHistory()));
        }
    }

    private static List<ForecastCenterExport> captureForecastCenters() {
        return ForecastDataStorage.playerData.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    var pos = entry.getValue();
                    return new ForecastCenterExport(entry.getKey().toString(), pos.getX(), pos.getY(), pos.getZ());
                })
                .toList();
    }

    private static List<ForecastRegionExport> captureForecastRegions(long gameTime) {
        Map<RegionInstanceKey, ForecastRegion> regions = new LinkedHashMap<>(ForecastGenerator.getRegionForecasts());
        return regions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(RegionInstanceKey::toString)))
                .map(entry -> toForecastRegionExport(entry.getKey(), entry.getValue(), gameTime))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static ForecastRegionExport toForecastRegionExport(RegionInstanceKey key, ForecastRegion region, long gameTime) {
        if (key == null || region == null) {
            return null;
        }
        var anchor = region.getAnchor();
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (Map.Entry<net.minecraft.resources.ResourceLocation, Integer> entry : region.getBiomeWeights().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                weights.put(entry.getKey().toString(), entry.getValue());
            }
        }
        float[] stormWeek = region.curves() == null ? new float[0] : region.curves().stormWeek();
        return new ForecastRegionExport(
                key.toString(),
                key.regionX(),
                key.regionZ(),
                key.regionSize(),
                anchor == null ? 0 : anchor.getX(),
                anchor == null ? 0 : anchor.getY(),
                anchor == null ? 0 : anchor.getZ(),
                region.getDominantBiome() == null ? "unknown" : region.getDominantBiome().toString(),
                weights,
                region.getTemperature(),
                region.getHumidity(),
                region.getPressure(),
                region.getWind(),
                stormWeek,
                gameTime
        );
    }

    private static List<AtmosphereRegionExport> captureAtmosphereRegions(ServerLevel primaryLevel, long gameTime) {
        if (primaryLevel == null) {
            return List.of();
        }
        String dimensionId = primaryLevel.dimension().location().toString();
        return AtmosphericStateRegistry.snapshot().stream()
                .filter(state -> state != null && state.getRegionId() != null)
                .sorted(Comparator.comparing(state -> state.getRegionId().toString()))
                .map(state -> toAtmosphereRegionExport(dimensionId, state, gameTime))
                .toList();
    }

    private static AtmosphereRegionExport toAtmosphereRegionExport(String dimensionId,
                                                                   RegionAtmosphereState state,
                                                                   long gameTime) {
        RegionInstanceKey key = state.getRegionId();
        var pos = state.getPosition();
        RegionalWeatherPhase phase = ServerWeatherStateResolver.resolve(null, key, gameTime);
        return new AtmosphereRegionExport(
                dimensionId,
                key.toString(),
                key.regionX(),
                key.regionZ(),
                key.regionSize(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                state.getDominantBiome() == null ? "unknown" : state.getDominantBiome().toString(),
                state.getBaseTemperature(),
                state.getBasePressure(),
                state.getHumidity(),
                state.getTemperature(),
                state.getPressure(),
                state.getWind(),
                state.getCloudCover(),
                state.getCloudWater(),
                state.getCycloneCloudFloor(),
                state.getCycloneRainFloor(),
                state.getSunlight(),
                state.getRainIntensity(),
                state.getTargetTemperature(gameTime),
                state.getTargetHumidity(gameTime),
                state.getTargetPressure(gameTime),
                state.getBiomeSunlightMultiplier(),
                phase.name(),
                state.getDailyTemperatureProfile(),
                state.getDailyHumidityProfile(),
                state.getDailyPressureProfile(),
                state.saveMutableState().toString()
        );
    }

    private static List<CycloneExport> captureCyclones() {
        return CycloneManager.getActiveCycloneSnapshots().stream()
                .sorted(Comparator.comparing(snapshot -> snapshot.id().toString()))
                .map(snapshot -> new CycloneExport(
                        snapshot.id().toString(),
                        snapshot.centerX(),
                        snapshot.centerZ(),
                        snapshot.radius(),
                        snapshot.intensity(),
                        snapshot.corePressureDrop(),
                        snapshot.lifetimeTicks(),
                        snapshot.ageTicks(),
                        snapshot.movementX(),
                        snapshot.movementZ()
                ))
                .toList();
    }

    private static List<WeakLowExport> captureWeakLows() {
        return WeakLowManager.getActiveSnapshots().stream()
                .sorted(Comparator.comparing(snapshot -> snapshot.id().toString()))
                .map(snapshot -> new WeakLowExport(
                        snapshot.id().toString(),
                        snapshot.regionKey() == null ? "unknown" : snapshot.regionKey().toString(),
                        snapshot.regionKey() == null ? 0 : snapshot.regionKey().regionX(),
                        snapshot.regionKey() == null ? 0 : snapshot.regionKey().regionZ(),
                        snapshot.regionKey() == null ? 0 : snapshot.regionKey().regionSize(),
                        snapshot.centerX(),
                        snapshot.centerZ(),
                        snapshot.radius(),
                        snapshot.intensity(),
                        snapshot.supportScore(),
                        snapshot.pressureAnomaly(),
                        snapshot.humidity(),
                        snapshot.cloudWater(),
                        snapshot.cloudCover(),
                        snapshot.convergence(),
                        snapshot.shear(),
                        snapshot.instability(),
                        snapshot.cumulonimbusSupport(),
                        snapshot.nimbostratusSupport(),
                        snapshot.weatherCellSupport(),
                        snapshot.blockedReason(),
                        snapshot.ageTicks(),
                        snapshot.lifetimeTicks(),
                        snapshot.decayReason(),
                        snapshot.promotedToCycloneSeed()
                ))
                .toList();
    }

    private static List<TornadoExport> captureNativeTornadoes(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        List<TornadoExport> out = new ArrayList<>();
        for (CloudCell cell : CloudCellSimulationManager.getInstance().nativeTornadoCells(level)) {
            if (cell == null || cell.funnelStrength() <= 0.001F) {
                continue;
            }
            float windSpeed = (float) Math.hypot(cell.wind().x(), cell.wind().z());
            float windAngle = (float) Math.atan2(cell.wind().z(), cell.wind().x());
            float funnelRadius = Math.max(10.0F, cell.radiusMinor() * 0.16F);
            float visualHeight = Math.max(1.0F, cell.baseY() + 4.0F - cell.funnelGroundY());
            CompoundTag tag = new CompoundTag();
            tag.putString("backend", "pa_native_cloud_cell");
            tag.putUUID("cellId", cell.id());
            tag.putString("dimension", cell.dimensionId());
            tag.putString("classification", cell.classification().name());
            tag.putString("phase", cell.phase().name());
            tag.putFloat("funnelStrength", cell.funnelStrength());
            tag.putFloat("energy", cell.energy());
            tag.putLong("ageTicks", cell.ageTicks());
            out.add(new TornadoExport(
                    cell.id().toString(),
                    cell.x(),
                    cell.funnelGroundY(),
                    cell.z(),
                    funnelRadius,
                    cell.funnelGroundY(),
                    visualHeight,
                    windSpeed,
                    windAngle,
                    0.0F,
                    cell.funnelStrength(),
                    StormSeverityScale.fromNormalized(Math.max(cell.energy(), cell.funnelStrength())),
                    0.0F,
                    cell.funnelStrength(),
                    cell.phase().name(),
                    tag.toString()
            ));
        }
        out.sort(Comparator.comparing(TornadoExport::id));
        return out;
    }

    private static List<RawNbtSnapshot> captureGlobalSnapshots(ServerLevel primaryLevel) {
        List<RawNbtSnapshot> out = new ArrayList<>();
        out.add(rawSnapshot("atmospheric_scheduler", "global", AtmosphericUpdateScheduler.savePersistentState()));
        out.add(rawSnapshot("seasonal_drift", "global", SeasonalAtmosphericDrift.savePersistentState()));
        out.add(rawSnapshot("wind_engine", "global", net.Gabou.projectatmosphere.modules.wind.WindEngine.savePersistentState()));
        out.add(rawSnapshot("ocean_basins", "global", OceanBasinManager.savePersistentState()));
        out.add(rawSnapshot("weak_lows_state", "global", WeakLowManager.savePersistentState()));
        out.add(rawSnapshot("cyclones_state", "global", CycloneManager.savePersistentState()));
        if (primaryLevel != null) {
            out.add(rawSnapshot("cloud_backend_migration", "primary_level", CloudBackendMigrationSavedData.get(primaryLevel).state().save()));
        }
        return out;
    }

    private static ServerLevelSnapshot captureLevelSnapshot(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        List<RawNbtSnapshot> cloudRegions = CloudRegionStateStore.getAll(level).stream()
                .filter(state -> state != null)
                .sorted(Comparator.comparing(state -> state.getRegionId().toString()))
                .map(state -> rawSnapshot("cloud_region", state.getRegionId().toString(), state.save()))
                .toList();
        List<RawNbtSnapshot> weatherCells = WeatherCellManager.getCells(level).stream()
                .filter(state -> state != null)
                .sorted(Comparator.comparing(state -> state.getId().toString()))
                .map(state -> rawSnapshot("weather_cell", state.getId().toString(), state.save()))
                .toList();
        RawNbtSnapshot backendMigration = rawSnapshot(
                "cloud_backend_migration",
                dimensionId,
                CloudBackendMigrationSavedData.get(level).state().save()
        );
        RawNbtSnapshot stormHistory = rawSnapshot(
                "storm_history",
                dimensionId,
                saveStormHistory(level)
        );
        LevelSummary summary = new LevelSummary(
                dimensionId,
                level.getGameTime(),
                level.getDayTime(),
                level.players().size(),
                level.isRaining(),
                level.isThundering(),
                cloudRegions.size(),
                weatherCells.size()
        );
        return new ServerLevelSnapshot(summary, cloudRegions, weatherCells, backendMigration, stormHistory);
    }

    private static CompoundTag saveStormHistory(ServerLevel level) {
        CompoundTag tag = new CompoundTag();
        GlobalStormHistoryData data = GlobalStormHistoryData.get(level);
        data.save(tag);
        return tag;
    }

    private static RawNbtSnapshot rawSnapshot(String recordType, String identifier, CompoundTag tag) {
        return new RawNbtSnapshot(recordType, identifier, tag == null ? "{}" : tag.toString());
    }

    private static int countCloudRegions(List<ServerLevelSnapshot> levelSnapshots) {
        int total = 0;
        for (ServerLevelSnapshot level : levelSnapshots) {
            total += level.cloudRegions().size();
        }
        return total;
    }

    private static int countWeatherCells(List<ServerLevelSnapshot> levelSnapshots) {
        int total = 0;
        for (ServerLevelSnapshot level : levelSnapshots) {
            total += level.weatherCells().size();
        }
        return total;
    }

    private static ServerLevel resolvePrimaryLevel(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        ServerLevel overworld = server.overworld();
        if (overworld != null) {
            return overworld;
        }
        for (ServerLevel level : server.getAllLevels()) {
            return level;
        }
        return null;
    }

    private static String projectAtmosphereVersion() {
        return ModList.get().getModContainerById(ProjectAtmosphere.MODID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    private static void writeJson(Path file, Object payload) throws IOException {
        Files.createDirectories(file.getParent());
        try (var writer = Files.newBufferedWriter(file)) {
            GSON.toJson(payload, writer);
        }
    }

    private static void writeJsonLines(Path file, List<?> payload) throws IOException {
        if (payload == null || payload.isEmpty()) {
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

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replace(':', '_').replace('/', '_').replace('\\', '_');
    }

    public record ServerStateSnapshot(ServerManifest manifest,
                                      List<ForecastCenterExport> forecastCenters,
                                      List<ForecastRegionExport> forecastRegions,
                                      List<AtmosphereRegionExport> atmosphereRegions,
                                      List<CycloneExport> cyclones,
                                      List<WeakLowExport> weakLows,
                                      List<TornadoExport> tornadoes,
                                      List<HurricaneExport> hurricanes,
                                      List<RawNbtSnapshot> globalState,
                                      List<ServerLevelSnapshot> levels) {
    }

    public record ServerManifest(long capturedAtEpochMs,
                                 String primaryDimensionId,
                                 String projectAtmosphereVersion,
                                 String minecraftVersion,
                                 String loader,
                                 boolean telemetryEnabled,
                                 boolean simpleCloudsLoaded,
                                 int levelCount,
                                 int forecastCenterCount,
                                 int forecastRegionCount,
                                 int atmosphereRegionCount,
                                 int cloudRegionCount,
                                 int weatherCellCount,
                                 int cycloneCount,
                                 int weakLowCount,
                                 int tornadoCount,
                                 int hurricaneCount,
                                 List<LevelSummary> levels) {
    }

    public record ServerLevelSnapshot(LevelSummary summary,
                                      List<RawNbtSnapshot> cloudRegions,
                                      List<RawNbtSnapshot> weatherCells,
                                      RawNbtSnapshot cloudBackendMigration,
                                      RawNbtSnapshot stormHistory) {
    }

    public record LevelSummary(String dimensionId,
                               long gameTime,
                               long dayTime,
                               int playerCount,
                               boolean raining,
                               boolean thundering,
                               int cloudRegionCount,
                               int weatherCellCount) {
    }

    public record ForecastCenterExport(String playerId, int x, int y, int z) {
    }

    public record ForecastRegionExport(String regionId,
                                       int regionX,
                                       int regionZ,
                                       int regionSize,
                                       int anchorX,
                                       int anchorY,
                                       int anchorZ,
                                       String dominantBiomeId,
                                       Map<String, Integer> biomeWeights,
                                       float[][] temperatureWeek,
                                       float[][] humidityWeek,
                                       float[][] pressureWeek,
                                       WindVector[] windWeek,
                                       float[] stormWeek,
                                       long sampledAtGameTime) {
    }

    public record AtmosphereRegionExport(String dimensionId,
                                         String regionId,
                                         int regionX,
                                         int regionZ,
                                         int regionSize,
                                         int anchorX,
                                         int anchorY,
                                         int anchorZ,
                                         String dominantBiomeId,
                                         float baseTemperature,
                                         float basePressure,
                                         float humidity,
                                         float temperature,
                                         float pressure,
                                         WindVector wind,
                                         float cloudCover,
                                         float cloudWater,
                                         float cycloneCloudFloor,
                                         float cycloneRainFloor,
                                         float sunlight,
                                         float rainIntensity,
                                         float targetTemperature,
                                         float targetHumidity,
                                         float targetPressure,
                                         float biomeSunlightMultiplier,
                                         String weatherPhase,
                                         float[] dailyTemperatureProfile,
                                         float[] dailyHumidityProfile,
                                         float[] dailyPressureProfile,
                                         String mutableStateNbt) {
    }

    public record CycloneExport(String id,
                                float centerX,
                                float centerZ,
                                float radius,
                                float intensity,
                                float corePressureDrop,
                                long lifetimeTicks,
                                int ageTicks,
                                float movementX,
                                float movementZ) {
    }

    public record WeakLowExport(String id,
                                String regionId,
                                int regionX,
                                int regionZ,
                                int regionSize,
                                double centerX,
                                double centerZ,
                                float radius,
                                float intensity,
                                float supportScore,
                                float pressureAnomaly,
                                float humidity,
                                float cloudWater,
                                float cloudCover,
                                float convergence,
                                float shear,
                                float instability,
                                float cumulonimbusSupport,
                                float nimbostratusSupport,
                                float weatherCellSupport,
                                String blockedReason,
                                int ageTicks,
                                int lifetimeTicks,
                                String decayReason,
                                boolean promotedToCycloneSeed) {
    }

    public record TornadoExport(String id,
                                double x,
                                double y,
                                double z,
                                float radius,
                                float visualBottomY,
                                float visualHeight,
                                float windSpeed,
                                float windAngle,
                                float windGust,
                                float normalizedIntensity,
                                int stormLevel,
                                float recentDebrisScore,
                                float formationProgress,
                                String phase,
                                String persistentNbt) {
    }

    public record HurricaneExport(String id,
                                  double centerX,
                                  double centerZ,
                                  float anchorY,
                                  float coreRadius,
                                  float stormExtentRadius,
                                  float eyeRadius,
                                  float edgeFade,
                                  int bandCount,
                                  float bandWidth,
                                  float spiralTightness,
                                  float rotationPhase,
                                  float rotationSpeed,
                                  float transitionStart,
                                  float transitionEnd,
                                  float normalizedIntensity,
                                  String cloudTypeId,
                                  int ageTicks,
                                  String persistentNbt) {
    }

    public record RawNbtSnapshot(String recordType, String identifier, String nbt) {
    }
}
