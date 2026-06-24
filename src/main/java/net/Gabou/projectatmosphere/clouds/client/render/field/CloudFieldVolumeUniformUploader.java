package net.Gabou.projectatmosphere.clouds.client.render.field;

import net.Gabou.projectatmosphere.clouds.field.CloudFieldRendererInput;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Uploads one synced CloudField snapshot to the prototype volume shader.
 */
public final class CloudFieldVolumeUniformUploader {
    private CloudFieldVolumeUniformUploader() {
    }

    /**
     * Applies per-frame and per-field uniforms for a single CloudField draw.
     *
     * @param shader target shader
     * @param input renderer input produced by ClientCloudFieldCache
     * @param snapshot field snapshot to render
     * @param bounds world-space AABB bounds used by the volume mesh
     * @param modelViewMat current model-view matrix
     * @param projectionMat current projection matrix
     */
    public static void apply(
            ShaderInstance shader,
            CloudFieldRendererInput input,
            CloudFieldSnapshot snapshot,
            CloudFieldVolumeRenderer.Bounds bounds,
            Matrix4f modelViewMat,
            Matrix4f projectionMat
    ) {
        shader.safeGetUniform("ModelViewMat").set(modelViewMat);
        shader.safeGetUniform("ProjMat").set(projectionMat);

        Vec3 min = bounds.min();
        Vec3 max = bounds.max();
        shader.safeGetUniform("VolumeMin").set((float) min.x(), (float) min.y(), (float) min.z());
        shader.safeGetUniform("VolumeMax").set((float) max.x(), (float) max.y(), (float) max.z());

        Vec3 camera = input.cameraPosition();
        shader.safeGetUniform("CameraPos").set((float) camera.x(), (float) camera.y(), (float) camera.z());

        Vec3 center = snapshot.center();
        shader.safeGetUniform("FieldCenter").set((float) center.x(), (float) center.y(), (float) center.z());
        shader.safeGetUniform("FieldRadius").set(snapshot.radius());
        shader.safeGetUniform("FieldBaseY").set(snapshot.baseY());
        shader.safeGetUniform("FieldTopY").set(snapshot.topY());
        shader.safeGetUniform("FieldDensity").set(snapshot.density());
        shader.safeGetUniform("FieldCoverage").set(snapshot.coverage());
        shader.safeGetUniform("FieldHydration").set(snapshot.hydrationProgress());
        shader.safeGetUniform("FieldGrowth").set(snapshot.growth());
        shader.safeGetUniform("FieldDecay").set(snapshot.decay());
        shader.safeGetUniform("FieldHumidityInfluence").set(snapshot.humidityInfluence());
        Vec3 wind = snapshot.windVector();
        shader.safeGetUniform("FieldWind").set((float) wind.x(), (float) wind.y(), (float) wind.z());
        shader.safeGetUniform("FieldVerticalDevelopment").set(snapshot.verticalDevelopment());
        shader.safeGetUniform("FieldStormPotential").set(snapshot.stormPotential());
        shader.safeGetUniform("FieldAgeTicks").set((float) Math.min(snapshot.fieldAgeTicks(), 16_777_216L));
        shader.safeGetUniform("FieldLifetimeTicks").set((float) Math.min(snapshot.lifetimeTicks(), 16_777_216L));
        shader.safeGetUniform("FieldSeed").set((float) (snapshot.seed() & 0xFFFFL));
        shader.safeGetUniform("FieldCloudletCount").set((float) snapshot.activeCloudletCount());

        shader.safeGetUniform("FieldSourceKind").set(snapshot.sourceKind().shaderId());
        shader.safeGetUniform("AnimationTime").set((input.worldTime() + input.partialTick()) * 0.05F);
        shader.safeGetUniform("DebugMode").set(CloudFieldVolumeRenderConfig.mode().shaderId());
        shader.safeGetUniform("TuneOpacityStrength").set(CloudFieldVolumeRenderConfig.opacityStrength());
        shader.safeGetUniform("TuneDensityThreshold").set(CloudFieldVolumeRenderConfig.densityThreshold());
        shader.safeGetUniform("TuneMaxFinalAlpha").set(CloudFieldVolumeRenderConfig.maxFinalAlpha());
        shader.safeGetUniform("TuneNoiseStrength").set(CloudFieldVolumeRenderConfig.noiseStrength());
        shader.safeGetUniform("TuneErosionStrength").set(CloudFieldVolumeRenderConfig.erosionStrength());
        shader.safeGetUniform("TuneBrightness").set(CloudFieldVolumeRenderConfig.brightness());
        shader.safeGetUniform("TuneUndersideDarkening").set(CloudFieldVolumeRenderConfig.undersideDarkening());
        shader.safeGetUniform("TuneDensityBoost").set(CloudFieldVolumeRenderConfig.densityBoost());
        shader.safeGetUniform("TuneAnimSpeed").set(CloudFieldVolumeRenderConfig.animSpeed());
    }
}
