package net.Gabou.projectatmosphere.clouds;

import com.mojang.blaze3d.vertex.PoseStack;
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

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        if (!shouldRenderDebugCloud) {
            return;
        }

        CloudRenderSnapshot snapshot = CloudRenderStateHolder.getInstance().getRenderableSnapshot();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        CloudDebugRenderer.render(snapshot, poseStack, buffer, cameraPos);
        buffer.endBatch(RenderType.lines());
    }
}
