package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.clouds.AtmosphereCloudPolicy;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderController;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderStateUpdater;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.clouds.client.lighting.CloudLightingManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Live Project Atmosphere cloud render hook.
 */
public final class CloudRenderHook {

    private CloudRenderHook() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        // The weather-map volumetric pipeline owns PA cloud visuals when it
        // is configured. This older per-region raymarch previously ran in
        // parallel and produced the persistent fullscreen black oval even
        // after the CloudField render switches were turned off.
        if (nativeVolumetricPipelineConfigured()) {
            return;
        }

        renderFromRenderStage(
                event.getLevelRenderer(),
                event.getPoseStack(),
                event.getProjectionMatrix(),
                event.getPartialTick(),
                event.getCamera(),
                event.getCamera().getPosition()
        );
    }

    private static boolean nativeVolumetricPipelineConfigured() {
        try {
            return AtmoCommonConfig.CLOUD_VOLUMETRIC_RENDERER_ENABLED.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    public static void renderFromRenderStage(
            LevelRenderer levelRenderer,
            PoseStack poseStack,
            Matrix4f projectionMatrix,
            float partialTick,
            Camera camera,
            Vec3 cameraPosition
    ) {
        if (levelRenderer == null || camera == null) {
            return;
        }

        renderVolumetricClouds(
                Minecraft.getInstance().level,
                poseStack,
                projectionMatrix,
                partialTick,
                cameraPosition
        );
    }

    public static void renderFromLevelRenderer(
            ClientLevel level,
            PoseStack poseStack,
            Matrix4f projectionMatrix,
            float partialTick,
            Vec3 cameraPosition
    ) {
        // Bypassed intentionally while validating the post-weather render path.
    }

    private static void renderVolumetricClouds(
            ClientLevel level,
            PoseStack poseStack,
            Matrix4f projectionMatrix,
            float partialTick,
            Vec3 cameraPosition
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
    }
}
