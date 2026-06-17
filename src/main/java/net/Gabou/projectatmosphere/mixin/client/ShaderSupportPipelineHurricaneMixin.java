package net.Gabou.projectatmosphere.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.ShaderSupportPipeline;
import net.Gabou.projectatmosphere.client.hurricane.cache.ClientHurricaneStateCache;
import net.Gabou.projectatmosphere.client.render.SimpleCloudsHurricaneRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShaderSupportPipeline.class, remap = false)
public abstract class ShaderSupportPipelineHurricaneMixin {
    @Inject(
            method = "afterLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/nonamecrackers2/simpleclouds/client/renderer/SimpleCloudsRenderer;getCloudTransparencyTarget()Ldev/nonamecrackers2/simpleclouds/client/framebuffer/WeightedBlendingTarget;"
            ),
            require = 0
    )
    private void projectatmosphere$renderHurricaneOpaque(Minecraft mc, SimpleCloudsRenderer renderer,
                                                         PoseStack stack, Matrix4f projMat, float partialTick,
                                                         double camX, double camY, double camZ, Frustum frustum,
                                                         CallbackInfo ci) {
        ClientLevel level = mc.level;
        if (level == null || !ClientHurricaneStateCache.hasHurricanes()) {
            return;
        }

        float[] cloudColor = renderer.getCloudColor(partialTick);
        mc.getProfiler().push("projectatmosphere_hurricane_opaque");
        SimpleCloudsHurricaneRenderer.INSTANCE.prepareFrame(level, partialTick);
        renderer.getCloudTarget().bindWrite(false);
        SimpleCloudsHurricaneRenderer.INSTANCE.renderOpaque(
                renderer, stack, projMat, partialTick, cloudColor[0], cloudColor[1], cloudColor[2]
        );
        mc.getProfiler().pop();
    }

    @Inject(
            method = "afterLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/nonamecrackers2/simpleclouds/client/renderer/SimpleCloudsRenderer;doFinalCompositePass(Lcom/mojang/blaze3d/vertex/PoseStack;FLorg/joml/Matrix4f;)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void projectatmosphere$renderHurricaneTransparency(Minecraft mc, SimpleCloudsRenderer renderer,
                                                               PoseStack stack, Matrix4f projMat, float partialTick,
                                                               double camX, double camY, double camZ, Frustum frustum,
                                                               CallbackInfo ci) {
        ClientLevel level = mc.level;
        if (level == null || !ClientHurricaneStateCache.hasHurricanes()) {
            return;
        }

        float[] cloudColor = renderer.getCloudColor(partialTick);
        mc.getProfiler().push("projectatmosphere_hurricane_transparency");
        SimpleCloudsHurricaneRenderer.INSTANCE.prepareFrame(level, partialTick);
        renderer.copyDepthFromCloudsToTransparency();
        renderer.getCloudTransparencyTarget().bindWrite(false);
        SimpleCloudsHurricaneRenderer.INSTANCE.renderTransparency(
                renderer, stack, projMat, partialTick, cloudColor[0], cloudColor[1], cloudColor[2]
        );
        mc.getProfiler().pop();
    }
}
