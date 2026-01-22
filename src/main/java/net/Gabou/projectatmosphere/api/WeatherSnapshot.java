package net.Gabou.projectatmosphere.api;

/**
 * Immutable snapshot of atmosphere conditions at a position.
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
