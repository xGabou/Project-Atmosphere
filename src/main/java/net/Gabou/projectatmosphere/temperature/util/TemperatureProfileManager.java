package net.Gabou.projectatmosphere.temperature.util;

import net.minecraft.resources.ResourceLocation;
import java.util.concurrent.ConcurrentHashMap;

public class TemperatureProfileManager {

    private static final ConcurrentHashMap<String, float[][]> WEEKLY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, float[]> DAILY = new ConcurrentHashMap<>();

    public static void putWeeklyForecast(ResourceLocation biome, float[][] week) {
        WEEKLY.put(biome.toString(), week);
        ForecastStorageManager.putForecast(biome, week);
    }

    public static float[][] getWeeklyForecast(ResourceLocation biome) {
        return WEEKLY.get(biome.toString());
    }

    public static void putDayProfile(ResourceLocation biome, float[] dayProfile) {
        DAILY.put(biome.toString(), dayProfile);
    }

    public static float getCurrentTemperature(ResourceLocation biome, long worldTick) {
        float[] dayProfile = DAILY.get(biome.toString());
        if (dayProfile == null) return Float.NaN;
        int idx = (int)((worldTick % 24000) / 100); // 100 ticks per entry
        return dayProfile[idx];
    }
}
