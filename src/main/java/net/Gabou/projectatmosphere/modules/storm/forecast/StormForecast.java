package net.Gabou.projectatmosphere.modules.storm.forecast;

import net.Gabou.projectatmosphere.modules.storm.util.StormProfileManager;
import net.Gabou.projectatmosphere.modules.storm.util.StormStorageManager;
import net.Gabou.projectatmosphere.modules.storm.util.StormGenerator;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class StormForecast {

    /**
     * Generates or loads weekly storm forecasts around the center position.
     * Used on world load or precompute pass.
     */
    public static void generateStormForecastAround(ServerLevel world, Set<BiomeInstanceKey> biomeSamples) {
        for (var entry : biomeSamples) {
            ResourceLocation biome = entry.biomeType();
            BlockPos pos = entry.samplePos();

            float[] week;
            if (StormStorageManager.hasForecast(entry)) {
                StormProfileManager.putWeeklyForecast(entry,StormStorageManager.getForecast(entry));
            } else {
                week = StormGenerator.generateWeeklyStormProfile(world, pos, biome);
                StormStorageManager.saveForecast(entry, week);
                StormProfileManager.putWeeklyForecast(entry, week);
            }


        }

    }

    /**
     * Same as above, but generates and returns values without saving.
     * Useful for temporary visualizations or comparisons.
     */
    public static Map<BiomeInstanceKey, float[]> generateTemporaryStormForecastAround(ServerLevel world,
                                                                                       BlockPos center,
                                                                                       int radiusBlocks) {
        Set<BiomeInstanceKey> samples = AtmosphereUtils.findBiomes(world, center, radiusBlocks);
        Map<BiomeInstanceKey, float[]> forecast = new HashMap<>();

        for (var entry : samples) {
            ResourceLocation biome = entry.biomeType();
            BlockPos pos = entry.samplePos();
            float[] week = StormGenerator.generateWeeklyStormProfile(world, pos, biome);
            forecast.put(entry, week);
        }

        return forecast;
    }
}
