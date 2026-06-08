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
    private static int lastCloudSeed;
    private static float lastCloudRadius;

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
        compositeSubmitted = !usesDownscaledTarget;
        lastCloudTypeId = "";
        lastCloudSeed = 0;
        lastCloudRadius = 0.0F;
    }

    public static void recordRendered(@NotNull CloudRenderSnapshot snapshot) {
        if (!frameOpen) {
            return;
        }

        renderedSnapshots++;
        lastCloudTypeId = snapshot.getCloudTypeId();
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

    public static void finishFrame() {
        if (!frameOpen) {
            return;
        }

        frameOpen = false;
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
                lastCloudSeed,
                lastCloudRadius
        );
        lastStats = stats;
        maybeLog(stats);
    }

    public static @NotNull FrameStats getLastStats() {
        return lastStats;
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
                "[CloudRender] quality={} steps={} scale={} main={}x{} target={}x{} downscaled={} snapshots={}/{} rendered={} skipped={} filtered={} submitSkipped={} composite={} lastCloud={}",
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
            int lastCloudSeed,
            float lastCloudRadius
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
                    0,
                    0.0F
            );
        }

        public @NotNull String describeLastCloud() {
            if (lastCloudTypeId == null || lastCloudTypeId.isBlank()) {
                return "none";
            }

            return lastCloudTypeId + "/seed=" + lastCloudSeed + "/radius=" + formatFloat(lastCloudRadius);
        }
    }
}
