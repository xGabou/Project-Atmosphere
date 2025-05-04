package net.Gabou.projectatmosphere.modules.temperature.forecast;

import net.Gabou.projectatmosphere.modules.temperature.spike.SpikeManager;
import net.Gabou.projectatmosphere.modules.temperature.util.ForecastStorageManager;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureGenerator;
import net.Gabou.projectatmosphere.modules.temperature.variation.VariationGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;

import static net.Gabou.projectatmosphere.util.AtmosphereUtils.findBiomes;

public class TemperatureForecast {

    /**
     * Scans a 500×500 area, uses cached forecasts when present,
     * otherwise generates and saves new weekly forecasts.
     */
    public static Map<ResourceLocation, float[][]> generateForecastAround(Level world, BlockPos center, int radiusBlocks) {
        // Skip if not in overworld
        if (!world.dimension().equals(Level.OVERWORLD)) {
            return Collections.emptyMap();
        }

        Set<ResourceLocation> foundBiomes = new HashSet<>();
        Map<ResourceLocation, BlockPos> biomeSamples = new HashMap<>();
        Map<ResourceLocation, float[][]> forecasts = new HashMap<>();

        findBiomes(world, center, radiusBlocks, foundBiomes, biomeSamples);

        // Step 2: Generate forecast for each biome based on its real sample position
        for (Map.Entry<ResourceLocation, BlockPos> entry : biomeSamples.entrySet()) {
            ResourceLocation biome = entry.getKey();
            BlockPos samplePos = entry.getValue();
            ForecastStorageManager.saveSamplePosition(biome, samplePos);
            if (ForecastStorageManager.hasForecast(biome)) {
                forecasts.put(biome, ForecastStorageManager.getForecast(biome));
            } else {
                float[][] week= SpikeManager.applySpikeLogic(biome,
                        VariationGenerator.applyVariationToWeek(
                        TemperatureGenerator.generateWeekForecast(world, samplePos, biome)));


                forecasts.put(biome, week);
                ForecastStorageManager.saveForecast(biome, week);
            }
        }

        return forecasts;
    }

    public static Map<ResourceLocation, float[][]> generateTemporaryForecastAround(ServerLevel world, BlockPos center, int radius) {
        Map<ResourceLocation, float[][]> result = new HashMap<>();
        Set<ResourceLocation> foundBiomes = new HashSet<>();
        Map<ResourceLocation, BlockPos> biomeSamples = new HashMap<>();

        // Populate biome sample positions
        findBiomes(world, center, radius, foundBiomes, biomeSamples);

        for (Map.Entry<ResourceLocation, BlockPos> entry : biomeSamples.entrySet()) {
            ResourceLocation biome = entry.getKey();
            BlockPos samplePos = entry.getValue();

            float[][] week = TemperatureGenerator.generateWeekForecast(world, samplePos, biome);

            // Process the forecast through variation and spike, without saving
            week = SpikeManager.applySpikeLogic(biome,VariationGenerator.applyVariationToWeek(week));
            result.put(biome, week);
        }

        return result;
    }




}
