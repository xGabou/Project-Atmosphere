package net.Gabou.projectatmosphere.clouds.field.runtime;

import net.Gabou.projectatmosphere.clouds.field.CloudField;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRendererInput;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRemovalDebugInfo;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRuntimeState;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshotFactory;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSourceRebindDebugInfo;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldStore;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldTarget;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldTargetResolver;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldTickContext;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldBackendBridge;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldFactory;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSource;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSourceSnapshot;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSourceType;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldUpdatePlan;
import net.Gabou.projectatmosphere.clouds.field.network.CloudFieldSyncManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Server-side owner for CloudField runtime state. It bridges current PA backend
 * cloud state into a CloudFieldStore and produces render-safe snapshots for
 * network sync. It does not render and does not own shader/cloudlet collision
 * behavior.
 */
public final class CloudFieldRuntimeManager {
    private static final long DEBUG_FIELD_LIFETIME_TICKS = 20L * 60L * 10L;

    private static final CloudFieldRuntimeManager INSTANCE = new CloudFieldRuntimeManager(
            CloudFieldBackendSourceCollector.createDefault(),
            CloudFieldBackendBridge.createDefault(),
            new CloudFieldSnapshotFactory()
    );

    private final ConcurrentMap<ResourceKey<Level>, CloudFieldStore> stores = new ConcurrentHashMap<>();
    private final ConcurrentMap<ResourceKey<Level>, CloudFieldRendererInput> currentInputs = new ConcurrentHashMap<>();
    private final ConcurrentMap<ResourceKey<Level>, CloudFieldSourceSnapshot> sourceSnapshots = new ConcurrentHashMap<>();
    private final ConcurrentMap<ResourceKey<Level>, CloudFieldBackendBridge.ApplyResult> applyResults = new ConcurrentHashMap<>();
    private final ConcurrentMap<ResourceKey<Level>, Long> lastTickByLevel = new ConcurrentHashMap<>();
    private final ConcurrentMap<ResourceKey<Level>, Map<UUID, CloudFieldSource>> manualDebugSources = new ConcurrentHashMap<>();
    private final ConcurrentMap<ResourceKey<Level>, List<CloudFieldRemovalDebugInfo>> recentRemovalHistory = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, SourceDebugInfo> sourceDebugByField = new ConcurrentHashMap<>();

    private final CloudFieldBackendSourceCollector sourceCollector;
    private final CloudFieldBackendBridge backendBridge;
    private final CloudFieldSnapshotFactory snapshotFactory;
    private final CloudFieldTargetResolver targetResolver = CloudFieldTargetResolver.createDefault();

    private CloudFieldRuntimeManager(
            CloudFieldBackendSourceCollector sourceCollector,
            CloudFieldBackendBridge backendBridge,
            CloudFieldSnapshotFactory snapshotFactory
    ) {
        this.sourceCollector = Objects.requireNonNull(sourceCollector, "sourceCollector");
        this.backendBridge = Objects.requireNonNull(backendBridge, "backendBridge");
        this.snapshotFactory = Objects.requireNonNull(snapshotFactory, "snapshotFactory");
    }

    public static CloudFieldRuntimeManager getInstance() {
        return INSTANCE;
    }

    public record DebugFieldSpawnResult(
            CloudFieldBackendBridge.ApplyResult applyResult,
            int replacedManualFields
    ) {
    }

    public CloudFieldRendererInput tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        CloudFieldStore store = storeFor(level);
        CloudFieldSourceSnapshot sourceSnapshot = withManualDebugSources(level, sourceCollector.collect(level));
        CloudFieldBackendBridge.ApplyResult applyResult = backendBridge.applySnapshot(store, sourceSnapshot, true);
        recordSourceDebug(applyResult.plans());
        recordRemovalDebug(level.dimension(), applyResult.removals());

        CloudFieldTickContext tickContext = new CloudFieldTickContext(
                playerAnchor(level),
                level.getGameTime(),
                1.0F,
                0.0F,
                null
        );
        store.tickAll(tickContext);
        recordRemovalDebug(level.dimension(), store.lastExpirationRemovals());

