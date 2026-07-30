package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.analytics.CloudCellAnalyticsPass;
import net.Gabou.projectatmosphere.clouds.api.CloudDensityQuery;
import net.Gabou.projectatmosphere.clouds.cell.network.CloudCellAnalyticsPacket;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudFieldCache;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.client.debug.field.CloudFieldDebugRenderHook;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderHook;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudRenderHook;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudWhiteoutFogHandler;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.ClientCloudVisualDensity;
import net.Gabou.projectatmosphere.clouds.client.render.CloudDiagnosticsOverlay;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.clouds.field.network.CloudFieldPacketDispatcher;
import net.Gabou.projectatmosphere.clouds.network.CloudRegionPacketDispatcher;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.client.ClientTickHandler;
import net.Gabou.projectatmosphere.client.fog.AtmosphereFogRenderHandler;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.auroras.AuroraCompatController;
import net.Gabou.projectatmosphere.compat.rainbows.RainbowWeatherTracker;
import net.Gabou.projectatmosphere.tools.debug.WorldSpaceDebugCubeRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;

@OnlyIn(Dist.CLIENT)
public class ClientOnlyRegistrar {
    private ClientOnlyRegistrar() {
    }

    public static void registerClient(IEventBus modEventBus) {
        boolean simpleCloudsLoaded = AtmosphereCloudServices.isSimpleCloudsLoaded();
        CloudFieldPacketDispatcher.setClientSink(ClientCloudFieldCache::setCurrentSnapshots);
        CloudFieldPacketDispatcher.setClientSupplier(ClientCloudFieldCache::getCurrentSnapshots);
        CloudFieldPacketDispatcher.setClientDeltaSink(ClientCloudFieldCache::applyDelta);
        CloudRegionPacketDispatcher.setClientSink(ClientCloudRegionDataCache::setCurrentRegions);
        CloudRegionPacketDispatcher.setClientSupplier(ClientCloudRegionDataCache::getCurrentRegions);
        NeoForge.EVENT_BUS.register(ClientTickHandler.class);
        NeoForge.EVENT_BUS.register(AtmosphereFogRenderHandler.class);
        NeoForge.EVENT_BUS.register(CloudDiagnosticsOverlay.class);
        if (!simpleCloudsLoaded) {
            // CloudFieldDebugRenderHook remains available only for explicit
            // wireframe diagnostics and is off by default.
            NeoForge.EVENT_BUS.register(CloudFieldDebugRenderHook.class);
            NeoForge.EVENT_BUS.register(CloudFieldVolumeRenderHook.class);
            // PA-native volumetric pipeline: takes visual ownership over the
            // CloudField renderer when cloudVolumetricRendererEnabled is set.
            NeoForge.EVENT_BUS.register(VolumetricCloudRenderHook.class);
            NeoForge.EVENT_BUS.register(VolumetricCloudWhiteoutFogHandler.class);
            CloudDensityQuery.setClientProvider(ClientCloudVisualDensity::densityAt);
            CloudCellAnalyticsPass.setReportSink(reports ->
                    PacketDistributor.sendToServer(new CloudCellAnalyticsPacket(reports)));
        } else {
            registerSimpleCloudsClientIntegration();
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Simple Clouds detected; Simple Clouds owns the base cloud layer.");
        }
        if (!FMLEnvironment.production) {
            NeoForge.EVENT_BUS.register(WorldSpaceDebugCubeRenderer.class);
        }

        RainbowWeatherTracker.setEnabled(CompatHandler.isRainbowsLoaded());
        AuroraCompatController.setEnabled(CompatHandler.isAurorasLoaded());
    }

    private static void registerSimpleCloudsClientIntegration() {
        String className = "net.Gabou.projectatmosphere.compat.simpleclouds.SimpleCloudsClientIntegration";
        try {
            Class<?> integration = Class.forName(className, true, ClientOnlyRegistrar.class.getClassLoader());
            integration.getMethod("register").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Simple Clouds is loaded but its PA client integration could not initialize", exception);
        }
    }
}
