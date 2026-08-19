package net.Gabou.projectatmosphere.platform.config;

/** Typed cloud configuration consumed by domain and orchestration code. */
public interface CloudConfigPort {
    boolean fullCloudMode();

    boolean vanillaCloudMode();

    boolean eventsEnabled();

    boolean cloudEnabledInDimension(String dimensionId);

    boolean freezeCloudMovement();

    boolean cloudMovementEnabled();

    double cloudWindDriftScale();

    float nativeCloudSpawnHeight();

    double cloudRenderDistance();
}
