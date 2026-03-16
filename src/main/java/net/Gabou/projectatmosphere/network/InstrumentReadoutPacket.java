package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.OverlayMessageState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side overlay message for instrument readouts.
 */
public record InstrumentReadoutPacket(String message) implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "instrument_readout");

    public static final Type<InstrumentReadoutPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, InstrumentReadoutPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeUtf(pkt.message(), 256),
                    buf -> new InstrumentReadoutPacket(buf.readUtf(256))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InstrumentReadoutPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> OverlayMessageState.show(pkt.message(), 3000L));
    }
}

