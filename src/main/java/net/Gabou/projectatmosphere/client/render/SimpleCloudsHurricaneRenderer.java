package net.Gabou.projectatmosphere.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.nonamecrackers2.simpleclouds.client.framebuffer.WeightedBlendingTarget;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import net.Gabou.projectatmosphere.client.render.shader.HurricaneShaders;
import net.Gabou.projectatmosphere.client.hurricane.cache.ClientHurricaneStateCache;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneCloudVolume;
import net.Gabou.projectatmosphere.client.render.mesh.VolumeBoxMesh;
import net.Gabou.projectatmosphere.tools.debug.HurricaneRenderDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;

import java.util.ArrayList;
import java.util.List;

public final class SimpleCloudsHurricaneRenderer {
    public static final SimpleCloudsHurricaneRenderer INSTANCE = new SimpleCloudsHurricaneRenderer();

    private static final int MAX_STORMS = 4;
    private static final float MAX_RAY_DISTANCE_CLOUD = 1100.0F;

    private ClientLevel preparedLevel;
    private long preparedGameTime = Long.MIN_VALUE;
    private float preparedPartialTick = Float.NaN;
    private final List<HurricaneCloudVolume> preparedHurricanes = new ArrayList<>();
    private boolean initialized;
    private VertexBuffer fullscreenQuad;
    private final VolumeBoxMesh volumeBox = new VolumeBoxMesh();
    private TextureTarget opaqueScratchTarget;
    private WeightedBlendingTarget transparencyScratchTarget;

    private SimpleCloudsHurricaneRenderer() {
    }

    public void prepareFrame(ClientLevel level, float partialTick) {
        if (this.preparedLevel == level
                && this.preparedGameTime == level.getGameTime()
                && Float.compare(this.preparedPartialTick, partialTick) == 0) {
            HurricaneRenderDiagnostics.markPrepareCacheHit();
            return;
        }

        this.ensureInitialized();
        List<ClientHurricaneStateCache.RenderableHurricane> renderables = ClientHurricaneStateCache.getRenderableHurricanes(partialTick);
        this.preparedLevel = level;
        this.preparedGameTime = level.getGameTime();
        this.preparedPartialTick = partialTick;
        this.preparedHurricanes.clear();
        if (renderables.isEmpty()) {
            HurricaneRenderDiagnostics.clear();
            return;
        }
        HurricaneRenderDiagnostics.beginFrame(level.getGameTime(), renderables.size(), Math.min(renderables.size(), MAX_STORMS));
        long prepareStartNs = HurricaneRenderDiagnostics.nowNs();

        for (ClientHurricaneStateCache.RenderableHurricane hurricane : renderables) {
            if (this.preparedHurricanes.size() >= MAX_STORMS) {
                break;
            }
            this.preparedHurricanes.add(HurricaneCloudVolume.from(hurricane, partialTick));
        }
        HurricaneRenderDiagnostics.recordPrepareCpuTime(prepareStartNs);
    }

    public void renderOpaque(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                             float partialTick, float cloudR, float cloudG, float cloudB) {
        if (this.preparedHurricanes.isEmpty() || !HurricaneShaders.isOpaqueReady()) {
            if (!HurricaneShaders.isTransparencyReady()) {
                HurricaneRenderDiagnostics.finishFrame();
            }
            return;
        }

        long scratchStartNs = HurricaneRenderDiagnostics.nowNs();
        this.ensureScratchTargets(renderer);
        HurricaneRenderDiagnostics.recordScratchCpuTime(scratchStartNs);
        StormUniforms uniforms = StormUniforms.from(this.preparedHurricanes);

        long passStartNs = HurricaneRenderDiagnostics.nowNs();
        this.runOpaqueEyeMaskPass(stack, projMat, uniforms, this.opaqueScratchTarget,
                renderer.getCloudTarget().getColorTextureId(), renderer.getCloudTarget().getDepthTextureId(), false);
        this.runOpaqueEyeMaskPass(stack, projMat, uniforms, renderer.getCloudTarget(),
                this.opaqueScratchTarget.getColorTextureId(), this.opaqueScratchTarget.getDepthTextureId(), true);
        HurricaneRenderDiagnostics.recordOpaqueMaskCpuTime(passStartNs);

        passStartNs = HurricaneRenderDiagnostics.nowNs();
        this.runOpaqueVolumePass(renderer, stack, projMat, partialTick, cloudR, cloudG, cloudB);
        HurricaneRenderDiagnostics.recordOpaqueVolumeCpuTime(passStartNs);

        if (!HurricaneShaders.isTransparencyReady()) {
            HurricaneRenderDiagnostics.finishFrame();
        }
    }

