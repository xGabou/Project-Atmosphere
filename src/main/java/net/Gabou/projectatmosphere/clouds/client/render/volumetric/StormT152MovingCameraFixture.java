package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.Locale;

/**
 * T152: the moving-camera fixture and its silhouette-stability metric.
 *
 * <p>Every performance measurement this feature has banked so far was taken at
 * a <em>static</em> pose. That is correct for cost, and blind to quality: the
 * shipped shader deliberately freezes its sampling lattice - {@code searchBlue}
 * is a static screen-space phase - precisely because moving it made thin
 * silhouette pixels alternate between hit and miss. A static pose cannot see
 * that alternation, so no static measurement can qualify a traversal change as
 * temporally safe. T151's interleaving candidate was rejected on its
 * performance ceiling before this fixture was needed; the visible-volume
 * traversal of T153-T157 is not going to be, so the metric has to exist first.
 *
 * <p>This drives one deterministic route and measures the rendered cloud
 * buffer on every frame of it. It is diagnostic-only and marker-gated: it
 * changes no production equation, no descriptor, no sample position and no
 * quality profile. The only production state it touches is the temporal-history
 * switch, which is the measured variable, and the player's own position; both
 * are saved on entry and restored on every terminal path.
 *
 * <h2>The route</h2>
 *
 * <p>A single cubic Bezier through the storm, scaled by the live fixture's own
 * radius and height, so the same route means the same thing on a bigger or
 * smaller system. It runs outside -&gt; approach -&gt; entry -&gt; interior -&gt;
 * openings -&gt; exit without ever stopping, and the camera looks along the
 * analytic tangent. The tangent of a cubic Bezier is continuous, so the heading
 * never steps: a yaw discontinuity would spike every temporal metric at exactly
 * the segment boundaries the metrics are supposed to characterise, and would be
 * indistinguishable from a real instability.
 *
 * <p>Position is advanced once per <em>rendered frame</em>, not once per tick.
 * A tick-driven route measures nothing at 1-2 frames per second, because the
 * camera would sit still for most rendered frames and let temporal
 * accumulation converge - which is the one thing a motion fixture must not
 * allow. The camera is placed with {@code moveTo} plus
 * {@code setOldPosAndRot}, so the render-time interpolation between the
 * previous and current position is exactly the identity and the frame is drawn
 * at the requested route point rather than somewhere between two of them.
 *
 * <h2>The two arms</h2>
 *
 * <p>Ghosting and disocclusion are not measurable inside one arm: a single
 * temporally accumulated sequence has nothing to be compared against. The route
 * therefore runs twice - once with history disabled, once with production
 * history - and the two are compared frame index against frame index. The
 * history-off arm is <strong>not</strong> an image-quality reference; it is
 * noisier by construction, because it is the un-accumulated jittered march.
 * It is the reference for <em>what temporal accumulation changes under
 * motion</em>, which is exactly the ghosting/disocclusion question, and it is
 * only ever used that way.
 *
 * <p>The cross-arm comparison is done on a fixed {@value #GHOST_WIDTH}x{@value
 * #GHOST_HEIGHT} box-downsample of the alpha channel, so the stored sequence is
 * bounded regardless of the render resolution. Ghosting is a spatially broad
 * effect and survives that reduction; the within-arm metrics below are all
 * computed at full resolution, where they need to be.
 *
 * <h2>What each metric is</h2>
 *
 * <ul>
 *   <li><b>coverage / edgeFraction</b> - fraction of pixels the march resolved
 *       as opaque cloud, and fraction sitting in the partial-alpha transition
 *       band. Silhouette softness shows up in the second one.</li>
 *   <li><b>silCx/silCy/silW/silH</b> - centroid and bounding extent of the
 *       covered set, in pixels. Silhouette position and width.</li>
 *   <li><b>dMean/dMax</b> - mean and max per-pixel alpha change against the
 *       previous frame. Under a moving camera this is large by construction;
 *       it is the denominator the flicker terms are read against, not a
 *       fault on its own.</li>
 *   <li><b>flipOn/flipOff</b> - pixels crossing the coverage threshold this
 *       frame in each direction.</li>
 *   <li><b>flicker</b> - pixels that crossed the threshold this frame in the
 *       <em>opposite</em> direction to their previous crossing. This is the
 *       hit/miss alternation the frozen lattice exists to prevent, and it is
 *       deliberately distinguished from ordinary flipping: a silhouette
 *       sweeping across the screen flips a great many pixels once each, and
 *       that is not flicker.</li>
 *   <li><b>colRunsMean/Max</b> - separate covered runs per occupied column.
 *       Greater than one means that column's cloud is broken into disconnected
 *       pieces on screen. Column connectivity.</li>
 *   <li><b>reEntryMean</b> - empty gaps lying strictly inside a column's
 *       covered span, i.e. occupied -&gt; empty -&gt; occupied along the
 *       column. This is the image-space signature of the traversal continuity
 *       T155 must preserve, measured on the shipped renderer so a later change
 *       has a baseline to be judged against.</li>
 *   <li><b>innerSkyRunMax/Mean</b> - the longest such gap. A hollow shell or a
 *       lost re-entry shows here as a growing inner-sky run.</li>
 *   <li><b>ghostMean/Max/Bias</b> (history arm only) - per-pixel difference
 *       against the same frame index of the history-off arm. A positive bias
 *       means accumulation is retaining cloud where the current frame has
 *       none, which is trailing smear.</li>
 *   <li><b>disoccFraction / disoccGhost</b> (history arm only) - restricted to
 *       pixels whose coverage state changed in the history-off arm between the
 *       previous frame and this one. Those are the pixels reprojection has no
 *       valid history for, so the ghost error there is the disocclusion
 *       error.</li>
 * </ul>
 */
