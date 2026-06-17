package net.Gabou.projectatmosphere.command.tree;

import net.Gabou.projectatmosphere.command.tree.service.CommandPressureService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaPressureCommand {
    private PaPressureCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("pressure")
                .executes(ctx -> CommandPressureService.sendCurrentPressure(ctx.getSource()))
                .then(Commands.literal("current")
                        .executes(ctx -> CommandPressureService.sendCurrentPressure(ctx.getSource())))
                .then(Commands.literal("week")
                        .executes(ctx -> CommandPressureService.sendWeeklyPressure(ctx.getSource())))
                .then(Commands.literal("raw")
                        .executes(ctx -> CommandPressureService.sendRawPressure(ctx.getSource())))
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages.success(
                                    ctx.getSource(),
                                    false,
                                    "Pressure commands",
                                    "/pa pressure current",
                                    "/pa pressure week",
                                    "/pa pressure raw"
                            );
                            return 1;
                        }));
    }
}
