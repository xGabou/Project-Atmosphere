package net.Gabou.projectatmosphere.client;

import net.Gabou.projectatmosphere.client.fog.AtmosphereFogState;
import net.Gabou.projectatmosphere.compat.rainbows.RainbowWeatherTracker;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    public static void handleRainfallUpdate(ResourceLocation dimensionId, float rainLevel) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimensionId);
        RainbowWeatherTracker.applyServerUpdate(key, rainLevel);
    }

    public static void handleFogStatusUpdate(float humidityPercent, float rainIntensity) {
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
