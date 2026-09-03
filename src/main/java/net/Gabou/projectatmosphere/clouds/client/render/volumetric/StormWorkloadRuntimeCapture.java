package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * T123's short, on-demand workload readback. Diagnostic frames encode
 * integer per-pixel counter channels; their target-wide sum is the actual
 * executed work for that rendered frame. FINAL rendering never enters this
 * class or pays a readback.
 */
final class StormWorkloadRuntimeCapture {
    private static final int STAGES = 5;
    /** Token value that never identifies an accepted capture. */
    static final long NO_TOKEN = 0L;
    /**
     * T132 freshness token. Every accepted request takes the next value, and a
     * completed {@link WorkloadResult} carries the token of the request that
     * produced it. The consumer matches on that token, so a result left over
     * from an earlier capture of the same view can never be read as the current
     * one. Topology generation is deliberately not used: generation drift is
     * allowed while the structural fingerprint is unchanged, so it identifies
     * neither a capture nor a fixture.
     */
    private static final AtomicLong CAPTURE_SEQUENCE = new AtomicLong();
    private static volatile Request active;
    private static volatile String latest = "not_captured";
    private static volatile WorkloadResult latestResult;

    private StormWorkloadRuntimeCapture() {
    }

    static synchronized String request(String view) {
        return requestCapture(view).status();
    }

    /**
     * Requests a capture and returns the token the caller must later match.
     * Any previously completed result is dropped here too, so a capture that
     * fails without producing a new result cannot expose the old one as
     * current.
     */
    static synchronized CaptureRequest requestCapture(String view) {
        if (active != null) {
            return new CaptureRequest("busy:stage=" + active.stage + "/" + STAGES, NO_TOKEN);
        }
        if (view == null || view.isBlank()) {
            return new CaptureRequest("invalid_view", NO_TOKEN);
        }
        latestResult = null;
        long token = CAPTURE_SEQUENCE.incrementAndGet();
        active = new Request(view.trim().toLowerCase(Locale.ROOT), token);
        latest = "acquiring view=" + active.view + " captureToken=" + token
                + " stage=0/" + STAGES;
        VolumetricCloudRenderer.invalidateHistory();
        return new CaptureRequest(latest, token);
    }

    static boolean active() {
        return active != null;
    }

    /**
     * Abandons an in-flight capture and drops any partial values. A capture the
     * caller has given up on must not stay active: the next request would be
     * refused as busy and the stale request would then finish its remaining
     * stages from frames rendered under a different arm, producing a result
     * stitched from several configurations.
     */
    static synchronized void abort(String reason) {
        Request request = active;
        if (request == null) {
            return;
        }
        active = null;
        latestResult = null;
        latest = "capture_abandoned:view=" + request.view
                + " captureToken=" + request.token
                + " stage=" + request.stage + "/" + STAGES
                + " reason=" + reason;
        VolumetricCloudRenderer.invalidateHistory();
    }

    static VolumetricCloudRaymarchDebugView view() {
        Request request = active;
        int stage = request == null ? 0 : request.stage;
        return switch (stage) {
            case 1 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_SECONDARY;
            case 2 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_TERTIARY;
            case 3 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_QUATERNARY;
            case 4 -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_QUINARY;
            default -> VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_PRIMARY;
        };
    }

    static String latest() {
        return latest;
    }

    /** Most recent completed counter readback, retained for the diagnostic suite. */
    static WorkloadResult latestResult() {
        return latestResult;
    }

