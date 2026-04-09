package net.Gabou.projectatmosphere.mixin;

import com.google.common.collect.ImmutableMap;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.MultiRegionCloudMeshGenerator;
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
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix2f;
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
import net.Gabou.projectatmosphere.util.HurricaneUpload;
import net.Gabou.projectatmosphere.util.RegionUpload;
import net.Gabou.projectatmosphere.util.TornadoUpload;


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
    private static final String PROJECTATMOSPHERE$TORNADO_BUFFER_NAME = "CloudTornadoes";
    @Unique
    private static final String PROJECTATMOSPHERE$HURRICANE_BUFFER_NAME = "CloudHurricanes";
    @Unique
    private static final ResourceLocation PROJECTATMOSPHERE$HURRICANE_CLOUD_ID = HurricaneInstance.HURRICANE_CLOUD_TYPE_ID;

    @Shadow @Final public static int MAX_CLOUD_FORMATIONS;
    @Shadow protected ComputeShader regionTextureGenerator;
    @Shadow protected CloudGetter cloudGetter;
    @Shadow protected CloudInfo[] cachedTypes;

    @Unique
    private static final int PROJECTATMOSPHERE$MAX_TORNADOES = Math.max(1, Integer.getInteger("projectatmosphere.simpleclouds.maxTornadoes", 64));
    @Unique
    private static final int PROJECTATMOSPHERE$MAX_HURRICANES = Math.max(1, Integer.getInteger("projectatmosphere.simpleclouds.maxHurricanes", 8));
    @Unique
    private static final int PROJECTATMOSPHERE$TORNADO_STRIDE = 32;
    @Unique
    private static final int PROJECTATMOSPHERE$HURRICANE_STRIDE = 64;

    @Unique private ShaderStorageBufferObject projectatmosphere$tornadoBuffer;
    @Unique private ShaderStorageBufferObject projectatmosphere$hurricaneBuffer;
    @Unique private int projectatmosphere$currentTornadoCount;
    @Unique private int projectatmosphere$currentHurricaneCount;
    @Unique private boolean projectatmosphere$hasTornadoBlock;
    @Unique private boolean projectatmosphere$hasHurricaneBlock;
    @Unique private int projectatmosphere$lastTornadoShaderId = -1;
    @Unique private int projectatmosphere$lastHurricaneShaderId = -1;

    @Inject(method = "uploadCloudRegionData", at = @At("TAIL"))
    private void projectatmosphere$uploadNativeStormData(float partialTick, CallbackInfo ci) {
        this.projectatmosphere$uploadTornadoData(partialTick);
        this.projectatmosphere$uploadHurricaneData(partialTick);
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
        this.projectatmosphere$tornadoBuffer = null;
        this.projectatmosphere$hurricaneBuffer = null;
        this.projectatmosphere$currentTornadoCount = 0;
        this.projectatmosphere$currentHurricaneCount = 0;
        this.projectatmosphere$hasTornadoBlock = false;
        this.projectatmosphere$hasHurricaneBlock = false;
        this.projectatmosphere$lastTornadoShaderId = -1;
        this.projectatmosphere$lastHurricaneShaderId = -1;
    }

    @Unique
    private void projectatmosphere$uploadTornadoData(float partialTick) {
        if (this.regionTextureGenerator == null || !this.regionTextureGenerator.isValid()) {
            return;
        }
        this.projectatmosphere$ensureTornadoBuffer();
        if (this.projectatmosphere$tornadoBuffer == null) {
            this.projectatmosphere$updateTornadoUniforms(0);
            this.projectatmosphere$currentTornadoCount = 0;
            return;
        }

        List<TornadoUpload> uploads = this.projectatmosphere$collectTornadoUploads(partialTick);
        int count = Math.min(uploads.size(), PROJECTATMOSPHERE$MAX_TORNADOES);
        if (count > 0) {
            this.projectatmosphere$tornadoBuffer.writeData(buffer -> {
                for (int i = 0; i < count; i++) {
                    TornadoUpload upload = uploads.get(i);
                    buffer.putFloat(upload.typeIndex);
                    buffer.putFloat(upload.centerX);
                    buffer.putFloat(upload.centerZ);
                    buffer.putFloat(upload.radius);
                    buffer.putFloat(upload.bottom);
                    buffer.putFloat(upload.height);
                    buffer.putFloat(0.0F);
                    buffer.putFloat(0.0F);
                }
                buffer.flip();
            }, count * PROJECTATMOSPHERE$TORNADO_STRIDE, false);
        }
        this.projectatmosphere$currentTornadoCount = count;
        this.projectatmosphere$updateTornadoUniforms(count);
    }

    @Unique
    private void projectatmosphere$uploadHurricaneData(float partialTick) {
        if (this.regionTextureGenerator == null || !this.regionTextureGenerator.isValid()) {
            return;
        }
        this.projectatmosphere$ensureHurricaneBuffer();
        if (this.projectatmosphere$hurricaneBuffer == null) {
            this.projectatmosphere$updateHurricaneUniforms(0);
            this.projectatmosphere$currentHurricaneCount = 0;
            return;
        }

        List<HurricaneUpload> uploads = this.projectatmosphere$collectHurricaneUploads(partialTick);
        int count = Math.min(uploads.size(), PROJECTATMOSPHERE$MAX_HURRICANES);
        if (count > 0) {
            this.projectatmosphere$hurricaneBuffer.writeData(buffer -> {
                for (int i = 0; i < count; i++) {
                    HurricaneUpload upload = uploads.get(i);
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
                }
                buffer.flip();
            }, count * PROJECTATMOSPHERE$HURRICANE_STRIDE, false);
        }
        this.projectatmosphere$currentHurricaneCount = count;
        this.projectatmosphere$updateHurricaneUniforms(count);
    }

    @Unique
    private void projectatmosphere$ensureTornadoBuffer() {
        if (this.projectatmosphere$tornadoBuffer != null && this.projectatmosphere$tornadoBuffer.getId() == -1) {
            this.projectatmosphere$tornadoBuffer = null;
        }
        if (this.regionTextureGenerator == null || this.projectatmosphere$tornadoBuffer != null) {
            return;
        }
        if (!this.projectatmosphere$supportsTornadoBuffer()) {
            return;
        }
        this.projectatmosphere$tornadoBuffer = this.regionTextureGenerator.createAndBindSSBO(PROJECTATMOSPHERE$TORNADO_BUFFER_NAME, GL43.GL_DYNAMIC_DRAW);
        if (this.projectatmosphere$tornadoBuffer != null) {
            this.projectatmosphere$tornadoBuffer.allocateBuffer(PROJECTATMOSPHERE$MAX_TORNADOES * PROJECTATMOSPHERE$TORNADO_STRIDE);
        }
    }

    @Unique
    private void projectatmosphere$ensureHurricaneBuffer() {
        if (this.projectatmosphere$hurricaneBuffer != null && this.projectatmosphere$hurricaneBuffer.getId() == -1) {
            this.projectatmosphere$hurricaneBuffer = null;
        }
        if (this.regionTextureGenerator == null || this.projectatmosphere$hurricaneBuffer != null) {
            return;
        }
        if (!this.projectatmosphere$supportsHurricaneBuffer()) {
            return;
        }
        this.projectatmosphere$hurricaneBuffer = this.regionTextureGenerator.createAndBindSSBO(PROJECTATMOSPHERE$HURRICANE_BUFFER_NAME, GL43.GL_DYNAMIC_DRAW);
        if (this.projectatmosphere$hurricaneBuffer != null) {
            this.projectatmosphere$hurricaneBuffer.allocateBuffer(PROJECTATMOSPHERE$MAX_HURRICANES * PROJECTATMOSPHERE$HURRICANE_STRIDE);
        }
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
    private boolean projectatmosphere$supportsTornadoBuffer() {
        if (this.regionTextureGenerator == null || !this.regionTextureGenerator.isValid()) {
            return false;
        }
        int shaderId = this.regionTextureGenerator.getId();
        if (shaderId <= 0) {
            return false;
        }
        if (shaderId != this.projectatmosphere$lastTornadoShaderId) {
            this.projectatmosphere$lastTornadoShaderId = shaderId;
            int index = GL43.glGetProgramResourceIndex(shaderId, GL43.GL_SHADER_STORAGE_BLOCK, PROJECTATMOSPHERE$TORNADO_BUFFER_NAME);
            this.projectatmosphere$hasTornadoBlock = index != GL43.GL_INVALID_INDEX;
            if (!this.projectatmosphere$hasTornadoBlock) {
                PROJECTATMOSPHERE$LOGGER.warn("Missing '{}' SSBO on shader '{}'; tornado rendering disabled until the shader is updated.", PROJECTATMOSPHERE$TORNADO_BUFFER_NAME, this.regionTextureGenerator.getName());
            }
        }
        return this.projectatmosphere$hasTornadoBlock;
    }

    @Unique
    private boolean projectatmosphere$supportsHurricaneBuffer() {
        if (this.regionTextureGenerator == null || !this.regionTextureGenerator.isValid()) {
            return false;
        }
        int shaderId = this.regionTextureGenerator.getId();
        if (shaderId <= 0) {
            return false;
        }
        if (shaderId != this.projectatmosphere$lastHurricaneShaderId) {
            this.projectatmosphere$lastHurricaneShaderId = shaderId;
            int index = GL43.glGetProgramResourceIndex(shaderId, GL43.GL_SHADER_STORAGE_BLOCK, PROJECTATMOSPHERE$HURRICANE_BUFFER_NAME);
            this.projectatmosphere$hasHurricaneBlock = index != GL43.GL_INVALID_INDEX;
            if (!this.projectatmosphere$hasHurricaneBlock) {
                PROJECTATMOSPHERE$LOGGER.warn("Missing '{}' SSBO on shader '{}'; hurricane rendering disabled until the shader is updated.", PROJECTATMOSPHERE$HURRICANE_BUFFER_NAME, this.regionTextureGenerator.getName());
            }
        }
        return this.projectatmosphere$hasHurricaneBlock;
    }

    @Unique
    private void projectatmosphere$updateTornadoUniforms(int count) {
        if (this.regionTextureGenerator != null && this.regionTextureGenerator.isValid()) {
            this.regionTextureGenerator.forUniform("TotalCloudTornadoes", (program, location) -> GL41.glProgramUniform1i(program, location, count));
        }
        ComputeShader shader = this.projectatmosphere$getShader();
        if (shader != null && shader.isValid()) {
            shader.forUniform("TotalCloudTornadoes", (program, location) -> GL41.glProgramUniform1i(program, location, count));
        }
    }
    @Unique
    private void projectatmosphere$updateHurricaneUniforms(int count) {
        if (this.regionTextureGenerator != null && this.regionTextureGenerator.isValid()) {
            this.regionTextureGenerator.forUniform("TotalCloudHurricanes", (program, location) -> GL41.glProgramUniform1i(program, location, count));
        }
        ComputeShader shader = this.projectatmosphere$getShader();
        if (shader != null && shader.isValid()) {
            shader.forUniform("TotalCloudHurricanes", (program, location) -> GL41.glProgramUniform1i(program, location, count));
        }
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
