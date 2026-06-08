package net.Gabou.projectatmosphere.clouds.type;

/**
 * Cible possible d'évolution pour un type de nuage.
 */
public final class CloudEvolutionTarget {

    private final String targetCloudTypeId;
    private final int minAgeTicks;
    private final float minHumidity;
    private final float minInstability;
    private final float maxPressure;
    private final float minStormChance;
    private final float chancePerCheck;

    public CloudEvolutionTarget(
            String targetCloudTypeId,
            int minAgeTicks,
            float minHumidity,
            float minInstability,
            float maxPressure,
            float minStormChance,
            float chancePerCheck
    ) {
        this.targetCloudTypeId = targetCloudTypeId;
        this.minAgeTicks = Math.max(0, minAgeTicks);
        this.minHumidity = minHumidity;
        this.minInstability = minInstability;
        this.maxPressure = maxPressure;
        this.minStormChance = minStormChance;
        this.chancePerCheck = clamp01(chancePerCheck);
    }

    /**
     * Vérifie si la météo autorise cette évolution.
     *
     * @param cloudTypeTicks âge du type courant
     * @param humidity humidité actuelle
     * @param instability instabilité actuelle
     * @param pressure pression actuelle
     * @param stormChance probabilité d'orage actuelle
     * @return true si la cible est éligible
     */
    public boolean matches(int cloudTypeTicks, float humidity, float instability, float pressure, float stormChance) {
        return cloudTypeTicks >= minAgeTicks
                && humidity >= minHumidity
                && instability >= minInstability
                && pressure <= maxPressure
                && stormChance >= minStormChance;
    }

    public String getTargetCloudTypeId() {
        return targetCloudTypeId;
    }

    public int getMinAgeTicks() {
        return minAgeTicks;
    }

    public float getMinHumidity() {
        return minHumidity;
    }

    public float getMinInstability() {
        return minInstability;
    }

    public float getMaxPressure() {
        return maxPressure;
    }

    public float getMinStormChance() {
        return minStormChance;
    }

    public float getChancePerCheck() {
        return chancePerCheck;
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
