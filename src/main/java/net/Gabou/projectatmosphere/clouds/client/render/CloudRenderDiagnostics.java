package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stores lightweight facts about the live cloud render path.
 * This class does not draw or read backend state.
 */
public final class CloudRenderDiagnostics {
    private static final int LOG_INTERVAL_TICKS = 20;

    private static volatile FrameStats lastStats = FrameStats.empty();
    private static long lastLoggedWorldTime = Long.MIN_VALUE;
    private static long lastDepthProbeWorldTime = Long.MIN_VALUE;
    private static long lastStateSnapshotWorldTime = Long.MIN_VALUE;
    private static String lastStateSnapshotSignature = "";

    private static boolean frameOpen;
    private static long worldTime;
    private static String qualityName = "UNKNOWN";
    private static int raymarchSteps;
    private static float resolutionScale;
    private static int mainWidth;
    private static int mainHeight;
    private static int targetWidth;
    private static int targetHeight;
    private static boolean downscaled;
    private static int sourceSnapshots;
    private static int renderableSnapshots;
    private static int renderedSnapshots;
    private static int submitSkippedSnapshots;
    private static boolean compositeSubmitted;
    private static String lastCloudTypeId = "";
    private static String lastCloudShapeId = "";
    private static int lastCloudSeed;
    private static float lastCloudRadius;
    private static long frameCpuStartNs;
    private static long raymarchCpuNs;
    private static long compositeCpuNs;
    private static float raymarchGpuMs;
    private static float compositeGpuMs;
    private static boolean gpuTimingSupported;
    private static boolean raymarchGpuTimingValid;
    private static boolean compositeGpuTimingValid;
    private static int raymarchGpuAgeFrames;
    private static int compositeGpuAgeFrames;
    private static int raymarchGpuPendingQueries;
    private static int compositeGpuPendingQueries;

    private CloudRenderDiagnostics() {
    }

    public static void beginFrame(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull RenderTarget mainTarget,
            @NotNull RenderTarget cloudTarget,
            int sourceSnapshotCount,
            int renderableSnapshotCount,
            boolean usesDownscaledTarget
    ) {
        frameOpen = true;
        worldTime = frameContext.getWorldTime();
        qualityName = resolveQualityName();
        raymarchSteps = frameContext.getRenderProfile().getRaymarchSteps();
        resolutionScale = frameContext.getRenderProfile().getResolutionScale();
        mainWidth = mainTarget.width;
        mainHeight = mainTarget.height;
        targetWidth = cloudTarget.width;
        targetHeight = cloudTarget.height;
        downscaled = usesDownscaledTarget;
        sourceSnapshots = Math.max(0, sourceSnapshotCount);
        renderableSnapshots = Math.max(0, renderableSnapshotCount);
        renderedSnapshots = 0;
        submitSkippedSnapshots = 0;
        compositeSubmitted = false;
        lastCloudTypeId = "";
        lastCloudShapeId = "";
        lastCloudSeed = 0;
        lastCloudRadius = 0.0F;
        frameCpuStartNs = System.nanoTime();
        raymarchCpuNs = 0L;
        compositeCpuNs = 0L;
        raymarchGpuMs = 0.0F;
        compositeGpuMs = 0.0F;
        gpuTimingSupported = true;
        raymarchGpuTimingValid = false;
        compositeGpuTimingValid = false;
        raymarchGpuAgeFrames = -1;
        compositeGpuAgeFrames = -1;
        raymarchGpuPendingQueries = 0;
        compositeGpuPendingQueries = 0;
    }

    public static void recordRendered(@NotNull CloudRenderSnapshot snapshot) {
        if (!frameOpen) {
            return;
        }

        renderedSnapshots++;
        lastCloudTypeId = snapshot.getCloudTypeId();
        lastCloudShapeId = snapshot.getShapeProfile().getShapeId();
        lastCloudSeed = snapshot.getCloudSeed();
        lastCloudRadius = snapshot.getRegionRadius();
    }

    public static void recordSubmitSkipped() {
        if (!frameOpen) {
            return;
        }

        submitSkippedSnapshots++;
    }

    public static void recordCompositeSubmitted(boolean submitted) {
        if (!frameOpen) {
            return;
        }

        compositeSubmitted = submitted;
    }

