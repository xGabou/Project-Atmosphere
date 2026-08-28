package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * T100: measures the baked cloud noise and re-derives every storm morphology
 * threshold from it.
 *
 * <p>This runs the production bake through {@link CloudNoiseFieldModel}, so the
 * statistics describe exactly the volumes the GPU samples. It then recomputes
 * each derived constant in {@link StormMorphologyThresholds} from the
 * measurement and fails if a recorded value has drifted. Thresholds are
 * therefore never adjusted to accommodate an observed result; a drift is a
 * signal that the recorded derivation is stale.
 */
public final class StormDensityThresholdSandbox {
    private static final int SAMPLE_COUNT = 262_144;
    /** Storm-sized world volume, comfortably larger than one noise tile in every axis. */
    private static final double SPAN_XZ = 2048.0D;
    private static final double BASE_Y = 200.0D;
    private static final double SPAN_Y = 320.0D;
    /** Measured descriptor strengths in the live T124 severe-storm capture. */
    private static final double[] LIVE_DESCRIPTOR_STRENGTHS = {
            0.7832D, 0.8792D, 0.9485D, 1.0000D, 0.9700D,
            0.9539D, 0.8222D, 0.7231D, 0.7851D, 0.7992D
    };
    private static final String[] LIVE_DESCRIPTOR_ROLES = {
            "BASE", "BASE", "CORE", "CORE", "TOWER",
            "TOWER", "ANVIL", "ANVIL", "ANVIL", "ANVIL"
    };

