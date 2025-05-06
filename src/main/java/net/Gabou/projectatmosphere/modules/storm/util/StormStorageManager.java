package net.Gabou.projectatmosphere.modules.storm.util;

import com.google.gson.*;
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

public class StormStorageManager {
    private static final Gson GSON = new Gson();
    private static final Map<BiomeInstanceKey, float[]> cache = new ConcurrentHashMap<>();
    public static final String FILE_NAME = "storm_forecasts.json";

    public static void loadAll(ServerLevel world) {
        Path path = StorageUtils.getPerWorldSavePath(world, FILE_NAME);
        if (!Files.exists(path)) return;

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : root.entrySet()) {
                BiomeInstanceKey key = BiomeInstanceKey.fromString(entry.getKey());

                JsonArray arr = entry.getValue().getAsJsonArray();
                float[] week = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    week[i] = arr.get(i).getAsFloat();
                }

                saveForecast(key, week);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveAll(ServerLevel world) {
        Path path = StorageUtils.getPerWorldSavePath(world, FILE_NAME);
        JsonObject root = new JsonObject();

        for (var entry : cache.entrySet()) {
            JsonArray arr = new JsonArray();
            for (float f : entry.getValue()) {
                arr.add(f);
            }
            root.add(entry.getKey().toString(), arr);
        }

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveForecast(BiomeInstanceKey biome, float[] week) {
        cache.put(biome, week);
    }

    public static boolean hasForecast(BiomeInstanceKey biome) {
        BiomeInstanceKey resolved = AtmosphereUtils.findNearestBiomeInstanceKey(biome, cache);
        return resolved != null && cache.containsKey(resolved);
    }

    public static float[] getForecast(BiomeInstanceKey biome) {
        return AtmosphereUtils.getRightForecastForBiome1(biome, cache);
    }

    public static Set<BiomeInstanceKey> getAllBiomeKeys() {
        return cache.keySet();
    }

    public static void clearCache(ServerLevel world) {
        cache.clear();
        try {
            Files.deleteIfExists(StorageUtils.getPerWorldSavePath(world, FILE_NAME));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
