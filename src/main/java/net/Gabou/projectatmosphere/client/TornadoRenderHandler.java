package net.Gabou.projectatmosphere.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.modules.tornado.TornadoLevel;
import net.Gabou.projectatmosphere.particles.DebrisParticleData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.opengl.GL11C.GL_BACK;
import static org.lwjgl.opengl.GL11C.glCullFace;

public class TornadoRenderHandler {

    private static final ResourceLocation NOISE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("projectatmosphere", "textures/effects/noise.png");
    private static final ResourceLocation TORNADO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("projectatmosphere", "textures/effects/base.png");
    private static final ResourceLocation FLOWMAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("projectatmosphere", "textures/effects/flowmap.png");
    private static final ResourceLocation NORMALMAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("projectatmosphere", "textures/effects/tornado_normal.png");

    private static final float SPAWN_DESCENT_DURATION = 10.0f;


    public static void renderTornado(PoseStack stack, double tornadoX, double tornadoY, double tornadoZ, float twistSpeed, ClientLevel level, Camera camera, Minecraft minecraft, TornadoInstance tornado) {
        ShaderInstance shader = MyShaders.TORNADO;
        if (shader == null) return;

//        // Bind the tornado’s base texture: prefer SimpleClouds’ cloud color target, fallback to static texture
//        AtomicBoolean boundBaseToClouds = new AtomicBoolean(false);
//        SimpleCloudsRenderer.getOptionalInstance().ifPresent(scr -> {
//            RenderTarget cloudRT = scr.getCloudTarget(); // offscreen clouds color
//            if (cloudRT == null) {
//                ProjectAtmosphere.LOGGER.warn("Cloud render target is null, cannot bind clouds as tornado base texture.");
//                return;
//            }
//            // Replace the base sampler (Sampler0) with the cloud texture and also expose it as CloudScene
//            shader.setSampler("Sampler0", cloudRT);
//            shader.setSampler("CloudScene", cloudRT);
//
//            // Pass size so we can compute screen-space UVs in the shader
//            Uniform u = shader.getUniform("ScreenSizeX");
//            Uniform u1 = shader.getUniform("ScreenSizeY");
//            if (u != null && u1 != null) {
//                u.set((float) cloudRT.width);
//                u1.set((float) cloudRT.height);
//            }
//            boundBaseToClouds.set(true);
//        });
//
//        if (!boundBaseToClouds.get()) {
        // Bind the tornado base texture: prefer SimpleClouds' cloud color target, fallback to static texture
        AtomicBoolean boundBaseToClouds = new AtomicBoolean(false);
        SimpleCloudsRenderer.getOptionalInstance().ifPresent(scr -> {
            RenderTarget cloudRT = scr.getCloudTarget();
            if (cloudRT == null) {
                ProjectAtmosphere.LOGGER.warn("Cloud render target is null, cannot bind clouds as tornado base texture.");
                return;
            }
            shader.setSampler("Sampler0", cloudRT);
            shader.setSampler("CloudScene", cloudRT);

            Uniform u = shader.getUniform("ScreenSizeX");
            Uniform u1 = shader.getUniform("ScreenSizeY");
            if (u != null && u1 != null) {
                u.set((float) cloudRT.width);
                u1.set((float) cloudRT.height);
            }
            boundBaseToClouds.set(true);
        });

        if (!boundBaseToClouds.get()) {
            RenderSystem.setShaderTexture(0, TORNADO_TEXTURE);
            RenderSystem.setShaderTexture(4, TORNADO_TEXTURE);
        }
        RenderSystem.setShaderTexture(1, FLOWMAP_TEXTURE);
        RenderSystem.setShaderTexture(2, NORMALMAP_TEXTURE);
        RenderSystem.setShaderTexture(3, NOISE_TEXTURE);
        RenderSystem.setShader(() -> shader);
        shader.apply();
        int segments = 64;
        int rings = 128;
        float baseRadius = 20f;
        float topRadius = 5f;
        float height = 356f;
        stack.pushPose();
        stack.translate(tornadoX, tornadoY, tornadoZ);

        Matrix4f matrix = stack.last().pose();


        var modelView = shader.getUniform("ModelViewMat");
        if (modelView != null) modelView.set(matrix);

        var projMat = shader.getUniform("ProjMat");
        if (projMat != null) projMat.set(RenderSystem.getProjectionMatrix());

        var timeUniform = shader.getUniform("Time");
        if (timeUniform != null) timeUniform.set(TornadoManager.getShaderTime());

        var twistUniform = shader.getUniform("TwistSpeed");
        if (twistUniform != null) twistUniform.set(twistSpeed);

        var baseRadiusUniform = shader.getUniform("BaseRadius");
        if (baseRadiusUniform != null) baseRadiusUniform.set(baseRadius);

        var topRadiusUniform = shader.getUniform("TopRadius");
        if (topRadiusUniform != null) topRadiusUniform.set(topRadius);

        var heightUniform = shader.getUniform("Height");
        if (heightUniform != null) heightUniform.set(height);


        var dustUniform = shader.getUniform("DustIntensity");
        if (dustUniform != null) dustUniform.set(0.5F);

        var coreUniform = shader.getUniform("CoreTightness");
        if (coreUniform != null) coreUniform.set(0.2f);

        var flowIntensity = shader.getUniform("FlowIntensity");
        if (flowIntensity != null) flowIntensity.set(0.1f);

        var scaleUniform = shader.getUniform("Scale");
        if (scaleUniform != null) {
            float scale = (float) (tornado.getLevel().getBaseDamage() / TornadoLevel.F1.getBaseDamage());
            scaleUniform.set(scale);
        }


        float partialTicks = minecraft.getFrameTime();
        float sunAngle = level.getTimeOfDay(partialTicks);
        float angle = sunAngle * ((float) Math.PI * 2.0F);


        float xLight = Mth.cos(angle);
        float yLight = Mth.sin(angle);
        float zLight = 0.2f;


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


        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);


        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);


        float windSpeed = tornado.wind.gustSpeed();
        float windAngleDeg = tornado.wind.angleRadians();
        float windAngleRad = (float) Math.toRadians(windAngleDeg);

        double windX = Math.cos(windAngleRad) * windSpeed;
        double windZ = Math.sin(windAngleRad) * windSpeed;

        Vec3 horizontalWind = new Vec3(windX, 5, windZ);


        float spawnProgress = Mth.clamp(tornado.getLifetimeSeconds() / SPAWN_DESCENT_DURATION, 0f, 1f);
        float cutoffY = height * (1.0f - spawnProgress);

        float time = TornadoManager.getShaderTime();

        for (int i = rings - 1; i >= 0; i--) {
            float y0 = i * (height / rings);
            float y1 = (i + 1) * (height / rings);
            if (y1 < cutoffY) {
                break;
            }
            if (y0 < cutoffY) {
                y0 = cutoffY;
            }

            float t0 = y0 / height;
            float t1 = y1 / height;


            for (int j = 0; j < segments; j++) {
                float u0 = j / (float) segments;
                float u1 = (j + 1f) / segments;

                final float U_EPS = 1e-6f;
                float u0s = (j == 0) ? (u0 + U_EPS) : u0;
                float u1s = (j == segments - 1) ? (1f - U_EPS) : u1;

                float twist = (float) (Math.PI * 3.5);
                float angleOffset0 = twist * (1 - t0);
                float angleOffset1 = twist * (1 - t1);

                float angle0_0 = (float) (2 * Math.PI * u0 + angleOffset0);
                float angle0_1 = (float) (2 * Math.PI * u1 + angleOffset0);
                float angle1_0 = (float) (2 * Math.PI * u0 + angleOffset1);
                float angle1_1 = (float) (2 * Math.PI * u1 + angleOffset1);


                float x00 = tornadoShapeRadius(y0, angle0_0, time) * (float) Math.cos(angle0_0);
                float z00 = tornadoShapeRadius(y0, angle0_0, time) * (float) Math.sin(angle0_0);
                float x01 = tornadoShapeRadius(y0, angle0_1, time) * (float) Math.cos(angle0_1);
                float z01 = tornadoShapeRadius(y0, angle0_1, time) * (float) Math.sin(angle0_1);
                float x10 = tornadoShapeRadius(y1, angle1_0, time) * (float) Math.cos(angle1_0);
                float z10 = tornadoShapeRadius(y1, angle1_0, time) * (float) Math.sin(angle1_0);
                float x11 = tornadoShapeRadius(y1, angle1_1, time) * (float) Math.cos(angle1_1);
                float z11 = tornadoShapeRadius(y1, angle1_1, time) * (float) Math.sin(angle1_1);


                float wiggleFreq = 5f;
                float wiggleAmp = 0.5f;

                x00 += Math.sin(y0 * 0.1f + angle0_0 * wiggleFreq) * wiggleAmp;
                z00 += Math.cos(y0 * 0.1f + angle0_0 * wiggleFreq) * wiggleAmp;
                x01 += Math.sin(y0 * 0.1f + angle0_1 * wiggleFreq) * wiggleAmp;
                z01 += Math.cos(y0 * 0.1f + angle0_1 * wiggleFreq) * wiggleAmp;
                x10 += Math.sin(y1 * 0.1f + angle1_0 * wiggleFreq) * wiggleAmp;
                z10 += Math.cos(y1 * 0.1f + angle1_0 * wiggleFreq) * wiggleAmp;
                x11 += Math.sin(y1 * 0.1f + angle1_1 * wiggleFreq) * wiggleAmp;
                z11 += Math.cos(y1 * 0.1f + angle1_1 * wiggleFreq) * wiggleAmp;

                float epsilon = 0.0001f;
                float v0 = (y0 + epsilon) / height;
                float v1 = (y1 - epsilon) / height;

                float bendScale = 1.5f;
                float bendFactor0 = (y0 / height) * bendScale * windSpeed;
                float bendFactor1 = (y1 / height) * bendScale * windSpeed;

                float offsetX0 = (float) horizontalWind.x * bendFactor0;
                float offsetZ0 = (float) horizontalWind.z * bendFactor0;
                float offsetX1 = (float) horizontalWind.x * bendFactor1;
                float offsetZ1 = (float) horizontalWind.z * bendFactor1;

                x00 += offsetX0;
                z00 += offsetZ0;
                x01 += offsetX0;
                z01 += offsetZ0;
                x10 += offsetX1;
                z10 += offsetZ1;
                x11 += offsetX1;
                z11 += offsetZ1;


                buffer.vertex(matrix, x00, y0, z00).uv(u0s, v0).endVertex();
                buffer.vertex(matrix, x10, y1, z10).uv(u0s, v1).endVertex();
                buffer.vertex(matrix, x11, y1, z11).uv(u1s, v1).endVertex();

                buffer.vertex(matrix, x00, y0, z00).uv(u0s, v0).endVertex();
                buffer.vertex(matrix, x11, y1, z11).uv(u1s, v1).endVertex();
                buffer.vertex(matrix, x01, y0, z01).uv(u1s, v0).endVertex();
            }
        }


