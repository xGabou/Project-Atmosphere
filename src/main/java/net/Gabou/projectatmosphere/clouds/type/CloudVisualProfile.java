package net.Gabou.projectatmosphere.clouds.type;

/**
 * Paramètres visuels immutables utilisés pour convertir un type de nuage en uniforms shader.
 */
public final class CloudVisualProfile {

    private final float verticalThickness;
    private final float edgeErosionStrength;
    private final float topSoftness;
    private final float baseSoftness;
    private final float baseDarkness;
    private final float noiseScale;
    private final float detailNoiseScale;
    private final float erosionNoiseScale;
    private final float densityMultiplier;
    private final float coverageMultiplier;
    private final float heightSquash;
    private final float towerStrength;
    private final float anvilStrength;
    private final float precipitationCoreStrength;

    public CloudVisualProfile(
            float verticalThickness,
            float edgeErosionStrength,
            float topSoftness,
            float baseSoftness,
            float baseDarkness,
            float noiseScale,
            float detailNoiseScale,
            float erosionNoiseScale,
            float densityMultiplier,
            float coverageMultiplier,
            float heightSquash,
            float towerStrength,
            float anvilStrength,
            float precipitationCoreStrength
    ) {
        this.verticalThickness = clamp(verticalThickness, 0.05F, 4.0F);
        this.edgeErosionStrength = clamp01(edgeErosionStrength);
        this.topSoftness = clamp(topSoftness, 0.01F, 0.80F);
        this.baseSoftness = clamp(baseSoftness, 0.01F, 0.80F);
        this.baseDarkness = clamp01(baseDarkness);
        this.noiseScale = clamp(noiseScale, 0.001F, 1.0F);
        this.detailNoiseScale = clamp(detailNoiseScale, 0.001F, 2.0F);
        this.erosionNoiseScale = clamp(erosionNoiseScale, 0.001F, 2.0F);
        this.densityMultiplier = clamp(densityMultiplier, 0.0F, 4.0F);
        this.coverageMultiplier = clamp(coverageMultiplier, 0.0F, 4.0F);
        this.heightSquash = clamp(heightSquash, 0.10F, 4.0F);
        this.towerStrength = clamp01(towerStrength);
        this.anvilStrength = clamp01(anvilStrength);
        this.precipitationCoreStrength = clamp01(precipitationCoreStrength);
    }

    /**
     * Retourne l'épaisseur verticale relative du corps du nuage.
     *
     * @return épaisseur verticale relative
     */
    public float getVerticalThickness() {
        return verticalThickness;
    }

    /**
     * Retourne la force d'érosion appliquée aux bords.
     *
     * @return force d'érosion des bords
     */
    public float getEdgeErosionStrength() {
        return edgeErosionStrength;
    }

    /**
     * Retourne la douceur de disparition du sommet.
     *
     * @return douceur du sommet
     */
    public float getTopSoftness() {
        return topSoftness;
    }

    /**
     * Retourne la douceur de disparition de la base.
     *
     * @return douceur de la base
     */
    public float getBaseSoftness() {
        return baseSoftness;
    }

    /**
     * Retourne l'assombrissement visuel de la base.
     *
     * @return assombrissement de base
     */
    public float getBaseDarkness() {
        return baseDarkness;
    }

    /**
     * Retourne l'échelle du bruit de forme principal.
     *
     * @return échelle du bruit principal
     */
    public float getNoiseScale() {
        return noiseScale;
    }

    /**
     * Retourne l'échelle du bruit de détail.
     *
     * @return échelle du bruit de détail
     */
    public float getDetailNoiseScale() {
        return detailNoiseScale;
    }

    /**
     * Retourne l'échelle du bruit d'érosion.
     *
     * @return échelle du bruit d'érosion
     */
    public float getErosionNoiseScale() {
        return erosionNoiseScale;
    }

    /**
     * Retourne le multiplicateur de densité visuelle.
     *
     * @return multiplicateur de densité
     */
    public float getDensityMultiplier() {
        return densityMultiplier;
    }

    /**
     * Retourne le multiplicateur de couverture visuelle.
     *
     * @return multiplicateur de couverture
     */
    public float getCoverageMultiplier() {
        return coverageMultiplier;
    }

    /**
     * Retourne le facteur d'écrasement vertical perçu.
     *
     * @return écrasement vertical
     */
    public float getHeightSquash() {
        return heightSquash;
    }

    /**
     * Retourne la force de développement vertical.
     *
     * @return force de tour
     */
    public float getTowerStrength() {
        return towerStrength;
    }

    /**
     * Retourne la force d'enclume au sommet.
     *
     * @return force d'enclume
     */
    public float getAnvilStrength() {
        return anvilStrength;
    }

    /**
     * Retourne la force du noyau de précipitation.
     *
     * @return force du noyau de précipitation
     */
    public float getPrecipitationCoreStrength() {
        return precipitationCoreStrength;
    }

    public static CloudVisualProfile blend(CloudVisualProfile from, CloudVisualProfile to, float mix) {
        CloudVisualProfile left = from != null ? from : to;
        CloudVisualProfile right = to != null ? to : from;
        if (left == null && right == null) {
            return new CloudVisualProfile(1.0F, 0.0F, 0.2F, 0.2F, 0.0F, 0.02F, 0.12F, 0.12F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F);
        }
        if (left == right) {
            return left;
        }

        float t = clamp01(mix);
        return new CloudVisualProfile(
                lerp(left.verticalThickness, right.verticalThickness, t),
                lerp(left.edgeErosionStrength, right.edgeErosionStrength, t),
                lerp(left.topSoftness, right.topSoftness, t),
                lerp(left.baseSoftness, right.baseSoftness, t),
                lerp(left.baseDarkness, right.baseDarkness, t),
                lerp(left.noiseScale, right.noiseScale, t),
                lerp(left.detailNoiseScale, right.detailNoiseScale, t),
                lerp(left.erosionNoiseScale, right.erosionNoiseScale, t),
                lerp(left.densityMultiplier, right.densityMultiplier, t),
                lerp(left.coverageMultiplier, right.coverageMultiplier, t),
                lerp(left.heightSquash, right.heightSquash, t),
                lerp(left.towerStrength, right.towerStrength, t),
                lerp(left.anvilStrength, right.anvilStrength, t),
                lerp(left.precipitationCoreStrength, right.precipitationCoreStrength, t)
        );
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    private static float lerp(float from, float to, float mix) {
        return from + ((to - from) * mix);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