    public static long nowNs() {
        return System.nanoTime();
    }

    public static void recordRaymarchCpuTime(long startNs) {
        if (!frameOpen || startNs <= 0L) {
            return;
        }

        raymarchCpuNs += Math.max(0L, System.nanoTime() - startNs);
    }

    public static void recordCompositeCpuTime(long startNs) {
        if (!frameOpen || startNs <= 0L) {
            return;
        }

        compositeCpuNs += Math.max(0L, System.nanoTime() - startNs);
    }

    public static void recordGpuTimings(
            float raymarchMilliseconds,
            float compositeMilliseconds,
            boolean supported,
            boolean raymarchValid,
            boolean compositeValid,
            int raymarchAgeFrames,
            int compositeAgeFrames,
            int raymarchPending,
            int compositePending
    ) {
        if (!frameOpen) {
            return;
        }

        raymarchGpuMs = Math.max(0.0F, raymarchMilliseconds);
        compositeGpuMs = Math.max(0.0F, compositeMilliseconds);
        gpuTimingSupported = supported;
        raymarchGpuTimingValid = raymarchValid;
        compositeGpuTimingValid = compositeValid;
        raymarchGpuAgeFrames = raymarchAgeFrames;
        compositeGpuAgeFrames = compositeAgeFrames;
        raymarchGpuPendingQueries = Math.max(0, raymarchPending);
        compositeGpuPendingQueries = Math.max(0, compositePending);
    }

    public static void finishFrame() {
        if (!frameOpen) {
            return;
        }

        frameOpen = false;
        long frameCpuNs = frameCpuStartNs <= 0L ? 0L : Math.max(0L, System.nanoTime() - frameCpuStartNs);
        int filteredSkipped = Math.max(0, sourceSnapshots - renderableSnapshots);
        int totalSkipped = filteredSkipped + submitSkippedSnapshots;
        FrameStats stats = new FrameStats(
                worldTime,
                qualityName,
                raymarchSteps,
                resolutionScale,
                mainWidth,
                mainHeight,
                targetWidth,
                targetHeight,
                downscaled,
                sourceSnapshots,
                renderableSnapshots,
                renderedSnapshots,
                filteredSkipped,
                submitSkippedSnapshots,
                totalSkipped,
                compositeSubmitted,
                lastCloudTypeId,
                lastCloudShapeId,
                lastCloudSeed,
                lastCloudRadius,
                nsToMs(frameCpuNs),
                nsToMs(raymarchCpuNs),
                nsToMs(compositeCpuNs),
                raymarchGpuMs,
                compositeGpuMs,
                gpuTimingSupported,
                raymarchGpuTimingValid,
                compositeGpuTimingValid,
                raymarchGpuAgeFrames,
                compositeGpuAgeFrames,
                raymarchGpuPendingQueries,
                compositeGpuPendingQueries,
                estimatePixelStepsMillions()
        );
        lastStats = stats;
        maybeLog(stats);
    }

    public static @NotNull FrameStats getLastStats() {
        return lastStats;
    }

    public static boolean shouldLogDepthProbe(long worldTime) {
        if (!isDebugEnabled()) {
            return false;
        }

        if (worldTime == lastDepthProbeWorldTime || worldTime % LOG_INTERVAL_TICKS != 0L) {
            return false;
        }

        lastDepthProbeWorldTime = worldTime;
        return true;
    }

    public static @NotNull String getCurrentQualityName() {
        return resolveQualityName();
    }

    public static boolean shouldLogStateSnapshot(long worldTime, @NotNull String signature) {
        if (!isDebugEnabled()) {
            return false;
        }

        if (signature.equals(lastStateSnapshotSignature) && worldTime == lastStateSnapshotWorldTime) {
            return false;
        }

        if (signature.equals(lastStateSnapshotSignature) && worldTime % LOG_INTERVAL_TICKS != 0L) {
            return false;
        }

        lastStateSnapshotWorldTime = worldTime;
        lastStateSnapshotSignature = signature;
        return true;
    }

