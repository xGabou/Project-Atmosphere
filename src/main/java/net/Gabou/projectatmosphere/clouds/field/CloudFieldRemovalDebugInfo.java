package net.Gabou.projectatmosphere.clouds.field;

import java.util.Objects;
import java.util.UUID;

/**
 * Debug-only explanation for a CloudField removal. This is not synchronized to
 * clients and should not be used by renderers.
 */
public record CloudFieldRemovalDebugInfo(
        UUID fieldId,
        Reason reason,
        long ageTicks,
        long lifetimeTicks,
        boolean sourceMissing
) {
    public CloudFieldRemovalDebugInfo {
        fieldId = Objects.requireNonNull(fieldId, "fieldId");
        reason = reason == null ? Reason.CLEANUP : reason;
        ageTicks = Math.max(0L, ageTicks);
        lifetimeTicks = Math.max(0L, lifetimeTicks);
    }

    /**
     * Captures the removal context for an existing field.
     */
    public static CloudFieldRemovalDebugInfo of(CloudField field, Reason reason, boolean sourceMissing) {
        Objects.requireNonNull(field, "field");
        return new CloudFieldRemovalDebugInfo(
                field.fieldId(),
                reason,
                field.ageTicks(),
                field.lifetimeTicks(),
                sourceMissing
        );
    }

    public enum Reason {
        MISSING_SOURCE,
        MISSING_SOURCE_GRACE_EXPIRED,
        INVALID_SOURCE,
        LIFETIME_EXPIRED,
        DECAY_EXPIRED,
        DISTANCE,
        DIMENSION,
        LOD,
        CLEANUP
    }
}
