package net.Gabou.projectatmosphere.modules.sandStorm;

import com.BreadRes.desertstormwarming.logic.SandstormManager;
import com.BreadRes.desertstormwarming.logic.SandstormPhase;

public class SandStormAPI {

    /**
     * Starts a sandstorm at the given phase using the Desert Storm Warming mod.
     * No internal logic or condition checks.
     *
     * @param phase The sandstorm phase to begin with.
     */
    public static void startSandstorm(SandstormPhase phase) {
        SandstormManager.start(phase);
    }

    /**
     * Stops the currently active sandstorm, if any.
     */
    public static void stopSandstorm() {
        SandstormManager.stop();
    }

    /**
     * Checks if a sandstorm is currently active.
     */
    public static boolean isSandstormActive() {
        return SandstormManager.isActive();
    }

    /**
     * Sets the current sandstorm phase.
     */
    public static void setPhase(SandstormPhase phase) {
        SandstormManager.setPhase(phase);
    }
}
