package net.Gabou.projectatmosphere.temperature.util;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class TemperatureProfileManager {

    private static final Map<String, float[][]> WEEKLY = new ConcurrentHashMap<>();
    private static final Map<String, float[]> TOMORROW = new ConcurrentHashMap<>();
    private static final Map<String, float[]> DAILY = new ConcurrentHashMap<>();



    /** Returns the 240-entry daily profile for a biome, or null if none exists. */
    public static float[] getDayProfile(ResourceLocation biome) {
        return DAILY.get(biome.toString());
    }

    /** Internal: retrieve tomorrow’s curve. */
    public static float[] getTomorrowProfile(ResourceLocation biome) {
        return TOMORROW.get(biome.toString());
    }

    /** Returns the cached 7×2 forecast for a biome. */
    public static float[][] getWeeklyForecast(ResourceLocation biome) {
        return WEEKLY.get(biome.toString());
    }

    /** Stores the 240-entry daily temperature profile (every 100 ticks). */
    public static void putDayProfile(ResourceLocation biome, float[] profile) {
        DAILY.put(biome.toString(), profile);
    }

    /** Store tomorrow’s 240-step curve. */
    public static void putTomorrowProfile(ResourceLocation biome, float[] profile) {
        TOMORROW.put(biome.toString(), profile);
    }

    /** Stores the weekly [7][2] min/max forecast and persists it. */
    public static void putWeeklyForecast(ResourceLocation biome, float[][] week) {
        WEEKLY.put(biome.toString(), week);
        ForecastStorageManager.saveForecast(biome, week);
    }

    /** Returns true if the biome has a daily profile. */
    public static boolean hasDayProfile(ResourceLocation biome) {
        return DAILY.containsKey(biome.toString());
    }

    /** Returns true if the biome has a tomorrow profile. */
    public static boolean hasTomorrowProfile(ResourceLocation biome) {
        return TOMORROW.containsKey(biome.toString());
    }

    /** Returns true if the biome has a weekly forecast. */
    public static boolean hasWeeklyForecast(ResourceLocation biome) {
        return WEEKLY.containsKey(biome.toString());
    }


    /** Clears the daily profile for a biome. */
    static void clearDayProfile(ResourceLocation biome) {
        DAILY.remove(biome.toString());
    }
    /** Clears the weekly forecast for a biome. */
    static void clearWeeklyForecast(ResourceLocation biome) {
        WEEKLY.remove(biome.toString());
    }
    /** Clears both today & tomorrow curves. */
    public static void clearAll() {
        DAILY.clear();
        TOMORROW.clear();
        WEEKLY.clear();
    }


    public static Set<String> getAllBiomeKeys() {
        return DAILY.keySet();
    }

    /**
     * Returns the temperature at this tick (0–23999) for a biome:
     *   – from the DAILY profile if present,
     *   – otherwise midday> (index [day][1]) from the WEEKLY forecast.
     */
    public static float getCurrentTemperature(ResourceLocation biome, long tick) {
        // Try daily curve first
        float[] day = DAILY.get(biome.toString());
        if (day != null) {
            int idx = (int)((tick % 24000L) / 100);
            return day[idx];
        }

        // Fallback to weekly midday temperature
        float[][] week = WEEKLY.get(biome.toString());
        if (week != null) {
            int dayIndex = (int)((tick / 24000L) % 7);
            return week[dayIndex][1];
        }

        return Float.NaN;
    }
}
