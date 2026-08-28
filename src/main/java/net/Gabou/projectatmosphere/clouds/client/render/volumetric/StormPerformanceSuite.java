package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostic-only controller for the controlled T121--T123 evidence capture.
 * The suite freezes one published descriptor fixture at begin, then takes two
 * compact passes through its four fixed poses. It never changes a production
 * density, lighting, morphology, governor, or quality equation.
 */
final class StormPerformanceSuite {
    /**
     * One pass. The separated cross-pass comparison is invalidated as criterion-5
     * evidence, and repeated sampling needs the fixture identity to hold for the
     * whole run: a second pass doubles the capture count and pushed the suite
     * past the storm's structural lifetime.
     */
    private static final int PASSES = 1;
    private static final int POSE_CONFIRM_FRAMES = 2;
    private static final int GOVERNOR_CONFIRM_FRAMES = 2;
    private static final int FINAL_SETTLE_FRAMES = 8;
    /**
     * The repeated-median protocol intentionally retains its five samples per
     * arm and all control gates.  This is only the bounded wall-clock budget
     * expressed in rendered frames: at the verified 100 ms side-view GPU cost,
     * 600 frames cannot physically hold the required 20 captures.
     */
    private static final int MAX_FRAMES_PER_VIEW = 3_600;
    private static final int MAX_GOVERNOR_WAIT_FRAMES = 600;
    private static final float TARGET_GOVERNOR_SCALE = 0.50000F;
    private static final float TARGET_RESOLUTION_SCALE = 0.75000F;
    private static final float GOVERNOR_TOLERANCE = 0.00001F;
    private static final float FACING_TOLERANCE_DEGREES = 1.0F;
    /**
     * Samples per logical arm. Odd so the median is an actual observed frame,
     * and five is enough for a single outlying frame to be outvoted while
     * keeping a suite's runtime workable.
     */
    private static final int SAMPLES_PER_ARM = 5;
    /** Pair retakes allowed when a background regeneration lands mid-pair. */
    private static final int MAX_ADJACENT_ATTEMPTS = 6;
    private static final String[] VIEW_ORDER = {
            "side", "far", "below", "above",
            // SC-018's three reference viewing distances, carried into T133.
            "distance600", "distance900", "distance1200"};

    /** True for the SC-018 distance views, which measure coherence and cost. */
    private static boolean isSc018View(String view) {
        return view != null && view.startsWith("distance");
    }

    private static volatile Session active;
    private static volatile String latest = "not_started";

    private StormPerformanceSuite() {
    }

