package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.List;
import java.util.Locale;

/**
 * T132 robust reference from repeated adjacent samples.
 *
 * <p>The production raymarch has hard decision points - the transmittance early
 * termination and the smoothstep edges - so a ray landing arbitrarily close to
 * one can resolve either way between two otherwise identical frames. Measured
 * over 80 gated adjacent pairs that showed up in 4 pairs, usually as a single
 * pixel. Individual-frame bit identity is therefore not a valid neutrality
 * criterion for this renderer.
 *
 * <p>The replacement is a per-component median over an odd number of
 * consecutive samples taken inside one settled window. The median rejects a
 * lone outlying sample without altering the tolerance: the medians are still
 * compared at one binary16 storage ULP. The raw dispersion of each arm is
 * reported alongside, so an optimization result can never hide behind the
 * median.
 */
final class StormReferenceSampleSet {
    private StormReferenceSampleSet() {
    }

    /** Per-component median across the samples. Requires an odd, non-empty set. */
    static float[] median(List<StormReferenceImageComparison.Reference> samples) {
        int count = samples.size();
        float[] first = samples.get(0).pixels();
        float[] out = new float[first.length];
        float[] scratch = new float[count];
        for (int index = 0; index < out.length; index++) {
            for (int sample = 0; sample < count; sample++) {
                scratch[sample] = samples.get(sample).pixels()[index];
            }
            // Insertion sort: count is 5 or 7, so this beats any allocation.
            for (int i = 1; i < count; i++) {
                float value = scratch[i];
                int j = i - 1;
                while (j >= 0 && scratch[j] > value) {
                    scratch[j + 1] = scratch[j];
                    j--;
                }
                scratch[j + 1] = value;
            }
            out[index] = scratch[count / 2];
        }
        return out;
    }

    /** Wraps a median buffer so the existing strict comparator can consume it. */
    static StormReferenceImageComparison.Reference asReference(
            StormReferenceImageComparison.Reference template, float[] pixels) {
        return new StormReferenceImageComparison.Reference(
                template.view(), template.width(), template.height(), true,
                StormReferenceImageComparison.digest(pixels, template.width(), template.height()),
                pixels, template.effectiveWorldTime(), template.liveWorldTime(),
                template.worldTimePinned(), template.renderInputs(), template.cloudContent());
    }

    /**
     * Raw within-arm dispersion. Every pair of samples is compared, so a single
     * outlying frame cannot be averaged away silently.
     */
    static ArmNoise noise(
            List<StormReferenceImageComparison.Reference> samples, float[] medianPixels) {
        int count = samples.size();
        int length = medianPixels.length;
        double maxMagnitude = 0.0D;
        for (StormReferenceImageComparison.Reference sample : samples) {
            for (float value : sample.pixels()) {
                maxMagnitude = Math.max(maxMagnitude, Math.abs(value));
            }
        }
        double epsilon = StormReferenceImageComparison.halfPrecisionEpsilon(maxMagnitude);

        double maxAbs = 0.0D;
        double sumAbs = 0.0D;
        double sumSquares = 0.0D;
        long comparedComponents = 0L;
        int maxChangedPixels = 0;
        boolean[] varied = new boolean[length / 4];
        for (int a = 0; a < count; a++) {
            for (int b = a + 1; b < count; b++) {
                float[] left = samples.get(a).pixels();
                float[] right = samples.get(b).pixels();
                int changed = 0;
                for (int pixel = 0; pixel < varied.length; pixel++) {
                    boolean pixelChanged = false;
                    for (int channel = 0; channel < 4; channel++) {
                        int index = pixel * 4 + channel;
                        double delta = Math.abs(left[index] - right[index]);
                        maxAbs = Math.max(maxAbs, delta);
                        sumAbs += delta;
                        sumSquares += delta * delta;
                        comparedComponents++;
                        if (delta > epsilon) {
                            pixelChanged = true;
                        }
                    }
                    if (pixelChanged) {
                        changed++;
                        varied[pixel] = true;
                    }
                }
                maxChangedPixels = Math.max(maxChangedPixels, changed);
            }
        }

        int unionVarying = 0;
        for (boolean flag : varied) {
            if (flag) {
                unionVarying++;
            }
        }

        double maxRange = 0.0D;
        for (int index = 0; index < length; index++) {
            float low = Float.POSITIVE_INFINITY;
            float high = Float.NEGATIVE_INFINITY;
            for (StormReferenceImageComparison.Reference sample : samples) {
                float value = sample.pixels()[index];
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
            maxRange = Math.max(maxRange, high - low);
        }

        int differingFromMedian = 0;
        for (StormReferenceImageComparison.Reference sample : samples) {
            for (int index = 0; index < length; index++) {
                if (Math.abs(sample.pixels()[index] - medianPixels[index]) > epsilon) {
                    differingFromMedian++;
                    break;
                }
            }
        }

        return new ArmNoise(count, maxAbs,
                comparedComponents == 0 ? 0.0D : sumAbs / comparedComponents,
                comparedComponents == 0 ? 0.0D : Math.sqrt(sumSquares / comparedComponents),
                maxChangedPixels, unionVarying, maxRange, differingFromMedian,
                varied.length, epsilon);
    }

    record ArmNoise(
            int samples,
            double pairwiseMaxAbsRGBA,
            double pairwiseMeanAbsRGBA,
            double pairwiseRmsRGBA,
            int pairwiseMaxChangedPixels,
            int unionVaryingPixels,
            double maxPerPixelRange,
            int samplesDifferingFromMedian,
            int totalPixels,
            double epsilon
    ) {
        String format(String label) {
            return label + "={samples=" + samples
                    + " pairwiseMaxAbsRGBA=" + sci(pairwiseMaxAbsRGBA)
                    + " pairwiseMeanAbsRGBA=" + sci(pairwiseMeanAbsRGBA)
                    + " pairwiseRmsRGBA=" + sci(pairwiseRmsRGBA)
                    + " pairwiseMaxChangedPixels=" + pairwiseMaxChangedPixels
                    + " unionVaryingPixels=" + unionVaryingPixels
                    + " totalPixels=" + totalPixels
                    + " maxPerPixelRange=" + sci(maxPerPixelRange)
                    + " samplesDifferingFromMedian=" + samplesDifferingFromMedian
                    + " epsilon=" + sci(epsilon) + '}';
        }

        private static String sci(double value) {
            return String.format(Locale.ROOT, "%.6e", value);
        }
    }
}
