package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.mixin.client.BlendModeAccessor;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;

import java.nio.ByteBuffer;

/** Exact capture/restore guard for PA cloud passes. */
public final class CloudRenderStateGuard {
    // Never duplicate the renderer's highest manual binding here. Unit 14 was
    // previously omitted when the candidate map shifted the two 3-D noises up
    // one slot, leaking an unbound texture into the caller's GL state.
    private static final int MAX_TRACKED_TEXTURE_UNIT =
            CloudTextureUnitContract.MAX_MINECRAFT_TRACKED_UNIT;
    private static final int MAX_TEXTURE_UNIT = CloudTextureUnitContract.MAX_PA_TOUCHED_UNIT;
    private static final int INDEXED_BLEND_TARGETS = 2;
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private CloudRenderStateGuard() {
    }

    public static State capture() {
        RenderSystem.assertOnRenderThread();
        return new State(CURRENT.get());
    }

    /** Binds the draw framebuffer and viewport that Forge supplied at entry. */
    public static boolean bindCapturedDrawFramebuffer() {
        State state = CURRENT.get();
        if (state == null) {
            return false;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
        RenderSystem.viewport(
                state.viewport[0], state.viewport[1], state.viewport[2], state.viewport[3]
        );
        return true;
    }

    /** Restores both framebuffer bindings captured at Forge event entry. */
    public static boolean bindCapturedFramebuffers() {
        State state = CURRENT.get();
        if (state == null) {
            return false;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, state.readFramebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
        RenderSystem.viewport(
                state.viewport[0], state.viewport[1], state.viewport[2], state.viewport[3]
        );
        return true;
    }

    public static int capturedDrawFramebuffer() {
        State state = CURRENT.get();
        return state == null ? GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) : state.drawFramebuffer;
    }

    public static int capturedReadFramebuffer() {
        State state = CURRENT.get();
        return state == null ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) : state.readFramebuffer;
    }

    public static int[] capturedViewport() {
        State state = CURRENT.get();
        if (state != null) {
            return state.viewport.clone();
        }
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        return viewport;
    }

    public static final class State implements AutoCloseable {
        private final State previous;
        private final int readFramebuffer;
        private final int drawFramebuffer;
        private final int[] viewport = new int[4];
        private final boolean depthTest;
        private final boolean depthMask;
        private final int depthFunc;
        private final boolean blend;
        private final int blendEquationRgb;
        private final int blendEquationAlpha;
        private final int blendSrcRgb;
        private final int blendDstRgb;
        private final int blendSrcAlpha;
        private final int blendDstAlpha;
        private final boolean[] indexedBlend = new boolean[INDEXED_BLEND_TARGETS];
        private final int[] indexedBlendEquationRgb = new int[INDEXED_BLEND_TARGETS];
        private final int[] indexedBlendEquationAlpha = new int[INDEXED_BLEND_TARGETS];
        private final int[] indexedBlendSrcRgb = new int[INDEXED_BLEND_TARGETS];
        private final int[] indexedBlendDstRgb = new int[INDEXED_BLEND_TARGETS];
        private final int[] indexedBlendSrcAlpha = new int[INDEXED_BLEND_TARGETS];
        private final int[] indexedBlendDstAlpha = new int[INDEXED_BLEND_TARGETS];
        private final boolean cull;
        private final int cullFace;
        private final boolean scissor;
        private final int[] scissorBox = new int[4];
        private final boolean[] colorMask = new boolean[4];
        private final float[] shaderColor;
        private final int rawActiveTexture;
        private final int cachedActiveTexture;
        private final int[] texture2d = new int[MAX_TEXTURE_UNIT + 1];
        private final int[] texture3d = new int[MAX_TEXTURE_UNIT + 1];
        private final int program;
        private final ShaderInstance shader;
        private final int vertexArray;
        private final int arrayBuffer;
        private boolean closed;