    private static void maybeLog(@NotNull FrameStats stats) {
        if (!isDebugEnabled()) {
            return;
        }

        if (stats.worldTime() == lastLoggedWorldTime || stats.worldTime() % LOG_INTERVAL_TICKS != 0L) {
            return;
        }

        lastLoggedWorldTime = stats.worldTime();
        ProjectAtmosphere.LOGGER.info(
                "[CloudRender] quality={} steps={} scale={} main={}x{} target={}x{} downscaled={} snapshots={}/{} rendered={} skipped={} filtered={} submitSkipped={} composite={} workMPxSteps={} cpuMs={} rayCpuMs={} compositeCpuMs={} rayGpuMs={} compositeGpuMs={} gpuTimer={} rayAge={} rayPending={} compAge={} compPending={} lastCloud={}",
                stats.qualityName(),
                stats.raymarchSteps(),
                formatFloat(stats.resolutionScale()),
                stats.mainWidth(),
                stats.mainHeight(),
                stats.targetWidth(),
                stats.targetHeight(),
                stats.downscaled(),
                stats.renderableSnapshots(),
                stats.sourceSnapshots(),
                stats.renderedSnapshots(),
                stats.totalSkippedSnapshots(),
                stats.filteredSkippedSnapshots(),
                stats.submitSkippedSnapshots(),
                stats.compositeSubmitted(),
                formatFloat(stats.pixelStepMegas()),
                formatFloat(stats.frameCpuMs()),
                formatFloat(stats.raymarchCpuMs()),
                formatFloat(stats.compositeCpuMs()),
                formatFloat(stats.raymarchGpuMs()),
                formatFloat(stats.compositeGpuMs()),
                stats.gpuTimingSupported(),
                stats.raymarchGpuAgeFrames(),
                stats.raymarchGpuPendingQueries(),
                stats.compositeGpuAgeFrames(),
                stats.compositeGpuPendingQueries(),
                stats.describeLastCloud()
        );
    }

    private static boolean isDebugEnabled() {
        try {
            return AtmoCommonConfig.DEBUG_MODE.get();
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private static String resolveQualityName() {
        try {
            return AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.get().name();
        } catch (IllegalStateException exception) {
            return "UNKNOWN";
        }
    }

    private static String formatFloat(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static float nsToMs(long value) {
        return (float) (value / 1_000_000.0D);
    }

    private static float estimatePixelStepsMillions() {
        return targetWidth * (float) targetHeight * (float) raymarchSteps / 1_000_000.0F;
    }

    public record FrameStats(
            long worldTime,
            @NotNull String qualityName,
            int raymarchSteps,
            float resolutionScale,
            int mainWidth,
            int mainHeight,
            int targetWidth,
            int targetHeight,
            boolean downscaled,
            int sourceSnapshots,
            int renderableSnapshots,
            int renderedSnapshots,
            int filteredSkippedSnapshots,
            int submitSkippedSnapshots,
            int totalSkippedSnapshots,
            boolean compositeSubmitted,
            @Nullable String lastCloudTypeId,
            @Nullable String lastCloudShapeId,
            int lastCloudSeed,
            float lastCloudRadius,
            float frameCpuMs,
            float raymarchCpuMs,
            float compositeCpuMs,
            float raymarchGpuMs,
            float compositeGpuMs,
            boolean gpuTimingSupported,
            boolean raymarchGpuTimingValid,
            boolean compositeGpuTimingValid,
            int raymarchGpuAgeFrames,
            int compositeGpuAgeFrames,
            int raymarchGpuPendingQueries,
            int compositeGpuPendingQueries,
            float pixelStepMegas
    ) {
        private static FrameStats empty() {
            return new FrameStats(
                    0L,
                    "UNKNOWN",
                    0,
                    1.0F,
                    0,
                    0,
                    0,
                    0,
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    false,
                    "",
                    "",
                    0,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    true,
                    false,
                    false,
                    -1,
                    -1,
                    0,
                    0,
                    0.0F
            );
        }

        public @NotNull String describeLastCloud() {
            if (lastCloudTypeId == null || lastCloudTypeId.isBlank()) {
                return "none";
            }

            String shape = lastCloudShapeId == null || lastCloudShapeId.isBlank() ? "shape=unknown" : "shape=" + lastCloudShapeId;
            return lastCloudTypeId + "/" + shape + "/seed=" + lastCloudSeed + "/radius=" + formatFloat(lastCloudRadius);
        }
    }
}
