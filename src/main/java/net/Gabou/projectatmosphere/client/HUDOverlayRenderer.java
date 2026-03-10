package net.Gabou.projectatmosphere.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public class HUDOverlayRenderer {

    public static void showTemperatureOverlay(String msg) {
        OverlayMessageState.show(msg, 3000L);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(HUDOverlayRenderer::onRenderOverlay);
    }

    private static void onRenderOverlay(RenderGuiEvent.Post event) {
        String message = OverlayMessageState.getMessage();
        if (message == null || System.currentTimeMillis() > OverlayMessageState.getDisplayUntilMillis()) {
            OverlayMessageState.clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int x = (screenWidth - font.width(message)) / 2;
        int y = screenHeight - 60;

        guiGraphics.drawString(font, message, x, y, 0xFFFFFF, true);
    }
}
