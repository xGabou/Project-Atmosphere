package net.Gabou.projectatmosphere.modules.seasonaltrees.core;

public enum SeasonPhase {
    SPRING,
    SUMMER,
    AUTUMN,
    WINTER;

    public boolean isTransitionSeason() {
        return this == SPRING || this == AUTUMN;
    }
}
