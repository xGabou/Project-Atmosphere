package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.Locale;

/**
 * Image-quality metrics for a diagnostic arm that deliberately changes the
 * picture.
 *
 * <p>{@link StormReferenceImageComparison} answers "is this bit-identical",
 * which is the right question for T161 and T163 because those arms must change
 * nothing. The T166 traversal and LOD arms are different: they are supposed to
 * change the image, and the question is <em>how</em>. A max-error figure cannot
 * distinguish a uniformly slightly-dimmer cloud from one that has lost its thin
 * edges, and those two have completely different verdicts.
 *
 * <p>So this measures the things the decision actually turns on:
 *
 * <ul>
 *   <li><b>silhouette IoU</b> - does the cloud still occupy the same pixels;</li>
 *   <li><b>edge-band SSIM</b> - is the boundary still shaped the same, measured
 *       only in a band around the silhouette where softening shows up;</li>
 *   <li><b>cloud-region SSIM</b> - is the interior structure preserved;</li>
 *   <li><b>thin retention</b> - what fraction of faint material survives, which
 *       is what a coarser sample lattice erases first;</li>
 *   <li><b>hole retention</b> - what fraction of the openings between lobes
 *       stay open, which is what a coarser lattice fills in.</li>
 * </ul>
 *
 * <p>Diagnostic-only. Nothing here runs on a production frame.
 */
final class StormArmQualityMetrics {
    /** Alpha at or above which the compositor lets a pixel reach the frame. */
    private static final double PRESENT = 0.002D;
    /** Upper bound of "thin": faint material a coarse lattice drops first. */
    private static final double THIN = 0.25D;
    /** SSIM window edge, in pixels. */
    private static final int WINDOW = 8;
    /** SSIM stabilisers for data on a 0..1 scale. */
    private static final double C1 = 0.0001D;
    private static final double C2 = 0.0009D;

    private StormArmQualityMetrics() {
    }

    record Result(
            boolean evaluated,
            String reason,
            int comparedPixels,
            double silhouetteIou,
            double cloudRegionSsim,
            double edgeBandSsim,
            double thinRetention,
            double holeRetention,
            double alphaMassRatio
    ) {
        static Result failed(String reason) {
            return new Result(false, reason, 0, 0, 0, 0, 0, 0, 0);
        }

        String format() {
            if (!evaluated) {
                return "armQuality evaluated=false reason=" + reason;
            }
            return String.format(Locale.ROOT,
                    "armQuality evaluated=true comparedPixels=%d silhouetteIoU=%.4f"
                            + " cloudRegionSSIM=%.4f edgeBandSSIM=%.4f thinRetention=%.4f"
                            + " holeRetention=%.4f alphaMassRatio=%.4f",
                    comparedPixels, silhouetteIou, cloudRegionSsim, edgeBandSsim,
                    thinRetention, holeRetention, alphaMassRatio);
        }
    }

    static Result compare(
            StormReferenceImageComparison.Reference anchor,
            StormReferenceImageComparison.Reference arm) {
        if (anchor == null || arm == null) {
            return Result.failed("missing_capture");
        }
        float[] a = anchor.pixels();
        float[] b = arm.pixels();
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return Result.failed("pixel_buffer_mismatch");
        }
        int width = anchor.width();
        int height = anchor.height();
        if (width != arm.width() || height != arm.height()
                || a.length != width * height * 4) {
            return Result.failed("dimension_mismatch");
        }

        double[] alphaA = alphaPlane(a, width * height);
        double[] alphaB = alphaPlane(b, width * height);
        double[] lumaA = lumaPlane(a, width * height);
        double[] lumaB = lumaPlane(b, width * height);

        int intersection = 0;
        int union = 0;
        int thinTotal = 0;
        int thinKept = 0;
        int holeTotal = 0;
        int holeKept = 0;
        double massA = 0.0D;
        double massB = 0.0D;
        for (int i = 0; i < alphaA.length; i++) {
            boolean presentA = alphaA[i] >= PRESENT;
            boolean presentB = alphaB[i] >= PRESENT;
            if (presentA || presentB) {
                union++;
            }
            if (presentA && presentB) {
                intersection++;
            }
            if (presentA && alphaA[i] < THIN) {
                thinTotal++;
                if (presentB) {
                    thinKept++;
                }
            }
            massA += alphaA[i];
            massB += alphaB[i];
        }

