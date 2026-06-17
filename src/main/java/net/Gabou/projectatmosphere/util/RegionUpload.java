package net.Gabou.projectatmosphere.util;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;

public final class RegionUpload {
    public final CloudRegion region;
    public final float[] data;

    public RegionUpload(CloudRegion region, float[] data) {
        this.region = region;
        this.data = data;
    }
}
