package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.client.render.shader.CloudShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Dessine les nuages live avec le shader dedie.
 * Cette classe ne lit jamais le cache debug et ne manipule pas le backend.
 */
public final class CloudRaymarchRenderer {

    private static final float HISTORY_RESET_CAMERA_DISTANCE = 1.25F;
    private static final float HISTORY_FADE_CAMERA_DISTANCE = 0.20F;
    private static final float HISTORY_RESET_MATRIX_DELTA = 0.055F;
    private static final float HISTORY_FADE_MATRIX_DELTA = 0.012F;
    private static VertexBuffer fullscreenQuad;
    private static Vec3 previousCameraPosition;
    private static Matrix4f previousModelViewMatrix;
    private static Matrix4f previousProjectionMatrix;

    private CloudRaymarchRenderer() {

    }

    public static void resetTemporalState() {
        previousCameraPosition = null;
        previousModelViewMatrix = null;
        previousProjectionMatrix = null;
    }

    /**
     * Rend un snapshot live avec le shader dedie.
     *
     * @param frameContext contexte de frame courant
     * @param snapshot snapshot live valide
     */
    public static boolean renderSnapshot(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull CloudRenderSnapshot snapshot,
            @NotNull RenderTarget outputTarget,
            int sceneDepthTextureId,
            @NotNull CloudGpuTimer gpuTimer
    ) {
        ShaderInstance shader = CloudShaders.getShader();
        if (shader == null || !snapshot.isEnabled() || !CloudDensityProvider.hasVisibleDensity(snapshot)) {
            return false;
        }

        ensureFullscreenQuad();
        if (fullscreenQuad == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean writeDepth = outputTarget != minecraft.getMainRenderTarget();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (writeDepth) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(writeDepth);
        RenderSystem.setShader(() -> shader);

        CloudRenderDebugMode debugMode = CloudRenderDebugMode.current();
        CloudRenderDiagnostics.recordShaderDebugMode(debugMode.isActive());
        shader.safeGetUniform("WriteDepth").set(writeDepth ? 1 : 0);
        shader.safeGetUniform("CloudDebugMode").set(debugMode.id());
        shader.setSampler("DepthSampler", sceneDepthTextureId);
        CloudUniformUploader.apply(shader, frameContext, snapshot, outputTarget);
        shader.apply();

        gpuTimer.begin();
        try {
            fullscreenQuad.bind();
            fullscreenQuad.drawWithShader(
                    frameContext.getModelViewMatrix(),
                    frameContext.getProjectionMatrix(),
                    shader
            );
            VertexBuffer.unbind();
        } finally {
            gpuTimer.end();
        }
        shader.clear();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        return true;
    }

    public static boolean resolveTemporalTarget(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull RenderTarget sourceTarget,
            @Nullable RenderTarget historyReadTarget,
            @NotNull RenderTarget historyWriteTarget,
            int sceneDepthTextureId,
            boolean historyValid
    ) {
        float historyWeight = resolveHistoryWeight(frameContext, sourceTarget, historyValid);
        boolean useHistory = historyReadTarget != null && historyValid && historyWeight > 0.001F;
        return drawCompositePass(
                sourceTarget,
                sourceTarget,
                useHistory ? historyReadTarget : null,
                historyWriteTarget,
                sceneDepthTextureId,
                frameContext.getRenderProfile().getCompositeBlurRadius(),
                frameContext.getRenderProfile().getCompositeBlurStrength(),
                historyWeight,
                useHistory,
                false
        );
    }

    public static boolean compositeTarget(
            @NotNull RenderTarget sourceTarget,
            @NotNull RenderTarget depthTarget,
            @NotNull RenderTarget destinationTarget,
            int sceneDepthTextureId
    ) {
        return compositeTarget(sourceTarget, depthTarget, destinationTarget, sceneDepthTextureId, 0.0F, 0.0F);
    }

    public static boolean compositeTarget(
            @NotNull RenderTarget sourceTarget,
            @NotNull RenderTarget depthTarget,
            @NotNull RenderTarget destinationTarget,
            int sceneDepthTextureId,
            float blurRadius,
            float blurStrength
    ) {
        return drawCompositePass(
                sourceTarget,
                depthTarget,
                null,
                destinationTarget,
                sceneDepthTextureId,
                blurRadius,
                blurStrength,
                0.0F,
                false,
                true
        );
    }

    public static boolean compositeTarget(@NotNull RenderTarget sourceTarget, @NotNull RenderTarget destinationTarget, int sceneDepthTextureId) {
        return compositeTarget(sourceTarget, sourceTarget, destinationTarget, sceneDepthTextureId);
    }

    private static boolean drawCompositePass(
            @NotNull RenderTarget sourceTarget,
            @NotNull RenderTarget depthTarget,
            @Nullable RenderTarget historyTarget,
            @NotNull RenderTarget destinationTarget,
            int sceneDepthTextureId,
            float blurRadius,
            float blurStrength,
            float historyWeight,
            boolean useHistory,
            boolean blendToDestination
    ) {
        ShaderInstance shader = CloudShaders.getCompositeShader();
        if (shader == null) {
            return false;
        }

        ensureFullscreenQuad();
        if (fullscreenQuad == null) {
            return false;
        }

        destinationTarget.bindWrite(true);
        if (!blendToDestination) {
            destinationTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            destinationTarget.clear(Minecraft.ON_OSX);
        }

        applyLinearFiltering(sourceTarget.getColorTextureId());
        applyLinearFiltering(depthTarget.getDepthTextureId());
        if (historyTarget != null) {
            applyLinearFiltering(historyTarget.getColorTextureId());
        }

        if (blendToDestination) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);

        shader.setSampler("CloudColorSampler", sourceTarget.getColorTextureId());
        shader.setSampler("CloudDepthSampler", depthTarget.getDepthTextureId());
        shader.setSampler("SceneDepthSampler", sceneDepthTextureId);
        shader.setSampler("CloudHistorySampler", historyTarget != null ? historyTarget.getColorTextureId() : sourceTarget.getColorTextureId());
        shader.safeGetUniform("BlurRadius").set(Math.max(0.0F, blurRadius));
        shader.safeGetUniform("BlurStrength").set(Mth.clamp(blurStrength, 0.0F, 1.0F));
        shader.safeGetUniform("HistoryBlendWeight").set(Mth.clamp(historyWeight, 0.0F, 0.95F));
        shader.safeGetUniform("UseHistory").set(useHistory ? 1 : 0);
        shader.safeGetUniform("CompositeDebugMode").set(0);
        shader.apply();

        fullscreenQuad.bind();
        fullscreenQuad.drawWithShader(new Matrix4f(), new Matrix4f(), shader);
        VertexBuffer.unbind();
        shader.clear();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        return true;
    }

