package net.Gabou.projectatmosphere.mixin.client;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Expose les helpers protégés de {@link Screen} pour les écrans clients du mod.
 */
@Mixin(Screen.class)
public interface ScreenInvoker {

    /**
     * Ajoute un widget rendu et narrable au screen cible.
     *
     * @param widget widget à enregistrer
     * @return widget ajouté
     */
    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T projectatmosphere$addRenderableWidget(T widget);
}
