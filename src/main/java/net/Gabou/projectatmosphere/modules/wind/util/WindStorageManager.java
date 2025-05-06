package net.Gabou.projectatmosphere.modules.wind.util;

import com.google.gson.*;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.StorageUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Static storage system for 7-day float[2] wind forecasts per biome.
 */
public class WindStorageManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<BiomeInstanceKey, float[][]> CACHE = new HashMap<>();
    private static final String FILE_NAME = "wind_forecasts.json";

    public static boolean hasForecast(BiomeInstanceKey biome) {
        BiomeInstanceKey key = AtmosphereUtils.findNearestBiomeInstanceKey(biome,CACHE);
        return key != null && CACHE.containsKey(key);
    }

    public static float[][] getForecast(BiomeInstanceKey biome) {
        return AtmosphereUtils.getRightForecastForBiome(biome,CACHE);
    }

    public static void saveForecast(BiomeInstanceKey biome, float[][] week) {
        CACHE.put(biome, week);
    }

    public static void clearCache(ServerLevel world) {
        CACHE.clear();
        StorageUtils.clearCache(world,FILE_NAME);

    }

    public static void saveAll(ServerLevel world) {
        StorageUtils.saveAll(world,CACHE, FILE_NAME,GSON);
    }

    public static void loadAll(ServerLevel world) {
        StorageUtils.loadAll(world,CACHE, FILE_NAME,GSON);
    }

    public static Set<BiomeInstanceKey> getAllBiomeKeys() {
        return CACHE.keySet();
    }
    
}
