package net.Gabou.projectatmosphere.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nonamecrackers2.simpleclouds.client.shader.SimpleCloudsShaders;
import dev.nonamecrackers2.simpleclouds.client.shader.SingleSSBOShaderInstance;
import net.Gabou.projectatmosphere.client.render.TornadoMesh;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

public class TornadoRenderHandler {

    public static void renderTornadoes(PoseStack poseStack, Camera camera) {
        TornadoMesh.init();

        SingleSSBOShaderInstance shader = SimpleCloudsShaders.getCloudsShader();
        //RenderSystem.setShader(() -> shader);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        shader.safeGetUniform("Time").set(Minecraft.getInstance().level.getGameTime() % 1000 / 20f);

        for (TornadoInstance tornado : TornadoManager.getActiveTornadoes()) {
            poseStack.pushPose();
            int ssbo = TornadoMesh.uploadTornadoSSBO(tornado.position, tornado.radius, 20);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, shader.getShaderStorageBinding(), ssbo);
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

