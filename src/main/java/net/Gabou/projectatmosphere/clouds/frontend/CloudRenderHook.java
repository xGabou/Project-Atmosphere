package net.Gabou.projectatmosphere.clouds.frontend;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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

        if (minecraft.level == null) {
            CloudRenderStateUpdater.clearCurrentSnapshots();
            return;
        }

        CloudRenderStateUpdater.updateCurrentSnapshots(
                ClientCloudRegionDataCache.getCurrentRegions(),
                minecraft.level.getGameTime(),
                event.getPartialTick(),
                event.getCamera().getPosition()
        );

        CloudRenderer.render();
    }
}