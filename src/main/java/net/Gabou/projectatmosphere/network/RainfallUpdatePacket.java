package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client rainfall intensity synchronization used by Rainbows compatibility.
 * <p>
 * This class must remain safe to load on a dedicated server: do not reference client-only
 * classes directly. Client integration is invoked via reflection in {@link #handle}.
 */
public record RainfallUpdatePacket(ResourceLocation dimensionId, float rainLevel) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "rainfall_update");

    public static final Type<RainfallUpdatePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, RainfallUpdatePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeResourceLocation(pkt.dimensionId);
                        buf.writeFloat(pkt.rainLevel);
                    },
                    buf -> new RainfallUpdatePacket(buf.readResourceLocation(), buf.readFloat())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RainfallUpdatePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, pkt.dimensionId());
                Class<?> tracker = Class.forName("net.Gabou.projectatmosphere.compat.rainbows.RainbowWeatherTracker");
                var method = tracker.getMethod("applyServerUpdate", ResourceKey.class, float.class);
                method.invoke(null, dimension, pkt.rainLevel());
            } catch (Throwable ignored) {
                // Optional compat; ignore if client classes aren't present.
            }
        });
    }
}

