package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.cell.client.ClientCloudCellCache;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Session lifecycle for the volumetric cloud pipeline: clears synced cell
 * state and temporal history on login/logout so a new world never reuses the
 * previous world's clouds or reprojection history. Baked noise textures are
 * kept - they are world-independent and expensive to rebuild.
 */
@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT)
public final class VolumetricCloudClientLifecycle {
    private VolumetricCloudClientLifecycle() {
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        resetSessionState();
        VolumetricCloudRenderHook.setRuntimeEnabled(true);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetSessionState();
    }

    private static void resetSessionState() {
        ClientCloudCellCache.clear();
        VolumetricCloudRenderer.invalidateHistory();
        CameraCloudDensityTracker.reset();
    }
}
