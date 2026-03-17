package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.client.ClientSyncLock;
import net.Gabou.projectatmosphere.client.loading.ClientForecastLoadingWorkQueue;
import net.Gabou.projectatmosphere.client.loading.ForecastLoadingStage;
import net.Gabou.projectatmosphere.client.loading.ForecastLoadingState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Packet for transmitting per-region daily temperature forecasts to the client.
 * Retains biome map shape for backward compatibility on the client cache.
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

    public static BiomeDayTemperaturePacket decode(FriendlyByteBuf buf) {
        return new BiomeDayTemperaturePacket(buf);
    }

    /**
     * Handles incoming daily temperature data on the client.
     * Clears old forecasts before inserting new ones to prevent stale data.
     */
    public static void handle(BiomeDayTemperaturePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            int profileCount = msg.temperatureDayMap.size();
            ClientSyncLock.setReadyForLocalPlayer(false);
            ForecastLoadingState.update(
                    ForecastLoadingStage.RECEIVING_FORECAST_DATA,
                    null,
                    profileCount > 0 ? profileCount + " biome profiles received" : "Forecast snapshot received",
                    0.5F,
                    "biome_day_temperature_received"
            );
            ClientForecastLoadingWorkQueue.queueForecastSnapshot(msg.temperatureDayMap, "biome_day_temperature_packet");
        });
        ctx.get().setPacketHandled(true);
    }
}
