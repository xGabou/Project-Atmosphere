package net.Gabou.projectatmosphere.mixin.client;

import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudRenderHook;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderConfig;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGuiVignette {
    @Shadow
    @Final
    private static ResourceLocation VIGNETTE_LOCATION;

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$skipVignetteWhenNativeCloudsOwnSky(
            GuiGraphics guiGraphics,
            Entity cameraEntity,
            CallbackInfo ci
    ) {
        if (projectatmosphere$cloudRendererOwnsSkyOverlays()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$skipVignetteTextureOverlayWhenNativeCloudsOwnSky(
            GuiGraphics guiGraphics,
            ResourceLocation texture,
            float opacity,
            CallbackInfo ci
    ) {
        if (VIGNETTE_LOCATION.equals(texture) && projectatmosphere$cloudRendererOwnsSkyOverlays()) {
            ci.cancel();
        }
    }

    private static boolean projectatmosphere$cloudRendererOwnsSkyOverlays() {
        return VolumetricCloudRenderHook.isActive()
                || CloudFieldVolumeRenderConfig.canOwnVanillaCloudLayer();
    }
}
