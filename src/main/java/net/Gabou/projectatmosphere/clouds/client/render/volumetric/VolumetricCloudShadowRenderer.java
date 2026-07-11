package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.client.render.shader.VolumetricCloudShaders;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowMapAccess;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowSnapshot;
import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.cell.CloudCellDensityMath;
import net.Gabou.projectatmosphere.clouds.client.render.depth.SceneDepthFrame;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * GPU cloud shadow pipeline: renders the weather map into a top-down
 * transmittance texture and multiplies the scene under it. The GPU texture is
 * also published through {@link CloudShadowMapAccess} so shader packs and
 * gameplay consumers can read real cloud coverage. A CPU 64x64 grid is kept
 * for gameplay queries; it is derived analytically from the cells, never read
 * back from the GPU (avoids the historical NVidia stall/crash).
 */
public final class VolumetricCloudShadowRenderer {
    public static final int SHADOW_MAP_SIZE = 512;
    public static final float SHADOW_EXTENT = 4096.0F;
    private static final int CPU_GRID_SIZE = 64;

    private static double lastOriginX;
    private static double lastOriginZ;
    private static long frameCounter;
    private static long lastCpuPublishFrame = -100L;

    private VolumetricCloudShadowRenderer() {
    }

    /**
     * Regenerates the shadow transmittance map (call at the profile's update
     * interval, not necessarily per frame).
     */
    public static boolean renderShadowMap(
            CloudWeatherMapRenderer.Result weather,
            RenderTarget weatherTarget,
            Vector3f lightDirection,
            double cameraX,
            double cameraZ,
            float groundY,
            float densityMul
    ) {
        ShaderInstance shader = VolumetricCloudShaders.shadowMapShader();
        if (shader == null || weatherTarget == null || !weather.rendered()) {
            return false;
        }
        RenderTarget target = VolumetricCloudRenderTargets.prepareShadowTarget(SHADOW_MAP_SIZE);
        if (target == null) {
            return false;
        }

        float texel = SHADOW_EXTENT / SHADOW_MAP_SIZE;
        lastOriginX = Math.floor((cameraX - SHADOW_EXTENT * 0.5D) / texel) * texel;
        lastOriginZ = Math.floor((cameraZ - SHADOW_EXTENT * 0.5D) / texel) * texel;

        VolumetricCloudRenderTargets.clearAndBind(target);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        shader.setSampler("WeatherMapSampler", weatherTarget.getColorTextureId());
        shader.safeGetUniform("ShadowOrigin").set((float) lastOriginX, (float) lastOriginZ);
        shader.safeGetUniform("ShadowExtent").set(SHADOW_EXTENT);
        shader.safeGetUniform("WeatherOrigin").set((float) weather.originX(), (float) weather.originZ());
        shader.safeGetUniform("WeatherExtent").set(CloudWeatherMapRenderer.WEATHER_EXTENT);
        shader.safeGetUniform("SlabBaseY").set(weather.slabBaseY());
        shader.safeGetUniform("SlabTopY").set(weather.slabTopY());
        shader.safeGetUniform("GroundY").set(groundY);
        shader.safeGetUniform("LightDir").set(lightDirection.x, lightDirection.y, lightDirection.z);
        shader.safeGetUniform("DensityMul").set(densityMul);
        shader.apply();
        try {
            FullscreenQuad.draw(shader);
        } finally {
            shader.clear();
        }
        frameCounter++;
        return true;
    }

