package net.Gabou.projectatmosphere.clouds.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Donnée de rendu transportable pour une région de nuage PA.
 * Cette classe ne possède pas la simulation et ne fait aucun rendu.
 */
public final class CloudRegionRenderData {

    private final UUID regionId;
    private final String dimensionId;
    private final Vec3 center;
    private final Vec3 previousCenter;
    private final Vec3 velocity;
    private final float radius;
    private final float baseY;
    private final float topY;
    private final float density;
    private final float coverage;
    private final float edgeSoftness;
    private final boolean active;
    private final int debugColorOrTint;
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

    public CloudRegionRenderData(
            UUID regionId,
            String dimensionId,
            Vec3 center,
            Vec3 previousCenter,
            Vec3 velocity,
            float radius,
            float baseY,
            float topY,
            float density,
            float coverage,
            float edgeSoftness,
            boolean active,
            int debugColorOrTint,
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
            float precipitationCoreStrength
    ) {
        this.regionId = regionId;
        this.dimensionId = dimensionId;
        this.center = center;
        this.previousCenter = previousCenter;
        this.velocity = velocity;
        this.radius = radius;
        this.baseY = baseY;
        this.topY = topY;
        this.density = density;
        this.coverage = coverage;
        this.edgeSoftness = edgeSoftness;
        this.active = active;
        this.debugColorOrTint = debugColorOrTint;
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
    }

    public UUID getRegionId() {
        return regionId;
    }

    public String getDimensionId() {
        return dimensionId;
    }

    public Vec3 getCenter() {
        return center;
    }

    public Vec3 getPreviousCenter() {
        return previousCenter;
    }

    public Vec3 getVelocity() {
        return velocity;
    }

    public float getRadius() {
        return radius;
    }

    public float getBaseY() {
        return baseY;
    }

    public float getTopY() {
        return topY;
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

    public boolean isActive() {
        return active;
    }

    public int getDebugColorOrTint() {
        return debugColorOrTint;
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

    /**
     * Écrit cette donnée transportable dans un buffer réseau.
     *
     * @param buffer buffer réseau cible
     */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(regionId);
        buffer.writeUtf(dimensionId);
        buffer.writeDouble(center.x());
        buffer.writeDouble(center.y());
        buffer.writeDouble(center.z());
        buffer.writeDouble(previousCenter.x());
        buffer.writeDouble(previousCenter.y());
        buffer.writeDouble(previousCenter.z());
        buffer.writeDouble(velocity.x());
        buffer.writeDouble(velocity.y());
        buffer.writeDouble(velocity.z());
        buffer.writeFloat(radius);
        buffer.writeFloat(baseY);
        buffer.writeFloat(topY);
        buffer.writeFloat(density);
        buffer.writeFloat(coverage);
        buffer.writeFloat(edgeSoftness);
        buffer.writeBoolean(active);
        buffer.writeInt(debugColorOrTint);
        buffer.writeVarInt(ageTicks);
        buffer.writeVarInt(lifetimeTicks);
        buffer.writeFloat(growth);
        buffer.writeFloat(decay);
        buffer.writeUtf(cloudTypeId);
        buffer.writeUtf(previousCloudTypeId);
        buffer.writeVarInt(cloudTypeTicks);
        buffer.writeFloat(verticalThickness);
        buffer.writeFloat(edgeErosionStrength);
        buffer.writeFloat(topSoftness);
        buffer.writeFloat(baseSoftness);
        buffer.writeFloat(baseDarkness);
        buffer.writeFloat(noiseScale);
        buffer.writeFloat(detailNoiseScale);
        buffer.writeFloat(erosionNoiseScale);
        buffer.writeFloat(densityMultiplier);
        buffer.writeFloat(coverageMultiplier);
        buffer.writeFloat(heightSquash);
        buffer.writeFloat(towerStrength);
        buffer.writeFloat(anvilStrength);
        buffer.writeFloat(precipitationCoreStrength);
    }

    /**
     * Lit une donnée transportable depuis un buffer réseau.
     *
     * @param buffer buffer réseau source
     * @return donnée de région de nuage décodée
     */
    public static CloudRegionRenderData decode(FriendlyByteBuf buffer) {
        UUID regionId = buffer.readUUID();
        String dimensionId = buffer.readUtf();
        Vec3 center = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        Vec3 previousCenter = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        Vec3 velocity = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        float radius = buffer.readFloat();
        float baseY = buffer.readFloat();
        float topY = buffer.readFloat();
        float density = buffer.readFloat();
        float coverage = buffer.readFloat();
        float edgeSoftness = buffer.readFloat();
        boolean active = buffer.readBoolean();
        int debugColorOrTint = buffer.readInt();
        int ageTicks = buffer.readVarInt();
        int lifetimeTicks = buffer.readVarInt();
        float growth = buffer.readFloat();
        float decay = buffer.readFloat();
        String cloudTypeId = buffer.readUtf();
        String previousCloudTypeId = buffer.readUtf();
        int cloudTypeTicks = buffer.readVarInt();
        float verticalThickness = buffer.readFloat();
        float edgeErosionStrength = buffer.readFloat();
        float topSoftness = buffer.readFloat();
        float baseSoftness = buffer.readFloat();
        float baseDarkness = buffer.readFloat();
        float noiseScale = buffer.readFloat();
        float detailNoiseScale = buffer.readFloat();
        float erosionNoiseScale = buffer.readFloat();
        float densityMultiplier = buffer.readFloat();
        float coverageMultiplier = buffer.readFloat();
        float heightSquash = buffer.readFloat();
        float towerStrength = buffer.readFloat();
        float anvilStrength = buffer.readFloat();
        float precipitationCoreStrength = buffer.readFloat();

        return new CloudRegionRenderData(
                regionId,
                dimensionId,
                center,
                previousCenter,
                velocity,
                radius,
                baseY,
                topY,
                density,
                coverage,
                edgeSoftness,
                active,
                debugColorOrTint,
                ageTicks,
                lifetimeTicks,
                growth,
                decay,
                cloudTypeId,
                previousCloudTypeId,
                cloudTypeTicks,
                verticalThickness,
                edgeErosionStrength,
                topSoftness,
                baseSoftness,
                baseDarkness,
                noiseScale,
                detailNoiseScale,
                erosionNoiseScale,
                densityMultiplier,
                coverageMultiplier,
                heightSquash,
                towerStrength,
                anvilStrength,
                precipitationCoreStrength
        );
    }
}
