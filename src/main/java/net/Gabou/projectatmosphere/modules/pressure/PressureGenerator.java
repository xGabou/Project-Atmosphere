// PressureGenerator.java
package net.Gabou.projectatmosphere.modules.pressure;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.util.AtmosphericPhysics;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Random;


/**
 * Generates a raw, biome‐isolated 7‐day [min,max] pressure forecast.
 */
public class PressureGenerator {

    public static final float PRESSION_MOYENNE = 1013.25f; // hPa, average sea level pressure
    public static float[][] generateWeekForecast(Level world, BiomeInstanceKey key) {

        ResourceLocation biome = key.biomeType();
        BlockPos pos = key.samplePos();
        long day = world.getDayTime() / 24000L;
        long seed = ProjectAtmosphere.seed
                ^ pos.asLong() ^ biome.hashCode() ^ day;
        Random rand = new Random(seed);

        double P0 = PRESSION_MOYENNE * Math.pow( //Pression 0 en hpa
                1.0 - 0.0065 * pos.getY() / 288.15,
                9.80665 / (287.05 * 0.0065)
        );
        float base = (float) P0;

        float[][] tempWeek = ForecastGenerator.getForecastMap().get(key).getTemperature();
        float[][] rhWeek = ForecastGenerator.getForecastMap().get(key).getHumidity();
        if (tempWeek == null || rhWeek == null) return new float[7][2];

        double[] densities = AtmosphericPhysics.computeAirDensity(tempWeek, rhWeek);
        double referenceDensity = 1.225; // kg/m³ at sea level, 15°C

        float[][] week = new float[7][2];
        for (int d = 0; d < 7; d++) {
            float Tavg = (tempWeek[d][0] + tempWeek[d][1]) * 0.5f;
            float deltaT = -0.5f * (Tavg - 15f);

            // NEW: accurate air density adjustment
            float densityModifier = (float)(densities[d] / referenceDensity);
            float deltaDensity = (densityModifier - 1f) * 30f; // Tune this multiplier

            float center = base + deltaT + deltaDensity;

            float volatility = 0.3f;
            float variation = 2.0f * volatility;

            float offset = (rand.nextFloat() - 0.5f) * variation;
            float min = center - variation + offset;
            float max = center + offset;

            week[d][0] = Math.max(870f, Math.min(min, 1080f));
            week[d][1] = Math.max(870f, Math.min(max, 1080f));
        }
        return week;
    }


}
