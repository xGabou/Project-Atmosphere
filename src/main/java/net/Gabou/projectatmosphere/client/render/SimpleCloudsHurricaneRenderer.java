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
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneInstance;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneManager;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneRenderDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SimpleCloudsHurricaneRenderer {
    public static final SimpleCloudsHurricaneRenderer INSTANCE = new SimpleCloudsHurricaneRenderer();

    private static final int MAX_STORMS = 4;
    private static final float MAX_RAY_DISTANCE_CLOUD = 1100.0F;

    private ClientLevel preparedLevel;
    private long preparedGameTime = Long.MIN_VALUE;
    private float preparedPartialTick = Float.NaN;
    private final List<PreparedHurricane> preparedHurricanes = new ArrayList<>();
    private boolean initialized;
    private VertexBuffer fullscreenQuad;
    private TextureTarget opaqueScratchTarget;
    private WeightedBlendingTarget transparencyScratchTarget;

    private SimpleCloudsHurricaneRenderer() {
    }

    public void prepareFrame(ClientLevel level, float partialTick) {
        if (this.preparedLevel == level
                && this.preparedGameTime == level.getGameTime()
                && Float.compare(this.preparedPartialTick, partialTick) == 0) {
            return;
        }

        this.ensureInitialized();
        this.preparedLevel = level;
        this.preparedGameTime = level.getGameTime();
        this.preparedPartialTick = partialTick;
        this.preparedHurricanes.clear();

        for (HurricaneInstance hurricane : HurricaneManager.getClientHurricanes()) {
            if (this.preparedHurricanes.size() >= MAX_STORMS) {
                break;
            }
            this.preparedHurricanes.add(PreparedHurricane.from(hurricane, partialTick));
        }
    }

    public void renderOpaque(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                             float partialTick, float cloudR, float cloudG, float cloudB) {
        if (this.preparedHurricanes.isEmpty() || !HurricaneShaders.isOpaqueReady()) {
            return;
        }

        this.ensureScratchTargets(renderer);
        StormUniforms uniforms = StormUniforms.from(this.preparedHurricanes);

        this.runOpaqueEyeMaskPass(renderer, stack, projMat, uniforms, this.opaqueScratchTarget,
                renderer.getCloudTarget().getColorTextureId(), renderer.getCloudTarget().getDepthTextureId(), false);
        this.runOpaqueEyeMaskPass(renderer, stack, projMat, uniforms, renderer.getCloudTarget(),
                this.opaqueScratchTarget.getColorTextureId(), this.opaqueScratchTarget.getDepthTextureId(), true);
        this.runOpaqueVolumePass(renderer, stack, projMat, partialTick, cloudR, cloudG, cloudB, uniforms);
    }

    public void renderTransparency(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                                   float partialTick, float cloudR, float cloudG, float cloudB) {
        if (this.preparedHurricanes.isEmpty() || !HurricaneShaders.isTransparencyReady()) {
            return;
        }

        this.ensureScratchTargets(renderer);
        StormUniforms uniforms = StormUniforms.from(this.preparedHurricanes);

        this.runTransparencyMaskPass(renderer, stack, projMat, uniforms, this.transparencyScratchTarget,
                renderer.getCloudTransparencyTarget().getColorTextureId(),
                renderer.getCloudTransparencyTarget().getRevealageTextureId(),
                renderer.getCloudTransparencyTarget().getDepthTextureId(),
                false);
        this.runTransparencyMaskPass(renderer, stack, projMat, uniforms, renderer.getCloudTransparencyTarget(),
                this.transparencyScratchTarget.getColorTextureId(),
                this.transparencyScratchTarget.getRevealageTextureId(),
                this.transparencyScratchTarget.getDepthTextureId(),
                true);
        this.runTransparencyVolumePass(renderer, stack, projMat, partialTick, cloudR, cloudG, cloudB, uniforms);
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
        if (this.opaqueScratchTarget != null) {
            this.opaqueScratchTarget.destroyBuffers();
            this.opaqueScratchTarget = null;
        }
        if (this.transparencyScratchTarget != null) {
            this.transparencyScratchTarget.destroyBuffers();
            this.transparencyScratchTarget = null;
        }
        this.initialized = false;
    }

    private void ensureInitialized() {
        if (this.initialized) {
            return;
        }
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1.0F, -1.0F, 0.0F).uv(0.0F, 0.0F).endVertex();
        builder.vertex(1.0F, -1.0F, 0.0F).uv(1.0F, 0.0F).endVertex();
        builder.vertex(1.0F, 1.0F, 0.0F).uv(1.0F, 1.0F).endVertex();
        builder.vertex(-1.0F, 1.0F, 0.0F).uv(0.0F, 1.0F).endVertex();
        this.fullscreenQuad = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.fullscreenQuad.bind();
        this.fullscreenQuad.upload(builder.end());
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

    private void runOpaqueEyeMaskPass(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat, StormUniforms uniforms,
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
        this.applyCommonUniforms(shader, renderer, stack, projMat);
        this.applyStormUniforms(shader, uniforms);
        shader.safeGetUniform("ProtectionEnabled").set(protectionEnabled ? 1 : 0);
        shader.apply();

        this.fullscreenQuad.bind();
        this.fullscreenQuad.drawWithShader(new Matrix4f(), new Matrix4f(), shader);
        VertexBuffer.unbind();
        shader.clear();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    private void runTransparencyMaskPass(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat, StormUniforms uniforms,
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
        this.applyCommonUniforms(shader, renderer, stack, projMat);
        this.applyStormUniforms(shader, uniforms);
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
                                     float partialTick, float cloudR, float cloudG, float cloudB,
                                     StormUniforms uniforms) {
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
        shader.setSampler("DepthSampler", renderer.getCloudTarget().getDepthTextureId());

        this.applyCommonUniforms(shader, renderer, stack, projMat);
        shader.safeGetUniform("CloudColor").set(cloudR, cloudG, cloudB, 1.0F);
        this.applyStormUniforms(shader, uniforms);
        shader.apply();

        this.fullscreenQuad.bind();
        this.fullscreenQuad.drawWithShader(new Matrix4f(), new Matrix4f(), shader);
        VertexBuffer.unbind();
        shader.clear();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private void runTransparencyVolumePass(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                                           float partialTick, float cloudR, float cloudG, float cloudB,
                                           StormUniforms uniforms) {
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
        this.applyStormUniforms(shader, uniforms);
        shader.apply();

        GL30.glEnablei(GL11.GL_BLEND, 0);
        GL30.glEnablei(GL11.GL_BLEND, 1);
        GL40.glBlendEquationi(0, GL14.GL_FUNC_ADD);
        GL40.glBlendEquationi(1, GL14.GL_FUNC_ADD);
        GL40.glBlendFunci(0, GL11.GL_ONE, GL11.GL_ONE);
        GL40.glBlendFunci(1, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR);

        this.fullscreenQuad.bind();
        this.fullscreenQuad.drawWithShader(new Matrix4f(), new Matrix4f(), shader);
        VertexBuffer.unbind();
        shader.clear();

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

    private void applyStormUniforms(ShaderInstance shader, StormUniforms uniforms) {
        shader.safeGetUniform("StormCount").set(uniforms.stormCount());
        shader.safeGetUniform("StormPositions").set(uniforms.stormPositions());
        shader.safeGetUniform("StormHeights").set(uniforms.stormHeights());
        shader.safeGetUniform("EyeRadii").set(uniforms.eyeRadii());
        shader.safeGetUniform("EyeClearRadii").set(uniforms.eyeClearRadii());
        shader.safeGetUniform("EyeSlopes").set(uniforms.eyeSlopes());
        shader.safeGetUniform("EyewallThicknesses").set(uniforms.eyewallThicknesses());
        shader.safeGetUniform("CanopyRadii").set(uniforms.canopyRadii());
        shader.safeGetUniform("ShieldRadii").set(uniforms.shieldRadii());
        shader.safeGetUniform("CanopyBaseFactors").set(uniforms.canopyBaseFactors());
        shader.safeGetUniform("CanopyTopFactors").set(uniforms.canopyTopFactors());
        shader.safeGetUniform("ShieldBaseFactors").set(uniforms.shieldBaseFactors());
        shader.safeGetUniform("ShieldTopFactors").set(uniforms.shieldTopFactors());
        shader.safeGetUniform("BandStartRadii").set(uniforms.bandStartRadii());
        shader.safeGetUniform("BandEndRadii").set(uniforms.bandEndRadii());
        shader.safeGetUniform("BandWidths").set(uniforms.bandWidths());
        shader.safeGetUniform("BandStrengths").set(uniforms.bandStrengths());
        shader.safeGetUniform("BandCounts").set(uniforms.bandCounts());
        shader.safeGetUniform("FringeStrengths").set(uniforms.fringeStrengths());
        shader.safeGetUniform("StormSpins").set(uniforms.stormSpins());
        shader.safeGetUniform("StormIntensities").set(uniforms.stormIntensities());
        shader.safeGetUniform("StormSeeds").set(uniforms.stormSeeds());
    }

    private record PreparedHurricane(UUID id, float centerX, float centerZ, float baseY, float height,
                                     float eyeRadius, float eyeClearRadius, float eyeSlope,
                                     float eyewallThickness, float canopyRadius, float shieldRadius,
                                     float canopyBaseFactor, float canopyTopFactor,
                                     float shieldBaseFactor, float shieldTopFactor,
                                     float bandStartRadius, float bandEndRadius, float bandWidth,
                                     float bandStrength, float bandCount, float fringeStrength,
                                     float spin, float intensity, float seed) {
        static PreparedHurricane from(HurricaneInstance hurricane, float partialTick) {
            float scale = SimpleCloudsConstants.CLOUD_SCALE;
            Vec3 renderPos = hurricane.getRenderPosition(partialTick);
            HurricaneRenderDescriptor descriptor = hurricane.getRenderDescriptor(partialTick);

            return new PreparedHurricane(
                    hurricane.getId(),
                    (float) renderPos.x / scale,
                    (float) renderPos.z / scale,
                    -descriptor.baseOffsetWorld() / scale,
                    descriptor.volumeHeightWorld() / scale,
                    descriptor.eyeRadiusWorld() / scale,
                    descriptor.eyeClearRadiusWorld() / scale,
                    descriptor.eyeSlope(),
                    descriptor.eyewallThicknessWorld() / scale,
                    descriptor.canopyRadiusWorld() / scale,
                    descriptor.shieldRadiusWorld() / scale,
                    descriptor.canopyBaseFactor(),
                    descriptor.canopyTopFactor(),
                    descriptor.shieldBaseFactor(),
                    descriptor.shieldTopFactor(),
                    descriptor.bandStartRadiusWorld() / scale,
                    descriptor.bandEndRadiusWorld() / scale,
                    descriptor.bandWidthWorld() / scale,
                    descriptor.bandStrength(),
                    descriptor.bandCount(),
                    descriptor.fringeStrength(),
                    hurricane.getVisualSpin(partialTick),
                    Mth.clamp(hurricane.getRenderIntensity(partialTick), 0.0F, 1.0F),
                    hurricane.getVisualSeed()
            );
        }
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
        static StormUniforms from(List<PreparedHurricane> hurricanes) {
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

            for (int i = 0; i < hurricanes.size(); i++) {
                PreparedHurricane hurricane = hurricanes.get(i);
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
    }
}
