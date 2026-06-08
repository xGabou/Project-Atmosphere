package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.client.render.shader.CloudShaders;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * Dessine les nuages live avec le shader dedie.
 * Cette classe ne lit jamais le cache debug et ne manipule pas le backend.
 */
public final class CloudRaymarchRenderer {

    private static VertexBuffer fullscreenQuad;

    private CloudRaymarchRenderer() {

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
            int sceneDepthTextureId
    ) {
        ShaderInstance shader = CloudShaders.getShader();
        if (shader == null || !snapshot.isEnabled() || !CloudDensityProvider.hasVisibleDensity(snapshot)) {
            return false;
        }

        ensureFullscreenQuad();
        if (fullscreenQuad == null) {
            return false;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);

        shader.setSampler("DepthSampler", sceneDepthTextureId);
        CloudUniformUploader.apply(shader, frameContext, snapshot, outputTarget);
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

    public static boolean compositeTarget(@NotNull RenderTarget sourceTarget, @NotNull RenderTarget destinationTarget) {
        ShaderInstance shader = CloudShaders.getCompositeShader();
        if (shader == null) {
            return false;
        }

        ensureFullscreenQuad();
        if (fullscreenQuad == null) {
            return false;
        }

        destinationTarget.bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);

        shader.setSampler("CloudColorSampler", sourceTarget.getColorTextureId());
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
