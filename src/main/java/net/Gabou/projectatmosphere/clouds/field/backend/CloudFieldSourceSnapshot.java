package net.Gabou.projectatmosphere.clouds.field.backend;

import java.util.Collection;
import java.util.List;

/**
 * Immutable captured set of backend sources for one bridge/update pass.
 */
public record CloudFieldSourceSnapshot(
        List<CloudFieldSource> sources,
        long capturedGameTime,
        String dimensionId,
        String sourceDescription
) {
    public CloudFieldSourceSnapshot {
        sources = sources == null ? List.of() : List.copyOf(sources);
        dimensionId = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId.trim();
        sourceDescription = sourceDescription == null ? "" : sourceDescription.trim();
    }

    public static CloudFieldSourceSnapshot of(
            Collection<CloudFieldSource> sources,
            long capturedGameTime,
            String dimensionId,
            String sourceDescription
    ) {
        return new CloudFieldSourceSnapshot(
                sources == null ? List.of() : List.copyOf(sources),
                capturedGameTime,
                dimensionId,
                sourceDescription
        );
    }

    public List<CloudFieldSource> activeSources() {
        return sources.stream()
                .filter(CloudFieldSource::isUsable)
                .toList();
    }

    public boolean isEmpty() {
        return sources.isEmpty();
    }
}
