package net.Gabou.projectatmosphere.modules.weather;

public enum RegionalWeatherPhase {
    CALM,
    CLOUDY,
    RAIN,
    THUNDER,
    SEVERE,
    CYCLONE;

    public boolean isStormCapable() {
        return this == THUNDER || this == SEVERE || this == CYCLONE;
    }
}
