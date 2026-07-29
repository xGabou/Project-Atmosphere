package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyMemberTier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reports deterministic metrics for the exact structured TOWER source table. */
public final class CloudMorphologyTopologySandbox {
    private CloudMorphologyTopologySandbox() {
    }

    public static void main(String[] args) {
        CloudMorphologyGenerators.StructuredTowerTopology topology =
                CloudMorphologyGenerators.structuredTowerTopology();
        requireCount("angles", topology.angles(), 12);
        requireCount("radial", topology.radial(), 12);
        requireCount("heights", topology.heights(), 12);
        requireCount("radii", topology.radii(), 12);

        double heightRadiusR2 = correlationSquared(topology.heights(), topology.radii(), 0);
        double heightRadialR2 = correlationSquared(topology.heights(), topology.radial(), 1);
        double crownSeparation = circularSeparation(topology.angles().get(10), topology.angles().get(11));
        double[] stageRadiusMeans = stageMeans(topology.radii());
        double[] stageRadialMeans = stageMeans(topology.radial());

        System.out.printf(
                Locale.ROOT,
                "Structured tower topology heightRadiusR2=%.6f heightRadialR2=%.6f crownSeparation=%.3f"
                        + " stageRadiusMeans=%.6f/%.6f/%.6f/%.6f"
                        + " stageRadialMeans=%.6f/%.6f/%.6f/%.6f%n",
                heightRadiusR2,
                heightRadialR2,
                crownSeparation,
                stageRadiusMeans[0],
                stageRadiusMeans[1],
                stageRadiusMeans[2],
                stageRadiusMeans[3],
                stageRadialMeans[0],
                stageRadialMeans[1],
                stageRadialMeans[2],
                stageRadialMeans[3]
        );

        requireAtMost("height/radius R^2", heightRadiusR2, 0.40D);
        requireAtMost("height/radial R^2", heightRadialR2, 0.40D);
        requireRange("crown separation", crownSeparation, 90.0D, 155.0D);
        if (stageRadiusMeans[3] < stageRadiusMeans[2] * 0.95D) {
            throw new IllegalStateException(
                    "crown radius collapsed below tower support: tower="
                            + stageRadiusMeans[2] + " crown=" + stageRadiusMeans[3]
            );
        }

        PuffTopologySummary humilis = reportPuffTopology("cumulus_humilis", 4_096);
        PuffTopologySummary mediocris = reportPuffTopology("cumulus_mediocris", 4_096);
        validateContinuousCarrierTopology("cumulus_humilis", 512);
        validateContinuousCarrierTopology("cumulus_mediocris", 512);
        validateAnalyticPuffTopology("cumulus_humilis", 512, 1.50D, 1.35D);
        validateAnalyticPuffTopology("cumulus_mediocris", 512, 1.50D, 1.35D);
        validatePuffTopology("cumulus_humilis", humilis, 1.90D);
        validatePuffTopology("cumulus_mediocris", mediocris, 2.20D);
        validateStructuredPuffProfileOrdering();
        validatePuffRetargetPreservesRadiusRatios();

        CloudMorphologyGenerators.PuffTopologyParameters puff =
                CloudMorphologyGenerators.puffTopologyParameters();
        double analyticalWorstPrimaryRatio = puff.groupRadiusMultiplier()
                * 1.10D
                * puff.radialMaximum()
                / (0.90D * 0.92D * 0.92D * (1.0D + 0.72D));
        System.out.printf(
                Locale.ROOT,
                "PUFF analyticalWorstPrimaryRatio=%.6f parameters[group=%.3f radial=%.3f..%.3f angularJitter=%.3f]%n",
                analyticalWorstPrimaryRatio,
                puff.groupRadiusMultiplier(),
                puff.radialMinimum(),
                puff.radialMaximum(),
                puff.angularJitterRadians()
        );
        requireAtMost("PUFF analytical primary separation", analyticalWorstPrimaryRatio, 0.90D);
    }

    private static void validateStructuredPuffProfileOrdering() {
        CloudMorphologyMemberTier[] tiers = {
                CloudMorphologyMemberTier.BASE,
                CloudMorphologyMemberTier.MIDDLE,
                CloudMorphologyMemberTier.CROWN
        };
        for (int sample = 0; sample <= 32; sample++) {
            double phase = sample / 32.0D;
            double[] normalizedRadii = new double[tiers.length];
            for (int index = 0; index < tiers.length; index++) {
                CloudMorphologyMemberTier tier = tiers[index];
                double peak = switch (tier) {
                    case BASE -> lerp(phase, 0.32D, 0.38D);
                    case MIDDLE -> lerp(phase, 0.38D, 0.44D);
                    case CROWN -> lerp(phase, 0.43D, 0.50D);
                    case UNKNOWN -> throw new IllegalStateException(
                            "Structured PUFF profile cannot evaluate UNKNOWN tier"
                    );
                };
                double equator = switch (tier) {
                    case BASE -> lerp(phase, 0.92D, 0.98D);
                    case MIDDLE -> lerp(phase, 0.94D, 1.00D);
                    case CROWN -> lerp(phase, 0.90D, 0.96D);
                    case UNKNOWN -> throw new IllegalStateException(
                            "Structured PUFF profile cannot evaluate UNKNOWN tier"
                    );
                };
                double upperMidpoint = peak + (1.0D - peak) * 0.5D;
                normalizedRadii[index] = analyticRadiusAtHeight(
                        upperMidpoint,
                        phase,
                        tier
                ) / equator;
            }
            if (!(normalizedRadii[0] < normalizedRadii[1]
                    && normalizedRadii[1] < normalizedRadii[2])) {
                throw new IllegalStateException(
                        "Structured PUFF upper caps lost BASE<MIDDLE<CROWN ordering phase="
                                + phase + " radii=" + normalizedRadii[0] + "/"
                                + normalizedRadii[1] + "/" + normalizedRadii[2]
                );
            }
        }
    }

    /**
     * Replays the production spawner's RandomSource consumption and reports
     * horizontal topology before imposing any acceptance threshold. This keeps
     * source spacing diagnosis independent from the renderer and weather map.
     */
    private static PuffTopologySummary reportPuffTopology(String cloudTypeId, int sampleCount) {
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cloudTypeId);
        List<Double> nearestBirthRatios = new ArrayList<>();
        List<Double> nearestTargetRatios = new ArrayList<>();
        List<Double> primaryBirthRatios = new ArrayList<>();
        List<Double> nearestCentreRadiusRatios = new ArrayList<>();
        List<Double> centreSpreadRatios = new ArrayList<>();
        List<Double> footprintRatios = new ArrayList<>();
        List<Double> centroidOffsetRatios = new ArrayList<>();
        List<Double> centreAnisotropyRatios = new ArrayList<>();
        List<Double> baseSpreadRatios = new ArrayList<>();
        List<Double> shoulderBirthRatios = new ArrayList<>();
        List<Double> shoulderTargetRatios = new ArrayList<>();
        List<Double> upperSupportRatios = new ArrayList<>();
        List<Double> upperOffsetRatios = new ArrayList<>();
        List<Double> upperProtrusionRatios = new ArrayList<>();
        List<Double> upperSpanWorld = new ArrayList<>();
        List<Double> upperCoreFractions = new ArrayList<>();
        List<Double> structuralMassRatios = new ArrayList<>();
        double minimumBasePairRatio = Double.POSITIVE_INFINITY;
        int disconnectedBirth = 0;
        int disconnectedTarget = 0;
        int totalMembers = 0;
        int birthMembersPassingRadius120 = 0;
        int targetMembersPassingRadius120 = 0;
        int groupsWithoutBirthRadius120 = 0;
        int groupsWithoutTargetRadius120 = 0;
        double componentBirthSum = 0.0D;
        double componentTargetSum = 0.0D;

