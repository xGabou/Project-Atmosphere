package net.Gabou.projectatmosphere.modules.region;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Region-level forecast container. The region key is the only runtime forecast
 * identity; biome ids are retained only as aggregate weights for diagnostics
 * and climate modifiers.
 */
public final class ForecastRegion {
    private static final float MIN_TEMPERATURE_C = -90f;
    private static final float MAX_TEMPERATURE_C = 70f;
    private static final float MIN_PRESSURE_HPA = 880f;
    private static final float MAX_PRESSURE_HPA = 1085f;
    private static final float MIN_HUMIDITY = 0f;
    private static final float MAX_HUMIDITY = 100f;

    private final RegionInstanceKey id;
    private final BlockPos anchor;
    private final Map<ResourceLocation, Integer> biomeWeights;
    private RegionCurves curves;

    private float[][] temperature;
    private float[][] humidity;
    private float[][] pressure;
    private WindVector[] wind;
    private WindVector windDay;

    public ForecastRegion(RegionInstanceKey id) {
        this(id, id == null ? null : id.center());
    }

    public ForecastRegion(RegionInstanceKey id, @Nullable BlockPos anchor) {
        this(id, anchor, null, Map.of());
    }

    public ForecastRegion(RegionInstanceKey id,
                          @Nullable BlockPos anchor,
                          @Nullable RegionCurves curves,
                          @Nullable Map<ResourceLocation, Integer> biomeWeights) {
        this.id = id;
        this.anchor = anchor == null && id != null ? id.center() : anchor;
        this.curves = curves;
        this.biomeWeights = new HashMap<>();
        if (biomeWeights != null) {
            biomeWeights.forEach((key, value) -> {
                if (key != null && value != null && value > 0) {
                    this.biomeWeights.merge(key, value, Integer::sum);
                }
            });
        }
        syncFromCurvesOrFallback();
    }

    public RegionInstanceKey id() {
        return id;
    }

    public RegionInstanceKey getKey() {
        return id;
    }

    public RegionCurves curves() {
        return curves;
    }

    public BlockPos getAnchor() {
        return anchor;
    }

    public Map<ResourceLocation, Integer> getBiomeWeights() {
        return biomeWeights;
    }

