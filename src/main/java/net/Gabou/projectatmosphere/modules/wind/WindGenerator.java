package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.util.*;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;

import java.util.Set;

public class WindGenerator {

    private static final float SPEED_SCALING = 1.0f;

    public static WindVector[] generateWindWeek(BiomeInstanceKey selfKey) {
        BlockPos center = selfKey.samplePos();
        ResourceLocation biome = selfKey.biomeType();
        WindVector[] result = new WindVector[7];

        BiomeForecast biomeForecast = ForecastGenerator.getClosestValidForecast(selfKey, ForecastType.PRESSURE);
        float[][] selfPressure = biomeForecast.getPressure();
        float[][] selfTemp = biomeForecast.getTemperature();
        float[][] selfHumidity = biomeForecast.getHumidity();
        Set<BiomeInstanceKey> neighbors = ForecastGenerator.getBiomeSamples();

        float altitude = center.getY();
        float biomeFactor = getBiomeWindModifier(biome);
        double[] airDensity = AtmosphericPhysics.computeAirDensity(selfTemp, selfHumidity);

        for (int d = 0; d < 7; d++) {
            float Pself = (selfPressure[d][0] + selfPressure[d][1]) * 0.5f;
            float Pavg = 0f;
            int count = 0;
            Vec2 windVector = new Vec2(0, 0);

            for (BiomeInstanceKey key : neighbors) {
                float[][] p = ForecastGenerator.getClosestValidForecast(key, ForecastType.PRESSURE).getPressure();
                if (p != null) {
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
            }

            if (count == 0) {
                result[d] = new WindVector(5f, 0f);
                continue;
            }

            Pavg /= count;
            float dP = Math.abs(Pavg - Pself);
            float densityFactor = (float) (airDensity[d] / 1.225f);
            float altitudeFactor = (float) Math.log(1 + altitude / 10f);

            float speed = (float) Math.sqrt(2 * dP / densityFactor) * biomeFactor * altitudeFactor * SPEED_SCALING;
            float angle = (float) Math.atan2(windVector.y, windVector.x); // angle in radians

            result[d] = new WindVector(Math.max(1.2f, speed), angle);
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
