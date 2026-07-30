package net.Gabou.projectatmosphere.command.tree;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.command.tree.service.CommandCloudService;
import net.Gabou.projectatmosphere.command.tree.service.CommandDebugService;
import net.Gabou.projectatmosphere.command.tree.service.CommandForecastService;
import net.Gabou.projectatmosphere.command.tree.service.CommandFogService;
import net.Gabou.projectatmosphere.command.tree.service.CommandSystemService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PaCommandAliases {
    private PaCommandAliases() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(boolean simpleCloudsLoaded) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("weatherdebug")
                .then(Commands.literal("forecast")
                        .executes(ctx -> CommandForecastService.sendCurrentForecast(ctx.getSource()))
                        .then(Commands.literal("regenerate")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> CommandForecastService.regenerateForecast(ctx.getSource())))
                        .then(Commands.literal("resetSpikes")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> CommandForecastService.resetSpikes(ctx.getSource())))
                        .then(Commands.argument("biome", StringArgumentType.word())
                                .executes(ctx -> CommandForecastService.sendCurrentForecast(ctx.getSource()))));

        root.then(Commands.literal("cloud")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> CommandCloudService.spawnCloud(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));

        root.then(Commands.literal("clouds")
                .then(Commands.literal("list").executes(ctx -> CommandCloudService.sendCloudList(ctx.getSource())))
                .then(Commands.literal("count").executes(ctx -> CommandCloudService.sendCloudCount(ctx.getSource())))
                .then(Commands.literal("stats")
                        .executes(ctx -> CommandCloudService.sendCloudFieldStats(ctx.getSource())))
                .then(Commands.literal("evolution")
                        .executes(ctx -> CommandCloudService.sendCloudFieldEvolution(ctx.getSource())))
                .then(Commands.literal("spawnField")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandCloudService.spawnDebugCloudField(ctx.getSource())))
                .then(Commands.literal("clearFields")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandCloudService.clearCloudFields(ctx.getSource())))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandCloudService.clearClouds(ctx.getSource())))
                .then(Commands.literal("clearInactive")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandCloudService.clearInactiveClouds(ctx.getSource())))
                .then(Commands.literal("sync")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> CommandCloudService.syncClouds(ctx.getSource()))));

        root.then(Commands.literal("rain")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> CommandCloudService.spawnRain(ctx.getSource(), 1))
                .then(Commands.argument("intensity", IntegerArgumentType.integer(1, 2))
                        .executes(ctx -> CommandCloudService.spawnRain(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "intensity")
                        ))));

        root.then(Commands.literal("thunder")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> CommandCloudService.spawnThunder(ctx.getSource(), 1))
                .then(Commands.argument("intensity", IntegerArgumentType.integer(1, 2))
                        .executes(ctx -> CommandCloudService.spawnThunder(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "intensity")
                        ))));

        root.then(Commands.literal("snowstorm")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> CommandCloudService.spawnSnowstorm(ctx.getSource(), false))
                .then(Commands.argument("overwrite", BoolArgumentType.bool())
                        .executes(ctx -> CommandCloudService.spawnSnowstorm(
                                ctx.getSource(),
                                BoolArgumentType.getBool(ctx, "overwrite")
                        ))));

        root.then(Commands.literal("fog")
                .executes(ctx -> CommandFogService.sendFogInfo(ctx.getSource())));

        root.then(Commands.literal("cpu")
                .executes(ctx -> CommandSystemService.sendCpuInfo(ctx.getSource())));

        root.then(Commands.literal("debugmode")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> CommandDebugService.setDebugMode(
                                ctx.getSource(),
                                BoolArgumentType.getBool(ctx, "value")
                        ))));

        if (simpleCloudsLoaded) {
            OptionalSimpleCloudsCommands.addAliases(root);
        }

        root.then(Commands.literal("windSpeed")
                .then(Commands.literal("get")
                        .executes(ctx -> net.Gabou.projectatmosphere.command.tree.service.CommandWindService.sendWeeklyWind(ctx.getSource()))));

        return root;
    }
}
