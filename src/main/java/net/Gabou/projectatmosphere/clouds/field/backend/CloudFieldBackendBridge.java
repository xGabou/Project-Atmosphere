package net.Gabou.projectatmosphere.clouds.field.backend;

import net.Gabou.projectatmosphere.clouds.field.CloudField;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRemovalDebugInfo;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSourceRebindDebugInfo;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldStore;
import net.minecraft.world.phys.Vec3;

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
    private static final double REBIND_DISTANCE_FLOOR = 96.0D;
    private static final double REBIND_DISTANCE_CAP = 384.0D;
    private static final float RADIUS_RATIO_LIMIT = 1.80F;
    private static final float RADIUS_ABSOLUTE_TOLERANCE = 80.0F;
    private static final float DENSITY_TOLERANCE = 0.45F;
    private static final float COVERAGE_TOLERANCE = 0.50F;
    private static final float HYDRATION_TOLERANCE = 0.50F;
    private static final float STORM_TOLERANCE = 0.65F;

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
        int activeFieldCountBeforeApply = store.size();
        List<CloudFieldUpdatePlan> plans = new ArrayList<>();
        Map<UUID, CloudFieldSource> sourceByFieldId = new HashMap<>();
        for (CloudFieldSource source : sourceSnapshot.sources()) {
            sourceByFieldId.put(factory.fieldIdFor(source), source);
        }
        Set<UUID> sourceFieldIds = new HashSet<>();
        int created = 0;
        int updated = 0;
        int removed = 0;
        int unchanged = 0;
        int missingSourceFields = 0;
        int recoveredSourceFields = 0;
        int reboundSourceFields = 0;
        int skippedRebindCandidates = 0;
        int duplicateSourcesSkipped = 0;
        int duplicateNearActiveField = 0;
        int duplicateNearStaleField = 0;
        List<CloudFieldRemovalDebugInfo> removals = new ArrayList<>();
        List<CloudFieldSourceRebindDebugInfo> rebinds = new ArrayList<>();

        for (CloudFieldSource source : sourceSnapshot.sources()) {
            UUID exactFieldId = factory.fieldIdFor(source);
            CloudField exactExisting = store.getField(exactFieldId).orElse(null);
            if (!source.isUsable()) {
                plans.add(CloudFieldUpdatePlan.removal(source, exactFieldId, exactExisting != null));
                if (exactExisting != null && store.removeField(exactFieldId).isPresent()) {
                    removed++;
                    removals.add(CloudFieldRemovalDebugInfo.of(
                            exactExisting,
                            CloudFieldRemovalDebugInfo.Reason.INVALID_SOURCE,
                            false
                    ));
                } else {
                    unchanged++;
                }
                continue;
            }

            if (exactExisting != null) {
                CloudFieldUpdatePlan plan = CloudFieldUpdatePlan.forSource(exactExisting, source, factory);
                plans.add(plan);
                sourceFieldIds.add(exactFieldId);
                if (plan.removeField()) {
                    CloudField removedField = store.getField(plan.fieldId()).orElse(null);
                    if (store.removeField(plan.fieldId()).isPresent()) {
                        removed++;
                        removals.add(CloudFieldRemovalDebugInfo.of(
                                removedField,
                                CloudFieldRemovalDebugInfo.Reason.INVALID_SOURCE,
                                false
                        ));
                    }
                    continue;
                }
                if (plan.hasFieldChanges()) {
                    updated++;
                } else {
                    unchanged++;
                }
                store.setTargetSource(exactFieldId, source);
                if (store.markSourcePresent(exactFieldId)) {
                    recoveredSourceFields++;
                }
                continue;
            }

            RebindCandidate rebindCandidate = findRebindCandidate(store, source, sourceByFieldId, sourceFieldIds);
            if (rebindCandidate != null) {
                CloudFieldSource oldSource = store.getTargetSource(rebindCandidate.field().fieldId()).orElse(null);
                CloudFieldUpdatePlan plan = CloudFieldUpdatePlan.retarget(rebindCandidate.field(), source, factory);
                plans.add(plan);
                sourceFieldIds.add(rebindCandidate.field().fieldId());
                store.setTargetSource(rebindCandidate.field().fieldId(), source);
                if (store.markSourcePresent(rebindCandidate.field().fieldId())) {
                    recoveredSourceFields++;
                }
                updated++;
                reboundSourceFields++;
                rebinds.add(new CloudFieldSourceRebindDebugInfo(
                        rebindCandidate.field().fieldId(),
                        oldSource == null ? "none" : oldSource.sourceId(),
                        oldSource == null ? "none" : oldSource.sourceType().name(),
                        source.sourceId(),
                        source.sourceType().name(),
                        rebindCandidate.distance(),
                        rebindCandidate.reason()
                ));
                continue;
            }

            DuplicateCandidate duplicateCandidate = findDuplicateCandidate(store, source, sourceByFieldId, sourceFieldIds);
            if (duplicateCandidate != null) {
                duplicateSourcesSkipped++;
                unchanged++;
                if (duplicateCandidate.stale()) {
                    duplicateNearStaleField++;
                } else {
                    duplicateNearActiveField++;
                }
                if (duplicateCandidate.rebindCandidateSkipped()) {
                    skippedRebindCandidates++;
                }
                continue;
            }

            CloudFieldUpdatePlan plan = CloudFieldUpdatePlan.forSource(null, source, factory);
            plans.add(plan);
            if (plan.removeField()) {
                unchanged++;
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
            sourceFieldIds.add(plan.fieldId());
            store.setTargetSource(plan.fieldId(), source);
            if (store.markSourcePresent(plan.fieldId())) {
                recoveredSourceFields++;
            }
        }

        if (removeMissingSourceFields) {
            for (CloudField field : List.copyOf(store.listActiveFields())) {
                if (!sourceFieldIds.contains(field.fieldId())) {
                    int missingTicks = store.markSourceMissing(field.fieldId());
                    missingSourceFields++;
                    if (missingTicks >= store.missingSourceGraceTicks() && store.removeField(field.fieldId()).isPresent()) {
                        removed++;
                        removals.add(CloudFieldRemovalDebugInfo.of(
                                field,
                                CloudFieldRemovalDebugInfo.Reason.MISSING_SOURCE_GRACE_EXPIRED,
                                true
                        ));
                    }
                }
            }
        }
        store.setLastRecoveredSourceFields(recoveredSourceFields);

        return new ApplyResult(
                created,
                updated,
                removed,
                unchanged,
                plans,
                activeFieldCountBeforeApply,
                sourceSnapshot.sources().size(),
                sourceSnapshot.activeSources().size(),
                missingSourceFields,
                recoveredSourceFields,
                reboundSourceFields,
                skippedRebindCandidates,
                duplicateSourcesSkipped,
                duplicateNearActiveField,
                duplicateNearStaleField,
                removals,
                rebinds
        );
    }

    private RebindCandidate findRebindCandidate(
            CloudFieldStore store,
            CloudFieldSource source,
            Map<UUID, CloudFieldSource> liveSourcesByExactFieldId,
            Set<UUID> assignedFieldIds
    ) {
        RebindCandidate best = null;
        for (CloudField field : sortedFields(store)) {
            if (field.isExpired()
                    || assignedFieldIds.contains(field.fieldId())
                    || !field.dimensionId().equals(source.dimensionId())) {
                continue;
            }
            int missingTicks = store.missingSourceTicks(field.fieldId());
            if (missingTicks >= store.missingSourceGraceTicks()) {
                continue;
            }
            CloudFieldSource oldSource = store.getTargetSource(field.fieldId()).orElse(null);
            if (!sourceTypesCanShareField(oldSource, source)) {
                continue;
            }
            if (oldSource != null && liveSourcesByExactFieldId.containsKey(factory.fieldIdFor(oldSource))) {
                continue;
            }
            if (!sourceShapeCompatible(field, source) || !sourceWeatherCompatible(field, oldSource, source)) {
                continue;
            }
            double distance = horizontalDistance(field.center(), source.center());
            if (distance > rebindDistanceThreshold(field, source)) {
                continue;
            }
            RebindCandidate candidate = new RebindCandidate(
                    field,
                    distance,
                    oldSource == null ? "missing source replacement" : oldSource.sourceType().name() + " replacement"
            );
            if (best == null || candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private DuplicateCandidate findDuplicateCandidate(
            CloudFieldStore store,
            CloudFieldSource source,
            Map<UUID, CloudFieldSource> liveSourcesByExactFieldId,
            Set<UUID> assignedFieldIds
    ) {
        DuplicateCandidate best = null;
        for (CloudField field : sortedFields(store)) {
            if (field.isExpired() || !field.dimensionId().equals(source.dimensionId())) {
                continue;
            }
            CloudFieldSource oldSource = store.getTargetSource(field.fieldId()).orElse(null);
            if (!sourceTypesCanShareField(oldSource, source)) {
                continue;
            }
            if (!sourceShapeCompatible(field, source) || !sourceWeatherCompatible(field, oldSource, source)) {
                continue;
            }
            double distance = horizontalDistance(field.center(), source.center());
            if (distance > rebindDistanceThreshold(field, source)) {
                continue;
            }
            boolean oldSourceStillLive = oldSource != null && liveSourcesByExactFieldId.containsKey(factory.fieldIdFor(oldSource));
            boolean stale = store.isSourceMissing(field.fieldId()) || !oldSourceStillLive;
            DuplicateCandidate candidate = new DuplicateCandidate(
                    field,
                    distance,
                    stale,
                    stale && !assignedFieldIds.contains(field.fieldId())
            );
            if (best == null || candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static List<CloudField> sortedFields(CloudFieldStore store) {
        List<CloudField> fields = new ArrayList<>(store.listActiveFields());
        fields.sort((first, second) -> first.fieldId().compareTo(second.fieldId()));
        return fields;
    }

    private static boolean sourceShapeCompatible(CloudField field, CloudFieldSource source) {
        float smallerRadius = Math.max(1.0F, Math.min(field.radius(), source.radius()));
        float largerRadius = Math.max(field.radius(), source.radius());
        if ((largerRadius / smallerRadius) > RADIUS_RATIO_LIMIT
                && Math.abs(field.radius() - source.radius()) > RADIUS_ABSOLUTE_TOLERANCE) {
            return false;
        }

        float fieldHeight = Math.max(1.0F, field.topY() - field.baseY());
        float sourceHeight = Math.max(1.0F, source.topY() - source.baseY());
        float verticalTolerance = Math.max(40.0F, Math.max(fieldHeight, sourceHeight) * 0.60F);
        return Math.abs(field.baseY() - source.baseY()) <= verticalTolerance
                && Math.abs(field.topY() - source.topY()) <= verticalTolerance;
    }

    private static boolean sourceWeatherCompatible(CloudField field, CloudFieldSource oldSource, CloudFieldSource newSource) {
        if (Math.abs(field.density() - newSource.density()) > DENSITY_TOLERANCE
                || Math.abs(field.coverage() - newSource.coverage()) > COVERAGE_TOLERANCE
                || Math.abs(field.humidityInfluence() - newSource.humidityInfluence()) > HYDRATION_TOLERANCE
                || Math.abs(field.stormPotential() - newSource.stormPotential()) > STORM_TOLERANCE) {
            return false;
        }
        if (oldSource == null) {
            return true;
        }
        if (!oldSource.dimensionId().equals(newSource.dimensionId())) {
            return false;
        }
        if (!oldSource.morphologyFamily().equals(newSource.morphologyFamily())
                && Math.abs(oldSource.stormPotential() - newSource.stormPotential()) > 0.35F) {
            return false;
        }
        return Math.abs(oldSource.density() - newSource.density()) <= DENSITY_TOLERANCE
                && Math.abs(oldSource.coverage() - newSource.coverage()) <= COVERAGE_TOLERANCE;
    }

    private static boolean sourceTypesCanShareField(CloudFieldSource oldSource, CloudFieldSource newSource) {
        if (newSource == null) {
            return false;
        }
        if (oldSource == null) {
            return newSource.sourceType() != CloudFieldSourceType.MANUAL_DEBUG;
        }
        CloudFieldSourceType oldType = oldSource.sourceType();
        CloudFieldSourceType newType = newSource.sourceType();
        if (oldType == newType) {
            return true;
        }
        if (oldType == CloudFieldSourceType.MANUAL_DEBUG || newType == CloudFieldSourceType.MANUAL_DEBUG) {
            return false;
        }
        if (oldType == CloudFieldSourceType.WEATHER_SUMMARY || newType == CloudFieldSourceType.WEATHER_SUMMARY) {
            return false;
        }
        return true;
    }

    private static double rebindDistanceThreshold(CloudField field, CloudFieldSource source) {
        double dynamic = Math.max(field.radius(), source.radius()) * 0.70D;
        return Math.min(REBIND_DISTANCE_CAP, Math.max(REBIND_DISTANCE_FLOOR, dynamic));
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        Vec3 safeFirst = first == null ? Vec3.ZERO : first;
        Vec3 safeSecond = second == null ? Vec3.ZERO : second;
        double dx = safeFirst.x() - safeSecond.x();
        double dz = safeFirst.z() - safeSecond.z();
        return Math.sqrt((dx * dx) + (dz * dz));
    }

    public CloudFieldFactory factory() {
        return factory;
    }

    public record ApplyResult(
            int created,
            int updated,
            int removed,
            int unchanged,
            Collection<CloudFieldUpdatePlan> plans,
            int activeFieldCountBeforeApply,
            int collectedSourceCount,
            int acceptedSourceCount,
            int missingSourceFields,
            int recoveredSourceFields,
            int reboundSourceFields,
            int skippedRebindCandidates,
            int duplicateSourcesSkipped,
            int duplicateNearActiveField,
            int duplicateNearStaleField,
            Collection<CloudFieldRemovalDebugInfo> removals,
            Collection<CloudFieldSourceRebindDebugInfo> rebinds
    ) {
        public ApplyResult(
                int created,
                int updated,
                int removed,
                int unchanged,
                Collection<CloudFieldUpdatePlan> plans
        ) {
            this(created, updated, removed, unchanged, plans, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
        }

        public ApplyResult {
            plans = plans == null ? List.of() : List.copyOf(plans);
            missingSourceFields = Math.max(0, missingSourceFields);
            recoveredSourceFields = Math.max(0, recoveredSourceFields);
            reboundSourceFields = Math.max(0, reboundSourceFields);
            skippedRebindCandidates = Math.max(0, skippedRebindCandidates);
            duplicateSourcesSkipped = Math.max(0, duplicateSourcesSkipped);
            duplicateNearActiveField = Math.max(0, duplicateNearActiveField);
            duplicateNearStaleField = Math.max(0, duplicateNearStaleField);
            removals = removals == null ? List.of() : List.copyOf(removals);
            rebinds = rebinds == null ? List.of() : List.copyOf(rebinds);
        }
    }

    private record RebindCandidate(CloudField field, double distance, String reason)
            implements Comparable<RebindCandidate> {
        @Override
        public int compareTo(RebindCandidate other) {
            int distanceCompare = Double.compare(distance, other.distance);
            if (distanceCompare != 0) {
                return distanceCompare;
            }
            return field.fieldId().compareTo(other.field.fieldId());
        }
    }

    private record DuplicateCandidate(
            CloudField field,
            double distance,
            boolean stale,
            boolean rebindCandidateSkipped
    ) implements Comparable<DuplicateCandidate> {
        @Override
        public int compareTo(DuplicateCandidate other) {
            int distanceCompare = Double.compare(distance, other.distance);
            if (distanceCompare != 0) {
                return distanceCompare;
            }
            return field.fieldId().compareTo(other.field.fieldId());
        }
    }
}
