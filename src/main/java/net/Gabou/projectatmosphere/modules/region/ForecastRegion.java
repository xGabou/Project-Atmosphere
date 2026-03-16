package net.Gabou.projectatmosphere.modules.region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Region-level forecast container with weighted sections derived from biomes.
 * Also supports legacy aggregation from per-biome forecasts.
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
    private final List<BiomeInstanceKey> sourceBiomes;
    private Section[] sections;
    private RegionCurves curves;
    private final BiomeFallbackSnapshot fallbackSnapshot;

    private final List<BiomeInstanceKey> samples = new ArrayList<>();
    private final List<BiomeForecast> biomeForecasts = new ArrayList<>();
    private final Map<ResourceLocation, Integer> biomeWeights = new HashMap<>();

    private float[][] temperature;
    private float[][] humidity;
    private float[][] pressure;
    private WindVector[] wind;
    private WindVector windDay;
    private boolean legacyFinalized;

    public ForecastRegion(RegionInstanceKey id) {
        this(id, null);
    }

    public ForecastRegion(RegionInstanceKey id, @Nullable BlockPos anchor) {
        this(id, anchor, List.of(), new Section[0], null, null);
    }

    public ForecastRegion(RegionInstanceKey id,
                          List<BiomeInstanceKey> sourceBiomes,
                          Section[] sections,
                          RegionCurves curves,
                          BiomeFallbackSnapshot fallbackSnapshot) {
        this(id, null, sourceBiomes, sections, curves, fallbackSnapshot);
    }

    private ForecastRegion(RegionInstanceKey id,
                           @Nullable BlockPos anchor,
                           List<BiomeInstanceKey> sourceBiomes,
                           Section[] sections,
                           RegionCurves curves,
                           BiomeFallbackSnapshot fallbackSnapshot) {
        this.id = id;
        this.sourceBiomes = sourceBiomes == null ? List.of() : List.copyOf(sourceBiomes);
        this.sections = sections == null ? new Section[0] : sections.clone();
        this.curves = curves;
        this.fallbackSnapshot = fallbackSnapshot;
        this.anchor = resolveAnchor(id, anchor, this.sourceBiomes);
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

    public BiomeFallbackSnapshot fallbackSnapshot() {
        return fallbackSnapshot;
    }

    public List<BiomeInstanceKey> sourceBiomes() {
        return sourceBiomes;
    }

    public Section[] sections() {
        return sections.clone();
    }

    public BlockPos getAnchor() {
        return anchor;
    }

    public Map<ResourceLocation, Integer> getBiomeWeights() {
        if (biomeWeights.isEmpty()) {
            for (BiomeInstanceKey sample : getSamples()) {
                if (sample == null) {
                    continue;
                }
                biomeWeights.merge(sample.biomeType(), 1, Integer::sum);
            }
        }
        return biomeWeights;
    }

    public List<BiomeInstanceKey> getSamples() {
        return samples.isEmpty() ? sourceBiomes : samples;
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
            if (week != null && week.length > 0) {
                windDay = week[0];
            }
        }
        return windDay;
    }

    public float sampleTemperature(Vec3 inRegionPos, long gameTime) {
        if (curves == null) {
            return 0f;
        }
        return curves.sampleTemperature(inRegionPos, gameTime, sections);
    }

    public float sampleHumidity(Vec3 inRegionPos, long gameTime) {
        if (curves == null) {
            return 0f;
        }
        return curves.sampleHumidity(inRegionPos, gameTime, sections);
    }

    public float samplePressure(long gameTime) {
        if (curves == null) {
            return 0f;
        }
        return curves.samplePressure(gameTime, sections);
    }

    public WindVector sampleWind(long gameTime) {
        if (curves == null) {
            return WindVector.fromBase(0f, 0f);
        }
        return curves.sampleWind(gameTime, sections);
    }

    public float sampleStorm(long gameTime) {
        if (curves == null) {
            return 0f;
        }
        return curves.sampleStorm(gameTime, sections);
    }

    /**
     * Drop references to per-biome snapshots after aggregation to keep runtime region-only.
     */
    public void clearBiomeForecasts() {
        if (sections == null) {
            return;
        }
        for (Section s : sections) {
            s.clearSnapshot();
        }
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
        legacyFinalized = false;
    }

    /**
     * Builds unified curves by averaging the contributed biome forecasts and clamping
     * them to reasonable physical ranges.
     */
    public void finalizeAggregation() {
        if (legacyFinalized) {
            return;
        }
        if (biomeForecasts.isEmpty()) {
            if (curves == null) {
                buildFallback();
            } else {
                syncFromCurves();
            }
            legacyFinalized = true;
            return;
        }

        temperature = clampWeek(averageWeek(biomeForecasts, BiomeForecast::getTemperature), MIN_TEMPERATURE_C, MAX_TEMPERATURE_C);
        humidity = clampWeek(averageWeek(biomeForecasts, BiomeForecast::getHumidity), MIN_HUMIDITY, MAX_HUMIDITY);
        pressure = clampWeek(averageWeek(biomeForecasts, BiomeForecast::getPressure), MIN_PRESSURE_HPA, MAX_PRESSURE_HPA);
        wind = averageWindWeek(biomeForecasts, BiomeForecast::getWind);
        windDay = wind.length > 0 ? wind[0] : WindVector.fromBase(0f, 0f);
        curves = new DefaultRegionCurves(temperature, humidity, pressure, wind, new float[0]);
        legacyFinalized = true;
    }

    private void syncFromCurves() {
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
        for (int i = 0; i < wind.length; i++) {
            wind[i] = WindVector.fromBase(1.2f, 0f);
        }
        windDay = wind[0];
        curves = new DefaultRegionCurves(temperature, humidity, pressure, wind, new float[0]);
    }

    private static BlockPos resolveAnchor(RegionInstanceKey id, @Nullable BlockPos anchor, List<BiomeInstanceKey> sourceBiomes) {
        if (anchor != null) {
            return anchor;
        }
        if (sourceBiomes != null) {
            for (BiomeInstanceKey key : sourceBiomes) {
                if (key != null && key.samplePos() != null) {
                    return key.samplePos();
                }
            }
        }
        return id == null ? null : id.center();
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

    public static final class Section {
        private final float factor;
        private BiomeForecastSnapshot snapshot; // cleared after aggregation

        public Section(float factor, @Nullable BiomeForecastSnapshot snapshot) {
            this.factor = factor;
            this.snapshot = snapshot;
        }

        public float factor() {
            return factor;
        }

        public @Nullable BiomeForecastSnapshot snapshot() {
            return snapshot;
        }

        public void clearSnapshot() {
            this.snapshot = null;
        }
    }

    @Override
    public String toString() {
        return "ForecastRegion{" +
                "id=" + id +
                ", sourceBiomes=" + sourceBiomes.size() +
                ", sections=" + Arrays.toString(sections) +
                '}';
    }
}
