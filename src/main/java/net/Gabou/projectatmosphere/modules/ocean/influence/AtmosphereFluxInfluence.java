package net.Gabou.projectatmosphere.modules.ocean.influence;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.ocean.AtmosVolumeInfluence;
import net.Gabou.projectatmosphere.modules.ocean.AtmosphericVolume;
import net.Gabou.projectatmosphere.modules.ocean.OceanUpdateContext;
import net.minecraft.util.Mth;

/**
 * Applies slow fluxes of humidity, pressure and temperature to atmospheric cells.
 */
public final class AtmosphereFluxInfluence implements AtmosVolumeInfluence {
    private final float humidityCoupling;
    private final float temperatureCoupling;
    private final float pressureCoupling;
    private final float windMixing;

    public AtmosphereFluxInfluence(float humidityCoupling, float temperatureCoupling, float pressureCoupling, float windMixing) {
        this.humidityCoupling = humidityCoupling;
        this.temperatureCoupling = temperatureCoupling;
        this.pressureCoupling = pressureCoupling;
        this.windMixing = windMixing;
    }

    public AtmosphereFluxInfluence() {
        this(0.004f, 0.003f, 0.0025f, 0.002f);
    }

    @Override
    public void applyTo(AtmosphericVolume volume, OceanUpdateContext context) {
        var basin = volume.basin();
        var state = volume.state();
        float weight = volume.weight();
        boolean oceanCell = volume.oceanCell();

        float humidityTarget = basin.getHumidityReservoir();
        float humidityDelta = (humidityTarget - state.getHumidity()) * humidityCoupling * weight;
        if (oceanCell) {
            humidityDelta *= 1.8f;
        }
        state.adjustHumidity(humidityDelta);

        float targetTemperature = basin.getSurfaceTemperature();
        if (!oceanCell) {
            targetTemperature -= 0.6f * (1f - Mth.clamp(weight, 0f, 1f));
        }
        float temperatureDelta = (targetTemperature - state.getTemperature()) * temperatureCoupling * weight;
        state.adjustTemperature(temperatureDelta);

        float targetPressure = basin.getBasePressure() + basin.getPressureOffset();
        float pressureDelta = (targetPressure - state.getPressure()) * pressureCoupling * weight;
        state.adjustPressure(pressureDelta);

        WindVector wind = ForecastOrchestrator.getWind(state.getKey(), context.gameTime());
        WindVector bias = basin.getWindBias();
        if (wind != null && bias != null && (bias.baseSpeed() > 0.01f || bias.gustSpeed() > 0.01f)) {
            float mix = windMixing * weight;
            float newSpeed = Mth.lerp(mix, wind.baseSpeed(), wind.baseSpeed() + bias.baseSpeed());
            float newAngle = wind.angleRadians() + (bias.angleRadians() * mix);
            float gust = Math.max(newSpeed, Mth.lerp(mix, wind.gustSpeed(), bias.gustSpeed()));
            state.setWind(new WindVector(Math.max(0f, newSpeed), newAngle, gust));
        }
    }
}
