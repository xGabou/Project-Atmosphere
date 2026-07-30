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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * RegionPersistence implementation for region-only forecast JSON.
 */
public final class FileRegionPersistence implements RegionPersistence {
    public static final String REGION_FOLDER = "region_forecasts";
    public static final String LEGACY_FALLBACK_FOLDER = "region_fallbacks";
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

    public boolean hasLegacyRegionData() {
        Path folder = regionFolder();
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .anyMatch(this::isLegacyRegionFile);
        } catch (IOException e) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to inspect legacy region forecasts in {}", folder, e);
            return false;
        }
    }

    public boolean hasLegacyFallbackData() {
        Path folder = legacyFallbackFolder();
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(folder)) {
            return stream.anyMatch(path -> path.getFileName().toString().endsWith(".json"));
        } catch (IOException e) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to inspect legacy region fallbacks in {}", folder, e);
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
            if (isLegacyRegionJson(root)) {
                logInvalidRegion(path, "legacy biome-backed region file");
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

            Map<ResourceLocation, Integer> weights = readBiomeWeights(root.getAsJsonObject("biomeWeights"));
            float[][] temperature = deserialize2d(root.getAsJsonArray("temperature"));
            float[][] humidity = deserialize2d(root.getAsJsonArray("humidity"));
            float[][] pressure = deserialize2d(root.getAsJsonArray("pressure"));
            WindVector[] wind = deserializeWindWeek(root.getAsJsonArray("wind"));
            float[] storm = deserialize1d(root.getAsJsonArray("storm"));

            if (!isValidWeek(temperature, -120f, 100f)
                    || !isValidWeek(humidity, 0f, 100f)
                    || !isValidWeek(pressure, 850f, 1100f)
                    || !isValidWindWeek(wind)
                    || !isValidStormWeek(storm)) {
                logInvalidRegion(path, "integrity validation failed");
                return Optional.empty();
            }

            RegionCurves curves = new DefaultRegionCurves(temperature, humidity, pressure, wind, storm);
            return Optional.of(new ForecastRegion(storedId, anchor, curves, weights));
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
        root.addProperty("version", 2);
        root.add("region", writeRegionId(region.getKey()));
        if (region.getAnchor() != null) {
            root.add("anchor", AtmosphereUtils.serializeBlockPos(region.getAnchor()));
        }
        root.add("biomeWeights", writeBiomeWeights(region.getBiomeWeights()));
        root.add("temperature", serialize2d(region.getTemperature()));
        root.add("humidity", serialize2d(region.getHumidity()));
        root.add("pressure", serialize2d(region.getPressure()));
        root.add("wind", serializeWindWeek(region.getWind()));
        root.add("storm", serialize1d(region.curves() == null ? null : region.curves().stormWeek()));

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

    public Path regionFolder() {
        return StorageUtils.getPerWorldSavePath(level, REGION_FOLDER);
    }

    public Path legacyFallbackFolder() {
        return StorageUtils.getPerWorldSavePath(level, LEGACY_FALLBACK_FOLDER);
    }

    public Path regionPathFor(RegionInstanceKey id) {
        String fileName = REGION_FOLDER + "/" + id.regionX() + "_" + id.regionZ() + "_" + id.regionSize() + ".json";
        return StorageUtils.getPerWorldSavePath(level, fileName);
    }

    private boolean isLegacyRegionFile(Path path) {
        try (Reader r = Files.newBufferedReader(path)) {
            JsonObject root = gson.fromJson(r, JsonObject.class);
            return root != null && isLegacyRegionJson(root);
        } catch (IOException | RuntimeException e) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to inspect region forecast {}", path, e);
            return true;
        }
    }

    public static boolean isLegacyRegionJson(JsonObject root) {
        if (root == null) {
            return false;
        }
        if (root.has("sourceBiomes")) {
            return true;
        }
        JsonArray sections = root.getAsJsonArray("sections");
        if (sections == null) {
            return false;
        }
        for (JsonElement element : sections) {
            if (element != null && element.isJsonObject() && element.getAsJsonObject().has("biome")) {
                return true;
            }
        }
        return false;
    }

    public Optional<RegionInstanceKey> parseRegionIdFromFile(Path path) {
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

    public static JsonObject writeRegionId(RegionInstanceKey id) {
        JsonObject obj = new JsonObject();
        obj.addProperty("rx", id.regionX());
        obj.addProperty("rz", id.regionZ());
        obj.addProperty("size", id.regionSize());
        return obj;
    }

    public static RegionInstanceKey readRegionId(JsonObject root) {
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

    private static JsonObject writeBiomeWeights(Map<ResourceLocation, Integer> weights) {
        JsonObject obj = new JsonObject();
        if (weights == null) {
            return obj;
        }
        weights.forEach((biome, weight) -> {
            if (biome != null && weight != null && weight > 0) {
                obj.addProperty(biome.toString(), weight);
            }
        });
        return obj;
    }

    public static Map<ResourceLocation, Integer> readBiomeWeights(JsonObject obj) {
        Map<ResourceLocation, Integer> weights = new HashMap<>();
        if (obj == null) {
            return weights;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            try {
                int weight = entry.getValue().getAsInt();
                if (weight > 0) {
                    weights.put(ResourceLocation.parse(entry.getKey()), weight);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return weights;
    }

    public static JsonArray serialize2d(float[][] data) {
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

    public static float[][] deserialize2d(JsonArray arr) {
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

    public static JsonArray serialize1d(float[] data) {
        JsonArray arr = new JsonArray();
        if (data == null) {
            return arr;
        }
        for (float value : data) {
            arr.add(value);
        }
        return arr;
    }

    public static float[] deserialize1d(JsonArray arr) {
        if (arr == null) {
            return new float[0];
        }
        float[] out = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = arr.get(i).getAsFloat();
        }
        return out;
    }

    public static JsonArray serializeWindWeek(WindVector[] week) {
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

    public static WindVector[] deserializeWindWeek(JsonArray arr) {
        if (arr == null) {
            return null;
        }
        WindVector[] week = new WindVector[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            JsonObject obj = arr.get(i).getAsJsonObject();
            float base = obj.has("base") ? obj.get("base").getAsFloat() : getLegacyFloat(obj, "speed");
            float angle = obj.has("angle") ? obj.get("angle").getAsFloat() : 0f;
            float gust = obj.has("gust") ? obj.get("gust").getAsFloat() : getLegacyFloat(obj, "gustSpeed", base);
            week[i] = new WindVector(base, angle, gust);
        }
        return week;
    }

    private static float getLegacyFloat(JsonObject obj, String key) {
        return getLegacyFloat(obj, key, 0f);
    }

    private static float getLegacyFloat(JsonObject obj, String key, float fallback) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsFloat() : fallback;
    }

    private void logInvalidRegion(Path path, String reason) {
        ProjectAtmosphere.LOGGER.warn("[Atmosphere] Region forecast {} rejected during load: {}", path, reason);
    }

    private static boolean isValidWeek(float[][] week, float min, float max) {
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
            if (wind == null
                    || !Float.isFinite(wind.baseSpeed())
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
