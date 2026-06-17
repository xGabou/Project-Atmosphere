package net.Gabou.projectatmosphere.modules.fog;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.network.FogDebugOverridePacket;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FogCommand {
    private static final float DEFAULT_STRENGTH = 0.85F;
    private static final int DEFAULT_DURATION_SECONDS = 30;

    private FogCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("fog")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .executes(ctx -> applyOverride(ctx, DEFAULT_STRENGTH, DEFAULT_DURATION_SECONDS))
                        .then(Commands.argument("strength", FloatArgumentType.floatArg(0.05F, 1.0F))
                                .executes(ctx -> applyOverride(
                                        ctx,
                                        FloatArgumentType.getFloat(ctx, "strength"),
                                        DEFAULT_DURATION_SECONDS
                                ))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                                        .executes(ctx -> applyOverride(
                                                ctx,
                                                FloatArgumentType.getFloat(ctx, "strength"),
                                                IntegerArgumentType.getInteger(ctx, "seconds")
                                        )))))
                .then(Commands.literal("clear")
                        .executes(FogCommand::clearOverride));
    }

    private static int applyOverride(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, float strength, int seconds) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            ctx.getSource().sendFailure(Component.literal("Fog debug override is only available in the Overworld."));
            return 0;
        }

        int durationTicks = seconds * 20;
        PacketDistributor.sendToPlayer(player, new FogDebugOverridePacket(strength, durationTicks));
        ctx.getSource().sendSuccess(
                () -> Component.literal(String.format("Fog override applied: strength=%.2f duration=%ds", strength, seconds)),
                true
        );
        return 1;
    }

    private static int clearOverride(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            ctx.getSource().sendFailure(Component.literal("Fog debug override is only available in the Overworld."));
            return 0;
        }

        PacketDistributor.sendToPlayer(player, new FogDebugOverridePacket(0.0F, 0));
        ctx.getSource().sendSuccess(() -> Component.literal("Fog override cleared."), true);
        return 1;
    }
}
