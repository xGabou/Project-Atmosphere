package net.Gabou.projectatmosphere.mixin.client;

import net.Gabou.projectatmosphere.client.loading.ForecastLoadingOverlayRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void projectatmosphere$renderForecastOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ForecastLoadingOverlayRenderer.render(guiGraphics);
    }
}
