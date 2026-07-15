package net.Gabou.projectatmosphere.clouds.client.render.field;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.Gabou.projectatmosphere.client.render.shader.CloudFieldVolumeShaders;
import net.Gabou.projectatmosphere.clouds.client.render.CloudGpuTimer;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderStateGuard;
import net.Gabou.projectatmosphere.clouds.client.render.depth.SceneDepthFrame;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Depth-aware upsample and scene composite for the CloudField target. */
public final class CloudFieldCompositeRenderer {
    private static final CloudGpuTimer GPU_TIMER = new CloudGpuTimer();
    private static VertexBuffer fullscreenQuad;
    private static volatile LastDrawInputs lastDrawInputs = LastDrawInputs.EMPTY;

    private CloudFieldCompositeRenderer() {
    }

    public static boolean composite(
            RenderTarget source,
            RenderTarget destination,
            CloudFieldCompositeDebugMode requestedMode
    ) {
        return composite(source, destination, requestedMode, true, SceneDepthFrame.INVALID);
    }

    public static boolean composite(
            RenderTarget source,
            RenderTarget destination,
            CloudFieldCompositeDebugMode requestedMode,
            boolean depthAwareComposite
    ) {
        return composite(source, destination, requestedMode, depthAwareComposite, SceneDepthFrame.INVALID);
    }

    public static boolean composite(
            RenderTarget source,
            RenderTarget destination,
            CloudFieldCompositeDebugMode requestedMode,
            boolean depthAwareComposite,
            SceneDepthFrame sceneDepth
    ) {
        lastDrawInputs = LastDrawInputs.EMPTY;
        ShaderInstance shader = CloudFieldVolumeShaders.getCompositeShader();
        if (source == null || destination == null || shader == null || source.getDepthTextureId() <= 0) {
            return false;
        }
        ensureFullscreenQuad();
        if (fullscreenQuad == null) {
            return false;
        }

        CloudFieldCompositeDebugMode mode = requestedMode == null
                ? CloudFieldCompositeDebugMode.FINAL
                : requestedMode;
        GPU_TIMER.poll();
        RenderSystem.disableScissor();
        boolean capturedDestination = CloudRenderStateGuard.bindCapturedDrawFramebuffer();
        if (!capturedDestination) {
            destination.bindWrite(true);
        }
        int[] destinationViewport = capturedDestination
                ? CloudRenderStateGuard.capturedViewport()
                : new int[]{0, 0, destination.viewWidth, destination.viewHeight};
        int destinationFramebuffer = capturedDestination
                ? CloudRenderStateGuard.capturedDrawFramebuffer()
                : -1;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (depthAwareComposite && (mode == CloudFieldCompositeDebugMode.FINAL
                || mode == CloudFieldCompositeDebugMode.SPATIAL)) {
            // The fixed-function test is a safe final line of defence even if
            // a detached scene texture cannot be supplied.
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);
        shader.setSampler("CloudColorSampler", source.getColorTextureId());
        shader.setSampler("CloudDepthSampler", source.getDepthTextureId());
        SceneDepthFrame safeSceneDepth = sceneDepth == null ? SceneDepthFrame.INVALID : sceneDepth;
        boolean guidedUpsampling = depthAwareComposite
                && safeSceneDepth.valid()
                && safeSceneDepth.detached();
        shader.setSampler("SceneDepthSampler", guidedUpsampling ? safeSceneDepth.textureId() : 0);
        shader.safeGetUniform("CompositeMode").set(mode.shaderId());
        shader.safeGetUniform("DepthCompositeEnabled").set(depthAwareComposite ? 1 : 0);
        shader.safeGetUniform("SceneDepthValid").set(guidedUpsampling ? 1 : 0);
        shader.apply();

        GPU_TIMER.begin();
        try {
            fullscreenQuad.bind();
            fullscreenQuad.drawWithShader(new Matrix4f(), new Matrix4f(), shader);
            VertexBuffer.unbind();
        } finally {
            GPU_TIMER.end();
            shader.clear();
        }
        lastDrawInputs = LastDrawInputs.capture(
                mode,
                depthAwareComposite,
                guidedUpsampling,
                safeSceneDepth,
                source,
                destination,
                destinationFramebuffer,
                destinationViewport,
                capturedDestination
        );
        return true;
    }

    public static LastDrawInputs lastDrawInputs() {
        return lastDrawInputs;
    }

    public static String performanceDiagnostics() {
        GPU_TIMER.poll();
        if (!GPU_TIMER.isSupported()) {
            return "compositeGpuMs=unsupported";
        }
        if (!GPU_TIMER.hasResult()) {
            return "compositeGpuMs=pending pendingCompositeQueries=" + GPU_TIMER.getPendingQueries();
        }
        return "compositeGpuMs=" + CloudFieldVolumeRenderStats.format(GPU_TIMER.getLastMilliseconds())
                + " compositeAgeFrames=" + GPU_TIMER.getLastResultAgeFrames()
                + " pendingCompositeQueries=" + GPU_TIMER.getPendingQueries();
    }

