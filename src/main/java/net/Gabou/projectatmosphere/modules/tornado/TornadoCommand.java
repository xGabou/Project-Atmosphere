package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
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
import net.minecraftforge.network.PacketDistributor;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.network.SpawnTornadoPacket;

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
                    var wind = ForecastOrchestrator.getCurrentWind(key,level.getGameTime());
                   //SimpleCloudsCompat.spawnCloudInBiome("cumulonimbus", key, level, null, wind);

                    Vec3 playerPos = player.position();
                    // Spawn tornado visually and sync with clients
                    TornadoManager.spawn(new Vec3(playerPos.x,level.getSeaLevel(),playerPos.z), 2.5f, wind);
                    NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(),
                            new SpawnTornadoPacket(player.position(), 2.5f, wind));

                    ctx.getSource().sendSuccess(
                            () -> Component.literal("🌪️ Tornado + ☁️ Cumulonimbus spawned."), true);
                    return 1;
                });

        event.getDispatcher().register(baseCommand);
        event.getDispatcher().register(Commands.literal("spawntornadoes")
                .requires(source -> source.hasPermission(2))
                .executes(baseCommand.getCommand()));
    }
}
