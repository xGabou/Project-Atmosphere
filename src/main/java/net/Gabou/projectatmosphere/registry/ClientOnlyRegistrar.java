package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderHook;
import net.Gabou.projectatmosphere.clouds.client.debug.CloudDebugRenderHook;
import net.Gabou.projectatmosphere.clouds.client.debug.CloudDebugStateInitializer;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.auroras.AuroraCompatController;
import net.Gabou.projectatmosphere.compat.rainbows.RainbowWeatherTracker;
import net.Gabou.projectatmosphere.config.AtmoConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;


@OnlyIn(Dist.CLIENT)
public class ClientOnlyRegistrar {
    // ---------------------------------------------------------------------
    // Client registration
    // ---------------------------------------------------------------------
    public static void registerClient(IEventBus modEventBus, FMLJavaModLoadingContext context) {
        registerSimpleCloudsClientCompat();
        MinecraftForge.EVENT_BUS.register(CloudDebugRenderHook.class);
        MinecraftForge.EVENT_BUS.register(CloudRenderHook.class);
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> new AtmoConfigScreen(screen)));
        RainbowWeatherTracker.setEnabled(CompatHandler.isRainbowsLoaded());
        AuroraCompatController.setEnabled(CompatHandler.isAurorasLoaded());

        if (!FMLEnvironment.production) {
            CloudDebugStateInitializer.initialize();
        }
    }

    private static void registerSimpleCloudsClientCompat() {
        if (!AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return;
        }
        registerForgeSubscriber("net.Gabou.projectatmosphere.client.ClientTickHandler");
        registerForgeSubscriber("net.Gabou.projectatmosphere.client.fog.SimpleCloudsWhiteoutFogHandler");
        registerForgeSubscriber("net.Gabou.projectatmosphere.client.render.pipeline.SimpleCloudsDhPipelineSelector");
        registerForgeSubscriber("net.Gabou.projectatmosphere.command.CloudDumpCommand");
        registerForgeSubscriber("net.Gabou.projectatmosphere.tools.debug.TornadoLateRenderDiagnostics");
    }

    private static void registerForgeSubscriber(String className) {
        try {
            MinecraftForge.EVENT_BUS.register(Class.forName(className));
        } catch (ClassNotFoundException | LinkageError error) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Simple Clouds client compat ignorée: {}", className, error);
        }
    }
}
