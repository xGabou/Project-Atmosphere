package net.Gabou.projectatmosphere.api;

/**
 * Immutable backend weather snapshot used by gameplay, sync, and future render consumers.
 */
public record WeatherSnapshot(
        float cloudCover,
        float rainIntensity,
        float temperatureC,
        float windSpeedMps,
        float windAngleRad,
        boolean isStorming,
        boolean isSnowing
) {
}
