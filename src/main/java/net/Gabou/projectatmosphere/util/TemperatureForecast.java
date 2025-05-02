package net.Gabou.projectatmosphere.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.*;

public class TemperatureForecast {

    /**
     * Scans all unique biomes in a radius (500x500 area) and generates weekly forecasts per biome.
     */
    public static Map<ResourceLocation, float[]> generateForecastAround(Level world, BlockPos center, int radiusBlocks) {
        Set<ResourceLocation> foundBiomes = new HashSet<>();
        Map<ResourceLocation, float[]> forecasts = new HashMap<>();

        int step = 16; // chunk-level scan for performance
        int radius = radiusBlocks;

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                BlockPos samplePos = center.offset(dx, 0, dz);
                ResourceLocation biomeKey = world.getBiome(samplePos).unwrapKey().get().location();
                foundBiomes.add(biomeKey);
            }
        }

        for (ResourceLocation biome : foundBiomes) {
            forecasts.put(biome, TemperatureUtils.generateWeekForecast(world, center, biome));
        }

        return forecasts;
    }
}
