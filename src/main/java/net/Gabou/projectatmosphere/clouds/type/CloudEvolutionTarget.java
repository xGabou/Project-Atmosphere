package net.Gabou.projectatmosphere.clouds.type;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Cible possible d'evolution pour un type de nuage.
 */
public final class CloudEvolutionTarget {

    private final String targetCloudTypeId;
    private final int minAgeTicks;
    private final float minHumidity;
    private final float minInstability;
    private final float maxPressure;
    private final float minStormChance;
    private final float minTemperature;
    private final float maxTemperature;
    private final float minDensity;
    private final float minCoverage;
    private final float minLift;
    private final float minMergePressure;
    private final float chancePerCheck;

    public CloudEvolutionTarget(
            String targetCloudTypeId,
            int minAgeTicks,
            float minHumidity,
            float minInstability,
            float maxPressure,
            float minStormChance,
            float minDensity,
            float minCoverage,
            float minLift,
            float minMergePressure,
            float chancePerCheck
    ) {
        this(
                targetCloudTypeId,
                minAgeTicks,
                minHumidity,
                minInstability,
                maxPressure,
                minStormChance,
                Float.NaN,
                Float.NaN,
                minDensity,
                minCoverage,
                minLift,
                minMergePressure,
                chancePerCheck
        );
    }

    public CloudEvolutionTarget(
            String targetCloudTypeId,
            int minAgeTicks,
            float minHumidity,
            float minInstability,
            float maxPressure,
            float minStormChance,
            float chancePerCheck
    ) {
        this(
                targetCloudTypeId,
                minAgeTicks,
                minHumidity,
                minInstability,
                maxPressure,
                minStormChance,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                chancePerCheck
        );
    }

    public CloudEvolutionTarget(
            String targetCloudTypeId,
            int minAgeTicks,
            float minHumidity,
            float minInstability,
            float maxPressure,
            float minStormChance,
            float minTemperature,
            float maxTemperature,
            float minDensity,
            float minCoverage,
            float minLift,
            float minMergePressure,
            float chancePerCheck
    ) {
        this.targetCloudTypeId = targetCloudTypeId;
        this.minAgeTicks = Math.max(0, minAgeTicks);
        this.minHumidity = minHumidity;
        this.minInstability = minInstability;
        this.maxPressure = maxPressure;
        this.minStormChance = minStormChance;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        this.minDensity = minDensity;
        this.minCoverage = minCoverage;
        this.minLift = minLift;
        this.minMergePressure = minMergePressure;
        this.chancePerCheck = clamp01(chancePerCheck);
    }

    /**
     * Vérifie si les seuils sont tous atteints.
     */
    public boolean matches(
            int cloudTypeTicks,
            float humidity,
            float instability,
            float pressure,
            float stormChance,
            float temperature,
            float density,
            float coverage,
            float lift,
            float mergePressure
    ) {
        return satisfiedCount(cloudTypeTicks, humidity, instability, pressure, stormChance, temperature, density, coverage, lift, mergePressure)
                >= activeThresholdCount();
    }

    /**
     * Permet une evolution anticipée si une partie des seuils est deja atteinte.
     */
    public boolean canAdvanceEarly(
            int cloudTypeTicks,
            float humidity,
            float instability,
            float pressure,
            float stormChance,
            float temperature,
            float density,
            float coverage,
            float lift,
            float mergePressure
    ) {
        int activeThresholds = activeThresholdCount();
        if (activeThresholds <= 0) {
            return false;
        }

        int satisfiedThresholds = satisfiedCount(
                cloudTypeTicks,
                humidity,
                instability,
                pressure,
                stormChance,
                temperature,
                density,
                coverage,
                lift,
                mergePressure
        );
        if (satisfiedThresholds <= 0 || satisfiedThresholds >= activeThresholds) {
            return false;
        }

        float progress = (float) satisfiedThresholds / (float) activeThresholds;
        float earlyChance = clamp01(chancePerCheck * progress * progress);
        return ThreadLocalRandom.current().nextFloat() < earlyChance;
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

    public float getMinTemperature() {
        return minTemperature;
    }

    public float getMaxTemperature() {
        return maxTemperature;
    }

    public float getMinDensity() {
        return minDensity;
    }

    public float getMinCoverage() {
        return minCoverage;
    }

    public float getMinLift() {
        return minLift;
    }

    public float getMinMergePressure() {
        return minMergePressure;
    }

    public float getChancePerCheck() {
        return chancePerCheck;
    }

    public int activeThresholdCount() {
        int count = 0;
        if (minAgeTicks >= 0) {
            count++;
        }
        if (isActiveThreshold(minHumidity)) {
            count++;
        }
        if (isActiveThreshold(minInstability)) {
            count++;
        }
        if (isActiveThreshold(maxPressure)) {
            count++;
        }
        if (isActiveThreshold(minStormChance)) {
            count++;
        }
        if (isActiveThreshold(minTemperature)) {
            count++;
        }
        if (isActiveThreshold(maxTemperature)) {
            count++;
        }
        if (isActiveThreshold(minDensity)) {
            count++;
        }
        if (isActiveThreshold(minCoverage)) {
            count++;
        }
        if (isActiveThreshold(minLift)) {
            count++;
        }
        if (isActiveThreshold(minMergePressure)) {
            count++;
        }
        return count;
    }

    public int satisfiedCount(
            int cloudTypeTicks,
            float humidity,
            float instability,
            float pressure,
            float stormChance,
            float temperature,
            float density,
            float coverage,
            float lift,
            float mergePressure
    ) {
        int count = 0;
        if (cloudTypeTicks >= minAgeTicks) {
            count++;
        }
        if (isActiveThreshold(minHumidity) && humidity >= minHumidity) {
            count++;
        }
        if (isActiveThreshold(minInstability) && instability >= minInstability) {
            count++;
        }
        if (isActiveThreshold(maxPressure) && pressure <= maxPressure) {
            count++;
        }
        if (isActiveThreshold(minStormChance) && stormChance >= minStormChance) {
            count++;
        }
        if (isActiveThreshold(minTemperature) && temperature >= minTemperature) {
            count++;
        }
        if (isActiveThreshold(maxTemperature) && temperature <= maxTemperature) {
            count++;
        }
        if (isActiveThreshold(minDensity) && density >= minDensity) {
            count++;
        }
        if (isActiveThreshold(minCoverage) && coverage >= minCoverage) {
            count++;
        }
        if (isActiveThreshold(minLift) && lift >= minLift) {
            count++;
        }
        if (isActiveThreshold(minMergePressure) && mergePressure >= minMergePressure) {
            count++;
        }
        return count;
    }

    private static boolean isActiveThreshold(float value) {
        return !Float.isNaN(value);
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
