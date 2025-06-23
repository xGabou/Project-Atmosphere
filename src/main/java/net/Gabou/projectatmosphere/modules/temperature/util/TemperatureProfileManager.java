package net.Gabou.projectatmosphere.modules.temperature.util;

import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class TemperatureProfileManager {

    private static final Map<BiomeInstanceKey, float[][]> WEEKLY = new ConcurrentHashMap<>();
    private static final Map<BiomeInstanceKey, float[]> TOMORROW = new ConcurrentHashMap<>();
    private static final Map<BiomeInstanceKey, float[]> DAILY = new ConcurrentHashMap<>();



    /** Returns the 240-entry daily profile for a biome, or null if none exists. */
    public static float[] getDayProfile(BiomeInstanceKey biome) {
        return AtmosphereUtils.getRightForecastForBiome1(biome,DAILY);
    }

    /** Internal: retrieve tomorrow’s curve. */
    public static float[] getTomorrowProfile(BiomeInstanceKey biome) {
        return AtmosphereUtils.getRightForecastForBiome1(biome,TOMORROW);
    }

    /** Returns the cached 7×2 forecast for a biome. */
    public static float[][] getWeeklyForecast(BiomeInstanceKey biome) {
        return AtmosphereUtils.getRightForecastForBiome(biome,WEEKLY);
    }

    /** Stores the 240-entry daily temperature profile (every 100 ticks). */
    public static void putDayProfile(BiomeInstanceKey biome, float[] profile) {
        DAILY.put(biome, profile);
    }

    /** Store tomorrow’s 240-step curve. */
    public static void putTomorrowProfile(BiomeInstanceKey biome, float[] profile) {
        TOMORROW.put(biome, profile);
    }

    /** Stores the weekly [7][2] min/max forecast and persists it. */
    public static void putWeeklyForecast(BiomeInstanceKey biome, float[][] week) {
        WEEKLY.put(biome, week);
        ForecastStorageManager.saveForecast(biome, week);
    }

    /** Returns true if the biome has a daily profile. */
    public static boolean hasDayProfile(BiomeInstanceKey biome) {
        BiomeInstanceKey resolved = AtmosphereUtils.findNearestBiomeInstanceKey(biome, DAILY);
        return resolved != null && DAILY.containsKey(resolved);
    }

    /** Returns true if the biome has a tomorrow profile. */
    public static boolean hasTomorrowProfile(BiomeInstanceKey biome) {
        BiomeInstanceKey resolved = AtmosphereUtils.findNearestBiomeInstanceKey(biome, TOMORROW);
        return resolved != null && TOMORROW.containsKey(resolved);
    }

    /** Returns true if the biome has a weekly forecast. */
    public static boolean hasWeeklyForecast(BiomeInstanceKey biome) {
        BiomeInstanceKey resolved = AtmosphereUtils.findNearestBiomeInstanceKey(biome, WEEKLY);
        return resolved != null && WEEKLY.containsKey(resolved);
    }




    /** Clears the daily profile for a biome. */
    static void clearDayProfile(BiomeInstanceKey biome) {
        DAILY.remove(biome);
    }
    /** Clears the weekly forecast for a biome. */
    static void clearWeeklyForecast(BiomeInstanceKey biome) {
        WEEKLY.remove(biome);
    }
    /** Clears both today & tomorrow curves. */
    public static void clearAll() {
        DAILY.clear();
        TOMORROW.clear();
        WEEKLY.clear();
    }

    public static Map<BiomeInstanceKey, float[]> getDayProfile() {
        return DAILY;
    }


    public static Set<BiomeInstanceKey> getAllBiomeKeys() {
        return DAILY.keySet();
    }

    /**
     * Returns the temperature at this tick (0–23999) for a biome:
     *   – from the DAILY profile if present,
     *   – otherwise midday> (index [day][1]) from the WEEKLY forecast.
     */
    public static float getCurrentTemperature(BiomeInstanceKey biome, long tick) {
        // Try daily curve first
        biome = AtmosphereUtils.findNearestBiomeInstanceKey(biome,DAILY);
        float[] day = DAILY.get(biome);
        if (day != null) {
            int idx = (int)((tick % 24000L) / 100);
            return day[idx];
        }

        // Fallback to weekly midday temperature
        float[][] week = WEEKLY.get(biome);
        if (week != null) {
            int dayIndex = (int)((tick / 24000L) % 7);
            return week[dayIndex][1];
        }

        return Float.NaN;
    }
}
