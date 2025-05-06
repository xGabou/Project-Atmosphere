// src/main/java/net/Gabou/projectatmosphere/modules/pressure/util/PressureStorageManager.java
package net.Gabou.projectatmosphere.modules.pressure.util;

import com.google.gson.*;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PressureStorageManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<BiomeInstanceKey, float[][]> CACHE = new HashMap<>();
    private static final String FILE_NAME = "pressure_forecasts.json";

    public static boolean hasForecast(BiomeInstanceKey key) {
        BiomeInstanceKey resolved = AtmosphereUtils.findNearestBiomeInstanceKey(key, CACHE);
        return resolved != null && CACHE.containsKey(resolved);
    }

    public static float[][] getForecast(BiomeInstanceKey key) {
        return AtmosphereUtils.getRightForecastForBiome(key, CACHE);
    }

    public static void putForecast(BiomeInstanceKey key, float[][] week) {
        CACHE.put(key, week);
    }

    public static Set<BiomeInstanceKey> getAllBiomeKeys() {
        return CACHE.keySet();
    }

    public static void clearCache(ServerLevel world) {
        CACHE.clear();
        StorageUtils.clearCache(world, FILE_NAME);
    }

    public static void saveAll(ServerLevel world) {
        JsonObject root = new JsonObject();

        for (var entry : CACHE.entrySet()) {
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
            Path p = getSavePath(world);
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                GSON.toJson(root, w);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }



    public static void loadAll(ServerLevel world) {
        Path p = getSavePath(world);
        if (!Files.exists(p)) return;

        try (Reader r = Files.newBufferedReader(p)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            for (var entry : root.entrySet()) {
                BiomeInstanceKey key = BiomeInstanceKey.fromString(entry.getKey());
                JsonArray arr = entry.getValue().getAsJsonArray();

                float[][] week = new float[arr.size()][2];
                for (int i = 0; i < arr.size(); i++) {
                    JsonArray pair = arr.get(i).getAsJsonArray();
                    week[i][0] = pair.get(0).getAsFloat();
                    week[i][1] = pair.get(1).getAsFloat();
                }

                putForecast(key, week);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


    private static Path getSavePath(ServerLevel world) {
        return StorageUtils.getPerWorldSavePath(world, FILE_NAME);
    }
}
