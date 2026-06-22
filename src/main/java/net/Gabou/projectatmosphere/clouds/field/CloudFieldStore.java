package net.Gabou.projectatmosphere.clouds.field;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns active CloudFields and their runtime hydration state. This store is
 * intentionally unaware of concrete renderer internals.
 */
public final class CloudFieldStore {
    private final Map<UUID, CloudField> fields = new LinkedHashMap<>();
    private final Map<UUID, CloudFieldRuntimeState> runtimeStates = new LinkedHashMap<>();
    private final CloudFieldLifecycleController lifecycleController;

    public CloudFieldStore(CloudFieldLifecycleController lifecycleController) {
        this.lifecycleController = lifecycleController == null
                ? CloudFieldLifecycleController.defaultController()
                : lifecycleController;
    }

    public static CloudFieldStore createDefault() {
        return new CloudFieldStore(CloudFieldLifecycleController.defaultController());
    }

    public void addField(CloudField field) {
        Objects.requireNonNull(field, "field");
        fields.put(field.fieldId(), field);
    }

    public Optional<CloudField> removeField(UUID fieldId) {
        if (fieldId == null) {
            return Optional.empty();
        }
        CloudField removed = fields.remove(fieldId);
        runtimeStates.remove(fieldId);
        return Optional.ofNullable(removed);
    }

    public Optional<CloudField> getField(UUID fieldId) {
        if (fieldId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(fields.get(fieldId));
    }

    public Optional<CloudFieldRuntimeState> getRuntimeState(UUID fieldId) {
        if (fieldId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(runtimeStates.get(fieldId));
    }

    public List<CloudField> listActiveFields() {
        return List.copyOf(fields.values());
    }

    public Collection<CloudFieldRuntimeState> listRuntimeStates() {
        return List.copyOf(runtimeStates.values());
    }

    public Map<UUID, CloudFieldRuntimeState> runtimeStateMap() {
        return Map.copyOf(runtimeStates);
    }

    public void tickAll(CloudFieldTickContext context) {
        if (fields.isEmpty()) {
            return;
        }

        for (CloudField field : List.copyOf(fields.values())) {
            CloudFieldLifecycleController.TickResult result = lifecycleController.tick(
                    field,
                    runtimeStates.get(field.fieldId()),
                    context
            );
            fields.put(result.field().fieldId(), result.field());
            runtimeStates.put(result.field().fieldId(), result.runtimeState());
        }
        removeExpiredFields();
    }

    public int removeExpiredFields() {
        int before = fields.size();
        fields.values().removeIf(CloudField::isExpired);
        runtimeStates.keySet().removeIf(fieldId -> !fields.containsKey(fieldId));
        return before - fields.size();
    }

    public int size() {
        return fields.size();
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }
}
