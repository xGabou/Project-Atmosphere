package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Applique une géométrie backend simple selon le type de nuage.
 * Cette classe sert au test de forme; les valeurs seront remplacées par le spawner météo réel.
 */
final class CloudRegionTypeGeometry {

    private CloudRegionTypeGeometry() {

    }

    /**
     * Ajuste le rayon et les bornes verticales pour que le type soit visible sans attendre le shader final.
     *
     * @param state état backend à modifier
     * @param cloudTypeId identifiant de type demandé
     */
    static void apply(@NotNull CloudRegionState state, String cloudTypeId) {
        CloudClusterState cluster = state.getClusters().stream()
                .filter(CloudClusterState::isActive)
                .reduce((first, second) -> first.getFootprint() >= second.getFootprint() ? first : second)
                .orElse(null);
        if (cluster == null) {
            return;
        }

        apply(cluster, cloudTypeId);
    }

    static void apply(@NotNull CloudClusterState cluster, String cloudTypeId) {
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cloudTypeId);
        CloudMorphologyGenerators.applyToCluster(cluster, definition);
    }
}
