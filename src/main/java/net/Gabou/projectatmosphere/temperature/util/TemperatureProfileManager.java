package net.Gabou.projectatmosphere.temperature.util;

import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class TemperatureProfileManager {

    private static final Map<String, float[][]> WEEKLY = new ConcurrentHashMap<>();
    private static final Map<String, float[]> DAILY = new ConcurrentHashMap<>();

    /** Stores the weekly [7][2] min/max forecast and persists it. */
    public static void putWeeklyForecast(ResourceLocation biome, float[][] week) {
        WEEKLY.put(biome.toString(), week);
        ForecastStorageManager.saveForecast(biome, week);
    }

    /** Returns the cached 7×2 forecast for a biome. */
    public static float[][] getWeeklyForecast(ResourceLocation biome) {
        return WEEKLY.get(biome.toString());
    }

    /** Stores the 240-entry daily temperature profile (every 100 ticks). */
    public static void putDayProfile(ResourceLocation biome, float[] profile) {
        DAILY.put(biome.toString(), profile);
    }

    /** Returns the 240-entry daily profile for a biome, or null if none exists. */
    public static float[] getDayProfile(ResourceLocation biome) {
        return DAILY.get(biome.toString());
    }

    /**
     * Returns the temperature at this tick (0–23999) for a biome:
     *   – from the DAILY profile if present,
     *   – otherwise midday (index [day][1]) from the WEEKLY forecast.
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
