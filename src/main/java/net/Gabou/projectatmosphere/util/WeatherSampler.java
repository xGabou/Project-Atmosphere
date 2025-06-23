package net.Gabou.projectatmosphere.util;

import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.modules.humidity.manager.HumidityManager;
import net.Gabou.projectatmosphere.modules.pressure.manager.PressureManager;
import net.Gabou.projectatmosphere.modules.temperature.manager.TemperatureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    public static WeatherStats computeWeatherStats(Set<BiomeInstanceKey> keys, ServerLevel level, long tick) {
        float totalHumidity = 0, totalTemp = 0, totalPressure = 0;
        int count = 0;
        Map<ResourceLocation, Integer> biomeFreq = new HashMap<>();

        for (BiomeInstanceKey key : keys) {
            float humidity = HumidityManager.getCurrentHumidity(key, tick);
            float temperature = TemperatureManager.getCurrentTemperature(key, tick);
            float pressure = PressureManager.getCurrentPressure(key, tick);

            totalHumidity += humidity;
            totalTemp += temperature;
            totalPressure += pressure;
            biomeFreq.merge(key.biomeType(), 1, Integer::sum);
            count++;
        }

        if (count == 0) return null;

        // Determine dominant biome
        ResourceLocation dominantBiome = biomeFreq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(keys.iterator().next().biomeType());

        return new WeatherStats(totalHumidity / count, totalTemp / count, totalPressure / count, dominantBiome);
    }

    public record WeatherStats(float humidity, float temperature, float pressure, ResourceLocation dominantBiome) {}
}
