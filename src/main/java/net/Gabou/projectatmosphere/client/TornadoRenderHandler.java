package net.Gabou.projectatmosphere.client;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.particles.DebrisParticleData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class TornadoRenderHandler {

    private static final ResourceLocation NOISE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("projectatmosphere", "textures/effects/noise.png");
    private static final ResourceLocation TORNADO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("projectatmosphere", "textures/effects/tornado.png");
    private static final ResourceLocation FLOWMAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("projectatmosphere", "textures/effects/flowmap.png");
    private static final ResourceLocation NORMALMAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("projectatmosphere", "textures/effects/tornado_normal.png");


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

    /// / 2nd rendering block: no translation, no scaling — invisible
//            RenderSystem.applyModelViewMatrix();
//            TornadoMesh.drawCone();
//
//
//        }
//    }

//public static void renderTornado(PoseStack stack, double x, double y, double z) {
//    if (MyShaders.TORNADO == null) return;
//
//    stack.pushPose();
//    stack.translate(x, y, z);
//    Matrix4f matrix = stack.last().pose();
//
//    ShaderInstance shader = MyShaders.TORNADO;
//    RenderSystem.setShader(() -> shader);
//    shader.apply();
//
//    var modelView = shader.getUniform("ModelViewMat");
//    if (modelView != null) modelView.set(matrix);
//
//    var projMat = shader.getUniform("ProjMat");
//    if (projMat != null) projMat.set(RenderSystem.getProjectionMatrix());
//
//    var timeUniform = shader.getUniform("Time");
//    if (timeUniform != null) timeUniform.set(TornadoManager.getShaderTime());
//
//    RenderSystem.enableBlend();
//    RenderSystem.defaultBlendFunc();
//    RenderSystem.disableCull(); // Still useful for full visibility
//
//    Tesselator tess = Tesselator.getInstance();
//    BufferBuilder buffer = tess.getBuilder();
//    buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
//
//    int segments = 64;
//    int rings = 30;
//    float baseRadius = 8f;
//    float topRadius = 1.5f;
//    float height = SimpleCloudsConfig.CLIENT.cloudHeight.get();
//
//    float twistSpeed = TornadoManager.getShaderTime() * 1.5f;
//
//    for (int i = rings - 1; i >= 0; i--) { // render top → bottom
//        float y0 = i * (height / rings);
//        float y1 = (i + 1) * (height / rings);
//
//        float radius0 = baseRadius - (baseRadius - topRadius) * (i / (float) rings);
//        float radius1 = baseRadius - (baseRadius - topRadius) * ((i + 1f) / rings);
//
//        float twist0 = twistSpeed + i * 0.2f;
//        float twist1 = twistSpeed + (i + 1f) * 0.2f;
//
//        for (int j = 0; j < segments; j++) {
//            float u0 = j / (float) segments;
//            float u1 = (j + 1 == segments) ? 1f : (j + 1) / (float) segments;
//
//            float angle0 = (float) (2 * Math.PI * j / segments);
//            float angle1 = (j + 1 == segments) ? 0f : (float) (2 * Math.PI * (j + 1) / segments);
//
//
//            float x00 = (float) (radius0 * Math.cos(angle0 + twist0));
//            float z00 = (float) (radius0 * Math.sin(angle0 + twist0));
//            float x01 = (float) (radius0 * Math.cos(angle1 + twist0));
//            float z01 = (float) (radius0 * Math.sin(angle1 + twist0));
//            float x10 = (float) (radius1 * Math.cos(angle0 + twist1));
//            float z10 = (float) (radius1 * Math.sin(angle0 + twist1));
//            float x11 = (float) (radius1 * Math.cos(angle1 + twist1));
//            float z11 = (float) (radius1 * Math.sin(angle1 + twist1));
//
//            // Triangle 1 (bottom-left, top-left, top-right)
//            buffer.vertex(matrix, x00, y0, z00).uv(u0, y0 / height).endVertex();
//            buffer.vertex(matrix, x10, y1, z10).uv(u0, y1 / height).endVertex();
//            buffer.vertex(matrix, x11, y1, z11).uv(u1, y1 / height).endVertex();
//
//            // Triangle 2 (bottom-left, top-right, bottom-right)
//            buffer.vertex(matrix, x00, y0, z00).uv(u0, y0 / height).endVertex();
//            buffer.vertex(matrix, x11, y1, z11).uv(u1, y1 / height).endVertex();
//            buffer.vertex(matrix, x01, y0, z01).uv(u1, y0 / height).endVertex();
//        }
//        // Close the last ring segment (wrap j = segments to j = 0)
//        {
//            float u0 = 1f;
//            float u1 = 0f;
//
//            float angle0 = (float) (2 * Math.PI);
//            float angle1 = 0f;
//
//            float x00 = (float) (radius0 * Math.cos(angle0 + twist0));
//            float z00 = (float) (radius0 * Math.sin(angle0 + twist0));
//            float x01 = (float) (radius0 * Math.cos(angle1 + twist0));
//            float z01 = (float) (radius0 * Math.sin(angle1 + twist0));
//            float x10 = (float) (radius1 * Math.cos(angle0 + twist1));
//            float z10 = (float) (radius1 * Math.sin(angle0 + twist1));
//            float x11 = (float) (radius1 * Math.cos(angle1 + twist1));
//            float z11 = (float) (radius1 * Math.sin(angle1 + twist1));
//
//            buffer.vertex(matrix, x00, y0, z00).uv(u0, y0 / height).endVertex();
//            buffer.vertex(matrix, x10, y1, z10).uv(u0, y1 / height).endVertex();
//            buffer.vertex(matrix, x11, y1, z11).uv(u1, y1 / height).endVertex();
//
//            buffer.vertex(matrix, x00, y0, z00).uv(u0, y0 / height).endVertex();
//            buffer.vertex(matrix, x11, y1, z11).uv(u1, y1 / height).endVertex();
//            buffer.vertex(matrix, x01, y0, z01).uv(u1, y0 / height).endVertex();
//        }
//
//    }
//
//
//    RenderSystem.applyModelViewMatrix();
//    tess.end();
//
//    RenderSystem.enableCull();
//    RenderSystem.disableBlend();
//    stack.popPose();
//}
    public static void renderTornado(PoseStack stack, double tornadoX, double tornadoY, double tornadoZ, float twistSpeed, ClientLevel level, Camera camera, Minecraft minecraft) {
        ShaderInstance shader = MyShaders.TORNADO;
        if (shader == null) return;
        RenderSystem.setShader(() -> shader);

        int segments = 64;
        int rings = 64;
        float baseRadius = 8f;
        float topRadius = 1.5f;
        float height = SimpleCloudsConfig.CLIENT.cloudHeight.get();
        stack.pushPose();
        stack.translate(tornadoX, tornadoY, tornadoZ);

        Matrix4f matrix = stack.last().pose();
        shader.apply();

        var modelView = shader.getUniform("ModelViewMat");
        if (modelView != null) modelView.set(matrix);

        var projMat = shader.getUniform("ProjMat");
        if (projMat != null) projMat.set(RenderSystem.getProjectionMatrix());

        var timeUniform = shader.getUniform("Time");
        if (timeUniform != null) timeUniform.set(TornadoManager.getShaderTime());

        var twistUniform = shader.getUniform("TwistSpeed");
        if (twistUniform != null) twistUniform.set(twistSpeed);
        // Geometry uniforms
        var baseRadiusUniform = shader.getUniform("BaseRadius");
        if (baseRadiusUniform != null) baseRadiusUniform.set(baseRadius);

        var topRadiusUniform = shader.getUniform("TopRadius");
        if (topRadiusUniform != null) topRadiusUniform.set(topRadius);

        var heightUniform = shader.getUniform("Height");
        if (heightUniform != null) heightUniform.set(height);

// Tornado profile & flow uniforms
        var dustUniform = shader.getUniform("DustIntensity");
        if (dustUniform != null) dustUniform.set(0.5f); // tweak as needed

        var coreUniform = shader.getUniform("CoreTightness");
        if (coreUniform != null) coreUniform.set(0.5f); // tweak as needed

        var flowIntensity = shader.getUniform("FlowIntensity");
        if (flowIntensity != null) flowIntensity.set(0.2f); // tweak as needed

// Lighting + sky


// Sky color
        float partialTicks = minecraft.getFrameTime();
        float sunAngle = level.getTimeOfDay(partialTicks); // value from 0.0 to 1.0
        float angle = sunAngle * ((float) Math.PI * 2.0F);


        float xLight = Mth.cos(angle);
        float yLight = Mth.sin(angle); // sun rises from -1 to 1 over the day
        float zLight = 0.2f;           // slightly tilted forward


        float length = Mth.sqrt(xLight * xLight + yLight * yLight + zLight * zLight);
        xLight /= length;
        yLight /= length;
        zLight /= length;


        var lightX = shader.getUniform("LightDirX");
        var lightY = shader.getUniform("LightDirY");
        var lightZ = shader.getUniform("LightDirZ");

        if (lightX != null) lightX.set(xLight);
        if (lightY != null) lightY.set(yLight);
        if (lightZ != null) lightZ.set(zLight);
        Vec3 skyVec3 = level.getSkyColor(camera.getPosition(), partialTicks);

        float r = (float) skyVec3.x;
        float g = (float) skyVec3.y;
        float b = (float) skyVec3.z;

        Uniform uSkyR = shader.getUniform("SkyColorR");
        Uniform uSkyG = shader.getUniform("SkyColorG");
        Uniform uSkyB = shader.getUniform("SkyColorB");

        if (uSkyR != null) uSkyR.set(r);
        if (uSkyG != null) uSkyG.set(g);
        if (uSkyB != null) uSkyB.set(b);


        RenderSystem.setShaderTexture(0, TORNADO_TEXTURE);
        // bind flow & normal
        RenderSystem.setShaderTexture(1, FLOWMAP_TEXTURE);
        RenderSystem.setShaderTexture(2, NORMALMAP_TEXTURE);
        RenderSystem.setShaderTexture(3, NOISE_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest(); // Critical
        RenderSystem.depthMask(true);  // Allow writing to depth buffer


        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);


        for (int i = rings - 1; i >= 0; i--) {
            float y0 = i * (height / rings);
            float y1 = (i + 1) * (height / rings);


            float radius0 = baseRadius - (baseRadius - topRadius) * (i / (float) rings);
            float radius1 = baseRadius - (baseRadius - topRadius) * ((i + 1f) / rings);

            for (int j = 0; j < segments; j++) {
                float u0 = j / (float) segments;
                float u1 = (j + 1f) / (float) segments;
                float angle0 = (float) (2 * Math.PI * u0);
                float angle1 = (float) (2 * Math.PI * u1);

                float x00 = (float) (radius0 * Math.cos(angle0));
                float z00 = (float) (radius0 * Math.sin(angle0));
                float x01 = (float) (radius0 * Math.cos(angle1));
                float z01 = (float) (radius0 * Math.sin(angle1));
                float x10 = (float) (radius1 * Math.cos(angle0));
                float z10 = (float) (radius1 * Math.sin(angle0));
                float x11 = (float) (radius1 * Math.cos(angle1));
                float z11 = (float) (radius1 * Math.sin(angle1));

                float epsilon = 0.0001f;
                float v0 = (y0 + epsilon) / height;
                float v1 = (y1 - epsilon) / height;


                buffer.vertex(matrix, x00, y0, z00).uv(u0, v0).endVertex();
                buffer.vertex(matrix, x10, y1, z10).uv(u0, v1).endVertex();
                buffer.vertex(matrix, x11, y1, z11).uv(u1, v1).endVertex();

                buffer.vertex(matrix, x00, y0, z00).uv(u0, v0).endVertex();
                buffer.vertex(matrix, x11, y1, z11).uv(u1, v1).endVertex();
                buffer.vertex(matrix, x01, y0, z01).uv(u1, v0).endVertex();
            }
        }

        tess.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        stack.popPose();
    }

    public static void spawnDebrisParticles(TornadoInstance tornado, ClientLevel level) {
        for (int i = 0; i < 10; i++) {
            double maxRadius = 8.0;
            double radius = Math.sqrt(level.random.nextDouble()) * maxRadius;
            double height = level.random.nextDouble() * SimpleCloudsConfig.CLIENT.cloudHeight.get();
            float angularSpeed = 5f;

            level.addParticle(new DebrisParticleData(tornado, radius, height, angularSpeed),
                    tornado.position.x, tornado.position.y, tornado.position.z, 0, 0.01, 0);
        }
    }


}

