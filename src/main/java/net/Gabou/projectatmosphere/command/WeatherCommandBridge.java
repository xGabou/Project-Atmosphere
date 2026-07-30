package net.Gabou.projectatmosphere.command;

import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.command.tree.service.CommandCloudService;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

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
        String cloudId = switch (kind) {
            case CLEAR -> AtmosphereCloudServices.isSimpleCloudsLoaded()
                    ? CloudLibrary.getCloudIdFromSeverity(1)
                    : CloudTypeRegistry.getClearWeatherCloudId();
            case RAIN -> AtmosphereCloudServices.isSimpleCloudsLoaded()
                    ? CloudLibrary.getRandomRainCloud(1, false)
                    : CloudTypeRegistry.getRandomRainCloud(1);
            case THUNDER -> AtmosphereCloudServices.isSimpleCloudsLoaded()
                    ? CloudLibrary.getRandomThunderCloud(1)
                    : CloudTypeRegistry.getRandomThunderCloud(1);
        };

        if (!CommandCloudService.spawnWeatherCloudAtSource(source, cloudId)) {
            source.sendFailure(Component.literal("Failed to spawn weather cloud."));
            return 0;
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
