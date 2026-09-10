package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldCompositeRenderer;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * T135: the five-mode performance budget measurement.
 *
 * <p>The budget contract needs a cloud GPU cost and a total-frame cost per
 * quality mode, on one fixture, at one resolution, with the non-cloud remainder
 * separated rather than inferred. This samples exactly that: while a sweep is
 * armed the render hook feeds it one record per presented frame - the cloud
 * pass's own GPU timer result and the wall-clock frame interval - and the
 * driver walks (pose x quality mode), holding each combination long enough to
 * discard the settling frames and keep a percentile-worthy sample.
 *
 * <p>Two things this deliberately does not do. It does not derive the non-cloud
 * remainder from a model: it is total minus cloud, both measured on the same
 * frames. And it does not reuse a sample across modes: every mode is held and
 * sampled separately, because the resolution scale changes with the mode and
 * the cloud cost is not a simple function of the step count.
 *
 * <p>Inert unless armed. Ordinary frames pay one boolean test.
 */
public final class StormT135PerformanceProfile {

    /** Frames discarded after a mode or pose change before sampling begins. */
    private static final int SETTLE_FRAMES = 45;
    /** Frames kept per (pose, mode) cell. */
    private static final int SAMPLE_FRAMES = 240;
    /** Hard ceiling so a stalled cell cannot hang a run. */
    private static final int CELL_TIMEOUT_FRAMES = 1500;

    /**
     * Active per-cell budgets. The T136 five-mode sweep keeps the full 45/120
     * protocol. The T138 resolution sweep runs forty cells against one live
     * storm, and a cell at scale 1.00 on the NEAR_EDGE pose presents at under
     * one frame per second, so a full-length protocol would outlive the fixture
     * it is measuring. Shortening the sample is a fixture-validity decision,
     * not a statistical shortcut: a cell that decays mid-sample is still
     * discarded outright rather than reported.
     */
    private static int settleTarget = SETTLE_FRAMES;
    private static int sampleTarget = SAMPLE_FRAMES;

    /**
     * Sets the per-cell settle and sample budgets. Both are clamped into the
     * measurable range; the sample budget never exceeds the fixed sample arrays
     * and never falls below the 16-sample floor {@code finish} already requires
     * before a cell may be recorded.
     */
    /**
     * T171 control. Disabling the gate restores the pre-T171 behaviour so the
     * defect's contribution can be measured rather than asserted.
     */
    public static synchronized void setFreshSampleGating(boolean enabled) {
        freshSampleGating = enabled;
    }

    public static synchronized boolean freshSampleGating() {
        return freshSampleGating;
    }

    public static synchronized void setCellBudget(int settleFrames, int sampleFrames) {
        settleTarget = Math.max(0, Math.min(SETTLE_FRAMES, settleFrames));
        sampleTarget = Math.max(16, Math.min(SAMPLE_FRAMES, sampleFrames));
    }

    private static volatile boolean active;
    private static String poseName = "";
    private static AtmoCommonConfig.CloudRaymarchQuality mode;
    private static int settled;
    private static int sampled;
    private static int cellFrames;
    private static long previousFrameNanos;
    private static int cloudTargetWidth;
    private static int cloudTargetHeight;
    /**
     * Descriptor count required for this cell. A severe fixture that decays
     * mid-cell produces a bimodal sample that is not a cost measurement, so a
     * cell whose descriptor count ever drops below what it started with is
     * rejected rather than reported.
     */
    private static int requiredDescriptors;
    private static boolean contaminated;
    private static String armLabel = "production";
    /**
     * T171. The GPU timer resolves asynchronously, so {@code lastGpuMilliseconds()}
     * holds whatever timestamp pair most recently completed - which is frequently
     * the same value it held on the previous frame.
     *
     * <p>Until T171 this class recorded that value once per sampled frame without
     * checking whether it was new, so a cell's sample array mixed distinct GPU
     * measurements with re-recorded duplicates. The duplicate rate depends on the
     * phase between the frame loop and query resolution, which is exactly the kind
     * of thing that shifts between arms and between runs, and it is the leading
     * suspect for T170's 13% spread across arms that render identical images.
     *
     * <p>{@code VolumetricCloudRenderer.lastGpuTimingSample()} was written for this
     * and documented as "identifies a fresh completed GPU timestamp result without
     * using a frame-time proxy" - and was never called. It is called now.
     */
    private static boolean freshSampleGating = true;
    private static long lastRecordedTimingSerial = -1L;
    private static int duplicateSamplesRejected;
    private static final float[] cloudMilliseconds = new float[SAMPLE_FRAMES];
    private static final float[] frameMilliseconds = new float[SAMPLE_FRAMES];
    /**
     * Reconstruction cost. The depth-aware upsample runs at the full display
     * resolution whatever the cloud target's size, so it is the one term in the
     * pass that does not shrink with the internal resolution and has to be
     * measured separately instead of folded into the cloud time.
     */
    private static final float[] compositeMilliseconds = new float[SAMPLE_FRAMES];
    /** T188. The rain-support field build, sampled alongside the march. */
    private static final float[] rainFieldMilliseconds = new float[SAMPLE_FRAMES];
    private static float effectiveResolutionScale = Float.NaN;
    private static final List<Cell> results = new ArrayList<>();

