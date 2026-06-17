package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-to-client packet for coarse atmosphere state.
 */
public class SyncAtmosphereStatusPacket implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "sync_atmosphere_status");
    public static final Type<SyncAtmosphereStatusPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SyncAtmosphereStatusPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), SyncAtmosphereStatusPacket::decode);

    private final float humidityPercent;
    private final float rainIntensity;
    private final float cloudCover;

    public SyncAtmosphereStatusPacket(float humidityPercent, float rainIntensity, float cloudCover) {
        this.humidityPercent = humidityPercent;
        this.rainIntensity = rainIntensity;
        this.cloudCover = cloudCover;
    }

    public SyncAtmosphereStatusPacket(FriendlyByteBuf buf) {
        this.humidityPercent = buf.readFloat();
        this.rainIntensity = buf.readFloat();
        this.cloudCover = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(this.humidityPercent);
        buf.writeFloat(this.rainIntensity);
        buf.writeFloat(this.cloudCover);
    }

    public static SyncAtmosphereStatusPacket decode(FriendlyByteBuf buf) {
        return new SyncAtmosphereStatusPacket(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncAtmosphereStatusPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                ClientPacketHandlers.handleAtmosphereStatusUpdate(
                        msg.humidityPercent,
                        msg.rainIntensity,
                        msg.cloudCover
                );
            }
        });
    }
}
