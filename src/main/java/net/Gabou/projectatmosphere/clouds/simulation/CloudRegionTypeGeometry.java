package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
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
        Shape shape = resolveShape(cloudTypeId);
        double centerY = state.getCenter().y();

        state.setRadius(shape.radius);
        state.setVerticalBounds(
                (float) centerY - shape.baseOffset,
                (float) centerY + shape.topOffset
        );
        state.setDensity(shape.density);
        state.setCoverage(shape.coverage);
        state.setEdgeSoftness(shape.edgeSoftness);
    }

    private static Shape resolveShape(String cloudTypeId) {
        String normalizedType = CloudTypeRegistry.getOrDefault(cloudTypeId).getId();

        return switch (normalizedType) {
            case "cumulus_mediocris" -> new Shape(58.0F, 18.0F, 32.0F, 0.70F, 0.78F, 0.34F);
            case "cumulus_congestus" -> new Shape(72.0F, 22.0F, 62.0F, 0.76F, 0.82F, 0.32F);
            case "cumulonimbus_calvus" -> new Shape(94.0F, 32.0F, 112.0F, 0.84F, 0.88F, 0.30F);
            case "cumulonimbus_capillatus" -> new Shape(118.0F, 36.0F, 142.0F, 0.88F, 0.92F, 0.28F);
            case "stratus_nebulosus" -> new Shape(128.0F, 8.0F, 14.0F, 0.50F, 0.92F, 0.52F);
            case "stratocumulus" -> new Shape(106.0F, 10.0F, 24.0F, 0.62F, 0.86F, 0.44F);
            case "nimbostratus" -> new Shape(150.0F, 12.0F, 30.0F, 0.82F, 0.96F, 0.48F);
            case "cirrus" -> new Shape(122.0F, 5.0F, 12.0F, 0.34F, 0.58F, 0.64F);
            default -> new Shape(48.0F, 10.0F, 24.0F, 0.62F, 0.72F, 0.38F);
        };
    }

    private record Shape(
            float radius,
            float baseOffset,
            float topOffset,
            float density,
            float coverage,
            float edgeSoftness
    ) {
    }
}
