package net.Gabou.projectatmosphere.mixin;

import com.google.common.collect.ImmutableMap;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.MultiRegionCloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.shader.buffer.BindingManager;
import dev.nonamecrackers2.simpleclouds.client.shader.buffer.ShaderStorageBufferObject;
import dev.nonamecrackers2.simpleclouds.client.shader.compute.ComputeShader;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudInfo;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudGetter;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.api.common.cloud.region.ITornadoRegion;
import net.Gabou.projectatmosphere.api.common.cloud.region.TornadoDescriptor;
import net.Gabou.projectatmosphere.client.hurricane.ClientHurricaneStateCache;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneInstance;
import net.Gabou.projectatmosphere.util.HurricaneUpload;
import net.Gabou.projectatmosphere.util.RegionUpload;
import net.Gabou.projectatmosphere.util.TornadoUpload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL41;
import org.lwjgl.opengl.GL43;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Mixin(value = MultiRegionCloudMeshGenerator.class, remap = false)
public abstract class MultiRegionCloudMeshGeneratorMixin {
    @Unique
    private static final Logger PROJECTATMOSPHERE$LOGGER = LogManager.getLogger("ProjectAtmosphere/CloudMeshGenerator");
    @Unique
    private static final String PROJECTATMOSPHERE$STORM_BUFFER_NAME = "CloudStorms";
    @Unique
    private static final ResourceLocation PROJECTATMOSPHERE$HURRICANE_CLOUD_ID = HurricaneInstance.HURRICANE_CLOUD_TYPE_ID;

    @Shadow @Final public static int MAX_CLOUD_FORMATIONS;
    @Shadow protected ComputeShader regionTextureGenerator;
    @Shadow protected CloudGetter cloudGetter;
    @Shadow protected CloudInfo[] cachedTypes;

    @Unique
    private static final int PROJECTATMOSPHERE$MAX_TORNADOES = 64;
    @Unique
    private static final int PROJECTATMOSPHERE$MAX_UNIFORM_TORNADOES = 16;
    @Unique
    private static final int PROJECTATMOSPHERE$MAX_HURRICANES = 8;
    @Unique
    private static final int PROJECTATMOSPHERE$TORNADO_STRIDE = 32;
    @Unique
    private static final int PROJECTATMOSPHERE$HURRICANE_STRIDE = 64;

    @Unique private ShaderStorageBufferObject projectatmosphere$stormBuffer;
    @Unique private boolean projectatmosphere$stormBufferUsesBindingManager;
    @Unique private int projectatmosphere$currentTornadoCount;
    @Unique private int projectatmosphere$currentHurricaneCount;
    @Unique private boolean projectatmosphere$hasStormBlock;
    @Unique private boolean projectatmosphere$stormBufferUnavailableLogged;
    @Unique private int projectatmosphere$lastStormShaderId = -1;

    @Inject(method = "uploadCloudRegionData", at = @At("TAIL"))
    private void projectatmosphere$uploadNativeStormData(float partialTick, CallbackInfo ci) {
        this.projectatmosphere$uploadStormData(partialTick);
    }

