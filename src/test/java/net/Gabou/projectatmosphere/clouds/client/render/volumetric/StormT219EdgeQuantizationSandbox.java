package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * T219: what sets the width of a mid-field puff edge?
 *
 * <p>Measured on the captured frames, the cloud/sky boundary occupies a band
 * that is constant in WORLD units - about 2.8 blocks - across a 2.4x range of
 * tangent distances (5.0 px at 1.12 R down to 2.2 px at 1.9 R). A screen-space
 * effect such as resample aliasing would be constant in PIXELS instead, so the
 * band is set by something with a world-space length scale. Two candidates have
 * the right magnitude and this sandbox separates them:
 *
 * <ol>
 *   <li><b>March quantization.</b> The exterior fine stride is
 *       {@code clamp(2.5 * sqrt(96/budget), 2.5, 8.0)} = <b>2.5 blocks</b> at
 *       ULTRA. If the boundary is only resolved to that lattice, the band is an
 *       artefact and a finer stride removes it.</li>
 *   <li><b>The density field's own feather.</b> The puff envelope feathers over
 *       roughly 4-5 blocks by construction, and a grazing ray through a soft
 *       boundary produces a soft edge no matter how finely it is sampled. Then
 *       the band is physics and the march is innocent.</li>
 * </ol>
 *
 * <p>The test marches the real production density path across the silhouette at
 * decreasing step sizes down to 0.05 blocks. If the band collapses with the
 * step, cause 1. If it converges to a non-zero width, cause 2, and that width
 * is the answer the renderer is entitled to produce.
 *
 * <p>Diagnostic only. Nothing here writes a production constant.
 *
 * <h2>Honest limits</h2>
 * <ul>
 *   <li>This marches a single continuous stride. Production runs a promotion
 *       machine (coarse stride, an empty-span scan on the fine lattice, and a
 *       forced fine promotion when the scan finds material), so the real march
 *       is bracketed by the 2.5-block and 7.5-block rows rather than equal to
 *       either. The reference row is exact either way, which is what the
 *       comparison rests on.</li>
 *   <li>Production phases each ray's origin by blue noise over a full fine
 *       stride, and the T207 resolve averages four phases. That is modelled
 *       here as a uniform random origin phase per ray, and reported both with
 *       and without so the reader can see what the phase buys.</li>
 *   <li>The T213 macro envelope and T215 lower-body fill are shader-side and
 *       are not applied; both are large-scale vertical shape terms and neither
 *       moves a boundary by a block.</li>
 * </ul>
 */
public final class StormT219EdgeQuantizationSandbox {

    /** Live tuning measured in the T218 capture run. */
    private static final double EXTINCTION_SCALE = 0.115D;
    private static final double DENSITY_MUL = 1.45D;

    /** The exterior fine stride at ULTRA, from {@code exteriorFineStepWorld}. */
    private static final double PRODUCTION_FINE_STEP = 2.5D;
    /** {@code coarseStep = max(baseStep * 1.5, fineStep * 3)}; the fine-driven floor. */
    private static final double PRODUCTION_COARSE_STEP = 7.5D;

    private static final double OCCUPIED_ISO = 0.10D;
    /** The capture driver's SIDE pose: 1.7 R out, mid height. */
    private static final double SIDE_RADIUS_FRACTION = 1.7D;

    /** Vertical field of view and frame height of the capture, for px conversion. */
    private static final double FOV_DEGREES = 70.0D;
    private static final double FRAME_HEIGHT = 900.0D;

    private static final long SEED = 219L;

    private StormT219EdgeQuantizationSandbox() {
    }

