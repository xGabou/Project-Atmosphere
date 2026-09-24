package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * T218: does a deeply buried cloud interior carry visible structure at all?
 *
 * <p>T217 made {@code 4_INSIDE} bright. It is still smooth. Before another
 * shading term is proposed, this measures whether structure is physically
 * available inside one attenuation length, on the real production density
 * path - descriptor envelope, base noise remap, multi-scale erosion, final
 * density - over the real baked noise volumes and the same ten-member severe
 * fixture the other sandboxes use.
 *
 * <p>Three questions, in order:
 *
 * <ol>
 *   <li><b>Scale.</b> How much density variance exists inside windows of 5, 10,
 *       15, 30, 60, 120 and 240 blocks, split into the base-carrier band and
 *       the detail-erosion band? Only variance inside the visible window can
 *       ever reach the frame.</li>
 *   <li><b>Visibility.</b> What survives transmittance weighting? A ray from a
 *       buried camera sees a transmittance-weighted average of density, so the
 *       spread of that average across rays is the ceiling on image contrast
 *       for any term that reads local density.</li>
 *   <li><b>Estimator.</b> Does the production two-sample inside light estimator
 *       ({@code localDensity * 18 + forwardDensity * 82}) average away local
 *       variation relative to a fully marched optical depth, and would four or
 *       eight samples recover it?</li>
 * </ol>
 *
 * <p>Diagnostic only. Nothing here writes a production constant.
 *
 * <h2>Honest limits</h2>
 * <ul>
 *   <li>The forward probe is fetched at LOD 1.2 in the shader; this mirror
 *       samples it at LOD 0. That <em>overstates</em> the production
 *       estimator's variance, so it biases against a "the estimator smooths it
 *       away" conclusion rather than towards one.</li>
 *   <li>The T213 macro envelope and the T215 lower-body fill are shader-side
 *       and are not applied here. Both are large-scale vertical shape terms;
 *       neither introduces structure at the 5-30 block scales this measures.</li>
 *   <li>{@code ExtinctionScale} and {@code DensityMul} are the live values
 *       measured in T217 (0.115, 1.45), not read from a running client.</li>
 * </ul>
 */
public final class StormT218InteriorStructureSandbox {

    /** Live tuning measured in T217 at the capture fixture. */
    private static final double EXTINCTION_SCALE = 0.115D;
    private static final double DENSITY_MUL = 1.45D;

    /** The visible silhouette iso-level, as elsewhere. */
    private static final double OCCUPIED_ISO = 0.10D;

    /** 4_INSIDE is at 0.25 of the horizontal radius, 0.45 of the body height. */
    private static final double INSIDE_RADIUS_FRACTION = 0.25D;
    private static final double INSIDE_HEIGHT_FRACTION = 0.45D;
    /** 4B_THROUGH_BODY, for contrast: 0.82 radius, 0.62 height. */
    private static final double THROUGH_RADIUS_FRACTION = 0.82D;
    private static final double THROUGH_HEIGHT_FRACTION = 0.62D;

    private static final double[] WINDOWS = {5.0D, 10.0D, 15.0D, 30.0D, 60.0D, 120.0D, 240.0D};

    /** The production inside estimator's two weights and its probe distance. */
    private static final double LOCAL_WEIGHT = 18.0D;
    private static final double FORWARD_WEIGHT = 82.0D;
    private static final double FORWARD_DISTANCE = 28.0D;
    /** The nominal path the two weights integrate: 18 + 82 blocks. */
    private static final double ESTIMATOR_PATH = LOCAL_WEIGHT + FORWARD_WEIGHT;

    private static final long SEED = 218L;

    private StormT218InteriorStructureSandbox() {
    }

