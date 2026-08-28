package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.Locale;

/**
 * T132's deterministic reference-frame capture.
 *
 * <p>The suite's neutrality evidence cannot come from a temporally accumulated
 * frame: at history blend 0.85 the composited image depends on the preceding
 * frame sequence, so two passes never agree even on an unchanged fixture at an
 * identical pose. This capture instead renders the production FINAL path with
 * temporal accumulation switched off for exactly the reference frames.
 *
 * <p>That single switch also pins the sampling phase. In
 * {@code cloud_atmosphere_volume.fsh} the jitter phase is
 * {@code jitterFrame = HistoryValid == 1 && HistoryBlend > 0.001 ? FrameIndex : 0.0},
 * so with history disabled the shader already takes its fixed, frame-independent
 * branch. The coarse-search offset {@code searchBlue} is keyed on
 * {@code gl_FragCoord} alone and is deterministic either way. No shader change
 * is required, and no production equation is touched.
 *
 * <p>The previous history setting is saved on request and restored by every
 * terminal path - completion, failure, and cancellation - so production
 * temporal behaviour is never left altered.
 */
final class StormReferenceImageCapture {
    /** Frames rendered with history disabled before the buffer is read back. */
    private static final int SETTLE_FRAMES = 3;
    /**
     * Consecutive frames whose {@code projection} component signature must be
     * bit-identical before a reference frame is accepted.
     *
     * <p>Minecraft's field of view is a per-tick interpolation towards a target,
     * so while it is settling after the suite's teleport the value changes every
     * frame and the signature changes with it. Once the target is reached the
     * value is produced by the same arithmetic each frame and the signature is
     * exactly repeated, so a run of identical signatures is convergence rather
     * than a slow drift. Three matches {@link #SETTLE_FRAMES} and the suite's
     * existing two-frame pose and governor confirmations; two would already
     * indicate convergence, and the third guards against a coincidentally equal
     * pair while the interpolation is crossing a float plateau.
     */
    private static final int REQUIRED_PROJECTION_STABLE_FRAMES = 3;
    /**
     * Bounded wait. At 60 frames per second this is roughly four seconds, well
     * beyond any FOV interpolation, and far below the suite's own 600-frame
     * per-view ceiling so a stuck projection surfaces here rather than as a
     * generic view timeout.
     */
    private static final int MAX_PROJECTION_WAIT_FRAMES = 240;
    /**
     * Consecutive frames whose background cloud-content signatures must all be
     * unchanged before a reference frame is accepted.
     *
     * <p>The candidate grid, the weather map and the descriptor publication all
     * rebuild on their own schedules. A rebuild that lands inside a settle
     * window changes what the shader samples without changing any uniform, which
     * is exactly the residual, wandering difference the adjacent pairs showed.
     * Three matches the projection and settle-frame conventions already used
     * here; nothing is frozen, the capture only waits.
     */
    private static final int REQUIRED_CONTENT_STABLE_FRAMES = 3;
    private static final int MAX_CONTENT_WAIT_FRAMES = 600;

    private static volatile Request active;
    private static volatile String latest = "not_captured";
    private static volatile StormReferenceImageComparison.Reference latestResult;
    /**
     * T132 Option B. While a suite is comparing reference frames, every frame of
     * the comparison renders at this one clock value so the WorldTime-driven
     * precipitation domain cannot animate between PASS A and PASS B. It is an
     * override on the value uploaded to the shader, not a change to the world
     * clock, the weather progression, or the rain equations, and it is armed
     * only for the duration of a suite's reference captures.
     */
    private static volatile boolean worldTimePinned;
    private static volatile float pinnedWorldTime;
    /** True once a pin value has been chosen for the current suite. */
    private static volatile boolean pinLatched;

    private StormReferenceImageCapture() {
    }

    /**
     * Begins a deterministic reference capture for {@code view}.
     *
     * <p>Any previously completed reference is dropped first: a stale buffer
     * must never be able to satisfy a later request, for the same reason the
     * workload capture carries a token.
     */
    static synchronized String request(String view) {
        return request(view, null);
    }

