package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side fog debug override packet carrying a temporary strength and duration.
 */
public class FogDebugOverridePacket implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "fog_debug_override");
    public static final Type<FogDebugOverridePacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, FogDebugOverridePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), FogDebugOverridePacket::decode);

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

    public static FogDebugOverridePacket decode(FriendlyByteBuf buf) {
        return new FogDebugOverridePacket(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FogDebugOverridePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                ClientPacketHandlers.handleFogDebugOverride(msg.strength, msg.durationTicks);
            }
        });
    }
}
