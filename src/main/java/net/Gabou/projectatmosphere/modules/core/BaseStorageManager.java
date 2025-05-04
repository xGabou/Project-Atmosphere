// src/main/java/net/Gabou/projectatmosphere/modules/core/BaseStorageManager.java
package net.Gabou.projectatmosphere.modules.core;

import com.google.gson.*;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A generic JSON‐backed per‐world storage manager for weekly forecasts.
 * @param <T> the forecast type (e.g. float[][] or double[])
 */
public abstract class BaseStorageManager<T> {
    private final Map<String, T> cache = new ConcurrentHashMap<>();
    private final Gson GSON = new Gson();

    /** Subclasses must point this at the right JSON file name. */
    protected abstract Path getSavePath(ServerLevel world);

    /** Parse one entry’s JsonElement into T. */
    protected abstract T parseForecast(JsonElement element);

    /** Serialize one entry of T back into JsonElement. */
    protected abstract JsonElement serializeForecast(T forecast);

    /** Load everything from disk into memory. */
    public void loadAll(ServerLevel world) {
        Path path = getSavePath(world);
        if (!Files.exists(path)) return;
        try (Reader r = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (var e : root.entrySet()) {
                cache.put(e.getKey(), parseForecast(e.getValue()));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /** Save or update one biome’s forecast, then persist all. */
    public void saveForecast(ServerLevel world, ResourceLocation biome, T week) {
        cache.put(biome.toString(), week);
        saveAll(world);
    }

    /** @return true if we have a cached forecast for this biome. */
    public boolean hasForecast(ResourceLocation biome) {
        return cache.containsKey(biome.toString());
    }

    /** @return the cached forecast, or null if none. */
    public T getForecast(ResourceLocation biome) {
        return cache.get(biome.toString());
    }

    /** @return all biome keys currently cached. */
    public Set<String> getAllBiomeKeys() {
        return cache.keySet();
    }

    /** Drop everything from memory (won’t delete disk). */
    public void clearCache() {
        cache.clear();
    }

    /** Optional hook to record sample positions. */
    public void saveSamplePosition(ResourceLocation biome, BlockPos pos) {
        // no-op unless overridden
    }

    /** Write the entire cache back out to disk. */
    private void saveAll(ServerLevel world) {
        Path path = getSavePath(world);
        JsonObject root = new JsonObject();
        for (var e : cache.entrySet()) {
            root.add(e.getKey(), serializeForecast(e.getValue()));
        }
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(root, w);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
