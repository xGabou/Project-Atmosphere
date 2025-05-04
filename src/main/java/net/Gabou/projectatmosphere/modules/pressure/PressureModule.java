// src/main/java/net/Gabou/projectatmosphere/modules/pressure/PressionModule.java
package net.Gabou.projectatmosphere.modules.pressure;

import net.Gabou.projectatmosphere.modules.core.BaseAtmosphereModule;
import net.Gabou.projectatmosphere.modules.pressure.manager.PressureManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public class PressureModule extends BaseAtmosphereModule {
    private static final int DEFAULT_RADIUS = PressureManager.radiusBlocks;

    public PressureModule() {
        super(DEFAULT_RADIUS);
    }

    @Override
    protected void doInit(ServerLevel world, BlockPos center, int radius) {
        PressureManager.init(world, center);
    }

    @Override
    protected void doPrecompute(ServerLevel world) {
        PressureManager.onPrecomputeProfiles(world);
    }

    @Override
    protected void doSwap(ServerLevel world) {
        PressureManager.onSwapProfiles(world);
    }

    @Override
    protected void clearAll() {
        PressureManager.clearForecastCache();
    }


    @Override
    protected void runAsync(Runnable task) {
        AsyncAtmosphereService.runPression(task);
    }
}
