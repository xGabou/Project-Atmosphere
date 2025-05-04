// src/main/java/net/Gabou/projectatmosphere/modules/pressure/util/PressureProfileManager.java
package net.Gabou.projectatmosphere.modules.pressure.util;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PressureProfileManager {
    private static final Map<String, double[][]> WEEKLY  = new ConcurrentHashMap<>();
    private static final Map<String, double[]>   TOMORROW = new ConcurrentHashMap<>();
    private static final Map<String, double[]>   DAILY    = new ConcurrentHashMap<>();

    public static void putWeeklyForecast(ResourceLocation biome, double[][] weekHpa) {
        WEEKLY.put(biome.toString(), weekHpa);
    }
    public static double[][] getWeeklyForecast(ResourceLocation biome) {
        return WEEKLY.get(biome.toString());
    }
    public static boolean hasWeeklyForecast(ResourceLocation biome) {
        return WEEKLY.containsKey(biome.toString());
    }

    public static void putTomorrowProfile(ResourceLocation biome, double[] profile) {
        TOMORROW.put(biome.toString(), profile);
    }
    public static double[] getTomorrowProfile(ResourceLocation biome) {
        return TOMORROW.get(biome.toString());
    }
    public static boolean hasTomorrowProfile(ResourceLocation biome) {
        return TOMORROW.containsKey(biome.toString());
    }

    public static void putDayProfile(ResourceLocation biome, double[] profile) {
        DAILY.put(biome.toString(), profile);
    }
    public static double[] getDayProfile(ResourceLocation biome) {
        return DAILY.get(biome.toString());
    }
    public static boolean hasDayProfile(ResourceLocation biome) {
        return DAILY.containsKey(biome.toString());
    }

    public static Set<String> getAllBiomeKeys() {
        return DAILY.keySet();
    }

    public static void clearAll() {
        WEEKLY.clear();
        TOMORROW.clear();
        DAILY.clear();
    }

    /** Returns the instantaneous pressure at this tick, using daily if present else weekly midday. */
    public static double getCurrentPressure(ResourceLocation biome, long tick) {
        double[] day = DAILY.get(biome.toString());
        if (day != null) {
            int idx = (int)((tick % 24000L) / 100);
            return day[idx];
        }
        double[][] week = WEEKLY.get(biome.toString());
        if (week != null) {
            int d = (int)((tick / 24000L) % 7);
            return week[d][1];
        }
        return Double.NaN;
    }
}
