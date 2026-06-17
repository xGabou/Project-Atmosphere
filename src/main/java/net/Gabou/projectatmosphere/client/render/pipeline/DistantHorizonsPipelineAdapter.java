package net.Gabou.projectatmosphere.client.render.pipeline;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraftforge.fml.ModList;

public final class DistantHorizonsPipelineAdapter implements AtmospherePipelineAdapter {
    public static final DistantHorizonsPipelineAdapter INSTANCE = new DistantHorizonsPipelineAdapter();

    private DistantHorizonsPipelineAdapter() {
    }

    @Override
    public String id() {
        return "projectatmosphere:distant_horizons";
    }

    @Override
    public boolean isAvailable() {
        return isEnabled() && ModList.get().isLoaded("distanthorizons");
    }

    private static boolean isEnabled() {
        try {
            return AtmoCommonConfig.ENABLE_DISTANT_HORIZONS_ADAPTER.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }
}
