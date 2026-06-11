package net.Gabou.projectatmosphere.modules.weather;

import net.minecraft.util.Mth;

public enum SnowTier {
    NONE(0.0F, 0.0F, 1.0F),
    SNOWY_DAY(0.30F, 0.12F, 0.82F),
    SNOWSTORM(0.68F, 0.36F, 0.52F),
    BLIZZARD(1.0F, 0.72F, 0.24F);

    private final float particleDensity;
    private final float whiteoutStrength;
    private final float visibilityMultiplier;

    SnowTier(float particleDensity, float whiteoutStrength, float visibilityMultiplier) {
        this.particleDensity = particleDensity;
        this.whiteoutStrength = whiteoutStrength;
        this.visibilityMultiplier = visibilityMultiplier;
    }

    public float getParticleDensity() {
        return particleDensity;
    }

    public float getWhiteoutStrength() {
        return whiteoutStrength;
    }

    public float getVisibilityMultiplier() {
        return visibilityMultiplier;
    }

    public static SnowTier resolve(float temperatureCelsius, float humidity, float windSpeedMps, float precipitationStrength) {
        if (temperatureCelsius > 1.5F || precipitationStrength <= 0.02F) {
            return NONE;
        }

        float snowScore = Mth.clamp(precipitationStrength, 0.0F, 1.0F)
                * Mth.clamp(humidity, 0.0F, 1.0F)
                * Mth.clamp(windSpeedMps / 18.0F, 0.35F, 1.35F);
        if (snowScore >= 0.72F || windSpeedMps >= 18.0F) {
            return BLIZZARD;
        }
        if (snowScore >= 0.38F || windSpeedMps >= 10.0F) {
            return SNOWSTORM;
        }
        return SNOWY_DAY;
    }
}
