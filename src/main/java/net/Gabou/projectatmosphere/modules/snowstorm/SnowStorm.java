package net.Gabou.projectatmosphere.modules.snowstorm;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.modules.weather.SnowTier;

public class SnowStorm {

    private final int intensity;
    private final SnowTier tier;
    private final CloudRegion cloudRegion;


    public CloudRegion getCloudRegion() {
        return cloudRegion;
    }

    public int getIntensity() {
        return intensity;
    }

    public SnowTier getTier() {
        return tier;
    }

    public SnowStorm(int intensity, CloudRegion cloudRegion) {
        this.intensity = intensity;
        this.tier = tierFromIntensity(intensity);
        this.cloudRegion = cloudRegion;
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
