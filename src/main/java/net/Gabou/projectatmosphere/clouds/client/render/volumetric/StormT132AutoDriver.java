package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-only driver that runs the whole T132 validation workflow from inside the
 * client, so the evidence can be collected without keyboard automation.
 *
 * <p>The diagnostic commands are registered on {@code RegisterClientCommandsEvent},
 * so they exist only in the chat GUI and cannot be reached from a server
 * dispatcher or RCON. Driving them by synthetic keystrokes proved unreliable:
 * the injected keys reach whatever screen happens to be focused rather than the
 * chat line. This driver instead calls the same diagnostic entry points the
 * command handlers call, and issues the one server-side command it needs
 * through the player's own connection - the same mechanism the suite already
 * uses to teleport between poses.
 *
 * <p>It is inert unless the marker file {@code run/t132-autorun.txt} exists, so
 * an ordinary client never enters it. It changes no rendering behaviour; it only
 * sequences diagnostics that a human would otherwise type.
 */
final class StormT132AutoDriver {
    private static final Path MARKER = Path.of("t132-autorun.txt");
    /**
     * T098 production ray trace marker. When present the run goes straight
     * from a matured fixture to the SIDE ray trace and then to the T098
     * captures, skipping the T132 performance suite and the T128 centre-line
     * trace, neither of which the ray-trace investigation consumes. Absent,
     * the run is exactly the established T132/T133 sequence.
     */
    private static final Path RAYTRACE_MARKER = Path.of("t098-raytrace.txt");
    /**
     * T135 marker. When present the run performs the five-mode performance
     * sweep before the T098 captures, at the SC-006 reference resolution.
     */
    private static final Path T135_MARKER = Path.of("t135-performance.txt");
    /**
     * T138 rank-1 marker. When present the sweep walks (pose x internal
     * resolution) at a fixed ULTRA step budget instead of (pose x quality
     * mode), so the measured variable is the marched pixel count alone. The
     * step budget, lighting, descriptor path and fixture are identical in every
     * arm; only the cloud render target's dimensions change.
     */
    private static final Path T138_MARKER = Path.of("t138-resolution.txt");
    /**
     * T141 marker. The sweep walks (pose x descriptor-evaluation arm) at one
     * fixed resolution and one fixed quality mode, so the measured variable is
     * descriptor evaluation work alone. Non-comment lines select the poses.
     */
    private static final Path T141_MARKER = Path.of("t141-descriptor-eval.txt");
    /** T153 marker: production plus four visible-volume oracle ceiling arms. */
    private static final Path T153_MARKER = Path.of("t153-visible-volume-oracle.txt");
    /**
     * T152 marker. The run drives the deterministic moving-camera route twice -
     * once without temporal accumulation and once with it - and measures
     * silhouette stability per frame. It shares the T135 fixture resolution but
     * none of the pose sweep, because a pose sweep is exactly what it exists to
     * complement.
     */
    private static final Path T152_MARKER = Path.of("t152-moving-camera.txt");
    /** SC-006 states its total-frame budget at this resolution. */
    private static final int T135_WIDTH = 1920;
    private static final int T135_HEIGHT = 1080;
    /** Frames held after a teleport before the first mode is sampled. */
    private static final int T135_POSE_SETTLE_FRAMES = 120;
    private static final Pattern BASE_TOP =
            Pattern.compile("baseTop=(-?[0-9.]+)\\.\\.(-?[0-9.]+)");

    /**
     * Client ticks a four-stage counter readback may occupy. Each stage needs
     * one rendered frame, and the expensive arms present at one to two frames
     * per second while the client still runs ten ticks per frame.
     */
    private static final int COUNTER_TIMEOUT_FRAMES = 2400;

    /** Frames to let terrain and the cloud field settle before spawning. */
    private static final int WORLD_SETTLE_FRAMES = 400;
    /** Frames to wait for the spawned storm to publish complete descriptors. */
    private static final int ADOPT_TIMEOUT_FRAMES = 3600;
    /** Frames a single diagnostic may run before the driver gives up. */
    private static final int STAGE_TIMEOUT_FRAMES = 36000;
    private static final int TRACE_MARGIN_BLOCKS = 32;
    /** Consecutive frames the published topology generation must hold still. */
    private static final int REQUIRED_MATURE_FRAMES = 900;
    private static final int MATURE_TIMEOUT_FRAMES = 18000;
    private static final int MAX_SUITE_ATTEMPTS = 4;
    private static final int INFRA_TIMEOUT_FRAMES = 36_000;
    private static final Path LATEST_LOG = Path.of("logs", "latest.log");
    private static final String INFRA_LOG_MARKER = "T132_AUTORUN_INFRA_BEGIN";

    private enum Phase {
        IDLE, BOOTSTRAP_PREPARE, BOOTSTRAP_WAIT_SOURCE, BOOTSTRAP_UNLOAD_SOURCE,
        BOOTSTRAP_RESTORE, BOOTSTRAP_WAIT_RESTORED, WAIT_WORLD, SPAWN_STORM, WAIT_ADOPT, WAIT_MATURE, BEGIN_SUITE, POLL_SUITE,
        BEGIN_TRACE, POLL_TRACE,
        RAYTRACE_FIXTURE,
        T135_PREPARE, T135_MOVE, T135_SETTLE, T135_VERIFY, T135_SAMPLE, T135_COUNTERS,
        T135_RESPAWN, T135_REPORT,
        T152_BEGIN, T152_POLL,
        BEGIN_T098, POLL_T098, DONE
    }

    private static Phase phase = Phase.IDLE;
    private static boolean checkedMarker;
    private static boolean enabled;
    private static int frames;
    private static int stageFrames;
    private static String suiteReport = "";
    private static long lastTopologyGeneration = Long.MIN_VALUE;
    private static int stableGenerationFrames;
    private static int suiteAttempts;
    private static boolean restoreConfirmationAccepted;
    /**
     * The production configuration already has a debug/screenshot movement
     * freeze.  The marker-gated harness uses it only to make its repeated
     * capture fixture stationary, then restores the exact pre-run value.
     * This is deliberately not a renderer or morphology switch.
     */
    private static Boolean originalMovementFreeze;
    private static boolean movementFreezeApplied;
    private static boolean daylightFreezeApplied;
    /**
     * Fixed sun position for the capture window. Noon keeps the sun high and
     * well clear of the sunrise/sunset windows, so the derived lighting sits on
     * a flat part of its curve rather than a steep one.
     */
    private static final long FIXTURE_DAY_TIME = 6000L;
    private static float originalFixedResolutionScale = Float.NaN;
    private static boolean fixedResolutionApplied;
    private static AtmoCommonConfig.CloudRaymarchQuality originalRaymarchQuality;
    private static boolean raymarchQualityApplied;

    private StormT132AutoDriver() {
    }

    static void tick() {
        if (!checkedMarker) {
            checkedMarker = true;
            enabled = Files.exists(MARKER);
            if (enabled) {
                phase = Phase.BOOTSTRAP_PREPARE;
                ProjectAtmosphere.LOGGER.info("T132_AUTORUN armed by {}", MARKER.toAbsolutePath());
            }
        }
        if (!enabled || phase == Phase.DONE || phase == Phase.IDLE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        frames++;
        stageFrames++;

        switch (phase) {
            case BOOTSTRAP_PREPARE -> bootstrapPrepare(minecraft);
            case BOOTSTRAP_WAIT_SOURCE -> bootstrapWaitSource(minecraft);
            case BOOTSTRAP_UNLOAD_SOURCE -> bootstrapUnloadAndRestore(minecraft);
            case BOOTSTRAP_RESTORE -> bootstrapRestore(minecraft);
            case BOOTSTRAP_WAIT_RESTORED -> bootstrapWaitRestored(minecraft);
            default -> tickInWorld(minecraft);
        }
    }

    private static boolean rayTraceRunRequested() {
        return Files.exists(RAYTRACE_MARKER);
    }

    private static boolean performanceRunRequested() {
        return Files.exists(T135_MARKER) || resolutionRunRequested()
                || evaluationRunRequested() || oracleRunRequested()
                || movingCameraRunRequested();
    }

    private static boolean resolutionRunRequested() {
        return Files.exists(T138_MARKER);
    }

    private static boolean evaluationRunRequested() {
        return Files.exists(T141_MARKER);
    }

    private static boolean oracleRunRequested() {
        return Files.exists(T153_MARKER);
    }

    private static boolean movingCameraRunRequested() {
        return Files.exists(T152_MARKER);
    }

    /** The five shipped quality modes, in ascending cost order. */
    private static final AtmoCommonConfig.CloudRaymarchQuality[] T135_MODES = {
            AtmoCommonConfig.CloudRaymarchQuality.LOW,
            AtmoCommonConfig.CloudRaymarchQuality.LOW_24,
            AtmoCommonConfig.CloudRaymarchQuality.MEDIUM,
            AtmoCommonConfig.CloudRaymarchQuality.HIGH,
            AtmoCommonConfig.CloudRaymarchQuality.ULTRA
    };
    /**
     * T136 scenarios. A is the severe worst case, B is storm gameplay the
     * player is not deliberately parked in, D is the clear-weather control.
     * Ordered cheapest-last so an expiring fixture costs the least important
     * cells first.
     */
    private static final String[] T135_POSES = {
            "SIDE", "BELOW", "ABOVE", "FAR", "NEAR_EDGE",
            "PLAY_NEAR", "PLAY_MID", "PLAY_HIGH", "CLEAR"};
    /**
     * T138 internal-resolution arms, as linear scales of the 1920x1080 display
     * resolution. 0.75 is the shipped ULTRA scale and is included so the sweep
     * carries its own baseline rather than borrowing T136's. The targets are
     * ceil(1920*s) x ceil(1080*s): 1920x1080, 1440x810, 960x540, 720x405,
     * 480x270, 360x203 and 240x135. The two most aggressive arms exist because
     * the representative gap after T145 is 54.7x and a frontier that stops at
     * a quarter scale cannot answer whether an order of magnitude is reachable.
     */
    private static final float[] T138_SCALES = {
            1.00F, 0.75F, 0.50F, 0.375F, 0.25F, 0.1875F, 0.125F,
            // 0.1768 is the half-marched-pixel point: it is what a 2-phase
            // interleave would actually march. T151 used it, with 0.125 as the
            // 4-phase equivalent, to bound interleaving before building it.
            0.1768F};
    /**
     * T138 poses. CLEAR is dropped: it has no storm and therefore no marched
     * cloud pixels to scale. PLAY_NEAR leads because it is the representative
     * case the budget has to hold, and the three most expensive severe poses
     * trail so a decaying fixture costs the least decisive cells first.
     */
    private static final String[] T138_DEFAULT_POSES = {
            "PLAY_NEAR", "SIDE", "FAR", "ABOVE", "BELOW", "NEAR_EDGE",
            // PLAY_MID and PLAY_HIGH sit 7x and 5x the storm radius away, far
            // enough that the client stops holding the storm's descriptors.
            // They are measured last so a pose that cannot keep a fixture
            // cannot cost the decisive severe and representative cells.
            "PLAY_MID", "PLAY_HIGH"};
    /**
     * The pose list actually swept. Non-comment lines of the T138 marker
     * override the default, so a follow-up arm can re-aim the sweep without a
     * recompile - which matters because the first sweep proved the shipped
     * PLAY_* poses sit outside the 2000-block cloud render distance at T134
     * storm scale and therefore render no storm at all.
     */
    private static String[] T138_POSES = T138_DEFAULT_POSES;

    private static void resolveT138Poses() {
        try {
            java.util.List<String> parsed = new java.util.ArrayList<>();
            for (String line : Files.readAllLines(T138_MARKER)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    parsed.add(trimmed.toUpperCase(Locale.ROOT));
                }
            }
            T138_POSES = parsed.isEmpty()
                    ? T138_DEFAULT_POSES
                    : parsed.toArray(new String[0]);
        } catch (Exception exception) {
            T138_POSES = T138_DEFAULT_POSES;
        }
        ProjectAtmosphere.LOGGER.info("T138_RES poses={}",
                String.join(",", T138_POSES));
    }
    /**
     * Attempts allowed on one (pose, arm) cell before it is recorded as
     * unmeasurable and the sweep moves on. Without a bound a pose that can
     * never hold descriptors respawns forever, which is exactly what the first
     * T138 attempt did at PLAY_HIGH.
     */
    private static final int T138_MAX_ARM_ATTEMPTS = 3;
    private static int t138ArmAttempts;
    private static boolean t138CellPending;
    /**
     * Poses that also run a history-disabled arm, so the temporal blend's own
     * cost is measured per resolution instead of assumed constant. Restricted
     * to the representative pose and the severe reference pose: running it
     * everywhere would double a sweep that already outlasts its fixture.
     */
    private static final String[] T138_HISTORY_ARM_POSES = {};
    private static boolean t138ResolutionRun;
    private static int t138ScaleIndex;
    private static boolean t138HistoryArm;

