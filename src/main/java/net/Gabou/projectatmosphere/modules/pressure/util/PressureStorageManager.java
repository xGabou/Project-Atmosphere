// src/main/java/net/Gabou/projectatmosphere/modules/pressure/util/PressureStorageManager.java
package net.Gabou.projectatmosphere.modules.pressure.util;

import com.google.gson.*;
import net.Gabou.projectatmosphere.modules.pressure.forecast.PressureForecast.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
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
        return CACHE.containsKey(key);
    }

    public static float[][] getForecast(BiomeInstanceKey key) {
        return CACHE.getOrDefault(key, new float[7][2]);
    }

    public static void putForecast(BiomeInstanceKey key, float[][] week) {
        CACHE.put(key, week);
    }

    public static Set<BiomeInstanceKey> getAllBiomeKeys() {
        return CACHE.keySet();
    }

    public static void clearCache(ServerLevel world) {
        CACHE.clear();
        try {
            Files.deleteIfExists(getSavePath(world));
        } catch (IOException ignored) {}
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

            JsonObject obj = new JsonObject();
            obj.add("biome", new JsonPrimitive(key.biomeType().toString()));
            obj.add("pos", AtmosphereUtils.serializeBlockPos(key.samplePos()));
            obj.add("data", arr);

            root.add(key.biomeType().toString() + "@" + key.samplePos().toShortString(), obj);
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
                JsonObject obj = entry.getValue().getAsJsonObject();
                ResourceLocation biome = new ResourceLocation(obj.get("biome").getAsString());
                BlockPos pos = AtmosphereUtils.deserializeBlockPos(obj.get("pos").getAsJsonObject());

                JsonArray arr = obj.get("data").getAsJsonArray();
                float[][] week = new float[7][2];
                for (int i = 0; i < 7; i++) {
                    JsonArray pair = arr.get(i).getAsJsonArray();
                    week[i][0] = pair.get(0).getAsFloat();
                    week[i][1] = pair.get(1).getAsFloat();
                }

                CACHE.put(new BiomeInstanceKey(biome, pos), week);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static Path getSavePath(ServerLevel world) {
        return AtmosphereUtils.getPerWorldSavePath(world, FILE_NAME);
    }
}
