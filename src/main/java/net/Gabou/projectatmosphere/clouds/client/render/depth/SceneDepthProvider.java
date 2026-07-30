package net.Gabou.projectatmosphere.clouds.client.render.depth;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.jetbrains.annotations.Nullable;

/**
 * Backend-neutral extension point for an optional renderer that can expose
 * the framebuffer containing the scene depth used by its current pipeline.
 * Implementations live in conditionally loaded compatibility packages and do
 * not leak optional API types into this contract.
 */
@FunctionalInterface
public interface SceneDepthProvider {
    @Nullable
    Source resolve(Context context);

    record Context(
            RenderTarget vanillaMainTarget,
            int forgeDrawFramebuffer,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight
    ) {
    }

    record Source(
            int framebuffer,
            int x,
            int y,
            int width,
            int height,
            String name
    ) {
        public boolean isUsable() {
            return framebuffer > 0 && width > 0 && height > 0;
        }
    }
}
