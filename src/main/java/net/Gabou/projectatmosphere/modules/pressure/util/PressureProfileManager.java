package net.Gabou.projectatmosphere.modules.pressure.util;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PressureProfileManager {

    private static final Map<String, float[][]> WEEKLY_FORECASTS = new HashMap<>();
    private static final Map<String, float[]> TODAY_PROFILE = new HashMap<>();
    private static final Map<String, float[]> TOMORROW_PROFILE = new HashMap<>();

    public static void putWeeklyForecast(ResourceLocation biome, float[][] week) {
        WEEKLY_FORECASTS.put(biome.toString(), week);
    }

    public static float[][] getWeeklyForecast(ResourceLocation biome) {
        return WEEKLY_FORECASTS.getOrDefault(biome.toString(), new float[7][2]);
    }

    public static void putDayProfile(ResourceLocation biome, float[] profile) {
        TODAY_PROFILE.put(biome.toString(), profile);
    }

    public static void putTomorrowProfile(ResourceLocation biome, float[] profile) {
        TOMORROW_PROFILE.put(biome.toString(), profile);
    }

    public static float[] getTodayProfile(ResourceLocation biome) {
        return TODAY_PROFILE.get(biome.toString());
    }

    public static float[] getTomorrowProfile(ResourceLocation biome) {
        return TOMORROW_PROFILE.get(biome.toString());
    }


    public static boolean hasWeeklyForecast(ResourceLocation biome) {
        return WEEKLY_FORECASTS.containsKey(biome.toString());
    }

    public static void clearAll() {
        WEEKLY_FORECASTS.clear();
        TODAY_PROFILE.clear();
        TOMORROW_PROFILE.clear();
    }

    public static Set<String> getAllBiomeKeys() {
        return TODAY_PROFILE.keySet();
    }
}
