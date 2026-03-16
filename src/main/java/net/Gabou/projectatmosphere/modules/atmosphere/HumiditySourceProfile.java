package net.Gabou.projectatmosphere.modules.atmosphere;

import net.minecraft.util.Mth;

/**
 * Lightweight humidity behavior profile derived from the regional climate target.
 * Stage 2 uses it to differentiate wet and dry regions without hard floors.
 */
public record HumiditySourceProfile(float baseRetention,
                                    float evaporationStrength,
                                    float dryingResistance) {

    public static HumiditySourceProfile fromClimate(String dominantBiomeId, float targetHumidity) {
        float targetWetness = Mth.clamp((targetHumidity - 0.15f) / 0.70f, 0f, 1f);
        float wetness = Mth.clamp(targetWetness + biomeMoistureBias(dominantBiomeId), 0f, 1f);
        return new HumiditySourceProfile(
                Mth.lerp(wetness, 0.020f, 0.030f),
                Mth.lerp(wetness, 0.008f, 0.015f),
                Mth.lerp(wetness, 0.15f, 0.70f)
        );
    }

    private static float biomeMoistureBias(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return 0f;
        }
        String lower = biomeId.toLowerCase();
        if (containsAny(lower, "jungle", "mangrove", "swamp", "marsh", "bog", "rainforest")) {
            return 0.18f;
        }
        if (containsAny(lower, "ocean", "river", "beach", "forest", "mushroom")) {
            return 0.08f;
        }
        if (containsAny(lower, "desert", "badlands")) {
            return -0.18f;
        }
        if (containsAny(lower, "savanna")) {
            return -0.08f;
        }
        return 0f;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
