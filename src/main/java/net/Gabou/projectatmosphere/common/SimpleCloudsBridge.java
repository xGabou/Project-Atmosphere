package net.Gabou.projectatmosphere.common;

import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;

public final class SimpleCloudsBridge {
    private SimpleCloudsBridge() {}

    public static double getCloudScale() {
        return SimpleCloudsConstants.CLOUD_SCALE;
    }
}

