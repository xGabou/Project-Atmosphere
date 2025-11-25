package net.Gabou.projectatmosphere.manager;

import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Keeps a pointer to the most relevant BiomeForecast for each BiomeInstanceKey.
 * This avoids O(n) map scans at runtime by allowing direct lookup of the last known forecast.
 */
public final class ForecastPointerRegistry {

    private static final ConcurrentMap<BiomeInstanceKey, BiomeForecast> POINTERS = new ConcurrentHashMap<>();

    /** Assign a forecast pointer for a biome instance. */
    public static void setPointer(BiomeInstanceKey key, BiomeForecast forecast) {
        if (key != null && forecast != null)
            POINTERS.put(key, forecast);
    }

    /** Retrieve the forecast pointer for a biome instance, or null if none exists. */
    public static BiomeForecast getPointer(BiomeInstanceKey key) {
        if (key == null) {
            return null;
        }
        BiomeForecast averageForecast = ForecastGenerator.getAverageForecast(key.biomeType());
        if (averageForecast == null) {
            return POINTERS.get(key);
        }
        return POINTERS.getOrDefault(key, averageForecast);
    }

    /** Check whether a biome already has a forecast pointer assigned. */
    public static boolean hasPointer(BiomeInstanceKey key) {
        return POINTERS.containsKey(key);
    }

    /** Clears all pointers (called when forecasts regenerate). */
    public static void clear() {
        POINTERS.clear();
    }

    private ForecastPointerRegistry() {}
}
