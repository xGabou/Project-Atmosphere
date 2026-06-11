package net.Gabou.projectatmosphere.client.render.pipeline;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

public final class AtmospherePipelineAdapters {
    private AtmospherePipelineAdapters() {
    }

    public static AtmospherePipelineAdapter select() {
        if (isShaderSafeMode()) {
            return ShaderPackPipelineAdapter.INSTANCE;
        }
        if (DistantHorizonsPipelineAdapter.INSTANCE.isAvailable()) {
            return DistantHorizonsPipelineAdapter.INSTANCE;
        }
        if (VoxyPipelineAdapter.INSTANCE.isAvailable()) {
            return VoxyPipelineAdapter.INSTANCE;
        }
        return VanillaPipelineAdapter.INSTANCE;
    }

    private static boolean isShaderSafeMode() {
        try {
            return AtmoCommonConfig.SHADER_SAFE_MODE.get();
        } catch (IllegalStateException exception) {
            return false;
        }
    }
}
