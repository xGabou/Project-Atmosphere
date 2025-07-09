package net.Gabou.projectatmosphere.modules.tornado;

import dev.nonamecrackers2.simpleclouds.common.command.CloudCommandSource;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class TornadoCommand {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("spawnTornado")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ServerLevel level = player.serverLevel();
                            BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(player.blockPosition(), level),player.blockPosition());
                            SimpleCloudsCompat.spawnCloudInBiome("cumulonimbus",key, level,null, ForecastOrchestrator.getCurrentWind(key));

                            // Spawn tornado visually (not bound to cloud instance ID — that would require deeper reflection)
                            TornadoManager.spawn(player.position(), 2.5f);  // adjust radius if needed

                            ctx.getSource().sendSuccess(() -> Component.literal("🌪️ Tornado + ☁️ Cumulonimbus spawned."), true);
                            return 1;
                        })
        );
    }
}
