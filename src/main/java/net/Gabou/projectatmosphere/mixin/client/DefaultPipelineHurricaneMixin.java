package net.Gabou.projectatmosphere.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.DefaultPipeline;
import net.Gabou.projectatmosphere.client.hurricane.cache.ClientHurricaneStateCache;
import net.Gabou.projectatmosphere.client.render.SimpleCloudsHurricaneRenderer;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderStateGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultPipeline.class, remap = false)
public abstract class DefaultPipelineHurricaneMixin {
    @Inject(
            method = "afterSky",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/nonamecrackers2/simpleclouds/client/renderer/SimpleCloudsRenderer;copyDepthFromCloudsToMain()V"
            ),
            require = 0
    )
    private void projectatmosphere$renderHurricaneOpaque(Minecraft mc, SimpleCloudsRenderer renderer,
                                                         Matrix4f modelViewMat, Matrix4f projMat, float partialTick,
                                                         double camX, double camY, double camZ, Frustum frustum,
                                                         CallbackInfo ci) {
        ClientLevel level = mc.level;
        if (level == null || !ClientHurricaneStateCache.hasHurricanes()) {
            return;
        }

        float[] cloudColor = renderer.getCloudColor(partialTick);
        mc.getProfiler().push("projectatmosphere_hurricane_opaque");
        try (CloudRenderStateGuard.State ignored = CloudRenderStateGuard.capture()) {
            PoseStack stack = projectatmosphere$poseStack(modelViewMat);
            SimpleCloudsHurricaneRenderer.INSTANCE.prepareFrame(level, partialTick);
            SimpleCloudsHurricaneRenderer.INSTANCE.renderOpaque(
                    renderer, stack, projMat, partialTick, cloudColor[0], cloudColor[1], cloudColor[2]
            );
        } finally {
            mc.getProfiler().pop();
        }
    }

    @Inject(
            method = "afterSky",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/nonamecrackers2/simpleclouds/client/renderer/SimpleCloudsRenderer;doFinalCompositePass(Lorg/joml/Matrix4f;FLorg/joml/Matrix4f;)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void projectatmosphere$renderHurricaneTransparency(Minecraft mc, SimpleCloudsRenderer renderer,
                                                               Matrix4f modelViewMat, Matrix4f projMat, float partialTick,
                                                               double camX, double camY, double camZ, Frustum frustum,
                                                               CallbackInfo ci) {
        ClientLevel level = mc.level;
        if (level == null || !ClientHurricaneStateCache.hasHurricanes()) {
            return;
        }

        float[] cloudColor = renderer.getCloudColor(partialTick);
        mc.getProfiler().push("projectatmosphere_hurricane_transparency");
        try (CloudRenderStateGuard.State ignored = CloudRenderStateGuard.capture()) {
            PoseStack stack = projectatmosphere$poseStack(modelViewMat);
            SimpleCloudsHurricaneRenderer.INSTANCE.prepareFrame(level, partialTick);
            renderer.copyDepthFromCloudsToTransparency();
            renderer.getCloudTransparencyTarget().bindWrite(false);
            SimpleCloudsHurricaneRenderer.INSTANCE.renderTransparency(
                    renderer, stack, projMat, partialTick, cloudColor[0], cloudColor[1], cloudColor[2]
            );
        } finally {
            mc.getProfiler().pop();
        }
    }

    private static PoseStack projectatmosphere$poseStack(Matrix4f modelViewMat) {
        PoseStack stack = new PoseStack();
        stack.last().pose().set(modelViewMat);
        return stack;
    }
}
