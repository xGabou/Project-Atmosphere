package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.HUDOverlayRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-to-client packet carrying a single instrument readout string.
 * It updates the HUD overlay only and must not own any simulation state.
 */
public class InstrumentReadoutPacket implements CustomPacketPayload {
    public static final Type<InstrumentReadoutPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "instrument_readout")
    );
    public static final StreamCodec<FriendlyByteBuf, InstrumentReadoutPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), InstrumentReadoutPacket::decode);
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InstrumentReadoutPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> HUDOverlayRenderer.showTemperatureOverlay(msg.message));
    }
}
