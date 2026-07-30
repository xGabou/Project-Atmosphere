package net.Gabou.projectatmosphere.clouds.field;

import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSource;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSourceType;

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
    public static final int DEFAULT_MISSING_SOURCE_GRACE_TICKS = 20 * 45;

    private final Map<UUID, CloudField> fields = new LinkedHashMap<>();
    private final Map<UUID, CloudFieldRuntimeState> runtimeStates = new LinkedHashMap<>();
    private final Map<UUID, CloudFieldSource> targetSources = new LinkedHashMap<>();
    private final Map<UUID, Integer> missingSourceTicks = new LinkedHashMap<>();
    private final CloudFieldLifecycleController lifecycleController;
    private List<CloudFieldRemovalDebugInfo> lastExpirationRemovals = List.of();
    private int lastRecoveredSourceFields;

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
        targetSources.remove(fieldId);
        missingSourceTicks.remove(fieldId);
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

    /**
     * Stores the latest backend/source target for an existing or newly created
     * field. The source is read during lifecycle ticks, not during rendering.
     */
    public void setTargetSource(UUID fieldId, CloudFieldSource source) {
        if (fieldId == null) {
            return;
        }
        if (source == null) {
            targetSources.remove(fieldId);
            return;
        }
        targetSources.put(fieldId, source);
    }

    /**
     * Marks that a backend source was present for this field during the latest
     * apply pass. Returns true when the field recovered from a missing source.
     */
    public boolean markSourcePresent(UUID fieldId) {
        if (fieldId == null) {
            return false;
        }
        return missingSourceTicks.remove(fieldId) != null;
    }

    /**
     * Marks that a field's backend source was missing for one apply pass and
     * returns the number of consecutive missing ticks.
     */
    public int markSourceMissing(UUID fieldId) {
        if (fieldId == null) {
            return 0;
        }
        int ticks = missingSourceTicks.getOrDefault(fieldId, 0) + 1;
        missingSourceTicks.put(fieldId, ticks);
        return ticks;
    }

    /**
     * Returns consecutive ticks where the backend source was missing.
     */
    public int missingSourceTicks(UUID fieldId) {
        if (fieldId == null) {
            return 0;
        }
        return missingSourceTicks.getOrDefault(fieldId, 0);
    }

    /**
     * Returns true when a field is currently ticking without a live source.
     */
    public boolean isSourceMissing(UUID fieldId) {
        return missingSourceTicks(fieldId) > 0;
    }

    /**
     * Records how many fields recovered a missing source during the last apply.
     */
    public void setLastRecoveredSourceFields(int recoveredSourceFields) {
        lastRecoveredSourceFields = Math.max(0, recoveredSourceFields);
    }

    /**
     * Returns fields whose source recovered during the latest backend apply.
     */
    public int lastRecoveredSourceFields() {
        return lastRecoveredSourceFields;
    }

    /**
     * Returns the missing source grace period. The default is 900 ticks, or 45
     * seconds, so short source identity gaps do not remove persistent fields.
     */
    public int missingSourceGraceTicks() {
        return DEFAULT_MISSING_SOURCE_GRACE_TICKS;
    }

    /**
     * Returns the latest backend/source target used to evolve a field.
     */
    public Optional<CloudFieldSource> getTargetSource(UUID fieldId) {
        if (fieldId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(targetSources.get(fieldId));
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

    /**
     * Returns the latest source type for each field with a known target source.
     * This is render metadata only; renderers still consume CloudFieldSnapshot,
     * not backend sources directly.
     */
    public Map<UUID, CloudFieldSourceType> targetSourceTypeMap() {
        Map<UUID, CloudFieldSourceType> sourceTypes = new LinkedHashMap<>();
        for (Map.Entry<UUID, CloudFieldSource> entry : targetSources.entrySet()) {
            CloudFieldSource source = entry.getValue();
            if (source != null) {
                sourceTypes.put(entry.getKey(), source.sourceType());
            }
        }
        return Map.copyOf(sourceTypes);
    }

    /**
     * Returns fields removed by expiration or decay during the last store tick.
     */
    public List<CloudFieldRemovalDebugInfo> lastExpirationRemovals() {
        return lastExpirationRemovals;
    }

    public void tickAll(CloudFieldTickContext context) {
        lastExpirationRemovals = List.of();
        if (fields.isEmpty()) {
            return;
        }

        for (CloudField field : List.copyOf(fields.values())) {
            CloudFieldLifecycleController.TickResult result = lifecycleController.tick(
                    field,
                    runtimeStates.get(field.fieldId()),
                    context,
                    targetSources.get(field.fieldId()),
                    missingSourceTicks(field.fieldId())
            );
            fields.put(result.field().fieldId(), result.field());
            runtimeStates.put(result.field().fieldId(), result.runtimeState());
            if (!result.field().fieldId().equals(field.fieldId())) {
                CloudFieldSource source = targetSources.remove(field.fieldId());
                setTargetSource(result.field().fieldId(), source);
                Integer missingTicks = missingSourceTicks.remove(field.fieldId());
                if (missingTicks != null) {
                    missingSourceTicks.put(result.field().fieldId(), missingTicks);
                }
            }
        }
        lastExpirationRemovals = removeExpiredFieldsWithDebug();
    }

    public int removeExpiredFields() {
        lastExpirationRemovals = removeExpiredFieldsWithDebug();
        return lastExpirationRemovals.size();
    }

    private List<CloudFieldRemovalDebugInfo> removeExpiredFieldsWithDebug() {
        List<CloudFieldRemovalDebugInfo> removals = new java.util.ArrayList<>();
        for (CloudField field : List.copyOf(fields.values())) {
            if (!field.isExpired()) {
                continue;
            }
            boolean lifetimeExpired = field.lifetimeTicks() > 0L && field.ageTicks() >= field.lifetimeTicks();
            CloudFieldRemovalDebugInfo.Reason reason = lifetimeExpired
                    ? CloudFieldRemovalDebugInfo.Reason.LIFETIME_EXPIRED
                    : CloudFieldRemovalDebugInfo.Reason.DECAY_EXPIRED;
            removals.add(CloudFieldRemovalDebugInfo.of(field, reason, !targetSources.containsKey(field.fieldId())));
            fields.remove(field.fieldId());
        }
        runtimeStates.keySet().removeIf(fieldId -> !fields.containsKey(fieldId));
        targetSources.keySet().removeIf(fieldId -> !fields.containsKey(fieldId));
        missingSourceTicks.keySet().removeIf(fieldId -> !fields.containsKey(fieldId));
        return List.copyOf(removals);
    }

    public int size() {
        return fields.size();
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }
}
