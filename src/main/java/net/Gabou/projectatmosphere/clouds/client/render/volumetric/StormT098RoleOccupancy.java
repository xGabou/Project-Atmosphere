package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * T098 root-cause instrumentation: role-resolved occupancy of the live adopted
 * severe system.
 *
 * <p>The T098 captures show adopted CORE and TOWER descriptors producing no
 * visible body. The decision tree asks first whether the CORE/TOWER
 * <em>envelope</em> is already weak or absent - a geometry and placement
 * problem - or whether it is strong and a later stage carves it away. This walks
 * the live descriptor set through the production evaluator, one stage at a time,
 * and reports where each role's signal is still present.
 *
 * <p>Observation only: it reads the published snapshot and calls the same
 * evaluator the render path uses. It changes no descriptor, no envelope and no
 * density, and it runs only when the T098 capture driver asks for it.
 */
final class StormT098RoleOccupancy {

    /** Envelope below this cannot become visible body downstream. */
    private static final double VISIBLE_ENVELOPE = 0.05D;

    /** World-space sampling step, in blocks. */
    private static final double STEP = 24.0D;

    private StormT098RoleOccupancy() {
    }

    /**
     * Measures the adopted system and returns a multi-line report.
     *
     * <p>Every envelope-positive sample is attributed to the role that owns the
     * descriptor producing it, so a role that contributes nothing anywhere shows
     * as a zero rather than being hidden inside a combined group total.
     */

    /**
     * T098 phase 5: per-descriptor envelope-extent clamp statistics.
     *
     * <p>edgeWidthBlocks bounds the coverage boundary by a fraction of the
     * lobe's own half-height, because envelopeFromDistance fades over plus/minus
     * that width isotropically while the width itself is derived from a
     * horizontal extent. The bound is intended to be selective: pathological
     * wide-and-flat descriptors bind, ordinary roles do not.
     *
     * <p>That selectivity was characterised against a single transcribed
     * fixture, where the closest non-ANVIL case was BASE at 0.73 against a bound
     * of 0.75 - a narrow margin. This reports the unclamped ratio and the bind
     * decision for every live descriptor so fresh fixtures either confirm the
     * separation or show it collapsing, without anyone having to infer it from
     * the rendered image.
     */
    private static void appendEnvelopeExtentStatistics(
            StringBuilder out, StormLobeDescriptor[] descriptors) {
        out.append("--- envelope extent clamp (T098 phase 5) ---\n");
        out.append("role|member|unclampedSoftness|halfHeight|ratio|bound|clamped\n");

        java.util.EnumMap<StormLobeDescriptor.Role, double[]> stats =
                new java.util.EnumMap<>(StormLobeDescriptor.Role.class);
        java.util.EnumMap<StormLobeDescriptor.Role, Integer> bound =
                new java.util.EnumMap<>(StormLobeDescriptor.Role.class);
        java.util.EnumMap<StormLobeDescriptor.Role, java.util.List<Double>> ratios =
                new java.util.EnumMap<>(StormLobeDescriptor.Role.class);

        for (StormLobeDescriptor descriptor : descriptors) {
            // The unclamped width is what edgeWidthBlocks would have returned
            // before the extent bound, recomputed here rather than stored so the
            // two cannot drift apart silently.
            double normalized = switch (descriptor.role()) {
                case ANVIL -> Math.max(0.12D, descriptor.edgeSoftness() * 1.65D);
                case BASE -> Math.max(0.06D, descriptor.edgeSoftness() * 0.66D);
                default -> Math.max(0.06D, descriptor.edgeSoftness() * 0.62D);
            };
            double unclamped = normalized
                    * Math.min(descriptor.majorRadius(), descriptor.minorRadius());
            double halfHeight = Math.max(
                    StormLobeEvaluator.roleTopY(descriptor)
                            - StormLobeEvaluator.roleBaseY(descriptor), 1.0D) * 0.5D;
            double ratio = unclamped / halfHeight;
            double applied = StormLobeEvaluator.edgeWidthBlocks(descriptor);
            boolean clamped = applied < unclamped - 1.0E-4D;

            out.append(String.format(java.util.Locale.ROOT,
                    "%s|%d|%.1f|%.1f|%.3f|%.1f|%s%n",
                    descriptor.role(), descriptor.memberIndex(), unclamped, halfHeight,
                    ratio, applied, clamped ? "YES" : "no"));

            ratios.computeIfAbsent(descriptor.role(), key -> new ArrayList<>()).add(ratio);
            bound.merge(descriptor.role(), clamped ? 1 : 0, Integer::sum);
            stats.computeIfAbsent(descriptor.role(), key -> new double[] {0.0D});
        }

        out.append("roleSummary|role|count|clamped|minRatio|medianRatio|maxRatio\n");
        for (StormLobeDescriptor.Role role : StormLobeDescriptor.Role.values()) {
            java.util.List<Double> values = ratios.get(role);
            if (values == null || values.isEmpty()) {
                continue;
            }
            java.util.Collections.sort(values);
            out.append(String.format(java.util.Locale.ROOT,
                    "roleSummary|%s|%d|%d|%.3f|%.3f|%.3f%n",
                    role, values.size(), bound.getOrDefault(role, 0),
                    values.get(0), values.get(values.size() / 2),
                    values.get(values.size() - 1)));
        }
    }

