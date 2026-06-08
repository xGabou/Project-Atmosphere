package net.Gabou.projectatmosphere.clouds.type;

import java.util.Objects;

/**
 * Définition complète d'un type de nuage vivant.
 */
public final class CloudTypeDefinition {

    private final String id;
    private final String displayName;
    private final CloudFamily family;
    private final CloudVisualProfile visualProfile;
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
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.family = Objects.requireNonNull(family, "family");
        this.visualProfile = Objects.requireNonNull(visualProfile, "visualProfile");
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

    public CloudSpawnConditions getSpawnConditions() {
        return spawnConditions;
    }

    public CloudEvolutionRules getEvolutionRules() {
        return evolutionRules;
    }
}
