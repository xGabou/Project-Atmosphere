package net.Gabou.projectatmosphere.weather;

public enum TornadoLevel {
    F1(73, 112, 5.0),
    F2(113, 157, 10.0),
    F3(158, 206, 20.0),
    F4(207, 260, 40.0),
    F5(261, 318, 80.0);

    private final int minWindSpeed;
    private final int maxWindSpeed;
    private final double baseDamage;

    TornadoLevel(int minWindSpeed, int maxWindSpeed, double baseDamage) {
        this.minWindSpeed = minWindSpeed;
        this.maxWindSpeed = maxWindSpeed;
        this.baseDamage = baseDamage;
    }

    public int getMinWindSpeed() {
        return minWindSpeed;
    }

    public int getMaxWindSpeed() {
        return maxWindSpeed;
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    public static TornadoLevel fromWindSpeed(int windSpeed) {
        for (TornadoLevel level : values()) {
            if (windSpeed >= level.minWindSpeed && windSpeed <= level.maxWindSpeed) {
                return level;
            }
        }
        return F1;
    }
}
