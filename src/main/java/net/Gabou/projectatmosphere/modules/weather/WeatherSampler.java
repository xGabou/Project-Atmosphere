package net.Gabou.projectatmosphere.modules.weather;

import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

public final class WeatherSampler {
    private static final ResourceLocation DEFAULT_BIOME = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");

    private WeatherSampler() {
    }

    // ---------------------------------------------------------------------
    // Region sampling
    // ---------------------------------------------------------------------
    public static Map<RegionInstanceKey, Integer> sampleRegionsInArea(int centerX, int centerZ, int radius, ServerLevel level) {
        Map<RegionInstanceKey, Integer> result = new HashMap<>();
        int step = 24;
        int radiusSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }

                BlockPos pos = new BlockPos(centerX + dx, level.getSeaLevel(), centerZ + dz);
                result.merge(RegionInstanceKey.from(pos), 1, Integer::sum);
            }
        }

        return result;
    }

    // ---------------------------------------------------------------------
    // Weather aggregation
    // ---------------------------------------------------------------------
    public static WeatherStats computeWeatherStats(Map<RegionInstanceKey, Integer> regionWeights, long tick) {
        if (regionWeights == null || regionWeights.isEmpty()) {
            return null;
        }

        float totalHumidity = 0f;
        float totalTemp = 0f;
        float totalPressure = 0f;
        float totalStormFactor = 0f;
        float totalWindX = 0f;
        float totalWindZ = 0f;
        float totalGust = 0f;
        int totalWeight = 0;
        RegionInstanceKey dominantRegion = null;
        int dominantWeight = Integer.MIN_VALUE;

        for (Map.Entry<RegionInstanceKey, Integer> entry : regionWeights.entrySet()) {
            RegionInstanceKey key = entry.getKey();
            int weight = entry.getValue() == null ? 0 : entry.getValue();
            if (key == null || weight <= 0) {
                continue;
            }

            float humidity = ForecastOrchestrator.getCurrentHumidity(key, tick);
            float temperature = ForecastOrchestrator.getCurrentTemperature(key, tick);
            float pressure = ForecastOrchestrator.getCurrentPressure(key, tick);
            float stormFactor = ForecastOrchestrator.getCurrentStormChance(key, tick);
            WindVector wind = ForecastOrchestrator.getWind(key, tick);

            totalHumidity += humidity * weight;
            totalTemp += temperature * weight;
            totalPressure += pressure * weight;
            totalStormFactor += stormFactor * weight;
            totalWindX += (float) Math.cos(wind.angleRadians()) * wind.baseSpeed() * weight;
            totalWindZ += (float) Math.sin(wind.angleRadians()) * wind.baseSpeed() * weight;
            totalGust += wind.gustSpeed() * weight;
            totalWeight += weight;

            if (weight > dominantWeight) {
                dominantWeight = weight;
                dominantRegion = key;
            }
        }

        if (dominantRegion == null || totalWeight <= 0) {
            return null;
        }

        float avgWindSpeed = (float) Math.sqrt(totalWindX * totalWindX + totalWindZ * totalWindZ) / totalWeight;
        float avgWindAngle = (float) Math.atan2(totalWindZ, totalWindX);
        WindVector averageWind = new WindVector(avgWindSpeed, avgWindAngle, totalGust / totalWeight);

        return new WeatherStats(
                totalHumidity / totalWeight,
                totalTemp / totalWeight,
                totalPressure / totalWeight,
                averageWind,
                dominantRegion,
                resolveDominantBiome(dominantRegion),
                resolveAnchor(dominantRegion),
                totalStormFactor / totalWeight
        );
    }

    // ---------------------------------------------------------------------
    // Fallback resolution
    // ---------------------------------------------------------------------
    private static ResourceLocation resolveDominantBiome(RegionInstanceKey regionKey) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(regionKey);
        if (state != null && state.getDominantBiome() != null) {
            return state.getDominantBiome();
        }

        var region = ForecastGenerator.getRegionForecasts().get(regionKey);
        if (region == null || region.getBiomeWeights().isEmpty()) {
            return DEFAULT_BIOME;
        }

        ResourceLocation dominant = null;
        int bestWeight = Integer.MIN_VALUE;
        for (Map.Entry<ResourceLocation, Integer> entry : region.getBiomeWeights().entrySet()) {
            if (entry.getValue() > bestWeight) {
                dominant = entry.getKey();
                bestWeight = entry.getValue();
            }
        }
        return dominant == null ? DEFAULT_BIOME : dominant;
    }

    private static BlockPos resolveAnchor(RegionInstanceKey regionKey) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(regionKey);
        if (state != null && state.getPosition() != null) {
            return state.getPosition();
        }

        var region = ForecastGenerator.getRegionForecasts().get(regionKey);
        if (region != null && region.getAnchor() != null) {
            return region.getAnchor();
        }

        return regionKey.center();
    }

    // ---------------------------------------------------------------------
    // Output contract
    // ---------------------------------------------------------------------
    public record WeatherStats(
            float humidity,
            float temperature,
            float pressure,
            WindVector windVector,
            RegionInstanceKey dominantRegion,
            ResourceLocation dominantBiome,
            BlockPos pos,
            float stormFactor
    ) {
    }
}
