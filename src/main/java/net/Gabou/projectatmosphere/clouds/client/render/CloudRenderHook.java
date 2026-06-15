package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.clouds.AtmosphereCloudPolicy;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderStateUpdater;
import net.Gabou.projectatmosphere.clouds.client.lighting.CloudLightingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

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
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;

        if (level == null) {
            CloudRenderStateUpdater.clearCurrentSnapshots();
            CloudLightingManager.clear();
            return;
        }

        if (!AtmosphereCloudPolicy.shouldRenderPaClouds(level)) {
            CloudRenderStateUpdater.clearCurrentSnapshots();
            CloudLightingManager.clear();
            return;
        }

        CloudRenderFrameContext frameContext = new CloudRenderFrameContext(
                level,
                event.getPoseStack(),
                event.getCamera().getPosition(),
                new Matrix4f(event.getPoseStack().last().pose()),
                new Matrix4f(event.getProjectionMatrix()),
                CloudRenderProfile.createDefault(),
                level.getDayTime(),
                event.getPartialTick()
        );

        CloudRenderStateUpdater.updateCurrentSnapshots(
                ClientCloudRegionDataCache.getCurrentRegions(),
                frameContext.getWorldTime(),
                frameContext.getPartialTick(),
                frameContext.getCameraPosition()
        );

        CloudRenderer.render(frameContext);
    }
}
