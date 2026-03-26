package net.Gabou.projectatmosphere.render;

import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneManager;

public final class HurricaneStateProvider {
    private HurricaneStateProvider() {}

    /**
     * Return active hurricane, or {@code null}. Coordinates are converted from world
     * space to Simple Clouds' cloud space by subtracting the camera position and
     * dividing by the cloud scale.
     */
    public static HurricaneStateCloudSpace getActive(double camX, double camZ) {
        var hurricane = HurricaneManager.getPrimaryClientHurricane();
        if (hurricane == null) {
            return null;
        }

        double scale = SimpleCloudsCompat.getCloudScale();
        return new HurricaneStateCloudSpace(
            (hurricane.position.x - camX) / scale,
            (hurricane.position.z - camZ) / scale,
            hurricane.radius / scale,
            hurricane.getEyewallRadius() / scale
        );
    }

    public record HurricaneStateCloudSpace(
        double centerXCloudSpace,
        double centerZCloudSpace,
        double eyeRadiusCloudSpace,
        double eyewallFadeCloudSpace
    ) {}
}

