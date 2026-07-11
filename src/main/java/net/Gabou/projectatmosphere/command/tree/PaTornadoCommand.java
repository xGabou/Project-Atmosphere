package net.Gabou.projectatmosphere.command.tree;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.command.tree.service.NativeTornadoCommandService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaTornadoCommand {
    private PaTornadoCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(boolean simpleCloudsLoaded) {
        if (!simpleCloudsLoaded) {
            return Commands.literal("tornado")
                    .then(Commands.literal("spawn")
                            .requires(source -> source.hasPermission(2))
                            .executes(ctx -> NativeTornadoCommandService.spawn(ctx.getSource())))
                    .then(Commands.literal("remove")
                            .requires(source -> source.hasPermission(2))
                            .executes(ctx -> NativeTornadoCommandService.remove(ctx.getSource(), 256.0D))
                            .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                    .executes(ctx -> NativeTornadoCommandService.remove(
                                            ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")
                                    ))))
                    .then(Commands.literal("clear")
                            .requires(source -> source.hasPermission(2))
                            .executes(ctx -> NativeTornadoCommandService.clear(ctx.getSource())))
                    .then(Commands.literal("list")
                            .executes(ctx -> NativeTornadoCommandService.list(ctx.getSource())));
        }
        return OptionalSimpleCloudsCommands.tornado();
    }
}
