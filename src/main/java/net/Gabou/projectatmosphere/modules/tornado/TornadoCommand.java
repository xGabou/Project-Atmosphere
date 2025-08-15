package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.api.WindVector;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class TornadoCommand {
    @SubscribeEvent
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
                    WindVector.WindSample sample = WindVector.getOrFallback(key, level);
                    net.Gabou.projectatmosphere.modules.core.WindVector wind =
                            net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(sample.speedMps(),
                                    (float) Math.toRadians(sample.directionDeg()));
                    SimpleCloudsCompat.spawnCloudInBiome("cumulonimbus", key, level, null, wind);

                    Vec3 playerPos = player.position();
                    TornadoManager.spawnServer(level,
                            new Vec3(playerPos.x, level.getSeaLevel(), playerPos.z),
                            10f,
                            wind);

                    ctx.getSource().sendSuccess(
                            () -> Component.literal("🌪️ Tornado + ☁️ Cumulonimbus spawned."), true);
                    return 1;
                });

        event.getDispatcher().register(baseCommand);
        event.getDispatcher().register(Commands.literal("spawntornadoes")
                .requires(source -> source.hasPermission(2))
                .executes(baseCommand.getCommand()));

        event.getDispatcher().register(Commands.literal("cleartornadoes")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    TornadoManager.clearTornadoes();
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("🌪️ All tornadoes cleared."), true);
                    return 1;
                }));
        event.getDispatcher().register(Commands.literal("removetornado")
                .requires(source -> source.hasPermission(2))
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
                        ctx.getSource().sendFailure(
                                Component.literal("No tornado found near you."));
                    }
                    return 1;
                }));
    }
}
