package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-side fog debug override packet carrying a temporary strength and duration.
 * It updates client fog state only and must not own rendering policy.
 */
public class FogDebugOverridePacket {
    private final float strength;
    private final int durationTicks;

    public FogDebugOverridePacket(float strength, int durationTicks) {
        this.strength = strength;
        this.durationTicks = durationTicks;
    }

    public FogDebugOverridePacket(FriendlyByteBuf buf) {
        this.strength = buf.readFloat();
        this.durationTicks = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(this.strength);
        buf.writeVarInt(this.durationTicks);
    }

    // ---------------------------------------------------------------------
    // Decode and handle
    // ---------------------------------------------------------------------
    public static FogDebugOverridePacket decode(FriendlyByteBuf buf) {
        return new FogDebugOverridePacket(buf);
    }

    public static void handle(FogDebugOverridePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPacketHandlers.handleFogDebugOverride(msg.strength, msg.durationTicks)));
        context.setPacketHandled(true);
    }
}
