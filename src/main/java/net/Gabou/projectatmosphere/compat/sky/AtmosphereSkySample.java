package net.Gabou.projectatmosphere.compat.sky;

public record AtmosphereSkySample(
        float humidityPercent,
        float rainIntensity,
        float cloudCover,
        float recentRainFactor,
        float clearingTrend,
        float wetBiomeFactor,
        float temperatureC,
        float daylightFactor,
        float nightFactor,
        float sunVisibility,
        float atmosphericClarity,
        float cloudBreakup,
        boolean canSeeSky
) {
    public static final AtmosphereSkySample NONE = new AtmosphereSkySample(
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            false
    );
}