    static synchronized String request(String view, java.util.UUID fixtureGroupId) {
        if (active != null) {
            return "busy:frames=" + active.frames + "/" + SETTLE_FRAMES;
        }
        if (view == null || view.isBlank()) {
            return "invalid_view";
        }
        latestResult = null;
        boolean restoreHistory = VolumetricCloudDebugConfig.historyEnabled();
        active = new Request(view.trim(), restoreHistory, fixtureGroupId);
        VolumetricCloudDebugConfig.setHistoryEnabled(false);
        // Composes with the history bypass: history off pins the jitter phase
        // through the shader's existing HistoryValid branch, and this pins the
        // animation clock. Both are released by every terminal path below.
        beginSuitePinning(VolumetricCloudRenderer.lastDrawInputs().liveWorldTimeTicks());
        worldTimePinned = pinLatched;
        VolumetricCloudRenderer.invalidateHistory();
        latest = "acquiring view=" + active.view + " frames=0/" + SETTLE_FRAMES
                + " historyBypassed=true restoreHistoryEnabled=" + restoreHistory
                + " worldTimePinned=" + worldTimePinned
                + " pinnedWorldTime=" + String.format(Locale.ROOT, "%.5f", pinnedWorldTime);
        return latest;
    }

    static boolean active() {
        return active != null;
    }

    /** True while the diagnostic is overriding the uploaded WorldTime uniform. */
    static boolean worldTimePinned() {
        return worldTimePinned;
    }

    /** The clock a draw should upload: the pinned value while armed, else live. */
    static float effectiveWorldTime(float liveWorldTime) {
        return worldTimePinned ? pinnedWorldTime : liveWorldTime;
    }

    /**
     * Arms the suite-wide clock pin. The first reference capture of a suite
     * latches the live clock; every later capture in the same suite reuses it,
     * so PASS A and PASS B of every view render at one identical value.
     */
    static synchronized void beginSuitePinning(float liveWorldTime) {
        if (!pinLatched) {
            pinnedWorldTime = liveWorldTime;
            pinLatched = true;
        }
    }

    /**
     * Disarms the pin and forgets the latched value. Called when a suite ends
     * for any reason, so a later suite or session can never inherit a stale pin.
     */
    static synchronized void endSuitePinning() {
        worldTimePinned = false;
        pinLatched = false;
        pinnedWorldTime = 0.0F;
    }

    /** The clock value this suite pins to, meaningful only while latched. */
    static float pinnedWorldTime() {
        return pinnedWorldTime;
    }

    static boolean pinLatched() {
        return pinLatched;
    }

    static String latest() {
        return latest;
    }

    /** Most recent completed deterministic reference, or {@code null}. */
    static StormReferenceImageComparison.Reference latestResult() {
        return latestResult;
    }

    /** Restores production history state without producing a reference. */
    static synchronized void cancel() {
        Request request = active;
        if (request == null) {
            return;
        }
        active = null;
        latestResult = null;
        restore(request);
        latest = "cancelled view=" + request.view;
    }

