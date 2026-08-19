package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.client.HUDOverlayRenderer;
import net.Gabou.projectatmosphere.platform.network.PacketContext;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server-to-client packet carrying a single instrument readout string.
 * It updates the HUD overlay only and must not own any simulation state.
 */
public class InstrumentReadoutPacket {
    private final String message;

    public InstrumentReadoutPacket(String message) {
        this.message = message;
    }

    public InstrumentReadoutPacket(FriendlyByteBuf buf) {
        this.message = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(message, 256);
    }

    // ---------------------------------------------------------------------
    // Decode and handle
    // ---------------------------------------------------------------------
    public static InstrumentReadoutPacket decode(FriendlyByteBuf buf) {
        return new InstrumentReadoutPacket(buf);
    }

    public static void handle(InstrumentReadoutPacket msg, PacketContext context) {
        context.enqueueClient(() -> HUDOverlayRenderer.showTemperatureOverlay(msg.message));
        context.markHandled();
    }
}
