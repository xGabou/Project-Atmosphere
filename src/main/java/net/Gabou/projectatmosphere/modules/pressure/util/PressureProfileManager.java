package net.Gabou.projectatmosphere.modules.pressure.util;



import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PressureProfileManager {

    private static final Map<BiomeInstanceKey, float[][]> WEEKLY = new HashMap<>();
    private static final Map<BiomeInstanceKey, float[]> TODAY = new HashMap<>();
    private static final Map<BiomeInstanceKey, float[]> TOMORROW = new HashMap<>();

    public static void putWeeklyForecast(BiomeInstanceKey key, float[][] week) {
        WEEKLY.put(key, week);
    }

    public static float[][] getWeeklyForecast(BiomeInstanceKey key) {
        return AtmosphereUtils.getRightForecastForBiome(key, WEEKLY);
    }

    public static void removeWeeklyForecast(BiomeInstanceKey key) {
        WEEKLY.remove(key);
    }

    public static void putDayProfile(BiomeInstanceKey key, float[] curve) {
        TODAY.put(key, curve);
    }

    public static float[] getTodayProfile(BiomeInstanceKey key) {
        return AtmosphereUtils.getRightForecastForBiome1(key, TODAY);
    }

    public static void removeDayProfile(BiomeInstanceKey key) {
        TODAY.remove(key);
    }

    public static void putTomorrowProfile(BiomeInstanceKey key, float[] curve) {
        TOMORROW.put(key, curve);
    }

    public static float[] getTomorrowProfile(BiomeInstanceKey key) {
        return AtmosphereUtils.getRightForecastForBiome1(key, TOMORROW);
    }

    public static void removeTomorrowProfile(BiomeInstanceKey key) {
        TOMORROW.remove(key);
    }

    /** Returns true if the biome has a daily profile. */
    public static boolean hasDayProfile(BiomeInstanceKey biome) {
        BiomeInstanceKey resolved = AtmosphereUtils.findNearestBiomeInstanceKey(biome, TODAY);
        return resolved != null && TODAY.containsKey(resolved);
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


    public static Set<BiomeInstanceKey> getAllBiomeKeys() {
        return WEEKLY.keySet();
    }

    public static void swapTomorrowToToday() {
        for (BiomeInstanceKey key : WEEKLY.keySet()) {
            float[] tom = TOMORROW.remove(key);
            if (tom != null) TODAY.put(key, tom);
        }
    }

    public static void clearAll() {
        WEEKLY.clear();
        TODAY.clear();
        TOMORROW.clear();
    }
}
