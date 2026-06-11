package net.Gabou.projectatmosphere.clouds.service;

/**
 * No-op cloud service used when an external cloud renderer owns the cloud layer.
 */
final class DisabledAtmosphereCloudService implements AtmosphereCloudService {
    @Override
    public boolean shouldTrySpawn(net.minecraft.server.level.ServerLevel level, int cloudBoosterTicks, boolean wasRegenerating) {
        return false;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
