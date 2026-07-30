package net.Gabou.projectatmosphere.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.loading.ForecastLoadingStage;
import net.Gabou.projectatmosphere.client.loading.IntegratedForecastLoadingBridge;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.DefaultRegionCurves;
import net.Gabou.projectatmosphere.modules.region.FileRegionPersistence;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.region.RegionBiomeSample;
import net.Gabou.projectatmosphere.modules.region.RegionCurves;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public class ForecastDataStorage {
    private static final String FILE_NAME = "forecast_centers.json";
    private static final String FORECAST_FILE = "biome_forecasts.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean hasForecastData;
    private static boolean hasRegionForecastData;
    private static boolean hasCenterData;

    public static final Map<UUID, BlockPos> playerData = new ConcurrentHashMap<>();

    public static void saveAll(ServerLevel world) {
        savePlayerCenters(world);
        saveRegionForecasts(world);
    }

    public static void clearAll(ServerLevel world) {
        playerData.clear();
        hasForecastData = false;
        hasRegionForecastData = false;
        hasCenterData = false;

        try {
            Files.deleteIfExists(getSavePath(world, FILE_NAME));
            Files.deleteIfExists(getSavePath(world, FORECAST_FILE));
            deleteDirectoryIfExists(StorageUtils.getPerWorldSavePath(world, FileRegionPersistence.REGION_FOLDER));
            deleteDirectoryIfExists(StorageUtils.getPerWorldSavePath(world, FileRegionPersistence.LEGACY_FALLBACK_FOLDER));
        } catch (IOException e) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to clear forecast data", e);
        }
    }

    public static void loadAll(ServerLevel world) {
        clearCache();
        loadPlayerCenters(world);

        FileRegionPersistence persistence = new FileRegionPersistence(world);
        if (loadRegionForecasts(world, persistence)) {
            return;
        }

        if (hasLegacyData(world, persistence)) {
            IntegratedForecastLoadingBridge.update(
                    ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                    "Legacy biome forecast data detected. Converting to region forecasts...",
                    0.16F,
                    "legacy_region_conversion_start"
            );
            migrateLegacyData(world, persistence);
            if (loadRegionForecasts(world, persistence)) {
                IntegratedForecastLoadingBridge.update(
                        ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                        "Legacy biome forecast conversion complete",
                        0.32F,
                        "legacy_region_conversion_done"
                );
                return;
            }
        }
    }

    private static void clearCache() {
        playerData.clear();
        hasForecastData = false;
        hasRegionForecastData = false;
        hasCenterData = false;
    }

    public static boolean hasForecastData() {
        return hasForecastData;
    }

    public static boolean hasRegionForecastData() {
        return hasRegionForecastData;
    }

    public static boolean hasCenterData() {
        return hasCenterData;
    }

    private static void savePlayerCenters(ServerLevel world) {
        JsonObject root = new JsonObject();
        for (var entry : playerData.entrySet()) {
            BlockPos pos = entry.getValue();
            JsonObject obj = new JsonObject();
            obj.addProperty("x", pos.getX());
            obj.addProperty("y", pos.getY());
            obj.addProperty("z", pos.getZ());
            root.add(entry.getKey().toString(), obj);
        }

        try {
            Path path = getSavePath(world, FILE_NAME);
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(root, w);
            }
        } catch (IOException ex) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to save forecast centers", ex);
        }
    }

    private static void loadPlayerCenters(ServerLevel world) {
        Path path = getSavePath(world, FILE_NAME);
        if (!Files.exists(path)) {
            return;
        }
        hasCenterData = true;
        try (Reader r = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) {
                return;
            }
            for (var entry : root.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                JsonObject obj = entry.getValue().getAsJsonObject();
                playerData.put(uuid, new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt()));
            }
        } catch (IOException | RuntimeException ex) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to load forecast centers", ex);
        }
    }

    private static void saveRegionForecasts(ServerLevel world) {
        FileRegionPersistence persistence = new FileRegionPersistence(world);
        for (ForecastRegion region : ForecastGenerator.getRegionForecasts().values()) {
            persistence.saveRegion(region);
        }
        hasForecastData = !ForecastGenerator.getRegionForecasts().isEmpty();
        hasRegionForecastData = hasForecastData;
    }

    private static boolean loadRegionForecasts(ServerLevel world, FileRegionPersistence persistence) {
        if (!persistence.hasRegionData()) {
            return false;
        }

        int loaded = 0;
        for (RegionInstanceKey regionId : persistence.listRegionIds()) {
            var region = persistence.loadRegion(regionId);
            if (region.isEmpty()) {
                continue;
            }
            ForecastGenerator.putRegionForecast(region.get());
            loaded++;
        }

        if (loaded == 0) {
            return false;
        }

        ForecastGenerator.rebuildLoadedForecastIndexes();
        SandStormManager.dailyAndSand(world);
        hasForecastData = true;
        hasRegionForecastData = true;
        return true;
    }

    private static boolean hasLegacyData(ServerLevel world, FileRegionPersistence persistence) {
        return Files.exists(getSavePath(world, FORECAST_FILE))
                || persistence.hasLegacyRegionData()
                || persistence.hasLegacyFallbackData();
    }

    private static void migrateLegacyData(ServerLevel world, FileRegionPersistence persistence) {
        Map<RegionInstanceKey, List<ForecastRegion.GeneratedSample>> groupedSamples = new LinkedHashMap<>();
        Map<RegionInstanceKey, ForecastRegion> directRegions = new LinkedHashMap<>();

        readLegacyBiomeForecasts(world, groupedSamples);
        readLegacyRegionForecasts(persistence, groupedSamples, directRegions);
        readLegacyFallbacks(persistence, groupedSamples);

        int total = Math.max(1, groupedSamples.size() + directRegions.size());
        int converted = 0;

        for (Map.Entry<RegionInstanceKey, ForecastRegion> entry : directRegions.entrySet()) {
            converted++;
            updateMigrationProgress(converted, total);
            ForecastRegion region = entry.getValue();
            persistence.saveRegion(region);
            ForecastGenerator.putRegionForecast(region);
        }

        for (Map.Entry<RegionInstanceKey, List<ForecastRegion.GeneratedSample>> entry : groupedSamples.entrySet()) {
            converted++;
            updateMigrationProgress(converted, total);
            try {
                BlockPos anchor = firstSamplePos(entry.getValue(), entry.getKey().center());
                ForecastRegion region = ForecastRegion.aggregate(entry.getKey(), anchor, entry.getValue(), new float[0]);
                persistence.saveRegion(region);
                ForecastGenerator.putRegionForecast(region);
            } catch (RuntimeException ex) {
                ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed converting legacy region {}. Regenerating fresh.", entry.getKey(), ex);
                ForecastRegion fresh = ForecastGenerator.generateForecastForRegionKey(entry.getKey(), world);
                persistence.saveRegion(fresh);
            }
        }

        hasForecastData = !ForecastGenerator.getRegionForecasts().isEmpty();
        hasRegionForecastData = hasForecastData;
    }

    private static void updateMigrationProgress(int converted, int total) {
        IntegratedForecastLoadingBridge.update(
                ForecastLoadingStage.DESIGNING_FORECAST_REGIONS,
                "Converting legacy forecast region " + converted + " / " + total,
                0.16F + (0.14F * converted / Math.max(1.0F, (float) total)),
                "legacy_region_conversion_progress"
        );
    }

    private static void readLegacyBiomeForecasts(ServerLevel world,
                                                 Map<RegionInstanceKey, List<ForecastRegion.GeneratedSample>> groupedSamples) {
        Path path = getSavePath(world, FORECAST_FILE);
        if (!Files.exists(path)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> legacyEntry : root.entrySet()) {
                JsonElement value = legacyEntry.getValue();
                if (!value.isJsonObject()) {
                    continue;
                }
                JsonObject obj = value.getAsJsonObject();
                if (!obj.has("biome") || !obj.has("x") || !obj.has("z")) {
                    continue;
                }
                ResourceLocation biome = ResourceLocation.parse(obj.get("biome").getAsString());
                BlockPos pos = new BlockPos(
                        obj.get("x").getAsInt(),
                        obj.has("y") ? obj.get("y").getAsInt() : world.getSeaLevel(),
                        obj.get("z").getAsInt()
                );
                ForecastRegion.GeneratedSample sample = new ForecastRegion.GeneratedSample(
                        new RegionBiomeSample(biome, pos, 1),
                        FileRegionPersistence.deserialize2d(obj.getAsJsonArray("temperature")),
                        FileRegionPersistence.deserialize2d(obj.getAsJsonArray("humidity")),
                        FileRegionPersistence.deserialize2d(obj.getAsJsonArray("pressure")),
                        FileRegionPersistence.deserializeWindWeek(obj.getAsJsonArray("wind"))
                );
                groupedSamples.computeIfAbsent(RegionInstanceKey.from(pos), ignored -> new ArrayList<>()).add(sample);
            }
        } catch (IOException | RuntimeException ex) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed reading legacy biome forecasts {}", path, ex);
        }
    }

    private static void readLegacyRegionForecasts(FileRegionPersistence persistence,
                                                  Map<RegionInstanceKey, List<ForecastRegion.GeneratedSample>> groupedSamples,
                                                  Map<RegionInstanceKey, ForecastRegion> directRegions) {
        Path folder = persistence.regionFolder();
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> stream = Files.list(folder)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> readLegacyRegionFile(path, persistence, groupedSamples, directRegions));
        } catch (IOException ex) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed listing legacy region forecasts {}", folder, ex);
        }
    }

    private static void readLegacyRegionFile(Path path,
                                             FileRegionPersistence persistence,
                                             Map<RegionInstanceKey, List<ForecastRegion.GeneratedSample>> groupedSamples,
                                             Map<RegionInstanceKey, ForecastRegion> directRegions) {
        try (Reader r = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null || !FileRegionPersistence.isLegacyRegionJson(root)) {
                return;
            }
            RegionInstanceKey id = FileRegionPersistence.readRegionId(root);
            if (id == null) {
                id = persistence.parseRegionIdFromFile(path).orElse(null);
            }
            if (id == null) {
                return;
            }
            Map<ResourceLocation, Integer> weights = readLegacyBiomeWeights(root);
            float[][] temperature = FileRegionPersistence.deserialize2d(root.getAsJsonArray("temperature"));
            float[][] humidity = FileRegionPersistence.deserialize2d(root.getAsJsonArray("humidity"));
            float[][] pressure = FileRegionPersistence.deserialize2d(root.getAsJsonArray("pressure"));
            WindVector[] wind = FileRegionPersistence.deserializeWindWeek(root.getAsJsonArray("wind"));
            float[] storm = FileRegionPersistence.deserialize1d(root.getAsJsonArray("storm"));
            BlockPos anchor = readAnchor(root, id.center());

            if (isUsableDirectRegion(temperature, humidity, pressure, wind)) {
                RegionCurves curves = new DefaultRegionCurves(temperature, humidity, pressure, wind, storm);
                directRegions.put(id, new ForecastRegion(id, anchor, curves, weights));
                return;
            }

            addLegacySectionSamples(root.getAsJsonArray("sections"), id, groupedSamples);
        } catch (IOException | RuntimeException ex) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed reading legacy region forecast {}", path, ex);
        }
    }

    private static void readLegacyFallbacks(FileRegionPersistence persistence,
                                            Map<RegionInstanceKey, List<ForecastRegion.GeneratedSample>> groupedSamples) {
        Path folder = persistence.legacyFallbackFolder();
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> stream = Files.list(folder)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try (Reader r = Files.newBufferedReader(path)) {
                            JsonObject root = GSON.fromJson(r, JsonObject.class);
                            RegionInstanceKey id = FileRegionPersistence.readRegionId(root);
                            if (id == null) {
                                id = persistence.parseRegionIdFromFile(path).orElse(null);
                            }
                            if (id != null) {
                                addLegacySectionSamples(root.getAsJsonArray("sections"), id, groupedSamples);
                            }
                        } catch (IOException | RuntimeException ex) {
                            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed reading legacy region fallback {}", path, ex);
                        }
                    });
        } catch (IOException ex) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed listing legacy region fallbacks {}", folder, ex);
        }
    }

    private static void addLegacySectionSamples(JsonArray sections,
                                                RegionInstanceKey fallbackId,
                                                Map<RegionInstanceKey, List<ForecastRegion.GeneratedSample>> groupedSamples) {
        if (sections == null) {
            return;
        }
        for (JsonElement sectionElement : sections) {
            if (!sectionElement.isJsonObject()) {
                continue;
            }
            JsonObject section = sectionElement.getAsJsonObject();
            JsonObject biomeObj = section.has("biome") && section.get("biome").isJsonObject()
                    ? section.getAsJsonObject("biome")
                    : null;
            if (biomeObj == null || !biomeObj.has("id") || !biomeObj.has("pos")) {
                continue;
            }
            ResourceLocation biome = ResourceLocation.parse(biomeObj.get("id").getAsString());
            BlockPos pos = AtmosphereUtils.deserializeBlockPos(biomeObj.getAsJsonObject("pos"));
            int weight = section.has("factor") ? Math.max(1, Math.round(section.get("factor").getAsFloat() * 1000f)) : 1;
            ForecastRegion.GeneratedSample sample = new ForecastRegion.GeneratedSample(
                    new RegionBiomeSample(biome, pos, weight),
                    FileRegionPersistence.deserialize2d(section.getAsJsonArray("temperature")),
                    FileRegionPersistence.deserialize2d(section.getAsJsonArray("humidity")),
                    FileRegionPersistence.deserialize2d(section.getAsJsonArray("pressure")),
                    FileRegionPersistence.deserializeWindWeek(section.getAsJsonArray("wind"))
            );
            groupedSamples.computeIfAbsent(RegionInstanceKey.from(pos), ignored -> new ArrayList<>()).add(sample);
        }
    }

    private static Map<ResourceLocation, Integer> readLegacyBiomeWeights(JsonObject root) {
        Map<ResourceLocation, Integer> weights = new HashMap<>();
        JsonArray sourceBiomes = root.getAsJsonArray("sourceBiomes");
        if (sourceBiomes != null) {
            for (JsonElement element : sourceBiomes) {
                if (element.isJsonObject()) {
                    JsonObject obj = element.getAsJsonObject();
                    if (obj.has("id")) {
                        weights.merge(ResourceLocation.parse(obj.get("id").getAsString()), 1, Integer::sum);
                    }
                }
            }
        }
        JsonArray sections = root.getAsJsonArray("sections");
        if (sections != null) {
            for (JsonElement element : sections) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject section = element.getAsJsonObject();
                if (section.has("biome") && section.get("biome").isJsonObject()) {
                    JsonObject biome = section.getAsJsonObject("biome");
                    if (biome.has("id")) {
                        int weight = section.has("factor") ? Math.max(1, Math.round(section.get("factor").getAsFloat() * 1000f)) : 1;
                        weights.merge(ResourceLocation.parse(biome.get("id").getAsString()), weight, Integer::sum);
                    }
                }
            }
        }
        return weights;
    }

    private static boolean isUsableDirectRegion(float[][] temperature, float[][] humidity, float[][] pressure, WindVector[] wind) {
        return temperature != null && temperature.length == 7
                && humidity != null && humidity.length == 7
                && pressure != null && pressure.length == 7
                && wind != null && wind.length == 7;
    }

    private static BlockPos readAnchor(JsonObject root, BlockPos fallback) {
        if (root.has("anchor") && root.get("anchor").isJsonObject()) {
            return AtmosphereUtils.deserializeBlockPos(root.getAsJsonObject("anchor"));
        }
        return fallback;
    }

    private static BlockPos firstSamplePos(List<ForecastRegion.GeneratedSample> samples, BlockPos fallback) {
        for (ForecastRegion.GeneratedSample sample : samples) {
            if (sample != null && sample.sample() != null && sample.sample().pos() != null) {
                return sample.sample().pos();
            }
        }
        return fallback;
    }

    private static Path getSavePath(ServerLevel world, String fileName) {
        return StorageUtils.getPerWorldSavePath(world, fileName);
    }

    private static void deleteDirectoryIfExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }
}
