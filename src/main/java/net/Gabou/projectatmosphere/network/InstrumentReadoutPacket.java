package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.client.HUDOverlayRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-side overlay message for instrument readouts.
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

    public static InstrumentReadoutPacket decode(FriendlyByteBuf buf) {
        return new InstrumentReadoutPacket(buf);
    }

    public static void handle(InstrumentReadoutPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                HUDOverlayRenderer.showTemperatureOverlay(msg.message)));
        context.setPacketHandled(true);
    }
}
