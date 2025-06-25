package net.Gabou.projectatmosphere;


import dev.nonamecrackers2.simpleclouds.common.api.SimpleCloudsHooks;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import net.Gabou.projectatmosphere.client.ClientTickHandler;
import net.Gabou.projectatmosphere.registry.ModItems;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.registry.ModParticles;
import net.Gabou.projectatmosphere.registry.ModTabs;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.Gabou.projectatmosphere.event.*;

@Mod(ProjectAtmosphere.MODID)
@EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class ProjectAtmosphere {
    public static final int DEFAULT_RADIUS = 500;
    public static final String MODID = "projectatmosphere";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public ProjectAtmosphere() {
        LOGGER.info("Project Atmosphere is loading!");
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(modEventBus);
        //ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AtmoCommonConfig.COMMON_SPEC);
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(TemperatureTickHandler.class);
        MinecraftForge.EVENT_BUS.register(SeasonTracker.class);
        MinecraftForge.EVENT_BUS.register(BiomeChangeManager.class);
        MinecraftForge.EVENT_BUS.register(EventHandler.class);
        MinecraftForge.EVENT_BUS.register(ClientTickHandler.class);
        SimpleCloudsConstants.SPAWN_RADIUS = DEFAULT_RADIUS;
        ModTabs.REGISTRY.register(modEventBus);
        ModParticles.register(modEventBus);
    }

    @SubscribeEvent
    public static void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        AsyncAtmosphereService.init();
        SimpleCloudsHooks.setExternalWeatherControl(true);
        if (world != null) {
            AtmosphereManager.onServerStarting(world);
        }
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey() == CreativeModeTabs.INGREDIENTS){
            event.accept(ModItems.THERMOMETRE);
            event.accept(ModItems.BAROMETER);
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
