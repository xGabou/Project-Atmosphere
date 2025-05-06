package net.Gabou.projectatmosphere.modules.humidity.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.StorageUtils;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HumidityStorageManager {
    private static final Gson GSON = new Gson();
    private static final Map<BiomeInstanceKey, float[][]> cache = new ConcurrentHashMap<>();
    public static final String FILE_NAME = "humidity_forecasts.json";

    public static void loadAll(ServerLevel world) {
        Path savePath = StorageUtils.getPerWorldSavePath(world, FILE_NAME);
        if (!Files.exists(savePath)) return;

        try (Reader reader = Files.newBufferedReader(savePath)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            for (var entry : root.entrySet()) {
                BiomeInstanceKey key = BiomeInstanceKey.fromString(entry.getKey());
                JsonArray weekArr = entry.getValue().getAsJsonArray();

                float[][] week = new float[weekArr.size()][2];
                for (int i = 0; i < weekArr.size(); i++) {
                    JsonArray dayArr = weekArr.get(i).getAsJsonArray();
                    week[i][0] = dayArr.get(0).getAsFloat();
                    week[i][1] = dayArr.get(1).getAsFloat();
                }

                putForecast(key, week);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void saveAll(ServerLevel world) {
        Path savePath = StorageUtils.getPerWorldSavePath(world, FILE_NAME);
        JsonObject root = new JsonObject();

        for (BiomeInstanceKey key : getAllBiomeKeys()) {
            float[][] week = getForecast(key);
            JsonArray weekArr = new JsonArray();
            for (float[] day : week) {
                JsonArray dayArr = new JsonArray();
                dayArr.add(day[0]);
                dayArr.add(day[1]);
                weekArr.add(dayArr);
            }
            root.add(key.toString(), weekArr);
        }

        try (Writer writer = Files.newBufferedWriter(savePath)) {
            GSON.toJson(root, writer);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void putForecast(BiomeInstanceKey biome, float[][] week) {
        cache.put(biome, week);
    }

    public static boolean hasForecast(BiomeInstanceKey biome) {
        BiomeInstanceKey resolved = AtmosphereUtils.findNearestBiomeInstanceKey(biome, cache);
        return resolved != null && cache.containsKey(resolved);
    }

    public static float[][] getForecast(BiomeInstanceKey biome) {
        return AtmosphereUtils.getRightForecastForBiome(biome, cache);
    }

    public static Set<BiomeInstanceKey> getAllBiomeKeys() {
        return cache.keySet();
    }

    public static void clearCache(ServerLevel world) {
        cache.clear();
        StorageUtils.clearCache(world, FILE_NAME);
    }
}
