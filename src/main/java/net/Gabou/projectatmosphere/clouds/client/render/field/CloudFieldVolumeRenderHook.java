package net.Gabou.projectatmosphere.clouds.client.render.field;

import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.client.render.shader.CloudFieldVolumeShaders;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudFieldCache;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRendererInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client render hook for the first GLSL CloudField volume prototype.
 */
public final class CloudFieldVolumeRenderHook {
    private CloudFieldVolumeRenderHook() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        int cachedSnapshots = ClientCloudFieldCache.getCurrentSnapshots().size();
        if (!CloudFieldVolumeRenderConfig.isEnabled()) {
            CloudFieldVolumeRenderConfig.recordStats(CloudFieldVolumeRenderStats.idle(
                    false,
                    CloudFieldVolumeShaders.isReady(),
                    CloudFieldVolumeRenderConfig.mode(),
                    CloudFieldVolumeRenderConfig.filter(),
                    "disabled",
                    cachedSnapshots
            ));
            return;
        }
        if (level == null) {
            CloudFieldVolumeRenderConfig.recordStats(CloudFieldVolumeRenderStats.idle(
                    true,
                    CloudFieldVolumeShaders.isReady(),
                    CloudFieldVolumeRenderConfig.mode(),
                    CloudFieldVolumeRenderConfig.filter(),
                    "no_client_level",
                    cachedSnapshots
            ));
            return;
        }
        if (cachedSnapshots <= 0) {
            CloudFieldVolumeRenderConfig.recordStats(CloudFieldVolumeRenderStats.idle(
                    true,
                    CloudFieldVolumeShaders.isReady(),
                    CloudFieldVolumeRenderConfig.mode(),
                    CloudFieldVolumeRenderConfig.filter(),
                    "no_snapshots",
                    0
            ));
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        CloudFieldRendererInput input = ClientCloudFieldCache.createRendererInput(
                cameraPosition,
                level.getGameTime(),
                event.getPartialTick()
        );

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x(), -cameraPosition.y(), -cameraPosition.z());
        CloudFieldVolumeRenderStats stats = CloudFieldVolumeRenderer.render(
                input,
                poseStack,
                event.getProjectionMatrix(),
                level.dimension().location().toString(),
                cachedSnapshots
        );
        poseStack.popPose();

        CloudFieldVolumeRenderConfig.recordStats(stats);
    }
}
