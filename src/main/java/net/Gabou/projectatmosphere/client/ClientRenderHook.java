package net.Gabou.projectatmosphere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;


public class ClientRenderHook {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientRenderHook::onRender);
    }


    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (Minecraft.getInstance().level == null) return;

        ClientLevel level = Minecraft.getInstance().level;
        List<TornadoInstance> snapshot = new ArrayList<>(TornadoManager.getActiveTornadoes());
        if (snapshot.isEmpty()) return;
        PoseStack poseStack = event.getPoseStack();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();



        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);


        for (TornadoInstance tornado : snapshot) {
            try {
                if (tornado.position.distanceToSqr(camPos) > 500 * 500) {
                    continue;
                }
                TornadoRenderHandler.renderTornado(
                        poseStack,
                        tornado.position.x,
                        Minecraft.getInstance().level.getSeaLevel(),
                        tornado.position.z,
                        tornado.getTwist(),
                        level, camera, Minecraft.getInstance(), tornado

                );

            } catch (Exception e) {
                ProjectAtmosphere.LOGGER.error("Error rendering tornado at position: " + tornado.position, e);
            }

        }

        poseStack.popPose();
    }



}
