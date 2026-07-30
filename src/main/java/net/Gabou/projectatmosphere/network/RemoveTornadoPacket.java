package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Server-to-client packet removing a tornado by UUID from the client cache.
 * It only updates client tornado state and must not own tornado lifecycle logic.
 */
public class RemoveTornadoPacket implements CustomPacketPayload {
    public static final Type<RemoveTornadoPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "remove_tornado")
    );
    public static final StreamCodec<FriendlyByteBuf, RemoveTornadoPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), RemoveTornadoPacket::decode);
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

    // ---------------------------------------------------------------------
    // Decode and handle
    // ---------------------------------------------------------------------
    public static RemoveTornadoPacket decode(FriendlyByteBuf buf) {
        return new RemoveTornadoPacket(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoveTornadoPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> SevereWeatherClientPacketHandlers.removeTornado(packet.id));
    }
}
