package net.Gabou.projectatmosphere.platform;

/** Loader/runtime facts needed by otherwise loader-neutral services. */
public interface PlatformEnvironment {
    boolean isModLoaded(String modId);

    boolean isProduction();
}
