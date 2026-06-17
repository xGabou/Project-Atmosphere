package net.Gabou.projectatmosphere.manager;

public final class AtmosphereWorldEffectsDiagnostics {
    private static volatile FrameStats lastStats = FrameStats.empty();

    private AtmosphereWorldEffectsDiagnostics() {
    }

    public static void record(FrameStats stats) {
        lastStats = stats == null ? FrameStats.empty() : stats;
    }

    public static FrameStats getLastStats() {
        return lastStats;
    }

    public record FrameStats(
            long worldTime,
            boolean enabled,
            int players,
            int samples,
            int rainySamples,
            int skyBlockedSamples,
            int firesRemoved,
            int campfiresDoused,
            int cauldronsFilled,
            int eventHooks,
            int customHooks,
            float lastRainIntensity
    ) {
        public static FrameStats empty() {
            return new FrameStats(0L, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0F);
        }
    }
}
