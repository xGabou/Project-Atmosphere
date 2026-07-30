package net.Gabou.projectatmosphere.client;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT)
public class HUDOverlayRenderer {

    private static String temperatureMessage = null;
    private static long displayUntil = 0;

    public static void showTemperatureOverlay(String msg) {
        temperatureMessage = msg;
        displayUntil = System.currentTimeMillis() + 3000;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;

        if (temperatureMessage == null || System.currentTimeMillis() > displayUntil) {
            temperatureMessage = null;
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int x = (screenWidth - font.width(temperatureMessage)) / 2;
        int y = screenHeight - 60;

        guiGraphics.drawString(font, temperatureMessage, x, y, 0xFFFFFF, true);
    }

}
