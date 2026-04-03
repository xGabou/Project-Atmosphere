package net.Gabou.projectatmosphere.modules.fog;

import net.Gabou.projectatmosphere.api.AtmoApi;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.network.SyncFogStatusPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.PacketDistributor;

public final class FogStatusSyncManager {
    private FogStatusSyncManager() {
    }

    public static void syncPlayers(ServerLevel level) {
        if (!AtmoCommonConfig.FOG_ENABLED.get()) {
            return;
        }

        int interval = Math.max(1, AtmoCommonConfig.FOG_SYNC_INTERVAL_TICKS.get());
        if (level.getGameTime() % interval != 0L) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            syncPlayer(player);
        }
    }

    public static void syncPlayer(ServerPlayer player) {
        if (!AtmoCommonConfig.FOG_ENABLED.get() || player == null) {
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        long gameTime = level.getGameTime();
        float humidity = ForecastOrchestrator.getCurrentHumidity(level, pos, gameTime);
        float rainIntensity = AtmoApi.getInstance().getWeatherSnapshot(level, pos, gameTime).rainIntensity();

        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncFogStatusPacket(
                        Mth.clamp(humidity, 0.0F, 100.0F),
                        Mth.clamp(rainIntensity, 0.0F, 1.0F)
                )
        );
    }
}
