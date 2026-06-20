package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Remplit les uniforms du shader de nuage avec les donnees live du snapshot.
 * Cette classe ne dessine rien et ne touche pas au cache debug.
 */
public final class CloudUniformUploader {

    private CloudUniformUploader() {

    }

    /**
     * Applique les uniforms principaux du shader de nuage.
     *
     * @param shader shader cible
     * @param frameContext contexte de frame courant
     * @param snapshot snapshot de rendu live
     */
    public static void apply(
            @NotNull ShaderInstance shader,
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull CloudRenderSnapshot snapshot,
            @NotNull RenderTarget outputTarget
    ) {
        shader.safeGetUniform("ModelViewMat").set(frameContext.getModelViewMatrix());
        shader.safeGetUniform("ProjMat").set(frameContext.getProjectionMatrix());
        Matrix4f inverseProjection = new Matrix4f(frameContext.getProjectionMatrix()).invert();
        Matrix4f inverseView = new Matrix4f(frameContext.getModelViewMatrix()).invert();
        shader.safeGetUniform("InverseProjectionMatrix").set(inverseProjection);
        shader.safeGetUniform("InverseViewMatrix").set(inverseView);

        Vec3 cameraPosition = frameContext.getCameraPosition();
        shader.safeGetUniform("CameraPosition").set(
                (float) cameraPosition.x(),
                (float) cameraPosition.y(),
                (float) cameraPosition.z()
        );

        Vec3 center = snapshot.getRegionCenter();
        Vec3 previousCenter = snapshot.getPreviousRegionCenter();
        Vec3 velocity = snapshot.getVelocity();

        shader.safeGetUniform("CloudCenter").set(
                (float) center.x(),
                (float) center.y(),
                (float) center.z()
        );
        shader.safeGetUniform("CloudPreviousCenter").set(
                (float) previousCenter.x(),
                (float) previousCenter.y(),
                (float) previousCenter.z()
        );
        shader.safeGetUniform("CloudVelocity").set(
                (float) velocity.x(),
                (float) velocity.y(),
                (float) velocity.z()
        );

        shader.safeGetUniform("CloudRadius").set(snapshot.getRegionRadius());
        shader.safeGetUniform("CloudBaseY").set(snapshot.getCloudBaseY());
        shader.safeGetUniform("CloudTopY").set(snapshot.getCloudTopY());
        CloudRenderAabb.Bounds bounds = CloudRenderAabb.compute(snapshot);
        if (bounds != null) {
            shader.safeGetUniform("CloudBoundsMin").set(
                    (float) bounds.min().x(),
                    (float) bounds.min().y(),
                    (float) bounds.min().z()
            );
            shader.safeGetUniform("CloudBoundsMax").set(
                    (float) bounds.max().x(),
                    (float) bounds.max().y(),
                    (float) bounds.max().z()
            );
        }
        CloudRenderAabb.logShaderUpload(frameContext, bounds);
        shader.safeGetUniform("CloudDensity").set(snapshot.getDensity());
        shader.safeGetUniform("CloudCoverage").set(snapshot.getCoverage());
        shader.safeGetUniform("CloudEdgeSoftness").set(snapshot.getEdgeSoftness());
        shader.safeGetUniform("CloudWorldTime").set((float) frameContext.getWorldTime());
        shader.safeGetUniform("CloudPartialTick").set(frameContext.getPartialTick());
        shader.safeGetUniform("CloudAgeTicks").set(snapshot.getAgeTicks());
        shader.safeGetUniform("CloudLifetimeTicks").set(snapshot.getLifetimeTicks());
        shader.safeGetUniform("CloudGrowth").set(snapshot.getGrowth());
        shader.safeGetUniform("CloudDecay").set(snapshot.getDecay());
        shader.safeGetUniform("CloudVerticalThickness").set(snapshot.getVerticalThickness());
        shader.safeGetUniform("CloudEdgeErosionStrength").set(snapshot.getEdgeErosionStrength());
        shader.safeGetUniform("CloudTopSoftness").set(snapshot.getTopSoftness());
        shader.safeGetUniform("CloudBaseSoftness").set(snapshot.getBaseSoftness());
        shader.safeGetUniform("CloudBaseDarkness").set(snapshot.getBaseDarkness());
        shader.safeGetUniform("CloudNoiseScale").set(snapshot.getNoiseScale());
        shader.safeGetUniform("CloudDetailNoiseScale").set(snapshot.getDetailNoiseScale());
        shader.safeGetUniform("CloudErosionNoiseScale").set(snapshot.getErosionNoiseScale());
        shader.safeGetUniform("CloudDensityMultiplier").set(snapshot.getDensityMultiplier());
        shader.safeGetUniform("CloudCoverageMultiplier").set(snapshot.getCoverageMultiplier());
        shader.safeGetUniform("CloudHeightSquash").set(snapshot.getHeightSquash());
        shader.safeGetUniform("CloudTowerStrength").set(snapshot.getTowerStrength());
        shader.safeGetUniform("CloudAnvilStrength").set(snapshot.getAnvilStrength());
        shader.safeGetUniform("CloudPrecipitationCoreStrength").set(snapshot.getPrecipitationCoreStrength());
        shader.safeGetUniform("CloudMaterialDarkness").set(snapshot.getMaterialProfile().getDarkness());
        shader.safeGetUniform("CloudMaterialPrecipitationTint").set(snapshot.getMaterialProfile().getPrecipitationTint());
        shader.safeGetUniform("CloudMaterialOpacityBias").set(snapshot.getMaterialProfile().getOpacityBias());
        shader.safeGetUniform("CloudMaterialUndersideDarkness").set(snapshot.getMaterialProfile().getUndersideDarkness());
        shader.safeGetUniform("CloudMaterialEdgeErosion").set(snapshot.getMaterialProfile().getEdgeErosion());
        shader.safeGetUniform("CloudStormCoreDarkening").set(snapshot.getMaterialProfile().getStormCoreDarkening());
        shader.safeGetUniform("CloudMaterialShadowContribution").set(snapshot.getMaterialProfile().getShadowContribution());
        shader.safeGetUniform("CloudMaterialLightningResponse").set(snapshot.getMaterialProfile().getLightningResponse());
        shader.safeGetUniform("CloudShapeLobeCounts").set(
                (float) snapshot.getShapeProfile().getLobeCountMin(),
                (float) snapshot.getShapeProfile().getLobeCountMax()
        );
        shader.safeGetUniform("CloudShapeBaseRadius").set(snapshot.getShapeProfile().getBaseRadius());
        shader.safeGetUniform("CloudShapeBaseOffset").set(snapshot.getShapeProfile().getBaseOffset());
        shader.safeGetUniform("CloudShapeTopOffset").set(snapshot.getShapeProfile().getTopOffset());
        shader.safeGetUniform("CloudShapeLobeStrength").set(snapshot.getShapeProfile().getLobeStrength());
        shader.safeGetUniform("CloudShapeVerticalTilt").set(snapshot.getShapeProfile().getVerticalTilt());
        shader.safeGetUniform("CloudShapeWindShearStrength").set(snapshot.getShapeProfile().getWindShearStrength());
        shader.safeGetUniform("CloudShapeCellSplitStrength").set(snapshot.getShapeProfile().getCellSplitStrength());
        shader.safeGetUniform("CloudShapeTowerNarrowing").set(snapshot.getShapeProfile().getTowerNarrowing());
        shader.safeGetUniform("CloudShapeAnvilSpread").set(snapshot.getShapeProfile().getAnvilSpread());
        shader.safeGetUniform("CloudShapeBaseFlattening").set(snapshot.getShapeProfile().getBaseFlattening());
        shader.safeGetUniform("CloudShapeEdgeRaggedness").set(snapshot.getShapeProfile().getEdgeRaggedness());
        shader.safeGetUniform("CloudShapeStormWallStrength").set(snapshot.getShapeProfile().getStormWallStrength());
        shader.safeGetUniform("CloudMorphologyFamily").set(snapshot.getMorphologyFamily().ordinal());
        shader.safeGetUniform("CloudStormVisualTier").set(snapshot.getStormVisualTier().ordinal());
        shader.safeGetUniform("CloudStormVisualDarkness").set(snapshot.getStormVisualTier().getDarkness());
        shader.safeGetUniform("CloudPrecipitationTier").set(snapshot.getPrecipitationTier().ordinal());
        shader.safeGetUniform("CloudPrecipitationIntensity").set(snapshot.getPrecipitationTier().getRepresentativeIntensity());
        shader.safeGetUniform("CloudShadowContribution").set(snapshot.getShadowContribution());
        shader.safeGetUniform("CloudLightningInfluence").set(snapshot.getLightningInfluence());

        float[] cloudColor = CloudLightingBridge.resolveCloudColor(snapshot, frameContext);
        shader.safeGetUniform("CloudColor").set(cloudColor[0], cloudColor[1], cloudColor[2], cloudColor[3]);
        Vector3f sunDirection = CloudLightingBridge.resolveSunDirection(frameContext);
        shader.safeGetUniform("SunDirection").set(sunDirection.x, sunDirection.y, sunDirection.z);

        Vector3f sunColor = CloudLightingBridge.resolveSunColor(snapshot, frameContext);
        shader.safeGetUniform("SunColor").set(sunColor.x, sunColor.y, sunColor.z);

        Vector3f ambientCloudColor = CloudLightingBridge.resolveAmbientCloudColor(snapshot, frameContext);
        shader.safeGetUniform("AmbientCloudColor").set(ambientCloudColor.x, ambientCloudColor.y, ambientCloudColor.z);
        shader.safeGetUniform("SunsetStrength").set(CloudLightingBridge.resolveSunsetStrength(frameContext));
        shader.safeGetUniform("HorizonGlowStrength").set(CloudLightingBridge.resolveHorizonGlowStrength(frameContext));
        shader.safeGetUniform("EdgeLightStrength").set(CloudLightingBridge.resolveEdgeLightStrength(frameContext));
        shader.safeGetUniform("UndersideDarkening").set(CloudLightingBridge.resolveUndersideDarkening(frameContext));
        shader.safeGetUniform("LightAbsorption").set(CloudLightingBridge.resolveLightAbsorption(snapshot, frameContext));

        float[] fogColor = CloudLightingBridge.resolveFogColor();
        shader.safeGetUniform("FogColor").set(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);

        float maxDistance = frameContext.getRenderProfile().getMaxRenderDistance();
        shader.safeGetUniform("FogStart").set(maxDistance * 0.55F);
        shader.safeGetUniform("FogEnd").set(maxDistance);
        shader.safeGetUniform("MaxDistance").set(maxDistance);

        shader.safeGetUniform("OutSize").set((float) outputTarget.width, (float) outputTarget.height);

        shader.safeGetUniform("AnimationTime").set((frameContext.getWorldTime() + frameContext.getPartialTick()) * 0.05F);
        shader.safeGetUniform("RaymarchSteps").set(Math.max(1, frameContext.getRenderProfile().getRaymarchSteps()));
        shader.safeGetUniform("RayJitterFrame").set((float) (frameContext.getWorldTime() & 1023L) + frameContext.getPartialTick());
        shader.safeGetUniform("RayJitterStrength").set(frameContext.getRenderProfile().getRayJitterStrength());
        shader.safeGetUniform("RayJitterTemporalStrength").set(frameContext.getRenderProfile().getRayJitterTemporalStrength());
        shader.safeGetUniform("CloudSeed").set(snapshot.getCloudSeed());
    }
}
