package net.Gabou.projectatmosphere.clouds.field.backend;

import net.Gabou.projectatmosphere.clouds.field.CloudField;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * Describes how a CloudField should change for a backend source update. Fields
 * are immutable, so applying a plan returns the desired next CloudField value
 * while preserving stable identity when source id and seed are unchanged.
 */
public record CloudFieldUpdatePlan(
        String sourceId,
        CloudFieldSourceType sourceType,
        UUID fieldId,
        boolean createNewField,
        boolean removeField,
        boolean identityChanged,
        boolean centerChanged,
        boolean radiusChanged,
        boolean verticalBoundsChanged,
        boolean windChanged,
        boolean densityChanged,
        boolean coverageChanged,
        boolean growthChanged,
        boolean decayChanged,
        boolean verticalDevelopmentChanged,
        boolean stormPotentialChanged,
        boolean cloudletCountChanged,
        CloudField desiredField
) {
    public CloudFieldUpdatePlan {
        sourceId = sourceId == null ? "" : sourceId;
        sourceType = sourceType == null ? CloudFieldSourceType.MANUAL_DEBUG : sourceType;
    }

    public static CloudFieldUpdatePlan forSource(
            CloudField existing,
            CloudFieldSource source,
            CloudFieldFactory factory
    ) {
        Objects.requireNonNull(source, "source");
        CloudFieldFactory fieldFactory = factory == null ? new CloudFieldFactory() : factory;
        UUID fieldId = fieldFactory.fieldIdFor(source);
        CloudField desired = fieldFactory.create(source).orElse(null);
        if (desired == null) {
            return removal(source, fieldId, existing != null);
        }

        if (existing == null) {
            return new CloudFieldUpdatePlan(
                    source.sourceId(),
                    source.sourceType(),
                    fieldId,
                    true,
                    false,
                    false,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    desired
            );
        }

        boolean identityChanged = !existing.fieldId().equals(desired.fieldId());
        return new CloudFieldUpdatePlan(
                source.sourceId(),
                source.sourceType(),
                fieldId,
                identityChanged,
                false,
                identityChanged,
                !sameVec(existing.center(), desired.center()),
                !sameFloat(existing.radius(), desired.radius()),
                !sameFloat(existing.baseY(), desired.baseY()) || !sameFloat(existing.topY(), desired.topY()),
                !sameVec(existing.windVector(), desired.windVector()),
                !sameFloat(existing.density(), desired.density()),
                !sameFloat(existing.coverage(), desired.coverage()),
                !sameFloat(existing.growth(), desired.growth()),
                !sameFloat(existing.decay(), desired.decay()),
                !sameFloat(existing.verticalDevelopment(), desired.verticalDevelopment()),
                !sameFloat(existing.stormPotential(), desired.stormPotential()),
                existing.cloudletCount() != desired.cloudletCount(),
                desired
        );
    }

    /**
     * Describes a source retarget/rebind for an existing persistent field. The
     * field identity is preserved; lifecycle evolution will move it toward the
     * new source target.
     */
    public static CloudFieldUpdatePlan retarget(
            CloudField existing,
            CloudFieldSource source,
            CloudFieldFactory factory
    ) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(source, "source");
        CloudFieldFactory fieldFactory = factory == null ? new CloudFieldFactory() : factory;
        return new CloudFieldUpdatePlan(
                source.sourceId(),
                source.sourceType(),
                existing.fieldId(),
                false,
                false,
                false,
                !sameVec(existing.center(), source.center()),
                !sameFloat(existing.radius(), source.radius()),
                !sameFloat(existing.baseY(), source.baseY()) || !sameFloat(existing.topY(), source.topY()),
                !sameVec(existing.windVector(), source.wind()),
                !sameFloat(existing.density(), source.density()),
                !sameFloat(existing.coverage(), source.coverage()),
                !sameFloat(existing.growth(), source.growth()),
                !sameFloat(existing.decay(), source.decay()),
                !sameFloat(existing.verticalDevelopment(), source.verticalDevelopment()),
                !sameFloat(existing.stormPotential(), source.stormPotential()),
                existing.cloudletCount() != fieldFactory.cloudletCountFor(source),
                existing
        );
    }

    public static CloudFieldUpdatePlan removal(CloudFieldSource source, UUID fieldId, boolean existingFieldPresent) {
        return new CloudFieldUpdatePlan(
                source == null ? "" : source.sourceId(),
                source == null ? CloudFieldSourceType.MANUAL_DEBUG : source.sourceType(),
                fieldId,
                false,
                existingFieldPresent,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null
        );
    }

    public boolean hasFieldChanges() {
        return createNewField
                || removeField
                || identityChanged
                || centerChanged
                || radiusChanged
                || verticalBoundsChanged
                || windChanged
                || densityChanged
                || coverageChanged
                || growthChanged
                || decayChanged
                || verticalDevelopmentChanged
                || stormPotentialChanged
                || cloudletCountChanged;
    }

    public CloudField applyTo(CloudField existing) {
        if (removeField) {
            return null;
        }
        if (desiredField == null) {
            return existing;
        }
        return desiredField;
    }

    private static boolean sameVec(Vec3 first, Vec3 second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.distanceTo(second) <= 0.000001D;
    }

    private static boolean sameFloat(float first, float second) {
        return Math.abs(first - second) <= 0.00001F;
    }
}
