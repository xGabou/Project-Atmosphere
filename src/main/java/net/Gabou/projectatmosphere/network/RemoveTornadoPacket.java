package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.platform.network.PacketContext;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Server-to-client packet removing a tornado by UUID from the client cache.
 * It only updates client tornado state and must not own tornado lifecycle logic.
 */
public class RemoveTornadoPacket {
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

    public void handle(PacketContext context) {
        context.enqueueClient(() -> SevereWeatherClientPacketHandlers.removeTornado(this.id));
        context.markHandled();
    }
}
