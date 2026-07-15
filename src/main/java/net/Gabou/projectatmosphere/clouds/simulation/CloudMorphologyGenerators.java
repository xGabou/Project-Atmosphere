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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Family-specific cloud morphology generators.
 */
final class CloudMorphologyGenerators {
    private static final int STRUCTURED_TOWER_COUNT = 12;
    private static final float[] STRUCTURED_TOWER_ANGLES = {
            0.0F, 8.0F, 137.0F, 263.0F,
            68.0F, 196.0F, 319.0F,
            31.0F, 166.0F, 330.0F,
            92.0F, 227.0F
    };
    private static final float[] STRUCTURED_TOWER_RADIAL = {
            0.00F, 0.40F, 0.46F, 0.36F,
            0.38F, 0.30F, 0.44F,
            0.34F, 0.43F, 0.33F,
            0.34F, 0.32F
    };
    private static final float[] STRUCTURED_TOWER_HEIGHT = {
            0.00F, 0.04F, 0.05F, 0.03F,
            0.20F, 0.23F, 0.21F,
            0.38F, 0.42F, 0.39F,
            0.56F, 0.59F
    };
    private static final float[] STRUCTURED_TOWER_RADIUS = {
            0.96F, 0.88F, 0.82F, 0.94F,
            0.92F, 0.78F, 0.90F,
            0.86F, 0.72F, 0.88F,
            0.86F, 0.80F
    };
    private static final float[] STRUCTURED_TOWER_LOWER = {
            1.00F, 1.00F, 1.00F, 1.00F,
            1.02F, 1.00F, 1.02F,
            1.04F, 1.02F, 1.04F,
            1.06F, 1.06F
    };
    private static final float[] STRUCTURED_TOWER_UPPER = {
            0.84F, 0.84F, 0.86F, 0.82F,
            0.98F, 1.00F, 0.96F,
            1.00F, 1.02F, 0.98F,
            1.04F, 1.02F
    };

    private CloudMorphologyGenerators() {
    }

    /** Read-only access to the exact deterministic tower table for diagnostics. */
    static StructuredTowerTopology structuredTowerTopology() {
        return new StructuredTowerTopology(
                immutableValues(STRUCTURED_TOWER_ANGLES),
                immutableValues(STRUCTURED_TOWER_RADIAL),
                immutableValues(STRUCTURED_TOWER_HEIGHT),
                immutableValues(STRUCTURED_TOWER_RADIUS)
        );
    }

