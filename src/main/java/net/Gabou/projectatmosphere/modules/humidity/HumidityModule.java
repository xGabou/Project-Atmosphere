package net.Gabou.projectatmosphere.modules.humidity;

import net.Gabou.projectatmosphere.command.DebugAtmoCommand;
import net.Gabou.projectatmosphere.command.SpawnCloudCommand;
import net.Gabou.projectatmosphere.modules.humidity.Command.HumidityCommand;
import net.Gabou.projectatmosphere.modules.humidity.manager.HumidityManager;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityStorageManager;
import net.Gabou.projectatmosphere.modules.temperature.TemperatureModule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;


public class HumidityModule {

    public static void init() {
    }

    public static void onPlayerJoined(ServerLevel level, BlockPos pos) {
            HumidityManager.onPlayerJoined(level, pos);
    }

    public static void onServerStopping(ServerLevel event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (world != null) {
            HumidityStorageManager.saveAll(world);
        }
    }


    public static void onSwapProfiles(ServerLevel world) {
        HumidityManager.onSwapProfiles(world);
    }
    public static void onSeasonChange(ServerLevel world) {
        HumidityManager.onSeasonChange(world);
    }

    public static void onServerStarting(ServerLevel world, BlockPos center) {
        HumidityStorageManager.loadAll(world);
       // HumidityManager.init(world, center);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        HumidityManager.onRegisterCommands(event);
    }

    public static void onPrecomputeProfiles(ServerLevel world) {
        HumidityManager.onPrecomputeProfiles(world);
    }
    public static void onRegenerate(ServerLevel world) {
        HumidityManager.onRegenerate(world, world.players());
    }

    public static void updateForecastAround(ServerLevel world, BlockPos center) {
        HumidityManager.updateForecastAround(world, center);
    }
}
