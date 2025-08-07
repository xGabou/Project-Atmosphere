package net.Gabou.projectatmosphere.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
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
        if (Minecraft.getInstance().level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        List<TornadoInstance> tornadoes = TornadoManager.getActiveTornadoes();
        if (tornadoes.isEmpty()) return;

        ShaderInstance shader = MyShaders.TORNADO;
        if (shader == null) return;

        RenderSystem.setShader(() -> shader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        for (TornadoInstance tornado : tornadoes) {
            poseStack.pushPose();
            TornadoRenderHandler.renderTornado(
                    poseStack,
                    tornado.position.x,
                    Minecraft.getInstance().level.getSeaLevel(),
                    tornado.position.z,
                    tornado.getTwist()
            );
            poseStack.popPose();
        }

        poseStack.popPose();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }



}