    @Redirect(
            method = "initExtra",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/nonamecrackers2/simpleclouds/client/shader/compute/ComputeShader;loadShader(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/server/packs/resources/ResourceProvider;IIILcom/google/common/collect/ImmutableMap;)Ldev/nonamecrackers2/simpleclouds/client/shader/compute/ComputeShader;"
            )
    )
    private ComputeShader projectatmosphere$useProjectAtmosphereCloudRegionShader(ResourceLocation loc, ResourceProvider provider, int localX, int localY, int localZ, ImmutableMap<String, String> parameters) throws IOException {
        ResourceLocation shaderLoc = loc;
        if (loc != null && "simpleclouds".equals(loc.getNamespace()) && "cloud_regions".equals(loc.getPath())) {
            shaderLoc = ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "cloud_regions");
        }
        return ComputeShader.loadShader(shaderLoc, provider, localX, localY, localZ, parameters);
    }

    @Inject(method = "determineChunkGenSettings", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$includeHurricanesInChunkHeights(float minX, float minZ, float maxX, float maxZ, CallbackInfoReturnable cir) {
        List<ClientHurricaneStateCache.RenderableHurricane> hurricanes = ClientHurricaneStateCache.getRenderableHurricanes(0.0F);
        if (hurricanes.isEmpty() || this.cloudGetter == null) {
            return;
        }

        CloudType hurricaneType = this.cloudGetter.getCloudTypeForId(PROJECTATMOSPHERE$HURRICANE_CLOUD_ID);
        if (hurricaneType == null) {
            return;
        }

        float[][] positions = new float[][]{{minX, minZ}, {minX, maxZ}, {maxX, minZ}, {maxX, maxZ}};
        int smallestStartHeight = 0;
        int largestEndHeight = 0;
        boolean hasRenderableContent = false;
        boolean initializedHeights = false;

        for (float[] pos : positions) {
            var typeAt = this.cloudGetter.getCloudTypeAtPosition(pos[0], pos[1]);
            if (typeAt.getRight() < 1.0F) {
                hasRenderableContent = true;
            }
            CloudType type = typeAt.getLeft();
            if (type == null) {
                continue;
            }
            int startHeight = type.noiseConfig().getStartHeight();
            int endHeight = type.noiseConfig().getEndHeight();
            if (!initializedHeights || smallestStartHeight > startHeight) {
                smallestStartHeight = startHeight;
            }
            if (!initializedHeights || largestEndHeight < endHeight) {
                largestEndHeight = endHeight;
            }
            initializedHeights = true;
        }

        for (ClientHurricaneStateCache.RenderableHurricane hurricane : hurricanes) {
            if (!this.projectatmosphere$hurricaneIntersects(hurricane, minX, minZ, maxX, maxZ)) {
                continue;
            }
            int startHeight = Mth.floor(hurricane.anchorY() + hurricaneType.noiseConfig().getStartHeight());
            int endHeight = Mth.ceil(hurricane.anchorY() + hurricaneType.noiseConfig().getEndHeight());
            if (!initializedHeights || smallestStartHeight > startHeight) {
                smallestStartHeight = startHeight;
            }
            if (!initializedHeights || largestEndHeight < endHeight) {
                largestEndHeight = endHeight;
            }
            hasRenderableContent = true;
            initializedHeights = true;
        }

        if (!hasRenderableContent || !initializedHeights || smallestStartHeight == largestEndHeight) {
            cir.setReturnValue(this.projectatmosphere$invokeSkipSettings());
        } else {
            cir.setReturnValue(this.projectatmosphere$invokeHeightSettings(smallestStartHeight, largestEndHeight));
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void projectatmosphere$resetStormBuffers(CallbackInfo ci) {
        this.projectatmosphere$closeManualStormBuffer();
        this.projectatmosphere$stormBuffer = null;
        this.projectatmosphere$stormBufferUsesBindingManager = false;
        this.projectatmosphere$currentTornadoCount = 0;
        this.projectatmosphere$currentHurricaneCount = 0;
        this.projectatmosphere$hasStormBlock = false;
        this.projectatmosphere$stormBufferUnavailableLogged = false;
        this.projectatmosphere$lastStormShaderId = -1;
    }

    @Unique
    private void projectatmosphere$uploadStormData(float partialTick) {
        if (this.regionTextureGenerator == null || !this.regionTextureGenerator.isValid()) {
            return;
        }

        List<TornadoUpload> uploads = this.projectatmosphere$collectTornadoUploads(partialTick);
        List<HurricaneUpload> hurricaneUploads = this.projectatmosphere$collectHurricaneUploads(partialTick);
        int tornadoCount = Math.min(uploads.size(), PROJECTATMOSPHERE$MAX_TORNADOES);
        int uniformTornadoCount = Math.min(tornadoCount, PROJECTATMOSPHERE$MAX_UNIFORM_TORNADOES);
        int hurricaneCount = Math.min(hurricaneUploads.size(), PROJECTATMOSPHERE$MAX_HURRICANES);
        if (tornadoCount <= 0 && hurricaneCount <= 0) {
            this.projectatmosphere$updateStormUniforms(0, 0);
            this.projectatmosphere$currentTornadoCount = 0;
            this.projectatmosphere$currentHurricaneCount = 0;
            return;
        }

        this.projectatmosphere$updateTornadoUniformData(uploads, uniformTornadoCount);
        if (hurricaneCount <= 0) {
            this.projectatmosphere$updateStormUniforms(uniformTornadoCount, 0);
            this.projectatmosphere$currentTornadoCount = uniformTornadoCount;
            this.projectatmosphere$currentHurricaneCount = 0;
            return;
        }

        this.projectatmosphere$ensureStormBuffer();
        if (this.projectatmosphere$stormBuffer == null) {
            this.projectatmosphere$updateStormUniforms(uniformTornadoCount, 0);
            this.projectatmosphere$currentTornadoCount = uniformTornadoCount;
            this.projectatmosphere$currentHurricaneCount = 0;
            return;
        }

        this.projectatmosphere$bindStormBufferToShaders();
        this.projectatmosphere$stormBuffer.writeData(buffer -> {
            for (int i = 0; i < PROJECTATMOSPHERE$MAX_TORNADOES; i++) {
                if (i < tornadoCount) {
                    TornadoUpload upload = uploads.get(i);
                    buffer.putFloat(upload.typeIndex);
                    buffer.putFloat(upload.centerX);
                    buffer.putFloat(upload.centerZ);
                    buffer.putFloat(upload.radius);
                    buffer.putFloat(upload.bottom);
                    buffer.putFloat(upload.height);
                    buffer.putFloat(0.0F);
                    buffer.putFloat(0.0F);
                } else {
                    for (int j = 0; j < PROJECTATMOSPHERE$TORNADO_STRIDE / Float.BYTES; j++) {
                        buffer.putFloat(0.0F);
                    }
                }
            }
            for (int i = 0; i < PROJECTATMOSPHERE$MAX_HURRICANES; i++) {
                if (i < hurricaneCount) {
                    HurricaneUpload upload = hurricaneUploads.get(i);
                    buffer.putFloat(upload.typeIndex());
                    buffer.putFloat(upload.centerX());
                    buffer.putFloat(upload.centerZ());
                    buffer.putFloat(upload.anchorY());
                    buffer.putFloat(upload.coreRadius());
                    buffer.putFloat(upload.stormExtentRadius());
                    buffer.putFloat(upload.eyeRadius());
                    buffer.putFloat(upload.edgeFade());
                    buffer.putFloat(upload.bandCount());
                    buffer.putFloat(upload.bandWidth());
                    buffer.putFloat(upload.spiralTightness());
                    buffer.putFloat(upload.rotationPhase());
                    buffer.putFloat(upload.rotationSpeed());
                    buffer.putFloat(upload.transitionStart());
                    buffer.putFloat(upload.transitionEnd());
                    buffer.putFloat(0.0F);
                } else {
                    for (int j = 0; j < PROJECTATMOSPHERE$HURRICANE_STRIDE / Float.BYTES; j++) {
                        buffer.putFloat(0.0F);
                    }
                }
            }
            buffer.flip();
        }, this.projectatmosphere$stormBufferSize(), false);

        this.projectatmosphere$currentTornadoCount = uniformTornadoCount;
        this.projectatmosphere$currentHurricaneCount = hurricaneCount;
        this.projectatmosphere$updateStormUniforms(uniformTornadoCount, hurricaneCount);
    }

    @Unique
    private void projectatmosphere$ensureStormBuffer() {
        if (this.projectatmosphere$stormBuffer != null && this.projectatmosphere$stormBuffer.getId() == -1) {
            this.projectatmosphere$stormBuffer = null;
            this.projectatmosphere$stormBufferUsesBindingManager = false;
        }
        if (this.regionTextureGenerator == null || this.projectatmosphere$stormBuffer != null) {
            return;
        }
        if (!this.projectatmosphere$supportsStormBuffer()) {
            return;
        }
        ShaderStorageBufferObject newBuffer = null;
        try {
            newBuffer = this.projectatmosphere$createStormBuffer();
            if (newBuffer != null) {
                newBuffer.allocateBuffer(this.projectatmosphere$stormBufferSize());
                this.projectatmosphere$stormBuffer = newBuffer;
                this.projectatmosphere$bindStormBufferToShaders();
            }
        } catch (Throwable e) {
            if (newBuffer != null) {
                this.projectatmosphere$closeFailedStormBuffer(newBuffer);
            }
            this.projectatmosphere$stormBuffer = null;
            this.projectatmosphere$stormBufferUsesBindingManager = false;
            if (!this.projectatmosphere$stormBufferUnavailableLogged) {
                this.projectatmosphere$stormBufferUnavailableLogged = true;
                PROJECTATMOSPHERE$LOGGER.warn("Unable to allocate Simple Clouds storm SSBO '{}'; tornado cloud carving and hurricane cloud shaping are disabled for this client. Cause: {}", PROJECTATMOSPHERE$STORM_BUFFER_NAME, e.getMessage());
            }
        }
    }

    @Unique
    private void projectatmosphere$bindStormBufferToShaders() {
        if (this.projectatmosphere$stormBuffer == null || this.projectatmosphere$stormBuffer.getId() == -1) {
            return;
        }
        ComputeShader shader = this.projectatmosphere$getShader();
        if (shader != null && shader.isValid()) {
            this.projectatmosphere$stormBuffer.optionalBindToProgram(PROJECTATMOSPHERE$STORM_BUFFER_NAME, shader.getId());
        }
    }

    @Unique
    private int projectatmosphere$stormBufferSize() {
        return PROJECTATMOSPHERE$MAX_TORNADOES * PROJECTATMOSPHERE$TORNADO_STRIDE
                + PROJECTATMOSPHERE$MAX_HURRICANES * PROJECTATMOSPHERE$HURRICANE_STRIDE;
    }

    @Unique
    private List<TornadoUpload> projectatmosphere$collectTornadoUploads(float partialTick) {
        List<TornadoUpload> uploads = new ArrayList<>();
        List<RegionUpload> regions = this.projectatmosphere$getRenderableRegions(partialTick);
        for (RegionUpload regionUpload : regions) {
            if (!(regionUpload.region instanceof ITornadoRegion tornadoRegion)) {
                continue;
            }
            List<TornadoDescriptor> descriptors = tornadoRegion.getTornadoesView();
            if (descriptors.isEmpty()) {
                continue;
            }
            float[] data = regionUpload.data;
            float typeIndex = data[2];
            float baseX = data[0];
            float baseZ = data[1];
            float maxRadius = data[3];
            for (TornadoDescriptor descriptor : descriptors) {
                float centerX = baseX + descriptor.getOffsetX();
                float centerZ = baseZ + descriptor.getOffsetZ();
                float radius = Mth.clamp(descriptor.getRadius(), 0.5F, maxRadius);
                float height = Math.max(1.0F, descriptor.getHeight());
                uploads.add(new TornadoUpload(typeIndex, centerX, centerZ, radius, descriptor.getBottomY(), height));
                if (uploads.size() >= PROJECTATMOSPHERE$MAX_TORNADOES) {
                    return uploads;
                }
            }
        }
        return uploads;
    }

    @Unique
    private List<HurricaneUpload> projectatmosphere$collectHurricaneUploads(float partialTick) {
        List<HurricaneUpload> uploads = new ArrayList<>();
        int hurricaneTypeIndex = this.projectatmosphere$findCloudTypeIndex(PROJECTATMOSPHERE$HURRICANE_CLOUD_ID);
        if (hurricaneTypeIndex < 0) {
            return uploads;
        }

        for (ClientHurricaneStateCache.RenderableHurricane hurricane : ClientHurricaneStateCache.getRenderableHurricanes(partialTick)) {
            if (!Objects.equals(hurricane.cloudTypeId(), PROJECTATMOSPHERE$HURRICANE_CLOUD_ID)) {
                continue;
            }
            uploads.add(new HurricaneUpload(
                    hurricaneTypeIndex,
                    (float)hurricane.centerX(),
                    (float)hurricane.centerZ(),
                    hurricane.anchorY(),
                    hurricane.coreRadius(),
                    hurricane.stormExtentRadius(),
                    hurricane.eyeRadius(),
                    Math.max(1.0F, hurricane.edgeFade()),
                    Math.max(1, hurricane.bandCount()),
                    Math.max(1.0F, hurricane.bandWidth()),
                    hurricane.spiralTightness(),
                    hurricane.rotationPhase(),
                    hurricane.rotationSpeed(),
                    hurricane.transitionStart(),
                    hurricane.transitionEnd()
            ));
            if (uploads.size() >= PROJECTATMOSPHERE$MAX_HURRICANES) {
                break;
            }
        }
        return uploads;
    }

    @Unique
    private List<RegionUpload> projectatmosphere$getRenderableRegions(float partialTick) {
        List<RegionUpload> regions = new ArrayList<>();
        if (this.cloudGetter == null) {
            return regions;
        }
        for (CloudRegion region : this.cloudGetter.getClouds()) {
            float[] upload = this.projectatmosphere$buildRegionUpload(partialTick, region);
            if (!this.projectatmosphere$isRegionValid(upload)) {
                continue;
            }
            regions.add(new RegionUpload(region, upload));
            if (regions.size() >= MAX_CLOUD_FORMATIONS) {
                break;
            }
        }
        return regions;
    }

    @Unique
    private float[] projectatmosphere$buildRegionUpload(float partialTick, CloudRegion region) {
        Matrix2f transform = region.createTransform(partialTick);
        int typeIndex = this.projectatmosphere$findCloudTypeIndex(region.getCloudTypeId());
        return new float[]{
                region.getPosX(partialTick),
                region.getPosZ(partialTick),
                typeIndex,
                region.getRadius(partialTick),
                transform.m00,
                transform.m01,
                transform.m10,
                transform.m11
        };
    }

    @Unique
    private boolean projectatmosphere$isRegionValid(float[] upload) {
        return upload != null && upload.length > 2 && upload[2] >= 0.0F;
    }

    @Unique
    private int projectatmosphere$findCloudTypeIndex(ResourceLocation cloudTypeId) {
        if (this.cachedTypes == null || this.cachedTypes.length == 0 || this.cloudGetter == null) {
            return -1;
        }
        CloudType type = this.cloudGetter.getCloudTypeForId(cloudTypeId);
        if (type == null) {
            return -1;
        }
        for (int i = 0; i < this.cachedTypes.length; i++) {
            if (Objects.equals(this.cachedTypes[i], type)) {
                return i;
            }
        }
        return -1;
    }

    @Unique
    private boolean projectatmosphere$hurricaneIntersects(ClientHurricaneStateCache.RenderableHurricane hurricane, float minX, float minZ, float maxX, float maxZ) {
        float closestX = Mth.clamp((float)hurricane.centerX(), minX, maxX);
        float closestZ = Mth.clamp((float)hurricane.centerZ(), minZ, maxZ);
        float dx = closestX - (float)hurricane.centerX();
        float dz = closestZ - (float)hurricane.centerZ();
        float radius = hurricane.stormExtentRadius() + hurricane.edgeFade();
        return dx * dx + dz * dz <= radius * radius;
    }

    @Unique
    private boolean projectatmosphere$supportsStormBuffer() {
        if (this.regionTextureGenerator == null || !this.regionTextureGenerator.isValid()) {
            return false;
        }
        if (AtmoCommonConfig.DISABLE_SIMPLE_CLOUDS_TORNADO_SSBO.get()) {
            if (!this.projectatmosphere$stormBufferUnavailableLogged) {
                this.projectatmosphere$stormBufferUnavailableLogged = true;
                PROJECTATMOSPHERE$LOGGER.warn("Simple Clouds storm SSBO integration is disabled by config; tornado cloud carving will use uniforms and hurricane cloud shaping is disabled.");
            }
            return false;
        }
        int maxBindings = GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);
        if (maxBindings <= 16) {
            if (!this.projectatmosphere$stormBufferUnavailableLogged) {
                this.projectatmosphere$stormBufferUnavailableLogged = true;
                PROJECTATMOSPHERE$LOGGER.warn("Disabling Simple Clouds storm SSBO integration because this GPU exposes only {} shader storage buffer bindings. Tornado cloud carving will use uniforms and hurricane cloud shaping is disabled.", maxBindings);
            }
            return false;
        }
        int shaderId = this.regionTextureGenerator.getId();
        if (shaderId <= 0) {
            return false;
        }
        if (shaderId != this.projectatmosphere$lastStormShaderId) {
            this.projectatmosphere$lastStormShaderId = shaderId;
            int index = GL43.glGetProgramResourceIndex(shaderId, GL43.GL_SHADER_STORAGE_BLOCK, PROJECTATMOSPHERE$STORM_BUFFER_NAME);
            this.projectatmosphere$hasStormBlock = index != GL43.GL_INVALID_INDEX;
            if (!this.projectatmosphere$hasStormBlock) {
                PROJECTATMOSPHERE$LOGGER.warn("Missing '{}' SSBO on shader '{}'; tornado cloud carving and hurricane cloud shaping are disabled until the shader is updated.", PROJECTATMOSPHERE$STORM_BUFFER_NAME, this.regionTextureGenerator.getName());
            }
        }
        return this.projectatmosphere$hasStormBlock;
    }

    @Unique
    private ShaderStorageBufferObject projectatmosphere$createStormBuffer() {
        this.projectatmosphere$stormBufferUsesBindingManager = true;
        return this.regionTextureGenerator.createAndBindSSBO(PROJECTATMOSPHERE$STORM_BUFFER_NAME, GL43.GL_DYNAMIC_DRAW);
    }

    @Unique
    private void projectatmosphere$closeFailedStormBuffer(ShaderStorageBufferObject buffer) {
        if (this.projectatmosphere$stormBufferUsesBindingManager) {
            BindingManager.freeSSBO(buffer);
        } else {
            buffer.close();
        }
        this.projectatmosphere$stormBufferUsesBindingManager = false;
    }

    @Unique
    private void projectatmosphere$closeManualStormBuffer() {
        if (this.projectatmosphere$stormBuffer != null && !this.projectatmosphere$stormBufferUsesBindingManager && this.projectatmosphere$stormBuffer.getId() != -1) {
            this.projectatmosphere$stormBuffer.close();
        }
    }

    @Unique
    private void projectatmosphere$updateStormUniforms(int tornadoCount, int hurricaneCount) {
        if (this.regionTextureGenerator != null && this.regionTextureGenerator.isValid()) {
            this.regionTextureGenerator.forUniform("TotalCloudTornadoes", (program, location) -> GL41.glProgramUniform1i(program, location, tornadoCount));
            this.regionTextureGenerator.forUniform("TotalCloudHurricanes", (program, location) -> GL41.glProgramUniform1i(program, location, hurricaneCount));
        }
        ComputeShader shader = this.projectatmosphere$getShader();
        if (shader != null && shader.isValid()) {
            shader.forUniform("TotalCloudTornadoes", (program, location) -> GL41.glProgramUniform1i(program, location, tornadoCount));
            shader.forUniform("TotalCloudHurricanes", (program, location) -> GL41.glProgramUniform1i(program, location, hurricaneCount));
        }
    }

    @Unique
    private void projectatmosphere$updateTornadoUniformData(List<TornadoUpload> uploads, int tornadoCount) {
        float[] data0 = new float[PROJECTATMOSPHERE$MAX_UNIFORM_TORNADOES * 4];
        float[] data1 = new float[PROJECTATMOSPHERE$MAX_UNIFORM_TORNADOES * 4];
        for (int i = 0; i < tornadoCount; i++) {
            TornadoUpload upload = uploads.get(i);
            int base = i * 4;
            data0[base] = upload.typeIndex;
            data0[base + 1] = upload.centerX;
            data0[base + 2] = upload.centerZ;
            data0[base + 3] = upload.radius;
            data1[base] = upload.bottom;
            data1[base + 1] = upload.height;
        }

        this.projectatmosphere$setTornadoRegionUniforms(this.regionTextureGenerator, data0);
        this.projectatmosphere$setTornadoMeshUniforms(this.projectatmosphere$getShader(), data0, data1);
    }

    @Unique
    private void projectatmosphere$setTornadoRegionUniforms(ComputeShader shader, float[] data0) {
        if (shader == null || !shader.isValid()) {
            return;
        }
        shader.forUniform("CloudTornadoData0[0]", (program, location) -> GL41.glProgramUniform4fv(program, location, data0));
    }

    @Unique
    private void projectatmosphere$setTornadoMeshUniforms(ComputeShader shader, float[] data0, float[] data1) {
        if (shader == null || !shader.isValid()) {
            return;
        }
        shader.forUniform("CloudTornadoData0[0]", (program, location) -> GL41.glProgramUniform4fv(program, location, data0));
        shader.forUniform("CloudTornadoData1[0]", (program, location) -> GL41.glProgramUniform4fv(program, location, data1));
    }

    @Unique
    private static Method projectatmosphere$skipSettingsMethod;
    @Unique
    private static Method projectatmosphere$heightSettingsMethod;

    @Unique
    private ComputeShader projectatmosphere$getShader() {
        return ((CloudMeshGeneratorAccessor)(Object)this).projectatmosphere$getShader();
    }

    @Unique
    private Object projectatmosphere$invokeSkipSettings() {
        try {
            if (projectatmosphere$skipSettingsMethod == null) {
                projectatmosphere$skipSettingsMethod = dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator.class.getDeclaredMethod("skip");
                projectatmosphere$skipSettingsMethod.setAccessible(true);
            }
            return projectatmosphere$skipSettingsMethod.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke Simple Clouds skip() helper", e);
        }
    }

    @Unique
    private Object projectatmosphere$invokeHeightSettings(int minHeight, int maxHeight) {
        try {
            if (projectatmosphere$heightSettingsMethod == null) {
                projectatmosphere$heightSettingsMethod = dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator.class.getDeclaredMethod("heights", int.class, int.class);
                projectatmosphere$heightSettingsMethod.setAccessible(true);
            }
            return projectatmosphere$heightSettingsMethod.invoke(null, minHeight, maxHeight);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke Simple Clouds heights(...) helper", e);
        }
    }
}
