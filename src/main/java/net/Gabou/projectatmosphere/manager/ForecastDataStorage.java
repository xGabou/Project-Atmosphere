package net.Gabou.projectatmosphere.manager;

import com.google.gson.*;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.HumidityGuard;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ForecastDataStorage {
    private static final String FILE_NAME = "forecast_centers.json";
    private static final String FORECAST_FILE = "biome_forecasts.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean hasForecastData;
    private static boolean hasCenterData;

    public static final Map<UUID, BlockPos> playerData = new ConcurrentHashMap<>();

    
    public static void saveAll(ServerLevel world) {
        savePlayerCenters(world);
        saveForecastMap(world);
    }

    public static void clearAll(ServerLevel world) {
        playerData.clear();
        hasForecastData = false;
        hasCenterData = false;

        
        try {
            Files.deleteIfExists(getSavePath(world, FILE_NAME));
            Files.deleteIfExists(getSavePath(world, FORECAST_FILE));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadAll(ServerLevel world) {
        clearCache();
        loadPlayerCenters(world);
        loadForecastMap(world);
    }

    private static void clearCache() {
        playerData.clear();
        hasForecastData = false;
        hasCenterData = false;
    }

    public static boolean  hasForecastData() {
        return hasForecastData;
    }
    public static boolean hasCenterData() {
        return hasCenterData;
    }

    private static void savePlayerCenters(ServerLevel world) {
        JsonObject root = new JsonObject();
        for (var entry : playerData.entrySet()) {
            UUID uuid = entry.getKey();
            BlockPos pos = entry.getValue();
            JsonObject obj = new JsonObject();
            obj.addProperty("x", pos.getX());
            obj.addProperty("y", pos.getY());
            obj.addProperty("z", pos.getZ());
            root.add(uuid.toString(), obj);
        }

        try {
            Path path = getSavePath(world, FILE_NAME);
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(root, w);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void loadPlayerCenters(ServerLevel world) {
        Path path = getSavePath(world, FILE_NAME);
        if (!Files.exists(path)) return;
        hasCenterData= Files.exists(StorageUtils.getPerWorldSavePath(world, "forecast_centers.json"));

        try (Reader r = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            for (var entry : root.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                JsonObject obj = entry.getValue().getAsJsonObject();
                int x = obj.get("x").getAsInt();
                int y = obj.get("y").getAsInt();
                int z = obj.get("z").getAsInt();
                playerData.put(uuid, new BlockPos(x, y, z));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void saveForecastMap(ServerLevel world) {
        ProjectAtmosphere.LOGGER.info(
                "[Atmosphere] Saving biome forecasts: entries={} thread={}",
                ForecastGenerator.getForecastMap().size(),
                Thread.currentThread().getName()
        );
        JsonObject root = new JsonObject();
        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : ForecastGenerator.getForecastMap().entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            BiomeForecast forecast = entry.getValue();

            JsonObject obj = new JsonObject();
            obj.addProperty("biome", key.biomeType().toString());
            obj.addProperty("x", key.samplePos().getX());
            obj.addProperty("y", key.samplePos().getY());
            obj.addProperty("z", key.samplePos().getZ());

            obj.add("temperature", serializeWeek(forecast.getTemperature()));
            obj.add("pressure", serializeWeek(forecast.getPressure()));
            obj.add("humidity", serializeWeek(forecast.getHumidity()));
            obj.add("wind", serializeWinds(forecast.getWind()));

            root.add(key.toString(), obj);
        }

        try {
            Path path = getSavePath(world, FORECAST_FILE);
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(root, w);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void loadForecastMap(ServerLevel world) {
        Path path = getSavePath(world, FORECAST_FILE);
        if (!Files.exists(path)) return;
        hasForecastData = Files.exists(StorageUtils.getPerWorldSavePath(world, "biome_forecasts.json"));

        try (Reader r = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            ProjectAtmosphere.LOGGER.info(
                    "[Atmosphere] Loading biome forecasts: entries={} thread={}",
                    root.size(),
                    Thread.currentThread().getName()
            );

            for (var entry : root.entrySet()) {
                JsonObject obj = entry.getValue().getAsJsonObject();
                ResourceLocation biome = ResourceLocation.parse(obj.get("biome").getAsString());
                BlockPos pos = new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt());
                BiomeInstanceKey key = new BiomeInstanceKey(biome, pos);

                BiomeForecast forecast = new BiomeForecast();
                forecast.setTemperature(deserializeWeek(obj.getAsJsonArray("temperature")));
                forecast.setPressure(deserializeWeek(obj.getAsJsonArray("pressure")));
                float[][] humidity = deserializeWeek(obj.getAsJsonArray("humidity"));
                humidity = HumidityGuard.clampWeekPercent(
                        humidity,
                        0f,
                        "ForecastDataStorage.loadForecastMap",
                        RegionInstanceKey.from(pos),
                        biome,
                        world.dimension(),
                        pos
                );
                forecast.setHumidity(humidity);
                forecast.setWind(deserializeWinds(obj.getAsJsonArray("wind")));
                if (obj.has("stormChance")) {
                    // legacy field retained for backward compatibility; no longer stored
                }

                ForecastGenerator.putForecast(key, forecast);
            }
            ForecastGenerator.groupForecastsByBiome();
            ForecastGenerator.groupBiomeByType();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static Path getSavePath(ServerLevel world, String fileName) {
        return StorageUtils.getPerWorldSavePath(world, fileName);
    }

    
    private static JsonArray serializeWeek(float[][] week) {
        JsonArray array = new JsonArray();
        if (week == null) return array;
        for (float[] pair : week) {
            JsonArray pairArr = new JsonArray();
            pairArr.add(pair[0]);
            pairArr.add(pair[1]);
            array.add(pairArr);
        }
        return array;
    }

    /**
     * Deserializes a week array from a JsonArray.
     * Returns float[entryCount][2]. If a pair is missing values, defaults to 0f.
     * Logs a warning if data is malformed.
     */
    private static float[][] deserializeWeek(JsonArray arr) {
        float[][] week = new float[arr == null ? 0 : arr.size()][2];
        if (arr == null) return week;
        for (int i = 0; i < arr.size(); i++) {
            JsonElement e = arr.get(i);
            if (e == null || !e.isJsonArray()) {
                System.err.println("[ProjectAtmosphere] Warning: Week entry at index " + i + " is not an array. Defaulted to 0.");
                week[i][0] = 0f;
                week[i][1] = 0f;
                continue;
            }
            JsonArray pair = e.getAsJsonArray();
            week[i][0] = (pair.size() > 0 && !pair.get(0).isJsonNull()) ? getAsFloatSafe(pair.get(0)) : 0f;
            week[i][1] = (pair.size() > 1 && !pair.get(1).isJsonNull()) ? getAsFloatSafe(pair.get(1)) : 0f;
            if (pair.size() < 2) {
                System.err.println("[ProjectAtmosphere] Warning: Week entry at index " + i + " missing value(s). Defaulted to 0.");
            }
        }
        return week;
    }

    
    private static float getAsFloatSafe(JsonElement e) {
        try {
            return e.getAsFloat();
        } catch (Exception ex) {
            return 0f;
        }
    }


    private static JsonArray serializeWinds(WindVector[] winds) {
        JsonArray array = new JsonArray();
        if (winds == null) return array;
        for (WindVector wind : winds) {
            JsonObject obj = new JsonObject();
            obj.addProperty("speed", wind.baseSpeed());
            obj.addProperty("angle", wind.angleRadians());
            obj.addProperty("gustSpeed", wind.gustSpeed());
            array.add(obj);
        }
        return array;
    }


    /**
     * Deserializes a WindVector array from a JsonArray.
     * If a value is missing, defaults to 0f and logs a warning.
     */
    private static WindVector[] deserializeWinds(JsonArray arr) {
        WindVector[] winds = new WindVector[arr == null ? 0 : arr.size()];
        if (arr == null) return winds;
        for (int i = 0; i < arr.size(); i++) {
            JsonElement e = arr.get(i);
            if (e == null || !e.isJsonObject()) {
                System.err.println("[ProjectAtmosphere] Warning: Wind entry at index " + i + " is not an object. Defaulted to zero wind.");
                winds[i] = new WindVector(0f, 0f, 0f);
                continue;
            }
            JsonObject obj = e.getAsJsonObject();
            float speed = obj.has("speed") && !obj.get("speed").isJsonNull() ? getAsFloatSafe(obj.get("speed")) : 0f;
            float angle = obj.has("angle") && !obj.get("angle").isJsonNull() ? getAsFloatSafe(obj.get("angle")) : 0f;
            float gustSpeed = obj.has("gustSpeed") && !obj.get("gustSpeed").isJsonNull() ? getAsFloatSafe(obj.get("gustSpeed")) : 0f;

            if (!obj.has("speed") || !obj.has("angle") || !obj.has("gustSpeed")) {
                System.err.println("[ProjectAtmosphere] Warning: Wind entry missing field(s) at index " + i + ". Defaulted to 0.");
            }

            winds[i] = new WindVector(speed, angle, gustSpeed);
        }
        return winds;
    }



}
