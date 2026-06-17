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

public final class CommandPressureService {
    private CommandPressureService() {
    }

    public static int sendCurrentPressure(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Pressure forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        long tick = level.getGameTime();
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No pressure forecast region is available at this position."));
            return 0;
        }

        float pressure = ForecastOrchestrator.getCurrentPressure(level, pos, tick);
        PaCommandMessages.success(
                source,
                false,
                "Current pressure",
                "Region: " + region.getKey(),
                "Value: " + UnitFormatter.formatPressure(pressure)
        );
        return 1;
    }

    public static int sendWeeklyPressure(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Pressure forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null || region.getPressure() == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No pressure forecast region is available at this position."));
            return 0;
        }

        StringBuilder message = new StringBuilder("[Project Atmosphere]\nAction: Weekly pressure");
        message.append("\nRegion: ").append(region.getKey());
        appendWeek(message, region.getPressure());
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(message.toString()), false);
        return 1;
    }

    public static int sendRawPressure(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Pressure forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null || region.getPressure() == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No pressure forecast region is available at this position."));
            return 0;
        }

        PaCommandMessages.success(
                source,
                false,
                "Raw pressure",
                "Region: " + region.getKey(),
                "Data: " + Arrays.deepToString(region.getPressure())
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
                    .append(UnitFormatter.formatPressure(range[0]))
                    .append(" to ")
                    .append(UnitFormatter.formatPressure(range[1]));
        }
    }
}
