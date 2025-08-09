package net.Gabou.projectatmosphere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT)
public class HUDOverlayRenderer {

    private static String temperatureMessage = null;
    private static long displayUntil = 0;

    public static void showTemperatureOverlay(String msg) {
        temperatureMessage = msg;
        displayUntil = System.currentTimeMillis() + 3000; 
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
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


