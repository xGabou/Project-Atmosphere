package net.Gabou.projectatmosphere.modules.atmosphere;

import net.minecraft.util.Mth;

/**
 * Computes the humidity budget terms for a region.
 * Stage 2 keeps ocean and wind terms at zero; these are integrated in Stage 3.
 */
public final class HumidityBudgetService {
    private HumidityBudgetService() {
    }

    public static HumidityBudget compute(float currentHumidity,
                                         float targetHumidity,
                                         float sunlightFactor,
                                         float cloudCover,
                                         float rainIntensity,
                                         String dominantBiomeId,
                                         float oceanFlux,
                                         float windTransport,
                                         float updateScale,
                                         float responseScale) {
        HumiditySourceProfile profile = HumiditySourceProfile.fromClimate(dominantBiomeId, targetHumidity);
        float humidityError = targetHumidity - currentHumidity;
        float humidityDeficit = Math.max(0f, humidityError);
        float clampedRain = Mth.clamp(rainIntensity, 0f, 1f);
        float moistureActivity = 0.45f + (sunlightFactor * 0.55f);

        float solarDrying = sunlightFactor
                * (0.0010f + (1f - profile.dryingResistance()) * 0.0015f)
                * updateScale;
        solarDrying *= Mth.clamp(1f - cloudCover * 0.25f, 0.75f, 1f);

        float biomeEvaporation = humidityDeficit
                * profile.evaporationStrength()
                * moistureActivity
                * responseScale;

        float rainExchange = clampedRain
                * (0.005f + profile.evaporationStrength() * 0.5f)
                * responseScale;

        float forecastRestore = humidityError
                * profile.baseRetention()
                * responseScale;

        float precipitationSink = clampedRain
                * (0.0005f + (1f - profile.dryingResistance()) * 0.0005f)
                * updateScale;

        return new HumidityBudget(
                solarDrying,
                biomeEvaporation,
                oceanFlux,
                rainExchange,
                windTransport,
                forecastRestore,
                precipitationSink
        );
    }
}
