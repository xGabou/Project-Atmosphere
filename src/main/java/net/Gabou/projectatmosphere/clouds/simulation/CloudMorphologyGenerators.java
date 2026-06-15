package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudVisualProfile;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Family-specific cloud morphology generators.
 */
final class CloudMorphologyGenerators {

    private CloudMorphologyGenerators() {
    }

    static @NotNull SpawnPlan createSpawnPlan(@NotNull CloudTypeDefinition definition, @NotNull RandomSource random) {
        CloudMorphologyFamily family = definition.getMorphologyFamily();
        return switch (family) {
            case PUFF -> puffPlan(definition, random);
            case TOWER -> towerPlan(definition, random);
            case STORM_ANVIL -> stormAnvilPlan(definition, random);
            case SHEET -> sheetPlan(definition, random);
            case CELLULAR_SHEET -> cellularSheetPlan(definition, random);
            case FILAMENT -> filamentPlan(definition, random);
            case SPIRAL_STORM -> spiralStormPlan(definition, random);
            case DEBUG -> debugPlan(definition, random);
        };
    }

    static @NotNull Vec3 createClusterCenter(
            @NotNull Vec3 origin,
            @NotNull SpawnPlan plan,
            int clusterIndex,
            @NotNull RandomSource random
    ) {
        if (clusterIndex <= 0) {
            return origin;
        }

        return switch (plan.family()) {
            case PUFF -> radialCell(origin, plan, random, 0.22F, 0.86F, 7.0F);
            case TOWER -> towerCell(origin, plan, clusterIndex, random);
            case STORM_ANVIL -> stormCell(origin, plan, clusterIndex, random);
            case SPIRAL_STORM -> spiralCell(origin, plan, clusterIndex, random);
            case SHEET -> sheetCell(origin, plan, clusterIndex, random, 0.18F);
            case CELLULAR_SHEET -> sheetCell(origin, plan, clusterIndex, random, 0.52F);
            case FILAMENT -> filamentCell(origin, plan, clusterIndex, random);
            case DEBUG -> radialCell(origin, plan, random, 0.18F, 0.72F, 4.0F);
        };
    }

    static void tuneSpawnedCluster(
            @NotNull CloudClusterState cluster,
            @NotNull CloudTypeDefinition definition,
            @NotNull SpawnPlan plan,
            float scale,
            int clusterIndex,
            @NotNull RandomSource random
    ) {
        applyToCluster(cluster, definition);

        float radiusJitter = 0.92F + random.nextFloat() * 0.16F;
        float finalRadius = Math.max(6.0F, cluster.getRadius() * scale * radiusJitter);
        if (plan.family() == CloudMorphologyFamily.FILAMENT) {
            finalRadius = Math.max(5.0F, finalRadius * (0.42F + random.nextFloat() * 0.20F));
        } else if (plan.family() == CloudMorphologyFamily.CELLULAR_SHEET) {
            finalRadius = Math.max(10.0F, finalRadius * (0.52F + random.nextFloat() * 0.24F));
        }

        cluster.setRadius(finalRadius);
        cluster.setDensity(Mth.clamp(plan.density() * (0.90F + random.nextFloat() * 0.18F), 0.0F, 1.0F));
        cluster.setCoverage(Mth.clamp(plan.coverage() * (0.88F + random.nextFloat() * 0.20F), 0.0F, 1.0F));
        cluster.setEdgeSoftness(Mth.clamp(plan.edgeSoftness() * (0.88F + random.nextFloat() * 0.24F), 0.02F, 0.95F));

        if (plan.family() == CloudMorphologyFamily.TOWER && clusterIndex > 0) {
            float lift = Math.min(0.45F, clusterIndex * 0.06F);
            cluster.setVerticalBounds(cluster.getBaseY() + lift * plan.baseDrop(), cluster.getTopY() + lift * plan.topRise());
        }
        if (plan.family() == CloudMorphologyFamily.STORM_ANVIL && clusterIndex > plan.clusterCount() / 2) {
            cluster.setVerticalBounds(cluster.getBaseY() + plan.baseDrop() * 0.38F, cluster.getTopY() + plan.topRise() * 0.22F);
            cluster.setRadius(cluster.getRadius() * 1.24F);
        }
    }

