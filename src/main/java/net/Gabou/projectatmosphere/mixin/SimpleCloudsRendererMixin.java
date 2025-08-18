package net.Gabou.projectatmosphere.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import net.Gabou.projectatmosphere.render.HurricaneMeshRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimpleCloudsRenderer.class)
public abstract class SimpleCloudsRendererMixin {

    @Inject(
        method = "renderBeforeWeather(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FDDD)V",
        at = @At("TAIL"),
        remap = false
    )
    private void pa$afterBeforeWeather(PoseStack stack, Matrix4f projMat, float partialTick,
                                       double camX, double camY, double camZ, CallbackInfo ci) {
        SimpleCloudsRenderer self = (SimpleCloudsRenderer)(Object)this;

        // Enter Simple Clouds' cloud space (translate + scale + cloud height)
        stack.pushPose();
        self.translateClouds(stack, camX, camY, camZ);

        // Draw hurricane ring in cloud space (y≈0 is cloud plane)
        HurricaneMeshRenderer.renderCloudSpace(self, stack, projMat, partialTick, camX, camZ);

        stack.popPose();
    }
}

