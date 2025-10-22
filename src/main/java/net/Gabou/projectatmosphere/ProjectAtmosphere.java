package net.Gabou.projectatmosphere;


import dev.nonamecrackers2.simpleclouds.api.SimpleCloudsAPI;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import net.Gabou.projectatmosphere.client.ClientTickHandler;
import net.Gabou.projectatmosphere.client.HUDOverlayRenderer;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneCommand;
import net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempUserConfig;
import net.Gabou.projectatmosphere.modules.tornado.TornadoCommand;
import net.Gabou.projectatmosphere.modules.tornado.TornadoDebug;
import net.Gabou.projectatmosphere.registry.*;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.CloudSpawnScheduler;
import net.Gabou.projectatmosphere.util.TickCounter;
import net.Gabou.projectatmosphere.modules.tornado.TornadoProbabilityManager;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.locale.Language;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.Gabou.projectatmosphere.event.*;

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


    public ProjectAtmosphere(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Project Atmosphere is loading!");
        modContainer.registerConfig(ModConfig.Type.COMMON, AtmoCommonConfig.COMMON_SPEC);
        CompatHandler.init();
        SimpleCloudsEventListener.register(NeoForge.EVENT_BUS);
        SimpleCloudsConstants.SPAWN_RADIUS = Math.round(
                DEFAULT_RADIUS / DEFAULT_REGION_RADIUS *
                        SimpleCloudsConstants.CLOUD_SCALE *
                        ForecastGenerator.MAX_POSITIONS_PER_BIOME
        );
        registerListenersAndRegistries(modEventBus);
    }

    private void registerListenersAndRegistries(IEventBus modEventBus) {
        // Mod lifecycle events
        NetworkHandler.register(modEventBus);
        modEventBus.addListener(this::setup);
        modEventBus.addListener((FMLClientSetupEvent event) -> clientSetup(event,modEventBus));

        // Registries
        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        ModParticles.register(modEventBus);
        ModTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModBlocks.REGISTRY.register(modEventBus);

        // Gameplay events → global bus
        CloudSpawnScheduler.register(NeoForge.EVENT_BUS);
        SeasonTracker.register(); // let it self-register with EventManager
        NeoForge.EVENT_BUS.register(BiomeChangeManager.class);
        NeoForge.EVENT_BUS.register(EventHandler.class);

        NeoForge.EVENT_BUS.addListener(TickCounter::onServerTick);
        NeoForge.EVENT_BUS.addListener(TornadoDebug::register);
        NeoForge.EVENT_BUS.addListener(TornadoCommand::register);
        NeoForge.EVENT_BUS.addListener(HurricaneCommand::register);
    }



    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);

        if (world != null) {
            if (!world.isClientSide) {
                AsyncAtmosphereService.init(false);
                SimpleCloudsCompat.init(world);
                seed = world.getSeed();
            } else {
                AsyncAtmosphereService.init(true);
            }

        }
    }


    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        AtmosphereManager.onServerStarting(overworld);
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
        LOGGER.info("Setting up Project Atmosphere (Common)");
        initModules();
        TornadoProbabilityManager.init();
        event.enqueueWork(() -> {
            SimpleCloudsAPI.getApi().getHooks().setExternalWeatherControl(true);
            // Load user biome temperature overrides after defaults are initialized
           BiomeTempUserConfig.load();
        });

    }


    private void clientSetup(final FMLClientSetupEvent event,IEventBus modEventBus) {
        event.enqueueWork(() -> {
            LOGGER.info("Setting up Project Atmosphere (Client)");
            NeoForge.EVENT_BUS.register(ClientTickHandler.class);
            ClientOnlyRegistrar.registerClient(modEventBus);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.BAROMETER_BLOCK.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.THERMOMETER_BLOCK.get(), RenderType.translucent());

//            Map<String, String> translations = Language.getInstance().getLanguageData();
//            translations.put("sandstorm.debug.blocked", "Nothing to report. Stay alert.");
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
