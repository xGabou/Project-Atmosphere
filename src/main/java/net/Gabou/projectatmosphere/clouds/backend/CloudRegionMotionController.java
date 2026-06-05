package net.Gabou.projectatmosphere.clouds.backend;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Gère le mouvement des régions de nuage backend.
 * Cette classe ne crée pas de nuage et ne fait aucun rendu.
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
        return false;
    }
}