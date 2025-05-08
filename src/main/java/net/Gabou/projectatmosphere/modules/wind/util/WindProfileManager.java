package net.Gabou.projectatmosphere.modules.wind.util;

import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class WindProfileManager {

    private static final Map<BiomeInstanceKey, float[]> WEEKLY = new HashMap<>();
    private static final Map<BiomeInstanceKey, Float> TODAY = new HashMap<>();
    private static final Map<BiomeInstanceKey, Float> TOMORROW = new HashMap<>();

    public static void putWeeklyForecast(BiomeInstanceKey biome, float[] week) {
        WEEKLY.put(biome, week);
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

    public static float[] getWeeklyForecast(BiomeInstanceKey biome) {
        return AtmosphereUtils.getRightForecastForBiome1(biome,WEEKLY);
    }

    public static void putDayProfile(BiomeInstanceKey biome, Float profile) {
        TODAY.put(biome, profile);
    }

    public static void putTomorrowProfile(BiomeInstanceKey biome, Float profile) {
        TOMORROW.put(biome, profile);
    }

    public static float getTodayProfile(BiomeInstanceKey biome) {
        return AtmosphereUtils.getRightForecastForBiome2(biome,TODAY);
    }

    public static float getTomorrowProfile(BiomeInstanceKey biome) {
        return AtmosphereUtils.getRightForecastForBiome2(biome,TOMORROW);
    }


    public static float getCurrentWindSpeed(BiomeInstanceKey biome, long worldTick) {
        float profile = getTodayProfile(biome);

        float t = (worldTick % 24000L) / 24000f;  // 0 → 1 over a full Minecraft day
        return profile +  profile * t;
    }

    public static void generateTodayAndTomorrowProfiles(ServerLevel world) {
        for (Map.Entry<BiomeInstanceKey, float[]> entry : WEEKLY.entrySet()) {
            BiomeInstanceKey biome = entry.getKey();
            float[] week = entry.getValue();

            long day = world.getDayTime() / 24000L;
            int todayIndex = (int)(day % 7);
            int tomorrowIndex = (int)((day + 1) % 7);

            TODAY.put(biome, week[todayIndex]);
            TOMORROW.put(biome, week[tomorrowIndex]);
        }
    }

    public static void clearAll() {
        WEEKLY.clear();
        TODAY.clear();
        TOMORROW.clear();
    }

    public static Set<BiomeInstanceKey> getAllBiomeKeys() {
        return TODAY.keySet();
    }
}
