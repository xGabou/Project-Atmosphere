package net.Gabou.projectatmosphere.client.render;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SimpleCloudsRenderDiagnostics {
    private static final Logger LOGGER = LogManager.getLogger("ProjectAtmosphere/SimpleCloudsRender");
    private static final boolean ENABLED = Boolean.getBoolean("projectatmosphere.simpleclouds.debugRender");
    private static final ThreadLocal<PassStats> CURRENT_PASS = ThreadLocal.withInitial(PassStats::new);

    private SimpleCloudsRenderDiagnostics() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void beginPass(String passName, int totalChunks, int opaqueBytes, int transparentBytes, Object meshStatus) {
        if (!ENABLED) {
            return;
        }

        PassStats stats = CURRENT_PASS.get();
        stats.passName = passName;
        stats.totalChunks = totalChunks;
        stats.opaqueBytes = opaqueBytes;
        stats.transparentBytes = transparentBytes;
        stats.meshStatus = meshStatus;
        stats.drawCalls = 0;
        stats.totalElements = 0;
        stats.alphaFallbacks = 0;
    }

    public static void recordDraw(String passName, int elementCount) {
        if (!ENABLED) {
            return;
        }

        PassStats stats = CURRENT_PASS.get();
        if (stats.passName == null || "unknown".equals(stats.passName)) {
            stats.passName = passName;
        }
        stats.drawCalls++;
        stats.totalElements += Math.max(0, elementCount);
    }

    public static void noteAlphaFallback(int elementCount, int ticksSinceLastGen) {
        if (!ENABLED) {
            return;
        }

        PassStats stats = CURRENT_PASS.get();
        stats.alphaFallbacks++;
        LOGGER.info(
                "[SimpleCloudsRender] alpha fallback triggered elementCount={} ticksSinceLastGen={} pass={} drawCalls={} totalElements={}",
                elementCount,
                ticksSinceLastGen,
                stats.passName,
                stats.drawCalls,
                stats.totalElements
        );
    }

    public static void endPass() {
        if (!ENABLED) {
            return;
        }

        PassStats stats = CURRENT_PASS.get();
        LOGGER.info(
                "[SimpleCloudsRender] pass={} totalChunks={} drawCalls={} totalElements={} alphaFallbacks={} opaqueBytes={} transparentBytes={} meshStatus={}",
                stats.passName,
                stats.totalChunks,
                stats.drawCalls,
                stats.totalElements,
                stats.alphaFallbacks,
                stats.opaqueBytes,
                stats.transparentBytes,
                stats.meshStatus
        );
    }

    private static final class PassStats {
        private String passName = "unknown";
        private int totalChunks;
        private int opaqueBytes;
        private int transparentBytes;
        private Object meshStatus;
        private int drawCalls;
        private int totalElements;
        private int alphaFallbacks;
    }
}
