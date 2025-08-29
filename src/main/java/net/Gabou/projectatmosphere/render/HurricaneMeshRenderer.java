package net.Gabou.projectatmosphere.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
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

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION);

        VertexConsumer consumer = bufferbuilder;

        final int segs = 160;
        final float y = 0.02f;
        final float twoPi = (float) (Math.PI * 2.0);
        Matrix4f mv = pose.last().pose();

        for (int i = 0; i <= segs; i++) {
            float a = twoPi * i / segs;
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            consumer.addVertex(mv, cx + cs * inner, y, cz + sn * inner);
            consumer.addVertex(mv, cx + cs * outer, y, cz + sn * outer);
        }

        // In Mojang mappings 1.21.1 this is MeshData
        var mesh = bufferbuilder.build();
        BufferUploader.drawWithShader(mesh);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        shader.clear();
    }

}