        CloudFieldRendererInput input = snapshotFactory.createRendererInput(
                store.listActiveFields(),
                store.runtimeStateMap(),
                store.targetSourceTypeMap(),
                tickContext
        );
        ResourceKey<Level> dimension = level.dimension();
        currentInputs.put(dimension, input);
        sourceSnapshots.put(dimension, sourceSnapshot);
        applyResults.put(dimension, applyResult);
        lastTickByLevel.put(dimension, level.getGameTime());
        return input;
    }

    public CloudFieldRendererInput ensureCurrent(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Long lastTick = lastTickByLevel.get(level.dimension());
        if (lastTick == null || lastTick.longValue() != level.getGameTime()) {
            return tick(level);
        }
        return currentInput(level);
    }

    public CloudFieldRendererInput currentInput(ServerLevel level) {
        if (level == null) {
            return CloudFieldRendererInput.empty(0L, 0.0F, Vec3.ZERO);
        }
        return currentInputs.getOrDefault(
                level.dimension(),
                CloudFieldRendererInput.empty(level.getGameTime(), 0.0F, playerAnchor(level))
        );
    }

    public List<CloudFieldSnapshot> currentSnapshots(ServerLevel level) {
        return currentInput(level).fields();
    }

    public CloudFieldStore storeFor(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return stores.computeIfAbsent(level.dimension(), ignored -> CloudFieldStore.createDefault());
    }

    public CloudFieldSourceSnapshot lastSourceSnapshot(ServerLevel level) {
        if (level == null) {
            return CloudFieldSourceSnapshot.of(List.of(), 0L, "", "empty");
        }
        return sourceSnapshots.getOrDefault(
                level.dimension(),
                CloudFieldSourceSnapshot.of(List.of(), level.getGameTime(), level.dimension().location().toString(), "empty")
        );
    }

    public CloudFieldBackendBridge.ApplyResult lastApplyResult(ServerLevel level) {
        if (level == null) {
            return new CloudFieldBackendBridge.ApplyResult(0, 0, 0, 0, List.of());
        }
        return applyResults.getOrDefault(
                level.dimension(),
                new CloudFieldBackendBridge.ApplyResult(0, 0, 0, 0, List.of())
        );
    }

    public void clear(ServerLevel level) {
        if (level == null) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        CloudFieldStore existingStore = stores.get(dimension);
        if (existingStore != null) {
            for (CloudField field : existingStore.listActiveFields()) {
                sourceDebugByField.remove(field.fieldId());
            }
        }
        stores.remove(dimension);
        currentInputs.remove(dimension);
        sourceSnapshots.remove(dimension);
        applyResults.remove(dimension);
        lastTickByLevel.remove(dimension);
        manualDebugSources.remove(dimension);
    }

    /**
     * Creates one debug CloudField at a player's position through the normal
     * backend bridge/store path. This is server-side test tooling only.
     */
    public DebugFieldSpawnResult spawnDebugField(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        ServerLevel level = player.serverLevel();
        int replacedManualFields = clearManualDebugFields(level);
        Vec3 playerPosition = player.position();
        Vec3 center = new Vec3(playerPosition.x(), playerPosition.y() + 120.0D, playerPosition.z());
        Vec3 wind = nearestBackendWind(level, center);
        String dimensionId = level.dimension().location().toString();
        String sourceId = String.format(
                Locale.ROOT,
                "manual-debug:%s:%d:%d:%d",
                dimensionId,
                BlockPos.containing(playerPosition).getX(),
                BlockPos.containing(playerPosition).getZ(),
                level.getGameTime()
        );
        CloudFieldSource source = new CloudFieldSource(
                sourceId,
                CloudFieldSourceType.MANUAL_DEBUG,
                dimensionId,
                center,
                120.0F,
                (float) playerPosition.y() + 80.0F,
                (float) playerPosition.y() + 160.0F,
                0.80F,
                0.75F,
                0.80F,
                wind,
                1.0F,
                0.0F,
                0.35F,
                0.0F,
                debugSeed(sourceId, level.getGameTime()),
                0L,
                DEBUG_FIELD_LIFETIME_TICKS,
                96,
                "cumulus_humilis",
                "puff",
                true
        );
        UUID fieldId = backendBridge.factory().fieldIdFor(source);
        manualDebugSources.compute(level.dimension(), (ignored, existing) -> {
            Map<UUID, CloudFieldSource> next = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
            next.put(fieldId, source);
            return Map.copyOf(next);
        });

        CloudFieldStore store = storeFor(level);
        CloudFieldSourceSnapshot snapshot = withManualDebugSources(level, sourceCollector.collect(level));
        CloudFieldBackendBridge.ApplyResult applyResult = backendBridge.applySnapshot(store, snapshot, false);
        recordSourceDebug(applyResult.plans());
        recordRemovalDebug(level.dimension(), applyResult.removals());

        CloudFieldTickContext tickContext = new CloudFieldTickContext(
                playerPosition,
                level.getGameTime(),
                1.0F,
                0.0F,
                null
        );
        store.tickAll(tickContext);
        recordRemovalDebug(level.dimension(), store.lastExpirationRemovals());

        CloudFieldRendererInput input = snapshotFactory.createRendererInput(
                store.listActiveFields(),
                store.runtimeStateMap(),
                store.targetSourceTypeMap(),
                tickContext
        );
        ResourceKey<Level> dimension = level.dimension();
        currentInputs.put(dimension, input);
        sourceSnapshots.put(dimension, snapshot);
        applyResults.put(dimension, applyResult);
        lastTickByLevel.put(dimension, level.getGameTime());
        return new DebugFieldSpawnResult(applyResult, replacedManualFields);
    }

    /**
     * Clears server-side CloudField runtime state without removing backend PA
     * cloud regions. The next backend tick may repopulate fields from sources.
     */
    public int clearRuntimeFields(ServerLevel level) {
        if (level == null) {
            return 0;
        }
        int removed = storeFor(level).size();
        clear(level);
        return removed;
    }

    private int clearManualDebugFields(ServerLevel level) {
        if (level == null) {
            return 0;
        }
        ResourceKey<Level> dimension = level.dimension();
        manualDebugSources.remove(dimension);

        CloudFieldStore store = stores.get(dimension);
        if (store == null) {
            return 0;
        }

        int removed = 0;
        for (CloudField field : store.listActiveFields()) {
            CloudFieldSource source = store.getTargetSource(field.fieldId()).orElse(null);
            SourceDebugInfo debugInfo = sourceDebugByField.get(field.fieldId());
            boolean manualDebugSource = source != null && source.sourceType() == CloudFieldSourceType.MANUAL_DEBUG;
            boolean manualDebugRecord = debugInfo != null && "MANUAL_DEBUG".equals(debugInfo.sourceType());
            if ((manualDebugSource || manualDebugRecord) && store.removeField(field.fieldId()).isPresent()) {
                sourceDebugByField.remove(field.fieldId());
                removed++;
            }
        }
        return removed;
    }

    public List<String> describeCloudFields(ServerLevel level) {
        CloudFieldRendererInput input = ensureCurrent(level);
        List<CloudFieldSnapshot> fields = new ArrayList<>(input.fields());
        fields.sort(Comparator.comparing(snapshot -> snapshot.fieldId().toString()));
        List<String> lines = new ArrayList<>();
        for (CloudFieldSnapshot snapshot : fields) {
            SourceDebugInfo source = sourceDebugByField.get(snapshot.fieldId());
            String sourceText = formatSourceDebug(source);
            lines.add(String.format(
                    Locale.ROOT,
                    "%s source=%s lod=%s hydration=%s %.2f density=%.2f coverage=%.2f cloudlets=%d/%d",
                    shortId(snapshot.fieldId()),
                    sourceText,
                    snapshot.lodBand(),
                    snapshot.hydrationState(),
                    snapshot.hydrationProgress(),
                    snapshot.density(),
                    snapshot.coverage(),
                    snapshot.activeCloudletCount(),
                    snapshot.targetCloudletCount()
            ));
        }
        return List.copyOf(lines);
    }

    /**
     * Describes current persistent field values against the resolved evolution
     * target used by the production CloudField pipeline.
     */
    public List<String> describeCloudFieldEvolution(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        ensureCurrent(level);
        CloudFieldStore store = storeFor(level);
        List<CloudField> fields = new ArrayList<>(store.listActiveFields());
        fields.sort(Comparator.comparing(field -> field.fieldId().toString()));

        List<String> lines = new ArrayList<>();
        for (CloudField field : fields) {
            CloudFieldSource source = store.getTargetSource(field.fieldId()).orElse(null);
            CloudFieldRuntimeState runtimeState = store.getRuntimeState(field.fieldId()).orElse(null);
            int missingTicks = store.missingSourceTicks(field.fieldId());
            CloudFieldTarget target = source == null ? null : targetResolver.resolve(field, source, missingTicks);
            SourceDebugInfo sourceDebug = sourceDebugByField.get(field.fieldId());
            boolean createdOnLastApply = sourceDebug != null && sourceDebug.createdOnLastApply();
            lines.add(formatEvolutionDebug(field, runtimeState, target, source != null, missingTicks, createdOnLastApply));
        }
        return List.copyOf(lines);
    }

    /**
     * Describes source collection, backend apply, and removal activity from the
     * latest CloudField runtime tick.
     */
    public List<String> describeCloudFieldStats(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        ensureCurrent(level);
        CloudFieldStore store = storeFor(level);
        CloudFieldBackendBridge.ApplyResult applyResult = lastApplyResult(level);
        Long lastTick = lastTickByLevel.get(level.dimension());
        CloudFieldRendererInput currentInput = currentInput(level);
        CloudFieldBackendSourceCollector.DebugInfo collectorDebug = sourceCollector.lastDebugInfo(level);

        List<CloudFieldRemovalDebugInfo> removals = new ArrayList<>(recentRemovalHistory(level));

        int missingSourceGraceExpired = countReason(removals, CloudFieldRemovalDebugInfo.Reason.MISSING_SOURCE_GRACE_EXPIRED);
        int invalidSource = countReason(removals, CloudFieldRemovalDebugInfo.Reason.INVALID_SOURCE);
        int lifetime = countReason(removals, CloudFieldRemovalDebugInfo.Reason.LIFETIME_EXPIRED);
        int decay = countReason(removals, CloudFieldRemovalDebugInfo.Reason.DECAY_EXPIRED);
        int distance = countReason(removals, CloudFieldRemovalDebugInfo.Reason.DISTANCE);
        int dimension = countReason(removals, CloudFieldRemovalDebugInfo.Reason.DIMENSION);
        int lod = countReason(removals, CloudFieldRemovalDebugInfo.Reason.LOD);
        int cleanup = countReason(removals, CloudFieldRemovalDebugInfo.Reason.CLEANUP);
        int staleSourceFields = 0;
        int maxMissingTicks = 0;
        int manualDebugFields = 0;
        for (CloudField field : store.listActiveFields()) {
            CloudFieldSource source = store.getTargetSource(field.fieldId()).orElse(null);
            if (source != null && source.sourceType() == CloudFieldSourceType.MANUAL_DEBUG) {
                manualDebugFields++;
            }
            int missingTicks = store.missingSourceTicks(field.fieldId());
            if (missingTicks > 0) {
                staleSourceFields++;
                maxMissingTicks = Math.max(maxMissingTicks, missingTicks);
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add(String.format(
                Locale.ROOT,
                "runtimeTickCalled=%s lastTickGameTime=%d dimension=%s snapshotsProduced=%d snapshotsSynced=%d lastSyncTick=%d serverSnapshotsPresent=%s",
                lastTick != null,
                lastTick == null ? -1L : lastTick,
                level.dimension().location(),
                currentInput.fields().size(),
                CloudFieldSyncManager.getLastSyncedCount(),
                CloudFieldSyncManager.getLastSyncTick(),
                !currentInput.fields().isEmpty()
        ));
        lines.add(String.format(
                Locale.ROOT,
                "collector playerBlock=%s dimension=%s sampledRegions=%d activeRegions=%d sampledPaClusters=%d activeClusters=%d regionFallbacks=%d weatherSampledRegions=%d weatherNoCloudRegion=%d weatherFallbackCandidates=%d weatherFallbackCreated=%d finalCollectedSources=%d",
                collectorDebug.playerBlockPosition(),
                collectorDebug.dimensionId(),
                collectorDebug.sampledRegionCount(),
                collectorDebug.activeRegionCount(),
                collectorDebug.sampledPaClusterCount(),
                collectorDebug.activeClusterCount(),
                collectorDebug.regionFallbackCount(),
                collectorDebug.sampledWeatherRegionCount(),
                collectorDebug.weatherRegionsWithoutCloudRegion(),
                collectorDebug.weatherFallbackCandidateCount(),
                collectorDebug.weatherFallbackCreatedCount(),
                collectorDebug.finalCollectedSources()
        ));
        lines.add(String.format(
                Locale.ROOT,
                "collectorRejected total=%d noRegionState=%d noAtmosphereState=%d humidityTooLow=%d cloudCoverTooLow=%d densityTooLow=%d radiusTooSmall=%d invalidBaseTop=%d wrongDimension=%d clusterInactive=%d sourceDuplicate=%d other=%d",
                collectorDebug.rejectedCandidateCount(),
                collectorDebug.rejectedNoRegionState(),
                collectorDebug.rejectedNoAtmosphereState(),
                collectorDebug.rejectedHumidityTooLow(),
                collectorDebug.rejectedCloudCoverTooLow(),
                collectorDebug.rejectedDensityTooLow(),
                collectorDebug.rejectedRadiusTooSmall(),
                collectorDebug.rejectedInvalidBaseTop(),
                collectorDebug.rejectedWrongDimension(),
                collectorDebug.rejectedClusterInactive(),
                collectorDebug.rejectedSourceDuplicate(),
                collectorDebug.rejectedOther()
        ));
        lines.add(String.format(
                Locale.ROOT,
                "manualDebugFields=%d manualDebugSources=%d manualDebugRegistryActive=%s manualDebugSourceLossExempt=%s",
                manualDebugFields,
                manualDebugSourceCount(level),
                manualDebugSourceCount(level) > 0,
                false
        ));
        lines.add(String.format(
                Locale.ROOT,
                "activeBeforeApply=%d currentActive=%d collectedSources=%d acceptedSources=%d created=%d updated=%d unchanged=%d missingSourceFields=%d recoveredSourceFields=%d reboundSourceFields=%d duplicateSourcesSkipped=%d duplicateNearActiveField=%d duplicateNearStaleField=%d staleSourceFields=%d maxMissingTicks=%d missingGraceTicks=%d removedBackend=%d removedExpired=%d removedTotal=%d",
                applyResult.activeFieldCountBeforeApply(),
                store.size(),
                applyResult.collectedSourceCount(),
                applyResult.acceptedSourceCount(),
                applyResult.created(),
                applyResult.updated(),
                applyResult.unchanged(),
                applyResult.missingSourceFields(),
                applyResult.recoveredSourceFields(),
                applyResult.reboundSourceFields(),
                applyResult.duplicateSourcesSkipped(),
                applyResult.duplicateNearActiveField(),
                applyResult.duplicateNearStaleField(),
                staleSourceFields,
                maxMissingTicks,
                store.missingSourceGraceTicks(),
                applyResult.removed(),
                store.lastExpirationRemovals().size(),
                removals.size()
        ));
        lines.add(String.format(
                Locale.ROOT,
                "removalReasons missingGraceExpired=%d invalidSource=%d lifetime=%d decay=%d distance=%d dimension=%d lod=%d cleanup=%d",
                missingSourceGraceExpired,
                invalidSource,
                lifetime,
                decay,
                distance,
                dimension,
                lod,
                cleanup
        ));
        lines.add(String.format(
                Locale.ROOT,
                "rebindDebug rebound=%d skippedCandidates=%d duplicateSkipped=%d",
                applyResult.reboundSourceFields(),
                applyResult.skippedRebindCandidates(),
                applyResult.duplicateSourcesSkipped()
        ));
        for (CloudFieldSourceRebindDebugInfo rebind : applyResult.rebinds()) {
            lines.add(String.format(
                    Locale.ROOT,
                    "rebound %s %s:%s -> %s:%s distance=%.1f reason=%s",
                    rebind.fieldId(),
                    rebind.oldSourceType(),
                    rebind.oldSourceId(),
                    rebind.newSourceType(),
                    rebind.newSourceId(),
                    rebind.distanceBlocks(),
                    rebind.reason()
            ));
        }
        if (removals.isEmpty()) {
            lines.add("recentRemoved: none");
        } else {
            for (CloudFieldRemovalDebugInfo removal : removals) {
                lines.add(String.format(
                        Locale.ROOT,
                        "recentRemoved %s reason=%s age=%d/%d sourceMissing=%s",
                        removal.fieldId(),
                        removal.reason(),
                        removal.ageTicks(),
                        removal.lifetimeTicks(),
                        removal.sourceMissing()
                ));
            }
        }
        for (CloudField field : store.listActiveFields()) {
            int missingTicks = store.missingSourceTicks(field.fieldId());
            if (missingTicks > 0) {
                lines.add(String.format(
                        Locale.ROOT,
                        "stale %s missingTicks=%d/%d age=%d/%d",
                        field.fieldId(),
                        missingTicks,
                        store.missingSourceGraceTicks(),
                        field.ageTicks(),
                        field.lifetimeTicks()
                ));
            }
        }
        return List.copyOf(lines);
    }

    private void recordSourceDebug(Collection<CloudFieldUpdatePlan> plans) {
        if (plans == null) {
            return;
        }
        for (CloudFieldUpdatePlan plan : plans) {
            if (plan == null || plan.fieldId() == null) {
                continue;
            }
            if (plan.removeField()) {
                sourceDebugByField.remove(plan.fieldId());
                continue;
            }
            sourceDebugByField.put(
                    plan.fieldId(),
                    new SourceDebugInfo(plan.sourceId(), plan.sourceType().name(), plan.createNewField())
            );
        }
    }

    private void recordRemovalDebug(ResourceKey<Level> dimension, Collection<CloudFieldRemovalDebugInfo> removals) {
        if (removals == null) {
            return;
        }
        List<CloudFieldRemovalDebugInfo> copied = new ArrayList<>();
        for (CloudFieldRemovalDebugInfo removal : removals) {
            if (removal != null) {
                sourceDebugByField.remove(removal.fieldId());
                removeManualDebugSource(dimension, removal.fieldId());
                copied.add(removal);
            }
        }
        if (!copied.isEmpty() && dimension != null) {
            recentRemovalHistory.compute(dimension, (ignored, existing) -> {
                List<CloudFieldRemovalDebugInfo> next = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
                next.addAll(copied);
                int fromIndex = Math.max(0, next.size() - 10);
                return List.copyOf(next.subList(fromIndex, next.size()));
            });
        }
    }

    private CloudFieldSourceSnapshot withManualDebugSources(ServerLevel level, CloudFieldSourceSnapshot snapshot) {
        CloudFieldSourceSnapshot base = snapshot == null
                ? CloudFieldSourceSnapshot.of(List.of(), level.getGameTime(), level.dimension().location().toString(), "empty")
                : snapshot;
        Collection<CloudFieldSource> manualSources = manualDebugSources
                .getOrDefault(level.dimension(), Map.of())
                .values();
        if (manualSources.isEmpty()) {
            return base;
        }
        List<CloudFieldSource> merged = new ArrayList<>(base.sources());
        merged.addAll(manualSources);
        return CloudFieldSourceSnapshot.of(
                merged,
                base.capturedGameTime(),
                base.dimensionId(),
                base.sourceDescription() + "+manual-debug"
        );
    }

    private void removeManualDebugSource(ResourceKey<Level> dimension, UUID fieldId) {
        if (dimension == null || fieldId == null) {
            return;
        }
        manualDebugSources.computeIfPresent(dimension, (ignored, existing) -> {
            if (!existing.containsKey(fieldId)) {
                return existing;
            }
            Map<UUID, CloudFieldSource> next = new LinkedHashMap<>(existing);
            next.remove(fieldId);
            return next.isEmpty() ? null : Map.copyOf(next);
        });
    }

    private int manualDebugSourceCount(ServerLevel level) {
        if (level == null) {
            return 0;
        }
        return manualDebugSources.getOrDefault(level.dimension(), Map.of()).size();
    }

    private List<CloudFieldRemovalDebugInfo> recentRemovalHistory(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        return recentRemovalHistory.getOrDefault(level.dimension(), List.of());
    }

    private static String formatEvolutionDebug(
            CloudField field,
            CloudFieldRuntimeState runtimeState,
            CloudFieldTarget target,
            boolean hasTargetSource,
            int missingSourceTicks,
            boolean createdOnLastApply
    ) {
        String targetText = target == null ? "none" : "present";
        String sourceState = !hasTargetSource ? "none" : missingSourceTicks > 0 ? "stale" : "live";
        float currentHydration = runtimeState == null ? 0.0F : runtimeState.hydrationProgress();
        String runtimeHydrationState = runtimeState == null ? "none" : runtimeState.hydrationState().name();
        return String.format(
                Locale.ROOT,
                "%s state=%s target=%s source=%s missingTicks=%d center=%s -> %s radius=%.1f -> %s baseY=%.1f -> %s topY=%.1f -> %s density=%.3f -> %s coverage=%.3f -> %s fieldHydration=%.3f -> %s runtimeHydration=%s %.3f vertical=%.3f -> %s storm=%.3f -> %s decay=%.3f -> %s age=%d/%d",
                shortId(field.fieldId()),
                createdOnLastApply ? "new" : "persistent",
                targetText,
                sourceState,
                missingSourceTicks,
                formatVec(field.center()),
                target == null ? "none" : formatVec(target.center()),
                field.radius(),
                target == null ? "none" : formatFloat(target.radius()),
                field.baseY(),
                target == null ? "none" : formatFloat(target.baseY()),
                field.topY(),
                target == null ? "none" : formatFloat(target.topY()),
                field.density(),
                target == null ? "none" : formatFloat(target.density()),
                field.coverage(),
                target == null ? "none" : formatFloat(target.coverage()),
                field.humidityInfluence(),
                target == null ? "none" : formatFloat(target.targetHydration()),
                runtimeHydrationState,
                currentHydration,
                field.verticalDevelopment(),
                target == null ? "none" : formatFloat(target.verticalDevelopment()),
                field.stormPotential(),
                target == null ? "none" : formatFloat(target.stormPotential()),
                field.decay(),
                target == null ? "none" : formatFloat(target.decayPressure()),
                field.ageTicks(),
                field.lifetimeTicks()
        );
    }

    private static int countReason(List<CloudFieldRemovalDebugInfo> removals, CloudFieldRemovalDebugInfo.Reason reason) {
        int count = 0;
        for (CloudFieldRemovalDebugInfo removal : removals) {
            if (removal != null && removal.reason() == reason) {
                count++;
            }
        }
        return count;
    }

    private Vec3 nearestBackendWind(ServerLevel level, Vec3 center) {
        CloudFieldSourceSnapshot snapshot = lastSourceSnapshot(level);
        if (snapshot.isEmpty()) {
            snapshot = sourceCollector.collect(level);
        }
        CloudFieldSource best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3 safeCenter = center == null ? Vec3.ZERO : center;
        for (CloudFieldSource source : snapshot.activeSources()) {
            if (source == null || source.wind() == null) {
                continue;
            }
            double distance = source.center().distanceToSqr(safeCenter);
            if (best == null || distance < bestDistance) {
                best = source;
                bestDistance = distance;
            }
        }
        return best == null ? Vec3.ZERO : best.wind();
    }

    private static long debugSeed(String sourceId, long gameTime) {
        long value = sourceId == null ? 0L : sourceId.hashCode();
        value ^= Long.rotateLeft(gameTime, 21);
        value ^= 0x9E3779B97F4A7C15L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    private static String formatVec(Vec3 value) {
        Vec3 safeValue = value == null ? Vec3.ZERO : value;
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", safeValue.x(), safeValue.y(), safeValue.z());
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static Vec3 playerAnchor(ServerLevel level) {
        if (level == null || level.players().isEmpty()) {
            return Vec3.ZERO;
        }

        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        int count = 0;
        for (ServerPlayer player : level.players()) {
            if (player == null) {
                continue;
            }
            Vec3 position = player.position();
            x += position.x();
            y += position.y();
            z += position.z();
            count++;
        }
        if (count == 0) {
            return Vec3.ZERO;
        }
        return new Vec3(x / count, y / count, z / count);
    }

    private static String formatSourceDebug(SourceDebugInfo source) {
        if (source == null) {
            return "unknown";
        }
        return sourceCategory(source.sourceType()) + "(" + source.sourceType() + ":" + source.sourceId() + ")";
    }

    private static String sourceCategory(String sourceType) {
        if ("MANUAL_DEBUG".equals(sourceType)) {
            return "manual_test";
        }
        if ("WEATHER_SUMMARY".equals(sourceType)) {
            return "automatic_weather";
        }
        return "backend";
    }

    private static String shortId(UUID id) {
        if (id == null) {
            return "unknown";
        }
        String value = id.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private record SourceDebugInfo(String sourceId, String sourceType, boolean createdOnLastApply) {
    }
}
