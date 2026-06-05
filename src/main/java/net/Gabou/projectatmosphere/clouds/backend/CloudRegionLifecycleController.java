package net.Gabou.projectatmosphere.clouds.backend;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Gère la durée de vie, l'activation et le decay des régions de nuage backend.
 * Cette classe ne fait pas de rendu et ne synchronise rien.
 */
final class CloudRegionLifecycleController {

    /**
     * Met à jour le cycle de vie d'une région de nuage.
     *
     * @param level niveau serveur
     * @param state région de nuage à mettre à jour
     * @return true si la région a été modifiée
     */
    boolean tick(@NotNull ServerLevel level, @NotNull CloudRegionState state) {
        return false;
    }
}