package net.Gabou.projectatmosphere.modules.fog;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.util.Mth;

public final class FogHeuristics {
    private FogHeuristics() {
    }

    public static FogProfile sample(float humidityPercent, float wetBiomeFactor, float rainIntensity) {
        float humidityStart = AtmoCommonConfig.FOG_HUMIDITY_START_PERCENT.get().floatValue();
        float humidityFull = AtmoCommonConfig.FOG_HUMIDITY_FULL_PERCENT.get().floatValue();
        float humidityFactor = remapClamped(humidityPercent, humidityStart, humidityFull);

        float strength = humidityFactor * (0.75F + wetBiomeFactor * 0.35F);
        strength += wetBiomeFactor * AtmoCommonConfig.FOG_WET_BIOME_BASE_STRENGTH.get().floatValue();
        strength += rainIntensity * AtmoCommonConfig.FOG_RAIN_BOOST.get().floatValue();
        strength = Mth.clamp(strength, 0.0F, 1.0F);
        strength = Mth.sqrt(strength);

        return new FogProfile(strength, humidityFactor, wetBiomeFactor, rainIntensity);
    }

    public static FogProfile debugSample(float strength) {
        float clamped = Mth.clamp(strength, 0.0F, 1.0F);
        return new FogProfile(clamped, clamped, clamped, 0.0F);
    }

    public static FogProfile max(FogProfile first, FogProfile second) {
        if (first == null || first == FogProfile.NONE) {
            return second == null ? FogProfile.NONE : second;
        }
        if (second == null || second == FogProfile.NONE) {
            return first;
        }
        return new FogProfile(
                Math.max(first.strength(), second.strength()),
                Math.max(first.humidityFactor(), second.humidityFactor()),
                Math.max(first.wetBiomeFactor(), second.wetBiomeFactor()),
                Math.max(first.rainFactor(), second.rainFactor())
        );
    }

    public static float remapClamped(float value, float start, float end) {
        if (end <= start) {
            return value >= end ? 1.0F : 0.0F;
        }
        return Mth.clamp((value - start) / (end - start), 0.0F, 1.0F);
    }

    public record FogProfile(float strength, float humidityFactor, float wetBiomeFactor, float rainFactor) {
        public static final FogProfile NONE = new FogProfile(0.0F, 0.0F, 0.0F, 0.0F);
    }
}
