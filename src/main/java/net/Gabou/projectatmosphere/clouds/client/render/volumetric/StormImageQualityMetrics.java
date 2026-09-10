package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.Arrays;
import java.util.Locale;

/**
 * T178 Task 0. Perceptual comparison metrics for two RGBA reference frames.
 *
 * <p>T176 and T177 both measured light-tap reduction with changed-pixel counts,
 * max absolute error and mean absolute error, and both reports had to conclude
 * that those numbers cannot decide the question actually at issue. The failure
 * mode of dropping a light tap is <em>flattened self-shadowing</em>: interior
 * contrast falls while the silhouette, the mean error and the changed-pixel
 * count barely move. A metric that averages over the frame is close to blind to
 * it.
 *
 * <p>Every metric here is computed from the two captured frames alone. The
 * definitions are written out in each method because a perceptual metric whose
 * definition is not stated is not reproducible, and because several of these
 * are proxies rather than standard measures - those are labelled
 * {@code proxy} in the emitted line rather than presented as established
 * quantities.
 *
 * <p>Deliberately not implemented: puff separation as a segmentation quantity.
 * Counting distinct puffs needs a segmentation of the cloud into lobes, which
 * this comparison has no basis to perform - the frames carry no per-lobe
 * identity. {@link #valleyDepthProxy} measures the luminance valleys
 * <em>between</em> bright regions instead, which is the observable that
 * separation reads from, and it is reported under its own name.
 */
final class StormImageQualityMetrics {

    /** Alpha above which a pixel counts as cloud. */
    private static final double CLOUD_ALPHA = 0.02D;
    /** Alpha at or above which a pixel counts as cloud interior, not edge. */
    private static final double INTERIOR_ALPHA = 0.60D;
    /** Upper alpha bound for "thin" material - wisps, edges of tenuous puffs. */
    private static final double THIN_ALPHA_MAX = 0.25D;
    /** SSIM window side and stride. */
    private static final int WINDOW = 8;
    private static final int STRIDE = 4;

    private StormImageQualityMetrics() {
    }

    /**
     * Rec.709 luminance. Reference frames are linear HDR, so this is a
     * radiometric weighting, not a gamma-encoded one.
     */
    private static double luminance(float[] px, int pixel) {
        int o = pixel * 4;
        return 0.2126D * px[o] + 0.7152D * px[o + 1] + 0.0722D * px[o + 2];
    }

    private static double alpha(float[] px, int pixel) {
        return px[pixel * 4 + 3];
    }

    /**
     * Computes every T178 metric and returns the formatted result line.
     *
     * @param a the reference frame (production, four light taps)
     * @param b the arm under test
     */
    static String evaluate(float[] a, float[] b, int width, int height) {
        int n = width * height;
        double[] la = new double[n];
        double[] lb = new double[n];
        boolean[] cloudA = new boolean[n];
        boolean[] cloudB = new boolean[n];
        boolean[] interiorA = new boolean[n];
        for (int i = 0; i < n; i++) {
            la[i] = luminance(a, i);
            lb[i] = luminance(b, i);
            cloudA[i] = alpha(a, i) > CLOUD_ALPHA;
            cloudB[i] = alpha(b, i) > CLOUD_ALPHA;
            interiorA[i] = alpha(a, i) >= INTERIOR_ALPHA;
        }

        double range = 0.0D;
        for (int i = 0; i < n; i++) {
            range = Math.max(range, Math.max(la[i], lb[i]));
        }
        if (range <= 0.0D) {
            return "t178Quality evaluated=false reason=black_frames";
        }

        boolean[] edgeBand = edgeBand(cloudA, width, height);

        double cloudSsim = ssim(la, lb, cloudA, width, height, range);
        double edgeSsim = ssim(la, lb, edgeBand, width, height, range);
        double iou = silhouetteIou(cloudA, cloudB);
        double thin = thinRetention(a, b, n);
        double hole = holeRetention(cloudA, cloudB, width, height);
        double[] shadow = interiorContrast(la, lb, interiorA, n);
        double darkKeep = darkInteriorRetention(la, lb, interiorA, n);
        double pockets = shadowPocketRetention(la, lb, interiorA, width, height);
        double[] valley = valleyDepthProxy(la, lb, interiorA, width, height);

        return "t178Quality evaluated=true"
                + " cloudSsim=" + f(cloudSsim)
                + " edgeSsim=" + f(edgeSsim)
                + " silhouetteIoU=" + f(iou)
                + " thinRetention=" + f(thin)
                + " holeRetention=" + f(hole)
                + " selfShadowContrastRef=" + f(shadow[0])
                + " selfShadowContrastArm=" + f(shadow[1])
                + " selfShadowContrastRatio=" + f(shadow[2])
                + " darkInteriorRetention=" + f(darkKeep)
                + " shadowPocketRetention=" + f(pockets)
                + " valleyDepthRefProxy=" + f(valley[0])
                + " valleyDepthArmProxy=" + f(valley[1])
                + " valleyDepthRatioProxy=" + f(valley[2])
                + " interiorPixels=" + (int) shadow[3]
                + " puffSeparation=not_implemented_needs_segmentation";
    }

