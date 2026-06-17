package net.Gabou.projectatmosphere.command.tree;

import net.Gabou.projectatmosphere.command.tree.service.CommandForecastService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaForecastCommand {
    private PaForecastCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("forecast")
                .executes(ctx -> CommandForecastService.sendCurrentForecast(ctx.getSource()))
                .then(Commands.literal("current")
                        .executes(ctx -> CommandForecastService.sendCurrentForecast(ctx.getSource())))
                .then(Commands.literal("week")
                        .executes(ctx -> CommandForecastService.sendWeeklyForecast(ctx.getSource())))
                .then(Commands.literal("regenerate")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandForecastService.regenerateForecast(ctx.getSource())))
                .then(Commands.literal("reset")
                        .then(Commands.literal("spikes")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> CommandForecastService.resetSpikes(ctx.getSource()))))
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages.success(
                                    ctx.getSource(),
                                    false,
                                    "Forecast commands",
                                    "/pa forecast current",
                                    "/pa forecast week",
                                    "/pa forecast regenerate",
                                    "/pa forecast reset spikes"
                            );
                            return 1;
                        }));
    }
}
