package net.Gabou.projectatmosphere.mixin;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.region.ITornadoRegion;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.region.TornadoDescriptor;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.MultiRegionCloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.shader.buffer.ShaderStorageBufferObject;
import dev.nonamecrackers2.simpleclouds.client.shader.compute.ComputeShader;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudGetter;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL41;
import org.lwjgl.opengl.GL43;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = MultiRegionCloudMeshGenerator.class, remap = false)
public abstract class MultiRegionCloudMeshGeneratorMixin {
    @Shadow @Final public static int MAX_CLOUD_FORMATIONS;

    @Shadow protected ComputeShader regionTextureGenerator;

    @Shadow protected ComputeShader shader;

    @Shadow protected CloudGetter cloudGetter;

    @Unique private static final int PROJECTATMOSPHERE$MAX_TORNADOES = Math.max(1, Integer.getInteger("projectatmosphere.simpleclouds.maxTornadoes", 64));
    @Unique private static final int PROJECTATMOSPHERE$TORNADO_STRIDE = 32;

    @Unique private ShaderStorageBufferObject projectatmosphere$tornadoBuffer;
    @Unique private int projectatmosphere$currentTornadoCount;

    @Invoker("lambda$uploadCloudRegionData$4")
    protected abstract float[] projectatmosphere$invokeRegionUpload(float partialTick, CloudRegion region);

    @Invoker("lambda$uploadCloudRegionData$5")
    protected abstract boolean projectatmosphere$invokeIsRegionValid(float[] upload);

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
        this.regionTextureGenerator.forUniform("TotalCloudTornadoes", (name, location) -> GL41.glProgramUniform1i(location, count));
        if (this.shader != null && this.shader.isValid()) {
            this.shader.forUniform("TotalCloudTornadoes", (name, location) -> GL41.glProgramUniform1i(location, count));
        }
    }

    @Unique
    private void projectatmosphere$ensureTornadoBuffer() {
        if (this.regionTextureGenerator == null || this.projectatmosphere$tornadoBuffer != null) {
            return;
        }
        this.projectatmosphere$tornadoBuffer = this.regionTextureGenerator.createAndBindSSBO("CloudTornadoes", GL43.GL_DYNAMIC_DRAW);
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
            float[] upload = this.projectatmosphere$invokeRegionUpload(partialTick, region);
            if (!this.projectatmosphere$invokeIsRegionValid(upload)) {
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
    private static final class RegionUpload {
        private final CloudRegion region;
        private final float[] data;

        private RegionUpload(CloudRegion region, float[] data) {
            this.region = region;
            this.data = data;
        }
    }

    @Unique
    private static final class TornadoUpload {
        private final float typeIndex;
        private final float centerX;
        private final float centerZ;
        private final float radius;
        private final float bottom;
        private final float height;

        private TornadoUpload(float typeIndex, float centerX, float centerZ, float radius, float bottom, float height) {
            this.typeIndex = typeIndex;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.bottom = bottom;
            this.height = height;
        }
    }
}
