package net.Gabou.projectatmosphere.temperature;

import net.Gabou.projectatmosphere.temperature.command.TemperatureCommand;
import net.Gabou.projectatmosphere.temperature.event.TemperatureTickHandler;
import net.Gabou.projectatmosphere.temperature.util.AsyncTemperatureService;
import net.Gabou.projectatmosphere.temperature.util.ForecastStorageManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;


public class Temperature {


    /** Call once on your mod’s constructor (only if Sereneseasons is present). */
    public static void init() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // 1) Initialize the async executor
        modBus.addListener(evt -> AsyncTemperatureService.init());
        // 2) Dedicated server setup

        var fm = MinecraftForge.EVENT_BUS;
        // 3) Integrated (singleplayer) server start
        fm.addListener(Temperature::onServerStarting);
        // 4) Register /temperature commands
        fm.addListener((RegisterCommandsEvent evt) -> TemperatureCommand.register(evt.getDispatcher()));
        // 5) Midnight tick → rebuild daily profiles
        fm.register(TemperatureTickHandler.class);
        // 6) Clean shutdown hook
        fm.addListener(Temperature::onServerStopping);
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        // 2b-iii: Save all forecasts to disk
        ForecastStorageManager.saveAll();
        // 2b-iv: Shutdown async services
        AsyncTemperatureService.shutdown();
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
        BlockPos spawn = world.getSharedSpawnPos();
        //TODO use a config value for the radius and use players position instead of spawn
        TemperatureManager.init(world, spawn, /*radiusBlocks=*/250);
    }

}