    public void renderTransparency(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                                   float partialTick, float cloudR, float cloudG, float cloudB) {
        if (this.preparedHurricanes.isEmpty() || !HurricaneShaders.isTransparencyReady()) {
            HurricaneRenderDiagnostics.finishFrame();
            return;
        }

        long scratchStartNs = HurricaneRenderDiagnostics.nowNs();
        this.ensureScratchTargets(renderer);
        HurricaneRenderDiagnostics.recordScratchCpuTime(scratchStartNs);
        StormUniforms uniforms = StormUniforms.from(this.preparedHurricanes);

        long passStartNs = HurricaneRenderDiagnostics.nowNs();
        this.runTransparencyMaskPass(stack, projMat, uniforms, this.transparencyScratchTarget,
                renderer.getCloudTransparencyTarget().getColorTextureId(),
                renderer.getCloudTransparencyTarget().getRevealageTextureId(),
                renderer.getCloudTransparencyTarget().getDepthTextureId(),
                false);
        this.runTransparencyMaskPass(stack, projMat, uniforms, renderer.getCloudTransparencyTarget(),
                this.transparencyScratchTarget.getColorTextureId(),
                this.transparencyScratchTarget.getRevealageTextureId(),
                this.transparencyScratchTarget.getDepthTextureId(),
                true);
        HurricaneRenderDiagnostics.recordTransparencyMaskCpuTime(passStartNs);

        passStartNs = HurricaneRenderDiagnostics.nowNs();
        this.runTransparencyVolumePass(renderer, stack, projMat, partialTick, cloudR, cloudG, cloudB);
        HurricaneRenderDiagnostics.recordTransparencyVolumeCpuTime(passStartNs);
        HurricaneRenderDiagnostics.finishFrame();
    }

    public void close() {
        this.preparedLevel = null;
        this.preparedGameTime = Long.MIN_VALUE;
        this.preparedPartialTick = Float.NaN;
        this.preparedHurricanes.clear();
        if (this.fullscreenQuad != null) {
            this.fullscreenQuad.close();
            this.fullscreenQuad = null;
        }
        this.volumeBox.close();
        if (this.opaqueScratchTarget != null) {
            this.opaqueScratchTarget.destroyBuffers();
            this.opaqueScratchTarget = null;
        }
        if (this.transparencyScratchTarget != null) {
            this.transparencyScratchTarget.destroyBuffers();
            this.transparencyScratchTarget = null;
        }
        HurricaneRenderDiagnostics.clear();
        this.initialized = false;
    }

