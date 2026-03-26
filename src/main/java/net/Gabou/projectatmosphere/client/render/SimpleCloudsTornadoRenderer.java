package net.Gabou.projectatmosphere.client.render;

import com.mojang.blaze3d.platform.MemoryTracker;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nonamecrackers2.simpleclouds.client.mesh.instancing.InstanceableMesh;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.shader.SimpleCloudsShaders;
import dev.nonamecrackers2.simpleclouds.client.shader.SingleSSBOShaderInstance;
import dev.nonamecrackers2.simpleclouds.client.shader.buffer.BindingManager;
import dev.nonamecrackers2.simpleclouds.client.shader.buffer.ShaderStorageBufferObject;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SimpleCloudsTornadoRenderer {
    public static final SimpleCloudsTornadoRenderer INSTANCE = new SimpleCloudsTornadoRenderer();

    private static final ResourceLocation BAYER_MATRIX_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("simpleclouds", "textures/shader/bayer_matrix.png");

    private static final float VOXEL_STEP = 0.34F;
    private static final float FRINGE_WIDTH = 0.56F;
    private static final float SHELL_HALF_WIDTH = 0.32F;
    private static final float CORE_WIDTH = 0.060F;
    private static final float CORE_FALLOFF = 0.18F;
    private static final float DENSITY_BIAS = 0.16F;
    private static final float NOISE_SCALE_PRIMARY = 1.10F;
    private static final float NOISE_SCALE_SECONDARY = 2.20F;
    private static final float NOISE_VERTICAL_PRIMARY = 0.30F;
    private static final float NOISE_VERTICAL_SECONDARY = 0.55F;
    private static final float NOISE_PRIMARY_WEIGHT = 0.24F;
    private static final float NOISE_SECONDARY_WEIGHT = 0.10F;
    private static final float GROUND_RADIUS_FACTOR = 0.24F;
    private static final float TOP_RADIUS_FACTOR = 1.16F;
    private static final float TOP_FLARE_FACTOR = 1.55F;
    private static final float PLUME_START = 0.72F;
    private static final float PLUME_RADIUS_GAIN = 1.40F;
    private static final float PLUME_SOFTEN_GAIN = 0.42F;
    private static final float PLUME_BODY_REDUCTION = 0.55F;
    private static final float UPPER_BRIGHTEN_START = 0.70F;
    private static final float UPPER_BRIGHTEN_GAIN = 0.20F;
    private static final float MOUTH_FADE_END = 0.08F;
    private static final float TOP_FADE_START = 0.985F;
    private static final float BRIGHTNESS_BASE = 0.46F;
    private static final float BRIGHTNESS_GAIN = 0.18F;
    private static final float BODY_FILL_START = 0.24F;
    private static final float BODY_FILL_END = 0.68F;
    private static final float BODY_DENSITY_STRENGTH = 0.68F;
    private static final float BODY_BREAKUP_STRENGTH = 0.08F;
    private static final float TORNADO_WHITEOUT_STRENGTH = 0.38F;
    private static final float TORNADO_WHITEOUT_THRESHOLD = 0.10F;
    private static final float MIN_VISUAL_WORLD_RADIUS = 24.0F;
    private static final float MIN_GROUND_RADIUS_CLOUD = 0.72F;
    private static final float MIN_TOP_RADIUS_CLOUD = 2.60F;
    private static final float CLOUD_BLEND_HEIGHT_ABOVE_BASE_WORLD = 200.0F;

    private static final int OPAQUE_STRIDE_BYTES = Integer.BYTES + 5 * Float.BYTES;
    private static final int TRANSPARENT_STRIDE_BYTES = 6 * Float.BYTES;

    private boolean initialized;
    private InstanceableMesh sideMesh;
    private InstanceableMesh cubeMesh;
    private ShaderStorageBufferObject opaqueSsbo;
    private ShaderStorageBufferObject transparentSsbo;

    private final List<OpaqueSideInstance> opaqueInstances = new ArrayList<>();
    private final List<TransparentCubeInstance> transparentInstances = new ArrayList<>();
    private ByteBuffer opaqueUploadBuffer;
    private ByteBuffer transparentUploadBuffer;

    private ClientLevel preparedLevel;
    private long preparedGameTime = Long.MIN_VALUE;
    private int opaqueCount;
    private int transparentCount;
    private long lastRenderOpaqueLogGameTime = Long.MIN_VALUE;
    private long lastRenderTransparencyLogGameTime = Long.MIN_VALUE;

    private SimpleCloudsTornadoRenderer() {
    }

    public void prepareFrame(ClientLevel level, float partialTick) {
        RenderSystem.assertOnRenderThread();
        this.ensureInitialized();

        if (this.preparedLevel == level && this.preparedGameTime == level.getGameTime()) {
            return;
        }

        boolean shouldDebugLog = shouldDebugLog(level);
        this.preparedLevel = level;
        this.preparedGameTime = level.getGameTime();
        this.opaqueInstances.clear();
        this.transparentInstances.clear();

        if (shouldDebugLog) {
            debug(
                    "prepareFrame gameTime={} activeTornadoes={}",
                    level.getGameTime(),
                    TornadoManager.getClientTornadoes().size()
            );
        }

        for (TornadoInstance tornado : TornadoManager.getClientTornadoes()) {
            this.appendTornado(level, tornado);
        }

        this.uploadBuffers();
        if (shouldDebugLog) {
            debug(
                    "prepareFrame complete gameTime={} opaqueCount={} transparentCount={}",
                    level.getGameTime(),
                    this.opaqueCount,
                    this.transparentCount
            );
        }
    }

    public void renderOpaque(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                             float partialTick, float cloudR, float cloudG, float cloudB) {
        ClientLevel level = Minecraft.getInstance().level;
        if (shouldDebugLog(level) && level != null && this.lastRenderOpaqueLogGameTime != level.getGameTime()) {
            this.lastRenderOpaqueLogGameTime = level.getGameTime();
            debug(
                    "renderOpaque called gameTime={} opaqueCount={} shadersReady={}",
                    level.getGameTime(),
                    this.opaqueCount,
                    SimpleCloudsShaders.areShadersInitialized()
            );
        }
        if (this.opaqueCount <= 0 || !SimpleCloudsShaders.areShadersInitialized()) {
            return;
        }

        BufferUploader.reset();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();

        SingleSSBOShaderInstance shader = SimpleCloudsShaders.getCloudsShader();
        RenderSystem.setShader(() -> shader);

        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        AbstractTexture ditherTexture = textureManager.getTexture(BAYER_MATRIX_TEXTURE);
        shader.setSampler("BayerMatrixSampler", ditherTexture);
        shader.safeGetUniform("DitherScale").set(SimpleCloudsRenderer.DITHER_SCALE);

        SimpleCloudsRenderer.prepareShader(shader, stack.last().pose(), projMat,
                renderer.getFogStart(), renderer.getFogEnd());
        shader.apply();

        RenderSystem.setShaderColor(cloudR, cloudG, cloudB, 1.0F);
        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
            shader.COLOR_MODULATOR.upload();
        }

        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, shader.getShaderStorageBinding(), this.opaqueSsbo.getId());
        this.sideMesh.drawInstanced(this.opaqueCount);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, shader.getShaderStorageBinding(), 0);
        shader.clear();

        GL30.glBindVertexArray(0);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
    }

    public void renderTransparency(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                                   float partialTick, float cloudR, float cloudG, float cloudB) {
        ClientLevel level = Minecraft.getInstance().level;
        if (shouldDebugLog(level) && level != null && this.lastRenderTransparencyLogGameTime != level.getGameTime()) {
            this.lastRenderTransparencyLogGameTime = level.getGameTime();
            debug(
                    "renderTransparency called gameTime={} transparentCount={} shadersReady={}",
                    level.getGameTime(),
                    this.transparentCount,
                    SimpleCloudsShaders.areShadersInitialized()
            );
        }
        if (this.transparentCount <= 0 || !SimpleCloudsShaders.areShadersInitialized()) {
            return;
        }

        BufferUploader.reset();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        SingleSSBOShaderInstance shader = SimpleCloudsShaders.getCloudsTransparencyShader();
        RenderSystem.setShader(() -> shader);

        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        AbstractTexture ditherTexture = textureManager.getTexture(BAYER_MATRIX_TEXTURE);
        shader.setSampler("BayerMatrixSampler", ditherTexture);
        shader.safeGetUniform("DitherScale").set(SimpleCloudsRenderer.DITHER_SCALE);

        SimpleCloudsRenderer.prepareShader(shader, stack.last().pose(), projMat,
                renderer.getFogStart(), renderer.getFogEnd());
        shader.apply();

        RenderSystem.setShaderColor(cloudR, cloudG, cloudB, 1.0F);
        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
            shader.COLOR_MODULATOR.upload();
        }

        GL30.glEnablei(GL11.GL_BLEND, 0);
        GL30.glEnablei(GL11.GL_BLEND, 1);
        GL40.glBlendEquationi(0, GL14.GL_FUNC_ADD);
        GL40.glBlendEquationi(1, GL14.GL_FUNC_ADD);
        GL40.glBlendFunci(0, GL11.GL_ONE, GL11.GL_ONE);
        GL40.glBlendFunci(1, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR);

        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, shader.getShaderStorageBinding(), this.transparentSsbo.getId());
        this.cubeMesh.drawInstanced(this.transparentCount);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, shader.getShaderStorageBinding(), 0);
        shader.clear();

        GL30.glDisablei(GL11.GL_BLEND, 0);
        GL30.glDisablei(GL11.GL_BLEND, 1);
        GL40.glBlendFuncSeparatei(0, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL40.glBlendFuncSeparatei(1, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        GL30.glBindVertexArray(0);
        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public float sampleWhiteoutAtCamera(ClientLevel level, Vec3 cameraPos, float partialTick) {
        float scale = SimpleCloudsConstants.CLOUD_SCALE;
        float cloudHeight = CloudManager.get(level).getCloudHeight();
        float sampleX = (float) cameraPos.x / scale;
        float sampleY = ((float) cameraPos.y - cloudHeight) / scale;
        float sampleZ = (float) cameraPos.z / scale;

        float strongest = 0.0F;
        float animationTime = TornadoManager.getShaderTime();
        for (TornadoInstance tornado : TornadoManager.getClientTornadoes()) {
            CloudSpaceTornado cloudTornado = CloudSpaceTornado.from(level, tornado);
            float density = this.sampleDensityCloudSpace(
                    sampleX - cloudTornado.centerX(),
                    sampleY - cloudTornado.bottomY(),
                    sampleZ - cloudTornado.centerZ(),
                    cloudTornado,
                    animationTime
            );
            float whiteout = Mth.clamp((density - TORNADO_WHITEOUT_THRESHOLD) / Math.max(FRINGE_WIDTH, 0.001F), 0.0F, 1.0F) * TORNADO_WHITEOUT_STRENGTH;
            strongest = Math.max(strongest, whiteout);
        }
        return strongest;
    }

    public void close() {
        if (!this.initialized) {
            return;
        }

        this.sideMesh.destroy();
        this.cubeMesh.destroy();
        BindingManager.freeSSBO(this.opaqueSsbo);
        BindingManager.freeSSBO(this.transparentSsbo);
        this.initialized = false;
        this.preparedLevel = null;
        this.opaqueUploadBuffer = null;
        this.transparentUploadBuffer = null;
        this.opaqueCount = 0;
        this.transparentCount = 0;
    }

    private void ensureInitialized() {
        if (this.initialized) {
            return;
        }

        this.sideMesh = InstanceableMesh.defaultSide();
        this.cubeMesh = InstanceableMesh.defaultCube();
        this.opaqueSsbo = BindingManager.createSSBO(GL15.GL_DYNAMIC_DRAW);
        this.transparentSsbo = BindingManager.createSSBO(GL15.GL_DYNAMIC_DRAW);
        this.initialized = true;
    }

    private void appendTornado(ClientLevel level, TornadoInstance tornado) {
        CloudSpaceTornado cloudTornado = CloudSpaceTornado.from(level, tornado);
        Map<CellKey, Float> densityByCell = new HashMap<>();
        float animationTime = TornadoManager.getShaderTime();
        boolean shouldDebugLog = shouldDebugLog(level);
        int startOpaqueCount = this.opaqueInstances.size();
        int startTransparentCount = this.transparentInstances.size();
        int sampledCellCount = 0;
        float minDensity = Float.POSITIVE_INFINITY;
        float maxDensity = Float.NEGATIVE_INFINITY;

        float maxRadius = Math.max(cloudTornado.baseRadius(), cloudTornado.topRadius()) + FRINGE_WIDTH + VOXEL_STEP;
        int minX = Mth.floor((cloudTornado.centerX() - maxRadius) / VOXEL_STEP);
        int maxX = Mth.ceil((cloudTornado.centerX() + maxRadius) / VOXEL_STEP);
        int minY = Mth.floor(cloudTornado.bottomY() / VOXEL_STEP);
        int maxY = Mth.ceil((cloudTornado.bottomY() + cloudTornado.height()) / VOXEL_STEP);
        int minZ = Mth.floor((cloudTornado.centerZ() - maxRadius) / VOXEL_STEP);
        int maxZ = Mth.ceil((cloudTornado.centerZ() + maxRadius) / VOXEL_STEP);

        if (shouldDebugLog) {
            debug(
                    "appendTornado reached id={} center=({}, {}, {}) boundsX=[{},{}] boundsY=[{},{}] boundsZ=[{},{}] baseRadius={} topRadius={} height={}",
                    tornado.getId(),
                    cloudTornado.centerX(),
                    cloudTornado.bottomY(),
                    cloudTornado.centerZ(),
                    minX,
                    maxX,
                    minY,
                    maxY,
                    minZ,
                    maxZ,
                    cloudTornado.baseRadius(),
                    cloudTornado.topRadius(),
                    cloudTornado.height()
            );
        }

        for (int cellX = minX; cellX <= maxX; cellX++) {
            for (int cellY = minY; cellY <= maxY; cellY++) {
                for (int cellZ = minZ; cellZ <= maxZ; cellZ++) {
                    float sampleX = cellX * VOXEL_STEP;
                    float sampleY = cellY * VOXEL_STEP;
                    float sampleZ = cellZ * VOXEL_STEP;
                    sampledCellCount++;

                    float density = this.sampleDensityCloudSpace(
                            sampleX - cloudTornado.centerX(),
                            sampleY - cloudTornado.bottomY(),
                            sampleZ - cloudTornado.centerZ(),
                            cloudTornado,
                            animationTime
                    );
                    minDensity = Math.min(minDensity, density);
                    maxDensity = Math.max(maxDensity, density);

                    if (density > -FRINGE_WIDTH) {
                        densityByCell.put(new CellKey(cellX, cellY, cellZ), density);
                    }
                }
            }
        }

        for (Map.Entry<CellKey, Float> entry : densityByCell.entrySet()) {
            CellKey key = entry.getKey();
            float density = entry.getValue();

            float y = key.y() * VOXEL_STEP;
            float y01 = Mth.clamp((y - cloudTornado.bottomY()) / Math.max(cloudTornado.height(), 0.001F), 0.0F, 1.0F);
            float upperBrighten = smoothstep(UPPER_BRIGHTEN_START, 1.0F, y01);
            float brightness = Mth.clamp(BRIGHTNESS_BASE + y01 * BRIGHTNESS_GAIN + upperBrighten * UPPER_BRIGHTEN_GAIN, 0.0F, 1.0F);
            float radius = VOXEL_STEP * 0.5F;

            if (density > 0.0F) {
                this.emitOpaqueFaces(densityByCell, key, brightness, radius);
            } else {
                float alpha = Mth.clamp(1.0F + density / FRINGE_WIDTH, 0.0F, 1.0F);
                alpha *= Mth.lerp(upperBrighten, 1.0F, 0.72F);
                this.transparentInstances.add(new TransparentCubeInstance(
                        key.x() * VOXEL_STEP,
                        key.y() * VOXEL_STEP,
                        key.z() * VOXEL_STEP,
                        brightness,
                        alpha,
                        radius
                ));
            }
        }

        if (shouldDebugLog) {
            debug(
                    "appendTornado result id={} sampledCells={} retainedCells={} densityMin={} densityMax={} addedOpaque={} addedTransparent={}",
                    tornado.getId(),
                    sampledCellCount,
                    densityByCell.size(),
                    minDensity,
                    maxDensity,
                    this.opaqueInstances.size() - startOpaqueCount,
                    this.transparentInstances.size() - startTransparentCount
            );
        }
    }

    private void emitOpaqueFaces(Map<CellKey, Float> densityByCell, CellKey key, float brightness, float radius) {
        float x = key.x() * VOXEL_STEP;
        float y = key.y() * VOXEL_STEP;
        float z = key.z() * VOXEL_STEP;

        if (!this.isSolid(densityByCell, key.x() - 1, key.y(), key.z())) {
            this.opaqueInstances.add(new OpaqueSideInstance(0, x, y, z, brightness, radius));
        }
        if (!this.isSolid(densityByCell, key.x() + 1, key.y(), key.z())) {
            this.opaqueInstances.add(new OpaqueSideInstance(1, x, y, z, brightness, radius));
        }
        if (!this.isSolid(densityByCell, key.x(), key.y() - 1, key.z())) {
            this.opaqueInstances.add(new OpaqueSideInstance(2, x, y, z, brightness, radius));
        }
        if (!this.isSolid(densityByCell, key.x(), key.y() + 1, key.z())) {
            this.opaqueInstances.add(new OpaqueSideInstance(3, x, y, z, brightness, radius));
        }
        if (!this.isSolid(densityByCell, key.x(), key.y(), key.z() - 1)) {
            this.opaqueInstances.add(new OpaqueSideInstance(4, x, y, z, brightness, radius));
        }
        if (!this.isSolid(densityByCell, key.x(), key.y(), key.z() + 1)) {
            this.opaqueInstances.add(new OpaqueSideInstance(5, x, y, z, brightness, radius));
        }
    }

    private boolean isSolid(Map<CellKey, Float> densityByCell, int x, int y, int z) {
        Float density = densityByCell.get(new CellKey(x, y, z));
        return density != null && density > 0.0F;
    }

    private float sampleDensityCloudSpace(float localX, float localY, float localZ,
                                          CloudSpaceTornado tornado, float animationTime) {
        float height = Math.max(tornado.height(), 0.001F);
        if (localY <= -FRINGE_WIDTH || localY >= height + FRINGE_WIDTH) {
            return -1.0F;
        }

        float y01 = Mth.clamp(localY / height, 0.0F, 1.0F);
        float twist = animationTime * 0.18F + tornado.twist() + y01 * 5.8F;
        float cos = Mth.cos(twist);
        float sin = Mth.sin(twist);

        float qx = localX * cos - localZ * sin;
        float qz = localX * sin + localZ * cos;
        float funnelRadius = computeFunnelRadius(tornado, y01);
        float radialDistance = Mth.sqrt(qx * qx + qz * qz);

        float plumeBlend = smoothstep(PLUME_START, 1.0F, y01);
        float shellWidth = SHELL_HALF_WIDTH + plumeBlend * PLUME_SOFTEN_GAIN;
        float shell = shellWidth - Math.abs(radialDistance - funnelRadius);
        float bodyRadius = Math.max(0.0F, funnelRadius - Mth.lerp(y01, BODY_FILL_START, BODY_FILL_END));
        float body = bodyRadius - radialDistance;
        float core = CORE_WIDTH - radialDistance * CORE_FALLOFF;

        float advectX = animationTime * (0.08F + y01 * 0.18F);
        float advectZ = animationTime * (0.05F + y01 * 0.12F);
        float shellBreakup = fbm(
                (qx + advectX) * NOISE_SCALE_PRIMARY,
                localY * NOISE_VERTICAL_PRIMARY + animationTime * 0.10F,
                (qz - advectZ) * NOISE_SCALE_PRIMARY
        ) * NOISE_PRIMARY_WEIGHT;
        shellBreakup += fbm(
                qx * NOISE_SCALE_SECONDARY + 17.0F + advectX * 1.7F,
                localY * NOISE_VERTICAL_SECONDARY + 11.0F,
                qz * NOISE_SCALE_SECONDARY - 9.0F - advectZ * 1.3F
        ) * NOISE_SECONDARY_WEIGHT;
        float bodyBreakup = shellBreakup * BODY_BREAKUP_STRENGTH;

        float mouthFade = smoothstep(0.0F, MOUTH_FADE_END, y01);
        float topFade = 1.0F - smoothstep(TOP_FADE_START, 1.0F, y01);
        float plumeBodyStrength = Mth.lerp(plumeBlend, BODY_DENSITY_STRENGTH, BODY_DENSITY_STRENGTH * PLUME_BODY_REDUCTION);
        float density = Math.max(shell + shellBreakup, Math.max(body * plumeBodyStrength + bodyBreakup, core));
        return (density - DENSITY_BIAS) * mouthFade * topFade;
    }

    private void uploadBuffers() {
        this.opaqueCount = this.opaqueInstances.size();
        this.transparentCount = this.transparentInstances.size();

        if (this.opaqueCount > 0) {
            int size = this.opaqueCount * OPAQUE_STRIDE_BYTES;
            ByteBuffer buffer = this.prepareUploadBuffer(size, true);
            for (OpaqueSideInstance instance : this.opaqueInstances) {
                instance.write(buffer);
            }
            buffer.flip();
            this.opaqueSsbo.uploadData(buffer);
        }

        if (this.transparentCount > 0) {
            int size = this.transparentCount * TRANSPARENT_STRIDE_BYTES;
            ByteBuffer buffer = this.prepareUploadBuffer(size, false);
            for (TransparentCubeInstance instance : this.transparentInstances) {
                instance.write(buffer);
            }
            buffer.flip();
            this.transparentSsbo.uploadData(buffer);
        }
    }

    private static boolean shouldDebugLog(ClientLevel level) {
        return ProjectAtmosphere.DEBUG_MODE && level != null && level.getGameTime() % 20L == 0L;
    }

    private static void debug(String message, Object... args) {
        if (ProjectAtmosphere.DEBUG_MODE) {
            ProjectAtmosphere.LOGGER.info("[TornadoDebug] " + message, args);
        }
    }

    private ByteBuffer prepareUploadBuffer(int size, boolean opaque) {
        ByteBuffer current = opaque ? this.opaqueUploadBuffer : this.transparentUploadBuffer;
        if (current == null || current.capacity() < size) {
            if (current != null) {
                MemoryUtil.memFree(current);
            }
            current = MemoryTracker.create(size).order(ByteOrder.nativeOrder());
            if (opaque) {
                this.opaqueUploadBuffer = current;
            } else {
                this.transparentUploadBuffer = current;
            }
        }
        current.clear();
        current.limit(size);
        return current;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float fbm(float x, float y, float z) {
        float value = 0.0F;
        float amplitude = 0.5F;
        float frequency = 1.0F;

        for (int i = 0; i < 4; i++) {
            value += noise(x * frequency, y * frequency, z * frequency) * amplitude;
            frequency *= 2.0F;
            amplitude *= 0.5F;
        }

        return value;
    }

    private static float noise(float x, float y, float z) {
        int xi = Mth.floor(x);
        int yi = Mth.floor(y);
        int zi = Mth.floor(z);

        float xf = x - xi;
        float yf = y - yi;
        float zf = z - zi;

        float u = xf * xf * (3.0F - 2.0F * xf);
        float v = yf * yf * (3.0F - 2.0F * yf);
        float w = zf * zf * (3.0F - 2.0F * zf);

        float n000 = hash(xi, yi, zi);
        float n100 = hash(xi + 1, yi, zi);
        float n010 = hash(xi, yi + 1, zi);
        float n110 = hash(xi + 1, yi + 1, zi);
        float n001 = hash(xi, yi, zi + 1);
        float n101 = hash(xi + 1, yi, zi + 1);
        float n011 = hash(xi, yi + 1, zi + 1);
        float n111 = hash(xi + 1, yi + 1, zi + 1);

        float x00 = Mth.lerp(u, n000, n100);
        float x10 = Mth.lerp(u, n010, n110);
        float x01 = Mth.lerp(u, n001, n101);
        float x11 = Mth.lerp(u, n011, n111);
        float y0 = Mth.lerp(v, x00, x10);
        float y1 = Mth.lerp(v, x01, x11);
        return Mth.lerp(w, y0, y1);
    }

    private static float hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
    }

    private static float computeFunnelRadius(CloudSpaceTornado tornado, float y01) {
        float curve = (float) Math.pow(y01, 0.62F);
        float radius = Mth.lerp(curve, tornado.baseRadius(), tornado.topRadius());
        float shoulder = (float) Math.pow(y01, 1.45F) * tornado.topRadius() * 0.28F;
        float ropePinch = (1.0F - smoothstep(0.0F, 0.22F, y01)) * tornado.baseRadius() * 0.18F;
        radius = radius + shoulder - ropePinch;
        float plumeBlend = smoothstep(PLUME_START, 1.0F, y01);
        float plumeRadius = radius * TOP_FLARE_FACTOR + tornado.topRadius() * PLUME_RADIUS_GAIN * plumeBlend;
        return Mth.lerp(plumeBlend, radius, plumeRadius);
    }

    private record CellKey(int x, int y, int z) {
    }

    private record CloudSpaceTornado(float centerX, float centerZ, float bottomY,
                                     float height, float baseRadius, float topRadius, float twist) {
        static CloudSpaceTornado from(ClientLevel level, TornadoInstance tornado) {
            float scale = SimpleCloudsConstants.CLOUD_SCALE;
            float cloudHeight = CloudManager.get(level).getCloudHeight();
            float centerX = (float) tornado.position.x / scale;
            float centerZ = (float) tornado.position.z / scale;
            float bottomY = (tornado.getVisualBottomY() - cloudHeight) / scale;
            float baseHeight = tornado.getVisualHeight() / scale;
            float minBlendTop = (CLOUD_BLEND_HEIGHT_ABOVE_BASE_WORLD / scale) - bottomY;
            float height = Math.max(baseHeight, minBlendTop);
            float canopyRadius = Math.max(tornado.radius, MIN_VISUAL_WORLD_RADIUS) / scale;
            float groundRadius = Math.max(MIN_GROUND_RADIUS_CLOUD, canopyRadius * GROUND_RADIUS_FACTOR);
            float topRadius = Math.max(MIN_TOP_RADIUS_CLOUD, Math.max(groundRadius * 2.0F, canopyRadius * TOP_RADIUS_FACTOR));
            return new CloudSpaceTornado(centerX, centerZ, bottomY, height, groundRadius, topRadius, tornado.getTwist());
        }
    }

    private record OpaqueSideInstance(int side, float x, float y, float z, float brightness, float radius) {
        void write(ByteBuffer buffer) {
            buffer.putInt(this.side);
            buffer.putFloat(this.x);
            buffer.putFloat(this.y);
            buffer.putFloat(this.z);
            buffer.putFloat(this.brightness);
            buffer.putFloat(this.radius);
        }
    }

    private record TransparentCubeInstance(float x, float y, float z, float brightness, float alpha, float radius) {
        void write(ByteBuffer buffer) {
            buffer.putFloat(this.x);
            buffer.putFloat(this.y);
            buffer.putFloat(this.z);
            buffer.putFloat(this.brightness);
            buffer.putFloat(this.alpha);
            buffer.putFloat(this.radius);
        }
    }
}
