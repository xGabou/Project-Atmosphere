package net.Gabou.projectatmosphere.modules.temperature.util;

import com.google.gson.*;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static net.Gabou.projectatmosphere.util.StorageUtils.getPerWorldSavePath;


public class ForecastStorageManager {

    /*
     * This class is responsible for storing and retrieving temperature forecasts
     * for different biomes. It uses a JSON file to save the data, and provides
     * methods to load, save, and clear the cache.
     */
    // A map to store sample positions for biomes
    public static final String FILE_NAME = "projectatmosphere_forecasts.json";


    /**
     * Saves the sample position for a given biome.
     * This method is used to store the position of a biome for future reference.
     */

    /**
     * Returns the sample position for a given biome.
     * This method is used to retrieve the position of a biome.
     */

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<BiomeInstanceKey, float[][]> cache = new HashMap<>();

    /**
     * Loads the temperature forecasts from the JSON file.
     * This method is called asynchronously to avoid blocking the main thread.
     */
    public static void loadAll(ServerLevel world) {
        AsyncAtmosphereService.runTemperature(() -> {
            Path savePath = getPerWorldSavePath(world, FILE_NAME);
            if (!Files.exists(savePath)) return;

            try (Reader r = Files.newBufferedReader(savePath)) {
                JsonObject root = GSON.fromJson(r, JsonObject.class);
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    BiomeInstanceKey key = BiomeInstanceKey.fromString(e.getKey());
                    JsonArray arr = e.getValue().getAsJsonArray();

                    float[][] week = new float[arr.size()][2];
                    for (int i = 0; i < arr.size(); i++) {
                        JsonArray pair = arr.get(i).getAsJsonArray();
                        week[i][0] = pair.get(0).getAsFloat();
                        week[i][1] = pair.get(1).getAsFloat();
                    }

                    saveForecast(key, week);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }


    /**
     * Saves the temperature forecasts to the JSON file.
     * This method is called asynchronously to avoid blocking the main thread.
     */
    public static void saveAll(ServerLevel world) {
        AsyncAtmosphereService.runTemperature(() -> {
            JsonObject root = new JsonObject();

            for (var entry : cache.entrySet()) {
                BiomeInstanceKey key = entry.getKey();
                float[][] week = entry.getValue();

                JsonArray arr = new JsonArray();
                for (float[] day : week) {
                    JsonArray pair = new JsonArray();
                    pair.add(day[0]);
                    pair.add(day[1]);
                    arr.add(pair);
                }

                root.add(key.toString(), arr);
            }

            try {
                Path path = getPerWorldSavePath(world, FILE_NAME);
                Files.createDirectories(path.getParent());
                try (Writer w = Files.newBufferedWriter(path)) {
                    GSON.toJson(root, w);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }



    /**
     * Loads the temperature forecasts from the JSON file.
     * This method is called synchronously to avoid blocking the main thread.
     */
    public static boolean hasForecast(BiomeInstanceKey biome) {
        BiomeInstanceKey key = AtmosphereUtils.findNearestBiomeInstanceKey(biome, cache);
        return key!=null && cache.containsKey(key);
    }

    /**
     * Returns the temperature forecast for a given biome.
     * The forecast is a 7x2 array representing the min and max temperatures for each day of the week.
     */
    public static float[][] getForecast(BiomeInstanceKey biome) {
        return AtmosphereUtils.getRightForecastForBiome(biome, cache);
    }

    /**
     * Saves the temperature forecast for a given biome.
     * The forecast is a 7x2 array representing the min and max temperatures for each day of the week.
     */
    public static void saveForecast(BiomeInstanceKey biome, float[][] week) {
        cache.put(biome, week);
    }

    /**
     * Returns a set of all biome keys in the cache.
     * This method is used to get the list of biomes for which forecasts are available.
     */
    public static Set<BiomeInstanceKey> getAllBiomeKeys() {
        return new HashSet<>(cache.keySet());
    }

    /**
     * Clears the cache and deletes the JSON file.
     * This method is called when the mod is unloaded or when the user wants to reset the forecasts.
     */
    public static void clearCache(ServerLevel world) {
        cache.clear();
        StorageUtils.clearCache(world, FILE_NAME);
    }
}
