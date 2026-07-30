package net.Gabou.projectatmosphere.modules.wind;

import net.minecraft.nbt.CompoundTag;

public final class WindRuntimeState {
    private static final String TAG_HIGH_SPEED = "CurrentHighSpeed";
    private static final String TAG_HIGH_DIRECTION = "CurrentHighDirectionDeg";
    private static final String TAG_LOW_SPEED = "CurrentLowSpeed";
    private static final String TAG_LOW_DIRECTION = "CurrentLowDirectionDeg";
    private static final String TAG_GUST_BONUS = "CurrentGustBonus";
    private static final String TAG_GUST_ACTIVE = "GustActive";
    private static final String TAG_GUST_END_TICK = "GustEndTick";

    private float currentHighSpeed;
    private float currentHighDirectionDeg;
    private float currentLowSpeed;
    private float currentLowDirectionDeg;
    private float currentGustBonus;
    private boolean gustActive;
    private long gustEndTick;

    public float getCurrentHighSpeed() {
        return currentHighSpeed;
    }

    public void setCurrentHighSpeed(float currentHighSpeed) {
        this.currentHighSpeed = currentHighSpeed;
    }

    public float getCurrentHighDirectionDeg() {
        return currentHighDirectionDeg;
    }

    public void setCurrentHighDirectionDeg(float currentHighDirectionDeg) {
        this.currentHighDirectionDeg = currentHighDirectionDeg;
    }

    public float getCurrentLowSpeed() {
        return currentLowSpeed;
    }

    public void setCurrentLowSpeed(float currentLowSpeed) {
        this.currentLowSpeed = currentLowSpeed;
    }

    public float getCurrentLowDirectionDeg() {
        return currentLowDirectionDeg;
    }

    public void setCurrentLowDirectionDeg(float currentLowDirectionDeg) {
        this.currentLowDirectionDeg = currentLowDirectionDeg;
    }

    public float getCurrentGustBonus() {
        return currentGustBonus;
    }

    public void setCurrentGustBonus(float currentGustBonus) {
        this.currentGustBonus = currentGustBonus;
    }

    public boolean isGustActive() {
        return gustActive;
    }

    public void setGustActive(boolean gustActive) {
        this.gustActive = gustActive;
    }

    public long getGustEndTick() {
        return gustEndTick;
    }

    public void setGustEndTick(long gustEndTick) {
        this.gustEndTick = gustEndTick;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat(TAG_HIGH_SPEED, currentHighSpeed);
        tag.putFloat(TAG_HIGH_DIRECTION, currentHighDirectionDeg);
        tag.putFloat(TAG_LOW_SPEED, currentLowSpeed);
        tag.putFloat(TAG_LOW_DIRECTION, currentLowDirectionDeg);
        tag.putFloat(TAG_GUST_BONUS, currentGustBonus);
        tag.putBoolean(TAG_GUST_ACTIVE, gustActive);
        tag.putLong(TAG_GUST_END_TICK, gustEndTick);
        return tag;
    }

    static WindRuntimeState load(CompoundTag tag) {
        WindRuntimeState state = new WindRuntimeState();
        if (tag == null || tag.isEmpty()) {
            return state;
        }
        state.setCurrentHighSpeed(safeNonNegative(tag.getFloat(TAG_HIGH_SPEED)));
        state.setCurrentHighDirectionDeg(safeDegrees(tag.getFloat(TAG_HIGH_DIRECTION)));
        state.setCurrentLowSpeed(safeNonNegative(tag.getFloat(TAG_LOW_SPEED)));
        state.setCurrentLowDirectionDeg(safeDegrees(tag.getFloat(TAG_LOW_DIRECTION)));
        state.setCurrentGustBonus(safeNonNegative(tag.getFloat(TAG_GUST_BONUS)));
        state.setGustActive(tag.getBoolean(TAG_GUST_ACTIVE));
        state.setGustEndTick(tag.getLong(TAG_GUST_END_TICK));
        return state;
    }

    private static float safeNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }

    private static float safeDegrees(float value) {
        return Float.isFinite(value) ? value : 0f;
    }
}