    public static void main(String[] args) {
        byte[] base = CloudNoiseFieldModel.bakeBase();
        byte[] detail = CloudNoiseFieldModel.bakeDetail();
        StormFieldSampler sampler = StormFieldSampler.of(
                StormFieldSampler.Composition.CORRECTED_PHASE_4S, base, detail);
        List<StormLobeDescriptor> lobes = severeStorm();

        System.out.println("=== T218 buried-interior structure / diffusion limit ===");
        System.out.printf(Locale.ROOT,
                "fixture: %d descriptors, noise=%s, extinctionScale=%.4f densityMul=%.3f%n",
                lobes.size(), sampler.hasNoise(), EXTINCTION_SCALE, DENSITY_MUL);

        Pose inside = locate(sampler, lobes, INSIDE_RADIUS_FRACTION, INSIDE_HEIGHT_FRACTION,
                "4_INSIDE");
        Pose through = locate(sampler, lobes, THROUGH_RADIUS_FRACTION, THROUGH_HEIGHT_FRACTION,
                "4B_THROUGH_BODY");

        stepA(sampler, lobes, inside, through);
        stepB(sampler, lobes, inside);
        stepC(sampler, lobes, inside, through);
        stepD(sampler, lobes, inside);
        stepE(sampler, lobes, inside, through);

        System.out.println("\n=== T218 sandbox complete ===");
    }

    // -----------------------------------------------------------------
    // Fixture geometry and the two interior poses
    // -----------------------------------------------------------------

    private record Pose(String name, double x, double y, double z,
                        double coverage, double strength, double density) {
    }

    private static Pose locate(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            double radiusFraction,
            double heightFraction,
            String name) {
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
        double height = topY - baseY;
        // Horizontal radius of the occupied body at mid-height, resolved the
        // same way the capture driver's fixture resolves it.
        double midY = baseY + height * 0.5D;
        double radius = 0.0D;
        for (double r = 0.0D; r <= 900.0D; r += 4.0D) {
            boolean any = false;
            for (int a = 0; a < 16; a++) {
                double ang = a * Math.PI * 2.0D / 16.0D;
                double d = sampler.densityAt(lobes,
                        centreX + Math.cos(ang) * r, midY, centreZ + Math.sin(ang) * r);
                if (d >= OCCUPIED_ISO) {
                    any = true;
                    break;
                }
            }
            if (any) {
                radius = r;
            }
        }
        double x = centreX + radius * radiusFraction;
        double y = baseY + height * heightFraction;
        double coverage = sampler.coverageAt(lobes, x, y, centreZ);
        double strength = sampler.envelopeStrengthAt(lobes, x, y, centreZ);
        double density = sampler.densityAt(lobes, x, y, centreZ);
        System.out.printf(Locale.ROOT,
                "%n%s: centre=(%.1f,%.1f) baseY=%.1f topY=%.1f radius=%.1f -> p=(%.1f,%.1f,%.1f)%n"
                        + "  coverage=%.4f strength=%.4f density=%.4f%n",
                name, centreX, centreZ, baseY, topY, radius, x, y, centreZ,
                coverage, strength, density);
        return new Pose(name, x, y, centreZ, coverage, strength, density);
    }

    // -----------------------------------------------------------------
    // STEP A - the attenuation length actually in force
    // -----------------------------------------------------------------

    private static void stepA(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Pose inside,
            Pose through) {
        System.out.println("\n--- STEP A: attenuation length at each interior pose ---");
        System.out.println("pose|mean density over 60 blocks|sigma per block|1/e blocks"
                + "|95% blocks|blocks to alpha 0.99");
        for (Pose pose : List.of(inside, through)) {
            // Mean density over the first 60 blocks in a spread of directions,
            // which is what the view ray actually traverses, rather than the
            // single point value.
            double meanDensity = meanDensityAround(sampler, lobes, pose, 60.0D, 256);
            double sigma = meanDensity * DENSITY_MUL * EXTINCTION_SCALE;
            System.out.printf(Locale.ROOT, "%s|%.4f|%.5f|%.2f|%.2f|%.2f%n",
                    pose.name(), meanDensity, sigma,
                    1.0D / sigma, 3.0D / sigma, Math.log(100.0D) / sigma);
        }
    }

    private static double meanDensityAround(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Pose pose,
            double reach,
            int rays) {
        Random random = new Random(SEED);
        double sum = 0.0D;
        int count = 0;
        for (int i = 0; i < rays; i++) {
            double[] dir = randomDirection(random);
            for (double t = 1.0D; t <= reach; t += 2.0D) {
                sum += sampler.densityAt(lobes,
                        pose.x() + dir[0] * t, pose.y() + dir[1] * t, pose.z() + dir[2] * t);
                count++;
            }
        }
        return sum / count;
    }

    // -----------------------------------------------------------------
    // STEP B - density variance by window size, split into noise bands
    // -----------------------------------------------------------------

