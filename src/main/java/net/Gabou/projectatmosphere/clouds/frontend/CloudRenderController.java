package net.Gabou.projectatmosphere.clouds.frontend;

import org.jetbrains.annotations.Nullable;

/**
 * Contrôleur du futur rendu live des nuages.
 * Cette classe lit seulement le snapshot live et prépare la séparation avec le rendu debug.
 */
public final class CloudRenderController {

    private CloudRenderController() {

    }

    /**
     * Retourne le snapshot live courant si un rendu live peut être envisagé.
     *
     * @return snapshot live courant, ou null si aucun snapshot valide n'existe
     */
    public static @Nullable CloudRenderSnapshot getRenderableLiveSnapshot() {
        CloudRenderSnapshot snapshot = CloudRenderStateHolder.getInstance().getCurrentSnapshot();

        if (snapshot == null) {
            return null;
        }

        if (!snapshot.isEnabled()) {
            return null;
        }

        if (snapshot.getRegionCenter() == null) {
            return null;
        }

        if (snapshot.getRegionRadius() <= 0.0F) {
            return null;
        }

        if (snapshot.getCloudTopY() <= snapshot.getCloudBaseY()) {
            return null;
        }

        return snapshot;
    }

    /**
     * Indique si un snapshot live valide existe.
     *
     * @return true si un snapshot live valide existe
     */
    public static boolean hasRenderableLiveSnapshot() {
        return getRenderableLiveSnapshot() != null;
    }
}