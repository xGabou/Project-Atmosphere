package net.Gabou.projectatmosphere.modules.wind.forecast;

import net.Gabou.projectatmosphere.modules.wind.util.WindProfileManager;
import net.Gabou.projectatmosphere.modules.wind.util.WindStorageManager;
import net.Gabou.projectatmosphere.modules.wind.util.WindGenerator;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class WindForecast {

    public static void generateForecastAround(ServerLevel world, BlockPos center, int radius) {
        Set<BiomeInstanceKey> biomeSamples = AtmosphereUtils.findBiomes(world, center, radius);

        for (BiomeInstanceKey entry : biomeSamples) {
            ResourceLocation biome = entry.biomeType();
            BlockPos pos = entry.samplePos();

            float[] week;
            if (WindStorageManager.hasForecast(entry)) {
                WindProfileManager.putWeeklyForecast(entry,WindStorageManager.getForecast(entry));
            } else {
                week = WindGenerator.generateBaseWindWeek(world, pos, biome, entry);
                WindStorageManager.saveForecast(entry, week);
                WindProfileManager.putWeeklyForecast(entry, week);
            }

        }


    }

    public static Map<BiomeInstanceKey, float[]> generateTemporaryForecastAround(ServerLevel world, BlockPos center, int radius) {
        Set<BiomeInstanceKey> samples = AtmosphereUtils.findBiomes(world, center, radius);
        Map<BiomeInstanceKey, float[]> result = new HashMap<>();

        for (BiomeInstanceKey entry : samples) {
            ResourceLocation biome = entry.biomeType();
            BlockPos pos = entry.samplePos();
            float[] week = WindGenerator.generateBaseWindWeek(world, pos, biome, entry);
            result.put(entry, week);
        }

        return result;
    }
}
