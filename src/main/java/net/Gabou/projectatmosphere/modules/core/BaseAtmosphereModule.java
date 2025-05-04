package net.Gabou.projectatmosphere.modules.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Base class for all atmosphere‐subsystem modules.
 * Now delegates to a module‐specific executor via runAsync(…).
 */
public abstract class BaseAtmosphereModule implements IAtmosphereModule {
    protected final int radius;

    protected BaseAtmosphereModule(int radius) {
        this.radius = radius;
    }

    @Override
    public void init(ServerLevel world, BlockPos center) {
        runAsync(() -> doInit(world, center, radius));
    }

    @Override
    public void onPlayerJoined(ServerLevel world, BlockPos center) {
        init(world, center);
    }


    @Override
    public void onPrecomputeProfiles(ServerLevel world) {
        runAsync(() -> doPrecompute(world));
    }

    @Override
    public void onSwapProfiles(ServerLevel world) {
        runAsync(() -> doSwap(world));
    }


    @Override
    public void onSeasonChange(ServerLevel world, BlockPos center) {
        runAsync(() -> {
            clearAll();
            doInit(world, center, radius);
        });
    }

    @Override
    public void clearForecastCache(ServerLevel world, BlockPos center) {
        runAsync(() -> {
            clearAll();
            doInit(world, center, radius);
        });
    }

    /** Module‐specific init logic (generate weekly & schedule daily). */
    protected abstract void doInit(ServerLevel world, BlockPos center, int radius);

    /** Module‐specific precompute logic (today & tomorrow). */

    protected abstract void doPrecompute(ServerLevel world);

    /** Module‐specific swap logic (tomorrow → today). */
    protected abstract void doSwap(ServerLevel world);

    /** Clear all module‐specific cached data. */
    protected abstract void clearAll();


    /**
     * Submit a task to the module’s executor.
     * Each subclass must route this to its own AsyncAtmosphereService.runXxx(…).
     */
    protected abstract void runAsync(Runnable task);
}
