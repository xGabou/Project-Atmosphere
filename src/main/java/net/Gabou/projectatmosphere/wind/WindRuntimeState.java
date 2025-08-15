package net.Gabou.projectatmosphere.wind;

public final class WindRuntimeState {
    private float currentBaseSpeed;
    private float currentGustSpeed;
    private float currentDirectionDeg;
    private long gustEndTick;
    private float targetBaseSpeed;
    private float targetDirectionDeg;
    private long nextRetargetTick;

    public float getCurrentBaseSpeed() {
        return currentBaseSpeed;
    }

    public void setCurrentBaseSpeed(float currentBaseSpeed) {
        this.currentBaseSpeed = currentBaseSpeed;
    }

    public float getCurrentGustSpeed() {
        return currentGustSpeed;
    }

    public void setCurrentGustSpeed(float currentGustSpeed) {
        this.currentGustSpeed = currentGustSpeed;
    }

    public float getCurrentDirectionDeg() {
        return currentDirectionDeg;
    }

    public void setCurrentDirectionDeg(float currentDirectionDeg) {
        this.currentDirectionDeg = currentDirectionDeg;
    }

    public long getGustEndTick() {
        return gustEndTick;
    }

    public void setGustEndTick(long gustEndTick) {
        this.gustEndTick = gustEndTick;
    }

    public float getTargetBaseSpeed() {
        return targetBaseSpeed;
    }

    public void setTargetBaseSpeed(float targetBaseSpeed) {
        this.targetBaseSpeed = targetBaseSpeed;
    }

    public float getTargetDirectionDeg() {
        return targetDirectionDeg;
    }

    public void setTargetDirectionDeg(float targetDirectionDeg) {
        this.targetDirectionDeg = targetDirectionDeg;
    }

    public long getNextRetargetTick() {
        return nextRetargetTick;
    }

    public void setNextRetargetTick(long nextRetargetTick) {
        this.nextRetargetTick = nextRetargetTick;
    }
}

