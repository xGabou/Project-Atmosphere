package net.Gabou.projectatmosphere.clouds.client.debug.field;

import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderDebugMode;

/**
 * Client-only toggle and limits for the legacy CloudField line/debug visualization.
 * CloudFieldVolumeRenderer owns the active experimental GLSL CloudField rendering path;
 * this renderer is explicit legacy diagnostics only and stays off by default.
 */
public final class CloudFieldDebugRenderConfig {
    private static final int DEFAULT_MAX_CLOUDLET_MARKERS = 96;

    private static volatile boolean enabled;
    private static volatile int maxCloudletMarkers = DEFAULT_MAX_CLOUDLET_MARKERS;

    private CloudFieldDebugRenderConfig() {
    }

    public static void setEnabled(boolean shouldEnable) {
        enabled = shouldEnable;
    }

    public static boolean isExplicitlyEnabled() {
        return enabled;
    }

    /**
     * Returns whether the legacy pre-raymarch CloudField debug renderer should draw.
     * This intentionally does not follow CloudRenderDebugMode so the legacy renderer
     * cannot draw beside the active CloudField volume renderer unless explicitly enabled.
     *
     * @return true when the legacy debug renderer was explicitly enabled
     */
    public static boolean shouldRender() {
        return enabled;
    }

    public static int maxCloudletMarkers() {
        return maxCloudletMarkers;
    }

    public static void setMaxCloudletMarkers(int maxMarkers) {
        maxCloudletMarkers = Math.max(0, Math.min(512, maxMarkers));
    }

    public static String status() {
        String mode = CloudRenderDebugMode.current().serializedName();
        return "Legacy CloudField debug renderer"
                + "\nlegacyCloudFieldRendererEnabled=" + enabled
                + "\ncloudRenderDebugMode=" + mode
                + "\nautoEnabledByCloudRenderDebug=false"
                + "\neffective=" + shouldRender()
                + " maxCloudletMarkers=" + maxCloudletMarkers;
    }
}
