package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig;
import net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range;
import net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Season;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the live atmospheric state for a sampled region.
 * Values are expressed in intuitive units (랍C, % humidity, hPa, m/s).
 */
public class RegionAtmosphereState {
    private static final int DAILY_SLOTS = 240;
    private static final float MIN_TEMPERATURE_C = -273.15f;
    private static final float MAX_REASONABLE_TEMPERATURE_C = 70f;
    private static final float MIN_PRESSURE_HPA = 900f;
    private static final float MAX_PRESSURE_HPA = 1080f;
    private static final long CLAMP_LOG_COOLDOWN_MS = 10000L;
    private static final Map<net.Gabou.projectatmosphere.util.RegionInstanceKey, Long> LAST_CLAMP_LOG = new ConcurrentHashMap<>();

    private final net.Gabou.projectatmosphere.util.RegionInstanceKey regionId;
    private final net.Gabou.projectatmosphere.util.RegionInstanceKey legacyKey;
    private final BlockPos anchor;
    private final ResourceLocation dominantBiome;
    private final float baseTemperature;
    private final float baseHumidity; // normalized 0..1
    private final float basePressure;
    private final float biomeSunlightMultiplier;
    private final float[] forecastTemperatureProfile;
    private final float[] forecastHumidityProfile;
    private final float[] forecastPressureProfile;
    private final float[] dailyTemperatureProfile;
    private final float[] dailyHumidityProfile;
    private final float[] dailyPressureProfile;
    private final float baselineMinTemp;
    private final float baselineMaxTemp;

    private float temperature;
    private float humidity; // normalized 0..1
    private float pressure;
    private WindVector wind;
    private float cloudCover;
    private float cloudWater;
    private float cycloneCloudFloor;
    private float cycloneRainFloor;
    private float sunlight;
    private float rainIntensity;

    RegionAtmosphereState(net.Gabou.projectatmosphere.util.RegionInstanceKey id, BlockPos anchor, ForecastRegion forecastRegion, ResourceLocation dominantBiome, float baseTemperature, float baseHumidity, float basePressure, WindVector wind) {
        this.regionId = id;
        this.legacyKey = id;
        this.anchor = anchor == null ? legacyKey.center() : anchor;
        this.dominantBiome = dominantBiome;
        this.baseTemperature = baseTemperature;
        this.baseHumidity = clampHumidity(baseHumidity);
        this.basePressure = basePressure;
        this.temperature = baseTemperature;
        this.humidity = clampHumidity(baseHumidity);
        this.pressure = basePressure;
        this.wind = wind;
        this.biomeSunlightMultiplier = computeBiomeSunlightMultiplier(dominantBiome);
        this.forecastTemperatureProfile = initialiseDailyCurve(deriveDailyCurve(forecastRegion.getTemperature(), baseTemperature), baseTemperature);
        this.forecastHumidityProfile = initialiseDailyCurveScaled(deriveDailyCurve(forecastRegion.getHumidity(), baseHumidity * 100f), this.humidity, 100f);
        this.forecastPressureProfile = initialiseDailyCurve(deriveDailyCurve(forecastRegion.getPressure(), basePressure), basePressure);
        this.dailyTemperatureProfile = forecastTemperatureProfile.clone();
        this.dailyHumidityProfile = forecastHumidityProfile.clone();
        this.dailyPressureProfile = forecastPressureProfile.clone();
        float[] bounds = computeTemperatureBounds(this.forecastTemperatureProfile, baseTemperature);
        this.baselineMinTemp = bounds[0];
        this.baselineMaxTemp = bounds[1];
    }

    public static RegionAtmosphereState fromForecast(net.Gabou.projectatmosphere.util.RegionInstanceKey id, ForecastRegion forecastRegion) {
        float temperature = averageDailyValue(forecastRegion.getTemperature(), 15f);
        float humidity = averageDailyValue(forecastRegion.getHumidity(), 60f) / 100f;
        float pressure = averageDailyValue(forecastRegion.getPressure(), 1013.25f);
        WindVector wind = null;
        if (forecastRegion.getWind() != null && forecastRegion.getWind().length > 0) {
            wind = forecastRegion.getWind()[0];
        }
        if (wind == null) {
            wind = WindVector.fromBase(1f, 0f);
        }
        ResourceLocation dominantBiome = selectDominantBiome(forecastRegion.getBiomeWeights());
        return new RegionAtmosphereState(id, forecastRegion.getAnchor(), forecastRegion, dominantBiome, temperature, humidity, pressure, wind);
    }

