package net.Gabou.projectatmosphere.command.tree;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.command.tree.service.CommandHurricaneService;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSuggestions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaHurricaneCommand {
    private PaHurricaneCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(boolean simpleCloudsLoaded) {
        return Commands.literal("hurricane")
                .then(Commands.literal("spawn")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("category", IntegerArgumentType.integer(1, 5))
                                .suggests(PaCommandSuggestions.HURRICANE_CATEGORY_SUGGESTIONS)
                                .executes(ctx -> CommandHurricaneService.spawnHurricane(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "category")
                                ))))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandHurricaneService.removeHurricane(ctx.getSource(), 256.0D))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                .executes(ctx -> CommandHurricaneService.removeHurricane(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius")
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
                            net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages.success(
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
}
