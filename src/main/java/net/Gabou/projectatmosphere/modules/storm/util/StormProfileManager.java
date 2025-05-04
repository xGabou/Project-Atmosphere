// net/Gabou/projectatmosphere/storm/util/StormProfileManager.java
package net.Gabou.projectatmosphere.modules.storm.util;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StormProfileManager {
    private static final Map<String,double[]> WEEKLY  = new ConcurrentHashMap<>();
    private static final Map<String,double[]> TOMORROW = new ConcurrentHashMap<>();
    private static final Map<String,double[]> DAILY    = new ConcurrentHashMap<>();

    public static boolean hasWeeklyForecast(ResourceLocation id) {
        return WEEKLY.containsKey(id.toString());
    }
    public static void putWeeklyForecast(ResourceLocation id, double[] week) {
        WEEKLY.put(id.toString(), week);
    }
    public static double[] getWeeklyForecast(ResourceLocation id) {
        return WEEKLY.get(id.toString());
    }

    public static boolean hasDayProfile(ResourceLocation id) {
        return DAILY.containsKey(id.toString());
    }
    public static void putDayProfile(ResourceLocation id, double[] curve) {
        DAILY.put(id.toString(), curve);
    }

    public static boolean hasTomorrowProfile(ResourceLocation id) {
        return TOMORROW.containsKey(id.toString());
    }
    public static void putTomorrowProfile(ResourceLocation id, double[] curve) {
        TOMORROW.put(id.toString(), curve);
    }

    public static double getCurrentSpike(ResourceLocation biome, long tick) {
        double[] curve = DAILY.get(biome.toString());
        if (curve != null) {
            int idx = (int)((tick % 24000L) / 100);
            return curve[idx];
        }
        double[] week = WEEKLY.get(biome.toString());
        if (week != null) {
            int d = (int)((tick/24000L)%7);
            return week[d];
        }
        return 0.0;
    }

    public static void swapTomorrowToToday() {
        for (String key : WEEKLY.keySet()) {
            double[] tom = TOMORROW.remove(key);
            if (tom != null) DAILY.put(key, tom);
        }
    }

    public static void clearAll() {
        WEEKLY.clear();
        TOMORROW.clear();
        DAILY.clear();
    }

    public static Set<String> getAllBiomeKeys() {
        return WEEKLY.keySet();
    }
}