    private static void stepB(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Pose inside) {
        System.out.println("\n--- STEP B: density SD inside a window, by band ---");
        System.out.println("Bands isolate one noise input by holding the other at its"
                + " window-centre value, so each column is that input's own contribution.");
        System.out.println("window blocks|sd total|sd base band|sd detail band|"
                + "detail share of banded variance|samples");

        int centres = 24;
        int perWindow = 512;
        Random random = new Random(SEED);
        double[] fields = new double[2];
        // Independent centres spread through the core around the buried pose,
        // so the result describes the core rather than one lucky neighbourhood.
        List<double[]> anchors = new ArrayList<>();
        int guard = 0;
        while (anchors.size() < centres && guard++ < 100000) {
            double ax = inside.x() + (random.nextDouble() - 0.5D) * 120.0D;
            double ay = inside.y() + (random.nextDouble() - 0.5D) * 80.0D;
            double az = inside.z() + (random.nextDouble() - 0.5D) * 120.0D;
            if (sampler.densityAt(lobes, ax, ay, az) >= 0.30D) {
                anchors.add(new double[]{ax, ay, az});
            }
        }

        long anchorSeed = 0L;
        for (double window : WINDOWS) {
            double sdTotal = 0.0D;
            double sdBase = 0.0D;
            double sdDetail = 0.0D;
            long used = 0L;
            for (double[] anchor : anchors) {
                Random local = new Random(SEED * 31L + (anchorSeed++));
                sampler.noiseFieldsAt(anchor[0], anchor[1], anchor[2], fields);
                double anchorBase = fields[0];
                double anchorDetail = fields[1];
                Accumulator total = new Accumulator();
                Accumulator baseOnly = new Accumulator();
                Accumulator detailOnly = new Accumulator();
                for (int i = 0; i < perWindow; i++) {
                    double px = anchor[0] + (local.nextDouble() - 0.5D) * window;
                    double py = anchor[1] + (local.nextDouble() - 0.5D) * window;
                    double pz = anchor[2] + (local.nextDouble() - 0.5D) * window;
                    double coverage = sampler.coverageAt(lobes, px, py, pz);
                    if (coverage <= 0.0D) {
                        continue;
                    }
                    double strength = sampler.envelopeStrengthAt(lobes, px, py, pz);
                    sampler.noiseFieldsAt(px, py, pz, fields);
                    total.add(sampler.densityFromFields(coverage, strength, fields[0], fields[1]));
                    // Base band: the detail input is frozen at the anchor, so
                    // only the base carrier and the envelope vary.
                    baseOnly.add(sampler.densityFromFields(
                            coverage, strength, fields[0], anchorDetail));
                    // Detail band: the base carrier is frozen instead.
                    detailOnly.add(sampler.densityFromFields(
                            coverage, strength, anchorBase, fields[1]));
                }
                sdTotal += total.sd();
                sdBase += baseOnly.sd();
                sdDetail += detailOnly.sd();
                used += total.count();
            }
            double t = sdTotal / anchors.size();
            double b = sdBase / anchors.size();
            double d = sdDetail / anchors.size();
            double share = (b * b + d * d) > 0.0D ? (d * d) / (b * b + d * d) : 0.0D;
            System.out.printf(Locale.ROOT, "%.0f|%.5f|%.5f|%.5f|%.1f%%|%d%n",
                    window, t, b, d, 100.0D * share, used);
        }
    }

    // -----------------------------------------------------------------
    // STEP C - what survives transmittance weighting
    // -----------------------------------------------------------------

    private enum Band { FULL, BASE_ONLY, DETAIL_ONLY }

    private static void stepC(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Pose inside,
            Pose through) {
        System.out.println("\n--- STEP C: transmittance-weighted visible contrast ---");
        System.out.println("Each ray sees sum(T_i * (1-exp(-sigma_i ds)) * d_i) / sum(weights)."
                + " The spread of that across rays is the ceiling on image contrast");
        System.out.println("for any shading term that reads local density.");
        System.out.println("pose|band|point SD|visible mean|visible SD|visible SD / point SD|"
                + "effective depth blocks");

        for (Pose pose : List.of(inside, through)) {
            for (Band band : Band.values()) {
                visibleContrast(sampler, lobes, pose, band);
            }
        }
    }

