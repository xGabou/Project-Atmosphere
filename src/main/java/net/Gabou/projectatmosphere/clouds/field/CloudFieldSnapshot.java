package net.Gabou.projectatmosphere.clouds.field;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable render-safe view of a CloudField.
 */
public record CloudFieldSnapshot(
        UUID fieldId,
        long seed,
        String dimensionId,
        Vec3 center,
        Vec3 previousCenter,
        float radius,
        float baseY,
        float topY,
        float density,
        float coverage,
        float growth,
        float decay,
        float humidityInfluence,
        Vec3 windVector,
        float verticalDevelopment,
        float stormPotential,
        CloudLodBand lodBand,
        CloudLodBand previousLodBand,
        CloudFieldHydrationState hydrationState,
        float hydrationProgress,
        int targetCloudletCount,
        int activeCloudletCount,
        long fieldAgeTicks,
        long lifetimeTicks,
        long worldTime,
        float partialTick,
        Vec3 cameraPosition
) {
    public CloudFieldSnapshot {
        fieldId = Objects.requireNonNull(fieldId, "fieldId");
        dimensionId = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
        center = center == null ? Vec3.ZERO : center;
        previousCenter = previousCenter == null ? center : previousCenter;
        radius = Math.max(0.0F, finite(radius, 0.0F));
        topY = Math.max(baseY, topY);
        density = clamp01(density);
        coverage = clamp01(coverage);
        growth = clamp01(growth);
        decay = clamp01(decay);
        humidityInfluence = clamp01(humidityInfluence);
        windVector = windVector == null ? Vec3.ZERO : windVector;
        verticalDevelopment = clamp01(verticalDevelopment);
        stormPotential = clamp01(stormPotential);
        lodBand = lodBand == null ? CloudLodBand.DYNAMIC : lodBand;
        previousLodBand = previousLodBand == null ? lodBand : previousLodBand;
        hydrationState = hydrationState == null ? CloudFieldHydrationState.NOT_HYDRATED : hydrationState;
        hydrationProgress = clamp01(hydrationProgress);
        targetCloudletCount = Math.max(0, targetCloudletCount);
        activeCloudletCount = Math.max(0, Math.min(activeCloudletCount, targetCloudletCount));
        fieldAgeTicks = Math.max(0L, fieldAgeTicks);
        lifetimeTicks = Math.max(0L, lifetimeTicks);
        partialTick = Float.isFinite(partialTick) ? partialTick : 0.0F;
        cameraPosition = cameraPosition == null ? Vec3.ZERO : cameraPosition;
    }

    public int dynamicCloudletCount() {
        if (!lodBand.hasIdentifiableCloudlets()) {
            return 0;
        }
        return activeCloudletCount;
    }

    public float effectiveDensity() {
        return clamp01(density * growth * (1.0F - decay));
    }

    public float effectiveCoverage() {
        return clamp01(coverage * growth * (1.0F - decay));
    }

    public boolean hasVisibleClouds() {
        return radius > 0.0F
                && topY > baseY
                && effectiveDensity() > 0.001F
                && effectiveCoverage() > 0.001F;
    }

    public boolean isHydratedEnoughForCloudlets() {
        return hydrationProgress > 0.001F && dynamicCloudletCount() > 0;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, finite(value, 0.0F)));
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
