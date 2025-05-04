package net.Gabou.projectatmosphere.modules.core;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * Base for any “raw” generator that produces a weekly forecast for one biome—
 * e.g. TemperatureGenerator, PressureGenerator, StormSpikeGenerator, etc.
 * @param <T>  the type of your 7‐day forecast (float[][], double[], etc)
 */
public abstract class BaseWeeklyGenerator<T> {
    /**
     * Produce one biome’s raw 7-day forecast.
     * Called by your ForecastGenerator after sampling biomes.
     */
    public abstract T generateWeekForecast(
            ServerLevel world, BlockPos samplePos, ResourceLocation biome);
}
