package net.Gabou.projectatmosphere.clouds.field;

import java.util.Objects;
import java.util.UUID;

/**
 * Debug-only explanation for a CloudField source identity rebind. This is not
 * synchronized to clients and should not be used by renderers.
 */
public record CloudFieldSourceRebindDebugInfo(
        UUID fieldId,
        String oldSourceId,
        String oldSourceType,
        String newSourceId,
        String newSourceType,
        double distanceBlocks,
        String reason
) {
    public CloudFieldSourceRebindDebugInfo {
        fieldId = Objects.requireNonNull(fieldId, "fieldId");
        oldSourceId = sanitize(oldSourceId);
        oldSourceType = sanitize(oldSourceType);
        newSourceId = sanitize(newSourceId);
        newSourceType = sanitize(newSourceType);
        distanceBlocks = Double.isFinite(distanceBlocks) ? Math.max(0.0D, distanceBlocks) : 0.0D;
        reason = sanitize(reason);
    }

    private static String sanitize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
