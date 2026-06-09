package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.util.UnitFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;

public final class CommandWindService {
    private CommandWindService() {
    }

    public static int sendCurrentWind(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Wind forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        RegionInstanceKey key = RegionInstanceKey.from(pos);
        long tick = level.getGameTime();
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No wind forecast region is available at this position."));
            return 0;
        }

        WindVector wind = ForecastOrchestrator.getWind(key, tick);
        PaCommandMessages.success(
                source,
                false,
                "Current wind",
                "Region: " + key,
                "Wind: " + PaCommandSupport.formatWind(wind),
                "Gust: " + UnitFormatter.formatWindSpeed(wind.gustSpeed())
        );
        return 1;
    }

    public static int sendWeeklyWind(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Wind forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null || region.getWind() == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No wind forecast region is available at this position."));
            return 0;
        }

        StringBuilder message = new StringBuilder("[Project Atmosphere]\nAction: Weekly wind");
        message.append("\nRegion: ").append(region.getKey());
        appendWeek(message, region.getWind());
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(message.toString()), false);
        return 1;
    }

    public static int sendRawWind(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Wind forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null || region.getWind() == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No wind forecast region is available at this position."));
            return 0;
        }

        PaCommandMessages.success(
                source,
                false,
                "Raw wind",
                "Region: " + region.getKey(),
                "Data: " + Arrays.toString(region.getWind())
        );
        return 1;
    }

    private static void appendWeek(StringBuilder message, WindVector[] week) {
        for (int day = 0; day < week.length; day++) {
            WindVector wind = week[day];
            if (wind == null) {
                message.append("\n  Day ").append(day + 1).append(": no data");
                continue;
            }
            message.append("\n  Day ").append(day + 1).append(": ")
                    .append(PaCommandSupport.formatWind(wind))
                    .append(", gust ")
                    .append(UnitFormatter.formatWindSpeed(wind.gustSpeed()));
        }
    }
}
