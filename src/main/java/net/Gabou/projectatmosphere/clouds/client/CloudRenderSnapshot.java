package net.Gabou.projectatmosphere.clouds.client;

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
    private final String cloudTypeId;
    private final String previousCloudTypeId;
    private final int cloudTypeTicks;
    private final float verticalThickness;
    private final float edgeErosionStrength;
    private final float topSoftness;
    private final float baseSoftness;
    private final float baseDarkness;
    private final float noiseScale;
    private final float detailNoiseScale;
    private final float erosionNoiseScale;
    private final float densityMultiplier;
    private final float coverageMultiplier;
    private final float heightSquash;
    private final float towerStrength;
    private final float anvilStrength;
    private final float precipitationCoreStrength;
    private final int cloudSeed;
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
            String cloudTypeId,
            String previousCloudTypeId,
            int cloudTypeTicks,
            float verticalThickness,
            float edgeErosionStrength,
            float topSoftness,
            float baseSoftness,
            float baseDarkness,
            float noiseScale,
            float detailNoiseScale,
            float erosionNoiseScale,
            float densityMultiplier,
            float coverageMultiplier,
            float heightSquash,
            float towerStrength,
            float anvilStrength,
            float precipitationCoreStrength,
            int cloudSeed,
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
        this.cloudTypeId = cloudTypeId;
        this.previousCloudTypeId = previousCloudTypeId;
        this.cloudTypeTicks = cloudTypeTicks;
        this.verticalThickness = verticalThickness;
        this.edgeErosionStrength = edgeErosionStrength;
        this.topSoftness = topSoftness;
        this.baseSoftness = baseSoftness;
        this.baseDarkness = baseDarkness;
        this.noiseScale = noiseScale;
        this.detailNoiseScale = detailNoiseScale;
        this.erosionNoiseScale = erosionNoiseScale;
        this.densityMultiplier = densityMultiplier;
        this.coverageMultiplier = coverageMultiplier;
        this.heightSquash = heightSquash;
        this.towerStrength = towerStrength;
        this.anvilStrength = anvilStrength;
        this.precipitationCoreStrength = precipitationCoreStrength;
        this.cloudSeed = cloudSeed;
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

    public String getCloudTypeId() {
        return cloudTypeId;
    }

    public String getPreviousCloudTypeId() {
        return previousCloudTypeId;
    }

    public int getCloudTypeTicks() {
        return cloudTypeTicks;
    }

    public float getVerticalThickness() {
        return verticalThickness;
    }

    public float getEdgeErosionStrength() {
        return edgeErosionStrength;
    }

    public float getTopSoftness() {
        return topSoftness;
    }

    public float getBaseSoftness() {
        return baseSoftness;
    }

    public float getBaseDarkness() {
        return baseDarkness;
    }

    public float getNoiseScale() {
        return noiseScale;
    }

    public float getDetailNoiseScale() {
        return detailNoiseScale;
    }

    public float getErosionNoiseScale() {
        return erosionNoiseScale;
    }

    public float getDensityMultiplier() {
        return densityMultiplier;
    }

    public float getCoverageMultiplier() {
        return coverageMultiplier;
    }

    public float getHeightSquash() {
        return heightSquash;
    }

    public float getTowerStrength() {
        return towerStrength;
    }

    public float getAnvilStrength() {
        return anvilStrength;
    }

    public float getPrecipitationCoreStrength() {
        return precipitationCoreStrength;
    }

    public int getCloudSeed() {
        return cloudSeed;
    }

    public int getDebugColorOrTint() {
        return debugColorOrTint;
    }
}