final class StormT152MovingCameraFixture {

    /**
     * Route points, and therefore rendered frames, per arm.
     *
     * <p>Bounded by the fixture's own lifetime, not by what the metric would
     * ideally like. The first run measured the storm dissipating 9.0 minutes
     * after the route began, against a 3600-frame two-arm route that needed
     * 13.3 at the observed ~9 fps, and arm 2 spent its whole interior over
     * empty sky. Two arms of 2200 complete in about 8.1 minutes.
     */
    private static final int ROUTE_FRAMES = 2200;

    /**
     * Sub-steps used to integrate the curve when the route is reparameterised.
     * Fine enough that the arc-length and angular integrals are exact to well
     * under one frame's advance.
     */
    private static final int REPARAMETERIZE_STEPS = 200_000;

    /**
     * Bounds on the "distance to the nearest cloud surface" the angular step is
     * taken against. The floor stands in for material near the camera once it
     * is inside the system, where the bounding cylinder no longer describes
     * what the eye is close to; the ceiling stops the far approach from taking
     * enormous strides through a region where nothing moves on screen anyway.
     */
    private static final double SURFACE_SCALE_FLOOR = 45.0D;
    private static final double SURFACE_SCALE_CEILING = 600.0D;

    /**
     * Separate ceiling once the camera is inside the system's footprint.
     * Distance to the cylinder wall is the wrong scale there - it is largest at
     * the deepest point, where the camera is in fact enveloped by near material
     * - so without its own bound the deep interior would take the coarsest
     * strides on the whole route. Measured on the live fixture, the shared
     * 600-block ceiling gives 3.0 blocks/frame at the closest approach against
     * a 150-block bound's 1.0.
     */
    private static final double INTERIOR_SCALE_CEILING = 180.0D;

    /** Frames held at the route's first point before the first measured frame. */
    private static final int SETTLE_FRAMES = 45;

    /** Alpha at or above this counts as resolved cloud. */
    private static final float COVERED = 0.5F;

    /** Partial-alpha band bounds for the soft-edge fraction. */
    private static final float EDGE_LOW = 0.02F;
    private static final float EDGE_HIGH = 0.98F;

    /** Alpha difference below this is not counted as a ghost disagreement. */
    private static final float GHOST_EPSILON = 0.02F;

    /** Fixed grid the cross-arm comparison is stored and compared on. */
    private static final int GHOST_WIDTH = 160;
    private static final int GHOST_HEIGHT = 90;
    private static final int GHOST_PIXELS = GHOST_WIDTH * GHOST_HEIGHT;

    /** Frames a single arm may spend failing to render before the run is abandoned. */
    private static final int ARM_STALL_FRAMES = 3600;

    /**
     * Descriptors the fixture must keep publishing for the route to mean
     * anything. The first run recorded a complete two-arm route in which the
     * storm dissipated partway through arm 2 - `stormDescriptors` fell 10 to 0
     * - and still reported success, because the between-arms identity check
     * compared a cached fixture against itself and could not see it. Every
     * frame now proves the storm is still published before it is recorded,
     * which is the same discipline T150 imposed on the pose sweep after empty
     * cells corrupted three separate measurement runs.
     */
    private static final int REQUIRED_DESCRIPTORS = 10;

    private enum Arm {
        /** Reference arm: temporal accumulation off. Runs first. */
        HISTORY_OFF("history_off"),
        /** Production arm: temporal accumulation on. Compared against the first. */
        HISTORY_ON("history_on");

        private final String label;

