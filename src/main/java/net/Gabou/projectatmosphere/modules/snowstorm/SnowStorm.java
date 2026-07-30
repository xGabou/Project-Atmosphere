package net.Gabou.projectatmosphere.modules.snowstorm;

import net.Gabou.projectatmosphere.modules.weather.SnowTier;
import net.minecraft.world.level.ChunkPos;

public class SnowStorm {

    private final int intensity;
    private final SnowTier tier;
    private final double centerX;
    private final double centerZ;
    private final double radius;

    public int getIntensity() {
        return intensity;
    }

    public SnowTier getTier() {
        return tier;
    }

    public SnowStorm(int intensity, double centerX, double centerZ, double radius) {
        this.intensity = intensity;
        this.tier = tierFromIntensity(intensity);
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = Math.max(1.0D, radius);
    }

    public boolean intersects(ChunkPos pos) {
        return intersects(pos.getMinBlockX(), pos.getMinBlockZ(), pos.getMaxBlockX(), pos.getMaxBlockZ());
    }

    public boolean intersects(double minX, double minZ, double maxX, double maxZ) {
        double nearestX = Math.max(minX, Math.min(centerX, maxX));
        double nearestZ = Math.max(minZ, Math.min(centerZ, maxZ));
        double dx = centerX - nearestX;
        double dz = centerZ - nearestZ;
        return dx * dx + dz * dz <= radius * radius;
    }

    private static SnowTier tierFromIntensity(int intensity) {
        if (intensity >= 3) {
            return SnowTier.BLIZZARD;
        }
        if (intensity >= 2) {
            return SnowTier.SNOWSTORM;
        }
        if (intensity >= 1) {
            return SnowTier.SNOWY_DAY;
        }
        return SnowTier.NONE;
    }


}
