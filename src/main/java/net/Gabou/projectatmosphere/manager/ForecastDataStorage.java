package net.Gabou.projectatmosphere.manager;

import com.google.gson.*;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
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

    // Save both forecast centers and forecast map
    public static void saveAll(ServerLevel world) {
        savePlayerCenters(world);
        saveForecastMap(world);
    }

    public static void loadAll(ServerLevel world) {
        loadPlayerCenters(world);
        loadForecastMap(world);
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

            for (var entry : root.entrySet()) {
                JsonObject obj = entry.getValue().getAsJsonObject();
                ResourceLocation biome = ResourceLocation.parse(obj.get("biome").getAsString());
                BlockPos pos = new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt());
                BiomeInstanceKey key = new BiomeInstanceKey(biome, pos);

                BiomeForecast forecast = new BiomeForecast();
                forecast.setTemperature(deserializeWeek(obj.getAsJsonArray("temperature")));
                forecast.setPressure(deserializeWeek(obj.getAsJsonArray("pressure")));
                forecast.setHumidity(deserializeWeek(obj.getAsJsonArray("humidity")));
                forecast.setWind(deserializeWinds(obj.getAsJsonArray("wind")));

                ForecastGenerator.putForecast(key, forecast);
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static Path getSavePath(ServerLevel world, String fileName) {
        return StorageUtils.getPerWorldSavePath(world, fileName);
    }

    // Helpers
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

    private static float[][] deserializeWeek(JsonArray arr) {
        float[][] week = new float[arr.size()][2];
        for (int i = 0; i < arr.size(); i++) {
            JsonArray pair = arr.get(i).getAsJsonArray();
            week[i][0] = pair.get(0).getAsFloat();
            week[i][1] = pair.get(1).getAsFloat();
        }
        return week;
    }

    private static JsonArray serializeWinds(WindVector[] winds) {
        JsonArray array = new JsonArray();
        if (winds == null) return array;
        for (WindVector wind : winds) {
            JsonObject obj = new JsonObject();
            obj.addProperty("speed", wind.speed());
            obj.addProperty("angle", wind.angleRadians());
            array.add(obj);
        }
        return array;
    }


    private static WindVector[] deserializeWinds(JsonArray arr) {
        WindVector[] winds = new WindVector[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            JsonObject obj = arr.get(i).getAsJsonObject();
            float speed = obj.get("speed").getAsFloat();
            float angle = obj.get("angle").getAsFloat();
            winds[i] = new WindVector(speed, angle);
        }
        return winds;
    }

}