    private static float resolveHistoryWeight(@NotNull CloudRenderFrameContext frameContext, @NotNull RenderTarget sourceTarget, boolean historyValid) {
        float baseWeight = Mth.clamp(frameContext.getRenderProfile().getTemporalHistoryWeight(), 0.0F, 0.95F);
        Vec3 cameraPosition = frameContext.getCameraPosition();
        Matrix4f modelViewMatrix = frameContext.getModelViewMatrix();
        Matrix4f projectionMatrix = frameContext.getProjectionMatrix();

        if (!historyValid
                || previousCameraPosition == null
                || previousModelViewMatrix == null
                || previousProjectionMatrix == null
                || sourceTarget.width <= 0
                || sourceTarget.height <= 0) {
            rememberCamera(frameContext);
            return 0.0F;
        }

        float cameraDelta = (float) cameraPosition.distanceTo(previousCameraPosition);
        float matrixDelta = Math.max(
                maxMatrixElementDelta(modelViewMatrix, previousModelViewMatrix),
                maxMatrixElementDelta(projectionMatrix, previousProjectionMatrix)
        );
        rememberCamera(frameContext);

        if (cameraDelta >= HISTORY_RESET_CAMERA_DISTANCE || matrixDelta >= HISTORY_RESET_MATRIX_DELTA) {
            return 0.0F;
        }

        float cameraStability = 1.0F - smoothStep(HISTORY_FADE_CAMERA_DISTANCE, HISTORY_RESET_CAMERA_DISTANCE, cameraDelta);
        float matrixStability = 1.0F - smoothStep(HISTORY_FADE_MATRIX_DELTA, HISTORY_RESET_MATRIX_DELTA, matrixDelta);
        return baseWeight * Math.min(cameraStability, matrixStability);
    }

    private static void rememberCamera(@NotNull CloudRenderFrameContext frameContext) {
        previousCameraPosition = frameContext.getCameraPosition();
        previousModelViewMatrix = frameContext.getModelViewMatrix();
        previousProjectionMatrix = frameContext.getProjectionMatrix();
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / Math.max(edge1 - edge0, 0.0001F), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float maxMatrixElementDelta(@NotNull Matrix4f current, @NotNull Matrix4f previous) {
        float max = 0.0F;
        max = Math.max(max, Math.abs(current.m00() - previous.m00()));
        max = Math.max(max, Math.abs(current.m01() - previous.m01()));
        max = Math.max(max, Math.abs(current.m02() - previous.m02()));
        max = Math.max(max, Math.abs(current.m03() - previous.m03()));
        max = Math.max(max, Math.abs(current.m10() - previous.m10()));
        max = Math.max(max, Math.abs(current.m11() - previous.m11()));
        max = Math.max(max, Math.abs(current.m12() - previous.m12()));
        max = Math.max(max, Math.abs(current.m13() - previous.m13()));
        max = Math.max(max, Math.abs(current.m20() - previous.m20()));
        max = Math.max(max, Math.abs(current.m21() - previous.m21()));
        max = Math.max(max, Math.abs(current.m22() - previous.m22()));
        max = Math.max(max, Math.abs(current.m23() - previous.m23()));
        max = Math.max(max, Math.abs(current.m30() - previous.m30()));
        max = Math.max(max, Math.abs(current.m31() - previous.m31()));
        max = Math.max(max, Math.abs(current.m32() - previous.m32()));
        max = Math.max(max, Math.abs(current.m33() - previous.m33()));
        return max;
    }
    /**
     * Applies smooth filtering to a texture before it is sampled by the composite shader.
     *
     * @param textureId The OpenGL texture id to configure.
     */
    private static void applyLinearFiltering(int textureId) {
        if (textureId <= 0) {
            return;
        }

        RenderSystem.bindTexture(textureId);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static void ensureFullscreenQuad() {
        if (fullscreenQuad != null) {
            return;
        }

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1.0F, -1.0F, 0.0F).uv(0.0F, 0.0F).endVertex();
        builder.vertex(1.0F, -1.0F, 0.0F).uv(1.0F, 0.0F).endVertex();
        builder.vertex(1.0F, 1.0F, 0.0F).uv(1.0F, 1.0F).endVertex();
        builder.vertex(-1.0F, 1.0F, 0.0F).uv(0.0F, 1.0F).endVertex();

        fullscreenQuad = new VertexBuffer(VertexBuffer.Usage.STATIC);
        fullscreenQuad.bind();
        fullscreenQuad.upload(builder.end());
        VertexBuffer.unbind();
    }
}
