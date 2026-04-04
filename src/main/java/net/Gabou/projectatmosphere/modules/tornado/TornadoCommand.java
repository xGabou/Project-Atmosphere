package net.Gabou.projectatmosphere.modules.tornado;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.DelayedTaskScheduler;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class TornadoCommand {
    private static final String PROJECTATMOSPHERE$CUMULONIMBUS_ID = "simpleclouds:cumulonimbus";
    private static final int PROJECTATMOSPHERE$SEARCH_RADIUS = 10;
    private static final float PROJECTATMOSPHERE$DEFAULT_RADIUS = 10.0F;
    private static final int PROJECTATMOSPHERE$SPAWN_DELAY_TICKS = 500;
    private static final int PROJECTATMOSPHERE$AWAIT_INTERVAL = 100;
    private static final int PROJECTATMOSPHERE$AWAIT_POLLS = 40;

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

                    if (TornadoManager.spawnServerWithoutCloud(level, tornadoPos, PROJECTATMOSPHERE$DEFAULT_RADIUS, wind)) {
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("Standalone tornado spawned without requiring clouds."),
                                true
                        );
                        return 1;
                    }

                    ctx.getSource().sendFailure(Component.literal("Unable to spawn standalone tornado."));
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
                        projectatmosphere$awaitCloud(ctx.getSource(), level, tornadoPos, wind, PROJECTATMOSPHERE$AWAIT_POLLS);
                        return 1;
                    }

                    CloudRegion spawnedRegion = SimpleCloudsCompat.spawnCloudInBiome(
                            "cumulonimbus",
                            key,
                            level,
                            null,
                            wind
                    );
                    if (spawnedRegion != null) {
                        CommandSourceStack source = ctx.getSource();
                        DelayedTaskScheduler.schedule(PROJECTATMOSPHERE$SPAWN_DELAY_TICKS, () -> {
                            if (TornadoManager.spawnServer(level, tornadoPos, PROJECTATMOSPHERE$DEFAULT_RADIUS, wind)) {
                                source.sendSuccess(
                                        () -> Component.literal("Tornado engaged once the seeded cumulonimbus matured."),
                                        true
                                );
                            } else {
                                projectatmosphere$awaitCloud(source, level, tornadoPos, wind, PROJECTATMOSPHERE$AWAIT_POLLS);
                            }
                        });
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("Seeded a cumulonimbus; waiting for SimpleClouds tornado engagement."),
                                true
                        );
                        return 1;
                    }

                    projectatmosphere$awaitCloud(ctx.getSource(), level, tornadoPos, wind, PROJECTATMOSPHERE$AWAIT_POLLS);
                    return 1;
                });

        root.then(baseCommand);
        root.then(Commands.literal("spawntornadoes")
                .requires(source -> source.hasPermission(2))
                .executes(baseCommand.getCommand()));

        root.then(Commands.literal("cleartornadoes")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    TornadoManager.clearTornadoes();
                    ctx.getSource().sendSuccess(() -> Component.literal("All tornadoes cleared."), true);
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
                        ctx.getSource().sendSuccess(() -> Component.literal("Tornado removed."), true);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("No tornado found near you."));
                    }
                    return 1;
                }));
    }

    private static void projectatmosphere$awaitCloud(CommandSourceStack source,
                                                     ServerLevel level,
                                                     Vec3 tornadoPos,
                                                     net.Gabou.projectatmosphere.modules.core.WindVector wind,
                                                     int remainingPolls) {
        if (remainingPolls <= 0) {
            source.sendFailure(Component.literal("No SimpleClouds cumulonimbus became available for this tornado."));
            return;
        }

        DelayedTaskScheduler.schedule(PROJECTATMOSPHERE$AWAIT_INTERVAL, () -> {
            CloudRegion region = projectatmosphere$findCumulonimbus(level, tornadoPos);
            if (region != null && TornadoManager.spawnServer(level, tornadoPos, PROJECTATMOSPHERE$DEFAULT_RADIUS, wind)) {
                source.sendSuccess(
                        () -> Component.literal("Tornado engaged once a SimpleClouds cumulonimbus entered range."),
                        true
                );
                return;
            }
            if (remainingPolls - 1 > 0) {
                projectatmosphere$awaitCloud(source, level, tornadoPos, wind, remainingPolls - 1);
            } else {
                source.sendFailure(Component.literal("Timed out waiting for a suitable SimpleClouds cumulonimbus."));
            }
        });
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
