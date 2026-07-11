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
    private static final int MAX_TEXTURE_UNIT = 11;
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
        private final int activeTexture;
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
            this.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            for (int unit = 0; unit <= MAX_TEXTURE_UNIT; unit++) {
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
                texture2d[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                texture3d[unit] = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
            }
            GlStateManager._activeTexture(activeTexture);
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
            closed = true;

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

            for (int unit = 0; unit <= MAX_TEXTURE_UNIT; unit++) {
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
                GlStateManager._bindTexture(texture2d[unit]);
                GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture3d[unit]);
            }
            GlStateManager._activeTexture(activeTexture);

            RenderSystem.setShader(() -> shader);
            GlStateManager._glUseProgram(program);
            GlStateManager._glBindVertexArray(vertexArray);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);

            // ShaderInstance.apply() caches a BlendMode separately from GL.
            // Invalidate that cache so the next vanilla shader reapplies it.
            BlendModeAccessor.projectatmosphere$setLastApplied(null);
            CURRENT.set(previous);
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
}
