package net.Gabou.projectatmosphere.clouds.type;

/**
 * Conditions simplifiées qui décrivent quand un type de nuage peut naître.
 */
public final class CloudSpawnConditions {

    private final float minHumidity;
    private final float maxHumidity;
    private final float minTemperature;
    private final float maxTemperature;
    private final float minPressure;
    private final float maxPressure;
    private final float minStormChance;
    private final float minInstability;
    private final float minLift;

    public CloudSpawnConditions(
            float minHumidity,
            float maxHumidity,
            float minTemperature,
            float maxTemperature,
            float minPressure,
            float maxPressure,
            float minStormChance,
            float minInstability,
            float minLift
    ) {
        this.minHumidity = minHumidity;
        this.maxHumidity = maxHumidity;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        this.minPressure = minPressure;
        this.maxPressure = maxPressure;
        this.minStormChance = minStormChance;
        this.minInstability = minInstability;
        this.minLift = minLift;
    }

    /**
     * Vérifie si les valeurs météo connues respectent ces conditions.
     *
     * @param humidity humidité normalisée ou pourcentage selon l'appelant
     * @param temperature température en degrés Celsius
     * @param pressure pression relative
     * @param stormChance probabilité d'orage normalisée
     * @param instability instabilité atmosphérique, future valeur PA
     * @param lift soulèvement atmosphérique, future valeur PA
     * @return true si le type peut apparaître
     */
    public boolean matches(
            float humidity,
            float temperature,
            float pressure,
            float stormChance,
            float instability,
            float lift
    ) {
        return humidity >= minHumidity
                && humidity <= maxHumidity
                && temperature >= minTemperature
                && temperature <= maxTemperature
                && pressure >= minPressure
                && pressure <= maxPressure
                && stormChance >= minStormChance
                && instability >= minInstability
                && lift >= minLift;
    }

    /**
     * Retourne l'humidité minimale.
     *
     * @return humidité minimale
     */
    public float getMinHumidity() {
        return minHumidity;
    }

    public float getMaxHumidity() {
        return maxHumidity;
    }

    public float getMinTemperature() {
        return minTemperature;
    }

    public float getMaxTemperature() {
        return maxTemperature;
    }

    public float getMinPressure() {
        return minPressure;
    }

    public float getMaxPressure() {
        return maxPressure;
    }

    public float getMinStormChance() {
        return minStormChance;
    }

    public float getMinInstability() {
        return minInstability;
    }

    public float getMinLift() {
        return minLift;
    }
}
