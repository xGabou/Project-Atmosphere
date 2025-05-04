package net.Gabou.projectatmosphere.temperature.util;

import com.google.gson.*;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static net.Gabou.projectatmosphere.temperature.Temperature.getPerWorldSavePath;

public class ForecastStorageManager {

    /*
     * This class is responsible for storing and retrieving temperature forecasts
     * for different biomes. It uses a JSON file to save the data, and provides
     * methods to load, save, and clear the cache.
     */
    // A map to store sample positions for biomes
    private static final Map<String, BlockPos> samplePositions = new HashMap<>();
    public static final String FILE_NAME = "projectatmosphere_forecasts.json";


    /**
     * Saves the sample position for a given biome.
     * This method is used to store the position of a biome for future reference.
     */
    public static void saveSamplePosition(ResourceLocation biome, BlockPos pos) {
        samplePositions.put(biome.toString(), pos);
    }
    /**
     * Returns the sample position for a given biome.
     * This method is used to retrieve the position of a biome.
     */
    public static BlockPos getSamplePosition(ResourceLocation biome) {
        return samplePositions.get(biome.toString());
    }


    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, float[][]> cache = new HashMap<>();

    /**
     * Loads the temperature forecasts from the JSON file.
     * This method is called asynchronously to avoid blocking the main thread.
     */
    public static void loadAll(ServerLevel world) {
        AsyncAtmosphereService.runAsync(AsyncAtmosphereService.Branch.TEMPERATURE,() -> {
            Path SAVE_PATH = getPerWorldSavePath(world, FILE_NAME);
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

    /**
     * Saves the temperature forecasts to the JSON file.
     * This method is called asynchronously to avoid blocking the main thread.
     */
    public static void saveAll(ServerLevel world) {
        AsyncAtmosphereService.runAsync(AsyncAtmosphereService.Branch.TEMPERATURE,() -> {
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
    public static boolean hasForecast(ResourceLocation biome) {
        return cache.containsKey(biome.toString());
    }

    /**
     * Returns the temperature forecast for a given biome.
     * The forecast is a 7x2 array representing the min and max temperatures for each day of the week.
     */
    public static float[][] getForecast(ResourceLocation biome) {
        return cache.get(biome.toString());
    }

    /**
     * Saves the temperature forecast for a given biome.
     * The forecast is a 7x2 array representing the min and max temperatures for each day of the week.
     */
    public static void saveForecast(ResourceLocation biome, float[][] week) {
        cache.put(biome.toString(), week);
    }

    /**
     * Returns a set of all biome keys in the cache.
     * This method is used to get the list of biomes for which forecasts are available.
     */
    public static Set<String> getAllBiomeKeys() {
        return new HashSet<>(cache.keySet());
    }

    /**
     * Clears the cache and deletes the JSON file.
     * This method is called when the mod is unloaded or when the user wants to reset the forecasts.
     */
    public static void clearCache(ServerLevel world) {
        cache.clear();
        samplePositions.clear();
        try {
            Files.deleteIfExists(getPerWorldSavePath(world, FILE_NAME));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