        for (int sample = 0; sample < sampleCount; sample++) {
            RandomSource random = RandomSource.create(0x50414646L + sample * 0x9E3779B9L);
            CloudMorphologyGenerators.SpawnPlan plan =
                    CloudMorphologyGenerators.createSpawnPlan(definition, random);
            Vec3 origin = Vec3.ZERO;
            List<PuffLobe> clusters = new ArrayList<>(plan.clusterCount());

            if (plan.hasHierarchicalPuff()) {
                for (CloudMorphologyGenerators.PuffLobeSpec spec : plan.puffLobes()) {
                    clusters.add(new PuffLobe(
                            origin.add(spec.offsetX(), spec.offsetY(), spec.offsetZ()),
                            spec.targetRadius() * 0.96D,
                            spec.targetRadius(),
                            spec.baseOffsetY(),
                            spec.topOffsetY(),
                            spec.tier()
                    ));
                }
            } else {
                PuffLobe primary = samplePuffLobe(origin, plan, 1.0F, random);
                clusters.add(primary);
                for (int index = 1; index < plan.clusterCount(); index++) {
                    Vec3 center = CloudMorphologyGenerators.createClusterCenter(origin, plan, index, random);
                    float scale = 0.72F + random.nextFloat() * 0.42F;
                    // Match the four constructor-side samples in CloudGroupSpawner
                    // before tuneSpawnedCluster consumes radius/media jitter.
                    consume(random, 4);
                    clusters.add(samplePuffLobe(center, plan, scale, random));
                }
            }

            PuffLobe primary = clusters.get(0);
            List<PuffLobe> baseLobes = clusters.stream()
                    .filter(lobe -> lobe.tier() == CloudMorphologyMemberTier.BASE)
                    .toList();
            List<PuffLobe> upperLobes = clusters.stream()
                    .filter(lobe -> lobe.tier() != CloudMorphologyMemberTier.BASE)
                    .toList();
            int expectedMembers = plan.clusterCount();
            int expectedUpper = "cumulus_mediocris".equals(cloudTypeId) ? 4 : 3;
            if (clusters.size() != expectedMembers || upperLobes.size() != expectedUpper
                    || baseLobes.size() + upperLobes.size() != clusters.size()) {
                throw new IllegalStateException(
                        cloudTypeId + " hierarchical member contract failed members="
                                + clusters.size() + " base=" + baseLobes.size()
                                + " upper=" + upperLobes.size()
                );
            }

            double minimumBase = baseLobes.stream().mapToDouble(PuffLobe::baseY).min().orElseThrow();
            double maximumBase = baseLobes.stream().mapToDouble(PuffLobe::baseY).max().orElseThrow();
            double maximumBaseTop = baseLobes.stream().mapToDouble(PuffLobe::topY).max().orElseThrow();
            baseSpreadRatios.add((maximumBase - minimumBase) / plan.radius());
            for (int index = 1; index < baseLobes.size(); index++) {
                PuffLobe shoulder = baseLobes.get(index);
                shoulderBirthRatios.add(
                        horizontalDistance(primary.center(), shoulder.center())
                                / (0.864D * (primary.birthRadius() + shoulder.birthRadius()))
                );
                shoulderTargetRatios.add(
                        horizontalDistance(primary.center(), shoulder.center())
                                / (0.864D * (primary.targetRadius() + shoulder.targetRadius()))
                );
            }
            for (int first = 0; first < baseLobes.size(); first++) {
                for (int second = first + 1; second < baseLobes.size(); second++) {
                    PuffLobe a = baseLobes.get(first);
                    PuffLobe b = baseLobes.get(second);
                    minimumBasePairRatio = Math.min(
                            minimumBasePairRatio,
                            horizontalDistance(a.center(), b.center())
                                    / (0.864D * (a.targetRadius() + b.targetRadius()))
                    );
                }
            }
            for (PuffLobe upper : upperLobes) {
                double nearestSupport = Double.POSITIVE_INFINITY;
                for (PuffLobe base : baseLobes) {
                    nearestSupport = Math.min(
                            nearestSupport,
                            distance3d(upper.center(), base.center())
                                    / (upper.targetRadius() + base.targetRadius())
                    );
                }
                upperSupportRatios.add(nearestSupport);
                upperOffsetRatios.add(horizontalDistance(origin, upper.center()) / plan.radius());
                upperProtrusionRatios.add((upper.topY() - maximumBaseTop) / plan.radius());
                double upperSpan = upper.topY() - upper.baseY();
                upperSpanWorld.add(upperSpan);
                upperCoreFractions.add(puffCoreFraction(upperSpan, upper.tier()));
            }
            double structuralMass = clusters.stream()
                    .mapToDouble(lobe -> lobe.targetRadius() * lobe.targetRadius()
                            * (lobe.topY() - lobe.baseY()))
                    .sum();
            structuralMassRatios.add(structuralMass / Math.pow(plan.radius(), 3.0D));

            int birthComponents = componentCount(clusters, false);
            int targetComponents = componentCount(clusters, true);
            componentBirthSum += birthComponents;
            componentTargetSum += targetComponents;
            if (birthComponents > 1) {
                disconnectedBirth++;
            }
            if (targetComponents > 1) {
                disconnectedTarget++;
            }

            double maxCentreDistance = 0.0D;
            double maxFootprint = 0.0D;
            double centroidX = 0.0D;
            double centroidZ = 0.0D;
            boolean anyBirthRadius120 = false;
            boolean anyTargetRadius120 = false;
            for (int index = 0; index < clusters.size(); index++) {
                PuffLobe cluster = clusters.get(index);
                centroidX += cluster.center().x;
                centroidZ += cluster.center().z;
                double centreDistance = horizontalDistance(origin, cluster.center());
                maxCentreDistance = Math.max(maxCentreDistance, centreDistance);
                maxFootprint = Math.max(maxFootprint, centreDistance + cluster.birthRadius());
                if (structuralRadius(clusters, index, false) >= 120.0D) {
                    birthMembersPassingRadius120++;
                    anyBirthRadius120 = true;
                }
                if (structuralRadius(clusters, index, true) >= 120.0D) {
                    targetMembersPassingRadius120++;
                    anyTargetRadius120 = true;
                }
                if (index == 0) {
                    continue;
                }
                nearestBirthRatios.add(nearestSeparationRatio(clusters, index, false));
                nearestTargetRatios.add(nearestSeparationRatio(clusters, index, true));
                primaryBirthRatios.add(separationRatio(primary, cluster, false));
                nearestCentreRadiusRatios.add(nearestCentreRadiusRatio(clusters, index));
            }
            centreSpreadRatios.add(maxCentreDistance / plan.radius());
            footprintRatios.add(maxFootprint / plan.radius());
            centroidOffsetRatios.add(
                    Math.sqrt(centroidX * centroidX + centroidZ * centroidZ)
                            / clusters.size()
                            / plan.radius()
            );
            centreAnisotropyRatios.add(centreAnisotropy(clusters, plan.radius()));
            if (!anyBirthRadius120) {
                groupsWithoutBirthRadius120++;
            }
            if (!anyTargetRadius120) {
                groupsWithoutTargetRadius120++;
            }
            totalMembers += clusters.size();
        }