    private StormT135PerformanceProfile() {
    }

    /** One completed (pose, mode) measurement. */
    public record Cell(
            String pose,
            String arm,
            int descriptors,
            String mode,
            int raymarchSteps,
            float resolutionScale,
            int frameWidth,
            int frameHeight,
            int cloudWidth,
            int cloudHeight,
            int samples,
            int duplicateSamplesRejected,
            double cloudP50,
            double cloudP95,
            double cloudMean,
            double cloudSd,
            double cloudCv,
            double cloudMin,
            double cloudMax,
            double frameP50,
            double frameP95,
            double frameMean,
            double remainderP50,
            double compositeP50,
            double compositeP95,
            float effectiveResolutionScale,
            // T188. The rain-support field's own GPU pass, timed separately
            // from the march. The architecture is only worth having if build
            // plus lookup beats the traversal it replaced, and a cloud-ray
            // number alone reports exactly one half of that trade.
            double rainFieldP50,
            double rainFieldP95
    ) {
        /**
         * T188. What the renderer actually spends on clouds: the march plus the
         * field pass that fed it. Zero for every program that builds no field,
         * so this is the march time unchanged wherever T188 is not in play.
         */
        public double cloudPlusFieldP50() {
            return cloudP50 + Math.max(0.0D, rainFieldP50);
        }
    }

    /** Begins sampling one (pose, mode) cell. Returns false when already busy. */
    public static synchronized boolean begin(
            String pose, AtmoCommonConfig.CloudRaymarchQuality quality, String arm) {
        lastRecordedTimingSerial = -1L;
        duplicateSamplesRejected = 0;
        if (active) {
            return false;
        }
        int descriptors = StormGeometryBuildCoordinator.lobeCount();
        // Clear weather legitimately has no descriptors, and demanding some
        // there would reject the one scenario that measures the non-cloud
        // remainder against no storm at all. Storm scenarios still require a
        // fixture, because a storm cell measured against an absent storm is
        // worse than a missing cell.
        boolean clearScenario = pose != null && pose.startsWith("CLEAR");
        if (!clearScenario && descriptors <= 0) {
            ProjectAtmosphere.LOGGER.warn(
                    "T136_PROFILE refusing to sample {}/{} with {} descriptors",
                    pose, quality, descriptors);
            return false;
        }
        requiredDescriptors = descriptors;
        contaminated = false;
        armLabel = arm;
        poseName = pose;
        mode = quality;
        settled = 0;
        sampled = 0;
        cellFrames = 0;
        previousFrameNanos = 0L;
        effectiveResolutionScale = Float.NaN;
        active = true;
        return true;
    }

    public static boolean active() {
        return active;
    }

    /** True once the current cell has collected its full sample. */
    public static synchronized boolean cellComplete() {
        return !active;
    }

    /** True when the cell just finished was rejected for fixture decay. */
    public static synchronized boolean lastCellContaminated() {
        return contaminated;
    }

    public static synchronized int requiredDescriptors() {
        return requiredDescriptors;
    }

    public static synchronized List<Cell> results() {
        return List.copyOf(results);
    }

    /** Removes a just-finished diagnostic cell that failed its post-capture validity gate. */
    public static synchronized void discardLastCell(String reason) {
        if (!results.isEmpty()) {
            Cell removed = results.remove(results.size() - 1);
            ProjectAtmosphere.LOGGER.warn(
                    "T135_PROFILE discarded {}/{} arm={} reason={}",
                    removed.pose(), removed.mode(), removed.arm(), reason);
        }
        contaminated = true;
    }