    private StormDensityThresholdSandbox() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && "scaleSurvey".equals(args[0])) {
            surveyBaseFieldScales();
            return;
        }
        Measurement measurement = measure();
        measurement.print();
        measurement.verifyRecordedThresholds();
        System.out.println("PHASE4S_RESULT|T100 measured noise thresholds|PASSED|"
                + "all recorded statistics and derivations reproduced");
    }

    /**
     * Measures the mean feature size, in blocks, of the storm base field at a
     * given domain scale.
     *
     * <p>The field is sampled along random world-space lines at one-block
     * steps; the mean distance between successive crossings of the field's own
     * median is half a dominant wavelength, so twice it is the mean feature
     * size. This is what decides whether the coverage remap carves the storm
     * into coherent billows or shreds it: a feature size well below the
     * storm's smallest macro dimension re-carves the macro shape rather than
     * decorating it.
     */
    static double measureBaseFieldFeatureBlocks(byte[] base, double scale, double warpAmplitude) {
        double[] texel = new double[4];
        double[] domain = new double[3];
        Random random = new Random(0xFEA7_5123L);
        int lines = 256;
        int steps = 1024;

        // First pass: the field's median at this scale.
        double[] values = new double[lines * 64];
        int valueIndex = 0;
        for (int line = 0; line < lines; line++) {
            double x = (random.nextDouble() - 0.5D) * SPAN_XZ;
            double z = (random.nextDouble() - 0.5D) * SPAN_XZ;
            double y = BASE_Y + random.nextDouble() * SPAN_Y;
            for (int step = 0; step < 64; step++) {
                values[valueIndex++] = sampleStormBaseField(
                        base, x + step * 16.0D, y, z, scale, warpAmplitude, texel, domain);
            }
        }
        double median = percentile(values, 0.5D);

        random = new Random(0xFEA7_5123L);
        long crossings = 0L;
        long blocks = 0L;
        for (int line = 0; line < lines; line++) {
            double x = (random.nextDouble() - 0.5D) * SPAN_XZ;
            double z = (random.nextDouble() - 0.5D) * SPAN_XZ;
            double y = BASE_Y + random.nextDouble() * SPAN_Y;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            boolean previousAbove = sampleStormBaseField(
                    base, x, y, z, scale, warpAmplitude, texel, domain) >= median;
            for (int step = 1; step < steps; step++) {
                double sx = x + dx * step;
                double sz = z + dz * step;
                boolean above = sampleStormBaseField(
                        base, sx, y, sz, scale, warpAmplitude, texel, domain) >= median;
                if (above != previousAbove) {
                    crossings++;
                }
                previousAbove = above;
                blocks++;
            }
        }
        if (crossings == 0L) {
            return Double.MAX_VALUE;
        }
        // Mean crossing interval is half a wavelength.
        return 2.0D * blocks / (double) crossings;
    }

    private static double sampleStormBaseField(
            byte[] base,
            double x,
            double y,
            double z,
            double scale,
            double warpAmplitude,
            double[] texel,
            double[] domain
    ) {
        StormDensityModel.baseNoiseDomain(x, y, z, scale, warpAmplitude, domain);
        CloudNoiseFieldModel.sampleBase(base, domain[0], domain[1], domain[2], texel);
        double lowFbm = StormDensityModel.lowFbm(texel[1], texel[2], texel[3]);
        return StormDensityModel.stormBaseField(
                StormDensityModel.baseCarrier(texel[0], lowFbm));
    }

    static Measurement measure() {
        byte[] base = CloudNoiseFieldModel.bakeBase();
        byte[] detail = CloudNoiseFieldModel.bakeDetail();

        double[] baseTexel = new double[4];
        double[] detailTexel = new double[4];
        double[] domain = new double[3];

        double[] carriers = new double[SAMPLE_COUNT];
        double[] detailFbms = new double[SAMPLE_COUNT];
        double[] detailR = new double[SAMPLE_COUNT];
        double[] detailG = new double[SAMPLE_COUNT];
        double[] detailB = new double[SAMPLE_COUNT];
        double[] lowFbms = new double[SAMPLE_COUNT];

        Random random = new Random(0x5701_1CE5L);
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            double x = (random.nextDouble() - 0.5D) * SPAN_XZ;
            double z = (random.nextDouble() - 0.5D) * SPAN_XZ;
            double y = BASE_Y + random.nextDouble() * SPAN_Y;

            StormDensityModel.baseNoiseDomain(
                    x, y, z, StormDensityModel.STORM_BASE_NOISE_SCALE, domain);
            CloudNoiseFieldModel.sampleBase(base, domain[0], domain[1], domain[2], baseTexel);
            double lowFbm = StormDensityModel.lowFbm(baseTexel[1], baseTexel[2], baseTexel[3]);
            lowFbms[index] = lowFbm;
            carriers[index] = StormDensityModel.baseCarrier(baseTexel[0], lowFbm);

            StormDensityModel.detailNoiseDomain(x, y, z, domain);
            CloudNoiseFieldModel.sampleDetail(detail, domain[0], domain[1], domain[2], detailTexel);
            detailR[index] = detailTexel[0];
            detailG[index] = detailTexel[1];
            detailB[index] = detailTexel[2];
            detailFbms[index] = StormDensityModel.detailFbm(
                    detailTexel[0], detailTexel[1], detailTexel[2]);
        }

        double carrierP05 = percentile(carriers, 0.05D);
        double carrierP95 = percentile(carriers, 0.95D);

        // The storm base field is the carrier normalized onto its measured
        // usable range. Recompute it here from the freshly measured
        // percentiles so the reported SD is self-consistent even before the
        // recorded constants are updated.
        double[] baseFields = new double[SAMPLE_COUNT];
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            baseFields[index] = StormDensityModel.smoothstep(
                    carrierP05, carrierP95, carriers[index]);
        }

        // Per-band variance shares. Each detail octave enters the FBM with a
        // fixed weight, so its share of the field variance is the variance of
        // its own weighted contribution over the total.
        double varR = variance(detailR) * sq(CloudNoiseFieldModel.DETAIL_WEIGHT_R);
        double varG = variance(detailG) * sq(CloudNoiseFieldModel.DETAIL_WEIGHT_G);
        double varB = variance(detailB) * sq(CloudNoiseFieldModel.DETAIL_WEIGHT_B);
        double varTotal = varR + varG + varB;

        return new Measurement(
                standardDeviation(detailFbms),
                percentile(detailFbms, 0.05D),
                standardDeviation(baseFields),
                carrierP05,
                carrierP95,
                standardDeviation(lowFbms),
                varR / varTotal,
                varG / varTotal,
                varB / varTotal
        );
    }

    record Measurement(
            double detailFbmSd,
            double detailFbmP05,
            double stormBaseFieldSd,
            double baseCarrierP05,
            double baseCarrierP95,
            double lowFbmSd,
            double bandShareR,
            double bandShareG,
            double bandShareB
    ) {
        /** Minimum CORE_FILL for one real descriptor strength at the deepest erosion bite. */
        double requiredCoreFill(double strength) {
            double bite = StormDensityModel.STORM_EROSION * (1.0D - detailFbmP05);
            // At coverage s and baseField=0, body = 1 - 1 / ((1 + C) * s).
            // Requiring body > bite gives C > 1 / (s * (1 - bite)) - 1.
            return 1.0D / (strength * (1.0D - bite)) - 1.0D;
        }

        double weakestRequiredCoreFill() {
            double required = Double.NEGATIVE_INFINITY;
            for (double strength : LIVE_DESCRIPTOR_STRENGTHS) {
                required = Math.max(required, requiredCoreFill(strength));
            }
            return required;
        }

        double retainedDensityAtWorstCase(double strength) {
            double bite = StormDensityModel.STORM_EROSION * (1.0D - detailFbmP05);
            return Math.max(StormDensityModel.stormBody(strength, strength, 0.0D) - bite, 0.0D);
        }

        /** Analytic density SD over an unsaturated full-coverage region. */
        double analyticOccupiedSd() {
            double baseTerm = stormBaseFieldSd / (1.0D + StormMorphologyThresholds.CORE_FILL);
            double detailTerm = StormDensityModel.STORM_EROSION * detailFbmSd;
            return Math.sqrt(baseTerm * baseTerm + detailTerm * detailTerm);
        }

        void print() {
            System.out.println("--- T100 measured cloud noise statistics ---");
            System.out.println("samples                     = " + SAMPLE_COUNT);
            line("detail FBM sd", detailFbmSd);
            line("detail FBM p05", detailFbmP05);
            line("base carrier p05", baseCarrierP05);
            line("base carrier p95", baseCarrierP95);
            line("storm base field sd", stormBaseFieldSd);
            line("base low FBM sd", lowFbmSd);
            line("detail band share R", bandShareR);
            line("detail band share G", bandShareG);
            line("detail band share B", bandShareB);
            System.out.println("--- derived ---");
            line("required CORE_FILL (weakest live minimum)", weakestRequiredCoreFill());
            line("recorded CORE_FILL", StormMorphologyThresholds.CORE_FILL);
            for (int index = 0; index < LIVE_DESCRIPTOR_STRENGTHS.length; index++) {
                line("effective fill " + LIVE_DESCRIPTOR_ROLES[index]
                                + " @ " + String.format(Locale.ROOT, "%.4f", LIVE_DESCRIPTOR_STRENGTHS[index]),
                        StormDensityModel.coreFillForEnvelopeStrength(LIVE_DESCRIPTOR_STRENGTHS[index]));
                line("p05 retained " + LIVE_DESCRIPTOR_ROLES[index]
                                + " @ " + String.format(Locale.ROOT, "%.4f", LIVE_DESCRIPTOR_STRENGTHS[index]),
                        retainedDensityAtWorstCase(LIVE_DESCRIPTOR_STRENGTHS[index]));
            }
            line("analytic occupied sd", analyticOccupiedSd());
            line("recorded MIN_OCCUPIED_REGION_SD", StormMorphologyThresholds.MIN_OCCUPIED_REGION_SD);
            line("lowest detail wavelength (blocks)",
                    StormMorphologyThresholds.LOWEST_DETAIL_WAVELENGTH_BLOCKS);
            line("min variance region (blocks)",
                    StormMorphologyThresholds.MIN_VARIANCE_REGION_BLOCKS);
        }

        void verifyRecordedThresholds() {
            double tolerance = StormMorphologyThresholds.MEASUREMENT_TOLERANCE;
            requireNear("DETAIL_FBM_SD", StormMorphologyThresholds.DETAIL_FBM_SD, detailFbmSd, tolerance);
            requireNear("DETAIL_FBM_P05", StormMorphologyThresholds.DETAIL_FBM_P05, detailFbmP05, tolerance);
            requireNear("BASE_CARRIER_P05", StormMorphologyThresholds.BASE_CARRIER_P05,
                    baseCarrierP05, tolerance);
            requireNear("BASE_CARRIER_P95", StormMorphologyThresholds.BASE_CARRIER_P95,
                    baseCarrierP95, tolerance);
            requireNear("STORM_BASE_FIELD_SD", StormMorphologyThresholds.STORM_BASE_FIELD_SD,
                    stormBaseFieldSd, tolerance);
            requireNear("DETAIL_BAND_SHARE_R", StormMorphologyThresholds.DETAIL_BAND_SHARE_R,
                    bandShareR, tolerance);
            requireNear("DETAIL_BAND_SHARE_G", StormMorphologyThresholds.DETAIL_BAND_SHARE_G,
                    bandShareG, tolerance);
            requireNear("DETAIL_BAND_SHARE_B", StormMorphologyThresholds.DETAIL_BAND_SHARE_B,
                    bandShareB, tolerance);

            for (int index = 0; index < LIVE_DESCRIPTOR_STRENGTHS.length; index++) {
                double strength = LIVE_DESCRIPTOR_STRENGTHS[index];
                double fill = StormDensityModel.coreFillForEnvelopeStrength(strength);
                double required = requiredCoreFill(strength);
                require(fill > required,
                        LIVE_DESCRIPTOR_ROLES[index] + " effective fill " + format(fill)
                                + " no longer exceeds its derived minimum " + format(required));
                require(fill < required + 0.10D,
                        LIVE_DESCRIPTOR_ROLES[index] + " effective fill " + format(fill)
                                + " is far above its derived minimum " + format(required)
                                + "; that role would saturate toward uniform density");
                require(retainedDensityAtWorstCase(LIVE_DESCRIPTOR_STRENGTHS[index]) > 0.0D,
                        LIVE_DESCRIPTOR_ROLES[index] + " strength "
                                + format(LIVE_DESCRIPTOR_STRENGTHS[index])
                                + " loses all p05 mass after erosion");
            }
            requireNear("MIN_OCCUPIED_REGION_SD",
                    StormMorphologyThresholds.MIN_OCCUPIED_REGION_SD,
                    analyticOccupiedSd() * 0.5D,
                    0.005D);
        }

        private static void line(String label, double value) {
            System.out.printf(Locale.ROOT, "%-36s = %.4f%n", label, value);
        }
    }

    /**
     * One-off survey used to derive the storm base-noise domain scale from the
     * field's measured feature size rather than by eye.
     */
    static void surveyBaseFieldScales() {
        byte[] base = CloudNoiseFieldModel.bakeBase();
        System.out.println("--- storm base field feature size by domain scale ---");
        System.out.printf(Locale.ROOT, "%-10s %-14s %-16s %-14s%n",
                "scale", "tile (blocks)", "warp/scale ratio", "feature (blocks)");
        double[] scales = {0.0052D, 0.0040D, 0.0032D, 0.0025D, 0.0020D, 0.0016D, 0.0012D};
        for (double scale : scales) {
            double legacyRatio = StormDensityModel.LEGACY_WARP_AMPLITUDE
                    * StormDensityModel.WARP_WAVE_NUMBER / scale;
            double featureLegacy = measureBaseFieldFeatureBlocks(
                    base, scale, StormDensityModel.LEGACY_WARP_AMPLITUDE);
            double proportional = StormDensityModel.proportionalWarpAmplitude(scale);
            double featureProportional = measureBaseFieldFeatureBlocks(base, scale, proportional);
            System.out.printf(Locale.ROOT,
                    "%-10.4f %-14.0f legacy=%-9.3f legacy=%-8.1f proportional=%.1f (warp=%.4f)%n",
                    scale, 1.0D / scale, legacyRatio, featureLegacy, featureProportional,
                    proportional);
        }
    }

    private static double sq(double value) {
        return value * value;
    }

    private static double variance(double[] values) {
        double mean = 0.0D;
        for (double value : values) {
            mean += value;
        }
        mean /= values.length;
        double sum = 0.0D;
        for (double value : values) {
            double delta = value - mean;
            sum += delta * delta;
        }
        return sum / values.length;
    }

    private static double standardDeviation(double[] values) {
        return Math.sqrt(variance(values));
    }

    private static double percentile(double[] values, double fraction) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int index = (int) Math.round(fraction * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static void requireNear(String label, double recorded, double measured, double tolerance) {
        require(Math.abs(recorded - measured) <= tolerance,
                label + " recorded " + format(recorded) + " but measured " + format(measured)
                        + " (tolerance " + format(tolerance) + ")");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
