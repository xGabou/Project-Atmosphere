package net.Gabou.projectatmosphere.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.Gabou.projectatmosphere.client.render.TornadoMesh;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.particles.DebrisParticleData;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class TornadoRenderHandler {

//    public static void renderTornadoess(PoseStack poseStack, Camera camera) {
//        TornadoMesh.init();
//
//        ShaderInstance shader = TornadoShaders.getTornadoShader();
//        RenderSystem.setShader(() -> shader);
//
//        shader.apply(); // VERY important to bind the shader before setting uniforms
//
//        var modelViewMat = shader.getUniform("ModelViewMat");
//        if (modelViewMat != null)
//            modelViewMat.set(poseStack.last().pose());
//
//        var projMat = shader.getUniform("ProjMat");
//        if (projMat != null)
//            projMat.set(RenderSystem.getProjectionMatrix());
//
//        var timeUniform = shader.getUniform("Time");
//        if (timeUniform != null)
//            timeUniform.set(TornadoManager.getShaderTime());
//
//        RenderSystem.enableDepthTest();
//        RenderSystem.enableBlend();
//        RenderSystem.defaultBlendFunc();
//
//
//        for (TornadoInstance tornado : TornadoManager.getActiveTornadoes()) {
//// 1st rendering block: fixed at (0, 80, 0), scaled 10×
//            poseStack.pushPose();
//            Vec3 camPos = camera.getPosition();
//            poseStack.translate(40, -40, 40);
//            poseStack.scale(10, 10, 10);
//            RenderSystem.applyModelViewMatrix();
//            TornadoMesh.drawCone();
//            poseStack.popPose();
//
//// 2nd rendering block: no translation, no scaling — invisible
//            RenderSystem.applyModelViewMatrix();
//            TornadoMesh.drawCone();
//
//
//        }
//    }

public static void renderTornado(PoseStack stack, double x, double y, double z) {
    if (MyShaders.TORNADO == null) return;

    stack.pushPose();
    stack.translate(x, y, z);
    Matrix4f matrix = stack.last().pose();

    ShaderInstance shader = MyShaders.TORNADO;
    RenderSystem.setShader(() -> shader);
    shader.apply();

    var modelView = shader.getUniform("ModelViewMat");
    if (modelView != null) {
        modelView.set(matrix);
    }
    var projMat = shader.getUniform("ProjMat");
    if (projMat != null) {
        projMat.set(RenderSystem.getProjectionMatrix());
    }
    var timeUniform = shader.getUniform("Time");
    if (timeUniform != null) {
        timeUniform.set(TornadoManager.getShaderTime());
    }

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.disableCull(); // See both sides

    Tesselator tess = Tesselator.getInstance();
    BufferBuilder buffer = tess.getBuilder();
    buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_TEX);

    int segments = 64;
    int rings = 30;
    float baseRadius = 8f;
    float topRadius = 1.5f;
    float height = SimpleCloudsConfig.CLIENT.cloudHeight.get() * 0.5f;

    for (int i = 0; i < rings; i++) {
        float y0 = i * (height / rings);
        float y1 = (i + 1) * (height / rings);

        float radius0 = baseRadius - (baseRadius - topRadius) * (i / (float) rings);
        float radius1 = baseRadius - (baseRadius - topRadius) * ((i + 1f) / rings);

        float twist0 = i * 0.5f; // Twist factor (radians)
        float twist1 = (i + 1) * 0.5f;

        for (int j = 0; j <= segments; j++) {
            float angle = (float) (2 * Math.PI * j / segments);
            float u = j / (float) segments;

            float x0 = (float) (radius0 * Math.cos(angle + twist0));
            float z0 = (float) (radius0 * Math.sin(angle + twist0));
            float x1 = (float) (radius1 * Math.cos(angle + twist1));
            float z1 = (float) (radius1 * Math.sin(angle + twist1));

            buffer.vertex(matrix, x0, y0, z0).uv(u, y0 / height).endVertex();
            buffer.vertex(matrix, x1, y1, z1).uv(u, y1 / height).endVertex();
        }
    }

    RenderSystem.applyModelViewMatrix();
    tess.end();

    RenderSystem.enableCull();
    RenderSystem.disableBlend();
    stack.popPose();
}


    public static void spawnDebrisParticles(TornadoInstance tornado, ClientLevel level) {
        for (int i = 0; i < 10; i++) {
            double radius = 2.5 + level.random.nextDouble() * 2.0;
            double height = level.random.nextDouble() * 10.0;
            float angularSpeed = 5f;

            level.addParticle(new DebrisParticleData(tornado, radius, height, angularSpeed),
                    tornado.position.x, tornado.position.y, tornado.position.z, 0, 0.01, 0);
        }
    }



}

