package net.Gabou.projectatmosphere.modules.temperature;

import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommands;
import net.Gabou.projectatmosphere.modules.temperature.event.SeasonTracker;
import net.Gabou.projectatmosphere.modules.temperature.event.TemperatureTickHandler;
import net.Gabou.projectatmosphere.modules.temperature.manager.TemperatureManager;
import net.Gabou.projectatmosphere.modules.temperature.spike.SpikeStateStorage;
import net.Gabou.projectatmosphere.modules.temperature.util.ForecastStorageManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = "projectatmosphere")
public class TemperatureModule {

    /** Call once from your main mod if Serene Seasons is loaded */
    public static void init(IEventBus modBus) {
        modBus.addListener(TemperatureModule::onCommonSetup);

        MinecraftForge.EVENT_BUS.addListener(TemperatureModule::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(TemperatureModule::onRegisterCommands);
        MinecraftForge.EVENT_BUS.register(TemperatureTickHandler.class);
        MinecraftForge.EVENT_BUS.register(SeasonTracker.class);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        AsyncAtmosphereService.init();
    }


    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (event.getServer().isDedicatedServer()) return;
        initTemperatureForServer(event.getServer());
    }

    private static void initTemperatureForServer(MinecraftServer server) {
        ServerLevel world = server.getLevel(Level.OVERWORLD);
        if (world == null) return;
        loadData(world);
        BlockPos center = world.getSharedSpawnPos();
        TemperatureManager.initTemperatureForServer(world, center);
    }
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
            BlockPos pos = player.blockPosition();
            TemperatureManager.onPlayerJoined(serverLevel, pos);
        }
    }


    private static void loadData(ServerLevel world) {
        ForecastStorageManager.loadAll(world);
        SpikeStateStorage.loadAll(world);
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        saveData(event.getServer().overworld());

    }

    private static void saveData(ServerLevel world) {
        ForecastStorageManager.saveAll(world);
        SpikeStateStorage.saveAll(world);
    }

    /** Phase 3: register the /temperature command */
    private static void onRegisterCommands(final RegisterCommandsEvent event) {
        TemperatureCommands.register(event.getDispatcher());
    }
    public static Path getPerWorldSavePath(ServerLevel world, String fileName) {
        return world.getServer()
                .getWorldPath(LevelResource.ROOT) // this gives saves/New World/
                .resolve(world.dimension().location().getNamespace().equals("minecraft")
                        ? world.dimension().location().getPath() // e.g., "DIM1", "DIM-1", or "overworld"
                        : world.dimension().location().toString()) // handles custom dimensions
                .resolve("data")
                .resolve("projectatmosphere")
                .resolve(fileName);
    }

}