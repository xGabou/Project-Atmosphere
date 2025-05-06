// src/main/java/net/Gabou/projectatmosphere/modules/humidity/util/HumidityProfileManager.java
package net.Gabou.projectatmosphere.modules.humidity.util;



import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HumidityProfileManager {
    private static final Map<BiomeInstanceKey, float[][]> WEEKLY = new ConcurrentHashMap<>();
    private static final Map<BiomeInstanceKey, float[]> TOMORROW = new ConcurrentHashMap<>();
    private static final Map<BiomeInstanceKey, float[]> DAILY = new ConcurrentHashMap<>();


    public static void putWeeklyForecast(BiomeInstanceKey id, float[][] week) {
        WEEKLY.put(id, week);
    }
    public static float[][] getWeeklyForecast(BiomeInstanceKey id) {
        return AtmosphereUtils.getRightForecastForBiome(id, WEEKLY) ;
    }


    public static void putDayProfile(BiomeInstanceKey id, float[] profile) {
        DAILY.put(id, profile);
    }
    public static float[] getDayProfile(BiomeInstanceKey id) {
        return AtmosphereUtils.getRightForecastForBiome1(id, DAILY);
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

    public static void putTomorrowProfile(BiomeInstanceKey id, float[] profile) {
        TOMORROW.put(id, profile);
    }
    public static float[] getTomorrowProfile(BiomeInstanceKey id) {
        return AtmosphereUtils.getRightForecastForBiome1(id, TOMORROW);
    }


    public static float getCurrentHumidity(BiomeInstanceKey biome, long tick) {
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

    public static Set<BiomeInstanceKey> getAllBiomeKeys() {
        return WEEKLY.keySet();
    }

    public static void clearAll() {
        WEEKLY.clear();
        TOMORROW.clear();
        DAILY.clear();
    }
}
