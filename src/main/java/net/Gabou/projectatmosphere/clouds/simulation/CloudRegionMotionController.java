package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
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
        if (!state.isActive()) {
            return false;
        }

        Vec3 velocity = state.getVelocity();

        if (velocity == null || velocity.equals(Vec3.ZERO)) {
            return false;
        }

        Vec3 currentCenter = state.getCenter();
        Vec3 nextCenter = currentCenter.add(velocity);

        state.setPreviousCenter(currentCenter);
        state.setCenter(nextCenter);

        return true;
    }
}