    /**
     * T149 lighting/detail arms, all at one resolution and one quality mode,
     * and at the mode's own shipped resolution rather than a pinned one. The
     * first four arms establish production and independent/combined ceilings;
     * the remaining arms isolate the continuous inputs before the complete
     * graded candidate is measured.
     */
    private static final StormOptimizationDiagnosticMode[] T141_ARMS = {
            StormOptimizationDiagnosticMode.NORMAL_PRODUCTION,
            StormOptimizationDiagnosticMode.T147_DETAIL_OFF,
            // Not an optimization mode: constant lighting is toggled separately.
            StormOptimizationDiagnosticMode.NORMAL_PRODUCTION,
            // Combined ceiling: detail dropped plus constant lighting.
            StormOptimizationDiagnosticMode.T147_DETAIL_OFF,
            StormOptimizationDiagnosticMode.T149_DETAIL_GRADED,
            StormOptimizationDiagnosticMode.T149_LIGHT_CONTRIBUTION,
            StormOptimizationDiagnosticMode.T149_LIGHT_DISTANCE,
            StormOptimizationDiagnosticMode.T149_LIGHT_VERTICAL,
            StormOptimizationDiagnosticMode.T149_LIGHT_GRADED,
            StormOptimizationDiagnosticMode.T149_GRADED
    };
    private static final StormOptimizationDiagnosticMode[] T153_ARMS = {
            StormOptimizationDiagnosticMode.NORMAL_PRODUCTION,
            StormOptimizationDiagnosticMode.T153_PERFECT_EMPTY_SKIP,
            StormOptimizationDiagnosticMode.T153_PERFECT_OCCUPIED_INTERVALS,
            StormOptimizationDiagnosticMode.T153_PERFECT_OPTICAL_RELEVANCE,
            StormOptimizationDiagnosticMode.T153_COMBINED
    };
    private static final String[] T153_POSES = {
            "PLAY_VIS_NEAR", "PLAY_VIS_MID", "SIDE", "FAR",
            "ABOVE", "BELOW", "NEAR_EDGE"
    };
    /** Index of the arm that is production plus constant lighting. */
    private static final int T141_CONSTANT_LIGHTING_ARM = 2;
    /** Index of the arm that is detail-off plus constant lighting. */
    private static final int T141_LIGHT_AND_DETAIL_ARM = 3;
    /**
     * The resolution every T141 cell is measured at. Fixed so evaluation work
     * is the only variable, and chosen at the shipped Ultra scale so the arms
     * are comparable with the T136 and T138 records.
     */
    // T147 measures the renderer as shipped, so the sweep must NOT pin a scale:
    // NaN releases the diagnostic override and every cell renders at the quality
    // mode's own Rank 1 ladder value.
    private static final float T141_RESOLUTION_SCALE = Float.NaN;
    private static final String[] T141_DEFAULT_POSES = {
            "PLAY_VIS_NEAR", "PLAY_VIS_MID", "SIDE", "FAR", "ABOVE", "BELOW",
            "NEAR_EDGE", "CLEAR"};
    private static String[] T141_POSES = T141_DEFAULT_POSES;
    private static boolean t141EvaluationRun;
    private static boolean t153OracleRun;
    private static boolean t153OriginalHistoryEnabled = true;
    private static int t153PoseAttempts;
    private static int t141ArmIndex;
    private static int t141ArmAttempts;
    private static boolean t141CellPending;

