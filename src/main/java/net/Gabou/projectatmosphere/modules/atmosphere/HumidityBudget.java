package net.Gabou.projectatmosphere.modules.atmosphere;

/**
 * Diagnostic scaffold for the humidity budget model.
 * Phase A keeps several terms at zero while the scheduler still runs the
 * legacy equation, but the structure is stable for later migration.
 */
public record HumidityBudget(float solarDrying,
                             float biomeEvaporation,
                             float oceanFlux,
                             float rainExchange,
                             float windTransport,
                             float forecastRestore,
                             float precipitationSink) {

    public float netDelta() {
        return biomeEvaporation + oceanFlux + rainExchange + windTransport + forecastRestore
                - solarDrying - precipitationSink;
    }
}
