package net.Gabou.projectatmosphere.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

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

        PoseStack poseStack = event.getPoseStack();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        // Get active tornadoes from your manager
        List<TornadoInstance> tornadoes = TornadoManager.getActiveTornadoes(); // You must implement this if not already

        if (tornadoes.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z); // world-relative positioning

        for (TornadoInstance tornado : tornadoes) {
            TornadoRenderHandler.renderTornado(poseStack, tornado.position.x, tornado.position.y, tornado.position.z);
        }

        poseStack.popPose();
    }

}