    static void applyToCluster(@NotNull CloudClusterState cluster, @NotNull CloudTypeDefinition definition) {
        CloudShapeProfile shape = definition.getShapeProfile();
        CloudVisualProfile visual = definition.getVisualProfile();
        CloudMorphologyFamily family = definition.getMorphologyFamily();

        double centerY = cluster.getCenter().y();
        float existingRadius = Math.max(1.0F, cluster.getRadius());
        float mergeBoost = 1.0F + cluster.getMergePressure() * 0.18F;
        float targetRadius = Math.max(shape.getBaseRadius() * radiusMultiplier(family), existingRadius * mergeBoost);
        float targetBaseY = (float) centerY - shape.getBaseOffset() * baseMultiplier(family);
        float targetTopY = (float) centerY + shape.getTopOffset() * topMultiplier(family) + cluster.getMergePressure() * 12.0F;

        cluster.setMorphologyFamily(family);
        cluster.setRadius(targetRadius);
        cluster.setVerticalBounds(
                Math.min(cluster.getBaseY(), targetBaseY),
                Math.max(cluster.getTopY(), targetTopY)
        );
        cluster.setDensity(Mth.clamp(densityFor(family, visual), 0.0F, 1.0F));
        cluster.setCoverage(Mth.clamp(coverageFor(family, visual, shape), 0.0F, 1.0F));
        cluster.setEdgeSoftness(Mth.clamp(edgeSoftnessFor(family, visual, shape), 0.02F, 0.95F));
    }

    private static SpawnPlan puffPlan(@NotNull CloudTypeDefinition definition, @NotNull RandomSource random) {
        CloudShapeProfile shape = definition.getShapeProfile();
        CloudVisualProfile visual = definition.getVisualProfile();
        String id = definition.getId().toLowerCase(Locale.ROOT);
        int minClusters = id.contains("vapor") ? 2 : id.contains("humilis") ? 3 : 4;
        int maxClusters = id.contains("vapor") ? 4 : id.contains("humilis") ? 5 : 7;
        float radius = shape.getBaseRadius() * (id.contains("mediocris") ? 0.98F : 0.82F);
        return plan(definition, random, minClusters, maxClusters, radius, radius * 2.05F,
                shape.getBaseOffset() * 0.95F, shape.getTopOffset(),
                0.38F + visual.getDensityMultiplier() * 0.25F,
                0.42F + visual.getCoverageMultiplier() * 0.24F,
                0.24F + visual.getEdgeErosionStrength() * 0.42F);
    }

    private static SpawnPlan towerPlan(@NotNull CloudTypeDefinition definition, @NotNull RandomSource random) {
        CloudShapeProfile shape = definition.getShapeProfile();
        CloudVisualProfile visual = definition.getVisualProfile();
        float radius = shape.getBaseRadius() * 0.86F;
        return plan(definition, random, 4, 7, radius, radius * 1.18F,
                shape.getBaseOffset() * 0.78F, shape.getTopOffset() * 1.34F,
                0.50F + visual.getDensityMultiplier() * 0.25F,
                0.48F + visual.getCoverageMultiplier() * 0.24F,
                0.20F + visual.getEdgeErosionStrength() * 0.38F);
    }

    private static SpawnPlan stormAnvilPlan(@NotNull CloudTypeDefinition definition, @NotNull RandomSource random) {
        CloudShapeProfile shape = definition.getShapeProfile();
        CloudVisualProfile visual = definition.getVisualProfile();
        float radius = shape.getBaseRadius() * 1.08F;
        return plan(definition, random, 7, 11, radius, radius * 2.55F,
                shape.getBaseOffset(), shape.getTopOffset() * 1.18F,
                0.58F + visual.getDensityMultiplier() * 0.25F + visual.getPrecipitationCoreStrength() * 0.12F,
                0.58F + visual.getCoverageMultiplier() * 0.24F,
                0.18F + visual.getEdgeErosionStrength() * 0.42F);
    }

