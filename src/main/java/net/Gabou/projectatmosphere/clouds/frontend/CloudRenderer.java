package net.Gabou.projectatmosphere.clouds.frontend;

import org.jetbrains.annotations.Nullable;

/**
 * Point d'entrée du futur rendu live des nuages.
 * Cette classe ne gère pas le rendu debug et ne lit jamais debugSnapshot.
 */
public final class CloudRenderer {

    private CloudRenderer() {

    }

    /**
     * Prépare le rendu live des nuages à partir du snapshot courant.
     * Pour l'instant, cette méthode valide seulement que le chemin live existe.
     */
    public static void render() {
        for (CloudRenderSnapshot snapshot : CloudRenderController.getRenderableLiveSnapshots()) {
            renderSnapshot(snapshot);
        }
    }

    /**
     * Reçoit un snapshot live valide.
     * Le rendu réel sera ajouté ici plus tard.
     *
     * @param snapshot snapshot live valide
     */
    private static void renderSnapshot(@Nullable CloudRenderSnapshot snapshot) {

        // Futur rendu live ici.
    }
}