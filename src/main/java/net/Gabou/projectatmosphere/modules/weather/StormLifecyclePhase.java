package net.Gabou.projectatmosphere.modules.weather;

public enum StormLifecyclePhase {
    FORMING,
    ACTIVE,
    DISSIPATING,
    DISSIPATED;

    public boolean isTerminal() {
        return this == DISSIPATED;
    }
}
