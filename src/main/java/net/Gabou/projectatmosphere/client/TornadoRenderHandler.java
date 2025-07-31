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
        var timeUniform = shader.getUniform("Time");
        if (timeUniform != null)
            timeUniform.set(TornadoManager.getShaderTime());

        for (TornadoInstance tornado : TornadoManager.getActiveTornadoes()) {
            poseStack.pushPose();
            Vec3 camPos = camera.getPosition();
            poseStack.translate(tornado.position.x - camPos.x, tornado.position.y - camPos.y, tornado.position.z - camPos.z);

            // optional: scale based on radius
            poseStack.scale(tornado.radius, tornado.radius, tornado.radius);

            // apply transformation
            RenderSystem.applyModelViewMatrix();
            TornadoMesh.drawTestTriangle(); // instead of drawInstanced
            poseStack.popPose();
        }
    }

}

