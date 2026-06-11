package net.Gabou.projectatmosphere.command.tree.service;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.util.DelayedTaskScheduler;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public final class CommandTornadoService {
    private static final String CUMULONIMBUS_ID = "simpleclouds:cumulonimbus";
    private static final int SEARCH_RADIUS = 10;
    private static final float DEFAULT_RADIUS = 10.0F;
    private static final int SPAWN_DELAY_TICKS = 500;
    private static final int AWAIT_INTERVAL = 100;
    private static final int AWAIT_POLLS = 40;
    private static final double DEFAULT_REMOVE_RADIUS = 256.0D;

    private CommandTornadoService() {
    }

    public static int spawnTornado(CommandSourceStack source, boolean noCloud) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Tornado spawning is only available to players.");
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(Component.literal("Tornado spawning is only available in the Overworld."));
            return 0;
        }

        Vec3 playerPos = player.position();
        Vec3 tornadoPos = new Vec3(playerPos.x, level.getSeaLevel(), playerPos.z);
        RegionInstanceKey regionKey = RegionInstanceKey.from(player.blockPosition());
        WindVectorApi.WindSample sample = WindVectorApi.getOrFallback(regionKey, level.getGameTime());
        WindVector wind = WindVector.fromBase(sample.speedMps(), (float) Math.toRadians(sample.directionDeg()));

        if (noCloud) {
            return spawnStandaloneTornado(source, level, tornadoPos, wind);
        }

        if (!PaCommandSupport.requireSimpleClouds(source)) {
            return 0;
        }

        CloudRegion existing = findCumulonimbus(level, tornadoPos);
        if (existing != null) {
            if (TornadoManager.spawnServer(level, tornadoPos, DEFAULT_RADIUS, wind)) {
                PaCommandMessages.success(
                        source,
                        true,
                        "Tornado spawned",
                        "Position: " + formatPos(tornadoPos),
                        "Mode: normal",
                        "Wind: " + PaCommandSupport.formatWind(wind),
                        "Result: forming"
                );
                return 1;
            }
            awaitCloud(source, level, tornadoPos, wind, AWAIT_POLLS);
            return 1;
        }

        CloudRegion spawnedRegion = SimpleCloudsCompat.spawnCloudInRegion(
                "cumulonimbus",
                regionKey,
                level,
                null,
                wind
        );
        if (spawnedRegion != null) {
            CommandSourceStack capturedSource = source;
            DelayedTaskScheduler.schedule(SPAWN_DELAY_TICKS, () -> {
                if (TornadoManager.spawnServer(level, tornadoPos, DEFAULT_RADIUS, wind)) {
                    PaCommandMessages.success(
                            capturedSource,
                            true,
                            "Tornado spawned",
                            "Position: " + formatPos(tornadoPos),
                            "Mode: normal",
                            "Wind: " + PaCommandSupport.formatWind(wind),
                            "Result: seeded cumulonimbus matured"
                    );
                } else {
                    awaitCloud(capturedSource, level, tornadoPos, wind, AWAIT_POLLS);
                }
            });
            PaCommandMessages.success(
                    source,
                    true,
                    "Tornado spawn queued",
                    "Mode: normal",
                    "Wind: " + PaCommandSupport.formatWind(wind),
                    "Result: seeded cumulonimbus"
            );
            return 1;
        }

        awaitCloud(source, level, tornadoPos, wind, AWAIT_POLLS);
        return 1;
    }

    public static int removeTornado(CommandSourceStack source, double maxDistance) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Tornado removal is only available to players.");
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal("Tornado removal is only available in the Overworld."));
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
        PaCommandMessages.success(
                source,
                true,
                "Tornado removal requested",
                "Distance: " + distance + " blocks"
        );
        return 1;
    }

    public static int clearTornadoes(CommandSourceStack source) {
        TornadoManager.clearTornadoes();
        PaCommandMessages.success(source, true, "All tornadoes cleared");
        return 1;
    }

    public static int sendTornadoList(CommandSourceStack source) {
        List<TornadoInstance> tornadoes = TornadoManager.getActiveTornadoes();
        if (tornadoes.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Project Atmosphere]\nAction: Tornado list\nResult: no active tornadoes"), false);
            return 1;
        }

        StringBuilder message = new StringBuilder("[Project Atmosphere]\nAction: Tornado list");
        message.append("\nActive tornadoes: ").append(tornadoes.size());
        for (int i = 0; i < tornadoes.size(); i++) {
            TornadoInstance tornado = tornadoes.get(i);
            message.append("\n").append(i + 1).append(". ")
                    .append(tornado.getId())
                    .append(" pos=").append(formatPos(tornado.position))
                    .append(" radius=").append(String.format(java.util.Locale.ROOT, "%.1f", tornado.radius))
                    .append(" phase=").append(tornado.getRuntimeDebugSnapshot().phase())
                    .append(" intensity=").append(String.format(java.util.Locale.ROOT, "%.3f", tornado.getNormalizedIntensity()));
        }
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    public static int sendTornadoInfo(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        TornadoInstance tornado = player == null
                ? TornadoManager.getActiveTornadoes().stream().findFirst().orElse(null)
                : TornadoManager.getActiveTornadoes().stream()
                .min(Comparator.comparingDouble(t -> t.position.distanceToSqr(player.position())))
                .orElse(null);
        if (tornado == null) {
            source.sendFailure(Component.literal("No active tornado found."));
            return 0;
        }

        TornadoInstance.RuntimeDebugSnapshot debug = tornado.getRuntimeDebugSnapshot();
        PaCommandMessages.success(
                source,
                false,
                "Tornado info",
                "Id: " + debug.id(),
                "Phase: " + debug.phase(),
                "Intensity: " + String.format(java.util.Locale.ROOT, "%.3f", debug.normalizedIntensity()),
                "Eligible entities: " + debug.eligibleEntityCount(),
                "Captured entities: " + debug.capturedEntityCount(),
                "Pull force avg/max: " + String.format(java.util.Locale.ROOT, "%.3f / %.3f", debug.averagePullForce(), debug.maxPullForce()),
                "Upward force avg/max: " + String.format(java.util.Locale.ROOT, "%.3f / %.3f", debug.averageUpwardForce(), debug.maxUpwardForce()),
                "Destroyed blocks: " + debug.destroyedBlockCount()
        );
        return 1;
    }

    private static int spawnStandaloneTornado(CommandSourceStack source, ServerLevel level, Vec3 tornadoPos, WindVector wind) {
        if (TornadoManager.spawnServerWithoutCloud(level, tornadoPos, DEFAULT_RADIUS, wind)) {
            PaCommandMessages.success(
                    source,
                    true,
                    "Tornado spawned",
                    "Position: " + formatPos(tornadoPos),
                    "Mode: no_cloud",
                    "Wind: " + PaCommandSupport.formatWind(wind),
                    "Result: forming"
            );
            return 1;
        }
        source.sendFailure(Component.literal("Unable to spawn standalone tornado."));
        return 0;
    }

    private static void awaitCloud(CommandSourceStack source,
                                   ServerLevel level,
                                   Vec3 tornadoPos,
                                   WindVector wind,
                                   int remainingPolls) {
        if (remainingPolls <= 0) {
            source.sendFailure(Component.literal("Timed out waiting for a suitable Simple Clouds cumulonimbus."));
            return;
        }

        DelayedTaskScheduler.schedule(AWAIT_INTERVAL, () -> {
            CloudRegion region = findCumulonimbus(level, tornadoPos);
            if (region != null && TornadoManager.spawnServer(level, tornadoPos, DEFAULT_RADIUS, wind)) {
                PaCommandMessages.success(
                        source,
                        true,
                        "Tornado spawned",
                        "Position: " + formatPos(tornadoPos),
                        "Mode: normal",
                        "Wind: " + PaCommandSupport.formatWind(wind),
                        "Result: attached to Simple Clouds cumulonimbus"
                );
                return;
            }
            if (remainingPolls - 1 > 0) {
                awaitCloud(source, level, tornadoPos, wind, remainingPolls - 1);
            } else {
                source.sendFailure(Component.literal("No Simple Clouds cumulonimbus became available for this tornado."));
            }
        });
    }

    @Nullable
    private static CloudRegion findCumulonimbus(ServerLevel level, Vec3 pos) {
        SpawnRegion region = new SpawnRegion(
                (int) Math.floor(pos.x),
                (int) Math.floor(pos.z),
                SEARCH_RADIUS
        );
        for (CloudRegion cloud : CloudManager.get(level).getClouds()) {
            if (!CUMULONIMBUS_ID.equals(cloud.getCloudTypeId().toString())) {
                continue;
            }
            if (cloud.intersects(region)) {
                return cloud;
            }
        }
        return null;
    }

    private static String formatPos(Vec3 pos) {
        return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", pos.x, pos.y, pos.z);
    }
}
