package net.Gabou.projectatmosphere.clouds.field.backend;

import net.Gabou.projectatmosphere.clouds.field.CloudField;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Applies prepared backend sources to a CloudFieldStore. This is an explicit
 * bridge utility, not automatic weather or renderer wiring.
 */
public final class CloudFieldBackendBridge {
    private final CloudFieldFactory factory;

    public CloudFieldBackendBridge(CloudFieldFactory factory) {
        this.factory = factory == null ? new CloudFieldFactory() : factory;
    }

    public static CloudFieldBackendBridge createDefault() {
        return new CloudFieldBackendBridge(new CloudFieldFactory());
    }

    public List<CloudFieldUpdatePlan> planUpdates(
            CloudFieldStore store,
            CloudFieldSourceSnapshot snapshot
    ) {
        Objects.requireNonNull(store, "store");
        CloudFieldSourceSnapshot sourceSnapshot = snapshot == null
                ? CloudFieldSourceSnapshot.of(List.of(), 0L, "", "empty")
                : snapshot;

        List<CloudFieldUpdatePlan> plans = new ArrayList<>();
        for (CloudFieldSource source : sourceSnapshot.sources()) {
            UUID fieldId = factory.fieldIdFor(source);
            CloudField existing = store.getField(fieldId).orElse(null);
            plans.add(CloudFieldUpdatePlan.forSource(existing, source, factory));
        }
        return List.copyOf(plans);
    }

    public ApplyResult applySnapshot(
            CloudFieldStore store,
            CloudFieldSourceSnapshot snapshot,
            boolean removeMissingSourceFields
    ) {
        Objects.requireNonNull(store, "store");
        CloudFieldSourceSnapshot sourceSnapshot = snapshot == null
                ? CloudFieldSourceSnapshot.of(List.of(), 0L, "", "empty")
                : snapshot;
        List<CloudFieldUpdatePlan> plans = planUpdates(store, sourceSnapshot);
        Map<UUID, CloudFieldSource> sourceByFieldId = new HashMap<>();
        for (CloudFieldSource source : sourceSnapshot.sources()) {
            sourceByFieldId.put(factory.fieldIdFor(source), source);
        }
        Set<UUID> sourceFieldIds = new HashSet<>();
        int created = 0;
        int updated = 0;
        int removed = 0;
        int unchanged = 0;

        for (CloudFieldUpdatePlan plan : plans) {
            if (plan.fieldId() != null) {
                sourceFieldIds.add(plan.fieldId());
            }
            if (plan.removeField()) {
                if (store.removeField(plan.fieldId()).isPresent()) {
                    removed++;
                }
                continue;
            }
            CloudField desired = plan.desiredField();
            if (desired == null) {
                unchanged++;
                continue;
            }
            if (plan.createNewField()) {
                created++;
                store.addField(desired);
            } else if (plan.hasFieldChanges()) {
                updated++;
            } else {
                unchanged++;
            }
            store.setTargetSource(plan.fieldId(), sourceByFieldId.get(plan.fieldId()));
        }

        if (removeMissingSourceFields) {
            for (CloudField field : List.copyOf(store.listActiveFields())) {
                if (!sourceFieldIds.contains(field.fieldId())) {
                    if (store.removeField(field.fieldId()).isPresent()) {
                        removed++;
                    }
                }
            }
        }

        return new ApplyResult(created, updated, removed, unchanged, plans);
    }

    public CloudFieldFactory factory() {
        return factory;
    }

    public record ApplyResult(
            int created,
            int updated,
            int removed,
            int unchanged,
            Collection<CloudFieldUpdatePlan> plans
    ) {
        public ApplyResult {
            plans = plans == null ? List.of() : List.copyOf(plans);
        }
    }
}
