package net.Gabou.projectatmosphere.mixin.client;

import dev.nonamecrackers2.simpleclouds.client.mesh.chunk.MeshChunk;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import net.Gabou.projectatmosphere.client.hurricane.cache.ClientHurricaneStateCache;
import net.Gabou.projectatmosphere.clouds.client.render.ClientCloudRenderOwnership;
import net.Gabou.projectatmosphere.tools.debug.SimpleCloudsRenderDiagnostics;
import net.Gabou.projectatmosphere.mixin.CloudMeshGeneratorDiagnosticsAccessor;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SimpleCloudsRenderer.class, remap = false)
public abstract class SimpleCloudsRendererDiagnosticsMixin {
    @Inject(
            method = "renderCloudsOpaque(Ldev/nonamecrackers2/simpleclouds/client/mesh/generator/CloudMeshGenerator;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FFFFFFLnet/minecraft/client/renderer/culling/Frustum;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void projectatmosphere$beginOpaquePass(CloudMeshGenerator generator, com.mojang.blaze3d.vertex.PoseStack stack, Matrix4f projMat, float fogStart, float fogEnd, float partialTick, float r, float g, float b, Frustum frustum, boolean ditherFade, CallbackInfo ci) {
        if (ClientCloudRenderOwnership.ownsOpaqueCloudPass(Minecraft.getInstance().level)) {
            ci.cancel();
            return;
        }
        if (ditherFade && ClientHurricaneStateCache.hasHurricanes()) {
            SimpleCloudsRenderer.renderCloudsOpaque(generator, stack, projMat, fogStart, fogEnd, partialTick, r, g, b, frustum, false);
            ci.cancel();
            return;
        }
        if (generator == null || SimpleCloudsRenderDiagnostics.isDhPipelineActive()) {
            return;
        }

        CloudMeshGeneratorDiagnosticsAccessor accessor = (CloudMeshGeneratorDiagnosticsAccessor)(Object)generator;
        SimpleCloudsRenderDiagnostics.beginPass(
                "opaque",
                accessor.projectatmosphere$getChunks() == null ? 0 : accessor.projectatmosphere$getChunks().size(),
                accessor.projectatmosphere$getOpaqueBufferSize(),
                accessor.projectatmosphere$getTransparentBufferSize(),
                projectatmosphere$countElements(accessor, false),
                projectatmosphere$countElements(accessor, true),
                generator.canRender(),
                generator.transparencyEnabled(),
                accessor.projectatmosphere$getMeshGenStatus()
        );
    }

    @Inject(method = "renderCloudsOpaque(Ldev/nonamecrackers2/simpleclouds/client/mesh/generator/CloudMeshGenerator;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FFFFFFLnet/minecraft/client/renderer/culling/Frustum;Z)V", at = @At("RETURN"))
    private static void projectatmosphere$endOpaquePass(CloudMeshGenerator generator, com.mojang.blaze3d.vertex.PoseStack stack, Matrix4f projMat, float fogStart, float fogEnd, float partialTick, float r, float g, float b, Frustum frustum, boolean ditherFade, CallbackInfo ci) {
        if (SimpleCloudsRenderDiagnostics.isDhPipelineActive()) {
            return;
        }
        SimpleCloudsRenderDiagnostics.endPass();
    }

    @Inject(
            method = "renderCloudsTransparency(Ldev/nonamecrackers2/simpleclouds/client/mesh/generator/CloudMeshGenerator;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FFFFFFLnet/minecraft/client/renderer/culling/Frustum;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void projectatmosphere$beginTransparencyPass(CloudMeshGenerator generator, com.mojang.blaze3d.vertex.PoseStack stack, Matrix4f projMat, float fogStart, float fogEnd, float partialTick, float r, float g, float b, Frustum frustum, boolean ditherFade, CallbackInfo ci) {
        if (ClientCloudRenderOwnership.ownsTransparentCloudPass(Minecraft.getInstance().level)) {
            ci.cancel();
            return;
        }
        if (ditherFade && ClientHurricaneStateCache.hasHurricanes()) {
            SimpleCloudsRenderer.renderCloudsTransparency(generator, stack, projMat, fogStart, fogEnd, partialTick, r, g, b, frustum, false);
            ci.cancel();
            return;
        }
        if (generator == null || SimpleCloudsRenderDiagnostics.isDhPipelineActive()) {
            return;
        }

        CloudMeshGeneratorDiagnosticsAccessor accessor = (CloudMeshGeneratorDiagnosticsAccessor)(Object)generator;
        SimpleCloudsRenderDiagnostics.beginPass(
                "transparent",
                accessor.projectatmosphere$getChunks() == null ? 0 : accessor.projectatmosphere$getChunks().size(),
                accessor.projectatmosphere$getOpaqueBufferSize(),
                accessor.projectatmosphere$getTransparentBufferSize(),
                projectatmosphere$countElements(accessor, false),
                projectatmosphere$countElements(accessor, true),
                generator.canRender(),
                generator.transparencyEnabled(),
                accessor.projectatmosphere$getMeshGenStatus()
        );
    }

    @Inject(method = "renderCloudsTransparency(Ldev/nonamecrackers2/simpleclouds/client/mesh/generator/CloudMeshGenerator;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FFFFFFLnet/minecraft/client/renderer/culling/Frustum;Z)V", at = @At("RETURN"))
    private static void projectatmosphere$endTransparencyPass(CloudMeshGenerator generator, com.mojang.blaze3d.vertex.PoseStack stack, Matrix4f projMat, float fogStart, float fogEnd, float partialTick, float r, float g, float b, Frustum frustum, boolean ditherFade, CallbackInfo ci) {
        if (SimpleCloudsRenderDiagnostics.isDhPipelineActive()) {
            return;
        }
        SimpleCloudsRenderDiagnostics.endPass();
    }

    private static int projectatmosphere$countElements(CloudMeshGeneratorDiagnosticsAccessor accessor, boolean transparent) {
        if (accessor.projectatmosphere$getChunks() == null) {
            return 0;
        }

        int total = 0;
        for (MeshChunk chunk : accessor.projectatmosphere$getChunks()) {
            if (transparent) {
                total += chunk.getTransparentBuffers().map(MeshChunk.BufferSet::getElementCount).orElse(0);
            } else {
                total += chunk.getOpaqueBuffers().getElementCount();
            }
        }
        return total;
    }

}