    private static void visibleContrast(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Pose pose,
            Band band) {
        int rays = 2048;
        double step = 1.0D;
        double maxT = 240.0D;
        Random random = new Random(SEED + band.ordinal() * 7919L);
        double[] fields = new double[2];
        sampler.noiseFieldsAt(pose.x(), pose.y(), pose.z(), fields);
        double frozenBase = fields[0];
        double frozenDetail = fields[1];

        Accumulator point = new Accumulator();
        Accumulator visible = new Accumulator();
        Accumulator depth = new Accumulator();
        for (int i = 0; i < rays; i++) {
            double[] dir = randomDirection(random);
            double transmittance = 1.0D;
            double weightSum = 0.0D;
            double weighted = 0.0D;
            double depthSum = 0.0D;
            for (double t = step * 0.5D; t <= maxT && transmittance > 1.0E-3D; t += step) {
                double px = pose.x() + dir[0] * t;
                double py = pose.y() + dir[1] * t;
                double pz = pose.z() + dir[2] * t;
                double coverage = sampler.coverageAt(lobes, px, py, pz);
                double density;
                if (coverage <= 0.0D) {
                    density = 0.0D;
                } else {
                    double strength = sampler.envelopeStrengthAt(lobes, px, py, pz);
                    sampler.noiseFieldsAt(px, py, pz, fields);
                    double baseField = band == Band.DETAIL_ONLY ? frozenBase : fields[0];
                    double detailFbm = band == Band.BASE_ONLY ? frozenDetail : fields[1];
                    density = sampler.densityFromFields(coverage, strength, baseField, detailFbm);
                }
                point.add(density);
                double sigma = density * DENSITY_MUL * EXTINCTION_SCALE;
                double absorbed = 1.0D - Math.exp(-sigma * step);
                double weight = transmittance * absorbed;
                weighted += weight * density;
                weightSum += weight;
                depthSum += weight * t;
                transmittance *= Math.exp(-sigma * step);
            }
            if (weightSum > 1.0E-6D) {
                visible.add(weighted / weightSum);
                depth.add(depthSum / weightSum);
            }
        }
        System.out.printf(Locale.ROOT, "%s|%s|%.5f|%.5f|%.5f|%.4f|%.2f%n",
                pose.name(), band, point.sd(), visible.mean(), visible.sd(),
                point.sd() > 0.0D ? visible.sd() / point.sd() : 0.0D, depth.mean());
    }

    // -----------------------------------------------------------------
    // STEP D - the two-sample inside estimator against 4, 8 and a full march
    // -----------------------------------------------------------------

