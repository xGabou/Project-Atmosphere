package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.Gabou.projectatmosphere.client.render.shader.CloudShaders;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowMapAccess;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowSnapshot;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class CloudTerrainShadowRenderer {
    private static VertexBuffer fullscreenQuad;

    private CloudTerrainShadowRenderer() {
    }

    public static boolean render(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull RenderTarget mainTarget,
            @Nullable RenderTarget shadowTarget
    ) {
        return render(frameContext, mainTarget, shadowTarget, resolveShadowStrength());
    }

    public static boolean render(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull RenderTarget mainTarget,
            @Nullable RenderTarget shadowTarget,
            float shadowStrength
    ) {
        if (!isEnabled() || shadowTarget == null || shadowTarget.getColorTextureId() <= 0) {
            return false;
        }

        CloudShadowSnapshot snapshot = CloudShadowMapAccess.getCurrentSnapshot();
        if (snapshot == null || !snapshot.isValid()) {
            return false;
        }

        ShaderInstance shader = CloudShaders.getTerrainShadowShader();
        if (shader == null) {
            return false;
        }

        ensureFullscreenQuad();
        if (fullscreenQuad == null) {
            return false;
        }

        applyLinearFiltering(shadowTarget.getColorTextureId());
        mainTarget.bindWrite(true);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);

        shader.setSampler("SceneDepthSampler", mainTarget.getDepthTextureId());
        shader.setSampler("CloudShadowSampler", shadowTarget.getColorTextureId());
        shader.safeGetUniform("InverseProjMat").set(frameContext.getInverseProjectionMatrix());
        shader.safeGetUniform("InverseModelViewMat").set(frameContext.getInverseModelViewMatrix());
        shader.safeGetUniform("ShadowBounds").set(snapshot.getMinX(), snapshot.getMinZ(), snapshot.getMaxX(), snapshot.getMaxZ());
        shader.safeGetUniform("ShadowStrength").set(Mth.clamp(shadowStrength, 0.0F, 0.72F));
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

    private static boolean isEnabled() {
        try {
            return AtmoCommonConfig.ENABLE_CLOUD_SHADOW_MAP.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    private static float resolveShadowStrength() {
        return Mth.clamp(0.52F, 0.0F, 0.72F);
    }

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