        System.out.printf(
                Locale.ROOT,
                "PUFF topology type=%s placement=%s samples=%d meanMembers=%.3f"
                        + " disconnectedBirth=%d(%.3f%%) disconnectedTarget=%d(%.3f%%)"
                        + " meanComponentsBirthTarget=%.4f/%.4f"
                        + " nearestBirth[p05/p50/p95/max]=%.4f/%.4f/%.4f/%.4f"
                        + " nearestTarget[p05/p50/p95/max]=%.4f/%.4f/%.4f/%.4f"
                        + " primaryBirth[p50/p95/max]=%.4f/%.4f/%.4f"
                        + " nearestCentreMinRadius[p05/p50]=%.4f/%.4f"
                        + " centreSpread[p50/p95/max]=%.4f/%.4f/%.4f"
                        + " footprint[p50/p95/max]=%.4f/%.4f/%.4f"
                        + " centroidOffset[p50/p95/max]=%.4f/%.4f/%.4f"
                        + " centreAnisotropy[p50/p95/max]=%.4f/%.4f/%.4f"
                        + " baseSpreadMax=%.4f"
                        + " shoulderBirth[min/max]=%.4f/%.4f"
                        + " shoulderTarget[min/max]=%.4f/%.4f"
                        + " basePairMin=%.4f"
                        + " upperSupportMax=%.4f"
                        + " upperOffset[min/max]=%.4f/%.4f"
                        + " upperProtrusion[p05/p50]=%.4f/%.4f"
                        + " upperSpanMin=%.4f upperCoreMin=%.4f"
                        + " structuralMass[p05/p50/p95]=%.4f/%.4f/%.4f"
                        + " radius120Members[birth/target]=%.3f%%/%.3f%%"
                        + " radius120GroupsNone[birth/target]=%.3f%%/%.3f%%%n",
                cloudTypeId,
                "production",
                sampleCount,
                (double) totalMembers / sampleCount,
                disconnectedBirth,
                disconnectedBirth * 100.0D / sampleCount,
                disconnectedTarget,
                disconnectedTarget * 100.0D / sampleCount,
                componentBirthSum / sampleCount,
                componentTargetSum / sampleCount,
                quantile(nearestBirthRatios, 0.05D),
                quantile(nearestBirthRatios, 0.50D),
                quantile(nearestBirthRatios, 0.95D),
                quantile(nearestBirthRatios, 1.00D),
                quantile(nearestTargetRatios, 0.05D),
                quantile(nearestTargetRatios, 0.50D),
                quantile(nearestTargetRatios, 0.95D),
                quantile(nearestTargetRatios, 1.00D),
                quantile(primaryBirthRatios, 0.50D),
                quantile(primaryBirthRatios, 0.95D),
                quantile(primaryBirthRatios, 1.00D),
                quantile(nearestCentreRadiusRatios, 0.05D),
                quantile(nearestCentreRadiusRatios, 0.50D),
                quantile(centreSpreadRatios, 0.50D),
                quantile(centreSpreadRatios, 0.95D),
                quantile(centreSpreadRatios, 1.00D),
                quantile(footprintRatios, 0.50D),
                quantile(footprintRatios, 0.95D),
                quantile(footprintRatios, 1.00D),
                quantile(centroidOffsetRatios, 0.50D),
                quantile(centroidOffsetRatios, 0.95D),
                quantile(centroidOffsetRatios, 1.00D),
                quantile(centreAnisotropyRatios, 0.50D),
                quantile(centreAnisotropyRatios, 0.95D),
                quantile(centreAnisotropyRatios, 1.00D),
                quantile(baseSpreadRatios, 1.00D),
                quantile(shoulderBirthRatios, 0.00D),
                quantile(shoulderBirthRatios, 1.00D),
                quantile(shoulderTargetRatios, 0.00D),
                quantile(shoulderTargetRatios, 1.00D),
                minimumBasePairRatio,
                quantile(upperSupportRatios, 1.00D),
                quantile(upperOffsetRatios, 0.00D),
                quantile(upperOffsetRatios, 1.00D),
                quantile(upperProtrusionRatios, 0.05D),
                quantile(upperProtrusionRatios, 0.50D),
                quantile(upperSpanWorld, 0.00D),
                quantile(upperCoreFractions, 0.00D),
                quantile(structuralMassRatios, 0.05D),
                quantile(structuralMassRatios, 0.50D),
                quantile(structuralMassRatios, 0.95D),
                birthMembersPassingRadius120 * 100.0D / totalMembers,
                targetMembersPassingRadius120 * 100.0D / totalMembers,
                groupsWithoutBirthRadius120 * 100.0D / sampleCount,
                groupsWithoutTargetRadius120 * 100.0D / sampleCount
        );
        return new PuffTopologySummary(
                disconnectedBirth,
                disconnectedTarget,
                quantile(primaryBirthRatios, 1.00D),
                quantile(nearestCentreRadiusRatios, 0.05D),
                quantile(footprintRatios, 0.50D),
                quantile(footprintRatios, 0.95D),
                quantile(centreAnisotropyRatios, 0.95D),
                quantile(baseSpreadRatios, 1.00D),
                quantile(shoulderBirthRatios, 0.00D),
                quantile(shoulderBirthRatios, 1.00D),
                quantile(shoulderTargetRatios, 0.00D),
                quantile(shoulderTargetRatios, 1.00D),
                minimumBasePairRatio,
                quantile(upperSupportRatios, 1.00D),
                quantile(upperOffsetRatios, 0.00D),
                quantile(upperOffsetRatios, 1.00D),
                quantile(upperProtrusionRatios, 0.05D),
                quantile(upperSpanWorld, 0.00D),
                quantile(upperCoreFractions, 0.00D),
                quantile(structuralMassRatios, 0.05D),
                quantile(structuralMassRatios, 0.95D),
                targetMembersPassingRadius120 * 100.0D / totalMembers,
                groupsWithoutTargetRadius120 * 100.0D / sampleCount
        );
    }

    private static void validatePuffTopology(
            String cloudTypeId,
            PuffTopologySummary summary,
            double maximumAnisotropyP95
    ) {
        if (summary.disconnectedBirth() != 0 || summary.disconnectedTarget() != 0) {
            throw new IllegalStateException(
                    cloudTypeId + " PUFF topology disconnected birth/target="
                            + summary.disconnectedBirth() + "/" + summary.disconnectedTarget()
            );
        }
        // This sphere proxy predates the shader-exact isosurface checks below.
        // Keep a runaway guard, but allow an upper member to own real
        // silhouette instead of forcing it back inside its parent.
        requireAtMost(cloudTypeId + " sampled primary separation", summary.primaryBirthMaximum(), 0.92D);
        // Parent/child lobes may be almost vertically aligned. Horizontal
        // centre/radius separation alone cannot distinguish that valid stack
        // from a collapsed layout. The continuous-carrier checks are the
        // authoritative rendered-connectivity contract.
        requireAtLeast(cloudTypeId + " distinct-lobe p05", summary.nearestCentreRadiusP05(), 0.09D);
        boolean humilis = "cumulus_humilis".equals(cloudTypeId);
        requireRange(
                cloudTypeId + " footprint p50",
                summary.footprintP50(),
                humilis ? 0.74D : 0.82D,
                humilis ? 0.86D : 0.96D
        );
        requireAtMost(
                cloudTypeId + " footprint p95",
                summary.footprintP95(),
                humilis ? 0.92D : 1.02D
        );
        requireAtMost(cloudTypeId + " anisotropy p95", summary.anisotropyP95(), maximumAnisotropyP95);
        requireAtMost(cloudTypeId + " coherent base spread", summary.baseSpreadMaximum(), 0.020D);
        // These ranges mirror the deliberate 0.40..0.44 centre spacing. They
        // are paired with the shader-exact connected-base-slice assertion and
        // the root/equator cap, so widening roots cannot satisfy continuity.
        requireRange(cloudTypeId + " shoulder birth minimum", summary.shoulderBirthMinimum(), 0.47D, 0.54D);
        requireRange(cloudTypeId + " shoulder birth maximum", summary.shoulderBirthMaximum(), 0.47D, 0.54D);
        requireRange(cloudTypeId + " shoulder target minimum", summary.shoulderTargetMinimum(), 0.45D, 0.52D);
        requireRange(cloudTypeId + " shoulder target maximum", summary.shoulderTargetMaximum(), 0.45D, 0.52D);
        requireAtLeast(cloudTypeId + " noncollapsed base pair", summary.minimumBasePairRatio(), 0.45D);
        // This legacy centre/sphere ratio includes vertical lift and therefore
        // exceeds one for valid mediocris crowns. Exact profile overlap is
        // separately required at density 0.15.
        requireAtMost(
                cloudTypeId + " upper support",
                summary.upperSupportMaximum(),
                humilis ? 1.15D : 1.35D
        );
        requireAtLeast(cloudTypeId + " upper horizontal offset minimum", summary.upperOffsetMinimum(), 0.24D);
        requireAtMost(
                cloudTypeId + " upper horizontal offset maximum",
                summary.upperOffsetMaximum(),
                humilis ? 0.65D : 0.70D
        );
        requireAtLeast(cloudTypeId + " upper protrusion p05", summary.upperProtrusionP05(), 0.12D);
        requireAtLeast(cloudTypeId + " upper core with fixed world feather",
                summary.upperCoreMinimum(), 0.299D);
        requireAtLeast(
                cloudTypeId + " structural mass p05",
                summary.structuralMassP05(),
                humilis ? 0.55D : 0.86D
        );
        requireAtMost(
                cloudTypeId + " structural mass p95",
                summary.structuralMassP95(),
                humilis ? 0.70D : 1.08D
        );
    }

    /**
     * Retains pre-carrier analytic silhouette metrics. Connectivity here is
     * diagnostic: the rendered acceptance contract is exercised separately by
     * {@link #validateContinuousCarrierTopology(String, int)}.
     */
    private static void validateAnalyticPuffTopology(
            String cloudTypeId,
            int sampleCount,
            double maximumBaseWidthHeight,
            double maximumUpperWidthHeight
    ) {
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cloudTypeId);
        int disconnected02 = 0;
        int disconnected05 = 0;
        int disconnected15 = 0;
        int disconnectedBaseSlice = 0;
        double observedBaseWidthHeight = 0.0D;
        double observedUpperWidthHeight = 0.0D;
        double minimumUpperMinorWidthHeight = Double.POSITIVE_INFINITY;
        double minimumCrownRise = Double.POSITIVE_INFINITY;
        double minimumUpperOffset = Double.POSITIVE_INFINITY;
        double minimumMiddleSeparation = Double.POSITIVE_INFINITY;
        double minimumCrownSeparation = Double.POSITIVE_INFINITY;
        double minimumUpperParentSeparation = Double.POSITIVE_INFINITY;
        double minimumJunctionProtrusion = Double.POSITIVE_INFINITY;
        double minimumJunctionBridge = Double.POSITIVE_INFINITY;
        double minimumUpperSilhouetteExposure = Double.POSITIVE_INFINITY;
        double minimumFootprint = Double.POSITIVE_INFINITY;
        double maximumFootprint = Double.NEGATIVE_INFINITY;
        double minimumGroupHeight = Double.POSITIVE_INFINITY;
        double maximumGroupHeight = Double.NEGATIVE_INFINITY;
        double minimumGroupWidthHeight = Double.POSITIVE_INFINITY;
        double maximumGroupWidthHeight = Double.NEGATIVE_INFINITY;
        double minimumProjectedWidthRatio = Double.POSITIVE_INFINITY;
        double maximumProjectedAnisotropy = Double.NEGATIVE_INFINITY;

        for (int sample = 0; sample < sampleCount; sample++) {
            RandomSource random = RandomSource.create(0x50414646L + sample * 0x9E3779B9L);
            CloudMorphologyGenerators.SpawnPlan plan =
                    CloudMorphologyGenerators.createSpawnPlan(definition, random);
            if (!plan.hasHierarchicalPuff()) {
                throw new IllegalStateException(cloudTypeId + " lacks hierarchical PUFF descriptors");
            }
            List<PuffLobe> lobes = new ArrayList<>(plan.puffLobes().size());
            double highestBaseTop = Double.NEGATIVE_INFINITY;
            double lowestBase = Double.POSITIVE_INFINITY;
            double highestTop = Double.NEGATIVE_INFINITY;
            double footprint = 0.0D;
            List<PuffLobe> middleLobes = new ArrayList<>(2);
            List<PuffLobe> crownLobes = new ArrayList<>(2);
            List<PuffLobe> baseLobes = new ArrayList<>(4);
            for (CloudMorphologyGenerators.PuffLobeSpec spec : plan.puffLobes()) {
                PuffLobe lobe = new PuffLobe(
                        new Vec3(spec.offsetX(), spec.offsetY(), spec.offsetZ()),
                        spec.targetRadius() * 0.96D,
                        spec.targetRadius(),
                        spec.baseOffsetY(),
                        spec.topOffsetY(),
                        spec.tier()
                );
                lobes.add(lobe);
                double span = lobe.topY() - lobe.baseY();
                double widthHeight = 2.0D * 0.96D * lobe.targetRadius() / span;
                lowestBase = Math.min(lowestBase, lobe.baseY());
                highestTop = Math.max(highestTop, lobe.topY());
                footprint = Math.max(
                        footprint,
                        horizontalDistance(Vec3.ZERO, lobe.center())
                                + lobe.targetRadius() * 0.96D
                );
                if (lobe.tier() == CloudMorphologyMemberTier.BASE) {
                    baseLobes.add(lobe);
                    observedBaseWidthHeight = Math.max(observedBaseWidthHeight, widthHeight);
                    highestBaseTop = Math.max(highestBaseTop, lobe.topY());
                } else {
                    observedUpperWidthHeight = Math.max(observedUpperWidthHeight, widthHeight);
                    minimumUpperMinorWidthHeight = Math.min(
                            minimumUpperMinorWidthHeight,
                            widthHeight * 0.90D
                    );
                    minimumUpperOffset = Math.min(
                            minimumUpperOffset,
                            horizontalDistance(Vec3.ZERO, lobe.center()) / plan.radius()
                    );
                    if (lobe.tier() == CloudMorphologyMemberTier.MIDDLE) {
                        middleLobes.add(lobe);
                    } else if (lobe.tier() == CloudMorphologyMemberTier.CROWN) {
                        crownLobes.add(lobe);
                    }
                }
            }
            double groupHeight = (highestTop - lowestBase) / plan.radius();
            double footprintRatio = footprint / plan.radius();
            double projectedWidth = maximumProjectedWidth(lobes);
            double[] projectedRange = projectedWidthRange(lobes, 0.90D);
            double projectedWidthRatio = projectedRange[0] / plan.radius();
            double projectedAnisotropy = projectedRange[1]
                    / Math.max(1.0E-6D, projectedRange[0]);
            double groupWidthHeight = projectedWidth / Math.max(1.0E-6D, highestTop - lowestBase);
            minimumFootprint = Math.min(minimumFootprint, footprintRatio);
            maximumFootprint = Math.max(maximumFootprint, footprintRatio);
            minimumGroupHeight = Math.min(minimumGroupHeight, groupHeight);
            maximumGroupHeight = Math.max(maximumGroupHeight, groupHeight);
            minimumGroupWidthHeight = Math.min(minimumGroupWidthHeight, groupWidthHeight);
            maximumGroupWidthHeight = Math.max(maximumGroupWidthHeight, groupWidthHeight);
            minimumProjectedWidthRatio = Math.min(
                    minimumProjectedWidthRatio,
                    projectedWidthRatio
            );
            maximumProjectedAnisotropy = Math.max(
                    maximumProjectedAnisotropy,
                    projectedAnisotropy
            );
            minimumMiddleSeparation = Math.min(
                    minimumMiddleSeparation,
                    minimumPairSeparation(middleLobes, plan.radius())
            );
            if (crownLobes.size() > 1) {
                minimumCrownSeparation = Math.min(
                        minimumCrownSeparation,
                        minimumPairSeparation(crownLobes, plan.radius())
                );
            }
            for (PuffLobe child : upperLobes(lobes)) {
                List<PuffLobe> parentTier = child.tier() == CloudMorphologyMemberTier.MIDDLE
                        ? baseLobes
                        : middleLobes;
                PuffLobe parent = nearestHorizontalLobe(child, parentTier);
                double separation = horizontalDistance(child.center(), parent.center());
                minimumUpperParentSeparation = Math.min(
                        minimumUpperParentSeparation,
                        separation / plan.radius()
                );
                double junctionY = child.baseY() + Math.min(
                        4.0D,
                        (child.topY() - child.baseY()) * 0.20D
                );
                double childSupport = minimumAnalyticSupportRadiusAtHeight(
                        child,
                        junctionY,
                        0.15D
                );
                double parentSupport = minimumAnalyticSupportRadiusAtHeight(
                        parent,
                        junctionY,
                        0.15D
                );
                minimumJunctionProtrusion = Math.min(
                        minimumJunctionProtrusion,
                        separation + childSupport - parentSupport
                );
                minimumJunctionBridge = Math.min(
                        minimumJunctionBridge,
                        parentSupport + childSupport - separation
                );
                minimumUpperSilhouetteExposure = Math.min(
                        minimumUpperSilhouetteExposure,
                        maximumAnalyticSilhouetteExposure(child, lobes, 0.15D)
                );
            }
            for (PuffLobe lobe : lobes) {
                if (lobe.tier() == CloudMorphologyMemberTier.CROWN) {
                    minimumCrownRise = Math.min(
                            minimumCrownRise,
                            (lobe.topY() - highestBaseTop) / plan.radius()
                    );
                }
            }
            if (analyticComponentCount(lobes, 0.02D) > 1) {
                disconnected02++;
            }
            if (analyticComponentCount(lobes, 0.05D) > 1) {
                disconnected05++;
            }
            if (analyticComponentCount(lobes, 0.15D) > 1) {
                disconnected15++;
            }
            double lowerSliceY = lowestBase + plan.radius() * 0.12D;
            if (analyticComponentCountAtHeight(baseLobes, lowerSliceY, 0.02D) > 1) {
                disconnectedBaseSlice++;
            }
        }

        System.out.printf(
                Locale.ROOT,
                "PUFF analytic type=%s samples=%d disconnected[.02/.05/.15/baseSlice]=%d/%d/%d/%d"
                        + " widthHeight[baseMax/upperMax/upperMinorMin]=%.4f/%.4f/%.4f"
                        + " footprint[min/max]=%.4f/%.4f groupHeight[min/max]=%.4f/%.4f"
                        + " groupWidthHeight[min/max]=%.4f/%.4f projected[minWidth/anisoMax]=%.4f/%.4f upperOffsetMin=%.4f"
                        + " middleSeparationMin=%.4f crownSeparationMin=%s crownRiseMin=%.4f"
                        + " upperParentSeparationMin=%.4f junction[protrusion/bridge]=%.3f/%.3f"
                        + " upperSilhouetteExposureMin=%.3f%n",
                cloudTypeId,
                sampleCount,
                disconnected02,
                disconnected05,
                disconnected15,
                disconnectedBaseSlice,
                observedBaseWidthHeight,
                observedUpperWidthHeight,
                minimumUpperMinorWidthHeight,
                minimumFootprint,
                maximumFootprint,
                minimumGroupHeight,
                maximumGroupHeight,
                minimumGroupWidthHeight,
                maximumGroupWidthHeight,
                minimumProjectedWidthRatio,
                maximumProjectedAnisotropy,
                minimumUpperOffset,
                minimumMiddleSeparation,
                Double.isFinite(minimumCrownSeparation)
                        ? String.format(Locale.ROOT, "%.4f", minimumCrownSeparation)
                        : "n/a",
                minimumCrownRise,
                minimumUpperParentSeparation,
                minimumJunctionProtrusion,
                minimumJunctionBridge,
                minimumUpperSilhouetteExposure
        );
        // Do not make this legacy analytic-mass graph the rendered acceptance
        // gate. The post-union carrier test above owns that contract.
        requireAtMost(
                cloudTypeId + " base rendered width/height",
                observedBaseWidthHeight,
                maximumBaseWidthHeight
        );
        requireAtMost(
                cloudTypeId + " upper rendered width/height",
                observedUpperWidthHeight,
                maximumUpperWidthHeight
        );
        requireAtLeast(
                cloudTypeId + " upper minor rendered width/height",
                minimumUpperMinorWidthHeight,
                0.65D
        );
        requireAtLeast(cloudTypeId + " upper offset", minimumUpperOffset, 0.24D);
        requireAtLeast(cloudTypeId + " middle separation", minimumMiddleSeparation, 0.43D);
        requireAtLeast(
                cloudTypeId + " upper-parent separation",
                minimumUpperParentSeparation,
                0.17D
        );
        // Do not require either protrusion or a wide disc exactly four blocks
        // above an upper member's root. Structured MIDDLE/CROWN roots are
        // intentionally embedded and close at baseY; requiring the former
        // protruding three-block bridge would recreate the planar mushroom
        // shelf. validateContinuousCarrierTopology() is the stronger
        // continuity authority (17 shared heights x 49 corridor points under
        // the most erosive production carrier), while the exposure check below
        // still rejects an upper lobe swallowed at every visible height.
        requireAtLeast(
                cloudTypeId + " upper silhouette exposure",
                minimumUpperSilhouetteExposure,
                0.08D
        );
        boolean humilis = "cumulus_humilis".equals(cloudTypeId);
        requireRange(
                cloudTypeId + " analytic footprint",
                minimumFootprint,
                humilis ? 0.68D : 0.78D,
                humilis ? 0.95D : 1.04D
        );
        requireAtMost(
                cloudTypeId + " analytic footprint maximum",
                maximumFootprint,
                humilis ? 0.95D : 1.04D
        );
        requireRange(
                cloudTypeId + " group height minimum",
                minimumGroupHeight,
                humilis ? 0.90D : 1.15D,
                humilis ? 1.14D : 1.47D
        );
        requireAtMost(
                cloudTypeId + " group height maximum",
                maximumGroupHeight,
                humilis ? 1.14D : 1.47D
        );
        requireRange(
                cloudTypeId + " group width/height minimum",
                minimumGroupWidthHeight,
                humilis ? 1.20D : 0.95D,
                humilis ? 1.80D : 1.50D
        );
        requireAtMost(
                cloudTypeId + " group width/height maximum",
                maximumGroupWidthHeight,
                humilis ? 1.80D : 1.50D
        );
        requireAtLeast(
                cloudTypeId + " minimum projected width",
                minimumProjectedWidthRatio,
                humilis ? 0.90D : 1.00D
        );
        requireAtMost(
                cloudTypeId + " projected anisotropy",
                maximumProjectedAnisotropy,
                1.55D
        );
        if (!humilis) {
            requireAtLeast(cloudTypeId + " crown separation", minimumCrownSeparation, 0.32D);
        }
        requireAtLeast(cloudTypeId + " crown rise above base", minimumCrownRise, 0.12D);
    }

    /**
     * Replays the shader's continuous PUFF carrier with its most erosive
     * world-stable carrier signal and the minimum lifecycle/material product.
     * This is the rendered-connectivity authority; the older analytic metrics
     * above remain useful morphology diagnostics only.
     */
    private static void validateContinuousCarrierTopology(String cloudTypeId, int sampleCount) {
        final double densityThreshold = 0.0008D;
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cloudTypeId);
        int disconnected = 0;
        int outsideEnvelopeLeaks = 0;
        int outsideSamples = 0;
        double minimumParentCorridor = Double.POSITIVE_INFINITY;
        double minimumBaseCorridor = Double.POSITIVE_INFINITY;
        double maximumPermutationDelta = 0.0D;
        double minimumMaterialDensity = Double.POSITIVE_INFINITY;

        for (int sample = 0; sample < sampleCount; sample++) {
            RandomSource random = RandomSource.create(0x50414646L + sample * 0x9E3779B9L);
            CloudMorphologyGenerators.SpawnPlan plan =
                    CloudMorphologyGenerators.createSpawnPlan(definition, random);
            if (!plan.hasHierarchicalPuff()) {
                throw new IllegalStateException(cloudTypeId + " lacks hierarchical PUFF descriptors");
            }

            List<PuffLobe> lobes = new ArrayList<>(plan.puffLobes().size());
            for (CloudMorphologyGenerators.PuffLobeSpec spec : plan.puffLobes()) {
                lobes.add(new PuffLobe(
                        new Vec3(spec.offsetX(), spec.offsetY(), spec.offsetZ()),
                        spec.targetRadius() * 0.96D,
                        spec.targetRadius(),
                        spec.baseOffsetY(),
                        spec.topOffsetY(),
                        spec.tier()
                ));
            }

            // PuffMedia.z maps [0,1] into this non-wrapping interval. The
            // lower endpoint produces the conservative root/equator envelope.
            List<CarrierDescriptor> descriptors = carrierDescriptors(lobes, 0.17320508D);
            int components = carrierComponentCount(descriptors, lobes, densityThreshold);
            if (components != 1) {
                disconnected++;
                System.out.printf(
                        Locale.ROOT,
                        "PUFF carrier disconnect type=%s sample=%d components=%d%n",
                        cloudTypeId,
                        sample,
                        components
                );
                for (int lobeIndex = 0; lobeIndex < lobes.size(); lobeIndex++) {
                    PuffLobe lobe = lobes.get(lobeIndex);
                    System.out.printf(
                            Locale.ROOT,
                            "  lobe=%d tier=%s center=(%.6f,%.6f,%.6f) radius=%.6f"
                                    + " baseTop=%.6f..%.6f%n",
                            lobeIndex,
                            lobe.tier(),
                            lobe.center().x,
                            lobe.center().y,
                            lobe.center().z,
                            lobe.targetRadius(),
                            lobe.baseY(),
                            lobe.topY()
                    );
                }
                List<PuffLobe> diagnosticBase = lobes.stream()
                        .filter(lobe -> lobe.tier() == CloudMorphologyMemberTier.BASE)
                        .toList();
                List<PuffLobe> diagnosticMiddle = lobes.stream()
                        .filter(lobe -> lobe.tier() == CloudMorphologyMemberTier.MIDDLE)
                        .toList();
                for (int childIndex = 0; childIndex < lobes.size(); childIndex++) {
                    PuffLobe child = lobes.get(childIndex);
                    List<PuffLobe> possibleParents = child.tier() == CloudMorphologyMemberTier.MIDDLE
                            ? diagnosticBase
                            : child.tier() == CloudMorphologyMemberTier.CROWN
                            ? diagnosticMiddle
                            : List.of();
                    for (PuffLobe possibleParent : possibleParents) {
                        int parentIndex = lobes.indexOf(possibleParent);
                        System.out.printf(
                                Locale.ROOT,
                                "  corridor child=%d parent=%d value=%.8f%n",
                                childIndex,
                                parentIndex,
                                maximumCarrierCorridorMinimum(
                                        descriptors,
                                        possibleParent,
                                        child
                                )
                        );
                    }
                }
            }

            List<PuffLobe> baseLobes = lobes.stream()
                    .filter(lobe -> lobe.tier() == CloudMorphologyMemberTier.BASE)
                    .toList();
            List<PuffLobe> middleLobes = lobes.stream()
                    .filter(lobe -> lobe.tier() == CloudMorphologyMemberTier.MIDDLE)
                    .toList();
            PuffLobe anchor = baseLobes.get(0);
            for (int index = 1; index < baseLobes.size(); index++) {
                minimumBaseCorridor = Math.min(
                        minimumBaseCorridor,
                        maximumCarrierCorridorMinimum(
                                descriptors,
                                anchor,
                                baseLobes.get(index)
                        )
                );
            }
            for (PuffLobe child : upperLobes(lobes)) {
                PuffLobe parent = nearestHorizontalLobe(
                        child,
                        child.tier() == CloudMorphologyMemberTier.MIDDLE
                                ? baseLobes
                                : middleLobes
                );
                minimumParentCorridor = Math.min(
                        minimumParentCorridor,
                        maximumCarrierCorridorMinimum(descriptors, parent, child)
                );
            }

            OutsideEnvelopeSummary outside = verifyNoCarrierOutsideEnvelope(descriptors);
            outsideEnvelopeLeaks += outside.leaks();
            outsideSamples += outside.samples();

            PermutationSummary permutation = verifyCarrierPermutationDeterminism(descriptors, lobes);
            maximumPermutationDelta = Math.max(
                    maximumPermutationDelta,
                    permutation.maximumDelta()
            );
            minimumMaterialDensity = Math.min(
                    minimumMaterialDensity,
                    permutation.minimumPositiveDensity()
            );
        }

        System.out.printf(
                Locale.ROOT,
                "PUFF carrier type=%s samples=%d carrier=0.000 material=0.186 threshold=.0008"
                        + " disconnected=%d parentCorridorMin=%.8f baseCorridorMin=%.8f"
                        + " outsideEnvelopeLeaks=%d/%d permutationDeltaMax=%.3e"
                        + " sampledPositiveDensityMin=%.8f%n",
                cloudTypeId,
                sampleCount,
                disconnected,
                minimumParentCorridor,
                minimumBaseCorridor,
                outsideEnvelopeLeaks,
                outsideSamples,
                maximumPermutationDelta,
                minimumMaterialDensity
        );
        if (disconnected != 0) {
            throw new IllegalStateException(
                    cloudTypeId + " continuous PUFF carrier disconnected layouts=" + disconnected
            );
        }
        requireAtLeast(
                cloudTypeId + " continuous PUFF parent-child corridor",
                minimumParentCorridor,
                densityThreshold
        );
        requireAtLeast(
                cloudTypeId + " continuous PUFF base corridor",
                minimumBaseCorridor,
                densityThreshold
        );
        if (outsideEnvelopeLeaks != 0) {
            throw new IllegalStateException(
                    cloudTypeId + " continuous PUFF density escaped envelope leaks="
                            + outsideEnvelopeLeaks + "/" + outsideSamples
            );
        }
        requireAtMost(
                cloudTypeId + " continuous PUFF permutation delta",
                maximumPermutationDelta,
                1.0E-12D
        );
    }

    private static List<CarrierDescriptor> carrierDescriptors(
            List<PuffLobe> lobes,
            double lobePhase
    ) {
        List<CarrierDescriptor> descriptors = new ArrayList<>(lobes.size());
        for (PuffLobe lobe : lobes) {
            descriptors.add(new CarrierDescriptor(lobe, lobePhase));
        }
        return List.copyOf(descriptors);
    }

    private static int carrierComponentCount(
            List<CarrierDescriptor> descriptors,
            List<PuffLobe> lobes,
            double threshold
    ) {
        boolean[] visited = new boolean[lobes.size()];
        int components = 0;
        for (int start = 0; start < lobes.size(); start++) {
            if (visited[start]) {
                continue;
            }
            components++;
            int[] queue = new int[lobes.size()];
            int head = 0;
            int tail = 0;
            visited[start] = true;
            queue[tail++] = start;
            while (head < tail) {
                int current = queue[head++];
                for (int candidate = 0; candidate < lobes.size(); candidate++) {
                    if (visited[candidate]
                            || maximumCarrierCorridorMinimum(
                            descriptors,
                            lobes.get(current),
                            lobes.get(candidate)
                    ) < threshold) {
                        continue;
                    }
                    visited[candidate] = true;
                    queue[tail++] = candidate;
                }
            }
        }
        return components;
    }

    /**
     * Finds the strongest straight corridor in the members' shared vertical
     * interval, then returns that corridor's weakest density. A positive result
     * proves a continuous material path rather than a single overlap sample.
     */
    private static double maximumCarrierCorridorMinimum(
            List<CarrierDescriptor> descriptors,
            PuffLobe first,
            PuffLobe second
    ) {
        double minimumY = Math.max(first.baseY(), second.baseY());
        double maximumY = Math.min(first.topY(), second.topY());
        if (maximumY <= minimumY) {
            return 0.0D;
        }
        double strongestCorridor = 0.0D;
        final int heightSamples = 17;
        final int lineSamples = 48;
        for (int heightSample = 0; heightSample < heightSamples; heightSample++) {
            double y = minimumY + (maximumY - minimumY)
                    * (heightSample + 0.5D) / heightSamples;
            double corridorMinimum = Double.POSITIVE_INFINITY;
            for (int lineSample = 0; lineSample <= lineSamples; lineSample++) {
                double t = (double) lineSample / lineSamples;
                Vec3 point = new Vec3(
                        lerp(t, first.center().x, second.center().x),
                        y,
                        lerp(t, first.center().z, second.center().z)
                );
                corridorMinimum = Math.min(
                        corridorMinimum,
                        continuousCarrierField(descriptors, point).density()
                );
            }
            strongestCorridor = Math.max(strongestCorridor, corridorMinimum);
        }
        return strongestCorridor;
    }

    private static OutsideEnvelopeSummary verifyNoCarrierOutsideEnvelope(
            List<CarrierDescriptor> descriptors
    ) {
        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        int leaks = 0;
        int samples = 0;
        for (CarrierDescriptor descriptor : descriptors) {
            PuffLobe lobe = descriptor.lobe();
            double conservativeRadius = lobe.targetRadius() * 1.05D;
            minimumX = Math.min(minimumX, lobe.center().x - conservativeRadius);
            maximumX = Math.max(maximumX, lobe.center().x + conservativeRadius);
            minimumY = Math.min(minimumY, lobe.baseY());
            maximumY = Math.max(maximumY, lobe.topY());
            minimumZ = Math.min(minimumZ, lobe.center().z - conservativeRadius);
            maximumZ = Math.max(maximumZ, lobe.center().z + conservativeRadius);

            double middleY = (lobe.baseY() + lobe.topY()) * 0.5D;
            CarrierLobeSample below = continuousCarrierLobeSample(
                    descriptor,
                    new Vec3(lobe.center().x, lobe.baseY(), lobe.center().z)
            );
            CarrierLobeSample above = continuousCarrierLobeSample(
                    descriptor,
                    new Vec3(lobe.center().x, lobe.topY(), lobe.center().z)
            );
            CarrierLobeSample radial = continuousCarrierLobeSample(
                    descriptor,
                    new Vec3(
                            lobe.center().x + conservativeMinorRadius(lobe) * 1.01D,
                            middleY,
                            lobe.center().z
                    )
            );
            samples += 3;
            if (below.envelopeDepth() != 0.0D) {
                leaks++;
            }
            if (above.envelopeDepth() != 0.0D) {
                leaks++;
            }
            if (radial.envelopeDepth() != 0.0D) {
                leaks++;
            }
        }

        double padding = 1.0D;
        for (int first = 0; first < 5; first++) {
            double u = (double) first / 4.0D;
            for (int second = 0; second < 5; second++) {
                double v = (double) second / 4.0D;
                double x = lerp(u, minimumX, maximumX);
                double y = lerp(v, minimumY, maximumY);
                double z = lerp(u, minimumZ, maximumZ);
                Vec3[] shell = {
                        new Vec3(minimumX - padding, y, z),
                        new Vec3(maximumX + padding, y, z),
                        new Vec3(x, minimumY - padding, z),
                        new Vec3(x, maximumY + padding, z),
                        new Vec3(x, y, minimumZ - padding),
                        new Vec3(x, y, maximumZ + padding)
                };
                for (Vec3 point : shell) {
                    CarrierFieldSample field = continuousCarrierField(descriptors, point);
                    samples++;
                    if (field.envelope() != 0.0D || field.density() != 0.0D) {
                        leaks++;
                    }
                }
            }
        }
        return new OutsideEnvelopeSummary(leaks, samples);
    }

    private static PermutationSummary verifyCarrierPermutationDeterminism(
            List<CarrierDescriptor> descriptors,
            List<PuffLobe> lobes
    ) {
        List<CarrierDescriptor> reversed = new ArrayList<>(descriptors.size());
        for (int index = descriptors.size() - 1; index >= 0; index--) {
            reversed.add(descriptors.get(index));
        }
        List<CarrierDescriptor> rotated = new ArrayList<>(descriptors.size());
        int offset = Math.max(1, descriptors.size() / 2);
        for (int index = 0; index < descriptors.size(); index++) {
            rotated.add(descriptors.get((index + offset) % descriptors.size()));
        }

        double maximumDelta = 0.0D;
        double minimumPositiveDensity = Double.POSITIVE_INFINITY;
        List<Vec3> probes = new ArrayList<>(lobes.size() * 2);
        for (PuffLobe lobe : lobes) {
            probes.add(new Vec3(
                    lobe.center().x,
                    (lobe.baseY() + lobe.topY()) * 0.5D,
                    lobe.center().z
            ));
        }
        for (int index = 1; index < lobes.size(); index++) {
            PuffLobe first = lobes.get(index - 1);
            PuffLobe second = lobes.get(index);
            double minimumY = Math.max(first.baseY(), second.baseY());
            double maximumY = Math.min(first.topY(), second.topY());
            if (maximumY > minimumY) {
                probes.add(new Vec3(
                        (first.center().x + second.center().x) * 0.5D,
                        (minimumY + maximumY) * 0.5D,
                        (first.center().z + second.center().z) * 0.5D
                ));
            }
        }
        for (Vec3 probe : probes) {
            double reference = continuousCarrierField(descriptors, probe).density();
            double reverseValue = continuousCarrierField(reversed, probe).density();
            double rotatedValue = continuousCarrierField(rotated, probe).density();
            maximumDelta = Math.max(
                    maximumDelta,
                    Math.max(
                            Math.abs(reference - reverseValue),
                            Math.abs(reference - rotatedValue)
                    )
            );
            if (reference > 0.0D) {
                minimumPositiveDensity = Math.min(minimumPositiveDensity, reference);
            }
        }
        return new PermutationSummary(maximumDelta, minimumPositiveDensity);
    }

    /** Mirrors resolvePuffContinuousField and accumulatePuffContinuousSample. */
    private static CarrierFieldSample continuousCarrierField(
            List<CarrierDescriptor> descriptors,
            Vec3 point
    ) {
        double envelopeMaximum = 0.0D;
        double envelopeSecond = 0.0D;
        double weightedMaximum = 0.0D;
        double weightedSecond = 0.0D;
        double baseRootMaximum = 0.0D;
        double baseRootSecond = 0.0D;
        for (CarrierDescriptor descriptor : descriptors) {
            CarrierLobeSample sample = continuousCarrierLobeSample(descriptor, point);
            double oldEnvelopeMaximum = envelopeMaximum;
            envelopeMaximum = Math.max(envelopeMaximum, sample.envelopeDepth());
            envelopeSecond = Math.max(
                    envelopeSecond,
                    Math.min(oldEnvelopeMaximum, sample.envelopeDepth())
            );
            double oldWeightedMaximum = weightedMaximum;
            weightedMaximum = Math.max(weightedMaximum, sample.weightedEnvelopeDepth());
            weightedSecond = Math.max(
                    weightedSecond,
                    Math.min(oldWeightedMaximum, sample.weightedEnvelopeDepth())
            );
            double baseRoot = descriptor.lobe().tier() == CloudMorphologyMemberTier.BASE
                    ? sample.envelopeDepth()
                    * (1.0D - shaderSmoothstep(0.34D, 0.55D, sample.height01()))
                    : 0.0D;
            double oldBaseRootMaximum = baseRootMaximum;
            baseRootMaximum = Math.max(baseRootMaximum, baseRoot);
            baseRootSecond = Math.max(
                    baseRootSecond,
                    Math.min(oldBaseRootMaximum, baseRoot)
            );
        }

        double envelope = resolvePuffAccumulation(envelopeMaximum, envelopeSecond);
        if (envelope <= 0.0D) {
            return new CarrierFieldSample(0.0D, 0.0D);
        }
        double weighted = resolvePuffAccumulation(weightedMaximum, weightedSecond);
        double overlap = envelopeSecond;
        double baseRootOverlap = baseRootSecond;
        double materialFactor = Math.max(
                0.0D,
                Math.min(1.0D, weighted / Math.max(envelope, 0.0001D))
        );

        // carrierSignal=0 is the strongest supported erosion case.
        double carrier = shaderSmoothstep(0.28D, 0.68D, 0.0D);
        double exposedIso = lerp(carrier, 0.34D, 0.08D);
        double coreProtection = shaderSmoothstep(0.38D, 0.55D, envelope);
        double junctionProtection = shaderSmoothstep(0.015D, 0.075D, overlap);
        double baseJunctionProtection = shaderSmoothstep(0.004D, 0.016D, baseRootOverlap);
        double protection = Math.max(
                coreProtection,
                Math.max(junctionProtection, baseJunctionProtection)
        );
        double surfaceIso = lerp(protection, exposedIso, 0.012D);
        double continuousShape = Math.max(
                (envelope - surfaceIso) / Math.max(1.0D - surfaceIso, 0.001D),
                0.0D
        );
        return new CarrierFieldSample(envelope, continuousShape * materialFactor);
    }

    private static double resolvePuffAccumulation(double maximum, double secondStrongest) {
        return maximum + 0.25D * secondStrongest * (1.0D - maximum);
    }

    /** Mirrors directPuffLobeSample's envelope/weighted components. */
    private static CarrierLobeSample continuousCarrierLobeSample(
            CarrierDescriptor descriptor,
            Vec3 point
    ) {
        PuffLobe lobe = descriptor.lobe();
        double span = Math.max(1.0D, lobe.topY() - lobe.baseY());
        double h = (point.y - lobe.baseY()) / span;
        if (h <= 0.0D || h >= 1.0D) {
            return CarrierLobeSample.EMPTY;
        }
        double dx = point.x - lobe.center().x;
        double dz = point.z - lobe.center().z;
        // A circle at the production minor radius is the conservative form of
        // the shader's rotated local/radii evaluation for every view axis.
        double radial = Math.sqrt(dx * dx + dz * dz) / conservativeMinorRadius(lobe);
        if (radial >= 1.05D) {
            return CarrierLobeSample.EMPTY;
        }
        double envelopeDepth = structuredPuffEnvelopeDepth(
                radial,
                h,
                descriptor.lobePhase(),
                lobe.tier(),
                span
        );
        // lifecycleEnvelope >= .30 and materialMass >= .62 in the shader.
        double minimumLifecycleMaterial = 0.30D * 0.62D;
        return new CarrierLobeSample(
                envelopeDepth,
                envelopeDepth * minimumLifecycleMaterial,
                h
        );
    }

    private static double analyticRadiusAtHeight(
            double height01,
            double phase,
            CloudMorphologyMemberTier tier
    ) {
        double peakHeight;
        double rootRadius;
        double equatorRadius;
        double upperPower;
        switch (tier) {
            case BASE -> {
                peakHeight = lerp(phase, 0.32D, 0.38D);
                rootRadius = lerp(phase, 0.70D, 0.76D);
                equatorRadius = lerp(phase, 0.92D, 0.98D);
                upperPower = lerp(phase, 0.95D, 1.15D);
            }
            case MIDDLE -> {
                peakHeight = lerp(phase, 0.38D, 0.44D);
                rootRadius = 0.0D;
                equatorRadius = lerp(phase, 0.94D, 1.00D);
                upperPower = lerp(phase, 1.30D, 1.55D);
            }
            case CROWN -> {
                peakHeight = lerp(phase, 0.43D, 0.50D);
                rootRadius = 0.0D;
                equatorRadius = lerp(phase, 0.90D, 0.96D);
                upperPower = lerp(phase, 1.70D, 2.00D);
            }
            case UNKNOWN -> {
                throw new IllegalStateException("Structured topology cannot evaluate UNKNOWN tier");
            }
            default -> throw new IllegalStateException("Unhandled PUFF tier " + tier);
        }
        double rootRatio = rootRadius / Math.max(equatorRadius, 0.001D);
        double verticalAtBase = -Math.sqrt(Math.max(0.0D, 1.0D - rootRatio * rootRatio));
        double verticalCoordinate = height01 <= peakHeight
                ? lerp(
                shaderSmoothstep(0.0D, peakHeight, height01),
                verticalAtBase,
                0.0D
        )
                : Math.pow(
                        shaderSmoothstep(
                                0.0D,
                                1.0D,
                                Math.max(
                                        0.0D,
                                        Math.min(
                                                1.0D,
                                                (height01 - peakHeight)
                                                        / Math.max(1.0D - peakHeight, 0.001D)
                                        )
                                )
                        ),
                        upperPower
                );
        return equatorRadius * Math.sqrt(Math.max(
                0.0D,
                1.0D - verticalCoordinate * verticalCoordinate
        ));
    }

    private static double structuredPuffEnvelopeDepth(
            double radial,
            double height01,
            double phase,
            CloudMorphologyMemberTier tier,
            double span
    ) {
        if (height01 <= 0.0D || height01 >= 1.0D) {
            return 0.0D;
        }
        double peakHeight;
        double rootRadius;
        double equatorRadius;
        double upperPower;
        switch (tier) {
            case BASE -> {
                peakHeight = lerp(phase, 0.32D, 0.38D);
                rootRadius = lerp(phase, 0.70D, 0.76D);
                equatorRadius = lerp(phase, 0.92D, 0.98D);
                upperPower = lerp(phase, 0.95D, 1.15D);
            }
            case MIDDLE -> {
                peakHeight = lerp(phase, 0.38D, 0.44D);
                rootRadius = 0.0D;
                equatorRadius = lerp(phase, 0.94D, 1.00D);
                upperPower = lerp(phase, 1.30D, 1.55D);
            }
            case CROWN -> {
                peakHeight = lerp(phase, 0.43D, 0.50D);
                rootRadius = 0.0D;
                equatorRadius = lerp(phase, 0.90D, 0.96D);
                upperPower = lerp(phase, 1.70D, 2.00D);
            }
            case UNKNOWN -> throw new IllegalStateException(
                    "Structured topology cannot evaluate UNKNOWN tier"
            );
            default -> throw new IllegalStateException("Unhandled PUFF tier " + tier);
        }
        double rootRatio = rootRadius / Math.max(equatorRadius, 0.001D);
        double verticalAtBase = -Math.sqrt(Math.max(0.0D, 1.0D - rootRatio * rootRatio));
        double verticalCoordinate = height01 <= peakHeight
                ? lerp(
                shaderSmoothstep(0.0D, peakHeight, height01),
                verticalAtBase,
                0.0D
        )
                : Math.pow(
                        shaderSmoothstep(
                                0.0D,
                                1.0D,
                                Math.max(
                                        0.0D,
                                        Math.min(
                                                1.0D,
                                                (height01 - peakHeight)
                                                        / Math.max(1.0D - peakHeight, 0.001D)
                                        )
                                )
                        ),
                        upperPower
                );
        double q = Math.sqrt(
                Math.pow(radial / Math.max(equatorRadius, 0.001D), 2.0D)
                        + verticalCoordinate * verticalCoordinate
        );
        double depth = Math.max(0.0D, Math.min(1.0D, 1.0D - q));
        if (tier == CloudMorphologyMemberTier.BASE) {
            double featherScale = Math.min(1.0D, Math.max(span, 1.0D) * 0.70D / 9.0D);
            double baseFeatherH = 5.0D * featherScale / Math.max(span, 1.0D);
            depth *= shaderSmoothstep(0.0D, baseFeatherH, height01);
        }
        return depth;
    }

    private static double conservativeMinorRadius(PuffLobe lobe) {
        return Math.max(1.0D, lobe.targetRadius() * 0.96D * 0.96D * 0.90D);
    }

    private static double maximumProjectedWidth(List<PuffLobe> lobes) {
        return projectedWidthRange(lobes, 1.0D)[1];
    }

    private static List<PuffLobe> upperLobes(List<PuffLobe> lobes) {
        List<PuffLobe> upper = new ArrayList<>(lobes.size());
        for (PuffLobe lobe : lobes) {
            if (lobe.tier() == CloudMorphologyMemberTier.MIDDLE
                    || lobe.tier() == CloudMorphologyMemberTier.CROWN) {
                upper.add(lobe);
            }
        }
        return upper;
    }

    private static PuffLobe nearestHorizontalLobe(PuffLobe source, List<PuffLobe> candidates) {
        PuffLobe nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (PuffLobe candidate : candidates) {
            double distance = horizontalDistance(source.center(), candidate.center());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        if (nearest == null) {
            throw new IllegalStateException("PUFF upper lobe has no lower-tier parent candidate");
        }
        return nearest;
    }

    private static double minimumAnalyticSupportRadiusAtHeight(
            PuffLobe lobe,
            double y,
            double threshold
    ) {
        if (minimumAnalyticLobeShape(lobe, 0.0D, y) < threshold) {
            return 0.0D;
        }
        double low = 0.0D;
        double high = lobe.targetRadius() * 0.96D * 0.96D * 0.90D * 1.05D;
        for (int iteration = 0; iteration < 48; iteration++) {
            double middle = (low + high) * 0.5D;
            if (minimumAnalyticLobeShape(lobe, middle, y) >= threshold) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static double maximumAnalyticSilhouetteExposure(
            PuffLobe source,
            List<PuffLobe> lobes,
            double threshold
    ) {
        double maximumExposure = 0.0D;
        final int heightSamples = 9;
        final int angleSamples = 64;
        for (int heightSample = 1; heightSample < heightSamples; heightSample++) {
            double y = source.baseY()
                    + (source.topY() - source.baseY()) * heightSample / heightSamples;
            double supportRadius = minimumAnalyticSupportRadiusAtHeight(source, y, threshold);
            if (supportRadius <= 0.0D) {
                continue;
            }
            int exposed = 0;
            for (int angleSample = 0; angleSample < angleSamples; angleSample++) {
                double angle = Math.PI * 2.0D * angleSample / angleSamples;
                double sampleX = source.center().x + Math.cos(angle) * supportRadius;
                double sampleZ = source.center().z + Math.sin(angle) * supportRadius;
                boolean covered = false;
                for (PuffLobe other : lobes) {
                    if (other == source) {
                        continue;
                    }
                    double dx = sampleX - other.center().x;
                    double dz = sampleZ - other.center().z;
                    if (minimumAnalyticLobeShape(
                            other,
                            Math.sqrt(dx * dx + dz * dz),
                            y
                    ) >= threshold) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    exposed++;
                }
            }
            maximumExposure = Math.max(
                    maximumExposure,
                    (double) exposed / angleSamples
            );
        }
        return maximumExposure;
    }

    private static double[] projectedWidthRange(
            List<PuffLobe> lobes,
            double axisScale
    ) {
        double minimumWidth = Double.POSITIVE_INFINITY;
        double maximum = 0.0D;
        for (int direction = 0; direction < 32; direction++) {
            double angle = Math.PI * direction / 32.0D;
            double axisX = Math.cos(angle);
            double axisZ = Math.sin(angle);
            double minimum = Double.POSITIVE_INFINITY;
            double maximumAlongAxis = Double.NEGATIVE_INFINITY;
            for (PuffLobe lobe : lobes) {
                double projectedCentre = lobe.center().x * axisX + lobe.center().z * axisZ;
                double radius = lobe.targetRadius() * 0.96D * axisScale;
                minimum = Math.min(minimum, projectedCentre - radius);
                maximumAlongAxis = Math.max(maximumAlongAxis, projectedCentre + radius);
            }
            double width = maximumAlongAxis - minimum;
            minimumWidth = Math.min(minimumWidth, width);
            maximum = Math.max(maximum, width);
        }
        return new double[]{minimumWidth, maximum};
    }

    private static double minimumPairSeparation(List<PuffLobe> lobes, double planRadius) {
        if (lobes.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }
        double minimum = Double.POSITIVE_INFINITY;
        for (int first = 0; first < lobes.size(); first++) {
            for (int second = first + 1; second < lobes.size(); second++) {
                minimum = Math.min(
                        minimum,
                        horizontalDistance(lobes.get(first).center(), lobes.get(second).center())
                                / planRadius
                );
            }
        }
        return minimum;
    }

    private static int analyticComponentCount(List<PuffLobe> lobes, double threshold) {
        boolean[] visited = new boolean[lobes.size()];
        int components = 0;
        for (int start = 0; start < lobes.size(); start++) {
            if (visited[start]) {
                continue;
            }
            components++;
            visited[start] = true;
            int[] queue = new int[lobes.size()];
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            while (head < tail) {
                int current = queue[head++];
                for (int candidate = 0; candidate < lobes.size(); candidate++) {
                    if (visited[candidate]
                            || analyticPairOverlap(lobes.get(current), lobes.get(candidate)) < threshold) {
                        continue;
                    }
                    visited[candidate] = true;
                    queue[tail++] = candidate;
                }
            }
        }
        return components;
    }

    private static int analyticComponentCountAtHeight(
            List<PuffLobe> lobes,
            double y,
            double threshold
    ) {
        boolean[] visited = new boolean[lobes.size()];
        int components = 0;
        for (int start = 0; start < lobes.size(); start++) {
            if (visited[start]
                    || maximumAnalyticShapeAtHeight(lobes.get(start), y) < threshold) {
                continue;
            }
            components++;
            visited[start] = true;
            int[] queue = new int[lobes.size()];
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            while (head < tail) {
                int current = queue[head++];
                for (int candidate = 0; candidate < lobes.size(); candidate++) {
                    if (visited[candidate]
                            || analyticPairOverlapAtHeight(
                            lobes.get(current),
                            lobes.get(candidate),
                            y
                    ) < threshold) {
                        continue;
                    }
                    visited[candidate] = true;
                    queue[tail++] = candidate;
                }
            }
        }
        return components;
    }

    private static double maximumAnalyticShapeAtHeight(PuffLobe lobe, double y) {
        double strongest = 0.0D;
        double[] phases = {0.17320508D, 0.550643913D, 0.928082746D};
        for (double phase : phases) {
            strongest = Math.max(strongest, analyticLobeShape(lobe, 0.0D, y, phase));
        }
        return strongest;
    }

    private static double analyticPairOverlapAtHeight(
            PuffLobe first,
            PuffLobe second,
            double y
    ) {
        if (y <= first.baseY() || y >= first.topY()
                || y <= second.baseY() || y >= second.topY()) {
            return 0.0D;
        }
        double horizontal = horizontalDistance(first.center(), second.center());
        double strongest = 0.0D;
        final int samples = 48;
        for (int lineSample = 0; lineSample <= samples; lineSample++) {
            double t = (double) lineSample / samples;
            double firstShape = minimumAnalyticLobeShape(first, horizontal * t, y);
            double secondShape = minimumAnalyticLobeShape(
                    second,
                    horizontal * (1.0D - t),
                    y
            );
            strongest = Math.max(strongest, Math.min(firstShape, secondShape));
        }
        return strongest;
    }

    private static double analyticPairOverlap(PuffLobe first, PuffLobe second) {
        double minimumY = Math.max(first.baseY(), second.baseY());
        double maximumY = Math.min(first.topY(), second.topY());
        if (maximumY <= minimumY) {
            return 0.0D;
        }
        double horizontal = horizontalDistance(first.center(), second.center());
        double strongest = 0.0D;
        final int samples = 32;
        for (int ySample = 1; ySample < samples; ySample++) {
            double y = minimumY + (maximumY - minimumY) * ySample / samples;
            for (int lineSample = 0; lineSample <= samples; lineSample++) {
                double t = (double) lineSample / samples;
                double firstShape = minimumAnalyticLobeShape(first, horizontal * t, y);
                double secondShape = minimumAnalyticLobeShape(second, horizontal * (1.0D - t), y);
                strongest = Math.max(strongest, Math.min(firstShape, secondShape));
            }
        }
        return strongest;
    }

    private static double minimumAnalyticLobeShape(PuffLobe lobe, double horizontalDistance, double y) {
        // PuffMedia.z maps [0,1] to this non-wrapping phase interval.
        double[] phases = {0.17320508D, 0.550643913D, 0.928082746D};
        double minimum = Double.POSITIVE_INFINITY;
        for (double phase : phases) {
            minimum = Math.min(minimum, analyticLobeShape(lobe, horizontalDistance, y, phase));
        }
        return minimum;
    }

    private static double analyticLobeShape(
            PuffLobe lobe,
            double horizontalDistance,
            double y,
            double phase
    ) {
        double span = Math.max(1.0D, lobe.topY() - lobe.baseY());
        double h = (y - lobe.baseY()) / span;
        if (h <= 0.0D || h >= 1.0D) {
            return 0.0D;
        }
        // Worst horizontal orientation: the connecting line lies on the 0.90
        // minor axis of the runtime ellipse.
        double minorRadius = Math.max(
                1.0D,
                lobe.targetRadius() * 0.96D * 0.96D * 0.90D
        );
        double radial = horizontalDistance / minorRadius;
        // Conservative initial material mass: shape.w is still below its
        // mature target when the group is born.
        return structuredPuffEnvelopeDepth(
                radial,
                h,
                phase,
                lobe.tier(),
                span
        ) * 0.76D;
    }

    private static double shaderSmoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0D, Math.min(1.0D, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0D - 2.0D * t);
    }

    private static double lerp(double t, double minimum, double maximum) {
        return minimum + (maximum - minimum) * t;
    }

    private static void validatePuffRetargetPreservesRadiusRatios() {
        CloudTypeDefinition humilis = CloudTypeRegistry.getOrDefault("cumulus_humilis");
        CloudMorphologyGenerators.SpawnPlan plan = CloudMorphologyGenerators.createSpawnPlan(
                humilis,
                RandomSource.create(0x5041464652455441L)
        );
        float[] preservedTargets = new float[plan.puffLobes().size()];
        for (int index = 0; index < plan.puffLobes().size(); index++) {
            float targetRadius = plan.puffLobes().get(index).targetRadius();
            preservedTargets[index] = CloudMorphologyGenerators.preservedPuffTargetRadius(
                    targetRadius * 0.96F,
                    targetRadius
            );
            requireFloatBits("structured PUFF target " + index, preservedTargets[index], targetRadius);
        }

        for (int first = 0; first < preservedTargets.length; first++) {
            for (int second = first + 1; second < preservedTargets.length; second++) {
                float beforeRatio = plan.puffLobes().get(first).targetRadius()
                        / plan.puffLobes().get(second).targetRadius();
                float afterRatio = preservedTargets[first] / preservedTargets[second];
                requireFloatBits("structured PUFF radius ratio " + first + "/" + second,
                        afterRatio, beforeRatio);
            }
        }

        // Legacy geometry is not classified by member index. Only the two
        // radius values themselves participate in the production decision.
        requireFloatBits("legacy growing target",
                CloudMorphologyGenerators.preservedPuffTargetRadius(33.0F, 36.0F), 36.0F);
        requireFloatBits("legacy non-shrinking target",
                CloudMorphologyGenerators.preservedPuffTargetRadius(37.0F, 36.0F), 37.0F);
    }

    private static PuffLobe samplePuffLobe(
            Vec3 center,
            CloudMorphologyGenerators.SpawnPlan plan,
            float scale,
            RandomSource random
    ) {
        float radiusJitter = 0.92F + random.nextFloat() * 0.16F;
        double targetRadius = Math.max(6.0F, plan.radius() * scale * radiusJitter);
        // finalDensity, finalCoverage and edgeSoftness jitter.
        consume(random, 3);
        return new PuffLobe(
                center,
                targetRadius * 0.90D,
                targetRadius,
                -plan.baseDrop(),
                plan.topRise(),
                CloudMorphologyMemberTier.BASE
        );
    }

    private static void consume(RandomSource random, int count) {
        for (int index = 0; index < count; index++) {
            random.nextFloat();
        }
    }

    private static int componentCount(List<PuffLobe> clusters, boolean targetRadius) {
        boolean[] visited = new boolean[clusters.size()];
        int components = 0;
        for (int start = 0; start < clusters.size(); start++) {
            if (visited[start]) {
                continue;
            }
            components++;
            visited[start] = true;
            int[] queue = new int[clusters.size()];
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            while (head < tail) {
                int current = queue[head++];
                for (int candidate = 0; candidate < clusters.size(); candidate++) {
                    if (visited[candidate]
                            || separationRatio(
                            clusters.get(current),
                            clusters.get(candidate),
                            targetRadius
                    ) > 1.0D) {
                        continue;
                    }
                    visited[candidate] = true;
                    queue[tail++] = candidate;
                }
            }
        }
        return components;
    }

    private static double nearestSeparationRatio(
            List<PuffLobe> clusters,
            int index,
            boolean targetRadius
    ) {
        double nearest = Double.POSITIVE_INFINITY;
        for (int candidate = 0; candidate < clusters.size(); candidate++) {
            if (candidate != index) {
                nearest = Math.min(
                        nearest,
                        separationRatio(clusters.get(index), clusters.get(candidate), targetRadius)
                );
            }
        }
        return nearest;
    }

    private static double nearestCentreRadiusRatio(List<PuffLobe> clusters, int index) {
        double nearest = Double.POSITIVE_INFINITY;
        PuffLobe source = clusters.get(index);
        for (int candidate = 0; candidate < clusters.size(); candidate++) {
            if (candidate == index) {
                continue;
            }
            PuffLobe other = clusters.get(candidate);
            nearest = Math.min(
                    nearest,
                    horizontalDistance(source.center(), other.center())
                            / Math.max(1.0E-6D, Math.min(source.birthRadius(), other.birthRadius()))
            );
        }
        return nearest;
    }

    private static double structuralRadius(
            List<PuffLobe> clusters,
            int index,
            boolean targetRadius
    ) {
        PuffLobe source = clusters.get(index);
        double radius = targetRadius ? source.targetRadius() : source.birthRadius();
        for (int candidate = 0; candidate < clusters.size(); candidate++) {
            if (candidate == index) {
                continue;
            }
            PuffLobe other = clusters.get(candidate);
            double otherRadius = targetRadius ? other.targetRadius() : other.birthRadius();
            radius = Math.max(
                    radius,
                    horizontalDistance(source.center(), other.center()) + otherRadius
            );
        }
        return radius;
    }

    private static double separationRatio(
            PuffLobe first,
            PuffLobe second,
            boolean targetRadius
    ) {
        double firstRadius = targetRadius ? first.targetRadius() : first.birthRadius();
        double secondRadius = targetRadius ? second.targetRadius() : second.birthRadius();
        return horizontalDistance(first.center(), second.center())
                / Math.max(1.0E-6D, firstRadius + secondRadius);
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double distance3d(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dy = first.y - second.y;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double centreAnisotropy(List<PuffLobe> clusters, double planRadius) {
        double meanX = 0.0D;
        double meanZ = 0.0D;
        for (PuffLobe cluster : clusters) {
            meanX += cluster.center().x;
            meanZ += cluster.center().z;
        }
        meanX /= clusters.size();
        meanZ /= clusters.size();

        double xx = 0.0D;
        double xz = 0.0D;
        double zz = 0.0D;
        for (PuffLobe cluster : clusters) {
            double dx = cluster.center().x - meanX;
            double dz = cluster.center().z - meanZ;
            xx += dx * dx;
            xz += dx * dz;
            zz += dz * dz;
        }
        xx /= clusters.size();
        xz /= clusters.size();
        zz /= clusters.size();
        double trace = xx + zz;
        double discriminant = Math.sqrt(Math.max(0.0D, (xx - zz) * (xx - zz) + 4.0D * xz * xz));
        double major = Math.max(0.0D, (trace + discriminant) * 0.5D);
        double minor = Math.max(0.0D, (trace - discriminant) * 0.5D);
        double epsilon = Math.max(1.0E-9D, planRadius * planRadius * 1.0E-6D);
        return Math.sqrt((major + epsilon) / (minor + epsilon));
    }

    private static double puffCoreFraction(
            double spanWorld,
            CloudMorphologyMemberTier tier
    ) {
        double desiredBase = tier == CloudMorphologyMemberTier.BASE ? 5.0D : 4.0D;
        double desiredTop = tier == CloudMorphologyMemberTier.CROWN ? 3.5D : 4.0D;
        double safeSpan = Math.max(1.0D, spanWorld);
        double desiredTotal = desiredBase + desiredTop;
        double featherScale = Math.min(1.0D, safeSpan * 0.70D / Math.max(0.001D, desiredTotal));
        return 1.0D - desiredTotal * featherScale / safeSpan;
    }

    private static double quantile(List<Double> values, double fraction) {
        values.sort(Double::compareTo);
        int index = (int) Math.round((values.size() - 1) * fraction);
        return values.get(Math.max(0, Math.min(values.size() - 1, index)));
    }

    private record CarrierDescriptor(PuffLobe lobe, double lobePhase) {
    }

    private record CarrierLobeSample(
            double envelopeDepth,
            double weightedEnvelopeDepth,
            double height01
    ) {
        private static final CarrierLobeSample EMPTY = new CarrierLobeSample(0.0D, 0.0D, 0.0D);
    }

    private record CarrierFieldSample(double envelope, double density) {
    }

    private record OutsideEnvelopeSummary(int leaks, int samples) {
    }

    private record PermutationSummary(double maximumDelta, double minimumPositiveDensity) {
    }

    private record PuffLobe(
            Vec3 center,
            double birthRadius,
            double targetRadius,
            double baseY,
            double topY,
            CloudMorphologyMemberTier tier
    ) {
    }

    private record PuffTopologySummary(
            int disconnectedBirth,
            int disconnectedTarget,
            double primaryBirthMaximum,
            double nearestCentreRadiusP05,
            double footprintP50,
            double footprintP95,
            double anisotropyP95,
            double baseSpreadMaximum,
            double shoulderBirthMinimum,
            double shoulderBirthMaximum,
            double shoulderTargetMinimum,
            double shoulderTargetMaximum,
            double minimumBasePairRatio,
            double upperSupportMaximum,
            double upperOffsetMinimum,
            double upperOffsetMaximum,
            double upperProtrusionP05,
            double upperSpanMinimum,
            double upperCoreMinimum,
            double structuralMassP05,
            double structuralMassP95,
            double targetMembersPassingRadius120Percent,
            double groupsWithoutTargetRadius120Percent
    ) {
    }

    private static double correlationSquared(List<Float> x, List<Float> y, int firstIndex) {
        int count = x.size() - firstIndex;
        double meanX = 0.0D;
        double meanY = 0.0D;
        for (int index = firstIndex; index < x.size(); index++) {
            meanX += x.get(index);
            meanY += y.get(index);
        }
        meanX /= count;
        meanY /= count;

        double covariance = 0.0D;
        double varianceX = 0.0D;
        double varianceY = 0.0D;
        for (int index = firstIndex; index < x.size(); index++) {
            double dx = x.get(index) - meanX;
            double dy = y.get(index) - meanY;
            covariance += dx * dy;
            varianceX += dx * dx;
            varianceY += dy * dy;
        }
        if (varianceX <= 0.0D || varianceY <= 0.0D) {
            return 0.0D;
        }
        double correlation = covariance / Math.sqrt(varianceX * varianceY);
        return correlation * correlation;
    }

    private static double circularSeparation(float firstDegrees, float secondDegrees) {
        double difference = Math.abs(firstDegrees - secondDegrees) % 360.0D;
        return Math.min(difference, 360.0D - difference);
    }

    private static double[] stageMeans(List<Float> values) {
        return new double[]{
                mean(values, 0, 4),
                mean(values, 4, 7),
                mean(values, 7, 10),
                mean(values, 10, 12)
        };
    }

    private static double mean(List<Float> values, int startInclusive, int endExclusive) {
        double sum = 0.0D;
        for (int index = startInclusive; index < endExclusive; index++) {
            sum += values.get(index);
        }
        return sum / (endExclusive - startInclusive);
    }

    private static void requireCount(String label, List<Float> values, int expected) {
        if (values.size() != expected) {
            throw new IllegalStateException(label + " expected=" + expected + " actual=" + values.size());
        }
    }

    private static void requireAtMost(String label, double actual, double maximum) {
        if (!Double.isFinite(actual) || actual > maximum) {
            throw new IllegalStateException(label + " maximum=" + maximum + " actual=" + actual);
        }
    }

    private static void requireAtLeast(String label, double actual, double minimum) {
        if (!Double.isFinite(actual) || actual < minimum) {
            throw new IllegalStateException(label + " minimum=" + minimum + " actual=" + actual);
        }
    }

    private static void requireRange(String label, double actual, double minimum, double maximum) {
        if (!Double.isFinite(actual) || actual < minimum || actual > maximum) {
            throw new IllegalStateException(
                    label + " expected=" + minimum + ".." + maximum + " actual=" + actual
            );
        }
    }

    private static void requireFloatBits(String label, float actual, float expected) {
        if (Float.floatToIntBits(actual) != Float.floatToIntBits(expected)) {
            throw new IllegalStateException(label + " expected=" + expected + " actual=" + actual);
        }
    }

}
