package net.Gabou.projectatmosphere.modules.pressure.util;

import net.Gabou.projectatmosphere.modules.pressure.forecast.PressureForecast.BiomeInstanceKey;

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
        return WEEKLY.get(key);
    }

    public static void removeWeeklyForecast(BiomeInstanceKey key) {
        WEEKLY.remove(key);
    }

    public static void putDayProfile(BiomeInstanceKey key, float[] curve) {
        TODAY.put(key, curve);
    }

    public static float[] getTodayProfile(BiomeInstanceKey key) {
        return TODAY.get(key);
    }

    public static void removeDayProfile(BiomeInstanceKey key) {
        TODAY.remove(key);
    }

    public static void putTomorrowProfile(BiomeInstanceKey key, float[] curve) {
        TOMORROW.put(key, curve);
    }

    public static float[] getTomorrowProfile(BiomeInstanceKey key) {
        return TOMORROW.get(key);
    }

    public static void removeTomorrowProfile(BiomeInstanceKey key) {
        TOMORROW.remove(key);
    }

    public static boolean hasWeeklyForecast(BiomeInstanceKey key) {
        return WEEKLY.containsKey(key);
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
