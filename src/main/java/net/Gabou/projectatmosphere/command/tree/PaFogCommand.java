package net.Gabou.projectatmosphere.command.tree;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.command.tree.service.CommandFogService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaFogCommand {
    private PaFogCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("fog")
                .then(Commands.literal("info")
                        .executes(ctx -> CommandFogService.sendFogInfo(ctx.getSource())))
                .then(Commands.literal("spawn")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandFogService.spawnFog(ctx.getSource(), 0.85F, 30))
                        .then(Commands.argument("strength", FloatArgumentType.floatArg(0.05F, 1.0F))
                                .executes(ctx -> CommandFogService.spawnFog(
                                        ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "strength"),
                                        30
                                ))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                                        .executes(ctx -> CommandFogService.spawnFog(
                                                ctx.getSource(),
                                                FloatArgumentType.getFloat(ctx, "strength"),
                                                IntegerArgumentType.getInteger(ctx, "seconds")
                                        )))))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandFogService.clearFog(ctx.getSource())))
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages.success(
                                    ctx.getSource(),
                                    false,
                                    "Fog commands",
                                    "/pa fog info",
                                    "/pa fog spawn [strength] [seconds]",
                                    "/pa fog clear"
                            );
                            return 1;
                        }));
    }
}
