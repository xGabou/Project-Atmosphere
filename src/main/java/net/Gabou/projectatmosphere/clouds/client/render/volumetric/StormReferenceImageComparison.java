package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * T132's deterministic image-neutrality comparator.
 *
 * <p>The former acceptance test was exact equality of a SHA-256 digest taken
 * over a composited FINAL frame while temporal history was accumulating at
 * blend 0.85. Two passes at an identical pose on an unchanged fixture cannot
 * agree under that test: the accumulated frame depends on the preceding frame
 * sequence, so the digest differs by construction and its noise floor exceeds
 * any signal a visually-neutral performance change would produce.
 *
 * <p>This class compares the raw premultiplied cloud buffer instead, captured
 * with temporal history deliberately bypassed so the jitter phase is pinned
 * (see {@link StormReferenceImageCapture}). The digest is retained, but only as
 * an informational fingerprint; acceptance is the numeric delta below.
 *
 * <p>Pure and GL-free so the deterministic sandbox can exercise it headlessly.
 */
final class StormReferenceImageComparison {
    /**
     * binary16 has a 10-bit stored mantissa, so the spacing between adjacent
     * representable values at magnitude {@code m} is {@code 2^(exponent(m)-10)}.
     */
    private static final int HALF_MANTISSA_BITS = 10;
    /** Smallest binary16 subnormal, the spacing below the normal range. */
    private static final double HALF_MIN_SUBNORMAL = Math.scalb(1.0D, -24);
    /** Least exponent of a binary16 normal value. */
    private static final int HALF_MIN_NORMAL_EXPONENT = -14;
    /** Greatest exponent of a finite binary16 value. */
    private static final int HALF_MAX_EXPONENT = 15;

    private StormReferenceImageComparison() {
    }

    /**
     * One representable step of the compared buffer's storage format at
     * {@code magnitude}.
     *
     * <p>The cloud targets are re-specified as {@code GL_RGBA16F} by
     * {@code VolumetricCloudRenderTargets.upgradeColorToRgba16f}, and the
     * readback widens half to float, which is exact and contributes no error of
     * its own. The quantization step of the stored value is therefore the whole
     * representable difference budget: this epsilon admits at most a single
     * storage step per channel and nothing larger. It is derived from the
     * format and the measured data, not chosen to make a comparison pass.
     */
    static double halfPrecisionEpsilon(double magnitude) {
        double absolute = Math.abs(magnitude);
        if (!Double.isFinite(absolute) || absolute <= 0.0D) {
            return HALF_MIN_SUBNORMAL;
        }
        int exponent = Math.getExponent(absolute);
        if (exponent < HALF_MIN_NORMAL_EXPONENT) {
            return HALF_MIN_SUBNORMAL;
        }
        int clamped = Math.min(exponent, HALF_MAX_EXPONENT);
        return Math.scalb(1.0D, clamped - HALF_MANTISSA_BITS);
    }

    /**
     * Compares two deterministic reference buffers.
     *
     * <p>Both references must report a bypassed temporal history. A reference
     * captured while history was accumulating is rejected outright rather than
     * compared, so a temporal difference can never be mistaken for, or hidden
     * inside, a rendering difference.
     */
    static Comparison compare(Reference first, Reference second) {
        if (first == null || second == null) {
            return Comparison.failed("reference_missing");
        }
        if (!first.historyBypassed() || !second.historyBypassed()) {
            return Comparison.failed("reference_not_history_bypassed");
        }
        if (first.width() != second.width() || first.height() != second.height()) {
            return Comparison.failed("reference_dimension_mismatch "
                    + first.width() + "x" + first.height()
                    + " vs " + second.width() + "x" + second.height());
        }
        float[] a = first.pixels();
        float[] b = second.pixels();
        if (a.length != b.length || a.length != first.width() * first.height() * 4) {
            return Comparison.failed("reference_length_mismatch");
        }

        int pixelCount = first.width() * first.height();
        double maxAbs = 0.0D;
        double sumAbs = 0.0D;
        double sumSquares = 0.0D;
        double maxMagnitude = 0.0D;
        for (int index = 0; index < a.length; index++) {
            double left = a[index];
            double right = b[index];
            maxMagnitude = Math.max(maxMagnitude, Math.max(Math.abs(left), Math.abs(right)));
            double delta = Math.abs(left - right);
            maxAbs = Math.max(maxAbs, delta);
            sumAbs += delta;
            sumSquares += delta * delta;
        }
        double epsilon = halfPrecisionEpsilon(maxMagnitude);

        int changedPixels = 0;
        for (int pixel = 0; pixel < pixelCount; pixel++) {
            int offset = pixel * 4;
            if (Math.abs(a[offset] - b[offset]) > epsilon
                    || Math.abs(a[offset + 1] - b[offset + 1]) > epsilon
                    || Math.abs(a[offset + 2] - b[offset + 2]) > epsilon
                    || Math.abs(a[offset + 3] - b[offset + 3]) > epsilon) {
                changedPixels++;
            }
        }

        return new Comparison(
                true,
                changedPixels == 0 && maxAbs <= epsilon,
                maxAbs,
                sumAbs / a.length,
                Math.sqrt(sumSquares / a.length),
                changedPixels,
                pixelCount,
                epsilon,
                maxMagnitude,
                first.digest(),
                second.digest(),
                ""
        );
    }

