package net.Gabou.projectatmosphere.modules.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Base for all of your “Manager” classes (TemperatureManager, PressureManager, …).
 * Drives init, precompute & swap hooks on a consistent API.
 */
public abstract class BaseAtmosphereManager {
    private final int radius;
    protected BaseAtmosphereManager(int radius) {
        this.radius = radius;
    }

    /** Kick‐off on server (or join): generate / schedule everything. */
    public void init(ServerLevel world, BlockPos center) {
        doInit(world, center, radius);
    }

    /** Called just before midnight (or tick 18000) to precompute profiles. */
    public void precompute(ServerLevel world) {
        doPrecompute(world);
    }

    /** Called at swap‐time (tick 21000 / 3 AM) to advance tomorrow→today. */
    public void swapProfiles(ServerLevel world) {
        doSwap(world);
    }

    /** Completely clear any cached state. */
    public void clearAll() {
        onClearAll();
    }

    /** Internal: initialize your weekly forecasts and schedule daily. */
    protected abstract void doInit(ServerLevel world, BlockPos center, int radius);

    /** Internal: schedule building today/tomorrow daily curves. */
    protected abstract void doPrecompute(ServerLevel world);

    /** Internal: swap tomorrow→today, then schedule next tomorrow. */
    protected abstract void doSwap(ServerLevel world);

    /** Internal: clear any internal caches if needed. */
    protected void onClearAll() {}

    /** If any async work is needed, this lets you choose your own thread‐pool. */
    protected abstract void runAsync(Runnable task);
}
