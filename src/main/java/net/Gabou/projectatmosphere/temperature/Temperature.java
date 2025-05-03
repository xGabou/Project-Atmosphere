package net.Gabou.projectatmosphere.temperature;

import net.Gabou.projectatmosphere.temperature.command.TemperatureCommand;
import net.Gabou.projectatmosphere.temperature.event.SeasonTracker;
import net.Gabou.projectatmosphere.temperature.event.TemperatureTickHandler;
import net.Gabou.projectatmosphere.temperature.util.AsyncTemperatureService;
import net.Gabou.projectatmosphere.temperature.util.ForecastStorageManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;


public class Temperature {

    public static void init(IEventBus modBus) {

        // 1) Initialize the async executor
        modBus.addListener(Temperature::onCommonSetup);


        var fm = MinecraftForge.EVENT_BUS;
        // 3) Integrated (singleplayer) server start
        fm.addListener(Temperature::onServerStarting);
        // 4) Register /temperature commands
        fm.addListener(Temperature::onRegisterCommands);
        // 5) Midnight tick → rebuild daily profiles
        fm.register(TemperatureTickHandler.class);
        // 6) Clean shutdown hook
        fm.addListener(Temperature::onServerStopping);
        MinecraftForge.EVENT_BUS.register(SeasonTracker.class);

    }

    private static void onServerStopping(ServerStoppingEvent event) {
        // 2b-iii: Save all forecasts to disk
        ForecastStorageManager.saveAll();
        // 2b-iv: Shutdown async services
        AsyncTemperatureService.shutdown();
    }

    /** Phase 1: initialize async services */
    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        AsyncTemperatureService.init();
    }

    // 2b) Integrated (or dedicated) server start
    private static void onServerStarting(ServerStartingEvent evt) {
        initTemperatureForServer(evt.getServer());
    }

    // Shared initialization logic
    private static void initTemperatureForServer(MinecraftServer server) {
        Level world = server.getLevel(Level.OVERWORLD);
        if (world == null) return;
        // 2b-ii: Generate + store new forecasts around world spawn, schedule daily profiles
// Replace `spawn` with player position if available, and read radius from config
        ServerPlayer player = world.getServer().getPlayerList().getPlayers().stream().findFirst().orElse(null);
        BlockPos center = (player != null) ? player.blockPosition() : world.getSharedSpawnPos();
// TODO: Replace 250 with a configurable value via .toml config if needed
        int radiusBlocks = 250;
        TemperatureManager.init(world, center, radiusBlocks);

    }

    /** Phase 3: register the /temperature command */
    private static void onRegisterCommands(final RegisterCommandsEvent event) {
        TemperatureCommand.register(event.getDispatcher());
    }
}
