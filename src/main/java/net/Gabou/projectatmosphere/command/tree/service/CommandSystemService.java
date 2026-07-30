package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphereStatusSyncManager;
import net.Gabou.projectatmosphere.clouds.network.CloudRegionSyncManager;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class CommandSystemService {
    private CommandSystemService() {
    }

    public static int sendCpuInfo(CommandSourceStack source) {
        int cores = Runtime.getRuntime().availableProcessors();
        long maxMemoryMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        String mode;
        if (cores <= 6) {
            mode = "Shared Executor (1 thread pool)";
        } else if (cores <= 10) {
            mode = "Two Executor Groups (shared in pairs)";
        } else {
            mode = "Four Separate Executors";
        }

        PaCommandMessages.success(
                source,
                false,
                "CPU info",
                "Logical cores: " + cores,
                "Max memory: " + maxMemoryMb + " MB",
                "Current async mode: " + mode
        );
        return 1;
    }

    public static int reloadSystem(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        AtmosphereManager.onServerStarted(level);
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            AtmosphereStatusSyncManager.syncPlayer(player);
            CloudRegionSyncManager.syncPlayer(player);
            AtmosphereCloudServices.get().syncSevereWeather(player);
        }
        PaCommandMessages.success(
                source,
                true,
                "System reloaded",
                "Result: runtime state rebuilt"
        );
        return 1;
    }

    public static int syncInternal(CommandSourceStack source) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "System sync is only available to players.");
        if (player == null) {
            return 0;
        }
        AtmosphereStatusSyncManager.syncPlayer(player);
        CloudRegionSyncManager.syncPlayer(player);
        AtmosphereCloudServices.get().syncSevereWeather(player);
        PaCommandMessages.success(
                source,
                false,
                "System sync sent",
                "Result: runtime state pushed to your client"
        );
        return 1;
    }
}
