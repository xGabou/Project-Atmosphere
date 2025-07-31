package net.Gabou.projectatmosphere.util;

import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.*;
import java.util.stream.Collectors;

public class WeatherSampler {

    public static Set<BiomeInstanceKey> sampleBiomesInRegion(SpawnRegion region, ServerLevel level) {
        Set<BiomeInstanceKey> keys = new HashSet<>();

        int baseX = region.x() << 4;
        int baseZ = region.z() << 4;

        for (int dx = 0; dx < 16; dx += 4) {
            for (int dz = 0; dz < 16; dz += 4) {
                BlockPos pos = new BlockPos(baseX + dx, level.getSeaLevel(), baseZ + dz);
                level.getBiome(pos).unwrapKey().ifPresent(key ->
                        keys.add(new BiomeInstanceKey(key.location(), pos))
                );
            }
        }
        return keys;
    }
    public static Set<BiomeInstanceKey> sampleBiomesInArea(int centerX, int centerZ, int radius, ServerLevel level) {
        Set<BiomeInstanceKey> result = new HashSet<>();
        int step = 24;
        int radiusSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (dx * dx + dz * dz > radiusSq)
                    continue;

                BlockPos pos = new BlockPos(centerX + dx, level.getSeaLevel(), centerZ + dz);

                level.getBiome(pos).unwrapKey().ifPresent(biomeKey -> {
                    ResourceLocation biomeId = biomeKey.location();

                    // Try to find existing BiomeInstanceKey for this biomeId that is closest to this sample pos
                    BiomeInstanceKey bestMatch = null;
                    double bestDistSq = Double.MAX_VALUE;

                    for (BiomeInstanceKey known : ForecastGenerator.getBiomeSamples()) {
                        if (known.biomeType().equals(biomeId)) {
                            double distSq = known.samplePos().distSqr(pos);
                            if (distSq < bestDistSq) {
                                bestDistSq = distSq;
                                bestMatch = known;
                            }
                        }
                    }

                    if (bestMatch != null) {
                        result.add(bestMatch); // use canonical sample
                    } else {
                        result.add(new BiomeInstanceKey(biomeId, pos)); // fallback to new one
                    }
                });
            }
        }

        return result;
    }






    public static WeatherStats computeWeatherStats(Set<BiomeInstanceKey> keys, ServerLevel level, long tick) {
        float totalHumidity = 0, totalTemp = 0, totalPressure = 0,
                totalStormChance = 0;
        WindVector totalWind = new WindVector(0, 0);
        int count = 0;
        Map<ResourceLocation, Integer> biomeFreq = new HashMap<>();

        for (BiomeInstanceKey key : keys) {
            float humidity = ForecastGenerator.getHumidityValue(key, tick);
            float temperature = ForecastGenerator.getTemperatureValue(key, tick);
            float pressure = ForecastGenerator.getPressureValue(key, tick);
            float stormChanceValue = ForecastGenerator.getStormChanceValue(key,tick);
            WindVector wind = ForecastGenerator.getWindValue(key);

            totalHumidity += humidity;
            totalTemp += temperature;
            totalPressure += pressure;
            totalWind = totalWind.add(wind);
            totalStormChance += stormChanceValue;
            biomeFreq.merge(key.biomeType(), 1, Integer::sum);
            count++;
        }

        if (count == 0) return null;

        // Determine dominant biome
        BiomeInstanceKey dominantKey = keys.stream()
                .collect(Collectors.groupingBy(BiomeInstanceKey::biomeType))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue(Comparator.comparingInt(List::size)))
                .map(entry -> entry.getValue().get(0)) // get first BiomeInstanceKey in the most common group
                .orElse(keys.iterator().next());


        return new WeatherStats(totalHumidity / count, totalTemp / count, totalPressure / count,totalWind.divide(count), dominantKey.biomeType(),dominantKey.samplePos(),totalStormChance/ count);
    }

    public record WeatherStats(float humidity, float temperature, float pressure,WindVector windVector ,ResourceLocation dominantBiome,BlockPos pos,float stormChance) {}
}
