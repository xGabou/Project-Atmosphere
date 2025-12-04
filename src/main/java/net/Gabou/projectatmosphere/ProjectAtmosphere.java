package net.Gabou.projectatmosphere;


import dev.nonamecrackers2.simpleclouds.api.SimpleCloudsAPI;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.registry.*;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.seasons.SeasonBootstrap;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.TickCounter;
import net.Gabou.projectatmosphere.modules.tornado.TornadoProbabilityManager;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.locale.Language;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
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

import java.util.Map;
import java.util.Objects;

@Mod(ProjectAtmosphere.MODID)
@EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class ProjectAtmosphere {

    public static final float DEFAULT_REGION_RADIUS = 1500F;

    public static final int DEFAULT_RADIUS = 50000;
    public static long seed;
    public static final String MODID = "projectatmosphere";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static boolean DEBUG_MODE = true;
    private static float seaLevel;
    private static boolean seaLevelInitialized = false;



    public ProjectAtmosphere(FMLJavaModLoadingContext context) {
        LOGGER.info("Project Atmosphere is loading!");
        IEventBus modEventBus = context.getModEventBus();

        context.registerConfig(ModConfig.Type.COMMON, AtmoCommonConfig.COMMON_SPEC);


        CompatHandler.init();
        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        NetworkHandler.init();

        SimpleCloudsConstants.SPAWN_RADIUS = Math.min(DEFAULT_RADIUS/5,10000);

        modEventBus.addListener(this::setup);
        modEventBus.addListener((FMLClientSetupEvent event) -> {
            clientSetup(event,context);
        });

        
        MinecraftForge.EVENT_BUS.register(TemperatureTickHandler.class);
        MinecraftForge.EVENT_BUS.register(SeasonTracker.class);
        MinecraftForge.EVENT_BUS.register(BiomeChangeManager.class);
        MinecraftForge.EVENT_BUS.register(EventHandler.class);

        MinecraftForge.EVENT_BUS.addListener((TickEvent event)-> {
            if (event.phase == TickEvent.Phase.END)TickCounter.onServerTick();
        });
        ModParticles.register(modEventBus);
        ModTabs.REGISTRY.register(modEventBus);
        ModBlocks.REGISTRY.register(modEventBus);
        SeasonBootstrap.initOrCrash();
    }


    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);

        if (world != null) {
            if(!world.isClientSide)
            {
                AsyncAtmosphereService.init(false);
                SimpleCloudsCompat.init(world);
                seed = world.getSeed();
            }
            else{
                AsyncAtmosphereService.init(true);
            }

        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        AtmosphereManager.onServerStarted(overworld);
        initSeaLevel(overworld);
        if(ProjectAtmosphere.DEBUG_MODE)
            ProjectAtmosphere.LOGGER.info("BiomeSampler initialized with live biome source.");
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
        if(ProjectAtmosphere.DEBUG_MODE)
            LOGGER.info("Setting up Project Atmosphere (Common)");
        initModules();
        TornadoProbabilityManager.init();
        event.enqueueWork(() -> {

            SimpleCloudsAPI.getApi().getHooks().setExternalWeatherControl(true);
            // Load user biome temperature overrides after defaults are initialized
            net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempUserConfig.load();
        });

    }


    private void clientSetup(final FMLClientSetupEvent event, FMLJavaModLoadingContext context) {
        event.enqueueWork(() -> {
            if(ProjectAtmosphere.DEBUG_MODE)
                LOGGER.info("Setting up Project Atmosphere (Client)");
            ClientOnlyRegistrar.registerClient(MinecraftForge.EVENT_BUS,context);
            Map<String, String> translations = Language.getInstance().getLanguageData();
            translations.put("sandstorm.debug.blocked", "Nothing to report. Stay alert.");
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.BAROMETER_BLOCK.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.THERMOMETER_BLOCK.get(), RenderType.translucent());


        });
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AtmosphereManager.onRegisterCommands(event);
    }

    







    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if(ProjectAtmosphere.DEBUG_MODE)
            LOGGER.info("Player logged in!");
        AtmosphereManager.onPlayerLogin(player.getServer().getLevel(ServerLevel.OVERWORLD), player);


        

    }


    @SubscribeEvent
    public static void onConfigLoaded(ModConfigEvent event) {



    }

    private static void sendInfo() {
        if(ProjectAtmosphere.DEBUG_MODE)
            LOGGER.info("All modules subsystems have been initialized (Serene Seasons detected).");
    }

    private static void isSereneLoaded() {
        if (!ModList.get().isLoaded("sereneseasons")) {
            if(ProjectAtmosphere.DEBUG_MODE)
                LOGGER.info("Serene Seasons is not found—skipping all modules subsystems.");
        }
    }

    static void initSeaLevel(Level level) {
        if (!seaLevelInitialized) {
            seaLevel = level.getSeaLevel();
            seaLevelInitialized = true;
        }
    }

    public static float getSeaLevel() {
        if (!seaLevelInitialized) {
            LOGGER.warn("Sea level requested before initialization; defaulting to 60f.");
            return 60f;
        }
        return seaLevel;
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