    private static void stepD(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Pose inside) {
        System.out.println("\n--- STEP D: the inside light estimator, 2 vs 4 vs 8 vs full ---");
        System.out.println("Production is od = d(p)*18 + d(p + L*28)*82, one local sample and one"
                + " detached probe. N-sample arms are trapezoid quadrature over the same");
        System.out.println("100-block nominal path. 'full' is a 1-block-step integral over that"
                + " path. Sample set: points a buried view ray actually shades, drawn along");
        System.out.println("rays from the pose and kept while transmittance is still above 0.05.");

        // Sun elevations spanning what the capture and the route see.
        double[] elevations = {20.0D, 45.0D, 70.0D};
        List<double[]> shaded = shadedSamples(sampler, lobes, inside, 2000);
        System.out.printf(Locale.ROOT, "shaded sample set: %d points%n", shaded.size());

        System.out.println("sunElev|estimator|od mean|od SD|od SD/mean|corr vs full|"
                + "RMS err vs full|s0 mean density|s0 ball SD|s1 mean density|s1 ball SD");
        for (double elevation : elevations) {
            double rad = Math.toRadians(elevation);
            double horizontal = Math.cos(rad);
            double[] light = {horizontal * 0.7071D, Math.sin(rad), horizontal * 0.7071D};
            List<double[]> rows = new ArrayList<>();
            for (double[] p : shaded) {
                double odFull = marchOpticalDepth(sampler, lobes, p, light, ESTIMATOR_PATH, 1.0D);
                double local = sampler.densityAt(lobes, p[0], p[1], p[2]);
                double forward = sampler.densityAt(lobes,
                        p[0] + light[0] * FORWARD_DISTANCE,
                        p[1] + light[1] * FORWARD_DISTANCE,
                        p[2] + light[2] * FORWARD_DISTANCE);
                double od2 = local * LOCAL_WEIGHT + forward * FORWARD_WEIGHT;
                double od4 = trapezoid(sampler, lobes, p, light, ESTIMATOR_PATH, 4);
                double od8 = trapezoid(sampler, lobes, p, light, ESTIMATOR_PATH, 8);
                rows.add(new double[]{odFull, od2, od4, od8});
            }
            String[] labels = {"full (100 taps)", "2 (production)", "4 (diagnostic)",
                    "8 (diagnostic)"};
            for (int column = 0; column < 4; column++) {
                Accumulator acc = new Accumulator();
                Accumulator err = new Accumulator();
                double[] xs = new double[rows.size()];
                double[] ys = new double[rows.size()];
                for (int i = 0; i < rows.size(); i++) {
                    acc.add(rows.get(i)[column]);
                    double e = rows.get(i)[column] - rows.get(i)[0];
                    err.add(e * e);
                    xs[i] = rows.get(i)[column];
                    ys[i] = rows.get(i)[0];
                }
                double corr = correlation(xs, ys);
                String ball0 = "";
                String ball1 = "";
                if (column == 1) {
                    double[] s0 = ballStats(sampler, lobes, shaded,
                            new double[]{0.0D, 0.0D, 0.0D}, 4.0D);
                    double[] s1 = ballStats(sampler, lobes, shaded,
                            new double[]{light[0] * FORWARD_DISTANCE,
                                    light[1] * FORWARD_DISTANCE,
                                    light[2] * FORWARD_DISTANCE}, 4.0D);
                    ball0 = String.format(Locale.ROOT, "%.4f|%.5f", s0[0], s0[1]);
                    ball1 = String.format(Locale.ROOT, "%.4f|%.5f", s1[0], s1[1]);
                }
                System.out.printf(Locale.ROOT, "%.0f|%s|%.3f|%.3f|%.4f|%.4f|%.3f|%s|%s%n",
                        elevation, labels[column], acc.mean(), acc.sd(),
                        acc.mean() > 0.0D ? acc.sd() / acc.mean() : 0.0D,
                        corr, Math.sqrt(err.mean()), ball0, ball1);
            }
        }

        // What the sample separation costs: the correlation of density with
        // itself at the estimator's own 28-block probe offset and around it.
        System.out.println("\nseparation blocks|density autocorrelation along the light direction");
        double rad = Math.toRadians(45.0D);
        double[] light = {Math.cos(rad) * 0.7071D, Math.sin(rad), Math.cos(rad) * 0.7071D};
        for (double separation : new double[]{2, 4, 8, 14, 20, 28, 40, 60, 100}) {
            double[] xs = new double[shaded.size()];
            double[] ys = new double[shaded.size()];
            for (int i = 0; i < shaded.size(); i++) {
                double[] p = shaded.get(i);
                xs[i] = sampler.densityAt(lobes, p[0], p[1], p[2]);
                ys[i] = sampler.densityAt(lobes,
                        p[0] + light[0] * separation,
                        p[1] + light[1] * separation,
                        p[2] + light[2] * separation);
            }
            System.out.printf(Locale.ROOT, "%.0f|%.4f%n", separation, correlation(xs, ys));
        }
    }

    // -----------------------------------------------------------------
    // STEP E - the ANGULAR scale of what is visible, which is what the frame
    // shows. A ray sees a transmittance-weighted average; two neighbouring
    // pixels differ only where the field differs BETWEEN their directions
    // inside the visible ball. A 109-block feature is nearly constant across a
    // 10-block ball, so it produces almost no pixel-to-pixel contrast however
    // much variance it carries.
    // -----------------------------------------------------------------

    private static void stepE(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Pose inside,
            Pose through) {
        System.out.println("\n--- STEP E: angular structure of the visible field ---");
        System.out.println("A square patch of directions is traced like a camera frustum. The"
                + " autocorrelation of visible density across that patch gives the angular");
        System.out.println("scale of the structure; multiplying by the effective depth converts"
                + " it to the world-space scale a viewer would read.");
        System.out.println("pose|visible mean|visible SD|relative SD|effective depth|"
                + "1/e angular lag (deg)|world scale (blocks)");
        for (Pose pose : List.of(inside, through)) {
            angularStructure(sampler, lobes, pose);
        }
    }

