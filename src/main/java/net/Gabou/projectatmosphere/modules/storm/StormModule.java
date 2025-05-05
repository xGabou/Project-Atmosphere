package net.Gabou.projectatmosphere.modules.storm;

import net.Gabou.projectatmosphere.modules.storm.manager.StormManager;
import net.Gabou.projectatmosphere.modules.storm.util.StormStorageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import static net.Gabou.projectatmosphere.ProjectAtmosphere.DEFAULT_RADIUS;

/**
 * Static utility module for storm management lifecycle.
 */
public class StormModule {


    public static void init() {
    }


    public static void onServerStarting(ServerLevel world,BlockPos center) {
        StormStorageManager.loadAll(world);
        StormManager.init(world, center);
    }

    public static void onPlayerJoined(ServerLevel world, BlockPos pos) {
            StormManager.onPlayerJoined(world, pos);
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (world != null) {
            StormStorageManager.saveAll(world);
        }
    }



    public static void onRegenerate(ServerLevel world) {
        StormManager.onRegenerate(world,world.players());
    }

    public static void onSeasonChange(ServerLevel world) {
        StormManager.onSeasonChange(world);
    }

    public static void onSwapProfiles(ServerLevel world) {
        StormManager.onSwapProfiles(world);
    }

    public static void onPrecomputeProfiles(ServerLevel world) {
        StormManager.onPrecomputeProfiles(world);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StormManager.onRegisterCommands(event);
    }

    public static void updateForecastAround(ServerLevel world, BlockPos center) {

    }
}
