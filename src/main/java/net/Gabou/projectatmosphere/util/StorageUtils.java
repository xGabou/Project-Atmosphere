package net.Gabou.projectatmosphere.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class StorageUtils {
    // ---------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------
    public static void loadAll(ServerLevel world, Map<BiomeInstanceKey, float[][]> cache, String fileName, Gson gson) {
            Path SAVE_PATH = getPerWorldSavePath(world, fileName);
            if (!Files.exists(SAVE_PATH)) return;
            try (Reader r = Files.newBufferedReader(SAVE_PATH)) {
                JsonObject root = gson.fromJson(r, JsonObject.class);
                for (var entry : root.entrySet()) {
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    ResourceLocation biome = ResourceLocation.parse(obj.get("biome").getAsString());
                    BlockPos pos = AtmosphereUtils.deserializeBlockPos(obj.get("pos").getAsJsonObject());
                    JsonArray arr = obj.get("data").getAsJsonArray();
                    float[][] week = new float[7][2];
                    for (int i = 0; i < 7; i++) {
                        JsonArray pair = arr.get(i).getAsJsonArray();
                        week[i][0] = pair.get(0).getAsFloat();
                        week[i][1] = pair.get(1).getAsFloat();
                    }
                    cache.put(new BiomeInstanceKey(biome, pos), week);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
    }
    public static void loadAll1(ServerLevel world, Map<BiomeInstanceKey, float[]> cache, String fileName, Gson gson) {
        Path SAVE_PATH = getPerWorldSavePath(world, fileName);
        if (!Files.exists(SAVE_PATH)) return;
        try (Reader r = Files.newBufferedReader(SAVE_PATH)) {
            JsonObject root = gson.fromJson(r, JsonObject.class);
            for (var entry : root.entrySet()) {
                JsonObject obj = entry.getValue().getAsJsonObject();
                ResourceLocation biome = ResourceLocation.parse(obj.get("biome").getAsString());
                BlockPos pos = AtmosphereUtils.deserializeBlockPos(obj.get("pos").getAsJsonObject());
                JsonArray arr = obj.get("data").getAsJsonArray();
                float[] week = new float[7];
                for (int i = 0; i < 7; i++) {
                    JsonArray pair = arr.get(i).getAsJsonArray();
                    week[i] = pair.get(0).getAsFloat();
                }
                cache.put(new BiomeInstanceKey(biome, pos), week);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    public static void clearCache(ServerLevel world, String fileName) {
        try {
            Files.deleteIfExists(getPerWorldSavePath(world, fileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ---------------------------------------------------------------------
    // Saving
    // ---------------------------------------------------------------------
    /**
     * Saves the temperature forecasts to the JSON file.
     * This method is called asynchronously to avoid blocking the main thread.
     */
    public static void saveAll(ServerLevel world, Map<BiomeInstanceKey, float[][]> cache, String fileName, Gson gson) {
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

            JsonObject obj = new JsonObject();
            obj.add("biome", new JsonPrimitive(key.biomeType().toString()));
            obj.add("pos", AtmosphereUtils.serializeBlockPos(key.samplePos()));
            obj.add("data", arr);

            root.add(key.biomeType().toString() + "@" + key.samplePos().toShortString(), obj);
        }

        try {
            Path p = getPerWorldSavePath(world,fileName);
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                gson.toJson(root, w);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    public static void saveAll1(ServerLevel world, Map<BiomeInstanceKey, float[]> cache, String fileName, Gson gson) {
        JsonObject root = new JsonObject();
        for (var entry : cache.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            float[] week = entry.getValue();

            JsonArray arr = new JsonArray();
            for (float day : week) {
                arr.add(day);
            }

            JsonObject obj = new JsonObject();
            obj.add("biome", new JsonPrimitive(key.biomeType().toString()));
            obj.add("pos", AtmosphereUtils.serializeBlockPos(key.samplePos()));
            obj.add("data", arr);

            root.add(key.biomeType().toString() + "@" + key.samplePos().toShortString(), obj);
        }

        try {
            Path p = getPerWorldSavePath(world,fileName);
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                gson.toJson(root, w);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ---------------------------------------------------------------------
    // Paths
    // ---------------------------------------------------------------------
    public static Path getPerWorldSavePath(ServerLevel world, String fileName) {
        String dimensionPath = world.dimension().location().getNamespace().equals("minecraft")
                ? world.dimension().location().getPath()
                : world.dimension().location().getNamespace() + "_" + world.dimension().location().getPath();

        return world.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve(dimensionPath)
                .resolve("data")
                .resolve("projectatmosphere")
                .resolve(fileName);
    }
}
