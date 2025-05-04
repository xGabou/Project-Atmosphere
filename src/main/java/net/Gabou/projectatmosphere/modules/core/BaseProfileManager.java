package net.Gabou.projectatmosphere.modules.core;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base for your “profile manager” classes (TemperatureProfileManager,
 * PressureProfileManager, StormProfileManager, HumidityProfileManager).
 *
 * @param <D>  the per-day curve type (float[] or double[])
 * @param <W>  the weekly min/max type (float[][] or double[])
 */
public abstract class BaseProfileManager<D,W> {
    private final Map<String,W> WEEKLY   = new ConcurrentHashMap<>();
    private final Map<String,D> TOMORROW = new ConcurrentHashMap<>();
    private final Map<String,D> DAILY    = new ConcurrentHashMap<>();

    public D getDayProfile(ResourceLocation biome)     { return DAILY.get(biome.toString()); }
    public D getTomorrowProfile(ResourceLocation biome){ return TOMORROW.get(biome.toString()); }
    public W getWeeklyForecast(ResourceLocation biome) { return WEEKLY.get(biome.toString()); }

    public void putDayProfile(ResourceLocation biome, D profile)      { DAILY.put(biome.toString(), profile); }
    public void putTomorrowProfile(ResourceLocation biome, D profile) { TOMORROW.put(biome.toString(), profile); }
    public void putWeeklyForecast(ResourceLocation biome, W week) {
        WEEKLY.put(biome.toString(), week);
        saveForecast(biome, week);
    }

    public boolean hasDayProfile(ResourceLocation biome)     { return DAILY.containsKey(biome.toString()); }
    public boolean hasTomorrowProfile(ResourceLocation biome){ return TOMORROW.containsKey(biome.toString()); }
    public boolean hasWeeklyForecast(ResourceLocation biome) { return WEEKLY.containsKey(biome.toString()); }

    public void clearDayProfile(ResourceLocation biome)   { DAILY.remove(biome.toString()); }
    public void clearWeeklyForecast(ResourceLocation biome){ WEEKLY.remove(biome.toString()); }
    public void clearAll() {
        DAILY.clear();
        TOMORROW.clear();
        WEEKLY.clear();
    }

    public Set<String> getAllBiomeKeys() { return DAILY.keySet(); }

    /**
     * Hook to persist weekly forecasts (called by putWeeklyForecast).
     * Each subclass should call its StorageManager.
     */
    protected abstract void saveForecast(ResourceLocation biome, W week);

    /**
     * “Current” at tick 0–23999; subclasses must define how to sample
     * from DAILY or WEEKLY if DAILY is missing.
     */
    public abstract double getCurrent(ResourceLocation biome, long tick);
}