        // A hole is a transparent pixel enclosed by material, not the open sky
        // outside the storm. Requiring present neighbours on both sides in both
        // axes is a cheap enclosure test that the sky cannot satisfy.
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int i = y * width + x;
                if (alphaA[i] >= PRESENT) {
                    continue;
                }
                boolean enclosed = alphaA[i - 1] >= PRESENT && alphaA[i + 1] >= PRESENT
                        && alphaA[i - width] >= PRESENT && alphaA[i + width] >= PRESENT;
                if (!enclosed) {
                    continue;
                }
                holeTotal++;
                if (alphaB[i] < PRESENT) {
                    holeKept++;
                }
            }
        }

        boolean[] cloudRegion = new boolean[alphaA.length];
        boolean[] edgeBand = new boolean[alphaA.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                cloudRegion[i] = alphaA[i] >= PRESENT || alphaB[i] >= PRESENT;
                edgeBand[i] = isEdge(alphaA, width, height, x, y)
                        || isEdge(alphaB, width, height, x, y);
            }
        }

        return new Result(
                true,
                "ok",
                union,
                union == 0 ? 1.0D : (double) intersection / union,
                windowedSsim(lumaA, lumaB, width, height, cloudRegion),
                windowedSsim(lumaA, lumaB, width, height, edgeBand),
                thinTotal == 0 ? 1.0D : (double) thinKept / thinTotal,
                holeTotal == 0 ? 1.0D : (double) holeKept / holeTotal,
                massA <= 0.0D ? 1.0D : massB / massA);
    }

    /** True when this present pixel touches an absent one, in four-connectivity. */
    private static boolean isEdge(double[] alpha, int width, int height, int x, int y) {
        int i = y * width + x;
        if (alpha[i] < PRESENT) {
            return false;
        }
        return (x > 0 && alpha[i - 1] < PRESENT)
                || (x < width - 1 && alpha[i + 1] < PRESENT)
                || (y > 0 && alpha[i - width] < PRESENT)
                || (y < height - 1 && alpha[i + width] < PRESENT);
    }

    /**
     * Mean SSIM over 8x8 windows, restricted to windows that overlap the mask.
     *
     * <p>Restricting to the mask is the point: a global SSIM over a frame that
     * is mostly empty sky is dominated by pixels both images agree are empty,
     * and reports ~1.0 for an arm that destroyed the cloud.
     */
    private static double windowedSsim(
            double[] first, double[] second, int width, int height, boolean[] mask) {
        double total = 0.0D;
        int windows = 0;
        for (int originY = 0; originY + WINDOW <= height; originY += WINDOW / 2) {
            for (int originX = 0; originX + WINDOW <= width; originX += WINDOW / 2) {
                int count = 0;
                double sumA = 0.0D;
                double sumB = 0.0D;
                boolean relevant = false;
                for (int y = originY; y < originY + WINDOW; y++) {
                    for (int x = originX; x < originX + WINDOW; x++) {
                        int i = y * width + x;
                        relevant |= mask[i];
                        sumA += first[i];
                        sumB += second[i];
                        count++;
                    }
                }
                if (!relevant || count == 0) {
                    continue;
                }
                double meanA = sumA / count;
                double meanB = sumB / count;
                double varA = 0.0D;
                double varB = 0.0D;
                double covariance = 0.0D;
                for (int y = originY; y < originY + WINDOW; y++) {
                    for (int x = originX; x < originX + WINDOW; x++) {
                        int i = y * width + x;
                        double da = first[i] - meanA;
                        double db = second[i] - meanB;
                        varA += da * da;
                        varB += db * db;
                        covariance += da * db;
                    }
                }
                double denominator = Math.max(1, count - 1);
                varA /= denominator;
                varB /= denominator;
                covariance /= denominator;
                double ssim = ((2.0D * meanA * meanB + C1) * (2.0D * covariance + C2))
                        / ((meanA * meanA + meanB * meanB + C1) * (varA + varB + C2));
                total += ssim;
                windows++;
            }
        }
        return windows == 0 ? 1.0D : total / windows;
    }

    private static double[] alphaPlane(float[] pixels, int count) {
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            out[i] = clamp01(pixels[i * 4 + 3]);
        }
        return out;
    }

    /**
     * Premultiplied luminance. The arms change coverage as well as colour, so
     * comparing unweighted colour would score a pixel that lost its cloud
     * entirely against whatever colour happened to remain in the buffer.
     */
    private static double[] lumaPlane(float[] pixels, int count) {
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            double alpha = clamp01(pixels[i * 4 + 3]);
            double luma = 0.2126D * clamp01(pixels[i * 4])
                    + 0.7152D * clamp01(pixels[i * 4 + 1])
                    + 0.0722D * clamp01(pixels[i * 4 + 2]);
            out[i] = luma * alpha;
        }
        return out;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }
}
