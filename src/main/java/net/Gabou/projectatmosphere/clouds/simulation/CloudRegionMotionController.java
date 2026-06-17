package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Gère le mouvement des régions de nuage backend.
 * Cette classe ne crée pas de nuage, ne sauvegarde rien directement et ne fait aucun rendu.
 */
final class CloudRegionMotionController {

    /**
     * Met à jour la position d'une région de nuage.
     *
     * @param level niveau serveur
     * @param state région de nuage à mettre à jour
     * @return true si la région a été modifiée
     */
    boolean tick(@NotNull ServerLevel level, @NotNull CloudRegionState state) {
        if (!state.isActive() || state.isEmpty()) {
            return false;
        }
        if (!isMovementEnabled() || isMovementFrozen()) {
            return false;
        }

        boolean changed = false;
        for (CloudClusterState cluster : state.getClusters()) {
            if (cluster == null || !cluster.isActive()) {
                continue;
            }

            Vec3 currentCenter = cluster.getCenter();
            Vec3 velocity = resolveWindVelocity(level, currentCenter);
            if (velocity.lengthSqr() <= 0.0000001D) {
                cluster.setVelocity(Vec3.ZERO);
                continue;
            }

            Vec3 nextCenter = currentCenter.add(velocity);

            cluster.setPreviousCenter(currentCenter);
            cluster.setCenter(nextCenter);
            cluster.setVelocity(velocity);
            changed = true;
        }

        return changed;
    }

    static boolean isMovementFrozen() {
        try {
            return AtmoCommonConfig.FREEZE_CLOUD_MOVEMENT.get();
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private boolean isMovementEnabled() {
        try {
            return AtmoCommonConfig.ENABLE_CLOUD_MOVEMENT.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    private Vec3 resolveWindVelocity(@NotNull ServerLevel level, @NotNull Vec3 center) {
        BlockPos pos = BlockPos.containing(center);
        WindVector wind = ForecastOrchestrator.getWind(RegionInstanceKey.from(pos), level.getGameTime());
        if (wind == null || wind.baseSpeed() <= 0.0F) {
            return Vec3.ZERO;
        }

        double scale = getDriftScale();
        double speed = Math.max(0.0D, wind.baseSpeed()) * scale;
        double angle = wind.angleRadians();
        return new Vec3(-Math.sin(angle) * speed, 0.0D, Math.cos(angle) * speed);
    }

    private double getDriftScale() {
        try {
            return AtmoCommonConfig.CLOUD_WIND_DRIFT_SCALE.get();
        } catch (IllegalStateException exception) {
            return 0.035D;
        }
    }
}
