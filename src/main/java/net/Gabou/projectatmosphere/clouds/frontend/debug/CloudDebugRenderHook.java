package net.Gabou.projectatmosphere.clouds.frontend.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.clouds.frontend.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.frontend.CloudRenderStateHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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

        if (shouldRenderDebugCloud) {
            CloudRenderSnapshot snapshot = CloudRenderStateHolder.getInstance().getDebugSnapshot();
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
            Vec3 cameraPosition = event.getCamera().getPosition();

            CloudWireframeRenderer.render(snapshot, poseStack, buffer, cameraPosition);
            buffer.endBatch(RenderType.lines());
        }
    }
}