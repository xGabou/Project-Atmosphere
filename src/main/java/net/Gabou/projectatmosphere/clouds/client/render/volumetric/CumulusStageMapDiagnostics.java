package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * On-demand, read-only analysis of the three structured cumulus maps. Texture
 * transfers use a pixel-pack buffer and are mapped only after a non-blocking
 * fence poll reports completion. The render thread therefore never waits for
 * the diagnostic readback.
 */
public final class CumulusStageMapDiagnostics {
    private static final float SUPPORT_THRESHOLD = 0.012F;
    private static final String[] STAGE_NAMES = {"base", "core", "tower", "crown"};

    private static volatile boolean captureRequested;
    private static int pixelPackBufferId;
    private static long pendingFence;
    private static long pendingMapBytes;
    private static CaptureMetadata pendingMetadata = CaptureMetadata.unknown();
    private static CompletableFuture<Report> pendingAnalysis;
    private static CaptureMetadata analysisMetadata = CaptureMetadata.unknown();
    private static volatile Report latestReport = Report.unknown("not_captured");
    private static volatile CaptureMetadata latestMetadata = CaptureMetadata.unknown();
    private static volatile String status = "idle";
    private static long nextCaptureId = 1L;

    private CumulusStageMapDiagnostics() {
    }

    public static synchronized String requestCapture() {
        if (captureRequested || pendingFence != 0L || pendingAnalysis != null) {
            return "busy:" + status;
        }
        captureRequested = true;
        status = "requested";
        return "requested";
    }

    public static String status() {
        return status;
    }

    public static String formattedLatest() {
        return latestMetadata.format() + "\n" + latestReport.format();
    }

