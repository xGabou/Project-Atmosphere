package net.Gabou.projectatmosphere.modules.temperature.forecast;

import net.Gabou.projectatmosphere.modules.temperature.spike.SpikeManager;
import net.Gabou.projectatmosphere.modules.temperature.util.ForecastStorageManager;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureGenerator;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.Gabou.projectatmosphere.modules.temperature.variation.VariationGenerator;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
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
    public static void generateForecastAround(Level world,  Set<BiomeInstanceKey> biomeSamples) {





        // Step 2: Generate forecast for each biome based on its real sample position
        for (BiomeInstanceKey entry : biomeSamples) {
            ResourceLocation biome = entry.biomeType();
            BlockPos pos = entry.samplePos();


            if (ForecastStorageManager.hasForecast(entry)) {
                TemperatureProfileManager.putWeeklyForecast(entry, ForecastStorageManager.getForecast(entry));
            } else {
                float[][] week= SpikeManager.applySpikeLogic(entry,
                        VariationGenerator.applyVariationToWeek(
                        TemperatureGenerator.generateWeekForecast(world, pos, biome)));
                ForecastStorageManager.saveForecast(entry, week);
                TemperatureProfileManager.putWeeklyForecast(entry, week);
            }
        }


    }

    public static Map<BiomeInstanceKey, float[][]> generateTemporaryForecastAround(ServerLevel world, BlockPos center, int radius) {
        Set<BiomeInstanceKey> biomeSamples = AtmosphereUtils.findBiomes(world, center, radius);
        Map<BiomeInstanceKey, float[][]> forecasts = new HashMap<>();

        for (BiomeInstanceKey entry : biomeSamples) {
            ResourceLocation biome = entry.biomeType();
            BlockPos pos = entry.samplePos();


            float[][] week = TemperatureGenerator.generateWeekForecast(world, pos, biome);

            // Process the forecast through variation and spike, without saving
            week = SpikeManager.applySpikeLogic(entry,VariationGenerator.applyVariationToWeek(week));
            forecasts.put(entry, week);
        }

        return forecasts;
    }




}
