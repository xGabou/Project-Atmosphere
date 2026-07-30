package net.Gabou.projectatmosphere.modules.wind;

import java.util.ArrayList;
import java.util.List;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.region.RegionBiomeSample;
import net.Gabou.projectatmosphere.util.AtmosphericPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class WindGenerator {
    private static final int NEIGHBOR_DISTANCE = 200;
    private static final int NEIGHBOR_DISTANCE_SQR = NEIGHBOR_DISTANCE * NEIGHBOR_DISTANCE;
    private static final float SPEED_SCALING = 1.0f;

    public static List<ForecastRegion.GeneratedSample> attachWindForecasts(List<ForecastRegion.GeneratedSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return List.of();
        }
        List<ForecastRegion.GeneratedSample> result = new ArrayList<>(samples.size());
        for (ForecastRegion.GeneratedSample sample : samples) {
            WindVector[] wind = generateWindWeek(sample, samples);
            result.add(new ForecastRegion.GeneratedSample(
                    sample.sample(),
                    sample.temperature(),
                    sample.humidity(),
                    sample.pressure(),
                    wind
            ));
        }
        return result;
    }

    private static WindVector[] generateWindWeek(ForecastRegion.GeneratedSample self, List<ForecastRegion.GeneratedSample> all) {
        RegionBiomeSample selfSample = self.sample();
        BlockPos center = selfSample.pos();
        WindVector[] result = new WindVector[7];
        float[][] pressure = self.pressure();
        float[][] temperature = self.temperature();
        float[][] humidity = self.humidity();
        double[] airDensity = AtmosphericPhysics.computeAirDensity(temperature, humidity);
        float biomeFactor = getBiomeWindModifier(selfSample.biomeId());
        float altitudeFactor = 0.5f + 0.5f * Math.min(center.getY() / 256f, 1f);

        for (int d = 0; d < 7; d++) {
            float selfPressure = averageDay(pressure, d);
            float neighborPressureSum = 0f;
            int count = 0;
            float sumVx = 0f;
            float sumVz = 0f;

            for (ForecastRegion.GeneratedSample other : all) {
                if (other == self || other.sample() == null || other.sample().pos() == null) {
                    continue;
                }
                BlockPos neighborPos = other.sample().pos();
                int dx = neighborPos.getX() - center.getX();
                int dz = neighborPos.getZ() - center.getZ();
                int distSq = dx * dx + dz * dz;
                if (distSq <= 0 || distSq > NEIGHBOR_DISTANCE_SQR) {
                    continue;
                }

                float neighborPressure = averageDay(other.pressure(), d);
                neighborPressureSum += neighborPressure;
                count++;
                float dist = Mth.sqrt(distSq);
                float dP = selfPressure - neighborPressure;
                sumVx += dP * dx / dist;
                sumVz += dP * dz / dist;
            }

            if (count == 0) {
                float randomAngle = (float) (Math.random() * Math.PI * 2);
                result[d] = WindVector.fromBase(1.2f, randomAngle);
                continue;
            }

            float avgPressure = neighborPressureSum / count;
            float dPHpa = Math.abs(avgPressure - selfPressure);
            float dPPa = dPHpa * 100f;
            float densityFactor = airDensity.length > d && airDensity[d] > 0 ? (float) (airDensity[d] / 1.225f) : 1f;
            float speed = (float) Math.sqrt(2 * dPPa / Math.max(0.1f, densityFactor)) * biomeFactor * altitudeFactor * SPEED_SCALING;
            speed = Mth.clamp(speed, 1.2f, 60f);

            int hash = selfSample.hashCode();
            float gustFactor = Mth.sin((d + hash % 50) * 0.6f) * 0.5f + 1.6f;
            float gustSpeed = Mth.clamp(speed * gustFactor, 1.5f, 75f);

            float lenSq = sumVx * sumVx + sumVz * sumVz;
            float angle = lenSq > 1e-6f ? (float) Math.atan2(sumVz, sumVx) : 0f;
            result[d] = new WindVector(speed, angle, gustSpeed);
        }

        return result;
    }

    private static float averageDay(float[][] curve, int day) {
        if (curve == null || curve.length <= day || curve[day] == null || curve[day].length == 0) {
            return 0f;
        }
        if (curve[day].length == 1) {
            return curve[day][0];
        }
        return (curve[day][0] + curve[day][1]) * 0.5f;
    }

    private static float getBiomeWindModifier(ResourceLocation biome) {
        String path = biome == null ? "" : biome.getPath();
        if (path.contains("forest") || path.contains("taiga") || path.contains("jungle")) return 0.7f;
        if (path.contains("plains") || path.contains("savanna")) return 1.1f;
        if (path.contains("ocean") || path.contains("beach")) return 1.25f;
        if (path.contains("mountain") || path.contains("peak") || path.contains("windswept")) return 1.4f;
        return 1.0f;
    }
}
