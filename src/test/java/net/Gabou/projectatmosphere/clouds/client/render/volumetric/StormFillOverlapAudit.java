package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * T239 Task 1. Does the storm's interior fill depend on BASE + CORE + TOWER
 * overlap?
 *
 * <p>The composition (StormDensityModel, the CPU mirror of the shader's stage
 * 5/6) forms the body against a lower bound whose fill is the continuity fill
 * only where all three convective roles are active
 * ({@code embeddedConvectiveOverlap}); everywhere else it is the strength-only
 * fill. Two measurements, both on the real baked noise volumes:
 *
 * <ol>
 * <li><b>Controlled.</b> One set of noise samples pushed through the exact
 * composition under each rule and each region's live strength, at matched
 * coverage: base field, fill, pre-erosion body, erosion, post-erosion density
 * and the hole rate. The noise is position-independent in distribution, so the
 * rule is the only variable.</li>
 * <li><b>Spatial.</b> The live production and T237-V2 storms (descriptors
 * from the T238 trace) grid-sampled: every interior point classified by its
 * active role mask, with its density and hole rate per class.</li>
 * </ol>
 */
public final class StormFillOverlapAudit {
    private static final double HOLE = 0.02D;

    private StormFillOverlapAudit() {
    }

    public static void main(String[] args) {
        byte[] base = CloudNoiseFieldModel.bakeBase();
        byte[] detail = CloudNoiseFieldModel.bakeDetail();
        StormFieldSampler sampler = StormFieldSampler.of(
                StormFieldSampler.Composition.CORRECTED_PHASE_4S, base, detail);
        controlled(sampler);
        spatial(sampler, "production", production());
        spatial(sampler, "T237-V2", v2());
    }

    // ---------------------------------------------------------------------

    private static void controlled(StormFieldSampler sampler) {
        int n = 200_000;
        double[] baseField = new double[n];
        double[] detailFbm = new double[n];
        double[] fields = new double[2];
        java.util.Random random = new java.util.Random(239L);
        for (int i = 0; i < n; i++) {
            sampler.noiseFieldsAt(
                    random.nextDouble() * 4000.0D - 2000.0D,
                    100.0D + random.nextDouble() * 1000.0D,
                    random.nextDouble() * 4000.0D - 2000.0D, fields);
            baseField[i] = fields[0];
            detailFbm[i] = fields[1];
        }
        System.out.println("T239_FILL_CONTROLLED samples=" + n
                + " (real baked base + detail volumes; same samples for every row)");
        System.out.println("region            strength overlap coverage | fill   baseField  body(pre-erosion) erosion  post  p05post  holes<0.02");
        String[] names = {"overlap B+C+T", "tower-only", "tower-only", "anvil-only", "base-only",
                "rule-only (0.93)", "rule-only (0.93)"};
        double[] strength = {0.93D, 0.919D, 0.877D, 0.78D, 0.89D, 0.93D, 0.93D};
        boolean[] overlap = {true, false, false, false, false, true, false};
        for (double coverage : new double[] {1.0D, 0.95D, 0.90D, 0.80D, 0.60D}) {
            for (int r = 0; r < names.length; r++) {
                double fill = overlap[r]
                        ? StormDensityModel.coreFillForCoverageAndStrength(coverage, strength[r])
                        : StormDensityModel.coreFillForEnvelopeStrength(strength[r]);
                double sumBase = 0, sumBody = 0, sumErosion = 0, sumPost = 0;
                int holes = 0;
                double[] post = new double[n];
                for (int i = 0; i < n; i++) {
                    double body = StormDensityModel.stormBody(
                            coverage, strength[r], baseField[i], overlap[r]);
                    double finalDensity = StormDensityModel.erode(body, detailFbm[i]);
                    sumBase += baseField[i];
                    sumBody += body;
                    sumErosion += body - finalDensity;
                    sumPost += finalDensity;
                    post[i] = finalDensity;
                    if (finalDensity < HOLE) {
                        holes++;
                    }
                }
                java.util.Arrays.sort(post);
                System.out.println(String.format(Locale.ROOT,
                        "%-17s %.3f    %-5s   %.2f     | %.3f  %.3f      %.3f             %.3f    %.3f %.3f    %.4f",
                        names[r], strength[r], overlap[r], coverage, fill, sumBase / n,
                        sumBody / n, sumErosion / n, sumPost / n, post[n / 20], (double) holes / n));
            }
        }
    }

    // ---------------------------------------------------------------------

    private static void spatial(StormFieldSampler sampler, String label, List<StormLobeDescriptor> lobes) {
        // Classes by active role mask (bits: 1 base, 2 core, 4 tower, 8 anvil).
        String[] classes = {"overlap B+C+T (mask&7==7)", "tower-only", "anvil-only", "base-only",
                "other mixes"};
        long[] count = new long[classes.length];
        double[] sumDensity = new double[classes.length];
        long[] holes = new long[classes.length];
        long[] withTower = new long[classes.length];
        double[] fields = new double[2];
        int required = 7;
        double step = 12.0D;
        for (double y = 130.0D; y <= 1010.0D; y += step) {
            for (double x = -700.0D; x <= 1000.0D; x += step) {
                for (double z = -700.0D; z <= 700.0D; z += step) {
                    double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
                    if (coverage < 0.82D) {
                        continue; // interior only: where the continuity fill can act
                    }
                    int mask = StormLobeEvaluator.activeRoleMaskAt(lobes, x, y, z);
                    int cls;
                    if ((mask & required) == required) {
                        cls = 0;
                    } else if (mask == 4) {
                        cls = 1;
                    } else if (mask == 8) {
                        cls = 2;
                    } else if (mask == 1) {
                        cls = 3;
                    } else {
                        cls = 4;
                    }
                    sampler.noiseFieldsAt(x, y, z, fields);
                    double strength = StormLobeEvaluator.envelopeStrengthAt(lobes, x, y, z);
                    double density = StormDensityModel.finalDensity(
                            coverage, strength, fields[0], fields[1], cls == 0);
                    count[cls]++;
                    sumDensity[cls] += density;
                    if (density < HOLE) {
                        holes[cls]++;
                    }
                    if ((mask & 4) != 0) {
                        withTower[cls]++;
                    }
                }
            }
        }
        long total = 0;
        for (long c : count) {
            total += c;
        }
        System.out.println("T239_FILL_SPATIAL storm=" + label + " interiorPoints(coverage>=0.82)=" + total
                + " grid=" + (int) step + " blocks");
        for (int c = 0; c < classes.length; c++) {
            System.out.println(String.format(Locale.ROOT,
                    "  %-26s share %.3f  meanDensity %.3f  holes<0.02 %.4f  (with tower bit %.2f)",
                    classes[c], (double) count[c] / Math.max(total, 1),
                    sumDensity[c] / Math.max(count[c], 1), (double) holes[c] / Math.max(count[c], 1),
                    (double) withTower[c] / Math.max(count[c], 1)));
        }
    }

