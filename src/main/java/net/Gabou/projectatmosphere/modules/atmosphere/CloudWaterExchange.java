package net.Gabou.projectatmosphere.modules.atmosphere;

/**
 * Represents a single humidity <-> cloud-water exchange step.
 */
public record CloudWaterExchange(float humidityDelta,
                                 float cloudWaterDelta,
                                 float condensation,
                                 float reEvaporation,
                                 float precipitationDraw) {
}
