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

    private StormDensityThresholdSandbox() {
    }

    public static void main(String[] args) {
        Measurement measurement = measure();
        measurement.print();
        measurement.verifyRecordedThresholds();
        System.out.println("PHASE4S_RESULT|T100 measured noise thresholds|PASSED|"
                + "all recorded statistics and derivations reproduced");
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
        /** Minimum CORE_FILL that keeps the weakest core sample above the deepest erosion bite. */
        double requiredCoreFill() {
            double bite = StormDensityModel.STORM_EROSION * (1.0D - detailFbmP05);
            // body(0) = CORE_FILL / (1 + CORE_FILL) > bite  =>  CORE_FILL > bite / (1 - bite)
            return bite / (1.0D - bite);
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
            line("required CORE_FILL (minimum)", requiredCoreFill());
            line("recorded CORE_FILL", StormMorphologyThresholds.CORE_FILL);
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

            require(StormMorphologyThresholds.CORE_FILL > requiredCoreFill(),
                    "CORE_FILL " + format(StormMorphologyThresholds.CORE_FILL)
                            + " no longer exceeds the derived minimum "
                            + format(requiredCoreFill())
                            + "; the storm core would develop erosion holes");
            require(StormMorphologyThresholds.CORE_FILL < requiredCoreFill() + 0.10D,
                    "CORE_FILL " + format(StormMorphologyThresholds.CORE_FILL)
                            + " is far above the derived minimum "
                            + format(requiredCoreFill())
                            + "; the core would saturate toward uniform density");
            requireNear("MIN_OCCUPIED_REGION_SD",
                    StormMorphologyThresholds.MIN_OCCUPIED_REGION_SD,
                    analyticOccupiedSd() * 0.5D,
                    0.005D);
        }

        private static void line(String label, double value) {
            System.out.printf(Locale.ROOT, "%-36s = %.4f%n", label, value);
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