// --- Cone-like bowl (frustum) above the top ---
        int bowlRings = 24;     // mesh resolution
        float bowlHeight = 12f;   // vertical size of the cap
        topRadius=topRadius-3f;
        float angleDeg   = 18f;   // or:
        float targetTopR = topRadius * 1.6f; // or:
        float factor     = 1.6f;
        float p          = 1.0f;  // 1 = straight cone; 1.05–1.2 = cone-ish but slightly rounded

// radius grows linearly with height → cone look
// slope = how many blocks of radius per 1 block of height
        float flareSlope = 0.30f; // tune: 0.2 = subtle, 0.5 = wide cone
        float maxBowlRadius = topRadius + flareSlope * bowlHeight;

        for (int i = 0; i < bowlRings; i++) {
            float t0 = i / (float) bowlRings;
            float t1 = (i + 1f) / bowlRings;

            float y0 = height + t0 * bowlHeight;
            float y1 = height + t1 * bowlHeight;

            // Pure conical growth (straight sides)
            float r0 = coneRadiusByAngle(y0, height, topRadius, bowlHeight, angleDeg, p);
            float r1 = coneRadiusByAngle(y1, height, topRadius, bowlHeight, angleDeg, p);

            // OPTIONAL: "rounded cone" (very slight curvature, still cone-ish)
            // float p = 1.10f; // 1.0 = pure cone, >1 = even straighter near seam
            // r0 = topRadius + (maxBowlRadius - topRadius) * (float) Math.pow(t0, p);
            // r1 = topRadius + (maxBowlRadius - topRadius) * (float) Math.pow(t1, p);

            // Keep twist continuity with the funnel
            float twist = (float) (Math.PI * 3.5);
            float aOff0 = twist * (1f - Math.min(1f, y0 / height));
            float aOff1 = twist * (1f - Math.min(1f, y1 / height));

            for (int j = 0; j < segments; j++) {
                float u0 = j / (float) segments;
                float u1 = (j + 1f) / (float) segments;

                float a00 = (float) (2 * Math.PI * u0 + aOff0);
                float a01 = (float) (2 * Math.PI * u1 + aOff0);
                float a10 = (float) (2 * Math.PI * u0 + aOff1);
                float a11 = (float) (2 * Math.PI * u1 + aOff1);

                float x00 = r0 * (float) Math.cos(a00);
                float z00 = r0 * (float) Math.sin(a00);
                float x01 = r0 * (float) Math.cos(a01);
                float z01 = r0 * (float) Math.sin(a01);
                float x10 = r1 * (float) Math.cos(a10);
                float z10 = r1 * (float) Math.sin(a10);
                float x11 = r1 * (float) Math.cos(a11);
                float z11 = r1 * (float) Math.sin(a11);

                // Optional: same bend so the bowl leans with wind
                float b0 = (y0 / (height + bowlHeight)) * 1.5f * windSpeed;
                float b1 = (y1 / (height + bowlHeight)) * 1.5f * windSpeed;
                x00 += (float) horizontalWind.x * b0;
                z00 += (float) horizontalWind.z * b0;
                x01 += (float) horizontalWind.x * b0;
                z01 += (float) horizontalWind.z * b0;
                x10 += (float) horizontalWind.x * b1;
                z10 += (float) horizontalWind.z * b1;
                x11 += (float) horizontalWind.x * b1;
                z11 += (float) horizontalWind.z * b1;

                // Clamp V near 1 to avoid stretching if your shader expects 0..1
                float epsilon = 1e-4f;
                float v0 = Math.min(1f - epsilon, y0 / height);
                float v1 = Math.min(1f - epsilon, y1 / height);

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
            double maxRadius = 16.0;
            double radius = Math.sqrt(level.random.nextDouble()) * maxRadius;
            double height = level.random.nextDouble() * SimpleCloudsConfig.CLIENT.cloudHeight.get();
            float angularSpeed = 4f;

            level.addParticle(new DebrisParticleData(tornado, radius, height, angularSpeed),
                    tornado.position.x, tornado.position.y, tornado.position.z, 0, 0.01, 0);
        }
    }



    private static float tornadoShapeRadius(float y, float angle, float time) {
        float yAdj = y + 45.0f;
        float zcurve = (float) Math.pow(yAdj, 1.5f) * 0.03f;
        float base = zcurve + 5.5f;
        float scale = Mth.clamp(zcurve * 0.2f, 0.1f, 1.0f);
        float radius = base + scale * Mth.sin((time - Mth.sqrt(yAdj)) + angle) * 5.0f;
        float ridgedNoise = 1.0f - 2.0f * Math.abs(Mth.sin((time * 1.5f + 0.1f * yAdj) + angle));
        radius -= ridgedNoise * 1.2f;
        return radius;
    }

    /**
     * Cone cap radius by specifying a *cone angle* (degrees).
     * angleDeg = 0..89 (slope = tan(angleDeg))
     * p = 1 for pure cone; >1 slightly round; <1 flares faster.
     */
    static float coneRadiusByAngle(float y, float seamY, float topRadius, float bowlHeight,
                                   float angleDeg, float p) {
        float t = Mth.clamp((y - seamY) / bowlHeight, 0f, 1f);
        float slope = (float) Math.tan(Math.toRadians(angleDeg));
        float targetR = topRadius + slope * bowlHeight;
        float linear = topRadius + (targetR - topRadius) * t;
        return p == 1f ? linear : topRadius + (targetR - topRadius) * (float) Math.pow(t, p);
    }


}

