package net.Gabou.projectatmosphere.clouds.field.runtime;

import net.Gabou.projectatmosphere.clouds.field.CloudField;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRendererInput;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshotFactory;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldStore;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldTickContext;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldBackendBridge;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldFactory;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSourceSnapshot;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldUpdatePlan;
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
    private final ConcurrentMap<UUID, SourceDebugInfo> sourceDebugByField = new ConcurrentHashMap<>();

    private final CloudFieldBackendSourceCollector sourceCollector;
    private final CloudFieldBackendBridge backendBridge;
    private final CloudFieldSnapshotFactory snapshotFactory;

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

    public CloudFieldRendererInput tick(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        CloudFieldStore store = storeFor(level);
        CloudFieldSourceSnapshot sourceSnapshot = sourceCollector.collect(level);
        CloudFieldBackendBridge.ApplyResult applyResult = backendBridge.applySnapshot(store, sourceSnapshot, true);
        recordSourceDebug(applyResult.plans());

        CloudFieldTickContext tickContext = new CloudFieldTickContext(
                playerAnchor(level),
                level.getGameTime(),
                1.0F,
                0.0F,
                null
        );
        store.tickAll(tickContext);

        CloudFieldRendererInput input = snapshotFactory.createRendererInput(
                store.listActiveFields(),
                store.runtimeStateMap(),
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
        stores.remove(dimension);
        currentInputs.remove(dimension);
        sourceSnapshots.remove(dimension);
        applyResults.remove(dimension);
        lastTickByLevel.remove(dimension);
    }

    public List<String> describeCloudFields(ServerLevel level) {
        CloudFieldRendererInput input = ensureCurrent(level);
        List<CloudFieldSnapshot> fields = new ArrayList<>(input.fields());
        fields.sort(Comparator.comparing(snapshot -> snapshot.fieldId().toString()));
        List<String> lines = new ArrayList<>();
        for (CloudFieldSnapshot snapshot : fields) {
            SourceDebugInfo source = sourceDebugByField.get(snapshot.fieldId());
            String sourceText = source == null ? "unknown" : source.sourceType() + ":" + source.sourceId();
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
                    new SourceDebugInfo(plan.sourceId(), plan.sourceType().name())
            );
        }
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

    private static String shortId(UUID id) {
        if (id == null) {
            return "unknown";
        }
        String value = id.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private record SourceDebugInfo(String sourceId, String sourceType) {
    }
}
