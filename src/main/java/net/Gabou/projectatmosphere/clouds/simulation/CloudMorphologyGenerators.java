package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.field.CloudMorphologyMembership;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyMemberTier;
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
    private static final float PUFF_GROUP_RADIUS_MULTIPLIER = 1.65F;
    private static final float PUFF_RADIAL_MIN = 0.30F;
    private static final float PUFF_RADIAL_MAX = 0.64F;
    private static final float PUFF_ANGULAR_JITTER_RADIANS = 0.28F;
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
    // T134 severe-system physical scale. These are source-plan dimensions, not
    // a uniform post-generation descriptor multiplier. The individual roles
    // retain their existing BASE -> CORE -> TOWER -> ANVIL topology while the
    // system occupies the derived 1,200--1,500 block horizontal envelope and
    // 720--880 block vertical envelope.
    private static final int STORM_SYSTEM_MEMBER_COUNT = 10;
    private static final float STORM_SYSTEM_PLAN_RADIUS = 450.0F;
    private static final float STORM_SYSTEM_GROUP_RADIUS = 400.0F;
    private static final float STORM_SYSTEM_BASE_DROP = 120.0F;
    private static final float STORM_SYSTEM_TOP_RISE = 780.0F;
    private static final float STORM_STABLE_SCALE_MIN = 0.97F;
    private static final float STORM_STABLE_SCALE_MAX = 1.03F;
    private static final float STORM_RADIUS_JITTER_MIN = 0.98F;
    private static final float STORM_RADIUS_JITTER_MAX = 1.02F;
    // T134: the original downstream anvil endpoint (0.50) left a freshly
    // spawned, centre-relative descriptor envelope at 1,198.009 blocks in
    // the controlled suite.  Extending only the downwind source placement to
    // 0.52 contributes a deterministic outer-envelope margin without
    // changing any lobe radius, density/noise equation, or vertical profile.
    private static final float STORM_ANVIL_UPWIND_POSITION = -0.20F;
    private static final float STORM_ANVIL_DOWNWIND_POSITION = 0.52F;

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

    /** Read-only access to the deterministic PUFF placement contract. */
    static PuffTopologyParameters puffTopologyParameters() {
        return new PuffTopologyParameters(
                PUFF_GROUP_RADIUS_MULTIPLIER,
                PUFF_RADIAL_MIN,
                PUFF_RADIAL_MAX,
                PUFF_ANGULAR_JITTER_RADIANS
        );
    }

    /** Source-plan contract for T134 severe systems, exposed only to diagnostics. */
    static StormPhysicalScale stormPhysicalScale() {
        return new StormPhysicalScale(
                STORM_SYSTEM_MEMBER_COUNT,
                STORM_SYSTEM_PLAN_RADIUS,
                STORM_SYSTEM_GROUP_RADIUS,
                STORM_SYSTEM_BASE_DROP,
                STORM_SYSTEM_TOP_RISE
        );
    }

    static float stormMatureRadiusLowerBound() {
        return STORM_STABLE_SCALE_MIN * STORM_RADIUS_JITTER_MIN;
    }

    static float stormMatureRadiusUpperBound() {
        return STORM_STABLE_SCALE_MAX * STORM_RADIUS_JITTER_MAX;
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
            case PUFF -> puffCell(origin, plan, clusterIndex, random);
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
        if (family == CloudMorphologyFamily.PUFF && plan.hasHierarchicalPuff()) {
            tuneHierarchicalPuff(cluster, definition, plan, clusterIndex, random);
            return;
        }
        if (family != CloudMorphologyFamily.PUFF && family != CloudMorphologyFamily.TOWER) {
            applyToCluster(cluster, definition);
        } else {
            cluster.setMorphologyFamily(family);
        }

        float radiusJitter = family == CloudMorphologyFamily.TOWER
                ? 0.96F + random.nextFloat() * 0.08F
                : family == CloudMorphologyFamily.STORM_ANVIL
                ? STORM_RADIUS_JITTER_MIN
                        + random.nextFloat() * (STORM_RADIUS_JITTER_MAX - STORM_RADIUS_JITTER_MIN)
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
                : family == CloudMorphologyFamily.STORM_ANVIL
                ? Mth.lerp(
                    Mth.clamp((scale - 0.72F) / 0.42F, 0.0F, 1.0F),
                    STORM_STABLE_SCALE_MIN,
                    STORM_STABLE_SCALE_MAX
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
        if (family == CloudMorphologyFamily.STORM_ANVIL) {
            applyStormLobeEnvelope(cluster, plan, finalRadius, clusterIndex);
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

        if ("cumulus_mediocris".equals(definition.getId())
                && "cumulus_humilis".equals(cluster.getPreviousCloudTypeId())
                && cluster.getMorphologyCount() > 1) {
            retargetPuffPreservingGeometry(cluster, definition);
            return;
        }

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
        if (family == CloudMorphologyFamily.STORM_ANVIL && morphologyCount >= 4) {
            SpawnPlan stormPlan = stormAnvilPlanForCount(definition, morphologyCount);
            StormLobeSpec storm = stormLobeSpec(stormPlan, morphologyIndex, (float) centerY);
            float roleTargetRadius = Math.max(
                    8.0F,
                    stormPlan.radius() * storm.radiusMultiplier()
            );
            cluster.setVerticalBounds(storm.baseY(), storm.topY());
            cluster.setGrowthTargets(
                    Math.max(existingRadius, roleTargetRadius),
                    Mth.clamp(coverageFor(family, visual, shape), 0.0F, 1.0F),
                    Mth.clamp(densityFor(family, visual), 0.0F, 1.0F)
            );
            return;
        }
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
        boolean humilis = "cumulus_humilis".equals(id);
        boolean mediocris = "cumulus_mediocris".equals(id);
        // Fair-weather cumulus needs a real hierarchy, not a ring of four
        // almost full-size cushions. Keep the count deterministic so the
        // persisted membership layout and render descriptors cannot change
        // shape when the same field is rebuilt.
        int minClusters = id.contains("vapor") ? 2 : humilis ? 7 : mediocris ? 8 : 4;
        int maxClusters = id.contains("vapor") ? 4 : humilis ? 7 : mediocris ? 8 : 7;
        float radius = shape.getBaseRadius() * (mediocris ? 0.98F : 0.82F);
        SpawnPlan basePlan = plan(definition, random, minClusters, maxClusters, radius,
                radius * PUFF_GROUP_RADIUS_MULTIPLIER,
                shape.getBaseOffset() * 0.95F, shape.getTopOffset(),
                0.38F + visual.getDensityMultiplier() * 0.25F,
                0.42F + visual.getCoverageMultiplier() * 0.24F,
                0.24F + visual.getEdgeErosionStrength() * 0.42F);
        if (!humilis && !mediocris) {
            return basePlan;
        }
        return basePlan.withPuffLobes(createHierarchicalPuffLobes(basePlan, mediocris, random));
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
        return stormSystemPlan(
                definition,
                STORM_SYSTEM_MEMBER_COUNT,
                random.nextFloat() * (float) (Math.PI * 2.0D)
        );
    }

    static SpawnPlan stormAnvilPlanForCount(
            @NotNull CloudTypeDefinition definition,
            int clusterCount
    ) {
        return stormSystemPlan(definition, Math.max(4, clusterCount), 0.0F);
    }

    private static SpawnPlan stormSystemPlan(
            @NotNull CloudTypeDefinition definition,
            int clusterCount,
            float orientation
    ) {
        CloudVisualProfile visual = definition.getVisualProfile();
        return new SpawnPlan(
                CloudMorphologyFamily.STORM_ANVIL,
                clusterCount,
                STORM_SYSTEM_PLAN_RADIUS,
                STORM_SYSTEM_GROUP_RADIUS,
                STORM_SYSTEM_BASE_DROP,
                STORM_SYSTEM_TOP_RISE,
                0.58F + visual.getDensityMultiplier() * 0.25F
                        + visual.getPrecipitationCoreStrength() * 0.12F,
                0.58F + visual.getCoverageMultiplier() * 0.24F,
                0.18F + visual.getEdgeErosionStrength() * 0.42F,
                orientation
        );
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

    /**
     * Places lateral PUFF siblings on a stable open ring around the canonical
     * primary. Independent random angles allowed lobes to collect on one side
     * or separate completely; a count-1 ring made the common three-member case
     * collinear. Reserving one of count angular slots keeps the footprint
     * asymmetric but bounded, while every secondary retains a measured overlap
     * with the primary at its smallest birth radius.
     */
    private static Vec3 puffCell(
            Vec3 origin,
            SpawnPlan plan,
            int clusterIndex,
            RandomSource random
    ) {
        PuffLobeSpec spec = plan.puffLobe(clusterIndex);
        if (spec != null) {
            return origin.add(spec.offsetX(), spec.offsetY(), spec.offsetZ());
        }
        int slot = Mth.clamp(clusterIndex, 1, Math.max(1, plan.clusterCount() - 1));
        float angularSample = random.nextFloat();
        float angle = plan.orientationRadians()
                + slot * ((float) (Math.PI * 2.0D) / Math.max(1, plan.clusterCount()))
                + (angularSample - 0.5F) * PUFF_ANGULAR_JITTER_RADIANS;
        float distance = plan.groupRadius()
                * (PUFF_RADIAL_MIN + random.nextFloat() * (PUFF_RADIAL_MAX - PUFF_RADIAL_MIN));
        return origin.add(
                Math.cos(angle) * distance,
                random.nextFloat() * 7.0F - 3.5F,
                Math.sin(angle) * distance
        );
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

    /**
     * Gives the authoritative storm members distinct, overlapping vertical
     * jobs. Previously every lower member inherited nearly the complete storm
     * slab, so activating the role maps exposed several disconnected pointed
     * masses instead of a base feeding a convective tower and anvil.
     */
    private static void applyStormLobeEnvelope(
            CloudClusterState cluster,
            SpawnPlan plan,
            float finalRadius,
            int clusterIndex
    ) {
        float centerY = (float) cluster.getCenter().y();
        StormLobeSpec spec = stormLobeSpec(plan, clusterIndex, centerY);
        float targetRadius = Math.max(8.0F, finalRadius * spec.radiusMultiplier());
        float birthRadius = Math.max(8.0F, targetRadius * 0.88F);
        cluster.setRadius(birthRadius);
        cluster.setSpawnRadius(birthRadius);
        cluster.setTargetRadius(targetRadius);
        cluster.setVerticalBounds(spec.baseY(), spec.topY());
    }

    static StormLobeSpec stormLobeSpec(SpawnPlan plan, int clusterIndex, float centerY) {
        CloudMorphologyMembership.Stage stage = new CloudMorphologyMembership(
                null,
                clusterIndex,
                plan.clusterCount()
        ).stageFor(CloudMorphologyFamily.STORM_ANVIL);
        float radiusMultiplier;
        float baseY;
        float topY;
        int anvilStart = plan.clusterCount() / 2 + 1;
        int lowerCount = anvilStart;
        int baseCount = Math.max(1, lowerCount / 3);
        int coreCount = Math.max(1, (lowerCount - baseCount) / 2);
        int towerStart = baseCount + coreCount;
        switch (stage) {
            case BASE -> {
                radiusMultiplier = 1.16F;
                baseY = centerY - plan.baseDrop();
                topY = centerY + plan.topRise() * 0.42F;
            }
            case CORE -> {
                int ordinal = Math.max(0, clusterIndex - baseCount);
                float progress = coreCount <= 1
                        ? 0.0F
                        : (float) ordinal / (float) (coreCount - 1);
                radiusMultiplier = Mth.lerp(progress, 0.56F, 0.52F);
                baseY = centerY - plan.topRise() * 0.06F;
                topY = centerY + plan.topRise() * Mth.lerp(progress, 0.30F, 0.32F);
            }
            case TOWER -> {
                int towerCount = Math.max(1, anvilStart - towerStart);
                int ordinal = Math.max(0, clusterIndex - towerStart);
                float progress = towerCount <= 1
                        ? 0.0F
                        : (float) ordinal / (float) (towerCount - 1);
                radiusMultiplier = Mth.lerp(progress, 0.35F, 0.24F);
                baseY = centerY - plan.topRise() * 0.13F;
                topY = centerY + plan.topRise() * Mth.lerp(progress, 0.34F, 0.30F);
            }
            case ANVIL -> {
                // This allowance includes the existing 0.97--1.03 mature
                // scale and 0.98--1.02 descriptor jitter. It preserves the
                // T127 minimum system footprint even at their lower bound.
                radiusMultiplier = 1.10F;
                baseY = centerY - plan.topRise() * 0.12F;
                topY = centerY + plan.topRise() * 0.15F;
            }
            default -> throw new IllegalStateException("Unexpected storm lobe stage " + stage);
        }
        return new StormLobeSpec(stage, radiusMultiplier, baseY, Math.max(baseY + 8.0F, topY));
    }

    private static void tuneHierarchicalPuff(
            CloudClusterState cluster,
            CloudTypeDefinition definition,
            SpawnPlan plan,
            int clusterIndex,
            RandomSource random
    ) {
        PuffLobeSpec spec = plan.puffLobe(clusterIndex);
        if (spec == null) {
            throw new IllegalStateException(
                    "Hierarchical PUFF plan is missing lobe " + clusterIndex
                            + " of " + plan.clusterCount()
            );
        }

        float finalDensity = Mth.clamp(
                plan.density() * (0.90F + random.nextFloat() * 0.18F),
                0.0F,
                1.0F
        );
        float finalCoverage = Mth.clamp(
                plan.coverage() * (0.88F + random.nextFloat() * 0.20F),
                0.0F,
                1.0F
        );
        // All structured members start at the same near-mature scale. With
        // the renderer's conservative 0.96*0.90 minor radius this keeps every
        // anchor/shoulder edge connected at birth instead of popping together
        // only after growth.
        float initialRadius = Math.max(6.0F, spec.targetRadius() * 0.96F);

        cluster.setMorphologyFamily(CloudMorphologyFamily.PUFF);
        cluster.setMorphologyLayout(
                CloudClusterState.HIERARCHICAL_PUFF_LAYOUT_VERSION,
                spec.tier()
        );
        cluster.setRadius(initialRadius);
        cluster.setSpawnRadius(initialRadius);
        cluster.setGrowthTargets(
                spec.targetRadius(),
                finalCoverage,
                finalDensity
        );
        cluster.setDensity(Mth.clamp(finalDensity * 0.82F, 0.0F, 1.0F));
        cluster.setCoverage(Mth.clamp(finalCoverage * 0.82F, 0.0F, 1.0F));
        cluster.setEdgeSoftness(Mth.clamp(
                plan.edgeSoftness() * (0.88F + random.nextFloat() * 0.24F),
                0.02F,
                0.95F
        ));

        float groupOriginY = (float) cluster.getCenter().y() - spec.offsetY();
        cluster.setVerticalBounds(
                groupOriginY + spec.baseOffsetY(),
                groupOriginY + spec.topOffsetY()
        );
    }

    /**
     * PUFF membership indices predate hierarchical layouts and are persisted
     * without a layout version. Preserve the actual stored geometry instead
     * of reinterpreting an old index as a new base/crown role.
     */
    private static void retargetPuffPreservingGeometry(
            CloudClusterState cluster,
            CloudTypeDefinition definition
    ) {
        CloudShapeProfile shape = definition.getShapeProfile();
        CloudVisualProfile visual = definition.getVisualProfile();

        cluster.setMorphologyFamily(CloudMorphologyFamily.PUFF);
        cluster.setGrowthTargets(
                preservedPuffTargetRadius(cluster.getRadius(), cluster.getTargetRadius()),
                Mth.clamp(coverageFor(CloudMorphologyFamily.PUFF, visual, shape), 0.0F, 1.0F),
                Mth.clamp(densityFor(CloudMorphologyFamily.PUFF, visual), 0.0F, 1.0F)
        );
        // Centre and vertical bounds are already persisted authoritative lobe
        // geometry. Keeping them untouched preserves both versioned upper
        // tiers and legacy lateral groups; radius/media evolve without a role
        // guess. Versioned membership persists across this transition.
    }

    /**
     * Keeps an established PUFF lobe's individual radius target when its type
     * evolves. This decision is intentionally independent from membership
     * indices because legacy saves do not carry a morphology layout version.
     */
    static float preservedPuffTargetRadius(float currentRadius, float targetRadius) {
        return Math.max(currentRadius, targetRadius);
    }

    private static List<PuffLobeSpec> createHierarchicalPuffLobes(
            SpawnPlan plan,
            boolean mediocris,
            RandomSource random
    ) {
        // Four compact, vertically credible base billows establish the flat
        // condensation level. Two parent-relative middle billows and one/two
        // crowns then form the cauliflower silhouette. Every range below is
        // constrained against the renderer's 0.96 horizontal radius: no base
        // primitive is allowed to become the wide, shallow pancake that caused
        // the rejected pear/shelf capture.
        int upperCount = mediocris ? 4 : 3;
        int baseCount = plan.clusterCount() - upperCount;
        if (baseCount != 4) {
            throw new IllegalStateException(
                    "Hierarchical PUFF requires exactly four base lobes: " + plan.clusterCount()
            );
        }

        float radius = plan.radius();
        float condensationBase = -plan.baseDrop();
        List<PuffLobeSpec> lobes = new ArrayList<>(plan.clusterCount());
        float anchorRadius = radius * randomRange(
                random,
                mediocris ? 0.44F : 0.42F,
                mediocris ? 0.50F : 0.46F
        );
        float anchorHeight = radius * randomRange(
                random,
                mediocris ? 0.72F : 0.62F,
                mediocris ? 0.84F : 0.72F
        );
        lobes.add(new PuffLobeSpec(
                0.0F,
                0.0F,
                0.0F,
                anchorRadius,
                condensationBase,
                condensationBase + anchorHeight,
                CloudMorphologyMemberTier.BASE
        ));

        int shoulderCount = baseCount - 1;
        for (int shoulder = 0; shoulder < shoulderCount; shoulder++) {
            float shoulderRadius = radius * randomRange(
                    random,
                    mediocris ? 0.40F : 0.36F,
                    mediocris ? 0.46F : 0.42F
            );
            float angle = plan.orientationRadians()
                    + shoulder * ((float) (Math.PI * 2.0D) / shoulderCount)
                    + randomRange(random, -0.06F, 0.06F);
            // This distance is measured against the actual lobe radii. The
            // previous 0.54..0.60 range could connect at birth only by
            // reopening the rejected 0.72-radius shelf. At 0.40..0.44 the
            // compact BASE roots overlap even on the rendered minor axis.
            float distance = randomRange(random, 0.40F, 0.44F)
                    * (anchorRadius + shoulderRadius);
            float shoulderHeight = radius * randomRange(
                    random,
                    mediocris ? 0.64F : 0.56F,
                    mediocris ? 0.78F : 0.66F
            );
            // A sub-two-percent stagger preserves a coherent meteorological
            // base without synchronising four analytic fade boundaries.
            float shoulderBase = condensationBase
                    + radius * randomRange(random, 0.0F, 0.018F);
            lobes.add(new PuffLobeSpec(
                    (float) Math.cos(angle) * distance,
                    0.0F,
                    (float) Math.sin(angle) * distance,
                    shoulderRadius,
                    shoulderBase,
                    shoulderBase + shoulderHeight,
                    CloudMorphologyMemberTier.BASE
            ));
        }

        int firstSupport = random.nextInt(shoulderCount);
        float middleTangentSign = random.nextBoolean() ? 1.0F : -1.0F;
        List<PuffLobeSpec> middleLobes = new ArrayList<>(2);
        for (int middle = 0; middle < 2; middle++) {
            int supportIndex = 1 + ((firstSupport + middle) % shoulderCount);
            PuffLobeSpec middleLobe = createUpperPuffLobe(
                    lobes.get(supportIndex),
                    condensationBase,
                    radius,
                    random,
                    0.26F,
                    0.30F,
                    mediocris ? 0.36F : 0.32F,
                    mediocris ? 0.42F : 0.38F,
                    mediocris ? 0.24F : 0.22F,
                    mediocris ? 0.32F : 0.30F,
                    mediocris ? 0.72F : 0.58F,
                    mediocris ? 0.86F : 0.70F,
                    0.60F,
                    CloudMorphologyMemberTier.MIDDLE,
                    middleTangentSign
            );
            middleLobes.add(middleLobe);
            lobes.add(middleLobe);
        }

        int crownCount = upperCount - middleLobes.size();
        float crownTangentSign = random.nextBoolean() ? 1.0F : -1.0F;
        for (int crown = 0; crown < crownCount; crown++) {
            PuffLobeSpec crownSupport = middleLobes.get(crown % middleLobes.size());
            lobes.add(createUpperPuffLobe(
                    crownSupport,
                    condensationBase,
                    radius,
                    random,
                    0.32F,
                    0.37F,
                    mediocris ? 0.30F : 0.26F,
                    mediocris ? 0.36F : 0.32F,
                    mediocris ? 0.56F : 0.45F,
                    mediocris ? 0.68F : 0.53F,
                    mediocris ? 0.62F : 0.48F,
                    mediocris ? 0.76F : 0.58F,
                    0.55F,
                    CloudMorphologyMemberTier.CROWN,
                    crownTangentSign
            ));
        }
        return List.copyOf(lobes);
    }

    private static PuffLobeSpec createUpperPuffLobe(
            PuffLobeSpec support,
            float condensationBase,
            float nominalRadius,
            RandomSource random,
            float minimumSeparationScale,
            float maximumSeparationScale,
            float minimumRadiusScale,
            float maximumRadiusScale,
            float minimumBaseLift,
            float maximumBaseLift,
            float minimumHeight,
            float maximumHeight,
            float tangentRatioScale,
            CloudMorphologyMemberTier tier,
            float tangentSign
    ) {
        float supportLength = Math.max(
                0.001F,
                (float) Math.sqrt(support.offsetX() * support.offsetX()
                        + support.offsetZ() * support.offsetZ())
        );
        float radialX = support.offsetX() / supportLength;
        float radialZ = support.offsetZ() / supportLength;
        float tangentX = -radialZ;
        float tangentZ = radialX;
        float childRadius = nominalRadius
                * randomRange(random, minimumRadiusScale, maximumRadiusScale);
        float tangentRatio = tangentSign * randomRange(
                random,
                tangentRatioScale * 0.55F,
                tangentRatioScale
        );
        // Keep the fair-weather footprint compact: separation is used mostly
        // around the parent's tangent, not as a second radial expansion of the
        // whole meteorological field.
        float radialWeight = tier == CloudMorphologyMemberTier.MIDDLE ? 0.14F : 0.10F;
        float directionLength = (float) Math.sqrt(
                radialWeight * radialWeight + tangentRatio * tangentRatio
        );
        float directionX = (radialX * radialWeight + tangentX * tangentRatio)
                / directionLength;
        float directionZ = (radialZ * radialWeight + tangentZ * tangentRatio)
                / directionLength;
        // The previous nominal-radius shifts placed upper centres only 2.9..4.0
        // blocks from their parents at runtime. Their support remained almost
        // completely contained until the parent cap narrowed, so seven valid
        // descriptors collapsed into three stacked mushrooms. Separation now
        // scales with both actual lobe radii: the .26..37 ranges expose a real
        // cauliflower shoulder while the analytic .15-isosurface retains a
        // multi-block bridge across every generated layout.
        float separation = (support.targetRadius() + childRadius)
                * randomRange(random, minimumSeparationScale, maximumSeparationScale);
        float x = support.offsetX() + directionX * separation;
        float z = support.offsetZ() + directionZ * separation;
        float localBase = condensationBase
                + nominalRadius * randomRange(random, minimumBaseLift, maximumBaseLift);
        float localTop = localBase
                + nominalRadius * randomRange(random, minimumHeight, maximumHeight);
        if (tier == CloudMorphologyMemberTier.CROWN) {
            // The root is closed by the renderer's structured implicit field.
            // It must therefore begin far enough inside its authored support
            // for the parent to remain material until the child opens. The old
            // condensation-relative base produced two proven mediocris layouts
            // where one crown had zero carrier corridor to every middle lobe.
            // Preserve the sampled crown top and radius; only deepen an
            // unsupported root, without consuming another random value.
            float supportedBaseCeiling = support.topOffsetY()
                    - nominalRadius * 0.38F;
            localBase = Math.min(localBase, supportedBaseCeiling);
        }
        return new PuffLobeSpec(
                x,
                (localBase + localTop) * 0.5F,
                z,
                childRadius,
                localBase,
                localTop,
                tier
        );
    }

    private static float randomRange(RandomSource random, float minimum, float maximum) {
        return Mth.lerp(random.nextFloat(), minimum, maximum);
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

    record PuffTopologyParameters(
            float groupRadiusMultiplier,
            float radialMinimum,
            float radialMaximum,
            float angularJitterRadians
    ) {
    }

    record PuffLobeSpec(
            float offsetX,
            float offsetY,
            float offsetZ,
            float targetRadius,
            float baseOffsetY,
            float topOffsetY,
            CloudMorphologyMemberTier tier
    ) {
        PuffLobeSpec {
            if (!Float.isFinite(offsetX) || !Float.isFinite(offsetY) || !Float.isFinite(offsetZ)
                    || !Float.isFinite(targetRadius) || targetRadius <= 0.0F
                    || !Float.isFinite(baseOffsetY) || !Float.isFinite(topOffsetY)
                    || topOffsetY <= baseOffsetY || tier == null) {
                throw new IllegalArgumentException("Invalid PUFF lobe specification");
            }
        }
    }

    record StormLobeSpec(
            CloudMorphologyMembership.Stage stage,
            float radiusMultiplier,
            float baseY,
            float topY
    ) {
        StormLobeSpec {
            if (stage == null || !Float.isFinite(radiusMultiplier) || radiusMultiplier <= 0.0F
                    || !Float.isFinite(baseY) || !Float.isFinite(topY) || topY <= baseY) {
                throw new IllegalArgumentException("Invalid storm lobe specification");
            }
        }
    }

    record StormPhysicalScale(
            int memberCount,
            float planRadius,
            float groupRadius,
            float baseDrop,
            float topRise
    ) {
        StormPhysicalScale {
            if (memberCount < 4 || !Float.isFinite(planRadius) || planRadius <= 0.0F
                    || !Float.isFinite(groupRadius) || groupRadius <= 0.0F
                    || !Float.isFinite(baseDrop) || baseDrop <= 0.0F
                    || !Float.isFinite(topRise) || topRise <= 0.0F) {
                throw new IllegalArgumentException("Invalid storm physical-scale contract");
            }
        }
    }

    private static Vec3 stormCell(Vec3 origin, SpawnPlan plan, int clusterIndex, RandomSource random) {
        CloudMorphologyMembership.Stage stage = new CloudMorphologyMembership(
                null,
                clusterIndex,
                plan.clusterCount()
        ).stageFor(CloudMorphologyFamily.STORM_ANVIL);
        float angle = (float) (random.nextFloat() * Math.PI * 2.0D);
        if (stage == CloudMorphologyMembership.Stage.ANVIL) {
            // A tightly overlapping row produces one spreading cap while
            // avoiding both a single perfect ellipsoid and visibly separate
            // anvil balls. All members share the wind-aligned orientation.
            int anvilStart = plan.clusterCount() / 2 + 1;
            int anvilCount = Math.max(1, plan.clusterCount() - anvilStart);
            int anvilIndex = Math.max(0, clusterIndex - anvilStart);
            float along01 = anvilCount <= 1
                    ? 0.5F
                    : (float) anvilIndex / (float) (anvilCount - 1);
            float along = plan.groupRadius() * Mth.lerp(
                    along01,
                    STORM_ANVIL_UPWIND_POSITION,
                    STORM_ANVIL_DOWNWIND_POSITION
            )
                    + (random.nextFloat() - 0.5F) * plan.groupRadius() * 0.025F;
            float cross = (float) Math.sin(along01 * Math.PI * 2.0D)
                    * plan.groupRadius() * 0.18F
                    + (random.nextFloat() - 0.5F) * plan.groupRadius() * 0.025F;
            float y = plan.topRise() * (0.78F + along01 * 0.025F)
                    + (random.nextFloat() - 0.5F) * 3.0F;
            return origin.add(along, y, cross);
        }
        int anvilStart = plan.clusterCount() / 2 + 1;
        int lowerCount = anvilStart;
        int baseCount = Math.max(1, lowerCount / 3);
        int coreCount = Math.max(1, (lowerCount - baseCount) / 2);
        int towerStart = baseCount + coreCount;
        float distanceScale;
        float heightScale;
        float shearScale;
        switch (stage) {
            case BASE -> {
                distanceScale = 0.12F;
                heightScale = 0.02F;
                shearScale = 0.00F;
            }
            case CORE -> {
                int ordinal = Math.max(0, clusterIndex - baseCount);
                float progress = coreCount <= 1
                        ? 0.0F
                        : (float) ordinal / (float) (coreCount - 1);
                distanceScale = Mth.lerp(progress, 0.06F, 0.04F);
                heightScale = Mth.lerp(progress, 0.18F, 0.25F);
                shearScale = Mth.lerp(progress, 0.02F, 0.035F);
            }
            case TOWER -> {
                int towerCount = Math.max(1, anvilStart - towerStart);
                int ordinal = Math.max(0, clusterIndex - towerStart);
                float progress = towerCount <= 1
                        ? 0.0F
                        : (float) ordinal / (float) (towerCount - 1);
                distanceScale = Mth.lerp(progress, 0.045F, 0.030F);
                heightScale = Mth.lerp(progress, 0.36F, 0.56F);
                shearScale = Mth.lerp(progress, 0.04F, 0.075F);
            }
            default -> throw new IllegalStateException("Unexpected lower storm stage " + stage);
        }
        float distance = plan.radius() * (0.03F + random.nextFloat() * distanceScale);
        float shear = plan.radius() * shearScale;
        float y = plan.topRise() * heightScale + (random.nextFloat() - 0.5F) * 6.0F;
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
            float orientationRadians,
            List<PuffLobeSpec> puffLobes
    ) {
        SpawnPlan(
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
            this(
                    family,
                    clusterCount,
                    radius,
                    groupRadius,
                    baseDrop,
                    topRise,
                    density,
                    coverage,
                    edgeSoftness,
                    orientationRadians,
                    List.of()
            );
        }

        SpawnPlan {
            puffLobes = puffLobes == null ? List.of() : List.copyOf(puffLobes);
            if (!puffLobes.isEmpty() && puffLobes.size() != clusterCount) {
                throw new IllegalArgumentException(
                        "PUFF lobe count " + puffLobes.size()
                                + " does not match cluster count " + clusterCount
                );
            }
        }

        boolean hasHierarchicalPuff() {
            return !puffLobes.isEmpty();
        }

        PuffLobeSpec puffLobe(int index) {
            return index >= 0 && index < puffLobes.size() ? puffLobes.get(index) : null;
        }

        SpawnPlan withPuffLobes(List<PuffLobeSpec> lobes) {
            return new SpawnPlan(
                    family,
                    clusterCount,
                    radius,
                    groupRadius,
                    baseDrop,
                    topRise,
                    density,
                    coverage,
                    edgeSoftness,
                    orientationRadians,
                    lobes
            );
        }
    }
}
