package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.visual.CloudVisualMetrics;
import net.Gabou.projectatmosphere.clouds.visual.CloudVisualState;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.weather.PrecipitationTier;
import net.Gabou.projectatmosphere.modules.weather.StormVisualTier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds distance-based PA cloud render plans. It is client render-only and
 * uses existing cloud snapshots and visual metadata fields as inputs.
 */
public final class CloudRenderLodManager {
    private static final int GLOBAL_RENDER_BUDGET = 42;
    private static final float DISTANCE_FADE_FRACTION = 0.88F;
    private static final float MIN_STORM_PRIORITY = 0.55F;
    private static final float MIN_HORIZON_PRIORITY = 0.22F;
    private static final float MIN_FAR_PRIORITY = 0.10F;

    private CloudRenderLodManager() {
    }

    public static @NotNull List<CloudRenderLodPlan> createPlans(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull List<CloudRenderSnapshot> snapshots
    ) {
        if (snapshots.isEmpty()) {
            return List.of();
        }

        CloudRenderProfile baseProfile = frameContext.getRenderProfile();
        float maxDistance = resolveMaxRenderDistance();
        List<Candidate> candidates = new ArrayList<>();
        for (CloudRenderSnapshot snapshot : snapshots) {
            Candidate candidate = createCandidate(frameContext, snapshot, maxDistance);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        candidates.sort(Comparator
                .comparing(Candidate::tierOrder)
                .thenComparing(Comparator.comparingDouble(Candidate::priority).reversed())
                .thenComparingDouble(Candidate::distanceToCamera));

        Map<CloudRenderLodTier, Integer> usedByTier = new EnumMap<>(CloudRenderLodTier.class);
        List<CloudRenderLodPlan> plans = new ArrayList<>(Math.min(GLOBAL_RENDER_BUDGET, candidates.size()));
        for (Candidate candidate : candidates) {
            if (plans.size() >= GLOBAL_RENDER_BUDGET) {
                break;
            }
            int used = usedByTier.getOrDefault(candidate.tier(), 0);
            if (used >= candidate.tier().getBudget()) {
                continue;
            }

            CloudRenderProfile profile = baseProfile.withLod(
                    resolveRaymarchSteps(baseProfile.getRaymarchSteps(), candidate),
                    maxDistance
            );
            CloudRenderSnapshot lodSnapshot = CloudRenderLodSnapshotFactory.create(candidate.snapshot(), candidate);
            if (!CloudDensityProvider.hasVisibleDensity(lodSnapshot)) {
                continue;
            }
            plans.add(new CloudRenderLodPlan(
                    lodSnapshot,
                    profile,
                    candidate.tier(),
                    candidate.distanceToCamera(),
                    candidate.priority(),
                    candidate.fadeAlpha()
            ));
            usedByTier.put(candidate.tier(), used + 1);
        }

        return List.copyOf(plans);
    }

    private static Candidate createCandidate(CloudRenderFrameContext frameContext, CloudRenderSnapshot snapshot, float maxDistance) {
        if (snapshot == null || snapshot.getRegionCenter() == null || snapshot.getRegionRadius() <= 0.0F) {
            return null;
        }
        float distance = horizontalDistance(frameContext.getCameraPosition(), snapshot.getRegionCenter());
        float edgeDistance = Math.max(0.0F, distance - snapshot.getRegionRadius());
        if (edgeDistance > maxDistance) {
            return null;
        }

        CloudRenderLodTier tier = CloudRenderLodTier.forDistance(edgeDistance);
        float priority = computePriority(snapshot);
        if (!passesTierImportance(tier, priority, snapshot)) {
            return null;
        }

        float fadeAlpha = resolveFadeAlpha(edgeDistance, maxDistance, priority);
        if (fadeAlpha <= 0.015F) {
            return null;
        }
        float detailBlend = resolveDetailBlend(edgeDistance);
        return new Candidate(snapshot, tier, distance, edgeDistance, priority, fadeAlpha, detailBlend);
    }

    private static boolean passesTierImportance(CloudRenderLodTier tier, float priority, CloudRenderSnapshot snapshot) {
        if (tier == CloudRenderLodTier.NEAR || tier == CloudRenderLodTier.MEDIUM) {
            return true;
        }
        if (isMajorStorm(snapshot)) {
            return true;
        }
        if (tier == CloudRenderLodTier.FAR) {
            return priority >= MIN_FAR_PRIORITY;
        }
        return priority >= MIN_HORIZON_PRIORITY;
    }

    private static int resolveRaymarchSteps(int baseSteps, Candidate candidate) {
        float severityBoost = isMajorStorm(candidate.snapshot()) ? 0.16F : 0.0F;
        float scale = Math.min(1.0F, candidate.tier().getRaymarchStepScale() + severityBoost);
        int minimum = switch (candidate.tier()) {
            case NEAR -> baseSteps;
            case MEDIUM -> Math.min(baseSteps, 14);
            case FAR -> Math.min(baseSteps, 8);
            case HORIZON -> Math.min(baseSteps, 5);
        };
        return Mth.clamp(Math.round(baseSteps * scale), Math.max(1, minimum), baseSteps);
    }

    private static float computePriority(CloudRenderSnapshot snapshot) {
        CloudVisualState visualState = toVisualState(snapshot);
        float visualPriority = CloudVisualMetrics.lodPriority(visualState);
        float morphology = morphologyPriority(snapshot);
        float storm = Mth.clamp(snapshot.getStormVisualTier().getDarkness(), 0.0F, 1.0F);
        float precipitation = snapshot.getPrecipitationTier().getRepresentativeIntensity();
        float size = Mth.clamp(snapshot.getRegionRadius() / 900.0F, 0.0F, 1.0F);
        float vertical = Mth.clamp(snapshot.getVerticalThickness(), 0.0F, 1.0F);

        return Mth.clamp(
                visualPriority * 0.36F
                        + morphology * 0.24F
                        + storm * 0.20F
                        + precipitation * 0.08F
                        + size * 0.07F
                        + vertical * 0.05F,
                0.0F,
                1.0F
        );
    }

    private static CloudVisualState toVisualState(CloudRenderSnapshot snapshot) {
        float density = CloudDensityProvider.getEffectiveDensity(snapshot);
        float coverage = CloudDensityProvider.getEffectiveCoverage(snapshot);
        float precipitation = snapshot.getPrecipitationTier().getRepresentativeIntensity();
        float storm = Mth.clamp(
                snapshot.getStormVisualTier().getDarkness() * 0.58F
                        + precipitation * 0.18F
                        + snapshot.getTowerStrength() * 0.12F
                        + snapshot.getAnvilStrength() * 0.12F,
                0.0F,
                1.0F
        );
        float darkness = Mth.clamp(
                snapshot.getMaterialProfile().getDarkness() * 0.42F
                        + snapshot.getBaseDarkness() * 0.20F
                        + snapshot.getStormVisualTier().getDarkness() * 0.24F
                        + precipitation * 0.14F,
                0.0F,
                1.0F
        );
        float shadow = Mth.clamp(
                snapshot.getShadowContribution() * 0.42F
                        + density * coverage * 0.30F
                        + darkness * 0.18F
                        + snapshot.getStormVisualTier().getShadowBias() * 0.10F,
                0.0F,
                1.0F
        );
        float vertical = Mth.clamp(
                Mth.clamp((snapshot.getCloudTopY() - snapshot.getCloudBaseY()) / 192.0F, 0.0F, 1.0F) * 0.40F
                        + snapshot.getVerticalThickness() * 0.16F
                        + snapshot.getTowerStrength() * 0.24F
                        + snapshot.getAnvilStrength() * 0.20F,
                0.0F,
                1.0F
        );
        float visibility = Mth.clamp(
                Mth.clamp(snapshot.getRegionRadius() / 900.0F, 0.0F, 1.0F) * 0.32F
                        + density * coverage * 0.20F
                        + vertical * 0.18F
                        + storm * 0.18F
                        + shadow * 0.12F,
                0.0F,
                1.0F
        );
        return new CloudVisualState(
                null,
                null,
                snapshot.getDimension(),
                snapshot.getCloudTypeId(),
                snapshot.getMorphologyFamily(),
                snapshot.getRegionCenter(),
                snapshot.getPreviousRegionCenter(),
                snapshot.getVelocity(),
                snapshot.getRegionRadius(),
                snapshot.getCloudBaseY(),
                snapshot.getCloudTopY(),
                density,
                coverage,
                density * coverage,
                precipitation,
                storm,
                darkness,
                shadow,
                density * coverage,
                vertical,
                visibility,
                snapshot.getStormVisualTier(),
                snapshot.getPrecipitationTier(),
                snapshot.getCloudSeed()
        );
    }

    private static float morphologyPriority(CloudRenderSnapshot snapshot) {
        CloudMorphologyFamily family = snapshot.getMorphologyFamily();
        if (family == CloudMorphologyFamily.SPIRAL_STORM) {
            return 1.0F;
        }
        if (family == CloudMorphologyFamily.STORM_ANVIL) {
            return 0.86F;
        }
        if (family == CloudMorphologyFamily.TOWER) {
            return 0.72F;
        }
        if (family == CloudMorphologyFamily.SHEET || family == CloudMorphologyFamily.CELLULAR_SHEET) {
            return 0.38F;
        }
        if (family == CloudMorphologyFamily.FILAMENT) {
            return 0.24F;
        }
        return isNamedMajorSystem(snapshot) ? 0.75F : 0.18F;
    }

    static boolean isMajorStorm(CloudRenderSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        if (snapshot.getMorphologyFamily() == CloudMorphologyFamily.SPIRAL_STORM
                || snapshot.getMorphologyFamily() == CloudMorphologyFamily.STORM_ANVIL) {
            return true;
        }
        if (snapshot.getStormVisualTier() == StormVisualTier.SEVERE_CORE
                || snapshot.getStormVisualTier() == StormVisualTier.CYCLONE_CORE) {
            return true;
        }
        return isNamedMajorSystem(snapshot)
                || snapshot.getPrecipitationTier() == PrecipitationTier.HEAVY_RAIN;
    }

    private static boolean isNamedMajorSystem(CloudRenderSnapshot snapshot) {
        String id = snapshot.getCloudTypeId();
        if (id == null) {
            return false;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        return normalized.contains("hurricane")
                || normalized.contains("supercell")
                || normalized.contains("blizzard")
                || normalized.contains("cyclone")
                || normalized.contains("cumulonimbus");
    }

    private static float resolveFadeAlpha(float edgeDistance, float maxDistance, float priority) {
        float fadeStart = Math.max(CloudRenderLodTier.FAR.getMinDistance(), maxDistance * DISTANCE_FADE_FRACTION);
        float distanceFade = 1.0F - smoothStep(fadeStart, maxDistance, edgeDistance);
        float stormFloor = priority >= MIN_STORM_PRIORITY ? 0.28F : 0.0F;
        return Mth.clamp(Math.max(distanceFade, stormFloor) * Mth.clamp(priority + 0.36F, 0.30F, 1.0F), 0.0F, 1.0F);
    }

    private static float resolveDetailBlend(float edgeDistance) {
        if (edgeDistance <= CloudRenderLodTier.NEAR.getMaxDistance()) {
            return 1.0F;
        }
        float t = 1.0F - smoothStep(CloudRenderLodTier.NEAR.getMaxDistance(), CloudRenderLodTier.HORIZON.getMinDistance(), edgeDistance);
        return Mth.clamp(t, 0.0F, 1.0F);
    }

    private static float resolveMaxRenderDistance() {
        return Math.max(100.0F, AtmoCommonConfig.CLOUD_RENDER_DISTANCE.get().floatValue());
    }

    private static float horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        if (edge1 <= edge0) {
            return value >= edge1 ? 1.0F : 0.0F;
        }
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    record Candidate(
            CloudRenderSnapshot snapshot,
            CloudRenderLodTier tier,
            float distanceToCamera,
            float edgeDistance,
            float priority,
            float fadeAlpha,
            float detailBlend
    ) {
        int tierOrder() {
            return tier.getOrder();
        }
    }
}