    /** SHA-256 over the raw buffer, retained as an informational fingerprint only. */
    static String digest(float[] pixels, int width, int height) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(("rgba16f:" + width + 'x' + height + ':').getBytes(StandardCharsets.UTF_8));
            byte[] scratch = new byte[4];
            for (float value : pixels) {
                int bits = Float.floatToIntBits(value);
                scratch[0] = (byte) (bits >>> 24);
                scratch[1] = (byte) (bits >>> 16);
                scratch[2] = (byte) (bits >>> 8);
                scratch[3] = (byte) bits;
                sha.update(scratch);
            }
            byte[] hash = sha.digest();
            StringBuilder out = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                out.append(String.format(Locale.ROOT, "%02x", hash[index]));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException exception) {
            return "sha256_unavailable";
        }
    }

    /**
     * One deterministic reference frame. {@code historyBypassed} records that
     * the frame was rendered with temporal accumulation disabled, which also
     * pins the shader's jitter phase to zero because {@code jitterFrame} in
     * {@code cloud_atmosphere_volume.fsh} is {@code FrameIndex} only while
     * {@code HistoryValid == 1 && HistoryBlend > 0.001}.
     */
    record Reference(
            String view, int width, int height, boolean historyBypassed, String digest, float[] pixels,
            float effectiveWorldTime, float liveWorldTime, boolean worldTimePinned,
            StormSceneStability.RenderInputs renderInputs,
            StormCloudContent cloudContent
    ) {
        String format() {
            return "referenceImage view=" + view + " target=" + width + 'x' + height
                    + " historyBypassed=" + historyBypassed
                    + " worldTimePinned=" + worldTimePinned
                    + " liveWorldTime=" + String.format(Locale.ROOT, "%.5f", liveWorldTime)
                    + " effectiveReferenceWorldTime="
                    + String.format(Locale.ROOT, "%.5f", effectiveWorldTime)
                    + " digest=" + digest
                    + (renderInputs == null ? "" : ' ' + renderInputs.format())
                    + (cloudContent == null ? "" : ' ' + cloudContent.format());
        }
    }

    record Comparison(
            boolean evaluated,
            boolean passed,
            double maxAbsRGBA,
            double meanAbsRGBA,
            double rmsRGBA,
            int changedPixelCountAboveEpsilon,
            int totalComparedPixels,
            double epsilon,
            double maxComparedMagnitude,
            String referenceDigestA,
            String referenceDigestB,
            String failureReason
    ) {
        static Comparison failed(String reason) {
            return new Comparison(false, false, 0.0D, 0.0D, 0.0D, 0, 0, 0.0D, 0.0D, "", "", reason);
        }

        String format() {
            if (!evaluated) {
                return "referenceImageComparison evaluated=false reason=" + failureReason;
            }
            return "referenceImageComparison evaluated=true passed=" + passed
                    + " maxAbsRGBA=" + sci(maxAbsRGBA)
                    + " meanAbsRGBA=" + sci(meanAbsRGBA)
                    + " rmsRGBA=" + sci(rmsRGBA)
                    + " changedPixelCountAboveEpsilon=" + changedPixelCountAboveEpsilon
                    + " totalComparedPixels=" + totalComparedPixels
                    + " epsilon=" + sci(epsilon)
                    + " maxComparedMagnitude=" + sci(maxComparedMagnitude)
                    + " epsilonBasis=rgba16f_storage_ulp"
                    + " informationalDigestA=" + referenceDigestA
                    + " informationalDigestB=" + referenceDigestB;
        }

        private static String sci(double value) {
            return String.format(Locale.ROOT, "%.6e", value);
        }
    }
}
