package net.Gabou.projectatmosphere.client;

import net.Gabou.projectatmosphere.client.atmosphere.AtmosphereClientState;
import net.Gabou.projectatmosphere.client.fog.AtmosphereFogState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

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
}