    private static void angularStructure(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Pose pose) {
        int n = 96;
        double fovDegrees = 70.0D;
        double halfTan = Math.tan(Math.toRadians(fovDegrees * 0.5D));
        double[][] grid = new double[n][n];
        double step = 1.0D;
        double maxT = 240.0D;
        double[] fields = new double[2];
        Accumulator depth = new Accumulator();
        Accumulator visible = new Accumulator();
        for (int gy = 0; gy < n; gy++) {
            for (int gx = 0; gx < n; gx++) {
                double sx = (2.0D * (gx + 0.5D) / n - 1.0D) * halfTan;
                double sy = (2.0D * (gy + 0.5D) / n - 1.0D) * halfTan;
                double len = Math.sqrt(sx * sx + sy * sy + 1.0D);
                double[] dir = {sx / len, sy / len, 1.0D / len};
                double transmittance = 1.0D;
                double weightSum = 0.0D;
                double weighted = 0.0D;
                double depthSum = 0.0D;
                for (double t = step * 0.5D; t <= maxT && transmittance > 1.0E-3D; t += step) {
                    double px = pose.x() + dir[0] * t;
                    double py = pose.y() + dir[1] * t;
                    double pz = pose.z() + dir[2] * t;
                    double coverage = sampler.coverageAt(lobes, px, py, pz);
                    double density = 0.0D;
                    if (coverage > 0.0D) {
                        double strength = sampler.envelopeStrengthAt(lobes, px, py, pz);
                        sampler.noiseFieldsAt(px, py, pz, fields);
                        density = sampler.densityFromFields(
                                coverage, strength, fields[0], fields[1]);
                    }
                    double sigma = density * DENSITY_MUL * EXTINCTION_SCALE;
                    double absorbed = 1.0D - Math.exp(-sigma * step);
                    double weight = transmittance * absorbed;
                    weighted += weight * density;
                    weightSum += weight;
                    depthSum += weight * t;
                    transmittance *= Math.exp(-sigma * step);
                }
                double value = weightSum > 1.0E-6D ? weighted / weightSum : 0.0D;
                grid[gy][gx] = value;
                if (weightSum > 1.0E-6D) {
                    visible.add(value);
                    depth.add(depthSum / weightSum);
                }
            }
        }
        // Autocorrelation across the patch, averaged over both axes.
        double mean = visible.mean();
        double variance = visible.sd() * visible.sd();
        int maxLag = n / 3;
        int lagAtE = -1;
        for (int lag = 1; lag <= maxLag; lag++) {
            double sum = 0.0D;
            int count = 0;
            for (int gy = 0; gy < n; gy++) {
                for (int gx = 0; gx + lag < n; gx++) {
                    sum += (grid[gy][gx] - mean) * (grid[gy][gx + lag] - mean);
                    count++;
                }
            }
            for (int gx = 0; gx < n; gx++) {
                for (int gy = 0; gy + lag < n; gy++) {
                    sum += (grid[gy][gx] - mean) * (grid[gy + lag][gx] - mean);
                    count++;
                }
            }
            double correlation = variance > 0.0D ? (sum / count) / variance : 0.0D;
            if (correlation < 1.0D / Math.E) {
                lagAtE = lag;
                break;
            }
        }
        double degreesPerCell = fovDegrees / n;
        double angular = lagAtE > 0 ? lagAtE * degreesPerCell : Double.NaN;
        double world = Math.toRadians(angular) * depth.mean();
        System.out.printf(Locale.ROOT, "%s|%.5f|%.5f|%.4f|%.2f|%.3f|%.2f%n",
                pose.name(), mean, visible.sd(),
                mean > 0.0D ? visible.sd() / mean : 0.0D,
                depth.mean(), angular, world);
    }

