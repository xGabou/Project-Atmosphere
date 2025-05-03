package net.Gabou.projectatmosphere.temperature.util;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ForecastStorageManager {

    private static final Path SAVE_PATH = Paths.get("config", "projectatmosphere_forecasts.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, float[][]> cache = new HashMap<>();

    public static void loadAll() {
        AsyncTemperatureService.runAsync(() -> {
            if (!Files.exists(SAVE_PATH)) return;
            try (Reader r = Files.newBufferedReader(SAVE_PATH)) {
                JsonObject root = GSON.fromJson(r, JsonObject.class);
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    String biome = e.getKey();
                    JsonArray arr = e.getValue().getAsJsonArray();
                    float[][] week = new float[7][2];
                    for (int i = 0; i < 7; i++) {
                        JsonArray pair = arr.get(i).getAsJsonArray();
                        week[i][0] = pair.get(0).getAsFloat();
                        week[i][1] = pair.get(1).getAsFloat();
                    }
                    cache.put(biome, week);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }

    public static void saveAll() {
        AsyncTemperatureService.runAsync(() -> {
            JsonObject root = new JsonObject();
            cache.forEach((biome, week) -> {
                JsonArray arr = new JsonArray();
                for (int i = 0; i < 7; i++) {
                    JsonArray pair = new JsonArray();
                    pair.add(week[i][0]);
                    pair.add(week[i][1]);
                    arr.add(pair);
                }
                root.add(biome, arr);
            });
            try {
                Files.createDirectories(SAVE_PATH.getParent());
                try (Writer w = Files.newBufferedWriter(SAVE_PATH)) {
                    GSON.toJson(root, w);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }

    public static boolean hasForecast(ResourceLocation biome) {
        return cache.containsKey(biome.toString());
    }

    public static float[][] getForecast(ResourceLocation biome) {
        return cache.get(biome.toString());
    }

    public static void saveForecast(ResourceLocation biome, float[][] week) {
        cache.put(biome.toString(), week);
    }

    public static Set<String> getAllBiomeKeys() {
        return new HashSet<>(cache.keySet());
    }
}
