package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
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

/**
 * Holds the live atmospheric state for a sampled biome region.
 * Values are expressed in intuitive units (°C, % humidity, hPa, m/s).
 */
public class RegionAtmosphereState {
    private static final int DAILY_SLOTS = 240;

    private final BiomeInstanceKey key;
    private final float baseTemperature;
    private final float baseHumidity; // normalized 0..1
    private final float basePressure;
    private final float biomeSunlightMultiplier;
    private final float[] dailyTemperatureProfile;
    private final float[] dailyHumidityProfile;
    private final float[] dailyPressureProfile;

    private float temperature;
    private float humidity; // normalized 0..1
    private float pressure;
    private WindVector wind;
    private float cloudCover;
    private float sunlight;
    private float rainIntensity;

    RegionAtmosphereState(BiomeInstanceKey key, BiomeForecast forecast, float baseTemperature, float baseHumidity, float basePressure, WindVector wind) {
        this.key = key;
        this.baseTemperature = baseTemperature;
        this.baseHumidity = clampHumidity(baseHumidity);
        this.basePressure = basePressure;
        this.temperature = baseTemperature;
        this.humidity = clampHumidity(baseHumidity);
        this.pressure = basePressure;
        this.wind = wind;
        this.biomeSunlightMultiplier = computeBiomeSunlightMultiplier(key.biomeType());
        this.dailyTemperatureProfile = initialiseDailyCurve(forecast.getTemperatureDay(), baseTemperature);
        this.dailyHumidityProfile = initialiseDailyCurveScaled(forecast.getHumidityDay(), this.humidity, 100f);
        this.dailyPressureProfile = initialiseDailyCurve(forecast.getPressureDay(), basePressure);
    }

    public static RegionAtmosphereState fromForecast(BiomeInstanceKey key, BiomeForecast forecast) {
        float temperature = averageDailyValue(forecast.getTemperature(), 15f);
        float humidity = averageDailyValue(forecast.getHumidity(), 60f) / 100f;
        float pressure = averageDailyValue(forecast.getPressure(), 1013.25f);
        WindVector wind = null;
        if (forecast.getWind() != null && forecast.getWind().length > 0) {
            wind = forecast.getWind()[0];
        }
        if (wind == null) {
            wind = WindVector.fromBase(1f, 0f);
        }
        return new RegionAtmosphereState(key, forecast, temperature, humidity, pressure, wind);
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

    public BiomeInstanceKey getKey() {
        return key;
    }

    public BlockPos getPosition() {
        return key.samplePos();
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public void adjustTemperature(float delta) {
        temperature += delta;
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
        this.pressure = Mth.clamp(pressure, 870f, 1085f);
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

    public void relaxTowardBase(float factor) {
        temperature += (baseTemperature - temperature) * factor;
        humidity += (baseHumidity - humidity) * factor;
        pressure += (basePressure - pressure) * factor;
        humidity = clampHumidity(humidity);
        pressure = Mth.clamp(pressure, 870f, 1085f);
    }

    public double distanceTo(double x, double z) {
        double dx = key.samplePos().getX() - x;
        double dz = key.samplePos().getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static float clampHumidity(float value) {
        return Mth.clamp(value, 0f, 1.2f);
    }

    private static float computeBiomeSunlightMultiplier(ResourceLocation biomeId) {
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
}
