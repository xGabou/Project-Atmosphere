package net.Gabou.projectatmosphere.modules.region;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * RegionPersistence implementation that stores per-region fallback JSON alongside existing data files.
 */
public final class FileRegionPersistence implements RegionPersistence {
    private static final String REGION_FOLDER = "region_forecasts";
    private static final String LEGACY_FALLBACK_FOLDER = "region_fallbacks";
    private static final int EXPECTED_DAYS = 7;
    private final ServerLevel level;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public FileRegionPersistence(ServerLevel level) {
        this.level = level;
    }

    @Override
    public boolean hasRegionData() {
        Path folder = regionFolder();
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(folder)) {
            return stream.anyMatch(path -> path.getFileName().toString().endsWith(".json"));
        } catch (IOException e) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to inspect region forecast folder {}", folder, e);
            return false;
        }
    }

    @Override
    public List<RegionInstanceKey> listRegionIds() {
        Path folder = regionFolder();
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::parseRegionIdFromFile)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparingInt(RegionInstanceKey::regionX)
                            .thenComparingInt(RegionInstanceKey::regionZ)
                            .thenComparingInt(RegionInstanceKey::regionSize))
                    .toList();
        } catch (IOException e) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to list region forecast files in {}", folder, e);
            return List.of();
        }
    }

    @Override
    public Optional<ForecastRegion> loadRegion(RegionInstanceKey id) {
        Path path = regionPathFor(id);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try (Reader r = Files.newBufferedReader(path)) {
            JsonObject root = gson.fromJson(r, JsonObject.class);
            if (root == null) {
                logInvalidRegion(path, "empty JSON document");
                return Optional.empty();
            }

            RegionInstanceKey storedId = readRegionId(root);
            if (storedId == null) {
                logInvalidRegion(path, "missing region key");
                return Optional.empty();
            }
            if (!storedId.equals(id)) {
                logInvalidRegion(path, "region key does not match file name");
                return Optional.empty();
            }

            BlockPos anchor = null;
            if (root.has("anchor") && root.get("anchor").isJsonObject()) {
                anchor = AtmosphereUtils.deserializeBlockPos(root.getAsJsonObject("anchor"));
            }

            List<BiomeInstanceKey> sourceBiomes = readBiomeKeys(root.getAsJsonArray("sourceBiomes"));
            ForecastRegion.Section[] sections = readSections(root.getAsJsonArray("sections"));
            float[][] temperature = deserialize2d(root.getAsJsonArray("temperature"));
            float[][] humidity = deserialize2d(root.getAsJsonArray("humidity"));
            float[][] pressure = deserialize2d(root.getAsJsonArray("pressure"));
            WindVector[] wind = deserializeWindWeek(root.getAsJsonArray("wind"));
            float[] storm = deserialize1d(root.getAsJsonArray("storm"));

            if (!isValidWeek("temperature", temperature, -120f, 100f)
                    || !isValidWeek("humidity", humidity, 0f, 100f)
                    || !isValidWeek("pressure", pressure, 850f, 1100f)
                    || !isValidWindWeek(wind)
                    || !isValidStormWeek(storm)) {
                logInvalidRegion(path, "integrity validation failed");
                return Optional.empty();
            }

            RegionCurves curves = new DefaultRegionCurves(temperature, humidity, pressure, wind, storm);
            ForecastRegion region = new ForecastRegion(storedId, anchor, sourceBiomes, sections, curves, null);
            region.clearBiomeForecasts();
            return Optional.of(region);
        } catch (IOException | RuntimeException e) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to load region forecast {}", path, e);
            return Optional.empty();
        }
    }

    @Override
    public void saveRegion(ForecastRegion region) {
        if (region == null || region.getKey() == null) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("region", writeRegionId(region.getKey()));
        if (region.getAnchor() != null) {
            root.add("anchor", AtmosphereUtils.serializeBlockPos(region.getAnchor()));
        }
        root.add("sourceBiomes", serializeBiomeKeys(region.getSamples()));
        root.add("temperature", serialize2d(region.getTemperature()));
        root.add("humidity", serialize2d(region.getHumidity()));
        root.add("pressure", serialize2d(region.getPressure()));
        root.add("wind", serializeWindWeek(region.getWind()));
        root.add("storm", serialize1d(region.curves() == null ? null : region.curves().stormWeek()));
        root.add("sections", serializeSections(region.sections()));

        Path path = regionPathFor(region.getKey());
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                gson.toJson(root, w);
            }
        } catch (IOException e) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to save region forecast {}", path, e);
        }
    }

    @Override
    public Optional<BiomeFallbackSnapshot> loadFallback(RegionInstanceKey id) {
        Path path = legacyFallbackPathFor(id);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try (Reader r = Files.newBufferedReader(path)) {
            JsonObject root = gson.fromJson(r, JsonObject.class);
            List<BiomeInstanceKey> sourceBiomes = readBiomeKeys(root.getAsJsonArray("biomes"));
            ForecastRegion.Section[] sections = readSections(root.getAsJsonArray("sections"));
            BiomeFallbackSnapshot fb = new BiomeFallbackSnapshot(id, sourceBiomes, sections);
            return Optional.of(fb);
        } catch (IOException e) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to load legacy region fallback {}", path, e);
            return Optional.empty();
        }
    }

    @Override
    public BiomeFallbackSnapshot saveFallback(RegionInstanceKey id, ForecastRegion.Section[] sections, List<BiomeInstanceKey> sourceBiomes) {
        JsonObject root = new JsonObject();
        root.addProperty("rx", id.regionX());
        root.addProperty("rz", id.regionZ());
        root.addProperty("size", id.regionSize());

        root.add("biomes", serializeBiomeKeys(sourceBiomes));
        root.add("sections", serializeSections(sections));

        Path path = legacyFallbackPathFor(id);
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                gson.toJson(root, w);
            }
        } catch (IOException e) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to save legacy region fallback {}", path, e);
        }
        return new BiomeFallbackSnapshot(id, Collections.unmodifiableList(sourceBiomes), sections);
    }

    private Path regionFolder() {
        return StorageUtils.getPerWorldSavePath(level, REGION_FOLDER);
    }

    private Path regionPathFor(RegionInstanceKey id) {
        String fileName = REGION_FOLDER + "/" + id.regionX() + "_" + id.regionZ() + "_" + id.regionSize() + ".json";
        return StorageUtils.getPerWorldSavePath(level, fileName);
    }

    private Path legacyFallbackPathFor(RegionInstanceKey id) {
        String fileName = LEGACY_FALLBACK_FOLDER + "/" + id.regionX() + "_" + id.regionZ() + "_" + id.regionSize() + ".json";
        return StorageUtils.getPerWorldSavePath(level, fileName);
    }

    private Optional<RegionInstanceKey> parseRegionIdFromFile(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot >= 0 ? fileName.substring(0, dot) : fileName;
        String[] parts = stem.split("_");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new RegionInstanceKey(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static JsonObject writeRegionId(RegionInstanceKey id) {
        JsonObject obj = new JsonObject();
        obj.addProperty("rx", id.regionX());
        obj.addProperty("rz", id.regionZ());
        obj.addProperty("size", id.regionSize());
        return obj;
    }

    private static RegionInstanceKey readRegionId(JsonObject root) {
        JsonObject regionObj = root.has("region") && root.get("region").isJsonObject()
                ? root.getAsJsonObject("region")
                : root;
        if (!regionObj.has("rx") || !regionObj.has("rz")) {
            return null;
        }
        int rx = regionObj.get("rx").getAsInt();
        int rz = regionObj.get("rz").getAsInt();
        int size = regionObj.has("size") ? regionObj.get("size").getAsInt() : RegionInstanceKey.DEFAULT_REGION_SIZE;
        return new RegionInstanceKey(rx, rz, size);
    }

    private static JsonArray serializeBiomeKeys(List<BiomeInstanceKey> sourceBiomes) {
        JsonArray biomesArr = new JsonArray();
        if (sourceBiomes == null) {
            return biomesArr;
        }
        for (BiomeInstanceKey key : sourceBiomes) {
            if (key == null || key.biomeType() == null || key.samplePos() == null) {
                continue;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("id", key.biomeType().toString());
            obj.add("pos", AtmosphereUtils.serializeBlockPos(key.samplePos()));
            biomesArr.add(obj);
        }
        return biomesArr;
    }

    private static List<BiomeInstanceKey> readBiomeKeys(JsonArray biomesArr) {
        List<BiomeInstanceKey> sourceBiomes = new ArrayList<>();
        if (biomesArr == null) {
            return sourceBiomes;
        }
        for (JsonElement el : biomesArr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("id") || !obj.has("pos")) {
                continue;
            }
            ResourceLocation biome = ResourceLocation.parse(obj.get("id").getAsString());
            BlockPos pos = AtmosphereUtils.deserializeBlockPos(obj.get("pos").getAsJsonObject());
            sourceBiomes.add(new BiomeInstanceKey(biome, pos));
        }
        return sourceBiomes;
    }

    private static JsonArray serializeSections(ForecastRegion.Section[] sections) {
        JsonArray sectionsArr = new JsonArray();
        if (sections == null) {
            return sectionsArr;
        }
        for (ForecastRegion.Section section : sections) {
            JsonObject obj = new JsonObject();
            obj.addProperty("factor", section.factor());
            BiomeForecastSnapshot snap = section.snapshot();
            if (snap != null && snap.biomeKey() != null && snap.biomeKey().samplePos() != null) {
                JsonObject biomeObj = new JsonObject();
                biomeObj.addProperty("id", snap.biomeKey().biomeType().toString());
                biomeObj.add("pos", AtmosphereUtils.serializeBlockPos(snap.biomeKey().samplePos()));
                obj.add("biome", biomeObj);
                obj.add("temperature", serialize2d(snap.temperatureCurve()));
                obj.add("humidity", serialize2d(snap.humidityCurve()));
                obj.add("pressure", serialize2d(snap.pressureCurve()));
                obj.add("wind", serializeWindWeek(snap.windCurve()));
            }
            sectionsArr.add(obj);
        }
        return sectionsArr;
    }

    private static ForecastRegion.Section[] readSections(JsonArray sectionsArr) {
        ForecastRegion.Section[] sections = new ForecastRegion.Section[8];
        for (int i = 0; i < sections.length; i++) {
            if (sectionsArr == null || i >= sectionsArr.size() || !sectionsArr.get(i).isJsonObject()) {
                sections[i] = new ForecastRegion.Section(0f, null);
                continue;
            }
            JsonObject obj = sectionsArr.get(i).getAsJsonObject();
            float factor = obj.has("factor") ? obj.get("factor").getAsFloat() : 0f;
            BiomeForecastSnapshot snapshot = null;
            if (obj.has("biome") && obj.get("biome").isJsonObject()) {
                JsonObject biomeObj = obj.getAsJsonObject("biome");
                if (biomeObj.has("id") && biomeObj.has("pos")) {
                    ResourceLocation biome = ResourceLocation.parse(biomeObj.get("id").getAsString());
                    BlockPos pos = AtmosphereUtils.deserializeBlockPos(biomeObj.get("pos").getAsJsonObject());
                    BiomeInstanceKey key = new BiomeInstanceKey(biome, pos);
                    float[][] temp = deserialize2d(obj.getAsJsonArray("temperature"));
                    float[][] hum = deserialize2d(obj.getAsJsonArray("humidity"));
                    float[][] pressure = deserialize2d(obj.getAsJsonArray("pressure"));
                    WindVector[] wind = deserializeWindWeek(obj.getAsJsonArray("wind"));
                    snapshot = new BiomeForecastSnapshot(key, temp, hum, pressure, wind);
                }
            }
            sections[i] = new ForecastRegion.Section(factor, snapshot);
        }
        return sections;
    }

    private static JsonArray serialize2d(float[][] data) {
        JsonArray arr = new JsonArray();
        if (data == null) {
            return arr;
        }
        for (float[] row : data) {
            JsonArray rowArr = new JsonArray();
            if (row != null) {
                for (float v : row) {
                    rowArr.add(v);
                }
            }
            arr.add(rowArr);
        }
        return arr;
    }

    private static float[][] deserialize2d(JsonArray arr) {
        if (arr == null) {
            return null;
        }
        float[][] out = new float[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            JsonArray rowArr = arr.get(i).getAsJsonArray();
            float[] row = new float[rowArr.size()];
            for (int j = 0; j < rowArr.size(); j++) {
                row[j] = rowArr.get(j).getAsFloat();
            }
            out[i] = row;
        }
        return out;
    }

    private static JsonArray serialize1d(float[] data) {
        JsonArray arr = new JsonArray();
        if (data == null) {
            return arr;
        }
        for (float value : data) {
            arr.add(value);
        }
        return arr;
    }

    private static float[] deserialize1d(JsonArray arr) {
        if (arr == null) {
            return new float[0];
        }
        float[] out = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = arr.get(i).getAsFloat();
        }
        return out;
    }

    private static JsonArray serializeWindWeek(WindVector[] week) {
        JsonArray arr = new JsonArray();
        if (week == null) {
            return arr;
        }
        for (WindVector w : week) {
            JsonObject obj = new JsonObject();
            if (w != null) {
                obj.addProperty("base", w.baseSpeed());
                obj.addProperty("angle", w.angleRadians());
                obj.addProperty("gust", w.gustSpeed());
            } else {
                obj.addProperty("base", 0f);
                obj.addProperty("angle", 0f);
                obj.addProperty("gust", 0f);
            }
            arr.add(obj);
        }
        return arr;
    }

    private static WindVector[] deserializeWindWeek(JsonArray arr) {
        if (arr == null) {
            return null;
        }
        WindVector[] week = new WindVector[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            JsonObject obj = arr.get(i).getAsJsonObject();
            float base = obj.has("base") ? obj.get("base").getAsFloat() : 0f;
            float angle = obj.has("angle") ? obj.get("angle").getAsFloat() : 0f;
            float gust = obj.has("gust") ? obj.get("gust").getAsFloat() : base;
            week[i] = new WindVector(base, angle, gust);
        }
        return week;
    }

    private void logInvalidRegion(Path path, String reason) {
        ProjectAtmosphere.LOGGER.warn("[Atmosphere] Region forecast {} rejected during load: {}", path, reason);
    }

    private static boolean isValidWeek(String label, float[][] week, float min, float max) {
        if (week == null || week.length != EXPECTED_DAYS) {
            return false;
        }
        for (float[] day : week) {
            if (day == null || day.length < 2) {
                return false;
            }
            for (float value : day) {
                if (!Float.isFinite(value) || value < min || value > max) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isValidWindWeek(WindVector[] week) {
        if (week == null || week.length != EXPECTED_DAYS) {
            return false;
        }
        for (WindVector wind : week) {
            if (wind == null) {
                return false;
            }
            if (!Float.isFinite(wind.baseSpeed())
                    || !Float.isFinite(wind.gustSpeed())
                    || !Float.isFinite(wind.angleRadians())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidStormWeek(float[] storm) {
        if (storm == null) {
            return false;
        }
        for (float value : storm) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
