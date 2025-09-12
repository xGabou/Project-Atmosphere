package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.util.*;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WindGenerator {
    private static final Map<BiomeInstanceKey, Set<BiomeInstanceKey>> neighborCache = new ConcurrentHashMap<>();
    private static final int MIN_DISTANCE = 200;

    private static Set<BiomeInstanceKey> getNeighbors(BiomeInstanceKey self, Set<BiomeInstanceKey> all) {
        return neighborCache.computeIfAbsent(self, key -> {
            Set<BiomeInstanceKey> list = new HashSet<>();
            BlockPos c = key.samplePos();
            for (BiomeInstanceKey other : all) {
                if (other == key) continue;
                BlockPos o = other.samplePos();
                if (c.distSqr(o) <= MIN_DISTANCE * MIN_DISTANCE) {
                    list.add(other);
                }
            }
            return list;
        });
    }


    private static final float SPEED_SCALING = 1.0f;

    public static WindVector[] generateWindWeek(BiomeInstanceKey selfKey) {
        BlockPos center = selfKey.samplePos();
        ResourceLocation biome = selfKey.biomeType();
        WindVector[] result = new WindVector[7];

        BiomeForecast biomeForecast = ForecastGenerator.getClosestValidForecast(selfKey, ForecastType.PRESSURE);
        float[][] selfPressure = biomeForecast.getPressure();
        float[][] selfTemp = biomeForecast.getTemperature();
        float[][] selfHumidity = biomeForecast.getHumidity();
        Set<BiomeInstanceKey> neighbors = getNeighbors(selfKey, ForecastGenerator.getBiomeSamples());


        float altitude = center.getY();
        float biomeFactor = getBiomeWindModifier(biome);
        double[] airDensity = AtmosphericPhysics.computeAirDensity(selfTemp, selfHumidity);

        for (int d = 0; d < 7; d++) {
            float Pself = (selfPressure[d][0] + selfPressure[d][1]) * 0.5f;
            float Pavg = 0f;
            int count = 0;
            Vec2 windVector = new Vec2(0, 0);

            for (BiomeInstanceKey key : neighbors) {
                BiomeForecast forecast = ForecastGenerator.getClosestValidForecast(key, ForecastType.PRESSURE);
                if (forecast == null) continue;
                float[][] p = forecast.getPressure();
                if (p == null) continue;
                float Pn = (p[d][0] + p[d][1]) * 0.5f;
                Pavg += Pn;
                count++;

                BlockPos neighborPos = key.samplePos();
                double dx = neighborPos.getX() - center.getX();
                double dz = neighborPos.getZ() - center.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < 1e-2) continue;

                float dP = Pself - Pn;
                float vx = (float) (dP * dx / dist);
                float vz = (float) (dP * dz / dist);

                windVector = new Vec2(windVector.x + vx, windVector.y + vz);

            }

            if (count == 0) {
                float randomAngle = (float) (Math.random() * Math.PI * 2);
                result[d] = WindVector.fromBase(1.2f, randomAngle);
                continue;
            }

            Pavg /= count;
            float dP = Math.abs(Pavg - Pself);
            float densityFactor = (float) (airDensity[d] / 1.225f);
            float normalizedAltitude = Math.min(altitude / 256f, 1f);
            float altitudeFactor = 0.5f + 0.5f * normalizedAltitude;


            float speed = (float) Math.sqrt(2 * dP / densityFactor) * biomeFactor * altitudeFactor * SPEED_SCALING;
            speed = Mth.clamp(speed, 1.2f, 60f);
            int hash = selfKey.hashCode();
            float baseSpeed = speed;
            float gustFactor = Mth.sin((d + hash % 50) * 0.6f) * 0.5f + 1.6f; 
            float gustSpeed = baseSpeed * gustFactor;
            gustSpeed = Mth.clamp(gustSpeed, 1.5f, 75f);
            if (windVector.length() > 1e-3) {
                windVector = windVector.normalized();
            }
            float angle = (float) Math.atan2(windVector.y, windVector.x);


            result[d] = new WindVector(baseSpeed, angle, gustSpeed);
        }

        return result;
    }

    private static float getBiomeWindModifier(ResourceLocation biome) {
        String path = biome.getPath();
        if (path.contains("forest") || path.contains("taiga") || path.contains("jungle")) return 0.7f;
        if (path.contains("plains") || path.contains("savanna")) return 1.1f;
        if (path.contains("ocean") || path.contains("beach")) return 1.25f;
        if (path.contains("mountain") || path.contains("peak") || path.contains("windswept")) return 1.4f;
        return 1.0f;
    }
}
