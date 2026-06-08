package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

/**
 * Définit les paramètres de qualité du futur rendu live des nuages.
 * Cette classe ne fait aucun rendu et ne lit jamais le backend.
 */
public final class CloudRenderProfile {

    private final int raymarchSteps;
    private final float maxRenderDistance;
    private final float resolutionScale;
    private final boolean shadowsEnabled;

    private CloudRenderProfile(
            int raymarchSteps,
            float maxRenderDistance,
            float resolutionScale,
            boolean shadowsEnabled
    ) {
        this.raymarchSteps = raymarchSteps;
        this.maxRenderDistance = maxRenderDistance;
        this.resolutionScale = resolutionScale;
        this.shadowsEnabled = shadowsEnabled;
    }

    /**
     * Crée un profil de rendu temporaire par défaut.
     *
     * @return profil de rendu par défaut
     */
    public static CloudRenderProfile createDefault() {
        AtmoCommonConfig.CloudRaymarchQuality quality = AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.get();
        return new CloudRenderProfile(
                quality.getRaymarchSteps(),
                512.0F,
                1.0F,
                false
        );
    }

    public int getRaymarchSteps() {
        return raymarchSteps;
    }

    public float getMaxRenderDistance() {
        return maxRenderDistance;
    }

    public float getResolutionScale() {
        return resolutionScale;
    }

    public boolean isShadowsEnabled() {
        return shadowsEnabled;
    }
}
