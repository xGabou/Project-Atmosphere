package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.client.ClientPacketHandlers;
import net.Gabou.projectatmosphere.platform.network.PacketContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-to-client forecast packet carrying per-biome day temperature arrays.
 * It updates the client forecast cache and must not own forecast generation or mutation logic.
 */
public class BiomeDayTemperaturePacket {
    private final Map<ResourceLocation, float[]> temperatureDayMap;

    public BiomeDayTemperaturePacket(Map<ResourceLocation, float[]> temperatureDayMap) {
        this.temperatureDayMap = temperatureDayMap;
    }

    public BiomeDayTemperaturePacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.temperatureDayMap = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation biome = buf.readResourceLocation();
            int len = buf.readVarInt();
            float[] temps = new float[len];
            for (int j = 0; j < len; j++) temps[j] = buf.readFloat();
            temperatureDayMap.put(biome, temps);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(temperatureDayMap.size());
        temperatureDayMap.forEach((biome, temps) -> {
            buf.writeResourceLocation(biome);
            buf.writeVarInt(temps.length);
            for (float f : temps) buf.writeFloat(f);
        });
    }

    // ---------------------------------------------------------------------
    // Decode and handle
    // ---------------------------------------------------------------------
    public static BiomeDayTemperaturePacket decode(FriendlyByteBuf buf) {
        return new BiomeDayTemperaturePacket(buf);
    }

    /**
     * Handles incoming daily temperature data on the client.
     * Clears old forecasts before inserting new ones to prevent stale data.
     */
    public static void handle(BiomeDayTemperaturePacket msg, PacketContext context) {
        context.enqueueClient(() -> ClientPacketHandlers.handleBiomeDayTemperatures(msg.temperatureDayMap));
        context.markHandled();
    }
}
