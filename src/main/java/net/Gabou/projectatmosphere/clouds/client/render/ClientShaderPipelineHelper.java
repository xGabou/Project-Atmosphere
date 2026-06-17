package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

/**
 * Detects when Project Atmosphere should prefer conservative rendering paths
 * that avoid fighting external shader pipelines.
 */
public final class ClientShaderPipelineHelper {
    private ClientShaderPipelineHelper() {
    }

    public static boolean isConservativeShaderPathPreferred() {
        if (isShaderSafeModeEnabled()) {
            return true;
        }
        return isExternalShaderPackActive();
    }

    public static boolean isShaderSafeModeEnabled() {
        try {
            return AtmoCommonConfig.SHADER_SAFE_MODE.get();
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    public static boolean isExternalShaderPackActive() {
        try {
            return nonamecrackers2.crackerslib.common.compat.CompatHelper.areShadersRunning();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
