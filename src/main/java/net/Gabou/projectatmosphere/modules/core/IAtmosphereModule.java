// src/main/java/net/Gabou/projectatmosphere/modules/core/IAtmosphereModule.java
package net.Gabou.projectatmosphere.modules.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;


public interface IAtmosphereModule {
    void init(ServerLevel world, BlockPos center);
    void onPlayerJoined(ServerLevel world, BlockPos center);
    void onPrecomputeProfiles(ServerLevel world);
    void onSwapProfiles(ServerLevel world);
    void onSeasonChange(ServerLevel world, BlockPos center);
    void clearForecastCache(ServerLevel world, BlockPos center);

}
