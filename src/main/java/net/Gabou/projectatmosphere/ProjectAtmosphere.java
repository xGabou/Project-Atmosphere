package net.Gabou.projectatmosphere;

import net.Gabou.projectatmosphere.client.renderer.*;
import net.Gabou.projectatmosphere.client.renderer.SmallNormalCloud1Renderer;
import net.Gabou.projectatmosphere.command.DebugAtmoCommand;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.modules.storm.StormModule;
import net.Gabou.projectatmosphere.registry.EntityRegistrar;
import net.Gabou.projectatmosphere.command.SpawnCloudCommand;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.GeckoLib;
import net.Gabou.projectatmosphere.event.*;
import net.Gabou.projectatmosphere.modules.temperature.TemperatureModule;

@Mod(ProjectAtmosphere.MODID)
@EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class ProjectAtmosphere {
    public static final int DEFAULT_RADIUS = 100;
    public static final String MODID = "projectatmosphere";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public ProjectAtmosphere() {
        LOGGER.info("Project Atmosphere is loading!");
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        //ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AtmoCommonConfig.COMMON_SPEC);
        GeckoLib.initialize();
        EntityRegistrar.registerEntities(modEventBus);
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(TemperatureTickHandler.class);
        MinecraftForge.EVENT_BUS.register(SeasonTracker.class);
        MinecraftForge.EVENT_BUS.register(BiomeChangeManager.class);
        MinecraftForge.EVENT_BUS.register(EventHandler.class);
    }

    @SubscribeEvent
    public static void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        AsyncAtmosphereService.init();
        if (world != null) {
            AtmosphereManager.onServerStarting(world);
        }
    }



    private void initModules() {
        isSereneLoaded();
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

    private void setup(final FMLCommonSetupEvent  event) {
        LOGGER.info("Setting up Project Atmosphere (Common)");
            initModules();

        }


    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Setting up Project Atmosphere (Client)");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
       AtmosphereManager.onRegisterCommands(event);
    }

    @SubscribeEvent
    public static void onPlayerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        ServerLevel world = event.getEntity().getServer().getLevel(ServerLevel.OVERWORLD);
        if (world != null && event.getEntity() instanceof ServerPlayer player) {
            AtmosphereManager.onPlayerJoined(world, player);
        }
    }
    @Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID,bus = Mod.EventBusSubscriber.Bus.MOD,value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(final EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityRegistrar.CLOUD_ENTITY.get(), CloudRenderer::new);
            event.registerEntityRenderer(EntityRegistrar.CLOUD_ENTITY1.get(), CloudRenderer1::new);
            event.registerEntityRenderer(EntityRegistrar.CLOUD_ENTITY2.get(), CloudRenderer2::new);
            event.registerEntityRenderer(EntityRegistrar.CLOUD_ENTITY3.get(), CloudRenderer3::new);
            event.registerEntityRenderer(EntityRegistrar.CLOUD_ENTITY4.get(), CloudRenderer4::new);
            event.registerEntityRenderer(EntityRegistrar.CLOUD_ENTITY5.get(), CloudRenderer5::new);
            event.registerEntityRenderer(EntityRegistrar.SMALL_NORMAL_CLOUD_1_ENTITY.get(), SmallNormalCloud1Renderer::new);

        }
    }
    @SubscribeEvent
    public static void onConfigLoaded(ModConfigEvent event) {
//        if (event.getConfig().getSpec() == AtmoCommonConfig.COMMON_SPEC) {
//            ProjectAtmosphere.LOGGER.info("✔ Config loaded!");
//        }
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
