package net.Gabou.projectatmosphere.modules.region;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;

/**
 * Immutable copy of a biome forecast used for region aggregation and fallback snapshots.
 */
public final class BiomeForecastSnapshot {
    private final BiomeInstanceKey biomeKey;
    private final float[][] temperature;
    private final float[][] humidity;
    private final float[][] pressure;
    private final WindVector[] wind;

    public BiomeForecastSnapshot(BiomeInstanceKey biomeKey,
                                 @Nullable float[][] temperature,
                                 @Nullable float[][] humidity,
                                 @Nullable float[][] pressure,
                                 @Nullable WindVector[] wind) {
        this.biomeKey = biomeKey;
        this.temperature = copy2d(temperature);
        this.humidity = copy2d(humidity);
        this.pressure = copy2d(pressure);
        this.wind = copyWind(wind);
    }

    public BiomeInstanceKey biomeKey() {
        return biomeKey;
    }

    @Nullable
    public float[][] temperatureCurve() {
        return copy2d(temperature);
    }

    @Nullable
    public float[][] humidityCurve() {
        return copy2d(humidity);
    }

    @Nullable
    public float[][] pressureCurve() {
        return copy2d(pressure);
    }

    @Nullable
    public WindVector[] windCurve() {
        return copyWind(wind);
    }

    public static BiomeForecastSnapshot from(BiomeForecast forecast) {
        if (forecast == null || forecast.getBiomeKey() == null) {
            throw new IllegalArgumentException("BiomeForecastSnapshot requires a forecast with a biome key");
        }
        return new BiomeForecastSnapshot(
                forecast.getBiomeKey(),
                forecast.getTemperature(),
                forecast.getHumidity(),
                forecast.getPressure(),
                forecast.getWind());
    }

    private static float[][] copy2d(@Nullable float[][] src) {
        if (src == null) {
            return null;
        }
        float[][] copy = new float[src.length][];
        for (int i = 0; i < src.length; i++) {
            copy[i] = src[i] == null ? null : Arrays.copyOf(src[i], src[i].length);
        }
        return copy;
    }

    private static WindVector[] copyWind(@Nullable WindVector[] src) {
        if (src == null) {
            return null;
        }
        return Arrays.copyOf(src, src.length);
    }
}
