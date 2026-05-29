package net.Gabou.projectatmosphere.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT)
public final class TornadoLateRenderDiagnostics {
    private TornadoLateRenderDiagnostics() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        if (!SimpleCloudsMod.dhLoaded()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        SimpleCloudsRenderer renderer = SimpleCloudsRenderer.getOptionalInstance().orElse(null);
        if (renderer == null) {
            return;
        }

        SimpleCloudsTornadoRenderer.INSTANCE.prepareFrame(level, event.getPartialTick());
        if (!SimpleCloudsTornadoRenderer.INSTANCE.hasVisibleTornado(null)) {
            return;
        }

        Camera camera = event.getCamera();
        PoseStack stack = new PoseStack();
        stack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        stack.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
        renderer.translateClouds(stack, camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);

        float[] cloudColor = renderer.getCloudColor(event.getPartialTick());
        mc.getMainRenderTarget().bindWrite(false);
        int mainDepthTexture = mc.getMainRenderTarget().getDepthTextureId();
        boolean detachedMainDepth = mainDepthTexture > 0;
        if (detachedMainDepth) {
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, 0, 0);
        }

        try {
            SimpleCloudsTornadoRenderer.INSTANCE.renderOpaqueToTarget(
                    renderer,
                    mc.getMainRenderTarget(),
                    stack,
                    event.getProjectionMatrix(),
                    event.getPartialTick(),
                    cloudColor[0],
                    cloudColor[1],
                    cloudColor[2],
                    null,
                    mainDepthTexture,
                    -1,
                    true,
                    true,
                    true
            );
        } finally {
            if (detachedMainDepth) {
                GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                        GL11.GL_TEXTURE_2D, mainDepthTexture, 0);
            }
        }
    }
}
