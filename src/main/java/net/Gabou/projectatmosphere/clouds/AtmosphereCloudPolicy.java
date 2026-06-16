package net.Gabou.projectatmosphere.clouds;

import net.Gabou.projectatmosphere.clouds.backend.CloudBackendResolver;
import net.Gabou.projectatmosphere.clouds.backend.CloudVisualBackend;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
                && AtmoCommonConfig.CLOUD_MODE.get() == AtmoCommonConfig.CloudMode.FULL
                && canUsePaInDimension(level);
    }

    public static boolean shouldOwnWeather(@Nullable Level level) {
        return AtmoCommonConfig.EVENTS_ENABLED.get()
                && AtmoCommonConfig.CLOUD_MODE.get() != AtmoCommonConfig.CloudMode.VANILLA
                && canUsePaInDimension(level);
    }

    public static boolean canUsePaInDimension(@Nullable Level level) {
        if (level == null) {
            return false;
        }

        String dimensionId = level.dimension().location().toString();
        boolean listed = containsDimension(dimensionId, AtmoCommonConfig.CLOUD_DIMENSION_IDS.get());
        return AtmoCommonConfig.CLOUD_DIMENSION_FILTER_MODE.get() == AtmoCommonConfig.DimensionFilterMode.BLACKLIST
                ? !listed
                : listed;
    }

    private static boolean containsDimension(String dimensionId, @Nullable List<? extends String> configuredIds) {
        if (configuredIds == null || configuredIds.isEmpty()) {
            return false;
        }

        for (String configuredId : configuredIds) {
            if (configuredId != null && dimensionId.equals(configuredId.trim())) {
                return true;
            }
        }
        return false;
    }
}
