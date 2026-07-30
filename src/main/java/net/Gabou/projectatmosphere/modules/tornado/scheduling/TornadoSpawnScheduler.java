package net.Gabou.projectatmosphere.modules.tornado.scheduling;

import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;

/**
 * Manages tornado spawn slots and cooldowns.
 * Allows up to three simultaneous tornadoes with staggered initial availability
 * and per-slot cooldowns after spawning.
 */
public final class TornadoSpawnScheduler {
    private static final int MAX_TORNADOES = 3;
    private static final long MINUTES_TO_TICKS = 20L * 60L;
    private static final long INITIAL_DELAY_TICKS = 30L * MINUTES_TO_TICKS;
    private static final long SLOT_INTERVAL_TICKS = 20L * MINUTES_TO_TICKS;
    private static final long COOLDOWN_TICKS = 60L * MINUTES_TO_TICKS;

    private static final long[] slotReadyTicks = new long[MAX_TORNADOES];

    static {
        for (int i = 0; i < MAX_TORNADOES; i++) {
            slotReadyTicks[i] = INITIAL_DELAY_TICKS + (i * SLOT_INTERVAL_TICKS);
        }
    }

    private TornadoSpawnScheduler() {}

    /**
     * Returns true if there is at least one slot ready for spawning and the
     * active tornado count is below the allowed maximum.
     */
    public static boolean isSlotAvailable(long nowTick) {
        int activeTornadoes = AtmosphereCloudServices.get().activeTornadoCount();
        if (activeTornadoes >= MAX_TORNADOES) {
            return false;
        }
        for (long ready : slotReadyTicks) {
            if (nowTick >= ready) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marks the earliest available slot as used, placing it on cooldown.
     */
    public static void recordSpawn(long nowTick) {
        for (int i = 0; i < slotReadyTicks.length; i++) {
            if (nowTick >= slotReadyTicks[i]) {
                slotReadyTicks[i] = nowTick + COOLDOWN_TICKS;
                break;
            }
        }
    }
}
