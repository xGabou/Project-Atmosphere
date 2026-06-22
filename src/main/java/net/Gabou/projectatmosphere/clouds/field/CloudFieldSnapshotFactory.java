package net.Gabou.projectatmosphere.clouds.field;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Converts pure fields plus runtime state into immutable render-safe snapshots.
 */
public final class CloudFieldSnapshotFactory {
    public CloudFieldSnapshot create(
            CloudField field,
            CloudFieldRuntimeState runtimeState,
            CloudFieldTickContext context
    ) {
        Objects.requireNonNull(field, "field");
        CloudFieldTickContext tickContext = context == null
                ? CloudFieldTickContext.of(field.center(), 0L, 0.0F)
                : context;
        CloudLodBand band = tickContext.distanceClassifier().classify(field, tickContext.cameraPosition());
        CloudFieldRuntimeState state = runtimeState == null
                ? CloudFieldRuntimeState.initial(field, band, tickContext.worldTime())
                : runtimeState;

        return new CloudFieldSnapshot(
                field.fieldId(),
                field.seed(),
                field.dimensionId(),
                field.center(),
                state.previousCenter(),
                field.radius(),
                field.baseY(),
                field.topY(),
                field.density(),
                field.coverage(),
                field.growth(),
                field.decay(),
                field.humidityInfluence(),
                field.windVector(),
                field.verticalDevelopment(),
                field.stormPotential(),
                state.currentLodBand(),
                state.previousLodBand(),
                state.hydrationState(),
                state.hydrationProgress(),
                field.cloudletCount(),
                state.currentCloudletCount(),
                field.ageTicks(),
                field.lifetimeTicks(),
                tickContext.worldTime(),
                tickContext.partialTick(),
                tickContext.cameraPosition()
        );
    }

    public CloudFieldRendererInput createRendererInput(
            Collection<CloudField> fields,
            Map<UUID, CloudFieldRuntimeState> runtimeStates,
            CloudFieldTickContext context
    ) {
        CloudFieldTickContext tickContext = context == null
                ? CloudFieldTickContext.of(null, 0L, 0.0F)
                : context;
        if (fields == null || fields.isEmpty()) {
            return CloudFieldRendererInput.empty(
                    tickContext.worldTime(),
                    tickContext.partialTick(),
                    tickContext.cameraPosition()
            );
        }

        List<CloudFieldSnapshot> snapshots = fields.stream()
                .map(field -> create(
                        field,
                        runtimeStates == null ? null : runtimeStates.get(field.fieldId()),
                        tickContext
                ))
                .toList();
        return new CloudFieldRendererInput(
                snapshots,
                tickContext.worldTime(),
                tickContext.partialTick(),
                tickContext.cameraPosition()
        );
    }
}
