package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.client.render.depth.SceneDepthFrame;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldCompositeDebugMode;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldCompositeRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * On-demand numerical isolation of the native volume pipeline. The diagnostic
 * copies the already-rendered low-resolution color/depth targets and detached
 * scene depth through one fence-gated PBO, then reproduces the production
 * composite on a worker thread. It never draws an additional cloud pass and it
 * never waits for the GPU.
 */
public final class VolumetricStabilityDiagnostics {
    private static final int MIN_FRAMES = 2;
    private static final int MAX_FRAMES = 32;
    private static final float COLOR_ALPHA_EPSILON = 0.001F;
    private static final float ACTIVE_ALPHA_EPSILON = 0.002F;
    private static final int MACRO_GRID_SIZE = 16;
    private static final long MAX_CAPTURE_BATCH_BYTES = 128L * 1024L * 1024L;
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private static boolean captureActive;
    private static int requestedFrames;
    private static int scheduledFrames;
    private static long runId;
    private static long nextRunId = 1L;
    private static long nextCaptureId = 1L;
    private static long reservedCaptureBytes;
    private static final ArrayDeque<PendingTransfer> pendingTransfers = new ArrayDeque<>();
    private static CompletableFuture<FrameDigest> pendingAnalysis;
    private static FrameDigest comparisonBaseline;
    private static final List<FrameSummary> completedFrames = new ArrayList<>();
    private static volatile String status = "idle";
    private static volatile String latestReport = "not_captured";

    private VolumetricStabilityDiagnostics() {
    }

    public static synchronized String requestCapture(int frameCount) {
        if (captureActive || !pendingTransfers.isEmpty() || pendingAnalysis != null) {
            return "busy:" + status;
        }
        requestedFrames = Math.max(MIN_FRAMES, Math.min(MAX_FRAMES, frameCount));
        scheduledFrames = 0;
        runId = nextRunId++;
        completedFrames.clear();
        comparisonBaseline = null;
        reservedCaptureBytes = 0L;
        captureActive = true;
        status = "requested:run=" + runId + " frames=0/" + requestedFrames;
        return status;
    }

    public static String status() {
        return status;
    }

    public static String formattedLatest() {
        return "Volumetric stability status=" + status + "\n" + latestReport;
    }

    /**
     * True only for a production frame that the current on-demand batch still
     * needs. The renderer uses this to retain component-level uniform hashes
     * without allocating diagnostic records during ordinary gameplay.
     */
    static boolean captureUniformComponentsForNextFrame() {
        return captureActive && scheduledFrames < requestedFrames;
    }

