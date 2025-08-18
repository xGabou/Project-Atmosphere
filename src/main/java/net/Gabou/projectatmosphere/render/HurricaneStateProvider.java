package net.Gabou.projectatmosphere.render;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.common.SimpleCloudsBridge;

public final class HurricaneStateProvider {
    private HurricaneStateProvider() {}

    /**
     * Return active hurricane, or {@code null}. Coordinates are converted from world
     * space to Simple Clouds' cloud space by subtracting the camera position and
     * dividing by the cloud scale.
     */
    public static HurricaneStateCloudSpace getActive(double camX, double camZ) {
        var s = ForecastOrchestrator.getActiveHurricane();
        if (s == null) {
            return null;
        }

        double scale = SimpleCloudsBridge.getCloudScale();
        return new HurricaneStateCloudSpace(
            (s.centerX() - camX) / scale,
            (s.centerZ() - camZ) / scale,
            s.eyeRadius() / scale,
            s.eyewallFade() / scale
        );
    }

    public record HurricaneStateCloudSpace(
        double centerXCloudSpace,
        double centerZCloudSpace,
        double eyeRadiusCloudSpace,
        double eyewallFadeCloudSpace
    ) {}
}