    static synchronized String begin(double requestedX, double requestedY, double requestedZ) {
        if (active != null) {
            return "busy:pass=" + active.passName() + " state=" + active.state.name().toLowerCase(Locale.ROOT)
                    + " view=" + active.view();
        }
        // T119's compact range is the accepted production path. This selects
        // diagnostic topology only; it does not alter a shader equation.
        VolumetricCloudDebugConfig.setStormTopologyMode(StormTopologyMode.COMPACT);
        VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(
                StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
        String baseline = StormPerformanceBaseline.begin(requestedX, requestedY, requestedZ);
        if (!baseline.startsWith("T130 frozen baseline fixture")) {
            latest = "suite_start_failed:" + baseline;
            return latest;
        }
        StormPerformanceBaseline.SuiteFixture fixture = StormPerformanceBaseline.suiteFixture();
        if (fixture == null) {
            latest = "suite_start_failed:fixture_missing";
            return latest;
        }
        // A new suite always latches its own clock value.
        StormReferenceImageCapture.endSuitePinning();
        active = new Session(fixture);
        latest = "acquiring pass=A state=move view=side group=" + fixture.groupId()
                + " structuralFingerprint=" + fixture.structuralFingerprint()
                + " topology=compact frozenDescriptorStructure=" + fixture.descriptorStructure();
        return latest;
    }

    static String latest() {
        return latest;
    }

    static boolean active() {
        return active != null;
    }

    static synchronized void observe(RenderTarget ignoredTarget) {
        Session session = active;
        if (session == null) {
            return;
        }
        String fixtureValidation = StormPerformanceBaseline.suiteFixtureValidation();
        if (!fixtureValidation.startsWith("fixture_valid")) {
            abort(session, fixtureValidation);
            return;
        }
        // The T119 optimization A/B deliberately selects the legacy topology for
        // its OFF arm; every other phase must still be compact.
        if (!"OFF".equals(session.groupName())
                && VolumetricCloudDebugConfig.stormTopologyMode() != StormTopologyMode.COMPACT) {
            abort(session, "topology_not_compact");
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            abort(session, "player_missing");
            return;
        }
        if (++session.framesInView > MAX_FRAMES_PER_VIEW) {
            abort(session, "required_render_samples_timeout pass=" + session.passName()
                    + " view=" + session.view());
            return;
        }

        switch (session.state) {
            case MOVE_TO_VIEW -> moveAndConfirmPose(session, player);
            case GOVERNOR_FOR_WORKLOAD -> waitForControlsThenStartWorkload(session);
            case WORKLOAD -> collectWorkloadThenWaitForGovernor(session);
            case SAMPLING -> collectSamples(session);
            case GROUP_WORKLOAD -> collectGroupWorkloadThenAdvance(session);
            case GOVERNOR_FOR_BASELINE -> waitForControlsThenSettleFinal(session);
            case SETTLE_FINAL -> settleFinalThenCaptureBaseline(session, player);
            case BASELINE -> collectBaselineThenAdvance(session);
            case COMPLETE, ABORTED -> {
                // Terminal sessions clear active; retained for exhaustiveness.
            }
        }
    }

    private static void moveAndConfirmPose(Session session, LocalPlayer player) {
        StormPerformanceBaseline.SuitePose pose = session.pose();
        if (pose == null) {
            abort(session, "fixture_pose_missing:" + session.view());
            return;
        }
        if (!session.poseApplied) {
            float expectedYaw = yawTo(pose.x(), pose.y(), pose.z(),
                    session.fixture.centerX(), session.fixture.centerY(), session.fixture.centerZ());
            float expectedPitch = pitchTo(pose.x(), pose.y(), pose.z(),
                    session.fixture.centerX(), session.fixture.centerY(), session.fixture.centerZ());
            if (player.connection == null) {
                abort(session, "player_connection_missing");
                return;
            }
            // A client-side setPos is immediately corrected by the server.
            // Use the same authoritative teleport a manual T130 run requires.
            player.connection.sendCommand(String.format(Locale.ROOT,
                    "tp @s %.5f %.5f %.5f %.3f %.3f",
                    pose.x(), pose.y(), pose.z(), expectedYaw, expectedPitch));
            VolumetricCloudRenderer.invalidateHistory();
            session.poseApplied = true;
            session.poseFrames = 0;
            latest = acquiring(session, "pose");
            return;
        }
        double positionError = distance(player.getX(), player.getY(), player.getZ(), pose.x(), pose.y(), pose.z());
        float expectedYaw = yawTo(pose.x(), pose.y(), pose.z(),
                session.fixture.centerX(), session.fixture.centerY(), session.fixture.centerZ());
        float expectedPitch = pitchTo(pose.x(), pose.y(), pose.z(),
                session.fixture.centerX(), session.fixture.centerY(), session.fixture.centerZ());
        float facingError = Math.max(angleDistance(player.getYRot(), expectedYaw),
                Math.abs(player.getXRot() - expectedPitch));
        if (positionError > StormPerformanceBaseline.cameraToleranceBlocks()
                || facingError > FACING_TOLERANCE_DEGREES) {
            session.poseFrames = 0;
            latest = acquiring(session, "pose") + " positionError=" + fmt(positionError)
                    + " facingError=" + fmt(facingError);
            return;
        }
        if (++session.poseFrames < POSE_CONFIRM_FRAMES) {
            latest = acquiring(session, "pose") + " confirmed=" + session.poseFrames + '/' + POSE_CONFIRM_FRAMES;
            return;
        }
        session.beginGovernorWait(State.GOVERNOR_FOR_WORKLOAD);
        latest = acquiring(session, "governor_before_workload");
    }

    private static void waitForControlsThenStartWorkload(Session session) {
        if (!waitForControls(session, "workload")) {
            return;
        }
        session.workloadGovernorScale = VolumetricCloudRenderer.governorStepScale();
        session.workloadResolutionScale = VolumetricCloudRenderer.lastResolutionScale();
        StormWorkloadRuntimeCapture.CaptureRequest workload =
                StormWorkloadRuntimeCapture.requestCapture(session.view());
        if (!workload.accepted()) {
            abort(session, "workload_request_failed:" + workload.status());
            return;
        }
        // Bind this view/pass to the exact capture just requested. Freshness is
        // the token, not the view name: two passes over the same view share a
        // name, so a name match alone cannot tell a new capture from an old one.
        session.expectedWorkloadToken = workload.token();
        session.state = State.WORKLOAD;
        latest = acquiring(session, "workload") + " captureToken=" + workload.token()
                + " frames=0/2 governorScale="
                + fmt(session.workloadGovernorScale) + " resolutionScale=" + fmt(session.workloadResolutionScale);
    }

    private static void collectWorkloadThenWaitForGovernor(Session session) {
        if (StormWorkloadRuntimeCapture.active()) {
            latest = acquiring(session, "workload");
            return;
        }
        StormWorkloadRuntimeCapture.WorkloadResult workload = StormWorkloadRuntimeCapture.latestResult();
        String failure = workloadFreshnessFailure(
                workload, session.expectedWorkloadToken, session.view());
        if (failure != null) {
            abort(session, failure);
            return;
        }
        session.pendingWorkload = workload;
        session.expectedWorkloadToken = StormWorkloadRuntimeCapture.NO_TOKEN;
        session.beginSampling();
        session.state = State.SAMPLING;
        requestSample(session, "warmup:" + session.groupName());
    }

    /**
     * The workload-acceptance predicate, kept pure so the deterministic sandbox
     * can prove a stale result is refused without a GL context.
     *
     * <p>Returns {@code null} when the result is the one this capture requested,
     * otherwise the abort reason.
     */
    static String workloadFreshnessFailure(
            StormWorkloadRuntimeCapture.WorkloadResult result, long expectedToken, String expectedView) {
        if (expectedToken == StormWorkloadRuntimeCapture.NO_TOKEN) {
            return "workload_capture_not_requested";
        }
        if (result == null) {
            return "workload_capture_missing expectedToken=" + expectedToken;
        }
        if (result.captureToken() != expectedToken) {
            return "workload_capture_stale expectedToken=" + expectedToken
                    + " resultToken=" + result.captureToken()
                    + " resultView=" + result.view();
        }
        if (expectedView == null || !expectedView.equalsIgnoreCase(result.view())) {
            return "workload_capture_wrong_view expected=" + expectedView
                    + " actual=" + result.view()
                    + " captureToken=" + result.captureToken();
        }
        return null;
    }

    /**
     * Repeated adjacent sampling. Every group is collected inside one settled
     * window at one pose: no teleport, no other view, no pose setup between
     * samples. Groups A1 and A2 are the local production-noise control; OFF and
     * ON are the optimization arms. Each group discards one warm-up capture
     * first, because a pose's - and a toggle's - first capture measures warm-up.
     */
    private static void collectSamples(Session session) {
        if (StormReferenceImageCapture.active()) {
            latest = acquiring(session, "sampling " + session.groupName())
                    + ' ' + StormReferenceImageCapture.latest();
            return;
        }
        StormReferenceImageComparison.Reference captured = StormReferenceImageCapture.latestResult();
        if (captured == null || !session.view().equalsIgnoreCase(captured.view())
                || !captured.historyBypassed()) {
            abort(session, "sample_capture_failed:" + session.groupName()
                    + ':' + StormReferenceImageCapture.latest());
            return;
        }
        if (session.awaitingWarmup) {
            session.awaitingWarmup = false;
        } else {
            List<StormReferenceImageComparison.Reference> group = session.currentGroup();
            // Every sample of a group must have rendered against the same
            // background content as the group's first sample.
            if (!group.isEmpty()) {
                String mismatch = pairContentMismatch(group.get(0), captured);
                if (mismatch != null) {
                    if (++session.groupAttempts > MAX_ADJACENT_ATTEMPTS) {
                        abort(session, "sample_group_content_unstable:" + session.groupName()
                                + ':' + mismatch);
                        return;
                    }
                    group.clear();
                    session.awaitingWarmup = true;
                    requestSample(session, "content_retake");
                    return;
                }
            }
            group.add(captured);
        }
        if (session.currentGroup().size() >= SAMPLES_PER_ARM) {
            // SC-020 wants each optimization's owned work measured under its own
            // arm. The view-level capture runs in NORMAL_PRODUCTION before
            // sampling, so it cannot show what an OFF arm actually cost; take a
            // capture now, while this arm's mode is still applied.
            StormWorkloadRuntimeCapture.CaptureRequest armWorkload =
                    StormWorkloadRuntimeCapture.requestCapture(session.view());
            if (!armWorkload.accepted()) {
                abort(session, "arm_workload_request_failed:" + session.groupName()
                        + ':' + armWorkload.status());
                return;
            }
            session.expectedWorkloadToken = armWorkload.token();
            session.pendingWorkloadGroup = session.groupName();
            session.state = State.GROUP_WORKLOAD;
            latest = acquiring(session, "arm_workload:" + session.groupName());
            return;
        }
        requestSample(session, session.groupName());
    }

    /**
     * Stores the owned-work counters for the arm that just finished, then
     * advances. The arm's diagnostic mode is still applied here, so the numbers
     * describe that arm rather than the production default.
     */
    private static void collectGroupWorkloadThenAdvance(Session session) {
        if (StormWorkloadRuntimeCapture.active()) {
            latest = acquiring(session, "arm_workload:" + session.pendingWorkloadGroup);
            return;
        }
        StormWorkloadRuntimeCapture.WorkloadResult workload =
                StormWorkloadRuntimeCapture.latestResult();
        String failure = workloadFreshnessFailure(
                workload, session.expectedWorkloadToken, session.view());
        if (failure != null) {
            abort(session, "arm_" + failure);
            return;
        }
        session.groupWorkload.put(session.pendingWorkloadGroup, workload);
        session.expectedWorkloadToken = StormWorkloadRuntimeCapture.NO_TOKEN;
        session.pendingWorkloadGroup = null;
        session.state = State.SAMPLING;
        {
            if (!session.advanceGroup()) {
                VolumetricCloudDebugConfig.setStormTopologyMode(StormTopologyMode.COMPACT);
                VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(
                        StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
                session.beginGovernorWait(State.GOVERNOR_FOR_BASELINE);
                latest = acquiring(session, "governor_before_baseline");
                return;
            }
        }
        requestSample(session, session.groupName());
    }

    private static void requestSample(Session session, String label) {
        String requested = StormReferenceImageCapture.request(
                session.view(), java.util.UUID.fromString(session.fixture.groupId()));
        if (!requested.startsWith("acquiring")) {
            VolumetricCloudDebugConfig.setStormTopologyMode(StormTopologyMode.COMPACT);
            VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(
                    StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
            abort(session, "sample_request_failed:" + label + ':' + requested);
            return;
        }
        latest = acquiring(session, "sampling " + label)
                + " sample=" + session.currentGroup().size() + '/' + SAMPLES_PER_ARM
                + ' ' + requested;
    }

    /**
     * Null when both captures rendered against identical background content.
     */
    private static String pairContentMismatch(
            StormReferenceImageComparison.Reference a, StormReferenceImageComparison.Reference b) {
        if (a.renderInputs() != null && b.renderInputs() != null) {
            if (a.renderInputs().weatherMapInputSignature()
                    != b.renderInputs().weatherMapInputSignature()) {
                return "weatherMapInputSignature";
            }
            // A drifting camera would otherwise pass unnoticed: the pose is only
            // re-verified at the baseline capture, long after sampling.
            if (a.renderInputs().components() != null && b.renderInputs().components() != null
                    && (a.renderInputs().components().cameraPosition()
                            != b.renderInputs().components().cameraPosition()
                    || a.renderInputs().components().projection()
                            != b.renderInputs().components().projection())) {
                return "cameraPose";
            }
            // A matching weather map and camera pose are not sufficient for a
            // repeated-reference arm: live lighting, ambient state, or any
            // other named production uniform can still move the image.  This
            // is a diagnostic admission gate only; it neither changes the
            // uploaded uniforms nor freezes normal rendering behaviour.
            StormSceneStability.RenderInputComparison renderInputs =
                    StormSceneStability.compareRenderInputs(a.renderInputs(), b.renderInputs());
            if (renderInputs.evaluated() && !renderInputs.renderInputsMatch()) {
                return renderInputs.changedUniformComponents().isEmpty()
                        ? "comparisonUniformSignature"
                        : "renderInputs:" + String.join(",", renderInputs.changedUniformComponents());
            }
        }
        StormCloudContent.Comparison content =
                StormCloudContent.compare(a.cloudContent(), b.cloudContent());
        if (content.evaluated() && !content.cloudContentMatch()) {
            return String.join(",", content.differingCategories());
        }
        return null;
    }

    private static void waitForControlsThenSettleFinal(Session session) {
        if (!waitForControls(session, "baseline")) {
            return;
        }
        session.finalFrames = 0;
        session.state = State.SETTLE_FINAL;
        latest = acquiring(session, "final_settle") + " frames=0/" + FINAL_SETTLE_FRAMES;
    }

    private static boolean waitForControls(Session session, String phase) {
        float governorScale = VolumetricCloudRenderer.governorStepScale();
        float resolutionScale = VolumetricCloudRenderer.lastResolutionScale();
        if (Math.abs(governorScale - TARGET_GOVERNOR_SCALE) > GOVERNOR_TOLERANCE
                || Math.abs(resolutionScale - TARGET_RESOLUTION_SCALE) > GOVERNOR_TOLERANCE) {
            session.governorConfirmFrames = 0;
            if (++session.governorWaitFrames > MAX_GOVERNOR_WAIT_FRAMES) {
                abort(session, "render_controls_timeout phase=" + phase
                        + " expectedGovernor=" + fmt(TARGET_GOVERNOR_SCALE)
                        + " actualGovernor=" + fmt(governorScale)
                        + " expectedResolution=" + fmt(TARGET_RESOLUTION_SCALE)
                        + " actualResolution=" + fmt(resolutionScale)
                        + " waitedFrames=" + session.governorWaitFrames);
            } else {
                latest = acquiring(session, "controls_" + phase)
                        + " expectedGovernor=" + fmt(TARGET_GOVERNOR_SCALE)
                        + " actualGovernor=" + fmt(governorScale)
                        + " expectedResolution=" + fmt(TARGET_RESOLUTION_SCALE)
                        + " actualResolution=" + fmt(resolutionScale)
                        + " waitedFrames=" + session.governorWaitFrames
                        + '/' + MAX_GOVERNOR_WAIT_FRAMES;
            }
            return false;
        }
        if (++session.governorConfirmFrames < GOVERNOR_CONFIRM_FRAMES) {
            latest = acquiring(session, "controls_" + phase) + " confirmed="
                    + session.governorConfirmFrames + '/' + GOVERNOR_CONFIRM_FRAMES;
            return false;
        }
        return true;
    }

    private static void settleFinalThenCaptureBaseline(Session session, LocalPlayer player) {
        if (Math.abs(VolumetricCloudRenderer.governorStepScale() - TARGET_GOVERNOR_SCALE) > GOVERNOR_TOLERANCE
                || Math.abs(VolumetricCloudRenderer.lastResolutionScale() - TARGET_RESOLUTION_SCALE) > GOVERNOR_TOLERANCE) {
            // Workload already ran at the target controls. Re-establish both
            // controls before GPU timing rather than silently timing another level.
            session.beginGovernorWait(State.GOVERNOR_FOR_BASELINE);
            latest = acquiring(session, "governor_before_baseline");
            return;
        }
        VolumetricCloudRenderer.LastDrawInputs inputs = VolumetricCloudRenderer.lastDrawInputs();
        if (!inputs.valid() || inputs.debugView() != VolumetricCloudRaymarchDebugView.FINAL) {
            latest = acquiring(session, "final_settle") + " waiting_for_final_draw";
            return;
        }
        if (!inputs.historyValid()) {
            session.finalFrames = 0;
            latest = acquiring(session, "final_settle") + " waiting_for_history";
            return;
        }
        if (++session.finalFrames < FINAL_SETTLE_FRAMES) {
            latest = acquiring(session, "final_settle") + " frames=" + session.finalFrames + '/' + FINAL_SETTLE_FRAMES;
            return;
        }
        String baseline = StormPerformanceBaseline.capture(session.view(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        if (!baseline.startsWith("acquiring")) {
            abort(session, "baseline_request_failed:" + baseline);
            return;
        }
        session.state = State.BASELINE;
        latest = acquiring(session, "baseline") + " gpuSamples=0/8 governorScale=" + fmt(TARGET_GOVERNOR_SCALE);
    }

    private static void collectBaselineThenAdvance(Session session) {
        if (StormPerformanceBaseline.suiteCaptureActive()) {
            latest = acquiring(session, "baseline");
            return;
        }
        if (!StormPerformanceBaseline.suiteCaptureComplete(session.view())) {
            abort(session, "baseline_capture_missing:" + StormPerformanceBaseline.latest());
            return;
        }
        StormPerformanceBaseline.CaptureResult baseline = StormPerformanceBaseline.suiteCapture(session.view());
        if (baseline == null || session.pendingWorkload == null) {
            abort(session, "baseline_or_workload_result_missing");
            return;
        }
        session.currentPass().add(new ViewCapture(session.pendingWorkload, baseline,
                List.copyOf(session.groupA1), List.copyOf(session.groupA2),
                List.copyOf(session.groupOff), List.copyOf(session.groupOn),
                List.copyOf(session.groupT121Off), List.copyOf(session.groupT121On),
                List.copyOf(session.groupT122Off), List.copyOf(session.groupT122On),
                java.util.Map.copyOf(session.groupWorkload),
                session.workloadGovernorScale, session.workloadResolutionScale));
        session.pendingWorkload = null;
        session.clearSampleGroups();
        session.viewIndex++;
        if (session.viewIndex < VIEW_ORDER.length) {
            session.resetForNextView();
            latest = acquiring(session, "move");
            return;
        }
        if (session.passIndex + 1 < PASSES) {
            session.passIndex++;
            session.viewIndex = 0;
            session.resetForNextView();
            latest = acquiring(session, "move") + " repeatedFixture=true";
            return;
        }
        String controlMismatch = session.controlMismatch();
        if (controlMismatch != null) {
            abort(session, "controlled_evidence_invalid:" + controlMismatch + "\n" + session.formatComplete());
            return;
        }
        session.state = State.COMPLETE;
        latest = session.formatComplete();
        active = null;
        // The latched suite clock must not survive into a later suite.
        StormReferenceImageCapture.endSuitePinning();
        publishTerminal("complete", latest);
    }

    private static void abort(Session session, String reason) {
        // Restores historyEnabled if a deterministic reference capture is still
        // in flight; a no-op otherwise. Production temporal behaviour is never
        // left altered by an aborted suite.
        StormReferenceImageCapture.cancel();
        StormReferenceImageCapture.endSuitePinning();
        // The optimization A/B may have left the diagnostic topology selected.
        VolumetricCloudDebugConfig.setStormTopologyMode(StormTopologyMode.COMPACT);
        VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(
                StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
        session.state = State.ABORTED;
        latest = "stormPerformanceSuite aborted pass=" + session.passName() + " view=" + session.diagnosticView()
                + " reason=" + reason;
        active = null;
        publishTerminal("aborted", latest);
    }

    private static void publishTerminal(String outcome, String report) {
        ProjectAtmosphere.LOGGER.info("[StormPerformanceSuite] {}\n{}", outcome, report);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal(
                    "Storm performance suite " + outcome + "; full report written to latest.log."), false);
        }
    }

    /** Deterministic no-GL state-machine guards for the two-pass capture. */
    static void selfCheckStateMachine() {
        List<String> order = new ArrayList<>();
        State state = State.MOVE_TO_VIEW;
        for (int pass = 0; pass < PASSES; pass++) {
            for (String view : VIEW_ORDER) {
                requireState(state, State.MOVE_TO_VIEW, "pose transition", pass, view);
                state = State.GOVERNOR_FOR_WORKLOAD;
                for (int frame = 0; frame < GOVERNOR_CONFIRM_FRAMES; frame++) {
                    requireState(state, State.GOVERNOR_FOR_WORKLOAD, "workload governor wait", pass, view);
                }
                state = State.WORKLOAD;
                order.add(pass + ":" + view + ":workload");
                for (int frame = 0; frame < 2; frame++) {
                    requireState(state, State.WORKLOAD, "workload frame", pass, view);
                }
                state = State.SAMPLING;
                order.add(pass + ":" + view + ":sampling");
                requireState(state, State.SAMPLING, "repeated sampling", pass, view);
                if (state == State.MOVE_TO_VIEW) {
                    throw new IllegalStateException("sampling re-entered pose setup");
                }
                state = State.GOVERNOR_FOR_BASELINE;
                for (int frame = 0; frame < GOVERNOR_CONFIRM_FRAMES; frame++) {
                    requireState(state, State.GOVERNOR_FOR_BASELINE, "baseline governor wait", pass, view);
                }
                state = State.SETTLE_FINAL;
                for (int frame = 0; frame < FINAL_SETTLE_FRAMES; frame++) {
                    requireState(state, State.SETTLE_FINAL, "FINAL settlement", pass, view);
                }
                state = State.BASELINE;
                boolean baselineComplete = false;
                if (baselineComplete) {
                    throw new IllegalStateException("suite accepted an uncollected baseline");
                }
                baselineComplete = true;
                if (!baselineComplete) {
                    throw new IllegalStateException("suite baseline ordering failed");
                }
                order.add(pass + ":" + view + ":baseline");
                state = pass == PASSES - 1 && view.equals(VIEW_ORDER[VIEW_ORDER.length - 1])
                        ? State.COMPLETE : State.MOVE_TO_VIEW;
            }
        }
        // Derived from VIEW_ORDER so adding SC-018 distance views cannot
        // silently desynchronise this invariant from the real traversal.
        List<String> expected = new ArrayList<>();
        for (int pass = 0; pass < PASSES; pass++) {
            for (String view : VIEW_ORDER) {
                expected.add(pass + ":" + view + ":workload");
                expected.add(pass + ":" + view + ":sampling");
                expected.add(pass + ":" + view + ":baseline");
            }
        }
        if (!order.equals(expected) || state != State.COMPLETE) {
            throw new IllegalStateException("suite ordering/completion invariant failed: " + order);
        }
        if (!"complete".equals(diagnosticViewFor(VIEW_ORDER.length))
                || !"invalid(-1)".equals(diagnosticViewFor(-1))) {
            throw new IllegalStateException("suite terminal diagnostic view invariant failed");
        }
        if (TARGET_GOVERNOR_SCALE != 0.5F || TARGET_RESOLUTION_SCALE != 0.75F || GOVERNOR_CONFIRM_FRAMES < 1
                || MAX_GOVERNOR_WAIT_FRAMES < GOVERNOR_CONFIRM_FRAMES
                || FINAL_SETTLE_FRAMES < 1 || POSE_CONFIRM_FRAMES < 1
                || MAX_FRAMES_PER_VIEW <= FINAL_SETTLE_FRAMES) {
            throw new IllegalStateException("suite control-wait invariant failed");
        }
        State invalidated = State.GOVERNOR_FOR_WORKLOAD;
        invalidated = State.ABORTED;
        requireState(invalidated, State.ABORTED, "structural invalidation", 0, "side");
        // Each view's adjacent pair belongs to that view only: resetForNextView
        // clears both references, so no previous view can satisfy the next.
        Session probe = new Session(null);
        probe.groupA1.add(null);
        probe.groupOff.add(null);
        probe.resetForNextView();
        if (!probe.groupA1.isEmpty() || !probe.groupOff.isEmpty()
                || probe.state != State.MOVE_TO_VIEW) {
            throw new IllegalStateException("suite retained sample groups across views");
        }
        if (SAMPLES_PER_ARM % 2 == 0 || SAMPLES_PER_ARM < 3) {
            throw new IllegalStateException("sample count must be odd and at least three");
        }
        if (!ViewCapture.sameControlValue(0.0D, -0.0D)
                || !ViewCapture.sameControlValue(0.0F, -0.0F)
                || ViewCapture.sameControlValue(0.0D, Math.nextUp(0.0D))
                || ViewCapture.sameControlValue(0.0F, Math.nextUp(0.0F))) {
            throw new IllegalStateException("suite signed-zero control comparison invariant failed");
        }
    }

    private static void requireState(State actual, State expected, String phase, int pass, String view) {
        if (actual != expected) {
            throw new IllegalStateException("suite skipped " + phase + " pass=" + pass + " view=" + view);
        }
    }

    private static float yawTo(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        return (float) (Math.toDegrees(Math.atan2(toZ - fromZ, toX - fromX)) - 90.0D);
    }

    private static float pitchTo(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        return (float) -Math.toDegrees(Math.atan2(toY - fromY,
                Math.hypot(toX - fromX, toZ - fromZ)));
    }

    private static float angleDistance(float first, float second) {
        float delta = (first - second) % 360.0F;
        return Math.abs(delta > 180.0F ? delta - 360.0F : delta < -180.0F ? delta + 360.0F : delta);
    }

    private static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        return Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz));
    }

    private static String acquiring(Session session, String state) {
        return "acquiring pass=" + session.passName() + " state=" + state + " view=" + session.view()
                + " group=" + session.fixture.groupId() + " structuralFingerprint="
                + session.fixture.structuralFingerprint() + " topology=compact";
    }

    /**
     * Robust comparison of two sample groups. The medians are compared with the
     * unchanged one-storage-ULP comparator; the raw within-arm dispersion of
     * each group is reported beside it so the result cannot hide behind the
     * median.
     */
    private static String groupReport(
            String label, String armA, String armB,
            List<StormReferenceImageComparison.Reference> groupA,
            List<StormReferenceImageComparison.Reference> groupB) {
        return groupReport(label, armA, armB, groupA, groupB, java.util.Map.of());
    }

    /**
     * SC-020 owned-work evidence. Each arm's counters are captured under that
     * arm's own diagnostic mode, so an OFF arm shows the work the optimization
     * actually removes rather than the production default's numbers.
     */
    private static String armWorkloadReport(
            String arm,
            java.util.Map<String, StormWorkloadRuntimeCapture.WorkloadResult> workloads) {
        StormWorkloadRuntimeCapture.WorkloadResult result = workloads.get(arm);
        if (result == null) {
            return " " + arm + "Work=absent";
        }
        return " " + arm + "Work={"
                + "conservativeDescriptorRejects=" + fmtCount(result.conservativeDescriptorRejects())
                + " descriptorEvaluations=" + fmtCount(result.descriptorEvaluations())
                + " descriptorTextureFetches=" + fmtCount(result.descriptorTextureFetches())
                + " avoidedDescriptorTextureFetches="
                + fmtCount(result.avoidedDescriptorTextureFetches())
                + " primaryRaySteps=" + fmtCount(result.primaryRaySteps())
                + " emptySpaceRejects=" + fmtCount(result.emptySpaceRejects())
                + " lightMarchDensityEvaluations="
                + fmtCount(result.lightMarchDensityEvaluations())
                + " earlyTerminations=" + fmtCount(result.earlyTerminations())
                + "}";
    }

    private static String groupReport(
            String label,
            String armA,
            String armB,
            List<StormReferenceImageComparison.Reference> groupA,
            List<StormReferenceImageComparison.Reference> groupB,
            java.util.Map<String, StormWorkloadRuntimeCapture.WorkloadResult> workloads) {
        if (groupA.size() < SAMPLES_PER_ARM || groupB.size() < SAMPLES_PER_ARM) {
            return label + "={evaluated=false reason=incomplete_sample_groups"
                    + " samplesA=" + groupA.size() + " samplesB=" + groupB.size() + '}';
        }
        float[] medianA = StormReferenceSampleSet.median(groupA);
        float[] medianB = StormReferenceSampleSet.median(groupB);
        StormReferenceImageComparison.Comparison median =
                StormReferenceImageComparison.compare(
                        StormReferenceSampleSet.asReference(groupA.get(0), medianA),
                        StormReferenceSampleSet.asReference(groupB.get(0), medianB));
        StormReferenceSampleSet.ArmNoise noiseA = StormReferenceSampleSet.noise(groupA, medianA);
        StormReferenceSampleSet.ArmNoise noiseB = StormReferenceSampleSet.noise(groupB, medianB);
        StormCloudContent.Comparison content = StormCloudContent.compare(
                groupA.get(0).cloudContent(), groupB.get(0).cloudContent());
        boolean clockMatch = Float.floatToIntBits(groupA.get(0).effectiveWorldTime())
                == Float.floatToIntBits(groupB.get(0).effectiveWorldTime());
        return label + "={evaluated=true armA=" + armA + " armB=" + armB
                + " armATopology=" + observedTopology(groupA)
                + " armBTopology=" + observedTopology(groupB)
                + " armAOptimization=" + observedOptimization(groupA)
                + " armBOptimization=" + observedOptimization(groupB)
                + " armsDistinct=" + (!observedTopology(groupA).equals(observedTopology(groupB))
                        || !observedOptimization(groupA).equals(observedOptimization(groupB)))
                + armWorkloadReport(armA, workloads)
                + armWorkloadReport(armB, workloads)
                + " samplesPerArm=" + SAMPLES_PER_ARM
                + " medianDigestA=" + StormReferenceImageComparison.digest(
                        medianA, groupA.get(0).width(), groupA.get(0).height())
                + " medianDigestB=" + StormReferenceImageComparison.digest(
                        medianB, groupB.get(0).width(), groupB.get(0).height())
                + " effectiveWorldTimeMatch=" + clockMatch
                + " cloudContentMatch=" + content.cloudContentMatch()
                + " topologyRestored=" + (VolumetricCloudDebugConfig.stormTopologyMode()
                        == StormTopologyMode.COMPACT)
                + " medianComparison={" + median.format() + '}'
                + ' ' + noiseA.format("armNoise" + armA)
                + ' ' + noiseB.format("armNoise" + armB) + '}';
    }

    /**
     * The topology every sample of a group actually rendered with, taken from
     * the draw snapshot rather than from the value the suite intended to set.
     * "mixed" means the group is not usable as an arm.
     */
    private static String observedTopology(List<StormReferenceImageComparison.Reference> group) {
        StormTopologyMode seen = null;
        for (StormReferenceImageComparison.Reference sample : group) {
            if (sample.renderInputs() == null) {
                return "unknown";
            }
            StormTopologyMode mode = sample.renderInputs().stormTopologyMode();
            if (seen == null) {
                seen = mode;
            } else if (seen != mode) {
                return "mixed";
            }
        }
        return seen == null ? "unknown" : seen.serializedName();
    }

    /**
     * The sole input to the T132 criterion 3 verdict. Aggregates the adjacent
     * controls of both captures of this view; the retired separated-pass
     * comparison is deliberately not consulted.
     */
    private static String authoritativeAdjacentControls(
            ViewCapture reference,
            ViewCapture validation,
            StormPerformanceBaseline.SuiteFixture fixture) {
        List<String> differences = new ArrayList<>(reference.adjacentControlDifferences(fixture));
        if (validation != null && validation != reference) {
            for (String difference : validation.adjacentControlDifferences(fixture)) {
                if (!differences.contains(difference)) {
                    differences.add(difference);
                }
            }
        }
        boolean matched = differences.isEmpty();
        return "protocol=adjacent_repeated_sampling authoritative=true"
                + " fixture=" + fixture.groupId()
                + " structuralFingerprint=" + fixture.structuralFingerprint()
                + " structuralChanged=" + reference.baseline.structuralChanged()
                + " governorScale=" + fmt(reference.workloadGovernorScale)
                + " resolutionScale=" + fmt(reference.workloadResolutionScale)
                + " productionTopology="
                + reference.baseline.inputs().stormTopologyMode().serializedName()
                + " rayStepsConfigured=" + reference.baseline.inputs().raymarchSteps()
                + " lightStepsConfigured=" + reference.baseline.inputs().lightSteps()
                + " target=" + reference.baseline.targetWidth() + "x" + reference.baseline.targetHeight()
                + " workload=" + reference.workload.width() + "x" + reference.workload.height()
                + " samplesPerArm=" + SAMPLES_PER_ARM
                + " controlsMatched=" + matched
                + (matched ? "" : " controlDifferences=" + String.join(",", differences));
    }

    /**
     * SC-018 evidence for one reference viewing distance. The criterion asks
     * that the documented severe-storm targets still hold when the system is
     * observed from each distance, so this records identity, ownership,
     * coherence, that the system was actually marched at this range, and the
     * cost of doing so. It is objective evidence only; T098 owns the
     * subjective visual acceptance.
     */
    private static String sc018Evidence(
            String view,
            ViewCapture capture,
            StormPerformanceBaseline.SuiteFixture fixture) {
        StormCloudContent content = capture.groupA1.isEmpty()
                ? null : capture.groupA1.get(0).cloudContent();
        // One coherent severe system: exactly the fixture's own group, with no
        // second storm group and no foreign descriptors in frame.
        boolean coherent = content != null
                && content.stormGroupCount() == 1
                && content.stormDescriptorCount() == fixture.descriptorCount();
        // Marched, not merely published: primary steps and descriptor work at
        // this range prove the system did not vanish with distance.
        boolean marched = capture.workload.primaryRaySteps() > 0.0D
                && capture.workload.descriptorEvaluations() > 0.0D;
        double nominal = switch (view) {
            case "distance600" -> 600.0D;
            case "distance900" -> 900.0D;
            case "distance1200" -> 1200.0D;
            default -> 0.0D;
        };
        return "sc018={view=" + view
                + " nominalDistanceBlocks=" + fmt(nominal)
                + " measuredStormDistance=" + fmt(capture.baseline.stormDistance())
                + " fixture=" + fixture.groupId()
                + " structuralFingerprint=" + fixture.structuralFingerprint()
                + " structuralChanged=" + capture.baseline.structuralChanged()
                + " descriptors=" + (content == null ? "missing" : content.stormDescriptorCount())
                + " expectedDescriptors=" + fixture.descriptorCount()
                + " stormGroupCount=" + (content == null ? "missing" : content.stormGroupCount())
                + " stormLobeCount=" + (content == null ? "missing" : content.stormLobeCount())
                + " oneCoherentSevereSystem=" + coherent
                + " marchedAtRange=" + marched
                + " primaryRaySteps=" + fmtCount(capture.workload.primaryRaySteps())
                + " descriptorEvaluations=" + fmtCount(capture.workload.descriptorEvaluations())
                + " gpuMinMs=" + fmt(capture.baseline.gpuMin())
                + " gpuMedianMs=" + fmt(capture.baseline.gpuMedian())
                + " gpuMeanMs=" + fmt(capture.baseline.gpuMean())
                + " gpuMaxMs=" + fmt(capture.baseline.gpuMax())
                + " gpuSamples=" + capture.baseline.gpuSamples()
                + " resolutionScale=" + fmt(capture.workloadResolutionScale)
                + " governorScale=" + fmt(capture.workloadGovernorScale)
                + " target=" + capture.baseline.targetWidth() + "x" + capture.baseline.targetHeight()
                + " sc018Passed=" + (coherent && marched && !capture.baseline.structuralChanged())
                + "}";
    }

    /**
     * The optimization mode every sample of a group actually rendered with,
     * taken from the draw snapshot rather than from the value the suite
     * intended to set. "mixed" means the group is not usable as an arm.
     */
    private static String observedOptimization(List<StormReferenceImageComparison.Reference> group) {
        StormOptimizationDiagnosticMode seen = null;
        for (StormReferenceImageComparison.Reference sample : group) {
            if (sample.renderInputs() == null) {
                return "unknown";
            }
            StormOptimizationDiagnosticMode mode =
                    sample.renderInputs().optimizationDiagnosticMode();
            if (seen == null) {
                seen = mode;
            } else if (seen != mode) {
                return "mixed";
            }
        }
        return seen == null ? "unknown" : seen.serializedName();
    }

    private static String diagnosticViewFor(int viewIndex) {
        if (viewIndex >= 0 && viewIndex < VIEW_ORDER.length) {
            return VIEW_ORDER[viewIndex];
        }
        return viewIndex == VIEW_ORDER.length ? "complete" : "invalid(" + viewIndex + ')';
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    private static String fmtCount(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private enum State {
        MOVE_TO_VIEW, GOVERNOR_FOR_WORKLOAD, WORKLOAD, SAMPLING, GROUP_WORKLOAD,
        GOVERNOR_FOR_BASELINE, SETTLE_FINAL, BASELINE, COMPLETE, ABORTED
    }

    private record ViewCapture(
            StormWorkloadRuntimeCapture.WorkloadResult workload,
            StormPerformanceBaseline.CaptureResult baseline,
            List<StormReferenceImageComparison.Reference> groupA1,
            List<StormReferenceImageComparison.Reference> groupA2,
            List<StormReferenceImageComparison.Reference> groupOff,
            List<StormReferenceImageComparison.Reference> groupOn,
            List<StormReferenceImageComparison.Reference> groupT121Off,
            List<StormReferenceImageComparison.Reference> groupT121On,
            List<StormReferenceImageComparison.Reference> groupT122Off,
            List<StormReferenceImageComparison.Reference> groupT122On,
            java.util.Map<String, StormWorkloadRuntimeCapture.WorkloadResult> groupWorkload,
            float workloadGovernorScale,
            float workloadResolutionScale
    ) {
        private String format() {
            return "workload={" + workload.format() + " workloadGovernorScale=" + fmt(workloadGovernorScale)
                    + " workloadResolutionScale=" + fmt(workloadResolutionScale)
                    + "} baseline={" + baseline.format() + '}'
                    + " sampleGroups={A1=" + groupA1.size() + " A2=" + groupA2.size()
                    + " OFF=" + groupOff.size() + " ON=" + groupOn.size() + '}'
                    + " animated={materialOffsetX=" + fmt(baseline.inputs().materialOffsetX())
                    + " materialOffsetZ=" + fmt(baseline.inputs().materialOffsetZ())
                    + " baselineWorldTimeTicks=" + fmt(baseline.inputs().worldTimeTicks())
                    + " worldTimeAffectsDensity=" + baseline.inputs().worldTimeAffectsDensity()
                    + " lightDir=(" + fmt(baseline.inputs().lightDirX())
                    + ',' + fmt(baseline.inputs().lightDirY())
                    + ',' + fmt(baseline.inputs().lightDirZ()) + ')'
                    + " runtimeProfileDigest="
                    + (baseline.runtimeProfile() == null ? "missing" : baseline.runtimeProfile().digest())
                    + '}';
        }

        /** The evolving, non-structural inputs this capture actually rendered with. */
        private StormSceneStability.AnimatedInputs animatedInputs() {
            // The clock comes from the reference frame that was actually
            // compared, not from the later baseline draw which runs unpinned.
            return new StormSceneStability.AnimatedInputs(
                    baseline.inputs().materialOffsetX(),
                    baseline.inputs().materialOffsetZ(),
                    groupA1.isEmpty()
                            ? baseline.inputs().worldTimeTicks()
                            : groupA1.get(0).effectiveWorldTime(),
                    groupA1.isEmpty()
                            ? baseline.inputs().liveWorldTimeTicks()
                            : groupA1.get(0).liveWorldTime(),
                    !groupA1.isEmpty() && groupA1.get(0).worldTimePinned(),
                    baseline.inputs().worldTimeAffectsDensity(),
                    baseline.inputs().lightDirX(),
                    baseline.inputs().lightDirY(),
                    baseline.inputs().lightDirZ(),
                    baseline.runtimeProfile());
        }

        /**
         * The authoritative T132 controls: those of the adjacent repeated-sampling
         * protocol. Every arm of every comparison in this view is captured
         * back-to-back under one pose, one governor state and one workload, so
         * these are evaluated within the capture rather than across two
         * temporally separated passes. The retired separated-pass comparison
         * contributes nothing here - see criterion 3 in tasks.md.
         */
        private List<String> adjacentControlDifferences(StormPerformanceBaseline.SuiteFixture fixture) {
            List<String> differences = new ArrayList<>();
            if (!fixture.structuralFingerprint().equals(baseline.fingerprintAtCapture())
                    || !fixture.structuralFingerprint().equals(baseline.fingerprintAtComplete())) {
                differences.add("frozen_fixture_fingerprint_mismatch");
            }
            if (baseline.structuralChanged()) {
                differences.add("structuralChanged");
            }
            if (Math.abs(workloadGovernorScale - TARGET_GOVERNOR_SCALE) > GOVERNOR_TOLERANCE
                    || Math.abs(baseline.inputs().stepScale() - TARGET_GOVERNOR_SCALE) > GOVERNOR_TOLERANCE) {
                differences.add("governorScale_not_0.50000");
            }
            if (Math.abs(workloadResolutionScale - TARGET_RESOLUTION_SCALE) > GOVERNOR_TOLERANCE
                    || Math.abs(baseline.resolutionScale() - TARGET_RESOLUTION_SCALE) > GOVERNOR_TOLERANCE) {
                differences.add("resolutionScale_not_0.75000");
            }
            // Compact is the required production arm; the legacy arm exists only
            // inside an A/B group, which restores compact before the view ends.
            if (baseline.inputs().stormTopologyMode() != StormTopologyMode.COMPACT) {
                differences.add("production_topology_not_compact");
            }
            if (baseline.inputs().optimizationDiagnosticMode()
                    != StormOptimizationDiagnosticMode.NORMAL_PRODUCTION) {
                differences.add("production_optimization_mode_not_normal");
            }
            if (workload.width() != baseline.targetWidth()
                    || workload.height() != baseline.targetHeight()) {
                differences.add("workload_target_does_not_match_baseline_target");
            }
            // A capture must be its own readback, never a reused one.
            if (workload.captureToken() == StormWorkloadRuntimeCapture.NO_TOKEN) {
                differences.add("workload_capture_token_missing");
            }
            if (!baseline.inputs().historyValid()) {
                differences.add("history_not_valid");
            }
            groupControlDifferences(differences, "A1", groupA1);
            groupControlDifferences(differences, "A2", groupA2);
            groupControlDifferences(differences, "OFF", groupOff);
            groupControlDifferences(differences, "ON", groupOn);
            groupControlDifferences(differences, "T121OFF", groupT121Off);
            groupControlDifferences(differences, "T121ON", groupT121On);
            groupControlDifferences(differences, "T122OFF", groupT122Off);
            groupControlDifferences(differences, "T122ON", groupT122On);
            return differences;
        }

        /**
         * Per-arm controls: every sample must have settled its projection, been
         * captured with the clock pinned and history bypassed, and carry a single
         * observed topology.
         */
        private void groupControlDifferences(
                List<String> differences,
                String name,
                List<StormReferenceImageComparison.Reference> group) {
            if (group.isEmpty()) {
                return;
            }
            if (group.size() != SAMPLES_PER_ARM) {
                differences.add(name + "_sample_count=" + group.size());
            }
            for (StormReferenceImageComparison.Reference sample : group) {
                if (sample.renderInputs() == null) {
                    differences.add(name + "_render_inputs_missing");
                    break;
                }
                if (!sample.renderInputs().projectionStability().contains("stabilized=true")) {
                    differences.add(name + "_projection_not_settled");
                    break;
                }
            }
            for (StormReferenceImageComparison.Reference sample : group) {
                if (!sample.worldTimePinned()) {
                    differences.add(name + "_world_time_not_pinned");
                    break;
                }
                if (!sample.historyBypassed()) {
                    differences.add(name + "_history_not_bypassed");
                    break;
                }
            }
            String observed = observedTopology(group);
            if ("unknown".equals(observed) || "mixed".equals(observed)) {
                differences.add(name + "_topology_" + observed);
            }
            String observedMode = observedOptimization(group);
            if ("unknown".equals(observedMode) || "mixed".equals(observedMode)) {
                differences.add(name + "_optimization_" + observedMode);
            }
        }

        private String controlDifferences(ViewCapture other, StormPerformanceBaseline.SuiteFixture fixture) {
            List<String> differences = new ArrayList<>();
            if (other == null) {
                return "missing_pass";
            }
            compare(differences, "cameraX", baseline.cameraX(), other.baseline.cameraX());
            compare(differences, "cameraY", baseline.cameraY(), other.baseline.cameraY());
            compare(differences, "cameraZ", baseline.cameraZ(), other.baseline.cameraZ());
            compare(differences, "yaw", baseline.yaw(), other.baseline.yaw());
            compare(differences, "pitch", baseline.pitch(), other.baseline.pitch());
            compare(differences, "stormDistance", baseline.stormDistance(), other.baseline.stormDistance());
            compare(differences, "workloadGovernorScale", workloadGovernorScale, other.workloadGovernorScale);
            compare(differences, "workloadResolutionScale", workloadResolutionScale, other.workloadResolutionScale);
            compare(differences, "baselineGovernorScale", baseline.inputs().stepScale(), other.baseline.inputs().stepScale());
            compare(differences, "resolutionScale", baseline.resolutionScale(), other.baseline.resolutionScale());
            compare(differences, "targetWidth", baseline.targetWidth(), other.baseline.targetWidth());
            compare(differences, "targetHeight", baseline.targetHeight(), other.baseline.targetHeight());
            compare(differences, "workloadWidth", workload.width(), other.workload.width());
            compare(differences, "workloadHeight", workload.height(), other.workload.height());
            compare(differences, "rayStepsConfigured", baseline.inputs().raymarchSteps(), other.baseline.inputs().raymarchSteps());
            compare(differences, "lightStepsConfigured", baseline.inputs().lightSteps(), other.baseline.inputs().lightSteps());
            compare(differences, "historyValid", baseline.inputs().historyValid(), other.baseline.inputs().historyValid());
            compare(differences, "historyBlend", baseline.inputs().historyBlend(), other.baseline.inputs().historyBlend());
            compare(differences, "topology", baseline.inputs().stormTopologyMode(), other.baseline.inputs().stormTopologyMode());
            // Two passes must be two captures. Equal tokens would mean one
            // readback was recorded twice.
            if (workload.captureToken() == other.workload.captureToken()) {
                differences.add("workload_capture_token_reused=" + workload.captureToken());
            }
            if (workload.captureToken() == StormWorkloadRuntimeCapture.NO_TOKEN
                    || other.workload.captureToken() == StormWorkloadRuntimeCapture.NO_TOKEN) {
                differences.add("workload_capture_token_missing");
            }
            compare(differences, "structuralChanged", baseline.structuralChanged(), other.baseline.structuralChanged());
            compare(differences, "fingerprintAtCapture", baseline.fingerprintAtCapture(), other.baseline.fingerprintAtCapture());
            compare(differences, "fingerprintAtComplete", baseline.fingerprintAtComplete(), other.baseline.fingerprintAtComplete());
            if (!fixture.structuralFingerprint().equals(baseline.fingerprintAtCapture())
                    || !fixture.structuralFingerprint().equals(other.baseline.fingerprintAtCapture())
                    || !fixture.structuralFingerprint().equals(baseline.fingerprintAtComplete())
                    || !fixture.structuralFingerprint().equals(other.baseline.fingerprintAtComplete())) {
                differences.add("frozen_fixture_fingerprint_mismatch");
            }
            if (Math.abs(workloadGovernorScale - TARGET_GOVERNOR_SCALE) > GOVERNOR_TOLERANCE
                    || Math.abs(other.workloadGovernorScale - TARGET_GOVERNOR_SCALE) > GOVERNOR_TOLERANCE
                    || Math.abs(baseline.inputs().stepScale() - TARGET_GOVERNOR_SCALE) > GOVERNOR_TOLERANCE
                    || Math.abs(other.baseline.inputs().stepScale() - TARGET_GOVERNOR_SCALE) > GOVERNOR_TOLERANCE) {
                differences.add("governorScale_not_0.50000");
            }
            if (Math.abs(workloadResolutionScale - TARGET_RESOLUTION_SCALE) > GOVERNOR_TOLERANCE
                    || Math.abs(other.workloadResolutionScale - TARGET_RESOLUTION_SCALE) > GOVERNOR_TOLERANCE
                    || Math.abs(baseline.resolutionScale() - TARGET_RESOLUTION_SCALE) > GOVERNOR_TOLERANCE
                    || Math.abs(other.baseline.resolutionScale() - TARGET_RESOLUTION_SCALE) > GOVERNOR_TOLERANCE) {
                differences.add("resolutionScale_not_0.75000");
            }
            if (baseline.structuralChanged() || other.baseline.structuralChanged()) {
                differences.add("structuralChanged");
            }
            if (!baseline.inputs().historyValid() || !other.baseline.inputs().historyValid()) {
                differences.add("history_not_valid");
            }
            if (baseline.inputs().stormTopologyMode() != StormTopologyMode.COMPACT
                    || other.baseline.inputs().stormTopologyMode() != StormTopologyMode.COMPACT) {
                differences.add("topology_not_compact");
            }
            if (workload.width() != baseline.targetWidth() || workload.height() != baseline.targetHeight()
                    || other.workload.width() != other.baseline.targetWidth()
                    || other.workload.height() != other.baseline.targetHeight()) {
                differences.add("workload_target_does_not_match_baseline_target");
            }
            return differences.isEmpty() ? "" : String.join(",", differences);
        }

        private String delta(ViewCapture reference) {
            StormWorkloadRuntimeCapture.WorkloadResult a = reference.workload;
            StormWorkloadRuntimeCapture.WorkloadResult b = workload;
            return "gpuMeanMs=" + fmt(baseline.gpuMean() - reference.baseline.gpuMean())
                    + " conservativeDescriptorRejects=" + fmtCount(b.conservativeDescriptorRejects() - a.conservativeDescriptorRejects())
                    + " avoidedDescriptorTextureFetches=" + fmtCount(b.avoidedDescriptorTextureFetches() - a.avoidedDescriptorTextureFetches())
                    + " primaryRaySteps=" + fmtCount(b.primaryRaySteps() - a.primaryRaySteps())
                    + " descriptorEvaluations=" + fmtCount(b.descriptorEvaluations() - a.descriptorEvaluations())
                    + " descriptorTextureFetches=" + fmtCount(b.descriptorTextureFetches() - a.descriptorTextureFetches())
                    + " lightMarchDensityEvaluations=" + fmtCount(b.lightMarchDensityEvaluations() - a.lightMarchDensityEvaluations())
                    + " emptySpaceRejects=" + fmtCount(b.emptySpaceRejects() - a.emptySpaceRejects())
                    + " earlyTerminations=" + fmtCount(b.earlyTerminations() - a.earlyTerminations());
        }

        private static void compare(List<String> differences, String field, Object first, Object second) {
            if (!first.equals(second)) {
                differences.add(field + "=" + first + "->" + second);
            }
        }

        private static void compare(List<String> differences, String field, double first, double second) {
            if (!sameControlValue(first, second)) {
                differences.add(field + "=" + fmt(first) + "->" + fmt(second));
            }
        }

        private static void compare(List<String> differences, String field, float first, float second) {
            if (!sameControlValue(first, second)) {
                differences.add(field + "=" + fmt(first) + "->" + fmt(second));
            }
        }

        /**
         * Camera APIs can return either signed representation of zero after a
         * mathematically level look-at. They are the same pose, while every
         * non-zero control value remains an exact comparison for this fixture.
         */
        private static boolean sameControlValue(double first, double second) {
            return (first == 0.0D && second == 0.0D)
                    || Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
        }

        private static boolean sameControlValue(float first, float second) {
            return (first == 0.0F && second == 0.0F)
                    || Float.floatToIntBits(first) == Float.floatToIntBits(second);
        }
    }

    private static final class Session {
        private final StormPerformanceBaseline.SuiteFixture fixture;
        private final List<ViewCapture> passA = new ArrayList<>(VIEW_ORDER.length);
        private final List<ViewCapture> passB = new ArrayList<>(VIEW_ORDER.length);
        private int passIndex;
        private int viewIndex;
        private int framesInView;
        private int poseFrames;
        private int finalFrames;
        private int governorWaitFrames;
        private int governorConfirmFrames;
        private boolean poseApplied;
        private float workloadGovernorScale;
        private float workloadResolutionScale;
        private StormWorkloadRuntimeCapture.WorkloadResult pendingWorkload;
        private final List<StormReferenceImageComparison.Reference> groupA1 = new ArrayList<>();
        private final List<StormReferenceImageComparison.Reference> groupA2 = new ArrayList<>();
        private final List<StormReferenceImageComparison.Reference> groupOff = new ArrayList<>();
        private final List<StormReferenceImageComparison.Reference> groupOn = new ArrayList<>();
        private final List<StormReferenceImageComparison.Reference> groupT121Off = new ArrayList<>();
        private final List<StormReferenceImageComparison.Reference> groupT121On = new ArrayList<>();
        private final List<StormReferenceImageComparison.Reference> groupT122Off = new ArrayList<>();
        private final List<StormReferenceImageComparison.Reference> groupT122On = new ArrayList<>();
        /** Owned-work counters per arm, captured under that arm's own mode. */
        private final java.util.Map<String, StormWorkloadRuntimeCapture.WorkloadResult>
                groupWorkload = new java.util.LinkedHashMap<>();
        private String pendingWorkloadGroup;
        private List<String> sampleGroups = List.of();
        private int sampleGroupIndex;
        private int groupAttempts;
        private boolean awaitingWarmup;
        private long expectedWorkloadToken = StormWorkloadRuntimeCapture.NO_TOKEN;
        private State state = State.MOVE_TO_VIEW;

        private Session(StormPerformanceBaseline.SuiteFixture fixture) {
            this.fixture = fixture;
        }

        private String passName() {
            return passIndex == 0 ? "A" : "B";
        }

        private String view() {
            return VIEW_ORDER[viewIndex];
        }

        private String diagnosticView() {
            return diagnosticViewFor(viewIndex);
        }

        private StormPerformanceBaseline.SuitePose pose() {
            return fixture.pose(view());
        }

        private List<ViewCapture> currentPass() {
            return passIndex == 0 ? passA : passB;
        }

        /** A1/A2 are the local noise control; OFF/ON only when the A/B is enabled. */
        private void beginSampling() {
            clearSampleGroups();
            // t133-ab selects the SC-020 optimization arms; t132-ab selects the
            // banked T119 arms. SC-018 distance views take one settled arm.
            sampleGroups = isSc018View(view())
                    ? List.of("A1")
                    : java.nio.file.Files.exists(java.nio.file.Path.of("t133-ab.txt"))
                            ? List.of("A1", "A2", "T121OFF", "T121ON", "T122OFF", "T122ON")
                            : java.nio.file.Files.exists(java.nio.file.Path.of("t132-ab.txt"))
                                    ? List.of("A1", "A2", "OFF", "ON")
                                    : List.of("A1", "A2");
            sampleGroupIndex = 0;
            awaitingWarmup = true;
            applyGroupState();
        }

        private void clearSampleGroups() {
            groupA1.clear();
            groupA2.clear();
            groupOff.clear();
            groupOn.clear();
            groupT121Off.clear();
            groupT121On.clear();
            groupT122Off.clear();
            groupT122On.clear();
            groupWorkload.clear();
            pendingWorkloadGroup = null;
            sampleGroups = List.of();
            sampleGroupIndex = 0;
            groupAttempts = 0;
            awaitingWarmup = false;
        }

        private String groupName() {
            return sampleGroupIndex < sampleGroups.size()
                    ? sampleGroups.get(sampleGroupIndex) : "done";
        }

        private List<StormReferenceImageComparison.Reference> currentGroup() {
            return switch (groupName()) {
                case "A1" -> groupA1;
                case "A2" -> groupA2;
                case "OFF" -> groupOff;
                case "ON" -> groupOn;
                case "T121OFF" -> groupT121Off;
                case "T121ON" -> groupT121On;
                case "T122OFF" -> groupT122Off;
                case "T122ON" -> groupT122On;
                default -> new ArrayList<>();
            };
        }

        /** True while another group remains. Applies that group's toggle state. */
        private boolean advanceGroup() {
            sampleGroupIndex++;
            groupAttempts = 0;
            if (sampleGroupIndex >= sampleGroups.size()) {
                return false;
            }
            // A group that changes the optimization state discards a warm-up,
            // because a toggle may need its own settling.
            awaitingWarmup = true;
            applyGroupState();
            return true;
        }

        private void applyGroupState() {
            String group = groupName();
            VolumetricCloudDebugConfig.setStormTopologyMode("OFF".equals(group)
                    ? StormTopologyMode.LEGACY_SCAN : StormTopologyMode.COMPACT);
            // Exactly one optimization is disabled at a time; every other arm
            // runs the production paths.
            VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(switch (group) {
                case "T121OFF" -> StormOptimizationDiagnosticMode.T121_OFF;
                case "T122OFF" -> StormOptimizationDiagnosticMode.T122_OFF;
                default -> StormOptimizationDiagnosticMode.NORMAL_PRODUCTION;
            });
        }

        private void beginGovernorWait(State targetState) {
            state = targetState;
            governorWaitFrames = 0;
            governorConfirmFrames = 0;
        }

        private void resetForNextView() {
            state = State.MOVE_TO_VIEW;
            framesInView = 0;
            poseFrames = 0;
            finalFrames = 0;
            governorWaitFrames = 0;
            governorConfirmFrames = 0;
            poseApplied = false;
            workloadGovernorScale = 0.0F;
            workloadResolutionScale = 0.0F;
            pendingWorkload = null;
            clearSampleGroups();
            expectedWorkloadToken = StormWorkloadRuntimeCapture.NO_TOKEN;
        }

        private String controlMismatch() {
            if (passA.size() != VIEW_ORDER.length) {
                return "incomplete_capture";
            }
            if (PASSES > 1) {
                if (passB.size() != VIEW_ORDER.length) {
                    return "incomplete_two_pass_capture";
                }
                for (int index = 0; index < VIEW_ORDER.length; index++) {
                    String differences = passA.get(index).controlDifferences(passB.get(index), fixture);
                    if (!differences.isEmpty()) {
                        return "view=" + VIEW_ORDER[index] + " fields=" + differences;
                    }
                }
            }
            return null;
        }

        private String formatComplete() {
            StringBuilder out = new StringBuilder("stormPerformanceSuite complete")
                    .append("\ngroup=").append(fixture.groupId())
                    .append(" structuralFingerprint=").append(fixture.structuralFingerprint())
                    .append(" topology=compact")
                    .append(" scaleEnvelope={baseTop=")
                    .append(String.format(Locale.ROOT, "%.5f..%.5f", fixture.baseY(), fixture.topY()))
                    .append(" height=")
                    .append(String.format(Locale.ROOT, "%.5f", fixture.topY() - fixture.baseY()))
                    .append(" horizontalRadius=")
                    .append(String.format(Locale.ROOT, "%.5f", fixture.horizontalRadius()))
                    .append(" footprintDiameter=")
                    .append(String.format(Locale.ROOT, "%.5f", fixture.horizontalRadius() * 2.0D))
                    .append(" descriptors=").append(fixture.descriptorCount())
                    .append('}')
                    .append(" descriptorStructure=").append(fixture.descriptorStructure());
            for (int index = 0; index < VIEW_ORDER.length; index++) {
                ViewCapture reference = passA.get(index);
                ViewCapture validation = index < passB.size() ? passB.get(index) : reference;
                boolean hasSecondPass = index < passB.size();
                String differences = reference.controlDifferences(validation, fixture);
                StormReferenceImageComparison.Comparison imageComparison =
                        reference.groupA1.size() >= SAMPLES_PER_ARM
                                && validation.groupA1.size() >= SAMPLES_PER_ARM
                        ? StormReferenceImageComparison.compare(
                                StormReferenceSampleSet.asReference(reference.groupA1.get(0),
                                        StormReferenceSampleSet.median(reference.groupA1)),
                                StormReferenceSampleSet.asReference(validation.groupA1.get(0),
                                        StormReferenceSampleSet.median(validation.groupA1)))
                        : StormReferenceImageComparison.Comparison.failed("incomplete_sample_groups");
                // Criterion 5 is only chargeable to the performance path when the
                // scene provably held still: both passes run the same binary, so
                // an evolving storm explains an image delta on its own.
                StormSceneStability.Result stability = StormSceneStability.evaluate(
                        reference.animatedInputs(), validation.animatedInputs());
                StormCloudContent.Comparison cloudContent = StormCloudContent.compare(
                        reference.groupA1.isEmpty() ? null : reference.groupA1.get(0).cloudContent(),
                        validation.groupA1.isEmpty() ? null : validation.groupA1.get(0).cloudContent());
                StormSceneStability.RenderInputComparison renderInputs =
                        StormSceneStability.compareRenderInputs(
                                reference.groupA1.isEmpty() ? null : reference.groupA1.get(0).renderInputs(),
                                validation.groupA1.isEmpty() ? null : validation.groupA1.get(0).renderInputs());
                out.append("\n").append(VIEW_ORDER[index])
                        .append(" PASS_A={").append(reference.format()).append('}')
                        .append(" PASS_B={").append(validation.format()).append('}')
                        .append(" delta={").append(validation.delta(reference)).append('}')
                        .append(" imageNeutrality={").append(imageComparison.format()).append('}')
                        .append(" sceneStability={").append(stability.format()).append('}')
                        .append(" ").append(renderInputs.format())
                        .append(" ").append(cloudContent.format())
                        .append("\n  localNoiseControl_PASS_A=")
                        .append(groupReport("localNoiseControl", "A1", "A2",
                                reference.groupA1, reference.groupA2))
                        .append("\n  localNoiseControl_PASS_B=")
                        .append(groupReport("localNoiseControl", "A1", "A2",
                                validation.groupA1, validation.groupA2))
                        .append("\n  optimizationAB_T119_PASS_A=")
                        .append(groupReport("T119_stormTopology", "OFF", "ON",
                                reference.groupOff, reference.groupOn, reference.groupWorkload()))
                        .append("\n  optimizationAB_T119_PASS_B=")
                        .append(groupReport("T119_stormTopology", "OFF", "ON",
                                validation.groupOff, validation.groupOn, validation.groupWorkload()))
                        .append("\n  optimizationAB_T121_PASS_A=")
                        .append(groupReport("T121_conservativeRejection", "T121OFF", "T121ON",
                                reference.groupT121Off, reference.groupT121On, reference.groupWorkload()))
                        .append("\n  optimizationAB_T121_PASS_B=")
                        .append(groupReport("T121_conservativeRejection", "T121OFF", "T121ON",
                                validation.groupT121Off, validation.groupT121On, validation.groupWorkload()))
                        .append("\n  optimizationAB_T122_PASS_A=")
                        .append(groupReport("T122_descriptorFetchReuse", "T122OFF", "T122ON",
                                reference.groupT122Off, reference.groupT122On, reference.groupWorkload()))
                        .append("\n  optimizationAB_T122_PASS_B=")
                        .append(groupReport("T122_descriptorFetchReuse", "T122OFF", "T122ON",
                                validation.groupT122Off, validation.groupT122On, validation.groupWorkload()))
                        .append(isSc018View(VIEW_ORDER[index])
                                ? "\n  " + sc018Evidence(VIEW_ORDER[index], reference, fixture)
                                : "")
                        .append("\n  authoritativeAdjacentControls={")
                        .append(authoritativeAdjacentControls(reference, validation, fixture))
                        .append('}')
                        .append("\n  historicalSeparatedPassComparison={authoritative=false")
                        .append(" retired=2026-08-27")
                        .append(" reason=temporal_separation_admits_unrelated_drift")
                        .append(" usedForT132Acceptance=false")
                        .append(" criterion5={")
                        .append(StormSceneStability.attribution(
                                imageComparison.evaluated(), imageComparison.passed(),
                                stability, renderInputs, cloudContent))
                        .append('}')
                        .append(" separatedPassControlFieldsMatch=").append(differences.isEmpty())
                        .append(differences.isEmpty() ? "" : " separatedPassControlDifferences=" + differences)
                        .append('}')
                        .append(" t121Executed=")
                        .append(reference.workload.conservativeDescriptorRejects() > 0.0D
                                || validation.workload.conservativeDescriptorRejects() > 0.0D)
                        .append(" t122ReuseExecuted=")
                        .append(reference.workload.avoidedDescriptorTextureFetches() > 0.0D
                                || validation.workload.avoidedDescriptorTextureFetches() > 0.0D)
                        .append(" t123EarlyTerminationObserved=")
                        .append(reference.workload.earlyTerminations() > 0.0D
                                || validation.workload.earlyTerminations() > 0.0D);
            }
            return out.toString();
        }
    }
}
