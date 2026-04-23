package net.Gabou.projectatmosphere.client.hurricane;

import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
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
    private static final Map<UUID, Entry> ENTRIES = new LinkedHashMap<>();

    private ClientHurricaneStateCache() {
    }

    public static void applySnapshots(List<HurricaneRenderSnapshot> snapshots) {
        Minecraft mc = Minecraft.getInstance();
        long clientTick = mc.level != null ? mc.level.getGameTime() : 0L;

        Map<UUID, Entry> next = new LinkedHashMap<>();
        for (HurricaneRenderSnapshot snapshot : snapshots) {
            Entry previous = ENTRIES.get(snapshot.id());
            if (previous == null) {
                next.put(snapshot.id(), new Entry(snapshot, snapshot, clientTick));
            } else {
                next.put(snapshot.id(), new Entry(previous.current, snapshot, clientTick));
            }
        }

        ENTRIES.clear();
        ENTRIES.putAll(next);
    }

    public static void tick(ClientLevel level) {
        if (level == null) {
            clear();
        }
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public static List<HurricaneRenderSnapshot> getSemanticSnapshots() {
        return getSemanticSnapshots(0.0F);
    }

    public static List<HurricaneRenderSnapshot> getSemanticSnapshots(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return List.of();
        }
        if (ENTRIES.isEmpty()) {
            return projectatmosphere$getIntegratedServerSnapshots(mc);
        }

        long clientTick = mc.level.getGameTime();
        List<HurricaneRenderSnapshot> snapshots = new ArrayList<>(ENTRIES.size());
        for (Entry entry : ENTRIES.values()) {
            float blend = Mth.clamp(((float)(clientTick - entry.clientUpdateTick) + partialTick) / (float)DEFAULT_BLEND_TICKS, 0.0F, 1.0F);
            HurricaneRenderSnapshot start = entry.previous;
            HurricaneRenderSnapshot end = entry.current;

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
                    Mth.lerp(blend, start.rotationPhase(), end.rotationPhase()) + Mth.lerp(blend, start.rotationSpeed(), end.rotationSpeed()) * partialTick,
                    Mth.lerp(blend, start.rotationSpeed(), end.rotationSpeed()),
                    Mth.lerp(blend, start.transitionStart(), end.transitionStart()),
                    Mth.lerp(blend, start.transitionEnd(), end.transitionEnd()),
                    end.cloudTypeId(),
                    Mth.floor(Mth.lerp(blend, start.ageTicks(), end.ageTicks()) + partialTick)
            ));
        }
        return snapshots;
    }

    private static List<HurricaneRenderSnapshot> projectatmosphere$getIntegratedServerSnapshots(Minecraft mc) {
        if (!mc.hasSingleplayerServer()) {
            return List.of();
        }
        var server = mc.getSingleplayerServer();
        if (server == null) {
            return List.of();
        }
        return HurricaneManager.getActiveHurricanes().stream()
                .map(hurricane -> hurricane.createRenderSnapshot())
                .toList();
    }

    public static List<RenderableHurricane> getRenderableHurricanes(float partialTick) {
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
                    snapshot.ageTicks()
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
            int ageTicks
    ) {
    }
}
