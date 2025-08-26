package net.Gabou.projectatmosphere;


import dev.nonamecrackers2.simpleclouds.api.SimpleCloudsAPI;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.registry.*;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.TickCounter;
import net.Gabou.projectatmosphere.modules.tornado.TornadoProbabilityManager;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.Gabou.projectatmosphere.event.*;
import sereneseasons.config.SeasonsConfig;
import sereneseasons.core.SereneSeasons;
import sereneseasons.season.SeasonHandler;
import sereneseasons.season.SeasonTime;

import java.util.Map;
import java.util.Objects;

@Mod(ProjectAtmosphere.MODID)
@EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class ProjectAtmosphere {

    public static final float DEFAULT_REGION_RADIUS = 700F; 

    public static final int DEFAULT_RADIUS = 10000;
    public static long seed;
    public static final String MODID = "projectatmosphere";
    public static final Logger LOGGER = LogManager.getLogger(MODID);




    public ProjectAtmosphere(FMLJavaModLoadingContext context) {
        LOGGER.info("Project Atmosphere is loading!");
        IEventBus modEventBus = context.getModEventBus();

        context.registerConfig(ModConfig.Type.COMMON, AtmoCommonConfig.COMMON_SPEC);


        CompatHandler.init();
        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        NetworkHandler.init();

        SimpleCloudsConstants.SPAWN_RADIUS = Math.round(
                DEFAULT_RADIUS / DEFAULT_REGION_RADIUS *
                        SimpleCloudsConstants.CLOUD_SCALE *
                        ForecastGenerator.MAX_POSITIONS_PER_BIOME
        );

        modEventBus.addListener(this::setup);
        modEventBus.addListener((FMLClientSetupEvent event) -> {
            clientSetup(event,context);
        });

        
        MinecraftForge.EVENT_BUS.register(TemperatureTickHandler.class);
        MinecraftForge.EVENT_BUS.register(SeasonTracker.class);
        MinecraftForge.EVENT_BUS.register(BiomeChangeManager.class);
        MinecraftForge.EVENT_BUS.register(EventHandler.class);

        MinecraftForge.EVENT_BUS.addListener(TickCounter::onServerTick);
        ModParticles.register(modEventBus);
        ModTabs.REGISTRY.register(modEventBus);
        ModBlocks.REGISTRY.register(modEventBus);

    }


    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);

        if (world != null) {
            if(!world.isClientSide)
            {
                AsyncAtmosphereService.init(false);
                SimpleCloudsCompat.init(world);
                AtmosphereManager.onServerStarting(world);
                seed = world.getSeed();
            }
            else{
                AsyncAtmosphereService.init(true);
            }

        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel world = Objects.requireNonNull(player.getServer()).getLevel(ServerLevel.OVERWORLD);
            if (world != null) {
                AtmosphereManager.onPlayerLogout(world, player);
            }
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
            seed = 0; 
        }
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Setting up Project Atmosphere (Common)");
        initModules();
        TornadoProbabilityManager.init();
        event.enqueueWork(() -> {
            SimpleCloudsAPI.getApi().getHooks().setExternalWeatherControl(true);
        });

    }


    private void clientSetup(final FMLClientSetupEvent event, FMLJavaModLoadingContext context) {
        event.enqueueWork(() -> {
            LOGGER.info("Setting up Project Atmosphere (Client)");
            ClientOnlyRegistrar.registerClient(MinecraftForge.EVENT_BUS,context);
            Map<String, String> translations = Language.getInstance().getLanguageData();
            translations.put("sandstorm.debug.blocked", "Nothing to report. Stay alert.");
        });
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AtmosphereManager.onRegisterCommands(event);
    }

    







    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LOGGER.info("Player logged in!");
        AtmosphereManager.onPlayerLogin(player.getServer().getLevel(ServerLevel.OVERWORLD), player);


        

    }


    @SubscribeEvent
    public static void onConfigLoaded(ModConfigEvent event) {



    }

    private static void sendInfo() {
        LOGGER.info("All modules subsystems have been initialized (Serene Seasons detected).");
    }

    private static void isSereneLoaded() {
        if (!ModList.get().isLoaded("sereneseasons")) {
            LOGGER.info("Serene Seasons is not found—skipping all modules subsystems.");
        }
    }


    public abstract static class SystemProfile {

        public final int cpuCount = Runtime.getRuntime().availableProcessors();
        public final long maxMemoryMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        public boolean isLowSpecCPU() {
            return cpuCount <= 4;
        }

        public boolean isLowMemory() {
            return maxMemoryMB <= 2048;
        }

        public boolean isLowSpec() {
            return isLowSpecCPU() || isLowMemory() || !isGoodEnoughGPU();
        }

        public abstract boolean isGoodEnoughGPU();

        public abstract String getGPUName();

        
        public static SystemProfile create(boolean isClient) {
            return isClient ? new ClientSystemProfile() : new ServerSystemProfile();
        }
    }
}
