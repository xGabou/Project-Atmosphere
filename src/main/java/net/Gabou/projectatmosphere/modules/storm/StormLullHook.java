package net.Gabou.projectatmosphere.modules.storm;


import net.minecraft.server.level.ServerLevel;

public final class StormLullHook {
    private StormLullHook() {}

    /**
     * Adjust chance by days since last severe (severity >= threshold).
     */
    public static float applyLullBoost(ServerLevel level, int currentDay, float baseChance) {
        GlobalStormHistoryData data = GlobalStormHistoryData.get(level);
        int last = data.getLastSevereDay();
        int daysSince = (last == Integer.MIN_VALUE) ? Integer.MAX_VALUE : Math.max(0, currentDay - last);
        return StormChanceAdjuster.adjust(baseChance, daysSince);
    }

    /** Record if severity crosses threshold. */
    public static void maybeRecordSevere(ServerLevel level, int currentDay, int severity) {
        if (severity >= StormChanceAdjuster.SEVERITY_THRESHOLD) {
            GlobalStormHistoryData.get(level).recordSevere(currentDay);
        }
    }
}
