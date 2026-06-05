package net.Gabou.projectatmosphere.clouds.frontend;

public final class CloudRenderStateHolder {
    private CloudRenderStateHolder() {
    }

    private static final CloudRenderStateCache INSTANCE = new CloudRenderStateCache();

    public static CloudRenderStateCache getInstance() {
        return INSTANCE;
    }
}
