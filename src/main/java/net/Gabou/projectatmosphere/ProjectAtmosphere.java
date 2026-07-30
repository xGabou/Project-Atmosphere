package net.Gabou.projectatmosphere;



import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.clouds.field.network.CloudFieldSyncManager;
import net.Gabou.projectatmosphere.clouds.cell.sim.CloudCellSimulationManager;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDataReloadListener;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.registry.*;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.seasons.SeasonBootstrap;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.TickCounter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.Gabou.projectatmosphere.event.*;

import java.util.Objects;

@Mod(ProjectAtmosphere.MODID)
@EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class ProjectAtmosphere {

    public static final float DEFAULT_REGION_RADIUS = 2400F;

    public static final int DEFAULT_RADIUS = 50000;
    public static long seed;
    public static final String MODID = "projectatmosphere";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static boolean DEBUG_MODE = true;
    private static float seaLevel;
    private static boolean seaLevelInitialized = false;



    public ProjectAtmosphere(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Project Atmosphere is loading!");
        modContainer.registerConfig(ModConfig.Type.COMMON, AtmoCommonConfig.COMMON_SPEC);

        CompatHandler.init();
        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        NetworkHandler.register(modEventBus);

        modEventBus.addListener(this::setup);
        if (FMLEnvironment.dist.isClient()) {
            registerClientBootstrap(modEventBus, modContainer);
        }

        NeoForge.EVENT_BUS.register(TemperatureTickHandler.class);
        NeoForge.EVENT_BUS.register(BiomeChangeManager.class);
        NeoForge.EVENT_BUS.register(EventHandler.class);
        NeoForge.EVENT_BUS.addListener(CloudTypeDataReloadListener::onAddReloadListeners);

        NeoForge.EVENT_BUS.addListener(TickCounter::onServerTick);
        ModParticles.register(modEventBus);
        ModTabs.CREATIVE_MODE_TABS.register(modEventBus);
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
                AtmosphereCloudServices.get().onServerStarting(world);
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
            CloudFieldSyncManager.forgetPlayer(player.getUUID());
            CloudCellSimulationManager.getInstance().forgetPlayer(player.getUUID());
            ServerLevel world = Objects.requireNonNull(player.getServer()).getLevel(ServerLevel.OVERWORLD);
            if (world != null) {
                AtmosphereManager.onPlayerLogout(world, player);
            }
        }
    }

    private void initModules() {
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
        event.enqueueWork(() -> {

            configureOptionalCloudHooks();
            // Load user biome temperature overrides after defaults are initialized
            net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempUserConfig.load();
        });

    }

    private void configureOptionalCloudHooks() {
        if (AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            LOGGER.info("[Atmosphere] Simple Clouds owns cloud hooks; PA cloud hooks disabled.");
        }
    }


    private static void registerClientBootstrap(IEventBus modEventBus, ModContainer modContainer) {
        String className = "net.Gabou.projectatmosphere.registry.ClientBootstrap";
        try {
            Class<?> bootstrap = Class.forName(className, true, ProjectAtmosphere.class.getClassLoader());
            bootstrap.getMethod("register", IEventBus.class, ModContainer.class)
                    .invoke(null, modEventBus, modContainer);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Project Atmosphere client bootstrap failed", exception);
        }
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
            LOGGER.info("All module subsystems have been initialized.");
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
            if (!isClient) {
                return new ServerSystemProfile();
            }
            String className = "net.Gabou.projectatmosphere.ClientSystemProfile";
            try {
                Class<?> profileClass = Class.forName(className, true, ProjectAtmosphere.class.getClassLoader());
                return (SystemProfile) profileClass.getConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError exception) {
                LOGGER.warn("Client GPU profile unavailable; using the server-safe profile.", exception);
                return new ServerSystemProfile();
            }
        }
    }
}
