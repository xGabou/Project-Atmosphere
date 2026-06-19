package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.client.debug.CloudDebugSnapshotFactory;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Tracks the last known healthy cloud snapshot and the current render fallback state.
 * The fallback is only visible when rendering has actually failed or produced no output.
 */
public final class CloudRenderFallbackState {
    private static final int FALLBACK_COLOR = 0xFFFF3030;

    private static volatile FailureStatus status = FailureStatus.inactive();
    private static volatile CloudRenderSnapshot lastKnownGoodSnapshot;
    private static volatile CloudRenderSnapshot fallbackSnapshot;
    private static volatile String lastLogKey = "";

    private CloudRenderFallbackState() {
    }

    public static synchronized void recordRenderedSnapshot(@Nullable CloudRenderSnapshot snapshot) {
        if (snapshot != null) {
            lastKnownGoodSnapshot = snapshot;
        }
    }

    public static synchronized void recordFrameOutcome(
            @Nullable CloudRenderDiagnostics.FrameStats stats,
            @Nullable CloudRenderSnapshot candidateSnapshot,
            @Nullable Vec3 cameraPosition
    ) {
        if (stats == null) {
            return;
        }

        if (stats.renderedSnapshots() > 0) {
            clearActiveFailure();
            return;
        }

        if (stats.sourceSnapshots() <= 0) {
            if (!status.active()) {
                clearActiveFailure();
            }
            return;
        }

        if (stats.renderableSnapshots() <= 0) {
            if (!status.active()) {
                clearActiveFailure();
            }
            return;
        }

        activate(
                "No cloud output",
                buildDetail("clouds were present but nothing was rendered", stats),
                candidateSnapshot,
                cameraPosition,
                stats,
                false
        );
    }

    public static synchronized void recordThrowable(
            @Nullable Throwable throwable,
            @Nullable CloudRenderSnapshot candidateSnapshot,
            @Nullable Vec3 cameraPosition,
            @Nullable CloudRenderDiagnostics.FrameStats stats
    ) {
        String title = throwable == null ? "Cloud render failure" : throwable.getClass().getSimpleName();
        String message = throwable == null ? "" : throwable.getMessage();
        String detail = message == null || message.isBlank() ? title : message.trim();
        if (stats != null) {
            detail = detail + " | " + buildCounts(stats);
        }

        String logKey = title + "|" + extractLogDetail(detail);
        boolean logThrowable = !logKey.equals(lastLogKey);
        activate(title, detail, candidateSnapshot, cameraPosition, stats, false);
        if (logThrowable && throwable != null) {
            ProjectAtmosphere.LOGGER.warn("[CloudState] cloud render exception captured; fallback marker enabled", throwable);
        }
    }

    public static synchronized void clearActiveFailure() {
        status = FailureStatus.inactive();
        fallbackSnapshot = null;
        lastLogKey = "";
    }

    public static synchronized void resetAll() {
        clearActiveFailure();
        lastKnownGoodSnapshot = null;
    }

    public static synchronized @Nullable CloudRenderSnapshot getFallbackSnapshot(@Nullable Vec3 cameraPosition) {
        if (!status.active()) {
            return null;
        }

        if (fallbackSnapshot == null) {
            fallbackSnapshot = buildFallbackSnapshot(null, cameraPosition);
        }

        return fallbackSnapshot;
    }

    public static synchronized @Nullable CloudRenderSnapshot getLastKnownGoodSnapshot() {
        return lastKnownGoodSnapshot;
    }

    public static FailureStatus getStatus() {
        return status;
    }

    private static void activate(
            @NotNull String title,
            @NotNull String detail,
            @Nullable CloudRenderSnapshot candidateSnapshot,
            @Nullable Vec3 cameraPosition,
            @Nullable CloudRenderDiagnostics.FrameStats stats
    ) {
        activate(title, detail, candidateSnapshot, cameraPosition, stats, false);
    }

    private static void activate(
            @NotNull String title,
            @NotNull String detail,
            @Nullable CloudRenderSnapshot candidateSnapshot,
            @Nullable Vec3 cameraPosition,
            @Nullable CloudRenderDiagnostics.FrameStats stats,
            boolean preferCameraMarker
    ) {
        CloudRenderSnapshot marker = buildFallbackSnapshot(candidateSnapshot, cameraPosition, preferCameraMarker);
        if (marker == null) {
            return;
        }

        FailureStatus next = new FailureStatus(
                true,
                title,
                detail,
                stats == null ? -1L : stats.worldTime(),
                stats == null ? 0 : stats.sourceSnapshots(),
                stats == null ? 0 : stats.renderableSnapshots(),
                stats == null ? 0 : stats.renderedSnapshots()
        );

        if (!next.equals(status)) {
            String logKey = title + "|" + extractLogDetail(detail);
            if (!logKey.equals(lastLogKey)) {
                ProjectAtmosphere.LOGGER.warn(
                        "[CloudState] fallback activated reason={} detail={}",
                        title,
                        detail
                );
                lastLogKey = logKey;
            }
        }

        status = next;
        fallbackSnapshot = marker;
    }

    private static @Nullable CloudRenderSnapshot buildFallbackSnapshot(
            @Nullable CloudRenderSnapshot candidateSnapshot,
            @Nullable Vec3 cameraPosition
    ) {
        return buildFallbackSnapshot(candidateSnapshot, cameraPosition, false);
    }

    private static @Nullable CloudRenderSnapshot buildFallbackSnapshot(
            @Nullable CloudRenderSnapshot candidateSnapshot,
            @Nullable Vec3 cameraPosition,
            boolean preferCameraMarker
    ) {
        if (preferCameraMarker && cameraPosition != null) {
            return CloudDebugSnapshotFactory.createDebugSnapshot(
                    CloudDebugSnapshotFactory.createFakeSnapshot(cameraPosition),
                    FALLBACK_COLOR
            );
        }

        CloudRenderSnapshot source = candidateSnapshot != null ? candidateSnapshot : lastKnownGoodSnapshot;
        if (source != null) {
            return CloudDebugSnapshotFactory.createDebugSnapshot(source, FALLBACK_COLOR);
        }

        if (cameraPosition != null) {
            return CloudDebugSnapshotFactory.createDebugSnapshot(
                    CloudDebugSnapshotFactory.createFakeSnapshot(cameraPosition),
                    FALLBACK_COLOR
            );
        }

        return null;
    }

    private static String buildDetail(@NotNull String prefix, @NotNull CloudRenderDiagnostics.FrameStats stats) {
        return prefix + " | " + buildCounts(stats);
    }

    private static String buildCounts(@NotNull CloudRenderDiagnostics.FrameStats stats) {
        return String.format(
                Locale.ROOT,
                "source=%d renderable=%d rendered=%d skipped=%d",
                stats.sourceSnapshots(),
                stats.renderableSnapshots(),
                stats.renderedSnapshots(),
                stats.totalSkippedSnapshots()
        );
    }

    private static String extractLogDetail(@NotNull String detail) {
        int separator = detail.indexOf(" | ");
        return separator >= 0 ? detail.substring(0, separator) : detail;
    }

    public record FailureStatus(
            boolean active,
            @NotNull String title,
            @NotNull String detail,
            long worldTime,
            int sourceSnapshots,
            int renderableSnapshots,
            int renderedSnapshots
    ) {
        private static FailureStatus inactive() {
            return new FailureStatus(false, "", "", -1L, 0, 0, 0);
        }
    }
}
