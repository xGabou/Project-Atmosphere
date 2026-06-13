package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.clouds.type.CloudVisualProfile;
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
        CloudShapeProfile shape = definition.getShapeProfile();
        CloudVisualProfile visual = definition.getVisualProfile();
        double centerY = cluster.getCenter().y();
        float existingRadius = Math.max(1.0F, cluster.getRadius());
        float targetRadius = Math.max(shape.getBaseRadius(), existingRadius * (1.0F + cluster.getMergePressure() * 0.18F));
        float targetBaseY = (float) centerY - shape.getBaseOffset();
        float targetTopY = (float) centerY + shape.getTopOffset() + cluster.getMergePressure() * 12.0F;

        cluster.setRadius(targetRadius);
        cluster.setVerticalBounds(
                Math.min(cluster.getBaseY(), targetBaseY),
                Math.max(cluster.getTopY(), targetTopY)
        );
        cluster.setDensity(clamp01(0.48F + visual.getDensityMultiplier() * 0.26F + visual.getPrecipitationCoreStrength() * 0.18F));
        cluster.setCoverage(clamp01(0.56F + visual.getCoverageMultiplier() * 0.24F + shape.getBaseFlattening() * 0.14F));
        cluster.setEdgeSoftness(clamp01(0.24F + visual.getEdgeErosionStrength() * 0.28F + shape.getEdgeRaggedness() * 0.12F));
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
