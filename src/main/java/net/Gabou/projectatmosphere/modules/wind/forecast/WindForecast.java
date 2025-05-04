package net.Gabou.projectatmosphere.modules.wind.forecast;

import net.Gabou.projectatmosphere.modules.wind.util.WindStorageManager;
import net.Gabou.projectatmosphere.modules.wind.util.WindGenerator;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

public class WindForecast {

    public static Map<ResourceLocation, float[][]> generateForecastAround(ServerLevel world, BlockPos center, int radius) {
        Map<ResourceLocation, BlockPos> samples = AtmosphereUtils.findBiomes(world, center, radius);
        Map<ResourceLocation, float[][]> result = new HashMap<>();

        for (Map.Entry<ResourceLocation, BlockPos> entry : samples.entrySet()) {
            ResourceLocation biome = entry.getKey();
            BlockPos pos = entry.getValue();

            float[][] week;
            if (WindStorageManager.hasForecast(biome)) {
                week = WindStorageManager.getForecast(biome);
            } else {
                week = WindGenerator.generateWeeklyWindProfile(world, pos, biome);
                WindStorageManager.saveForecast(biome, week);
            }

            result.put(biome, week);
        }

        return result;
    }

    public static Map<ResourceLocation, float[][]> generateTemporaryForecastAround(ServerLevel world, BlockPos center, int radius) {
        Map<ResourceLocation, BlockPos> samples = AtmosphereUtils.findBiomes(world, center, radius);
        Map<ResourceLocation, float[][]> result = new HashMap<>();

        for (Map.Entry<ResourceLocation, BlockPos> entry : samples.entrySet()) {
            ResourceLocation biome = entry.getKey();
            BlockPos pos = entry.getValue();
            float[][] week = WindGenerator.generateWeeklyWindProfile(world, pos, biome);
            result.put(biome, week);
        }

        return result;
    }
}
