// net/Gabou/projectatmosphere/storm/util/StormProfileManager.java
package net.Gabou.projectatmosphere.modules.storm.util;

import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;


import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StormProfileManager {
    private static final Map<BiomeInstanceKey,float[]> WEEKLY  = new ConcurrentHashMap<>();
    private static final Map<BiomeInstanceKey,float[]> TOMORROW = new ConcurrentHashMap<>();
    private static final Map<BiomeInstanceKey,float[]> DAILY    = new ConcurrentHashMap<>();


    public static void putWeeklyForecast(BiomeInstanceKey id, float[] week) {
        WEEKLY.put(id, week);
    }
    public static float[] getWeeklyForecast(BiomeInstanceKey id) {
        return AtmosphereUtils.getRightForecastForBiome1(id,WEEKLY);
    }

    /** Returns today's storm intensity for a biome */
    public static float getCurrentStormIntensity(BiomeInstanceKey biome, long worldTick) {
        float[] forecast = getWeeklyForecast(biome);
        if (forecast.length == 0) return 0.0f;

        int dayIndex = (int)((worldTick / 24000L) % 7);
        return forecast[Math.min(dayIndex, forecast.length - 1)];
    }


    public static void putDayProfile(BiomeInstanceKey id, float[] curve) {
        DAILY.put(id, curve);
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

    public static void putTomorrowProfile(BiomeInstanceKey id, float[] curve) {
        TOMORROW.put(id, curve);
    }

    public static float[] getDayProfile(BiomeInstanceKey id) {
        return AtmosphereUtils.getRightForecastForBiome1(id,DAILY);
    }
    public static float[] getTomorrowProfile(BiomeInstanceKey id) {
        return AtmosphereUtils.getRightForecastForBiome1(id,TOMORROW);
    }


    public static float getCurrentSpike(BiomeInstanceKey biome, long tick) {
        float[] curve = getDayProfile(biome);
        if (curve != null) {
            int idx = (int)((tick % 24000L) / 100);
            return curve[idx];
        }
        float[] week = getWeeklyForecast(biome);
        if (week != null) {
            int d = (int)((tick/24000L)%7);
            return week[d];
        }
        return 0.0f;
    }

    public static void swapTomorrowToToday() {
        for (BiomeInstanceKey key : WEEKLY.keySet()) {
            float[] tom = TOMORROW.remove(key);
            if (tom != null) DAILY.put(key, tom);
        }
    }

    public static void clearAll() {
        WEEKLY.clear();
        TOMORROW.clear();
        DAILY.clear();
    }

    public static Set<BiomeInstanceKey> getAllBiomeKeys() {
        return WEEKLY.keySet();
    }
}
