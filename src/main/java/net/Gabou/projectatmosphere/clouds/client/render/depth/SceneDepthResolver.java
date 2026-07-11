package net.Gabou.projectatmosphere.clouds.client.render.depth;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderStateGuard;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Resolves the depth associated with Forge's actual draw framebuffer and
 * copies it to a detached depth texture before any cloud pass samples it.
 */
public final class SceneDepthResolver {
    private static final List<ProviderEntry> OPTIONAL_PROVIDERS = new CopyOnWriteArrayList<>();

    private static TextureTarget detachedTarget;
    private static String lastDiagnostics = "";

    private SceneDepthResolver() {
    }

    /**
     * Registers a provider from a conditionally loaded compatibility class.
     * Registration itself uses no optional dependency types.
     */
    public static AutoCloseable registerProvider(String name, SceneDepthProvider provider) {
        ProviderEntry entry = new ProviderEntry(
                name == null || name.isBlank() ? "optional" : name,
                Objects.requireNonNull(provider, "provider")
        );
        OPTIONAL_PROVIDERS.add(entry);
        return () -> OPTIONAL_PROVIDERS.remove(entry);
    }

    public static SceneDepthFrame resolve(RenderTarget vanillaMainTarget) {
        RenderSystem.assertOnRenderThread();
        int[] viewport = CloudRenderStateGuard.capturedViewport();
        SceneDepthProvider.Context context = new SceneDepthProvider.Context(
                vanillaMainTarget,
                CloudRenderStateGuard.capturedDrawFramebuffer(),
                viewport[0], viewport[1], viewport[2], viewport[3]
        );

        ResolvedSource source = resolveSource(context);
        SceneDepthFrame frame = source == null ? SceneDepthFrame.INVALID : copyDepth(source);
        logIfChanged(frame);
        return frame;
    }

    public static void shutdown() {
        RenderSystem.assertOnRenderThreadOrInit();
        if (detachedTarget != null) {
            detachedTarget.destroyBuffers();
            detachedTarget = null;
        }
        lastDiagnostics = "";
    }

    public static String diagnostics() {
        return lastDiagnostics.isBlank() ? SceneDepthFrame.INVALID.diagnostics() : lastDiagnostics;
    }

    private static ResolvedSource resolveSource(SceneDepthProvider.Context context) {
        int activeFramebuffer = context.forgeDrawFramebuffer();
        if (hasDepthAttachment(activeFramebuffer)) {
            String sourceName = context.vanillaMainTarget() != null
                    && activeFramebuffer == context.vanillaMainTarget().frameBufferId
                    ? "vanilla_main"
                    : "forge_active_framebuffer";
            return new ResolvedSource(
                    activeFramebuffer,
                    context.viewportX(),
                    context.viewportY(),
                    context.viewportWidth(),
                    context.viewportHeight(),
                    sourceName,
                    false
            );
        }

        for (ProviderEntry entry : OPTIONAL_PROVIDERS) {
            try {
                SceneDepthProvider.Source candidate = entry.provider().resolve(context);
                if (candidate != null && candidate.isUsable() && hasDepthAttachment(candidate.framebuffer())) {
                    return new ResolvedSource(
                            candidate.framebuffer(), candidate.x(), candidate.y(),
                            candidate.width(), candidate.height(),
                            entry.name() + ":" + candidate.name(), false
                    );
                }
            } catch (RuntimeException exception) {
                ProjectAtmosphere.LOGGER.warn(
                        "[CloudDepth] optional provider '{}' failed; trying the vanilla fallback",
                        entry.name(), exception
                );
            }
        }

        if (vanillaDepthUsable(context.vanillaMainTarget())) {
            RenderTarget main = context.vanillaMainTarget();
            return new ResolvedSource(
                    main.frameBufferId, 0, 0, main.width, main.height,
                    "vanilla_main_fallback", true
            );
        }
        return null;
    }

    private static SceneDepthFrame copyDepth(ResolvedSource source) {
        if (source.width() <= 0 || source.height() <= 0 || source.framebuffer() <= 0) {
            return SceneDepthFrame.INVALID;
        }
        ensureTarget(source.width(), source.height());
        if (detachedTarget == null || detachedTarget.getDepthTextureId() <= 0) {
            return SceneDepthFrame.INVALID;
        }

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.framebuffer());
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, detachedTarget.frameBufferId);
        int readStatus = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
        int drawStatus = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
        if (readStatus != GL30.GL_FRAMEBUFFER_COMPLETE || drawStatus != GL30.GL_FRAMEBUFFER_COMPLETE) {
            CloudRenderStateGuard.bindCapturedFramebuffers();
            ProjectAtmosphere.LOGGER.warn(
                    "[CloudDepth] framebuffer incomplete during detached depth copy: read={} draw={} source={}",
                    readStatus, drawStatus, source.name()
            );
            return SceneDepthFrame.INVALID;
        }

        GlStateManager._glBlitFrameBuffer(
                source.x(), source.y(), source.x() + source.width(), source.y() + source.height(),
                0, 0, detachedTarget.width, detachedTarget.height,
                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST
        );
        CloudRenderStateGuard.bindCapturedFramebuffers();
        return new SceneDepthFrame(
                detachedTarget.getDepthTextureId(),
                detachedTarget.width,
                detachedTarget.height,
                source.framebuffer(),
                detachedTarget.frameBufferId,
                source.name(),
                true,
                source.fallback(),
                true
        );
    }

    private static boolean hasDepthAttachment(int framebuffer) {
        if (framebuffer <= 0) {
            return false;
        }
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
        try {
            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                return false;
            }
            int type = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_READ_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            );
            if (type != GL11.GL_NONE) {
                return true;
            }
            type = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_READ_FRAMEBUFFER,
                    GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            );
            return type != GL11.GL_NONE;
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        }
    }

    private static boolean vanillaDepthUsable(RenderTarget target) {
        return target != null
                && target.frameBufferId > 0
                && target.getDepthTextureId() > 0
                && target.width > 0
                && target.height > 0
                && hasDepthAttachment(target.frameBufferId);
    }

    private static void ensureTarget(int width, int height) {
        if (detachedTarget != null
                && detachedTarget.width == width
                && detachedTarget.height == height) {
            return;
        }
        if (detachedTarget != null) {
            detachedTarget.destroyBuffers();
        }
        detachedTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        detachedTarget.setFilterMode(GL11.GL_NEAREST);
    }

    private static void logIfChanged(SceneDepthFrame frame) {
        String diagnostics = frame.diagnostics();
        if (!diagnostics.equals(lastDiagnostics)) {
            lastDiagnostics = diagnostics;
            ProjectAtmosphere.LOGGER.info("[CloudDepth] {}", diagnostics);
        }
    }

    private record ProviderEntry(String name, SceneDepthProvider provider) {
    }

    private record ResolvedSource(
            int framebuffer,
            int x,
            int y,
            int width,
            int height,
            String name,
            boolean fallback
    ) {
    }
}
