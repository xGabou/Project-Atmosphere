// src/main/java/net/Gabou/projectatmosphere/modules/humidity/HumidityModule.java
package net.Gabou.projectatmosphere.modules.humidity;

import net.Gabou.projectatmosphere.modules.core.BaseAtmosphereModule;
import net.Gabou.projectatmosphere.modules.humidity.manager.HumidityManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class HumidityModule extends BaseAtmosphereModule {
    private static final int DEFAULT_RADIUS = 250;

    public HumidityModule() {
        super(DEFAULT_RADIUS);
    }

    @Override
    protected void doInit(ServerLevel world, BlockPos center, int radius) {
        HumidityManager.init(world, center);
    }

    @Override
    protected void doPrecompute(ServerLevel world) {
        HumidityManager.onPrecomputeProfiles(world);
    }

    @Override
    protected void doSwap(ServerLevel world) {
        HumidityManager.onSwapProfiles(world);
    }

    @Override
    protected void clearAll() {
        // will be regenerated on next init
        HumidityManager.clearForecastCache(null, null);
    }

    @Override
    protected void runAsync(Runnable task) {
        AsyncAtmosphereService.runHumidity(task);
    }
}
