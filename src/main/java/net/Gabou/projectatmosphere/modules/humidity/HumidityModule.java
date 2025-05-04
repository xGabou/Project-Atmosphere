package net.Gabou.projectatmosphere.modules.humidity;

import net.Gabou.projectatmosphere.command.DebugAtmoCommand;
import net.Gabou.projectatmosphere.command.SpawnCloudCommand;
import net.Gabou.projectatmosphere.modules.humidity.Command.HumidityCommand;
import net.Gabou.projectatmosphere.modules.humidity.manager.HumidityManager;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityStorageManager;
import net.Gabou.projectatmosphere.modules.temperature.TemperatureModule;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
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

@Mod.EventBusSubscriber(modid = "projectatmosphere")
public class HumidityModule {

    private static final int DEFAULT_RADIUS = 250;

    public static void init() {
    }

    public static void onServerStarted(ServerLevel world) {


        HumidityStorageManager.loadAll(world);
        BlockPos center = world.getSharedSpawnPos();
        HumidityManager.init(world, center);
    }

    private static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level) {
            HumidityManager.onPlayerJoined(level, player.blockPosition());
        }
    }

    public static void onServerStopping(ServerLevel event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (world != null) {
            HumidityStorageManager.saveAll(world);
        }
    }

    public static void precompute(ServerLevel world) {
        HumidityManager.onPrecomputeProfiles(world);
    }

    public static void swap(ServerLevel world) {
        HumidityManager.onSwapProfiles(world);
    }

    public static void clear(ServerLevel world) {
        HumidityManager.clearForecastCache(world, world.getSharedSpawnPos());
    }

    public static void onServerStarting(ServerLevel world, BlockPos center) {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        HumidityCommand.register(event.getDispatcher());
    }
}
