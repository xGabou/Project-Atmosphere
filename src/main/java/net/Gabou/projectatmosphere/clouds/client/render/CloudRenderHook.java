package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.clouds.AtmosphereCloudPolicy;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderController;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.client.debug.CloudWireframeRenderer;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderDiagnostics;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderStateUpdater;
import net.Gabou.projectatmosphere.clouds.client.lighting.CloudLightingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Hook de rendu live des nuages Project Atmosphere.
 * Cette classe ne gère pas le debug et ne lit jamais debugSnapshot.
 */
public final class CloudRenderHook {

    private CloudRenderHook() {

    }

    /**
     * Appelle le futur renderer live pendant le rendu du niveau.
     *
     * @param event événement de rendu du niveau
     */
    public static void renderFromLevelRenderer(
            ClientLevel level,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            Matrix4f projectionMatrix,
            float partialTick,
            net.minecraft.world.phys.Vec3 cameraPosition
    ) {
        if (level == null) {
            CloudRenderStateUpdater.clearCurrentSnapshots();
            CloudLightingManager.clear();
            CloudRenderFallbackState.resetAll();
            return;
        }

        if (!AtmosphereCloudPolicy.shouldRenderPaClouds(level)) {
            CloudRenderStateUpdater.clearCurrentSnapshots();
            CloudLightingManager.clear();
            CloudRenderFallbackState.resetAll();
            return;
        }

        CloudRenderFrameContext frameContext = new CloudRenderFrameContext(
                level,
                poseStack,
                cameraPosition,
                new Matrix4f(poseStack.last().pose()),
                new Matrix4f(projectionMatrix),
                CloudRenderProfile.createDefault(),
                level.getGameTime(),
                partialTick
        );

        CloudRenderStateUpdater.updateCurrentSnapshots(
                ClientCloudRegionDataCache.getCurrentRegions(),
                frameContext.getWorldTime(),
                frameContext.getPartialTick(),
                frameContext.getCameraPosition()
        );

        List<CloudRenderSnapshot> renderableSnapshots = CloudRenderController.getRenderableLiveSnapshots();

        try {
            CloudRenderer.render(frameContext);
            CloudRenderFallbackState.recordFrameOutcome(
                    CloudRenderDiagnostics.getLastStats(),
                    renderableSnapshots.isEmpty() ? null : renderableSnapshots.get(0),
                    frameContext.getCameraPosition()
            );
        } catch (Throwable throwable) {
            CloudRenderFallbackState.recordThrowable(
                    throwable,
                    renderableSnapshots.isEmpty() ? null : renderableSnapshots.get(0),
                    frameContext.getCameraPosition(),
                    CloudRenderDiagnostics.getLastStats()
            );
        }

        CloudRenderSnapshot fallbackSnapshot = CloudRenderFallbackState.getFallbackSnapshot(frameContext.getCameraPosition());
        if (fallbackSnapshot == null) {
            return;
        }

        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        CloudWireframeRenderer.render(fallbackSnapshot, frameContext.getPoseStack(), buffer, frameContext.getCameraPosition());
        buffer.endBatch(RenderType.lines());
    }
}
