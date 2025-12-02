package net.Gabou.projectatmosphere.modules.core;

import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Aggregated, region-level forecast that replaces per-biome forecasts.
 * A ForecastRegion owns the unified temperature, humidity, pressure and wind curves
 * for a large fixed grid cell and tracks which biomes contributed to that forecast.
 */
public class ForecastRegion {
    private static final float MIN_TEMPERATURE_C = -90f;
    private static final float MAX_TEMPERATURE_C = 70f;
    private static final float MIN_PRESSURE_HPA = 880f;
    private static final float MAX_PRESSURE_HPA = 1085f;
    private static final float MIN_HUMIDITY = 0f;
    private static final float MAX_HUMIDITY = 100f;

    private final RegionInstanceKey key;
    private final BlockPos anchor;
    private final List<BiomeInstanceKey> samples = new ArrayList<>();
    private final List<BiomeForecast> biomeForecasts = new ArrayList<>();
    private final Map<ResourceLocation, Integer> biomeWeights = new HashMap<>();

    private float[][] temperature;
    private float[][] humidity;
    private float[][] pressure;
    private WindVector[] wind;
    private WindVector windDay;

    public ForecastRegion(RegionInstanceKey key) {
        this(key, key.center());
    }

    public ForecastRegion(RegionInstanceKey key, BlockPos anchor) {
        this.key = key;
        this.anchor = anchor == null ? key.center() : anchor;
    }

    public RegionInstanceKey getKey() {
        return key;
    }

    public BlockPos getAnchor() {
        return anchor;
    }

    public Map<ResourceLocation, Integer> getBiomeWeights() {
        return biomeWeights;
    }

    public List<BiomeInstanceKey> getSamples() {
        return samples;
    }

    public float[][] getTemperature() {
        return temperature;
    }

    public float[][] getHumidity() {
        return humidity;
    }

    public float[][] getPressure() {
        return pressure;
    }

    public WindVector[] getWind() {
        return wind;
    }

    public WindVector getWindDay() {
        return windDay;
    }

    /**
     * Adds a biome contribution to this region. Weight defaults to 1 per sample.
     */
    public void addBiomeForecast(BiomeInstanceKey biomeKey, BiomeForecast forecast) {
        addBiomeForecast(biomeKey, forecast, 1);
    }

    /**
     * Adds a biome contribution to this region with an explicit weight.
     */
    public void addBiomeForecast(BiomeInstanceKey biomeKey, BiomeForecast forecast, int weight) {
        if (biomeKey == null || forecast == null) {
            return;
        }
        samples.add(biomeKey);
        biomeForecasts.add(forecast);
        biomeWeights.merge(biomeKey.biomeType(), Math.max(1, weight), Integer::sum);
    }

    /**
     * Builds unified curves by averaging the contributed biome forecasts and clamping
     * them to reasonable physical ranges.
     */
    public void finalizeAggregation() {
        if (biomeForecasts.isEmpty()) {
            buildFallback();
            return;
        }

        temperature = clampWeek(averageWeek(biomeForecasts, BiomeForecast::getTemperature), MIN_TEMPERATURE_C, MAX_TEMPERATURE_C);
        humidity = clampWeek(averageWeek(biomeForecasts, BiomeForecast::getHumidity), MIN_HUMIDITY, MAX_HUMIDITY);
        pressure = clampWeek(averageWeek(biomeForecasts, BiomeForecast::getPressure), MIN_PRESSURE_HPA, MAX_PRESSURE_HPA);
        wind = averageWindWeek(biomeForecasts, BiomeForecast::getWind);
        windDay = wind.length > 0 ? wind[0] : WindVector.fromBase(0f, 0f);
    }

    private void buildFallback() {
        temperature = flatWeek(12f);
        humidity = flatWeek(65f);
        pressure = flatWeek(1013.25f);
        wind = new WindVector[7];
        for (int i = 0; i < wind.length; i++) {
            wind[i] = WindVector.fromBase(1.2f, 0f);
        }
        windDay = wind[0];
    }

    private static float[][] flatWeek(float value) {
        float[][] arr = new float[7][2];
        for (int d = 0; d < 7; d++) {
            arr[d][0] = value;
            arr[d][1] = value;
        }
        return arr;
    }

    private static float[][] averageWeek(List<BiomeForecast> forecasts, Function<BiomeForecast, float[][]> extractor) {
        int days = 7;
        int cols = 2;
        float[][] result = new float[days][cols];
        int count = forecasts.size();

        for (BiomeForecast forecast : forecasts) {
            float[][] data = extractor.apply(forecast);
            if (data == null) {
                continue;
            }
            for (int d = 0; d < days; d++) {
                for (int c = 0; c < cols; c++) {
                    result[d][c] += data[d][c];
                }
            }
        }

        if (count == 0) {
            return result;
        }

        for (int d = 0; d < days; d++) {
            for (int c = 0; c < cols; c++) {
                result[d][c] /= count;
            }
        }
        return result;
    }

    private static float[][] clampWeek(float[][] week, float min, float max) {
        if (week == null) {
            return flatWeek(Mth.clamp(0f, min, max));
        }
        for (int d = 0; d < week.length; d++) {
            for (int c = 0; c < week[d].length; c++) {
                week[d][c] = Mth.clamp(week[d][c], min, max);
            }
        }
        return week;
    }

    private static WindVector[] averageWindWeek(List<BiomeForecast> forecasts, Function<BiomeForecast, WindVector[]> extractor) {
        WindVector[] result = new WindVector[7];
        for (int d = 0; d < result.length; d++) {
            float sumX = 0f;
            float sumZ = 0f;
            float sumGust = 0f;
            int count = 0;
            for (BiomeForecast forecast : forecasts) {
                WindVector[] week = extractor.apply(forecast);
                if (week == null || week.length <= d || week[d] == null) {
                    continue;
                }
                WindVector wind = week[d];
                float angle = wind.angleRadians();
                float speed = wind.baseSpeed();
                sumX += speed * (float) Math.cos(angle);
                sumZ += speed * (float) Math.sin(angle);
                sumGust += wind.gustSpeed();
                count++;
            }
            if (count == 0) {
                result[d] = WindVector.fromBase(0.5f, 0f);
                continue;
            }
            float avgX = sumX / count;
            float avgZ = sumZ / count;
            float avgSpeed = (float) Math.sqrt(avgX * avgX + avgZ * avgZ);
            float avgAngle = (float) Math.atan2(avgZ, avgX);
            float avgGust = sumGust / count;
            result[d] = new WindVector(avgSpeed, avgAngle, avgGust);
        }
        return result;
    }
}
