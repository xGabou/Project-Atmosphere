package net.Gabou.projectatmosphere.api.climate;

/** Read at observedGameTick from existing regional server state. Rain is an index, never mm/hour. */
public record RegionalClimateObservation(int regionX, int regionZ, int regionSize, long observedGameTick,
        double temperatureCelsius, double precipitationIntensity, double humidityFraction,
        double windXMetresPerSecond, double windZMetresPerSecond, double pressureHpa) { }