    private static String f(double v) {
        return Double.isNaN(v) ? "n/a" : String.format(Locale.ROOT, "%.6f", v);
    }

    /**
     * Structural similarity on luminance, restricted to windows that overlap
     * the supplied mask by more than half. Standard Wang et al. formulation
     * with C1 = (0.01 L)^2 and C2 = (0.03 L)^2, L taken as the observed dynamic
     * range across both frames rather than an assumed 1.0, because these frames
     * are linear HDR and can exceed unity.
     */
    private static double ssim(double[] a, double[] b, boolean[] mask,
            int width, int height, double range) {
        double c1 = Math.pow(0.01D * range, 2.0D);
        double c2 = Math.pow(0.03D * range, 2.0D);
        double total = 0.0D;
        int windows = 0;
        int half = WINDOW * WINDOW / 2;
        for (int y = 0; y + WINDOW <= height; y += STRIDE) {
            for (int x = 0; x + WINDOW <= width; x += STRIDE) {
                int inMask = 0;
                double sa = 0.0D;
                double sb = 0.0D;
                for (int wy = 0; wy < WINDOW; wy++) {
                    int row = (y + wy) * width + x;
                    for (int wx = 0; wx < WINDOW; wx++) {
                        int i = row + wx;
                        if (mask[i]) {
                            inMask++;
                        }
                        sa += a[i];
                        sb += b[i];
                    }
                }
                if (inMask <= half) {
                    continue;
                }
                int count = WINDOW * WINDOW;
                double ma = sa / count;
                double mb = sb / count;
                double va = 0.0D;
                double vb = 0.0D;
                double cov = 0.0D;
                for (int wy = 0; wy < WINDOW; wy++) {
                    int row = (y + wy) * width + x;
                    for (int wx = 0; wx < WINDOW; wx++) {
                        int i = row + wx;
                        double da = a[i] - ma;
                        double db = b[i] - mb;
                        va += da * da;
                        vb += db * db;
                        cov += da * db;
                    }
                }
                va /= count - 1;
                vb /= count - 1;
                cov /= count - 1;
                total += ((2 * ma * mb + c1) * (2 * cov + c2))
                        / ((ma * ma + mb * mb + c1) * (va + vb + c2));
                windows++;
            }
        }
        return windows == 0 ? Double.NaN : total / windows;
    }