    /** Polls both the GL fence and the off-thread numerical analysis. */
    public static synchronized void poll() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }

        if (pendingAnalysis != null && pendingAnalysis.isDone()) {
            try {
                latestReport = pendingAnalysis.join();
                latestMetadata = analysisMetadata;
                status = "ready:capture=" + analysisMetadata.captureId();
                ProjectAtmosphere.LOGGER.info(
                        "[VolumetricCumulusDiagnostics]\n{}",
                        formattedLatest()
                );
            } catch (RuntimeException exception) {
                latestReport = Report.unknown("analysis_failed:" + exception.getClass().getSimpleName());
                latestMetadata = analysisMetadata;
                status = latestReport.status();
                ProjectAtmosphere.LOGGER.warn(
                        "[VolumetricCumulusDiagnostics] analysis failed for capture {}",
                        analysisMetadata.captureId(),
                        exception
                );
            } finally {
                pendingAnalysis = null;
                analysisMetadata = CaptureMetadata.unknown();
            }
        }

        if (pendingFence == 0L) {
            return;
        }
        int signaled = GL32.glClientWaitSync(pendingFence, 0, 0L);
        if (signaled == GL32.GL_WAIT_FAILED) {
            failPendingCapture("fence_wait_failed");
            ProjectAtmosphere.LOGGER.warn("[VolumetricCumulusDiagnostics] fence poll failed");
            return;
        }
        if (signaled != GL32.GL_ALREADY_SIGNALED && signaled != GL32.GL_CONDITION_SATISFIED) {
            return;
        }

        int previousPixelPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        float[] supports;
        float[] bases;
        float[] tops;
        boolean mappedBuffer = false;
        try {
            GL32.glDeleteSync(pendingFence);
            pendingFence = 0L;
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pixelPackBufferId);
            ByteBuffer mapped = GL30.glMapBufferRange(
                    GL21.GL_PIXEL_PACK_BUFFER,
                    0L,
                    pendingMapBytes * 3L,
                    GL30.GL_MAP_READ_BIT
            );
            if (mapped == null) {
                failPendingCapture("map_failed");
                return;
            }
            mappedBuffer = true;
            int valuesPerMap = Math.toIntExact(pendingMapBytes / Float.BYTES);
            supports = new float[valuesPerMap];
            bases = new float[valuesPerMap];
            tops = new float[valuesPerMap];
            FloatBuffer floats = mapped.order(ByteOrder.nativeOrder()).asFloatBuffer();
            floats.position(0);
            floats.get(supports);
            floats.position(valuesPerMap);
            floats.get(bases);
            floats.position(valuesPerMap * 2);
            floats.get(tops);
            boolean dataValid = GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            mappedBuffer = false;
            if (!dataValid) {
                throw new IllegalStateException("pixel-pack buffer contents became invalid");
            }
        } catch (RuntimeException exception) {
            if (mappedBuffer) {
                try {
                    GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
                } catch (RuntimeException ignored) {
                    // Preserve the original failure; failPendingCapture deletes
                    // the diagnostic-only PBO immediately afterward.
                }
            }
            failPendingCapture("read_failed:" + exception.getClass().getSimpleName());
            ProjectAtmosphere.LOGGER.warn("[VolumetricCumulusDiagnostics] PBO readback failed", exception);
            return;
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPixelPackBuffer);
        }

        if (pixelPackBufferId > 0) {
            GL15.glDeleteBuffers(pixelPackBufferId);
            pixelPackBufferId = 0;
        }
        CaptureMetadata metadata = pendingMetadata;
        pendingMetadata = CaptureMetadata.unknown();
        pendingMapBytes = 0L;
        analysisMetadata = metadata;
        status = "analyzing:capture=" + metadata.captureId();
        pendingAnalysis = CompletableFuture.supplyAsync(() -> analyze(
                supports,
                bases,
                tops,
                metadata.width(),
                metadata.height(),
                metadata.slabBaseY(),
                metadata.slabTopY(),
                metadata.originX(),
                metadata.originZ(),
                metadata.extent(),
                metadata.coverageMultiplier()
        ));
    }

    /** Issues three asynchronous texture-to-PBO copies for one requested capture. */
    public static synchronized void tryDispatch(
            RenderTarget supportTarget,
            RenderTarget baseTarget,
            RenderTarget topTarget,
            float slabBaseY,
            float slabTopY,
            double originX,
            double originZ,
            float extent,
            long frameIndex,
            long gameTime,
            long inputSignature,
            String roleSummary,
            String cacheStatus,
            float coverageMultiplier
    ) {
        if (!captureRequested || pendingFence != 0L || pendingAnalysis != null) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            status = "not_render_thread";
            return;
        }
        if (!validTarget(supportTarget) || !validTarget(baseTarget) || !validTarget(topTarget)) {
            status = "missing_target";
            return;
        }
        if (supportTarget.width != baseTarget.width || supportTarget.width != topTarget.width
                || supportTarget.height != baseTarget.height || supportTarget.height != topTarget.height) {
            status = "target_size_mismatch";
            captureRequested = false;
            return;
        }
        if (!Float.isFinite(slabBaseY) || !Float.isFinite(slabTopY)
                || slabTopY <= slabBaseY || !Float.isFinite(extent) || extent <= 0.0F) {
            status = "invalid_domain";
            captureRequested = false;
            return;
        }

        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousPixelPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int previousPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int previousPackRowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int previousPackSkipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int previousPackSkipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int previousPackSwapBytes = GL11.glGetInteger(GL11.GL_PACK_SWAP_BYTES);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            int width = supportTarget.width;
            int height = supportTarget.height;
            pendingMapBytes = (long) width * height * 4L * Float.BYTES;
            pixelPackBufferId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pixelPackBufferId);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, pendingMapBytes * 3L, GL15.GL_STREAM_READ);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, GL11.GL_FALSE);
            clearGlErrors();

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, supportTarget.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, 0L);
            requireNoGlError("support_read");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, baseTarget.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pendingMapBytes);
            requireNoGlError("base_read");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, topTarget.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pendingMapBytes * 2L);
            requireNoGlError("top_read");
            pendingFence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            if (pendingFence == 0L) {
                throw new IllegalStateException("glFenceSync returned null");
            }
            pendingMetadata = new CaptureMetadata(
                    nextCaptureId++,
                    frameIndex,
                    gameTime,
                    inputSignature,
                    roleSummary,
                    cacheStatus,
                    Math.max(coverageMultiplier, 0.001F),
                    width,
                    height,
                    slabBaseY,
                    slabTopY,
                    originX,
                    originZ,
                    extent
            );
            captureRequested = false;
            status = "gpu_in_flight:capture=" + pendingMetadata.captureId();
        } catch (RuntimeException exception) {
            captureRequested = false;
            failPendingCapture("dispatch_failed:" + exception.getClass().getSimpleName());
            ProjectAtmosphere.LOGGER.warn("[VolumetricCumulusDiagnostics] dispatch failed", exception);
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, previousPackRowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, previousPackSkipPixels);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, previousPackSkipRows);
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, previousPackSwapBytes);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPixelPackBuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
    }

    public static synchronized void shutdown() {
        captureRequested = false;
        if (pendingFence != 0L) {
            GL32.glDeleteSync(pendingFence);
            pendingFence = 0L;
        }
        if (pixelPackBufferId > 0) {
            GL15.glDeleteBuffers(pixelPackBufferId);
            pixelPackBufferId = 0;
        }
        if (pendingAnalysis != null) {
            pendingAnalysis.cancel(false);
            pendingAnalysis = null;
        }
        pendingMapBytes = 0L;
        pendingMetadata = CaptureMetadata.unknown();
        analysisMetadata = CaptureMetadata.unknown();
        latestMetadata = CaptureMetadata.unknown();
        latestReport = Report.unknown("not_captured");
        status = "idle";
    }

    private static void failPendingCapture(String reason) {
        if (pendingFence != 0L) {
            GL32.glDeleteSync(pendingFence);
            pendingFence = 0L;
        }
        if (pixelPackBufferId > 0) {
            GL15.glDeleteBuffers(pixelPackBufferId);
            pixelPackBufferId = 0;
        }
        pendingMapBytes = 0L;
        pendingMetadata = CaptureMetadata.unknown();
        status = reason;
    }

    private static void clearGlErrors() {
        for (int i = 0; i < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
            // Diagnostics are an explicit isolation point. Clear stale errors
            // so only the following texture transfer is attributed here.
        }
    }

    private static void requireNoGlError(String operation) {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            throw new IllegalStateException(operation + "_gl_error_0x" + Integer.toHexString(error));
        }
    }

    static Report analyze(
            float[] supports,
            float[] bases,
            float[] tops,
            int width,
            int height,
            float slabBaseY,
            float slabTopY,
            double originX,
            double originZ,
            float extent,
            float coverageMultiplier
    ) {
        int pixelCount = Math.max(0, width * height);
        int valueCount = pixelCount * 4;
        if (width <= 0 || height <= 0 || supports.length < valueCount
                || bases.length < valueCount || tops.length < valueCount) {
            return Report.unknown("invalid_buffers");
        }

        float slabSpan = slabTopY - slabBaseY;
        float supportThreshold = SUPPORT_THRESHOLD / Math.max(coverageMultiplier, 0.001F);
        double texelX = extent / width;
        double texelZ = extent / height;
        List<StageStats> stages = new ArrayList<>(4);
        for (int stage = 0; stage < 4; stage++) {
            StageAccumulator accumulator = new StageAccumulator();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int offset = (y * width + x) * 4 + stage;
                    float support = finiteNonNegative(supports[offset]);
                    if (support <= supportThreshold) {
                        continue;
                    }
                    float rawBase01 = bases[offset] / support;
                    float rawTop01 = tops[offset] / support;
                    boolean invalidEndpoint = !Float.isFinite(rawBase01) || !Float.isFinite(rawTop01)
                            || rawBase01 < 0.0F || rawBase01 > 1.0F
                            || rawTop01 < 0.0F || rawTop01 > 1.0F;
                    boolean invertedEndpoint = Float.isFinite(rawBase01) && Float.isFinite(rawTop01)
                            && rawTop01 < rawBase01;
                    if (invalidEndpoint || invertedEndpoint) {
                        accumulator.addEndpointAnomaly(invalidEndpoint, invertedEndpoint);
                    }
                    float baseY = decodeHeight(bases[offset], support, slabBaseY, slabSpan);
                    float topY = decodeHeight(tops[offset], support, slabBaseY, slabSpan);
                    if (!Float.isFinite(baseY) || !Float.isFinite(topY)) {
                        continue;
                    }
                    topY = Math.max(baseY + 1.0F, topY);
                    double worldX = originX + (x + 0.5D) * texelX;
                    double worldZ = originZ + (y + 0.5D) * texelZ;
                    accumulator.add(worldX, worldZ, support, baseY, topY);
                }
            }
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int offset = (y * width + x) * 4 + stage;
                    if (finiteNonNegative(supports[offset]) <= supportThreshold) {
                        continue;
                    }
                    if (x + 1 < width) {
                        accumulateNeighborJump(
                                accumulator,
                                offset,
                                offset + 4,
                                supports,
                                bases,
                                tops,
                                slabBaseY,
                                slabSpan,
                                supportThreshold
                        );
                    }
                    if (y + 1 < height) {
                        accumulateNeighborJump(
                                accumulator,
                                offset,
                                offset + width * 4,
                                supports,
                                bases,
                                tops,
                                slabBaseY,
                                slabSpan,
                                supportThreshold
                        );
                    }
                }
            }
            stages.add(accumulator.finish(
                    STAGE_NAMES[stage],
                    pixelCount,
                    texelX * texelZ
            ));
        }

        List<PairStats> pairs = new ArrayList<>(3);
        for (int lower = 0; lower < 3; lower++) {
            int upper = lower + 1;
            PairAccumulator accumulator = new PairAccumulator();
            int upperActive = 0;
            for (int pixel = 0; pixel < pixelCount; pixel++) {
                int baseOffset = pixel * 4;
                float lowerSupport = finiteNonNegative(supports[baseOffset + lower]);
                float upperSupport = finiteNonNegative(supports[baseOffset + upper]);
                if (upperSupport > supportThreshold) {
                    upperActive++;
                }
                if (lowerSupport <= supportThreshold || upperSupport <= supportThreshold) {
                    continue;
                }
                float lowerBase = decodeHeight(
                        bases[baseOffset + lower], lowerSupport, slabBaseY, slabSpan
                );
                float lowerTop = decodeHeight(
                        tops[baseOffset + lower], lowerSupport, slabBaseY, slabSpan
                );
                float upperBase = decodeHeight(
                        bases[baseOffset + upper], upperSupport, slabBaseY, slabSpan
                );
                if (Float.isFinite(lowerBase) && Float.isFinite(lowerTop) && Float.isFinite(upperBase)) {
                    accumulator.add(upperBase - Math.max(lowerBase + 1.0F, lowerTop));
                }
            }
            StageStats lowerStats = stages.get(lower);
            StageStats upperStats = stages.get(upper);
            double centroidDistance = Math.hypot(
                    upperStats.centroidX() - lowerStats.centroidX(),
                    upperStats.centroidZ() - lowerStats.centroidZ()
            );
            pairs.add(accumulator.finish(
                    STAGE_NAMES[lower] + "->" + STAGE_NAMES[upper],
                    upperActive,
                    centroidDistance,
                    lowerStats.activeWorldArea() <= 0.0D
                            ? Double.NaN
                            : upperStats.activeWorldArea() / lowerStats.activeWorldArea()
            ));
        }
        return new Report(
                "ok",
                width,
                height,
                supportThreshold,
                hashFloats(supports),
                hashQuantizedFloats(supports, 1024),
                hashQuantizedFloats(supports, 4096),
                hashFloats(bases),
                hashFloats(tops),
                List.copyOf(stages),
                List.copyOf(pairs)
        );
    }

    private static void accumulateNeighborJump(
            StageAccumulator accumulator,
            int first,
            int second,
            float[] supports,
            float[] bases,
            float[] tops,
            float slabBaseY,
            float slabSpan,
            float supportThreshold
    ) {
        float firstSupport = finiteNonNegative(supports[first]);
        float secondSupport = finiteNonNegative(supports[second]);
        if (secondSupport <= supportThreshold) {
            return;
        }
        float firstBase = decodeHeight(bases[first], firstSupport, slabBaseY, slabSpan);
        float firstTop = decodeHeight(tops[first], firstSupport, slabBaseY, slabSpan);
        float secondBase = decodeHeight(bases[second], secondSupport, slabBaseY, slabSpan);
        float secondTop = decodeHeight(tops[second], secondSupport, slabBaseY, slabSpan);
        if (Float.isFinite(firstBase) && Float.isFinite(firstTop)
                && Float.isFinite(secondBase) && Float.isFinite(secondTop)) {
            accumulator.addNeighborJump(
                    Math.abs(firstBase - secondBase),
                    Math.abs(Math.max(firstBase + 1.0F, firstTop)
                            - Math.max(secondBase + 1.0F, secondTop))
            );
        }
    }

    private static boolean validTarget(RenderTarget target) {
        return target != null && target.width > 0 && target.height > 0
                && target.getColorTextureId() > 0;
    }

    private static float decodeHeight(float encoded, float support, float slabBaseY, float slabSpan) {
        if (!Float.isFinite(encoded) || support <= 0.0F) {
            return Float.NaN;
        }
        float normalized = Math.max(0.0F, Math.min(1.0F, encoded / support));
        return slabBaseY + normalized * slabSpan;
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, value) : 0.0F;
    }

    private static String fmt(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "unknown";
    }

    private static String hashFloats(float[] values) {
        long hash = 0xcbf29ce484222325L;
        for (float value : values) {
            hash ^= Integer.toUnsignedLong(Float.floatToRawIntBits(value));
            hash *= 0x100000001b3L;
        }
        return String.format(Locale.ROOT, "%016x", hash);
    }

    private static String hashQuantizedFloats(float[] values, int bins) {
        long hash = 0xcbf29ce484222325L;
        for (float value : values) {
            int quantized = Float.isFinite(value)
                    ? Math.round(value * bins)
                    : Integer.MIN_VALUE;
            hash ^= Integer.toUnsignedLong(quantized);
            hash *= 0x100000001b3L;
        }
        return String.format(Locale.ROOT, "%016x", hash);
    }

    private static String fmtPrecise(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.9f", value) : "unknown";
    }

    public record CaptureMetadata(
            long captureId,
            long frameIndex,
            long gameTime,
            long inputSignature,
            String roleSummary,
            String cacheStatus,
            float coverageMultiplier,
            int width,
            int height,
            float slabBaseY,
            float slabTopY,
            double originX,
            double originZ,
            float extent
    ) {
        public CaptureMetadata {
            roleSummary = roleSummary == null || roleSummary.isBlank() ? "unknown" : roleSummary;
            cacheStatus = cacheStatus == null || cacheStatus.isBlank() ? "unknown" : cacheStatus;
        }

        static CaptureMetadata unknown() {
            return new CaptureMetadata(
                    0L, 0L, 0L, Long.MIN_VALUE, "unknown", "unknown",
                    Float.NaN, 0, 0, Float.NaN, Float.NaN, Double.NaN, Double.NaN, Float.NaN
            );
        }

        String format() {
            return "Cumulus capture metadata"
                    + "\ncapture=" + (captureId <= 0L ? "unknown" : captureId)
                    + " frame=" + frameIndex
                    + " gameTime=" + gameTime
                    + " inputSignature=" + String.format(Locale.ROOT, "%016x", inputSignature)
                    + " roles=" + roleSummary
                    + " coverageMul=" + fmt(coverageMultiplier)
                    + "\nsize=" + width + "x" + height
                    + " origin=" + fmt(originX) + "," + fmt(originZ)
                    + " extent=" + fmt(extent)
                    + " slab=" + fmt(slabBaseY) + ".." + fmt(slabTopY)
                    + "\n" + cacheStatus;
        }
    }

    public record Report(
            String status,
            int width,
            int height,
            float supportThreshold,
            String supportHash,
            String supportQ10Hash,
            String supportQ12Hash,
            String baseHash,
            String topHash,
            List<StageStats> stages,
            List<PairStats> pairs
    ) {
        public Report {
            status = status == null || status.isBlank() ? "unknown" : status;
            supportHash = supportHash == null || supportHash.isBlank() ? "unknown" : supportHash;
            supportQ10Hash = supportQ10Hash == null || supportQ10Hash.isBlank() ? "unknown" : supportQ10Hash;
            supportQ12Hash = supportQ12Hash == null || supportQ12Hash.isBlank() ? "unknown" : supportQ12Hash;
            baseHash = baseHash == null || baseHash.isBlank() ? "unknown" : baseHash;
            topHash = topHash == null || topHash.isBlank() ? "unknown" : topHash;
            stages = List.copyOf(stages == null ? List.of() : stages);
            pairs = List.copyOf(pairs == null ? List.of() : pairs);
        }

        public static Report unknown(String reason) {
            return new Report(
                    reason, 0, 0, SUPPORT_THRESHOLD,
                    "unknown", "unknown", "unknown", "unknown", "unknown", List.of(), List.of()
            );
        }

        public String format() {
            StringBuilder builder = new StringBuilder("Cumulus stage-map diagnostics")
                    .append("\nstatus=").append(status)
                    .append(" size=").append(width).append("x").append(height)
                    .append(" supportThreshold=").append(fmt(supportThreshold))
                     .append("\nhashes support/base/top=")
                    .append(supportHash).append("/").append(baseHash).append("/").append(topHash)
                    .append(" supportQ10/Q12=")
                    .append(supportQ10Hash).append("/").append(supportQ12Hash);
            for (StageStats stage : stages) {
                builder.append("\n- ").append(stage.format());
            }
            for (PairStats pair : pairs) {
                builder.append("\n- ").append(pair.format());
            }
            return builder.toString();
        }
    }

    public record StageStats(
            String stage,
            int activeTexels,
            double activePercent,
            double activeWorldArea,
            double supportSum,
            double meanSupport,
            double maxSupport,
            double centroidX,
            double centroidZ,
            double sigmaMajor,
            double sigmaMinor,
            double minBaseY,
            double meanBaseY,
            double maxBaseY,
            double minTopY,
            double meanTopY,
            double maxTopY,
            double meanThickness,
            int invalidEndpointTexels,
            int invertedEndpointTexels,
            int neighborPairs,
            int jumpPairsAboveFourBlocks,
            double maxBaseJump,
            double maxTopJump
    ) {
        String format() {
            double jumpPercent = neighborPairs <= 0
                    ? Double.NaN
                    : jumpPairsAboveFourBlocks * 100.0D / neighborPairs;
            return "stage=" + stage
                    + " active=" + activeTexels + " (" + fmt(activePercent) + "%)"
                    + " area=" + fmt(activeWorldArea)
                    + " supportSum=" + fmtPrecise(supportSum)
                    + " supportMeanMax=" + fmt(meanSupport) + "/" + fmt(maxSupport)
                    + " centroid=" + fmt(centroidX) + "," + fmt(centroidZ)
                    + " sigmaMajorMinor=" + fmt(sigmaMajor) + "/" + fmt(sigmaMinor)
                    + " baseMinMeanMax=" + fmt(minBaseY) + "/" + fmt(meanBaseY) + "/" + fmt(maxBaseY)
                    + " topMinMeanMax=" + fmt(minTopY) + "/" + fmt(meanTopY) + "/" + fmt(maxTopY)
                    + " thicknessMean=" + fmt(meanThickness)
                    + " invalid/invertedEndpoints=" + invalidEndpointTexels + "/" + invertedEndpointTexels
                    + " neighborJumpsGt4=" + jumpPairsAboveFourBlocks + "/" + neighborPairs
                    + " (" + fmt(jumpPercent) + "%)"
                    + " maxBaseTopJump=" + fmt(maxBaseJump) + "/" + fmt(maxTopJump);
        }
    }

    public record PairStats(
            String pair,
            int coactiveTexels,
            double upperContainedPercent,
            int gapTexels,
            double gapPercent,
            double meanGap,
            double maxGap,
            int overlapTexels,
            double meanOverlap,
            double maxOverlap,
            double centroidDistance,
            double upperToLowerAreaRatio
    ) {
        String format() {
            return "pair=" + pair
                    + " coactive=" + coactiveTexels
                    + " upperContained=" + fmt(upperContainedPercent) + "%"
                    + " gaps=" + gapTexels + " (" + fmt(gapPercent) + "%)"
                    + " gapMeanMax=" + fmt(meanGap) + "/" + fmt(maxGap)
                    + " overlaps=" + overlapTexels
                    + " overlapMeanMax=" + fmt(meanOverlap) + "/" + fmt(maxOverlap)
                    + " centroidDistance=" + fmt(centroidDistance)
                    + " upperLowerAreaRatio=" + fmt(upperToLowerAreaRatio);
        }
    }

    private static final class StageAccumulator {
        private int count;
        private double supportSum;
        private double maxSupport;
        private double xSum;
        private double zSum;
        private double xSquareSum;
        private double zSquareSum;
        private double xzSum;
        private double minBase = Double.POSITIVE_INFINITY;
        private double maxBase = Double.NEGATIVE_INFINITY;
        private double minTop = Double.POSITIVE_INFINITY;
        private double maxTop = Double.NEGATIVE_INFINITY;
        private double baseSum;
        private double topSum;
        private double thicknessSum;
        private int invalidEndpointTexels;
        private int invertedEndpointTexels;
        private int neighborPairs;
        private int jumpPairs;
        private double maxBaseJump;
        private double maxTopJump;

        void add(double x, double z, float support, float baseY, float topY) {
            count++;
            supportSum += support;
            maxSupport = Math.max(maxSupport, support);
            xSum += x * support;
            zSum += z * support;
            xSquareSum += x * x * support;
            zSquareSum += z * z * support;
            xzSum += x * z * support;
            minBase = Math.min(minBase, baseY);
            maxBase = Math.max(maxBase, baseY);
            minTop = Math.min(minTop, topY);
            maxTop = Math.max(maxTop, topY);
            baseSum += baseY * support;
            topSum += topY * support;
            thicknessSum += Math.max(0.0F, topY - baseY) * support;
        }

        void addNeighborJump(double baseJump, double topJump) {
            neighborPairs++;
            maxBaseJump = Math.max(maxBaseJump, baseJump);
            maxTopJump = Math.max(maxTopJump, topJump);
            if (Math.max(baseJump, topJump) > 4.0D) {
                jumpPairs++;
            }
        }

        void addEndpointAnomaly(boolean invalid, boolean inverted) {
            if (invalid) {
                invalidEndpointTexels++;
            }
            if (inverted) {
                invertedEndpointTexels++;
            }
        }

        StageStats finish(String stage, int pixelCount, double texelArea) {
            if (count <= 0 || supportSum <= 0.0D) {
                return new StageStats(
                        stage, 0, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                        Double.NaN, Double.NaN, Double.NaN,
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                        invalidEndpointTexels, invertedEndpointTexels,
                        0, 0, Double.NaN, Double.NaN
                );
            }
            double centroidX = xSum / supportSum;
            double centroidZ = zSum / supportSum;
            double covarianceX = Math.max(0.0D, xSquareSum / supportSum - centroidX * centroidX);
            double covarianceZ = Math.max(0.0D, zSquareSum / supportSum - centroidZ * centroidZ);
            double covarianceXZ = xzSum / supportSum - centroidX * centroidZ;
            double discriminant = Math.sqrt(Math.max(
                    0.0D,
                    (covarianceX - covarianceZ) * (covarianceX - covarianceZ)
                            + 4.0D * covarianceXZ * covarianceXZ
            ));
            double majorVariance = Math.max(0.0D, (covarianceX + covarianceZ + discriminant) * 0.5D);
            double minorVariance = Math.max(0.0D, (covarianceX + covarianceZ - discriminant) * 0.5D);
            return new StageStats(
                    stage,
                    count,
                    count * 100.0D / Math.max(1, pixelCount),
                    count * texelArea,
                    supportSum,
                    supportSum / count,
                    maxSupport,
                    centroidX,
                    centroidZ,
                    Math.sqrt(majorVariance),
                    Math.sqrt(minorVariance),
                    minBase,
                    baseSum / supportSum,
                    maxBase,
                    minTop,
                    topSum / supportSum,
                    maxTop,
                    thicknessSum / supportSum,
                    invalidEndpointTexels,
                    invertedEndpointTexels,
                    neighborPairs,
                    jumpPairs,
                    maxBaseJump,
                    maxTopJump
            );
        }
    }

    private static final class PairAccumulator {
        private int coactive;
        private int gaps;
        private int overlaps;
        private double gapSum;
        private double maxGap;
        private double overlapSum;
        private double maxOverlap;

        void add(double signedGap) {
            coactive++;
            if (signedGap > 0.0D) {
                gaps++;
                gapSum += signedGap;
                maxGap = Math.max(maxGap, signedGap);
            } else {
                double overlap = -signedGap;
                overlaps++;
                overlapSum += overlap;
                maxOverlap = Math.max(maxOverlap, overlap);
            }
        }

        PairStats finish(
                String pair,
                int upperActive,
                double centroidDistance,
                double upperToLowerAreaRatio
        ) {
            return new PairStats(
                    pair,
                    coactive,
                    upperActive <= 0 ? Double.NaN : coactive * 100.0D / upperActive,
                    gaps,
                    coactive <= 0 ? Double.NaN : gaps * 100.0D / coactive,
                    gaps <= 0 ? 0.0D : gapSum / gaps,
                    maxGap,
                    overlaps,
                    overlaps <= 0 ? 0.0D : overlapSum / overlaps,
                    maxOverlap,
                    centroidDistance,
                    upperToLowerAreaRatio
            );
        }
    }
}
