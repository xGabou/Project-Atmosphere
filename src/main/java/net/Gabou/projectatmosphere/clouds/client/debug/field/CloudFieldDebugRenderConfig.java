package net.Gabou.projectatmosphere.clouds.client.debug.field;

import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderDebugMode;

/**
 * Client-only toggle and limits for CloudField debug visualization.
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

    public static boolean shouldRender() {
        return enabled || CloudRenderDebugMode.current().isActive();
    }

    public static int maxCloudletMarkers() {
        return maxCloudletMarkers;
    }

    public static void setMaxCloudletMarkers(int maxMarkers) {
        maxCloudletMarkers = Math.max(0, Math.min(512, maxMarkers));
    }

    public static String status() {
        String mode = CloudRenderDebugMode.current().serializedName();
        return "CloudField debug explicit=" + enabled
                + " cloudRenderDebug=" + mode
                + " effective=" + shouldRender()
                + " maxCloudletMarkers=" + maxCloudletMarkers;
    }
}
