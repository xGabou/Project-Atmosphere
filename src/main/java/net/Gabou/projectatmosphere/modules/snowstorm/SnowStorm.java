package net.Gabou.projectatmosphere.modules.snowstorm;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;

public class SnowStorm {

    private final int intensity;
    private final CloudRegion cloudRegion;


    public CloudRegion getCloudRegion() {
        return cloudRegion;
    }

    public int getIntensity() {
        return intensity;
    }

    public SnowStorm(int intensity, CloudRegion cloudRegion) {
        this.intensity = intensity;
        this.cloudRegion = cloudRegion;
    }



}