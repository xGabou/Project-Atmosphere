package net.Gabou.projectatmosphere.modules.storm.forecast;

import net.Gabou.projectatmosphere.modules.storm.util.StormStorageManager;
import net.Gabou.projectatmosphere.modules.storm.util.StormGenerator;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class StormForecast {

    /**
     * Generates or loads weekly storm forecasts around the center position.
     * Used on world load or precompute pass.
     */
    public static Map<ResourceLocation, double[]> generateStormForecastAround(ServerLevel world,
                                                                              BlockPos center,
                                                                              int radiusBlocks) {
        if (!world.dimension().equals(Level.OVERWORLD)) return Map.of();

        Map<ResourceLocation, BlockPos> samples = AtmosphereUtils.findBiomes(world, center, radiusBlocks);
        Map<ResourceLocation, double[]> forecast = new HashMap<>();

        for (var entry : samples.entrySet()) {
            ResourceLocation biome = entry.getKey();
            BlockPos pos = entry.getValue();

            double[] week;
            if (StormStorageManager.hasForecast(biome)) {
                week = StormStorageManager.getForecast(biome);
            } else {
                week = StormGenerator.generateWeeklyStormProfile(world, pos, biome);
                StormStorageManager.saveForecast(biome, week);
            }

            forecast.put(biome, week);
        }

        return forecast;
    }

    /**
     * Same as above, but generates and returns values without saving.
     * Useful for temporary visualizations or comparisons.
     */
    public static Map<ResourceLocation, double[]> generateTemporaryStormForecastAround(ServerLevel world,
                                                                                       BlockPos center,
                                                                                       int radiusBlocks) {
        Map<ResourceLocation, BlockPos> samples = AtmosphereUtils.findBiomes(world, center, radiusBlocks);
        Map<ResourceLocation, double[]> forecast = new HashMap<>();

        for (var entry : samples.entrySet()) {
            ResourceLocation biome = entry.getKey();
            BlockPos pos = entry.getValue();
            double[] week = StormGenerator.generateWeeklyStormProfile(world, pos, biome);
            forecast.put(biome, week);
        }

        return forecast;
    }
}
