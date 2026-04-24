package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.client.render.TornadoRenderDebugState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public final class TornadoRenderDebugClientCommand {
    private TornadoRenderDebugClientCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tornado")
                .requires(source -> TornadoRenderDebugState.isCommandAvailable())
                .then(Commands.literal("render")
                        .executes(ctx -> sendStatus(ctx.getSource()))
                        .then(Commands.literal("status")
                                .executes(ctx -> sendStatus(ctx.getSource())))
                        .then(Commands.literal("inspect")
                                .executes(ctx -> requestInspect(ctx.getSource())))
                        .then(Commands.literal("mode")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(TornadoRenderDebugState.supportedModes().split(", "), builder))
                                        .executes(ctx -> setMode(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "value")
                                        ))))
                        .then(Commands.literal("freeze")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> setFreeze(
                                                ctx.getSource(),
                                                BoolArgumentType.getBool(ctx, "enabled")
                                        ))))
                        .then(Commands.literal("storm")
                                .then(Commands.literal("auto")
                                        .executes(ctx -> setStormIndex(ctx.getSource(), -1)))
                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setStormIndex(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "index")
                                        )))));
    }

    private static int sendStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Tornado render debug: " + TornadoRenderDebugState.describe()), false);
        return 1;
    }

    private static int setMode(CommandSourceStack source, String token) {
        TornadoRenderDebugState.Mode mode = TornadoRenderDebugState.Mode.fromToken(token);
        TornadoRenderDebugState.setMode(mode);
        source.sendSuccess(() -> Component.literal(
                "Tornado render debug mode set to '" + mode.token() + "'. Supported modes: " + TornadoRenderDebugState.supportedModes()
        ), false);
        return 1;
    }

    private static int setFreeze(CommandSourceStack source, boolean enabled) {
        TornadoRenderDebugState.setFreezeEnabled(enabled);
        source.sendSuccess(() -> Component.literal("Tornado render debug freeze set to " + enabled + "."), false);
        return 1;
    }

    private static int setStormIndex(CommandSourceStack source, int stormIndex) {
        TornadoRenderDebugState.setRequestedStormIndex(stormIndex);
        source.sendSuccess(() -> Component.literal(
                stormIndex < 0
                        ? "Tornado render debug storm selection set to auto."
                        : "Tornado render debug storm index set to " + stormIndex + "."
        ), false);
        return 1;
    }

    private static int requestInspect(CommandSourceStack source) {
        TornadoRenderDebugState.requestDiagnosticReport();
        source.sendSuccess(() -> Component.literal("Requested tornado render diagnostic report. Check the log output."), false);
        return 1;
    }
}