    public ResourceLocation getDominantBiome() {
        ResourceLocation dominant = null;
        int best = Integer.MIN_VALUE;
        for (Map.Entry<ResourceLocation, Integer> entry : biomeWeights.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > best) {
                dominant = entry.getKey();
                best = entry.getValue();
            }
        }
        return dominant;
    }

    public float[][] getTemperature() {
        if (temperature == null && curves != null) {
            temperature = curves.temperatureWeek();
        }
        return temperature;
    }

    public float[][] getHumidity() {
        if (humidity == null && curves != null) {
            humidity = curves.humidityWeek();
        }
        return humidity;
    }

    public float[][] getPressure() {
        if (pressure == null && curves != null) {
            pressure = curves.pressureWeek();
        }
        return pressure;
    }

    public WindVector[] getWind() {
        if (wind == null && curves != null) {
            wind = curves.windWeek();
        }
        if (windDay == null && wind != null && wind.length > 0) {
            windDay = wind[0];
        }
        return wind;
    }

    public WindVector getWindDay() {
        if (windDay == null) {
            WindVector[] week = getWind();
            windDay = week != null && week.length > 0 ? week[0] : WindVector.fromBase(0f, 0f);
        }
        return windDay;
    }

    public void finalizeAggregation() {
        syncFromCurvesOrFallback();
    }

    public float sampleTemperature(Vec3 inRegionPos, long gameTime) {
        return curves == null ? 0f : curves.sampleTemperature(inRegionPos, gameTime);
    }

    public float sampleHumidity(Vec3 inRegionPos, long gameTime) {
        return curves == null ? 0f : curves.sampleHumidity(inRegionPos, gameTime);
    }

    public float samplePressure(long gameTime) {
        return curves == null ? 0f : curves.samplePressure(gameTime);
    }

    public WindVector sampleWind(long gameTime) {
        return curves == null ? WindVector.fromBase(0f, 0f) : curves.sampleWind(gameTime);
    }

    public float sampleStorm(long gameTime) {
        return curves == null ? 0f : curves.sampleStorm(gameTime);
    }

    public static ForecastRegion aggregate(RegionInstanceKey id,
                                           BlockPos anchor,
                                           List<GeneratedSample> samples,
                                           @Nullable float[] stormWeek) {
        if (samples == null || samples.isEmpty()) {
            return new ForecastRegion(id, anchor);
        }

        Map<ResourceLocation, Integer> weights = new HashMap<>();
        int totalWeight = 0;
        for (GeneratedSample sample : samples) {
            if (sample == null || sample.sample() == null) {
                continue;
            }
            int weight = Math.max(1, sample.sample().weight());
            totalWeight += weight;
            weights.merge(sample.sample().biomeId(), weight, Integer::sum);
        }
        if (totalWeight <= 0) {
            return new ForecastRegion(id, anchor);
        }

        float[][] temperature = weightedWeek(samples, GeneratedSample::temperature, totalWeight, MIN_TEMPERATURE_C, MAX_TEMPERATURE_C);
        float[][] humidity = weightedWeek(samples, GeneratedSample::humidity, totalWeight, MIN_HUMIDITY, MAX_HUMIDITY);
        float[][] pressure = weightedWeek(samples, GeneratedSample::pressure, totalWeight, MIN_PRESSURE_HPA, MAX_PRESSURE_HPA);
        WindVector[] wind = weightedWind(samples, totalWeight);
        RegionCurves curves = new DefaultRegionCurves(temperature, humidity, pressure, wind, stormWeek == null ? new float[0] : stormWeek);
        return new ForecastRegion(id, anchor, curves, weights);
    }

    private void syncFromCurvesOrFallback() {
        if (curves == null) {
            buildFallback();
            return;
        }
        temperature = curves.temperatureWeek();
        humidity = curves.humidityWeek();
        pressure = curves.pressureWeek();
        wind = curves.windWeek();
        windDay = wind != null && wind.length > 0 ? wind[0] : WindVector.fromBase(0f, 0f);
    }

    private void buildFallback() {
        temperature = flatWeek(12f);
        humidity = flatWeek(65f);
        pressure = flatWeek(1013.25f);
        wind = new WindVector[7];
        Arrays.fill(wind, WindVector.fromBase(1.2f, 0f));
        windDay = wind[0];
        curves = new DefaultRegionCurves(temperature, humidity, pressure, wind, new float[0]);
    }

    private static float[][] flatWeek(float value) {
        float[][] arr = new float[7][2];
        for (int d = 0; d < 7; d++) {
            arr[d][0] = value;
            arr[d][1] = value;
        }
        return arr;
    }

    private static float[][] weightedWeek(List<GeneratedSample> samples,
                                          CurveExtractor extractor,
                                          int totalWeight,
                                          float min,
                                          float max) {
        float[][] result = new float[7][2];
        for (GeneratedSample sample : samples) {
            float[][] week = extractor.get(sample);
            if (week == null) {
                continue;
            }
            int weight = Math.max(1, sample.sample().weight());
            for (int d = 0; d < result.length && d < week.length; d++) {
                if (week[d] == null) {
                    continue;
                }
                for (int c = 0; c < result[d].length && c < week[d].length; c++) {
                    result[d][c] += week[d][c] * weight;
                }
            }
        }
        for (int d = 0; d < result.length; d++) {
            for (int c = 0; c < result[d].length; c++) {
                result[d][c] = Mth.clamp(result[d][c] / totalWeight, min, max);
            }
        }
        return result;
    }

    private static WindVector[] weightedWind(List<GeneratedSample> samples, int totalWeight) {
        WindVector[] result = new WindVector[7];
        for (int d = 0; d < result.length; d++) {
            float sumX = 0f;
            float sumZ = 0f;
            float sumGust = 0f;
            int usedWeight = 0;
            for (GeneratedSample sample : samples) {
                WindVector[] week = sample.wind();
                if (week == null || week.length <= d || week[d] == null) {
                    continue;
                }
                int weight = Math.max(1, sample.sample().weight());
                WindVector wind = week[d];
                float angle = wind.angleRadians();
                float speed = wind.baseSpeed();
                sumX += speed * (float) Math.cos(angle) * weight;
                sumZ += speed * (float) Math.sin(angle) * weight;
                sumGust += wind.gustSpeed() * weight;
                usedWeight += weight;
            }
            int divisor = usedWeight > 0 ? usedWeight : totalWeight;
            if (divisor <= 0) {
                result[d] = WindVector.fromBase(1.2f, 0f);
                continue;
            }
            float avgX = sumX / divisor;
            float avgZ = sumZ / divisor;
            float avgSpeed = (float) Math.sqrt(avgX * avgX + avgZ * avgZ);
            float avgAngle = (float) Math.atan2(avgZ, avgX);
            float avgGust = sumGust / divisor;
            result[d] = new WindVector(avgSpeed, avgAngle, avgGust);
        }
        return result;
    }

    @FunctionalInterface
    private interface CurveExtractor {
        float[][] get(GeneratedSample sample);
    }

    public record GeneratedSample(RegionBiomeSample sample,
                                  float[][] temperature,
                                  float[][] humidity,
                                  float[][] pressure,
                                  WindVector[] wind) {
    }

    @Override
    public String toString() {
        return "ForecastRegion{" +
                "id=" + id +
                ", anchor=" + anchor +
                ", biomeWeights=" + biomeWeights +
                '}';
    }
}