    private static SpawnPlan spiralStormPlan(@NotNull CloudTypeDefinition definition, @NotNull RandomSource random) {
        CloudShapeProfile shape = definition.getShapeProfile();
        CloudVisualProfile visual = definition.getVisualProfile();
        float radius = shape.getBaseRadius() * 1.18F;
        return plan(definition, random, 9, 14, radius, radius * 3.30F,
                shape.getBaseOffset(), shape.getTopOffset() * 1.08F,
                0.56F + visual.getDensityMultiplier() * 0.24F + visual.getPrecipitationCoreStrength() * 0.14F,
                0.58F + visual.getCoverageMultiplier() * 0.22F,
                0.24F + visual.getEdgeErosionStrength() * 0.36F);
    }

    private static SpawnPlan sheetPlan(@NotNull CloudTypeDefinition definition, @NotNull RandomSource random) {
        CloudShapeProfile shape = definition.getShapeProfile();
        CloudVisualProfile visual = definition.getVisualProfile();
        float radius = shape.getBaseRadius() * 0.74F;
        return plan(definition, random, 7, 11, radius, radius * 3.40F,
                Math.max(4.0F, shape.getBaseOffset() * 0.72F), Math.max(8.0F, shape.getTopOffset() * 0.72F),
                0.46F + visual.getDensityMultiplier() * 0.22F + visual.getPrecipitationCoreStrength() * 0.16F,
                0.64F + visual.getCoverageMultiplier() * 0.22F,
                0.32F + visual.getTopSoftness() * 0.46F);
    }

    private static SpawnPlan cellularSheetPlan(@NotNull CloudTypeDefinition definition, @NotNull RandomSource random) {
        CloudShapeProfile shape = definition.getShapeProfile();
        CloudVisualProfile visual = definition.getVisualProfile();
        float radius = shape.getBaseRadius() * 0.46F;
        return plan(definition, random, 10, 15, radius, radius * 4.10F,
                Math.max(5.0F, shape.getBaseOffset() * 0.80F), Math.max(12.0F, shape.getTopOffset() * 0.86F),
                0.42F + visual.getDensityMultiplier() * 0.22F,
                0.44F + visual.getCoverageMultiplier() * 0.20F,
                0.26F + visual.getEdgeErosionStrength() * 0.50F);
    }

    private static SpawnPlan filamentPlan(@NotNull CloudTypeDefinition definition, @NotNull RandomSource random) {
        CloudShapeProfile shape = definition.getShapeProfile();
        CloudVisualProfile visual = definition.getVisualProfile();
        float radius = shape.getBaseRadius() * 0.24F;
        return plan(definition, random, 6, 10, radius, radius * 6.25F,
                Math.max(2.0F, shape.getBaseOffset() * 0.42F), Math.max(5.0F, shape.getTopOffset() * 0.46F),
                0.18F + visual.getDensityMultiplier() * 0.16F,
                0.20F + visual.getCoverageMultiplier() * 0.14F,
                0.48F + visual.getEdgeErosionStrength() * 0.40F);
    }

    private static SpawnPlan debugPlan(@NotNull CloudTypeDefinition definition, @NotNull RandomSource random) {
        CloudShapeProfile shape = definition.getShapeProfile();
        CloudVisualProfile visual = definition.getVisualProfile();
        return plan(definition, random, 1, 2, shape.getBaseRadius(), shape.getBaseRadius() * 1.20F,
                shape.getBaseOffset(), shape.getTopOffset(),
                0.42F + visual.getDensityMultiplier() * 0.20F,
                0.42F + visual.getCoverageMultiplier() * 0.20F,
                0.24F + visual.getEdgeErosionStrength() * 0.20F);
    }

