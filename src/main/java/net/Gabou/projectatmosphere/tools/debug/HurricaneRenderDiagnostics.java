package net.Gabou.projectatmosphere.tools.debug;

public final class HurricaneRenderDiagnostics {
    private static volatile FrameStats lastStats = FrameStats.empty();

    private static boolean frameOpen;
    private static long frameStartNs;
    private static long worldTime;
    private static int renderableHurricanes;
    private static int preparedHurricanes;
    private static boolean prepareCacheHit;
    private static long prepareCpuNs;
    private static long scratchCpuNs;
    private static long opaqueMaskCpuNs;
    private static long opaqueVolumeCpuNs;
    private static long transparencyMaskCpuNs;
    private static long transparencyVolumeCpuNs;

    private HurricaneRenderDiagnostics() {
    }

    public static void beginFrame(long gameTime, int renderableCount, int preparedCount) {
        finishFrame();
        frameOpen = true;
        frameStartNs = System.nanoTime();
        worldTime = gameTime;
        renderableHurricanes = Math.max(0, renderableCount);
        preparedHurricanes = Math.max(0, preparedCount);
        prepareCacheHit = false;
        prepareCpuNs = 0L;
        scratchCpuNs = 0L;
        opaqueMaskCpuNs = 0L;
        opaqueVolumeCpuNs = 0L;
        transparencyMaskCpuNs = 0L;
        transparencyVolumeCpuNs = 0L;
    }

    public static void markPrepareCacheHit() {
        if (frameOpen) {
            prepareCacheHit = true;
        }
    }

    public static long nowNs() {
        return System.nanoTime();
    }

    public static void recordPrepareCpuTime(long startNs) {
        if (frameOpen && startNs > 0L) {
            prepareCpuNs += Math.max(0L, System.nanoTime() - startNs);
        }
    }

    public static void recordScratchCpuTime(long startNs) {
        if (frameOpen && startNs > 0L) {
            scratchCpuNs += Math.max(0L, System.nanoTime() - startNs);
        }
    }

    public static void recordOpaqueMaskCpuTime(long startNs) {
        if (frameOpen && startNs > 0L) {
            opaqueMaskCpuNs += Math.max(0L, System.nanoTime() - startNs);
        }
    }

    public static void recordOpaqueVolumeCpuTime(long startNs) {
        if (frameOpen && startNs > 0L) {
            opaqueVolumeCpuNs += Math.max(0L, System.nanoTime() - startNs);
        }
    }

    public static void recordTransparencyMaskCpuTime(long startNs) {
        if (frameOpen && startNs > 0L) {
            transparencyMaskCpuNs += Math.max(0L, System.nanoTime() - startNs);
        }
    }

    public static void recordTransparencyVolumeCpuTime(long startNs) {
        if (frameOpen && startNs > 0L) {
            transparencyVolumeCpuNs += Math.max(0L, System.nanoTime() - startNs);
        }
    }

    public static void finishFrame() {
        if (!frameOpen) {
            return;
        }

        frameOpen = false;
        lastStats = new FrameStats(
                worldTime,
                renderableHurricanes,
                preparedHurricanes,
                prepareCacheHit,
                nsToMs(prepareCpuNs),
                nsToMs(scratchCpuNs),
                nsToMs(opaqueMaskCpuNs),
                nsToMs(opaqueVolumeCpuNs),
                nsToMs(transparencyMaskCpuNs),
                nsToMs(transparencyVolumeCpuNs),
                nsToMs(Math.max(0L, System.nanoTime() - frameStartNs))
        );
    }

    public static void clear() {
        frameOpen = false;
        lastStats = FrameStats.empty();
    }

    public static FrameStats getLastStats() {
        return lastStats;
    }

    private static float nsToMs(long value) {
        return (float) (value / 1_000_000.0D);
    }

    public record FrameStats(
            long worldTime,
            int renderableHurricanes,
            int preparedHurricanes,
            boolean prepareCacheHit,
            float prepareCpuMs,
            float scratchCpuMs,
            float opaqueMaskCpuMs,
            float opaqueVolumeCpuMs,
            float transparencyMaskCpuMs,
            float transparencyVolumeCpuMs,
            float totalCpuMs
    ) {
        private static FrameStats empty() {
            return new FrameStats(
                    0L,
                    0,
                    0,
                    false,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F
            );
        }

        public boolean hasData() {
            return this.renderableHurricanes > 0 || this.preparedHurricanes > 0 || this.totalCpuMs > 0.0F;
        }

        public String describe(boolean detailed) {
            if (!this.hasData()) {
                return "idle";
            }

            if (!detailed) {
                return "storms=" + this.preparedHurricanes
                        + " prep=" + format(this.prepareCpuMs + this.scratchCpuMs)
                        + "ms total=" + format(this.totalCpuMs) + "ms";
            }

            return "storms=" + this.preparedHurricanes + "/" + this.renderableHurricanes
                    + " cache=" + yesNo(this.prepareCacheHit)
                    + " prep=" + format(this.prepareCpuMs)
                    + "ms scratch=" + format(this.scratchCpuMs)
                    + "ms opaque=" + format(this.opaqueMaskCpuMs)
                    + "/" + format(this.opaqueVolumeCpuMs)
                    + "ms trans=" + format(this.transparencyMaskCpuMs)
                    + "/" + format(this.transparencyVolumeCpuMs)
                    + "ms total=" + format(this.totalCpuMs) + "ms";
        }

        private static String yesNo(boolean value) {
            return value ? "yes" : "no";
        }

        private static String format(float value) {
            return String.format(java.util.Locale.ROOT, "%.2f", value);
        }
    }
}
