package net.Gabou.projectatmosphere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientRenderHook {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        ProjectAtmosphere.LOGGER.debug("ClientRenderHook:onRenderLevel stage={}" +
                ", activeTornadoes={}",
                event.getStage(),
                TornadoManager.getActiveTornadoes().size());

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();

        TornadoRenderHandler.renderTornadoes(poseStack, camera);
    }
}