    // ---------------------------------------------------------------------
    // Live descriptors from runClient-t238s1.out.log (T215_GEOMETRY after the
    // version 0 / version 2 relayouts). Shear and orientation are not traced:
    // orientation 0, shear 0 (tower shear is <= 0.18 R, small against these
    // radii); edge softness per role from the T212 trace (0.52 / 0.48 / 0.44 /
    // 0.56).

    private static List<StormLobeDescriptor> production() {
        List<StormLobeDescriptor> l = new ArrayList<>();
        l.add(d(0, 0, 3.0, 6.0, 136.0F, 583.6F, 522.0F, 469.8F, 0.9013F));
        l.add(d(1, 0, 67.59, -0.59, 154.19F, 601.79F, 522.0F, 469.8F, 0.8762F));
        l.add(d(2, 1, -5.83, 14.23, 348.05F, 628.85F, 297.36F, 267.62F, 1.0F));
        l.add(d(3, 1, -2.34, 27.80, 403.11F, 699.51F, 276.12F, 248.51F, 0.9673F));
        l.add(d(4, 2, 27.44, -13.25, 432.91F, 799.51F, 225.79F, 185.15F, 0.8767F));
        l.add(d(5, 2, 38.27, -12.89, 588.74F, 924.14F, 192.38F, 157.75F, 0.9193F));
        l.add(d(6, 3, -73.39, 8.89, 769.62F, 980.22F, 495.0F, 287.1F, 0.8224F));
        l.add(d(7, 3, 14.07, 68.66, 777.01F, 987.61F, 495.0F, 287.1F, 0.7627F));
        l.add(d(8, 3, 117.60, -59.25, 783.05F, 993.65F, 495.0F, 287.1F, 0.7432F));
        l.add(d(9, 3, 214.14, 4.32, 791.30F, 1001.90F, 495.0F, 287.1F, 0.7986F));
        return l;
    }

    private static List<StormLobeDescriptor> v2() {
        List<StormLobeDescriptor> l = new ArrayList<>();
        l.add(d(0, 0, 3.0, 6.0, 136.0F, 412.0F, 585.0F, 526.5F, 0.9013F));
        l.add(d(1, 0, 120.94, -42.82, 168.59F, 383.39F, 459.0F, 413.1F, 0.8762F));
        l.add(d(2, 1, 20.49, 28.44, 270.05F, 535.25F, 329.22F, 296.30F, 1.0F));
        l.add(d(3, 1, 42.37, -14.85, 332.91F, 629.31F, 307.98F, 277.18F, 0.9673F));
        l.add(d(4, 2, 46.35, 25.48, 284.71F, 674.71F, 345.6F, 283.39F, 0.8767F));
        l.add(d(5, 2, 91.38, -15.11, 549.75F, 924.15F, 299.52F, 245.61F, 0.9193F));
        l.add(d(6, 3, 41.33, 70.13, 772.51F, 897.31F, 247.5F, 143.55F, 0.8224F));
        l.add(d(7, 3, 193.96, -97.92, 763.17F, 887.97F, 324.0F, 187.92F, 0.7627F));
        l.add(d(8, 3, 377.24, 108.91, 753.47F, 878.27F, 387.0F, 224.46F, 0.7432F));
        l.add(d(9, 3, 524.93, -22.14, 746.38F, 871.18F, 360.0F, 208.8F, 0.7986F));
        return l;
    }

    private static final UUID GROUP = UUID.nameUUIDFromBytes("t239-fill-audit".getBytes());

    private static StormLobeDescriptor d(int member, int roleId, double x, double z,
            float baseY, float topY, float major, float minor, float strength) {
        StormLobeDescriptor.Role role = switch (roleId) {
            case 0 -> StormLobeDescriptor.Role.BASE;
            case 1 -> StormLobeDescriptor.Role.CORE;
            case 2 -> StormLobeDescriptor.Role.TOWER;
            default -> StormLobeDescriptor.Role.ANVIL;
        };
        float softness = switch (roleId) {
            case 0 -> 0.52F;
            case 1 -> 0.48F;
            case 2 -> 0.44F;
            default -> 0.56F;
        };
        return new StormLobeDescriptor(
                UUID.nameUUIDFromBytes(("t239-field-" + member).getBytes()),
                GROUP, member, 10, 0, role, x, z, baseY, topY, major, minor,
                0.0F, 1.0F, 0.0F, 0.0F, strength, softness, 0.37F, 0.62F, 1.0F, 1.0F);
    }
}
