package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureGenerator;
import net.Gabou.projectatmosphere.util.UnitFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

public final class CommandTemperatureService {
    private CommandTemperatureService() {
    }

    public static int sendCurrentTemperature(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Temperature forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        long tick = level.getGameTime();
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No temperature forecast region is available at this position."));
            return 0;
        }

        float temperature = ForecastOrchestrator.getCurrentTemperature(level, pos, tick);
        PaCommandMessages.success(
                source,
                false,
                "Current temperature",
                "Region: " + region.getKey(),
                "Biome: " + PaCommandSupport.currentBiomeId(level, pos),
                "Value: " + UnitFormatter.formatTemperature(temperature)
        );
        return 1;
    }

    public static int sendWeeklyTemperature(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Temperature forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null || region.getTemperature() == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No temperature forecast region is available at this position."));
            return 0;
        }

        StringBuilder message = new StringBuilder("[Project Atmosphere]\nAction: Weekly temperature");
        message.append("\nRegion: ").append(region.getKey());
        appendWeek(message, region.getTemperature());
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(message.toString()), false);
        return 1;
    }

    public static int sendDayTemperature(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Temperature forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        var regionKey = net.Gabou.projectatmosphere.util.RegionInstanceKey.from(pos);
        float[] profile = TemperatureCommandHelper.getDayProfile(regionKey);
        if (profile.length == 0) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No daily temperature profile is available at this position."));
            return 0;
        }

        PaCommandMessages.success(
                source,
                false,
                "Daily temperature profile",
                "Region: " + regionKey,
                "Data: " + Arrays.toString(profile)
        );
        return 1;
    }

    public static int sendSeasonTemperature(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Temperature forecasts are only available in the Overworld."));
            return 0;
        }

        PaCommandMessages.success(
                source,
                false,
                "Season state",
                "Value: " + TemperatureCommandHelper.getCurrentSubSeason(level)
        );
        return 1;
    }

    public static int sendRawTemperature(CommandSourceStack source, String biomeToken) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Temperature forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        ResourceLocation biome = PaCommandSupport.parseBiomeToken(source, level, pos, biomeToken);
        if (biome == null) {
            return 0;
        }
        float[][] week = TemperatureGenerator.generateWeekForecast(level, pos, biome);
        float baseTemp = level.getBiome(pos).value().getBaseTemperature();
        float current = ForecastOrchestrator.getCurrentTemperature(level, pos, level.getGameTime());

        PaCommandMessages.success(
                source,
                false,
                "Raw temperature",
                "Biome: " + biome,
                "Position: " + pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                "Biome base: " + UnitFormatter.formatTemperature(baseTemp),
                "Current value: " + UnitFormatter.formatTemperature(current),
                "Week: " + Arrays.deepToString(week)
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
                    .append(UnitFormatter.formatTemperature(range[0]))
                    .append(" to ")
                    .append(UnitFormatter.formatTemperature(range[1]));
        }
    }
}
