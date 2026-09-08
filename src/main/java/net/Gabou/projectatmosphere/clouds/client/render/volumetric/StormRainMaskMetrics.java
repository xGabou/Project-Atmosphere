package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.Locale;

/**
 * T188 Task 0. Rain-specific quality, measured on a rain-only capture.
 *
 * <p>Every earlier campaign that touched rain had to report its quality with
 * cloud metrics, because rain and cloud are composited in the rendered frame
 * and no rain mask can be recovered from the result. T186 said so explicitly
 * and banked a candidate it could not gate. This closes that gap.
 *
 * <p>The input is not an RGB image. It is the rain-only capture the shader
 * writes under {@code PA_RAIN_MASK_OUTPUT}, one texel per ray:
 *
 * <ul>
 *   <li><b>R</b> - rain optical mass along the ray, that is the rain that
 *       actually survives the transmittance in front of it. Zero means "no
 *       rain here", which is what makes two captures comparable as sets.</li>
 *   <li><b>G</b> - onset height: the world Y where rain first appeared.</li>
 *   <li><b>B</b> - termination height: the world Y where it last appeared.</li>
 *   <li><b>A</b> - the number of contiguous rain runs along the ray. Two runs
 *       is a gap in the shaft.</li>
 * </ul>
 *
 * <p>The asymmetry between missed and false rain is the point. A field that
 * quantises space can both delete rain that should be there and invent rain
 * that should not, and those are different failures with different verdicts -
 * a mean absolute error over the mask reports one number for both and hides
 * which one happened.
 *
 * <p>Diagnostic-only. Nothing here runs on a production frame.
 */
final class StormRainMaskMetrics {
    /**
     * Optical mass at or above which a ray counts as carrying rain. Matches the
     * density threshold the march itself uses to call a step rain-active, so
     * the mask is the same population the shader counted.
     */
    private static final double PRESENT = 0.0008D;
    /** Upper bound of "thin": the faint rain a coarse field erases first. */
    private static final double THIN = 0.05D;

    private StormRainMaskMetrics() {
    }

    record Result(
            boolean evaluated,
            String reason,
            int comparedPixels,
            int referenceRainPixels,
            int candidateRainPixels,
            int intersectionPixels,
            int missedRainPixels,
            int falseRainPixels,
            double rainIou,
            double missedRainFraction,
            double falseRainFraction,
            double thinRainRetention,
            double meanAbsMassError,
            double maxAbsMassError,
            double meanOnsetHeightError,
            double maxOnsetHeightError,
            double meanTerminationHeightError,
            double maxTerminationHeightError,
            int referenceGapRays,
            int candidateGapRays,
            double shaftContinuity,
            int newGapRays
    ) {
        String format() {
            if (!evaluated) {
                return "t188RainMask evaluated=false reason=" + reason;
            }
            return String.format(Locale.ROOT,
                    "t188RainMask evaluated=true comparedPixels=%d"
                            + " referenceRainPixels=%d candidateRainPixels=%d"
                            + " intersectionPixels=%d missedRainPixels=%d"
                            + " falseRainPixels=%d rainIoU=%.6f"
                            + " missedRainFraction=%.6f falseRainFraction=%.6f"
                            + " thinRainRetention=%.6f meanAbsMassError=%.6e"
                            + " maxAbsMassError=%.6e meanOnsetHeightError=%.4f"
                            + " maxOnsetHeightError=%.4f"
                            + " meanTerminationHeightError=%.4f"
                            + " maxTerminationHeightError=%.4f"
                            + " referenceGapRays=%d candidateGapRays=%d"
                            + " shaftContinuity=%.6f newGapRays=%d",
                    comparedPixels, referenceRainPixels, candidateRainPixels,
                    intersectionPixels, missedRainPixels, falseRainPixels,
                    rainIou, missedRainFraction, falseRainFraction,
                    thinRainRetention, meanAbsMassError, maxAbsMassError,
                    meanOnsetHeightError, maxOnsetHeightError,
                    meanTerminationHeightError, maxTerminationHeightError,
                    referenceGapRays, candidateGapRays, shaftContinuity,
                    newGapRays);
        }
    }

    static Result failed(String reason) {
        return new Result(false, reason, 0, 0, 0, 0, 0, 0,
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                0, 0, 0.0D, 0);
    }

