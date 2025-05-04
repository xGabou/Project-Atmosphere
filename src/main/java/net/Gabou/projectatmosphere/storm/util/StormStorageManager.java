// net/Gabou/projectatmosphere/storm/util/StormStorageManager.java
package net.Gabou.projectatmosphere.storm.util;

import com.google.gson.Gson;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StormStorageManager {
    private static final Path SAVE_PATH = Path.of("world", "projectatmosphere", "storm_forecasts.json");
    private static final Gson GSON = new Gson();

    private static final Map<String,double[]> cache = new ConcurrentHashMap<>();

    public static void loadAll() {
        // TODO: load JSON → cache
    }
    public static void saveForecast(ResourceLocation biome, double[] week) {
        cache.put(biome.toString(), week);
        // TODO: persist
    }
    public static boolean hasForecast(ResourceLocation biome) {
        return cache.containsKey(biome.toString());
    }
    public static double[] getForecast(ResourceLocation biome) {
        return cache.get(biome.toString());
    }
    public static Set<String> getAllBiomeKeys() {
        return cache.keySet();
    }
    public static void clearCache() {
        cache.clear();
    }
    public static void saveSamplePosition(ResourceLocation biome, BlockPos pos) {
        // optional: record sample positions
    }
}
