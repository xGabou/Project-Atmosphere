package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RainfallUpdatePacket {
    private final ResourceLocation dimensionId;
    private final float rainLevel;

    public RainfallUpdatePacket(ResourceLocation dimensionId, float rainLevel) {
        this.dimensionId = dimensionId;
        this.rainLevel = rainLevel;
    }

    public RainfallUpdatePacket(FriendlyByteBuf buf) {
        this.dimensionId = buf.readResourceLocation();
        this.rainLevel = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimensionId);
        buf.writeFloat(rainLevel);
    }

    public static RainfallUpdatePacket decode(FriendlyByteBuf buf) {
        return new RainfallUpdatePacket(buf);
    }

    public static void handle(RainfallUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPacketHandlers.handleRainfallUpdate(msg.dimensionId, msg.rainLevel)));
        context.setPacketHandled(true);
    }
}
