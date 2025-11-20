package net.Gabou.projectatmosphere.mixin;

import net.Gabou.projectatmosphere.api.common.cloud.region.ITornadoRegion;
import net.Gabou.projectatmosphere.api.common.cloud.region.TornadoDescriptor;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.MultiRegionCloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.shader.buffer.ShaderStorageBufferObject;
import dev.nonamecrackers2.simpleclouds.client.shader.compute.ComputeShader;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudInfo;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudGetter;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL41;
import org.lwjgl.opengl.GL43;
import org.joml.Matrix2f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Mixin(value = MultiRegionCloudMeshGenerator.class, remap = false)
public abstract class MultiRegionCloudMeshGeneratorMixin {
    @Unique private static final Logger PROJECTATMOSPHERE$LOGGER = LogManager.getLogger("ProjectAtmosphere/CloudMeshGenerator");
    @Unique private static final String PROJECTATMOSPHERE$TORNADO_BUFFER_NAME = "CloudTornadoes";

    @Shadow @Final public static int MAX_CLOUD_FORMATIONS;

    @Shadow protected ComputeShader regionTextureGenerator;

    @Shadow protected CloudGetter cloudGetter;

    @Shadow protected CloudInfo[] cachedTypes;

    @Unique private static final int PROJECTATMOSPHERE$MAX_TORNADOES = Math.max(1, Integer.getInteger("projectatmosphere.simpleclouds.maxTornadoes", 64));
    @Unique private static final int PROJECTATMOSPHERE$TORNADO_STRIDE = 32;

    @Unique private ShaderStorageBufferObject projectatmosphere$tornadoBuffer;
    @Unique private int projectatmosphere$currentTornadoCount;
    @Unique private boolean projectatmosphere$hasTornadoBlock;
    @Unique private int projectatmosphere$lastTornadoShaderId = -1;

    @Inject(method = "setupShader", at = @At("TAIL"))
    private void projectatmosphere$setupTornadoSsbo(CallbackInfo ci) {
        this.projectatmosphere$ensureTornadoBuffer();
    }

    @Inject(method = "uploadCloudRegionData", at = @At("TAIL"))
    private void projectatmosphere$uploadTornadoData(float partialTick, CallbackInfo ci) {
        if (this.regionTextureGenerator == null || !this.regionTextureGenerator.isValid()) {
            return;
        }
        this.projectatmosphere$ensureTornadoBuffer();
        if (this.projectatmosphere$tornadoBuffer == null) {
            if (this.projectatmosphere$currentTornadoCount != 0) {
                this.projectatmosphere$currentTornadoCount = 0;
                this.projectatmosphere$updateTornadoUniforms(0);
            }
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
    private void projectatmosphere$ensureTornadoBuffer() {
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
        int typeIndex = this.projectatmosphere$findCloudTypeIndex(region);
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
    private int projectatmosphere$findCloudTypeIndex(CloudRegion region) {
        if (this.cachedTypes == null || this.cachedTypes.length == 0) {
            return -1;
        }
        CloudType type = this.cloudGetter.getCloudTypeForId(region.getCloudTypeId());
        for (int i = 0; i < this.cachedTypes.length; i++) {
            if (Objects.equals(this.cachedTypes[i], type)) {
                return i;
            }
        }
        return -1;
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
    private ComputeShader projectatmosphere$getShader() {
        return ((CloudMeshGeneratorAccessor) (Object) this).projectatmosphere$getShader();
    }
}
