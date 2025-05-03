package net.Gabou.projectatmosphere.temperature;

import net.Gabou.projectatmosphere.temperature.command.TemperatureCommand;
import net.Gabou.projectatmosphere.temperature.event.SeasonTracker;
import net.Gabou.projectatmosphere.temperature.event.TemperatureTickHandler;
import net.Gabou.projectatmosphere.temperature.util.AsyncTemperatureService;
import net.Gabou.projectatmosphere.temperature.util.ForecastStorageManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

public class Temperature {

    /** Call once from your main mod if Serene Seasons is loaded */
    public static void init() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(Temperature::onCommonSetup);

        MinecraftForge.EVENT_BUS.addListener(Temperature::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(Temperature::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(Temperature::onRegisterCommands);
        MinecraftForge.EVENT_BUS.register(TemperatureTickHandler.class);
        MinecraftForge.EVENT_BUS.register(SeasonTracker.class);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        AsyncTemperatureService.init();
    }


    private static void onServerStarting(ServerStartingEvent event) {
        if (event.getServer().isDedicatedServer()) return;
        initTemperatureForServer(event.getServer());
    }

    private static void initTemperatureForServer(MinecraftServer server) {
        Level world = server.getLevel(Level.OVERWORLD);
        if (world == null) return;

        ForecastStorageManager.loadAll();
        BlockPos center = world.getSharedSpawnPos();
        TemperatureManager.init(world, center, /*radius=*/250);
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        ForecastStorageManager.saveAll();
        AsyncTemperatureService.shutdown();
    }
    /** Phase 3: register the /temperature command */
    private static void onRegisterCommands(final RegisterCommandsEvent event) {
        TemperatureCommand.register(event.getDispatcher());
    }
}
