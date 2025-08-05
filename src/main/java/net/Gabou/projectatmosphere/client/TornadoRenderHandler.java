package net.Gabou.projectatmosphere.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.client.render.TornadoMesh;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.Gabou.projectatmosphere.client.TornadoShaders;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;

public class TornadoRenderHandler {

    public static void renderTornadoes(PoseStack poseStack, Camera camera) {
        TornadoMesh.init();

        ShaderInstance shader = TornadoShaders.getTornadoShader();
        RenderSystem.setShader(() -> shader);

        shader.apply(); // VERY important to bind the shader before setting uniforms

        var modelViewMat = shader.getUniform("ModelViewMat");
        if (modelViewMat != null)
            modelViewMat.set(poseStack.last().pose());

        var projMat = shader.getUniform("ProjMat");
        if (projMat != null)
            projMat.set(RenderSystem.getProjectionMatrix());

        var timeUniform = shader.getUniform("Time");
        if (timeUniform != null)
            timeUniform.set(TornadoManager.getShaderTime());

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();


        for (TornadoInstance tornado : TornadoManager.getActiveTornadoes()) {
// 1st rendering block: fixed at (0, 80, 0), scaled 10×
            poseStack.pushPose();
            Vec3 camPos = camera.getPosition();
            poseStack.translate(40, -40, 40);
            poseStack.scale(10, 10, 10);
            RenderSystem.applyModelViewMatrix();
            TornadoMesh.drawCone();
            poseStack.popPose();

// 2nd rendering block: no translation, no scaling — invisible
            RenderSystem.applyModelViewMatrix();
            TornadoMesh.drawCone();


        }
    }



}

