package net.Gabou.projectatmosphere.modules.tornado;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

public class TornadoCommand {
    private static final String PROJECTATMOSPHERE$CUMULONIMBUS_ID = "simpleclouds:cumulonimbus";
    private static final int PROJECTATMOSPHERE$SEARCH_RADIUS = 10;
    private static final float PROJECTATMOSPHERE$DEFAULT_RADIUS = 10.0F;
    private static final double PROJECTATMOSPHERE$DEFAULT_REMOVE_RADIUS = 256.0D;

    public static void appendTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> noCloudsBaseCommand = Commands.literal("spawnTornadoNoClouds")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    if (!level.dimension().equals(Level.OVERWORLD)) {
                        return 0;
                    }

                    Vec3 playerPos = player.position();
                    Vec3 tornadoPos = new Vec3(playerPos.x, level.getSeaLevel(), playerPos.z);
                    RegionInstanceKey regionKey = RegionInstanceKey.from(player.blockPosition());
                    WindVectorApi.WindSample sample = WindVectorApi.getOrFallback(regionKey, level.getGameTime());
                    net.Gabou.projectatmosphere.modules.core.WindVector wind =
                            net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(
                                    sample.speedMps(),
                                    (float) Math.toRadians(sample.directionDeg())
                            );

                    if (TornadoManager.forceSpawnServerWithoutCloud(level, tornadoPos, PROJECTATMOSPHERE$DEFAULT_RADIUS, wind)) {
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("Standalone tornado spawned without requiring clouds."),
                                true
                        );
                        return 1;
                    }

                    ctx.getSource().sendFailure(Component.literal("Unable to force-spawn standalone tornado."));
                    return 0;
                });
        root.then(noCloudsBaseCommand);

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
                            player.blockPosition()
                    );
                    RegionInstanceKey regionKey = RegionInstanceKey.from(player.blockPosition());
                    WindVectorApi.WindSample sample = WindVectorApi.getOrFallback(regionKey, level.getGameTime());
                    net.Gabou.projectatmosphere.modules.core.WindVector wind =
                            net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(
                                    sample.speedMps(),
                                    (float) Math.toRadians(sample.directionDeg())
                            );

                    CloudRegion existing = projectatmosphere$findCumulonimbus(level, tornadoPos);
                    if (existing != null) {
                        if (TornadoManager.spawnServer(level, tornadoPos, PROJECTATMOSPHERE$DEFAULT_RADIUS, wind)) {
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Tornado engaged using SimpleClouds cumulonimbus."),
                                    true
                            );
                            return 1;
                        }
                        if (TornadoManager.forceSpawnServerWithoutCloud(level, tornadoPos, PROJECTATMOSPHERE$DEFAULT_RADIUS, wind)) {
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Cloud attachment failed, so a standalone tornado was force-spawned."),
                                    true
                            );
                            return 1;
                        }
                        ctx.getSource().sendFailure(Component.literal("Unable to spawn tornado."));
                        return 0;
                    }

                    CloudRegion spawnedRegion = SimpleCloudsCompat.spawnCloudInBiome(
                            "cumulonimbus",
                            key,
                            level,
                            null,
                            wind
                    );
                    if (spawnedRegion != null) {
                        if (TornadoManager.forceSpawnServerWithoutCloud(level, tornadoPos, PROJECTATMOSPHERE$DEFAULT_RADIUS, wind)) {
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Seeded a cumulonimbus and force-spawned a tornado immediately."),
                                    true
                            );
                            return 1;
                        }
                        ctx.getSource().sendFailure(Component.literal("Unable to spawn tornado after seeding a cumulonimbus."));
                        return 0;
                    }

                    if (TornadoManager.forceSpawnServerWithoutCloud(level, tornadoPos, PROJECTATMOSPHERE$DEFAULT_RADIUS, wind)) {
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("Forced a standalone tornado because no cumulonimbus could be attached."),
                                true
                        );
                        return 1;
                    }
                    ctx.getSource().sendFailure(Component.literal("Unable to spawn tornado."));
                    return 0;
                });

        root.then(baseCommand);
        root.then(Commands.literal("spawntornadoes")
                .requires(source -> source.hasPermission(2))
                .executes(baseCommand.getCommand()));

        root.then(Commands.literal("cleartornadoes")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> projectatmosphere$clearAllTornadoes(ctx.getSource())));

        LiteralArgumentBuilder<CommandSourceStack> removeTornado = Commands.literal("removetornado")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> projectatmosphere$removeNearestTornado(ctx.getSource(), PROJECTATMOSPHERE$DEFAULT_REMOVE_RADIUS))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                        .executes(ctx -> projectatmosphere$removeNearestTornado(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radius")
                        )))
                .then(Commands.literal("all")
                        .executes(ctx -> projectatmosphere$clearAllTornadoes(ctx.getSource())));
        root.then(removeTornado);
        root.then(Commands.literal("remove")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("tornado")
                        .executes(ctx -> projectatmosphere$removeNearestTornado(ctx.getSource(), PROJECTATMOSPHERE$DEFAULT_REMOVE_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                .executes(ctx -> projectatmosphere$removeNearestTornado(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius")
                                )))
                        .then(Commands.literal("all")
                                .executes(ctx -> projectatmosphere$clearAllTornadoes(ctx.getSource())))));
    }

    private static int projectatmosphere$removeNearestTornado(CommandSourceStack source, double maxDistance) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return 0;
        }

        Vec3 playerPos = player.position();
        double maxDistanceSq = maxDistance * maxDistance;
        TornadoInstance tornado = TornadoManager.getActiveTornadoes().stream()
                .filter(t -> t.position.distanceToSqr(playerPos) <= maxDistanceSq)
                .min(Comparator.comparingDouble(t -> t.position.distanceToSqr(playerPos)))
                .orElse(null);
        if (tornado == null) {
            source.sendFailure(Component.literal("No tornado found within " + Mth.floor(maxDistance) + " blocks."));
            return 0;
        }

        int distance = Mth.floor(Math.sqrt(tornado.position.distanceToSqr(playerPos)));
        TornadoManager.removeTornado(tornado);
        source.sendSuccess(() -> Component.literal("Removed tornado " + distance + " blocks away."), true);
        return 1;
    }

    private static int projectatmosphere$clearAllTornadoes(CommandSourceStack source) {
        TornadoManager.clearTornadoes();
        source.sendSuccess(() -> Component.literal("All tornadoes cleared."), true);
        return 1;
    }

    private static CloudRegion projectatmosphere$findCumulonimbus(ServerLevel level, Vec3 pos) {
        SpawnRegion region = new SpawnRegion(
                (int) Math.floor(pos.x),
                (int) Math.floor(pos.z),
                PROJECTATMOSPHERE$SEARCH_RADIUS
        );
        for (CloudRegion cloud : CloudManager.get(level).getClouds()) {
            if (!PROJECTATMOSPHERE$CUMULONIMBUS_ID.equals(cloud.getCloudTypeId().toString())) {
                continue;
            }
            if (cloud.intersects(region)) {
                return cloud;
            }
        }
        return null;
    }
}
