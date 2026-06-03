package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client sync packet for coarse atmosphere state.
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

    public static SyncAtmosphereStatusPacket decode(FriendlyByteBuf buf) {
        return new SyncAtmosphereStatusPacket(buf);
    }

    public static void handle(SyncAtmosphereStatusPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPacketHandlers.handleAtmosphereStatusUpdate(
                        msg.humidityPercent,
                        msg.rainIntensity,
                        msg.cloudCover
                )));
        context.setPacketHandled(true);
    }
}
