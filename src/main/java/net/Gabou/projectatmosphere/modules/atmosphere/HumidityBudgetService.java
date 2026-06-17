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

        float effectiveOceanFlux = resolveOceanFlux(oceanFlux, dominantBiomeId, currentHumidity, targetHumidity, updateScale, responseScale);

        float rainExchange = clampedRain
                * (0.0025f + profile.evaporationStrength() * 0.22f)
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
                effectiveOceanFlux,
                rainExchange,
                windTransport,
                forecastRestore,
                precipitationSink
        );
    }

    private static float resolveOceanFlux(
            float oceanFlux,
            String dominantBiomeId,
            float currentHumidity,
            float targetHumidity,
            float updateScale,
            float responseScale
    ) {
        if (Math.abs(oceanFlux) > 0.000001f || !isWaterBiome(dominantBiomeId)) {
            return oceanFlux;
        }

        float waterTarget = Math.max(targetHumidity, 0.78f);
        float deficit = Math.max(0f, waterTarget - currentHumidity);
        return Mth.clamp(deficit * 0.004f * updateScale * responseScale, 0f, 0.0025f);
    }

    private static boolean isWaterBiome(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return false;
        }
        String lower = biomeId.toLowerCase();
        return lower.contains("ocean")
                || lower.contains("river")
                || lower.contains("beach")
                || lower.contains("shore")
                || lower.contains("mangrove")
                || lower.contains("swamp");
    }
}