    /** Points a buried view ray actually shades, weighted by where transmittance still is. */
    private static List<double[]> shadedSamples(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Pose pose,
            int target) {
        Random random = new Random(SEED * 3L);
        List<double[]> out = new ArrayList<>();
        int guard = 0;
        while (out.size() < target && guard++ < 1000000) {
            double[] dir = randomDirection(random);
            double transmittance = 1.0D;
            for (double t = 1.0D; t <= 120.0D && transmittance > 0.05D; t += 2.0D) {
                double px = pose.x() + dir[0] * t;
                double py = pose.y() + dir[1] * t;
                double pz = pose.z() + dir[2] * t;
                double density = sampler.densityAt(lobes, px, py, pz);
                if (density > 0.02D) {
                    out.add(new double[]{px, py, pz});
                    if (out.size() >= target) {
                        break;
                    }
                }
                transmittance *= Math.exp(-density * DENSITY_MUL * EXTINCTION_SCALE * 2.0D);
            }
        }
        return out;
    }

    private static double marchOpticalDepth(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            double[] p,
            double[] light,
            double path,
            double step) {
        double od = 0.0D;
        for (double t = step * 0.5D; t <= path; t += step) {
            od += sampler.densityAt(lobes,
                    p[0] + light[0] * t, p[1] + light[1] * t, p[2] + light[2] * t) * step;
        }
        return od;
    }

    /** Trapezoid quadrature of the same 100-block path with n evenly spaced taps. */
    private static double trapezoid(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            double[] p,
            double[] light,
            double path,
            int taps) {
        double h = path / (taps - 1);
        double od = 0.0D;
        for (int i = 0; i < taps; i++) {
            double t = i * h;
            double density = sampler.densityAt(lobes,
                    p[0] + light[0] * t, p[1] + light[1] * t, p[2] + light[2] * t);
            od += density * h * (i == 0 || i == taps - 1 ? 0.5D : 1.0D);
        }
        return od;
    }

    /**
     * Mean density and mean SD in a small ball centred on each sample, offset
     * by {@code offset}. The mean is the estimator's own input at that tap; the
     * SD is how much local variation that tap has available to carry.
     */
    private static double[] ballStats(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            List<double[]> points,
            double[] offset,
            double radius) {
        Random random = new Random(SEED * 11L);
        double sdSum = 0.0D;
        double meanSum = 0.0D;
        int used = 0;
        for (int i = 0; i < points.size(); i += 8) {
            double[] p = points.get(i);
            meanSum += sampler.densityAt(lobes,
                    p[0] + offset[0], p[1] + offset[1], p[2] + offset[2]);
            Accumulator acc = new Accumulator();
            for (int s = 0; s < 64; s++) {
                double[] dir = randomDirection(random);
                double r = radius * Math.cbrt(random.nextDouble());
                acc.add(sampler.densityAt(lobes,
                        p[0] + offset[0] + dir[0] * r,
                        p[1] + offset[1] + dir[1] * r,
                        p[2] + offset[2] + dir[2] * r));
            }
            sdSum += acc.sd();
            used++;
        }
        return used > 0
                ? new double[]{meanSum / used, sdSum / used}
                : new double[]{0.0D, 0.0D};
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static double[] randomDirection(Random random) {
        double z = random.nextDouble() * 2.0D - 1.0D;
        double a = random.nextDouble() * Math.PI * 2.0D;
        double r = Math.sqrt(Math.max(0.0D, 1.0D - z * z));
        return new double[]{Math.cos(a) * r, z, Math.sin(a) * r};
    }

    private static double correlation(double[] xs, double[] ys) {
        int n = xs.length;
        if (n < 2) {
            return 0.0D;
        }
        double mx = 0.0D;
        double my = 0.0D;
        for (int i = 0; i < n; i++) {
            mx += xs[i];
            my += ys[i];
        }
        mx /= n;
        my /= n;
        double sxy = 0.0D;
        double sxx = 0.0D;
        double syy = 0.0D;
        for (int i = 0; i < n; i++) {
            double dx = xs[i] - mx;
            double dy = ys[i] - my;
            sxy += dx * dy;
            sxx += dx * dx;
            syy += dy * dy;
        }
        return sxx > 0.0D && syy > 0.0D ? sxy / Math.sqrt(sxx * syy) : 0.0D;
    }

    private static final class Accumulator {
        private long count;
        private double sum;
        private double sumSq;

        void add(double value) {
            count++;
            sum += value;
            sumSq += value * value;
        }

        long count() {
            return count;
        }

        double mean() {
            return count > 0 ? sum / count : 0.0D;
        }

        double sd() {
            if (count < 2) {
                return 0.0D;
            }
            double m = mean();
            return Math.sqrt(Math.max(0.0D, sumSq / count - m * m));
        }
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
