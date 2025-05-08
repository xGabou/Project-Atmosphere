package net.Gabou.projectatmosphere.modules.wind.util;

import com.google.gson.*;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.StorageUtils;
import net.minecraft.core.BlockPos;
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

import static net.Gabou.projectatmosphere.util.StorageUtils.getPerWorldSavePath;

/**
 * Static storage system for 7-day float[2] wind forecasts per biome.
 */
public class WindStorageManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<BiomeInstanceKey, float[]> CACHE = new HashMap<>();
    private static final String FILE_NAME = "wind_forecasts.json";

    public static boolean hasForecast(BiomeInstanceKey biome) {
        BiomeInstanceKey key = AtmosphereUtils.findNearestBiomeInstanceKey(biome,CACHE);
        return key != null && CACHE.containsKey(key);
    }

    public static float[] getForecast(BiomeInstanceKey biome) {
        return AtmosphereUtils.getRightForecastForBiome1(biome,CACHE);
    }

    public static void saveForecast(BiomeInstanceKey biome, float[] week) {
        CACHE.put(biome, week);
    }

    public static void clearCache(ServerLevel world) {
        CACHE.clear();
        StorageUtils.clearCache(world,FILE_NAME);

    }

    public static void saveAll(ServerLevel world) {
        JsonObject root = new JsonObject();

        CACHE.forEach((biomeKey, week) -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("biome", biomeKey.biomeType().toString());
            obj.add("pos", AtmosphereUtils.serializeBlockPos(biomeKey.samplePos()));

            JsonArray arr = new JsonArray();
            for (float day : week) {
                JsonArray pair = new JsonArray();
                pair.add(String.valueOf(day)); // min
                arr.add(pair);
            }

            obj.add("week", arr);
            root.add(biomeKey.toString(), obj);
        });

        try {
            Path path = getPerWorldSavePath(world, FILE_NAME);
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadAll(ServerLevel world) {
        Path path = getPerWorldSavePath(world, FILE_NAME);
        if (!Files.exists(path)) return;

        CACHE.clear();

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject obj = entry.getValue().getAsJsonObject();

                ResourceLocation biomeId = new ResourceLocation(obj.get("biome").getAsString());
                BlockPos pos = AtmosphereUtils.deserializeBlockPos(obj.get("pos").getAsJsonObject());
                BiomeInstanceKey key = new BiomeInstanceKey(biomeId, pos);

                JsonArray arr = obj.getAsJsonArray("week");
                float[]week = new float[7];
                for (int i = 0; i < 7; i++) {
                    JsonArray pair = arr.get(i).getAsJsonArray();
                    week[i] = pair.get(0).getAsFloat();
                }

                CACHE.put(key, week);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static Set<BiomeInstanceKey> getAllBiomeKeys() {
        return CACHE.keySet();
    }
    
}
