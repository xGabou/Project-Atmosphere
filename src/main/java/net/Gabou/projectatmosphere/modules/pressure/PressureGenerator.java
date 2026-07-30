package net.Gabou.projectatmosphere.modules.pressure;

import java.util.Random;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.region.RegionBiomeSample;
import net.Gabou.projectatmosphere.util.AtmosphericPhysics;

/**
 * Generates a raw 7-day [min,max] pressure forecast for a region sample.
 */
public class PressureGenerator {
    public static final float PRESSION_MOYENNE = 1013.25f;
    private static final float MIN_PRESSURE_HPA = 900f;
    private static final float MAX_PRESSURE_HPA = 1080f;

    public static float[][] generateWeekForecast(RegionBiomeSample sample,
                                                 float[][] tempWeek,
                                                 float[][] humidityWeek,
                                                 long day) {
        long seed = ProjectAtmosphere.seed ^ sample.pos().asLong() ^ sample.biomeId().hashCode() ^ day;
        Random rand = new Random(seed);

        double p0 = PRESSION_MOYENNE * Math.pow(
                1.0 - 0.0065 * sample.pos().getY() / 288.15,
                9.80665 / (287.05 * 0.0065)
        );
        float base = (float) p0;

        if (tempWeek == null || humidityWeek == null) {
            return new float[7][2];
        }

        double[] densities = AtmosphericPhysics.computeAirDensity(tempWeek, humidityWeek);
        double referenceDensity = 1.225;
        float[][] week = new float[7][2];
        for (int d = 0; d < 7; d++) {
            float avgTemp = (tempWeek[d][0] + tempWeek[d][1]) * 0.5f;
            float deltaT = -0.5f * (avgTemp - 15f);
            float densityModifier = (float) (densities[d] / referenceDensity);
            float deltaDensity = (densityModifier - 1f) * 30f;
            float center = base + deltaT + deltaDensity;
            float variation = 0.6f;
            float offset = (rand.nextFloat() - 0.5f) * variation;
            float min = center - variation + offset;
            float max = center + offset;
            week[d][0] = Math.max(MIN_PRESSURE_HPA, Math.min(min, MAX_PRESSURE_HPA));
            week[d][1] = Math.max(MIN_PRESSURE_HPA, Math.min(max, MAX_PRESSURE_HPA));
        }
        return week;
    }
}