    public static void main(String[] args) {
        byte[] base = CloudNoiseFieldModel.bakeBase();
        byte[] detail = CloudNoiseFieldModel.bakeDetail();
        StormFieldSampler sampler = StormFieldSampler.of(
                StormFieldSampler.Composition.CORRECTED_PHASE_4S, base, detail);
        List<StormLobeDescriptor> lobes = severeStorm();

        System.out.println("=== T219 mid-field edge quantization ===");
        System.out.printf(Locale.ROOT,
                "fixture: %d descriptors, noise=%s, extinction=%.4f densityMul=%.3f"
                        + " fineStep=%.2f coarseStep=%.2f%n",
                lobes.size(), sampler.hasNoise(), EXTINCTION_SCALE, DENSITY_MUL,
                PRODUCTION_FINE_STEP, PRODUCTION_COARSE_STEP);

        Geometry geometry = locate(sampler, lobes);
        stepA(sampler, lobes, geometry);
        stepB(sampler, lobes, geometry);
        stepC(sampler, lobes, geometry);
        stepD(sampler, lobes, geometry);

        System.out.println("\n=== T219 sandbox complete ===");
    }

    // -----------------------------------------------------------------
    // Fixture geometry and the SIDE camera
    // -----------------------------------------------------------------

    private record Geometry(double centreX, double centreZ, double baseY, double topY,
                            double radius, double camX, double camY, double camZ) {
        double midY() {
            return (baseY + topY) * 0.5D;
        }
    }

    private static Geometry locate(StormFieldSampler sampler, List<StormLobeDescriptor> lobes) {
        double baseY = Double.POSITIVE_INFINITY;
        double topY = Double.NEGATIVE_INFINITY;
        double sumX = 0.0D;
        double sumZ = 0.0D;
        for (StormLobeDescriptor lobe : lobes) {
            baseY = Math.min(baseY, StormLobeEvaluator.roleBaseY(lobe));
            topY = Math.max(topY, StormLobeEvaluator.roleTopY(lobe));
            sumX += lobe.centerX();
            sumZ += lobe.centerZ();
        }
        double centreX = sumX / lobes.size();
        double centreZ = sumZ / lobes.size();
        double midY = (baseY + topY) * 0.5D;
        double radius = 0.0D;
        for (double r = 0.0D; r <= 900.0D; r += 2.0D) {
            boolean any = false;
            for (int a = 0; a < 24; a++) {
                double ang = a * Math.PI * 2.0D / 24.0D;
                if (sampler.densityAt(lobes, centreX + Math.cos(ang) * r, midY,
                        centreZ + Math.sin(ang) * r) >= OCCUPIED_ISO) {
                    any = true;
                    break;
                }
            }
            if (any) {
                radius = r;
            }
        }
        Geometry g = new Geometry(centreX, centreZ, baseY, topY, radius,
                centreX + radius * SIDE_RADIUS_FRACTION, midY, centreZ);
        System.out.printf(Locale.ROOT,
                "%nSIDE pose: centre=(%.1f,%.1f) baseY=%.1f topY=%.1f radius=%.1f"
                        + " -> camera=(%.1f,%.1f,%.1f)%n",
                centreX, centreZ, baseY, topY, radius, g.camX(), g.camY(), g.camZ());
        return g;
    }

    // -----------------------------------------------------------------
    // STEP A - the physical boundary, sampled as finely as it takes
    // -----------------------------------------------------------------

    private static void stepA(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Geometry g) {
        System.out.println("\n--- STEP A: alpha band width vs march step ---");
        System.out.println("A fan of rays sweeps across the silhouette; for each stride the"
                + " 10-90% rise of accumulated alpha is measured in world blocks at the");
        System.out.println("tangent point, then converted to capture pixels. 'phase' means a"
                + " uniform random origin offset over one stride, which is what production's");
        System.out.println("blue-noise jitter does. If the band collapses with the stride the"
                + " cause is the march; if it converges, the cause is the field.");
        System.out.println("stride blocks|origin phase|band blocks|band px|"
                + "alpha at edge+1 block|rays");

        double[] strides = {PRODUCTION_COARSE_STEP, PRODUCTION_FINE_STEP, 1.25D, 0.625D,
                0.25D, 0.05D};
        for (double stride : strides) {
            bandWidth(sampler, lobes, g, stride, false);
        }
        System.out.println("-- the same strides with a per-ray origin phase --");
        for (double stride : strides) {
            if (stride >= 0.25D) {
                bandWidth(sampler, lobes, g, stride, true);
            }
        }
    }

