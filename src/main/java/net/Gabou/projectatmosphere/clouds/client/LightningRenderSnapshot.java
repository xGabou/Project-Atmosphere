package net.Gabou.projectatmosphere.clouds.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class LightningRenderSnapshot {
    public enum Mode {
        SHEET,
        BRANCHING_BOLT,
        DISTANT_FLASH,
        EYEWALL_ARC,
        GROUND_STRIKE
    }

    private final UUID cloudId;
    private final long branchSeed;
    private final Vec3 startAnchor;
    private final Vec3 endAnchor;
    private final float intensity;
    private final int ageTicks;
    private final int lifetimeTicks;
    private final int color;
    private final int forkCount;
    private final Mode mode;

    public LightningRenderSnapshot(
            UUID cloudId,
            long branchSeed,
            Vec3 startAnchor,
            Vec3 endAnchor,
            float intensity,
            int ageTicks,
            int lifetimeTicks,
            int color,
            int forkCount,
            Mode mode
    ) {
        this.cloudId = cloudId;
        this.branchSeed = cloudId == null ? branchSeed : branchSeed ^ cloudId.getLeastSignificantBits();
        this.startAnchor = startAnchor == null ? Vec3.ZERO : startAnchor;
        this.endAnchor = endAnchor == null ? this.startAnchor : endAnchor;
        this.intensity = Mth.clamp(intensity, 0.0F, 1.0F);
        this.ageTicks = Math.max(0, ageTicks);
        this.lifetimeTicks = Math.max(1, lifetimeTicks);
        this.color = color;
        this.forkCount = Mth.clamp(forkCount, 0, 16);
        this.mode = mode == null ? Mode.SHEET : mode;
    }

    public UUID getCloudId() {
        return cloudId;
    }

    public long getBranchSeed() {
        return branchSeed;
    }

    public Vec3 getStartAnchor() {
        return startAnchor;
    }

    public Vec3 getEndAnchor() {
        return endAnchor;
    }

    public float getIntensity() {
        return intensity;
    }

    public int getAgeTicks() {
        return ageTicks;
    }

    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    public int getColor() {
        return color;
    }

    public int getForkCount() {
        return forkCount;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isGroundStrike() {
        return mode == Mode.GROUND_STRIKE || mode == Mode.BRANCHING_BOLT;
    }

    public float getLifeFade() {
        float progress = Mth.clamp(ageTicks / (float) lifetimeTicks, 0.0F, 1.0F);
        return intensity * (1.0F - progress);
    }
}
