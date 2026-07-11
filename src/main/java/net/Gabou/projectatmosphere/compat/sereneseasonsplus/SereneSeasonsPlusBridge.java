package net.Gabou.projectatmosphere.compat.sereneseasonsplus;

import com.Gabou.sereneseasonsplus.api.SSPApi;
import com.Gabou.sereneseasonsplus.util.EnvironmentHelper;
import net.minecraft.server.level.ServerLevel;

/** Loaded reflectively only while Serene Seasons Plus is present. */
public final class SereneSeasonsPlusBridge {
    private SereneSeasonsPlusBridge() {
    }

    public static void onCloudSpawned(ServerLevel level, int cloudRegionId) {
        SSPApi.getINSTANCE().onSimpleCloudsSpawned(level, cloudRegionId);
    }

    public static void onCloudDespawned(ServerLevel level, int cloudRegionId) {
        SSPApi.getINSTANCE().onCloudsDespawned(level, cloudRegionId);
    }

    public static void onSeasonChanged(ServerLevel level, boolean skippedAdjacentSeason) {
        EnvironmentHelper.onSeasonChange(level, skippedAdjacentSeason);
    }
}
