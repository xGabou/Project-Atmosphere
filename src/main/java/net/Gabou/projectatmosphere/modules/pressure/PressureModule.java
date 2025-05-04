package net.Gabou.projectatmosphere.modules.pressure;

import net.Gabou.projectatmosphere.modules.pressure.manager.PressureManager;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureStorageManager;
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


public class PressureModule {

    public static void init() {

    }


    public static void onPlayerJoined(ServerLevel level, BlockPos pos) {
            PressureManager.onPlayerJoined(level, pos);
    }

    public static void onServerStopping(ServerLevel event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (world != null) {
            PressureStorageManager.saveAll(world);
        }
    }


    public static void onServerStarting(ServerLevel world, BlockPos center) {
        PressureStorageManager.loadAll(world);
        PressureManager.init(world, center);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {

        //PressureManager.onRegisterCommands(event);
    }
    public static void onSwapProfiles(ServerLevel world) {
        PressureManager.onSwapProfiles(world);
    }

    public static void onPrecomputeProfiles(ServerLevel world) {
        PressureManager.onPrecomputeProfiles(world);
    }

    public static void onRegenerate(ServerLevel world) {
        PressureManager.onRegenerate(world,world.players());
    }

    public static void onSeasonChange(ServerLevel world) {
        PressureManager.onSeasonChange(world);
    }
}
