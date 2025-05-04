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
    public static void init() {
        MinecraftForge.EVENT_BUS.register(TemperatureTickHandler.class);
        MinecraftForge.EVENT_BUS.register(SeasonTracker.class);
    }




    public static void onServerStarting(ServerLevel world) {
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

    public static void onServerStopping(ServerLevel event) {
        saveData(event.getServer().overworld());

    }

    private static void saveData(ServerLevel world) {
        ForecastStorageManager.saveAll(world);
        SpikeStateStorage.saveAll(world);
    }

    /** Phase 3: register the /temperature command */
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        TemperatureCommands.register(event.getDispatcher());
    }
    public static void onServerStarted(ServerLevel world) {

    }


}