        private State(State previous) {
            this.previous = previous;
            this.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            this.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            this.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            this.depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            this.blend = GL11.glIsEnabled(GL11.GL_BLEND);
            this.blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
            this.blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
            this.blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            this.blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            this.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            this.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            for (int target = 0; target < INDEXED_BLEND_TARGETS; target++) {
                indexedBlend[target] = GL30.glIsEnabledi(GL11.GL_BLEND, target);
                indexedBlendEquationRgb[target] = GL30.glGetIntegeri(GL20.GL_BLEND_EQUATION_RGB, target);
                indexedBlendEquationAlpha[target] = GL30.glGetIntegeri(GL20.GL_BLEND_EQUATION_ALPHA, target);
                indexedBlendSrcRgb[target] = GL30.glGetIntegeri(GL14.GL_BLEND_SRC_RGB, target);
                indexedBlendDstRgb[target] = GL30.glGetIntegeri(GL14.GL_BLEND_DST_RGB, target);
                indexedBlendSrcAlpha[target] = GL30.glGetIntegeri(GL14.GL_BLEND_SRC_ALPHA, target);
                indexedBlendDstAlpha[target] = GL30.glGetIntegeri(GL14.GL_BLEND_DST_ALPHA, target);
            }
            this.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            this.cullFace = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
            this.scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
            ByteBuffer mask = BufferUtils.createByteBuffer(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < colorMask.length; i++) {
                colorMask[i] = mask.get(i) != 0;
            }
            this.shaderColor = RenderSystem.getShaderColor().clone();
            this.rawActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            this.cachedActiveTexture = GlStateManager._getActiveTexture();
            synchronizeTrackedTextureUnit(CloudTextureUnitContract.MINECRAFT_WORKING_UNIT);
            for (int unit = 0; unit <= MAX_TEXTURE_UNIT; unit++) {
                activateTextureUnit(unit);
                texture2d[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                texture3d[unit] = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
                if (unit <= MAX_TRACKED_TEXTURE_UNIT) {
                    // An optional renderer may have used raw glBindTexture,
                    // leaving Minecraft's binding cache stale even after the
                    // active unit itself is synchronized. Binding the value we
                    // just queried reconciles the cache without changing GL.
                    GlStateManager._bindTexture(texture2d[unit]);
                }
            }
            // PA starts from a raw/cache-synchronized unit. Restoring an
            // external raw unit here would let RenderSystem.bindTexture update
            // one Minecraft cache slot while GL binds a different unit.
            synchronizeTrackedTextureUnit(CloudTextureUnitContract.MINECRAFT_WORKING_UNIT);
            this.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            this.shader = RenderSystem.getShader();
            this.vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            this.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            CURRENT.set(this);
        }

        public int drawFramebuffer() {
            return drawFramebuffer;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (CURRENT.get() != this) {
                throw new IllegalStateException("Cloud render-state guards must close in LIFO order");
            }
            closed = true;

            try {
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);

                setEnabled(GL11.GL_DEPTH_TEST, depthTest);
                RenderSystem.depthMask(depthMask);
                RenderSystem.depthFunc(depthFunc);
                setEnabled(GL11.GL_BLEND, blend);
                GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
                RenderSystem.blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
                for (int target = 0; target < INDEXED_BLEND_TARGETS; target++) {
                    if (indexedBlend[target]) {
                        GL30.glEnablei(GL11.GL_BLEND, target);
                    } else {
                        GL30.glDisablei(GL11.GL_BLEND, target);
                    }
                    GL40.glBlendEquationSeparatei(
                            target, indexedBlendEquationRgb[target], indexedBlendEquationAlpha[target]
                    );
                    GL40.glBlendFuncSeparatei(
                            target,
                            indexedBlendSrcRgb[target], indexedBlendDstRgb[target],
                            indexedBlendSrcAlpha[target], indexedBlendDstAlpha[target]
                    );
                }
                setEnabled(GL11.GL_CULL_FACE, cull);
                GL11.glCullFace(cullFace);
                if (scissor) {
                    RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
                } else {
                    RenderSystem.disableScissor();
                }
                RenderSystem.colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
                RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);

                synchronizeTrackedTextureUnit(CloudTextureUnitContract.MINECRAFT_WORKING_UNIT);
                for (int unit = 0; unit <= MAX_TEXTURE_UNIT; unit++) {
                    activateTextureUnit(unit);
                    if (unit <= MAX_TRACKED_TEXTURE_UNIT) {
                        // Restore the raw binding even if an external call left
                        // Minecraft's per-unit binding cache stale, then reconcile
                        // that cache to the exact captured value.
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2d[unit]);
                        GlStateManager._bindTexture(texture2d[unit]);
                    } else {
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2d[unit]);
                    }
                    GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture3d[unit]);
                }

                RenderSystem.setShader(() -> shader);
                GlStateManager._glUseProgram(program);
                GlStateManager._glBindVertexArray(vertexArray);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);

                // ShaderInstance.apply() caches a BlendMode separately from GL.
                // Invalidate that cache so the next vanilla shader reapplies it.
                BlendModeAccessor.projectatmosphere$setLastApplied(null);
            } finally {
                try {
                    restoreActiveTextureState(rawActiveTexture, cachedActiveTexture);
                } finally {
                    CURRENT.set(previous);
                }
            }
        }

        private static void activateTextureUnit(int unit) {
            int texture = GL13.GL_TEXTURE0 + unit;
            if (unit <= MAX_TRACKED_TEXTURE_UNIT) {
                GlStateManager._activeTexture(texture);
            } else {
                GL13.glActiveTexture(texture);
            }
        }

        private static void synchronizeTrackedTextureUnit(int unit) {
            if (unit < 0 || unit > MAX_TRACKED_TEXTURE_UNIT) {
                throw new IllegalArgumentException("Minecraft texture unit is not tracked: " + unit);
            }
            int texture = GL13.GL_TEXTURE0 + unit;
            // The raw call is required even when GlStateManager's cache already
            // names this unit; _activeTexture would otherwise be a no-op while
            // an optional renderer may have changed GL_ACTIVE_TEXTURE directly.
            GL13.glActiveTexture(texture);
            GlStateManager._activeTexture(texture);
        }

        private static void restoreActiveTextureState(int rawTexture, int cachedTexture) {
            int cachedUnit = cachedTexture - GL13.GL_TEXTURE0;
            if (cachedUnit >= 0 && cachedUnit <= MAX_TRACKED_TEXTURE_UNIT) {
                synchronizeTrackedTextureUnit(cachedUnit);
            } else {
                // _activeTexture itself does not index TEXTURES, so it can
                // restore an external cache value exactly. PA performs no
                // cached bind after this point.
                GlStateManager._activeTexture(cachedTexture);
            }
            GL13.glActiveTexture(rawTexture);
        }

        private static void setEnabled(int capability, boolean enabled) {
            if (capability == GL11.GL_DEPTH_TEST) {
                if (enabled) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            } else if (capability == GL11.GL_BLEND) {
                if (enabled) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            } else if (capability == GL11.GL_CULL_FACE) {
                if (enabled) RenderSystem.enableCull(); else RenderSystem.disableCull();
            }
        }
    }

    /**
     * Driver-only diagnostic for the raw/cache split used by shader pipelines.
     * It creates the valid external state raw=unit14/cache=unit0, verifies that
     * capture gives PA a synchronized unit0, mutates bindings on units 0 and 14,
     * and proves close restores both bindings and the exact active-state pair.
     */
    public static String verifyTextureStateRoundTrip() {
        RenderSystem.assertOnRenderThread();
        if (CURRENT.get() != null) {
            return "unavailable_nested_guard";
        }

        int originalRawTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int originalCachedTexture = GlStateManager._getActiveTexture();
        int priorDriverErrors = drainGlErrors();
        int originalUnit0Texture2d = 0;
        int originalUnit0Texture3d = 0;
        int originalUnit14Texture2d = 0;
        int originalUnit14Texture3d = 0;
        boolean originalBindingsCaptured = false;
        int sentinelUnit0Texture2d = 0;
        int sentinelUnit14Texture2d = 0;
        int sentinelUnit14Texture3d = 0;
        int mutationTexture2d = 0;
        int mutationTexture3d = 0;
        try {
            State.synchronizeTrackedTextureUnit(CloudTextureUnitContract.MINECRAFT_WORKING_UNIT);
            originalUnit0Texture2d = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            originalUnit0Texture3d = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + CloudTextureUnitContract.DETAIL_NOISE_UNIT);
            originalUnit14Texture2d = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            originalUnit14Texture3d = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
            originalBindingsCaptured = true;

            sentinelUnit0Texture2d = GL11.glGenTextures();
            sentinelUnit14Texture2d = GL11.glGenTextures();
            sentinelUnit14Texture3d = GL11.glGenTextures();
            mutationTexture2d = GL11.glGenTextures();
            mutationTexture3d = GL11.glGenTextures();

            State.synchronizeTrackedTextureUnit(CloudTextureUnitContract.MINECRAFT_WORKING_UNIT);
            // Deliberately make Minecraft's unit-0 binding cache stale: it
            // names the texture PA will request, while raw GL holds a distinct
            // sentinel. Capture must reconcile this before the cached bind.
            GlStateManager._bindTexture(mutationTexture2d);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sentinelUnit0Texture2d);
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + CloudTextureUnitContract.DETAIL_NOISE_UNIT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sentinelUnit14Texture2d);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, sentinelUnit14Texture3d);

            int expectedUnit0;
            int expectedUnit14Texture2d;
            int expectedUnit14Texture3d;
            boolean synchronizedAtEntry;
            boolean cachedBindReachedUnit0;
            try (State state = capture()) {
                expectedUnit0 = state.texture2d[CloudTextureUnitContract.MINECRAFT_WORKING_UNIT];
                expectedUnit14Texture2d = state.texture2d[CloudTextureUnitContract.DETAIL_NOISE_UNIT];
                expectedUnit14Texture3d = state.texture3d[CloudTextureUnitContract.DETAIL_NOISE_UNIT];
                synchronizedAtEntry = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE0
                        && GlStateManager._getActiveTexture() == GL13.GL_TEXTURE0;

                RenderSystem.bindTexture(mutationTexture2d);
                cachedBindReachedUnit0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
                        == mutationTexture2d;
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + CloudTextureUnitContract.DETAIL_NOISE_UNIT);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, mutationTexture2d);
                GL11.glBindTexture(GL12.GL_TEXTURE_3D, mutationTexture3d);
            }

            boolean activePairRestored = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
                    == GL13.GL_TEXTURE0 + CloudTextureUnitContract.DETAIL_NOISE_UNIT
                    && GlStateManager._getActiveTexture() == GL13.GL_TEXTURE0;
            int restoredUnit14Texture2d = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int restoredUnit14Texture3d = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
            State.synchronizeTrackedTextureUnit(CloudTextureUnitContract.MINECRAFT_WORKING_UNIT);
            int restoredUnit0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            boolean bindingsRestored = restoredUnit0 == expectedUnit0
                    && restoredUnit14Texture2d == expectedUnit14Texture2d
                    && restoredUnit14Texture3d == expectedUnit14Texture3d;
            boolean sentinelsDistinct = expectedUnit0 == sentinelUnit0Texture2d
                    && expectedUnit14Texture2d == sentinelUnit14Texture2d
                    && expectedUnit14Texture3d == sentinelUnit14Texture3d
                    && expectedUnit0 != expectedUnit14Texture2d
                    && expectedUnit14Texture2d != expectedUnit14Texture3d;
            int driverError = GL11.glGetError();
            boolean passed = synchronizedAtEntry
                    && cachedBindReachedUnit0
                    && activePairRestored
                    && bindingsRestored
                    && sentinelsDistinct
                    && driverError == GL11.GL_NO_ERROR;
            return (passed ? "passed" : "failed")
                    + " entrySync=" + synchronizedAtEntry
                    + " cachedBind=" + cachedBindReachedUnit0
                    + " activePair=" + activePairRestored
                    + " bindings=" + bindingsRestored
                    + " distinct=" + sentinelsDistinct
                    + " priorGlErrors=" + priorDriverErrors
                    + " glError=" + driverError
                    + " unit0.2d=" + restoredUnit0 + "/" + expectedUnit0
                    + " unit14.2d=" + restoredUnit14Texture2d + "/" + expectedUnit14Texture2d
                    + " unit14.3d=" + restoredUnit14Texture3d + "/" + expectedUnit14Texture3d;
        } finally {
            try {
                if (originalBindingsCaptured) {
                    State.synchronizeTrackedTextureUnit(CloudTextureUnitContract.MINECRAFT_WORKING_UNIT);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, originalUnit0Texture2d);
                    GlStateManager._bindTexture(originalUnit0Texture2d);
                    GL11.glBindTexture(GL12.GL_TEXTURE_3D, originalUnit0Texture3d);
                    GL13.glActiveTexture(GL13.GL_TEXTURE0 + CloudTextureUnitContract.DETAIL_NOISE_UNIT);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, originalUnit14Texture2d);
                    GL11.glBindTexture(GL12.GL_TEXTURE_3D, originalUnit14Texture3d);
                }
            } finally {
                try {
                    for (int texture : new int[]{
                            sentinelUnit0Texture2d,
                            sentinelUnit14Texture2d,
                            sentinelUnit14Texture3d,
                            mutationTexture2d,
                            mutationTexture3d
                    }) {
                        if (texture != 0) {
                            GL11.glDeleteTextures(texture);
                        }
                    }
                } finally {
                    State.restoreActiveTextureState(originalRawTexture, originalCachedTexture);
                }
            }
        }
    }

    private static int drainGlErrors() {
        int count = 0;
        while (count < 32 && GL11.glGetError() != GL11.GL_NO_ERROR) {
            count++;
        }
        return count;
    }
}
