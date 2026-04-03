package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncFogStatusPacket {
    private final float humidityPercent;
    private final float rainIntensity;

    public SyncFogStatusPacket(float humidityPercent, float rainIntensity) {
        this.humidityPercent = humidityPercent;
        this.rainIntensity = rainIntensity;
    }

    public SyncFogStatusPacket(FriendlyByteBuf buf) {
        this.humidityPercent = buf.readFloat();
        this.rainIntensity = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(this.humidityPercent);
        buf.writeFloat(this.rainIntensity);
    }

    public static SyncFogStatusPacket decode(FriendlyByteBuf buf) {
        return new SyncFogStatusPacket(buf);
    }

    public static void handle(SyncFogStatusPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPacketHandlers.handleFogStatusUpdate(msg.humidityPercent, msg.rainIntensity)));
        context.setPacketHandled(true);
    }
}
