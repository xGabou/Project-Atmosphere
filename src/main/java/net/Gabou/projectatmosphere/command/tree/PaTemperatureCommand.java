package net.Gabou.projectatmosphere.command.tree;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.command.tree.service.CommandTemperatureService;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSuggestions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaTemperatureCommand {
    private PaTemperatureCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("temperature")
                .executes(ctx -> CommandTemperatureService.sendCurrentTemperature(ctx.getSource()))
                .then(Commands.literal("current")
                        .executes(ctx -> CommandTemperatureService.sendCurrentTemperature(ctx.getSource())))
                .then(Commands.literal("week")
                        .executes(ctx -> CommandTemperatureService.sendWeeklyTemperature(ctx.getSource())))
                .then(Commands.literal("day")
                        .executes(ctx -> CommandTemperatureService.sendDayTemperature(ctx.getSource())))
                .then(Commands.literal("season")
                        .executes(ctx -> CommandTemperatureService.sendSeasonTemperature(ctx.getSource())))
                .then(Commands.literal("raw")
                        .executes(ctx -> CommandTemperatureService.sendRawTemperature(ctx.getSource(), null))
                        .then(Commands.argument("biome", StringArgumentType.word())
                                .suggests(PaCommandSuggestions.BIOME_SUGGESTIONS)
                                .executes(ctx -> CommandTemperatureService.sendRawTemperature(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "biome")
                                ))))
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages.success(
                                    ctx.getSource(),
                                    false,
                                    "Temperature commands",
                                    "/pa temperature current",
                                    "/pa temperature week",
                                    "/pa temperature day",
                                    "/pa temperature season",
                                    "/pa temperature raw [biome]"
                            );
                            return 1;
                        }));
    }
}