    /**
     * Pixels within two of a silhouette boundary in the reference, where a
     * boundary pixel is one whose four-neighbourhood is not uniformly cloud or
     * uniformly sky.
     */
    private static boolean[] edgeBand(boolean[] cloud, int width, int height) {
        boolean[] boundary = new boolean[cloud.length];
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int i = y * width + x;
                boolean c = cloud[i];
                if (c != cloud[i - 1] || c != cloud[i + 1]
                        || c != cloud[i - width] || c != cloud[i + width]) {
                    boundary[i] = true;
                }
            }
        }
        boolean[] band = new boolean[cloud.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (!boundary[i]) {
                    continue;
                }
                for (int dy = -2; dy <= 2; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= height) {
                        continue;
                    }
                    for (int dx = -2; dx <= 2; dx++) {
                        int nx = x + dx;
                        if (nx >= 0 && nx < width) {
                            band[ny * width + nx] = true;
                        }
                    }
                }
            }
        }
        return band;
    }

    /** Intersection over union of the two cloud masks. */
    private static double silhouetteIou(boolean[] a, boolean[] b) {
        long inter = 0;
        long union = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] && b[i]) {
                inter++;
            }
            if (a[i] || b[i]) {
                union++;
            }
        }
        return union == 0 ? Double.NaN : (double) inter / union;
    }

    /**
     * Fraction of the reference's thin material - present but tenuous, the
     * first thing a coarser march loses - that is still present in the arm.
     */
    private static double thinRetention(float[] a, float[] b, int n) {
        long thin = 0;
        long kept = 0;
        for (int i = 0; i < n; i++) {
            double av = alpha(a, i);
            if (av > CLOUD_ALPHA && av <= THIN_ALPHA_MAX) {
                thin++;
                if (alpha(b, i) > CLOUD_ALPHA) {
                    kept++;
                }
            }
        }
        return thin == 0 ? Double.NaN : (double) kept / thin;
    }

    /**
     * Fraction of the reference's interior holes that survive. A hole is a
     * non-cloud pixel with cloud on both sides horizontally and vertically, so
     * gaps punched through the body count and the sky outside the silhouette
     * does not.
     */
    private static double holeRetention(boolean[] a, boolean[] b, int width, int height) {
        long holes = 0;
        long kept = 0;
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int i = y * width + x;
                if (a[i]) {
                    continue;
                }
                boolean enclosed = scan(a, width, height, x, y, -1, 0)
                        && scan(a, width, height, x, y, 1, 0)
                        && scan(a, width, height, x, y, 0, -1)
                        && scan(a, width, height, x, y, 0, 1);
                if (!enclosed) {
                    continue;
                }
                holes++;
                if (!b[i]) {
                    kept++;
                }
            }
        }
        return holes == 0 ? Double.NaN : (double) kept / holes;
    }

    private static boolean scan(boolean[] mask, int width, int height,
            int x, int y, int dx, int dy) {
        int cx = x + dx;
        int cy = y + dy;
        while (cx >= 0 && cx < width && cy >= 0 && cy < height) {
            if (mask[cy * width + cx]) {
                return true;
            }
            cx += dx;
            cy += dy;
        }
        return false;
    }

    /**
     * Standard deviation of interior luminance, which is what self-shadowing
     * produces: a lit side, a shaded side and the gradient between them. A
     * flattened march keeps the mean and loses the spread, so the ratio is the
     * direct measure of the failure mode tap reduction has.
     *
     * @return reference sigma, arm sigma, arm/reference ratio, interior count
     */
    private static double[] interiorContrast(double[] a, double[] b,
            boolean[] interior, int n) {
        double sa = 0.0D;
        double sb = 0.0D;
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (interior[i]) {
                sa += a[i];
                sb += b[i];
                count++;
            }
        }
        if (count < 2) {
            return new double[] {Double.NaN, Double.NaN, Double.NaN, count};
        }
        double ma = sa / count;
        double mb = sb / count;
        double va = 0.0D;
        double vb = 0.0D;
        for (int i = 0; i < n; i++) {
            if (interior[i]) {
                va += (a[i] - ma) * (a[i] - ma);
                vb += (b[i] - mb) * (b[i] - mb);
            }
        }
        double sigmaA = Math.sqrt(va / (count - 1));
        double sigmaB = Math.sqrt(vb / (count - 1));
        return new double[] {sigmaA, sigmaB,
                sigmaA <= 0.0D ? Double.NaN : sigmaB / sigmaA, count};
    }

    /**
     * Of the darkest quarter of the reference's interior, the fraction still at
     * or below that same absolute luminance in the arm. Losing shadow shows up
     * here as pixels rising above the threshold.
     */
    private static double darkInteriorRetention(double[] a, double[] b,
            boolean[] interior, int n) {
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (interior[i]) {
                count++;
            }
        }
        if (count < 4) {
            return Double.NaN;
        }
        double[] values = new double[count];
        int w = 0;
        for (int i = 0; i < n; i++) {
            if (interior[i]) {
                values[w++] = a[i];
            }
        }
        Arrays.sort(values);
        double threshold = values[count / 4];
        long dark = 0;
        long kept = 0;
        for (int i = 0; i < n; i++) {
            if (interior[i] && a[i] <= threshold) {
                dark++;
                if (b[i] <= threshold) {
                    kept++;
                }
            }
        }
        return dark == 0 ? Double.NaN : (double) kept / dark;
    }

    /**
     * Fraction of the reference's localized shadow pockets that survive. A
     * pocket is an interior pixel strictly darker than all eight neighbours by
     * a margin; it survives if some pixel within one of the same position is
     * still a local minimum in the arm. These are the small dark cores between
     * puffs, which average error cannot see at all.
     */
    private static double shadowPocketRetention(double[] a, double[] b,
            boolean[] interior, int width, int height) {
        long pockets = 0;
        long kept = 0;
        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
                int i = y * width + x;
                if (!interior[i] || !isLocalMinimum(a, width, x, y)) {
                    continue;
                }
                pockets++;
                boolean survives = false;
                for (int dy = -1; dy <= 1 && !survives; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (isLocalMinimum(b, width, x + dx, y + dy)) {
                            survives = true;
                            break;
                        }
                    }
                }
                if (survives) {
                    kept++;
                }
            }
        }
        return pockets == 0 ? Double.NaN : (double) kept / pockets;
    }

    private static boolean isLocalMinimum(double[] l, int width, int x, int y) {
        int i = y * width + x;
        double v = l[i] + 1.0e-4D;
        return v < l[i - 1] && v < l[i + 1]
                && v < l[i - width] && v < l[i + width]
                && v < l[i - width - 1] && v < l[i - width + 1]
                && v < l[i + width - 1] && v < l[i + width + 1];
    }

    /**
     * Proxy for puff separation: the mean depth of luminance valleys inside the
     * cloud body, measured as the horizontal maximum within six pixels either
     * side of an interior local minimum, minus that minimum.
     *
     * <p>This is explicitly a proxy. Puff separation as such needs a
     * segmentation of the body into puffs, which these frames do not carry.
     * What it does measure is the observable separation reads from - if
     * neighbouring puffs stop being divided by a dark lane, this falls.
     *
     * @return reference depth, arm depth, arm/reference ratio
     */
    private static double[] valleyDepthProxy(double[] a, double[] b,
            boolean[] interior, int width, int height) {
        double sumA = 0.0D;
        double sumB = 0.0D;
        int count = 0;
        for (int y = 2; y < height - 2; y++) {
            for (int x = 6; x < width - 6; x++) {
                int i = y * width + x;
                if (!interior[i] || !isLocalMinimum(a, width, x, y)) {
                    continue;
                }
                double peakA = 0.0D;
                double peakB = 0.0D;
                for (int dx = -6; dx <= 6; dx++) {
                    peakA = Math.max(peakA, a[i + dx]);
                    peakB = Math.max(peakB, b[i + dx]);
                }
                sumA += peakA - a[i];
                sumB += peakB - b[i];
                count++;
            }
        }
        if (count == 0) {
            return new double[] {Double.NaN, Double.NaN, Double.NaN};
        }
        double da = sumA / count;
        double db = sumB / count;
        return new double[] {da, db, da <= 0.0D ? Double.NaN : db / da};
    }
}
