package net.Gabou.projectatmosphere.clouds.type;

import net.Gabou.projectatmosphere.modules.weather.StormVisualTier;

import java.util.Objects;

/**
 * Définition complète d'un type de nuage vivant.
 */
public final class CloudTypeDefinition {

    private final String id;
    private final String displayName;
    private final CloudFamily family;
    private final CloudVisualProfile visualProfile;
    private final CloudMaterialProfile materialProfile;
    private final CloudShapeProfile shapeProfile;
    private final StormVisualTier stormVisualTier;
    private final CloudSpawnConditions spawnConditions;
    private final CloudEvolutionRules evolutionRules;

    public CloudTypeDefinition(
            String id,
            String displayName,
            CloudFamily family,
            CloudVisualProfile visualProfile,
            CloudSpawnConditions spawnConditions,
            CloudEvolutionRules evolutionRules
    ) {
        this(
                id,
                displayName,
                family,
                visualProfile,
                CloudMaterialProfile.DEFAULT.withVisualDefaults(visualProfile),
                CloudShapeProfile.defaultFor(id, family, visualProfile),
                resolveDefaultVisualTier(family, visualProfile),
                spawnConditions,
                evolutionRules
        );
    }

    public CloudTypeDefinition(
            String id,
            String displayName,
            CloudFamily family,
            CloudVisualProfile visualProfile,
            CloudMaterialProfile materialProfile,
            CloudShapeProfile shapeProfile,
            StormVisualTier stormVisualTier,
            CloudSpawnConditions spawnConditions,
            CloudEvolutionRules evolutionRules
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.family = Objects.requireNonNull(family, "family");
        this.visualProfile = Objects.requireNonNull(visualProfile, "visualProfile");
        this.materialProfile = Objects.requireNonNull(materialProfile, "materialProfile");
        this.shapeProfile = Objects.requireNonNull(shapeProfile, "shapeProfile");
        this.stormVisualTier = Objects.requireNonNull(stormVisualTier, "stormVisualTier");
        this.spawnConditions = Objects.requireNonNull(spawnConditions, "spawnConditions");
        this.evolutionRules = Objects.requireNonNull(evolutionRules, "evolutionRules");
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public CloudFamily getFamily() {
        return family;
    }

    public CloudVisualProfile getVisualProfile() {
        return visualProfile;
    }

    public CloudMaterialProfile getMaterialProfile() {
        return materialProfile;
    }

    public CloudShapeProfile getShapeProfile() {
        return shapeProfile;
    }

    public StormVisualTier getStormVisualTier() {
        return stormVisualTier;
    }

    public CloudSpawnConditions getSpawnConditions() {
        return spawnConditions;
    }

    public CloudEvolutionRules getEvolutionRules() {
        return evolutionRules;
    }

    private static StormVisualTier resolveDefaultVisualTier(CloudFamily family, CloudVisualProfile visualProfile) {
        if (family == CloudFamily.CUMULONIMBUS) {
            return StormVisualTier.THUNDER_CORE;
        }
        if (family == CloudFamily.NIMBOSTRATUS) {
            return StormVisualTier.RAIN_CORE;
        }
        float severity = visualProfile == null ? 0.0F : Math.max(visualProfile.getBaseDarkness(), visualProfile.getPrecipitationCoreStrength());
        return StormVisualTier.fromSeverity(severity);
    }
}
