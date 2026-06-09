package net.Gabou.projectatmosphere.command.tree;

import net.Gabou.projectatmosphere.command.tree.service.CommandWindService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaWindCommand {
    private PaWindCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("wind")
                .executes(ctx -> CommandWindService.sendCurrentWind(ctx.getSource()))
                .then(Commands.literal("current")
                        .executes(ctx -> CommandWindService.sendCurrentWind(ctx.getSource())))
                .then(Commands.literal("week")
                        .executes(ctx -> CommandWindService.sendWeeklyWind(ctx.getSource())))
                .then(Commands.literal("raw")
                        .executes(ctx -> CommandWindService.sendRawWind(ctx.getSource())))
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages.success(
                                    ctx.getSource(),
                                    false,
                                    "Wind commands",
                                    "/pa wind current",
                                    "/pa wind week",
                                    "/pa wind raw"
                            );
                            return 1;
                        }));
    }
}
