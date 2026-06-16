package net.Gabou.projectatmosphere.command.tree;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
                        .then(Commands.literal("page")
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> CommandDebugService.runVerificationPage(
                                                ctx.getSource(),
                                                false,
                                                IntegerArgumentType.getInteger(ctx, "page")
                                        ))))
                        .then(Commands.literal("snapshot")
                                .executes(ctx -> CommandDebugService.runVerification(ctx.getSource(), true))
                                .then(Commands.literal("page")
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(ctx -> CommandDebugService.runVerificationPage(
                                                        ctx.getSource(),
                                                        true,
                                                IntegerArgumentType.getInteger(ctx, "page")
                                                ))))))
                .then(Commands.literal("cyclone")
                        .executes(ctx -> CommandDebugService.runCycloneCurrent(ctx.getSource()))
                        .then(Commands.literal("current")
                                .executes(ctx -> CommandDebugService.runCycloneCurrent(ctx.getSource())))
                        .then(Commands.literal("region")
                                .executes(ctx -> CommandDebugService.runCycloneCurrent(ctx.getSource())))
                        .then(Commands.literal("nearest")
                                .executes(ctx -> CommandDebugService.runCycloneNearest(ctx.getSource())))
                        .then(Commands.literal("list")
                                .executes(ctx -> CommandDebugService.runCycloneList(ctx.getSource()))))
                .then(Commands.literal("pressure")
                        .executes(ctx -> CommandDebugService.runPressureCurrent(ctx.getSource()))
                        .then(Commands.literal("current")
                                .executes(ctx -> CommandDebugService.runPressureCurrent(ctx.getSource())))
                        .then(Commands.literal("region")
                                .executes(ctx -> CommandDebugService.runPressureCurrent(ctx.getSource()))))
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> CommandDebugService.setDebugMode(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"))));
    }
}
