package net.Gabou.projectatmosphere.clouds.client.render.field;

import java.util.Locale;

/**
 * Last-frame diagnostics for the CloudField volume prototype renderer. It is
 * intentionally command-readable instead of log-spammed every render frame.
 */
public record CloudFieldVolumeRenderStats(
        boolean rendererEnabled,
        boolean shaderReady,
        CloudFieldVolumeRenderMode mode,
        CloudFieldVolumeRenderFilter filter,
        String dimensionId,
        long worldTime,
        int cachedSnapshots,
        int rendererInputFields,
        int fieldsBeforeFilter,
        int visibleFields,
        int renderedFields,
        int skippedFields,
        int noClientLevelSkipped,
        int noSnapshotsSkipped,
        int shaderUnavailableSkipped,
        int wrongDimensionSkipped,
        int invalidGeometrySkipped,
        int notVisibleSkipped,
        int filterSkipped,
        int maxFieldLimitSkipped,
        String lastRenderedFields,
        String lastSkipReason
) {
    public CloudFieldVolumeRenderStats {
        mode = mode == null ? CloudFieldVolumeRenderMode.NORMAL : mode;
        filter = filter == null ? CloudFieldVolumeRenderFilter.ALL : filter;
        dimensionId = dimensionId == null || dimensionId.isBlank() ? "unknown" : dimensionId;
        lastRenderedFields = lastRenderedFields == null || lastRenderedFields.isBlank() ? "none" : lastRenderedFields;
        lastSkipReason = lastSkipReason == null || lastSkipReason.isBlank() ? "none" : lastSkipReason;
    }

    /**
     * Creates an idle stats snapshot for states where the renderer did not
     * reach the draw path.
     *
     * @param enabled current renderer toggle
     * @param shaderReady whether the shader is loaded
     * @param mode current debug mode
     * @param reason concise skip reason
     * @param cachedSnapshots client snapshot cache size
     * @return command-readable stats snapshot
     */
    public static CloudFieldVolumeRenderStats idle(
            boolean enabled,
            boolean shaderReady,
            CloudFieldVolumeRenderMode mode,
            CloudFieldVolumeRenderFilter filter,
            String reason,
            int cachedSnapshots
    ) {
        int noClient = "no_client_level".equals(reason) ? 1 : 0;
        int noSnapshots = "no_snapshots".equals(reason) ? 1 : 0;
        int shaderUnavailable = "shader_unavailable".equals(reason) ? 1 : 0;
        return new CloudFieldVolumeRenderStats(
                enabled,
                shaderReady,
                mode,
                filter,
                "unknown",
                -1L,
                cachedSnapshots,
                cachedSnapshots,
                0,
                0,
                0,
                noClient + noSnapshots + shaderUnavailable,
                noClient,
                noSnapshots,
                shaderUnavailable,
                0,
                0,
                0,
                0,
                0,
                "none",
                reason
        );
    }

    /**
     * Formats a compact status snapshot for routine client command output.
     *
     * @return multi-line status string
     */
    public String compactStatus() {
        return "CloudField volume renderer"
                + "\nenabled=" + rendererEnabled
                + "\nshaderReady=" + shaderReady
                + "\nmode=" + mode.serializedName() + " (" + mode.shaderId() + ")"
                + "\nfilter=" + filter.serializedName()
                + "\ncachedSnapshots=" + cachedSnapshots
                + "\nfieldsBeforeFilter=" + fieldsBeforeFilter
                + "\nrenderedFields=" + renderedFields
                + "\nskippedFields=" + skippedFields
                + "\nfilteredOut=" + filterSkipped
                + "\nlastRenderedFields=" + lastRenderedFields
                + "\nsourceKindInSnapshot=true";
    }

    /**
     * Formats full render diagnostics for verbose client command output.
     *
     * @return multi-line verbose status string
     */
    public String verboseStatus() {
        return compactStatus()
                + "\ndimension=" + dimensionId
                + "\nworldTime=" + worldTime
                + "\nrendererInputFields=" + rendererInputFields
                + "\nvisibleFields=" + visibleFields
                + "\nskip.noClientLevel=" + noClientLevelSkipped
                + "\nskip.noSnapshots=" + noSnapshotsSkipped
                + "\nskip.shaderUnavailable=" + shaderUnavailableSkipped
                + "\nskip.wrongDimension=" + wrongDimensionSkipped
                + "\nskip.invalidGeometry=" + invalidGeometrySkipped
                + "\nskip.notVisible=" + notVisibleSkipped
                + "\nskip.filteredOut=" + filterSkipped
                + "\nskip.maxFieldLimit=" + maxFieldLimitSkipped
                + "\nsourceFilterMode=source_kind"
                + "\nlastSkipReason=" + lastSkipReason;
    }

    /**
     * Formats this stats snapshot for existing call sites. Defaults to compact.
     *
     * @return compact status string
     */
    public String status() {
        return compactStatus();
    }

    static String format(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
