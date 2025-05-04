package net.Gabou.projectatmosphere.modules.humidity.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static net.Gabou.projectatmosphere.util.AtmosphereUtils.getPerWorldSavePath;

public class HumidityStorageManager  {
    private static final Gson GSON = new Gson();
    private static final Map<String, float[][]> cache = new ConcurrentHashMap<>();
    public static final String FILE_NAME = "humidity_forecasts.json";

    /**
     * Load all saved weekly forecasts from disk into the in-memory cache.
     */
    public static void loadAll(ServerLevel world) {
        Path savePath = AtmosphereUtils.getPerWorldSavePath(world, FILE_NAME);
        if (!Files.exists(savePath)) return;

        try (Reader reader = Files.newBufferedReader(savePath)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            for (var entry : root.entrySet()) {
                String biomeKey = entry.getKey();
                JsonArray weekArr = entry.getValue().getAsJsonArray();
                float[][] week = new float[weekArr.size()][2];
                for (int i = 0; i < weekArr.size(); i++) {
                    JsonArray dayArr = weekArr.get(i).getAsJsonArray();
                    week[i][0] = dayArr.get(0).getAsFloat();
                    week[i][1] = dayArr.get(1).getAsFloat();
                }
                cache.put(biomeKey, week);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Save or update the weekly forecast for a single biome, persisting immediately.
     */
    public static void saveForecast(ServerLevel world, ResourceLocation biome, float[][] week) {
        saveAll(world);
    }
    public static void putForecast(ResourceLocation biome, float[][] week) {
        cache.put(biome.toString(), week);
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

    public static void clearCache(ServerLevel world) {
        cache.clear();
        //samplePositions.clear();
        try {
            Files.deleteIfExists(getPerWorldSavePath(world, FILE_NAME));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Write the entire cache out to disk as JSON.
     */
    public static void saveAll(ServerLevel world) {
        Path savePath = AtmosphereUtils.getPerWorldSavePath(world, FILE_NAME);
        JsonObject root = new JsonObject();

        for (var e : cache.entrySet()) {
            JsonArray weekArr = new JsonArray();
            for (float[] day : e.getValue()) {
                JsonArray dayArr = new JsonArray();
                dayArr.add(day[0]);
                dayArr.add(day[1]);
                weekArr.add(dayArr);
            }
            root.add(e.getKey(), weekArr);
        }

        try (Writer writer = Files.newBufferedWriter(savePath)) {
            GSON.toJson(root, writer);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Optional: record where we sampled this biome for debugging.
     */
    public static void saveSamplePosition(ResourceLocation biome, BlockPos pos) {
        // no-op for now
    }
}
