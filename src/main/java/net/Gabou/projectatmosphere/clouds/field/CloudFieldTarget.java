package net.Gabou.projectatmosphere.clouds.field;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Desired CloudField state produced from backend/weather input. Evolution code
 * moves persistent fields toward this immutable target over time.
 */
public record CloudFieldTarget(
        Vec3 center,
        float radius,
        float baseY,
        float topY,
        float density,
        float coverage,
        float targetHydration,
        float verticalDevelopment,
        float stormPotential,
        float decayPressure,
        Vec3 windInfluence,
        float growth,
        float decay,
        int cloudletCount,
        long lifetimeTicks
) {
    public CloudFieldTarget {
        center = center == null ? Vec3.ZERO : center;
        radius = Math.max(0.0F, finite(radius, 0.0F));
        baseY = finite(baseY, 0.0F);
        topY = Math.max(baseY, finite(topY, baseY));
        density = clamp01(density);
        coverage = clamp01(coverage);
        targetHydration = clamp01(targetHydration);
        verticalDevelopment = clamp01(verticalDevelopment);
        stormPotential = clamp01(stormPotential);
        decayPressure = clamp01(decayPressure);
        windInfluence = windInfluence == null ? Vec3.ZERO : windInfluence;
        growth = clamp01(growth);
        decay = clamp01(decay);
        cloudletCount = Math.max(0, cloudletCount);
        lifetimeTicks = Math.max(0L, lifetimeTicks);
    }

    /**
     * Builds a neutral target from the current field when no fresh backend
     * source is available.
     */
    public static CloudFieldTarget fromField(CloudField field) {
        Objects.requireNonNull(field, "field");
        return new CloudFieldTarget(
                field.center(),
                field.radius(),
                field.baseY(),
                field.topY(),
                field.density(),
                field.coverage(),
                field.humidityInfluence(),
                field.verticalDevelopment(),
                field.stormPotential(),
                field.decay(),
                field.windVector(),
                field.growth(),
                field.decay(),
                field.cloudletCount(),
                field.lifetimeTicks()
        );
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, finite(value, 0.0F)));
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
