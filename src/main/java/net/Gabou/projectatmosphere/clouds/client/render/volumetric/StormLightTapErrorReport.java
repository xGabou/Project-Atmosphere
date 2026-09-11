package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * T198 Tasks 1-3. The light-tap audit, joined on the CPU.
 *
 * <p>The audit program ({@code t198_light_audit}) marches exactly as
 * {@code t196_field_light} does and, at every light tap, evaluates the exact
 * production density beside the field's, accumulating per pixel and per tap
 * the signed error, its magnitude and square, the sample count, the over- and
 * under-estimate counts and both sides' sums. Each accumulator is one capture;
 * each decomposition variant (what the exact side evaluates) is one set of
 * captures. This class joins them with the anchor frame's pixel classes and
 * answers, per tap and per region: how biased is the field, which term of the
 * density carries the bias, and whether that term explains the pocket
 * luminance error the field produces.
 *
 * <p>Means, RMS and fractions are exact over the light samples. Medians and
 * p95s are over per-pixel means, because the per-sample distribution is not
 * retained; the line says so.
 */
final class StormLightTapErrorReport {
    static final int VARIANTS = 8;
    static final int OUTPUTS = 8;
    static final String[] VARIANT_NAMES = {
            "production", "detailNoiseMean", "erosionOff", "mip0", "weatherNeutral",
            "morphologyNeutral", "directFieldEquation", "erosionOffMip0"};
    static final String[] OUTPUT_NAMES = {
            "sumErr", "sumAbs", "sumSq", "count", "over", "under", "exactSum", "fieldSum"};
    /** The cone's tap spacing, 14 blocks growing by 1.42: what a density error is worth in optical depth. */
    static final double[] TAP_STEP = {14.0D, 14.0D * 1.42D, 14.0D * 1.42D * 1.42D,
            14.0D * 1.42D * 1.42D * 1.42D};
    private static final int TAPS = 4;
    private static final int O_SUM_ERR = 0;
    private static final int O_SUM_ABS = 1;
    private static final int O_SUM_SQ = 2;
    private static final int O_COUNT = 3;
    private static final int O_OVER = 4;
    private static final int O_UNDER = 5;
    private static final int O_EXACT = 6;
    private static final int O_FIELD = 7;

    private StormLightTapErrorReport() {
    }

    /** One region's aggregate for one variant and tap. */
    private record Stat(long samples, double meanErr, double meanAbs, double rms,
            double over, double under, double meanExact, double meanField,
            double medianPixelErr, double p95PixelAbs, int pixels) {
    }

