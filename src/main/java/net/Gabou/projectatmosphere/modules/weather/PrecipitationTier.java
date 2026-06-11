package net.Gabou.projectatmosphere.modules.weather;

import net.minecraft.util.Mth;

public enum PrecipitationTier {
    NONE(0.0F, 0.0F, 0.0F, 0.0F),
    SMALL_RAIN(0.25F, 0.35F, 0.18F, 0.08F),
    NORMAL_RAIN(0.55F, 0.70F, 0.42F, 0.18F),
    HEAVY_RAIN(0.90F, 1.00F, 0.75F, 0.34F);

    private final float representativeIntensity;
    private final float particleDensity;
    private final float splashIntensity;
    private final float fogBoost;

    PrecipitationTier(float representativeIntensity, float particleDensity, float splashIntensity, float fogBoost) {
        this.representativeIntensity = representativeIntensity;
        this.particleDensity = particleDensity;
        this.splashIntensity = splashIntensity;
        this.fogBoost = fogBoost;
    }

    public float getRepresentativeIntensity() {
        return representativeIntensity;
    }

    public float getParticleDensity() {
        return particleDensity;
    }

    public float getSplashIntensity() {
        return splashIntensity;
    }

    public float getFogBoost() {
        return fogBoost;
    }

    public static PrecipitationTier fromRainIntensity(float rainIntensity) {
        float intensity = Mth.clamp(rainIntensity, 0.0F, 1.0F);
        if (intensity >= 0.70F) {
            return HEAVY_RAIN;
        }
        if (intensity >= 0.35F) {
            return NORMAL_RAIN;
        }
        if (intensity > 0.02F) {
            return SMALL_RAIN;
        }
        return NONE;
    }
}
