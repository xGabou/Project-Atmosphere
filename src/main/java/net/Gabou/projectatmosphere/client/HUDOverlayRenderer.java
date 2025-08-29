package net.Gabou.projectatmosphere.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public class HUDOverlayRenderer {

    private static String temperatureMessage = null;
    private static long displayUntil = 0;

    public static void showTemperatureOverlay(String msg) {
        temperatureMessage = msg;
        displayUntil = System.currentTimeMillis() + 3000; // show for 3s
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(HUDOverlayRenderer::onRenderOverlay);
    }

    private static void onRenderOverlay(RenderGuiEvent.Post event) {
        if (temperatureMessage == null || System.currentTimeMillis() > displayUntil) {
            temperatureMessage = null;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int x = (screenWidth - font.width(temperatureMessage)) / 2;
        int y = screenHeight - 60;

        guiGraphics.drawString(font, temperatureMessage, x, y, 0xFFFFFF, true);
    }
}
