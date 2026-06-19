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
import org.joml.Vector4f;
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

        ProjectedBounds projectedBounds = resolveProjectedBounds(frameContext, snapshot, outputTarget);
        if (!projectedBounds.visible()) {
            CloudRenderDiagnostics.recordFrustumSkipped();
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

        boolean shaderDebugMode = false;
        CloudRenderDiagnostics.recordShaderDebugMode(false);
        shader.safeGetUniform("WriteDepth").set(writeDepth ? 1 : 0);
        shader.safeGetUniform("CloudDebugMode").set(shaderDebugMode ? 1 : 0);
        shader.setSampler("DepthSampler", sceneDepthTextureId);
        CloudUniformUploader.apply(shader, frameContext, snapshot, outputTarget);
        shader.apply();

        boolean scissorEnabled = projectedBounds.shouldScissor(outputTarget);
        if (scissorEnabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(projectedBounds.x(), projectedBounds.y(), projectedBounds.width(), projectedBounds.height());
        }

        gpuTimer.begin();
        CloudGlDebug.pushGroup("cloud-volume-draw");
        try {
            fullscreenQuad.bind();
            fullscreenQuad.drawWithShader(
                    frameContext.getModelViewMatrix(),
                    frameContext.getProjectionMatrix(),
                    shader
            );
            VertexBuffer.unbind();
            CloudGlDebug.checkErrors("cloud-volume-draw");
        } finally {
            gpuTimer.end();
            CloudGlDebug.popGroup();
            if (scissorEnabled) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
        shader.clear();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        return true;
    }

    private static ProjectedBounds resolveProjectedBounds(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull CloudRenderSnapshot snapshot,
            @NotNull RenderTarget target
    ) {
        if (target.width <= 0 || target.height <= 0 || snapshot.getRegionCenter() == null) {
            return ProjectedBounds.full(target);
        }

        Vec3 camera = frameContext.getCameraPosition();
        Vec3 center = snapshot.getRegionCenter();
        float radius = Math.max(1.0F, snapshot.getRegionRadius());
        float verticalPadding = renderVerticalPadding(snapshot, radius);
        float minXWorld = (float) center.x() - radius;
        float maxXWorld = (float) center.x() + radius;
        float minYWorld = snapshot.getCloudBaseY() - verticalPadding;
        float maxYWorld = snapshot.getCloudTopY() + verticalPadding;
        float minZWorld = (float) center.z() - radius;
        float maxZWorld = (float) center.z() + radius;

        if (camera.x() >= minXWorld && camera.x() <= maxXWorld
                && camera.y() >= minYWorld && camera.y() <= maxYWorld
                && camera.z() >= minZWorld && camera.z() <= maxZWorld) {
            return ProjectedBounds.full(target);
        }

        Matrix4f viewProjection = new Matrix4f(frameContext.getProjectionMatrix()).mul(frameContext.getModelViewMatrix());
        float minNdcX = Float.POSITIVE_INFINITY;
        float minNdcY = Float.POSITIVE_INFINITY;
        float maxNdcX = Float.NEGATIVE_INFINITY;
        float maxNdcY = Float.NEGATIVE_INFINITY;

        for (int xi = 0; xi < 2; xi++) {
            float x = xi == 0 ? minXWorld : maxXWorld;
            for (int yi = 0; yi < 2; yi++) {
                float y = yi == 0 ? minYWorld : maxYWorld;
                for (int zi = 0; zi < 2; zi++) {
                    float z = zi == 0 ? minZWorld : maxZWorld;
                    Vector4f clip = viewProjection.transform(new Vector4f(x, y, z, 1.0F));
                    if (clip.w() <= 0.05F) {
                        return ProjectedBounds.full(target);
                    }
                    float ndcX = clip.x() / clip.w();
                    float ndcY = clip.y() / clip.w();
                    minNdcX = Math.min(minNdcX, ndcX);
                    minNdcY = Math.min(minNdcY, ndcY);
                    maxNdcX = Math.max(maxNdcX, ndcX);
                    maxNdcY = Math.max(maxNdcY, ndcY);
                }
            }
        }

        // Be conservative here: the raymarch pass already clips against the 3D cloud AABB.
        // If the projected box is uncertain, render the full target instead of skipping the cloud.
        float offscreenMargin = 0.04F;
        if (maxNdcX < -1.0F - offscreenMargin
                || minNdcX > 1.0F + offscreenMargin
                || maxNdcY < -1.0F - offscreenMargin
                || minNdcY > 1.0F + offscreenMargin) {
            return ProjectedBounds.hidden();
        }

        int padding = 2;
        int minX = Mth.floor((Mth.clamp(minNdcX, -1.0F, 1.0F) * 0.5F + 0.5F) * target.width) - padding;
        int maxX = Mth.ceil((Mth.clamp(maxNdcX, -1.0F, 1.0F) * 0.5F + 0.5F) * target.width) + padding;
        int minY = Mth.floor((Mth.clamp(minNdcY, -1.0F, 1.0F) * 0.5F + 0.5F) * target.height) - padding;
        int maxY = Mth.ceil((Mth.clamp(maxNdcY, -1.0F, 1.0F) * 0.5F + 0.5F) * target.height) + padding;

        int scissorX = Mth.clamp(minX, 0, target.width);
        int scissorY = Mth.clamp(minY, 0, target.height);
        int scissorMaxX = Mth.clamp(maxX, 0, target.width);
        int scissorMaxY = Mth.clamp(maxY, 0, target.height);
        if (scissorMaxX <= scissorX || scissorMaxY <= scissorY) {
            return ProjectedBounds.full(target);
        }
        return new ProjectedBounds(true, scissorX, scissorY, scissorMaxX - scissorX, scissorMaxY - scissorY);
    }

    private static float renderVerticalPadding(@NotNull CloudRenderSnapshot snapshot, float radius) {
        float heightRange = Math.max(0.001F, snapshot.getCloudTopY() - snapshot.getCloudBaseY());
        float sheetFactor = Mth.clampedMap(snapshot.getHeightSquash(), 1.20F, 3.20F, 0.0F, 1.0F);
        return sheetFactor * Math.min(28.0F, Math.max(heightRange * 0.45F, radius * 0.035F));
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

        CloudGlDebug.pushGroup("cloud-composite-draw");
        try {
            fullscreenQuad.bind();
            fullscreenQuad.drawWithShader(new Matrix4f(), new Matrix4f(), shader);
            VertexBuffer.unbind();
            shader.clear();
            CloudGlDebug.checkErrors("cloud-composite-draw");
        } finally {
            CloudGlDebug.popGroup();
        }

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

    private record ProjectedBounds(boolean visible, int x, int y, int width, int height) {
        private static ProjectedBounds hidden() {
            return new ProjectedBounds(false, 0, 0, 0, 0);
        }

        private static ProjectedBounds full(RenderTarget target) {
            return new ProjectedBounds(true, 0, 0, Math.max(1, target.width), Math.max(1, target.height));
        }

        private boolean shouldScissor(RenderTarget target) {
            return this.visible
                    && this.width > 0
                    && this.height > 0
                    && (this.x > 0 || this.y > 0 || this.width < target.width || this.height < target.height);
        }
    }
}
