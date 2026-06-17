package net.Gabou.projectatmosphere.client.hurricane.cache;

import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneSemantics;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneManager;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneRenderSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientHurricaneStateCache {
    private static final int DEFAULT_BLEND_TICKS = 10;
    private static final int STALE_GRACE_TICKS = 60;
    private static final Map<UUID, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Map<UUID, CloudRegion> RESERVATION_REGIONS = new LinkedHashMap<>();
    private static long cachedSemanticTick = Long.MIN_VALUE;
    private static float cachedSemanticPartialTick = Float.NaN;
    private static List<HurricaneRenderSnapshot> cachedSemanticSnapshots = List.of();

    private ClientHurricaneStateCache() {
    }

    public static void applySnapshots(List<HurricaneRenderSnapshot> snapshots) {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long clientTick = mc.level != null ? mc.level.getGameTime() : 0L;

        Map<UUID, Entry> next = new LinkedHashMap<>();
        Map<UUID, CloudRegion> nextReservations = new LinkedHashMap<>();
        for (HurricaneRenderSnapshot snapshot : snapshots) {
            Entry previous = ENTRIES.get(snapshot.id());
            if (previous == null) {
                next.put(snapshot.id(), new Entry(snapshot, snapshot, clientTick));
            } else {
                next.put(snapshot.id(), new Entry(previous.current, snapshot, clientTick));
            }
            nextReservations.put(snapshot.id(), getReservationRegion(snapshot));
        }
        for (Map.Entry<UUID, Entry> existing : ENTRIES.entrySet()) {
            if (next.containsKey(existing.getKey())) {
                continue;
            }
            Entry entry = existing.getValue();
            if (clientTick - entry.clientUpdateTick <= STALE_GRACE_TICKS) {
                next.put(existing.getKey(), entry);
                nextReservations.put(existing.getKey(), getReservationRegion(entry.current));
            }
        }

        ENTRIES.clear();
        ENTRIES.putAll(next);
        RESERVATION_REGIONS.clear();
        RESERVATION_REGIONS.putAll(nextReservations);
        invalidateSemanticSnapshotCache();
    }

    public static void tick(ClientLevel level) {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            clear();
            return;
        }
        if (level == null) {
            clear();
            return;
        }
        boolean removed = ENTRIES.entrySet().removeIf(entry -> level.getGameTime() - entry.getValue().clientUpdateTick > STALE_GRACE_TICKS);
        if (removed) {
            RESERVATION_REGIONS.keySet().retainAll(ENTRIES.keySet());
            invalidateSemanticSnapshotCache();
        }
    }

    public static void clear() {
        ENTRIES.clear();
        RESERVATION_REGIONS.clear();
        invalidateSemanticSnapshotCache();
    }

    public static List<HurricaneRenderSnapshot> getSemanticSnapshots() {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return List.of();
        }
        return getSemanticSnapshots(0.0F);
    }

    public static List<HurricaneRenderSnapshot> getSemanticSnapshots(float partialTick) {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return List.of();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return List.of();
        }
        long clientTick = mc.level.getGameTime();
        if (clientTick == cachedSemanticTick && Float.compare(partialTick, cachedSemanticPartialTick) == 0) {
            return cachedSemanticSnapshots;
        }

        List<HurricaneRenderSnapshot> snapshots = ENTRIES.isEmpty()
                ? projectatmosphere$getIntegratedServerSnapshots(mc)
                : buildInterpolatedSnapshots(clientTick, partialTick);
        cachedSemanticTick = clientTick;
        cachedSemanticPartialTick = partialTick;
        cachedSemanticSnapshots = snapshots;
        return snapshots;
    }

    private static List<HurricaneRenderSnapshot> buildInterpolatedSnapshots(long clientTick, float partialTick) {
        List<HurricaneRenderSnapshot> snapshots = new ArrayList<>(ENTRIES.size());
        for (Entry entry : ENTRIES.values()) {
            float elapsed = (float)(clientTick - entry.clientUpdateTick) + partialTick;
            float blend = Mth.clamp(elapsed / (float)DEFAULT_BLEND_TICKS, 0.0F, 1.0F);
            HurricaneRenderSnapshot start = entry.previous;
            HurricaneRenderSnapshot end = entry.current;
            float rotationSpeed = Mth.lerp(blend, start.rotationSpeed(), end.rotationSpeed());
            float extrapolatedTicks = Math.max(0.0F, elapsed - DEFAULT_BLEND_TICKS);
            float rotationPhase = Mth.lerp(blend, start.rotationPhase(), end.rotationPhase()) + rotationSpeed * extrapolatedTicks;
            int ageTicks = Mth.floor(Mth.lerp(blend, start.ageTicks(), end.ageTicks()) + extrapolatedTicks);

            snapshots.add(new HurricaneRenderSnapshot(
                    end.id(),
                    Mth.lerp(blend, start.centerX(), end.centerX()),
                    Mth.lerp(blend, start.centerZ(), end.centerZ()),
                    Mth.lerp(blend, start.anchorY(), end.anchorY()),
                    Mth.lerp(blend, start.coreRadius(), end.coreRadius()),
                    Mth.lerp(blend, start.stormExtentRadius(), end.stormExtentRadius()),
                    Mth.lerp(blend, start.eyeRadius(), end.eyeRadius()),
                    Mth.lerp(blend, start.edgeFade(), end.edgeFade()),
                    end.bandCount(),
                    Mth.lerp(blend, start.bandWidth(), end.bandWidth()),
                    Mth.lerp(blend, start.spiralTightness(), end.spiralTightness()),
                    rotationPhase,
                    rotationSpeed,
                    Mth.lerp(blend, start.transitionStart(), end.transitionStart()),
                    Mth.lerp(blend, start.transitionEnd(), end.transitionEnd()),
                    Mth.lerp(blend, start.normalizedIntensity(), end.normalizedIntensity()),
                    end.cloudTypeId(),
                    ageTicks
            ));
        }
        return snapshots;
    }

    public static boolean hasHurricanes() {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return false;
        }
        if (!ENTRIES.isEmpty()) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.hasSingleplayerServer()) {
            return false;
        }
        return !HurricaneManager.getActiveHurricanes().isEmpty();
    }

    public static CloudRegion getReservationRegion(HurricaneRenderSnapshot snapshot) {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return null;
        }
        CloudRegion region = RESERVATION_REGIONS.get(snapshot.id());
        if (region == null) {
            region = HurricaneSemantics.createReservationRegion(snapshot);
            RESERVATION_REGIONS.put(snapshot.id(), region);
        } else {
            HurricaneSemantics.updateReservationRegion(region, snapshot);
        }
        return region;
    }

    private static List<HurricaneRenderSnapshot> projectatmosphere$getIntegratedServerSnapshots(Minecraft mc) {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return List.of();
        }
        if (!mc.hasSingleplayerServer()) {
            return List.of();
        }
        var server = mc.getSingleplayerServer();
        if (server == null) {
            return List.of();
        }
        List<net.Gabou.projectatmosphere.modules.hurricane.HurricaneInstance> active = HurricaneManager.getActiveHurricanes();
        if (active.isEmpty()) {
            return List.of();
        }
        List<HurricaneRenderSnapshot> snapshots = new ArrayList<>(active.size());
        for (net.Gabou.projectatmosphere.modules.hurricane.HurricaneInstance hurricane : active) {
            snapshots.add(hurricane.createRenderSnapshot());
        }
        return snapshots;
    }

    private static void invalidateSemanticSnapshotCache() {
        cachedSemanticTick = Long.MIN_VALUE;
        cachedSemanticPartialTick = Float.NaN;
        cachedSemanticSnapshots = List.of();
    }

    public static List<RenderableHurricane> getRenderableHurricanes(float partialTick) {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return List.of();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return List.of();
        }

        List<HurricaneRenderSnapshot> snapshots = getSemanticSnapshots(partialTick);
        List<RenderableHurricane> renderables = new ArrayList<>(snapshots.size());
        for (HurricaneRenderSnapshot snapshot : snapshots) {
            renderables.add(new RenderableHurricane(
                    snapshot.id(),
                    snapshot.centerX() / (double)SimpleCloudsConstants.CLOUD_SCALE,
                    snapshot.centerZ() / (double)SimpleCloudsConstants.CLOUD_SCALE,
                    snapshot.anchorY(),
                    snapshot.coreRadius() / (float)SimpleCloudsConstants.CLOUD_SCALE,
                    snapshot.stormExtentRadius() / (float)SimpleCloudsConstants.CLOUD_SCALE,
                    snapshot.eyeRadius() / (float)SimpleCloudsConstants.CLOUD_SCALE,
                    snapshot.edgeFade() / (float)SimpleCloudsConstants.CLOUD_SCALE,
                    snapshot.bandCount(),
                    snapshot.bandWidth() / (float)SimpleCloudsConstants.CLOUD_SCALE,
                    snapshot.spiralTightness(),
                    snapshot.rotationPhase(),
                    snapshot.rotationSpeed(),
                    snapshot.transitionStart() / (float)SimpleCloudsConstants.CLOUD_SCALE,
                    snapshot.transitionEnd() / (float)SimpleCloudsConstants.CLOUD_SCALE,
                    snapshot.cloudTypeId(),
                    snapshot.ageTicks(),
                    Mth.clamp(snapshot.normalizedIntensity(), 0.0F, 1.0F),
                    ((snapshot.id().hashCode() & 0x7fffffff) % 10000) / 10000.0F
            ));
        }
        return renderables;
    }

    private record Entry(HurricaneRenderSnapshot previous, HurricaneRenderSnapshot current, long clientUpdateTick) {
    }

    public record RenderableHurricane(
            UUID id,
            double centerX,
            double centerZ,
            float anchorY,
            float coreRadius,
            float stormExtentRadius,
            float eyeRadius,
            float edgeFade,
            int bandCount,
            float bandWidth,
            float spiralTightness,
            float rotationPhase,
            float rotationSpeed,
            float transitionStart,
            float transitionEnd,
            ResourceLocation cloudTypeId,
            int ageTicks,
            float intensity,
            float seed
    ) {
    }
}