    /** Removes every completed arm for one pose after its shared fixture changes. */
    public static synchronized void discardPose(String pose, String reason) {
        int before = results.size();
        results.removeIf(cell -> java.util.Objects.equals(pose, cell.pose()));
        int removed = before - results.size();
        if (removed > 0) {
            ProjectAtmosphere.LOGGER.warn(
                    "T135_PROFILE discarded pose={} cells={} reason={}",
                    pose, removed, reason);
        }
    }

    public static synchronized void reset() {
        active = false;
        results.clear();
    }

    /**
     * One presented frame. Called from the render hook after the cloud pass, so
     * the GPU timer result and the frame interval describe the same frame.
     */
    public static synchronized void observeFrame(int frameWidth, int frameHeight) {
        if (!active) {
            previousFrameNanos = 0L;
            return;
        }
        com.mojang.blaze3d.pipeline.RenderTarget cloudTarget =
                VolumetricCloudRenderTargets.currentCloudTarget();
        if (cloudTarget != null) {
            cloudTargetWidth = cloudTarget.width;
            cloudTargetHeight = cloudTarget.height;
        }
        long now = System.nanoTime();
        long previous = previousFrameNanos;
        previousFrameNanos = now;
        cellFrames++;
        if (cellFrames > CELL_TIMEOUT_FRAMES) {
            ProjectAtmosphere.LOGGER.warn(
                    "T135_PROFILE cell {}/{} timed out after {} frames with {} samples",
                    poseName, mode, cellFrames, sampled);
            finish(frameWidth, frameHeight);
            return;
        }
        if (requiredDescriptors > 0
                && StormGeometryBuildCoordinator.lobeCount() < requiredDescriptors) {
            ProjectAtmosphere.LOGGER.warn(
                    "T136_PROFILE {}/{} contaminated: descriptors fell {} -> {} after {} samples",
                    poseName, mode, requiredDescriptors,
                    StormGeometryBuildCoordinator.lobeCount(), sampled);
            contaminated = true;
            sampled = 0;
            active = false;
            return;
        }
        if (previous == 0L) {
            return;
        }
        if (settled < settleTarget) {
            settled++;
            return;
        }
        float cloudMs = VolumetricCloudRenderer.lastGpuMilliseconds();
        if (!Float.isFinite(cloudMs) || cloudMs < 0.0F) {
            // The asynchronous timer has no result yet; do not fabricate one.
            return;
        }
        // A frame that produced no new timestamp pair carries the previous
        // frame's measurement. Recording it again would count one GPU interval
        // twice and shrink the apparent spread of whatever the cell is really
        // doing, so the frame is skipped rather than resampled.
        long timingSerial = VolumetricCloudRenderer.lastGpuTimingSample();
        if (freshSampleGating) {
            if (timingSerial == lastRecordedTimingSerial) {
                duplicateSamplesRejected++;
                return;
            }
            lastRecordedTimingSerial = timingSerial;
        }
        // The scale actually in force this frame, read from the renderer rather
        // than from the mode table: a diagnostic override turns the mode's
        // configured scale into a label instead of a measurement.
        effectiveResolutionScale = VolumetricCloudRenderer.lastResolutionScale();
        float frameMs = (now - previous) / 1_000_000.0F;
        float compositeMs = CloudFieldCompositeRenderer.lastGpuMilliseconds();
        float rainFieldMs = VolumetricCloudRenderer.lastRainFieldGpuMilliseconds();
        if (sampled < sampleTarget) {
            cloudMilliseconds[sampled] = cloudMs;
            frameMilliseconds[sampled] = frameMs;
            // A program that builds no field leaves the timer at -1. Recording
            // zero there keeps the combined figure equal to the march time
            // instead of poisoning it with a sentinel.
            rainFieldMilliseconds[sampled] = Math.max(0.0F, rainFieldMs);
            // A pending composite query records as zero rather than as a
            // fabricated cost. The percentile then understates it, which is the
            // safe direction for a term used to argue a cost floor.
            compositeMilliseconds[sampled] = Math.max(0.0F, compositeMs);
            sampled++;
        }
        if (sampled >= sampleTarget) {
            finish(frameWidth, frameHeight);
        }
    }

