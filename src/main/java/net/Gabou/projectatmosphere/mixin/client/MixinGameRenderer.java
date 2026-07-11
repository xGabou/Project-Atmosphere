package net.Gabou.projectatmosphere.mixin.client;

import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudClientLifecycle;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Inject(method = "resize", at = @At("TAIL"))
    private void projectatmosphere$resizeCloudRenderer(int width, int height, CallbackInfo ci) {
        VolumetricCloudClientLifecycle.onResize();
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void projectatmosphere$shutdownCloudRenderer(CallbackInfo ci) {
        VolumetricCloudClientLifecycle.shutdownClient();
    }
}
