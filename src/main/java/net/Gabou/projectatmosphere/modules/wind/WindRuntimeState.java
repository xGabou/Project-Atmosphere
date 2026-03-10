package net.Gabou.projectatmosphere.modules.wind;

public final class WindRuntimeState {
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
}

