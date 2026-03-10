package net.Gabou.projectatmosphere.modules.tornado;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class TornadoCommand {
    private static final float PROJECTATMOSPHERE$DEFAULT_RADIUS = 10.0F;

    public static void appendTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> baseCommand = Commands.literal("spawnTornado")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    if (!level.dimension().equals(Level.OVERWORLD)) {
                        return 0;
                    }
                    Vec3 playerPos = player.position();
                    Vec3 tornadoPos = new Vec3(playerPos.x, level.getSeaLevel(), playerPos.z);
                    BiomeInstanceKey key = new BiomeInstanceKey(
                            AtmosphereUtils.getBiomeLocation(player.blockPosition(), level),
                            player.blockPosition());
                    WindVectorApi.WindSample sample = WindVectorApi.getOrFallback(key);
                    net.Gabou.projectatmosphere.modules.core.WindVector wind =
                            net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(
                                    sample.speedMps(),
                                    (float) Math.toRadians(sample.directionDeg())
                            );
                    TornadoManager.spawnServer(level, tornadoPos, PROJECTATMOSPHERE$DEFAULT_RADIUS, wind);
                    ctx.getSource().sendSuccess(() -> Component.literal("Spawned tornado."), true);
                    return 1;
                });

        root.then(baseCommand);
        root.then(Commands.literal("spawntornadoes")
                .requires(source -> source.hasPermission(2))
                .executes(baseCommand.getCommand()));

        root.then(Commands.literal("cleartornadoes")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    TornadoManager.clearTornadoes();
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("All tornadoes cleared."), true);
                    return 1;
                }));
        root.then(Commands.literal("removetornado")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    if (!level.dimension().equals(Level.OVERWORLD)) {
                        return 0;
                    }
                    Vec3 playerPos = player.position();
                    TornadoInstance tornado = TornadoManager.getActiveTornadoes().stream()
                            .filter(t -> t.position.distanceToSqr(playerPos) < 100)
                            .findFirst()
                            .orElse(null);
                    if (tornado != null) {
                        TornadoManager.removeTornado(tornado);
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("Tornado removed."), true);
                    } else {
                        ctx.getSource().sendFailure(
                                Component.literal("No tornado found near you."));
                    }
                    return 1;
                }));
    }
}
