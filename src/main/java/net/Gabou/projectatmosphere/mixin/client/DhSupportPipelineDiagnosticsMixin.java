package net.Gabou.projectatmosphere.mixin.client;

import dev.nonamecrackers2.simpleclouds.client.dh.pipeline.DhSupportPipeline;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import net.Gabou.projectatmosphere.tools.debug.SimpleCloudsRenderDiagnostics;
import net.Gabou.projectatmosphere.mixin.CloudMeshGeneratorDiagnosticsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DhSupportPipeline.class, remap = false)
public abstract class DhSupportPipelineDiagnosticsMixin {
    @Inject(method = "afterDistantHorizonsRender", at = @At("HEAD"))
    private void projectatmosphere$beginDhPass(Minecraft mc, SimpleCloudsRenderer renderer, com.mojang.blaze3d.vertex.PoseStack stack, Matrix4f projMat, float partialTick, double camX, double camY, double camZ, Frustum frustum, int dhFbo, CallbackInfo ci) {
        CloudMeshGenerator generator = renderer.getMeshGenerator();
        CloudMeshGeneratorDiagnosticsAccessor accessor = (CloudMeshGeneratorDiagnosticsAccessor)(Object)generator;
        SimpleCloudsRenderDiagnostics.beginDhPipelinePass(
                accessor.projectatmosphere$getChunks() == null ? 0 : accessor.projectatmosphere$getChunks().size(),
                accessor.projectatmosphere$getOpaqueBufferSize(),
                accessor.projectatmosphere$getTransparentBufferSize(),
                countElements(accessor, false),
                countElements(accessor, true),
                generator.canRender(),
                generator.transparencyEnabled(),
                accessor.projectatmosphere$getMeshGenStatus()
        );
    }

    @Inject(method = "afterDistantHorizonsRender", at = @At("RETURN"))
    private void projectatmosphere$endDhPass(Minecraft mc, SimpleCloudsRenderer renderer, com.mojang.blaze3d.vertex.PoseStack stack, Matrix4f projMat, float partialTick, double camX, double camY, double camZ, Frustum frustum, int dhFbo, CallbackInfo ci) {
        SimpleCloudsRenderDiagnostics.endDhPipelinePass();
    }

    private static int countElements(CloudMeshGeneratorDiagnosticsAccessor accessor, boolean transparent) {
        if (accessor.projectatmosphere$getChunks() == null) {
            return 0;
        }

        int total = 0;
        for (var chunk : accessor.projectatmosphere$getChunks()) {
            if (transparent) {
                total += chunk.getTransparentBuffers().map(dev.nonamecrackers2.simpleclouds.client.mesh.chunk.MeshChunk.BufferSet::getElementCount).orElse(0);
            } else {
                total += chunk.getOpaqueBuffers().getElementCount();
            }
        }
        return total;
    }

}
