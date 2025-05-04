// src/main/java/net/Gabou/projectatmosphere/modules/humidity/util/HumidityGenerator.java
package net.Gabou.projectatmosphere.modules.humidity.util;

import net.Gabou.projectatmosphere.modules.temperature.forecast.TemperatureForecast;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public class HumidityGenerator {

    private static final float pressVap = 0.611f; //Pression de vapeur standard à  0°C en kPa
    private static final double variationVap = 17.27f; //Constante de variation de vapeur (sans unités)
    private static final float celciusToKelvin = 273.3f; //Passer de celcius en Kelvin (°C)

    private static final float boostMc = 10f;

    public static float[][] generateWeekForecast(Level world, BlockPos samplePos, ResourceLocation biomeId) {
        Biome biome = world.getBiome(samplePos).get();
        float base = biome.getModifiedClimateSettings().downfall() * 100f;
        float[][] temperatureForecast = TemperatureProfileManager.getWeeklyForecast(biomeId);
        float[][] humidityForecast = new float[temperatureForecast.length][2];
        for (int day = 0; day < 7; day++) {
            float temperatureMin = temperatureForecast[day][0];
            float tempMinEffect = (float) (pressVap * Math.exp((variationVap * temperatureMin) / (temperatureMin + celciusToKelvin)));
            float tempHumidityBoostMin = (float) tempMinEffect * boostMc;
            float rawHumidityMin = base * 0.7f + tempHumidityBoostMin * 0.3f;
            float temperatureMax = temperatureForecast[day][1];
            float tempMaxEffect = (float) (pressVap * Math.exp((variationVap * temperatureMax) / (temperatureMax + celciusToKelvin)));
            float tempHumidityBoostMax = (float) tempMaxEffect * boostMc;
            float rawHumidityMax = base * 0.7f + tempHumidityBoostMax * 0.3f;
            humidityForecast[day][0] = rawHumidityMin;
            humidityForecast[day][1] = rawHumidityMax;
        }
        return humidityForecast;
    }

    private static float clamp(float v) {
        return v < (float) 0.0 ? (float) 0.0 : (Math.min(v, (float) 100.0));
    }
}