    public static void shutdown() {
        GPU_TIMER.close();
        lastDrawInputs = LastDrawInputs.EMPTY;
        if (fullscreenQuad != null) {
            fullscreenQuad.close();
            fullscreenQuad = null;
        }
    }

    private static void ensureFullscreenQuad() {
        if (fullscreenQuad != null) {
            return;
        }
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1.0F, -1.0F, 0.0F).uv(0.0F, 0.0F).endVertex();
        builder.vertex(1.0F, -1.0F, 0.0F).uv(1.0F, 0.0F).endVertex();
        builder.vertex(1.0F, 1.0F, 0.0F).uv(1.0F, 1.0F).endVertex();
        builder.vertex(-1.0F, 1.0F, 0.0F).uv(0.0F, 1.0F).endVertex();
        fullscreenQuad = new VertexBuffer(VertexBuffer.Usage.STATIC);
        fullscreenQuad.bind();
        fullscreenQuad.upload(builder.end());
        VertexBuffer.unbind();
    }

    /** Exact state used by the most recent successful composite draw. */
    public record LastDrawInputs(
            boolean valid,
            long signature,
            CloudFieldCompositeDebugMode mode,
            boolean depthCompositeEnabled,
            boolean guidedUpsampling,
            String sceneDepthSource,
            int sceneDepthTextureId,
            int sceneDepthWidth,
            int sceneDepthHeight,
            int sourceWidth,
            int sourceHeight,
            int destinationWidth,
            int destinationHeight,
            int drawFramebuffer,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            boolean capturedDestination
    ) {
        private static final long FNV_OFFSET = 0xcbf29ce484222325L;
        private static final long FNV_PRIME = 0x100000001b3L;
        public static final LastDrawInputs EMPTY = new LastDrawInputs(
                false,
                0L,
                CloudFieldCompositeDebugMode.FINAL,
                false,
                false,
                "invalid",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                -1,
                0,
                0,
                0,
                0,
                false
        );

        private static LastDrawInputs capture(
                CloudFieldCompositeDebugMode mode,
                boolean depthCompositeEnabled,
                boolean guidedUpsampling,
                SceneDepthFrame sceneDepth,
                RenderTarget source,
                RenderTarget destination,
                int drawFramebuffer,
                int[] viewport,
                boolean capturedDestination
        ) {
            int viewportX = viewport.length > 0 ? viewport[0] : 0;
            int viewportY = viewport.length > 1 ? viewport[1] : 0;
            int viewportWidth = viewport.length > 2 ? viewport[2] : 0;
            int viewportHeight = viewport.length > 3 ? viewport[3] : 0;
            long signature = FNV_OFFSET;
            signature = mix(signature, mode.shaderId());
            signature = mix(signature, depthCompositeEnabled ? 1L : 0L);
            signature = mix(signature, guidedUpsampling ? 1L : 0L);
            signature = mixString(signature, sceneDepth.source());
            signature = mix(signature, guidedUpsampling ? sceneDepth.textureId() : 0L);
            signature = mix(signature, sceneDepth.width());
            signature = mix(signature, sceneDepth.height());
            signature = mix(signature, source.width);
            signature = mix(signature, source.height);
            signature = mix(signature, destination.width);
            signature = mix(signature, destination.height);
            signature = mix(signature, drawFramebuffer);
            signature = mix(signature, viewportX);
            signature = mix(signature, viewportY);
            signature = mix(signature, viewportWidth);
            signature = mix(signature, viewportHeight);
            signature = mix(signature, capturedDestination ? 1L : 0L);
            return new LastDrawInputs(
                    true,
                    signature,
                    mode,
                    depthCompositeEnabled,
                    guidedUpsampling,
                    sceneDepth.source(),
                    guidedUpsampling ? sceneDepth.textureId() : 0,
                    guidedUpsampling ? sceneDepth.width() : 0,
                    guidedUpsampling ? sceneDepth.height() : 0,
                    source.width,
                    source.height,
                    destination.width,
                    destination.height,
                    drawFramebuffer,
                    viewportX,
                    viewportY,
                    viewportWidth,
                    viewportHeight,
                    capturedDestination
            );
        }

        private static long mixString(long hash, String value) {
            if (value == null) {
                return mix(hash, 0L);
            }
            for (int index = 0; index < value.length(); index++) {
                hash = mix(hash, value.charAt(index));
            }
            return hash;
        }

        private static long mix(long hash, long value) {
            hash ^= value;
            return hash * FNV_PRIME;
        }
    }
}
