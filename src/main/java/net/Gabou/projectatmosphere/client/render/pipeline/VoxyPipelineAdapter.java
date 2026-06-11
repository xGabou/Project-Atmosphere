package net.Gabou.projectatmosphere.client.render.pipeline;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraftforge.fml.ModList;

public final class VoxyPipelineAdapter implements AtmospherePipelineAdapter {
    public static final VoxyPipelineAdapter INSTANCE = new VoxyPipelineAdapter();

    private VoxyPipelineAdapter() {
    }

    @Override
    public String id() {
        return "projectatmosphere:voxy";
    }

    @Override
    public boolean isAvailable() {
        return isEnabled() && ModList.get().isLoaded("voxy");
    }

    private static boolean isEnabled() {
        try {
            return AtmoCommonConfig.ENABLE_VOXY_ADAPTER.get();
        } catch (IllegalStateException exception) {
            return false;
        }
    }
}