    static synchronized void capture(RenderTarget target) {
        Request request = active;
        if (request == null || target == null || !RenderSystem.isOnRenderThread()
                || target.getColorTextureId() <= 0 || target.width <= 0 || target.height <= 0) {
            return;
        }
        VolumetricCloudRenderer.LastDrawInputs inputs = VolumetricCloudRenderer.lastDrawInputs();
        if (!inputs.valid() || inputs.debugView() != VolumetricCloudRaymarchDebugView.FINAL) {
            latest = "acquiring view=" + request.view + " waiting_for_final_draw";
            return;
        }
        // The volumetric pass composites against scene depth. On the first
        // arrival at a pose the terrain is still being meshed, so the depth
        // buffer - which no uniform signature covers - is still changing. Wait
        // for chunk compilation to drain before any frame counts.
        if (!Minecraft.getInstance().levelRenderer.hasRenderedAllChunks()) {
            request.frames = 0;
            request.terrainWaitFrames++;
            latest = "acquiring view=" + request.view + " waiting_for_terrain frames="
                    + request.terrainWaitFrames;
            return;
        }
        if (inputs.historyValid()) {
            // The disabled flag has not reached a drawn frame yet. Do not count
            // this frame; a temporally accumulated one must never be read back.
            request.frames = 0;
            latest = "acquiring view=" + request.view + " waiting_for_history_bypass";
            return;
        }
        // The projection must have settled before any frame counts towards the
        // readback: a changing FOV reprojects the whole image, so an unsettled
        // frame would compare as a rendering difference.
        long projectionSignature = inputs.comparisonUniformComponents().projection();
        if (!request.projection.observe(projectionSignature)) {
            request.frames = 0;
            if (request.projection.timedOut()) {
                active = null;
                latestResult = null;
                restore(request);
                latest = "capture_failed:projection_stability_timeout view=" + request.view
                        + ' ' + request.projection.format(
                                inputs.comparisonUniformComponents().inverseProjection(), false);
                return;
            }
            latest = "acquiring view=" + request.view + " waiting_for_projection_stability "
                    + request.projection.format(
                            inputs.comparisonUniformComponents().inverseProjection(), false);
            return;
        }
        StormCloudContent content = StormCloudContent.capture(request.fixtureGroupId);
        long weatherSignature = CloudWeatherMapRenderer.lastInputSignatureForDiagnostics();
        if (!request.content.observe(content, weatherSignature)) {
            request.frames = 0;
            if (request.content.timedOut()) {
                active = null;
                latestResult = null;
                restore(request);
                latest = "capture_failed:content_stability_timeout view=" + request.view
                        + ' ' + request.content.format(false);
                return;
            }
            latest = "acquiring view=" + request.view + " waiting_for_content_stability "
                    + request.content.format(false);
            return;
        }
        if (++request.frames < SETTLE_FRAMES) {
            latest = "acquiring view=" + request.view
                    + " frames=" + request.frames + "/" + SETTLE_FRAMES;
            return;
        }

        FloatBuffer buffer = BufferUtils.createFloatBuffer(target.width * target.height * 4);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, buffer);
            float[] pixels = new float[target.width * target.height * 4];
            buffer.get(pixels);
            String digest = StormReferenceImageComparison.digest(pixels, target.width, target.height);
            latestResult = new StormReferenceImageComparison.Reference(
                    request.view, target.width, target.height, true, digest, pixels,
                    inputs.worldTimeTicks(), inputs.liveWorldTimeTicks(), inputs.worldTimePinned(),
                    new StormSceneStability.RenderInputs(
                            inputs.comparisonUniformSignature(),
                            inputs.comparisonUniformComponents(),
                            CloudWeatherMapRenderer.lastInputSignatureForDiagnostics(),
                            request.projection.format(
                                    inputs.comparisonUniformComponents().inverseProjection(), true)
                                    + ' ' + request.content.format(true),
                            inputs.stormTopologyMode(),
                            inputs.optimizationDiagnosticMode()),
                    StormCloudContent.capture(request.fixtureGroupId));
            active = null;
            restore(request);
            latest = latestResult.format();
        } catch (RuntimeException exception) {
            active = null;
            latestResult = null;
            restore(request);
            latest = "capture_failed:" + exception.getClass().getSimpleName();
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    /**
     * Releases everything this capture armed. The latched suite value survives
     * so the next view pins identically, but the override itself stops applying
     * the moment a capture ends, so no ordinary frame is ever rendered at the
     * pinned clock.
     */
    private static void restore(Request request) {
        worldTimePinned = false;
        VolumetricCloudDebugConfig.setHistoryEnabled(request.restoreHistoryEnabled);
        VolumetricCloudRenderer.invalidateHistory();
    }

    /**
     * Deterministic no-GL guard that the diagnostic never leaks its temporary
     * history state into production, on any terminal path.
     */
    static synchronized void selfCheckHistoryRestoration() {
        boolean original = VolumetricCloudDebugConfig.historyEnabled();
        try {
            selfCheckWorldTimePinLifecycle();
            for (boolean startEnabled : new boolean[]{true, false}) {
                VolumetricCloudDebugConfig.setHistoryEnabled(startEnabled);
                String status = request("side");
                if (!status.startsWith("acquiring")) {
                    throw new IllegalStateException("reference capture refused a valid request: " + status);
                }
                if (VolumetricCloudDebugConfig.historyEnabled()) {
                    throw new IllegalStateException("reference capture did not bypass temporal history");
                }
                if (latestResult != null) {
                    throw new IllegalStateException("reference capture kept a stale result across a request");
                }
                cancel();
                if (VolumetricCloudDebugConfig.historyEnabled() != startEnabled) {
                    throw new IllegalStateException(
                            "reference capture did not restore historyEnabled=" + startEnabled);
                }
                if (active != null || latestResult != null) {
                    throw new IllegalStateException("reference capture left state after cancel");
                }
            }
        } finally {
            active = null;
            latestResult = null;
            latest = "not_captured";
            VolumetricCloudDebugConfig.setHistoryEnabled(original);
        }
    }

    /**
     * Deterministic no-GL guard for the T132 Option B clock pin: it applies only
     * during a reference capture, one latched value serves the whole suite, and
     * every terminal path releases it.
     */
    private static synchronized void selfCheckWorldTimePinLifecycle() {
        boolean originalHistory = VolumetricCloudDebugConfig.historyEnabled();
        try {
            endSuitePinning();
            // D. The override is inert outside a capture.
            if (worldTimePinned() || pinLatched()
                    || effectiveWorldTime(1234.5F) != 1234.5F) {
                throw new IllegalStateException("clock pin applied outside a reference capture");
            }

            // The pin arms with the capture and overrides the uploaded clock.
            beginSuitePinning(1067297.375F);
            worldTimePinned = pinLatched;
            if (!worldTimePinned() || effectiveWorldTime(9999.0F) != 1067297.375F) {
                throw new IllegalStateException("clock pin did not override the uploaded WorldTime");
            }
            // One latched value serves the whole suite: a later view cannot
            // relatch a newer clock, so PASS A and PASS B render identically.
            beginSuitePinning(1067837.750F);
            if (effectiveWorldTime(9999.0F) != 1067297.375F) {
                throw new IllegalStateException("a later capture relatched the suite clock");
            }

            // E. Completion restores live behaviour.
            Request completed = new Request("side", originalHistory);
            restore(completed);
            if (worldTimePinned() || effectiveWorldTime(4242.0F) != 4242.0F) {
                throw new IllegalStateException("completion left the clock pinned");
            }
            if (!pinLatched()) {
                throw new IllegalStateException("completion discarded the suite clock latch");
            }

            // F. A failed capture restores live behaviour.
            worldTimePinned = true;
            restore(new Request("far", originalHistory));
            if (worldTimePinned() || effectiveWorldTime(4242.0F) != 4242.0F) {
                throw new IllegalStateException("a failed capture left the clock pinned");
            }

            // G. Cancellation restores live behaviour.
            active = new Request("below", originalHistory);
            worldTimePinned = true;
            cancel();
            if (worldTimePinned() || active != null
                    || effectiveWorldTime(4242.0F) != 4242.0F) {
                throw new IllegalStateException("cancellation left the clock pinned");
            }

            // H. A new suite cannot inherit the previous latch.
            endSuitePinning();
            if (pinLatched() || pinnedWorldTime() != 0.0F) {
                throw new IllegalStateException("a suite inherited a stale clock latch");
            }
            beginSuitePinning(500.0F);
            if (pinnedWorldTime() != 500.0F) {
                throw new IllegalStateException("a fresh suite did not latch its own clock");
            }

            // I. The clock pin composes with the history bypass and both release.
            endSuitePinning();
            VolumetricCloudDebugConfig.setHistoryEnabled(true);
            String status = request("above");
            if (!status.startsWith("acquiring") || !status.contains("worldTimePinned=true")
                    || !status.contains("historyBypassed=true")) {
                throw new IllegalStateException("history bypass and clock pin did not compose");
            }
            if (VolumetricCloudDebugConfig.historyEnabled() || !worldTimePinned()) {
                throw new IllegalStateException("composed reference capture did not arm both controls");
            }
            cancel();
            if (!VolumetricCloudDebugConfig.historyEnabled() || worldTimePinned()) {
                throw new IllegalStateException("composed reference capture did not release both controls");
            }
        } finally {
            active = null;
            latestResult = null;
            latest = "not_captured";
            endSuitePinning();
            VolumetricCloudDebugConfig.setHistoryEnabled(originalHistory);
        }
    }

    private static final class Request {
        private final String view;
        private final boolean restoreHistoryEnabled;
        /** Identifies which published descriptors belong to the frozen fixture. */
        private final java.util.UUID fixtureGroupId;
        /** Fresh per request, so a new pose always starts a new stability window. */
        private final ProjectionSettle projection = new ProjectionSettle();
        private final ContentSettle content = new ContentSettle();
        private int frames;
        private int terrainWaitFrames;

        private Request(String view, boolean restoreHistoryEnabled) {
            this(view, restoreHistoryEnabled, null);
        }

        private Request(String view, boolean restoreHistoryEnabled, java.util.UUID fixtureGroupId) {
            this.view = view;
            this.restoreHistoryEnabled = restoreHistoryEnabled;
            this.fixtureGroupId = fixtureGroupId;
        }
    }

    /**
     * Consecutive-frame convergence tracker for the background cloud content.
     * Pure apart from the snapshot it is handed, so the sandbox can drive an
     * exact sequence through it.
     */
    static final class ContentSettle {
        private StormCloudContent last;
        private long lastWeather;
        private boolean seeded;
        private int stableFrames;
        private int observedFrames;
        private int changes;
        private final java.util.Map<String, Integer> changeCounts = new java.util.LinkedHashMap<>();

        boolean observe(StormCloudContent current, long weatherSignature) {
            observedFrames++;
            java.util.List<String> moved = new java.util.ArrayList<>();
            if (seeded) {
                if (last.puffCandidateSignature() != current.puffCandidateSignature()) {
                    moved.add("puffCandidateSignature");
                }
                if (last.puffDescriptorSignature() != current.puffDescriptorSignature()) {
                    moved.add("puffContentSignature");
                }
                if (last.puffLobeCount() != current.puffLobeCount()) {
                    moved.add("puffLobeCount");
                }
                if (lastWeather != weatherSignature) {
                    moved.add("weatherMapSignature");
                }
                if (last.stormContentSignature() != current.stormContentSignature()) {
                    moved.add("stormContentSignature");
                }
                if (last.stormDescriptorCount() != current.stormDescriptorCount()) {
                    moved.add("stormDescriptorCount");
                }
            }
            last = current;
            lastWeather = weatherSignature;
            if (!seeded) {
                seeded = true;
                stableFrames = 1;
                return stableFrames >= REQUIRED_CONTENT_STABLE_FRAMES;
            }
            if (moved.isEmpty()) {
                stableFrames++;
            } else {
                changes++;
                for (String name : moved) {
                    changeCounts.merge(name, 1, Integer::sum);
                }
                stableFrames = 1;
            }
            return stableFrames >= REQUIRED_CONTENT_STABLE_FRAMES;
        }

        boolean timedOut() {
            return observedFrames >= MAX_CONTENT_WAIT_FRAMES;
        }

        String format(boolean stabilized) {
            StringBuilder out = new StringBuilder("contentStability={requiredStableFrames=")
                    .append(REQUIRED_CONTENT_STABLE_FRAMES)
                    .append(" observedFrames=").append(observedFrames)
                    .append(" changes=").append(changes)
                    .append(" finalStableFrames=").append(stableFrames)
                    .append(" stabilized=").append(stabilized);
            if (last != null) {
                out.append(" puffCandidateSignature=")
                        .append(Long.toHexString(last.puffCandidateSignature()))
                        .append(" puffContentSignature=")
                        .append(Long.toHexString(last.puffDescriptorSignature()))
                        .append(" weatherMapSignature=").append(Long.toHexString(lastWeather))
                        .append(" stormContentSignature=")
                        .append(Long.toHexString(last.stormContentSignature()))
                        .append(" stormDescriptorCount=").append(last.stormDescriptorCount())
                        .append(" puffLobeCount=").append(last.puffLobeCount());
            }
            out.append(" changedSignatures=")
                    .append(changeCounts.isEmpty() ? "none" : changeCounts.toString());
            return out.append('}').toString();
        }
    }

    /**
     * Consecutive-frame convergence tracker for the projection signature. Pure
     * and GL-free so the deterministic sandbox can drive an exact frame
     * sequence through it.
     */
    static final class ProjectionSettle {
        private long lastSignature;
        private boolean hasSignature;
        private int stableFrames;
        private int observedFrames;
        private int projectionChanges;

        /** Feeds one rendered frame. Returns true once the run is long enough. */
        boolean observe(long signature) {
            observedFrames++;
            if (!hasSignature || signature != lastSignature) {
                if (hasSignature) {
                    projectionChanges++;
                }
                lastSignature = signature;
                hasSignature = true;
                stableFrames = 1;
            } else {
                stableFrames++;
            }
            return stableFrames >= REQUIRED_PROJECTION_STABLE_FRAMES;
        }

        boolean timedOut() {
            return observedFrames >= MAX_PROJECTION_WAIT_FRAMES;
        }

        int stableFrames() {
            return stableFrames;
        }

        int observedFrames() {
            return observedFrames;
        }

        int projectionChanges() {
            return projectionChanges;
        }

        long signature() {
            return lastSignature;
        }

        String format(long inverseProjectionSignature, boolean stabilized) {
            return "projectionStability={requiredStableFrames=" + REQUIRED_PROJECTION_STABLE_FRAMES
                    + " observedFrames=" + observedFrames
                    + " projectionChanges=" + projectionChanges
                    + " finalStableFrames=" + stableFrames
                    + " projectionSignature=" + Long.toHexString(lastSignature)
                    + " inverseProjectionSignature=" + Long.toHexString(inverseProjectionSignature)
                    + " stabilized=" + stabilized + '}';
        }
    }
}
