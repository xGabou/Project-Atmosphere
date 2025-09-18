package net.Gabou.projectatmosphere.modules.tornado;

import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.DelayedTaskScheduler;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class TornadoCommand {

    public static void register(RegisterCommandsEvent event) {
        var baseCommand = Commands.literal("spawnTornado")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();

                    if (!level.dimension().equals(Level.OVERWORLD)) return 0;

                    BiomeInstanceKey key = new BiomeInstanceKey(
                            AtmosphereUtils.getBiomeLocation(player.blockPosition(), level),
                            player.blockPosition());

                    WindVectorApi.WindSample sample = WindVectorApi.getOrFallback(key);
                    net.Gabou.projectatmosphere.modules.core.WindVector wind =
                            net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(
                                    sample.speedMps(),
                                    (float) Math.toRadians(sample.directionDeg())
                            );

                    // Spawn clouds + tornado
                    SimpleCloudsCompat.spawnCloudInBiome("cumulonimbus", key, level, null, wind);
                    Vec3 playerPos = player.position();
                    if (CloudManager.get(level).getClouds().stream().noneMatch(cloudRegion ->
                            cloudRegion.intersects(new SpawnRegion(player.getBlockX(), player.getBlockZ(), 10)) &&
                                    cloudRegion.getCloudTypeId().toString().equals("simpleclouds:cumulonimbus"))) {

                        // Force a cumulonimbus spawn in the biome
                        SimpleCloudsCompat.spawnCloudInBiome("cumulonimbus", key, level, null, wind);

                        // Capture player pos at trigger time


                        // Delay tornado spawn
                        DelayedTaskScheduler.schedule(500, () -> TornadoManager.spawnServer(
                                level,
                                new Vec3(playerPos.x, level.getSeaLevel(), playerPos.z),
                                10f,
                                wind
                        ));
                    }
                    else{
                        TornadoManager.spawnServer(
                                level,
                                new Vec3(playerPos.x, level.getSeaLevel(), playerPos.z),
                                10f,
                                wind
                        );
                    }

                    ctx.getSource().sendSuccess(
                            () -> Component.literal("🌪️ Tornado + ☁️ Cumulonimbus spawned. in 500 ticks"), true);
                    return 1;
                });

        // Aliases
        event.getDispatcher().register(baseCommand);
        event.getDispatcher().register(
                Commands.literal("spawntornadoes")
                        .requires(src -> src.hasPermission(2))
                        .executes(baseCommand.getCommand())
        );

        // Clear all tornadoes
        event.getDispatcher().register(
                Commands.literal("cleartornadoes")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            TornadoManager.clearTornadoes();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("🌪️ All tornadoes cleared."), true);
                            return 1;
                        })
        );

        // Remove one tornado near player
        event.getDispatcher().register(
                Commands.literal("removetornado")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ServerLevel level = player.serverLevel();

                            if (!level.dimension().equals(Level.OVERWORLD)) return 0;

                            Vec3 playerPos = player.position();
                            TornadoInstance tornado = TornadoManager.getActiveTornadoes().stream()
                                    .filter(t -> t.position.distanceToSqr(playerPos) < 100)
                                    .findFirst()
                                    .orElse(null);

                            if (tornado != null) {
                                TornadoManager.removeTornado(tornado);
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("🌪️ Tornado removed."), true);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("No tornado found near you."));
                            }
                            return 1;
                        })
        );
    }
}
