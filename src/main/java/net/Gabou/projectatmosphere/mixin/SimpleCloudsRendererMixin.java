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
            method = "renderBeforeWeather(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V",
            at = @At("TAIL"),
            remap = false
    )
    private void pa$afterBeforeWeather(Matrix4f projMat, Matrix4f camMat, float partialTick,
                                       double camX, double camY, double camZ, CallbackInfo ci) {
        SimpleCloudsRenderer self = (SimpleCloudsRenderer)(Object)this;

        // In 1.21.x, PoseStack is no longer passed in, so you’ll need to
        // allocate your own for local rendering if HurricaneMeshRenderer expects one
        PoseStack pose = new PoseStack();

        // Draw hurricane ring in cloud space
        HurricaneMeshRenderer.renderCloudSpace(self, pose, projMat, partialTick, camX, camZ);
    }
}


