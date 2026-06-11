package net.Gabou.projectatmosphere.command;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.clouds.network.CloudRegionSyncManager;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class WeatherCommandBridge {

    private WeatherCommandBridge() {
    }

    public static int setClear(CommandSourceStack source, int duration) {
        return apply(source, WeatherKind.CLEAR);
    }

    public static int setRain(CommandSourceStack source, int duration) {
        return apply(source, WeatherKind.RAIN);
    }

    public static int setThunder(CommandSourceStack source, int duration) {
        return apply(source, WeatherKind.THUNDER);
    }

    private static int apply(CommandSourceStack source, WeatherKind kind) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(Component.literal("Weather clouds can only be spawned in the Overworld."));
            return 0;
        }
        if (!net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport.requireSimpleClouds(source)) {
            return 0;
        }

        BlockPos sourcePos = BlockPos.containing(source.getPosition());
        RegionInstanceKey regionKey = RegionInstanceKey.from(sourcePos);
        String cloudId = switch (kind) {
            case CLEAR -> CloudLibrary.getCloudIdFromSeverity(1);
            case RAIN -> CloudLibrary.getRandomRainCloud(1, false);
            case THUNDER -> CloudLibrary.getRandomThunderCloud(1);
        };

        CloudRegion region = SimpleCloudsCompat.spawnCloudInRegion(
                cloudId,
                regionKey,
                level,
                null,
                net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(1.0F, 0.0F)
        );
        if (region == null) {
            source.sendFailure(Component.literal("Failed to spawn weather cloud."));
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            CloudRegionSyncManager.syncPlayer(player);
        }

        source.sendSuccess(() -> Component.literal("Spawned " + kind.messageName + " cloud in your region."), true);
        return 1;
    }

    private enum WeatherKind {
        CLEAR("clear"),
        RAIN("rain"),
        THUNDER("thunder");

        private final String messageName;

        WeatherKind(String messageName) {
            this.messageName = messageName;
        }
    }
}
