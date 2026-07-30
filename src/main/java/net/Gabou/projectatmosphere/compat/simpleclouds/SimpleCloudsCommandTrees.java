package net.Gabou.projectatmosphere.compat.simpleclouds;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.command.tree.service.CommandHurricaneService;
import net.Gabou.projectatmosphere.command.tree.service.CommandTornadoService;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSuggestions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Command tree loaded only while the Simple Clouds backend is present. */
public final class SimpleCloudsCommandTrees {
    private SimpleCloudsCommandTrees() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tornado() {
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
                                        ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")
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
                            PaCommandMessages.success(
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

    public static LiteralArgumentBuilder<CommandSourceStack> hurricane() {
        return Commands.literal("hurricane")
                .then(Commands.literal("spawn")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("category", IntegerArgumentType.integer(1, 5))
                                .suggests(PaCommandSuggestions.HURRICANE_CATEGORY_SUGGESTIONS)
                                .executes(ctx -> CommandHurricaneService.spawnHurricane(
                                        ctx.getSource(), IntegerArgumentType.getInteger(ctx, "category")
                                ))))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandHurricaneService.removeHurricane(ctx.getSource(), 256.0D))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                .executes(ctx -> CommandHurricaneService.removeHurricane(
                                        ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")
                                ))))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandHurricaneService.clearHurricanes(ctx.getSource())))
                .then(Commands.literal("list")
                        .executes(ctx -> CommandHurricaneService.sendHurricaneList(ctx.getSource())))
                .then(Commands.literal("info")
                        .executes(ctx -> CommandHurricaneService.sendHurricaneInfo(ctx.getSource())))
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            PaCommandMessages.success(
                                    ctx.getSource(),
                                    false,
                                    "Hurricane commands",
                                    "/pa hurricane spawn <category>",
                                    "/pa hurricane remove [radius]",
                                    "/pa hurricane clear",
                                    "/pa hurricane list",
                                    "/pa hurricane info"
                            );
                            return 1;
                        }));
    }

    public static void addAliases(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("spawnTornado")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> CommandTornadoService.spawnTornado(ctx.getSource(), false)));
        root.then(Commands.literal("spawntornadoes")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> CommandTornadoService.spawnTornado(ctx.getSource(), false)));
        root.then(Commands.literal("spawnTornadoNoClouds")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> CommandTornadoService.spawnTornado(ctx.getSource(), true)));
        root.then(Commands.literal("cleartornadoes")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> CommandTornadoService.clearTornadoes(ctx.getSource())));
        root.then(Commands.literal("removetornado")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> CommandTornadoService.removeTornado(ctx.getSource(), 256.0D))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                        .executes(ctx -> CommandTornadoService.removeTornado(
                                ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")
                        ))));

        root.then(Commands.literal("spawnHurricane")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("category", IntegerArgumentType.integer(1, 5))
                        .executes(ctx -> CommandHurricaneService.spawnHurricane(
                                ctx.getSource(), IntegerArgumentType.getInteger(ctx, "category")
                        ))));
        root.then(Commands.literal("clearhurricanes")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> CommandHurricaneService.clearHurricanes(ctx.getSource())));
        root.then(Commands.literal("removehurricane")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> CommandHurricaneService.removeHurricane(ctx.getSource(), 256.0D))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                        .executes(ctx -> CommandHurricaneService.removeHurricane(
                                ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")
                        ))));
    }
}
