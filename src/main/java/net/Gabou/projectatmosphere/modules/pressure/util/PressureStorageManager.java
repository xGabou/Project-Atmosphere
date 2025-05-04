package net.Gabou.projectatmosphere.modules.pressure.util;

import com.google.gson.*;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
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
 * Static manager for storing float[7][2] pressure forecasts per biome.
 */
public class PressureStorageManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, float[][]> CACHE = new HashMap<>();
    private static final String FILE_NAME = "pressure_forecasts.json";

    public static boolean hasForecast(ResourceLocation biome) {
        return CACHE.containsKey(biome.toString());
    }

    public static float[][] getForecast(ResourceLocation biome) {
        return CACHE.getOrDefault(biome.toString(), new float[7][2]);
    }

    public static void saveForecast(ResourceLocation biome, float[][] week) {
        CACHE.put(biome.toString(), week);
    }

    public static void clearCache(ServerLevel world) {
        CACHE.clear();
        try {
            Files.deleteIfExists(getSavePath(world));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveAll(ServerLevel world) {
        JsonObject root = new JsonObject();
        CACHE.forEach((biome, week) -> {
            JsonArray arr = new JsonArray();
            for (float[] day : week) {
                JsonArray pair = new JsonArray();
                pair.add(day[0]); // min
                pair.add(day[1]); // max
                arr.add(pair);
            }
            root.add(biome, arr);
        });

        try {
            Path path = getSavePath(world);
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(root, w);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void loadAll(ServerLevel world) {
        Path path = getSavePath(world);
        if (!Files.exists(path)) return;

        CACHE.clear();

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonArray arr = e.getValue().getAsJsonArray();
                float[][] week = new float[7][2];
                for (int i = 0; i < 7; i++) {
                    JsonArray pair = arr.get(i).getAsJsonArray();
                    week[i][0] = pair.get(0).getAsFloat();
                    week[i][1] = pair.get(1).getAsFloat();
                }
                CACHE.put(e.getKey(), week);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static Set<String> getAllBiomeKeys() {
        return CACHE.keySet();
    }

    private static Path getSavePath(ServerLevel world) {
        return AtmosphereUtils.getPerWorldSavePath(world, FILE_NAME);
    }
}