        Arm(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private enum State { IDLE, SETTLE, RUN, DONE }

    private static State state = State.IDLE;
    private static Arm arm = Arm.HISTORY_OFF;
    private static int routeIndex;
    private static int settleFrames;
    private static int stallFrames;
    private static String status = "not_started";

    /** Route geometry, resolved once per run so both arms share one fixture. */
    private static double centerX;
    private static double centerZ;
    private static double midY;
    private static double radius;
    private static double height;
    private static String fixtureGroupId = "";
    private static String fixtureFingerprint = "";

    private static boolean originalHistoryEnabled = true;
    private static boolean historyOverrideApplied;
    private static boolean cameraModeApplied;

    /** Full-resolution alpha of the current and previous frame of this arm. */
    private static float[] alpha = new float[0];
    private static float[] previousAlpha = new float[0];
    private static boolean previousAlphaValid;
    private static int alphaWidth;
    private static int alphaHeight;

    /**
     * Per-pixel record of the direction of each pixel's last threshold
     * crossing: 0 none seen yet, 1 last crossed to covered, -1 last crossed to
     * empty. Flicker is a crossing that reverses this.
     */
    private static byte[] lastFlipDirection = new byte[0];

    /** Downsampled alpha of the reference arm, one entry per route point. */
    private static byte[][] referenceGhostFrames;

    /** Scratch for the current frame's downsample. */
    private static byte[] ghostScratch = new byte[GHOST_PIXELS];

    private static FloatBuffer readbackBuffer;
    private static int readbackCapacity;

    /**
     * Curve parameter for each frame. Precomputed at {@link #begin()} so both
     * arms walk identical points, and so the reparameterisation is resolved
     * once rather than per frame.
     */
    private static double[] routeT = new double[0];

    /** Resolved constant angular advance per frame, in radians. */
    private static double angularStep;

    private static double previousCameraX;
    private static double previousCameraY;
    private static double previousCameraZ;
    private static boolean previousCameraValid;

    private StormT152MovingCameraFixture() {
    }

    static boolean active() {
        return state == State.SETTLE || state == State.RUN;
    }

    static boolean finished() {
        return state == State.DONE;
    }

    static String latest() {
        return status;
    }

    /**
     * Resolves the route against the live fixture and arms the first pass.
     *
     * <p>Both arms are driven from this one resolution, so a fixture that
     * decays between them is detected as a changed identity rather than
     * silently producing two routes through two different storms.
     */
    static String begin() {
        StormPerformanceBaseline.SuiteFixture fixture = StormPerformanceBaseline.suiteFixture();
        if (fixture == null) {
            status = "t152_no_fixture";
            return status;
        }
        if (fixture.horizontalRadius() <= 1.0D || fixture.topY() - fixture.baseY() <= 1.0D) {
            status = "t152_degenerate_fixture radius=" + fixture.horizontalRadius();
            return status;
        }
        centerX = fixture.centerX();
        centerZ = fixture.centerZ();
        midY = (fixture.baseY() + fixture.topY()) * 0.5D;
        radius = fixture.horizontalRadius();
        height = Math.max(1.0D, fixture.topY() - fixture.baseY());
        fixtureGroupId = fixture.groupId();
        fixtureFingerprint = fixture.structuralFingerprint();

        buildRouteParameterisation();
        originalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
        referenceGhostFrames = new byte[ROUTE_FRAMES][];
        arm = Arm.HISTORY_OFF;
        routeIndex = 0;
        settleFrames = 0;
        stallFrames = 0;
        previousAlphaValid = false;
        previousCameraValid = false;
        state = State.SETTLE;

        applyArmHistory();
        ProjectAtmosphere.LOGGER.info(
                "T152_ROUTE_BEGIN group={} fingerprint={} centre=({},{}) midY={} radius={}"
                        + " height={} frames={} arms=2 priorHistory={} restoredOnExit=true",
                fixtureGroupId, fixtureFingerprint, fmt(centerX), fmt(centerZ), fmt(midY),
                fmt(radius), fmt(height), ROUTE_FRAMES, originalHistoryEnabled);
        ProjectAtmosphere.LOGGER.info(
                "T152_ROUTE_PARAMETERISATION angularStepRad={} surfaceScaleFloor={}"
                        + " surfaceScaleCeiling={} interiorScaleCeiling={}"
                        + " minStepBlocks={} maxStepBlocks={}",
                fmt(angularStep), fmt(SURFACE_SCALE_FLOOR), fmt(SURFACE_SCALE_CEILING),
                fmt(INTERIOR_SCALE_CEILING), fmt(minStepBlocks()), fmt(maxStepBlocks()));
        ProjectAtmosphere.LOGGER.info(
                "T152_ARM_BEGIN arm={} history={}", arm.label(), arm == Arm.HISTORY_ON);
        status = "t152_started";
        return status;
    }

    /**
     * Reads back the frame that was just drawn, records it, and places the
     * camera for the next one.
     *
     * <p>Called once per rendered frame from the render hook, on the render
     * thread, after the production draw has completed.
     */
    static void capture(RenderTarget target) {
        if (!active() || target == null || !RenderSystem.isOnRenderThread()
                || target.getColorTextureId() <= 0 || target.width <= 0 || target.height <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            abort("player_absent");
            return;
        }
        VolumetricCloudRenderer.LastDrawInputs inputs = VolumetricCloudRenderer.lastDrawInputs();
        if (!inputs.valid() || inputs.debugView() != VolumetricCloudRaymarchDebugView.FINAL) {
            if (++stallFrames > ARM_STALL_FRAMES) {
                abort("no_production_frame");
            }
            return;
        }
        // The history switch is uploaded per draw. Until the frame actually
        // drawn agrees with the arm, the readback would belong to the other
        // arm's temporal state.
        boolean wantHistory = arm == Arm.HISTORY_ON;
        if (!wantHistory && inputs.historyValid()) {
            if (++stallFrames > ARM_STALL_FRAMES) {
                abort("history_bypass_never_landed");
            }
            return;
        }
        if (StormGeometryBuildCoordinator.lobeCount() < REQUIRED_DESCRIPTORS) {
            abort("fixture_dissipated lobeCount="
                    + StormGeometryBuildCoordinator.lobeCount());
            return;
        }
        if (!minecraft.levelRenderer.hasRenderedAllChunks()) {
            if (++stallFrames > ARM_STALL_FRAMES) {
                abort("terrain_never_settled");
            }
            return;
        }
        stallFrames = 0;

        if (state == State.SETTLE) {
            // Hold at the first route point so the arm starts from a converged
            // state rather than from whatever the previous arm left behind.
            placeCamera(player, 0);
            if (++settleFrames >= SETTLE_FRAMES) {
                state = State.RUN;
                previousAlphaValid = false;
                previousCameraValid = false;
                java.util.Arrays.fill(lastFlipDirection, (byte) 0);
            }
            return;
        }

        if (!readAlpha(target)) {
            abort("readback_failed");
            return;
        }
        recordFrame(player, inputs);

        routeIndex++;
        if (routeIndex >= ROUTE_FRAMES) {
            completeArm(player);
            return;
        }
        placeCamera(player, routeIndex);
    }

    /** Finishes the current arm and either starts the second or reports. */
    private static void completeArm(LocalPlayer player) {
        ProjectAtmosphere.LOGGER.info("T152_ARM_END arm={} frames={}", arm.label(), ROUTE_FRAMES);
        if (arm == Arm.HISTORY_OFF) {
            // Re-resolve rather than reading the cached fixture back. The cache
            // is filled once when the driver resolves the fixture, so comparing
            // it here would compare the stored value against itself and pass
            // however far the live storm had drifted.
            StormPerformanceBaseline.begin(player.getX(), player.getY(), player.getZ());
            StormPerformanceBaseline.SuiteFixture fixture = StormPerformanceBaseline.suiteFixture();
            if (fixture == null || !fixture.groupId().equals(fixtureGroupId)
                    || !fixture.structuralFingerprint().equals(fixtureFingerprint)) {
                // Two routes through two different storms compare nothing. Say
                // so rather than reporting a ghost figure built from them.
                abort("fixture_identity_changed_between_arms");
                return;
            }
            arm = Arm.HISTORY_ON;
            routeIndex = 0;
            settleFrames = 0;
            previousAlphaValid = false;
            previousCameraValid = false;
            state = State.SETTLE;
            applyArmHistory();
            ProjectAtmosphere.LOGGER.info(
                    "T152_ARM_BEGIN arm={} history={}", arm.label(), true);
            placeCamera(player, 0);
            return;
        }
        restore();
        state = State.DONE;
        status = "t152_complete";
        ProjectAtmosphere.LOGGER.info(
                "T152_ROUTE_COMPLETE group={} frames={} arms=2", fixtureGroupId, ROUTE_FRAMES);
    }

    private static void abort(String reason) {
        restore();
        state = State.DONE;
        status = "t152_aborted:" + reason;
        ProjectAtmosphere.LOGGER.warn("T152_ROUTE_ABORT reason={} arm={} frame={}",
                reason, arm.label(), routeIndex);
    }

    private static void applyArmHistory() {
        VolumetricCloudDebugConfig.setHistoryEnabled(arm == Arm.HISTORY_ON);
        historyOverrideApplied = true;
    }

    private static void restore() {
        if (historyOverrideApplied) {
            VolumetricCloudDebugConfig.setHistoryEnabled(originalHistoryEnabled);
            historyOverrideApplied = false;
        }
        referenceGhostFrames = null;
    }

    // ------------------------------------------------------------------
    // Route
    // ------------------------------------------------------------------

    /**
     * Resolves the curve parameter for every frame.
     *
     * <p>Uniform curve parameterisation was measured and rejected. On the live
     * T134-scale fixture - horizontal radius 663 blocks - an evenly spaced
     * 480-frame route advances about 8.8 blocks per frame everywhere, which is
     * roughly 12 px/frame against distant material but <b>90 to 164 px/frame
     * through entry, interior and exit</b>. Those are the segments this metric
     * exists to read, and at that rate reprojection fails everywhere in them,
     * so every temporal term saturates and the fixture cannot discriminate one
     * renderer from another - which is the only thing it is for.
     *
     * <p>Each frame therefore advances a constant <em>angular</em> step against
     * the nearest cloud surface rather than a constant world-space step: large
     * strides far out, where nothing moves on screen anyway, and small ones
     * inside, where everything does. The total angular budget of the curve is
     * integrated first, then divided by the frame count, so the frame count is
     * fixed and the step follows from it.
     */
    private static void buildRouteParameterisation() {
        routeT = new double[ROUTE_FRAMES];
        double[] lengths = new double[REPARAMETERIZE_STEPS];
        double[] scales = new double[REPARAMETERIZE_STEPS];
        double budget = 0.0D;
        Vec3 previous = routePoint(0.0D);
        for (int step = 0; step < REPARAMETERIZE_STEPS; step++) {
            double t = (double) (step + 1) / REPARAMETERIZE_STEPS;
            Vec3 point = routePoint(t);
            double length = point.distanceTo(previous);
            double scale = surfaceScale((step + 0.5D) / REPARAMETERIZE_STEPS);
            lengths[step] = length;
            scales[step] = scale;
            budget += length / scale;
            previous = point;
        }
        angularStep = ROUTE_FRAMES <= 1 ? budget : budget / (ROUTE_FRAMES - 1);

        routeT[0] = 0.0D;
        int frame = 1;
        double accumulated = 0.0D;
        for (int step = 0; step < REPARAMETERIZE_STEPS && frame < ROUTE_FRAMES; step++) {
            accumulated += lengths[step] / scales[step];
            while (accumulated >= angularStep && frame < ROUTE_FRAMES) {
                accumulated -= angularStep;
                routeT[frame++] = (double) (step + 1) / REPARAMETERIZE_STEPS;
            }
        }
        // Rounding can leave the last frame or two unassigned; pin them to the
        // curve's end rather than to zero.
        while (frame < ROUTE_FRAMES) {
            routeT[frame++] = 1.0D;
        }
    }

    /**
     * Distance from a route point to the system's bounding cylinder, clamped.
     * Inside the cylinder the cylinder says nothing about what the camera is
     * actually near, so the floor takes over.
     */
    private static double surfaceScale(double t) {
        Vec3 point = routePoint(t);
        double horizontal = Math.hypot(point.x - centerX, point.z - centerZ);
        double ceiling = horizontal >= radius
                ? SURFACE_SCALE_CEILING
                : INTERIOR_SCALE_CEILING;
        return Math.min(ceiling,
                Math.max(SURFACE_SCALE_FLOOR, Math.abs(horizontal - radius)));
    }

    private static double parameterAt(int index) {
        if (routeT.length == 0) {
            return 0.0D;
        }
        return routeT[Math.max(0, Math.min(routeT.length - 1, index))];
    }

    private static double stepBlocksAt(int index) {
        if (index <= 0 || index >= routeT.length) {
            return 0.0D;
        }
        return routePoint(routeT[index]).distanceTo(routePoint(routeT[index - 1]));
    }

    private static double minStepBlocks() {
        double min = Double.MAX_VALUE;
        for (int index = 1; index < routeT.length; index++) {
            min = Math.min(min, stepBlocksAt(index));
        }
        return min == Double.MAX_VALUE ? 0.0D : min;
    }

    private static double maxStepBlocks() {
        double max = 0.0D;
        for (int index = 1; index < routeT.length; index++) {
            max = Math.max(max, stepBlocksAt(index));
        }
        return max;
    }

    /**
     * The route point for a frame index, as a cubic Bezier in world space.
     *
     * <p>Control points are expressed in multiples of the fixture's own
     * horizontal radius and vertical extent. The curve starts well outside the
     * system, crosses its boundary, passes within a quarter radius of the
     * centre at the midpoint - so the interior segment is genuinely interior,
     * not a grazing pass - and leaves on the far side and above, which puts an
     * exit and a re-entry-shaped view of the flank in the same route.
     */
    private static Vec3 routePoint(double t) {
        double u = 1.0D - t;
        double b0 = u * u * u;
        double b1 = 3.0D * u * u * t;
        double b2 = 3.0D * u * t * t;
        double b3 = t * t * t;

        double x0 = centerX + radius * 3.00D;
        double z0 = centerZ - radius * 1.20D;
        double y0 = midY - height * 0.20D;

        double x1 = centerX + radius * 1.10D;
        double z1 = centerZ - radius * 0.45D;
        double y1 = midY - height * 0.05D;

        double x2 = centerX - radius * 0.55D;
        double z2 = centerZ + radius * 0.45D;
        double y2 = midY + height * 0.10D;

        double x3 = centerX - radius * 2.60D;
        double z3 = centerZ + radius * 1.70D;
        double y3 = midY + height * 0.18D;

        return new Vec3(
                b0 * x0 + b1 * x1 + b2 * x2 + b3 * x3,
                b0 * y0 + b1 * y1 + b2 * y2 + b3 * y3,
                b0 * z0 + b1 * z1 + b2 * z2 + b3 * z3);
    }

    /** Analytic tangent, so the heading is continuous across the whole route. */
    private static Vec3 routeTangent(double t) {
        double u = 1.0D - t;
        double d0 = 3.0D * u * u;
        double d1 = 6.0D * u * t;
        double d2 = 3.0D * t * t;

        double x0 = centerX + radius * 3.00D;
        double z0 = centerZ - radius * 1.20D;
        double y0 = midY - height * 0.20D;

        double x1 = centerX + radius * 1.10D;
        double z1 = centerZ - radius * 0.45D;
        double y1 = midY - height * 0.05D;

        double x2 = centerX - radius * 0.55D;
        double z2 = centerZ + radius * 0.45D;
        double y2 = midY + height * 0.10D;

        double x3 = centerX - radius * 2.60D;
        double z3 = centerZ + radius * 1.70D;
        double y3 = midY + height * 0.18D;

        return new Vec3(
                d0 * (x1 - x0) + d1 * (x2 - x1) + d2 * (x3 - x2),
                d0 * (y1 - y0) + d1 * (y2 - y1) + d2 * (y3 - y2),
                d0 * (z1 - z0) + d1 * (z2 - z1) + d2 * (z3 - z2));
    }

    /**
     * Coarse label for a frame, by its distance from the system's axis. These
     * are reporting bands over one continuous curve, not separate motions: the
     * camera never stops or turns at a boundary.
     */
    private static String segment(Vec3 point) {
        double horizontal = Math.hypot(point.x - centerX, point.z - centerZ);
        double normalized = horizontal / radius;
        if (normalized > 2.0D) {
            return "OUTSIDE";
        }
        if (normalized > 1.15D) {
            return "APPROACH";
        }
        if (normalized > 0.85D) {
            return "ENTRY";
        }
        return "INTERIOR";
    }

    private static void placeCamera(LocalPlayer player, int index) {
        double t = parameterAt(index);
        Vec3 point = routePoint(t);
        Vec3 tangent = routeTangent(t);
        double horizontal = Math.hypot(tangent.x, tangent.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(-tangent.x, tangent.z)));
        float pitch = horizontal < 1.0E-6D
                ? 0.0F
                : (float) -Math.toDegrees(Math.atan2(tangent.y, horizontal));

        if (!cameraModeApplied) {
            player.connection.sendCommand("gamemode spectator");
            cameraModeApplied = true;
            ProjectAtmosphere.LOGGER.info(
                    "T152_CAMERA_MODE gamemode=spectator restoredByDriver=true");
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.moveTo(point.x, point.y, point.z, yaw, pitch);
        // moveTo alone leaves the previous position where it was, so the frame
        // would be drawn at an interpolated point between two route points
        // rather than at the requested one. Collapsing the old position onto
        // the new one makes the render-time interpolation the identity.
        player.setOldPosAndRot();
    }

    // ------------------------------------------------------------------
    // Measurement
    // ------------------------------------------------------------------

    private static boolean readAlpha(RenderTarget target) {
        int width = target.width;
        int height2 = target.height;
        int pixels = width * height2;
        int floats = pixels * 4;
        if (readbackBuffer == null || readbackCapacity < floats) {
            readbackBuffer = BufferUtils.createFloatBuffer(floats);
            readbackCapacity = floats;
        }
        if (alpha.length != pixels) {
            alpha = new float[pixels];
            previousAlpha = new float[pixels];
            lastFlipDirection = new byte[pixels];
            previousAlphaValid = false;
        }
        alphaWidth = width;
        alphaHeight = height2;

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            readbackBuffer.clear();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, readbackBuffer);
            for (int index = 0; index < pixels; index++) {
                alpha[index] = readbackBuffer.get(index * 4 + 3);
            }
            return true;
        } catch (RuntimeException exception) {
            ProjectAtmosphere.LOGGER.warn("T152 readback failed: {}", exception.toString());
            return false;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private static void recordFrame(
            LocalPlayer player, VolumetricCloudRenderer.LastDrawInputs inputs) {
        int width = alphaWidth;
        int height2 = alphaHeight;
        int pixels = width * height2;

        long covered = 0L;
        long edge = 0L;
        double sumX = 0.0D;
        double sumY = 0.0D;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        double deltaSum = 0.0D;
        float deltaMax = 0.0F;
        long flipOn = 0L;
        long flipOff = 0L;
        long flicker = 0L;

        for (int y = 0; y < height2; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int index = row + x;
                float value = alpha[index];
                boolean isCovered = value >= COVERED;
                if (isCovered) {
                    covered++;
                    sumX += x;
                    sumY += y;
                    if (x < minX) {
                        minX = x;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
                if (value >= EDGE_LOW && value < EDGE_HIGH) {
                    edge++;
                }
                if (previousAlphaValid) {
                    float previous = previousAlpha[index];
                    float delta = Math.abs(value - previous);
                    deltaSum += delta;
                    if (delta > deltaMax) {
                        deltaMax = delta;
                    }
                    boolean wasCovered = previous >= COVERED;
                    if (isCovered != wasCovered) {
                        byte direction = isCovered ? (byte) 1 : (byte) -1;
                        if (isCovered) {
                            flipOn++;
                        } else {
                            flipOff++;
                        }
                        // A pixel the silhouette sweeps over flips once. A pixel
                        // alternating between hit and miss flips back.
                        if (lastFlipDirection[index] == -direction) {
                            flicker++;
                        }
                        lastFlipDirection[index] = direction;
                    }
                }
            }
        }

        ColumnStatistics columns = measureColumns(width, height2);

        double t = parameterAt(routeIndex);
        Vec3 point = routePoint(t);
        double step = previousCameraValid
                ? Math.sqrt(sq(point.x - previousCameraX) + sq(point.y - previousCameraY)
                        + sq(point.z - previousCameraZ))
                : 0.0D;
        previousCameraX = point.x;
        previousCameraY = point.y;
        previousCameraZ = point.z;
        previousCameraValid = true;

        downsampleAlpha(width, height2);
        String ghost = "";
        if (arm == Arm.HISTORY_OFF) {
            referenceGhostFrames[routeIndex] = ghostScratch.clone();
        } else {
            ghost = compareAgainstReference();
        }

        StringBuilder line = new StringBuilder(384);
        line.append("T152_FRAME arm=").append(arm.label())
                .append(" frame=").append(routeIndex)
                .append(" t=").append(fmt(t))
                .append(" segment=").append(segment(point))
                .append(" cam=(").append(fmt(point.x)).append(',').append(fmt(point.y))
                .append(',').append(fmt(point.z)).append(')')
                .append(" stepBlocks=").append(fmt(step))
                .append(" surfScale=").append(fmt(surfaceScale(t)))
                .append(" cameraDensity=").append(fmt(inputs.cameraCloudDensity()))
                .append(" res=").append(width).append('x').append(height2)
                .append(" coverage=").append(fmt((double) covered / pixels))
                .append(" edgeFraction=").append(fmt((double) edge / pixels));
        if (covered > 0L) {
            line.append(" silCx=").append(fmt(sumX / covered))
                    .append(" silCy=").append(fmt(sumY / covered))
                    .append(" silW=").append(maxX - minX + 1)
                    .append(" silH=").append(maxY - minY + 1);
        } else {
            line.append(" silCx=none silCy=none silW=0 silH=0");
        }
        if (previousAlphaValid) {
            line.append(" dMean=").append(fmt(deltaSum / pixels))
                    .append(" dMax=").append(fmt(deltaMax))
                    .append(" flipOn=").append(fmt((double) flipOn / pixels))
                    .append(" flipOff=").append(fmt((double) flipOff / pixels))
                    .append(" flicker=").append(fmt((double) flicker / pixels));
        } else {
            line.append(" dMean=none dMax=none flipOn=none flipOff=none flicker=none");
        }
        line.append(" colRunsMean=").append(fmt(columns.runsMean()))
                .append(" colRunsMax=").append(columns.runsMax())
                .append(" reEntryMean=").append(fmt(columns.reEntryMean()))
                .append(" innerSkyRunMax=").append(columns.innerSkyRunMax())
                .append(" innerSkyRunMean=").append(fmt(columns.innerSkyRunMean()))
                .append(ghost);
        ProjectAtmosphere.LOGGER.info(line.toString());

        float[] swap = previousAlpha;
        previousAlpha = alpha;
        alpha = swap;
        previousAlphaValid = true;
        status = "t152_running arm=" + arm.label() + " frame=" + routeIndex;
    }

    private record ColumnStatistics(
            double runsMean, int runsMax, double reEntryMean,
            int innerSkyRunMax, double innerSkyRunMean) {
    }

    /**
     * Per-column connectivity of the covered set.
     *
     * <p>A column is scanned from the first covered pixel to the last. Every
     * empty stretch between those two ends is a gap the march left inside the
     * silhouette - a hole, an opening, or a lost re-entry - so counting the
     * gaps counts the occupied/empty/occupied transitions directly, and the
     * longest gap is the inner-sky run.
     */
    private static ColumnStatistics measureColumns(int width, int height2) {
        long runsTotal = 0L;
        int runsMax = 0;
        long gapsTotal = 0L;
        long gapLengthTotal = 0L;
        int gapLengthMax = 0;
        int occupiedColumns = 0;
        int gappedColumns = 0;

        for (int x = 0; x < width; x++) {
            int first = -1;
            int last = -1;
            for (int y = 0; y < height2; y++) {
                if (alpha[y * width + x] >= COVERED) {
                    if (first < 0) {
                        first = y;
                    }
                    last = y;
                }
            }
            if (first < 0) {
                continue;
            }
            occupiedColumns++;
            int runs = 0;
            int gaps = 0;
            boolean inRun = false;
            int gapLength = 0;
            int columnGapMax = 0;
            for (int y = first; y <= last; y++) {
                boolean covered = alpha[y * width + x] >= COVERED;
                if (covered) {
                    if (!inRun) {
                        runs++;
                        inRun = true;
                    }
                    if (gapLength > 0) {
                        gaps++;
                        gapLengthTotal += gapLength;
                        if (gapLength > columnGapMax) {
                            columnGapMax = gapLength;
                        }
                        if (gapLength > gapLengthMax) {
                            gapLengthMax = gapLength;
                        }
                        gapLength = 0;
                    }
                } else {
                    inRun = false;
                    gapLength++;
                }
            }
            runsTotal += runs;
            gapsTotal += gaps;
            if (runs > runsMax) {
                runsMax = runs;
            }
            if (columnGapMax > 0) {
                gappedColumns++;
            }
        }
        return new ColumnStatistics(
                occupiedColumns == 0 ? 0.0D : (double) runsTotal / occupiedColumns,
                runsMax,
                occupiedColumns == 0 ? 0.0D : (double) gapsTotal / occupiedColumns,
                gapLengthMax,
                gapsTotal == 0L ? 0.0D : (double) gapLengthTotal / gapsTotal);
    }

    /** Box-downsamples the current alpha onto the fixed comparison grid. */
    private static void downsampleAlpha(int width, int height2) {
        for (int gy = 0; gy < GHOST_HEIGHT; gy++) {
            int y0 = (int) ((long) gy * height2 / GHOST_HEIGHT);
            int y1 = Math.max(y0 + 1, (int) ((long) (gy + 1) * height2 / GHOST_HEIGHT));
            for (int gx = 0; gx < GHOST_WIDTH; gx++) {
                int x0 = (int) ((long) gx * width / GHOST_WIDTH);
                int x1 = Math.max(x0 + 1, (int) ((long) (gx + 1) * width / GHOST_WIDTH));
                double sum = 0.0D;
                int count = 0;
                for (int y = y0; y < y1 && y < height2; y++) {
                    int row = y * width;
                    for (int x = x0; x < x1 && x < width; x++) {
                        sum += alpha[row + x];
                        count++;
                    }
                }
                float mean = count == 0 ? 0.0F : (float) (sum / count);
                ghostScratch[gy * GHOST_WIDTH + gx] =
                        (byte) Math.round(Math.min(1.0F, Math.max(0.0F, mean)) * 255.0F);
            }
        }
    }

    /**
     * Compares this frame against the same frame index of the reference arm.
     *
     * <p>Disocclusion is isolated by the reference arm's own coverage change
     * between the previous frame and this one: those are the pixels that were
     * revealed or covered by the camera's motion, which are exactly the pixels
     * reprojection has no valid history for.
     */
    private static String compareAgainstReference() {
        byte[] reference = referenceGhostFrames == null ? null : referenceGhostFrames[routeIndex];
        if (reference == null) {
            return " ghostMean=none ghostMax=none ghostBias=none"
                    + " disoccFraction=none disoccGhost=none";
        }
        byte[] referencePrevious = routeIndex > 0 && referenceGhostFrames[routeIndex - 1] != null
                ? referenceGhostFrames[routeIndex - 1]
                : null;

        double sum = 0.0D;
        double bias = 0.0D;
        double max = 0.0D;
        long disocclusion = 0L;
        double disocclusionSum = 0.0D;
        for (int index = 0; index < GHOST_PIXELS; index++) {
            float here = (ghostScratch[index] & 0xFF) / 255.0F;
            float there = (reference[index] & 0xFF) / 255.0F;
            float signed = here - there;
            float difference = Math.abs(signed);
            if (difference < GHOST_EPSILON) {
                difference = 0.0F;
                signed = 0.0F;
            }
            sum += difference;
            bias += signed;
            if (difference > max) {
                max = difference;
            }
            if (referencePrevious != null) {
                boolean coveredNow = there >= COVERED;
                boolean coveredBefore = (referencePrevious[index] & 0xFF) / 255.0F >= COVERED;
                if (coveredNow != coveredBefore) {
                    disocclusion++;
                    disocclusionSum += difference;
                }
            }
        }
        return " ghostMean=" + fmt(sum / GHOST_PIXELS)
                + " ghostMax=" + fmt(max)
                + " ghostBias=" + fmt(bias / GHOST_PIXELS)
                + " disoccFraction=" + fmt((double) disocclusion / GHOST_PIXELS)
                + " disoccGhost=" + (disocclusion == 0L
                        ? "none" : fmt(disocclusionSum / disocclusion));
    }

    private static double sq(double value) {
        return value * value;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }
}
