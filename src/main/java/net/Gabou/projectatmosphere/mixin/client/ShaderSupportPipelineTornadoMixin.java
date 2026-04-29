package net.Gabou.projectatmosphere.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.ShaderSupportPipeline;
import net.Gabou.projectatmosphere.client.render.SimpleCloudsTornadoRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShaderSupportPipeline.class, remap = false)
public abstract class ShaderSupportPipelineTornadoMixin {
    @Inject(
            method = "afterLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/nonamecrackers2/simpleclouds/client/renderer/SimpleCloudsRenderer;getCloudTransparencyTarget()Ldev/nonamecrackers2/simpleclouds/client/framebuffer/WeightedBlendingTarget;"
            )
    )
    private void projectatmosphere$renderTornadoOpaque(Minecraft mc, SimpleCloudsRenderer renderer,
                                                       PoseStack stack, Matrix4f projMat, float partialTick,
                                                       double camX, double camY, double camZ, Frustum frustum,
                                                       CallbackInfo ci) {
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float[] cloudColor = renderer.getCloudColor(partialTick);
        mc.getProfiler().push("projectatmosphere_tornado_opaque");
        SimpleCloudsTornadoRenderer.INSTANCE.prepareFrame(level, partialTick);
        renderer.copyDepthFromCloudsToTransparency();
        renderer.getCloudTarget().bindWrite(false);
        SimpleCloudsTornadoRenderer.INSTANCE.renderOpaque(
                renderer, stack, projMat, partialTick, cloudColor[0], cloudColor[1], cloudColor[2],
                renderer.getCloudTransparencyTarget().getDepthTextureId(),
                -1,
                true
        );
        mc.getProfiler().pop();
    }

    @Inject(
            method = "afterLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void projectatmosphere$renderTornadoTransparency(Minecraft mc, SimpleCloudsRenderer renderer,
                                                             PoseStack stack, Matrix4f projMat, float partialTick,
                                                             double camX, double camY, double camZ, Frustum frustum,
                                                             CallbackInfo ci) {
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float[] cloudColor = renderer.getCloudColor(partialTick);
        mc.getProfiler().push("projectatmosphere_tornado_transparency");
        SimpleCloudsTornadoRenderer.INSTANCE.prepareFrame(level, partialTick);
        renderer.copyDepthFromCloudsToTransparency();
        renderer.getCloudTransparencyTarget().bindWrite(false);
        SimpleCloudsTornadoRenderer.INSTANCE.renderTransparency(
                renderer, stack, projMat, partialTick, cloudColor[0], cloudColor[1], cloudColor[2]
        );
        mc.getProfiler().pop();
    }
}