    private static ResourceLocation selectDominantBiome(Map<ResourceLocation, Integer> weights) {
        ResourceLocation dominant = null;
        int best = Integer.MIN_VALUE;
        for (Map.Entry<ResourceLocation, Integer> entry : weights.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                dominant = entry.getKey();
            }
        }
        return dominant;
    }

    private static float averageDailyValue(@Nullable float[][] week, float fallback) {
        if (week == null || week.length == 0) {
            return fallback;
        }
        float sum = 0f;
        int count = 0;
        for (float[] day : week) {
            if (day == null || day.length == 0) continue;
            if (day.length == 1) {
                sum += day[0];
                count++;
            } else {
                sum += (day[0] + day[Math.min(1, day.length - 1)]) * 0.5f;
                count++;
            }
        }
        return count == 0 ? fallback : sum / count;
    }

    public net.Gabou.projectatmosphere.util.RegionInstanceKey getRegionId() {
        return regionId;
    }

    public BlockPos getPosition() {
        return anchor;
    }

    public ResourceLocation getDominantBiome() {
        return dominantBiome;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        float clamped = clampTemperature(temperature);
        maybeLogTemperatureClamp(temperature, clamped);
        this.temperature = clamped;
    }

    public void adjustTemperature(float delta) {
        setTemperature(this.temperature + delta);
    }

    public float getHumidity() {
        return humidity;
    }

    public float getHumidityPercent() {
        return humidity * 100f;
    }

    public void setHumidity(float humidity) {
        this.humidity = clampHumidity(humidity);
    }

    public void adjustHumidity(float delta) {
        setHumidity(this.humidity + delta);
    }

    public float getPressure() {
        return pressure;
    }

    public void setPressure(float pressure) {
        this.pressure = Mth.clamp(pressure, MIN_PRESSURE_HPA, MAX_PRESSURE_HPA);
    }

    public void adjustPressure(float delta) {
        setPressure(this.pressure + delta);
    }

    public WindVector getWind() {
        return wind;
    }

    public void setWind(WindVector wind) {
        this.wind = wind;
    }

    public float getWindStrength() {
        return wind == null ? 0f : Math.max(0f, wind.baseSpeed());
    }

    public float getCloudCover() {
        return cloudCover;
    }

    public void setCloudCover(float value) {
        this.cloudCover = Mth.clamp(value, 0f, 1f);
    }

    public float getCloudWater() {
        return cloudWater;
    }

    public void setCloudWater(float value) {
        this.cloudWater = Mth.clamp(value, 0f, 1.2f);
    }

    public void adjustCloudWater(float delta) {
        setCloudWater(this.cloudWater + delta);
    }

    public float getCycloneCloudFloor() {
        return cycloneCloudFloor;
    }

    public float getCycloneRainFloor() {
        return cycloneRainFloor;
    }

    public void applyCycloneVisualFloor(float cloudFloor, float rainFloor) {
        cycloneCloudFloor = Math.max(cycloneCloudFloor, Mth.clamp(cloudFloor, 0f, 1f));
        cycloneRainFloor = Math.max(cycloneRainFloor, Mth.clamp(rainFloor, 0f, 1f));
    }

    public void decayCycloneVisualFloor(float cloudDecay, float rainDecay) {
        cycloneCloudFloor = Math.max(0f, cycloneCloudFloor - Math.max(0f, cloudDecay));
        cycloneRainFloor = Math.max(0f, cycloneRainFloor - Math.max(0f, rainDecay));
    }

    public float getBiomeSunlightMultiplier() {
        return biomeSunlightMultiplier;
    }

    public float getSunlight() {
        return sunlight;
    }

    public void setSunlight(float sunlight) {
        this.sunlight = Mth.clamp(sunlight, 0f, 1f);
    }

    public float getRainIntensity() {
        return rainIntensity;
    }

    public void setRainIntensity(float rainIntensity) {
        this.rainIntensity = Math.max(0f, rainIntensity);
    }

    public void dampenRain(float factor) {
        rainIntensity = Math.max(0f, rainIntensity - factor);
    }

    public void recordDailySnapshot(long dayTime) {
        int slot = (int) ((dayTime % 24000L) / 100L);
        if (slot < 0 || slot >= dailyTemperatureProfile.length) {
            return;
        }
        dailyTemperatureProfile[slot] = temperature;
        dailyHumidityProfile[slot] = humidity;
        dailyPressureProfile[slot] = pressure;
    }

    public float[] getDailyTemperatureProfile() {
        return dailyTemperatureProfile.clone();
    }

    public float[] getDailyHumidityProfile() {
        return dailyHumidityProfile.clone();
    }

    public float[] getDailyPressureProfile() {
        return dailyPressureProfile.clone();
    }

    public float getTargetTemperature(long dayTime) {
        if (forecastTemperatureProfile.length == 0) {
            return temperature;
        }
        float position = (float) Math.floorMod(dayTime, 24000L) / 100f;
        if (position >= forecastTemperatureProfile.length - 1f) {
            return forecastTemperatureProfile[forecastTemperatureProfile.length - 1];
        }
        return interpolate(forecastTemperatureProfile, position);
    }

    public float getTargetHumidity(long dayTime) {
        if (forecastHumidityProfile.length == 0) {
            return humidity;
        }
        float position = (float) Math.floorMod(dayTime, 24000L) / 100f;
        if (position >= forecastHumidityProfile.length - 1f) {
            return clampHumidity(forecastHumidityProfile[forecastHumidityProfile.length - 1]);
        }
        return clampHumidity(interpolate(forecastHumidityProfile, position));
    }

    public float getTargetPressure(long dayTime) {
        if (forecastPressureProfile.length == 0) {
            return pressure;
        }
        float position = (float) Math.floorMod(dayTime, 24000L) / 100f;
        if (position >= forecastPressureProfile.length - 1f) {
            return Mth.clamp(forecastPressureProfile[forecastPressureProfile.length - 1], MIN_PRESSURE_HPA, MAX_PRESSURE_HPA);
        }
        return Mth.clamp(interpolate(forecastPressureProfile, position), MIN_PRESSURE_HPA, MAX_PRESSURE_HPA);
    }

    public void relaxTowardBase(float factor) {
        temperature += (baseTemperature - temperature) * factor;
        humidity += (baseHumidity - humidity) * factor;
        pressure += (basePressure - pressure) * factor;
        humidity = clampHumidity(humidity);
        pressure = Mth.clamp(pressure, MIN_PRESSURE_HPA, MAX_PRESSURE_HPA);
    }

    public void relaxTemperatureAndPressureTowardBase(float factor) {
        temperature += (baseTemperature - temperature) * factor;
        pressure += (basePressure - pressure) * factor;
        pressure = Mth.clamp(pressure, MIN_PRESSURE_HPA, MAX_PRESSURE_HPA);
    }

    public double distanceTo(double x, double z) {
        double dx = anchor.getX() - x;
        double dz = anchor.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public float getBaselineMinTemperature() {
        return baselineMinTemp;
    }

    public float getBaselineMaxTemperature() {
        return baselineMaxTemp;
    }

    public float getBaselineTemperatureSpan() {
        return Math.max(0.001f, baselineMaxTemp - baselineMinTemp);
    }

    public float getSunlightDrivenTemperature(float sunlightFactor) {
        float clamped = Mth.clamp(sunlightFactor, 0f, 1f);
        return Mth.lerp(clamped, baselineMinTemp, baselineMaxTemp);
    }

    // ---------------------------------------------------------------------
    // Legacy, fallback, diagnostic, or rarely used code pending cleanup
    // ---------------------------------------------------------------------
    /**
     * Legacy compatibility path: wraps a biome forecast into a region forecast using the default grid.
     */
    @Deprecated
    public static RegionAtmosphereState fromForecast(BiomeInstanceKey biomeKey, net.Gabou.projectatmosphere.modules.core.BiomeForecast forecast) {
        ForecastRegion region = createLegacyForecastRegion(biomeKey, forecast);
        return fromForecast(region.getKey(), region);
    }

    /**
     * Legacy view for components still expecting RegionInstanceKey.
     */
    @Deprecated
    public net.Gabou.projectatmosphere.util.RegionInstanceKey getKey() {
        return legacyKey;
    }

    private static float clampHumidity(float value) {
        return Mth.clamp(value, 0f, 1.2f);
    }

    private static float computeBiomeSunlightMultiplier(@Nullable ResourceLocation biomeId) {
        if (biomeId == null) {
            return 1f;
        }
        float sum = 0f;
        int samples = 0;
        for (Season season : Season.values()) {
            Range range = BiomeTempConfig.getRange(biomeId, season);
            if (range == null) {
                continue;
            }
            sum += 0.5f * (range.minC() + range.maxC());
            samples++;
        }
        if (samples == 0) {
            return 1f;
        }
        float mean = sum / samples;
        float normalized = Mth.clamp((mean + 20f) / 50f, 0f, 1f);
        return 0.6f + normalized * 0.8f;
    }

    private static float[] initialiseDailyCurve(@Nullable float[] source, float fallback) {
        if (source != null && source.length > 0) {
            return resampleDailyCurve(source);
        }
        float[] arr = new float[DAILY_SLOTS];
        Arrays.fill(arr, fallback);
        return arr;
    }

    private static float[] initialiseDailyCurveScaled(@Nullable float[] source, float normalizedFallback, float scale) {
        if (source != null && source.length > 0) {
            float[] resampled = resampleDailyCurve(source);
            for (int i = 0; i < resampled.length; i++) {
                resampled[i] = Mth.clamp(resampled[i] / scale, 0f, 1.2f);
            }
            return resampled;
        }
        float[] arr = new float[DAILY_SLOTS];
        Arrays.fill(arr, Mth.clamp(normalizedFallback, 0f, 1.2f));
        return arr;
    }

    private static float[] deriveDailyCurve(@Nullable float[][] week, float fallback) {
        float[] arr = new float[24];
        if (week == null || week.length == 0 || week[0] == null || week[0].length == 0) {
            Arrays.fill(arr, fallback);
            return arr;
        }
        float dayMin = week[0][0];
        float dayMax = week[0][Math.min(1, week[0].length - 1)];
        for (int hour = 0; hour < arr.length; hour++) {
            float phase = (float) Math.sin(((hour - 6) / 24f) * (float) (Math.PI * 2)) * 0.5f + 0.5f;
            arr[hour] = Mth.lerp(phase, dayMin, dayMax);
        }
        return arr;
    }

    private static float[] resampleDailyCurve(float[] source) {
        float[] target = new float[DAILY_SLOTS];
        if (source.length == DAILY_SLOTS) {
            System.arraycopy(source, 0, target, 0, DAILY_SLOTS);
            return target;
        }
        float maxIndex = source.length - 1f;
        for (int i = 0; i < DAILY_SLOTS; i++) {
            float position = maxIndex * i / (DAILY_SLOTS - 1f);
            target[i] = interpolate(source, position);
        }
        return target;
    }

    private static float interpolate(float[] source, float position) {
        int lower = (int) Math.floor(position);
        int upper = Math.min(source.length - 1, lower + 1);
        float t = position - lower;
        float lowerValue = source[lower];
        float upperValue = source[upper];
        return lowerValue + (upperValue - lowerValue) * t;
    }

    private static float[] computeTemperatureBounds(float[] profile, float fallback) {
        if (profile == null || profile.length == 0) {
            return new float[]{fallback - 2f, fallback + 2f};
        }
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (float value : profile) {
            if (Float.isNaN(value)) {
                continue;
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        if (!Float.isFinite(min) || !Float.isFinite(max)) {
            return new float[]{fallback - 2f, fallback + 2f};
        }
        if (min == max) {
            min -= 2f;
            max += 2f;
        }
        return new float[]{min, max};
    }

    private float clampTemperature(float value) {
        float ceiling = Math.max(MAX_REASONABLE_TEMPERATURE_C, baselineMaxTemp + 25f);
        float clamped = Math.max(MIN_TEMPERATURE_C, Math.min(value, ceiling));
        if (!Float.isFinite(clamped)) {
            return Math.max(MIN_TEMPERATURE_C, Math.min(baseTemperature, ceiling));
        }
        return clamped;
    }

    private void maybeLogTemperatureClamp(float original, float clamped) {
        if (!AtmoCommonConfig.DEBUG_MODE.get()) {
            return;
        }
        if (Float.isFinite(original) && Math.abs(original - clamped) < 0.01f) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_CLAMP_LOG.get(regionId);
        if (last != null && (now - last) < CLAMP_LOG_COOLDOWN_MS) {
            return;
        }
        LAST_CLAMP_LOG.put(regionId, now);
        ProjectAtmosphere.LOGGER.warn(
                "[Atmosphere] Temperature clamp applied. region={} biome={} anchor={} base={} baselineMin={} baselineMax={} input={} clamped={}",
                regionId,
                dominantBiome,
                anchor,
                baseTemperature,
                baselineMinTemp,
                baselineMaxTemp,
                original,
                clamped,
                new RuntimeException("Temperature clamp trace")
        );
    }

    private static ForecastRegion createLegacyForecastRegion(BiomeInstanceKey biomeKey, net.Gabou.projectatmosphere.modules.core.BiomeForecast forecast) {
        ForecastRegion region = new ForecastRegion(net.Gabou.projectatmosphere.util.RegionInstanceKey.from(biomeKey.samplePos()));
        region.addBiomeForecast(biomeKey, forecast);
        region.finalizeAggregation();
        return region;
    }
}
