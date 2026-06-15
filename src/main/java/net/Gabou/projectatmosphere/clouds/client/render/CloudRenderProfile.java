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
    private final float temporalHistoryWeight;
    private final float compositeBlurRadius;
    private final float compositeBlurStrength;
    private final float rayJitterStrength;
    private final float rayJitterTemporalStrength;
    private final boolean shadowsEnabled;

    private CloudRenderProfile(
            int raymarchSteps,
            float maxRenderDistance,
            float resolutionScale,
            float temporalHistoryWeight,
            float compositeBlurRadius,
            float compositeBlurStrength,
            float rayJitterStrength,
            float rayJitterTemporalStrength,
            boolean shadowsEnabled
    ) {
        this.raymarchSteps = raymarchSteps;
        this.maxRenderDistance = maxRenderDistance;
        this.resolutionScale = resolutionScale;
        this.temporalHistoryWeight = temporalHistoryWeight;
        this.compositeBlurRadius = compositeBlurRadius;
        this.compositeBlurStrength = compositeBlurStrength;
        this.rayJitterStrength = rayJitterStrength;
        this.rayJitterTemporalStrength = rayJitterTemporalStrength;
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
                Math.max(100.0F, AtmoCommonConfig.CLOUD_RENDER_DISTANCE.get().floatValue()),
                quality.getResolutionScale(),
                quality.getTemporalHistoryWeight(),
                quality.getCompositeBlurRadius(),
                quality.getCompositeBlurStrength(),
                quality.getRayJitterStrength(),
                quality.getRayJitterTemporalStrength(),
                false
        );
    }

    public CloudRenderProfile withLod(int raymarchSteps, float maxRenderDistance) {
        return new CloudRenderProfile(
                Math.max(1, Math.min(raymarchSteps, this.raymarchSteps)),
                Math.max(100.0F, maxRenderDistance),
                this.resolutionScale,
                this.temporalHistoryWeight,
                this.compositeBlurRadius,
                this.compositeBlurStrength,
                this.rayJitterStrength,
                this.rayJitterTemporalStrength,
                this.shadowsEnabled
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

    public float getTemporalHistoryWeight() {
        return temporalHistoryWeight;
    }

    public float getCompositeBlurRadius() {
        return compositeBlurRadius;
    }

    public float getCompositeBlurStrength() {
        return compositeBlurStrength;
    }

    public float getRayJitterStrength() {
        return rayJitterStrength;
    }

    public float getRayJitterTemporalStrength() {
        return rayJitterTemporalStrength;
    }

    public boolean isShadowsEnabled() {
        return shadowsEnabled;
    }
}
