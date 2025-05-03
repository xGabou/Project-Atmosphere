package net.Gabou.projectatmosphere.temperature.util;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

public class ForecastStorageManager {

    private static final Path SAVE_PATH = Paths.get("config/projectatmosphere_forecasts.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** biome → [7][2] */
    static Map<String, float[][]> cache = new HashMap<>();

    public static void loadAll() {
        AsyncTemperatureService.runAsync(() -> {
            if (!Files.exists(SAVE_PATH)) return;
            try (Reader r = Files.newBufferedReader(SAVE_PATH)) {
                Map<?, ?> m = GSON.fromJson(r, Map.class);
                // TODO: deserialize into cache
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static void saveAll() {
        AsyncTemperatureService.runAsync(() -> {
            try (Writer w = Files.newBufferedWriter(SAVE_PATH)) {
                GSON.toJson(cache, w);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static void putForecast(ResourceLocation biome, float[][] week) {
        cache.put(biome.toString(), week);
    }

    public static float[][] getForecast(ResourceLocation biome) {
        return cache.get(biome.toString());
    }
    // inside ForecastStorageManager

    /**
     * @return a snapshot of all biome IDs currently cached
     */
    public static Set<String> getAllBiomeKeys() {
        return new HashSet<>(cache.keySet());
    }

}
