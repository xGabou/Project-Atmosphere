package net.Gabou.projectatmosphere.mixin.client;

import net.Gabou.projectatmosphere.client.loading.ForecastLoadingOverlayRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ReceivingLevelScreen.class, LevelLoadingScreen.class})
public abstract class LoadingScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void projectatmosphere$renderForecastOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ForecastLoadingOverlayRenderer.render(guiGraphics);
    }
}
