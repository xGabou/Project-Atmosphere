package net.Gabou.projectatmosphere.platform;

import java.util.Objects;

/** Composition point for loader/runtime services. */
public final class AtmospherePlatform {
    private static volatile PlatformEnvironment environment;

    private AtmospherePlatform() {
    }

    public static void installEnvironment(PlatformEnvironment installedEnvironment) {
        environment = Objects.requireNonNull(installedEnvironment, "installedEnvironment");
    }

    public static PlatformEnvironment environment() {
        PlatformEnvironment current = environment;
        if (current == null) {
            throw new IllegalStateException("Atmosphere platform environment has not been installed");
        }
        return current;
    }
}
