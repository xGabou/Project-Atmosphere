package net.Gabou.projectatmosphere;


import dev.nonamecrackers2.simpleclouds.api.SimpleCloudsAPI;
import dev.nonamecrackers2.simpleclouds.common.api.SimpleCloudsHooks;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import glitchcore.core.GlitchCore;
import net.Gabou.projectatmosphere.client.ClientTickHandler;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.network.SyncBiomeDataLoginPacket;
import net.Gabou.projectatmosphere.registry.*;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.Gabou.projectatmosphere.event.*;

import java.util.Objects;

@Mod(ProjectAtmosphere.MODID)
@EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class ProjectAtmosphere {

    public static final float DEFAULT_REGION_RADIUS = 700F; // Default radius for region generation

    public static final int DEFAULT_RADIUS = 10000;
    public static long seed;
    public static final String MODID = "projectatmosphere";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public ProjectAtmosphere() {
        LOGGER.info("Project Atmosphere is loading!");
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        CompatHandler.init();
        ModItems.register(modEventBus);
        SimpleCloudsConstants.SPAWN_RADIUS = Math.round(DEFAULT_RADIUS / DEFAULT_REGION_RADIUS* SimpleCloudsConstants.CLOUD_SCALE* ForecastGenerator.MAX_POSITIONS_PER_BIOME);

        //ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AtmoCommonConfig.COMMON_SPEC);
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(TemperatureTickHandler.class);
        MinecraftForge.EVENT_BUS.register(SeasonTracker.class);
        MinecraftForge.EVENT_BUS.register(BiomeChangeManager.class);
        MinecraftForge.EVENT_BUS.register(EventHandler.class);
        MinecraftForge.EVENT_BUS.register(ClientTickHandler.class);
        ModTabs.REGISTRY.register(modEventBus);
        ModBlocks.REGISTRY.register(modEventBus);
        ModNetworking.register();
        ModParticles.register(modEventBus);
    }

    @SubscribeEvent
    public static void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        AsyncAtmosphereService.init();
        if (world != null) {
            SimpleCloudsCompat.init(world);
            AtmosphereManager.onServerStarting(world);
            seed = world.getSeed();
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
            seed = 0; // Reset the seed when the server stops
        }
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Setting up Project Atmosphere (Common)");
        initModules();
        event.enqueueWork(() -> {
            SimpleCloudsAPI.getApi().getHooks().setExternalWeatherControl(true);
        });
        SeasonTracker.register();

    }


    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Setting up Project Atmosphere (Client)");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AtmosphereManager.onRegisterCommands(event);
    }

    //    @SubscribeEvent
//    public static void onEntityJoin(EntityJoinLevelEvent event) {
//        if (!(event.getEntity() instanceof ServerPlayer player)) return;
//        ServerLevel world = Objects.requireNonNull(event.getEntity().getServer()).getLevel(ServerLevel.OVERWORLD);
//        if (world != null) {
//            AtmosphereManager.onPlayerLogin(world, player);
//        }
//    }
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LOGGER.info("Player logged in!");
        AtmosphereManager.onPlayerLogin(player.getServer().getLevel(ServerLevel.OVERWORLD), player);
//        LoginDataGate.sendBiomeSyncPacketIfReady(player.getServer(), player);
        // Lance la logique async → quand prête, libère le client

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
