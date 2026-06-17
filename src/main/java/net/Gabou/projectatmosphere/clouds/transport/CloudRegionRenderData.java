package net.Gabou.projectatmosphere.clouds.transport;

import net.Gabou.projectatmosphere.clouds.type.CloudMaterialProfile;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import net.Gabou.projectatmosphere.modules.weather.PrecipitationTier;
import net.Gabou.projectatmosphere.modules.weather.StormVisualTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Donnée de rendu transportable pour une région de nuage PA.
 * Cette classe ne possède pas la simulation et ne fait aucun rendu.
 */
public final class CloudRegionRenderData {

    private final UUID regionId;
    private final UUID clusterId;
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
    private final long simulationTick;
    private final boolean active;
    private final int debugColorOrTint;
    private final int ageTicks;
    private final int lifetimeTicks;
    private final float growth;
    private final float decay;
    private final String cloudTypeId;
    private final String previousCloudTypeId;
    private final CloudMorphologyFamily morphologyFamily;
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
    private final float mergePressure;
    private final CloudMaterialProfile materialProfile;
    private final CloudShapeProfile shapeProfile;
    private final StormVisualTier stormVisualTier;
    private final PrecipitationTier precipitationTier;
    private final float shadowContribution;
    private final float lightningInfluence;

    public CloudRegionRenderData(
            UUID regionId,
            UUID clusterId,
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
            long simulationTick,
            boolean active,
            int debugColorOrTint,
            int ageTicks,
            int lifetimeTicks,
            float growth,
            float decay,
            String cloudTypeId,
            String previousCloudTypeId,
            CloudMorphologyFamily morphologyFamily,
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
            float mergePressure,
            CloudMaterialProfile materialProfile,
            CloudShapeProfile shapeProfile,
            StormVisualTier stormVisualTier,
            PrecipitationTier precipitationTier,
            float shadowContribution,
            float lightningInfluence
    ) {
        this.regionId = regionId;
        this.clusterId = clusterId;
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
        this.simulationTick = Math.max(0L, simulationTick);
        this.active = active;
        this.debugColorOrTint = debugColorOrTint;
        this.ageTicks = ageTicks;
        this.lifetimeTicks = lifetimeTicks;
        this.growth = growth;
        this.decay = decay;
        this.cloudTypeId = cloudTypeId;
        this.previousCloudTypeId = previousCloudTypeId;
        this.morphologyFamily = morphologyFamily == null ? CloudMorphologyFamily.PUFF : morphologyFamily;
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
        this.mergePressure = mergePressure;
        this.materialProfile = materialProfile == null ? CloudMaterialProfile.DEFAULT : materialProfile;
        this.shapeProfile = shapeProfile == null ? CloudShapeProfile.DEFAULT : shapeProfile;
        this.stormVisualTier = stormVisualTier == null ? StormVisualTier.CLEAR : stormVisualTier;
        this.precipitationTier = precipitationTier == null ? PrecipitationTier.NONE : precipitationTier;
        this.shadowContribution = clamp01(shadowContribution);
        this.lightningInfluence = clamp01(lightningInfluence);
    }

    public UUID getRegionId() {
        return regionId;
    }

