package net.Gabou.projectatmosphere.clouds.field;

import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * High-level cloud mass state. This is not a render AABB and should not be read
 * directly by renderers; produce a CloudFieldSnapshot for rendering.
 */
public record CloudField(
        UUID fieldId,
        long seed,
        String dimensionId,
        Vec3 center,
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
        String cloudTypeId,
        CloudMorphologyFamily morphologyFamily,
        CloudMorphologyMembership morphologyMembership,
        float anvilStrength,
        float precipitationIntensity,
        int cloudletCount,
        long ageTicks,
        long lifetimeTicks
) {
    public CloudField {
        fieldId = Objects.requireNonNull(fieldId, "fieldId");
        dimensionId = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
        center = center == null ? Vec3.ZERO : center;
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
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cloudTypeId);
        cloudTypeId = definition.getId();
        morphologyFamily = morphologyFamily == null
                ? definition.getMorphologyFamily()
                : morphologyFamily;
        morphologyMembership = (morphologyMembership == null
                ? CloudMorphologyMembership.ungrouped()
                : morphologyMembership).withFallbackGroup(fieldId);
        anvilStrength = clamp01(anvilStrength);
        precipitationIntensity = clamp01(precipitationIntensity);
        cloudletCount = Math.max(0, cloudletCount);
        ageTicks = Math.max(0L, ageTicks);
        lifetimeTicks = Math.max(0L, lifetimeTicks);
    }

    /** Backward-compatible construction for independent/non-canonical fields. */
    public CloudField(
            UUID fieldId,
            long seed,
            String dimensionId,
            Vec3 center,
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
            String cloudTypeId,
            CloudMorphologyFamily morphologyFamily,
            float anvilStrength,
            float precipitationIntensity,
            int cloudletCount,
            long ageTicks,
            long lifetimeTicks
    ) {
        this(
                fieldId,
                seed,
                dimensionId,
                center,
                radius,
                baseY,
                topY,
                density,
                coverage,
                growth,
                decay,
                humidityInfluence,
                windVector,
                verticalDevelopment,
                stormPotential,
                cloudTypeId,
                morphologyFamily,
                CloudMorphologyMembership.single(fieldId),
                anvilStrength,
                precipitationIntensity,
                cloudletCount,
                ageTicks,
                lifetimeTicks
        );
    }

    public CloudField movedByWind(float ticks) {
        double clampedTicks = Math.max(0.0F, finite(ticks, 0.0F));
        return withCenter(center.add(windVector.scale(clampedTicks)), ageTicks + Math.round(clampedTicks));
    }

    public CloudField withCenter(Vec3 newCenter, long newAgeTicks) {
        return new CloudField(
                fieldId,
                seed,
                dimensionId,
                newCenter,
                radius,
                baseY,
                topY,
                density,
                coverage,
                growth,
                decay,
                humidityInfluence,
                windVector,
                verticalDevelopment,
                stormPotential,
                cloudTypeId,
                morphologyFamily,
                morphologyMembership,
                anvilStrength,
                precipitationIntensity,
                cloudletCount,
                newAgeTicks,
                lifetimeTicks
        );
    }

    public CloudField withScalars(float newDensity, float newCoverage, float newGrowth, float newDecay) {
        return new CloudField(
                fieldId,
                seed,
                dimensionId,
                center,
                radius,
                baseY,
                topY,
                newDensity,
                newCoverage,
                newGrowth,
                newDecay,
                humidityInfluence,
                windVector,
                verticalDevelopment,
                stormPotential,
                cloudTypeId,
                morphologyFamily,
                morphologyMembership,
                anvilStrength,
                precipitationIntensity,
                cloudletCount,
                ageTicks,
                lifetimeTicks
        );
    }

    public CloudField withCloudletCount(int newCloudletCount) {
        return new CloudField(
                fieldId,
                seed,
                dimensionId,
                center,
                radius,
                baseY,
                topY,
                density,
                coverage,
                growth,
                decay,
                humidityInfluence,
                windVector,
                verticalDevelopment,
                stormPotential,
                cloudTypeId,
                morphologyFamily,
                morphologyMembership,
                anvilStrength,
                precipitationIntensity,
                newCloudletCount,
                ageTicks,
                lifetimeTicks
        );
    }

    public boolean isExpired() {
        return decay >= 0.999F || (lifetimeTicks > 0L && ageTicks >= lifetimeTicks);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, finite(value, 0.0F)));
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
