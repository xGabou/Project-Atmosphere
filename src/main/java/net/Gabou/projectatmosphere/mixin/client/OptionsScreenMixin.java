package net.Gabou.projectatmosphere.mixin.client;

import net.Gabou.projectatmosphere.client.screen.ProjectAtmosphereQuickOptionsScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ajoute un point d'entrée compact pour l'éditeur de shader dans le menu Options.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {

    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    /**
     * Ajoute un petit bouton PA en haut à droite du menu Options.
     *
     * @param ci contexte d'injection Mixin
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void projectatmosphere$addShaderButton(CallbackInfo ci) {
        OptionsScreen screen = (OptionsScreen) (Object) this;
        int windowWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();

        this.addRenderableWidget(
                Button.builder(Component.literal("PA").withStyle(ChatFormatting.AQUA), button ->
                                Minecraft.getInstance().setScreen(new ProjectAtmosphereQuickOptionsScreen(screen)))
                        .bounds(windowWidth - 28, 6, 22, 20)
                        .build()
        );
    }
}
