package net.Gabou.projectatmosphere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(
        modid = ProjectAtmosphere.MODID,                       // ← your mod ID
        bus = Mod.EventBusSubscriber.Bus.FORGE,   // ← must be FORGE bus
        value = Dist.CLIENT
)
public class ClientRenderHook {


    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (Minecraft.getInstance().level == null) return;

        ClientLevel level = Minecraft.getInstance().level;
        PoseStack poseStack = event.getPoseStack();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        List<TornadoInstance> tornadoes = TornadoManager.getActiveTornadoes();
        if (tornadoes.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);


        for (TornadoInstance tornado : tornadoes) {
            try{
                TornadoRenderHandler.renderTornado(
                        poseStack,
                        tornado.position.x,
                        Minecraft.getInstance().level.getSeaLevel(),
                        tornado.position.z,
                        tornado.getTwist(),
                        level,camera,Minecraft.getInstance()

                );
            }
            catch (Exception e){
                ProjectAtmosphere.LOGGER.error("Error rendering tornado at position: " + tornado.position, e);
            }

        }

        poseStack.popPose();
    }



}
