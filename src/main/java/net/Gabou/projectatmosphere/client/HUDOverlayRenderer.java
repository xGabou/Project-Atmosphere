package net.Gabou.projectatmosphere.client;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderFallbackState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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
        if (!event.getOverlay().id().toString().equals("minecraft:hotbar")) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;

        renderCloudFallback(guiGraphics, font, mc);

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

    private static void renderCloudFallback(GuiGraphics guiGraphics, Font font, Minecraft mc) {
        CloudRenderFallbackState.FailureStatus status = CloudRenderFallbackState.getStatus();
        if (!status.active()) {
            return;
        }

        String title = "PA cloud fallback: " + status.title();
        String detail = status.detail();
        String counts = "source " + status.sourceSnapshots()
                + " / renderable " + status.renderableSnapshots()
                + " / rendered " + status.renderedSnapshots();

        int titleWidth = font.width(title);
        int detailWidth = font.width(detail);
        int countsWidth = font.width(counts);
        int contentWidth = Math.max(titleWidth, Math.max(detailWidth, countsWidth));
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int x = Math.max(8, (screenWidth - contentWidth) / 2 - 8);
        int y = 10;
        int right = Math.min(screenWidth - 8, x + contentWidth + 16);
        int bottom = y + 28;

        guiGraphics.fill(x, y, right, bottom, 0xD0401010);
        guiGraphics.fill(x, y, right, y + 1, 0xFFFF4040);
        guiGraphics.drawString(font, Component.literal(title), x + 8, y + 5, 0xFFFF8080, false);
        guiGraphics.drawString(font, Component.literal(detail), x + 8, y + 15, 0xFFFFD6D6, false);
        guiGraphics.drawString(font, Component.literal(counts), x + 8, y + 25, 0xFFFFD6D6, false);
    }
}


