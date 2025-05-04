package net.Gabou.projectatmosphere.modules.pressure;

import net.Gabou.projectatmosphere.modules.pressure.manager.PressureManager;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureStorageManager;
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
public class PressureModule {
    private static final int DEFAULT_RADIUS = PressureManager.radiusBlocks;

    public static void init() {

    }


    public static void onServerStarted(ServerLevel world) {

        PressureStorageManager.loadAll(world);
        PressureManager.init(world, world.getSharedSpawnPos());
    }

    private static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel lvl) {
            PressureManager.onPlayerJoined(lvl, player.blockPosition());
        }
    }

    public static void onServerStopping(ServerLevel event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (world != null) {
            PressureStorageManager.saveAll(world);
        }
    }

    public static void precompute(ServerLevel world) {
        PressureManager.onPrecomputeProfiles(world);
    }

    public static void swap(ServerLevel world) {
        PressureManager.onSwapProfiles(world);
    }

    public static void clear() {
        PressureManager.clearForecastCache();
    }

    public static void onServerStarting(ServerLevel world, BlockPos center) {
    }
}
