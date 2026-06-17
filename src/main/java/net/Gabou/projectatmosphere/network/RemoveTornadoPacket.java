package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Server-to-client packet removing a tornado by UUID from the client cache.
 */
public class RemoveTornadoPacket implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "remove_tornado");
    public static final Type<RemoveTornadoPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, RemoveTornadoPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), RemoveTornadoPacket::decode);

    private final UUID id;

    public RemoveTornadoPacket(UUID id) {
        this.id = id;
    }

    public RemoveTornadoPacket(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
    }

    public static RemoveTornadoPacket decode(FriendlyByteBuf buf) {
        return new RemoveTornadoPacket(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoveTornadoPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> TornadoManager.removeClientTornado(pkt.id));
    }
}
