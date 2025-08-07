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
    public static void renderTornado(PoseStack stack, double x, double y, double z, float twistSpeed,Camera camera, ClientLevel level) {
        ShaderInstance shader = MyShaders.TORNADO;
        if (shader == null) return;
        RenderSystem.setShader(() -> shader);

        stack.pushPose();
        Matrix4f modelMat = new Matrix4f().translation((float)x, (float)y, (float)z);
        Matrix4f viewMat = RenderSystem.getModelViewMatrix(); // camera
        Matrix4f projMat = RenderSystem.getProjectionMatrix();
        shader.apply();

        var model = shader.getUniform("ModelMat");
        if (model != null) model.set(modelMat);

        var view = shader.getUniform("ViewMat");
        if (view != null) view.set(viewMat);

        var proj = shader.getUniform("ProjMat");
        if (proj != null) proj.set(projMat);

        var timeUniform = shader.getUniform("Time");
        if (timeUniform != null) timeUniform.set(TornadoManager.getShaderTime());

        var twistUniform = shader.getUniform("TwistSpeed");
        if (twistUniform != null) twistUniform.set(twistSpeed);

        RenderSystem.setShaderTexture(0, TORNADO_TEXTURE);
        // bind flow & normal
        RenderSystem.setShaderTexture(1, FLOWMAP_TEXTURE);
        RenderSystem.setShaderTexture(2, NORMALMAP_TEXTURE);
        RenderSystem.setShaderTexture(3, NOISE_TEXTURE);
        float partialTicks = Minecraft.getInstance().getFrameTime();
        Vec3 skyVec3 = level.getSkyColor(camera.getPosition(), partialTicks);
        Vector3f skyColor = new Vector3f((float) skyVec3.x, (float) skyVec3.y, (float) skyVec3.z);
        Uniform uSkyColor = shader.getUniform("SkyColor");
        if (uSkyColor != null) {
            uSkyColor.set(new float[]{ skyColor.x(), skyColor.y(), skyColor.z() });
        }
    // set sampler indices if needed (MC shaders usually infer by bind order)
    // but to be safe:
        var flowUni = shader.getUniform("FlowMap");
        var normalUni = shader.getUniform("NormalMap");
        var noiseUni = shader.getUniform("NoiseMap");
        if (flowUni != null) flowUni.set(1);
        if (normalUni != null) normalUni.set(2);
        if (noiseUni != null) noiseUni.set(3);

    // set our new tunables
        var flowIntUni = shader.getUniform("FlowIntensity");
        if (flowIntUni != null) flowIntUni.set(0.2f);
        var lightDirUni = shader.getUniform("LightDir");
        if (lightDirUni != null) lightDirUni.set(new float[]{0.5f, 1.0f, 0.2f});

        Uniform smokeUni = shader.getUniform("smokeUni");
        if (smokeUni != null) smokeUni.set(0);
        int segments = 64;
        int rings = 30;
        float baseRadius = 8f;
        float topRadius = 1.5f;
        float height = SimpleCloudsConfig.CLIENT.cloudHeight.get();

// 1) Shape parameters (must match your JSON defaults or tweak here)
        var baseRadUni = shader.getUniform("BaseRadius");
        if (baseRadUni != null) baseRadUni.set(baseRadius);
        var topRadUni  = shader.getUniform("TopRadius");
        if (topRadUni  != null) topRadUni.set(topRadius);
        var heightUni  = shader.getUniform("Height");
        if (heightUni  != null) heightUni.set(height);

// 2) Dust & core
        var dustUni = shader.getUniform("DustIntensity");
        if (dustUni != null) dustUni.set(0.5f);         // or your desired value
        var coreUni = shader.getUniform("CoreTightness");
        if (coreUni != null) coreUni.set(0.5f);         // or your desired value

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);



//        for (int i = rings - 1; i >= 0; i--) {
//            float y0 = i * (height / rings);
//            float y1 = (i + 1) * (height / rings);
//
//            float radius0 = baseRadius - (baseRadius - topRadius) * (i / (float) rings);
//            float radius1 = baseRadius - (baseRadius - topRadius) * ((i + 1f) / rings);
//
//            for (int j = 0; j < segments; j++) {
//                float u0 = j / (float) segments;
//                float u1 = (j + 1f) / (float) segments;
//                float angle0 = (float) (2 * Math.PI * u0);
//                float angle1 = (float) (2 * Math.PI * u1);
//
//                float x00 = (float) (radius0 * Math.cos(angle0));
//                float z00 = (float) (radius0 * Math.sin(angle0));
//                float x01 = (float) (radius0 * Math.cos(angle1));
//                float z01 = (float) (radius0 * Math.sin(angle1));
//                float x10 = (float) (radius1 * Math.cos(angle0));
//                float z10 = (float) (radius1 * Math.sin(angle0));
//                float x11 = (float) (radius1 * Math.cos(angle1));
//                float z11 = (float) (radius1 * Math.sin(angle1));
//
//                float v0 = y0 / height;
//                float v1 = y1 / height;
//
//                buffer.vertex( x00, y0, z00).uv(u0, v0).endVertex();
//                buffer.vertex( x10, y1, z10).uv(u0, v1).endVertex();
//                buffer.vertex( x11, y1, z11).uv(u1, v1).endVertex();
//
//                buffer.vertex(x00, y0, z00).uv(u0, v0).endVertex();
//                buffer.vertex( x11, y1, z11).uv(u1, v1).endVertex();
//                buffer.vertex( x01, y0, z01).uv(u1, v0).endVertex();
//            }
//        }
        buffer.vertex(0, 100, 0).uv(0, 0).endVertex();
        buffer.vertex(10, 100, 0).uv(1, 0).endVertex();
        buffer.vertex(10, 110, 0).uv(1, 1).endVertex();

        buffer.vertex(0, 100, 0).uv(0, 0).endVertex();
        buffer.vertex(10, 110, 0).uv(1, 1).endVertex();
        buffer.vertex(0, 110, 0).uv(0, 1).endVertex();

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