    private static void finish(int frameWidth, int frameHeight) {
        if (sampled >= 16) {
            float[] cloud = Arrays.copyOf(cloudMilliseconds, sampled);
            float[] frame = Arrays.copyOf(frameMilliseconds, sampled);
            float[] composite = Arrays.copyOf(compositeMilliseconds, sampled);
            float[] rainField = Arrays.copyOf(rainFieldMilliseconds, sampled);
            Arrays.sort(cloud);
            Arrays.sort(frame);
            Arrays.sort(composite);
            Arrays.sort(rainField);
            results.add(new Cell(
                    poseName,
                    armLabel,
                    requiredDescriptors,
                    mode == null ? "unknown" : mode.name(),
                    mode == null ? 0 : mode.getRaymarchSteps(),
                    mode == null ? 0.0F : mode.getResolutionScale(),
                    frameWidth,
                    frameHeight,
                    cloudTargetWidth,
                    cloudTargetHeight,
                    sampled,
                    duplicateSamplesRejected,
                    percentile(cloud, 0.50D),
                    percentile(cloud, 0.95D),
                    mean(cloud),
                    standardDeviation(cloud),
                    coefficientOfVariation(cloud),
                    cloud.length == 0 ? Double.NaN : cloud[0],
                    cloud.length == 0 ? Double.NaN : cloud[cloud.length - 1],
                    percentile(frame, 0.50D),
                    percentile(frame, 0.95D),
                    mean(frame),
                    percentile(frame, 0.50D) - percentile(cloud, 0.50D),
                    percentile(composite, 0.50D),
                    percentile(composite, 0.95D),
                    effectiveResolutionScale,
                    percentile(rainField, 0.50D),
                    percentile(rainField, 0.95D)
            ));
            Cell recorded = results.get(results.size() - 1);
            ProjectAtmosphere.LOGGER.info(
                    "T135_PROFILE pose={} arm={} descriptors={} mode={} steps={}"
                            + " resolutionScale={} framebuffer={}x{}"
                            + " cloudTarget={}x{} samples={} duplicatesRejected={}"
                            + " gating={}"
                            + " cloudP50={} cloudP95={} cloudMean={} cloudSd={}"
                            + " cloudCv={} cloudMin={} cloudMax={}"
                            + " frameP50={} frameP95={} frameMean={} remainderP50={}"
                            + " effectiveResolutionScale={}"
                            + " compositeP50={} compositeP95={}"
                            + " rainFieldP50={} rainFieldP95={}"
                            + " cloudPlusFieldP50={}",
                    recorded.pose(), recorded.arm(), recorded.descriptors(),
                    recorded.mode(), recorded.raymarchSteps(),
                    fmt(recorded.resolutionScale()), recorded.frameWidth(),
                    recorded.frameHeight(), recorded.cloudWidth(), recorded.cloudHeight(),
                    recorded.samples(), recorded.duplicateSamplesRejected(),
                    freshSampleGating,
                    fmt(recorded.cloudP50()), fmt(recorded.cloudP95()), fmt(recorded.cloudMean()),
                    fmt(recorded.cloudSd()), fmt(recorded.cloudCv()),
                    fmt(recorded.cloudMin()), fmt(recorded.cloudMax()),
                    fmt(recorded.frameP50()), fmt(recorded.frameP95()), fmt(recorded.frameMean()),
                    fmt(recorded.remainderP50()),
                    fmt(recorded.effectiveResolutionScale()),
                    fmt(recorded.compositeP50()), fmt(recorded.compositeP95()),
                    fmt(recorded.rainFieldP50()), fmt(recorded.rainFieldP95()),
                    fmt(recorded.cloudPlusFieldP50()));
        } else {
            ProjectAtmosphere.LOGGER.warn(
                    "T135_PROFILE pose={} mode={} produced only {} samples; discarded",
                    poseName, mode, sampled);
        }
        active = false;
    }

    private static double percentile(float[] sorted, double q) {
        if (sorted.length == 0) {
            return Double.NaN;
        }
        int index = (int) Math.round(q * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static double standardDeviation(float[] values) {
        if (values.length < 2) {
            return Double.NaN;
        }
        double average = mean(values);
        double sum = 0.0D;
        for (float v : values) {
            double d = v - average;
            sum += d * d;
        }
        // Sample standard deviation: these are a sample of the cell's frames,
        // not the whole population of frames the arm could ever render.
        return Math.sqrt(sum / (values.length - 1));
    }

    /** Dispersion as a fraction of the mean, which is what compares across arms. */
    private static double coefficientOfVariation(float[] values) {
        double average = mean(values);
        if (!(average > 0.0D)) {
            return Double.NaN;
        }
        return standardDeviation(values) / average;
    }

    private static double mean(float[] values) {
        double total = 0.0D;
        for (float v : values) {
            total += v;
        }
        return values.length == 0 ? Double.NaN : total / values.length;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