    /**
     * Sweeps rays across the silhouette and reports the 10-90% alpha rise.
     *
     * <p>The sweep is HORIZONTAL, at mid height, so each ray grazes the body's
     * lateral silhouette - the same geometry as a mid-field puff edge against
     * sky. A vertical sweep at this height would stay inside the body for its
     * whole range and never cross an edge.
     */
    private static void bandWidth(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Geometry g,
            double stride,
            boolean phase) {
        // Impact parameter sweep, fine enough that the measurement resolves a
        // band far narrower than the stride under test.
        // Sweep from the axis right out past the silhouette, so the edge is
        // located by the data rather than assumed to sit at the fixture radius.
        double sweepStep = 0.05D;
        double lo = 0.0D;
        double hi = g.radius() * 1.30D;
        int rays = (int) ((hi - lo) / sweepStep);
        double[] alphas = new double[rays];
        double[] offsets = new double[rays];
        Random random = new Random(SEED);
        for (int i = 0; i < rays; i++) {
            double impact = lo + i * sweepStep;
            offsets[i] = impact;
            alphas[i] = marchAlpha(sampler, lobes, g, impact, stride,
                    phase ? random.nextDouble() * stride : 0.0D);
        }
        // Smooth lightly so a single noisy ray does not define the band; the
        // smoothing window (0.5 blocks) is far below every stride tested.
        double[] sm = smooth(alphas, 5);
        double top = 0.0D;
        for (double a : sm) {
            top = Math.max(top, a);
        }
        if (top < 0.25D) {
            System.out.printf(Locale.ROOT, "%.3f|%s|no edge (max alpha %.3f)%n",
                    stride, phase ? "yes" : "no", top);
            return;
        }
        double target10 = 0.10D * top;
        double target90 = 0.90D * top;
        double x10 = crossing(offsets, sm, target10);
        double x90 = crossing(offsets, sm, target90);
        double band = Math.abs(x10 - x90);
        double tangent = Math.sqrt(Math.max(
                sq(g.camX() - g.centreX()) - sq(g.radius()), 1.0D));
        double blocksPerPixel = tangent * Math.toRadians(FOV_DEGREES) / FRAME_HEIGHT;
        double alphaAtOne = sampleAt(offsets, sm, Math.min(x10, x90) - 1.0D);
        System.out.printf(Locale.ROOT, "%.3f|%s|%.3f|%.3f|%.3f|%d%n",
                stride, phase ? "yes" : "no", band, band / blocksPerPixel,
                alphaAtOne, rays);
    }

    private static double marchAlpha(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Geometry g,
            double impact,
            double stride,
            double originPhase) {
        // Horizontal ray at mid height whose closest approach to the storm axis
        // is exactly `impact`: the camera sits at distance D on the +X axis, so
        // a ray aimed at lateral offset `impact / D * sqrt(D^2 - impact^2)`
        // grazes at that radius. Solving directly is simpler - point the ray at
        // the tangent construction.
        double dx = g.centreX() - g.camX();
        double dz = g.centreZ() - g.camZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= impact + 1.0D) {
            return 0.0D;
        }
        // Unit vector camera -> centre, and its perpendicular in the XZ plane.
        double ux = dx / distance;
        double uz = dz / distance;
        double px0 = -uz;
        double pz0 = ux;
        // A ray leaving the camera at angle asin(impact / distance) from the
        // centre line has closest approach `impact`.
        double sin = impact / distance;
        double cos = Math.sqrt(Math.max(1.0D - sin * sin, 0.0D));
        double rx = ux * cos + px0 * sin;
        double rz = uz * cos + pz0 * sin;

