package net.Gabou.projectatmosphere.mixin;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;

final class RegionUpload {
    final CloudRegion region;
    final float[] data;

    RegionUpload(CloudRegion region, float[] data) {
        this.region = region;
        this.data = data;
    }
}
