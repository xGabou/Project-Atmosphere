package net.Gabou.projectatmosphere.clouds.frontend;

/**
 * Fournit les valeurs de densité effectives utilisées par le rendu live.
 * Cette classe ne fait aucun draw call et ne lit jamais le backend.
 */
public final class CloudDensityProvider {

    private CloudDensityProvider() {

    }

    /**
     * Calcule la densité effective d'un snapshot en tenant compte de la croissance et du decay.
     *
     * @param snapshot snapshot de rendu live
     * @return densité effective entre 0 et 1
     */
    public static float getEffectiveDensity(CloudRenderSnapshot snapshot) {
        if (snapshot == null) {
            return 0.0F;
        }

        float lifecycleFactor = getLifecycleFactor(snapshot);
        return clamp01(snapshot.getDensity() * lifecycleFactor);
    }

    /**
     * Calcule la couverture effective d'un snapshot en tenant compte de la croissance et du decay.
     *
     * @param snapshot snapshot de rendu live
     * @return couverture effective entre 0 et 1
     */
    public static float getEffectiveCoverage(CloudRenderSnapshot snapshot) {
        if (snapshot == null) {
            return 0.0F;
        }

        float lifecycleFactor = getLifecycleFactor(snapshot);
        return clamp01(snapshot.getCoverage() * lifecycleFactor);
    }

    /**
     * Calcule le facteur de vie du nuage.
     *
     * @param snapshot snapshot de rendu live
     * @return facteur entre 0 et 1
     */
    public static float getLifecycleFactor(CloudRenderSnapshot snapshot) {
        if (snapshot == null) {
            return 0.0F;
        }

        return clamp01(snapshot.getGrowth() * (1.0F - snapshot.getDecay()));
    }

    /**
     * Vérifie si un snapshot a encore une densité suffisante pour être rendu.
     *
     * @param snapshot snapshot de rendu live
     * @return true si le snapshot peut produire un rendu visible
     */
    public static boolean hasVisibleDensity(CloudRenderSnapshot snapshot) {
        return getEffectiveDensity(snapshot) > 0.001F && getEffectiveCoverage(snapshot) > 0.001F;
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }

        if (value > 1.0F) {
            return 1.0F;
        }

        return value;
    }
}