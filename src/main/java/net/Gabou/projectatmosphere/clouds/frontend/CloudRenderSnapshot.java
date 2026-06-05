package net.Gabou.projectatmosphere.clouds.frontend;

import net.minecraft.world.phys.Vec3;

public final class CloudRenderSnapshot {

    private final boolean enabled;
    private final String dimension;
    private final long worldTime;
    private final float partialTick;

    private final Vec3 cameraPosition;
    private final Vec3 regionCenter;
    private final Vec3 previousRegionCenter;
    private final Vec3 velocity;

    private final float regionRadius;
    private final float cloudBaseY;
    private final float cloudTopY;

    private final float density;
    private final float coverage;
    private final float edgeSoftness;

    private final float windOffsetX;
    private final float windOffsetZ;

    private final int ageTicks;
    private final int lifetimeTicks;

    private final float growth;
    private final float decay;

    private final int debugColorOrTint;

    public CloudRenderSnapshot(
            boolean enabled,
            String dimension,
            long worldTime,
            float partialTick,
            Vec3 cameraPosition,
            Vec3 regionCenter,
            Vec3 previousRegionCenter,
            Vec3 velocity,
            float regionRadius,
            float cloudBaseY,
            float cloudTopY,
            float density,
            float coverage,
            float edgeSoftness,
            float windOffsetX,
            float windOffsetZ,
            int ageTicks,
            int lifetimeTicks,
            float growth,
            float decay,
            int debugColorOrTint
    ) {
        this.enabled = enabled;
        this.dimension = dimension;
        this.worldTime = worldTime;
        this.partialTick = partialTick;
        this.cameraPosition = cameraPosition;
        this.regionCenter = regionCenter;
        this.previousRegionCenter = previousRegionCenter;
        this.velocity = velocity;
        this.regionRadius = regionRadius;
        this.cloudBaseY = cloudBaseY;
        this.cloudTopY = cloudTopY;
        this.density = density;
        this.coverage = coverage;
        this.edgeSoftness = edgeSoftness;
        this.windOffsetX = windOffsetX;
        this.windOffsetZ = windOffsetZ;
        this.ageTicks = ageTicks;
        this.lifetimeTicks = lifetimeTicks;
        this.growth = growth;
        this.decay = decay;
        this.debugColorOrTint = debugColorOrTint;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDimension() {
        return dimension;
    }

    public long getWorldTime() {
        return worldTime;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public Vec3 getCameraPosition() {
        return cameraPosition;
    }

    public Vec3 getRegionCenter() {
        return regionCenter;
    }

    public Vec3 getPreviousRegionCenter() {
        return previousRegionCenter;
    }

    public Vec3 getVelocity() {
        return velocity;
    }

    public float getRegionRadius() {
        return regionRadius;
    }

    public float getCloudBaseY() {
        return cloudBaseY;
    }

    public float getCloudTopY() {
        return cloudTopY;
    }

    public float getDensity() {
        return density;
    }

    public float getCoverage() {
        return coverage;
    }

    public float getEdgeSoftness() {
        return edgeSoftness;
    }

    public float getWindOffsetX() {
        return windOffsetX;
    }

    public float getWindOffsetZ() {
        return windOffsetZ;
    }

    public int getAgeTicks() {
        return ageTicks;
    }

    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    public float getGrowth() {
        return growth;
    }

    public float getDecay() {
        return decay;
    }

    public int getDebugColorOrTint() {
        return debugColorOrTint;
    }
}