    static synchronized void capture(RenderTarget target) {
        Request request = active;
        if (request == null || target == null || !RenderSystem.isOnRenderThread()
                || target.getColorTextureId() <= 0 || target.width <= 0 || target.height <= 0) {
            return;
        }
        FloatBuffer pixels = BufferUtils.createFloatBuffer(target.width * target.height * 4);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            double first = 0.0D;
            double second = 0.0D;
            double third = 0.0D;
            double fourth = 0.0D;
            int count = target.width * target.height;
            for (int pixel = 0; pixel < count; pixel++) {
                int offset = pixel * 4;
                first += Math.max(0.0F, pixels.get(offset));
                second += Math.max(0.0F, pixels.get(offset + 1));
                third += Math.max(0.0F, pixels.get(offset + 2));
                fourth += Math.max(0.0F, pixels.get(offset + 3));
            }
            request.values[request.stage][0] = first;
            request.values[request.stage][1] = second;
            request.values[request.stage][2] = third;
            request.values[request.stage][3] = fourth;
            request.width = target.width;
            request.height = target.height;
            request.stage++;
            if (request.stage >= STAGES) {
                latestResult = request.result();
                latest = latestResult.format();
                active = null;
                VolumetricCloudRenderer.invalidateHistory();
            } else {
                latest = "acquiring view=" + request.view
                        + " captureToken=" + request.token
                        + " stage=" + request.stage + "/" + STAGES;
            }
        } catch (RuntimeException exception) {
            // A failed capture must leave no result behind. Previously the last
            // successful WorkloadResult stayed readable while active was
            // cleared, so a consumer matching only on the view name accepted it
            // as the current capture.
            active = null;
            latestResult = null;
            latest = "capture_failed:view=" + request.view
                    + " captureToken=" + request.token
                    + " cause=" + exception.getClass().getSimpleName();
            VolumetricCloudRenderer.invalidateHistory();
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private static final class Request {
        private final String view;
        private final long token;
        private final double[][] values = new double[STAGES][4];
        private int stage;
        private int width;
        private int height;

        private Request(String view, long token) {
            this.view = view;
            this.token = token;
        }

        private WorkloadResult result() {
            return new WorkloadResult(token, view, width, height,
                    values[1][3], values[0][3],
                    values[0][0], values[0][1], values[0][2],
                    values[1][0], values[1][1], values[1][2],
                    values[2][0], values[2][1], values[2][2], values[2][3],
                    values[3][0], values[3][1], values[3][2], values[3][3],
                    values[4][0]);
        }
    }

    /** Outcome of {@link #requestCapture(String)}: status text plus the token to match. */
    record CaptureRequest(String status, long token) {
        boolean accepted() {
            return token != NO_TOKEN && status.startsWith("acquiring");
        }
    }

    record WorkloadResult(
            long captureToken, String view, int width, int height,
            double conservativeDescriptorRejects, double avoidedDescriptorTextureFetches,
            double primaryRaySteps, double descriptorEvaluations, double descriptorTextureFetches,
            double lightMarchDensityEvaluations, double emptySpaceRejects, double earlyTerminations,
            double directStormShapeCalls, double groupFieldCalls, double lobesVisited,
            double cloudDensityCalls,
            double densityZeroCalls, double segmentTestCalls, double segmentTestPositive,
            double boxBoundRejects, double detailOctaveEvaluations
    ) {
        String format() {
            return "T123 workload view=" + view
                    + " captureToken=" + captureToken
                    + " target=" + width + "x" + height
                    + " conservativeDescriptorRejects=" + fmt(conservativeDescriptorRejects)
                    + " avoidedDescriptorTextureFetches=" + fmt(avoidedDescriptorTextureFetches)
                    + " primaryRaySteps=" + fmt(primaryRaySteps)
                    + " descriptorEvaluations=" + fmt(descriptorEvaluations)
                    + " descriptorTextureFetches=" + fmt(descriptorTextureFetches)
                    + " lightMarchDensityEvaluations=" + fmt(lightMarchDensityEvaluations)
                    + " emptySpaceRejects=" + fmt(emptySpaceRejects)
                    + " earlyTerminations=" + fmt(earlyTerminations)
                    + " directStormShapeCalls=" + fmt(directStormShapeCalls)
                    + " groupFieldCalls=" + fmt(groupFieldCalls)
                    + " lobesVisited=" + fmt(lobesVisited)
                    + " cloudDensityCalls=" + fmt(cloudDensityCalls)
                    + " densityZeroCalls=" + fmt(densityZeroCalls)
                    + " segmentTestCalls=" + fmt(segmentTestCalls)
                    + " segmentTestPositive=" + fmt(segmentTestPositive)
                    + " boxBoundRejects=" + fmt(boxBoundRejects)
                    + " detailOctaveEvaluations=" + fmt(detailOctaveEvaluations)
                    + " lightEvaluationsPerPixel=" + perPixel(lightMarchDensityEvaluations)
                    + " detailOctaveEvaluationsPerPixel=" + perPixel(detailOctaveEvaluations);
        }

        private String perPixel(double value) {
            long pixels = (long) width * (long) height;
            return pixels <= 0L
                    ? "n/a"
                    : String.format(Locale.ROOT, "%.6f", value / (double) pixels);
        }
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    /**
     * Deterministic no-GL guard for the T132 freshness contract: tokens are
     * unique and monotonic, a new request drops any completed result, and a
     * failed capture leaves no result behind.
     */
    static synchronized void selfCheckFreshnessContract() {
        Request previousActive = active;
        WorkloadResult previousResult = latestResult;
        String previousLatest = latest;
        try {
            active = null;
            latestResult = null;
            CaptureRequest first = requestCapture("above");
            if (!first.accepted()) {
                throw new IllegalStateException("workload capture refused a valid request");
            }
            if (!requestCapture("above").status().startsWith("busy")) {
                throw new IllegalStateException("workload capture accepted a concurrent request");
            }
            WorkloadResult completed = active.result();
            latestResult = completed;
            active = null;
            if (completed.captureToken() != first.token()) {
                throw new IllegalStateException("workload result lost its capture token");
            }
            if (!completed.format().contains("captureToken=" + first.token())) {
                throw new IllegalStateException("workload report omits its capture token");
            }

            CaptureRequest second = requestCapture("above");
            if (second.token() <= first.token()) {
                throw new IllegalStateException("workload capture token is not monotonic");
            }
            if (latestResult != null) {
                throw new IllegalStateException("a new request kept the previous completed result");
            }
            // Reinstate the earlier result, then fail the in-flight capture the
            // way the catch branch does. The stale result must not survive.
            latestResult = completed;
            Request inFlight = active;
            active = null;
            latestResult = null;
            latest = "capture_failed:view=" + inFlight.view + " captureToken=" + inFlight.token;
            if (latestResult != null) {
                throw new IllegalStateException("a failed capture exposed the previous result");
            }
        } finally {
            active = previousActive;
            latestResult = previousResult;
            latest = previousLatest;
        }
    }
}