    /**
     * @param anchor    the LEAN_FINAL frame the pixel classes come from
     * @param armLight  the field-light frame (same pose), or null; links the
     *                  optical-depth error to the luminance error per pixel
     * @param captures  [variant][output] audit captures; a missing variant
     *                  (null row) is skipped
     */
    static List<String> report(String pose,
            StormReferenceImageComparison.Reference anchor,
            StormReferenceImageComparison.Reference armLight,
            StormReferenceImageComparison.Reference[][] captures) {
        List<String> lines = new ArrayList<>();
        int width = anchor.width();
        int height = anchor.height();
        int n = width * height;
        int[] classes = StormImageQualityMetrics.pixelClasses(anchor.pixels(), width, height);
        int regions = StormImageQualityMetrics.CLASS_NAMES.length;
        // Region 0 in the output is "all cloud" (every class but none).
        String[] regionNames = new String[regions];
        regionNames[0] = "allCloud";
        for (int r = 1; r < regions; r++) {
            regionNames[r] = StormImageQualityMetrics.CLASS_NAMES[r];
        }
        float[][][] px = new float[VARIANTS][OUTPUTS][];
        boolean[] variantPresent = new boolean[VARIANTS];
        for (int v = 0; v < VARIANTS; v++) {
            if (captures[v] == null) {
                continue;
            }
            boolean complete = true;
            for (int o = 0; o < OUTPUTS; o++) {
                StormReferenceImageComparison.Reference capture = captures[v][o];
                if (capture == null || capture.width() != width || capture.height() != height) {
                    complete = false;
                    break;
                }
                px[v][o] = capture.pixels();
            }
            variantPresent[v] = complete;
            if (!complete) {
                lines.add("T198_TAP_ERROR pose=" + pose + " variant=" + VARIANT_NAMES[v]
                        + " evaluated=false reason=incomplete_captures");
            }
        }
        if (!variantPresent[0]) {
            lines.add("T198_TAP_ERROR pose=" + pose + " evaluated=false reason=no_production_variant");
            return lines;
        }

        Stat[][][] stats = new Stat[VARIANTS][TAPS][regions];
        for (int v = 0; v < VARIANTS; v++) {
            if (!variantPresent[v]) {
                continue;
            }
            for (int t = 0; t < TAPS; t++) {
                for (int r = 0; r < regions; r++) {
                    stats[v][t][r] = aggregate(px[v], t, classes, r, n);
                    Stat s = stats[v][t][r];
                    lines.add(String.format(Locale.ROOT,
                            "T198_TAP_ERROR pose=%s variant=%s tap=%d region=%s samples=%d pixels=%d"
                                    + " meanErr=%.6f medianPixelErr=%.6f meanAbs=%.6f rms=%.6f"
                                    + " p95PixelAbs=%.6f overFraction=%.4f underFraction=%.4f"
                                    + " meanExact=%.6f meanField=%.6f bias=%s",
                            pose, VARIANT_NAMES[v], t, regionNames[r], s.samples(), s.pixels(),
                            s.meanErr(), s.medianPixelErr(), s.meanAbs(), s.rms(), s.p95PixelAbs(),
                            s.over(), s.under(), s.meanExact(), s.meanField(),
                            s.samples() == 0 ? "n/a" : s.meanErr() > 0.0D ? "over" : "under"));
                }
                // The cone's optical-depth error: each tap's mean density
                // error times its step, summed - what the four taps together
                // add to the exponent.
            }
            for (int r = 0; r < regions; r++) {
                double opticalDepthErr = 0.0D;
                double opticalDepthExact = 0.0D;
                long samples = 0L;
                for (int t = 0; t < TAPS; t++) {
                    Stat s = stats[v][t][r];
                    if (s.samples() == 0) {
                        continue;
                    }
                    opticalDepthErr += s.meanErr() * TAP_STEP[t];
                    opticalDepthExact += s.meanExact() * TAP_STEP[t];
                    samples += s.samples();
                }
                lines.add(String.format(Locale.ROOT,
                        "T198_TAP_ERROR pose=%s variant=%s tap=all region=%s samples=%d"
                                + " coneOpticalDepthErr=%.6f coneOpticalDepthExact=%.6f"
                                + " relative=%.4f",
                        pose, VARIANT_NAMES[v], regionNames[r], samples, opticalDepthErr,
                        opticalDepthExact,
                        opticalDepthExact == 0.0D ? Double.NaN : opticalDepthErr / opticalDepthExact));
            }
        }

        // Task 2. Each variant removes one term from the exact side; how much
        // of the production error it takes with it is that term's share. The
        // direct-equation variant splits representation from interpolation.
        for (int v = 1; v < VARIANTS; v++) {
            if (!variantPresent[v]) {
                continue;
            }
            for (int r = 0; r < regions; r++) {
                for (int t = 0; t <= TAPS; t++) {
                    double errBefore;
                    double errAfter;
                    double exactShift;
                    double rmsBefore;
                    double rmsAfter;
                    long samples;
                    if (t < TAPS) {
                        Stat s0 = stats[0][t][r];
                        Stat sv = stats[v][t][r];
                        samples = s0.samples();
                        errBefore = s0.meanErr();
                        errAfter = sv.meanErr();
                        exactShift = s0.meanExact() - sv.meanExact();
                        rmsBefore = s0.rms();
                        rmsAfter = sv.rms();
                    } else {
                        samples = 0L;
                        errBefore = 0.0D;
                        errAfter = 0.0D;
                        exactShift = 0.0D;
                        rmsBefore = Double.NaN;
                        rmsAfter = Double.NaN;
                        for (int tt = 0; tt < TAPS; tt++) {
                            Stat s0 = stats[0][tt][r];
                            Stat sv = stats[v][tt][r];
                            if (s0.samples() == 0) {
                                continue;
                            }
                            samples += s0.samples();
                            errBefore += s0.meanErr() * TAP_STEP[tt];
                            errAfter += sv.meanErr() * TAP_STEP[tt];
                            exactShift += (s0.meanExact() - sv.meanExact()) * TAP_STEP[tt];
                        }
                    }
                    lines.add(String.format(Locale.ROOT,
                            "T198_DECOMPOSITION pose=%s term=%s tap=%s region=%s samples=%d"
                                    + " exactShift=%.6f errorBefore=%.6f errorAfter=%.6f"
                                    + " biasRemoved=%.6f biasRemovedFraction=%.4f rmsBefore=%.6f rmsAfter=%.6f",
                            pose, VARIANT_NAMES[v], t < TAPS ? Integer.toString(t) : "all(opticalDepth)",
                            regionNames[r], samples, exactShift, errBefore, errAfter,
                            errBefore - errAfter,
                            errBefore == 0.0D ? Double.NaN : (errBefore - errAfter) / errBefore,
                            rmsBefore, rmsAfter));
                }
            }
        }
        if (variantPresent[6]) {
            for (int r = 0; r < regions; r++) {
                double representation = 0.0D;
                double interpolation = 0.0D;
                double walk = 0.0D;
                double production = 0.0D;
                for (int t = 0; t < TAPS; t++) {
                    Stat s0 = stats[0][t][r];
                    Stat s6 = stats[6][t][r];
                    if (s0.samples() == 0) {
                        continue;
                    }
                    production += s0.meanErr() * TAP_STEP[t];
                    interpolation += s6.meanErr() * TAP_STEP[t];
                    representation += (s0.meanErr() - s6.meanErr()) * TAP_STEP[t];
                    if (variantPresent[7]) {
                        walk += (stats[7][t][r].meanErr() - s6.meanErr()) * TAP_STEP[t];
                    }
                }
                lines.add(String.format(Locale.ROOT,
                        "T198_SPLIT pose=%s region=%s productionOpticalDepthErr=%.6f"
                                + " representationErr=%.6f interpolationErr=%.6f buildModeWalkErr=%s"
                                + " representationFraction=%.4f interpolationFraction=%.4f",
                        pose, regionNames[r], production, representation, interpolation,
                        variantPresent[7] ? String.format(Locale.ROOT, "%.6f", walk) : "n/a",
                        production == 0.0D ? Double.NaN : representation / production,
                        production == 0.0D ? Double.NaN : interpolation / production));
            }
        }

        // Task 3. Per pixel, the missing term (exact under production minus
        // exact under the variant, in cone optical depth) against the field's
        // error (in cone optical depth): correlation, slope, and how much of
        // the error's variance the term explains, inside the pockets and out.
        double[] fieldErr = perPixelOpticalDepth(px[0], O_SUM_ERR, n);
        double[] lumErr = null;
        if (armLight != null && armLight.width() == width && armLight.height() == height) {
            lumErr = new double[n];
            float[] a = anchor.pixels();
            float[] b = armLight.pixels();
            for (int i = 0; i < n; i++) {
                int o = i * 4;
                lumErr[i] = (0.2126D * b[o] + 0.7152D * b[o + 1] + 0.0722D * b[o + 2])
                        - (0.2126D * a[o] + 0.7152D * a[o + 1] + 0.0722D * a[o + 2]);
            }
        }
        for (int v = 1; v < VARIANTS; v++) {
            if (!variantPresent[v]) {
                continue;
            }
            double[] exact0 = perPixelOpticalDepth(px[0], O_EXACT, n);
            double[] exactV = perPixelOpticalDepth(px[v], O_EXACT, n);
            double[] missing = new double[n];
            for (int i = 0; i < n; i++) {
                missing[i] = exact0[i] - exactV[i];
            }
            for (int r = 0; r < regions; r++) {
                lines.add(correlationLine("T198_BIAS_CAUSALITY", pose, VARIANT_NAMES[v],
                        regionNames[r], "missingTerm", "fieldErr", missing, fieldErr,
                        classes, r, px[0][O_COUNT], n));
            }
        }
        if (lumErr != null) {
            for (int r = 0; r < regions; r++) {
                lines.add(correlationLine("T198_LUMINANCE_LINK", pose, "production",
                        regionNames[r], "fieldErr", "luminanceErr", fieldErr, lumErr,
                        classes, r, px[0][O_COUNT], n));
            }
        }
        return lines;
    }

