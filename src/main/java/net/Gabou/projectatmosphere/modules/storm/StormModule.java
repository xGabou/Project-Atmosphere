package net.Gabou.projectatmosphere.modules.storm;

import net.Gabou.projectatmosphere.modules.storm.manager.StormManager;
import net.Gabou.projectatmosphere.modules.storm.util.StormStorageManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Static utility module for storm management lifecycle.
 */
@Mod.EventBusSubscriber(modid = "projectatmosphere")
public class StormModule {
    private static final int DEFAULT_RADIUS = StormManager.radiusBlocks;

    public static void init(IEventBus modBus) {
        modBus.addListener(StormModule::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(StormModule::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(StormModule::onPlayerJoin);
        MinecraftForge.EVENT_BUS.addListener(StormModule::onServerStopping);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        AsyncAtmosphereService.init();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (server.isDedicatedServer()) return;

        ServerLevel world = server.getLevel(ServerLevel.OVERWORLD);
        if (world == null) return;

        StormStorageManager.loadAll(world);
        BlockPos center = world.getSharedSpawnPos();
        StormManager.init(world, center, DEFAULT_RADIUS);
    }

    private static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel lvl) {
            BlockPos pos = player.blockPosition();
            StormManager.onPlayerJoined(lvl, pos);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (world != null) {
            StormStorageManager.saveAll(world);
        }
    }

    // Callable from commands or external systems
    public static void clearForecastCache() {
        StormManager.clearForecastCache();
    }

    public static void precompute(ServerLevel world) {
        StormManager.onPrecomputeProfiles(world);
    }

    public static void swap(ServerLevel world) {
        StormManager.onSwapProfiles(world);
    }

    public static void regenerate(ServerLevel world) {
        StormManager.onSeasonChange(world, world.getSharedSpawnPos());
    }
}
