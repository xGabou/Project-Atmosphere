package net.Gabou.projectatmosphere.pression.util;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PressureProfileManager {
    private static final Map<String, double[][]> WEEKLY = new ConcurrentHashMap<>();
    private static final Map<String, double[]> TOMORROW = new ConcurrentHashMap<>();
    private static final Map<String, double[]> DAILY    = new ConcurrentHashMap<>();

    // store a weekly [7]hPa forecast
    public static void putWeeklyForecast(ResourceLocation biome, double[][] weekHpa) {
        WEEKLY.put(biome.toString(), weekHpa);
        // optionally persist...
    }
    public static double[][] getWeeklyForecast(ResourceLocation biome) {
        return WEEKLY.get(biome.toString());
    }

    public static void putTomorrowProfile(ResourceLocation biome, double[] profile) {
        TOMORROW.put(biome.toString(), profile);
    }
    public static double[] getTomorrowProfile(ResourceLocation biome) {
        return TOMORROW.get(biome.toString());
    }

    public static void putDayProfile(ResourceLocation biome, double[] profile) {
        DAILY.put(biome.toString(), profile);
    }
    public static double[] getDayProfile(ResourceLocation biome) {
        return DAILY.get(biome.toString());
    }

    public static Set<String> getAllBiomeKeys() { return DAILY.keySet(); }

    /** returns the instantaneous pressure at tick: from daily if present */
    public static double getCurrentPressure(ResourceLocation biome, long tick) {
        double[] day = getDayProfile(biome);
        if (day != null) {
            int idx = (int)((tick % 24000L) / 100);
            return day[idx];
        }
        return Double.NaN;
    }
}

