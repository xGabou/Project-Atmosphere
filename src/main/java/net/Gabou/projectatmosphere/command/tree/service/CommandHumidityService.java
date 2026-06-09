package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.util.UnitFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;

public final class CommandHumidityService {
    private CommandHumidityService() {
    }

    public static int sendCurrentHumidity(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Humidity forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        long tick = level.getGameTime();
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No humidity forecast region is available at this position."));
            return 0;
        }

        float humidity = ForecastOrchestrator.getCurrentHumidity(level, pos, tick);
        PaCommandMessages.success(
                source,
                false,
                "Current humidity",
                "Region: " + region.getKey(),
                "Value: " + UnitFormatter.formatHumidity(humidity)
        );
        return 1;
    }

    public static int sendWeeklyHumidity(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Humidity forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null || region.getHumidity() == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No humidity forecast region is available at this position."));
            return 0;
        }

        StringBuilder message = new StringBuilder("[Project Atmosphere]\nAction: Weekly humidity");
        message.append("\nRegion: ").append(region.getKey());
        appendWeek(message, region.getHumidity());
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(message.toString()), false);
        return 1;
    }

    public static int sendRawHumidity(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Humidity forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null || region.getHumidity() == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No humidity forecast region is available at this position."));
            return 0;
        }

        PaCommandMessages.success(
                source,
                false,
                "Raw humidity",
                "Region: " + region.getKey(),
                "Data: " + Arrays.deepToString(region.getHumidity())
        );
        return 1;
    }

    private static void appendWeek(StringBuilder message, float[][] week) {
        for (int day = 0; day < week.length; day++) {
            float[] range = week[day];
            if (range == null || range.length < 2) {
                message.append("\n  Day ").append(day + 1).append(": no data");
                continue;
            }
            message.append("\n  Day ").append(day + 1).append(": ")
                    .append(UnitFormatter.formatHumidity(range[0]))
                    .append(" to ")
                    .append(UnitFormatter.formatHumidity(range[1]));
        }
    }
}
