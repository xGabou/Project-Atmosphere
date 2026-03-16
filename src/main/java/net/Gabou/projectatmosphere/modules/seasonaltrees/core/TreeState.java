package net.Gabou.projectatmosphere.modules.seasonaltrees.core;

import net.minecraft.nbt.CompoundTag;

public final class TreeState {
    private static final String TAG_LEAF_STATE = "leafState";
    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_LAST_SEASON = "lastSeason";
    private static final String TAG_VIGOR = "vigor";
    private static final String TAG_LAST_SEASON_TICK = "lastSeasonTick";
    private static final String TAG_LAST_VIGOR_DAY = "lastVigorDay";

    private LeafState leafState;
    private float progress;
    private SeasonPhase lastSeasonApplied;
    private float vigor;
    private long lastSeasonTick;
    private long lastVigorDay;

    public TreeState(LeafState leafState, float progress, SeasonPhase lastSeasonApplied, float vigor, long lastSeasonTick, long lastVigorDay) {
        this.leafState = leafState;
        this.progress = progress;
        this.lastSeasonApplied = lastSeasonApplied;
        this.vigor = vigor;
        this.lastSeasonTick = lastSeasonTick;
        this.lastVigorDay = lastVigorDay;
    }

    public static TreeState defaultState() {
        return new TreeState(LeafState.FULL, 1.0f, SeasonPhase.SUMMER, 0.75f, 0L, -1L);
    }

    public LeafState leafState() {
        return leafState;
    }

    public void setLeafState(LeafState leafState) {
        this.leafState = leafState;
    }

    public float progress() {
        return progress;
    }

    public void setProgress(float progress) {
        this.progress = progress;
    }

    public SeasonPhase lastSeasonApplied() {
        return lastSeasonApplied;
    }

    public void setLastSeasonApplied(SeasonPhase lastSeasonApplied) {
        this.lastSeasonApplied = lastSeasonApplied;
    }

    public float vigor() {
        return vigor;
    }

    public void setVigor(float vigor) {
        this.vigor = vigor;
    }

    public long lastSeasonTick() {
        return lastSeasonTick;
    }

    public void setLastSeasonTick(long lastSeasonTick) {
        this.lastSeasonTick = lastSeasonTick;
    }

    public long lastVigorDay() {
        return lastVigorDay;
    }

    public void setLastVigorDay(long lastVigorDay) {
        this.lastVigorDay = lastVigorDay;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_LEAF_STATE, leafState.name());
        tag.putFloat(TAG_PROGRESS, progress);
        tag.putString(TAG_LAST_SEASON, lastSeasonApplied.name());
        tag.putFloat(TAG_VIGOR, vigor);
        tag.putLong(TAG_LAST_SEASON_TICK, lastSeasonTick);
        tag.putLong(TAG_LAST_VIGOR_DAY, lastVigorDay);
        return tag;
    }

    public static TreeState fromTag(CompoundTag tag) {
        LeafState leafState = LeafState.fromSerialized(tag.getString(TAG_LEAF_STATE));
        float progress = tag.getFloat(TAG_PROGRESS);
        SeasonPhase lastSeason = SeasonPhase.valueOf(tag.getString(TAG_LAST_SEASON));
        float vigor = tag.getFloat(TAG_VIGOR);
        long lastSeasonTick = tag.getLong(TAG_LAST_SEASON_TICK);
        long lastVigorDay = tag.contains(TAG_LAST_VIGOR_DAY) ? tag.getLong(TAG_LAST_VIGOR_DAY) : -1L;
        return new TreeState(leafState, progress, lastSeason, vigor, lastSeasonTick, lastVigorDay);
    }
}
