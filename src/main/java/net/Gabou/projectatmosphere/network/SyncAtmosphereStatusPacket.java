package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.client.ClientPacketHandlers;
import net.Gabou.projectatmosphere.platform.network.PacketContext;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server-to-client packet for coarse atmosphere state.
 * It updates the client atmosphere cache and must not own any weather logic.
 */
public class SyncAtmosphereStatusPacket {
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

    // ---------------------------------------------------------------------
    // Decode and handle
    // ---------------------------------------------------------------------
    public static SyncAtmosphereStatusPacket decode(FriendlyByteBuf buf) {
        return new SyncAtmosphereStatusPacket(buf);
    }

    public static void handle(SyncAtmosphereStatusPacket msg, PacketContext context) {
        context.enqueueClient(() ->
                ClientPacketHandlers.handleAtmosphereStatusUpdate(
                        msg.humidityPercent,
                        msg.rainIntensity,
                        msg.cloudCover
                ));
        context.markHandled();
    }
}