    private void ensureInitialized() {
        if (this.initialized) {
            return;
        }
        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
        builder.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
        builder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
        builder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);
        this.fullscreenQuad = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.fullscreenQuad.bind();
        this.fullscreenQuad.upload(builder.buildOrThrow());
        VertexBuffer.unbind();
        this.initialized = true;
    }

    private void ensureScratchTargets(SimpleCloudsRenderer renderer) {
        RenderTarget cloudTarget = renderer.getCloudTarget();
        if (this.opaqueScratchTarget == null
                || this.opaqueScratchTarget.width != cloudTarget.width
                || this.opaqueScratchTarget.height != cloudTarget.height) {
            if (this.opaqueScratchTarget != null) {
                this.opaqueScratchTarget.destroyBuffers();
            }
            this.opaqueScratchTarget = new TextureTarget(cloudTarget.width, cloudTarget.height, true, Minecraft.ON_OSX);
        }

        WeightedBlendingTarget transparencyTarget = renderer.getCloudTransparencyTarget();
        if (this.transparencyScratchTarget == null
                || this.transparencyScratchTarget.width != transparencyTarget.width
                || this.transparencyScratchTarget.height != transparencyTarget.height) {
            if (this.transparencyScratchTarget != null) {
                this.transparencyScratchTarget.destroyBuffers();
            }
            this.transparencyScratchTarget = new WeightedBlendingTarget(
                    transparencyTarget.width,
                    transparencyTarget.height,
                    true,
                    false
            );
        }
    }

    private void runOpaqueEyeMaskPass(PoseStack stack, Matrix4f projMat, StormUniforms uniforms,
                                      RenderTarget destination, int sourceColorTextureId, int sourceDepthTextureId,
                                      boolean protectionEnabled) {
        ShaderInstance shader = HurricaneShaders.getOpaqueMaskShader();
        if (shader == null) {
            return;
        }

        destination.bindWrite(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(() -> shader);

        shader.setSampler("SourceColorSampler", sourceColorTextureId);
        shader.setSampler("SourceDepthSampler", sourceDepthTextureId);
        this.applyEyeMaskCommonUniforms(shader, stack, projMat);
        this.applyEyeMaskStormUniforms(shader, uniforms);
        shader.safeGetUniform("ProtectionEnabled").set(protectionEnabled ? 1 : 0);
        shader.apply();

        this.fullscreenQuad.bind();
        this.fullscreenQuad.drawWithShader(new Matrix4f(), new Matrix4f(), shader);
        VertexBuffer.unbind();
        shader.clear();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    private void runTransparencyMaskPass(PoseStack stack, Matrix4f projMat, StormUniforms uniforms,
                                         WeightedBlendingTarget destination, int sourceAccumTextureId,
                                         int sourceRevealageTextureId, int sourceDepthTextureId,
                                         boolean protectionEnabled) {
        ShaderInstance shader = HurricaneShaders.getTransparencyMaskShader();
        if (shader == null) {
            return;
        }

        destination.bindWrite(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(() -> shader);

        shader.setSampler("SourceAccumSampler", sourceAccumTextureId);
        shader.setSampler("SourceRevealageSampler", sourceRevealageTextureId);
        shader.setSampler("SourceDepthSampler", sourceDepthTextureId);
        this.applyEyeMaskCommonUniforms(shader, stack, projMat);
        this.applyEyeMaskStormUniforms(shader, uniforms);
        shader.safeGetUniform("ProtectionEnabled").set(protectionEnabled ? 1 : 0);
        shader.apply();

        this.fullscreenQuad.bind();
        this.fullscreenQuad.drawWithShader(new Matrix4f(), new Matrix4f(), shader);
        VertexBuffer.unbind();
        shader.clear();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    private void runOpaqueVolumePass(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                                     float partialTick, float cloudR, float cloudG, float cloudB) {
        ShaderInstance shader = HurricaneShaders.getOpaqueShader();
        if (shader == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        renderer.getCloudTarget().bindWrite(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        AbstractTexture baseTexture = mc.getTextureManager().getTexture(HurricaneShaders.BASE_TEXTURE);
        AbstractTexture noiseTexture = mc.getTextureManager().getTexture(HurricaneShaders.NOISE_TEXTURE);
        AbstractTexture flowTexture = mc.getTextureManager().getTexture(HurricaneShaders.FLOW_TEXTURE);
        shader.setSampler("BaseSampler", baseTexture);
        shader.setSampler("NoiseSampler", noiseTexture);
        shader.setSampler("FlowSampler", flowTexture);
        // The opaque eye-mask ping-pong immediately before this pass leaves an
        // exact, detached copy of the cloud target depth in opaqueScratchTarget.
        // Sampling cloudTarget's attached depth while writing cloudTarget is an
        // OpenGL feedback loop with undefined results on every driver.
        shader.setSampler("DepthSampler", this.opaqueScratchTarget.getDepthTextureId());

        this.applyCommonUniforms(shader, renderer, stack, projMat);
        shader.safeGetUniform("CloudColor").set(cloudR, cloudG, cloudB, 1.0F);
        List<HurricaneCloudVolume> renderOrder = new ArrayList<>(this.preparedHurricanes);
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        renderOrder.sort((left, right) -> Double.compare(
                right.centerWorld().distanceToSqr(cameraPos),
                left.centerWorld().distanceToSqr(cameraPos)
        ));

        for (HurricaneCloudVolume hurricane : renderOrder) {
            this.applySingleStormUniforms(shader, hurricane);
            shader.safeGetUniform("VolumeMin").set(
                    (float) hurricane.boundsMinCloud().x,
                    (float) hurricane.boundsMinCloud().y,
                    (float) hurricane.boundsMinCloud().z
            );
            shader.safeGetUniform("VolumeMax").set(
                    (float) hurricane.boundsMaxCloud().x,
                    (float) hurricane.boundsMaxCloud().y,
                    (float) hurricane.boundsMaxCloud().z
            );
            shader.apply();
            this.volumeBox.draw(shader, stack.last().pose(), projMat);
            shader.clear();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private void runTransparencyVolumePass(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                                           float partialTick, float cloudR, float cloudG, float cloudB) {
        ShaderInstance shader = HurricaneShaders.getTransparencyShader();
        if (shader == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        renderer.getCloudTransparencyTarget().bindWrite(false);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);

        AbstractTexture baseTexture = mc.getTextureManager().getTexture(HurricaneShaders.BASE_TEXTURE);
        AbstractTexture noiseTexture = mc.getTextureManager().getTexture(HurricaneShaders.NOISE_TEXTURE);
        AbstractTexture flowTexture = mc.getTextureManager().getTexture(HurricaneShaders.FLOW_TEXTURE);
        shader.setSampler("BaseSampler", baseTexture);
        shader.setSampler("NoiseSampler", noiseTexture);
        shader.setSampler("FlowSampler", flowTexture);
        shader.setSampler("DepthSampler", renderer.getCloudTarget().getDepthTextureId());

        this.applyCommonUniforms(shader, renderer, stack, projMat);
        shader.safeGetUniform("CloudColor").set(cloudR, cloudG, cloudB, 1.0F);

        GL30.glEnablei(GL11.GL_BLEND, 0);
        GL30.glEnablei(GL11.GL_BLEND, 1);
        GL40.glBlendEquationi(0, GL14.GL_FUNC_ADD);
        GL40.glBlendEquationi(1, GL14.GL_FUNC_ADD);
        GL40.glBlendFunci(0, GL11.GL_ONE, GL11.GL_ONE);
        GL40.glBlendFunci(1, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR);

        List<HurricaneCloudVolume> renderOrder = new ArrayList<>(this.preparedHurricanes);
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        renderOrder.sort((left, right) -> Double.compare(
                right.centerWorld().distanceToSqr(cameraPos),
                left.centerWorld().distanceToSqr(cameraPos)
        ));

        for (HurricaneCloudVolume hurricane : renderOrder) {
            this.applySingleStormUniforms(shader, hurricane);
            shader.safeGetUniform("VolumeMin").set(
                    (float) hurricane.boundsMinCloud().x,
                    (float) hurricane.boundsMinCloud().y,
                    (float) hurricane.boundsMinCloud().z
            );
            shader.safeGetUniform("VolumeMax").set(
                    (float) hurricane.boundsMaxCloud().x,
                    (float) hurricane.boundsMaxCloud().y,
                    (float) hurricane.boundsMaxCloud().z
            );
            shader.apply();
            this.volumeBox.draw(shader, stack.last().pose(), projMat);
            shader.clear();
        }

        GL30.glDisablei(GL11.GL_BLEND, 0);
        GL30.glDisablei(GL11.GL_BLEND, 1);
        GL40.glBlendFuncSeparatei(0, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL40.glBlendFuncSeparatei(1, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private void applyCommonUniforms(ShaderInstance shader, SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat) {
        Minecraft mc = Minecraft.getInstance();
        shader.safeGetUniform("ModelViewMat").set(stack.last().pose());
        shader.safeGetUniform("ProjMat").set(projMat);
        shader.safeGetUniform("InverseProjMat").set(new Matrix4f(projMat).invert());
        shader.safeGetUniform("InverseModelViewMat").set(new Matrix4f(stack.last().pose()).invert());

        float scale = SimpleCloudsConstants.CLOUD_SCALE;
        float cloudHeight = this.preparedLevel == null ? 0.0F : dev.nonamecrackers2.simpleclouds.common.world.CloudManager.get(this.preparedLevel).getCloudHeight();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        shader.safeGetUniform("CameraPos").set(
                (float) cameraPos.x / scale,
                ((float) cameraPos.y - cloudHeight) / scale,
                (float) cameraPos.z / scale
        );
        shader.safeGetUniform("AnimationTime").set((this.preparedGameTime + this.preparedPartialTick) * 0.065F);
        shader.safeGetUniform("MaxDistance").set(MAX_RAY_DISTANCE_CLOUD);
        shader.safeGetUniform("OutSize").set((float) mc.getWindow().getWidth(), (float) mc.getWindow().getHeight());
        shader.safeGetUniform("FogStart").set(renderer.getFogStart());
        shader.safeGetUniform("FogEnd").set(renderer.getFogEnd());
        float[] fogColor = RenderSystem.getShaderFogColor();
        shader.safeGetUniform("FogColor").set(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
    }

    private void applyEyeMaskCommonUniforms(ShaderInstance shader, PoseStack stack, Matrix4f projMat) {
        Minecraft mc = Minecraft.getInstance();
        shader.safeGetUniform("InverseProjMat").set(new Matrix4f(projMat).invert());
        shader.safeGetUniform("InverseModelViewMat").set(new Matrix4f(stack.last().pose()).invert());

        float scale = SimpleCloudsConstants.CLOUD_SCALE;
        float cloudHeight = this.preparedLevel == null ? 0.0F : dev.nonamecrackers2.simpleclouds.common.world.CloudManager.get(this.preparedLevel).getCloudHeight();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        shader.safeGetUniform("CameraPos").set(
                (float) cameraPos.x / scale,
                ((float) cameraPos.y - cloudHeight) / scale,
                (float) cameraPos.z / scale
        );
        shader.safeGetUniform("MaxDistance").set(MAX_RAY_DISTANCE_CLOUD);
    }

    private void applyEyeMaskStormUniforms(ShaderInstance shader, StormUniforms uniforms) {
        shader.safeGetUniform("StormCount").set(uniforms.stormCount());
        shader.safeGetUniform("StormPositions").set(uniforms.stormPositions());
        shader.safeGetUniform("StormHeights").set(uniforms.stormHeights());
        shader.safeGetUniform("EyeRadii").set(uniforms.eyeRadii());
        shader.safeGetUniform("EyeClearRadii").set(uniforms.eyeClearRadii());
        shader.safeGetUniform("EyeSlopes").set(uniforms.eyeSlopes());
    }

    private void applySingleStormUniforms(ShaderInstance shader, HurricaneCloudVolume hurricane) {
        float[] stormPositions = new float[MAX_STORMS * 3];
        float[] stormHeights = new float[MAX_STORMS];
        float[] eyeRadii = new float[MAX_STORMS];
        float[] eyeClearRadii = new float[MAX_STORMS];
        float[] eyeSlopes = new float[MAX_STORMS];
        float[] eyewallThicknesses = new float[MAX_STORMS];
        float[] canopyRadii = new float[MAX_STORMS];
        float[] shieldRadii = new float[MAX_STORMS];
        float[] canopyBaseFactors = new float[MAX_STORMS];
        float[] canopyTopFactors = new float[MAX_STORMS];
        float[] shieldBaseFactors = new float[MAX_STORMS];
        float[] shieldTopFactors = new float[MAX_STORMS];
        float[] bandStartRadii = new float[MAX_STORMS];
        float[] bandEndRadii = new float[MAX_STORMS];
        float[] bandWidths = new float[MAX_STORMS];
        float[] bandStrengths = new float[MAX_STORMS];
        float[] bandCounts = new float[MAX_STORMS];
        float[] fringeStrengths = new float[MAX_STORMS];
        float[] stormSpins = new float[MAX_STORMS];
        float[] stormIntensities = new float[MAX_STORMS];
        float[] stormSeeds = new float[MAX_STORMS];

        stormPositions[0] = hurricane.centerX();
        stormPositions[1] = hurricane.baseY();
        stormPositions[2] = hurricane.centerZ();
        stormHeights[0] = hurricane.height();
        eyeRadii[0] = hurricane.eyeRadius();
        eyeClearRadii[0] = hurricane.eyeClearRadius();
        eyeSlopes[0] = hurricane.eyeSlope();
        eyewallThicknesses[0] = hurricane.eyewallThickness();
        canopyRadii[0] = hurricane.canopyRadius();
        shieldRadii[0] = hurricane.shieldRadius();
        canopyBaseFactors[0] = hurricane.canopyBaseFactor();
        canopyTopFactors[0] = hurricane.canopyTopFactor();
        shieldBaseFactors[0] = hurricane.shieldBaseFactor();
        shieldTopFactors[0] = hurricane.shieldTopFactor();
        bandStartRadii[0] = hurricane.bandStartRadius();
        bandEndRadii[0] = hurricane.bandEndRadius();
        bandWidths[0] = hurricane.bandWidth();
        bandStrengths[0] = hurricane.bandStrength();
        bandCounts[0] = hurricane.bandCount();
        fringeStrengths[0] = hurricane.fringeStrength();
        stormSpins[0] = hurricane.spin();
        stormIntensities[0] = hurricane.intensity();
        stormSeeds[0] = hurricane.seed();

        shader.safeGetUniform("StormPositions").set(stormPositions);
        shader.safeGetUniform("StormHeights").set(stormHeights);
        shader.safeGetUniform("EyeRadii").set(eyeRadii);
        shader.safeGetUniform("EyeClearRadii").set(eyeClearRadii);
        shader.safeGetUniform("EyeSlopes").set(eyeSlopes);
        shader.safeGetUniform("EyewallThicknesses").set(eyewallThicknesses);
        shader.safeGetUniform("CanopyRadii").set(canopyRadii);
        shader.safeGetUniform("ShieldRadii").set(shieldRadii);
        shader.safeGetUniform("CanopyBaseFactors").set(canopyBaseFactors);
        shader.safeGetUniform("CanopyTopFactors").set(canopyTopFactors);
        shader.safeGetUniform("ShieldBaseFactors").set(shieldBaseFactors);
        shader.safeGetUniform("ShieldTopFactors").set(shieldTopFactors);
        shader.safeGetUniform("BandStartRadii").set(bandStartRadii);
        shader.safeGetUniform("BandEndRadii").set(bandEndRadii);
        shader.safeGetUniform("BandWidths").set(bandWidths);
        shader.safeGetUniform("BandStrengths").set(bandStrengths);
        shader.safeGetUniform("BandCounts").set(bandCounts);
        shader.safeGetUniform("FringeStrengths").set(fringeStrengths);
        shader.safeGetUniform("StormSpins").set(stormSpins);
        shader.safeGetUniform("StormIntensities").set(stormIntensities);
        shader.safeGetUniform("StormSeeds").set(stormSeeds);
    }

    private record StormUniforms(
            int stormCount,
            float[] stormPositions,
            float[] stormHeights,
            float[] eyeRadii,
            float[] eyeClearRadii,
            float[] eyeSlopes,
            float[] eyewallThicknesses,
            float[] canopyRadii,
            float[] shieldRadii,
            float[] canopyBaseFactors,
            float[] canopyTopFactors,
            float[] shieldBaseFactors,
            float[] shieldTopFactors,
            float[] bandStartRadii,
            float[] bandEndRadii,
            float[] bandWidths,
            float[] bandStrengths,
            float[] bandCounts,
            float[] fringeStrengths,
            float[] stormSpins,
            float[] stormIntensities,
            float[] stormSeeds
    ) {
        static StormUniforms from(List<HurricaneCloudVolume> hurricanes) {
            float[] stormPositions = newStormArray(MAX_STORMS * 3);
            float[] stormHeights = newStormArray();
            float[] eyeRadii = newStormArray();
            float[] eyeClearRadii = newStormArray();
            float[] eyeSlopes = newStormArray();
            float[] eyewallThicknesses = newStormArray();
            float[] canopyRadii = newStormArray();
            float[] shieldRadii = newStormArray();
            float[] canopyBaseFactors = newStormArray();
            float[] canopyTopFactors = newStormArray();
            float[] shieldBaseFactors = newStormArray();
            float[] shieldTopFactors = newStormArray();
            float[] bandStartRadii = newStormArray();
            float[] bandEndRadii = newStormArray();
            float[] bandWidths = newStormArray();
            float[] bandStrengths = newStormArray();
            float[] bandCounts = newStormArray();
            float[] fringeStrengths = newStormArray();
            float[] stormSpins = newStormArray();
            float[] stormIntensities = newStormArray();
            float[] stormSeeds = newStormArray();

            for (int i = 0; i < hurricanes.size(); i++) {
                HurricaneCloudVolume hurricane = hurricanes.get(i);
                stormPositions[i * 3] = hurricane.centerX();
                stormPositions[i * 3 + 1] = hurricane.baseY();
                stormPositions[i * 3 + 2] = hurricane.centerZ();
                stormHeights[i] = hurricane.height();
                eyeRadii[i] = hurricane.eyeRadius();
                eyeClearRadii[i] = hurricane.eyeClearRadius();
                eyeSlopes[i] = hurricane.eyeSlope();
                eyewallThicknesses[i] = hurricane.eyewallThickness();
                canopyRadii[i] = hurricane.canopyRadius();
                shieldRadii[i] = hurricane.shieldRadius();
                canopyBaseFactors[i] = hurricane.canopyBaseFactor();
                canopyTopFactors[i] = hurricane.canopyTopFactor();
                shieldBaseFactors[i] = hurricane.shieldBaseFactor();
                shieldTopFactors[i] = hurricane.shieldTopFactor();
                bandStartRadii[i] = hurricane.bandStartRadius();
                bandEndRadii[i] = hurricane.bandEndRadius();
                bandWidths[i] = hurricane.bandWidth();
                bandStrengths[i] = hurricane.bandStrength();
                bandCounts[i] = hurricane.bandCount();
                fringeStrengths[i] = hurricane.fringeStrength();
                stormSpins[i] = hurricane.spin();
                stormIntensities[i] = hurricane.intensity();
                stormSeeds[i] = hurricane.seed();
            }

            return new StormUniforms(
                    hurricanes.size(),
                    stormPositions,
                    stormHeights,
                    eyeRadii,
                    eyeClearRadii,
                    eyeSlopes,
                    eyewallThicknesses,
                    canopyRadii,
                    shieldRadii,
                    canopyBaseFactors,
                    canopyTopFactors,
                    shieldBaseFactors,
                    shieldTopFactors,
                    bandStartRadii,
                    bandEndRadii,
                    bandWidths,
                    bandStrengths,
                    bandCounts,
                    fringeStrengths,
                    stormSpins,
                    stormIntensities,
                    stormSeeds
            );
        }

        private static float[] newStormArray() {
            return new float[MAX_STORMS];
        }

        private static float[] newStormArray(int size) {
            return new float[size];
        }
    }
}
