package net.Gabou.projectatmosphere.modules.storm;

import net.minecraft.core.HolderLookup;
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

    public static GlobalStormHistoryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        GlobalStormHistoryData::new,  // constructor
                        GlobalStormHistoryData::load  // deserializer
                ),
                STORAGE_ID
        );
    }

    public GlobalStormHistoryData() {}

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("lastSevereDay", lastSevereDay);
        tag.putInt("recentSevereCount", recentSevereCount);
        tag.putInt("cooldownDaysRemaining", cooldownDaysRemaining);
        return tag;
    }

    public static GlobalStormHistoryData load(CompoundTag tag, HolderLookup.Provider provider) {
        GlobalStormHistoryData data = new GlobalStormHistoryData();
        data.lastSevereDay = tag.getInt("lastSevereDay");
        data.recentSevereCount = tag.getInt("recentSevereCount");
        data.cooldownDaysRemaining = tag.getInt("cooldownDaysRemaining");
        return data;
    }

    public int getLastSevereDay() {
        return lastSevereDay;
    }

    public int getRecentSevereCount() {
        return recentSevereCount;
    }

    public int getCooldownDaysRemaining() {
        return cooldownDaysRemaining;
    }

    public void setCooldownDaysRemaining(int days) {
        this.cooldownDaysRemaining = Math.max(0, days);
        setDirty();
    }

    public void recordSevere(int dayIndex) {
        if (lastSevereDay == dayIndex - 1) {
            recentSevereCount++;
        } else if (lastSevereDay != dayIndex) {
            recentSevereCount = 1;
        }
        lastSevereDay = dayIndex;
        setDirty();
    }

    public void resetIfCalm(int currentDay) {
        if (currentDay != lastSevereDay && recentSevereCount != 0) {
            recentSevereCount = 0;
            setDirty();
        }
    }
}

