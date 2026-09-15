package net.Gabou.projectatmosphere.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BiomeClientTemperatureCache {
    private static volatile Map<ResourceLocation, float[]> DAILY_FORECASTS = new ConcurrentHashMap<>();
    /**
     * Mean of every cached biome forecast, used when a biome has no entry of its
     * own. A biome is missing whenever no loaded region sampled it, which is
     * routine near the edge of the loaded area. The previous fixed 0.5 was worse
     * than useless: it reads as a plausible 0.5 degrees C rather than "unknown",
     * and {@code BiomeFreezingMixin} treats it as just above freezing.
     */
    private static volatile float FALLBACK_CELSIUS = 0.0F;

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
        FALLBACK_CELSIUS = meanOf(next);
    }

    public static void replaceDayForecasts(Map<ResourceLocation, float[]> newData) {
        Map<ResourceLocation, float[]> next = new ConcurrentHashMap<>(newData);
        DAILY_FORECASTS = next;
        FALLBACK_CELSIUS = meanOf(next);
    }

    /**
     * Returns whether the client actually has a forecast for this biome, so
     * callers can distinguish a real reading from the fallback.
     */
    public static boolean hasTemperature(ResourceLocation biome) {
        float[] arr = DAILY_FORECASTS.get(biome);
        return arr != null && arr.length > 0;
    }

    private static float meanOf(Map<ResourceLocation, float[]> data) {
        double sum = 0.0D;
        int count = 0;
        for (float[] arr : data.values()) {
            if (arr == null || arr.length == 0) {
                continue;
            }
            for (float value : arr) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? 0.0F : (float) (sum / count);
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
        if (arr == null || arr.length == 0) return FALLBACK_CELSIUS;
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
        FALLBACK_CELSIUS = 0.0F;
    }
}
