package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.client.hurricane.cache.ClientHurricaneStateCache;
import net.Gabou.projectatmosphere.clouds.analytics.CloudCellAnalyticsPass;
import net.Gabou.projectatmosphere.clouds.cell.client.ClientCloudCellCache;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudFieldCache;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.client.render.ClientCloudRenderOwnership;
import net.Gabou.projectatmosphere.clouds.client.render.depth.SceneDepthResolver;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldCompositeRenderer;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldRenderTargetManager;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderer;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowMapAccess;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.compat.simpleclouds.SimpleCloudsClientHooks;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
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
    private static volatile long worldGeneration = 1L;
    private static volatile long dimensionGeneration = 1L;
    private static volatile long ownerGeneration = 1L;
    private static volatile long resourceGeneration = 1L;

    private VolumetricCloudClientLifecycle() {
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        worldGeneration++;
        dimensionGeneration++;
        ownerGeneration++;
        resetSessionState();
        VolumetricCloudRenderHook.setRuntimeEnabled(true);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetSessionState();
    }

    /** Called by the exact Minecraft#setLevel lifecycle mixin on every world/dimension change. */
    public static void onClientLevelChanged() {
        worldGeneration++;
        dimensionGeneration++;
        resetSessionState();
    }

    /** Called by the exact GameRenderer#resize lifecycle mixin. */
    public static void onResize() {
        VolumetricCloudRenderer.invalidateHistory();
        ClientCloudVisualDensity.clear();
        CameraCloudDensityTracker.reset();
        runOnRenderThread(() -> {
            VolumetricCloudRenderTargets.onResize();
            CloudFieldRenderTargetManager.onResize();
            SceneDepthResolver.shutdown();
        });
    }

    /** Registers a client resource listener from the mod event bus. */
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void ignored, ResourceManager resourceManager, ProfilerFiller profiler) {
                onResourceReload();
            }

            @Override
            public String getName() {
                return "projectatmosphere_cloud_renderer_cleanup";
            }
        });
    }

    /** Runs after a client resource reload, before fresh cloud resources are used. */
    public static void onResourceReload() {
        resourceGeneration++;
        VolumetricCloudRenderer.invalidateBeforeNextComposite();
        ClientCloudVisualDensity.clear();
        CameraCloudDensityTracker.reset();
        runOnRenderThread(() -> {
            // Procedural noise is independent of resource-pack contents. Keep
            // it resident across F3+T; rebuilding 8 MiB of 3D noise here is
            // unnecessary and used to expose the upload to atlas unpack state.
            releaseRenderResources(false);
            ProjectAtmosphere.LOGGER.info("[VolumetricClouds] cleared render resources after resource reload");
        });
    }

    /** Releases all client GL resources when the GameRenderer closes. */
    public static void shutdownClient() {
        VolumetricCloudDebugConfig.resetDefaults();
        clearClientCaches();
        runOnRenderThread(() -> releaseRenderResources(true));
    }

    /** Clears temporal and target state when a configuration/backend switch occurs. */
    public static void onBackendChanged() {
        ownerGeneration++;
        VolumetricCloudRenderer.invalidateBeforeNextComposite();
        VolumetricCloudDebugConfig.resetDefaults();
        ClientCloudVisualDensity.clear();
        CameraCloudDensityTracker.reset();
        VolumetricCloudRenderHook.resetMaterialAdvection();
        runOnRenderThread(() -> releaseRenderResources(false));
    }

    private static void resetSessionState() {
        VolumetricCloudRenderer.invalidateBeforeNextComposite();
        VolumetricCloudDebugConfig.resetDefaults();
        clearClientCaches();
        runOnRenderThread(() -> releaseRenderResources(false));
    }

    static long worldGeneration() {
        return worldGeneration;
    }

    static long dimensionGeneration() {
        return dimensionGeneration;
    }

    static long ownerGeneration() {
        return ownerGeneration;
    }

    static long resourceGeneration() {
        return resourceGeneration;
    }

    private static void clearClientCaches() {
        VolumetricCloudRenderHook.resetMaterialAdvection();
        ClientCloudRenderOwnership.reset();
        ClientCloudCellCache.clear();
        ClientCloudFieldCache.clear();
        ClientCloudRegionDataCache.clear();
        ClientHurricaneStateCache.clear();
        if (AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            SimpleCloudsClientHooks.clearTornadoes();
        }
        ClientCloudVisualDensity.clear();
        CameraCloudDensityTracker.reset();
        CloudShadowMapAccess.clear();
    }

    private static void releaseRenderResources(boolean releaseNoiseTextures) {
        VolumetricCloudRenderer.shutdown();
        VolumetricCloudRenderTargets.shutdown();
        CloudFieldRenderTargetManager.shutdown();
        CloudFieldVolumeRenderer.shutdown();
        CloudFieldCompositeRenderer.shutdown();
        FullscreenQuad.shutdown();
        SceneDepthResolver.shutdown();
        CloudCellAnalyticsPass.shutdown();
        VolumetricCloudFrameDiagnostics.shutdownCumulusStageCapture();
        VolumetricCloudFrameDiagnostics.shutdownStabilityCapture();
        if (AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            SimpleCloudsClientHooks.releaseRenderResources();
        }
        if (releaseNoiseTextures) {
            CloudNoiseTextureManager.shutdown();
        }
        CloudShadowMapAccess.clear();
    }

    private static void runOnRenderThread(Runnable action) {
        if (RenderSystem.isOnRenderThreadOrInit()) {
            action.run();
        } else {
            RenderSystem.recordRenderCall(action::run);
        }
    }
}
