package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.modules.wind.manager.WindManager;
import net.Gabou.projectatmosphere.modules.wind.util.WindStorageManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = "projectatmosphere")
public class WindModule {

    private static final int DEFAULT_RADIUS = 250;

    public static void init() {
    }


    public static void onServerStarted(ServerLevel world) {
        WindStorageManager.loadAll(world);
        BlockPos center = world.getSharedSpawnPos();
        WindManager.init(world, center);
    }

    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level) {
            WindManager.onPlayerJoined(level, player.blockPosition());
        }
    }

    public static void onServerStopping(ServerLevel event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (world != null) {
            WindStorageManager.saveAll(world);
        }
    }

    public static void precompute(ServerLevel world) {
        WindManager.onPrecomputeProfiles(world);
    }

    public static void swap(ServerLevel world) {
        WindManager.onSwapProfiles(world);
    }

    public static void clear(ServerLevel world) {
        WindManager.clearForecastCache(world, world.getSharedSpawnPos());
    }

    public static void onServerStarting(ServerLevel world, BlockPos center) {
    }
}
