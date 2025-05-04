package net.Gabou.projectatmosphere.modules.storm;

import net.Gabou.projectatmosphere.modules.core.BaseAtmosphereModule;
import net.Gabou.projectatmosphere.modules.storm.manager.StormManager;
import net.Gabou.projectatmosphere.modules.storm.util.StormStorageManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Central Storm module: handles lifecycle, schedules per‐biome forecasts,
 * and plugs into the BaseAtmosphereModule scheduling hooks.
 */
@Mod.EventBusSubscriber(modid = "projectatmosphere")
public class StormModule extends BaseAtmosphereModule {
    private static final int DEFAULT_RADIUS = StormManager.radiusBlocks;

    public StormModule() {
        super(DEFAULT_RADIUS);
    }

    /** Call this once from your main mod class if you have a mod‐bus. */
    public static void init(IEventBus modBus) {
        // Forge setup phase
        modBus.addListener(StormModule::onCommonSetup);
        // Server events for init and teardown
        MinecraftForge.EVENT_BUS.addListener(StormModule::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(StormModule::onPlayerJoin);
        MinecraftForge.EVENT_BUS.addListener(StormModule::onServerStopping);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        AsyncAtmosphereService.init();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (server.isDedicatedServer()) return;
        ServerLevel world = server.getLevel(ServerLevel.OVERWORLD);
        if (world == null) return;

        // load any persisted storm data
        StormStorageManager.loadAll(world);

        // generate if needed
        BlockPos center = world.getSharedSpawnPos();
        StormManager.init(world, center, DEFAULT_RADIUS);
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel lvl) {
            BlockPos pos = player.blockPosition();
            StormManager.onPlayerJoined(lvl, pos);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (world != null) {
            // persist forecasts
            StormStorageManager.saveAll(world);
        }
    }

    // BaseAtmosphereModule callbacks:

    @Override
    protected void doInit(ServerLevel world, BlockPos center, int radius) {
        StormManager.init(world, center, radius);
    }

    @Override
    protected void doPrecompute(ServerLevel world) {
        StormManager.onPrecomputeProfiles(world);
    }

    @Override
    protected void doSwap(ServerLevel world) {
        StormManager.onSwapProfiles(world);
    }

    @Override
    protected void clearAll() {
        // fully clear cached forecasts
        StormManager.clearForecastCache();
    }


    @Override
    protected void runAsync(Runnable task) {
        // uses its own thread‐pool in AsyncAtmosphereService
        AsyncAtmosphereService.runStorm(task);
    }
}
