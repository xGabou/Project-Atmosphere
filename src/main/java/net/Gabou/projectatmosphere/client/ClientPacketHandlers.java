package net.Gabou.projectatmosphere.client;

import net.Gabou.projectatmosphere.client.atmosphere.AtmosphereClientState;
import net.Gabou.projectatmosphere.client.fog.AtmosphereFogState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.Gabou.projectatmosphere.client.loading.ClientForecastLoadingWorkQueue;
import net.Gabou.projectatmosphere.client.loading.ForecastLoadingStage;
import net.Gabou.projectatmosphere.client.loading.ForecastLoadingState;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    public static void handleAtmosphereStatusUpdate(float humidityPercent, float rainIntensity, float cloudCover) {
        AtmosphereClientState.applyServerUpdate(humidityPercent, rainIntensity, cloudCover);
        AtmosphereFogState.applyServerUpdate(humidityPercent, rainIntensity);
    }

    public static void handleFogDebugOverride(float strength, int durationTicks) {
        if (durationTicks <= 0 || strength <= 0.0F) {
            AtmosphereFogState.clearDebugOverride();
            return;
        }
        AtmosphereFogState.applyDebugOverride(strength, durationTicks);
    }

    public static void handleBiomeDayTemperatures(Map<ResourceLocation, float[]> temperatures) {
        int profileCount = temperatures.size();
        ClientSyncLock.setReadyForLocalPlayer(false);
        ForecastLoadingState.update(
                ForecastLoadingStage.RECEIVING_FORECAST_DATA,
                null,
                profileCount > 0 ? profileCount + " biome profiles received" : "Forecast snapshot received",
                0.5F,
                "biome_day_temperature_received"
        );
        ClientForecastLoadingWorkQueue.queueForecastSnapshot(temperatures, "biome_day_temperature_packet");
    }
}
