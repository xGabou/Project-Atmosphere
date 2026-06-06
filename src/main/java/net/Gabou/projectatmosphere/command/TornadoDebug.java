package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.clouds.backend.CloudRegionManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudRegionSyncManager;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.data.TornadoStorageManager;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.modules.tornado.TornadoProbabilityManager;
import net.Gabou.projectatmosphere.modules.tornado.TornadoSpawner;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class TornadoDebug {
    private TornadoDebug() {}

    public static void appendTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("cloud")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = player.serverLevel();
                                    if (!TemperatureCommandHelper.isInOverworld(level)) {
                                        ctx.getSource().sendFailure(Component.literal("Cloud spawning is only available in the Overworld."));
                                        return 0;
                                    }
                                    RegionInstanceKey regionKey = RegionInstanceKey.from(player.blockPosition());

                                    WindVectorApi.WindSample sample = WindVectorApi.getOrFallback(regionKey, level.getGameTime());
                                    net.Gabou.projectatmosphere.modules.core.WindVector wind =
                                            net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(
                                                    sample.speedMps(),
                                                    (float) Math.toRadians(sample.directionDeg())
                                            );

                                    String cloudId = StringArgumentType.getString(ctx, "id");
                                    var region = CloudRegionManager.getInstance().createCloudRegion(
                                            level,
                                            new Vec3(player.getX(), player.getY() + 80.0D, player.getZ()),
                                            64.0F,
                                            (float) player.getY() + 72.0F,
                                            (float) player.getY() + 88.0F,
                                            cloudId.contains("thunder") ? 0.85F : 0.65F,
                                            0.75F,
                                            0.35F,
                                            regionKey
                                    );

                                    if (region != null) {
                                        CloudRegionSyncManager.syncPlayer(player);
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal("Created PA cloud region '" + cloudId + "' at your position."),
                                                true);
                                        return 1;
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal("Failed to create PA cloud region '" + cloudId + "'."));
                                        return 0;
                                    }
                                })))
                .then(Commands.literal("tornado")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("risk")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = player.serverLevel();
                                    if (!TemperatureCommandHelper.isInOverworld(level)) {
                                        ctx.getSource().sendFailure(Component.literal("Tornado risk is only available in the Overworld."));
                                        return 0;
                                    }
                                    RegionInstanceKey key = RegionInstanceKey.from(player.blockPosition());
                                    float risk = TornadoProbabilityManager.computeRisk(key, level, level.getGameTime());
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Risk: " + risk), false);
                                    return 1;
                                }))
                        .then(Commands.literal("runtime")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = player.serverLevel();
                                    TornadoInstance tornado = TornadoManager.getActiveTornadoes().stream()
                                            .min((left, right) -> Double.compare(
                                                    left.position.distanceToSqr(player.position()),
                                                    right.position.distanceToSqr(player.position())
                                            ))
                                            .orElse(null);
                                    if (tornado == null) {
                                        ctx.getSource().sendFailure(Component.literal("No active tornado found."));
                                        return 0;
                                    }

                                    TornadoInstance.RuntimeDebugSnapshot debug = tornado.getRuntimeDebugSnapshot();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Tornado Runtime: " + debug.id() +
                                                    "\n  Phase:                 " + debug.phase() +
                                                    "\n  Intensity:             " + String.format(java.util.Locale.ROOT, "%.3f", debug.normalizedIntensity()) +
                                                    "\n  Eligible entities:     " + debug.eligibleEntityCount() +
                                                    "\n  Captured entities:     " + debug.capturedEntityCount() +
                                                    "\n  Pull force avg/max:    " + String.format(java.util.Locale.ROOT, "%.3f / %.3f", debug.averagePullForce(), debug.maxPullForce()) +
                                                    "\n  Upward force avg/max:  " + String.format(java.util.Locale.ROOT, "%.3f / %.3f", debug.averageUpwardForce(), debug.maxUpwardForce()) +
                                                    "\n  Sweep radius:          " + String.format(java.util.Locale.ROOT, "%.3f", debug.destructionSweepRadius()) +
                                                    "\n  Candidate blocks:      " + debug.destructionCandidateBlockCount() +
                                                    "\n  Destroyed blocks:      " + debug.destroyedBlockCount() +
                                                    "\n  Destroyed detail:      leaves/logs=" + debug.destroyedLeafLogCount()
                                                    + " weak=" + debug.destroyedWeakCount()
                                                    + " grass=" + debug.destroyedGrassCount()
                                                    + " glass=" + debug.destroyedGlassCount()
                                    ).withStyle(ChatFormatting.YELLOW), false);
                                    return 1;
                                }))
                        .then(Commands.literal("logging")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean value = BoolArgumentType.getBool(ctx, "value");
                                            AtmoCommonConfig.TORNADO_DEBUG_LOGGING.set(value);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Tornado runtime logging set to: " + value),
                                                    true
                                            );
                                            return 1;
                                        })))
                        .then(Commands.literal("force")
                                .then(Commands.argument("intensity", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            ServerLevel level = player.serverLevel();
                                            if (!TemperatureCommandHelper.isInOverworld(level)) {
                                                ctx.getSource().sendFailure(Component.literal("Tornado spawning is only available in the Overworld."));
                                                return 0;
                                            }
                                            RegionInstanceKey key = RegionInstanceKey.from(player.blockPosition());
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
                                            if (!TemperatureCommandHelper.isInOverworld(level)) {
                                                ctx.getSource().sendFailure(Component.literal("Tornado cooldown reset is only available in the Overworld."));
                                                return 0;
                                            }
                                            RegionInstanceKey key = RegionInstanceKey.from(player.blockPosition());
                                            TornadoStorageManager.setCooldown(key, 0);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Cooldown cleared."), true);
                                            return 1;
                                        }))));
    }
}
