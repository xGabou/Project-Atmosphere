package net.Gabou.projectatmosphere.clouds.client.render.field;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.Gabou.projectatmosphere.client.render.shader.CloudShaders;
import net.Gabou.projectatmosphere.client.render.shader.CloudFieldVolumeShaders;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudFieldCache;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRaymarchRenderer;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderProfile;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderTargetManager;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRendererInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client render hook for synced CloudField volume rendering.
 */
public final class CloudFieldVolumeRenderHook {
    private CloudFieldVolumeRenderHook() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
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

        boolean pushed = false;
        PoseStack poseStack = event.getPoseStack();
        try {
            Vec3 cameraPosition = event.getCamera().getPosition();
            RenderTarget mainTarget = minecraft.getMainRenderTarget();
            RenderTarget outputTarget = mainTarget;
            boolean downscaleApplied = false;
            int sceneDepthTextureId = mainTarget == null ? 0 : mainTarget.getDepthTextureId();
            float resolutionScale = CloudFieldVolumeRenderConfig.resolutionScale();
            boolean debugMode = CloudFieldVolumeRenderConfig.mode() != CloudFieldVolumeRenderMode.NORMAL;
            if (!debugMode && mainTarget != null && resolutionScale < 0.999F && CloudShaders.getCompositeShader() != null) {
                CloudRenderTargetManager.prepareTargets(CloudRenderProfile.createDefault());
                RenderTarget cloudTarget = CloudRenderTargetManager.getCloudColorTarget();
                if (cloudTarget != null) {
                    outputTarget = cloudTarget;
                    downscaleApplied = true;
                    cloudTarget.bindWrite(true);
                    cloudTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                    cloudTarget.clear(Minecraft.ON_OSX);
                }
            } else if (mainTarget != null) {
                mainTarget.bindWrite(false);
            }

            CloudFieldRendererInput input = ClientCloudFieldCache.createRendererInput(
                    cameraPosition,
                    level.getGameTime(),
                    event.getPartialTick()
            );
            poseStack.pushPose();
            pushed = true;
            poseStack.translate(-cameraPosition.x(), -cameraPosition.y(), -cameraPosition.z());
            CloudFieldVolumeRenderStats stats = CloudFieldVolumeRenderer.render(
                    input,
                    poseStack,
                    event.getProjectionMatrix(),
                    level.dimension().location().toString(),
                    cachedSnapshots,
                    event.getFrustum(),
                    outputTarget,
                    sceneDepthTextureId,
                    downscaleApplied
            );
            if (downscaleApplied && mainTarget != null && outputTarget != null && stats.renderedFields() > 0) {
                boolean composited = CloudRaymarchRenderer.compositeTarget(
                        outputTarget,
                        outputTarget,
                        mainTarget,
                        sceneDepthTextureId,
                        CloudFieldVolumeRenderConfig.quality().getCompositeBlurRadius(),
                        CloudFieldVolumeRenderConfig.quality().getCompositeBlurStrength()
                );
                if (!composited) {
                    mainTarget.bindWrite(false);
                }
            } else if (mainTarget != null) {
                mainTarget.bindWrite(false);
            }
            CloudFieldVolumeRenderConfig.recordStats(stats);
        } catch (Throwable throwable) {
            CloudFieldVolumeRenderer.restoreRenderState();
            CloudFieldVolumeRenderConfig.recordRenderException(throwable, cachedSnapshots);
        } finally {
            if (pushed) {
                poseStack.popPose();
            }
        }
    }
}
