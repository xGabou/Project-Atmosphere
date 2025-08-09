
package net.Gabou.projectatmosphere.modules.pressure;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
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

    public static final float PRESSION_MOYENNE = 1013.25f; 
    public static float[][] generateWeekForecast(Level world, BiomeInstanceKey key) {

        ResourceLocation biome = key.biomeType();
        BlockPos pos = key.samplePos();
        long day = world.getDayTime() / 24000L;
        long seed = ProjectAtmosphere.seed
                ^ pos.asLong() ^ biome.hashCode() ^ day;
        Random rand = new Random(seed);

        double P0 = PRESSION_MOYENNE * Math.pow( 
                1.0 - 0.0065 * pos.getY() / 288.15,
                9.80665 / (287.05 * 0.0065)
        );
        float base = (float) P0;

        BiomeForecast biomeForecast = ForecastGenerator.getClosestValidForecast(key, ForecastType.TEMPERATURE);
        float[][] tempWeek = biomeForecast.getTemperature();
        float[][] rhWeek = biomeForecast.getHumidity();
        if (tempWeek == null || rhWeek == null) return new float[7][2];

        double[] densities = AtmosphericPhysics.computeAirDensity(tempWeek, rhWeek);
        double referenceDensity = 1.225; 

        float[][] week = new float[7][2];
        for (int d = 0; d < 7; d++) {
            float Tavg = (tempWeek[d][0] + tempWeek[d][1]) * 0.5f;
            float deltaT = -0.5f * (Tavg - 15f);

            
            float densityModifier = (float)(densities[d] / referenceDensity);
            float deltaDensity = (densityModifier - 1f) * 30f; 

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
