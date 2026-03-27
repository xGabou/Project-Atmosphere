package net.Gabou.projectatmosphere.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.modules.weather.StormLifecyclePhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SimpleCloudsTornadoRenderer {
    public static final SimpleCloudsTornadoRenderer INSTANCE = new SimpleCloudsTornadoRenderer();

    private static final int MAX_STORMS = 8;
    private static final float CLOUD_BLEND_HEIGHT_ABOVE_BASE_WORLD = 260.0F;
    private static final float GROUND_CONTACT_EXTENSION_WORLD = 12.0F;
    private static final float GROUND_CONTACT_PADDING_WORLD = 2.0F;
    private static final float MIN_VISUAL_WORLD_WIDTH = 28.0F;
    private static final float MIN_VISUAL_WORLD_STORM_SIZE = 140.0F;
    private static final float MIN_VISUAL_WORLD_HEIGHT = 120.0F;
    private static final float MAX_RAY_DISTANCE_CLOUD = 420.0F;
    private static final float WHITEOUT_STRENGTH = 0.40F;
    private static final float WHITEOUT_THRESHOLD = 0.12F;

    private ClientLevel preparedLevel;
    private long preparedGameTime = Long.MIN_VALUE;
    private float preparedPartialTick = Float.NaN;
    private final List<PreparedTornado> preparedTornadoes = new ArrayList<>();
    private boolean initialized;
    private VertexBuffer fullscreenQuad;
    private long lastRenderOpaqueLogGameTime = Long.MIN_VALUE;
    private long lastRenderTransparencyLogGameTime = Long.MIN_VALUE;

    private SimpleCloudsTornadoRenderer() {
    }

    public void prepareFrame(ClientLevel level, float partialTick) {
        if (this.preparedLevel == level
                && this.preparedGameTime == level.getGameTime()
                && Float.compare(this.preparedPartialTick, partialTick) == 0) {
            return;
        }

        this.ensureInitialized();
        boolean shouldDebugLog = shouldDebugLog(level);
        this.preparedLevel = level;
        this.preparedGameTime = level.getGameTime();
        this.preparedPartialTick = partialTick;
        this.preparedTornadoes.clear();

        float animationTime = TornadoManager.getShaderTime() + partialTick * 0.05F;
        for (TornadoInstance tornado : TornadoManager.getClientTornadoes()) {
            if (this.preparedTornadoes.size() >= MAX_STORMS) {
                break;
            }
            this.preparedTornadoes.add(PreparedTornado.from(level, tornado, animationTime, partialTick));
        }

        if (shouldDebugLog) {
            debug("prepareFrame complete gameTime={} tornadoes={}", level.getGameTime(), this.preparedTornadoes.size());
        }
    }

    public void renderOpaque(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                             float partialTick, float cloudR, float cloudG, float cloudB) {
        this.renderOpaque(renderer, stack, projMat, partialTick, cloudR, cloudG, cloudB, renderer.getCloudTarget().getDepthTextureId(), true);
    }

    public void renderOpaque(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                             float partialTick, float cloudR, float cloudG, float cloudB,
                             int depthTextureId, boolean writeDepth) {
        ClientLevel level = Minecraft.getInstance().level;
        if (shouldDebugLog(level) && level != null && this.lastRenderOpaqueLogGameTime != level.getGameTime()) {
            this.lastRenderOpaqueLogGameTime = level.getGameTime();
            debug("renderOpaque called gameTime={} tornadoes={} shaderReady={}", level.getGameTime(), this.preparedTornadoes.size(), TornadoShaders.isReady());
        }
        if (this.preparedTornadoes.isEmpty() || !TornadoShaders.isReady()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ShaderInstance shader = TornadoShaders.getShader();
        if (shader == null) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(writeDepth);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        AbstractTexture tornadoTexture = mc.getTextureManager().getTexture(TornadoShaders.TORNADO_TEXTURE);
        AbstractTexture noiseTexture = mc.getTextureManager().getTexture(TornadoShaders.NOISE_TEXTURE);
        AbstractTexture flowTexture = mc.getTextureManager().getTexture(TornadoShaders.FLOW_TEXTURE);
        shader.setSampler("TornadoSampler", tornadoTexture);
        shader.setSampler("NoiseSampler", noiseTexture);
        shader.setSampler("FlowSampler", flowTexture);
        shader.setSampler("DepthSampler", depthTextureId);

        shader.safeGetUniform("ModelViewMat").set(stack.last().pose());
        shader.safeGetUniform("ProjMat").set(projMat);
        Matrix4f inverseProj = new Matrix4f(projMat).invert();
        Matrix4f inverseModelView = new Matrix4f(stack.last().pose()).invert();
        shader.safeGetUniform("InverseProjMat").set(inverseProj);
        shader.safeGetUniform("InverseModelViewMat").set(inverseModelView);

        float scale = SimpleCloudsConstants.CLOUD_SCALE;
        float cloudHeight = CloudManager.get(level).getCloudHeight();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        shader.safeGetUniform("CameraPos").set(
                (float) cameraPos.x / scale,
                ((float) cameraPos.y - cloudHeight) / scale,
                (float) cameraPos.z / scale
        );

        shader.safeGetUniform("CloudColor").set(cloudR, cloudG, cloudB, 1.0F);
        shader.safeGetUniform("AnimationTime").set(TornadoManager.getShaderTime() + partialTick * 0.05F);
        shader.safeGetUniform("MaxDistance").set(MAX_RAY_DISTANCE_CLOUD);
        shader.safeGetUniform("OutSize").set((float) mc.getWindow().getWidth(), (float) mc.getWindow().getHeight());
        shader.safeGetUniform("FogStart").set(renderer.getFogStart());
        shader.safeGetUniform("FogEnd").set(renderer.getFogEnd());
        float[] fogColor = RenderSystem.getShaderFogColor();
        shader.safeGetUniform("FogColor").set(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);

        float[] stormPositions = new float[MAX_STORMS * 3];
        float[] stormHeights = new float[MAX_STORMS];
        float[] stormWidths = new float[MAX_STORMS];
        float[] stormSizes = new float[MAX_STORMS];
        float[] stormSpins = new float[MAX_STORMS];
        float[] stormIntensities = new float[MAX_STORMS];
        float[] stormShapes = new float[MAX_STORMS];
        float[] stormProgress = new float[MAX_STORMS];

        for (int i = 0; i < this.preparedTornadoes.size(); i++) {
            PreparedTornado tornado = this.preparedTornadoes.get(i);
            stormPositions[i * 3] = tornado.centerX();
            stormPositions[i * 3 + 1] = tornado.bottomY();
            stormPositions[i * 3 + 2] = tornado.centerZ();
            stormHeights[i] = tornado.height();
            stormWidths[i] = tornado.width();
            stormSizes[i] = tornado.stormSize();
            stormSpins[i] = tornado.spin();
            stormIntensities[i] = tornado.intensity();
            stormShapes[i] = tornado.shape();
            stormProgress[i] = tornado.touchdownProgress();
        }

        shader.safeGetUniform("StormCount").set(this.preparedTornadoes.size());
        shader.safeGetUniform("StormPositions").set(stormPositions);
        shader.safeGetUniform("StormHeights").set(stormHeights);
        shader.safeGetUniform("StormWidths").set(stormWidths);
        shader.safeGetUniform("StormSizes").set(stormSizes);
        shader.safeGetUniform("StormSpins").set(stormSpins);
        shader.safeGetUniform("StormIntensities").set(stormIntensities);
        shader.safeGetUniform("StormShapes").set(stormShapes);
        shader.safeGetUniform("StormProgress").set(stormProgress);
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

    public void renderTransparency(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                                   float partialTick, float cloudR, float cloudG, float cloudB) {
        ClientLevel level = Minecraft.getInstance().level;
        if (shouldDebugLog(level) && level != null && this.lastRenderTransparencyLogGameTime != level.getGameTime()) {
            this.lastRenderTransparencyLogGameTime = level.getGameTime();
            debug("renderTransparency called gameTime={} tornadoes={}", level.getGameTime(), this.preparedTornadoes.size());
        }
    }

    public float sampleWhiteoutAtCamera(ClientLevel level, Vec3 cameraPos, float partialTick) {
        float scale = SimpleCloudsConstants.CLOUD_SCALE;
        float cloudHeight = CloudManager.get(level).getCloudHeight();
        float sampleX = (float) cameraPos.x / scale;
        float sampleY = ((float) cameraPos.y - cloudHeight) / scale;
        float sampleZ = (float) cameraPos.z / scale;
        float strongest = 0.0F;
        float animationTime = TornadoManager.getShaderTime() + partialTick * 0.05F;

        for (TornadoInstance tornado : TornadoManager.getClientTornadoes()) {
            PreparedTornado prepared = PreparedTornado.from(level, tornado, animationTime, partialTick);
            float density = sampleAnalyticalDensity(sampleX, sampleY, sampleZ, prepared, animationTime);
            float whiteout = Mth.clamp((density - WHITEOUT_THRESHOLD) / 0.18F, 0.0F, 1.0F) * WHITEOUT_STRENGTH;
            strongest = Math.max(strongest, whiteout);
        }
        return strongest;
    }

    public void close() {
        this.preparedLevel = null;
        this.preparedGameTime = Long.MIN_VALUE;
        this.preparedPartialTick = Float.NaN;
        this.preparedTornadoes.clear();
        if (this.fullscreenQuad != null) {
            this.fullscreenQuad.close();
            this.fullscreenQuad = null;
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

    private static float sampleAnalyticalDensity(float sampleX, float sampleY, float sampleZ, PreparedTornado tornado, float animationTime) {
        float baseHeight = tornado.bottomY() + tornado.height();
        if (sampleY < tornado.bottomY() - 2.0F || sampleY > baseHeight + 3.0F) {
            return -1.0F;
        }

        float localY = sampleY - tornado.bottomY();
        float y01 = Mth.clamp(localY / Math.max(tornado.height(), 0.001F), 0.0F, 1.0F);
        float fnlTop = Math.max(baseHeight - 13.125F, tornado.bottomY() + 3.75F);
        float percFnlHeight = Mth.clamp((sampleY - tornado.bottomY()) / Math.max(fnlTop - tornado.bottomY(), 0.001F), 0.0F, 1.0F);
        float torShape = Mth.lerp(Mth.clamp(tornado.width() / 62.5F, 0.0F, 1.0F), tornado.shape(), 20.0F);
        float wid = (tornado.width() / 2.5F)
                + ((tornado.width() / 2.5F) * percFnlHeight * tornado.touchdownProgress())
                + ((tornado.stormSize() / Math.max(Mth.lerp(tornado.touchdownProgress(), torShape + 2.0F, torShape), 0.001F))
                * percFnlHeight * percFnlHeight * percFnlHeight * percFnlHeight);
        wid = Mth.lerp((1.0F - percFnlHeight) * (1.0F - tornado.touchdownProgress()), wid, 0.0F);

        float phase = animationTime * 0.18F + tornado.seed();
        float swayX = (Mth.sin(phase + y01 * 6.2F) + Mth.cos(phase * 0.55F + y01 * 4.1F) * 0.45F) * tornado.width() * 0.18F * y01;
        float swayZ = (Mth.cos(phase * 1.07F + y01 * 5.65F) + Mth.sin(phase * 0.48F + y01 * 3.8F) * 0.45F) * tornado.width() * 0.18F * y01;
        float dx = sampleX - (tornado.centerX() + swayX);
        float dz = sampleZ - (tornado.centerZ() + swayZ);
        float dist = Mth.sqrt(dx * dx + dz * dz);
        float density = 1.0F - Mth.clamp(dist / Math.max(wid, 0.001F), 0.0F, 1.0F);
        density = density * density * (1.3F - y01 * 0.25F);

        float wallcloudLower = 15.0F * (float) Math.pow(Math.max(0.0F, 1.0F - Mth.clamp(dist / Math.max(tornado.stormSize() * 0.35F, 0.001F), 0.0F, 1.0F)), 0.25F)
                * Mth.clamp((tornado.intensity() - 0.45F) * 2.2F, 0.0F, 1.0F);
        if (sampleY <= baseHeight && sampleY >= baseHeight - wallcloudLower) {
            density = Math.max(density, 0.28F);
        }
        return density;
    }

    private static boolean shouldDebugLog(ClientLevel level) {
        return ProjectAtmosphere.DEBUG_MODE && level != null && level.getGameTime() % 20L == 0L;
    }

    private static float sampleTerrainSurfaceY(ClientLevel level, Vec3 renderPos, float radius) {
        int centerX = Mth.floor(renderPos.x);
        int centerZ = Mth.floor(renderPos.z);
        int sampleOffset = Math.max(2, Mth.floor(Math.min(radius * 0.45F, 10.0F)));

        float highestSurface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ) - 1.0F;
        highestSurface = Math.max(highestSurface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX + sampleOffset, centerZ) - 1.0F);
        highestSurface = Math.max(highestSurface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX - sampleOffset, centerZ) - 1.0F);
        highestSurface = Math.max(highestSurface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ + sampleOffset) - 1.0F);
        highestSurface = Math.max(highestSurface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ - sampleOffset) - 1.0F);
        return highestSurface;
    }

    private static void debug(String message, Object... args) {
        if (ProjectAtmosphere.DEBUG_MODE) {
            ProjectAtmosphere.LOGGER.info("[TornadoDebug] " + message, args);
        }
    }

    private record PreparedTornado(UUID id, float centerX, float centerZ, float bottomY, float height,
                                   float width, float stormSize, float spin, float intensity,
                                   float shape, float touchdownProgress, float seed, float animationTime) {
        static PreparedTornado from(ClientLevel level, TornadoInstance tornado, float animationTime, float partialTick) {
            float scale = SimpleCloudsConstants.CLOUD_SCALE;
            float cloudHeight = CloudManager.get(level).getCloudHeight();
            Vec3 renderPos = tornado.getRenderPosition(partialTick);
            float renderBottomY = tornado.getRenderBottomY(partialTick);
            float renderRadius = tornado.getRenderRadius(partialTick);
            float terrainSurfaceY = sampleTerrainSurfaceY(level, renderPos, renderRadius);
            float contactExtension = Math.max(GROUND_CONTACT_EXTENSION_WORLD, renderBottomY - terrainSurfaceY + GROUND_CONTACT_PADDING_WORLD);
            float centerX = (float) renderPos.x / scale;
            float centerZ = (float) renderPos.z / scale;
            float bottomY = ((renderBottomY - contactExtension) - cloudHeight) / scale;
            float baseHeight = Math.max(tornado.getRenderHeight(partialTick) + contactExtension, MIN_VISUAL_WORLD_HEIGHT) / scale;
            float minBlendHeight = (CLOUD_BLEND_HEIGHT_ABOVE_BASE_WORLD / scale) - bottomY;
            float height = Math.max(baseHeight, minBlendHeight);
            float width = Math.max(renderRadius * 2.0F, MIN_VISUAL_WORLD_WIDTH) / scale;
            float stormSize = Math.max(MIN_VISUAL_WORLD_STORM_SIZE / scale, Math.max(width * 4.25F, height * 0.34F));
            float intensity = Mth.clamp(tornado.getNormalizedIntensity(), 0.0F, 1.0F);
            float touchdownProgress = switch (tornado.getPhase()) {
                case FORMING -> Mth.clamp(intensity * 1.35F, 0.0F, 0.92F);
                case ACTIVE -> Mth.clamp(0.72F + intensity * 0.35F, 0.0F, 1.0F);
                case DISSIPATING -> Mth.clamp(intensity * 1.10F, 0.0F, 1.0F);
                default -> 0.0F;
            };
            float seed = (Math.abs(tornado.getId().hashCode()) % 10000) / 10000.0F;
            float shape = 8.0F + seed * 10.0F;
            return new PreparedTornado(
                    tornado.getId(),
                    centerX,
                    centerZ,
                    bottomY,
                    height,
                    width,
                    stormSize,
                    tornado.getTwist(),
                    intensity,
                    shape,
                    touchdownProgress,
                    seed,
                    animationTime
            );
        }
    }
}
