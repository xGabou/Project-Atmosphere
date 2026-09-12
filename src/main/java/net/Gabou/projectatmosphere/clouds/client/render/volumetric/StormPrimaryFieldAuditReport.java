package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * T202 Task 3. The primary-sample audit, joined on the CPU.
 *
 * <p>The audit program ({@code t202_primary_audit}) marches exactly as
 * {@code t202_primary_field} does and, at every primary body sample,
 * evaluates the exact production density beside the field's, accumulating per
 * pixel the signed error, its magnitude and square, its extremum, the over-
 * and under-estimate counts, the zero/nonzero and material (> 0.0008)
 * disagreements split by direction, the uncovered fallbacks, both sides' sums,
 * and the count and error of the samples the exact side places at a body edge
 * (0 < exact <= 0.05). Each group of four accumulators is one capture; this
 * class sums the four captures over the frame and per pixel class of the
 * anchor frame, and answers: how far is the field body from the exact body,
 * how often does it disagree about whether material is present, and where.
 *
 * <p>Means, RMS and fractions are exact over the primary samples. The maximum
 * is the frame's maximum over per-pixel maxima, so it is exact too.
 */
final class StormPrimaryFieldAuditReport {
    static final int OUTPUTS = 4;
    static final String[] OUTPUT_NAMES = {
            "count/sumErr/sumAbs/sumSq", "zeroDisagree/materialDisagree/over/under",
            "exactSum/fieldSum/edgeCount/edgeSumAbs",
            "maxAbs/fallback/exactZeroFieldNot/fieldZeroExactNot"};

    private StormPrimaryFieldAuditReport() {
    }

    /**
     * One line per region: all samples, then each pixel class of the anchor
     * frame. Regions with no samples print {@code samples=0} and nothing else.
     */
    static List<String> report(String label,
            StormReferenceImageComparison.Reference anchor,
            StormReferenceImageComparison.Reference[] captures) {
        List<String> lines = new ArrayList<>();
        int width = anchor.width();
        int height = anchor.height();
        int n = width * height;
        for (int o = 0; o < OUTPUTS; o++) {
            StormReferenceImageComparison.Reference capture = captures[o];
            if (capture == null || capture.width() != width || capture.height() != height) {
                lines.add("T202_PRIMARY_AUDIT " + label + " evaluated=false reason=missing_output"
                        + o + "(" + OUTPUT_NAMES[o] + ")");
                return lines;
            }
        }
        int[] classes = StormImageQualityMetrics.pixelClasses(anchor.pixels(), width, height);
        int regions = StormImageQualityMetrics.CLASS_NAMES.length + 1;
        String[] regionNames = new String[regions];
        regionNames[0] = "all";
        for (int r = 1; r < regions; r++) {
            regionNames[r] = StormImageQualityMetrics.CLASS_NAMES[r - 1];
        }
        float[] a0 = captures[0].pixels();
        float[] a1 = captures[1].pixels();
        float[] a2 = captures[2].pixels();
        float[] a3 = captures[3].pixels();
        for (int r = 0; r < regions; r++) {
            double count = 0.0D;
            double sumErr = 0.0D;
            double sumAbs = 0.0D;
            double sumSq = 0.0D;
            double zeroDisagree = 0.0D;
            double materialDisagree = 0.0D;
            double over = 0.0D;
            double under = 0.0D;
            double exactSum = 0.0D;
            double fieldSum = 0.0D;
            double edgeCount = 0.0D;
            double edgeSumAbs = 0.0D;
            double maxAbs = 0.0D;
            double fallback = 0.0D;
            double exactZeroFieldNot = 0.0D;
            double fieldZeroExactNot = 0.0D;
            int pixels = 0;
            for (int i = 0; i < n; i++) {
                if (r != 0 && classes[i] != r - 1) {
                    continue;
                }
                int p = i * 4;
                pixels++;
                count += a0[p];
                sumErr += a0[p + 1];
                sumAbs += a0[p + 2];
                sumSq += a0[p + 3];
                zeroDisagree += a1[p];
                materialDisagree += a1[p + 1];
                over += a1[p + 2];
                under += a1[p + 3];
                exactSum += a2[p];
                fieldSum += a2[p + 1];
                edgeCount += a2[p + 2];
                edgeSumAbs += a2[p + 3];
                maxAbs = Math.max(maxAbs, a3[p]);
                fallback += a3[p + 1];
                exactZeroFieldNot += a3[p + 2];
                fieldZeroExactNot += a3[p + 3];
            }
            if (count <= 0.0D) {
                lines.add(String.format(Locale.ROOT,
                        "T202_PRIMARY_AUDIT %s region=%s pixels=%d samples=0",
                        label, regionNames[r], pixels));
                continue;
            }
            lines.add(String.format(Locale.ROOT,
                    "T202_PRIMARY_AUDIT %s region=%s pixels=%d samples=%.0f"
                            + " meanErr=%.6f meanAbs=%.6f rms=%.6f maxAbs=%.6f"
                            + " overFraction=%.4f underFraction=%.4f"
                            + " zeroDisagreeFraction=%.4f exactZeroFieldNot=%.0f fieldZeroExactNot=%.0f"
                            + " materialDisagreeFraction=%.4f"
                            + " exactMean=%.6f fieldMean=%.6f relativeBias=%.4f"
                            + " edgeSamples=%.0f edgeMeanAbs=%.6f fallbacks=%.0f",
                    label, regionNames[r], pixels, count,
                    sumErr / count, sumAbs / count, Math.sqrt(sumSq / count), maxAbs,
                    over / count, under / count,
                    zeroDisagree / count, exactZeroFieldNot, fieldZeroExactNot,
                    materialDisagree / count,
                    exactSum / count, fieldSum / count,
                    exactSum > 0.0D ? (fieldSum - exactSum) / exactSum : Double.NaN,
                    edgeCount, edgeCount > 0.0D ? edgeSumAbs / edgeCount : Double.NaN,
                    fallback));
        }
        return lines;
    }
}
