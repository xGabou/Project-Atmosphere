package net.Gabou.projectatmosphere.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BiomeClientTemperatureCache {
    private static volatile Map<ResourceLocation, float[]> DAILY_FORECASTS = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------------
    // Cache updates
    // ---------------------------------------------------------------------
    /**
     * Updates the client-side forecast cache with the new per-biome daily temperature arrays.
     */
    public static void updateDayForecasts(Map<ResourceLocation, float[]> newData) {
        Map<ResourceLocation, float[]> next = new ConcurrentHashMap<>(newData.size());
        next.putAll(newData);
        DAILY_FORECASTS = next;
    }

    public static void replaceDayForecasts(Map<ResourceLocation, float[]> newData) {
        DAILY_FORECASTS = new ConcurrentHashMap<>(newData);
    }

    // ---------------------------------------------------------------------
    // Lookups
    // ---------------------------------------------------------------------
    /**
     * Retrieves the forecasted temperature for a biome at the given tick of the current day (0-23999).
     * Converts the tick value into an index for the forecast array.
     *
     * @param biome The biome ID.
     * @param level The current client level.
     * @return Forecasted temperature for that biome and time of day.
     */
    public static float getTemperature(ResourceLocation biome, Level level) {
        float[] arr = DAILY_FORECASTS.get(biome);
        if (arr == null || arr.length == 0) return 0.5F;
        if (level == null) return arr[0];

        // Convert in-game time (ticks) -> array index
        long timeOfDay = level.getDayTime() % 24000L;
        int i = (int) ((timeOfDay / 24000.0F) * arr.length);

        // Clamp to valid range
        i = Math.max(0, Math.min(i, arr.length - 1));
        return arr[i];
    }

    /**
     * Determines if the biome is freezing at the current in-game tick.
     *
     * @param biome The biome ID.
     * @param level The client level.
     * @return True if freezing (temperature < 0.0F)
     */
    public static boolean isFreezing(ResourceLocation biome, Level level) {
        return getTemperature(biome, level) < 0.0F;
    }

    // ---------------------------------------------------------------------
    // Reset
    // ---------------------------------------------------------------------
    /**
     * Clears all cached forecast data.
     */
    public static void clear() {
        DAILY_FORECASTS = new ConcurrentHashMap<>();
    }
}
