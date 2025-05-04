package net.Gabou.projectatmosphere.modules.wind.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

public class WindProfileManager {

    private static final Map<ResourceLocation, float[][]> WEEKLY = new HashMap<>();
    private static final Map<ResourceLocation, float[]> TODAY = new HashMap<>();
    private static final Map<ResourceLocation, float[]> TOMORROW = new HashMap<>();

    public static void putWeeklyForecast(ResourceLocation biome, float[][] week) {
        WEEKLY.put(biome, week);
    }

    public static boolean hasWeeklyForecast(ResourceLocation biome) {
        return WEEKLY.containsKey(biome);
    }

    public static float[][] getWeeklyForecast(ResourceLocation biome) {
        return WEEKLY.getOrDefault(biome, new float[7][2]);
    }

    public static void putDayProfile(ResourceLocation biome, float[] profile) {
        TODAY.put(biome, profile);
    }

    public static void putTomorrowProfile(ResourceLocation biome, float[] profile) {
        TOMORROW.put(biome, profile);
    }

    public static float[] getTodayProfile(ResourceLocation biome) {
        return TODAY.get(biome);
    }

    public static float[] getTomorrowProfile(ResourceLocation biome) {
        return TOMORROW.get(biome);
    }

    public static void swapToTomorrow() {
        TODAY.clear();
        TODAY.putAll(TOMORROW);
        TOMORROW.clear();
    }

    public static float getCurrentWindSpeed(ResourceLocation biome, long worldTick) {
        float[] profile = getTodayProfile(biome);
        if (profile == null) return 0f;

        float min = profile[0];
        float max = profile[1];
        float t = (worldTick % 24000L) / 24000f;  // 0 → 1 over a full Minecraft day

        return min + (max - min) * t;
    }

    public static void generateTodayAndTomorrowProfiles(ServerLevel world) {
        for (Map.Entry<ResourceLocation, float[][]> entry : WEEKLY.entrySet()) {
            ResourceLocation biome = entry.getKey();
            float[][] week = entry.getValue();

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
}