    public UUID getClusterId() {
        return clusterId;
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

    public long getSimulationTick() {
        return simulationTick;
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

    public CloudMorphologyFamily getMorphologyFamily() {
        return morphologyFamily;
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

    public float getMergePressure() {
        return mergePressure;
    }

    public CloudMaterialProfile getMaterialProfile() {
        return materialProfile;
    }

    public CloudShapeProfile getShapeProfile() {
        return shapeProfile;
    }

    public StormVisualTier getStormVisualTier() {
        return stormVisualTier;
    }

    public PrecipitationTier getPrecipitationTier() {
        return precipitationTier;
    }

    public float getShadowContribution() {
        return shadowContribution;
    }

    public float getLightningInfluence() {
        return lightningInfluence;
    }

    /**
     * Écrit cette donnée transportable dans un buffer réseau.
     *
     * @param buffer buffer réseau cible
     */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(regionId);
        buffer.writeUUID(clusterId);
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
        buffer.writeLong(simulationTick);
        buffer.writeBoolean(active);
        buffer.writeInt(debugColorOrTint);
        buffer.writeVarInt(ageTicks);
        buffer.writeVarInt(lifetimeTicks);
        buffer.writeFloat(growth);
        buffer.writeFloat(decay);
        buffer.writeUtf(cloudTypeId);
        buffer.writeUtf(previousCloudTypeId);
        buffer.writeEnum(morphologyFamily);
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
        buffer.writeInt(cloudSeed);
        buffer.writeFloat(mergePressure);
        encodeMaterialProfile(buffer, materialProfile);
        encodeShapeProfile(buffer, shapeProfile);
        buffer.writeEnum(stormVisualTier);
        buffer.writeEnum(precipitationTier);
        buffer.writeFloat(shadowContribution);
        buffer.writeFloat(lightningInfluence);
    }

    /**
     * Lit une donnée transportable depuis un buffer réseau.
     *
     * @param buffer buffer réseau source
     * @return donnée de région de nuage décodée
     */
    public static CloudRegionRenderData decode(FriendlyByteBuf buffer) {
        UUID regionId = buffer.readUUID();
        UUID clusterId = buffer.readUUID();
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
        long simulationTick = buffer.readLong();
        boolean active = buffer.readBoolean();
        int debugColorOrTint = buffer.readInt();
        int ageTicks = buffer.readVarInt();
        int lifetimeTicks = buffer.readVarInt();
        float growth = buffer.readFloat();
        float decay = buffer.readFloat();
        String cloudTypeId = buffer.readUtf();
        String previousCloudTypeId = buffer.readUtf();
        CloudMorphologyFamily morphologyFamily = buffer.readEnum(CloudMorphologyFamily.class);
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
        int cloudSeed = buffer.readInt();
        float mergePressure = buffer.readFloat();
        CloudMaterialProfile materialProfile = decodeMaterialProfile(buffer);
        CloudShapeProfile shapeProfile = decodeShapeProfile(buffer);
        StormVisualTier stormVisualTier = buffer.readEnum(StormVisualTier.class);
        PrecipitationTier precipitationTier = buffer.readEnum(PrecipitationTier.class);
        float shadowContribution = buffer.readFloat();
        float lightningInfluence = buffer.readFloat();

        return new CloudRegionRenderData(
                regionId,
                clusterId,
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
                simulationTick,
                active,
                debugColorOrTint,
                ageTicks,
                lifetimeTicks,
                growth,
                decay,
                cloudTypeId,
                previousCloudTypeId,
                morphologyFamily,
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
                precipitationCoreStrength,
                cloudSeed,
                mergePressure,
                materialProfile,
                shapeProfile,
                stormVisualTier,
                precipitationTier,
                shadowContribution,
                lightningInfluence
        );
    }

    private static void encodeMaterialProfile(FriendlyByteBuf buffer, CloudMaterialProfile profile) {
        CloudMaterialProfile safeProfile = profile == null ? CloudMaterialProfile.DEFAULT : profile;
        buffer.writeUtf(safeProfile.getMaterialId());
        buffer.writeUtf(safeProfile.getTextureId());
        buffer.writeFloat(safeProfile.getDarkness());
        buffer.writeFloat(safeProfile.getPrecipitationTint());
        buffer.writeFloat(safeProfile.getOpacityBias());
        buffer.writeFloat(safeProfile.getUndersideDarkness());
        buffer.writeFloat(safeProfile.getEdgeErosion());
        buffer.writeFloat(safeProfile.getStormCoreDarkening());
        buffer.writeFloat(safeProfile.getShadowContribution());
        buffer.writeFloat(safeProfile.getLightningResponse());
    }

    private static CloudMaterialProfile decodeMaterialProfile(FriendlyByteBuf buffer) {
        return new CloudMaterialProfile(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }

    private static void encodeShapeProfile(FriendlyByteBuf buffer, CloudShapeProfile profile) {
        CloudShapeProfile safeProfile = profile == null ? CloudShapeProfile.DEFAULT : profile;
        buffer.writeUtf(safeProfile.getShapeId());
        buffer.writeFloat(safeProfile.getBaseRadius());
        buffer.writeFloat(safeProfile.getBaseOffset());
        buffer.writeFloat(safeProfile.getTopOffset());
        buffer.writeVarInt(safeProfile.getLobeCountMin());
        buffer.writeVarInt(safeProfile.getLobeCountMax());
        buffer.writeFloat(safeProfile.getLobeStrength());
        buffer.writeFloat(safeProfile.getVerticalTilt());
        buffer.writeFloat(safeProfile.getWindShearStrength());
        buffer.writeFloat(safeProfile.getCellSplitStrength());
        buffer.writeFloat(safeProfile.getTowerNarrowing());
        buffer.writeFloat(safeProfile.getAnvilSpread());
        buffer.writeFloat(safeProfile.getBaseFlattening());
        buffer.writeFloat(safeProfile.getEdgeRaggedness());
        buffer.writeFloat(safeProfile.getStormWallStrength());
    }

    private static CloudShapeProfile decodeShapeProfile(FriendlyByteBuf buffer) {
        return new CloudShapeProfile(
                buffer.readUtf(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }
}
