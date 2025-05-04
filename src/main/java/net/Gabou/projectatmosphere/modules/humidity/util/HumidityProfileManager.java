// src/main/java/net/Gabou/projectatmosphere/modules/humidity/util/HumidityProfileManager.java
package net.Gabou.projectatmosphere.modules.humidity.util;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HumidityProfileManager {
    private static final Map<String, float[][]> WEEKLY = new ConcurrentHashMap<>();
    private static final Map<String, float[]> TOMORROW = new ConcurrentHashMap<>();
    private static final Map<String, float[]> DAILY = new ConcurrentHashMap<>();

    public static boolean hasWeeklyForecast(ResourceLocation id) {
        return WEEKLY.containsKey(id.toString());
    }
    public static void putWeeklyForecast(ResourceLocation id, float[][] week) {
        WEEKLY.put(id.toString(), week);
    }
    public static float[][] getWeeklyForecast(ResourceLocation id) {
        return WEEKLY.get(id.toString());
    }

    public static boolean hasDayProfile(ResourceLocation id) {
        return DAILY.containsKey(id.toString());
    }
    public static void putDayProfile(ResourceLocation id, float[] profile) {
        DAILY.put(id.toString(), profile);
    }
    public static float[] getDayProfile(ResourceLocation id) {
        return DAILY.get(id.toString());
    }

    public static boolean hasTomorrowProfile(ResourceLocation id) {
        return TOMORROW.containsKey(id.toString());
    }
    public static void putTomorrowProfile(ResourceLocation id, float[] profile) {
        TOMORROW.put(id.toString(), profile);
    }
    public static float[] getTomorrowProfile(ResourceLocation id) {
        return TOMORROW.get(id.toString());
    }


    public static float getCurrentHumidity(ResourceLocation biome, long tick) {
        float[] day = getDayProfile(biome);
        if (day != null) {
            int idx = (int)((tick % 24000L) / 100);
            return day[idx];
        }
        float[][] week = getWeeklyForecast( biome);
        if (week != null) {
            int dayIndex = (int)((tick / 24000L) % 7);
            return week[dayIndex][1]; // midday fallback
        }
        return Float.NaN;
    }

    public static Set<String> getAllBiomeKeys() {
        return WEEKLY.keySet();
    }

    public static void clearAll() {
        WEEKLY.clear();
        TOMORROW.clear();
        DAILY.clear();
    }
}
