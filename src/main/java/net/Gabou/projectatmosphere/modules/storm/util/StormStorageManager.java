// net/Gabou/projectatmosphere/modules/storm/util/StormStorageManager.java
package net.Gabou.projectatmosphere.modules.storm.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;  // :contentReference[oaicite:5]{index=5}
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

public class StormStorageManager {
    private static final Gson GSON = new Gson();
    private static final Map<String, double[]> cache = new ConcurrentHashMap<>();
    public static final String FILE_NAME = "storm_forecasts.json";

    public static void loadAll(ServerLevel world) {
        Path path = AtmosphereUtils.getPerWorldSavePath(world, FILE_NAME);
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : root.entrySet()) {
                JsonArray arr = entry.getValue().getAsJsonArray();
                double[] week = new double[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    week[i] = arr.get(i).getAsDouble();
                }
                cache.put(entry.getKey(), week);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveForecast(ResourceLocation biome, double[] week) {
        cache.put(biome.toString(), week);
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

    public static void clearCache(ServerLevel world) {
        cache.clear();
        try {
            Files.deleteIfExists(getPerWorldSavePath(world, FILE_NAME));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveSamplePosition(ResourceLocation biome, BlockPos pos) {
        // no-op
    }

    public static void saveAll(ServerLevel world) {
        Path path = AtmosphereUtils.getPerWorldSavePath(world, FILE_NAME);
        JsonObject root = new JsonObject();
        for (var entry : cache.entrySet()) {
            JsonArray arr = new JsonArray();
            for (double d : entry.getValue()) {
                arr.add(d);
            }
            root.add(entry.getKey(), arr);
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