        double transmittance = 1.0D;
        double tEnd = distance * 2.0D;
        for (double t = originPhase; t < tEnd; t += stride) {
            double sx = g.camX() + rx * (t + stride * 0.5D);
            double sz = g.camZ() + rz * (t + stride * 0.5D);
            double density = sampler.densityAt(lobes, sx, g.camY(), sz);
            if (density > 0.0D) {
                transmittance *= Math.exp(
                        -density * DENSITY_MUL * EXTINCTION_SCALE * stride);
                if (transmittance < 1.0E-4D) {
                    break;
                }
            }
        }
        return 1.0D - transmittance;
    }

    // -----------------------------------------------------------------
    // STEP B - the density field's own boundary profile
    // -----------------------------------------------------------------

    private static void stepB(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Geometry g) {
        System.out.println("\n--- STEP B: the density field's own feather ---");
        System.out.println("Density sampled straight outward along the radius at mid height."
                + " This is the field, with no ray integration at all, so it is the");
        System.out.println("length scale any correct renderer must reproduce.");
        System.out.println("quantity|blocks");
        double midY = g.midY();
        double step = 0.05D;
        double lastInside = -1.0D;
        double firstOutside = -1.0D;
        double d10 = -1.0D;
        double d90 = -1.0D;
        double peak = 0.0D;
        List<double[]> profile = new ArrayList<>();
        for (double r = g.radius() * 1.30D; r >= 0.0D; r -= step) {
            double d = sampler.densityAt(lobes, g.centreX(), midY, g.centreZ() + r);
            profile.add(new double[]{r, d});
            peak = Math.max(peak, d);
        }
        for (double[] row : profile) {
            if (row[1] >= 0.10D * peak && d10 < 0.0D) {
                d10 = row[0];
            }
            if (row[1] >= 0.90D * peak && d90 < 0.0D) {
                d90 = row[0];
            }
            if (row[1] <= 1.0E-4D) {
                firstOutside = row[0];
            } else if (lastInside < 0.0D) {
                lastInside = row[0];
            }
        }
        double tangent = Math.sqrt(Math.max(
                sq(g.camX() - g.centreX()) - sq(g.radius()), 1.0D));
        double blocksPerPixel = tangent * Math.toRadians(FOV_DEGREES) / FRAME_HEIGHT;
        System.out.printf(Locale.ROOT, "peak density on this radius|%.4f%n", peak);
        System.out.printf(Locale.ROOT, "10%%-90%% density rise|%.3f%n", Math.abs(d10 - d90));
        System.out.printf(Locale.ROOT, "zero to first material|%.3f%n",
                Math.abs(firstOutside - lastInside));
        System.out.printf(Locale.ROOT, "blocks per capture pixel at this pose|%.4f%n",
                blocksPerPixel);
        System.out.printf(Locale.ROOT, "10%%-90%% density rise in pixels|%.3f%n",
                Math.abs(d10 - d90) / blocksPerPixel);
    }

    // -----------------------------------------------------------------
    // STEP C - how much does a grazing chord soften a SHARP boundary?
    // -----------------------------------------------------------------

    private static void stepC(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Geometry g) {
        System.out.println("\n--- STEP C: the grazing-chord floor ---");
        System.out.println("Even a perfectly sharp density boundary produces a soft silhouette,"
                + " because the chord through a convex body grows as sqrt(depth) at the");
        System.out.println("tangent. This is that floor for the measured body radius, and it is"
                + " the width no sampling change can go below.");
        double tangent = Math.sqrt(Math.max(
                sq(g.camX() - g.centreX()) - sq(g.radius()), 1.0D));
        double blocksPerPixel = tangent * Math.toRadians(FOV_DEGREES) / FRAME_HEIGHT;
        // Alpha for a hard sphere of radius R, impact parameter b:
        // chord = 2*sqrt(R^2 - b^2), alpha = 1 - exp(-sigma * chord).
        double coreDensity = 0.55D;
        double sigma = coreDensity * DENSITY_MUL * EXTINCTION_SCALE;
        double r = g.radius();
        double b10 = -1.0D;
        double b90 = -1.0D;
        for (double b = r; b > r * 0.90D; b -= 0.005D) {
            double chord = 2.0D * Math.sqrt(Math.max(r * r - b * b, 0.0D));
            double alpha = 1.0D - Math.exp(-sigma * chord);
            if (alpha >= 0.10D && b10 < 0.0D) {
                b10 = b;
            }
            if (alpha >= 0.90D && b90 < 0.0D) {
                b90 = b;
            }
        }
        System.out.printf(Locale.ROOT,
                "hard-sphere 10%%-90%% alpha band|%.3f blocks|%.3f px%n",
                Math.abs(b10 - b90), Math.abs(b10 - b90) / blocksPerPixel);
        System.out.printf(Locale.ROOT,
                "  (R=%.1f, sigma=%.5f/block, tangent distance %.1f)%n", r, sigma, tangent);
    }

    // -----------------------------------------------------------------
    // STEP D - the distance ladder, to check this model against the frames
    // -----------------------------------------------------------------

    private static void stepD(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Geometry g) {
        System.out.println("\n--- STEP D: band vs camera distance ---");
        System.out.println("The captured frames show the boundary band constant in WORLD blocks"
                + " and shrinking as 1/distance in PIXELS. If this model reproduces that");
        System.out.println("trend it is describing the same boundary the renderer draws, which"
                + " is what licenses the STEP A conclusion.");
        System.out.println("distance factor|tangent dist|blocks per px|band blocks (2.5 stride)|"
                + "band px|band blocks (0.05 ref)|band px");
        double[] factors = {1.12D, 1.20D, 1.40D, 1.70D, 1.90D};
        for (double factor : factors) {
            Geometry gf = new Geometry(g.centreX(), g.centreZ(), g.baseY(), g.topY(),
                    g.radius(), g.centreX() + g.radius() * factor, g.midY(), g.centreZ());
            double tangent = Math.sqrt(Math.max(
                    sq(gf.camX() - gf.centreX()) - sq(gf.radius()), 1.0D));
            double blocksPerPixel = tangent * Math.toRadians(FOV_DEGREES) / FRAME_HEIGHT;
            double prod = bandOnly(sampler, lobes, gf, PRODUCTION_FINE_STEP);
            double ref = bandOnly(sampler, lobes, gf, 0.05D);
            System.out.printf(Locale.ROOT, "%.2f|%.1f|%.4f|%.3f|%.3f|%.3f|%.3f%n",
                    factor, tangent, blocksPerPixel,
                    prod, prod / blocksPerPixel, ref, ref / blocksPerPixel);
        }
    }

    /** The 10-90% alpha band in world blocks, with no printing. */
    private static double bandOnly(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Geometry g,
            double stride) {
        double sweepStep = 0.05D;
        double lo = 0.0D;
        double hi = g.radius() * 1.30D;
        int rays = (int) ((hi - lo) / sweepStep);
        double[] alphas = new double[rays];
        double[] offsets = new double[rays];
        for (int i = 0; i < rays; i++) {
            double impact = lo + i * sweepStep;
            offsets[i] = impact;
            alphas[i] = marchAlpha(sampler, lobes, g, impact, stride, 0.0D);
        }
        double[] sm = smooth(alphas, 5);
        double top = 0.0D;
        for (double a : sm) {
            top = Math.max(top, a);
        }
        if (top < 0.25D) {
            return Double.NaN;
        }
        return Math.abs(crossing(offsets, sm, 0.10D * top)
                - crossing(offsets, sm, 0.90D * top));
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static double sq(double v) {
        return v * v;
    }

    private static double[] smooth(double[] v, int k) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            double sum = 0.0D;
            int n = 0;
            for (int j = Math.max(0, i - k); j <= Math.min(v.length - 1, i + k); j++) {
                sum += v[j];
                n++;
            }
            out[i] = sum / n;
        }
        return out;
    }

    /** First x at which y crosses the target, scanning from the outside in. */
    private static double crossing(double[] xs, double[] ys, double target) {
        for (int i = ys.length - 1; i > 0; i--) {
            double a = ys[i];
            double b = ys[i - 1];
            if ((a - target) * (b - target) <= 0.0D && a != b) {
                double f = (target - a) / (b - a);
                return xs[i] + f * (xs[i - 1] - xs[i]);
            }
        }
        return xs[0];
    }

    private static double sampleAt(double[] xs, double[] ys, double x) {
        for (int i = 0; i < xs.length - 1; i++) {
            if ((xs[i] - x) * (xs[i + 1] - x) <= 0.0D) {
                return ys[i];
            }
        }
        return ys[ys.length - 1];
    }

    private static List<StormLobeDescriptor> severeStorm() {
        UUID group = UUID.nameUUIDFromBytes("phase4s-storm".getBytes());
        return List.of(
                descriptor(group, 0, StormLobeDescriptor.Role.BASE,
                        -28.0D, -6.0D, 224.0F, 300.0F, 172.0F, 138.0F, 0.7832F),
                descriptor(group, 1, StormLobeDescriptor.Role.BASE,
                        26.0D, 10.0D, 226.0F, 296.0F, 158.0F, 126.0F, 0.8792F),
                descriptor(group, 2, StormLobeDescriptor.Role.CORE,
                        -12.0D, 2.0D, 250.0F, 368.0F, 98.0F, 82.0F, 0.9485F),
                descriptor(group, 3, StormLobeDescriptor.Role.CORE,
                        14.0D, -8.0D, 256.0F, 374.0F, 92.0F, 76.0F, 1.0000F),
                descriptor(group, 4, StormLobeDescriptor.Role.TOWER,
                        -6.0D, 0.0D, 300.0F, 448.0F, 58.0F, 48.0F, 0.9700F),
                descriptor(group, 5, StormLobeDescriptor.Role.TOWER,
                        16.0D, -6.0D, 308.0F, 456.0F, 54.0F, 44.0F, 0.9539F),
                descriptor(group, 6, StormLobeDescriptor.Role.ANVIL,
                        10.0D, 0.0D, 396.0F, 504.0F, 206.0F, 82.0F, 0.8222F),
                descriptor(group, 7, StormLobeDescriptor.Role.ANVIL,
                        44.0D, -8.0D, 404.0F, 508.0F, 184.0F, 74.0F, 0.7231F),
                descriptor(group, 8, StormLobeDescriptor.Role.ANVIL,
                        -30.0D, 6.0D, 400.0F, 500.0F, 176.0F, 70.0F, 0.7851F),
                descriptor(group, 9, StormLobeDescriptor.Role.ANVIL,
                        -58.0D, -12.0D, 390.0F, 498.0F, 168.0F, 68.0F, 0.7992F)
        );
    }

    private static StormLobeDescriptor descriptor(
            UUID group,
            int memberIndex,
            StormLobeDescriptor.Role role,
            double centerX,
            double centerZ,
            float baseY,
            float topY,
            float majorRadius,
            float minorRadius,
            float density
    ) {
        return new StormLobeDescriptor(
                UUID.nameUUIDFromBytes(("field-" + group + "-" + memberIndex).getBytes()),
                group,
                memberIndex,
                10,
                0,
                role,
                centerX,
                centerZ,
                baseY,
                topY,
                majorRadius,
                minorRadius,
                0.3420F,
                0.9397F,
                role == StormLobeDescriptor.Role.TOWER ? 26.0F : 8.0F,
                role == StormLobeDescriptor.Role.TOWER ? 6.0F : 2.0F,
                density,
                0.14F,
                0.37F,
                0.62F,
                0.78F,
                1.0F
        );
    }
}
