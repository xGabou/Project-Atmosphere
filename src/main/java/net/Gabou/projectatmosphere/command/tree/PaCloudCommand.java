package net.Gabou.projectatmosphere.command.tree;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.command.tree.service.CommandCloudService;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSuggestions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaCloudCommand {
    private PaCloudCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(boolean simpleCloudsLoaded) {
        return Commands.literal("cloud")
                .then(Commands.literal("spawn")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandCloudService.spawnCloudField(ctx.getSource()))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(PaCommandSuggestions.CLOUD_TYPE_SUGGESTIONS)
                                .executes(ctx -> CommandCloudService.spawnCloudField(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("rain")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandCloudService.spawnRain(ctx.getSource(), 1))
                        .then(Commands.argument("intensity", IntegerArgumentType.integer(1, 2))
                                .executes(ctx -> CommandCloudService.spawnRain(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "intensity")
                                ))))
                .then(Commands.literal("thunder")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandCloudService.spawnThunder(ctx.getSource(), 1))
                        .then(Commands.argument("intensity", IntegerArgumentType.integer(1, 2))
                                .executes(ctx -> CommandCloudService.spawnThunder(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "intensity")
                                ))))
                .then(Commands.literal("snowstorm")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandCloudService.spawnSnowstorm(ctx.getSource(), false))
                        .then(Commands.argument("overwrite", BoolArgumentType.bool())
                                .executes(ctx -> CommandCloudService.spawnSnowstorm(
                                        ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "overwrite")
                                ))))
                .then(Commands.literal("list")
                        .executes(ctx -> CommandCloudService.sendCloudFieldList(ctx.getSource())))
                .then(Commands.literal("count")
                        .executes(ctx -> CommandCloudService.sendCloudCount(ctx.getSource())))
                .then(Commands.literal("stats")
                        .executes(ctx -> CommandCloudService.sendCloudFieldStats(ctx.getSource())))
                .then(Commands.literal("evolution")
                        .executes(ctx -> CommandCloudService.sendCloudFieldEvolution(ctx.getSource())))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandCloudService.clearCloudFields(ctx.getSource()))
                        .then(Commands.literal("inactive")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> CommandCloudService.clearInactiveClouds(ctx.getSource()))))
                .then(Commands.literal("sync")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandCloudService.syncClouds(ctx.getSource())))
                .then(Commands.literal("freeze")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> CommandCloudService.setCloudMovementFrozen(
                                        ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "enabled")
                                ))))
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages.success(
                                    ctx.getSource(),
                                    false,
                                    "Cloud commands",
                                    "/pa cloud spawn",
                                    "/pa cloud spawn [id]",
                                    "/pa cloud rain [intensity]",
                                    "/pa cloud thunder [intensity]",
                                    "/pa cloud snowstorm [overwrite]",
                                    "/pa cloud list",
                                    "/pa cloud count",
                                    "/pa cloud stats",
                                    "/pa cloud evolution",
                                    "/pa cloud render on|off|status",
                                    "/pa cloud render quality <low|low_24|low_24_steps|medium|high|ultra>",
                                    "/pa cloud clear",
                                    "/pa cloud clear inactive (legacy region cleanup)",
                                    "/pa cloud freeze <true|false>",
                                    "/pa cloud sync"
                            );
                            return 1;
                        }));
    }
}
