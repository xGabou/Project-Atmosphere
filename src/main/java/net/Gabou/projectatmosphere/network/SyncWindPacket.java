package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncWindPacket(float speed, float angle, float gust)
        implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "sync_wind");

    public static final Type<SyncWindPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SyncWindPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeFloat(pkt.speed);
                        buf.writeFloat(pkt.angle);
                        buf.writeFloat(pkt.gust);
                    },
                    buf -> new SyncWindPacket(buf.readFloat(), buf.readFloat(), buf.readFloat())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncWindPacket pkt, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // Replace this with your actual client-side wind update system
            // e.g., ClientWindStorage.update(pkt.speed(), pkt.angle(), pkt.gust());
        });
    }
}
