package net.Gabou.projectatmosphere.api.climate;

/** Detached climatological context at a representative, explicitly identified point. Not weather. */
public record GeographicClimate(int blockX, int blockZ, double latitudeDegrees,
        double baselineTemperatureCelsius, double baselineRainfallMmPerYear,
        double seaRelativeElevationBlocks, double coastDistanceBlocks, double oceanDistanceBlocks,
        boolean marine, double windX, double windZ) {
    public GeographicClimate {
        for (double v : new double[]{latitudeDegrees, baselineTemperatureCelsius, baselineRainfallMmPerYear,
                seaRelativeElevationBlocks, coastDistanceBlocks, oceanDistanceBlocks, windX, windZ}) {
            if (!Double.isFinite(v)) throw new IllegalArgumentException("Non-finite climate context");
        }
        if (baselineRainfallMmPerYear < 0 || coastDistanceBlocks < 0 || oceanDistanceBlocks < 0) {
            throw new IllegalArgumentException("Negative climate magnitude");
        }
    }
}