    private static SpawnPlan plan(
            @NotNull CloudTypeDefinition definition,
            @NotNull RandomSource random,
            int minClusters,
            int maxClusters,
            float radius,
            float groupRadius,
            float baseDrop,
            float topRise,
            float density,
            float coverage,
            float edgeSoftness
    ) {
        int clusterCount = minClusters + random.nextInt(Math.max(1, maxClusters - minClusters + 1));
        return new SpawnPlan(
                definition.getMorphologyFamily(),
                clusterCount,
                Math.max(6.0F, radius * (0.92F + random.nextFloat() * 0.18F)),
                Math.max(8.0F, groupRadius * (0.90F + random.nextFloat() * 0.20F)),
                Math.max(2.0F, baseDrop),
                Math.max(4.0F, topRise),
                Mth.clamp(density, 0.10F, 0.98F),
                Mth.clamp(coverage, 0.12F, 0.98F),
                Mth.clamp(edgeSoftness, 0.04F, 0.92F)
        );
    }

    private static Vec3 radialCell(Vec3 origin, SpawnPlan plan, RandomSource random, float min, float max, float yJitter) {
        float angle = (float) (random.nextFloat() * Math.PI * 2.0D);
        float distance = plan.groupRadius() * (min + random.nextFloat() * (max - min));
        return origin.add(Math.cos(angle) * distance, random.nextFloat() * yJitter - yJitter * 0.5F, Math.sin(angle) * distance);
    }

    private static Vec3 towerCell(Vec3 origin, SpawnPlan plan, int clusterIndex, RandomSource random) {
        float angle = (float) (random.nextFloat() * Math.PI * 2.0D);
        float distance = plan.radius() * (0.10F + random.nextFloat() * 0.32F);
        float tier = (float) clusterIndex / Math.max(1.0F, plan.clusterCount() - 1.0F);
        return origin.add(Math.cos(angle) * distance, tier * plan.topRise() * 0.38F, Math.sin(angle) * distance);
    }

    private static Vec3 stormCell(Vec3 origin, SpawnPlan plan, int clusterIndex, RandomSource random) {
        boolean anvil = clusterIndex > plan.clusterCount() / 2;
        float angle = (float) (random.nextFloat() * Math.PI * 2.0D);
        float distance = anvil
                ? plan.groupRadius() * (0.34F + random.nextFloat() * 0.58F)
                : plan.radius() * (0.08F + random.nextFloat() * 0.42F);
        float shear = anvil ? plan.groupRadius() * 0.34F : plan.radius() * 0.10F;
        float y = anvil ? plan.topRise() * (0.30F + random.nextFloat() * 0.20F) : random.nextFloat() * 12.0F;
        return origin.add(Math.cos(angle) * distance + shear, y, Math.sin(angle) * distance);
    }

    private static Vec3 spiralCell(Vec3 origin, SpawnPlan plan, int clusterIndex, RandomSource random) {
        float turn = (float) clusterIndex / Math.max(1.0F, plan.clusterCount() - 1.0F);
        float angle = turn * (float) (Math.PI * 3.6D) + random.nextFloat() * 0.36F;
        float distance = plan.groupRadius() * (0.12F + turn * 0.74F);
        float y = (random.nextFloat() - 0.5F) * plan.topRise() * 0.20F;
        return origin.add(Math.cos(angle) * distance, y, Math.sin(angle) * distance);
    }

    private static Vec3 sheetCell(Vec3 origin, SpawnPlan plan, int clusterIndex, RandomSource random, float gapBias) {
        int signedIndex = clusterIndex - (plan.clusterCount() / 2);
        float along = signedIndex * plan.radius() * (0.72F + gapBias) + (random.nextFloat() - 0.5F) * plan.radius() * 0.82F;
        float across = (random.nextFloat() - 0.5F) * plan.groupRadius() * (0.30F + gapBias * 0.32F);
        float y = (random.nextFloat() - 0.5F) * plan.topRise() * 0.16F;
        return origin.add(along, y, across);
    }

    private static Vec3 filamentCell(Vec3 origin, SpawnPlan plan, int clusterIndex, RandomSource random) {
        int signedIndex = clusterIndex - (plan.clusterCount() / 2);
        float along = signedIndex * plan.radius() * 1.42F + (random.nextFloat() - 0.5F) * plan.radius() * 0.72F;
        float across = (random.nextFloat() - 0.5F) * plan.radius() * 0.55F;
        float y = (random.nextFloat() - 0.5F) * plan.topRise() * 0.12F;
        return origin.add(along, y, across);
    }

