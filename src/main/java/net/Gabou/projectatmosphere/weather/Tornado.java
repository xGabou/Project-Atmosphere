package net.Gabou.projectatmosphere.weather;

public class Tornado {
    private final TornadoLevel level;

    public Tornado(int windSpeed) {
        this.level = TornadoLevel.fromWindSpeed(windSpeed);
    }

    public TornadoLevel getLevel() {
        return level;
    }

    public double getSuctionRadius() {
        return level.getBaseDamage() * 2;
    }

    public double getDamageMultiplier() {
        return level.getBaseDamage();
    }
}
