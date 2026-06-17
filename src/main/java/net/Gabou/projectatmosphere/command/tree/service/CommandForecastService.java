package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.modules.temperature.spike.SpikeManager;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.Gabou.projectatmosphere.util.UnitFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class CommandForecastService {
    private CommandForecastService() {
    }

    public static int sendCurrentForecast(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        long tick = level.getGameTime();
        RegionInstanceKey regionKey = RegionInstanceKey.from(pos);
        ResourceLocation biome = PaCommandSupport.currentBiomeId(level, pos);
        float temperature = ForecastOrchestrator.getCurrentTemperature(level, pos, tick);
        float humidity = ForecastOrchestrator.getCurrentHumidity(level, pos, tick);
        float pressure = ForecastOrchestrator.getCurrentPressure(level, pos, tick);
        WindVector wind = ForecastOrchestrator.getWind(regionKey, tick);
        float stormChance = ForecastOrchestrator.getCurrentStormChance(regionKey, tick);

        PaCommandMessages.success(
                source,
                false,
                "Current forecast",
                "Region: " + regionKey,
                "Biome: " + biome,
                "Temperature: " + UnitFormatter.formatTemperature(temperature),
                "Humidity: " + UnitFormatter.formatHumidity(humidity),
                "Pressure: " + UnitFormatter.formatPressure(pressure),
                "Wind: " + PaCommandSupport.formatWind(wind),
                "Storm chance: " + String.format(java.util.Locale.ROOT, "%.2f", stormChance)
        );
        return 1;
    }

    public static int sendWeeklyForecast(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Forecasts are only available in the Overworld."));
            return 0;
        }

        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        RegionInstanceKey regionKey = RegionInstanceKey.from(pos);
        var region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("No forecast region is available at this position."));
            return 0;
        }

        StringBuilder message = new StringBuilder("[Project Atmosphere]\nAction: Weekly forecast");
        message.append("\nRegion: ").append(regionKey);
        message.append("\nTemperature:");
        appendWeekLines(message, region.getTemperature(), UnitFormatter::formatTemperature);
        message.append("\nHumidity:");
        appendWeekLines(message, region.getHumidity(), UnitFormatter::formatHumidity);
        message.append("\nPressure:");
        appendWeekLines(message, region.getPressure(), UnitFormatter::formatPressure);
        message.append("\nWind:");
        appendWindWeek(message, region.getWind());

        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(message.toString()), false);
        return 1;
    }

    public static int regenerateForecast(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        AtmosphereManager.onRegenerate(level);
        PaCommandMessages.success(
                source,
                true,
                "Forecast regeneration requested",
                "Result: weather systems are rebuilding"
        );
        return 1;
    }

    public static int resetSpikes(CommandSourceStack source) {
        SpikeManager.clearSpikeCache(source.getLevel());
        PaCommandMessages.success(
                source,
                true,
                "Spike cache reset",
                "Result: spike state cleared"
        );
        return 1;
    }

    private static void appendWeekLines(StringBuilder message, float[][] week, java.util.function.Function<Float, String> formatter) {
        if (week == null || week.length == 0) {
            message.append("\n  no data");
            return;
        }
        for (int day = 0; day < week.length; day++) {
            float[] profile = week[day];
            if (profile == null || profile.length == 0) {
                message.append("\n  Day ").append(day + 1).append(": no data");
                continue;
            }
            message.append("\n  Day ").append(day + 1).append(": ");
            for (int i = 0; i < profile.length; i++) {
                message.append(formatter.apply(profile[i]));
                if (i < profile.length - 1) {
                    message.append(", ");
                }
            }
        }
    }

    private static void appendWindWeek(StringBuilder message, net.Gabou.projectatmosphere.modules.core.WindVector[] week) {
        if (week == null || week.length == 0) {
            message.append("\n  no data");
            return;
        }
        for (int day = 0; day < week.length; day++) {
            net.Gabou.projectatmosphere.modules.core.WindVector wind = week[day];
            if (wind == null) {
                message.append("\n  Day ").append(day + 1).append(": no data");
                continue;
            }
            message.append("\n  Day ").append(day + 1).append(": ")
                    .append(PaCommandSupport.formatWind(wind));
        }
    }
}