    private static float radiusMultiplier(CloudMorphologyFamily family) {
        return switch (family) {
            case PUFF -> 0.92F;
            case TOWER -> 0.78F;
            case STORM_ANVIL, SPIRAL_STORM -> 1.04F;
            case SHEET -> 1.70F;
            case CELLULAR_SHEET -> 1.12F;
            case FILAMENT -> 0.38F;
            case DEBUG -> 1.0F;
        };
    }

    private static float baseMultiplier(CloudMorphologyFamily family) {
        return switch (family) {
            case TOWER -> 0.76F;
            case SHEET, CELLULAR_SHEET -> 0.68F;
            case FILAMENT -> 0.38F;
            default -> 1.0F;
        };
    }

    private static float topMultiplier(CloudMorphologyFamily family) {
        return switch (family) {
            case TOWER -> 1.34F;
            case STORM_ANVIL, SPIRAL_STORM -> 1.18F;
            case SHEET -> 0.70F;
            case CELLULAR_SHEET -> 0.84F;
            case FILAMENT -> 0.42F;
            default -> 1.0F;
        };
    }

    private static float densityFor(CloudMorphologyFamily family, CloudVisualProfile visual) {
        return switch (family) {
            case FILAMENT -> 0.24F + visual.getDensityMultiplier() * 0.12F;
            case SHEET -> 0.50F + visual.getDensityMultiplier() * 0.20F + visual.getPrecipitationCoreStrength() * 0.16F;
            case CELLULAR_SHEET -> 0.44F + visual.getDensityMultiplier() * 0.20F;
            case STORM_ANVIL, SPIRAL_STORM -> 0.54F + visual.getDensityMultiplier() * 0.24F + visual.getPrecipitationCoreStrength() * 0.18F;
            case TOWER -> 0.50F + visual.getDensityMultiplier() * 0.24F;
            default -> 0.42F + visual.getDensityMultiplier() * 0.22F;
        };
    }

    private static float coverageFor(CloudMorphologyFamily family, CloudVisualProfile visual, CloudShapeProfile shape) {
        return switch (family) {
            case FILAMENT -> 0.20F + visual.getCoverageMultiplier() * 0.14F;
            case SHEET -> 0.62F + visual.getCoverageMultiplier() * 0.22F + shape.getBaseFlattening() * 0.10F;
            case CELLULAR_SHEET -> 0.42F + visual.getCoverageMultiplier() * 0.20F;
            case STORM_ANVIL, SPIRAL_STORM -> 0.58F + visual.getCoverageMultiplier() * 0.22F + shape.getAnvilSpread() * 0.10F;
            case TOWER -> 0.46F + visual.getCoverageMultiplier() * 0.22F;
            default -> 0.42F + visual.getCoverageMultiplier() * 0.22F;
        };
    }

    private static float edgeSoftnessFor(CloudMorphologyFamily family, CloudVisualProfile visual, CloudShapeProfile shape) {
        return switch (family) {
            case FILAMENT -> 0.48F + shape.getEdgeRaggedness() * 0.28F;
            case SHEET -> 0.36F + visual.getTopSoftness() * 0.40F;
            case CELLULAR_SHEET -> 0.24F + shape.getEdgeRaggedness() * 0.42F;
            case STORM_ANVIL, SPIRAL_STORM -> 0.18F + visual.getEdgeErosionStrength() * 0.32F + shape.getEdgeRaggedness() * 0.14F;
            case TOWER -> 0.18F + shape.getEdgeRaggedness() * 0.36F;
            default -> 0.22F + visual.getEdgeErosionStrength() * 0.30F + shape.getEdgeRaggedness() * 0.12F;
        };
    }

    record SpawnPlan(
            CloudMorphologyFamily family,
            int clusterCount,
            float radius,
            float groupRadius,
            float baseDrop,
            float topRise,
            float density,
            float coverage,
            float edgeSoftness
    ) {
    }
}
