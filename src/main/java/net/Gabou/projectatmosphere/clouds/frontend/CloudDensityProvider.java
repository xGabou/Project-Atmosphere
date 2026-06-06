package net.Gabou.projectatmosphere.clouds.frontend;

import net.minecraft.world.phys.Vec3;

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

    /**
     * Échantillonne une densité CPU simple pour le premier test de pipeline.
     * Cette méthode n'utilise pas de bruit et ne remplace pas le futur shader.
     *
     * @param snapshot snapshot de rendu live
     * @param worldPosition position monde à échantillonner
     * @return densité échantillonnée entre 0 et 1
     */
    public static float sampleDensity(CloudRenderSnapshot snapshot, Vec3 worldPosition) {
        if (snapshot == null || worldPosition == null || snapshot.getRegionCenter() == null) {
            return 0.0F;
        }

        float radius = snapshot.getRegionRadius();
        if (radius <= 0.0F) {
            return 0.0F;
        }

        double dx = worldPosition.x() - snapshot.getRegionCenter().x();
        double dz = worldPosition.z() - snapshot.getRegionCenter().z();
        float horizontalDistance = (float) Math.sqrt(dx * dx + dz * dz);
        float normalizedHorizontal = horizontalDistance / radius;

        if (normalizedHorizontal >= 1.0F) {
            return 0.0F;
        }

        float baseY = snapshot.getCloudBaseY();
        float topY = snapshot.getCloudTopY();
        if (topY <= baseY || worldPosition.y() < baseY || worldPosition.y() > topY) {
            return 0.0F;
        }

        float edgeSoftness = clamp01(snapshot.getEdgeSoftness());
        float horizontalFade = edgeSoftness <= 0.0F
                ? 1.0F
                : smoothstep(1.0F, 1.0F - edgeSoftness, normalizedHorizontal);

        float normalizedVertical = (float) ((worldPosition.y() - baseY) / (topY - baseY));
        float verticalFade = smoothstep(0.0F, 0.15F, normalizedVertical)
                * (1.0F - smoothstep(0.85F, 1.0F, normalizedVertical));

        float density = getEffectiveDensity(snapshot)
                * getEffectiveCoverage(snapshot)
                * horizontalFade
                * verticalFade;

        return clamp01(density);
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

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0F : 1.0F;
        }

        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }
}
