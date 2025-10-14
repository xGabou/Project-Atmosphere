package net.Gabou.projectatmosphere.manager;

import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class DailyForecastGenerator {

    /**
     * Generates and stores daily profiles (today and tomorrow) for all types (temperature, humidity, pressure).
     */
    public static void scheduleAll(Level world, Map<BiomeInstanceKey, BiomeForecast> forecastMap) {
        long now = world.getDayTime();
        int today = (int)((now / 24000L) % 7);
        int tomorrow = (today + 1) % 7;

        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : forecastMap.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            BiomeForecast forecast = entry.getValue();

            
            generateIfAbsent(key, forecast.getTemperature(), ForecastType.TEMPERATURE, today, tomorrow, forecast::setTemperatureDay, forecast::setTemperatureTomorrow);
            generateIfAbsent(key, forecast.getHumidity(), ForecastType.HUMIDITY, today, tomorrow, forecast::setHumidityDay, forecast::setHumidityTomorrow);
            generateIfAbsent(key, forecast.getPressure(), ForecastType.PRESSURE, today, tomorrow, forecast::setPressureDay, forecast::setPressureTomorrow);
            generateWindIfAbsent(key, forecast.getWind(), today, tomorrow,forecast::setWindDay, forecast::setWindTomorrow);
            generateIfAbsent(key,forecast.getStormChance(), ForecastType.STORM, today, tomorrow, forecast::setStormChanceDay, forecast::setStormChanceTomorrow);
        }
    }

    /**
     * Generates and applies a daily curve for today and/or tomorrow if the values are missing.
     *
     * @param key            The biome key to use
     * @param week           The 7x2 weekly forecast
     * @param type           The forecast type (TEMPERATURE, HUMIDITY, PRESSURE)
     * @param todayTick      Current in-game time
     * @param tomorrowTick   Tomorrow's in-game time (usually today + 24000)
     * @param todaySetter    Setter method for today's curve
     * @param tomorrowSetter Setter method for tomorrow's curve
     */
    private static void generateIfAbsent(
            BiomeInstanceKey key,
            float[][] week,
            ForecastType type,
            long todayTick,
            long tomorrowTick,
            Consumer<float[]> todaySetter,
            Consumer<float[]> tomorrowSetter
    ) {
        if (week == null) return;

        int todayIdx = (int) ((todayTick / 24000L) % 7);
        int tomorrowIdx = (int) ((tomorrowTick / 24000L) % 7);

        if (todaySetter != null) {
            float[] today = buildDailyCurve(week[todayIdx], type);
            if (today != null) todaySetter.accept(today);
        }

        if (tomorrowSetter != null) {
            float[] tomorrow = buildDailyCurve(week[tomorrowIdx], type);
            if (tomorrow != null) tomorrowSetter.accept(tomorrow);
        }
    }
    private static void generateWindIfAbsent(
            BiomeInstanceKey key,
            WindVector[] week, 
            long todayTick,
            long tomorrowTick,
            Consumer<WindVector> todaySetter,
            Consumer<WindVector> tomorrowSetter
    ) {
        if (week == null) return;

        int todayIdx = (int) ((todayTick / 24000L) % 7);
        int tomorrowIdx = (int) ((tomorrowTick / 24000L) % 7);

        if (todaySetter != null && week[todayIdx] != null) {
            todaySetter.accept(week[todayIdx]);
        }

        if (tomorrowSetter != null && week[tomorrowIdx] != null) {
            tomorrowSetter.accept(week[tomorrowIdx]);
        }
    }





    /**
     * Builds a 240-step daily curve from a [min, max] value array based on the forecast type.
     *
     * @param minMax the [min, max] array for the day
     * @param type   the forecast type (TEMPERATURE, HUMIDITY, PRESSURE)
     * @return a 240-step interpolated float curve
     */
    public static float[] buildDailyCurve(float[] minMax, ForecastType type) {
        float min = minMax[0];
        float max = minMax[1];
        float[] curve = new float[240];

        for (int i = 0; i < 240; i++) {
            float t = i / 239f;
            float factor;

            switch (type) {
                case TEMPERATURE, PRESSURE -> {
                    float theta = (float) (Math.PI * t); 
                    factor = (1f - (float) Math.cos(theta)) * 0.5f;
                }
                case HUMIDITY -> {
                    if (t < 0.25f) {
                        factor = 1f - (float) Math.pow(t * 4f, 0.8); 
                    } else if (t < 0.75f) {
                        factor = 0.1f + 0.9f * (1f - (float) Math.sin(Math.PI * (t - 0.25f) / 0.5f)); 
                    } else {
                        factor = 0.1f + (float) Math.pow((t - 0.75f) * 4f, 0.8); 
                    }
                    factor = 1f - factor; 
                }
                case STORM -> {
                    // Storm probability: low overnight, ramps up by afternoon, tapers at night
                    // Use a slightly skewed bell to make afternoons more active
                    float theta = (float) (Math.PI * Math.min(1f, t * 1.1f));
                    factor = (1f - (float) Math.cos(theta)) * 0.6f; // peak a bit higher than others
                    // soften early morning further
                    if (t < 0.2f) {
                        factor *= t * 5f;
                    }
                }
                default -> factor = 0f;
            }

            curve[i] = min + (max - min) * factor;
        }

        return curve;
    }

    /**
     * Regenerates today and tomorrow daily profiles for all known biomes in the forecast.
     */
    public static void scheduleGenerationForTodayAndTomorrow() {
        Map<BiomeInstanceKey, BiomeForecast> allForecasts = ForecastGenerator.getForecastMap();

        for (Map.Entry<BiomeInstanceKey, BiomeForecast> entry : allForecasts.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            BiomeForecast forecast = entry.getValue();

            float[][] temperature = forecast.getTemperature();
            float[][] pressure = forecast.getPressure();
            float[][] humidity = forecast.getHumidity();
            WindVector[] wind = forecast.getWind();

            if (temperature != null && temperature.length > 1) {
                forecast.setTemperatureDay(interpolateCurve(temperature[0], temperature[1]));
                forecast.setTemperatureTomorrow(interpolateCurve(temperature[1], temperature[2 % temperature.length]));
            }

            if (pressure != null && pressure.length > 1) {
                forecast.setPressureDay(interpolateCurve(pressure[0], pressure[1]));
                forecast.setPressureTomorrow(interpolateCurve(pressure[1], pressure[2 % pressure.length]));
            }

            if (humidity != null && humidity.length > 1) {
                forecast.setHumidityDay(interpolateCurve(humidity[0], humidity[1]));
                forecast.setHumidityTomorrow(interpolateCurve(humidity[1], humidity[2 % humidity.length]));
            }

            if (wind != null && wind.length > 1) {
                forecast.setWindDay(wind[0]);
                forecast.setWindTomorrow(wind[1]);
            }
        }

    }
    /**
     * Linearly interpolates a 240-step curve between two [min, max] values of consecutive days.
     *
     * @param today  the [min, max] array for today
     * @param next   the [min, max] array for the next day
     * @return interpolated curve
     */
    private static float[] interpolateCurve(float[] today, float[] next) {
        float min1 = today[0];
        float max1 = today[1];
        float min2 = next[0];
        float max2 = next[1];

        float[] curve = new float[240];
        for (int i = 0; i < 240; i++) {
            float t = i / 239f;
            
            float dayFactor = (1f - (float) Math.cos(Math.PI * t)) * 0.5f;

            
            float blendedMin = min1 + (min2 - min1) * t;
            float blendedMax = max1 + (max2 - max1) * t;

            curve[i] = blendedMin + (blendedMax - blendedMin) * dayFactor;
        }

        return curve;
    }

}
