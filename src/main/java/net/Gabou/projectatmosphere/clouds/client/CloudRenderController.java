package net.Gabou.projectatmosphere.clouds.client;


import net.Gabou.projectatmosphere.clouds.client.render.CloudDensityProvider;

import java.util.ArrayList;
import java.util.List;

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
    public static List<CloudRenderSnapshot> getRenderableLiveSnapshots() {
        List<CloudRenderSnapshot> snapshots = CloudRenderStateHolder.getInstance().getCurrentSnapshots();
        List<CloudRenderSnapshot> liveSnapshots = new ArrayList<>();
        for (CloudRenderSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }

            if (!snapshot.isEnabled()) {
                continue;
            }

            if (snapshot.getRegionCenter() == null) {
                continue;
            }

            if (snapshot.getRegionRadius() <= 0.0F) {
                continue;
            }

            if (snapshot.getCloudTopY() <= snapshot.getCloudBaseY()) {
                continue;
            }
            if (!CloudDensityProvider.hasVisibleDensity(snapshot)) {
                continue;
            }
            liveSnapshots.add(snapshot);

        }
        return liveSnapshots;
    }

    /**
     * Indique si un snapshot live valide existe.
     *
     * @return true si un snapshot live valide existe
     */
    public static boolean hasRenderableLiveSnapshots() {
        return !getRenderableLiveSnapshots().isEmpty();
    }
}
