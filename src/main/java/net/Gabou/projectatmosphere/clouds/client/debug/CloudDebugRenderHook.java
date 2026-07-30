package net.Gabou.projectatmosphere.clouds.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderStateHolder;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderAabb;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderDebugMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;

public final class CloudDebugRenderHook {
    private CloudDebugRenderHook() {}

    private static boolean shouldRenderDebugCloud = false;

    public static void changeCloudDebugRenderHook() {
        CloudDebugRenderHook.shouldRenderDebugCloud = !CloudDebugRenderHook.shouldRenderDebugCloud;
    }

    public static boolean isCloudDebugRenderEnabled() {
        return shouldRenderDebugCloud;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        if (!shouldRenderDebugCloud && !CloudRenderDebugMode.current().isActive()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPosition = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x(), -cameraPosition.y(), -cameraPosition.z());
        if (shouldRenderDebugCloud) {
            CloudWireframeRenderer.render(CloudRenderStateHolder.getInstance().getDebugSnapshot(), poseStack, buffer);
        }
        if (CloudRenderDebugMode.current().isActive()) {
            CloudWireframeRenderer.render(CloudRenderAabb.getDebugWireframeSnapshot(), poseStack, buffer);
        }
        poseStack.popPose();

        buffer.endBatch(RenderType.lines());
    }
}
