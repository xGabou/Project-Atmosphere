package net.Gabou.projectatmosphere.render;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.common.SimpleCloudsBridge;

public final class HurricaneStateProvider {
    private HurricaneStateProvider() {}

    /** Return active hurricane, or null. Must provide cloud-space coords/radii. */
    public static HurricaneStateCloudSpace getActive() {
        var s = ForecastOrchestrator.getActiveHurricane(); // your existing API
        if (s == null) return null;

        double scale = SimpleCloudsBridge.getCloudScale(); // return SimpleCloudsConstants.CLOUD_SCALE
        return new HurricaneStateCloudSpace(
            s.centerX() / scale,
            s.centerZ() / scale,
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

