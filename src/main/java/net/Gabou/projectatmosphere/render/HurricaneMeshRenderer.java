package net.Gabou.projectatmosphere.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

public final class HurricaneMeshRenderer {
    private HurricaneMeshRenderer() {}

    /** Draw a flat eye-wall ring in Simple Clouds' cloud space. */
    public static void renderCloudSpace(SimpleCloudsRenderer sc, PoseStack pose,
                                        Matrix4f proj, float partialTick,
                                        double camX, double camZ) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        var state = HurricaneStateProvider.getActive(camX, camZ);
        if (state == null) {
            return;
        }

        // Center & radii are expected in *cloud space* units
        float cx = (float) state.centerXCloudSpace();
        float cz = (float) state.centerZCloudSpace();
        float inner = (float) state.eyeRadiusCloudSpace();
        float outer = inner + (float) state.eyewallFadeCloudSpace();

        // Match fog/projection with SC
        RenderSystem.setShader(GameRenderer::getPositionShader);
        ShaderInstance shader = RenderSystem.getShader();
        SimpleCloudsRenderer.prepareShader(shader, pose.last().pose(), proj, sc.getFogStart(), sc.getFogEnd());
        shader.apply();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        Tesselator t = Tesselator.getInstance();
        BufferBuilder buf = t.getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION);

        final int segs = 160;
        final float y = 0.02f; // slight lift to avoid z-fighting
        final float twoPi = (float)(Math.PI * 2.0);
        Matrix4f mv = pose.last().pose();

        for (int i = 0; i <= segs; i++) {
            float a = twoPi * i / segs;
            float cs = (float)Math.cos(a), sn = (float)Math.sin(a);
            buf.vertex(mv, cx + cs * inner, y, cz + sn * inner).endVertex();
            buf.vertex(mv, cx + cs * outer, y, cz + sn * outer).endVertex();
        }

        BufferUploader.drawWithShader(buf.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        shader.clear();
    }
}