    /** Polls the asynchronous transfer and worker analysis without waiting. */
    public static synchronized void poll() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }

        collectCompletedAnalysis();
        // Do not map or analyze while the requested consecutive frame batch is
        // still being acquired. Readback work must not perturb its cadence.
        if (pendingAnalysis != null || scheduledFrames < requestedFrames
                || pendingTransfers.isEmpty()) {
            return;
        }

        PendingTransfer transfer = pendingTransfers.peekFirst();
        int signaled = GL32.glClientWaitSync(transfer.fence(), 0, 0L);
        if (signaled == GL32.GL_WAIT_FAILED) {
            failRun("fence_wait_failed", null);
            return;
        }
        if (signaled != GL32.GL_ALREADY_SIGNALED && signaled != GL32.GL_CONDITION_SATISFIED) {
            return;
        }

        int previousPixelPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        boolean mappedBuffer = false;
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, transfer.pixelPackBufferId());
            ByteBuffer mapped = GL30.glMapBufferRange(
                    GL21.GL_PIXEL_PACK_BUFFER,
                    0L,
                    transfer.layout().totalBytes(),
                    GL30.GL_MAP_READ_BIT
            );
            if (mapped == null) {
                failRun("map_failed", null);
                return;
            }
            mappedBuffer = true;
            mapped.order(ByteOrder.nativeOrder());

            float[] cloudColor = readFloats(
                    mapped,
                    transfer.layout().colorOffset(),
                    transfer.layout().colorValues()
            );
            float[] cloudDepth = readFloats(
                    mapped,
                    transfer.layout().cloudDepthOffset(),
                    transfer.layout().cloudDepthValues()
            );
            float[] sceneDepth = transfer.layout().sceneDepthValues() > 0
                    ? readFloats(
                            mapped,
                            transfer.layout().sceneDepthOffset(),
                            transfer.layout().sceneDepthValues()
                    )
                    : new float[0];

            boolean dataValid = GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            mappedBuffer = false;
            if (!dataValid) {
                throw new IllegalStateException("pixel-pack buffer contents became invalid");
            }

            pendingTransfers.removeFirst();
            GL32.glDeleteSync(transfer.fence());
            GL15.glDeleteBuffers(transfer.pixelPackBufferId());
            reservedCaptureBytes -= transfer.layout().totalBytes();
            CaptureMetadata metadata = transfer.metadata();
            TransferLayout layout = transfer.layout();
            FrameDigest baseline = comparisonBaseline;
            status = "analyzing:run=" + runId
                    + " frame=" + (completedFrames.size() + 1) + "/" + requestedFrames;
            pendingAnalysis = CompletableFuture.supplyAsync(() -> analyze(
                    cloudColor,
                    cloudDepth,
                    sceneDepth,
                    layout,
                    metadata,
                    baseline
            ));
        } catch (RuntimeException exception) {
            if (mappedBuffer) {
                try {
                    GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
                } catch (RuntimeException ignored) {
                    // Preserve the original diagnostic failure.
                }
            }
            failRun("read_failed:" + exception.getClass().getSimpleName(), exception);
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPixelPackBuffer);
        }
    }

    /**
     * Dispatches one transfer after the production composite and before the
     * current/history targets are swapped.
     */
    public static synchronized void tryDispatch(
            RenderTarget cloudTarget,
            SceneDepthFrame sceneDepth,
            long hookFrameIndex,
            long gameTime,
            float partialTick,
            long weatherInputSignature,
            String qualityName,
            VolumetricCloudRenderer.LastDrawInputs raymarchInputs,
            CloudFieldCompositeRenderer.LastDrawInputs compositeInputs,
            boolean composited
    ) {
        if (!captureActive || scheduledFrames >= requestedFrames) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            status = "not_render_thread";
            return;
        }
        if (!validCloudTarget(cloudTarget)) {
            status = "waiting_for_targets";
            return;
        }
        if (!composited || raymarchInputs == null || !raymarchInputs.valid()
                || compositeInputs == null || !compositeInputs.valid()) {
            status = "waiting_for_exact_draw_inputs";
            return;
        }
        if (!compositeInputs.capturedDestination()
                || compositeInputs.viewportWidth() <= 0
                || compositeInputs.viewportHeight() <= 0) {
            failRun("composite_destination_not_captured", null);
            return;
        }
        if (compositeInputs.sourceWidth() != cloudTarget.width
                || compositeInputs.sourceHeight() != cloudTarget.height) {
            failRun("composite_source_target_mismatch", null);
            return;
        }

        SceneDepthFrame safeSceneDepth = sceneDepth == null ? SceneDepthFrame.INVALID : sceneDepth;
        boolean captureSceneDepth = compositeInputs.guidedUpsampling();
        if (captureSceneDepth && (!safeSceneDepth.valid()
                || !safeSceneDepth.detached()
                || safeSceneDepth.textureId() != compositeInputs.sceneDepthTextureId()
                || safeSceneDepth.width() != compositeInputs.sceneDepthWidth()
                || safeSceneDepth.height() != compositeInputs.sceneDepthHeight())) {
            failRun("composite_scene_depth_mismatch", null);
            return;
        }
        TransferLayout layout;
        try {
            layout = TransferLayout.create(
                    cloudTarget.width,
                    cloudTarget.height,
                    compositeInputs.viewportWidth(),
                    compositeInputs.viewportHeight(),
                    captureSceneDepth ? safeSceneDepth.width() : 0,
                    captureSceneDepth ? safeSceneDepth.height() : 0
            );
        } catch (ArithmeticException exception) {
            failRun("capture_size_overflow", exception);
            return;
        }
        long nextReservedBytes;
        try {
            nextReservedBytes = Math.addExact(reservedCaptureBytes, layout.totalBytes());
        } catch (ArithmeticException exception) {
            failRun("capture_memory_size_overflow", exception);
            return;
        }
        if (nextReservedBytes > MAX_CAPTURE_BATCH_BYTES) {
            failRun(
                    "capture_memory_limit requestedBytes=" + nextReservedBytes
                            + " limitBytes=" + MAX_CAPTURE_BATCH_BYTES
                            + " captured=" + scheduledFrames + "/" + requestedFrames,
                    null
            );
            return;
        }

        CaptureMetadata metadata = CaptureMetadata.create(
                nextCaptureId++,
                hookFrameIndex,
                gameTime,
                partialTick,
                weatherInputSignature,
                qualityName,
                raymarchInputs,
                compositeInputs,
                safeSceneDepth,
                layout,
                composited
        );

        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousPixelPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int previousPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int previousPackRowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int previousPackSkipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int previousPackSkipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int previousPackSwapBytes = GL11.glGetInteger(GL11.GL_PACK_SWAP_BYTES);
        int previousPackImageHeight = GL11.glGetInteger(GL12.GL_PACK_IMAGE_HEIGHT);
        int previousPackSkipImages = GL11.glGetInteger(GL12.GL_PACK_SKIP_IMAGES);
        int pixelPackBufferId = 0;
        long fence = 0L;
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            clearGlErrors();
            pixelPackBufferId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pixelPackBufferId);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, layout.totalBytes(), GL15.GL_STREAM_READ);
            requireNoGlError("pbo_allocate");
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, GL11.GL_FALSE);
            GL11.glPixelStorei(GL12.GL_PACK_IMAGE_HEIGHT, 0);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_IMAGES, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, cloudTarget.getColorTextureId());
            GL11.glGetTexImage(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_FLOAT,
                    layout.colorOffset()
            );
            requireNoGlError("cloud_color_read");

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, cloudTarget.getDepthTextureId());
            GL11.glGetTexImage(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_DEPTH_COMPONENT,
                    GL11.GL_FLOAT,
                    layout.cloudDepthOffset()
            );
            requireNoGlError("cloud_depth_read");

            if (captureSceneDepth) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, safeSceneDepth.textureId());
                GL11.glGetTexImage(
                        GL11.GL_TEXTURE_2D,
                        0,
                        GL11.GL_DEPTH_COMPONENT,
                        GL11.GL_FLOAT,
                        layout.sceneDepthOffset()
                );
                requireNoGlError("scene_depth_read");
            }

            fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            if (fence == 0L) {
                throw new IllegalStateException("glFenceSync returned null");
            }
            pendingTransfers.addLast(new PendingTransfer(
                    pixelPackBufferId,
                    fence,
                    layout,
                    metadata
            ));
            reservedCaptureBytes = nextReservedBytes;
            pixelPackBufferId = 0;
            fence = 0L;
            scheduledFrames++;
            status = (scheduledFrames >= requestedFrames
                    ? "batch_acquired:run="
                    : "acquiring:run=")
                    + runId + " frames=" + scheduledFrames + "/" + requestedFrames
                    + " bytes=" + reservedCaptureBytes;
        } catch (RuntimeException exception) {
            if (fence != 0L) {
                GL32.glDeleteSync(fence);
            }
            if (pixelPackBufferId > 0) {
                GL15.glDeleteBuffers(pixelPackBufferId);
            }
            failRun("dispatch_failed:" + exception.getClass().getSimpleName(), exception);
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, previousPackRowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, previousPackSkipPixels);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, previousPackSkipRows);
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, previousPackSwapBytes);
            GL11.glPixelStorei(GL12.GL_PACK_IMAGE_HEIGHT, previousPackImageHeight);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_IMAGES, previousPackSkipImages);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPixelPackBuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
    }

    public static synchronized void shutdown() {
        captureActive = false;
        releasePendingTransfers();
        if (pendingAnalysis != null) {
            pendingAnalysis.cancel(false);
            pendingAnalysis = null;
        }
        comparisonBaseline = null;
        completedFrames.clear();
        requestedFrames = 0;
        scheduledFrames = 0;
        reservedCaptureBytes = 0L;
        status = "idle";
    }

    static void selfCheck() {
        CameraCloudDensityTracker.selfCheckExactClearSettlement();
        VolumetricCloudRenderer.LastDrawInputs.selfCheckUniformComponentBranching();
        float[][] depthSpaceFixturePixels = {
                {0.0F, 0.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 0.25F, 1.0F},
                {0.0F, 0.0F, 0.50F, 1.0F},
                {0.0F, 0.0F, 0.75F, 1.0F},
                {0.20F, 0.80F, 1.0F, 1.0F},
                {0.80F, 0.20F, 1.0F, 1.0F},
                {0.20F, 0.20F, 1.0F, 1.0F},
                {0.50F, 0.50F, 1.0F, 1.0F}
        };
        float[] depthSpaceFixture = new float[depthSpaceFixturePixels.length * 4];
        for (int pixel = 0; pixel < depthSpaceFixturePixels.length; pixel++) {
            System.arraycopy(depthSpaceFixturePixels[pixel], 0, depthSpaceFixture, pixel * 4, 4);
        }
        DepthSpaceStats depthSpaceStats = classifyDepthSpaceStates(
                depthSpaceFixture,
                depthSpaceFixturePixels.length
        );
        if (depthSpaceStats.unavailable() != 1 || depthSpaceStats.offscreen() != 1
                || depthSpaceStats.missingDepth() != 1 || depthSpaceStats.noCurrentHit() != 1
                || depthSpaceStats.currentBelowHalfPreviousAtLeastHalf() != 1
                || depthSpaceStats.currentAtLeastHalfPreviousBelowHalf() != 1
                || depthSpaceStats.bothBelowHalf() != 1 || depthSpaceStats.bothAtLeastHalf() != 1
                || depthSpaceStats.evaluated() != 4
                || Math.abs(depthSpaceStats.currentConfidenceSum() - 1.70D) > 0.000001D
                || Math.abs(depthSpaceStats.previousConfidenceSum() - 1.70D) > 0.000001D
                || Math.abs(depthSpaceStats.confidenceDeltaSum() - 1.20D) > 0.000001D
                || Math.abs(depthSpaceStats.maxConfidenceDelta() - 0.60D) > 0.000001D
                || !depthSpaceStats.format().contains("currentBelowHalfPreviousAtLeastHalf=1")) {
            throw new IllegalStateException("temporal depth-space diagnostic classification failed");
        }
        int lowWidth = 4;
        int lowHeight = 4;
        int mainWidth = 8;
        int mainHeight = 8;
        TransferLayout layout = TransferLayout.create(lowWidth, lowHeight, mainWidth, mainHeight, 8, 8);
        float[] color = new float[layout.colorValues()];
        float[] cloudDepth = new float[layout.cloudDepthValues()];
        float[] sceneDepth = new float[layout.sceneDepthValues()];
        java.util.Arrays.fill(cloudDepth, 1.0F);
        java.util.Arrays.fill(sceneDepth, 1.0F);
        for (int y = 1; y < 3; y++) {
            for (int x = 1; x < 3; x++) {
                int pixel = y * lowWidth + x;
                int base = pixel * 4;
                color[base] = 0.30F;
                color[base + 1] = 0.24F;
                color[base + 2] = 0.18F;
                color[base + 3] = 0.50F;
                cloudDepth[pixel] = 0.72F;
            }
        }
        CaptureMetadata metadata = CaptureMetadata.testing(
                layout, 17L, 17L, 11L,
                CloudFieldCompositeDebugMode.FINAL, true, true
        );
        FrameDigest first = analyze(color, cloudDepth, sceneDepth, layout, metadata, null);
        FrameDigest identical = analyze(color.clone(), cloudDepth.clone(), sceneDepth.clone(), layout,
                CaptureMetadata.testing(
                        layout, 18L, 18L, 11L,
                        CloudFieldCompositeDebugMode.FINAL, true, true
                ), first);
        if (!identical.summary().pair().comparable() || identical.summary().pair().alphaRms() != 0.0D
                || identical.summary().pair().activeUnionAlphaRms() != 0.0D
                || identical.summary().pair().reconstructedAlphaRms() != 0.0D
                || identical.summary().pair().reconstructedActiveUnionAlphaRms() != 0.0D
                || !identical.summary().observedPair().comparable()
                || identical.summary().observedPair().alphaRms() != 0.0D
                || identical.summary().observedPair().activeUnionAlphaRms() != 0.0D) {
            throw new IllegalStateException("identical stability frames did not compare bit-stably");
        }
        float[] changed = color.clone();
        changed[(1 * lowWidth + 1) * 4 + 3] = 0.75F;
        FrameDigest delta = analyze(changed, cloudDepth.clone(), sceneDepth.clone(), layout,
                CaptureMetadata.testing(
                        layout, 19L, 19L, 11L,
                        CloudFieldCompositeDebugMode.FINAL, true, true
                ), identical);
        if (!delta.summary().pair().comparable() || delta.summary().pair().alphaRms() <= 0.0D
                || delta.summary().pair().activeUnionAlphaRms() <= 0.0D
                || delta.summary().pair().reconstructedAlphaRms() <= 0.0D
                || delta.summary().pair().reconstructedActiveUnionAlphaRms() <= 0.0D
                || !delta.summary().observedPair().comparable()
                || delta.summary().observedPair().alphaRms() <= 0.0D
                || delta.summary().observedPair().activeUnionAlphaRms() <= 0.0D
                || delta.summary().observedPair().reconstructedActiveUnionAlphaRms() <= 0.0D
                || delta.summary().pair().activeUnionAlphaCompared() <= 0
                || delta.summary().pair().reconstructedActiveUnionAlphaCompared() <= 0) {
            throw new IllegalStateException("known alpha delta was not detected through both boundaries");
        }
        if (first.summary().colorDepthPairedPixels() != 4
                || first.summary().premultipliedViolations() != 0) {
            throw new IllegalStateException("color/depth or premultiplied-alpha accounting is incorrect");
        }

        FrameDigest skipped = analyze(color.clone(), cloudDepth.clone(), sceneDepth.clone(), layout,
                CaptureMetadata.testing(
                        layout, 22L, 22L, 11L,
                        CloudFieldCompositeDebugMode.FINAL, true, true
                ), first);
        if (skipped.summary().pair().comparable()
                || !skipped.summary().pair().reason().startsWith("nonconsecutive")
                || skipped.summary().observedPair().comparable()) {
            throw new IllegalStateException("non-consecutive capture pair was not rejected");
        }

        FrameDigest changedObservationSignature = analyze(
                color.clone(),
                cloudDepth.clone(),
                sceneDepth.clone(),
                layout,
                CaptureMetadata.testing(
                        layout, 18L, 18L, 11L, 12L,
                        CloudFieldCompositeDebugMode.FINAL, true, true
                ),
                first
        );
        if (!changedObservationSignature.summary().pair().comparable()
                || changedObservationSignature.summary().observedPair().comparable()
                || !changedObservationSignature.summary().observedPair().reason()
                        .startsWith("observation_structure_changed")) {
            throw new IllegalStateException("changed observation signature was not rejected independently");
        }

        TransferLayout branchLayout = TransferLayout.create(2, 2, 4, 4, 4, 4);
        float[] branchColor = new float[branchLayout.colorValues()];
        float[] branchDepth = new float[branchLayout.cloudDepthValues()];
        float[] branchSceneDepth = new float[branchLayout.sceneDepthValues()];
        java.util.Arrays.fill(branchDepth, 1.0F);
        java.util.Arrays.fill(branchSceneDepth, 0.5F);
        branchColor[0] = 0.32F;
        branchColor[1] = 0.16F;
        branchColor[2] = 0.08F;
        branchColor[3] = 0.40F;
        branchDepth[0] = 0.20F;
        branchColor[4] = 0.16F;
        branchColor[5] = 0.32F;
        branchColor[6] = 0.64F;
        branchColor[7] = 0.80F;
        branchDepth[1] = 0.80F;

        CompositeFrame guided = emulateComposite(
                branchColor,
                branchDepth,
                branchSceneDepth,
                branchLayout,
                CaptureMetadata.testing(
                        branchLayout, 1L, 1L, 31L,
                        CloudFieldCompositeDebugMode.FINAL, true, true
                )
        );
        int guidedPixel = 1 * branchLayout.mainWidth() + 1;
        int farPixel = 1 * branchLayout.mainWidth() + 3;
        if (!guided.comparable()
                || Math.abs(guided.alpha()[guidedPixel] - 0.225F) > 0.000001F
                || Math.abs(guided.luminance()[guidedPixel] - 0.4706F) > 0.000001F
                || guided.selectedNeighbor()[guidedPixel] != 0
                || guided.alpha()[farPixel] != 0.0F
                || guided.summary().sceneRejectedPixels() <= 0) {
            throw new IllegalStateException("depth-guided composite parity fixture failed");
        }

        CompositeFrame bilinear = emulateComposite(
                branchColor,
                branchDepth,
                branchSceneDepth,
                branchLayout,
                CaptureMetadata.testing(
                        branchLayout, 1L, 1L, 31L,
                        CloudFieldCompositeDebugMode.FINAL, false, false
                )
        );
        if (!bilinear.comparable()
                || Math.abs(bilinear.alpha()[guidedPixel] - 0.375F) > 0.000001F
                || Math.abs(bilinear.luminance()[guidedPixel] - 0.436904F) > 0.000001F
                || Math.abs(bilinear.alpha()[farPixel] - 0.60F) > 0.000001F
                || Math.abs(bilinear.luminance()[farPixel] - 0.38636F) > 0.000001F
                || bilinear.selectedNeighbor()[guidedPixel] != -1) {
            throw new IllegalStateException("depth-off bilinear composite parity fixture failed");
        }

        FrameDigest changedSignature = analyze(
                color.clone(),
                cloudDepth.clone(),
                sceneDepth.clone(),
                layout,
                CaptureMetadata.testing(
                        layout, 18L, 18L, 12L, 11L,
                        CloudFieldCompositeDebugMode.FINAL, true, true
                ),
                first
        );
        String noPairsReport = formatReport(
                99L,
                List.of(first.summary(), changedSignature.summary())
        );
        if (!noPairsReport.contains("comparablePairs=0")
                || !noPairsReport.contains("rawAlphaRms avg/max=n/a")
                || !noPairsReport.contains("inconclusive_no_comparable_pairs")
                || !noPairsReport.contains("observedPairs=1")
                || !noPairsReport.contains("observedRawAlphaRms avg/max=0.00000000/0.00000000")) {
            throw new IllegalStateException("zero-comparable-pair report was not marked inconclusive");
        }

        float[] emptyColor = new float[layout.colorValues()];
        FrameDigest emptyFirst = analyze(
                emptyColor,
                cloudDepth.clone(),
                sceneDepth.clone(),
                layout,
                CaptureMetadata.testing(
                        layout, 30L, 30L, 41L,
                        CloudFieldCompositeDebugMode.FINAL, true, true
                ),
                null
        );
        FrameDigest emptySecond = analyze(
                emptyColor.clone(),
                cloudDepth.clone(),
                sceneDepth.clone(),
                layout,
                CaptureMetadata.testing(
                        layout, 31L, 31L, 41L,
                        CloudFieldCompositeDebugMode.FINAL, true, true
                ),
                emptyFirst
        );
        String emptyReport = formatReport(100L, List.of(emptyFirst.summary(), emptySecond.summary()));
        if (emptySecond.summary().pair().activeUnionAlphaCompared() != 0
                || emptySecond.summary().pair().reconstructedActiveUnionAlphaCompared() != 0
                || !emptyReport.contains("rawActiveUnionAlphaRms global/max/samples=n/a")
                || !emptyReport.contains("observedRawActiveUnionAlphaRms global/max/samples=n/a")) {
            throw new IllegalStateException("empty-sky active-union accounting was not reported as unavailable");
        }
    }

    private static void collectCompletedAnalysis() {
        if (pendingAnalysis == null || !pendingAnalysis.isDone()) {
            return;
        }
        try {
            FrameDigest digest = pendingAnalysis.join();
            comparisonBaseline = digest;
            completedFrames.add(digest.summary());
            pendingAnalysis = null;
            if (completedFrames.size() >= requestedFrames) {
                captureActive = false;
                latestReport = formatReport(runId, completedFrames);
                status = "ready:run=" + runId + " frames=" + completedFrames.size();
                ProjectAtmosphere.LOGGER.info(
                        "[VolumetricStabilityDiagnostics]\n{}",
                        latestReport
                );
                comparisonBaseline = null;
            } else {
                status = "waiting_next_frame:run=" + runId
                        + " frames=" + completedFrames.size() + "/" + requestedFrames;
            }
        } catch (RuntimeException exception) {
            failRun("analysis_failed:" + exception.getClass().getSimpleName(), exception);
        }
    }

    private static FrameDigest analyze(
            float[] color,
            float[] cloudDepth,
            float[] sceneDepth,
            TransferLayout layout,
            CaptureMetadata metadata,
            FrameDigest baseline
    ) {
        int lowPixels = layout.lowWidth() * layout.lowHeight();
        if (color.length != lowPixels * 4 || cloudDepth.length != lowPixels
                || sceneDepth.length != layout.sceneDepthValues()) {
            throw new IllegalArgumentException("capture buffer size mismatch");
        }

        float[] alpha = new float[lowPixels];
        float[] luminance = new float[lowPixels];
        int invalidValues = 0;
        int activePixels = 0;
        int colorOnlyPixels = 0;
        int depthOnlyPixels = 0;
        int pairedPixels = 0;
        int premultipliedViolations = 0;
        float maxPremultipliedExcess = 0.0F;
        double alphaSum = 0.0D;
        float alphaMax = 0.0F;
        long colorHash = FNV_OFFSET;
        long alphaHash = FNV_OFFSET;
        long depthHash = FNV_OFFSET;

        for (int pixel = 0; pixel < lowPixels; pixel++) {
            int base = pixel * 4;
            float r = finiteOrZero(color[base]);
            float g = finiteOrZero(color[base + 1]);
            float b = finiteOrZero(color[base + 2]);
            float a = finiteOrZero(color[base + 3]);
            float d = finiteOrOne(cloudDepth[pixel]);
            for (int channel = 0; channel < 4; channel++) {
                float raw = color[base + channel];
                if (!Float.isFinite(raw)) {
                    invalidValues++;
                }
                colorHash = mix(colorHash, quantize01(raw, 4095.0F));
            }
            if (!Float.isFinite(cloudDepth[pixel])) {
                invalidValues++;
            }
            a = clamp01(a);
            d = clamp01(d);
            alpha[pixel] = a;
            luminance[pixel] = Math.max(0.0F, r * 0.2126F + g * 0.7152F + b * 0.0722F);
            alphaHash = mix(alphaHash, quantize01(a, 4095.0F));
            depthHash = mix(depthHash, quantize01(d, 65535.0F));
            boolean hasColor = a > COLOR_ALPHA_EPSILON;
            boolean hasDepth = d < 1.0F;
            if (a > ACTIVE_ALPHA_EPSILON) {
                activePixels++;
            }
            if (hasColor && hasDepth) {
                pairedPixels++;
            } else if (hasColor) {
                colorOnlyPixels++;
            } else if (hasDepth) {
                depthOnlyPixels++;
            }
            float excess = Math.max(r, Math.max(g, b)) - a;
            if (excess > 0.002F) {
                premultipliedViolations++;
                maxPremultipliedExcess = Math.max(maxPremultipliedExcess, excess);
            }
            alphaSum += a;
            alphaMax = Math.max(alphaMax, a);
        }

        long sceneDepthHash = FNV_OFFSET;
        for (float value : sceneDepth) {
            sceneDepthHash = mix(sceneDepthHash, quantize01(value, 65535.0F));
        }
        float[] macroAlpha = macroAverage(alpha, layout.lowWidth(), layout.lowHeight());
        long macroHash = FNV_OFFSET;
        for (float value : macroAlpha) {
            macroHash = mix(macroHash, quantize01(value, 4095.0F));
        }

        CompositeFrame composite = emulateComposite(color, cloudDepth, sceneDepth, layout, metadata);
        HistoryHistogram history = metadata.raymarchView() == VolumetricCloudRaymarchDebugView.HISTORY_REJECTION
                ? classifyHistoryStates(color, lowPixels)
                : HistoryHistogram.EMPTY;
        DepthSpaceStats depthSpace = metadata.raymarchView()
                == VolumetricCloudRaymarchDebugView.HISTORY_DEPTH_SPACE
                ? classifyDepthSpaceStates(color, lowPixels)
                : DepthSpaceStats.EMPTY;

        PairStats pair = baseline == null
                ? PairStats.firstFrame()
                : compareFrames(
                        baseline,
                        metadata,
                        alpha,
                        luminance,
                        cloudDepth,
                        macroAlpha,
                        sceneDepthHash,
                        composite
                );
        PairStats observedPair = baseline == null
                ? PairStats.firstFrame()
                : observeFrames(
                        baseline,
                        metadata,
                        alpha,
                        luminance,
                        cloudDepth,
                        macroAlpha,
                        sceneDepthHash,
                        composite
                );
        FrameSummary summary = new FrameSummary(
                metadata,
                hex(colorHash),
                hex(alphaHash),
                hex(depthHash),
                hex(macroHash),
                hex(sceneDepthHash),
                hex(composite.alphaHash()),
                hex(composite.selectedHash()),
                invalidValues,
                activePixels,
                pairedPixels,
                colorOnlyPixels,
                depthOnlyPixels,
                premultipliedViolations,
                maxPremultipliedExcess,
                (float) (alphaSum / Math.max(1, lowPixels)),
                alphaMax,
                composite.summary(),
                history,
                depthSpace,
                pair,
                observedPair
        );
        return new FrameDigest(
                summary,
                alpha,
                luminance,
                cloudDepth,
                macroAlpha,
                sceneDepthHash,
                composite.alpha(),
                composite.luminance(),
                composite.selectedNeighbor()
        );
    }

    private static CompositeFrame emulateComposite(
            float[] color,
            float[] cloudDepth,
            float[] sceneDepth,
            TransferLayout layout,
            CaptureMetadata metadata
    ) {
        int mainPixels = layout.mainWidth() * layout.mainHeight();
        boolean productionComposite = metadata.compositeMode() == CloudFieldCompositeDebugMode.FINAL
                || metadata.compositeMode() == CloudFieldCompositeDebugMode.SPATIAL;
        if (!productionComposite) {
            return CompositeFrame.unavailable(
                    mainPixels,
                    "debug_mode_" + metadata.compositeMode().serializedName()
            );
        }
        if (!metadata.depthCompositeEnabled()) {
            return emulateBilinearComposite(color, layout);
        }
        if (!metadata.guidedUpsampling() || layout.sceneDepthValues() == 0) {
            return CompositeFrame.unavailable(mainPixels, "depth_test_without_detached_scene");
        }
        float[] outputAlpha = new float[mainPixels];
        float[] outputLuminance = new float[mainPixels];
        byte[] selectedNeighbor = new byte[mainPixels];
        java.util.Arrays.fill(selectedNeighbor, (byte) -1);
        int[] selectedCounts = new int[4];
        int[] acceptedCounts = new int[5];
        int sceneRejectedPixels = 0;
        double acceptedWeightSum = 0.0D;
        int reconstructedPixels = 0;
        double counterfactualAlphaErrorSum = 0.0D;
        float counterfactualAlphaErrorMax = 0.0F;
        long alphaHash = FNV_OFFSET;
        long selectedHash = FNV_OFFSET;

        int[] xs = new int[4];
        int[] ys = new int[4];
        float[] weights = new float[4];
        for (int y = 0; y < layout.mainHeight(); y++) {
            float texY = (y + 0.5F) / layout.mainHeight();
            float sourceY = texY * layout.lowHeight() - 0.5F;
            int baseY = (int) Math.floor(sourceY);
            float fractionY = sourceY - (float) Math.floor(sourceY);
            for (int x = 0; x < layout.mainWidth(); x++) {
                float texX = (x + 0.5F) / layout.mainWidth();
                float sourceX = texX * layout.lowWidth() - 0.5F;
                int baseX = (int) Math.floor(sourceX);
                float fractionX = sourceX - (float) Math.floor(sourceX);
                xs[0] = clampInt(baseX, 0, layout.lowWidth() - 1);
                xs[1] = clampInt(baseX + 1, 0, layout.lowWidth() - 1);
                xs[2] = xs[0];
                xs[3] = xs[1];
                ys[0] = clampInt(baseY, 0, layout.lowHeight() - 1);
                ys[1] = ys[0];
                ys[2] = clampInt(baseY + 1, 0, layout.lowHeight() - 1);
                ys[3] = ys[2];
                weights[0] = (1.0F - fractionX) * (1.0F - fractionY);
                weights[1] = fractionX * (1.0F - fractionY);
                weights[2] = (1.0F - fractionX) * fractionY;
                weights[3] = fractionX * fractionY;

                float sampledSceneDepth = 1.0F;
                float sceneDepthBias = 0.00002F;
                if (layout.sceneWidth() > 0 && layout.sceneHeight() > 0) {
                    int sceneX = clampInt((int) (texX * layout.sceneWidth()), 0, layout.sceneWidth() - 1);
                    int sceneY = clampInt((int) (texY * layout.sceneHeight()), 0, layout.sceneHeight() - 1);
                    sampledSceneDepth = finiteOrOne(sceneDepth[sceneY * layout.sceneWidth() + sceneX]);
                    float sceneRight = finiteOrOne(sceneDepth[
                            sceneY * layout.sceneWidth()
                                    + clampInt(sceneX + 1, 0, layout.sceneWidth() - 1)
                    ]);
                    float sceneUp = finiteOrOne(sceneDepth[
                            clampInt(sceneY + 1, 0, layout.sceneHeight() - 1) * layout.sceneWidth()
                                    + sceneX
                    ]);
                    float gradient = Math.max(
                            Math.abs(sampledSceneDepth - sceneRight),
                            Math.abs(sampledSceneDepth - sceneUp)
                    );
                    sceneDepthBias += Math.min(gradient * 0.25F, 0.00035F);
                }

                float selectedScore = -1.0F;
                float selectedDepth = 1.0F;
                int selected = -1;
                boolean opaqueNeighborhood = true;
                float continuousAlpha = 0.0F;
                boolean anySceneRejected = false;
                for (int sample = 0; sample < 4; sample++) {
                    int pixel = ys[sample] * layout.lowWidth() + xs[sample];
                    int base = pixel * 4;
                    float alpha = finiteOrZero(color[base + 3]);
                    float depth = finiteOrOne(cloudDepth[pixel]);
                    boolean hasColor = alpha > COLOR_ALPHA_EPSILON;
                    boolean hasDepth = depth < 1.0F;
                    boolean visible = layout.sceneDepthValues() == 0
                            || sampledSceneDepth >= 0.99999F
                            || depth <= sampledSceneDepth + sceneDepthBias;
                    anySceneRejected |= hasColor && hasDepth && !visible;
                    opaqueNeighborhood &= hasColor && hasDepth && visible && alpha >= 0.18F;
                    if (hasColor && hasDepth && visible) {
                        continuousAlpha += alpha * weights[sample];
                        float score = weights[sample] * alpha;
                        if (score > selectedScore) {
                            selectedScore = score;
                            selectedDepth = depth;
                            selected = sample;
                        }
                    }
                }
                if (anySceneRejected) {
                    sceneRejectedPixels++;
                }

                int outputPixel = y * layout.mainWidth() + x;
                if (selected < 0 || selectedDepth >= 1.0F) {
                    alphaHash = mix(alphaHash, 0L);
                    selectedHash = mix(selectedHash, 0L);
                    continue;
                }
                // FINAL/SPATIAL also run the fixed GL_LEQUAL test and write
                // selectedDepth. The shader's biased visibility test alone is
                // not the final framebuffer result.
                if (selectedDepth > sampledSceneDepth) {
                    if (!anySceneRejected) {
                        sceneRejectedPixels++;
                    }
                    alphaHash = mix(alphaHash, 0L);
                    selectedHash = mix(selectedHash, 0L);
                    continue;
                }

                float accumulatedR = 0.0F;
                float accumulatedG = 0.0F;
                float accumulatedB = 0.0F;
                float accumulatedAlpha = 0.0F;
                float acceptedWeight = 0.0F;
                int accepted = 0;
                float depthTolerance = Math.max(0.00002F, (1.0F - selectedDepth) * 0.08F);
                for (int sample = 0; sample < 4; sample++) {
                    int pixel = ys[sample] * layout.lowWidth() + xs[sample];
                    int base = pixel * 4;
                    float alpha = finiteOrZero(color[base + 3]);
                    float depth = finiteOrOne(cloudDepth[pixel]);
                    boolean visible = layout.sceneDepthValues() == 0
                            || sampledSceneDepth >= 0.99999F
                            || depth <= sampledSceneDepth + sceneDepthBias;
                    boolean paired = alpha > COLOR_ALPHA_EPSILON && depth < 1.0F && visible;
                    boolean sameSurface = Math.abs(depth - selectedDepth) <= depthTolerance;
                    if (paired && (sameSurface || opaqueNeighborhood)) {
                        float weight = weights[sample];
                        accumulatedR += finiteOrZero(color[base]) * weight;
                        accumulatedG += finiteOrZero(color[base + 1]) * weight;
                        accumulatedB += finiteOrZero(color[base + 2]) * weight;
                        accumulatedAlpha += alpha * weight;
                        acceptedWeight += weight;
                        accepted++;
                    }
                }

                selectedNeighbor[outputPixel] = (byte) selected;
                selectedCounts[selected]++;
                selectedHash = mix(selectedHash, selected + 1L);
                if (acceptedWeight <= 0.0001F || accumulatedAlpha <= COLOR_ALPHA_EPSILON) {
                    alphaHash = mix(alphaHash, 0L);
                    continue;
                }
                float clampedAlpha = clamp01(accumulatedAlpha);
                outputAlpha[outputPixel] = clampedAlpha;
                float straightScale = 1.0F / Math.max(accumulatedAlpha, COLOR_ALPHA_EPSILON);
                outputLuminance[outputPixel] = Math.max(0.0F,
                        accumulatedR * straightScale * 0.2126F
                                + accumulatedG * straightScale * 0.7152F
                                + accumulatedB * straightScale * 0.0722F);
                acceptedCounts[Math.min(4, accepted)]++;
                acceptedWeightSum += acceptedWeight;
                reconstructedPixels++;
                float alphaError = Math.abs(clampedAlpha - continuousAlpha);
                counterfactualAlphaErrorSum += alphaError;
                counterfactualAlphaErrorMax = Math.max(counterfactualAlphaErrorMax, alphaError);
                alphaHash = mix(alphaHash, quantize01(clampedAlpha, 4095.0F));
            }
        }

        GradientStats gradient = gridGradient(
                outputAlpha,
                layout.mainWidth(),
                layout.mainHeight(),
                layout.lowWidth(),
                layout.lowHeight()
        );
        CompositeSummary summary = new CompositeSummary(
                "depth_guided_plus_fixed_depth_test",
                true,
                reconstructedPixels,
                selectedCounts,
                acceptedCounts,
                sceneRejectedPixels,
                (float) (acceptedWeightSum / Math.max(1, reconstructedPixels)),
                (float) (counterfactualAlphaErrorSum / Math.max(1, reconstructedPixels)),
                counterfactualAlphaErrorMax,
                gradient
        );
        return new CompositeFrame(
                true,
                outputAlpha,
                outputLuminance,
                selectedNeighbor,
                alphaHash,
                selectedHash,
                summary
        );
    }

    private static CompositeFrame emulateBilinearComposite(float[] color, TransferLayout layout) {
        int mainPixels = layout.mainWidth() * layout.mainHeight();
        float[] outputAlpha = new float[mainPixels];
        float[] outputLuminance = new float[mainPixels];
        byte[] selectedNeighbor = new byte[mainPixels];
        java.util.Arrays.fill(selectedNeighbor, (byte) -1);
        long alphaHash = FNV_OFFSET;
        long selectedHash = FNV_OFFSET;
        int reconstructedPixels = 0;
        int[] xs = new int[4];
        int[] ys = new int[4];
        float[] weights = new float[4];
        for (int y = 0; y < layout.mainHeight(); y++) {
            float texY = (y + 0.5F) / layout.mainHeight();
            float sourceY = texY * layout.lowHeight() - 0.5F;
            int baseY = (int) Math.floor(sourceY);
            float fractionY = sourceY - (float) Math.floor(sourceY);
            for (int x = 0; x < layout.mainWidth(); x++) {
                float texX = (x + 0.5F) / layout.mainWidth();
                float sourceX = texX * layout.lowWidth() - 0.5F;
                int baseX = (int) Math.floor(sourceX);
                float fractionX = sourceX - (float) Math.floor(sourceX);
                xs[0] = clampInt(baseX, 0, layout.lowWidth() - 1);
                xs[1] = clampInt(baseX + 1, 0, layout.lowWidth() - 1);
                xs[2] = xs[0];
                xs[3] = xs[1];
                ys[0] = clampInt(baseY, 0, layout.lowHeight() - 1);
                ys[1] = ys[0];
                ys[2] = clampInt(baseY + 1, 0, layout.lowHeight() - 1);
                ys[3] = ys[2];
                weights[0] = (1.0F - fractionX) * (1.0F - fractionY);
                weights[1] = fractionX * (1.0F - fractionY);
                weights[2] = (1.0F - fractionX) * fractionY;
                weights[3] = fractionX * fractionY;

                float accumulatedR = 0.0F;
                float accumulatedG = 0.0F;
                float accumulatedB = 0.0F;
                float accumulatedAlpha = 0.0F;
                for (int sample = 0; sample < 4; sample++) {
                    int base = (ys[sample] * layout.lowWidth() + xs[sample]) * 4;
                    float weight = weights[sample];
                    accumulatedR += finiteOrZero(color[base]) * weight;
                    accumulatedG += finiteOrZero(color[base + 1]) * weight;
                    accumulatedB += finiteOrZero(color[base + 2]) * weight;
                    accumulatedAlpha += finiteOrZero(color[base + 3]) * weight;
                }
                int outputPixel = y * layout.mainWidth() + x;
                if (accumulatedAlpha <= COLOR_ALPHA_EPSILON) {
                    alphaHash = mix(alphaHash, 0L);
                    selectedHash = mix(selectedHash, 0L);
                    continue;
                }
                float alpha = clamp01(accumulatedAlpha);
                float straightScale = 1.0F / Math.max(accumulatedAlpha, COLOR_ALPHA_EPSILON);
                outputAlpha[outputPixel] = alpha;
                outputLuminance[outputPixel] = Math.max(0.0F,
                        accumulatedR * straightScale * 0.2126F
                                + accumulatedG * straightScale * 0.7152F
                                + accumulatedB * straightScale * 0.0722F);
                reconstructedPixels++;
                alphaHash = mix(alphaHash, quantize01(alpha, 4095.0F));
                selectedHash = mix(selectedHash, 0L);
            }
        }
        GradientStats gradient = gridGradient(
                outputAlpha,
                layout.mainWidth(),
                layout.mainHeight(),
                layout.lowWidth(),
                layout.lowHeight()
        );
        CompositeSummary summary = new CompositeSummary(
                "bilinear_depth_off",
                true,
                reconstructedPixels,
                new int[4],
                new int[5],
                0,
                1.0F,
                0.0F,
                0.0F,
                gradient
        );
        return new CompositeFrame(
                true,
                outputAlpha,
                outputLuminance,
                selectedNeighbor,
                alphaHash,
                selectedHash,
                summary
        );
    }

    private static PairStats compareFrames(
            FrameDigest baseline,
            CaptureMetadata metadata,
            float[] alpha,
            float[] luminance,
            float[] depth,
            float[] macroAlpha,
            long sceneDepthHash,
            CompositeFrame composite
    ) {
        return compareFramesInternal(
                baseline,
                metadata,
                alpha,
                luminance,
                depth,
                macroAlpha,
                sceneDepthHash,
                composite,
                false
        );
    }

    private static PairStats observeFrames(
            FrameDigest baseline,
            CaptureMetadata metadata,
            float[] alpha,
            float[] luminance,
            float[] depth,
            float[] macroAlpha,
            long sceneDepthHash,
            CompositeFrame composite
    ) {
        return compareFramesInternal(
                baseline,
                metadata,
                alpha,
                luminance,
                depth,
                macroAlpha,
                sceneDepthHash,
                composite,
                true
        );
    }

    private static PairStats compareFramesInternal(
            FrameDigest baseline,
            CaptureMetadata metadata,
            float[] alpha,
            float[] luminance,
            float[] depth,
            float[] macroAlpha,
            long sceneDepthHash,
            CompositeFrame composite,
            boolean observational
    ) {
        CaptureMetadata previousMetadata = baseline.summary().metadata();
        long hookFrameDelta = metadata.hookFrameIndex() - previousMetadata.hookFrameIndex();
        long shaderFrameDelta = metadata.shaderFrameIndex() - previousMetadata.shaderFrameIndex();
        long gameTimeDelta = metadata.gameTime() - previousMetadata.gameTime();
        if (hookFrameDelta != 1L || shaderFrameDelta != 1L) {
            return PairStats.incomparable(
                    "nonconsecutive hookDelta=" + hookFrameDelta
                            + " shaderDelta=" + shaderFrameDelta
                            + " gameDelta=" + gameTimeDelta
            );
        }
        long previousInputSignature = observational
                ? previousMetadata.observationInputSignature()
                : previousMetadata.comparisonInputSignature();
        long currentInputSignature = observational
                ? metadata.observationInputSignature()
                : metadata.comparisonInputSignature();
        if (previousInputSignature != currentInputSignature
                || baseline.alpha().length != alpha.length
                || baseline.reconstructedAlpha().length != composite.alpha().length) {
            return PairStats.incomparable(
                    (observational ? "observation_structure_changed" : "controlled_input_changed")
                            + " fields=[" + metadata.changedInputsFrom(
                                    previousMetadata,
                                    observational
                            ) + "]"
                            + " prev=" + hex(previousInputSignature)
                            + " current=" + hex(currentInputSignature)
            );
        }
        boolean sceneDepthStable = baseline.sceneDepthHash() == sceneDepthHash;
        if (!sceneDepthStable && (metadata.useSceneDepth() || metadata.guidedUpsampling())) {
            return PairStats.incomparable("scene_depth_content_changed");
        }

        DeltaStats alphaDelta = deltaStats(baseline.alpha(), alpha, null);
        boolean[] activeMask = new boolean[alpha.length];
        boolean[] denseMask = new boolean[alpha.length];
        int over002 = 0;
        int over020 = 0;
        int over100 = 0;
        int occupation002 = 0;
        int occupation020 = 0;
        int occupation100 = 0;
        for (int index = 0; index < alpha.length; index++) {
            float prior = baseline.alpha()[index];
            float current = alpha[index];
            activeMask[index] = prior > ACTIVE_ALPHA_EPSILON
                    || current > ACTIVE_ALPHA_EPSILON;
            denseMask[index] = prior > 0.02F || current > 0.02F;
            float difference = Math.abs(prior - current);
            if (difference > 0.002F) {
                over002++;
            }
            if (difference > 0.02F) {
                over020++;
            }
            if (difference > 0.10F) {
                over100++;
            }
            if ((prior > 0.002F) != (current > 0.002F)) {
                occupation002++;
            }
            if ((prior > 0.02F) != (current > 0.02F)) {
                occupation020++;
            }
            if ((prior > 0.10F) != (current > 0.10F)) {
                occupation100++;
            }
        }
        DeltaStats activeAlphaDelta = deltaStats(baseline.alpha(), alpha, activeMask);
        DeltaStats luminanceDelta = deltaStats(baseline.luminance(), luminance, denseMask);

        boolean[] depthMask = new boolean[depth.length];
        int depthPresenceMismatch = 0;
        for (int index = 0; index < depth.length; index++) {
            boolean previousPresent = baseline.depth()[index] < 1.0F;
            boolean currentPresent = depth[index] < 1.0F;
            depthMask[index] = previousPresent && currentPresent;
            if (previousPresent != currentPresent) {
                depthPresenceMismatch++;
            }
        }
        DeltaStats depthDelta = deltaStats(baseline.depth(), depth, depthMask);
        DeltaStats macroDelta = deltaStats(baseline.macroAlpha(), macroAlpha, null);
        double highFrequencyRms = highFrequencyRms(
                baseline.alpha(),
                alpha,
                baseline.macroAlpha(),
                macroAlpha,
                metadata.lowWidth(),
                metadata.lowHeight()
        );
        ShiftStats bestShift = bestIntegerShift(
                baseline.alpha(),
                alpha,
                metadata.lowWidth(),
                metadata.lowHeight(),
                4
        );

        boolean reconstructionComparable = baseline.summary().composite().comparable()
                && composite.comparable();
        DeltaStats reconstructedAlphaDelta = reconstructionComparable
                ? deltaStats(baseline.reconstructedAlpha(), composite.alpha(), null)
                : DeltaStats.EMPTY;
        boolean[] reconstructedActiveMask = new boolean[composite.alpha().length];
        if (reconstructionComparable) {
            for (int index = 0; index < reconstructedActiveMask.length; index++) {
                reconstructedActiveMask[index] = baseline.reconstructedAlpha()[index]
                        > ACTIVE_ALPHA_EPSILON
                        || composite.alpha()[index] > ACTIVE_ALPHA_EPSILON;
            }
        }
        DeltaStats reconstructedActiveAlphaDelta = reconstructionComparable
                ? deltaStats(
                        baseline.reconstructedAlpha(),
                        composite.alpha(),
                        reconstructedActiveMask
                )
                : DeltaStats.EMPTY;
        DeltaStats reconstructedLuminanceDelta = reconstructionComparable
                ? deltaStats(baseline.reconstructedLuminance(), composite.luminance(), null)
                : DeltaStats.EMPTY;
        int selectedNeighborChanges = 0;
        int selectedCompared = 0;
        byte[] previousSelected = baseline.selectedNeighbor();
        if (reconstructionComparable) {
            for (int index = 0; index < previousSelected.length; index++) {
                if (previousSelected[index] >= 0 || composite.selectedNeighbor()[index] >= 0) {
                    selectedCompared++;
                    if (previousSelected[index] != composite.selectedNeighbor()[index]) {
                        selectedNeighborChanges++;
                    }
                }
            }
        }

        return new PairStats(
                true,
                observational
                        ? "observed_excludes_frame_world_history_content"
                        : "controlled_except_frame_index_and_history_content",
                reconstructionComparable,
                hookFrameDelta,
                shaderFrameDelta,
                gameTimeDelta,
                sceneDepthStable,
                alphaDelta.mad(),
                alphaDelta.rms(),
                alphaDelta.max(),
                activeAlphaDelta.rms(),
                activeAlphaDelta.count(),
                luminanceDelta.mad(),
                luminanceDelta.rms(),
                depthDelta.mad(),
                depthDelta.rms(),
                depthPresenceMismatch,
                macroDelta.rms(),
                highFrequencyRms,
                over002,
                over020,
                over100,
                occupation002,
                occupation020,
                occupation100,
                bestShift.dx(),
                bestShift.dy(),
                bestShift.rms(),
                reconstructedAlphaDelta.mad(),
                reconstructedAlphaDelta.rms(),
                reconstructedActiveAlphaDelta.rms(),
                reconstructedActiveAlphaDelta.count(),
                reconstructedLuminanceDelta.rms(),
                selectedNeighborChanges,
                selectedCompared
        );
    }

    private static DeltaStats deltaStats(float[] first, float[] second, boolean[] mask) {
        if (first.length != second.length || (mask != null && mask.length != first.length)) {
            return DeltaStats.EMPTY;
        }
        double absoluteSum = 0.0D;
        double squareSum = 0.0D;
        double max = 0.0D;
        int count = 0;
        for (int index = 0; index < first.length; index++) {
            if (mask != null && !mask[index]) {
                continue;
            }
            double difference = Math.abs(finiteOrZero(first[index]) - finiteOrZero(second[index]));
            absoluteSum += difference;
            squareSum += difference * difference;
            max = Math.max(max, difference);
            count++;
        }
        if (count == 0) {
            return DeltaStats.EMPTY;
        }
        return new DeltaStats(
                absoluteSum / count,
                Math.sqrt(squareSum / count),
                max,
                count
        );
    }

    private static double highFrequencyRms(
            float[] previous,
            float[] current,
            float[] previousMacro,
            float[] currentMacro,
            int width,
            int height
    ) {
        double squares = 0.0D;
        int count = 0;
        for (int y = 0; y < height; y++) {
            int macroY = Math.min(MACRO_GRID_SIZE - 1, y * MACRO_GRID_SIZE / Math.max(1, height));
            for (int x = 0; x < width; x++) {
                int macroX = Math.min(MACRO_GRID_SIZE - 1, x * MACRO_GRID_SIZE / Math.max(1, width));
                int macroIndex = macroY * MACRO_GRID_SIZE + macroX;
                int pixel = y * width + x;
                double rawDelta = current[pixel] - previous[pixel];
                double macroDelta = currentMacro[macroIndex] - previousMacro[macroIndex];
                double residual = rawDelta - macroDelta;
                squares += residual * residual;
                count++;
            }
        }
        return count == 0 ? 0.0D : Math.sqrt(squares / count);
    }

    private static ShiftStats bestIntegerShift(
            float[] previous,
            float[] current,
            int width,
            int height,
            int radius
    ) {
        ShiftStats best = new ShiftStats(0, 0, Double.POSITIVE_INFINITY);
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double squares = 0.0D;
                int count = 0;
                for (int y = Math.max(0, -dy); y < Math.min(height, height - dy); y += 2) {
                    for (int x = Math.max(0, -dx); x < Math.min(width, width - dx); x += 2) {
                        float first = previous[y * width + x];
                        float second = current[(y + dy) * width + x + dx];
                        double difference = first - second;
                        squares += difference * difference;
                        count++;
                    }
                }
                double rms = count == 0 ? Double.POSITIVE_INFINITY : Math.sqrt(squares / count);
                if (rms < best.rms()) {
                    best = new ShiftStats(dx, dy, rms);
                }
            }
        }
        return best;
    }

    private static float[] macroAverage(float[] values, int width, int height) {
        float[] result = new float[MACRO_GRID_SIZE * MACRO_GRID_SIZE];
        int[] counts = new int[result.length];
        for (int y = 0; y < height; y++) {
            int macroY = Math.min(MACRO_GRID_SIZE - 1, y * MACRO_GRID_SIZE / Math.max(1, height));
            for (int x = 0; x < width; x++) {
                int macroX = Math.min(MACRO_GRID_SIZE - 1, x * MACRO_GRID_SIZE / Math.max(1, width));
                int macroIndex = macroY * MACRO_GRID_SIZE + macroX;
                result[macroIndex] += values[y * width + x];
                counts[macroIndex]++;
            }
        }
        for (int index = 0; index < result.length; index++) {
            result[index] /= Math.max(1, counts[index]);
        }
        return result;
    }

    private static GradientStats gridGradient(
            float[] alpha,
            int width,
            int height,
            int lowWidth,
            int lowHeight
    ) {
        double boundarySum = 0.0D;
        double interiorSum = 0.0D;
        int boundaryCount = 0;
        int interiorCount = 0;
        for (int y = 0; y < height; y++) {
            int sourceY = sourceCell(y, height, lowHeight);
            for (int x = 1; x < width; x++) {
                int pixel = y * width + x;
                float difference = Math.abs(alpha[pixel] - alpha[pixel - 1]);
                if (sourceCell(x, width, lowWidth) != sourceCell(x - 1, width, lowWidth)) {
                    boundarySum += difference;
                    boundaryCount++;
                } else {
                    interiorSum += difference;
                    interiorCount++;
                }
            }
            if (y == 0) {
                continue;
            }
            int previousSourceY = sourceCell(y - 1, height, lowHeight);
            for (int x = 0; x < width; x++) {
                int pixel = y * width + x;
                float difference = Math.abs(alpha[pixel] - alpha[pixel - width]);
                if (sourceY != previousSourceY) {
                    boundarySum += difference;
                    boundaryCount++;
                } else {
                    interiorSum += difference;
                    interiorCount++;
                }
            }
        }
        double boundaryMean = boundarySum / Math.max(1, boundaryCount);
        double interiorMean = interiorSum / Math.max(1, interiorCount);
        return new GradientStats(
                boundaryMean,
                interiorMean,
                boundaryMean / Math.max(0.000000001D, interiorMean)
        );
    }

    private static int sourceCell(int outputCoordinate, int outputSize, int sourceSize) {
        float texCoord = (outputCoordinate + 0.5F) / Math.max(1, outputSize);
        return clampInt((int) Math.floor(texCoord * sourceSize - 0.5F), 0, sourceSize - 1);
    }

    private static HistoryHistogram classifyHistoryStates(float[] color, int pixelCount) {
        int[] counts = new int[8];
        for (int pixel = 0; pixel < pixelCount; pixel++) {
            int base = pixel * 4;
            float alpha = finiteOrZero(color[base + 3]);
            if (alpha <= COLOR_ALPHA_EPSILON) {
                counts[7]++;
                continue;
            }
            int state = nearestHistoryState(
                    finiteOrZero(color[base]),
                    finiteOrZero(color[base + 1]),
                    finiteOrZero(color[base + 2])
            );
            counts[state - 1]++;
        }
        return new HistoryHistogram(counts);
    }

    private static int nearestHistoryState(float r, float g, float b) {
        float[][] colors = {
                {0.12F, 0.22F, 0.92F},
                {0.92F, 0.12F, 0.82F},
                {0.95F, 0.58F, 0.08F},
                {0.95F, 0.16F, 0.10F},
                {0.10F, 0.78F, 0.92F},
                {0.78F, 0.82F, 0.16F},
                {0.12F, 0.92F, 0.24F},
                {1.0F, 1.0F, 1.0F}
        };
        int[] states = {1, 2, 3, 4, 5, 6, 6, 7};
        int bestState = 1;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (int index = 0; index < colors.length; index++) {
            float dr = r - colors[index][0];
            float dg = g - colors[index][1];
            float db = b - colors[index][2];
            float distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestState = states[index];
            }
        }
        return bestState;
    }

    private static DepthSpaceStats classifyDepthSpaceStates(float[] color, int pixelCount) {
        int unavailable = 0;
        int offscreen = 0;
        int missingDepth = 0;
        int currentBelowHalfPreviousAtLeastHalf = 0;
        int currentAtLeastHalfPreviousBelowHalf = 0;
        int bothBelowHalf = 0;
        int bothAtLeastHalf = 0;
        int noCurrentHit = 0;
        int evaluated = 0;
        double currentConfidenceSum = 0.0D;
        double previousConfidenceSum = 0.0D;
        double confidenceDeltaSum = 0.0D;
        double maxConfidenceDelta = 0.0D;
        for (int pixel = 0; pixel < pixelCount; pixel++) {
            int base = pixel * 4;
            float alpha = finiteOrZero(color[base + 3]);
            if (alpha <= COLOR_ALPHA_EPSILON) {
                noCurrentHit++;
                continue;
            }
            float status = finiteOrZero(color[base + 2]);
            if (status < 0.375F) {
                unavailable++;
                continue;
            }
            if (status < 0.625F) {
                offscreen++;
                continue;
            }
            if (status < 0.875F) {
                missingDepth++;
                continue;
            }

            float currentConfidence = clamp01(finiteOrZero(color[base]));
            float previousConfidence = clamp01(finiteOrZero(color[base + 1]));
            boolean currentAccepted = currentConfidence >= 0.50F;
            boolean previousAccepted = previousConfidence >= 0.50F;
            if (currentAccepted && previousAccepted) {
                bothAtLeastHalf++;
            } else if (currentAccepted) {
                currentAtLeastHalfPreviousBelowHalf++;
            } else if (previousAccepted) {
                currentBelowHalfPreviousAtLeastHalf++;
            } else {
                bothBelowHalf++;
            }
            double confidenceDelta = Math.abs(currentConfidence - previousConfidence);
            evaluated++;
            currentConfidenceSum += currentConfidence;
            previousConfidenceSum += previousConfidence;
            confidenceDeltaSum += confidenceDelta;
            maxConfidenceDelta = Math.max(maxConfidenceDelta, confidenceDelta);
        }
        return new DepthSpaceStats(
                unavailable,
                offscreen,
                missingDepth,
                currentBelowHalfPreviousAtLeastHalf,
                currentAtLeastHalfPreviousBelowHalf,
                bothBelowHalf,
                bothAtLeastHalf,
                noCurrentHit,
                evaluated,
                currentConfidenceSum,
                previousConfidenceSum,
                confidenceDeltaSum,
                maxConfidenceDelta
        );
    }

    private static String formatReport(long reportRunId, List<FrameSummary> frames) {
        StringBuilder builder = new StringBuilder("Volumetric stability run ")
                .append(reportRunId)
                .append(" frames=").append(frames.size());
        int comparablePairs = 0;
        int reconstructedComparablePairs = 0;
        int sceneStablePairs = 0;
        double alphaRmsSum = 0.0D;
        double alphaRmsMax = 0.0D;
        double activeUnionAlphaSquareSum = 0.0D;
        double activeUnionAlphaRmsMax = 0.0D;
        long activeUnionAlphaSamples = 0L;
        double reconstructedRmsSum = 0.0D;
        double reconstructedRmsMax = 0.0D;
        double reconstructedActiveUnionSquareSum = 0.0D;
        double reconstructedActiveUnionRmsMax = 0.0D;
        long reconstructedActiveUnionSamples = 0L;
        int observedPairs = 0;
        int observedReconstructedPairs = 0;
        double observedAlphaRmsSum = 0.0D;
        double observedAlphaRmsMax = 0.0D;
        double observedActiveUnionAlphaSquareSum = 0.0D;
        double observedActiveUnionAlphaRmsMax = 0.0D;
        long observedActiveUnionAlphaSamples = 0L;
        double observedReconstructedRmsSum = 0.0D;
        double observedReconstructedRmsMax = 0.0D;
        double observedReconstructedActiveUnionSquareSum = 0.0D;
        double observedReconstructedActiveUnionRmsMax = 0.0D;
        long observedReconstructedActiveUnionSamples = 0L;
        DepthSpaceStats aggregateDepthSpace = DepthSpaceStats.EMPTY;
        for (int index = 0; index < frames.size(); index++) {
            FrameSummary frame = frames.get(index);
            builder.append("\n\n[").append(index + 1).append("] ").append(frame.format());
            aggregateDepthSpace = aggregateDepthSpace.plus(frame.depthSpace());
            if (frame.pair().comparable()) {
                comparablePairs++;
                if (frame.pair().sceneDepthStable()) {
                    sceneStablePairs++;
                }
                alphaRmsSum += frame.pair().alphaRms();
                alphaRmsMax = Math.max(alphaRmsMax, frame.pair().alphaRms());
                int activeSamples = frame.pair().activeUnionAlphaCompared();
                activeUnionAlphaSquareSum += frame.pair().activeUnionAlphaRms()
                        * frame.pair().activeUnionAlphaRms() * activeSamples;
                activeUnionAlphaSamples += activeSamples;
                activeUnionAlphaRmsMax = Math.max(
                        activeUnionAlphaRmsMax,
                        frame.pair().activeUnionAlphaRms()
                );
                if (frame.pair().reconstructionComparable()) {
                    reconstructedComparablePairs++;
                    reconstructedRmsSum += frame.pair().reconstructedAlphaRms();
                    reconstructedRmsMax = Math.max(
                            reconstructedRmsMax,
                            frame.pair().reconstructedAlphaRms()
                    );
                    int reconstructedActiveSamples = frame.pair().reconstructedActiveUnionAlphaCompared();
                    reconstructedActiveUnionSquareSum += frame.pair().reconstructedActiveUnionAlphaRms()
                            * frame.pair().reconstructedActiveUnionAlphaRms() * reconstructedActiveSamples;
                    reconstructedActiveUnionSamples += reconstructedActiveSamples;
                    reconstructedActiveUnionRmsMax = Math.max(
                            reconstructedActiveUnionRmsMax,
                            frame.pair().reconstructedActiveUnionAlphaRms()
                    );
                }
            }
            if (frame.observedPair().comparable()) {
                observedPairs++;
                observedAlphaRmsSum += frame.observedPair().alphaRms();
                observedAlphaRmsMax = Math.max(
                        observedAlphaRmsMax,
                        frame.observedPair().alphaRms()
                );
                int observedActiveSamples = frame.observedPair().activeUnionAlphaCompared();
                observedActiveUnionAlphaSquareSum += frame.observedPair().activeUnionAlphaRms()
                        * frame.observedPair().activeUnionAlphaRms() * observedActiveSamples;
                observedActiveUnionAlphaSamples += observedActiveSamples;
                observedActiveUnionAlphaRmsMax = Math.max(
                        observedActiveUnionAlphaRmsMax,
                        frame.observedPair().activeUnionAlphaRms()
                );
                if (frame.observedPair().reconstructionComparable()) {
                    observedReconstructedPairs++;
                    observedReconstructedRmsSum += frame.observedPair().reconstructedAlphaRms();
                    observedReconstructedRmsMax = Math.max(
                            observedReconstructedRmsMax,
                            frame.observedPair().reconstructedAlphaRms()
                    );
                    int observedReconstructedActiveSamples =
                            frame.observedPair().reconstructedActiveUnionAlphaCompared();
                    observedReconstructedActiveUnionSquareSum +=
                            frame.observedPair().reconstructedActiveUnionAlphaRms()
                                    * frame.observedPair().reconstructedActiveUnionAlphaRms()
                                    * observedReconstructedActiveSamples;
                    observedReconstructedActiveUnionSamples += observedReconstructedActiveSamples;
                    observedReconstructedActiveUnionRmsMax = Math.max(
                            observedReconstructedActiveUnionRmsMax,
                            frame.observedPair().reconstructedActiveUnionAlphaRms()
                    );
                }
            }
        }
        builder.append("\n\nAggregate")
                .append("\ncomparablePairs=").append(comparablePairs)
                .append(" reconstructedComparablePairs=").append(reconstructedComparablePairs)
                .append(" sceneDepthStablePairs=").append(sceneStablePairs)
                .append("\nrawAlphaRms avg/max=");
        if (comparablePairs == 0) {
            builder.append("n/a\nconclusion=inconclusive_no_comparable_pairs");
        } else {
            builder.append(fmt(alphaRmsSum / comparablePairs))
                    .append("/").append(fmt(alphaRmsMax));
        }
        builder.append("\nrawActiveUnionAlphaRms global/max/samples=");
        if (activeUnionAlphaSamples == 0L) {
            builder.append("n/a");
        } else {
            builder.append(fmt(Math.sqrt(activeUnionAlphaSquareSum / activeUnionAlphaSamples)))
                    .append("/").append(fmt(activeUnionAlphaRmsMax))
                    .append("/").append(activeUnionAlphaSamples);
        }
        builder.append("\nreconstructedAlphaRms avg/max=");
        if (reconstructedComparablePairs == 0) {
            builder.append("n/a");
        } else {
            builder.append(fmt(reconstructedRmsSum / reconstructedComparablePairs))
                    .append("/").append(fmt(reconstructedRmsMax));
        }
        builder.append("\nreconstructedActiveUnionAlphaRms global/max/samples=");
        if (reconstructedActiveUnionSamples == 0L) {
            builder.append("n/a");
        } else {
            builder.append(fmt(Math.sqrt(
                            reconstructedActiveUnionSquareSum / reconstructedActiveUnionSamples
                    )))
                    .append("/").append(fmt(reconstructedActiveUnionRmsMax))
                    .append("/").append(reconstructedActiveUnionSamples);
        }
        builder.append("\nobservedPairs=").append(observedPairs)
                .append(" observedReconstructedPairs=").append(observedReconstructedPairs)
                .append("\nobservedRawAlphaRms avg/max=");
        if (observedPairs == 0) {
            builder.append("n/a");
        } else {
            builder.append(fmt(observedAlphaRmsSum / observedPairs))
                    .append("/").append(fmt(observedAlphaRmsMax));
        }
        builder.append("\nobservedRawActiveUnionAlphaRms global/max/samples=");
        if (observedActiveUnionAlphaSamples == 0L) {
            builder.append("n/a");
        } else {
            builder.append(fmt(Math.sqrt(
                            observedActiveUnionAlphaSquareSum / observedActiveUnionAlphaSamples
                    )))
                    .append("/").append(fmt(observedActiveUnionAlphaRmsMax))
                    .append("/").append(observedActiveUnionAlphaSamples);
        }
        builder.append("\nobservedReconstructedAlphaRms avg/max=");
        if (observedReconstructedPairs == 0) {
            builder.append("n/a");
        } else {
            builder.append(fmt(observedReconstructedRmsSum / observedReconstructedPairs))
                    .append("/").append(fmt(observedReconstructedRmsMax));
        }
        builder.append("\nobservedReconstructedActiveUnionAlphaRms global/max/samples=");
        if (observedReconstructedActiveUnionSamples == 0L) {
            builder.append("n/a");
        } else {
            builder.append(fmt(Math.sqrt(
                            observedReconstructedActiveUnionSquareSum
                                    / observedReconstructedActiveUnionSamples
                    )))
                    .append("/").append(fmt(observedReconstructedActiveUnionRmsMax))
                    .append("/").append(observedReconstructedActiveUnionSamples);
        }
        if (aggregateDepthSpace != DepthSpaceStats.EMPTY) {
            builder.append("\ndepthSpaceAggregate ").append(aggregateDepthSpace.format());
        }
        builder.append("\nobservedCaveat=FrameIndex,WorldTime,and_history_color_depth_sampler_contents_are_not_controlled;other_sampler_contents_are_assumed_immutable");
        return builder.toString();
    }

    private static float[] readFloats(ByteBuffer mapped, long byteOffset, int valueCount) {
        if (byteOffset < 0L || byteOffset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("mapped offset exceeds ByteBuffer range");
        }
        ByteBuffer view = mapped.duplicate().order(ByteOrder.nativeOrder());
        view.position(Math.toIntExact(byteOffset));
        FloatBuffer floats = view.slice().order(ByteOrder.nativeOrder()).asFloatBuffer();
        float[] values = new float[valueCount];
        floats.get(values);
        return values;
    }

    private static void failRun(String reason, RuntimeException exception) {
        captureActive = false;
        releasePendingTransfers();
        if (pendingAnalysis != null) {
            pendingAnalysis.cancel(false);
            pendingAnalysis = null;
        }
        comparisonBaseline = null;
        reservedCaptureBytes = 0L;
        status = reason;
        if (exception == null) {
            ProjectAtmosphere.LOGGER.warn("[VolumetricStabilityDiagnostics] {}", reason);
        } else {
            ProjectAtmosphere.LOGGER.warn("[VolumetricStabilityDiagnostics] " + reason, exception);
        }
    }

    private static void releasePendingTransfers() {
        PendingTransfer transfer;
        while ((transfer = pendingTransfers.pollFirst()) != null) {
            if (transfer.fence() != 0L) {
                GL32.glDeleteSync(transfer.fence());
            }
            if (transfer.pixelPackBufferId() > 0) {
                GL15.glDeleteBuffers(transfer.pixelPackBufferId());
            }
        }
    }

    private static boolean validCloudTarget(RenderTarget target) {
        return validColorTarget(target) && target.getDepthTextureId() > 0;
    }

    private static boolean validColorTarget(RenderTarget target) {
        return target != null && target.width > 0 && target.height > 0
                && target.getColorTextureId() > 0;
    }

    private static void clearGlErrors() {
        for (int index = 0; index < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; index++) {
            // Attribute only errors produced by this explicit diagnostic boundary.
        }
    }

    private static void requireNoGlError(String operation) {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            throw new IllegalStateException(operation + "_gl_error_0x" + Integer.toHexString(error));
        }
    }

    private static long mixString(long hash, String value) {
        String safe = value == null ? "" : value;
        for (int index = 0; index < safe.length(); index++) {
            hash = mix(hash, safe.charAt(index));
        }
        return hash;
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * FNV_PRIME;
    }

    private static long quantize01(float value, float scale) {
        if (!Float.isFinite(value)) {
            return 0L;
        }
        return Math.round(clamp01(value) * scale);
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static float finiteOrOne(float value) {
        return Float.isFinite(value) ? value : 1.0F;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int clampInt(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String hex(long value) {
        return String.format(Locale.ROOT, "%016x", value);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private record PendingTransfer(
            int pixelPackBufferId,
            long fence,
            TransferLayout layout,
            CaptureMetadata metadata
    ) {
    }

    private record TransferLayout(
            int lowWidth,
            int lowHeight,
            int mainWidth,
            int mainHeight,
            int sceneWidth,
            int sceneHeight,
            long colorOffset,
            int colorValues,
            long cloudDepthOffset,
            int cloudDepthValues,
            long sceneDepthOffset,
            int sceneDepthValues,
            long totalBytes
    ) {
        private static final TransferLayout EMPTY = new TransferLayout(
                0, 0, 0, 0, 0, 0,
                0L, 0, 0L, 0, 0L, 0, 0L
        );

        static TransferLayout create(
                int lowWidth,
                int lowHeight,
                int mainWidth,
                int mainHeight,
                int sceneWidth,
                int sceneHeight
        ) {
            int lowPixels = Math.multiplyExact(lowWidth, lowHeight);
            int colorValues = Math.multiplyExact(lowPixels, 4);
            int sceneValues = sceneWidth > 0 && sceneHeight > 0
                    ? Math.multiplyExact(sceneWidth, sceneHeight)
                    : 0;
            long colorBytes = Math.multiplyExact((long) colorValues, Float.BYTES);
            long cloudDepthBytes = Math.multiplyExact((long) lowPixels, Float.BYTES);
            long sceneDepthBytes = Math.multiplyExact((long) sceneValues, Float.BYTES);
            long cloudDepthOffset = colorBytes;
            long sceneDepthOffset = Math.addExact(cloudDepthOffset, cloudDepthBytes);
            long totalBytes = Math.addExact(sceneDepthOffset, sceneDepthBytes);
            if (totalBytes > Integer.MAX_VALUE) {
                throw new ArithmeticException("diagnostic PBO exceeds addressable ByteBuffer size");
            }
            return new TransferLayout(
                    lowWidth,
                    lowHeight,
                    mainWidth,
                    mainHeight,
                    sceneWidth,
                    sceneHeight,
                    0L,
                    colorValues,
                    cloudDepthOffset,
                    lowPixels,
                    sceneDepthOffset,
                    sceneValues,
                    totalBytes
            );
        }
    }

    private record CaptureMetadata(
            long captureId,
            long hookFrameIndex,
            long shaderFrameIndex,
            long gameTime,
            float partialTick,
            float worldTimeTicks,
            long weatherInputSignature,
            long uniformSignature,
            long comparisonInputSignature,
            long observationInputSignature,
            long uniformStructuralSignature,
            long rayComparisonUniformSignature,
            long rayObservationUniformSignature,
            long compositeSignature,
            VolumetricCloudRenderer.LastDrawInputs.UniformComponentSignatures
                    comparisonUniformComponents,
            VolumetricCloudRenderer.LastDrawInputs.UniformComponentSignatures
                    observationUniformComponents,
            String quality,
            VolumetricCloudRaymarchDebugView raymarchView,
            CloudFieldCompositeDebugMode compositeMode,
            String sceneDepthSource,
            int lowWidth,
            int lowHeight,
            int mainWidth,
            int mainHeight,
            int drawFramebuffer,
            int viewportX,
            int viewportY,
            float materialOffsetX,
            float materialOffsetZ,
            float materialDeltaX,
            float materialDeltaZ,
            float windX,
            float windY,
            float windZ,
            float stepScale,
            float cameraDensity,
            float maxPrecipitation,
            float maxRenderDistance,
            boolean worldTimeAffectsDensity,
            boolean historyConsumed,
            float historyBlend,
            boolean useSceneDepth,
            boolean depthCompositeEnabled,
            boolean guidedUpsampling,
            boolean composited
    ) {
        static CaptureMetadata create(
                long captureId,
                long hookFrameIndex,
                long gameTime,
                float partialTick,
                long weatherInputSignature,
                String qualityName,
                VolumetricCloudRenderer.LastDrawInputs raymarch,
                CloudFieldCompositeRenderer.LastDrawInputs composite,
                SceneDepthFrame sceneDepth,
                TransferLayout layout,
                boolean composited
        ) {
            long comparison = mix(FNV_OFFSET, weatherInputSignature);
            comparison = mix(comparison, raymarch.comparisonUniformSignature());
            comparison = mix(comparison, composite.signature());
            comparison = mix(comparison, layout.lowWidth());
            comparison = mix(comparison, layout.lowHeight());
            comparison = mix(comparison, layout.mainWidth());
            comparison = mix(comparison, layout.mainHeight());
            comparison = mix(comparison, layout.sceneWidth());
            comparison = mix(comparison, layout.sceneHeight());
            comparison = mixString(comparison, sceneDepth.source());
            comparison = mix(comparison, composited ? 1L : 0L);

            long observation = mix(FNV_OFFSET, weatherInputSignature);
            observation = mix(observation, raymarch.observationUniformSignature());
            observation = mix(observation, composite.signature());
            observation = mix(observation, layout.lowWidth());
            observation = mix(observation, layout.lowHeight());
            observation = mix(observation, layout.mainWidth());
            observation = mix(observation, layout.mainHeight());
            observation = mix(observation, layout.sceneWidth());
            observation = mix(observation, layout.sceneHeight());
            observation = mixString(observation, sceneDepth.source());
            observation = mix(observation, composited ? 1L : 0L);

            long full = mix(FNV_OFFSET, weatherInputSignature);
            full = mix(full, raymarch.uniformSignature());
            full = mix(full, composite.signature());
            full = mix(full, layout.lowWidth());
            full = mix(full, layout.lowHeight());
            full = mix(full, layout.mainWidth());
            full = mix(full, layout.mainHeight());
            full = mix(full, layout.sceneWidth());
            full = mix(full, layout.sceneHeight());
            full = mixString(full, sceneDepth.source());
            full = mix(full, composited ? 1L : 0L);

            return new CaptureMetadata(
                    captureId,
                    hookFrameIndex,
                    raymarch.frameIndex(),
                    gameTime,
                    partialTick,
                    raymarch.worldTimeTicks(),
                    weatherInputSignature,
                    raymarch.uniformSignature(),
                    comparison,
                    observation,
                    full,
                    raymarch.comparisonUniformSignature(),
                    raymarch.observationUniformSignature(),
                    composite.signature(),
                    raymarch.comparisonUniformComponents(),
                    raymarch.observationUniformComponents(),
                    qualityName == null ? "unknown" : qualityName,
                    raymarch.debugView(),
                    composite.mode(),
                    composite.guidedUpsampling() ? sceneDepth.source() : "unused",
                    layout.lowWidth(),
                    layout.lowHeight(),
                    layout.mainWidth(),
                    layout.mainHeight(),
                    composite.drawFramebuffer(),
                    composite.viewportX(),
                    composite.viewportY(),
                    raymarch.materialOffsetX(),
                    raymarch.materialOffsetZ(),
                    raymarch.materialFrameDeltaX(),
                    raymarch.materialFrameDeltaZ(),
                    raymarch.windX(),
                    raymarch.windY(),
                    raymarch.windZ(),
                    raymarch.stepScale(),
                    raymarch.cameraCloudDensity(),
                    raymarch.maxPrecipitation(),
                    raymarch.maxRenderDistance(),
                    raymarch.worldTimeAffectsDensity(),
                    raymarch.historyValid(),
                    raymarch.historyBlend(),
                    raymarch.useSceneDepth(),
                    composite.depthCompositeEnabled(),
                    composite.guidedUpsampling(),
                    composited
            );
        }

        static CaptureMetadata testing(
                TransferLayout layout,
                long hookFrameIndex,
                long shaderFrameIndex,
                long comparisonInputSignature,
                CloudFieldCompositeDebugMode compositeMode,
                boolean depthCompositeEnabled,
                boolean guidedUpsampling
        ) {
            return testing(
                    layout,
                    hookFrameIndex,
                    shaderFrameIndex,
                    comparisonInputSignature,
                    comparisonInputSignature,
                    compositeMode,
                    depthCompositeEnabled,
                    guidedUpsampling
            );
        }

        static CaptureMetadata testing(
                TransferLayout layout,
                long hookFrameIndex,
                long shaderFrameIndex,
                long comparisonInputSignature,
                long observationInputSignature,
                CloudFieldCompositeDebugMode compositeMode,
                boolean depthCompositeEnabled,
                boolean guidedUpsampling
        ) {
            return new CaptureMetadata(
                    shaderFrameIndex,
                    hookFrameIndex,
                    shaderFrameIndex,
                    shaderFrameIndex,
                    0.0F,
                    shaderFrameIndex,
                    7L,
                    shaderFrameIndex,
                    comparisonInputSignature,
                    observationInputSignature,
                    shaderFrameIndex,
                    comparisonInputSignature,
                    observationInputSignature,
                    comparisonInputSignature,
                    VolumetricCloudRenderer.LastDrawInputs.UniformComponentSignatures.EMPTY,
                    VolumetricCloudRenderer.LastDrawInputs.UniformComponentSignatures.EMPTY,
                    "test",
                    VolumetricCloudRaymarchDebugView.FINAL,
                    compositeMode,
                    guidedUpsampling ? "test" : "unused",
                    layout.lowWidth(),
                    layout.lowHeight(),
                    layout.mainWidth(),
                    layout.mainHeight(),
                    1,
                    0,
                    0,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    1.0F,
                    0.0F,
                    0.0F,
                    600.0F,
                    false,
                    false,
                    0.0F,
                    false,
                    depthCompositeEnabled,
                    guidedUpsampling,
                    true
            );
        }

        String changedInputsFrom(CaptureMetadata previous, boolean observational) {
            StringBuilder changes = new StringBuilder();
            if (weatherInputSignature != previous.weatherInputSignature) {
                appendChangedInput(changes, "WeatherTexture");
            }
            long previousRay = observational
                    ? previous.rayObservationUniformSignature
                    : previous.rayComparisonUniformSignature;
            long currentRay = observational
                    ? rayObservationUniformSignature
                    : rayComparisonUniformSignature;
            if (previousRay != currentRay) {
                VolumetricCloudRenderer.LastDrawInputs.UniformComponentSignatures previousComponents =
                        observational
                                ? previous.observationUniformComponents
                                : previous.comparisonUniformComponents;
                VolumetricCloudRenderer.LastDrawInputs.UniformComponentSignatures currentComponents =
                        observational
                                ? observationUniformComponents
                                : comparisonUniformComponents;
                appendChangedInput(
                        changes,
                        "RayUniforms{" + currentComponents.changesFrom(previousComponents) + "}"
                );
            }
            if (compositeSignature != previous.compositeSignature) {
                appendChangedInput(changes, "CompositeInputs");
            }
            if (lowWidth != previous.lowWidth || lowHeight != previous.lowHeight
                    || mainWidth != previous.mainWidth || mainHeight != previous.mainHeight) {
                appendChangedInput(changes, "TargetLayout");
            }
            if (!sceneDepthSource.equals(previous.sceneDepthSource)) {
                appendChangedInput(changes, "SceneDepthSource");
            }
            if (composited != previous.composited) {
                appendChangedInput(changes, "CompositePresence");
            }
            return changes.length() == 0 ? "UnclassifiedAggregate" : changes.toString();
        }

        private static void appendChangedInput(StringBuilder builder, String value) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(value);
        }

        static CaptureMetadata unknown() {
            return testing(
                    TransferLayout.EMPTY,
                    0L,
                    0L,
                    0L,
                    CloudFieldCompositeDebugMode.FINAL,
                    true,
                    false
            );
        }

        String format() {
            return "capture=" + captureId
                    + " hook/shader/game=" + hookFrameIndex + "/" + shaderFrameIndex + "/" + gameTime
                    + " partial=" + fmt(partialTick)
                    + " worldTime=" + fmt(worldTimeTicks)
                    + " worldTimeAffectsDensity=" + worldTimeAffectsDensity
                    + " weather=" + hex(weatherInputSignature)
                    + " uniform=" + hex(uniformSignature)
                    + " comparable=" + hex(comparisonInputSignature)
                    + " observed=" + hex(observationInputSignature)
                    + " fullInput=" + hex(uniformStructuralSignature)
                    + "\ninputParts rayComparable=" + hex(rayComparisonUniformSignature)
                    + " rayObserved=" + hex(rayObservationUniformSignature)
                    + " composite=" + hex(compositeSignature)
                    + "\ncomparisonComponents " + comparisonUniformComponents.compact()
                    + "\nobservationComponents " + observationUniformComponents.compact()
                    + "\nmode raymarch/composite=" + raymarchView.serializedName()
                    + "/" + compositeMode.serializedName()
                    + " quality=" + quality
                    + " target=" + lowWidth + "x" + lowHeight
                    + " -> viewport=" + viewportX + "," + viewportY + " " + mainWidth + "x" + mainHeight
                    + " fbo=" + drawFramebuffer
                    + " sceneDepth=" + sceneDepthSource
                    + " depthComposite/guided=" + depthCompositeEnabled + "/" + guidedUpsampling
                    + " composited=" + composited
                    + "\nhistory consumed/blend=" + historyConsumed + "/" + fmt(historyBlend)
                    + " sceneRayLimit=" + useSceneDepth
                    + " stepScale=" + fmt(stepScale)
                    + " cameraDensity=" + fmt(cameraDensity)
                    + " maxPrecip/distance=" + fmt(maxPrecipitation) + "/" + fmt(maxRenderDistance)
                    + " offset=" + fmt(materialOffsetX) + "," + fmt(materialOffsetZ)
                    + " delta=" + fmt(materialDeltaX) + "," + fmt(materialDeltaZ)
                    + " wind=" + fmt(windX) + "," + fmt(windY) + "," + fmt(windZ);
        }
    }

    private record FrameDigest(
            FrameSummary summary,
            float[] alpha,
            float[] luminance,
            float[] depth,
            float[] macroAlpha,
            long sceneDepthHash,
            float[] reconstructedAlpha,
            float[] reconstructedLuminance,
            byte[] selectedNeighbor
    ) {
    }

    private record FrameSummary(
            CaptureMetadata metadata,
            String colorHash,
            String alphaHash,
            String depthHash,
            String macroHash,
            String sceneDepthHash,
            String reconstructedAlphaHash,
            String selectedNeighborHash,
            int invalidValues,
            int activePixels,
            int colorDepthPairedPixels,
            int colorOnlyPixels,
            int depthOnlyPixels,
            int premultipliedViolations,
            float maxPremultipliedExcess,
            float meanAlpha,
            float maxAlpha,
            CompositeSummary composite,
            HistoryHistogram history,
            DepthSpaceStats depthSpace,
            PairStats pair,
            PairStats observedPair
    ) {
        String format() {
            return metadata.format()
                    + "\nlow hashes color/alpha/depth/macro/scene="
                    + colorHash + "/" + alphaHash + "/" + depthHash + "/" + macroHash + "/" + sceneDepthHash
                    + " active=" + activePixels
                    + " paired/colorOnly/depthOnly=" + colorDepthPairedPixels
                    + "/" + colorOnlyPixels + "/" + depthOnlyPixels
                    + " invalid=" + invalidValues
                    + " premulViolations=" + premultipliedViolations
                    + " maxExcess=" + fmt(maxPremultipliedExcess)
                    + " alphaMean/max=" + fmt(meanAlpha) + "/" + fmt(maxAlpha)
                    + "\ncomposite hashes alpha/selected=" + reconstructedAlphaHash
                    + "/" + selectedNeighborHash + " " + composite.format()
                    + (history == HistoryHistogram.EMPTY ? "" : "\nhistoryStates=" + history.format())
                    + (depthSpace == DepthSpaceStats.EMPTY ? "" : "\ndepthSpace=" + depthSpace.format())
                    + "\npair=" + pair.format()
                    + "\nobservedPair=" + observedPair.format();
        }
    }

    private record CompositeFrame(
            boolean comparable,
            float[] alpha,
            float[] luminance,
            byte[] selectedNeighbor,
            long alphaHash,
            long selectedHash,
            CompositeSummary summary
    ) {
        static CompositeFrame unavailable(int pixels, String reason) {
            byte[] selected = new byte[pixels];
            java.util.Arrays.fill(selected, (byte) -1);
            return new CompositeFrame(
                    false,
                    new float[pixels],
                    new float[pixels],
                    selected,
                    FNV_OFFSET,
                    FNV_OFFSET,
                    new CompositeSummary(
                            reason,
                            false,
                            0,
                            new int[4],
                            new int[5],
                            0,
                            0.0F,
                            0.0F,
                            0.0F,
                            new GradientStats(0.0D, 0.0D, 0.0D)
                    )
            );
        }
    }

    private record CompositeSummary(
            String path,
            boolean comparable,
            int reconstructedPixels,
            int[] selectedCounts,
            int[] acceptedCounts,
            int sceneRejectedPixels,
            float meanAcceptedWeight,
            float meanCounterfactualAlphaError,
            float maxCounterfactualAlphaError,
            GradientStats gridGradient
    ) {
        String format() {
            return "path=" + path
                    + " comparable=" + comparable
                    + " pixels=" + reconstructedPixels
                    + " selected=" + join(selectedCounts)
                    + " accepted1..4=" + acceptedCounts[1] + "/" + acceptedCounts[2]
                    + "/" + acceptedCounts[3] + "/" + acceptedCounts[4]
                    + " sceneRejected=" + sceneRejectedPixels
                    + " acceptedWeightMean=" + fmt(meanAcceptedWeight)
                    + " vsContinuousAlpha mean/max=" + fmt(meanCounterfactualAlphaError)
                    + "/" + fmt(maxCounterfactualAlphaError)
                    + " grid boundary/interior/ratio=" + fmt(gridGradient.boundaryMean())
                    + "/" + fmt(gridGradient.interiorMean())
                    + "/" + fmt(gridGradient.ratio());
        }
    }

    private record PairStats(
            boolean comparable,
            String reason,
            boolean reconstructionComparable,
            long hookFrameDelta,
            long shaderFrameDelta,
            long gameTimeDelta,
            boolean sceneDepthStable,
            double alphaMad,
            double alphaRms,
            double alphaMax,
            double activeUnionAlphaRms,
            int activeUnionAlphaCompared,
            double luminanceMad,
            double luminanceRms,
            double depthMad,
            double depthRms,
            int depthPresenceMismatch,
            double macroAlphaRms,
            double highFrequencyAlphaRms,
            int alphaDeltaOver002,
            int alphaDeltaOver020,
            int alphaDeltaOver100,
            int occupationChurn002,
            int occupationChurn020,
            int occupationChurn100,
            int bestShiftX,
            int bestShiftY,
            double bestShiftRms,
            double reconstructedAlphaMad,
            double reconstructedAlphaRms,
            double reconstructedActiveUnionAlphaRms,
            int reconstructedActiveUnionAlphaCompared,
            double reconstructedLuminanceRms,
            int selectedNeighborChanges,
            int selectedNeighborCompared
    ) {
        static PairStats firstFrame() {
            return incomparable("first_frame");
        }

        static PairStats incomparable(String reason) {
            return new PairStats(
                    false,
                    reason,
                    false,
                    0L,
                    0L,
                    0L,
                    false,
                    0.0D, 0.0D, 0.0D,
                    0.0D, 0,
                    0.0D, 0.0D, 0.0D, 0.0D,
                    0,
                    0.0D, 0.0D,
                    0, 0, 0, 0, 0, 0,
                    0, 0, 0.0D,
                    0.0D, 0.0D,
                    0.0D, 0,
                    0.0D,
                    0, 0
            );
        }

        String format() {
            if (!comparable) {
                return "incomparable(" + reason + ")";
            }
            return "basis=" + reason
                    + " sceneDepthStable=" + sceneDepthStable
                    + " frameDelta hook/shader/game=" + hookFrameDelta
                    + "/" + shaderFrameDelta + "/" + gameTimeDelta
                    + " rawAlpha mad/rms/max=" + fmt(alphaMad) + "/" + fmt(alphaRms) + "/" + fmt(alphaMax)
                    + " activeUnionRms/count=" + fmt(activeUnionAlphaRms)
                    + "/" + activeUnionAlphaCompared
                    + " luma mad/rms=" + fmt(luminanceMad) + "/" + fmt(luminanceRms)
                    + " depth mad/rms/presenceMismatch=" + fmt(depthMad) + "/" + fmt(depthRms)
                    + "/" + depthPresenceMismatch
                    + " macro/highFreqRms=" + fmt(macroAlphaRms) + "/" + fmt(highFrequencyAlphaRms)
                    + " alphaDelta>.002/.02/.10=" + alphaDeltaOver002 + "/" + alphaDeltaOver020
                    + "/" + alphaDeltaOver100
                    + " occupancyChurn=.002/.02/.10=" + occupationChurn002 + "/" + occupationChurn020
                    + "/" + occupationChurn100
                    + " bestShift=" + bestShiftX + "," + bestShiftY + " rms=" + fmt(bestShiftRms)
                    + " reconstructedComparable=" + reconstructionComparable
                    + (reconstructionComparable
                            ? " alphaMad/rms=" + fmt(reconstructedAlphaMad)
                                    + "/" + fmt(reconstructedAlphaRms)
                                    + " activeUnionRms/count="
                                    + fmt(reconstructedActiveUnionAlphaRms)
                                    + "/" + reconstructedActiveUnionAlphaCompared
                                    + " lumaRms=" + fmt(reconstructedLuminanceRms)
                                    + " selectedChanges=" + selectedNeighborChanges
                                    + "/" + selectedNeighborCompared
                            : " metrics=n/a");
        }
    }

    private record DeltaStats(double mad, double rms, double max, int count) {
        private static final DeltaStats EMPTY = new DeltaStats(0.0D, 0.0D, 0.0D, 0);
    }

    private record ShiftStats(int dx, int dy, double rms) {
    }

    private record GradientStats(double boundaryMean, double interiorMean, double ratio) {
    }

    private record HistoryHistogram(int[] counts) {
        private static final HistoryHistogram EMPTY = new HistoryHistogram(new int[0]);

        String format() {
            return counts.length == 8 ? join(counts) : "not_history_rejection";
        }
    }

    private record DepthSpaceStats(
            int unavailable,
            int offscreen,
            int missingDepth,
            int currentBelowHalfPreviousAtLeastHalf,
            int currentAtLeastHalfPreviousBelowHalf,
            int bothBelowHalf,
            int bothAtLeastHalf,
            int noCurrentHit,
            int evaluated,
            double currentConfidenceSum,
            double previousConfidenceSum,
            double confidenceDeltaSum,
            double maxConfidenceDelta
    ) {
        private static final DepthSpaceStats EMPTY = new DepthSpaceStats(
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0.0D, 0.0D, 0.0D, 0.0D
        );

        DepthSpaceStats plus(DepthSpaceStats other) {
            if (other == null || other == EMPTY) {
                return this;
            }
            if (this == EMPTY) {
                return other;
            }
            return new DepthSpaceStats(
                    unavailable + other.unavailable,
                    offscreen + other.offscreen,
                    missingDepth + other.missingDepth,
                    currentBelowHalfPreviousAtLeastHalf + other.currentBelowHalfPreviousAtLeastHalf,
                    currentAtLeastHalfPreviousBelowHalf + other.currentAtLeastHalfPreviousBelowHalf,
                    bothBelowHalf + other.bothBelowHalf,
                    bothAtLeastHalf + other.bothAtLeastHalf,
                    noCurrentHit + other.noCurrentHit,
                    evaluated + other.evaluated,
                    currentConfidenceSum + other.currentConfidenceSum,
                    previousConfidenceSum + other.previousConfidenceSum,
                    confidenceDeltaSum + other.confidenceDeltaSum,
                    Math.max(maxConfidenceDelta, other.maxConfidenceDelta)
            );
        }

        String format() {
            double divisor = Math.max(1, evaluated);
            return "unavailable=" + unavailable
                    + " offscreen=" + offscreen
                    + " missingDepth=" + missingDepth
                    + " currentBelowHalfPreviousAtLeastHalf=" + currentBelowHalfPreviousAtLeastHalf
                    + " currentAtLeastHalfPreviousBelowHalf=" + currentAtLeastHalfPreviousBelowHalf
                    + " bothBelowHalf=" + bothBelowHalf
                    + " bothAtLeastHalf=" + bothAtLeastHalf
                    + " noCurrentHit=" + noCurrentHit
                    + " evaluated=" + evaluated
                    + " confidenceCurrent/previous/deltaMean/maxDelta="
                    + fmt(currentConfidenceSum / divisor)
                    + "/" + fmt(previousConfidenceSum / divisor)
                    + "/" + fmt(confidenceDeltaSum / divisor)
                    + "/" + fmt(maxConfidenceDelta);
        }
    }

    private static String join(int[] values) {
        if (values == null || values.length == 0) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append('/');
            }
            builder.append(values[index]);
        }
        return builder.toString();
    }
}
