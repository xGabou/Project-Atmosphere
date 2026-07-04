package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.analytics.CloudCellAnalyticsPass;
import net.Gabou.projectatmosphere.clouds.api.CloudDensityQuery;
import net.Gabou.projectatmosphere.clouds.cell.CloudCellDensityMath;
import net.Gabou.projectatmosphere.clouds.cell.client.ClientCloudCellCache;
import net.Gabou.projectatmosphere.clouds.cell.network.CloudCellAnalyticsPacket;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudFieldCache;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.client.debug.field.CloudFieldDebugRenderHook;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderHook;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudRenderHook;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudWhiteoutFogHandler;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.clouds.field.network.CloudFieldPacketDispatcher;
import net.Gabou.projectatmosphere.clouds.network.CloudRegionPacketDispatcher;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.client.ClientTickHandler;
import net.Gabou.projectatmosphere.client.fog.AtmosphereFogRenderHandler;
import net.Gabou.projectatmosphere.client.fog.SimpleCloudsWhiteoutFogHandler;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.auroras.AuroraCompatController;
import net.Gabou.projectatmosphere.compat.rainbows.RainbowWeatherTracker;
import net.Gabou.projectatmosphere.config.AtmoConfigScreen;
import net.Gabou.projectatmosphere.tools.debug.WorldSpaceDebugCubeRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@OnlyIn(Dist.CLIENT)
public class ClientOnlyRegistrar {
    private ClientOnlyRegistrar() {
    }

    public static void registerClient(IEventBus modEventBus, FMLJavaModLoadingContext context) {
        boolean simpleCloudsLoaded = AtmosphereCloudServices.isSimpleCloudsLoaded();
        CloudFieldPacketDispatcher.setClientSink(ClientCloudFieldCache::setCurrentSnapshots);
        CloudFieldPacketDispatcher.setClientSupplier(ClientCloudFieldCache::getCurrentSnapshots);
        CloudRegionPacketDispatcher.setClientSink(ClientCloudRegionDataCache::setCurrentRegions);
        CloudRegionPacketDispatcher.setClientSupplier(ClientCloudRegionDataCache::getCurrentRegions);
        MinecraftForge.EVENT_BUS.register(ClientTickHandler.class);
        MinecraftForge.EVENT_BUS.register(AtmosphereFogRenderHandler.class);
        if (!simpleCloudsLoaded) {
            // CloudFieldVolumeRenderHook is the active experimental GLSL
            // CloudField renderer for synced CloudField snapshots. The older
            // pre-CloudField native CloudRenderHook/CloudDiagnosticsOverlay path
            // is intentionally not registered as visual ownership anymore.
            // CloudFieldDebugRenderHook remains available only for explicit
            // legacy diagnostics and is off by default.
            MinecraftForge.EVENT_BUS.register(CloudFieldDebugRenderHook.class);
            MinecraftForge.EVENT_BUS.register(CloudFieldVolumeRenderHook.class);
            // PA-native volumetric pipeline: takes visual ownership over the
            // CloudField renderer when cloudVolumetricRendererEnabled is set.
            MinecraftForge.EVENT_BUS.register(VolumetricCloudRenderHook.class);
            MinecraftForge.EVENT_BUS.register(VolumetricCloudWhiteoutFogHandler.class);
            CloudDensityQuery.setClientProvider((level, x, y, z) -> CloudCellDensityMath.densityAt(
                    ClientCloudCellCache.presentCells(
                            level.dimension().location().toString(), level.getGameTime(), 0.0F),
                    x, y, z));
            CloudCellAnalyticsPass.setReportSink(reports ->
                    NetworkHandler.CHANNEL.sendToServer(new CloudCellAnalyticsPacket(reports)));
        } else {
            MinecraftForge.EVENT_BUS.register(SimpleCloudsWhiteoutFogHandler.class);
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Simple Clouds detected; PA cloud client hooks disabled.");
        }
        if (!FMLEnvironment.production) {
            MinecraftForge.EVENT_BUS.register(WorldSpaceDebugCubeRenderer.class);
        }

        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> new AtmoConfigScreen(screen)));
        RainbowWeatherTracker.setEnabled(CompatHandler.isRainbowsLoaded());
        AuroraCompatController.setEnabled(CompatHandler.isAurorasLoaded());
    }
}