    private static List<Float> immutableValues(float[] values) {
        List<Float> copy = new ArrayList<>(values.length);
        for (float value : values) {
            copy.add(value);
        }
        return List.copyOf(copy);
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
        CloudMorphologyFamily family = plan.family();
        if (family != CloudMorphologyFamily.PUFF && family != CloudMorphologyFamily.TOWER) {
            applyToCluster(cluster, definition);
        } else {
            cluster.setMorphologyFamily(family);
        }

        float radiusJitter = family == CloudMorphologyFamily.TOWER
                ? 0.96F + random.nextFloat() * 0.08F
                : 0.92F + random.nextFloat() * 0.16F;
        float tier = family == CloudMorphologyFamily.TOWER
                ? towerTier(clusterIndex, plan.clusterCount())
                : morphologyTier(clusterIndex, plan.clusterCount());
        float lobeScale = family == CloudMorphologyFamily.TOWER
                ? towerRadiusScale(clusterIndex, plan.clusterCount(), tier)
                : 1.0F;
        float stableScale = family == CloudMorphologyFamily.TOWER
                ? Mth.lerp(
                    Mth.clamp((scale - 0.72F) / 0.42F, 0.0F, 1.0F),
                    0.96F,
                    1.04F
                )
                : scale;
        // Start from the plan radius. Secondary clusters were already created
        // with radius*scale; multiplying cluster.getRadius() by scale here
        // applied that scale twice and made upper tower lobes needle-thin.
        float finalRadius = Math.max(6.0F, plan.radius() * stableScale * lobeScale * radiusJitter);
        if (family == CloudMorphologyFamily.FILAMENT) {
            finalRadius = Math.max(5.0F, finalRadius * (0.42F + random.nextFloat() * 0.20F));
        } else if (family == CloudMorphologyFamily.CELLULAR_SHEET) {
            finalRadius = Math.max(10.0F, finalRadius * (0.52F + random.nextFloat() * 0.24F));
        }

        float finalDensity = Mth.clamp(plan.density() * (0.90F + random.nextFloat() * 0.18F), 0.0F, 1.0F);
        float finalCoverage = Mth.clamp(plan.coverage() * (0.88F + random.nextFloat() * 0.20F), 0.0F, 1.0F);
        // Convective silhouettes must be recognizable and stable immediately.
        // Their previous 72% horizontal birth size was paired with full final
        // height, making a young lobe look like a tall extrusion for 30 seconds.
        float birthRadiusScale = family == CloudMorphologyFamily.TOWER
                ? 0.94F
                : family == CloudMorphologyFamily.PUFF ? 0.90F : 0.72F;
        float initialRadius = Math.max(6.0F, finalRadius * birthRadiusScale);
        float initialDensity = Mth.clamp(finalDensity * 0.82F, 0.0F, 1.0F);
        float initialCoverage = Mth.clamp(finalCoverage * 0.82F, 0.0F, 1.0F);

        cluster.setRadius(initialRadius);
        cluster.setSpawnRadius(initialRadius);
        cluster.setGrowthTargets(finalRadius, finalCoverage, finalDensity);
        cluster.setDensity(initialDensity);
        cluster.setCoverage(initialCoverage);
        cluster.setEdgeSoftness(Mth.clamp(plan.edgeSoftness() * (0.88F + random.nextFloat() * 0.24F), 0.02F, 0.95F));

        if (family == CloudMorphologyFamily.PUFF || family == CloudMorphologyFamily.TOWER) {
            applyConvectiveLobeEnvelope(cluster, plan, finalRadius, clusterIndex);
        }
        if (family == CloudMorphologyFamily.STORM_ANVIL && clusterIndex > plan.clusterCount() / 2) {
            cluster.setVerticalBounds(cluster.getBaseY() + plan.baseDrop() * 0.38F, cluster.getTopY() + plan.topRise() * 0.22F);
            float anvilRadius = cluster.getRadius() * 1.24F;
            cluster.setRadius(anvilRadius);
            cluster.setSpawnRadius(anvilRadius);
            cluster.setTargetRadius(Math.max(cluster.getTargetRadius(), finalRadius * 1.24F));
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
        cluster.setSpawnRadius(cluster.getRadius());
        cluster.setGrowthTargets(cluster.getRadius(), cluster.getCoverage(), cluster.getDensity());
    }

    static void retargetCluster(@NotNull CloudClusterState cluster, @NotNull CloudTypeDefinition definition) {
        CloudShapeProfile shape = definition.getShapeProfile();
        CloudVisualProfile visual = definition.getVisualProfile();
        CloudMorphologyFamily family = definition.getMorphologyFamily();

        double centerY = cluster.getCenter().y();
        float existingRadius = Math.max(1.0F, cluster.getRadius());
        float mergeBoost = 1.0F + cluster.getMergePressure() * 0.18F;
        int morphologyIndex = cluster.getMorphologyIndex();
        int morphologyCount = cluster.getMorphologyCount();
        float tier = family == CloudMorphologyFamily.TOWER
                ? towerTier(morphologyIndex, morphologyCount)
                : morphologyTier(morphologyIndex, morphologyCount);
        float lobeScale = family == CloudMorphologyFamily.TOWER
                ? towerRadiusScale(morphologyIndex, morphologyCount, tier)
                : 1.0F;
        float targetRadius = Math.max(shape.getBaseRadius() * radiusMultiplier(family) * lobeScale, existingRadius * mergeBoost);
        float targetBaseY = (float) centerY - shape.getBaseOffset() * baseMultiplier(family);
        float targetTopY = (float) centerY + shape.getTopOffset() * topMultiplier(family) + cluster.getMergePressure() * 12.0F;

        cluster.setMorphologyFamily(family);
        if (family == CloudMorphologyFamily.PUFF || family == CloudMorphologyFamily.TOWER) {
            boolean structuredTower = family == CloudMorphologyFamily.TOWER
                    && morphologyCount == STRUCTURED_TOWER_COUNT;
            float lowerExtent = structuredTower
                    ? targetRadius * STRUCTURED_TOWER_LOWER[morphologyIndex]
                    : family == CloudMorphologyFamily.TOWER && morphologyIndex > 0
                    ? targetRadius * Mth.lerp(tier, 0.68F, 0.56F)
                    : Math.min(shape.getBaseOffset() * baseMultiplier(family), targetRadius * 0.48F);
            float upperExtent = structuredTower
                    ? targetRadius * STRUCTURED_TOWER_UPPER[morphologyIndex]
                    : family == CloudMorphologyFamily.TOWER
                    ? targetRadius * Mth.lerp(tier, 1.04F, 0.90F)
                        + shape.getTopOffset() * topMultiplier(family) * tier * 0.10F
                    : targetRadius * 0.84F;
            float localBaseY = (float) centerY - lowerExtent;
            if (family == CloudMorphologyFamily.TOWER) {
                float groupOriginY = (float) centerY
                        - tier * shape.getTopOffset() * topMultiplier(family)
                        * (structuredTower ? 1.0F : 0.60F);
                float groupBaseY = groupOriginY - shape.getBaseOffset() * 0.78F;
                int shoulderCount = Math.min(3, Math.max(1, morphologyCount - 1));
                // The primary and the three broad shoulders share one
                // condensation level. Retargeting used to raise only the
                // primary after spawn, carving the dark arch seen from the
                // side even though the freshly generated envelope was flat.
                localBaseY = morphologyIndex >= 0 && morphologyIndex <= shoulderCount
                        ? groupBaseY
                        : Math.max(groupBaseY, localBaseY);
            }
            cluster.setVerticalBounds(localBaseY, (float) centerY + upperExtent);
        } else {
            cluster.setVerticalBounds(
                    Math.min(cluster.getBaseY(), targetBaseY),
                    Math.max(cluster.getTopY(), targetTopY)
            );
        }
        cluster.setGrowthTargets(
                Math.max(existingRadius, targetRadius),
                Mth.clamp(coverageFor(family, visual, shape), 0.0F, 1.0F),
                Mth.clamp(densityFor(family, visual), 0.0F, 1.0F)
        );
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
        // Fixed four-stage topology: four base lobes, three core lobes, three
        // towers and two laterally separated crowns. No single primitive owns
        // the apex, so the source geometry cannot collapse into a needle.
        return plan(definition, random, STRUCTURED_TOWER_COUNT, STRUCTURED_TOWER_COUNT,
                radius, radius * 1.18F,
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
                Mth.clamp(edgeSoftness, 0.04F, 0.92F),
                random.nextFloat() * (float) (Math.PI * 2.0D)
        );
    }

    private static Vec3 radialCell(Vec3 origin, SpawnPlan plan, RandomSource random, float min, float max, float yJitter) {
        float angle = (float) (random.nextFloat() * Math.PI * 2.0D);
        float distance = plan.groupRadius() * (min + random.nextFloat() * (max - min));
        return origin.add(Math.cos(angle) * distance, random.nextFloat() * yJitter - yJitter * 0.5F, Math.sin(angle) * distance);
    }

    private static Vec3 towerCell(Vec3 origin, SpawnPlan plan, int clusterIndex, RandomSource random) {
        if (plan.clusterCount() == STRUCTURED_TOWER_COUNT) {
            int index = Mth.clamp(clusterIndex, 0, STRUCTURED_TOWER_COUNT - 1);
            float angle = plan.orientationRadians()
                    + (float) Math.toRadians(STRUCTURED_TOWER_ANGLES[index])
                    + (random.nextFloat() - 0.5F) * 0.08F;
            float distance = plan.radius() * Mth.clamp(
                    STRUCTURED_TOWER_RADIAL[index]
                            + (random.nextFloat() - 0.5F) * 0.03F,
                    0.0F,
                    0.60F
            );
            float height = plan.topRise() * Mth.clamp(
                    STRUCTURED_TOWER_HEIGHT[index]
                            + (random.nextFloat() - 0.5F) * 0.02F,
                    0.0F,
                    0.78F
            );
            return origin.add(Math.cos(angle) * distance, height, Math.sin(angle) * distance);
        }

        int siblingCount = Math.max(1, plan.clusterCount() - 1);
        int shoulderCount = Math.min(3, siblingCount);
        boolean shoulder = clusterIndex <= shoulderCount;
        float tier = towerTier(clusterIndex, plan.clusterCount());
        float angle;
        float radialScale;
        if (shoulder) {
            float shoulderT = (float) (clusterIndex - 1)
                    / (float) Math.max(1, shoulderCount - 1);
            // Three broad, similarly high shoulders establish the compact
            // condensation base. A monotonic golden-angle helix gave the
            // whole cloud one diagonal rock-like ridge instead.
            angle = (clusterIndex - 1)
                    * ((float) (Math.PI * 2.0D) / shoulderCount)
                    + (random.nextFloat() - 0.5F) * 0.18F;
            radialScale = Mth.lerp(shoulderT, 0.52F, 0.44F);
        } else {
            int upperIndex = clusterIndex - shoulderCount - 1;
            float upperT = Mth.clamp((tier - 0.46F) / 0.36F, 0.0F, 1.0F);
            // Paired crown lobes form successive cauliflower tiers. Their
            // footprint converges toward the updraft without becoming a
            // single central needle.
            angle = upperIndex * 2.399963F + 0.82F
                    + (random.nextFloat() - 0.5F) * 0.20F;
            radialScale = Mth.lerp(upperT, 0.30F, 0.08F);
        }
        float distance = plan.radius() * radialScale
                * (0.90F + random.nextFloat() * 0.20F);
        // Broad, overlapping cauliflower lobes climb through the column. Their
        // local bounds are assigned separately; this Y is the actual lobe
        // centre and is no longer discarded by the renderer projection.
        return origin.add(Math.cos(angle) * distance, tier * plan.topRise() * 0.60F, Math.sin(angle) * distance);
    }

    private static void applyConvectiveLobeEnvelope(
            CloudClusterState cluster,
            SpawnPlan plan,
            float finalRadius,
            int clusterIndex
    ) {
        float tier = plan.family() == CloudMorphologyFamily.TOWER
                ? towerTier(clusterIndex, plan.clusterCount())
                : morphologyTier(clusterIndex, plan.clusterCount());
        float centerY = (float) cluster.getCenter().y();
        if (plan.family() == CloudMorphologyFamily.PUFF) {
            float lowerExtent = Math.min(plan.baseDrop(), finalRadius * 0.46F);
            float upperExtent = finalRadius * Mth.lerp(tier, 0.86F, 0.72F);
            cluster.setVerticalBounds(centerY - lowerExtent, centerY + upperExtent);
            return;
        }

        // Reconstruct the group's condensation level from the deterministic
        // tier lift, then prevent higher lobes from protruding below it. The
        // primary owns the coherent flat base; progressively smaller lobes
        // overlap it and build the rounded tower above.
        boolean structuredTower = plan.family() == CloudMorphologyFamily.TOWER
                && plan.clusterCount() == STRUCTURED_TOWER_COUNT;
        float groupOriginY = centerY - tier * plan.topRise()
                * (structuredTower ? 1.0F : 0.60F);
        float groupBaseY = groupOriginY - plan.baseDrop();
        float lowerExtent = structuredTower
                ? finalRadius * STRUCTURED_TOWER_LOWER[clusterIndex]
                : clusterIndex == 0
                ? plan.baseDrop()
                : finalRadius * Mth.lerp(tier, 0.68F, 0.56F);
        // Smaller upper-tier lobes still need successively higher supported
        // crowns. The old shrinking multiplier nearly cancelled the centre-Y
        // rise, leaving every tier at the same top and rebuilding one dome.
        float upperExtent = structuredTower
                ? finalRadius * STRUCTURED_TOWER_UPPER[clusterIndex]
                : finalRadius * Mth.lerp(tier, 1.04F, 0.90F)
                    + plan.topRise() * tier * 0.10F;
        int shoulderCount = Math.min(3, Math.max(1, plan.clusterCount() - 1));
        float localBaseY = clusterIndex > 0 && clusterIndex <= shoulderCount
                ? groupBaseY
                : Math.max(groupBaseY, centerY - lowerExtent);
        float localTopY = centerY + upperExtent;
        cluster.setVerticalBounds(localBaseY, Math.max(localBaseY + 4.0F, localTopY));
    }

    private static float morphologyTier(int clusterIndex, int clusterCount) {
        return Mth.clamp(
                (float) Math.max(0, clusterIndex) / (float) Math.max(1, clusterCount - 1),
                0.0F,
                1.0F
        );
    }

    private static float towerTier(int clusterIndex, int clusterCount) {
        if (clusterCount == STRUCTURED_TOWER_COUNT) {
            return STRUCTURED_TOWER_HEIGHT[Mth.clamp(
                    clusterIndex,
                    0,
                    STRUCTURED_TOWER_COUNT - 1
            )];
        }
        if (clusterIndex <= 0) {
            return 0.0F;
        }
        int siblingCount = Math.max(1, clusterCount - 1);
        int shoulderCount = Math.min(3, siblingCount);
        if (clusterIndex <= shoulderCount) {
            float shoulderT = (float) (clusterIndex - 1)
                    / (float) Math.max(1, shoulderCount - 1);
            return Mth.lerp(shoulderT, 0.18F, 0.30F);
        }

        int upperIndex = clusterIndex - shoulderCount - 1;
        int upperCount = Math.max(1, siblingCount - shoulderCount);
        int bandCount = Math.max(1, (upperCount + 1) / 2);
        int band = upperIndex / 2;
        float bandT = (float) band / (float) Math.max(1, bandCount - 1);
        float pairedOffset = (upperIndex & 1) == 0 ? 0.0F : 0.025F;
        return Mth.clamp(Mth.lerp(bandT, 0.46F, 0.78F) + pairedOffset, 0.0F, 0.82F);
    }

    private static float towerRadiusScale(int clusterIndex, int clusterCount, float tier) {
        if (clusterCount == STRUCTURED_TOWER_COUNT) {
            return STRUCTURED_TOWER_RADIUS[Mth.clamp(
                    clusterIndex,
                    0,
                    STRUCTURED_TOWER_COUNT - 1
            )];
        }
        return clusterIndex == 0 ? 1.0F : Mth.lerp(tier, 0.82F, 0.44F);
    }

    record StructuredTowerTopology(
            List<Float> angles,
            List<Float> radial,
            List<Float> heights,
            List<Float> radii
    ) {
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
            float edgeSoftness,
            float orientationRadians
    ) {
    }
}