    static String describe() {
        StormRenderSnapshot snapshot = StormGeometryBuildCoordinator.snapshot();
        StormLobeDescriptor[] descriptors = snapshot.descriptorsUnsafe();
        StringBuilder out = new StringBuilder(2048);
        out.append("=== T098 role occupancy ===\n");
        if (descriptors.length == 0) {
            out.append("no descriptor-owned storm is currently adopted.\n");
            return out.toString();
        }

        appendEnvelopeExtentStatistics(out, descriptors);

        List<StormLobeDescriptor> lobes = new ArrayList<>(descriptors.length);
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double centreX = 0.0D;
        double centreZ = 0.0D;
        double maxExtent = 0.0D;
        for (StormLobeDescriptor descriptor : descriptors) {
            lobes.add(descriptor);
            minY = Math.min(minY, descriptor.baseY());
            maxY = Math.max(maxY, descriptor.topY());
            centreX += descriptor.centerX();
            centreZ += descriptor.centerZ();
        }
        centreX /= descriptors.length;
        centreZ /= descriptors.length;
        for (StormLobeDescriptor descriptor : descriptors) {
            double reach = Math.hypot(descriptor.centerX() - centreX, descriptor.centerZ() - centreZ)
                    + Math.max(descriptor.majorRadius(), descriptor.minorRadius());
            maxExtent = Math.max(maxExtent, reach);
        }

        // Phase 1 evidence: the full descriptor inventory, so "adopted" is shown
        // rather than inferred from a role count.
        out.append(String.format(Locale.ROOT,
                "descriptors=%d topologyGeneration=%d centre=(%.1f,%.1f) y=%.1f..%.1f extent=%.1f%n",
                descriptors.length, snapshot.topologyGeneration(),
                centreX, centreZ, minY, maxY, maxExtent));
        for (StormLobeDescriptor descriptor : descriptors) {
            out.append(String.format(Locale.ROOT,
                    "  member=%d/%d role=%-5s centre=(%.1f,%.1f) major=%.1f minor=%.1f"
                            + " y=%.1f..%.1f density=%.4f edgeSoftness=%.4f strength=%.4f"
                            + " lifecycle=%.3f verticalDevelopment=%.3f detailWeight=%.3f%n",
                    descriptor.memberIndex(), descriptor.memberCount(), descriptor.role(),
                    descriptor.centerX(), descriptor.centerZ(),
                    descriptor.majorRadius(), descriptor.minorRadius(),
                    descriptor.baseY(), descriptor.topY(),
                    descriptor.density(), descriptor.edgeSoftness(),
                    StormLobeEvaluator.envelopeStrength(descriptor),
                    descriptor.lifecycleStage(), descriptor.verticalDevelopment(),
                    descriptor.detailWeight()));
        }

        // Stage-by-stage occupancy. Each role is measured on its own descriptors
        // so the union cannot mask an empty role.
        int roleCount = 4;
        long[] envelopePositive = new long[roleCount];
        long[] envelopeVisible = new long[roleCount];
        double[] envelopeSum = new double[roleCount];
        double[] envelopeMax = new double[roleCount];
        double[] visibleMinY = new double[roleCount];
        double[] visibleMaxY = new double[roleCount];
        java.util.Arrays.fill(visibleMinY, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(visibleMaxY, Double.NEGATIVE_INFINITY);
        long unionPositive = 0L;
        long unionVisible = 0L;
        long samples = 0L;

        for (double y = minY; y <= maxY; y += STEP) {
            for (double x = centreX - maxExtent; x <= centreX + maxExtent; x += STEP) {
                for (double z = centreZ - maxExtent; z <= centreZ + maxExtent; z += STEP) {
                    samples++;
                    double best = 0.0D;
                    for (StormLobeDescriptor lobe : lobes) {
                        double distance = StormLobeEvaluator.signedDistanceAt(lobe, x, y, z);
                        double envelope = StormLobeEvaluator.envelopeFromDistance(
                                distance,
                                StormLobeEvaluator.edgeWidthBlocks(lobe),
                                StormLobeEvaluator.envelopeStrength(lobe));
                        if (envelope <= 0.0D) {
                            continue;
                        }
                        int slot = lobe.role().gpuId();
                        envelopePositive[slot]++;
                        envelopeSum[slot] += envelope;
                        envelopeMax[slot] = Math.max(envelopeMax[slot], envelope);
                        if (envelope >= VISIBLE_ENVELOPE) {
                            envelopeVisible[slot]++;
                            visibleMinY[slot] = Math.min(visibleMinY[slot], y);
                            visibleMaxY[slot] = Math.max(visibleMaxY[slot], y);
                        }
                        best = Math.max(best, envelope);
                    }
                    if (best > 0.0D) {
                        unionPositive++;
                    }
                    if (best >= VISIBLE_ENVELOPE) {
                        unionVisible++;
                    }
                }
            }
        }

        out.append(String.format(Locale.ROOT,
                "grid step=%.0f samples=%d unionPositive=%d unionVisible=%d visibleThreshold=%.2f%n",
                STEP, samples, unionPositive, unionVisible, VISIBLE_ENVELOPE));
        String[] names = {"BASE", "CORE", "TOWER", "ANVIL"};
        for (int slot = 0; slot < roleCount; slot++) {
            out.append(String.format(Locale.ROOT,
                    "  role=%-5s envelopePositive=%-8d envelopeVisible=%-8d meanEnvelope=%.4f"
                            + " maxEnvelope=%.4f visibleY=%.1f..%.1f%n",
                    names[slot], envelopePositive[slot], envelopeVisible[slot],
                    envelopePositive[slot] == 0 ? 0.0D
                            : envelopeSum[slot] / envelopePositive[slot],
                    envelopeMax[slot],
                    visibleMinY[slot] == Double.POSITIVE_INFINITY ? 0.0D : visibleMinY[slot],
                    visibleMaxY[slot] == Double.NEGATIVE_INFINITY ? 0.0D : visibleMaxY[slot]));
        }

        // Vertical profile of the union: the captures show an empty middle, so
        // report where the system actually has visible envelope by height.
        out.append("vertical profile (visible envelope voxels per 48-block band):\n");
        for (double y = minY; y <= maxY; y += 48.0D) {
            long band = 0L;
            long[] byRole = new long[roleCount];
            for (double x = centreX - maxExtent; x <= centreX + maxExtent; x += STEP) {
                for (double z = centreZ - maxExtent; z <= centreZ + maxExtent; z += STEP) {
                    double best = 0.0D;
                    for (StormLobeDescriptor lobe : lobes) {
                        double envelope = StormLobeEvaluator.envelopeFromDistance(
                                StormLobeEvaluator.signedDistanceAt(lobe, x, y, z),
                                StormLobeEvaluator.edgeWidthBlocks(lobe),
                                StormLobeEvaluator.envelopeStrength(lobe));
                        if (envelope >= VISIBLE_ENVELOPE) {
                            byRole[lobe.role().gpuId()]++;
                        }
                        best = Math.max(best, envelope);
                    }
                    if (best >= VISIBLE_ENVELOPE) {
                        band++;
                    }
                }
            }
            out.append(String.format(Locale.ROOT,
                    "  y=%7.1f union=%-7d base=%-7d core=%-7d tower=%-7d anvil=%-7d%n",
                    y, band, byRole[0], byRole[1], byRole[2], byRole[3]));
        }
        return out.toString();
    }
}