    private static void resolveT141Poses() {
        try {
            java.util.List<String> parsed = new java.util.ArrayList<>();
            for (String line : Files.readAllLines(T141_MARKER)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    parsed.add(trimmed.toUpperCase(Locale.ROOT));
                }
            }
            T141_POSES = parsed.isEmpty() ? T141_DEFAULT_POSES : parsed.toArray(new String[0]);
        } catch (Exception exception) {
            T141_POSES = T141_DEFAULT_POSES;
        }
        ProjectAtmosphere.LOGGER.info("T141_EVAL poses={} arms={}",
                String.join(",", T141_POSES), T141_ARMS.length);
    }

    private static void applyT141Arm() {
        VolumetricCloudDebugConfig.setFixedResolutionScale(T141_RESOLUTION_SCALE);
        if (t153OracleRun) {
            // Every T153 arm must use the same temporal state. Oracle replay
            // frames deliberately do not enter production history, so leaving
            // history enabled would make later oracle arms lose valid history
            // while the production baseline retained it. Disable it for the
            // complete diagnostic matrix and restore the prior value on exit.
            VolumetricCloudDebugConfig.setHistoryEnabled(false);
        }
        StormOptimizationDiagnosticMode[] arms = activeEvaluationArms();
        VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(
                arms[Math.max(0, Math.min(arms.length - 1, t141ArmIndex))]);
        // The lighting share needs its own arm at the new ladder: every earlier
        // measurement of it was taken at 1440x810.
        VolumetricCloudDebugConfig.setT136ConstantLighting(
                !t153OracleRun && (t141ArmIndex == T141_CONSTANT_LIGHTING_ARM
                        || t141ArmIndex == T141_LIGHT_AND_DETAIL_ARM));
    }

    private static String t141ArmName() {
        StormOptimizationDiagnosticMode[] arms = activeEvaluationArms();
        if (t153OracleRun) {
            return arms[Math.max(0, Math.min(arms.length - 1, t141ArmIndex))]
                    .serializedName();
        }
        if (t141ArmIndex == T141_CONSTANT_LIGHTING_ARM) {
            return "constant_lighting";
        }
        if (t141ArmIndex == T141_LIGHT_AND_DETAIL_ARM) {
            return "light_and_detail_off";
        }
        return arms[Math.max(0, Math.min(arms.length - 1, t141ArmIndex))]
                .serializedName();
    }

    private static StormOptimizationDiagnosticMode[] activeEvaluationArms() {
        return t153OracleRun ? T153_ARMS : T141_ARMS;
    }

    private static boolean t138HistoryArmPose(String pose) {
        for (String candidate : T138_HISTORY_ARM_POSES) {
            if (candidate.equals(pose)) {
                return true;
            }
        }
        return false;
    }

    /**
     * T150 storm-visibility guard. A pose may not produce a single cell until
     * the storm it claims to measure is proven present: first geometrically,
     * which is free, then by the march's own counters, which is authoritative.
     *
     * <p>The geometric test alone is not enough - the corrupted PLAY_VIS_NEAR
     * cells were inside the render distance and inside the frustum and still
     * marched nothing - and the counter test alone is wasteful, because a pose
     * that is obviously out of range should not cost a readback to reject.
     */
    private static int t150Attempts;
    private static boolean t150CaptureRequested;

    private static void verifyStormVisible(Minecraft minecraft, LocalPlayer player) {
        String pose = sweepPose();
        if (pose.startsWith("CLEAR")) {
            // The control pose expects no storm; verifying one would reject it.
            t150Attempts = 0;
            t150CaptureRequested = false;
            advance(Phase.T135_SAMPLE);
            return;
        }
        StormPerformanceBaseline.SuiteFixture fixture = StormPerformanceBaseline.suiteFixture();
        if (fixture == null) {
            failStormVisibility(pose, "fixture_missing");
            return;
        }
        if (!t150CaptureRequested) {
            StormFixtureVisibility.Verdict verdict = StormFixtureVisibility.evaluate(
                    StormGeometryBuildCoordinator.lobeCount(),
                    fixture.centerX(), fixture.centerZ(), fixture.baseY(), fixture.topY(),
                    fixture.horizontalRadius(),
                    player.getX(), player.getEyeY(), player.getZ(),
                    player.getYRot(), player.getXRot(),
                    minecraft.options.fov().get(),
                    AtmoCommonConfig.CLOUD_RENDER_DISTANCE.get());
            if (!verdict.valid()) {
                ProjectAtmosphere.LOGGER.warn("T150_VISIBILITY_REJECT pose={} {}",
                        pose, verdict.format());
                failStormVisibility(pose, verdict.reason());
                return;
            }
            ProjectAtmosphere.LOGGER.info("T150_VISIBILITY_GEOMETRY pose={} {}",
                    pose, verdict.format());
            t150CaptureRequested = true;
            VolumetricCloudFrameDiagnostics.requestStormWorkloadCapture("side");
            advance(Phase.T135_VERIFY);
            return;
        }
        if (VolumetricCloudFrameDiagnostics.stormWorkloadActive()) {
            if (stageFrames > COUNTER_TIMEOUT_FRAMES) {
                VolumetricCloudFrameDiagnostics.abortStormWorkloadCapture();
                failStormVisibility(pose, "visibility_capture_timeout");
            }
            return;
        }
        com.mojang.blaze3d.pipeline.RenderTarget cloudTarget =
                VolumetricCloudRenderTargets.currentCloudTarget();
        int marchedPixels = cloudTarget == null ? 0 : cloudTarget.width * cloudTarget.height;
        double densityCalls = VolumetricCloudFrameDiagnostics.stormWorkloadCloudDensityCalls();
        if (!StormFixtureVisibility.renderedStormConfirmed(densityCalls, marchedPixels)) {
            ProjectAtmosphere.LOGGER.warn(
                    "T150_VISIBILITY_REJECT pose={} rendered no storm:"
                            + " cloudDensityCalls={} marchedPixels={}",
                    pose, fmt(densityCalls), marchedPixels);
            failStormVisibility(pose, "rendered_no_storm");
            return;
        }
        ProjectAtmosphere.LOGGER.info(
                "T150_VISIBILITY_CONFIRMED pose={} cloudDensityCalls={} perPixel={}",
                pose, fmt(densityCalls), fmt(densityCalls / Math.max(1, marchedPixels)));
        t150Attempts = 0;
        t150CaptureRequested = false;
        advance(Phase.T135_SAMPLE);
    }

    /** Bounded retry: respawn and re-resolve, then abandon the pose. */
    private static void failStormVisibility(String pose, String reason) {
        t150CaptureRequested = false;
        if (++t150Attempts < T138_MAX_ARM_ATTEMPTS) {
            ProjectAtmosphere.LOGGER.info(
                    "T150_VISIBILITY retrying {} after {} (attempt {})",
                    pose, reason, t150Attempts + 1);
            advance(Phase.T135_RESPAWN);
            return;
        }
        ProjectAtmosphere.LOGGER.warn(
                "T150_VISIBILITY abandoning pose {}: {} after {} attempts."
                        + " No cell is recorded for it rather than an empty-sky one.",
                pose, reason, t150Attempts);
        t150Attempts = 0;
        // Skip the whole pose. Its arms are not measurable on this fixture.
        t141ArmIndex = 0;
        t141CellPending = false;
        t138ScaleIndex = 0;
        t138HistoryArm = false;
        t135PoseIndex++;
        if (t135PoseIndex >= sweepPoses().length) {
            advance(Phase.T135_REPORT);
        } else {
            advance(Phase.T135_MOVE);
        }
    }

    /** The pose list in force, which differs between the T136 and T138 sweeps. */
    private static String[] sweepPoses() {
        if (t141EvaluationRun) {
            return t153OracleRun ? T153_POSES : T141_POSES;
        }
        return t138ResolutionRun ? T138_POSES : T135_POSES;
    }

    private static String sweepPose() {
        String[] poses = sweepPoses();
        return poses[Math.max(0, Math.min(poses.length - 1, t135PoseIndex))];
    }

    /**
     * Applies one T138 arm: the internal resolution under test, and whether the
     * temporal history blend participates. Both are existing diagnostic
     * controls; neither changes a density, lighting or morphology equation.
     */
    private static void applyT138Arm() {
        float scale = T138_SCALES[Math.max(0, Math.min(T138_SCALES.length - 1, t138ScaleIndex))];
        VolumetricCloudDebugConfig.setFixedResolutionScale(scale);
        VolumetricCloudDebugConfig.setHistoryEnabled(!t138HistoryArm);
    }

    private static String t138ArmName() {
        float scale = T138_SCALES[Math.max(0, Math.min(T138_SCALES.length - 1, t138ScaleIndex))];
        return String.format(Locale.ROOT, "%s@%.3f",
                t138HistoryArm ? "historyOff" : "production", scale);
    }

    private static int t135PoseIndex;
    private static int t135ModeIndex;
    private static int t135SettleFrames;
    private static int t135CellRetries;
    /** 0 production, 1 constant lighting, 2 T122 descriptor refetch. */
    private static int t135Arm;
    private static boolean t135LightingArm;
    private static String t135CounterLabel = "";
    private static boolean t135CountersRequested;
    /** True while the T152 route owns the run, so a respawn returns to it. */
    private static boolean t152Run;
    private static int t152Attempts;
    private static boolean t153LastCounterInvalid;
    private static final java.util.List<T153CounterCell> T153_COUNTERS =
            new java.util.ArrayList<>();
    private static final java.util.Map<String, T153PoseFixture> T153_FIXTURES =
            new java.util.LinkedHashMap<>();

    private record T153CounterCell(
            String pose, String arm, StormWorkloadRuntimeCapture.WorkloadResult workload) {
    }

    private record T153PoseFixture(String groupId, String structuralFingerprint) {
    }

    private static String t135ArmName() {
        return switch (t135Arm) {
            case 1 -> "constantLighting";
            case 2 -> "t122Refetch";
            default -> "production";
        };
    }

    /**
     * The attribution arms, applied for the SIDE and PLAY_NEAR poses only. The
     * T122 arm re-issues the six descriptor texel fetches per lobe that
     * production keeps in registers, so the GPU-time difference is the marginal
     * cost of descriptor fetches measured directly rather than estimated.
     */
    private static void applyT135Arm() {
        VolumetricCloudDebugConfig.setT136ConstantLighting(t135Arm == 1);
        VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(t135Arm == 2
                ? StormOptimizationDiagnosticMode.T122_OFF
                : StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
    }
    /** Retries allowed per cell before it is abandoned as unmeasurable. */
    private static final int T135_MAX_CELL_RETRIES = 2;

    private static void tickInWorld(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || player.connection == null) {
            if (stageFrames > INFRA_TIMEOUT_FRAMES) {
                finishInfrastructureInvalid("world_entry_lost_during_" + phase);
            }
            return;
        }
        switch (phase) {
            case WAIT_WORLD -> {
                if (stageFrames >= WORLD_SETTLE_FRAMES) {
                    advance(Phase.SPAWN_STORM);
                }
            }
            case SPAWN_STORM -> {
                // Spectator does not fall. The sampling phase holds one pose for
                // many seconds, and a drifting camera would silently change what
                // each sample rendered.
                applyFixtureMotionFreeze(player);
                applyFixtureDaylightFreeze(player);
                applyFixtureResolutionControl();
                applyFixtureQualityControl();
                player.connection.sendCommand("gamemode spectator");
                player.connection.sendCommand("pa cloud spawn cumulonimbus_capillatus");
                ProjectAtmosphere.LOGGER.info("T132_AUTORUN spawn requested at {},{},{}",
                        fmt(player.getX()), fmt(player.getY()), fmt(player.getZ()));
                advance(Phase.WAIT_ADOPT);
            }
            case WAIT_ADOPT -> {
                if (StormGeometryBuildCoordinator.lobeCount() >= 10) {
                    ProjectAtmosphere.LOGGER.info(
                            "T132_AUTORUN descriptors adopted lobeCount={} after {} frames",
                            StormGeometryBuildCoordinator.lobeCount(), stageFrames);
                    advance(Phase.WAIT_MATURE);
                } else if (stageFrames > ADOPT_TIMEOUT_FRAMES) {
                    // The storm may need another spawn; retry once per timeout
                    // rather than stalling the whole run.
                    ProjectAtmosphere.LOGGER.info(
                            "T132_AUTORUN no descriptors after {} frames; respawning", stageFrames);
                    advance(Phase.SPAWN_STORM);
                }
            }
            case WAIT_MATURE -> {
                // A freshly spawned storm keeps regenerating its descriptors, and
                // the suite fixture is invalidated by any structural change. Wait
                // for the published topology to stop moving before starting.
                long generation = StormGeometryBuildCoordinator.topologyGeneration();
                if (generation != lastTopologyGeneration) {
                    lastTopologyGeneration = generation;
                    stableGenerationFrames = 0;
                } else if (++stableGenerationFrames >= REQUIRED_MATURE_FRAMES) {
                    ProjectAtmosphere.LOGGER.info(
                            "T132_AUTORUN storm mature: topologyGeneration={} stable for {} frames",
                            generation, stableGenerationFrames);
                    advance(rayTraceRunRequested() || performanceRunRequested()
                            ? Phase.RAYTRACE_FIXTURE : Phase.BEGIN_SUITE);
                }
                if (stageFrames > MATURE_TIMEOUT_FRAMES) {
                    ProjectAtmosphere.LOGGER.info(
                            "T132_AUTORUN storm never matured; proceeding at generation {}", generation);
                    advance(rayTraceRunRequested() || performanceRunRequested()
                            ? Phase.RAYTRACE_FIXTURE : Phase.BEGIN_SUITE);
                }
            }
            case BEGIN_SUITE -> {
                String begun = VolumetricCloudFrameDiagnostics.beginStormPerformanceSuite(
                        player.getX(), player.getY(), player.getZ());
                if (begun.startsWith("acquiring")) {
                    ProjectAtmosphere.LOGGER.info("T132_AUTORUN suite begun: {}", begun);
                    advance(Phase.POLL_SUITE);
                } else if (stageFrames > ADOPT_TIMEOUT_FRAMES) {
                    finish("suite_begin_failed:" + begun);
                }
            }
            case POLL_SUITE -> {
                String latest = VolumetricCloudFrameDiagnostics.stormPerformanceSuiteLatest();
                if (latest.startsWith("stormPerformanceSuite complete")) {
                    suiteReport = latest;
                    ProjectAtmosphere.LOGGER.info("T132_AUTORUN suite complete after {} frames",
                            stageFrames);
                    advance(Phase.BEGIN_TRACE);
                } else if (latest.startsWith("stormPerformanceSuite aborted")) {
                    ProjectAtmosphere.LOGGER.info("T132_AUTORUN suite aborted: {}", latest);
                    // The first begin snapshots the fixture from the spawn
                    // position; the suite then teleports, which can reorder
                    // groups by camera distance and reassign the group slot that
                    // the structural fingerprint covers. Re-begin from the pose
                    // the suite already moved to rather than abandoning the run.
                    if (++suiteAttempts <= MAX_SUITE_ATTEMPTS) {
                        ProjectAtmosphere.LOGGER.info(
                                "T132_AUTORUN retrying suite, attempt {}/{}",
                                suiteAttempts, MAX_SUITE_ATTEMPTS);
                        advance(Phase.WAIT_MATURE);
                    } else {
                        finish("suite_aborted");
                    }
                } else if (stageFrames > STAGE_TIMEOUT_FRAMES) {
                    finish("suite_timeout:" + latest);
                }
            }
            case BEGIN_TRACE -> {
                Matcher matcher = BASE_TOP.matcher(suiteReport);
                if (!matcher.find()) {
                    finish("trace_bounds_unavailable");
                    return;
                }
                float baseY = Float.parseFloat(matcher.group(1));
                float topY = Float.parseFloat(matcher.group(2));
                float start = (float) Math.floor(baseY - TRACE_MARGIN_BLOCKS);
                float end = (float) Math.ceil(topY + TRACE_MARGIN_BLOCKS);
                String requested = VolumetricCloudFrameDiagnostics.requestStormMaterialTrace(
                        player.getX(), player.getZ(), start, end);
                if (requested.startsWith("acquiring")) {
                    ProjectAtmosphere.LOGGER.info(
                            "T132_AUTORUN trace requested {}..{}: {}", start, end, requested);
                    advance(Phase.POLL_TRACE);
                } else if (stageFrames > ADOPT_TIMEOUT_FRAMES) {
                    finish("trace_request_failed:" + requested);
                }
            }
            case POLL_TRACE -> {
                String latest = VolumetricCloudFrameDiagnostics.stormMaterialTraceLatest();
                if (latest.startsWith("T128 production shader material trace")) {
                    ProjectAtmosphere.LOGGER.info("T132_AUTORUN trace complete:\n{}", latest);
                    // T098 captures ride on the same matured, descriptor-owned
                    // fixture the suite just validated, so the screenshots and
                    // the numeric evidence describe one storm.
                    advance(Phase.BEGIN_T098);
                } else if (stageFrames > STAGE_TIMEOUT_FRAMES) {
                    finish("trace_timeout:" + latest);
                }
            }
            case RAYTRACE_FIXTURE -> {
                // The ray trace addresses the storm by its published geometry,
                // so it needs the same resolved fixture the captures use. This
                // resolves it without running the timing suite; the trace
                // itself is taken inside the capture set, at the capture pose
                // and the capture render target, so the traced ray and the
                // captured frame cannot differ in configuration.
                String begun = VolumetricCloudFrameDiagnostics.beginStormPerformanceBaseline(
                        player.getX(), player.getY(), player.getZ());
                if (StormPerformanceBaseline.suiteFixture() != null) {
                    ProjectAtmosphere.LOGGER.info("T098_RAYTRACE fixture resolved: {}", begun);
                    advance(movingCameraRunRequested()
                            ? Phase.T152_BEGIN
                            : (performanceRunRequested()
                                    ? Phase.T135_PREPARE : Phase.BEGIN_T098));
                } else if (stageFrames > ADOPT_TIMEOUT_FRAMES) {
                    finish("raytrace_fixture_failed:" + begun);
                }
            }
            case T152_BEGIN -> {
                // Measured at the SC-006 reference resolution, like every other
                // record, and at the shipped Ultra ladder scale rather than the
                // capture pin: the question is how the renderer that ships
                // behaves under motion, so its own internal resolution is part
                // of the answer.
                try {
                    com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(() ->
                            org.lwjgl.glfw.GLFW.glfwSetWindowSize(
                                    minecraft.getWindow().getWindow(),
                                    T135_WIDTH, T135_HEIGHT));
                } catch (Throwable throwable) {
                    ProjectAtmosphere.LOGGER.warn(
                            "T152_ROUTE window resize failed: {}", throwable.toString());
                }
                restoreFixtureResolutionControl();
                AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.set(
                        AtmoCommonConfig.CloudRaymarchQuality.ULTRA);
                // The fixture's cloud-movement freeze stays applied. The route
                // is a camera-motion measurement, so material advection must
                // not also be moving: otherwise a silhouette change cannot be
                // attributed to the camera rather than to the storm.
                t152Run = true;
                String begun = StormT152MovingCameraFixture.begin();
                if ("t152_started".equals(begun)) {
                    advance(Phase.T152_POLL);
                } else if (stageFrames > ADOPT_TIMEOUT_FRAMES) {
                    finish("t152_begin_failed:" + begun);
                }
            }
            case T152_POLL -> {
                if (StormT152MovingCameraFixture.finished()) {
                    String latest = StormT152MovingCameraFixture.latest();
                    ProjectAtmosphere.LOGGER.info("T152_ROUTE result={}", latest);
                    // A dissipated fixture is a retryable condition, not a
                    // result. Both arms must fly the same storm, so the whole
                    // route restarts against a fresh one rather than salvaging
                    // a half-empty arm.
                    if (latest.contains("fixture_dissipated")
                            || latest.contains("fixture_identity_changed")) {
                        if (++t152Attempts < T138_MAX_ARM_ATTEMPTS) {
                            ProjectAtmosphere.LOGGER.info(
                                    "T152_ROUTE retrying on a fresh fixture,"
                                            + " attempt {}/{} after: {}",
                                    t152Attempts + 1, T138_MAX_ARM_ATTEMPTS, latest);
                            advance(Phase.T135_RESPAWN);
                            return;
                        }
                        finish("t152_fixture_unstable:" + latest);
                        return;
                    }
                    finish(latest.startsWith("t152_aborted") ? latest : "t152_complete");
                } else if (stageFrames > STAGE_TIMEOUT_FRAMES * 4) {
                    finish("t152_timeout:" + StormT152MovingCameraFixture.latest());
                }
            }
            case T135_PREPARE -> {
                // The budget contract is stated at 1920x1080, so the sweep is
                // measured there rather than converted from another resolution.
                try {
                    com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(() ->
                            org.lwjgl.glfw.GLFW.glfwSetWindowSize(
                                    minecraft.getWindow().getWindow(),
                                    T135_WIDTH, T135_HEIGHT));
                } catch (Throwable throwable) {
                    ProjectAtmosphere.LOGGER.warn(
                            "T135_PROFILE window resize failed: {}", throwable.toString());
                }
                // The fixture pins the resolution scale to 0.75 so the capture
                // set is stationary. A budget contract per quality mode has to
                // measure each mode's OWN resolution scale, so the pin is
                // released for the sweep and reapplied before the captures.
                restoreFixtureResolutionControl();
                StormT135PerformanceProfile.reset();
                t135PoseIndex = 0;
                t135ModeIndex = 0;
                t138ResolutionRun = resolutionRunRequested();
                t138ScaleIndex = 0;
                t138HistoryArm = false;
                t153OracleRun = oracleRunRequested();
                t153OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t141EvaluationRun = evaluationRunRequested() || t153OracleRun;
                t141ArmIndex = 0;
                t141ArmAttempts = 0;
                t141CellPending = false;
                t153LastCounterInvalid = false;
                T153_COUNTERS.clear();
                T153_FIXTURES.clear();
                t153PoseAttempts = 0;
                if (t141EvaluationRun) {
                    if (!t153OracleRun) {
                        resolveT141Poses();
                    }
                    StormT135PerformanceProfile.setCellBudget(30, 60);
                    AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.set(
                            AtmoCommonConfig.CloudRaymarchQuality.ULTRA);
                    applyT141Arm();
                    ProjectAtmosphere.LOGGER.info(
                            "{}_BEGIN poses={} arms={} mode=ULTRA steps=96"
                                    + " resolutionScale={} target={}x{}",
                            t153OracleRun ? "T153_ORACLE" : "T141_EVAL",
                            sweepPoses().length, activeEvaluationArms().length,
                            fmt(T141_RESOLUTION_SCALE), T135_WIDTH, T135_HEIGHT);
                    if (t153OracleRun) {
                        ProjectAtmosphere.LOGGER.info(
                                "T153_ORACLE_CONTROL history=false priorHistory={}"
                                        + " restoredOnExit=true groundTruthGpuQuery=false",
                                t153OriginalHistoryEnabled);
                    }
                }
                if (t138ResolutionRun) {
                    resolveT138Poses();
                    // Forty cells against one live storm. The full 45/120
                    // protocol would outlive the fixture, and a fixture that
                    // decays mid-cell produces no measurement at all.
                    StormT135PerformanceProfile.setCellBudget(30, 60);
                    AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.set(
                            AtmoCommonConfig.CloudRaymarchQuality.ULTRA);
                    applyT138Arm();
                    ProjectAtmosphere.LOGGER.info(
                            "T138_RES_BEGIN poses={} scales={} mode=ULTRA steps=96"
                                    + " target={}x{} historyArmPoses={}",
                            T138_POSES.length, T138_SCALES.length,
                            T135_WIDTH, T135_HEIGHT, T138_HISTORY_ARM_POSES.length);
                } else if (!t141EvaluationRun) {
                    ProjectAtmosphere.LOGGER.info(
                            "T135_PROFILE_BEGIN poses={} modes={} target={}x{}",
                            T135_POSES.length, T135_MODES.length, T135_WIDTH, T135_HEIGHT);
                }
                advance(Phase.T135_MOVE);
            }
            case T135_MOVE -> {
                // Re-resolve before every pose. A respawn puts the new storm at
                // the player, so poses derived from the previous fixture would
                // aim at empty sky and the cell would silently measure nothing.
                if (StormGeometryBuildCoordinator.lobeCount() < 10) {
                    advance(Phase.T135_RESPAWN);
                    return;
                }
                VolumetricCloudFrameDiagnostics.beginStormPerformanceBaseline(
                        player.getX(), player.getY(), player.getZ());
                StormPerformanceBaseline.SuiteFixture fixture =
                        StormPerformanceBaseline.suiteFixture();
                if (fixture == null) {
                    finish("t135_fixture_lost");
                    return;
                }
                if (t153OracleRun) {
                    String pose = sweepPose();
                    T153PoseFixture current = new T153PoseFixture(
                            fixture.groupId(), fixture.structuralFingerprint());
                    T153PoseFixture expected = T153_FIXTURES.get(pose);
                    if (expected == null) {
                        T153_FIXTURES.put(pose, current);
                        ProjectAtmosphere.LOGGER.info(
                                "T153_POSE_FIXTURE pose={} group={} fingerprint={}",
                                pose, current.groupId(), current.structuralFingerprint());
                    } else if (!expected.equals(current)) {
                        resetT153Pose(pose, "fixture_identity_changed expected="
                                + expected.groupId() + "/" + expected.structuralFingerprint()
                                + " actual=" + current.groupId() + "/"
                                + current.structuralFingerprint());
                        if (++t153PoseAttempts >= T138_MAX_ARM_ATTEMPTS) {
                            finish("t153_pose_fixture_unstable:" + pose);
                            return;
                        }
                        advance(StormGeometryBuildCoordinator.lobeCount() >= 10
                                ? Phase.T135_MOVE : Phase.T135_RESPAWN);
                        return;
                    }
                }
                double radius = fixture.horizontalRadius();
                double midY = (fixture.baseY() + fixture.topY()) * 0.5D;
                double height = Math.max(1.0D, fixture.topY() - fixture.baseY());
                double x = fixture.centerX();
                double y = midY;
                double z = fixture.centerZ();
                switch (sweepPose()) {
                    // A - severe worst case.
                    case "SIDE" -> x = fixture.centerX() + radius * 1.7D;
                    case "FAR" -> x = fixture.centerX() + radius * 2.6D;
                    case "NEAR_EDGE" -> {
                        x = fixture.centerX() + radius * 1.12D;
                        y = fixture.baseY() + height * 0.55D;
                    }
                    case "BELOW" -> {
                        y = Math.max(fixture.baseY() - Math.max(90.0D, height * 0.35D), 70.0D);
                    }
                    case "ABOVE" -> {
                        x = fixture.centerX() + radius * 0.6D;
                        y = fixture.topY() + Math.max(120.0D, height * 0.45D);
                    }
                    // B - storm gameplay. A player is near the storm but at
                    // ordinary altitude and not aimed through its thickest
                    // chord, which is the case the budget actually has to hold.
                    case "PLAY_NEAR" -> {
                        x = fixture.centerX() + radius * 4.0D;
                        y = 120.0D;
                    }
                    case "PLAY_MID" -> {
                        x = fixture.centerX() + radius * 7.0D;
                        z = fixture.centerZ() + radius * 3.0D;
                        y = 100.0D;
                    }
                    // B' - storm gameplay that actually contains a storm. The
                    // shipped PLAY_* poses were written for a much smaller
                    // storm; at T134 scale their camera sits beyond the
                    // 2000-block cloud render distance and the frame is empty
                    // sky. These keep the gameplay altitude of y=120 and move
                    // the camera to a distance from which the storm is drawn.
                    case "PLAY_VIS_NEAR" -> {
                        x = fixture.centerX() + radius * 1.6D;
                        y = 120.0D;
                    }
                    case "PLAY_VIS_MID" -> {
                        x = fixture.centerX() + radius * 2.4D;
                        y = 120.0D;
                    }
                    case "PLAY_HIGH" -> {
                        x = fixture.centerX() + radius * 5.0D;
                        z = fixture.centerZ() - radius * 2.0D;
                        y = 320.0D;
                    }
                    default -> {
                        // D - clear-weather control: the storm is out of frame,
                        // so this is the non-cloud remainder with no storm cost.
                        x = fixture.centerX() + radius * 14.0D;
                        y = 120.0D;
                    }
                }
                float yaw = (float) (Math.toDegrees(Math.atan2(
                        fixture.centerZ() - z, fixture.centerX() - x)) - 90.0D);
                float pitch = 0.0F;
                if ("ABOVE".equals(sweepPose())
                        || "BELOW".equals(sweepPose())
                        || sweepPose().startsWith("PLAY")) {
                    double dy = midY - y;
                    double horizontal = Math.hypot(fixture.centerX() - x, fixture.centerZ() - z);
                    pitch = horizontal < 1.0D
                            ? (dy >= 0.0D ? -89.0F : 89.0F)
                            : (float) -Math.toDegrees(Math.atan2(dy, horizontal));
                }
                player.connection.sendCommand(String.format(Locale.ROOT,
                        "tp @s %.5f %.5f %.5f %.3f %.3f", x, y, z, yaw, pitch));
                t135SettleFrames = 0;
                advance(Phase.T135_SETTLE);
            }
            case T135_SETTLE -> {
                if (++t135SettleFrames >= T135_POSE_SETTLE_FRAMES) {
                    advance(Phase.T135_VERIFY);
                }
            }
            case T135_VERIFY -> verifyStormVisible(minecraft, player);
            case T135_SAMPLE -> {
                if (StormT135PerformanceProfile.active()) {
                    return;
                }
                if (t141EvaluationRun) {
                    tickEvaluationSweep();
                    return;
                }
                if (t138ResolutionRun) {
                    // The resolution sweep owns its own retry accounting: it
                    // retries the same arm, not the next one, and abandons an
                    // arm rather than looping when a pose cannot hold a fixture.
                    tickResolutionSweep();
                    return;
                }
                // A cell rejected for fixture decay is retried against a fresh
                // storm rather than reported. This is what makes the matrix
                // trustworthy: no cell reaches the record unless its descriptor
                // count held for every sample in it.
                if (StormT135PerformanceProfile.lastCellContaminated()
                        || StormGeometryBuildCoordinator.lobeCount() <= 0) {
                    if (++t135CellRetries > T135_MAX_CELL_RETRIES) {
                        ProjectAtmosphere.LOGGER.warn(
                                "T136_PROFILE abandoning {}/{} after {} contaminated attempts",
                                T135_POSES[t135PoseIndex],
                                T135_MODES[Math.max(0, t135ModeIndex - 1)], t135CellRetries);
                        t135CellRetries = 0;
                        // Fall through to the next mode rather than looping.
                    } else {
                        ProjectAtmosphere.LOGGER.info(
                                "T136_PROFILE respawning fixture before retrying {}/{}",
                                T135_POSES[t135PoseIndex],
                                T135_MODES[Math.max(0, t135ModeIndex - 1)]);
                        t135ModeIndex = Math.max(0, t135ModeIndex - 1);
                        advance(Phase.T135_RESPAWN);
                        return;
                    }
                }
                t135CellRetries = 0;
                if (t135ModeIndex >= T135_MODES.length) {
                    boolean attributionPose = "SIDE".equals(T135_POSES[t135PoseIndex])
                            || "PLAY_NEAR".equals(T135_POSES[t135PoseIndex]);
                    if (attributionPose && t135Arm < 2) {
                        t135Arm++;
                        t135ModeIndex = 0;
                        applyT135Arm();
                        ProjectAtmosphere.LOGGER.info(
                                "T136_PROFILE attribution arm {} begins at {}",
                                t135ArmName(), T135_POSES[t135PoseIndex]);
                        return;
                    }
                    t135Arm = 0;
                    applyT135Arm();
                    t135LightingArm = false;
                    t135ModeIndex = 0;
                    t135PoseIndex++;
                    if (t135PoseIndex >= T135_POSES.length) {
                        advance(Phase.T135_REPORT);
                    } else {
                        advance(Phase.T135_MOVE);
                    }
                    return;
                }
                AtmoCommonConfig.CloudRaymarchQuality quality = T135_MODES[t135ModeIndex];
                AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.set(quality);
                if (!StormT135PerformanceProfile.begin(T135_POSES[t135PoseIndex], quality,
                        t135ArmName())) {
                    // A refusal consumes a retry. Without this the sweep loops
                    // between refusing and respawning forever, which is exactly
                    // how PLAY_MID/Ultra, PLAY_HIGH and CLEAR were lost.
                    if (++t135CellRetries > T135_MAX_CELL_RETRIES) {
                        ProjectAtmosphere.LOGGER.warn(
                                "T136_PROFILE abandoning {}/{}: fixture never became"
                                        + " measurable after {} attempts",
                                T135_POSES[t135PoseIndex], quality, t135CellRetries);
                        t135CellRetries = 0;
                        t135ModeIndex++;
                        return;
                    }
                    advance(Phase.T135_RESPAWN);
                    return;
                }
                t135CounterLabel = sweepPose() + "|" + t135ArmName()
                        + "|" + quality.name();
                t135ModeIndex++;
                t135CountersRequested = false;
                advance(Phase.T135_COUNTERS);
            }
            case T135_COUNTERS -> {
                // The timing sample must finish before the two counter frames
                // run, because those frames render a diagnostic view and would
                // otherwise pollute the timing they belong to.
                if (StormT135PerformanceProfile.active()) {
                    return;
                }
                if (!t135CountersRequested) {
                    t135CountersRequested = true;
                    VolumetricCloudFrameDiagnostics.requestStormWorkloadCapture("side");
                    // The timing sample runs inside this phase, so stageFrames
                    // has already counted every tick of it. At the T141 arms'
                    // one to two frames per second that is well past the
                    // timeout before the capture has rendered a single frame,
                    // which expired every capture and then left the stale
                    // request active across the next arms. Restart the clock
                    // where the capture actually begins.
                    advance(Phase.T135_COUNTERS);
                    return;
                }
                if (VolumetricCloudFrameDiagnostics.stormWorkloadActive()) {
                    if (stageFrames > COUNTER_TIMEOUT_FRAMES) {
                        ProjectAtmosphere.LOGGER.warn(
                                "T136_COUNTERS timed out for {}", t135CounterLabel);
                        // A capture left active would be resumed by the next
                        // cell and complete from frames of several different
                        // arms. Abandon it here instead.
                        VolumetricCloudFrameDiagnostics.abortStormWorkloadCapture();
                        advance(Phase.T135_SAMPLE);
                    }
                    return;
                }
                String line = VolumetricCloudFrameDiagnostics.stormWorkloadResultLine();
                if (line != null) {
                    ProjectAtmosphere.LOGGER.info("T136_COUNTERS cell={} {}",
                            t135CounterLabel, line);
                }
                if (t153OracleRun) {
                    StormWorkloadRuntimeCapture.WorkloadResult workload =
                            StormWorkloadRuntimeCapture.latestResult();
                    com.mojang.blaze3d.pipeline.RenderTarget target =
                            VolumetricCloudRenderTargets.currentCloudTarget();
                    int pixels = target == null ? 0 : target.width * target.height;
                    boolean visible = workload != null
                            && StormFixtureVisibility.renderedStormConfirmed(
                                    workload.cloudDensityCalls(), pixels);
                    boolean exact = workload != null && workload.oracleOverflowPixels() == 0.0D;
                    t153LastCounterInvalid = !visible || !exact;
                    if (t153LastCounterInvalid) {
                        String reason = !visible ? "t150_rendered_no_storm" : "oracle_interval_overflow";
                        StormT135PerformanceProfile.discardLastCell(reason);
                        ProjectAtmosphere.LOGGER.warn(
                                "T153_ORACLE_REJECT cell={} reason={} densityCalls={} pixels={}"
                                        + " overflowPixels={}",
                                t135CounterLabel, reason,
                                workload == null ? "n/a" : fmt(workload.cloudDensityCalls()),
                                pixels,
                                workload == null ? "n/a" : fmt(workload.oracleOverflowPixels()));
                    } else {
                        T153_COUNTERS.add(new T153CounterCell(
                                sweepPose(), t141ArmName(), workload));
                        ProjectAtmosphere.LOGGER.info(
                                "T150_VISIBILITY_CONFIRMED cell={} cloudDensityCalls={} perPixel={}",
                                t135CounterLabel, fmt(workload.cloudDensityCalls()),
                                fmt(workload.cloudDensityCalls() / Math.max(1, pixels)));
                    }
                }
                advance(Phase.T135_SAMPLE);
            }
            case T135_RESPAWN -> {
                // Deterministic re-adoption: spawn, wait for descriptors, then
                // resume the sweep at the cell that was rejected.
                if (StormGeometryBuildCoordinator.lobeCount() >= 10) {
                    if (stageFrames > 200) {
                        ProjectAtmosphere.LOGGER.info(
                                "T136_PROFILE fixture re-adopted with {} descriptors;"
                                        + " poses will be recomputed from it",
                                StormGeometryBuildCoordinator.lobeCount());
                        advance(t152Run ? Phase.T152_BEGIN : Phase.T135_MOVE);
                    }
                    return;
                }
                if (stageFrames == 1 || stageFrames % 1200 == 0) {
                    player.connection.sendCommand("pa cloud spawn cumulonimbus_capillatus");
                }
                if (stageFrames > INFRA_TIMEOUT_FRAMES) {
                    finish("t136_respawn_timeout");
                }
            }
            case T135_REPORT -> {
                String completionLabel = t141EvaluationRun
                        ? (t153OracleRun
                            ? "T153_ORACLE_COMPLETE cells="
                            : "T141_EVAL_COMPLETE cells=")
                        : (t138ResolutionRun
                            ? "T138_RES_COMPLETE cells="
                            : "T135_PROFILE_COMPLETE cells=");
                StringBuilder out = new StringBuilder(
                        completionLabel + StormT135PerformanceProfile.results().size());
                for (StormT135PerformanceProfile.Cell cell
                        : StormT135PerformanceProfile.results()) {
                    out.append(String.format(Locale.ROOT,
                            "%n%s|%s|%s|%d|%s|%d|%.3f|%dx%d|%dx%d|%d|%d"
                                    + "|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f",
                            t153OracleRun ? "T153_CELL"
                                    : (t138ResolutionRun ? "T138_CELL" : "T135_CELL"),
                            cell.pose(), cell.arm(), cell.descriptors(),
                            cell.mode(), cell.raymarchSteps(),
                            cell.effectiveResolutionScale(),
                            cell.frameWidth(), cell.frameHeight(),
                            cell.cloudWidth(), cell.cloudHeight(),
                            cell.cloudWidth() * cell.cloudHeight(),
                            cell.samples(), cell.cloudP50(), cell.cloudP95(),
                            cell.frameP50(), cell.frameP95(), cell.remainderP50(),
                            cell.compositeP50(), cell.compositeP95()));
                }
                ProjectAtmosphere.LOGGER.info(out.toString());
                // Restore the acceptance configuration for the capture set.
                AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.set(
                        AtmoCommonConfig.CloudRaymarchQuality.ULTRA);
                if (t138ResolutionRun || t141EvaluationRun) {
                    // Hand the capture set an unmodified adaptive scale so
                    // applyFixtureResolutionControl records the real prior value
                    // rather than the last arm under test.
                    VolumetricCloudDebugConfig.setHistoryEnabled(true);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(
                            StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
                    StormT135PerformanceProfile.setCellBudget(45, 120);
                }
                if (t153OracleRun) {
                    ProjectAtmosphere.LOGGER.info(buildT153DecisionReport());
                    VolumetricCloudDebugConfig.setHistoryEnabled(t153OriginalHistoryEnabled);
                    finish("t153_complete");
                } else {
                    applyFixtureResolutionControl();
                    advance(Phase.BEGIN_T098);
                }
            }
            case BEGIN_T098 -> {
                String begun = StormT098CaptureDriver.begin();
                if (begun.startsWith("t098_captures_started")) {
                    ProjectAtmosphere.LOGGER.info("T132_AUTORUN t098 captures begun: {}", begun);
                    advance(Phase.POLL_T098);
                } else {
                    // Disabled or no fixture is not a failure of the run that
                    // just completed; finish exactly as before.
                    ProjectAtmosphere.LOGGER.info("T132_AUTORUN t098 captures skipped: {}", begun);
                    finish("complete");
                }
            }
            case POLL_T098 -> {
                StormT098CaptureDriver.tick();
                if (StormT098CaptureDriver.finished()) {
                    finish("complete");
                } else if (stageFrames > STAGE_TIMEOUT_FRAMES * 4) {
                    finish("t098_capture_timeout");
                }
            }
            default -> {
            }
        }
    }

    /**
     * One step of the T138 (pose x internal resolution) sweep.
     *
     * <p>The quality mode is pinned to ULTRA for every cell, so the step
     * budget, light-cone taps, detail quality and weather-map size are constant
     * and the only variable is the cloud render target's dimensions. Poses that
     * carry the history arm run each scale twice - once with the temporal blend
     * and once without - which measures the blend's own cost per resolution
     * rather than inferring it.
     */
    private static void tickResolutionSweep() {
        String pose = sweepPose();
        // Resolve the cell that just ran before starting another. A cell that
        // decayed is retried on the SAME arm against a fresh fixture; only an
        // arm that has exhausted its attempts is skipped, and the arm index
        // never advances on a cell that was not recorded.
        if (t138CellPending) {
            t138CellPending = false;
            boolean failed = StormT135PerformanceProfile.lastCellContaminated()
                    || StormGeometryBuildCoordinator.lobeCount() <= 0;
            if (failed) {
                if (++t138ArmAttempts < T138_MAX_ARM_ATTEMPTS) {
                    ProjectAtmosphere.LOGGER.info(
                            "T138_RES respawning before retrying {}/{} (attempt {})",
                            pose, t138ArmName(), t138ArmAttempts + 1);
                    advance(Phase.T135_RESPAWN);
                    return;
                }
                ProjectAtmosphere.LOGGER.warn(
                        "T138_RES {}/{} unmeasurable after {} attempts; arm skipped",
                        pose, t138ArmName(), t138ArmAttempts);
            }
            t138ArmAttempts = 0;
            advanceResolutionArm(pose);
        }
        if (t138ScaleIndex >= T138_SCALES.length) {
            t138ScaleIndex = 0;
            t138HistoryArm = false;
            t138ArmAttempts = 0;
            applyT138Arm();
            t135PoseIndex++;
            if (t135PoseIndex >= T138_POSES.length) {
                advance(Phase.T135_REPORT);
            } else {
                advance(Phase.T135_MOVE);
            }
            return;
        }
        applyT138Arm();
        String arm = t138ArmName();
        if (!StormT135PerformanceProfile.begin(
                pose, AtmoCommonConfig.CloudRaymarchQuality.ULTRA, arm)) {
            if (++t138ArmAttempts < T138_MAX_ARM_ATTEMPTS) {
                advance(Phase.T135_RESPAWN);
                return;
            }
            ProjectAtmosphere.LOGGER.warn(
                    "T138_RES {}/{} never became measurable after {} attempts;"
                            + " arm skipped", pose, arm, t138ArmAttempts);
            t138ArmAttempts = 0;
            advanceResolutionArm(pose);
            return;
        }
        t138CellPending = true;
        t135CounterLabel = pose + "|" + arm + "|ULTRA";
        t135CountersRequested = false;
        advance(Phase.T135_COUNTERS);
    }

    /**
     * One step of the T141 (pose x descriptor-evaluation arm) sweep. Resolution
     * and quality mode are pinned for every cell, so the only variable between
     * arms is how much descriptor evaluation work the shader performs.
     */
    private static void tickEvaluationSweep() {
        String pose = sweepPose();
        if (t141CellPending) {
            t141CellPending = false;
            boolean failed = StormT135PerformanceProfile.lastCellContaminated()
                    || (t153OracleRun && t153LastCounterInvalid)
                    || (!pose.startsWith("CLEAR")
                        && StormGeometryBuildCoordinator.lobeCount() <= 0);
            t153LastCounterInvalid = false;
            if (failed) {
                if (t153OracleRun) {
                    resetT153Pose(pose, "cell_invalid_or_fixture_decayed");
                    if (++t153PoseAttempts >= T138_MAX_ARM_ATTEMPTS) {
                        finish("t153_pose_unmeasurable:" + pose);
                        return;
                    }
                    advance(StormGeometryBuildCoordinator.lobeCount() >= 10
                            ? Phase.T135_MOVE : Phase.T135_RESPAWN);
                    return;
                }
                if (++t141ArmAttempts < T138_MAX_ARM_ATTEMPTS) {
                    advance(Phase.T135_RESPAWN);
                    return;
                }
                ProjectAtmosphere.LOGGER.warn(
                        "{} {}/{} unmeasurable after {} attempts; arm skipped",
                        t153OracleRun ? "T153_ORACLE" : "T141_EVAL",
                        pose, t141ArmName(), t141ArmAttempts);
            }
            t141ArmAttempts = 0;
            t141ArmIndex++;
        }
        if (t141ArmIndex >= activeEvaluationArms().length) {
            t141ArmIndex = 0;
            t141ArmAttempts = 0;
            t153PoseAttempts = 0;
            applyT141Arm();
            t135PoseIndex++;
            if (t135PoseIndex >= sweepPoses().length) {
                advance(Phase.T135_REPORT);
            } else {
                advance(Phase.T135_MOVE);
            }
            return;
        }
        applyT141Arm();
        String arm = t141ArmName();
        if (!StormT135PerformanceProfile.begin(
                pose, AtmoCommonConfig.CloudRaymarchQuality.ULTRA, arm)) {
            if (++t141ArmAttempts < T138_MAX_ARM_ATTEMPTS) {
                advance(Phase.T135_RESPAWN);
                return;
            }
            ProjectAtmosphere.LOGGER.warn(
                    "{} {}/{} never became measurable after {} attempts; arm skipped",
                    t153OracleRun ? "T153_ORACLE" : "T141_EVAL",
                    pose, arm, t141ArmAttempts);
            t141ArmAttempts = 0;
            t141ArmIndex++;
            return;
        }
        t141CellPending = true;
        t135CounterLabel = pose + "|" + arm + "|ULTRA";
        t135CountersRequested = false;
        advance(Phase.T135_COUNTERS);
    }

    /**
     * Invalidates an entire five-arm T153 pose comparison. Retaining the
     * earlier arms after a severe group expires would compare two different
     * density fixtures and manufacture an oracle speedup or regression.
     */
    private static void resetT153Pose(String pose, String reason) {
        StormT135PerformanceProfile.discardPose(pose, reason);
        T153_COUNTERS.removeIf(cell -> pose.equals(cell.pose()));
        T153_FIXTURES.remove(pose);
        t141ArmIndex = 0;
        t141ArmAttempts = 0;
        t141CellPending = false;
        t153LastCounterInvalid = false;
        applyT141Arm();
        ProjectAtmosphere.LOGGER.warn(
                "T153_ORACLE restarting complete pose={} reason={}", pose, reason);
    }

    /** Builds the compact, self-contained T153 ceiling and attribution report. */
    private static String buildT153DecisionReport() {
        StringBuilder out = new StringBuilder("T153_ORACLE_DECISION");
        boolean complete = true;
        for (String pose : T153_POSES) {
            StormT135PerformanceProfile.Cell baseline = t153Cell(
                    pose, StormOptimizationDiagnosticMode.NORMAL_PRODUCTION.serializedName());
            StormT135PerformanceProfile.Cell combined = t153Cell(
                    pose, StormOptimizationDiagnosticMode.T153_COMBINED.serializedName());
            if (baseline == null || combined == null) {
                complete = false;
            }
            StormWorkloadRuntimeCapture.WorkloadResult baselineWork = t153Workload(
                    pose, StormOptimizationDiagnosticMode.NORMAL_PRODUCTION.serializedName());
            T153PoseFixture poseFixture = T153_FIXTURES.get(pose);
            out.append(System.lineSeparator()).append("T153_POSE pose=").append(pose)
                    .append(" fixtureGroup=")
                    .append(poseFixture == null ? "missing" : poseFixture.groupId())
                    .append(" fixtureFingerprint=")
                    .append(poseFixture == null ? "missing" : poseFixture.structuralFingerprint());
            for (StormOptimizationDiagnosticMode arm : T153_ARMS) {
                StormT135PerformanceProfile.Cell cell = t153Cell(pose, arm.serializedName());
                StormWorkloadRuntimeCapture.WorkloadResult work =
                        t153Workload(pose, arm.serializedName());
                if (cell == null || work == null) {
                    complete = false;
                    out.append(" arm[").append(arm.serializedName()).append("=missing]");
                    continue;
                }
                long pixels = Math.max(1L, (long) work.width() * work.height());
                double speedup = baseline == null
                        ? Double.NaN : baseline.cloudP50() / cell.cloudP50();
                double removedSteps = baselineWork == null
                        ? Double.NaN
                        : Math.max(0.0D,
                                baselineWork.primaryRaySteps() - work.primaryRaySteps());
                out.append(String.format(Locale.ROOT,
                        " arm[%s p50=%.4f p95=%.4f speedup=%.3fx"
                                + " stepsPerPixel=%.4f density=%1.0f descriptor=%1.0f"
                                + " textureFetches=%1.0f light=%1.0f detail=%1.0f"
                                + " emptyStepsRemoved=%1.0f skippedDistancePerPixel=%.4f"
                                + " preCloudPerPixel=%.4f holesPerPixel=%.4f"
                                + " postCloudPerPixel=%.4f postOpacityPerPixel=%.4f"
                                + " intervals=%1.0f overflowPixels=%1.0f]",
                        arm.serializedName(), cell.cloudP50(), cell.cloudP95(), speedup,
                        work.primaryRaySteps() / pixels,
                        work.cloudDensityCalls(), work.descriptorEvaluations(),
                        work.descriptorTextureFetches(), work.lightMarchDensityEvaluations(),
                        work.detailOctaveEvaluations(), removedSteps,
                        work.oracleSkippedDistance() / pixels,
                        work.oraclePreCloudDistance() / pixels,
                        work.oracleHoleDistance() / pixels,
                        work.oraclePostCloudDistance() / pixels,
                        work.oraclePostOpacityDistance() / pixels,
                        work.oracleIntervalsSeen(), work.oracleOverflowPixels()));
            }
            StormWorkloadRuntimeCapture.WorkloadResult productionWork = t153Workload(
                    pose, StormOptimizationDiagnosticMode.NORMAL_PRODUCTION.serializedName());
            if (productionWork != null) {
                out.append(" productionWorkAfterOpacity[steps=")
                        .append(productionWork.stepsAfterAlpha().format())
                        .append(" density=").append(productionWork.densityAfterAlpha().format())
                        .append(" descriptor=").append(productionWork.descriptorAfterAlpha().format())
                        .append(" light=").append(productionWork.lightAfterAlpha().format())
                        .append(" detail=").append(productionWork.detailAfterAlpha().format())
                        .append(']');
            }
        }

        StormT135PerformanceProfile.Cell representativeBaseline = t153Cell(
                "PLAY_VIS_NEAR",
                StormOptimizationDiagnosticMode.NORMAL_PRODUCTION.serializedName());
        StormT135PerformanceProfile.Cell representativeCombined = t153Cell(
                "PLAY_VIS_NEAR", StormOptimizationDiagnosticMode.T153_COMBINED.serializedName());
        double representativeSpeedup = representativeBaseline == null
                || representativeCombined == null
                ? Double.NaN
                : representativeBaseline.cloudP50() / representativeCombined.cloudP50();

        double preCloud = 0.0D;
        double holes = 0.0D;
        double postCloud = 0.0D;
        double postOpacity = 0.0D;
        for (String pose : T153_POSES) {
            StormWorkloadRuntimeCapture.WorkloadResult work = t153Workload(
                    pose, StormOptimizationDiagnosticMode.T153_COMBINED.serializedName());
            if (work != null) {
                preCloud += work.oraclePreCloudDistance();
                holes += work.oracleHoleDistance();
                postCloud += work.oraclePostCloudDistance();
                postOpacity += work.oraclePostOpacityDistance();
            }
        }
        String dominant = "none";
        double largest = 0.0D;
        if (preCloud > largest) { largest = preCloud; dominant = "pre_cloud_empty_distance"; }
        if (holes > largest) { largest = holes; dominant = "holes_between_occupied_intervals"; }
        if (postOpacity > largest) { largest = postOpacity; dominant = "post_opacity_work"; }
        if (postCloud > largest) { dominant = "post_cloud_empty_distance"; }

        String gate;
        if (!complete || !Double.isFinite(representativeSpeedup)) {
            gate = "INCONCLUSIVE_MISSING_OR_INVALID_CELLS";
        } else if (representativeSpeedup < 2.0D) {
            gate = "REJECT_PRIMARY_ARCHITECTURE_STOP_BEFORE_T154";
        } else if (representativeSpeedup < 3.0D) {
            gate = "PROCEED_T154";
        } else if (representativeSpeedup < 4.0D) {
            gate = "STRONG_ARCHITECTURE_CANDIDATE_PROCEED_T154";
        } else {
            gate = "VERY_STRONG_ARCHITECTURE_CANDIDATE_PROCEED_T154";
        }
        out.append(String.format(Locale.ROOT,
                "%nT153_GATE complete=%s representativePose=PLAY_VIS_NEAR"
                        + " productionP50=%.4f combinedP50=%.4f speedup=%.3fx"
                        + " threshold=2.000x decision=%s dominantSavings=%s"
                        + " sourceDistance[preCloud=%.0f,holes=%.0f,postCloud=%.0f,postOpacity=%.0f]"
                        + " note=oracle_map_construction_excluded_from_gpu_query",
                complete, representativeBaseline == null ? Double.NaN : representativeBaseline.cloudP50(),
                representativeCombined == null ? Double.NaN : representativeCombined.cloudP50(),
                representativeSpeedup, gate, dominant,
                preCloud, holes, postCloud, postOpacity));
        return out.toString();
    }

    private static StormT135PerformanceProfile.Cell t153Cell(String pose, String arm) {
        for (StormT135PerformanceProfile.Cell cell : StormT135PerformanceProfile.results()) {
            if (pose.equals(cell.pose()) && arm.equals(cell.arm())) {
                return cell;
            }
        }
        return null;
    }

    private static StormWorkloadRuntimeCapture.WorkloadResult t153Workload(
            String pose, String arm) {
        for (T153CounterCell cell : T153_COUNTERS) {
            if (pose.equals(cell.pose()) && arm.equals(cell.arm())) {
                return cell.workload();
            }
        }
        return null;
    }

    /** Moves to the next history arm, or to the next resolution. */
    private static void advanceResolutionArm(String pose) {
        if (t138HistoryArmPose(pose) && !t138HistoryArm) {
            t138HistoryArm = true;
            return;
        }
        t138HistoryArm = false;
        t138ScaleIndex++;
    }

    /**
     * The world seed for the automated run.
     *
     * <p>Defaults to the long-standing fixed seed, so T132 and T133 acceptance
     * runs reproduce exactly as before. The T098 live campaign needs fresh
     * severe fixtures rather than repeated draws from one world, so a seed may
     * be supplied as the first line of the autorun marker. Anything
     * unparseable falls back to the fixed seed rather than silently
     * randomising an acceptance run.
     */
    private static long autorunWorldSeed() {
        try {
            for (String line : Files.readAllLines(MARKER)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                long seed = Long.parseLong(trimmed);
                ProjectAtmosphere.LOGGER.info(
                        "T132_AUTORUN_INFRA world seed overridden by marker: {}", seed);
                return seed;
            }
        } catch (Exception exception) {
            ProjectAtmosphere.LOGGER.info(
                    "T132_AUTORUN_INFRA marker carries no usable seed ({}); using the fixed seed",
                    exception.getClass().getSimpleName());
        }
        return 0x544132L;
    }

    /**
     * Creates a level through Minecraft's normal {@code WorldOpenFlows}; no UI
     * automation or copied old-version template is involved.  The marker keeps
     * this path test-only and the two owned save names prevent accidental user
     * save modification.
     */
    private static void bootstrapPrepare(Minecraft minecraft) {
        try {
            if (minecraft.level != null) {
                ProjectAtmosphere.LOGGER.info("{} replacing pre-existing level through normal WorldOpenFlows", INFRA_LOG_MARKER);
            }
            StormT132WorldFixture.clearOwnedWorlds(minecraft);
            ProjectAtmosphere.LOGGER.info("{} source={} restored={} template={}", INFRA_LOG_MARKER,
                    StormT132WorldFixture.SOURCE_WORLD_ID, StormT132WorldFixture.RESTORED_WORLD_ID,
                    StormT132WorldFixture.TEMPLATE_ROOT.toAbsolutePath());
            LevelSettings settings = new LevelSettings(
                    "PA T132 Automated Source",
                    GameType.SPECTATOR,
                    false,
                    Difficulty.PEACEFUL,
                    true,
                    new GameRules(),
                    WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(autorunWorldSeed(), true, false);
            minecraft.createWorldOpenFlows().createFreshLevel(
                    StormT132WorldFixture.SOURCE_WORLD_ID,
                    settings,
                    options,
                    WorldPresets::createNormalWorldDimensions);
            ProjectAtmosphere.LOGGER.info("T132_AUTORUN_INFRA create_requested world={}",
                    StormT132WorldFixture.SOURCE_WORLD_ID);
            advance(Phase.BOOTSTRAP_WAIT_SOURCE);
        } catch (Throwable throwable) {
            finishInfrastructureInvalid("create_request_failed:" + throwable.getClass().getSimpleName());
        }
    }

    private static void bootstrapWaitSource(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player != null && player.connection != null
                && Files.exists(StormT132WorldFixture.worldPath(minecraft, StormT132WorldFixture.SOURCE_WORLD_ID))) {
            ProjectAtmosphere.LOGGER.info("T132_AUTORUN_INFRA source_entered world={} player={}, {}, {}",
                    StormT132WorldFixture.SOURCE_WORLD_ID, fmt(player.getX()), fmt(player.getY()), fmt(player.getZ()));
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server == null) {
                finishInfrastructureInvalid("source_integrated_server_missing");
                return;
            }
            // Minecraft.clearLevel waits for shutdown but does not initiate it.
            // Halting first avoids both a nested-tick deadlock and packets from
            // the old server arriving while the restored registry is loading.
            server.halt(false);
            ProjectAtmosphere.LOGGER.info("T132_AUTORUN_INFRA source_halt_requested");
            advance(Phase.BOOTSTRAP_UNLOAD_SOURCE);
        } else if (stageFrames > INFRA_TIMEOUT_FRAMES) {
            finishInfrastructureInvalid("source_world_entry_timeout");
        }
    }

    private static void bootstrapUnloadAndRestore(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null && !server.isShutdown()) {
            if (stageFrames > INFRA_TIMEOUT_FRAMES) {
                finishInfrastructureInvalid("source_server_halt_timeout");
            }
            return;
        }
        try {
            // clearLevel performs one nested client tick while it swaps the
            // screen. Advance first so that nested tick observes RESTORE and
            // cannot recursively call clearLevel again.
            advance(Phase.BOOTSTRAP_RESTORE);
            if (minecraft.level != null || minecraft.player != null) {
                minecraft.clearLevel(new TitleScreen());
            }
            ProjectAtmosphere.LOGGER.info("T132_AUTORUN_INFRA source_cleanly_unloaded");
        } catch (Throwable throwable) {
            finishInfrastructureInvalid("source_clear_failed:" + throwable.getClass().getSimpleName());
        }
    }

    private static void bootstrapRestore(Minecraft minecraft) {
        if (minecraft.level != null || minecraft.player != null) {
            if (stageFrames > INFRA_TIMEOUT_FRAMES) {
                finishInfrastructureInvalid("source_clear_timeout");
            }
            return;
        }
        try {
            StormT132WorldFixture.Validation snapshot = StormT132WorldFixture.snapshot(
                    StormT132WorldFixture.worldPath(minecraft, StormT132WorldFixture.SOURCE_WORLD_ID));
            if (!snapshot.valid()) {
                finishInfrastructureInvalid(snapshot.operation() + ':' + snapshot.reason());
                return;
            }
            StormT132WorldFixture.Validation restored = StormT132WorldFixture.restore(minecraft);
            if (!restored.valid()) {
                finishInfrastructureInvalid(restored.operation() + ':' + restored.reason());
                return;
            }
            minecraft.createWorldOpenFlows().loadLevel(new TitleScreen(), StormT132WorldFixture.RESTORED_WORLD_ID);
            ProjectAtmosphere.LOGGER.info("T132_AUTORUN_INFRA restore_load_requested world={} snapshotDataVersion={}",
                    StormT132WorldFixture.RESTORED_WORLD_ID, snapshot.dataVersion());
            advance(Phase.BOOTSTRAP_WAIT_RESTORED);
        } catch (Throwable throwable) {
            finishInfrastructureInvalid("restore_load_failed:" + throwable.getClass().getSimpleName());
        }
    }

    private static void bootstrapWaitRestored(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player != null && player.connection != null) {
            if (!noUnidentifiedMappingWarningSinceBootstrap()) {
                finishInfrastructureInvalid("unidentified_mapping_warning");
                return;
            }
            ProjectAtmosphere.LOGGER.info(
                    "T132_AUTORUN_INFRASTRUCTURE_VALID source={} restored={} restoredEntry={}, {}, {} autoQuickPlay=true",
                    StormT132WorldFixture.SOURCE_WORLD_ID, StormT132WorldFixture.RESTORED_WORLD_ID,
                    fmt(player.getX()), fmt(player.getY()), fmt(player.getZ()));
            advance(Phase.WAIT_WORLD);
        } else if (!restoreConfirmationAccepted && minecraft.screen instanceof ConfirmScreen confirmation) {
            // A current Tectonic/Lithostitched registry reports an experimental
            // worldgen lifecycle. This is the normal confirmation callback for
            // the freshly-created test world, not a recoverable-save warning.
            // Invoke its first (proceed) Button directly rather than injecting
            // keys or depending on focus/coordinates.
            for (var child : confirmation.children()) {
                if (child instanceof Button button) {
                    restoreConfirmationAccepted = true;
                    ProjectAtmosphere.LOGGER.info(
                            "T132_AUTORUN_INFRA accepting normal current-registry confirmation screen={}",
                            confirmation.getClass().getName());
                    button.onPress();
                    return;
                }
            }
            finishInfrastructureInvalid("confirmation_without_proceed_button");
        } else if (stageFrames % 120 == 0) {
            ProjectAtmosphere.LOGGER.info(
                    "T132_AUTORUN_INFRA restore_wait frame={} screen={} levelPresent={} serverPresent={}",
                    stageFrames,
                    minecraft.screen == null ? "none" : minecraft.screen.getClass().getName(),
                    minecraft.level != null,
                    minecraft.getSingleplayerServer() != null);
        } else if (stageFrames > INFRA_TIMEOUT_FRAMES) {
            finishInfrastructureInvalid("restored_world_entry_timeout");
        }
    }

    private static boolean noUnidentifiedMappingWarningSinceBootstrap() {
        try {
            if (!Files.isRegularFile(LATEST_LOG)) {
                return false;
            }
            String text = Files.readString(LATEST_LOG);
            int marker = text.lastIndexOf(INFRA_LOG_MARKER);
            if (marker < 0) {
                return false;
            }
            return !text.substring(marker).toLowerCase(Locale.ROOT).contains("unidentified mapping");
        } catch (IOException exception) {
            return false;
        }
    }

    private static void advance(Phase next) {
        phase = next;
        stageFrames = 0;
    }

    private static void finish(String outcome) {
        if (t153OracleRun) {
            VolumetricCloudDebugConfig.setHistoryEnabled(t153OriginalHistoryEnabled);
        }
        restoreFixtureMotionFreeze();
        restoreFixtureDaylightFreeze();
        restoreFixtureResolutionControl();
        restoreFixtureQualityControl();
        phase = Phase.DONE;
        ProjectAtmosphere.LOGGER.info("T132_AUTORUN_FINISHED outcome={} frames={}\n{}",
                outcome, frames, suiteReport.isEmpty() ? "(no suite report)" : suiteReport);
    }

    private static void finishInfrastructureInvalid(String reason) {
        suiteReport = "INFRASTRUCTURE_INVALID reason=" + reason
                + " excludedFromRendererEvidence=true";
        ProjectAtmosphere.LOGGER.error("T132_AUTORUN_INFRASTRUCTURE_INVALID reason={} "
                        + "excludedFrom=A_A,T119,fixtureStability,rendererConclusions",
                reason);
        finish("infrastructure_invalid:" + reason);
    }

    /**
     * Stops the sun. {@code lightDirection} is derived from
     * {@code level.getTimeOfDay(...)}, so with the daylight cycle running it
     * moves between the samples of one group and the group-content gate
     * correctly rejects the group. Every abort of the 2026-08-27 run was this
     * one signature. The world is generated fresh per run, so the prior state is
     * the vanilla default and is restored on exit regardless.
     */
    private static void applyFixtureDaylightFreeze(LocalPlayer player) {
        if (daylightFreezeApplied) {
            return;
        }
        try {
            // Normal command path, so the integrated server and client agree.
            player.connection.sendCommand("gamerule doDaylightCycle false");
            player.connection.sendCommand("time set " + FIXTURE_DAY_TIME);
            daylightFreezeApplied = true;
            ProjectAtmosphere.LOGGER.info(
                    "T132_AUTORUN_FIXTURE_CONTROL daylightCycleFrozen=true dayTime={} restoredOnExit=true",
                    FIXTURE_DAY_TIME);
        } catch (Throwable throwable) {
            finishInfrastructureInvalid(
                    "fixture_daylight_freeze_failed:" + throwable.getClass().getSimpleName());
        }
    }

    private static void restoreFixtureDaylightFreeze() {
        if (!daylightFreezeApplied) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null && player.connection != null) {
            player.connection.sendCommand("gamerule doDaylightCycle true");
            ProjectAtmosphere.LOGGER.info(
                    "T132_AUTORUN_FIXTURE_CONTROL daylightCycleRestored=true");
        } else {
            ProjectAtmosphere.LOGGER.warn(
                    "T132_AUTORUN_FIXTURE_CONTROL daylightCycleRestoreDeferred no_player_connection");
        }
        daylightFreezeApplied = false;
    }

    private static void applyFixtureMotionFreeze(LocalPlayer player) {
        if (movementFreezeApplied) {
            return;
        }
        try {
            originalMovementFreeze = AtmoCommonConfig.FREEZE_CLOUD_MOVEMENT.get();
            if (!originalMovementFreeze) {
                // Use the established normal command path so the integrated
                // server and client see exactly the same diagnostic setting.
                player.connection.sendCommand("pa cloud freeze true");
            }
            movementFreezeApplied = true;
            ProjectAtmosphere.LOGGER.info(
                    "T132_AUTORUN_FIXTURE_CONTROL cloudMovementFrozen=true priorValue={} restoredOnExit=true",
                    originalMovementFreeze);
        } catch (Throwable throwable) {
            finishInfrastructureInvalid("fixture_motion_freeze_failed:" + throwable.getClass().getSimpleName());
        }
    }

    private static void restoreFixtureMotionFreeze() {
        if (!movementFreezeApplied || originalMovementFreeze == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null && player.connection != null) {
            player.connection.sendCommand("pa cloud freeze " + originalMovementFreeze);
            ProjectAtmosphere.LOGGER.info(
                    "T132_AUTORUN_FIXTURE_CONTROL cloudMovementFreezeRestored={}", originalMovementFreeze);
        } else {
            ProjectAtmosphere.LOGGER.warn(
                    "T132_AUTORUN_FIXTURE_CONTROL cloudMovementFreezeRestoreDeferred={} no_player_connection",
                    originalMovementFreeze);
        }
        movementFreezeApplied = false;
    }

    private static void applyFixtureResolutionControl() {
        if (fixedResolutionApplied) {
            return;
        }
        originalFixedResolutionScale = VolumetricCloudDebugConfig.fixedResolutionScale();
        VolumetricCloudDebugConfig.setFixedResolutionScale(0.75F);
        fixedResolutionApplied = true;
        ProjectAtmosphere.LOGGER.info(
                "T132_AUTORUN_FIXTURE_CONTROL fixedResolutionScale=0.75000 priorValue={} restoredOnExit=true",
                Float.isFinite(originalFixedResolutionScale) ? fmt(originalFixedResolutionScale) : "adaptive");
    }

    private static void restoreFixtureResolutionControl() {
        if (!fixedResolutionApplied) {
            return;
        }
        VolumetricCloudDebugConfig.setFixedResolutionScale(originalFixedResolutionScale);
        ProjectAtmosphere.LOGGER.info(
                "T132_AUTORUN_FIXTURE_CONTROL fixedResolutionScaleRestored={}",
                Float.isFinite(originalFixedResolutionScale) ? fmt(originalFixedResolutionScale) : "adaptive");
        fixedResolutionApplied = false;
    }

    private static void applyFixtureQualityControl() {
        if (raymarchQualityApplied) {
            return;
        }
        originalRaymarchQuality = AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.get();
        AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.set(AtmoCommonConfig.CloudRaymarchQuality.ULTRA);
        raymarchQualityApplied = true;
        ProjectAtmosphere.LOGGER.info(
                "T132_AUTORUN_FIXTURE_CONTROL raymarchQuality=ULTRA(96/6) priorValue={} restoredOnExit=true",
                originalRaymarchQuality);
    }

    private static void restoreFixtureQualityControl() {
        if (!raymarchQualityApplied || originalRaymarchQuality == null) {
            return;
        }
        AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.set(originalRaymarchQuality);
        ProjectAtmosphere.LOGGER.info(
                "T132_AUTORUN_FIXTURE_CONTROL raymarchQualityRestored={}", originalRaymarchQuality);
        raymarchQualityApplied = false;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
