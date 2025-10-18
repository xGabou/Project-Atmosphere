package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.BiomeClientTemperatureCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Synchronizes daily biome temperature arrays from server to client.
 * <p>
 * Rewritten for NeoForge 1.21.1 using StreamCodec + CustomPacketPayload.
 */
public record BiomeDayTemperaturePacket(Map<ResourceLocation, float[]> temperatureDayMap)
        implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "biomedaytemperaturepacket");

    public static final Type<BiomeDayTemperaturePacket> TYPE = new Type<>(ID);

    /**
     * StreamCodec handles serialization & deserialization of the packet.
     */
    public static final StreamCodec<FriendlyByteBuf, BiomeDayTemperaturePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.temperatureDayMap.size());
                        pkt.temperatureDayMap.forEach((biome, temps) -> {
                            buf.writeResourceLocation(biome);
                            buf.writeVarInt(temps.length);
                            for (float f : temps) buf.writeFloat(f);
                        });
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        Map<ResourceLocation, float[]> map = new HashMap<>(size);
                        for (int i = 0; i < size; i++) {
                            ResourceLocation biome = buf.readResourceLocation();
                            int len = buf.readVarInt();
                            float[] temps = new float[len];
                            for (int j = 0; j < len; j++) temps[j] = buf.readFloat();
                            map.put(biome, temps);
                        }
                        return new BiomeDayTemperaturePacket(map);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handles packet client-side by updating cached biome temperature forecasts.
     */
    public static void handle(BiomeDayTemperaturePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                BiomeClientTemperatureCache.updateDayForecasts(pkt.temperatureDayMap())
        );
    }
}