    private static boolean inRegion(int[] classes, int r, int i) {
        return r == 0 ? classes[i] != StormImageQualityMetrics.CLASS_NONE : classes[i] == r;
    }

    private static boolean hasSamples(float[] count, int i) {
        return count[i * 4] + count[i * 4 + 1] + count[i * 4 + 2] + count[i * 4 + 3] > 0.0F;
    }

    private static Stat aggregate(float[][] out, int t, int[] classes, int r, int n) {
        double sumErr = 0.0D;
        double sumAbs = 0.0D;
        double sumSq = 0.0D;
        double count = 0.0D;
        double over = 0.0D;
        double under = 0.0D;
        double exact = 0.0D;
        double field = 0.0D;
        List<Double> pixelErr = new ArrayList<>();
        List<Double> pixelAbs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!inRegion(classes, r, i)) {
                continue;
            }
            int o = i * 4 + t;
            double c = out[O_COUNT][o];
            if (c <= 0.0D) {
                continue;
            }
            sumErr += out[O_SUM_ERR][o];
            sumAbs += out[O_SUM_ABS][o];
            sumSq += out[O_SUM_SQ][o];
            count += c;
            over += out[O_OVER][o];
            under += out[O_UNDER][o];
            exact += out[O_EXACT][o];
            field += out[O_FIELD][o];
            pixelErr.add(out[O_SUM_ERR][o] / c);
            pixelAbs.add(out[O_SUM_ABS][o] / c);
        }
        if (count <= 0.0D) {
            return new Stat(0L, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0);
        }
        return new Stat(Math.round(count), sumErr / count, sumAbs / count,
                Math.sqrt(sumSq / count), over / count, under / count, exact / count,
                field / count, percentile(pixelErr, 0.5D), percentile(pixelAbs, 0.95D),
                pixelErr.size());
    }

    private static double percentile(List<Double> values, double q) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        double[] sorted = new double[values.size()];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = values.get(i);
        }
        Arrays.sort(sorted);
        int index = (int) Math.min(sorted.length - 1, Math.floor(q * (sorted.length - 1)));
        return sorted[index];
    }

    /** Per pixel, the tap means of one accumulator weighted by the tap steps. */
    private static double[] perPixelOpticalDepth(float[][] out, int output, int n) {
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            double total = 0.0D;
            for (int t = 0; t < TAPS; t++) {
                int o = i * 4 + t;
                double c = out[O_COUNT][o];
                if (c > 0.0D) {
                    total += out[output][o] / c * TAP_STEP[t];
                }
            }
            result[i] = total;
        }
        return result;
    }

    private static String correlationLine(String tag, String pose, String term, String region,
            String xName, String yName, double[] x, double[] y, int[] classes, int r,
            float[] count, int n) {
        double sx = 0.0D;
        double sy = 0.0D;
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (!inRegion(classes, r, i) || !hasSamples(count, i)) {
                continue;
            }
            sx += x[i];
            sy += y[i];
            m++;
        }
        if (m < 3) {
            return tag + " pose=" + pose + " term=" + term + " region=" + region
                    + " evaluated=false reason=too_few_pixels pixels=" + m;
        }
        double mx = sx / m;
        double my = sy / m;
        double sxx = 0.0D;
        double syy = 0.0D;
        double sxy = 0.0D;
        for (int i = 0; i < n; i++) {
            if (!inRegion(classes, r, i) || !hasSamples(count, i)) {
                continue;
            }
            double dx = x[i] - mx;
            double dy = y[i] - my;
            sxx += dx * dx;
            syy += dy * dy;
            sxy += dx * dy;
        }
        double rr = sxx <= 0.0D || syy <= 0.0D ? Double.NaN : sxy / Math.sqrt(sxx * syy);
        double slope = sxx <= 0.0D ? Double.NaN : sxy / sxx;
        // Variance of the error left after subtracting the term itself (slope
        // 1, the hypothesis that the term IS the error), against the error's
        // own variance; and the same for the mean.
        double residualSq = 0.0D;
        for (int i = 0; i < n; i++) {
            if (!inRegion(classes, r, i) || !hasSamples(count, i)) {
                continue;
            }
            double resid = (y[i] - my) - (x[i] - mx);
            residualSq += resid * resid;
        }
        double explainedVariance = syy <= 0.0D ? Double.NaN : 1.0D - residualSq / syy;
        return String.format(Locale.ROOT,
                "%s pose=%s term=%s region=%s pixels=%d mean%s=%.6f mean%s=%.6f r=%.4f"
                        + " slope=%.4f explainedVarianceAtUnitSlope=%.4f meanRatio=%.4f",
                tag, pose, term, region, m, capitalize(xName), mx, capitalize(yName), my, rr,
                slope, explainedVariance, my == 0.0D ? Double.NaN : mx / my);
    }

    private static String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
