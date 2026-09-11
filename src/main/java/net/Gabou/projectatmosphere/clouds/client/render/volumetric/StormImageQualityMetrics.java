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
    // -----------------------------------------------------------------------
    // T197 Task 5. Pocket diagnostics.
    //
    // shadowPocketRetention is one scalar and cannot say whether a
    // resolution recovered the pockets 256 lost or merely traded some for
    // others, nor whether the lost ones were smeared shut (their walls moved)
    // or filled in (their floor rose). These measure the pockets themselves:
    // every reference pocket pixel, clustered into regions, its luminance
    // error, the depth of its well, and the displacement of its walls along
    // the four axes at the reference's half-depth level. Recovery against the
    // 256 capture is computed on the same reference pockets, which every arm
    // of a pose shares because the anchor is captured once per pose.
    // -----------------------------------------------------------------------

    /** Chebyshev radius within which pocket pixels belong to one region. */
    private static final int POCKET_REGION_RADIUS = 2;
    /** How far a wall is searched for, in pixels. */
    private static final int POCKET_WALL_REACH = 8;
    /** Pixels within this distance of a pocket count as its neighbourhood. */
    private static final int POCKET_NEIGHBOURHOOD = 3;

    /**
     * One capture's pocket diagnostic. {@code refPocket}, {@code kept} and
     * {@code region} are per pixel; {@code region} is -1 off a pocket.
     */
    record PocketDiagnostics(
            int pocketPixels,
            int pocketRegions,
            int keptPixels,
            int lostPixels,
            int keptRegions,
            int lostRegions,
            double meanPocketLuminanceRef,
            double meanPocketLuminanceArm,
            double meanPocketAbsError,
            double meanPocketSignedError,
            double meanDepthRef,
            double meanDepthArm,
            double meanWallDisplacementPx,
            double meanSignedWallDisplacementPx,
            int wallsMeasured,
            int wallsFilled,
            int wallsBeyondReach,
            boolean[] refPocket,
            boolean[] kept,
            int[] region) {

        String format() {
            return "pocketPixels=" + pocketPixels
                    + " pocketRegions=" + pocketRegions
                    + " keptPixels=" + keptPixels
                    + " lostPixels=" + lostPixels
                    + " keptRegions=" + keptRegions
                    + " lostRegions=" + lostRegions
                    + " pixelRetention=" + f(pocketPixels == 0 ? Double.NaN
                            : (double) keptPixels / pocketPixels)
                    + " regionRetention=" + f(pocketRegions == 0 ? Double.NaN
                            : (double) keptRegions / pocketRegions)
                    + " pocketLumRef=" + f(meanPocketLuminanceRef)
                    + " pocketLumArm=" + f(meanPocketLuminanceArm)
                    + " pocketAbsError=" + f(meanPocketAbsError)
                    + " pocketSignedError=" + f(meanPocketSignedError)
                    + " pocketErrorOverDepth=" + f(meanDepthRef <= 0.0D ? Double.NaN
                            : meanPocketAbsError / meanDepthRef)
                    + " depthRef=" + f(meanDepthRef)
                    + " depthArm=" + f(meanDepthArm)
                    + " depthRatio=" + f(meanDepthRef <= 0.0D ? Double.NaN
                            : meanDepthArm / meanDepthRef)
                    + " wallDisplacementPx=" + f(meanWallDisplacementPx)
                    + " wallSignedDisplacementPx=" + f(meanSignedWallDisplacementPx)
                    + " wallsMeasured=" + wallsMeasured
                    + " wallsFilled=" + wallsFilled
                    + " wallsBeyondReach=" + wallsBeyondReach;
        }

        /**
         * This capture's pockets classed against another capture of the same
         * reference: recovered (lost there, kept here), newly lost (kept
         * there, lost here), and the agreements.
         */
        String formatAgainst(PocketDiagnostics baseline, String tag) {
            if (baseline.refPocket.length != refPocket.length) {
                return tag + "=incomparable";
            }
            int recovered = 0;
            int newlyLost = 0;
            int bothKept = 0;
            int bothLost = 0;
            for (int i = 0; i < refPocket.length; i++) {
                if (!refPocket[i]) {
                    continue;
                }
                if (kept[i] && !baseline.kept[i]) {
                    recovered++;
                } else if (!kept[i] && baseline.kept[i]) {
                    newlyLost++;
                } else if (kept[i]) {
                    bothKept++;
                } else {
                    bothLost++;
                }
            }
            return tag + "Recovered=" + recovered
                    + " " + tag + "NewlyLost=" + newlyLost
                    + " " + tag + "BothKept=" + bothKept
                    + " " + tag + "BothLost=" + bothLost
                    + " " + tag + "LostThere=" + baseline.lostPixels
                    + " " + tag + "RecoveredFraction=" + f(baseline.lostPixels == 0
                            ? Double.NaN : (double) recovered / baseline.lostPixels)
                    + " " + tag + "NetChange=" + (recovered - newlyLost);
        }
    }

    static PocketDiagnostics pocketDiagnostics(float[] a, float[] b, int width, int height) {
        int n = width * height;
        double[] la = new double[n];
        double[] lb = new double[n];
        boolean[] interiorA = new boolean[n];
        for (int i = 0; i < n; i++) {
            la[i] = luminance(a, i);
            lb[i] = luminance(b, i);
            interiorA[i] = alpha(a, i) >= INTERIOR_ALPHA;
        }
        boolean[] refPocket = new boolean[n];
        boolean[] kept = new boolean[n];
        int[] region = new int[n];
        Arrays.fill(region, -1);
        int pocketPixels = 0;
        int keptPixels = 0;
        double sumLumRef = 0.0D;
        double sumLumArm = 0.0D;
        double sumAbs = 0.0D;
        double sumSigned = 0.0D;
        long errorSamples = 0L;
        double sumDepthRef = 0.0D;
        double sumDepthArm = 0.0D;
        double sumWallAbs = 0.0D;
        double sumWallSigned = 0.0D;
        int wallsMeasured = 0;
        int wallsFilled = 0;
        int wallsBeyond = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
                int i = y * width + x;
                if (!interiorA[i] || !isLocalMinimum(la, width, x, y)) {
                    continue;
                }
                refPocket[i] = true;
                pocketPixels++;
                boolean survives = false;
                for (int dy = -1; dy <= 1 && !survives; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (isLocalMinimum(lb, width, x + dx, y + dy)) {
                            survives = true;
                            break;
                        }
                    }
                }
                kept[i] = survives;
                if (survives) {
                    keptPixels++;
                }
                sumLumRef += la[i];
                sumLumArm += lb[i];
                // Error over the pocket's 3x3, the region the metric judged.
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int j = i + dy * width + dx;
                        sumAbs += Math.abs(lb[j] - la[j]);
                        sumSigned += lb[j] - la[j];
                        errorSamples++;
                    }
                }
                // Well depth: the 5x5 ring's brightest against the floor; the
                // arm's floor is its 3x3 minimum so a one-pixel drift is not
                // charged as filling.
                double ringA = 0.0D;
                double ringB = 0.0D;
                double floorB = Double.MAX_VALUE;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int j = i + dy * width + dx;
                        if (Math.max(Math.abs(dx), Math.abs(dy)) == 2) {
                            ringA = Math.max(ringA, la[j]);
                            ringB = Math.max(ringB, lb[j]);
                        } else {
                            floorB = Math.min(floorB, lb[j]);
                        }
                    }
                }
                double depthRef = Math.max(0.0D, ringA - la[i]);
                sumDepthRef += depthRef;
                sumDepthArm += Math.max(0.0D, ringB - floorB);
                // Walls: along each axis, the distance to the reference's
                // half-depth level in the reference and in the arm. The arm
                // is searched at the same absolute level, so a wall that
                // moved outward (smeared) reads positive and one that closed
                // in reads negative; a centre already above the level is a
                // filled pocket, not a moved wall.
                if (depthRef > 1.0e-4D) {
                    double level = la[i] + 0.5D * depthRef;
                    for (int[] direction : directions) {
                        int refDist = 0;
                        int armDist = 0;
                        for (int k = 1; k <= POCKET_WALL_REACH; k++) {
                            int px = x + direction[0] * k;
                            int py = y + direction[1] * k;
                            if (px < 0 || py < 0 || px >= width || py >= height) {
                                break;
                            }
                            int j = py * width + px;
                            if (refDist == 0 && la[j] >= level) {
                                refDist = k;
                            }
                            if (armDist == 0 && lb[j] >= level) {
                                armDist = k;
                            }
                            if (refDist != 0 && armDist != 0) {
                                break;
                            }
                        }
                        if (refDist == 0) {
                            // The reference's wall is beyond reach along this
                            // axis: not a wall this diagnostic can measure.
                            continue;
                        }
                        if (lb[i] >= level) {
                            wallsFilled++;
                            continue;
                        }
                        if (armDist == 0) {
                            wallsBeyond++;
                            continue;
                        }
                        wallsMeasured++;
                        sumWallAbs += Math.abs(armDist - refDist);
                        sumWallSigned += armDist - refDist;
                    }
                }
            }
        }
        // Regions: pocket pixels within POCKET_REGION_RADIUS of one another,
        // by union-find over the pocket pixels alone.
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
                int i = y * width + x;
                if (!refPocket[i]) {
                    continue;
                }
                for (int dy = -POCKET_REGION_RADIUS; dy <= POCKET_REGION_RADIUS; dy++) {
                    for (int dx = -POCKET_REGION_RADIUS; dx <= POCKET_REGION_RADIUS; dx++) {
                        int px = x + dx;
                        int py = y + dy;
                        if (px < 0 || py < 0 || px >= width || py >= height) {
                            continue;
                        }
                        int j = py * width + px;
                        if (refPocket[j]) {
                            union(parent, i, j);
                        }
                    }
                }
            }
        }
        java.util.Map<Integer, Integer> regionIndex = new java.util.HashMap<>();
        boolean[] regionKept = new boolean[pocketPixels];
        int regions = 0;
        for (int i = 0; i < n; i++) {
            if (!refPocket[i]) {
                continue;
            }
            int root = find(parent, i);
            Integer index = regionIndex.get(root);
            if (index == null) {
                index = regions++;
                regionIndex.put(root, index);
            }
            region[i] = index;
            if (kept[i]) {
                regionKept[index] = true;
            }
        }
        int keptRegions = 0;
        for (int r = 0; r < regions; r++) {
            if (regionKept[r]) {
                keptRegions++;
            }
        }
        return new PocketDiagnostics(
                pocketPixels, regions, keptPixels, pocketPixels - keptPixels,
                keptRegions, regions - keptRegions,
                pocketPixels == 0 ? Double.NaN : sumLumRef / pocketPixels,
                pocketPixels == 0 ? Double.NaN : sumLumArm / pocketPixels,
                errorSamples == 0L ? Double.NaN : sumAbs / errorSamples,
                errorSamples == 0L ? Double.NaN : sumSigned / errorSamples,
                pocketPixels == 0 ? Double.NaN : sumDepthRef / pocketPixels,
                pocketPixels == 0 ? Double.NaN : sumDepthArm / pocketPixels,
                wallsMeasured == 0 ? Double.NaN : sumWallAbs / wallsMeasured,
                wallsMeasured == 0 ? Double.NaN : sumWallSigned / wallsMeasured,
                wallsMeasured, wallsFilled, wallsBeyond,
                refPocket, kept, region);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int i, int j) {
        int ri = find(parent, i);
        int rj = find(parent, j);
        if (ri != rj) {
            parent[Math.max(ri, rj)] = Math.min(ri, rj);
        }
    }

    /**
     * Two images beside the log. {@code _mask.png}: the reference luminance
     * in grey with every reference pocket marked - green kept, red lost, and
     * against a baseline cyan recovered and magenta newly lost.
     * {@code _diff.png}: the signed luminance change arm minus reference
     * within {@link #POCKET_NEIGHBOURHOOD} pixels of a pocket, red brighter
     * (a pocket filling in) and blue darker, scaled to the reference's mean
     * pocket depth, over the dimmed reference elsewhere. Captures read back
     * bottom-up, so rows are flipped for the file.
     */
    static void writePocketImages(float[] a, float[] b, int width, int height,
            PocketDiagnostics pockets, PocketDiagnostics baseline,
            java.nio.file.Path prefix) throws java.io.IOException {
        int n = width * height;
        double[] la = new double[n];
        double[] lb = new double[n];
        double range = 1.0e-6D;
        for (int i = 0; i < n; i++) {
            la[i] = luminance(a, i);
            lb[i] = luminance(b, i);
            range = Math.max(range, la[i]);
        }
        boolean[] near = new boolean[n];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!pockets.refPocket()[y * width + x]) {
                    continue;
                }
                for (int dy = -POCKET_NEIGHBOURHOOD; dy <= POCKET_NEIGHBOURHOOD; dy++) {
                    for (int dx = -POCKET_NEIGHBOURHOOD; dx <= POCKET_NEIGHBOURHOOD; dx++) {
                        int px = x + dx;
                        int py = y + dy;
                        if (px >= 0 && py >= 0 && px < width && py < height) {
                            near[py * width + px] = true;
                        }
                    }
                }
            }
        }
        double depthScale = Double.isNaN(pockets.meanDepthRef()) || pockets.meanDepthRef() <= 0.0D
                ? range * 0.1D : pockets.meanDepthRef();
        try (com.mojang.blaze3d.platform.NativeImage mask =
                     new com.mojang.blaze3d.platform.NativeImage(width, height, false);
             com.mojang.blaze3d.platform.NativeImage diff =
                     new com.mojang.blaze3d.platform.NativeImage(width, height, false)) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = y * width + x;
                    int fileY = height - 1 - y;
                    int grey = (int) Math.round(255.0D * Math.sqrt(Math.min(1.0D, la[i] / range)));
                    int r = grey;
                    int g = grey;
                    int bl = grey;
                    if (pockets.refPocket()[i]) {
                        boolean keptHere = pockets.kept()[i];
                        boolean baseKept = baseline != null && baseline.kept()[i];
                        if (baseline != null && keptHere && !baseKept) {
                            r = 0; g = 255; bl = 255;
                        } else if (baseline != null && !keptHere && baseKept) {
                            r = 255; g = 0; bl = 255;
                        } else if (keptHere) {
                            r = 0; g = 255; bl = 0;
                        } else {
                            r = 255; g = 0; bl = 0;
                        }
                    }
                    mask.setPixelRGBA(x, fileY, abgr(r, g, bl));
                    if (near[i]) {
                        double signed = (lb[i] - la[i]) / depthScale;
                        double t = Math.min(1.0D, Math.abs(signed));
                        int base = 40;
                        int hot = (int) Math.round(base + (255 - base) * t);
                        if (signed >= 0.0D) {
                            diff.setPixelRGBA(x, fileY, abgr(hot, base, base));
                        } else {
                            diff.setPixelRGBA(x, fileY, abgr(base, base, hot));
                        }
                    } else {
                        int dim = grey / 3;
                        diff.setPixelRGBA(x, fileY, abgr(dim, dim, dim));
                    }
                }
            }
            mask.writeToFile(java.nio.file.Path.of(prefix + "_mask.png").toFile());
            diff.writeToFile(java.nio.file.Path.of(prefix + "_diff.png").toFile());
        }
    }

    /** NativeImage packs ABGR. */
    private static int abgr(int r, int g, int b) {
        return (255 << 24) | ((b & 255) << 16) | ((g & 255) << 8) | (r & 255);
    }
    // -----------------------------------------------------------------------
    // T198 Task 1. Pixel classes for the light-tap audit, from the reference
    // frame alone, with the same definitions the metrics above use: edge is
    // cloud that is not interior, dark interior is the darkest quartile of the
    // interior, a pocket is the 3x3 around a reference local minimum. Pocket
    // wins over dark, dark over interior, interior over edge.
    // -----------------------------------------------------------------------

    static final int CLASS_NONE = 0;
    static final int CLASS_EDGE = 1;
    static final int CLASS_INTERIOR = 2;
    static final int CLASS_DARK_INTERIOR = 3;
    static final int CLASS_POCKET = 4;
    static final String[] CLASS_NAMES = {"none", "edge", "interior", "darkInterior", "pocket"};

    static int[] pixelClasses(float[] a, int width, int height) {
        int n = width * height;
        double[] la = new double[n];
        boolean[] cloud = new boolean[n];
        boolean[] interior = new boolean[n];
        int interiorCount = 0;
        for (int i = 0; i < n; i++) {
            la[i] = luminance(a, i);
            cloud[i] = alpha(a, i) > CLOUD_ALPHA;
            interior[i] = alpha(a, i) >= INTERIOR_ALPHA;
            if (interior[i]) {
                interiorCount++;
            }
        }
        double darkThreshold = Double.NEGATIVE_INFINITY;
        if (interiorCount >= 4) {
            double[] values = new double[interiorCount];
            int w = 0;
            for (int i = 0; i < n; i++) {
                if (interior[i]) {
                    values[w++] = la[i];
                }
            }
            Arrays.sort(values);
            darkThreshold = values[interiorCount / 4];
        }
        int[] classes = new int[n];
        for (int i = 0; i < n; i++) {
            if (interior[i]) {
                classes[i] = la[i] <= darkThreshold ? CLASS_DARK_INTERIOR : CLASS_INTERIOR;
            } else if (cloud[i]) {
                classes[i] = CLASS_EDGE;
            }
        }
        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
                int i = y * width + x;
                if (!interior[i] || !isLocalMinimum(la, width, x, y)) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        classes[i + dy * width + dx] = CLASS_POCKET;
                    }
                }
            }
        }
        return classes;
    }
}
