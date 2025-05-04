// net/Gabou/projectatmosphere/humidity/util/HumidityStorageManager.java
package net.Gabou.projectatmosphere.humidity.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HumidityStorageManager {
    private static final Path SAVE_PATH = Path.of("world", "projectatmosphere", "humidity_forecasts.json");
    private static final Gson GSON = new Gson();

    private static final Map<String, float[][]> cache = new ConcurrentHashMap<>();

    public static void loadAll() {
        // TODO: read JSON from SAVE_PATH into cache
    }
    public static void saveForecast(ResourceLocation biome, float[][] week) {
        cache.put(biome.toString(), week);
        saveAll();
    }
    public static boolean hasForecast(ResourceLocation biome) {
        return cache.containsKey(biome.toString());
    }
    public static float[][] getForecast(ResourceLocation biome) {
        return cache.get(biome.toString());
    }
    public static Set<String> getAllBiomeKeys() {
        return cache.keySet();
    }
    public static void clearCache() {
        cache.clear();
    }

    private static void saveAll() {
        // TODO: dump cache → JSON at SAVE_PATH
    }

    public static void saveSamplePosition(ResourceLocation biome, BlockPos pos) {
        // optionally record sample positions
    }
}
