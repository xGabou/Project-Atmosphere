package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * T098 calibration scaffolding: reports the live {@code cell.density()} values
 * that reach the descriptor coverage envelope.
 *
 * <p>Phase 4S made the descriptor union a bounded coverage envelope scaled by
 * {@code density * detailWeight}. The deterministic sandboxes exercise that
 * with a fixture density of {@value #FIXTURE_REFERENCE_DENSITY}; what they
 * cannot establish is the range the live severe-storm generator actually
 * produces. This report closes that gap so the T098 captures can be read
 * against the density that produced them.
 *
 * <p>Deliberately minimal and on-demand only: no per-frame work, no counters,
 * no state. It is superseded by the full storm diagnostic capture specified in
 * {@code contracts/storm-render-diagnostics.md} when US4 is implemented, and
 * can be deleted outright once T098 records its calibration.
 */
final class StormDensityCalibrationReport {
    /** Descriptor density used by the deterministic morphology fixtures. */
    static final double FIXTURE_REFERENCE_DENSITY = 0.92D;

    private StormDensityCalibrationReport() {
    }

    /**
     * Builds one bounded snapshot of live descriptor densities, nearest storm
     * group first so the group being photographed is at the top.
     */
    static String describe(double cameraX, double cameraY, double cameraZ) {
        StormRenderSnapshot snapshot = StormGeometryBuildCoordinator.snapshot();
        StormLobeDescriptor[] descriptors = snapshot.descriptorsUnsafe();
        StringBuilder out = new StringBuilder(1024);
        out.append("=== T098 storm density calibration ===\n");
        out.append(String.format(
                Locale.ROOT,
                "camera=(%.1f, %.1f, %.1f)  descriptors=%d  topologyGeneration=%d%n",
                cameraX, cameraY, cameraZ, descriptors.length, snapshot.topologyGeneration()
        ));

        if (descriptors.length == 0) {
            out.append("no descriptor-owned storm is currently adopted.\n");
            out.append("Move within the native storm detail distance of a severe storm and retry.\n");
            return out.toString();
        }

        Stats overall = new Stats();
        Stats strength = new Stats();
        Map<UUID, List<StormLobeDescriptor>> byGroup = new LinkedHashMap<>();
        for (StormLobeDescriptor descriptor : descriptors) {
            overall.accept(descriptor.density());
            strength.accept(StormLobeEvaluator.envelopeStrength(descriptor));
            byGroup.computeIfAbsent(descriptor.groupId(), key -> new ArrayList<>()).add(descriptor);
        }

        out.append(String.format(
                Locale.ROOT,
                "cell.density  min=%.4f  max=%.4f  mean=%.4f   (fixture reference %.2f)%n",
                overall.minimum, overall.maximum, overall.mean(), FIXTURE_REFERENCE_DENSITY
        ));
        out.append(String.format(
                Locale.ROOT,
                "envelope strength (density * detailWeight)  min=%.4f  max=%.4f  mean=%.4f%n",
                strength.minimum, strength.maximum, strength.mean()
        ));

        StormFieldSampler sampler = StormFieldSampler.production();
        out.append(String.format(
                Locale.ROOT,
                "at camera: coverage=%.4f  density=%.4f  noiseBaked=%s%n",
                StormLobeEvaluator.coverageEnvelopeAt(descriptors, cameraX, cameraY, cameraZ),
                sampler.densityAt(descriptors, cameraX, cameraY, cameraZ),
                sampler.hasNoise()
        ));

        List<Map.Entry<UUID, List<StormLobeDescriptor>>> groups = new ArrayList<>(byGroup.entrySet());
        groups.sort(Comparator.comparingDouble(
                entry -> groupDistance(entry.getValue(), cameraX, cameraZ)));

        for (Map.Entry<UUID, List<StormLobeDescriptor>> entry : groups) {
            List<StormLobeDescriptor> members = entry.getValue();
            Stats groupStats = new Stats();
            int base = 0;
            int core = 0;
            int tower = 0;
            int anvil = 0;
            double centerX = 0.0D;
            double centerZ = 0.0D;
            double lowest = Double.MAX_VALUE;
            double highest = -Double.MAX_VALUE;
            for (StormLobeDescriptor member : members) {
                groupStats.accept(member.density());
                centerX += member.centerX();
                centerZ += member.centerZ();
                lowest = Math.min(lowest, member.baseY());
                highest = Math.max(highest, member.topY());
                switch (member.role()) {
                    case BASE -> base++;
                    case CORE -> core++;
                    case TOWER -> tower++;
                    case ANVIL -> anvil++;
                }
            }
            centerX /= members.size();
            centerZ /= members.size();

            out.append(String.format(
                    Locale.ROOT,
                    "%ngroup %s  distance=%.0fm  members=%d  roles[base=%d,core=%d,tower=%d,anvil=%d]%n",
                    shortId(entry.getKey()),
                    groupDistance(members, cameraX, cameraZ),
                    members.size(), base, core, tower, anvil
            ));
            out.append(String.format(
                    Locale.ROOT,
                    "  centre=(%.0f, %.0f)  baseY=%.0f  topY=%.0f%n",
                    centerX, centerZ, lowest, highest
            ));
            out.append(String.format(
                    Locale.ROOT,
                    "  cell.density  min=%.4f  max=%.4f  mean=%.4f%n",
                    groupStats.minimum, groupStats.maximum, groupStats.mean()
            ));
            members.sort(Comparator.comparingInt(StormLobeDescriptor::memberIndex));
            for (StormLobeDescriptor member : members) {
                out.append(String.format(
                        Locale.ROOT,
                        "    %-5s #%d  cell.density = %.4f   strength = %.4f%n",
                        member.role(),
                        member.memberIndex(),
                        member.density(),
                        StormLobeEvaluator.envelopeStrength(member)
                ));
            }
        }
        return out.toString();
    }

    private static double groupDistance(
            List<StormLobeDescriptor> members,
            double cameraX,
            double cameraZ
    ) {
        double centerX = 0.0D;
        double centerZ = 0.0D;
        for (StormLobeDescriptor member : members) {
            centerX += member.centerX();
            centerZ += member.centerZ();
        }
        centerX /= members.size();
        centerZ /= members.size();
        double dx = centerX - cameraX;
        double dz = centerZ - cameraZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** First eight hex characters are enough to tell two live storms apart. */
    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static final class Stats {
        private double minimum = Double.MAX_VALUE;
        private double maximum = -Double.MAX_VALUE;
        private double total;
        private int count;

        void accept(double value) {
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
            total += value;
            count++;
        }

        double mean() {
            return count > 0 ? total / count : 0.0D;
        }
    }
}
