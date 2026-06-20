package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.client.debug.CloudDebugRenderHook;
import net.Gabou.projectatmosphere.clouds.client.debug.CloudDebugStateInitializer;
import net.Gabou.projectatmosphere.clouds.client.render.CloudDiagnosticsOverlay;
import net.Gabou.projectatmosphere.clouds.client.render.FallbackDarkeningPass;
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
        CloudRegionPacketDispatcher.setClientSink(ClientCloudRegionDataCache::setCurrentRegions);
        CloudRegionPacketDispatcher.setClientSupplier(ClientCloudRegionDataCache::getCurrentRegions);
        MinecraftForge.EVENT_BUS.register(ClientTickHandler.class);
        MinecraftForge.EVENT_BUS.register(AtmosphereFogRenderHandler.class);
        if (!simpleCloudsLoaded) {
            MinecraftForge.EVENT_BUS.register(CloudDebugRenderHook.class);
            MinecraftForge.EVENT_BUS.register(CloudDiagnosticsOverlay.class);
            MinecraftForge.EVENT_BUS.register(FallbackDarkeningPass.class);
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

        if (!simpleCloudsLoaded && !FMLEnvironment.production) {
            CloudDebugStateInitializer.initialize();
        }
    }
}