    static Result compare(
            StormReferenceImageComparison.Reference reference,
            StormReferenceImageComparison.Reference candidate) {
        if (reference == null || candidate == null) {
            return failed("missing_capture");
        }
        if (reference.width() != candidate.width()
                || reference.height() != candidate.height()) {
            return failed("size_mismatch");
        }
        float[] a = reference.pixels();
        float[] b = candidate.pixels();
        int pixels = reference.width() * reference.height();
        if (a == null || b == null || a.length != b.length
                || a.length != pixels * 4) {
            return failed("pixel_buffer_mismatch");
        }

        int referenceRain = 0;
        int candidateRain = 0;
        int intersection = 0;
        int missed = 0;
        int falsePositive = 0;
        int thinReference = 0;
        int thinRetained = 0;
        double massErrorSum = 0.0D;
        double massErrorMax = 0.0D;
        double onsetErrorSum = 0.0D;
        double onsetErrorMax = 0.0D;
        int onsetSamples = 0;
        double terminationErrorSum = 0.0D;
        double terminationErrorMax = 0.0D;
        int terminationSamples = 0;
        int referenceGaps = 0;
        int candidateGaps = 0;
        int newGaps = 0;
        int continuousBoth = 0;

        for (int pixel = 0; pixel < pixels; pixel++) {
            int offset = pixel * 4;
            double referenceMass = Math.max(0.0F, a[offset]);
            double candidateMass = Math.max(0.0F, b[offset]);
            boolean referenceHasRain = referenceMass >= PRESENT;
            boolean candidateHasRain = candidateMass >= PRESENT;

            if (referenceHasRain) {
                referenceRain++;
            }
            if (candidateHasRain) {
                candidateRain++;
            }
            if (referenceHasRain && candidateHasRain) {
                intersection++;
            } else if (referenceHasRain) {
                missed++;
            } else if (candidateHasRain) {
                falsePositive++;
            }

            // Thin rain is measured only where the reference had it. A field
            // that invents faint rain elsewhere shows up as false rain, not as
            // retention above 1.0.
            if (referenceHasRain && referenceMass <= THIN) {
                thinReference++;
                if (candidateHasRain) {
                    thinRetained++;
                }
            }

            double massError = Math.abs(referenceMass - candidateMass);
            massErrorSum += massError;
            massErrorMax = Math.max(massErrorMax, massError);

            // Heights are only comparable where both captures found rain; a
            // missed ray has no onset to be wrong about, and counting its zero
            // as a height error would report the mask failure twice.
            if (referenceHasRain && candidateHasRain) {
                double onsetError = Math.abs(a[offset + 1] - b[offset + 1]);
                onsetErrorSum += onsetError;
                onsetErrorMax = Math.max(onsetErrorMax, onsetError);
                onsetSamples++;

                double terminationError = Math.abs(a[offset + 2] - b[offset + 2]);
                terminationErrorSum += terminationError;
                terminationErrorMax = Math.max(terminationErrorMax, terminationError);
                terminationSamples++;

                double referenceRuns = a[offset + 3];
                double candidateRuns = b[offset + 3];
                if (referenceRuns > 1.5D) {
                    referenceGaps++;
                }
                if (candidateRuns > 1.5D) {
                    candidateGaps++;
                }
                // The failure that matters is a shaft the field broke, not one
                // the reference had already.
                if (candidateRuns > referenceRuns + 0.5D) {
                    newGaps++;
                } else {
                    continuousBoth++;
                }
            }
        }

        int union = referenceRain + candidateRain - intersection;
        return new Result(
                true,
                "ok",
                pixels,
                referenceRain,
                candidateRain,
                intersection,
                missed,
                falsePositive,
                union <= 0 ? 1.0D : (double) intersection / (double) union,
                referenceRain <= 0 ? 0.0D : (double) missed / (double) referenceRain,
                referenceRain <= 0 ? 0.0D
                        : (double) falsePositive / (double) referenceRain,
                thinReference <= 0 ? 1.0D
                        : (double) thinRetained / (double) thinReference,
                pixels <= 0 ? 0.0D : massErrorSum / (double) pixels,
                massErrorMax,
                onsetSamples <= 0 ? 0.0D : onsetErrorSum / (double) onsetSamples,
                onsetErrorMax,
                terminationSamples <= 0 ? 0.0D
                        : terminationErrorSum / (double) terminationSamples,
                terminationErrorMax,
                referenceGaps,
                candidateGaps,
                intersection <= 0 ? 1.0D
                        : (double) continuousBoth / (double) intersection,
                newGaps);
    }
}
