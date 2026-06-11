package net.Gabou.projectatmosphere.command.tree.service;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.clouds.network.CloudRegionSyncManager;
import net.Gabou.projectatmosphere.clouds.simulation.CloudRegionManager;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class CommandCloudService {
    private CommandCloudService() {
    }

    public static int spawnCloud(CommandSourceStack source, String cloudId) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Cloud spawning is only available to players.");
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(Component.literal("Cloud spawning is only available in the Overworld."));
            return 0;
        }

        BlockPos pos = player.blockPosition();
        RegionInstanceKey regionKey = RegionInstanceKey.from(pos);
        WindVectorApi.WindSample sample = WindVectorApi.getOrFallback(regionKey, level.getGameTime());
        net.Gabou.projectatmosphere.modules.core.WindVector wind =
                net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(
                        sample.speedMps(),
                        (float) Math.toRadians(sample.directionDeg())
                );

        if (!PaCommandSupport.requireSimpleClouds(source)) {
            return 0;
        }

        CloudRegion region = SimpleCloudsCompat.spawnCloudInRegion(cloudId, regionKey, level, null, wind);
        if (region != null) {
            CloudRegionSyncManager.syncPlayer(player);
            PaCommandMessages.success(
                    source,
                    true,
                    "Cloud spawned",
                    "Cloud: " + cloudId,
                    "Region: " + regionKey,
                    "Wind: " + PaCommandSupport.formatWind(wind),
                    "Result: Simple Clouds region created"
            );
            return 1;
        }

        source.sendFailure(Component.literal("Failed to create Simple Clouds cloud '" + cloudId + "'."));
        return 0;
    }

    public static int spawnRain(CommandSourceStack source, int intensity) {
        return spawnCloud(source, CloudLibrary.getRandomRainCloud(intensity, true));
    }

    public static int spawnThunder(CommandSourceStack source, int intensity) {
        return spawnCloud(source, CloudLibrary.getRandomThunderCloud(intensity));
    }

    public static int spawnSnowstorm(CommandSourceStack source, boolean overwrite) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Snowstorm spawning is only available to players.");
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(Component.literal("Snowstorm clouds can only be spawned in the Overworld."));
            return 0;
        }
        if (net.Gabou.projectatmosphere.seasons.SeasonTimeHelper.stage(level) != net.Gabou.projectatmosphere.seasons.SeasonStage.WINTER && !overwrite) {
            source.sendFailure(Component.literal("It is not winter. Use overwrite to force the spawn."));
            return 0;
        }

        return spawnCloud(source, CloudLibrary.getSnowstormCloudId());
    }

    public static int sendCloudCount(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int storedCount = CloudRegionManager.getInstance().getCloudRegionCount(level);
        int activeRenderDataCount = CloudRegionManager.getInstance().getActiveRenderData(level).size();
        PaCommandMessages.success(
                source,
                false,
                "Cloud count",
                "Stored: " + storedCount,
                "Active render data: " + activeRenderDataCount
        );
        return 1;
    }

    public static int sendCloudList(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<String> lines = CloudRegionManager.getInstance().describeCloudRegions(level);
        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Project Atmosphere]\nAction: Cloud list\nResult: no saved cloud regions"), false);
            return 1;
        }

        StringBuilder message = new StringBuilder("[Project Atmosphere]\nAction: Cloud list");
        message.append("\nSaved regions: ").append(lines.size());
        int limit = Math.min(lines.size(), 8);
        for (int i = 0; i < limit; i++) {
            message.append("\n").append(i + 1).append(". ").append(lines.get(i));
        }
        if (lines.size() > limit) {
            message.append("\n... ").append(lines.size() - limit).append(" more");
        }
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    public static int clearClouds(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        CloudRegionManager.getInstance().clearCloudRegions(level);
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            CloudRegionSyncManager.syncPlayer(player);
        }
        PaCommandMessages.success(source, true, "Cloud regions cleared");
        return 1;
    }

    public static int clearInactiveClouds(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int removed = CloudRegionManager.getInstance().clearInactiveCloudRegions(level);
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            CloudRegionSyncManager.syncPlayer(player);
        }
        PaCommandMessages.success(
                source,
                true,
                "Inactive cloud regions cleared",
                "Removed: " + removed
        );
        return 1;
    }

    public static int syncClouds(CommandSourceStack source) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Cloud sync is only available to players.");
        if (player == null) {
            return 0;
        }
        CloudRegionSyncManager.syncPlayer(player);
        PaCommandMessages.success(source, false, "Cloud sync sent");
        return 1;
    }

}
