package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.api.AtmoApi;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.platform.network.AtmosphereNetwork;
import net.Gabou.projectatmosphere.network.SyncAtmosphereStatusPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

public final class AtmosphereStatusSyncManager {
    private AtmosphereStatusSyncManager() {
    }

    public static void syncPlayers(ServerLevel level) {
        int interval = Math.max(1, AtmoCommonConfig.FOG_SYNC_INTERVAL_TICKS.get());
        if (level.getGameTime() % interval != 0L) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            syncPlayer(player);
        }
    }

    public static void syncPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        long gameTime = level.getGameTime();
        float humidity = ForecastOrchestrator.getCurrentHumidity(level, pos, gameTime);
        var snapshot = AtmoApi.getInstance().getWeatherSnapshot(level, pos, gameTime);

        AtmosphereNetwork.sendToPlayer(
                player,
                new SyncAtmosphereStatusPacket(
                        Mth.clamp(humidity, 0.0F, 100.0F),
                        Mth.clamp(snapshot.rainIntensity(), 0.0F, 1.0F),
                        Mth.clamp(snapshot.cloudCover(), 0.0F, 1.0F)
                )
        );
    }
}
