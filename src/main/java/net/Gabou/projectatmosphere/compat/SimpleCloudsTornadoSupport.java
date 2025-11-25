package net.Gabou.projectatmosphere.compat;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks whether the SimpleClouds shader pack exposes the CloudTornadoes SSBO.
 * Updated on the client when the compute shader is inspected so server logic can
 * decide whether to fall back to the legacy mesh-based tornado.
 */
public final class SimpleCloudsTornadoSupport {
    private static final AtomicBoolean TORNADO_SSBO_SUPPORTED = new AtomicBoolean(true);

    private SimpleCloudsTornadoSupport() {
    }

    public static boolean isTornadoSsboSupported() {
        return TORNADO_SSBO_SUPPORTED.get();
    }

    public static void setTornadoSsboSupported(boolean supported) {
        TORNADO_SSBO_SUPPORTED.set(supported);
    }
}
