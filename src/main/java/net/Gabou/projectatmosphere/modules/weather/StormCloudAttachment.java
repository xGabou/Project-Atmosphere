package net.Gabou.projectatmosphere.modules.weather;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Shared cloud/storm attachment data used by tornado simulation and rendering.
 * Native PA clouds can provide real region/cluster ids; Simple Clouds uses a
 * stable synthetic parent id until both backends share the same cloud model.
 */
public record StormCloudAttachment(
        UUID parentRegionId,
        UUID parentClusterId,
        String backendId,
        Vec3 attachmentPoint,
        float cloudBaseY,
        float cloudTopY,
        float funnelTopY,
        StormLifecyclePhase lifecyclePhase,
        float precipitationIntensity,
        float tornadoIntensity
) {
    private static final UUID EMPTY_ID = new UUID(0L, 0L);
    public static final StormCloudAttachment NONE = new StormCloudAttachment(
            EMPTY_ID,
            EMPTY_ID,
            "none",
            Vec3.ZERO,
            0.0F,
            0.0F,
            0.0F,
            StormLifecyclePhase.DISSIPATED,
            0.0F,
            0.0F
    );

    public StormCloudAttachment {
        parentRegionId = parentRegionId == null ? EMPTY_ID : parentRegionId;
        parentClusterId = parentClusterId == null ? EMPTY_ID : parentClusterId;
        backendId = backendId == null || backendId.isBlank() ? "unknown" : backendId;
        attachmentPoint = attachmentPoint == null ? Vec3.ZERO : attachmentPoint;
        cloudTopY = Math.max(cloudBaseY, cloudTopY);
        funnelTopY = Math.max(cloudBaseY, funnelTopY);
        lifecyclePhase = lifecyclePhase == null ? StormLifecyclePhase.FORMING : lifecyclePhase;
        precipitationIntensity = clamp01(precipitationIntensity);
        tornadoIntensity = clamp01(tornadoIntensity);
    }

    public static StormCloudAttachment attached(
            UUID parentRegionId,
            UUID parentClusterId,
            String backendId,
            Vec3 attachmentPoint,
            float cloudBaseY,
            float cloudTopY,
            float funnelTopY,
            StormLifecyclePhase lifecyclePhase,
            float precipitationIntensity,
            float tornadoIntensity
    ) {
        return new StormCloudAttachment(
                parentRegionId,
                parentClusterId,
                backendId,
                attachmentPoint,
                cloudBaseY,
                cloudTopY,
                funnelTopY,
                lifecyclePhase,
                precipitationIntensity,
                tornadoIntensity
        );
    }

    public boolean isAttached() {
        return !EMPTY_ID.equals(parentRegionId) || !"none".equals(backendId);
    }

    public StormCloudAttachment withLifecycle(StormLifecyclePhase phase, float intensity) {
        if (!isAttached()) {
            return NONE;
        }
        return new StormCloudAttachment(
                parentRegionId,
                parentClusterId,
                backendId,
                attachmentPoint,
                cloudBaseY,
                cloudTopY,
                funnelTopY,
                phase,
                precipitationIntensity,
                intensity
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(isAttached());
        if (!isAttached()) {
            return;
        }
        buf.writeUUID(parentRegionId);
        buf.writeUUID(parentClusterId);
        buf.writeUtf(backendId);
        buf.writeDouble(attachmentPoint.x);
        buf.writeDouble(attachmentPoint.y);
        buf.writeDouble(attachmentPoint.z);
        buf.writeFloat(cloudBaseY);
        buf.writeFloat(cloudTopY);
        buf.writeFloat(funnelTopY);
        buf.writeEnum(lifecyclePhase);
        buf.writeFloat(precipitationIntensity);
        buf.writeFloat(tornadoIntensity);
    }

    public static StormCloudAttachment read(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return NONE;
        }
        UUID parentRegionId = buf.readUUID();
        UUID parentClusterId = buf.readUUID();
        String backendId = buf.readUtf();
        Vec3 attachmentPoint = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float cloudBaseY = buf.readFloat();
        float cloudTopY = buf.readFloat();
        float funnelTopY = buf.readFloat();
        StormLifecyclePhase phase = buf.readEnum(StormLifecyclePhase.class);
        float precipitationIntensity = buf.readFloat();
        float tornadoIntensity = buf.readFloat();
        return attached(
                parentRegionId,
                parentClusterId,
                backendId,
                attachmentPoint,
                cloudBaseY,
                cloudTopY,
                funnelTopY,
                phase,
                precipitationIntensity,
                tornadoIntensity
        );
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }
}
