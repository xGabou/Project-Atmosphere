package net.Gabou.projectatmosphere.platform.config;

import java.util.Objects;

/** Composition point for typed configuration ports. */
public final class AtmosphereConfig {
    private static volatile CloudConfigPort clouds;

    private AtmosphereConfig() {
    }

    public static void installClouds(CloudConfigPort installedClouds) {
        clouds = Objects.requireNonNull(installedClouds, "installedClouds");
    }

    public static CloudConfigPort clouds() {
        CloudConfigPort current = clouds;
        if (current == null) {
            throw new IllegalStateException("Atmosphere cloud configuration has not been installed");
        }
        return current;
    }
}
