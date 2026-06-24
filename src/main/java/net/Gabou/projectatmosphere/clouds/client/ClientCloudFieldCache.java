package net.Gabou.projectatmosphere.clouds.client;

import net.Gabou.projectatmosphere.clouds.field.CloudField;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldDistanceClassifier;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldHydrationController;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRendererInput;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRuntimeState;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.Gabou.projectatmosphere.clouds.field.CloudLodBand;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Client-side cache for synced CloudField snapshots. It owns client-local
 * LOD/hydration runtime state and produces future renderer input.
 */
public final class ClientCloudFieldCache {
    private static final CloudFieldDistanceClassifier DISTANCE_CLASSIFIER = CloudFieldDistanceClassifier.defaultClassifier();
    private static final CloudFieldHydrationController HYDRATION_CONTROLLER = CloudFieldHydrationController.defaultController();
    private static final ConcurrentMap<UUID, CloudFieldRuntimeState> RUNTIME_STATES = new ConcurrentHashMap<>();

    private static volatile List<CloudFieldSnapshot> currentSnapshots = List.of();

    private ClientCloudFieldCache() {
    }

    public static List<CloudFieldSnapshot> getCurrentSnapshots() {
        return currentSnapshots;
    }

    public static void setCurrentSnapshots(Collection<CloudFieldSnapshot> snapshots) {
        List<CloudFieldSnapshot> copied = snapshots == null ? List.of() : List.copyOf(snapshots);
        currentSnapshots = copied;

        Set<UUID> activeIds = new HashSet<>();
        for (CloudFieldSnapshot snapshot : copied) {
            if (snapshot != null) {
                activeIds.add(snapshot.fieldId());
            }
        }
        RUNTIME_STATES.keySet().removeIf(id -> !activeIds.contains(id));
    }

    public static void clear() {
        currentSnapshots = List.of();
        RUNTIME_STATES.clear();
    }

    public static boolean hasFields() {
        return !currentSnapshots.isEmpty();
    }

    public static CloudFieldRendererInput createRendererInput(Vec3 cameraPosition, long worldTime, float partialTick) {
        Vec3 camera = cameraPosition == null ? Vec3.ZERO : cameraPosition;
        List<CloudFieldSnapshot> localized = currentSnapshots.stream()
                .map(snapshot -> localize(snapshot, camera, worldTime, partialTick))
                .toList();
        return new CloudFieldRendererInput(localized, worldTime, partialTick, camera);
    }

    private static CloudFieldSnapshot localize(
            CloudFieldSnapshot snapshot,
            Vec3 cameraPosition,
            long worldTime,
            float partialTick
    ) {
        Vec3 center = interpolateCenter(snapshot, partialTick);
        CloudField field = fieldFromSnapshot(snapshot, center);
        CloudLodBand lodBand = DISTANCE_CLASSIFIER.classify(center, snapshot.radius(), cameraPosition);
        CloudFieldRuntimeState previous = RUNTIME_STATES.get(snapshot.fieldId());
        float deltaTicks = previous == null
                ? 1.0F
                : Math.max(0.0F, Math.min(20.0F, (float) (worldTime - previous.lastUpdateWorldTime()) + partialTick));
        CloudFieldRuntimeState runtime = HYDRATION_CONTROLLER.update(
                field,
                previous,
                lodBand,
                worldTime,
                deltaTicks,
                snapshot.center()
        );
        RUNTIME_STATES.put(snapshot.fieldId(), runtime);

        return new CloudFieldSnapshot(
                snapshot.fieldId(),
                snapshot.seed(),
                snapshot.dimensionId(),
                center,
                snapshot.center(),
                snapshot.radius(),
                snapshot.baseY(),
                snapshot.topY(),
                snapshot.density(),
                snapshot.coverage(),
                snapshot.growth(),
                snapshot.decay(),
                snapshot.humidityInfluence(),
                snapshot.windVector(),
                snapshot.verticalDevelopment(),
                snapshot.stormPotential(),
                snapshot.sourceKind(),
                runtime.currentLodBand(),
                runtime.previousLodBand(),
                runtime.hydrationState(),
                runtime.hydrationProgress(),
                snapshot.targetCloudletCount(),
                runtime.currentCloudletCount(),
                snapshot.fieldAgeTicks(),
                snapshot.lifetimeTicks(),
                worldTime,
                partialTick,
                cameraPosition
        );
    }

    private static CloudField fieldFromSnapshot(CloudFieldSnapshot snapshot, Vec3 center) {
        return new CloudField(
                snapshot.fieldId(),
                snapshot.seed(),
                snapshot.dimensionId(),
                center,
                snapshot.radius(),
                snapshot.baseY(),
                snapshot.topY(),
                snapshot.density(),
                snapshot.coverage(),
                snapshot.growth(),
                snapshot.decay(),
                snapshot.humidityInfluence(),
                snapshot.windVector(),
                snapshot.verticalDevelopment(),
                snapshot.stormPotential(),
                snapshot.targetCloudletCount(),
                snapshot.fieldAgeTicks(),
                snapshot.lifetimeTicks()
        );
    }

    private static Vec3 interpolateCenter(CloudFieldSnapshot snapshot, float partialTick) {
        Vec3 previous = snapshot.previousCenter() == null ? snapshot.center() : snapshot.previousCenter();
        Vec3 current = snapshot.center() == null ? previous : snapshot.center();
        float t = Math.max(0.0F, Math.min(1.0F, Float.isFinite(partialTick) ? partialTick : 0.0F));
        return previous.add(current.subtract(previous).scale(t));
    }
}
