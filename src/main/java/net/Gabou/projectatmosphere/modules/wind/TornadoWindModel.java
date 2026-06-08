package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Computes player-facing tornado forces so visuals and physics stay in sync.
 */
public final class TornadoWindModel {
    public record TornadoForces(Vec3 pullVector, Vec3 rotationVector, Vec3 liftVector, TornadoInstance source) { }

    private TornadoWindModel() { }

    public static TornadoForces compute(Vec3 position) {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return null;
        }
        List<TornadoInstance> active = TornadoManager.getActiveTornadoes();
        TornadoInstance nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (TornadoInstance tornado : active) {
            double distSq = tornado.position.distanceToSqr(position.x, tornado.position.y, position.z);
            if (distSq < nearestDistSq) {
                nearest = tornado;
                nearestDistSq = distSq;
            }
        }
        if (nearest == null) {
            return null;
        }

        Vec3 center = nearest.position;
        double dx = center.x - position.x;
        double dz = center.z - position.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double radius = Math.max(4.0, nearest.getSuctionRadius());
        if (horizontalDist > radius * 3.0) {
            return null;
        }

        double proximity = Math.max(0.0, 1.0 - Math.min(horizontalDist, radius) / radius);
        Vec3 pull = new Vec3(dx, 0.0, dz).normalize().scale(proximity * 0.08 * nearest.getLevel().getMaxWindSpeed());

        Vec3 swirlDir = horizontalDist > 1e-3 ? new Vec3(-(dz / horizontalDist), 0.0, dx / horizontalDist) : Vec3.ZERO;
        Vec3 rotation = swirlDir.scale(proximity * proximity * 0.12 * nearest.getLevel().getMaxWindSpeed());

        double liftScale = Math.max(0.0, 1.0 - Math.abs(position.y - center.y) / 40.0);
        Vec3 lift = new Vec3(0.0, proximity * 0.04 * nearest.getLevel().getMaxWindSpeed() * liftScale, 0.0);

        return new TornadoForces(pull, rotation, lift, nearest);
    }
}
