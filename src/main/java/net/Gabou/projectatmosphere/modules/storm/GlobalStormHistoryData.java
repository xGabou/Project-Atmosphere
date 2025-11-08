package net.Gabou.projectatmosphere.modules.storm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Tracks the last day index when a severe storm occurred (severity >= threshold).
 * One value per dimension. If you want universal across all dimensions,
 * just key by "global".
 */
public final class GlobalStormHistoryData extends SavedData {
    private static final String STORAGE_ID = "project_atmosphere_storm_history";

    private int lastSevereDay = Integer.MIN_VALUE; // none yet

    private int recentSevereCount = 0;
    private int cooldownDaysRemaining = 0;

    public int getLastSevereDay() { return lastSevereDay; }
    public int getRecentSevereCount() { return recentSevereCount; }
    public int getCooldownDaysRemaining() { return cooldownDaysRemaining; }

    public void setCooldownDaysRemaining(int days) {
        this.cooldownDaysRemaining = days;
    }

    public void recordSevere(int currentDay) {
        if (lastSevereDay == currentDay - 1) {
            recentSevereCount++;
        } else {
            recentSevereCount = 1;
        }
        lastSevereDay = currentDay;
    }

    public void resetIfCalm(int currentDay) {
        if (currentDay != lastSevereDay)
            recentSevereCount = 0;
    }


    public static GlobalStormHistoryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(GlobalStormHistoryData::load,
                GlobalStormHistoryData::new,
                STORAGE_ID);
    }

    public GlobalStormHistoryData() {}

    public static GlobalStormHistoryData load(CompoundTag tag) {
        GlobalStormHistoryData data = new GlobalStormHistoryData();
        data.lastSevereDay = tag.getInt("lastSevereDay");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("lastSevereDay", lastSevereDay);
        return tag;
    }
}
