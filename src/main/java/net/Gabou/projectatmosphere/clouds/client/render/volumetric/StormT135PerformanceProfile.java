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
    private static final int SAMPLE_FRAMES = 120;
    /** Hard ceiling so a stalled cell cannot hang a run. */
    private static final int CELL_TIMEOUT_FRAMES = 600;

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
    private static final float[] cloudMilliseconds = new float[SAMPLE_FRAMES];
    private static final float[] frameMilliseconds = new float[SAMPLE_FRAMES];
    /**
     * Reconstruction cost. The depth-aware upsample runs at the full display
     * resolution whatever the cloud target's size, so it is the one term in the
     * pass that does not shrink with the internal resolution and has to be
     * measured separately instead of folded into the cloud time.
     */
    private static final float[] compositeMilliseconds = new float[SAMPLE_FRAMES];
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
            double cloudP50,
            double cloudP95,
            double cloudMean,
            double frameP50,
            double frameP95,
            double frameMean,
            double remainderP50,
            double compositeP50,
            double compositeP95,
            float effectiveResolutionScale
    ) {
    }

    /** Begins sampling one (pose, mode) cell. Returns false when already busy. */
    public static synchronized boolean begin(
            String pose, AtmoCommonConfig.CloudRaymarchQuality quality, String arm) {
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
        // The scale actually in force this frame, read from the renderer rather
        // than from the mode table: a diagnostic override turns the mode's
        // configured scale into a label instead of a measurement.
        effectiveResolutionScale = VolumetricCloudRenderer.lastResolutionScale();
        float frameMs = (now - previous) / 1_000_000.0F;
        float compositeMs = CloudFieldCompositeRenderer.lastGpuMilliseconds();
        if (sampled < sampleTarget) {
            cloudMilliseconds[sampled] = cloudMs;
            frameMilliseconds[sampled] = frameMs;
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
            Arrays.sort(cloud);
            Arrays.sort(frame);
            Arrays.sort(composite);
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
                    percentile(cloud, 0.50D),
                    percentile(cloud, 0.95D),
                    mean(cloud),
                    percentile(frame, 0.50D),
                    percentile(frame, 0.95D),
                    mean(frame),
                    percentile(frame, 0.50D) - percentile(cloud, 0.50D),
                    percentile(composite, 0.50D),
                    percentile(composite, 0.95D),
                    effectiveResolutionScale
            ));
            Cell recorded = results.get(results.size() - 1);
            ProjectAtmosphere.LOGGER.info(
                    "T135_PROFILE pose={} arm={} descriptors={} mode={} steps={}"
                            + " resolutionScale={} framebuffer={}x{}"
                            + " cloudTarget={}x{} samples={}"
                            + " cloudP50={} cloudP95={} cloudMean={}"
                            + " frameP50={} frameP95={} frameMean={} remainderP50={}"
                            + " effectiveResolutionScale={}"
                            + " compositeP50={} compositeP95={}",
                    recorded.pose(), recorded.arm(), recorded.descriptors(),
                    recorded.mode(), recorded.raymarchSteps(),
                    fmt(recorded.resolutionScale()), recorded.frameWidth(),
                    recorded.frameHeight(), recorded.cloudWidth(), recorded.cloudHeight(),
                    recorded.samples(),
                    fmt(recorded.cloudP50()), fmt(recorded.cloudP95()), fmt(recorded.cloudMean()),
                    fmt(recorded.frameP50()), fmt(recorded.frameP95()), fmt(recorded.frameMean()),
                    fmt(recorded.remainderP50()),
                    fmt(recorded.effectiveResolutionScale()),
                    fmt(recorded.compositeP50()), fmt(recorded.compositeP95()));
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
