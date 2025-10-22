package net.Gabou.projectatmosphere.modules.tornado;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.data.TornadoStorageManager;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class TornadoDebug {
    private TornadoDebug() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("weatherdebug")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("cloud")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            ServerLevel level = player.serverLevel();
                                            if(!TemperatureCommandHelper.isInOverworld(level))
                                            {
                                                ctx.getSource().sendFailure(Component.literal("Cloud spawning is only available in the Overworld."));
                                                return 0;
                                            }
                                            var key = AtmosphereUtils.getBiomeKey(level, player.blockPosition());

                                            WindVectorApi.WindSample sample = WindVectorApi.getOrFallback(key);
                                            net.Gabou.projectatmosphere.modules.core.WindVector wind =
                                                    net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(
                                                            sample.speedMps(),
                                                            (float) Math.toRadians(sample.directionDeg())
                                                    );

                                            String cloudId = StringArgumentType.getString(ctx, "id");
                                            var region = SimpleCloudsCompat.spawnCloudInBiome(cloudId, key, level, null, wind);

                                            if (region != null) {
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("Spawned cloud '" + cloudId + "' at your position."),
                                                        true);
                                                return 1;
                                            } else {
                                                ctx.getSource().sendFailure(Component.literal("Failed to spawn cloud '" + cloudId + "'. SimpleClouds may not be initialized yet."));
                                                return 0;
                                            }
                                        })))
                        .then(Commands.literal("tornado")
                                .then(Commands.literal("risk")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            ServerLevel level = player.serverLevel();
                                            if(!TemperatureCommandHelper.isInOverworld(level))
                                            {
                                                ctx.getSource().sendFailure(Component.literal("Tornado risk is only available in the Overworld."));
                                                return 0;
                                            }
                                            BiomeInstanceKey key = AtmosphereUtils.getBiomeKey(level, player.blockPosition());
                                            float risk = TornadoProbabilityManager.computeRisk(key, level, level.getGameTime());
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Risk: " + risk), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("force")
                                        .then(Commands.argument("intensity", FloatArgumentType.floatArg(0f, 1f))
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    ServerLevel level = player.serverLevel();
                                                    if(!TemperatureCommandHelper.isInOverworld(level))
                                                    {
                                                        ctx.getSource().sendFailure(Component.literal("Tornado spawning is only available in the Overworld."));
                                                        return 0;
                                                    }
                                                    BiomeInstanceKey key = AtmosphereUtils.getBiomeKey(level, player.blockPosition());
                                                    float intensity = FloatArgumentType.getFloat(ctx, "intensity");
                                                    TornadoSpawner.spawn(key, level, intensity);
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("Tornado spawned."), true);
                                                    return 1;
                                                })))
                                .then(Commands.literal("cooldown")
                                        .then(Commands.literal("reset")
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    ServerLevel level = player.serverLevel();
                                                    if(!TemperatureCommandHelper.isInOverworld(level))
                                                    {
                                                        ctx.getSource().sendFailure(Component.literal("Tornado cooldown reset is only available in the Overworld."));
                                                        return 0;
                                                    }
                                                    BiomeInstanceKey key = AtmosphereUtils.getBiomeKey(level, player.blockPosition());
                                                    TornadoStorageManager.setCooldown(key, 0);
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("Cooldown cleared."), true);
                                                    return 1;
                                                })))));
    }
}