    /**
     * Multiplies the main framebuffer by the shadow transmittance where the
     * depth buffer has geometry. Must run after terrain, before clouds.
     */
    public static void applyGroundShadows(
            RenderTarget mainTarget,
            SceneDepthFrame sceneDepth,
            Matrix4f invProj,
            Matrix4f invViewRot,
            Vector3f cameraPos,
            Vector3f lightDirection,
            float slabBaseY,
            float groundY,
            float shadowStrength,
            float daylightFactor
    ) {
        ShaderInstance shader = VolumetricCloudShaders.shadowApplyShader();
        RenderTarget shadowTarget = VolumetricCloudRenderTargets.shadowTargetOrNull();
        if (shader == null || shadowTarget == null || mainTarget == null
                || sceneDepth == null || !sceneDepth.valid() || !sceneDepth.detached()
                || daylightFactor <= 0.02F) {
            return;
        }

        if (!net.Gabou.projectatmosphere.clouds.client.render.CloudRenderStateGuard.bindCapturedDrawFramebuffer()) {
            mainTarget.bindWrite(true);
        }
        RenderSystem.enableBlend();
        // Multiply blend is applied by the shader JSON (dstcolor, 0).
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        shader.setSampler("ShadowMapSampler", shadowTarget.getColorTextureId());
        shader.setSampler("SceneDepthSampler", sceneDepth.textureId());
        shader.safeGetUniform("InvProjMat").set(invProj);
        shader.safeGetUniform("InvViewRotMat").set(invViewRot);
        shader.safeGetUniform("CameraPos").set(cameraPos.x, cameraPos.y, cameraPos.z);
        shader.safeGetUniform("ShadowOrigin").set((float) lastOriginX, (float) lastOriginZ);
        shader.safeGetUniform("ShadowExtent").set(SHADOW_EXTENT);
        shader.safeGetUniform("SlabBaseY").set(slabBaseY);
        shader.safeGetUniform("GroundY").set(groundY);
        shader.safeGetUniform("LightDir").set(lightDirection.x, lightDirection.y, lightDirection.z);
        shader.safeGetUniform("ShadowStrength").set(shadowStrength);
        shader.safeGetUniform("DaylightFactor").set(daylightFactor);
        shader.apply();
        try {
            FullscreenQuad.draw(shader);
        } finally {
            shader.clear();
        }
    }

    /**
     * Publishes the shadow state through the public API: GPU texture handle
     * for shader packs plus an analytically derived CPU grid for gameplay.
     */
    public static void publishSnapshot(
            List<CloudCell> cells,
            Vector3f lightDirection,
            double cameraX,
            double cameraZ,
            float slabBaseY,
            float groundY
    ) {
        if (frameCounter - lastCpuPublishFrame < 4L) {
            return;
        }
        lastCpuPublishFrame = frameCounter;

        RenderTarget shadowTarget = VolumetricCloudRenderTargets.shadowTargetOrNull();
        int textureId = shadowTarget == null ? -1 : shadowTarget.getColorTextureId();

        float minX = (float) lastOriginX;
        float minZ = (float) lastOriginZ;
        float maxX = minX + SHADOW_EXTENT;
        float maxZ = minZ + SHADOW_EXTENT;

        float sunY = Math.max(lightDirection.y, 0.08F);
        float midCloudY = slabBaseY + 40.0F;
        float offsetX = lightDirection.x / sunY * (midCloudY - groundY);
        float offsetZ = lightDirection.z / sunY * (midCloudY - groundY);

        float[] values = new float[CPU_GRID_SIZE * CPU_GRID_SIZE];
        if (cells != null && !cells.isEmpty()) {
            float cellSize = SHADOW_EXTENT / CPU_GRID_SIZE;
            for (int gz = 0; gz < CPU_GRID_SIZE; gz++) {
                for (int gx = 0; gx < CPU_GRID_SIZE; gx++) {
                    double worldX = minX + (gx + 0.5F) * cellSize + offsetX;
                    double worldZ = minZ + (gz + 0.5F) * cellSize + offsetZ;
                    float occlusion = 0.0F;
                    for (CloudCell cell : cells) {
                        float footprint = CloudCellDensityMath.footprint01(cell, worldX, worldZ);
                        float weight = footprint * cell.density()
                                * Mth.clamp(cell.verticalExtent() / 90.0F, 0.15F, 1.0F);
                        occlusion = 1.0F - (1.0F - occlusion) * (1.0F - weight);
                    }
                    values[gz * CPU_GRID_SIZE + gx] = Mth.clamp(occlusion, 0.0F, 1.0F);
                }
            }
        }

        Matrix4f worldToShadow = new Matrix4f()
                .scale(1.0F / SHADOW_EXTENT, 1.0F, 1.0F / SHADOW_EXTENT)
                .translate(-minX, 0.0F, -minZ);

        CloudShadowMapAccess.publishSnapshot(new CloudShadowSnapshot(
                true,
                textureId,
                CPU_GRID_SIZE,
                CPU_GRID_SIZE,
                minX,
                minZ,
                maxX,
                maxZ,
                worldToShadow,
                frameCounter,
                values
        ));
    }

    public static double lastOriginX() {
        return lastOriginX;
    }

    public static double lastOriginZ() {
        return lastOriginZ;
    }
}
