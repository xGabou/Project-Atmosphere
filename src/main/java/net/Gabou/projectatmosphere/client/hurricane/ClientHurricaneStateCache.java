package net.Gabou.projectatmosphere.client.hurricane;

import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
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

    public static List<RenderableHurricane> getRenderableHurricanes(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ENTRIES.isEmpty()) {
            return List.of();
        }

        long clientTick = mc.level.getGameTime();
        List<RenderableHurricane> renderables = new ArrayList<>(ENTRIES.size());
        for (Entry entry : ENTRIES.values()) {
            float blend = Mth.clamp(((float)(clientTick - entry.clientUpdateTick) + partialTick) / (float)DEFAULT_BLEND_TICKS, 0.0F, 1.0F);
            HurricaneRenderSnapshot start = entry.previous;
            HurricaneRenderSnapshot end = entry.current;

            double centerX = Mth.lerp(blend, start.centerX(), end.centerX());
            double centerZ = Mth.lerp(blend, start.centerZ(), end.centerZ());
            float outerRadius = Mth.lerp(blend, start.outerRadius(), end.outerRadius());
            float eyeRadius = Mth.lerp(blend, start.eyeRadius(), end.eyeRadius());
            float edgeFade = Mth.lerp(blend, start.edgeFade(), end.edgeFade());
            float bandWidth = Mth.lerp(blend, start.bandWidth(), end.bandWidth());
            float spiralTightness = Mth.lerp(blend, start.spiralTightness(), end.spiralTightness());
            float rotationSpeed = Mth.lerp(blend, start.rotationSpeed(), end.rotationSpeed());
            float basePhase = Mth.lerp(blend, start.rotationPhase(), end.rotationPhase());
            float phase = basePhase + rotationSpeed * partialTick;
            int ageTicks = Mth.floor(Mth.lerp(blend, start.ageTicks(), end.ageTicks()) + partialTick);

            renderables.add(new RenderableHurricane(
                    end.id(),
                    centerX / (double)SimpleCloudsConstants.CLOUD_SCALE,
                    centerZ / (double)SimpleCloudsConstants.CLOUD_SCALE,
                    outerRadius / (float)SimpleCloudsConstants.CLOUD_SCALE,
                    eyeRadius / (float)SimpleCloudsConstants.CLOUD_SCALE,
                    edgeFade / (float)SimpleCloudsConstants.CLOUD_SCALE,
                    end.bandCount(),
                    bandWidth / (float)SimpleCloudsConstants.CLOUD_SCALE,
                    spiralTightness,
                    phase,
                    rotationSpeed,
                    end.cloudTypeId(),
                    ageTicks
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
            float outerRadius,
            float eyeRadius,
            float edgeFade,
            int bandCount,
            float bandWidth,
            float spiralTightness,
            float rotationPhase,
            float rotationSpeed,
            ResourceLocation cloudTypeId,
            int ageTicks
    ) {
    }
}
