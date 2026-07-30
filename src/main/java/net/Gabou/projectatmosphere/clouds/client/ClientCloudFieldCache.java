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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private static final double PRESENTATION_DELAY_TICKS = 20.0D;
    private static final double EXTRAPOLATION_LIMIT_TICKS = 5.0D;
    private static final double FADE_TICKS = 10.0D;
    private static final double DISCONTINUITY_MIN_BLOCKS = 256.0D;

    private static final CloudFieldDistanceClassifier DISTANCE_CLASSIFIER = CloudFieldDistanceClassifier.defaultClassifier();
    private static final CloudFieldHydrationController HYDRATION_CONTROLLER = CloudFieldHydrationController.defaultController();
    private static final ConcurrentMap<UUID, CloudFieldRuntimeState> RUNTIME_STATES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Double> RUNTIME_UPDATE_TIMES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, PresentationTrack> PRESENTATION_TRACKS = new ConcurrentHashMap<>();

    private static volatile List<CloudFieldSnapshot> currentSnapshots = List.of();
    private static volatile long latestPacketWorldTime = -1L;

    private ClientCloudFieldCache() {
    }

    public static List<CloudFieldSnapshot> getCurrentSnapshots() {
        return currentSnapshots;
    }

    public static void setCurrentSnapshots(Collection<CloudFieldSnapshot> snapshots) {
        List<CloudFieldSnapshot> copied = snapshots == null ? List.of() : List.copyOf(snapshots);
        currentSnapshots = copied;

        Set<UUID> activeIds = new HashSet<>();
        long packetWorldTime = latestPacketWorldTime;
        for (CloudFieldSnapshot snapshot : copied) {
            if (snapshot != null) {
                activeIds.add(snapshot.fieldId());
                packetWorldTime = Math.max(packetWorldTime, snapshot.worldTime());
                PRESENTATION_TRACKS.compute(snapshot.fieldId(), (id, track) -> {
                    PresentationTrack safeTrack = track == null ? new PresentationTrack() : track;
                    safeTrack.push(snapshot);
                    return safeTrack;
                });
            }
        }
        latestPacketWorldTime = packetWorldTime;
        for (UUID id : PRESENTATION_TRACKS.keySet()) {
            if (!activeIds.contains(id)) {
                PresentationTrack track = PRESENTATION_TRACKS.get(id);
                if (track != null) {
                    track.markMissing();
                }
            }
        }
    }

    public static synchronized void applyDelta(
            Collection<CloudFieldSnapshot> updated,
            Collection<UUID> removed
    ) {
        Map<UUID, CloudFieldSnapshot> next = new LinkedHashMap<>();
        for (CloudFieldSnapshot snapshot : currentSnapshots) {
            if (snapshot != null) {
                next.put(snapshot.fieldId(), snapshot);
            }
        }
        if (removed != null) {
            for (UUID id : removed) {
                if (id == null) {
                    continue;
                }
                next.remove(id);
                PresentationTrack track = PRESENTATION_TRACKS.get(id);
                if (track != null) {
                    track.markMissing();
                }
            }
        }
        long packetWorldTime = latestPacketWorldTime;
        if (updated != null) {
            for (CloudFieldSnapshot snapshot : updated) {
                if (snapshot == null) {
                    continue;
                }
                next.put(snapshot.fieldId(), snapshot);
                packetWorldTime = Math.max(packetWorldTime, snapshot.worldTime());
                PRESENTATION_TRACKS.compute(snapshot.fieldId(), (id, track) -> {
                    PresentationTrack safeTrack = track == null ? new PresentationTrack() : track;
                    safeTrack.push(snapshot);
                    return safeTrack;
                });
            }
        }
        currentSnapshots = List.copyOf(next.values());
        latestPacketWorldTime = packetWorldTime;
    }

    public static void clear() {
        currentSnapshots = List.of();
        RUNTIME_STATES.clear();
        RUNTIME_UPDATE_TIMES.clear();
        PRESENTATION_TRACKS.clear();
        latestPacketWorldTime = -1L;
    }

    public static boolean hasFields() {
        return !currentSnapshots.isEmpty();
    }

    public static CloudFieldRendererInput createRendererInput(Vec3 cameraPosition, long worldTime, float partialTick) {
        Vec3 camera = cameraPosition == null ? Vec3.ZERO : cameraPosition;
        float safePartialTick = Float.isFinite(partialTick) ? partialTick : 0.0F;
        double renderTime = worldTime + safePartialTick;
        bootstrapPresentationTracks();
        List<CloudFieldSnapshot> localized = new ArrayList<>();
        for (UUID id : PRESENTATION_TRACKS.keySet()) {
            PresentationTrack track = PRESENTATION_TRACKS.get(id);
            if (track == null) {
                continue;
            }
            if (track.shouldRemove(renderTime)) {
                PRESENTATION_TRACKS.remove(id, track);
                RUNTIME_STATES.remove(id);
                RUNTIME_UPDATE_TIMES.remove(id);
                continue;
            }
            CloudFieldSnapshot presented = track.present(renderTime, camera);
            if (presented == null) {
                continue;
            }
            localized.add(localize(presented, camera, worldTime, safePartialTick, renderTime));
        }
        return new CloudFieldRendererInput(localized, worldTime, safePartialTick, camera);
    }

    private static void bootstrapPresentationTracks() {
        List<CloudFieldSnapshot> snapshots = currentSnapshots;
        if (snapshots.isEmpty()) {
            return;
        }
        for (CloudFieldSnapshot snapshot : snapshots) {
            if (snapshot == null || PRESENTATION_TRACKS.containsKey(snapshot.fieldId())) {
                continue;
            }
            PRESENTATION_TRACKS.compute(snapshot.fieldId(), (id, track) -> {
                PresentationTrack safeTrack = track == null ? new PresentationTrack() : track;
                safeTrack.push(snapshot);
                return safeTrack;
            });
        }
    }

    private static CloudFieldSnapshot localize(
            CloudFieldSnapshot snapshot,
            Vec3 cameraPosition,
            long worldTime,
            float partialTick,
            double renderTime
    ) {
        Vec3 center = snapshot.center();
        CloudField field = fieldFromSnapshot(snapshot, center);
        CloudLodBand lodBand = DISTANCE_CLASSIFIER.classify(center, snapshot.radius(), cameraPosition);
        CloudFieldRuntimeState previous = RUNTIME_STATES.get(snapshot.fieldId());
        Double previousRenderTime = RUNTIME_UPDATE_TIMES.get(snapshot.fieldId());
        float deltaTicks = previousRenderTime == null
                ? 1.0F
                : (float) Math.max(0.0D, Math.min(20.0D, renderTime - previousRenderTime));
        CloudFieldRuntimeState runtime = HYDRATION_CONTROLLER.update(
                field,
                previous,
                lodBand,
                worldTime,
                deltaTicks,
                snapshot.center()
        );
        RUNTIME_STATES.put(snapshot.fieldId(), runtime);
        RUNTIME_UPDATE_TIMES.put(snapshot.fieldId(), renderTime);

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
                snapshot.cloudTypeId(),
                snapshot.morphologyFamily(),
                snapshot.morphologyMembership(),
                snapshot.anvilStrength(),
                snapshot.precipitationIntensity(),
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
                snapshot.cloudTypeId(),
                snapshot.morphologyFamily(),
                snapshot.morphologyMembership(),
                snapshot.anvilStrength(),
                snapshot.precipitationIntensity(),
                snapshot.targetCloudletCount(),
                snapshot.fieldAgeTicks(),
                snapshot.lifetimeTicks()
        );
    }

    private static CloudFieldSnapshot interpolateSnapshot(
            CloudFieldSnapshot older,
            CloudFieldSnapshot newer,
            double targetTime,
            Vec3 cameraPosition,
            float fade
    ) {
        double span = Math.max(1.0D, newer.worldTime() - older.worldTime());
        float t = (float) clamp01((targetTime - older.worldTime()) / span);
        float smoothT = t * t * (3.0F - 2.0F * t);
        // Snapshot centres are authoritative positions, not shape parameters.
        // Linear time interpolation preserves their measured velocity and can
        // never leave the segment between them. Applying wind tangents here
        // made equal frozen endpoints trace a Hermite loop away and back.
        Vec3 center = interpolateCenter(older, newer, t);
        return copySnapshot(
                newer,
                center,
                older.center(),
                lerp(older.radius(), newer.radius(), smoothT),
                lerp(older.baseY(), newer.baseY(), smoothT),
                lerp(older.topY(), newer.topY(), smoothT),
                lerp(older.density(), newer.density(), smoothT) * fade,
                lerp(older.coverage(), newer.coverage(), smoothT) * fade,
                lerp(older.growth(), newer.growth(), smoothT),
                lerp(older.decay(), newer.decay(), smoothT),
                lerp(older.humidityInfluence(), newer.humidityInfluence(), smoothT),
                lerp(older.windVector(), newer.windVector(), smoothT),
                lerp(older.verticalDevelopment(), newer.verticalDevelopment(), smoothT),
                lerp(older.stormPotential(), newer.stormPotential(), smoothT),
                lerp(older.anvilStrength(), newer.anvilStrength(), smoothT),
                lerp(older.precipitationIntensity(), newer.precipitationIntensity(), smoothT),
                lerp(older.hydrationProgress(), newer.hydrationProgress(), smoothT),
                Math.max(older.targetCloudletCount(), newer.targetCloudletCount()),
                Math.round(lerp(older.activeCloudletCount(), newer.activeCloudletCount(), smoothT)),
                lerp(older.fieldAgeTicks(), newer.fieldAgeTicks(), smoothT),
                Math.max(older.lifetimeTicks(), newer.lifetimeTicks()),
                targetTime,
                cameraPosition
        );
    }

    private static CloudFieldSnapshot extrapolateSnapshot(
            CloudFieldSnapshot older,
            CloudFieldSnapshot snapshot,
            double targetTime,
            Vec3 cameraPosition,
            float fade
    ) {
        double deltaTicks = Math.max(0.0D, Math.min(EXTRAPOLATION_LIMIT_TICKS, targetTime - snapshot.worldTime()));
        // Extrapolate the motion actually observed in authoritative centres.
        // Atmospheric wind remains available to animate cloud material, but
        // it must not move a frozen centre between identical server packets.
        Vec3 observedVelocity = observedSnapshotVelocity(older, snapshot);
        Vec3 center = snapshot.center().add(observedVelocity.scale(deltaTicks));
        return copySnapshot(
                snapshot,
                center,
                snapshot.center(),
                snapshot.radius(),
                snapshot.baseY(),
                snapshot.topY(),
                snapshot.density() * fade,
                snapshot.coverage() * fade,
                snapshot.growth(),
                snapshot.decay(),
                snapshot.humidityInfluence(),
                snapshot.windVector(),
                snapshot.verticalDevelopment(),
                snapshot.stormPotential(),
                snapshot.anvilStrength(),
                snapshot.precipitationIntensity(),
                snapshot.hydrationProgress(),
                snapshot.targetCloudletCount(),
                snapshot.activeCloudletCount(),
                snapshot.fieldAgeTicks() + Math.round(deltaTicks),
                snapshot.lifetimeTicks(),
                targetTime,
                cameraPosition
        );
    }

    private static Vec3 interpolateCenter(CloudFieldSnapshot older, CloudFieldSnapshot newer, float t) {
        double discontinuityThreshold = Math.max(
                DISCONTINUITY_MIN_BLOCKS,
                Math.max(older.radius(), newer.radius()) * 2.0D
        );
        Vec3 delta = newer.center().subtract(older.center());
        if (delta.length() > discontinuityThreshold) {
            return older.center().add(delta.scale(t));
        }
        return older.center().add(delta.scale(t));
    }

    private static Vec3 observedSnapshotVelocity(
            CloudFieldSnapshot older,
            CloudFieldSnapshot newer
    ) {
        if (older == null || newer == null) {
            return Vec3.ZERO;
        }
        double span = newer.worldTime() - older.worldTime();
        if (!Double.isFinite(span) || span <= 0.0D) {
            return Vec3.ZERO;
        }
        Vec3 delta = newer.center().subtract(older.center());
        double discontinuityThreshold = Math.max(
                DISCONTINUITY_MIN_BLOCKS,
                Math.max(older.radius(), newer.radius()) * 2.0D
        );
        if (delta.length() > discontinuityThreshold) {
            return Vec3.ZERO;
        }
        return delta.scale(1.0D / span);
    }

    private static CloudFieldSnapshot copySnapshot(
            CloudFieldSnapshot base,
            Vec3 center,
            Vec3 previousCenter,
            float radius,
            float baseY,
            float topY,
            float density,
            float coverage,
            float growth,
            float decay,
            float humidityInfluence,
            Vec3 wind,
            float verticalDevelopment,
            float stormPotential,
            float anvilStrength,
            float precipitationIntensity,
            float hydrationProgress,
            int targetCloudletCount,
            int activeCloudletCount,
            long fieldAgeTicks,
            long lifetimeTicks,
            double renderTime,
            Vec3 cameraPosition
    ) {
        long wholeTick = (long) Math.floor(renderTime);
        float partialTick = (float) (renderTime - wholeTick);
        return new CloudFieldSnapshot(
                base.fieldId(),
                base.seed(),
                base.dimensionId(),
                center,
                previousCenter,
                radius,
                baseY,
                topY,
                density,
                coverage,
                growth,
                decay,
                humidityInfluence,
                wind,
                verticalDevelopment,
                stormPotential,
                base.cloudTypeId(),
                base.morphologyFamily(),
                base.morphologyMembership(),
                anvilStrength,
                precipitationIntensity,
                base.sourceKind(),
                base.lodBand(),
                base.previousLodBand(),
                base.hydrationState(),
                hydrationProgress,
                targetCloudletCount,
                activeCloudletCount,
                fieldAgeTicks,
                lifetimeTicks,
                wholeTick,
                partialTick,
                cameraPosition
        );
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static long lerp(long a, long b, float t) {
        return Math.round(a + (b - a) * (double) t);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, float t) {
        Vec3 safeA = a == null ? Vec3.ZERO : a;
        Vec3 safeB = b == null ? Vec3.ZERO : b;
        return safeA.add(safeB.subtract(safeA).scale(t));
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static float fadeInOut(double elapsedTicks) {
        float t = (float) clamp01(elapsedTicks / FADE_TICKS);
        return t * t * (3.0F - 2.0F * t);
    }

    private static final class PresentationTrack {
        private CloudFieldSnapshot older;
        private CloudFieldSnapshot newer;
        private boolean presentInLatestPacket;
        private double firstPresentedRenderTime = Double.NaN;
        private double missingSinceRenderTime = Double.NaN;

        private synchronized void push(CloudFieldSnapshot snapshot) {
            if (snapshot == null) {
                return;
            }
            if (newer == null || snapshot.worldTime() >= newer.worldTime()) {
                older = newer;
                newer = snapshot;
            } else if (older == null || snapshot.worldTime() > older.worldTime()) {
                older = snapshot;
            }
            presentInLatestPacket = true;
            missingSinceRenderTime = Double.NaN;
        }

        private synchronized void markMissing() {
            presentInLatestPacket = false;
        }

        private synchronized CloudFieldSnapshot present(double renderTime, Vec3 cameraPosition) {
            if (newer == null) {
                return null;
            }
            if (Double.isNaN(firstPresentedRenderTime)) {
                firstPresentedRenderTime = renderTime;
            }
            if (!presentInLatestPacket && Double.isNaN(missingSinceRenderTime)) {
                missingSinceRenderTime = renderTime;
            }

            float fade = fadeInOut(renderTime - firstPresentedRenderTime);
            if (!presentInLatestPacket) {
                fade *= 1.0F - fadeInOut(renderTime - missingSinceRenderTime);
            }
            if (fade <= 0.001F) {
                if (!presentInLatestPacket) {
                    return null;
                }
                fade = 0.01F;
            }

            double targetTime = renderTime - PRESENTATION_DELAY_TICKS;
            if (older != null && newer.worldTime() > older.worldTime()
                    && targetTime >= older.worldTime()
                    && targetTime <= newer.worldTime()) {
                return interpolateSnapshot(older, newer, targetTime, cameraPosition, fade);
            }
            if (targetTime > newer.worldTime()) {
                return extrapolateSnapshot(older, newer, targetTime, cameraPosition, fade);
            }
            CloudFieldSnapshot base = older != null && targetTime < newer.worldTime() ? older : newer;
            return copySnapshot(
                    base,
                    base.center(),
                    base.previousCenter(),
                    base.radius(),
                    base.baseY(),
                    base.topY(),
                    base.density() * fade,
                    base.coverage() * fade,
                    base.growth(),
                    base.decay(),
                    base.humidityInfluence(),
                    base.windVector(),
                    base.verticalDevelopment(),
                    base.stormPotential(),
                    base.anvilStrength(),
                    base.precipitationIntensity(),
                    base.hydrationProgress(),
                    base.targetCloudletCount(),
                    base.activeCloudletCount(),
                    base.fieldAgeTicks(),
                    base.lifetimeTicks(),
                    targetTime,
                    cameraPosition
            );
        }

        private synchronized boolean shouldRemove(double renderTime) {
            return !presentInLatestPacket
                    && !Double.isNaN(missingSinceRenderTime)
                    && renderTime - missingSinceRenderTime > FADE_TICKS;
        }
    }
}
