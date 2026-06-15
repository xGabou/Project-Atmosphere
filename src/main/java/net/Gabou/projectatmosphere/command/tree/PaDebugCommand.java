package net.Gabou.projectatmosphere.command.tree;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.command.tree.service.CommandDebugService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaDebugCommand {
    private PaDebugCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug")
                .then(Commands.literal("on")
                        .executes(ctx -> CommandDebugService.setDebugMode(ctx.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(ctx -> CommandDebugService.setDebugMode(ctx.getSource(), false)))
                .then(Commands.literal("verify")
                        .executes(ctx -> CommandDebugService.runVerification(ctx.getSource(), false))
                        .then(Commands.literal("snapshot")
                                .executes(ctx -> CommandDebugService.runVerification(ctx.getSource(), true))))
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> CommandDebugService.setDebugMode(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"))));
    }
}
