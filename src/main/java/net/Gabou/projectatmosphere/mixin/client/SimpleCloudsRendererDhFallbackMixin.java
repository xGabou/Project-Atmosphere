package net.Gabou.projectatmosphere.mixin.client;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.client.dh.pipeline.DhSupportPipeline;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.CloudsRenderPipeline;
import net.Gabou.projectatmosphere.tools.debug.SimpleCloudsRenderDiagnostics;
import net.Gabou.projectatmosphere.mixin.CloudMeshGeneratorDiagnosticsAccessor;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(value = SimpleCloudsRenderer.class, remap = false)
public abstract class SimpleCloudsRendererDhFallbackMixin {
    @Shadow
    @Nullable
    private CloudsRenderPipeline renderPipelineThisPass;

    @Inject(method = "renderBeforeLevel", at = @At("TAIL"))
    private void projectatmosphere$forceDhSupportPipeline(com.mojang.blaze3d.vertex.PoseStack stack, Matrix4f projMat, float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        if (!SimpleCloudsMod.dhLoaded() || this.renderPipelineThisPass == DhSupportPipeline.INSTANCE) {
            return;
        }

        this.renderPipelineThisPass = DhSupportPipeline.INSTANCE;

        CloudMeshGenerator generator = ((SimpleCloudsRenderer)(Object)this).getMeshGenerator();
        CloudMeshGeneratorDiagnosticsAccessor accessor = (CloudMeshGeneratorDiagnosticsAccessor)(Object)generator;
        SimpleCloudsRenderDiagnostics.logDhPipelineFallback(
                "dh_support",
                accessor.projectatmosphere$getChunks() == null ? 0 : accessor.projectatmosphere$getChunks().size(),
                accessor.projectatmosphere$getChunkGenTasks() == null ? 0 : accessor.projectatmosphere$getChunkGenTasks().size(),
                accessor.projectatmosphere$getCompletedGenTasks() == null ? 0 : accessor.projectatmosphere$getCompletedGenTasks().size(),
                generator.canRender(),
                generator.transparencyEnabled(),
                accessor.projectatmosphere$getMeshGenStatus()
        );
    }
}
