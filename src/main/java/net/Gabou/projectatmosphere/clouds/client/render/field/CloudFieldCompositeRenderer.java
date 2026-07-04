package net.Gabou.projectatmosphere.clouds.client.render.field;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.Gabou.projectatmosphere.client.render.shader.CloudFieldVolumeShaders;
import net.Gabou.projectatmosphere.clouds.client.render.CloudGpuTimer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Depth-aware upsample and scene composite for the CloudField target. */
public final class CloudFieldCompositeRenderer {
    private static final CloudGpuTimer GPU_TIMER = new CloudGpuTimer();
    private static VertexBuffer fullscreenQuad;

    private CloudFieldCompositeRenderer() {
    }

    public static boolean composite(
            RenderTarget source,
            RenderTarget destination,
            CloudFieldCompositeDebugMode requestedMode
    ) {
        ShaderInstance shader = CloudFieldVolumeShaders.getCompositeShader();
        if (source == null || destination == null || shader == null || source.getDepthTextureId() <= 0) {
            return false;
        }
        ensureFullscreenQuad();
        if (fullscreenQuad == null) {
            return false;
        }

        CloudFieldCompositeDebugMode mode = requestedMode == null
                ? CloudFieldCompositeDebugMode.FINAL
                : requestedMode;
        GPU_TIMER.poll();
        RenderSystem.disableScissor();
        destination.bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (mode == CloudFieldCompositeDebugMode.FINAL) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);
        shader.setSampler("CloudColorSampler", source.getColorTextureId());
        shader.setSampler("CloudDepthSampler", source.getDepthTextureId());
        shader.safeGetUniform("CompositeMode").set(mode.shaderId());
        shader.apply();

        GPU_TIMER.begin();
        try {
            fullscreenQuad.bind();
            fullscreenQuad.drawWithShader(new Matrix4f(), new Matrix4f(), shader);
            VertexBuffer.unbind();
        } finally {
            GPU_TIMER.end();
            shader.clear();
            restoreRenderState();
        }
        return true;
    }

    public static String performanceDiagnostics() {
        GPU_TIMER.poll();
        if (!GPU_TIMER.isSupported()) {
            return "compositeGpuMs=unsupported";
        }
        if (!GPU_TIMER.hasResult()) {
            return "compositeGpuMs=pending pendingCompositeQueries=" + GPU_TIMER.getPendingQueries();
        }
        return "compositeGpuMs=" + CloudFieldVolumeRenderStats.format(GPU_TIMER.getLastMilliseconds())
                + " compositeAgeFrames=" + GPU_TIMER.getLastResultAgeFrames()
                + " pendingCompositeQueries=" + GPU_TIMER.getPendingQueries();
    }

    public static void restoreRenderState() {
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        GL11.glCullFace(GL11.GL_BACK);
        RenderSystem.disableBlend();
        RenderSystem.disableScissor();
    }

    public static void shutdown() {
        if (fullscreenQuad != null) {
            fullscreenQuad.close();
            fullscreenQuad = null;
        }
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
