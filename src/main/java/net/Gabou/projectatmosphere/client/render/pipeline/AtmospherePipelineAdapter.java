package net.Gabou.projectatmosphere.client.render.pipeline;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowMapAccess;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowSnapshot;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AtmospherePipelineAdapter {
    String id();

    default boolean isAvailable() {
        return true;
    }

    default boolean isDepthAware() {
        return true;
    }

    default boolean isShaderSafe() {
        return false;
    }

    default int cloudColorTexture(@Nullable RenderTarget cloudTarget) {
        return cloudTarget == null ? -1 : cloudTarget.getColorTextureId();
    }

    default int cloudDepthTexture(@Nullable RenderTarget cloudTarget) {
        return cloudTarget == null ? -1 : cloudTarget.getDepthTextureId();
    }

    default @NotNull CloudShadowSnapshot cloudShadowSnapshot(@NotNull CloudRenderFrameContext frameContext) {
        return CloudShadowMapAccess.getCurrentSnapshot();
    }
}
