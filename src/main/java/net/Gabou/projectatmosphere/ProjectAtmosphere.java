package net.Gabou.projectatmosphere;

import net.Gabou.projectatmosphere.client.renderer.CloudRenderer;
import net.Gabou.projectatmosphere.command.DebugAtmoCommand;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.modules.storm.StormModule;
import net.Gabou.projectatmosphere.registry.EntityRegistrar;
import net.Gabou.projectatmosphere.command.SpawnCloudCommand;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
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
import net.Gabou.projectatmosphere.modules.temperature.TemperatureModule;

@Mod(ProjectAtmosphere.MODID)
@EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class ProjectAtmosphere {
    private static final int DEFAULT_RADIUS = 250;
    public static final String MODID = "projectatmosphere";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public ProjectAtmosphere() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        GeckoLib.initialize();
        initModules();
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);

        EntityRegistrar.registerEntities(modEventBus);
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
    }

    @SubscribeEvent
    public static void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (world != null) {
            AtmosphereManager.onServerStarting(world);
        }
    }

    private void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (world != null) {
            AtmosphereManager.onServerStarted(world);
        }
    }


    private void initModules() {
        isSereneLoaded();
        AsyncAtmosphereService.init();
        AtmosphereManager.init();
        sendInfo();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AsyncAtmosphereService.shutdown();
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (world != null) {
            AtmosphereManager.onServerStopping(world);
        }
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Setting up Project Atmosphere (Common)");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Setting up Project Atmosphere (Client)");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AtmosphereManager.onRegisterCommands(event);
    }
    @Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID,bus = Mod.EventBusSubscriber.Bus.MOD,value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(final EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityRegistrar.CLOUD_ENTITY.get(), CloudRenderer::new);

        }
    }
    private static void sendInfo() {
        LOGGER.info("All modules subsystems have been initialized (Serene Seasons detected).");
    }
    private static void isSereneLoaded() {
        if (!ModList.get().isLoaded("sereneseasons")) {
            LOGGER.info("Serene Seasons is not found—skipping all modules subsystems.");
        }
    }
}
