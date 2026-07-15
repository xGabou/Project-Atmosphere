package net.Gabou.projectatmosphere.clouds.field;

import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
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
        String cloudTypeId,
        CloudMorphologyFamily morphologyFamily,
        CloudMorphologyMembership morphologyMembership,
        float anvilStrength,
        float precipitationIntensity,
        CloudFieldSourceKind sourceKind,
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
        sourceKind = sourceKind == null ? CloudFieldSourceKind.UNKNOWN : sourceKind;
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

    /** Backward-compatible construction for snapshots without grouped lobes. */
    public CloudFieldSnapshot(
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
            String cloudTypeId,
            CloudMorphologyFamily morphologyFamily,
            float anvilStrength,
            float precipitationIntensity,
            CloudFieldSourceKind sourceKind,
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
        this(
                fieldId,
                seed,
                dimensionId,
                center,
                previousCenter,
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
                sourceKind,
                lodBand,
                previousLodBand,
                hydrationState,
                hydrationProgress,
                targetCloudletCount,
                activeCloudletCount,
                fieldAgeTicks,
                lifetimeTicks,
                worldTime,
                partialTick,
                cameraPosition
        );
    }

    public int dynamicCloudletCount() {
        if (!lodBand.hasIdentifiableCloudlets()) {
            return 0;
        }
        return activeCloudletCount;
    }

    public float effectiveDensity() {
        // PA cluster/region sources already integrate growth and decay into
        // their authoritative density every server tick. Reapplying the
        // lifecycle envelope here made a freshly spawned native lobe start
        // near zero even though its simulation state deliberately starts at
        // 82% of mature density, then forced its silhouette through every
        // erosion threshold for 30 seconds.
        if (hasAuthoritativeLifecycleScalars()) {
            return clamp01(density);
        }
        return clamp01(density * growth * (1.0F - decay));
    }

    public float effectiveCoverage() {
        if (hasAuthoritativeLifecycleScalars()) {
            return clamp01(coverage);
        }
        return clamp01(coverage * growth * (1.0F - decay));
    }

    private boolean hasAuthoritativeLifecycleScalars() {
        return sourceKind == CloudFieldSourceKind.PA_CLUSTER
                || sourceKind == CloudFieldSourceKind.PA_REGION;
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

    /** 0 forming, 0.5 mature, 1 dissipating; consumed as continuous GPU metadata. */
    public float lifecycleStage() {
        if (decay > 0.001F) {
            return 0.5F + decay * 0.5F;
        }
        return growth < 0.999F ? growth * 0.5F : 0.5F;
    }

    /**
     * Lifecycle value consumed by weather-map shaders. Authoritative PA
     * sources have already integrated formation and dissipation into their
     * scalar state; presenting them as mature prevents the shader from
     * multiplying either phase a second time.
     */
    public float visualLifecycleStage() {
        return hasAuthoritativeLifecycleScalars() ? 0.5F : lifecycleStage();
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, finite(value, 0.0F)));
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
