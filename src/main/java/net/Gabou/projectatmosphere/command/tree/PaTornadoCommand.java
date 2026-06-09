package net.Gabou.projectatmosphere.command.tree;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.command.tree.service.CommandTornadoService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaTornadoCommand {
    private PaTornadoCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(boolean simpleCloudsLoaded) {
        return Commands.literal("tornado")
                .then(Commands.literal("spawn")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandTornadoService.spawnTornado(ctx.getSource(), false))
                        .then(Commands.literal("no_cloud")
                                .executes(ctx -> CommandTornadoService.spawnTornado(ctx.getSource(), true))))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandTornadoService.removeTornado(ctx.getSource(), 256.0D))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                .executes(ctx -> CommandTornadoService.removeTornado(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius")
                                ))))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandTornadoService.clearTornadoes(ctx.getSource())))
                .then(Commands.literal("list")
                        .executes(ctx -> CommandTornadoService.sendTornadoList(ctx.getSource())))
                .then(Commands.literal("info")
                        .executes(ctx -> CommandTornadoService.sendTornadoInfo(ctx.getSource())))
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages.success(
                                    ctx.getSource(),
                                    false,
                                    "Tornado commands",
                                    "/pa tornado spawn",
                                    "/pa tornado spawn no_cloud",
                                    "/pa tornado remove [radius]",
                                    "/pa tornado clear",
                                    "/pa tornado list",
                                    "/pa tornado info"
                            );
                            return 1;
                        }));
    }
}
