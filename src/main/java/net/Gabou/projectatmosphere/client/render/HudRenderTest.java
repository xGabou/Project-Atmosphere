package net.Gabou.projectatmosphere.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT)
public class HudRenderTest {

    @SubscribeEvent
    public static void onRenderGuiOverlayPost(RenderGuiOverlayEvent.Post event) {
        // Wait until hotbar is drawn — safe overlay moment
//        if (!event.getOverlay().id().toString().equals("minecraft:hotbar")) return;
//
//        Minecraft mc = Minecraft.getInstance();
//        if (mc.level == null || mc.player == null || mc.screen != null || mc.options.hideGui) return;
//
//        var graphics = event.getGuiGraphics();
//
//        // This is a pure 2D GUI rectangle drawn to screen — NO OpenGL or PoseStack
//        graphics.fill(50, 50, 150, 100, 0xAAFF0000); // semi-transparent red box
//        System.out.println("✅ GuiGraphics.fill drew on HUD");
    }
}
