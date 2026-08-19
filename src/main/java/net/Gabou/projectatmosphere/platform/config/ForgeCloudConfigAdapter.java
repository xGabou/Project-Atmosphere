package net.Gabou.projectatmosphere.platform.config;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

import java.util.List;

/** ForgeConfigSpec-backed implementation of the cloud configuration port. */
public final class ForgeCloudConfigAdapter implements CloudConfigPort {
    @Override
    public boolean fullCloudMode() {
        return AtmoCommonConfig.CLOUD_MODE.get() == AtmoCommonConfig.CloudMode.FULL;
    }

    @Override
    public boolean vanillaCloudMode() {
        return AtmoCommonConfig.CLOUD_MODE.get() == AtmoCommonConfig.CloudMode.VANILLA;
    }

    @Override
    public boolean eventsEnabled() {
        return AtmoCommonConfig.EVENTS_ENABLED.get();
    }

    @Override
    public boolean cloudEnabledInDimension(String dimensionId) {
        List<? extends String> configuredIds = AtmoCommonConfig.CLOUD_DIMENSION_IDS.get();
        boolean listed = configuredIds != null && configuredIds.stream()
                .filter(value -> value != null)
                .map(String::trim)
                .anyMatch(dimensionId::equals);
        return AtmoCommonConfig.CLOUD_DIMENSION_FILTER_MODE.get()
                == AtmoCommonConfig.DimensionFilterMode.BLACKLIST
                ? !listed
                : listed;
    }

    @Override
    public boolean freezeCloudMovement() {
        return AtmoCommonConfig.FREEZE_CLOUD_MOVEMENT.get();
    }

    @Override
    public boolean cloudMovementEnabled() {
        return AtmoCommonConfig.ENABLE_CLOUD_MOVEMENT.get();
    }

    @Override
    public double cloudWindDriftScale() {
        return AtmoCommonConfig.CLOUD_WIND_DRIFT_SCALE.get();
    }

    @Override
    public float nativeCloudSpawnHeight() {
        return AtmoCommonConfig.NATIVE_CLOUD_SPAWN_HEIGHT.get();
    }

    @Override
    public double cloudRenderDistance() {
        return AtmoCommonConfig.CLOUD_RENDER_DISTANCE.get();
    }
}
