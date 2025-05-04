package net.Gabou.projectatmosphere.modules.temperature;


import net.Gabou.projectatmosphere.event.TemperatureTickHandler;
import net.Gabou.projectatmosphere.modules.temperature.manager.TemperatureManager;
import net.Gabou.projectatmosphere.modules.temperature.spike.SpikeStateStorage;
import net.Gabou.projectatmosphere.modules.temperature.util.ForecastStorageManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.core.BlockPos;


public class TemperatureModule {

    /** Call once from your main mod if Serene Seasons is loaded */
    public static void init() {

    }

    public static void onPrecomputeProfiles(ServerLevel world) {
        TemperatureManager.onPrecomputeProfiles(world);
    }



    public static void onServerStarting(ServerLevel world,BlockPos center) {
        loadData(world);
        TemperatureManager.initTemperatureForServer(world, center);
    }

    public static void onPlayerJoined(ServerLevel serverLevel,BlockPos pos) {
            TemperatureManager.onPlayerJoined(serverLevel, pos);
    }
    public static void onSeasonChange(ServerLevel world) {
        TemperatureManager.onSeasonChange(world);
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
        TemperatureManager.onRegisterCommands(event);
    }


    public static void onSwapProfiles(ServerLevel world) {
        TemperatureManager.onSwapProfiles(world);
    }

    public static void onRegenerate(ServerLevel world) {
        TemperatureManager.onRegenerate(world,world.players());
    }
}