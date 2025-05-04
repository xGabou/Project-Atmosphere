package net.Gabou.projectatmosphere;

import net.Gabou.projectatmosphere.client.renderer.CloudRenderer;
import net.Gabou.projectatmosphere.registry.EntityRegistrar;
import net.Gabou.projectatmosphere.command.SpawnCloudCommand;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.GeckoLib;
import net.Gabou.projectatmosphere.temperature.Temperature;

@Mod(ProjectAtmosphere.MODID)
@EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class ProjectAtmosphere {
    public static final String MODID = "projectatmosphere";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public ProjectAtmosphere() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        GeckoLib.initialize();
        initModules(modEventBus);
        EntityRegistrar.registerEntities(modEventBus);
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
    }

    private void initModules(IEventBus modEventBus) {
        isSereneLoaded();
        initTemperatureModule(modEventBus);
        initPressionModule(modEventBus);
        initHumidityModule(modEventBus);
        initStormModule(modEventBus);
        sendInfo();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AsyncAtmosphereService.shutdown();
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Setting up Project Atmosphere (Common)");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Setting up Project Atmosphere (Client)");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SpawnCloudCommand.register(event.getDispatcher());
    }
    @Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID,bus = Mod.EventBusSubscriber.Bus.MOD,value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(final EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityRegistrar.CLOUD_ENTITY.get(), CloudRenderer::new);

        }
    }
    private void initTemperatureModule(IEventBus modBus) {
        // Only run if Serene Seasons is installed
        Temperature.init(modBus);
    }

    private static void sendInfo() {
        LOGGER.info("All modules subsystems have been initialized (Serene Seasons detected).");
    }


    private void initPressionModule(IEventBus modBus) {
        // Only run if Serene Seasons is installed
        Pression.init();
    }
    private void initHumidityModule(IEventBus modBus) {
        // Only run if Serene Seasons is installed
        Humidity.init();
    }
    private void initStormModule(IEventBus modBus) {
        // Only run if Serene Seasons is installed
        Storm.init();
    }
    private static void isSereneLoaded() {
        if (!ModList.get().isLoaded("sereneseasons")) {
            LOGGER.info("Serene Seasons is not found—skipping all modules subsystems.");
        }
    }
}
