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
    static String describe() {
        StormRenderSnapshot snapshot = StormGeometryBuildCoordinator.snapshot();
        StormLobeDescriptor[] descriptors = snapshot.descriptorsUnsafe();
        StringBuilder out = new StringBuilder(2048);
        out.append("=== T098 role occupancy ===\n");
        if (descriptors.length == 0) {
            out.append("no descriptor-owned storm is currently adopted.\n");
            return out.toString();
        }

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
