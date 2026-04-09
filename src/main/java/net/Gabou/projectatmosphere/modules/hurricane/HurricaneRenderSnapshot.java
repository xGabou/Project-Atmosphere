package net.Gabou.projectatmosphere.modules.hurricane;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record HurricaneRenderSnapshot(
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
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
        buf.writeDouble(this.centerX);
        buf.writeDouble(this.centerZ);
        buf.writeFloat(this.anchorY);
        buf.writeFloat(this.coreRadius);
        buf.writeFloat(this.stormExtentRadius);
        buf.writeFloat(this.eyeRadius);
        buf.writeFloat(this.edgeFade);
        buf.writeVarInt(this.bandCount);
        buf.writeFloat(this.bandWidth);
        buf.writeFloat(this.spiralTightness);
        buf.writeFloat(this.rotationPhase);
        buf.writeFloat(this.rotationSpeed);
        buf.writeFloat(this.transitionStart);
        buf.writeFloat(this.transitionEnd);
        buf.writeResourceLocation(this.cloudTypeId);
        buf.writeVarInt(this.ageTicks);
    }

    public static HurricaneRenderSnapshot decode(FriendlyByteBuf buf) {
        return new HurricaneRenderSnapshot(
                buf.readUUID(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readResourceLocation(),
                buf.readVarInt()
        );
    }
}
