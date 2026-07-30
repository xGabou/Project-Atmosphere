package net.Gabou.projectatmosphere.clouds.client.debug.field;

import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudFieldCache;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRendererInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Client render hook for CloudField debug geometry.
 */
public final class CloudFieldDebugRenderHook {
    private CloudFieldDebugRenderHook() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!CloudFieldDebugRenderConfig.shouldRender()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !ClientCloudFieldCache.hasFields()) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        CloudFieldRendererInput input = ClientCloudFieldCache.createRendererInput(
                cameraPosition,
                level.getGameTime(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false)
        );
        if (input.fields().isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x(), -cameraPosition.y(), -cameraPosition.z());
        CloudFieldDebugRenderer.render(input, poseStack, buffer);
        poseStack.popPose();

        buffer.endBatch(RenderType.lines());
    }
}
