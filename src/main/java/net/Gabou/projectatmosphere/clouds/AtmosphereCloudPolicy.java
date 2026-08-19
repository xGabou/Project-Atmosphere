package net.Gabou.projectatmosphere.clouds;

import net.Gabou.projectatmosphere.clouds.backend.CloudBackendResolver;
import net.Gabou.projectatmosphere.clouds.backend.CloudVisualBackend;
import net.Gabou.projectatmosphere.platform.config.AtmosphereConfig;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;


/**
 * Shared cloud/weather ownership policy for native PA rendering and vanilla compatibility hooks.
 */
public final class AtmosphereCloudPolicy {
    private AtmosphereCloudPolicy() {
    }

    public static boolean shouldRenderPaClouds(@Nullable Level level) {
        return CloudBackendResolver.resolve(level) == CloudVisualBackend.PA_NATIVE;
    }

    public static boolean shouldSuppressVanillaClouds(@Nullable Level level) {
        return CloudBackendResolver.resolve(level) != CloudVisualBackend.DISABLED
                && AtmosphereConfig.clouds().fullCloudMode()
                && canUsePaInDimension(level);
    }

    public static boolean shouldOwnWeather(@Nullable Level level) {
        return AtmosphereConfig.clouds().eventsEnabled()
                && !AtmosphereConfig.clouds().vanillaCloudMode()
                && canUsePaInDimension(level);
    }

    public static boolean canUsePaInDimension(@Nullable Level level) {
        if (level == null) {
            return false;
        }

        String dimensionId = level.dimension().location().toString();
        return AtmosphereConfig.clouds().cloudEnabledInDimension(dimensionId);
    }
}
