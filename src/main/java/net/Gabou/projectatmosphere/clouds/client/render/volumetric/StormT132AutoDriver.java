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
     * T161: the same-fixture A/B between the old monolithic FINAL program and
     * the new compile-time-specialized lean FINAL program. Both arms render the
     * identical pose, fixture, quality mode and resolution; the only difference
     * is which linked program the renderer binds.
     */
    private static final Path T161_MARKER = Path.of("t161-final-specialization.txt");
    /**
     * T140: the screen-coverage cost campaign. It measures cloud GPU cost as a
     * function of how much of the viewport can contain cloud at all, and
     * compares the shipped lean renderer against a diagnostic oracle that
     * rejects provably empty pixels before they march.
     */
    private static final Path T140_MARKER = Path.of("t140-coverage.txt");
    /**
     * T140 part 1: the five shipped quality modes at one pose, each at its
     * own ladder resolution, measured against the banked lean FINAL program.
     */
    private static final Path T140_MODES_MARKER = Path.of("t140-modes.txt");
    /**
     * T162: the post-T161 cost attribution ladder. Every arm is a separately
     * linked, lean-specialized program so no runtime diagnostic branch can
     * distort the timing, which is the trap T161 documented.
     */
    private static final Path T162_MARKER = Path.of("t162-attribution.txt");
    /**
     * T163: proves the productionized precipitation specialization is
     * bit-identical and measures what it bought, including the resolution
     * frontier it may have re-opened.
     */
    private static final Path T163_MARKER = Path.of("t163-precipitation.txt");
    /**
     * T166: the post-T163 GPU baseline, cost attribution and PMWeather-idea
     * evaluation. One campaign, because each launch pays for a world bootstrap
     * and a fresh severe fixture, and because every speedup here has to be a
     * within-run pair against an anchor measured on the same storm.
     */
    private static final Path T166_MARKER = Path.of("t166-baseline.txt");
    /**
     * T167: refine the two arms T166 left open - a graded replacement for the
     * over-aggressive distance step, and a nearest-K cap on descriptor owners -
     * plus a safer early-termination threshold. Measured independently first;
     * the combined stack is built from whatever actually wins.
     */
    private static final Path T167_MARKER = Path.of("t167-refine.txt");
    /**
     * T168: a footprint-derived step LOD that is pose-invariant by construction,
     * plus the descriptor-traversal audit T167's negative result asked for.
     */
    private static final Path T168_MARKER = Path.of("t168-footprint.txt");
    private static final Path T169_MARKER = Path.of("t169-lighting-detail.txt");
    /**
     * T170: the primary march itself. T169 bounded lighting and detail at 34.8%
     * of SIDE cloud cost combined, which leaves the march and its per-sample
     * descriptor work as the only remaining class large enough to close the
     * SIDE budget.
     */
    private static final Path T170_MARKER = Path.of("t170-primary-march.txt");
    /**
     * T171: the harness itself. T170 measured a 13% spread across arms proven to
     * render byte-identical images, which is larger than most remaining effects,
     * so nothing further is banked until that spread has a cause.
     */
    private static final Path T171_MARKER = Path.of("t171-harness-stability.txt");
    /**
     * T172: the real descriptor-invariant precompute, measured against T170's
     * compile-time ceilings under T171's two-repeat rule.
     */
    private static final Path T172_MARKER = Path.of("t172-descriptor-precompute.txt");
    /**
     * T173: the SIDE pose itself. Three campaigns produced SIDE blocks whose
     * repeats disagreed by up to 56%, and T172's per-cell counters showed why
     * the timing moved - the rendered workload changed mid-sweep and stayed
     * changed. This campaign holds everything constant and asks whether that
     * still happens.
     */
    private static final Path T173_MARKER = Path.of("t173-side-stability.txt");
    /**
     * T174: whether a whole descriptor group can be skipped before its walk.
     * T168 closed selection inside an entered group; this asks about entry
     * itself, which the group loop currently does with no metadata to reject on.
     */
    private static final Path T174_MARKER = Path.of("t174-group-entry.txt");
    /**
     * T175: are the primary march's density calls necessary? T174 established
     * they are not dominated by group walk overhead; this asks whether they are
     * mostly empty or mostly material, which decides the next architecture.
     */
    private static final Path T175_MARKER = Path.of("t175-density-necessity.txt");
    /**
     * T176: the light march, which T175 measured at 39% of the descriptor walk
     * and which has never been profiled because its counters were dead from
     * T169 until T175 revived them.
     */
    private static final Path T176_MARKER = Path.of("t176-light-march.txt");
    /**
     * T177 marker. Measures whether a light tap can reuse the descriptor group
     * its originating primary sample already resolved, and re-measures the
     * tap-count arms under a paired-ratio protocol rather than the absolute-p50
     * comparison T176 showed can agree inside a wrong timing mode.
     */
    private static final Path T177_MARKER = Path.of("t177-light-reuse.txt");
    /**
     * T178 marker. Precomputed lobe support, plus the light3 quality
     * measurement T176 and T177 both had to report as missing.
     */
    private static final Path T178_MARKER = Path.of("t178-lobe-support.txt");
    /**
     * T179 marker. Dominance pruning: rejecting a lobe before its exact SDF
     * because the ordered smooth union provably cannot move, rather than
     * because the lobe is spatially distant.
     */
    private static final Path T179_MARKER = Path.of("t179-dominance.txt");
    /**
     * T180 marker. Decomposes the workload class T175 attributed by
     * subtraction, and prices its two real production consumers by removing
     * each uniformly at compile time.
     */
    private static final Path T180_MARKER = Path.of("t180-consumers.txt");
    /**
     * T181 marker. Sweeps the empty-span scan's probe cap uniformly and puts
     * the first price on the march's union-distance refinement, which T180
     * identified as the larger consumer and left unmeasured.
     */
    private static final Path T181_MARKER = Path.of("t181-probe-refine.txt");
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

    /**
     * Whether any campaign wants the performance route.
     *
     * <p>Derived from {@link StormCampaignRegistry}, not written out. The
     * hand-maintained disjunction this replaces silently dropped T169: the
     * marker existed, every other site was wired, and the campaign was
     * unreachable because one term was missing here. A campaign registered in
     * {@code StormCampaignRegistry.CAMPAIGNS} now routes without a second
     * edit, and the sandbox invariant fails the build if a marker is declared
     * in this class without being registered.
     */
    private static boolean performanceRunRequested() {
        return StormCampaignRegistry.performanceMarkerPresent();
    }

    private static boolean budgetContractRunRequested() {
        return Files.exists(T135_MARKER);
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

    private static boolean specializationRunRequested() {
        return Files.exists(T161_MARKER);
    }

    private static boolean coverageRunRequested() {
        return Files.exists(T140_MARKER);
    }

    private static boolean fiveModeRunRequested() {
        return Files.exists(T140_MODES_MARKER);
    }

    private static boolean attributionRunRequested() {
        return Files.exists(T162_MARKER);
    }

    private static boolean precipitationRunRequested() {
        return Files.exists(T163_MARKER);
    }

    private static boolean baselineRunRequested() {
        return Files.exists(T166_MARKER);
    }

    private static boolean refinementRunRequested() {
        return Files.exists(T167_MARKER);
    }

    private static boolean footprintRunRequested() {
        return Files.exists(T168_MARKER);
    }

    private static boolean lightingDetailRunRequested() {
        return Files.exists(T169_MARKER);
    }

    private static boolean primaryMarchRunRequested() {
        return Files.exists(T170_MARKER);
    }

    private static boolean harnessStabilityRunRequested() {
        return Files.exists(T171_MARKER);
    }

    private static boolean precomputeRunRequested() {
        return Files.exists(T172_MARKER);
    }

    private static boolean sideStabilityRunRequested() {
        return Files.exists(T173_MARKER);
    }

    private static boolean groupEntryRunRequested() {
        return Files.exists(T174_MARKER);
    }

    private static boolean densityNecessityRunRequested() {
        return Files.exists(T175_MARKER);
    }

    private static boolean lightMarchRunRequested() {
        return Files.exists(T176_MARKER);
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
    /**
     * T161 arms. The lean program is measured first so a fixture that decays
     * mid-run costs the monolith's cell rather than the one being productionized.
     * Both arms are NORMAL_PRODUCTION: nothing about the rendered work changes,
     * only which linked build of the same shader executes it.
     */
    private static final CoreCostDiagnosticProgram[] T161_ARMS = {
            CoreCostDiagnosticProgram.LEAN_FINAL,
            CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH
    };
    /**
     * One pose. T161 is a program-identity comparison, not a coverage sweep:
     * PLAY_VIS_NEAR is the representative visible-storm gameplay case the
     * budget has to hold, and is the fixture the core-cost experiment used.
     */
    private static final String[] T161_POSES = {"PLAY_VIS_NEAR"};
    /**
     * T140 arms. LEAN_FINAL is the shipped renderer and the baseline every
     * speedup is quoted against; the rest are the same lean program plus a
     * whole-pixel rejection oracle at three granularities. Measuring the tile
     * arms alongside the per-pixel one shows how much of the theoretical
     * benefit survives coarse classification.
     */
    private static final CoreCostDiagnosticProgram[] T140_ARMS = {
            CoreCostDiagnosticProgram.LEAN_FINAL,
            CoreCostDiagnosticProgram.T140_PIXEL_ORACLE,
            CoreCostDiagnosticProgram.T140_TILE8,
            CoreCostDiagnosticProgram.T140_TILE16
    };
    /**
     * The coverage series, ordered heaviest first so a decaying fixture costs
     * the cheapest and least informative poses rather than the storm-heavy
     * reference. All five stand at the same point and differ only in aim.
     */
    private static final String[] T140_POSES = {
            "PLAY_VIS_NEAR", "T140_PARTIAL", "T140_EDGE", "T140_AWAY_180", "T140_AWAY_DOWN"};
    private static final StormOptimizationDiagnosticMode[] T140_OPTIMIZATION_ARMS = {
            StormOptimizationDiagnosticMode.NORMAL_PRODUCTION,
            StormOptimizationDiagnosticMode.NORMAL_PRODUCTION,
            StormOptimizationDiagnosticMode.NORMAL_PRODUCTION,
            StormOptimizationDiagnosticMode.NORMAL_PRODUCTION
    };
    private static boolean t140Run;
    private static boolean t140ModeRun;
    private static boolean t163Run;
    private static boolean t163OriginalHistoryEnabled;
    private static int t163ImageStep;
    private static StormReferenceImageComparison.Reference t163AnchorImage;

    /** One T163 cell: which linked program, at which internal resolution. */
    private record T163Arm(CoreCostDiagnosticProgram program, float resolutionScale) {
        String label() {
            return Float.isNaN(resolutionScale)
                    ? program.serializedName()
                    : String.format(Locale.ROOT, "%s@r%.4f", program.serializedName(),
                            resolutionScale);
        }
    }

    /**
     * Old and new FINAL measured back to back at each internal resolution, so
     * every speedup is a within-run pair rather than a comparison against a
     * number remembered from an earlier session.
     */
    private static final T163Arm[] T163_ARMS = {
            new T163Arm(CoreCostDiagnosticProgram.T163_WITH_RAIN, Float.NaN),
            new T163Arm(CoreCostDiagnosticProgram.LEAN_FINAL, Float.NaN),
            new T163Arm(CoreCostDiagnosticProgram.T163_WITH_RAIN, 0.375F),
            new T163Arm(CoreCostDiagnosticProgram.LEAN_FINAL, 0.375F),
            new T163Arm(CoreCostDiagnosticProgram.T163_WITH_RAIN, 0.50F),
            new T163Arm(CoreCostDiagnosticProgram.LEAN_FINAL, 0.50F)
    };
    /** Storm-heavy plus two other geometries, so the gain is not a one-pose artifact. */
    private static final String[] T163_POSES = {"PLAY_VIS_NEAR", "PLAY_VIS_MID", "SIDE"};
    private static final StormOptimizationDiagnosticMode[] T163_OPTIMIZATION_ARMS =
            buildT163OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT163OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T163_ARMS.length];
        java.util.Arrays.fill(modes, StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
        return modes;
    }

    private static T163Arm t163Arm() {
        return T163_ARMS[Math.max(0, Math.min(T163_ARMS.length - 1, t141ArmIndex))];
    }

    private static boolean t166Run;
    private static boolean t166OriginalHistoryEnabled;
    /**
     * Growth gate state. A freshly spawned severe storm keeps growing for
     * several seconds after it reaches ten descriptors, and every structural
     * pose is defined as a multiple of the fixture's horizontal radius - so a
     * camera computed while the radius is still climbing is placed for a storm
     * that no longer exists by the time the arms run.
     *
     * <p>This is not hypothetical. The first T166 run measured FAR's first four
     * cells at {@code nearestDistance=30.4} and the remaining twenty-four at
     * {@code 1087.1}: the radius grew about thirty-fold mid-matrix. Those four
     * cells reported 474k primary ray steps and <b>zero</b> light-march
     * evaluations against 3,706k and 224k for every later cell, and the anchor
     * among them was 3.1x cheaper than the same program re-measured later in
     * the same pose. Descriptor count did not catch it - it was ten throughout -
     * and neither did the T150 geometric visibility verdict, because the storm
     * genuinely was visible; it was simply tiny.
     */
    private static final int T166_GROWTH_STABLE_TICKS = 40;
    private static final double T166_GROWTH_TOLERANCE = 0.01D;
    /**
     * Bound on the wait. It exists so a fixture that never settles produces a
     * logged, measurable pose rather than a hung campaign.
     */
    private static final int T166_GROWTH_TIMEOUT_TICKS = 2_400;
    /**
     * How far the radius may drift from the value the pose's camera was built
     * from before the pose is rebuilt.
     *
     * <p>Waiting for growth to stop is not sufficient on its own, and the
     * second T166 run proved it: the storm grows in plateaus, the stability
     * window closed during one of them at radius 269, the FAR camera was placed
     * at 2.6x269, and the radius then went on to 673 - so the first arms
     * measured a camera 700 blocks from the centre and the rest measured one at
     * 1750. The wait reduces how often that happens; only re-checking the
     * radius against the camera actually catches it.
     */
    private static final double T166_POSE_RADIUS_TOLERANCE = 0.02D;
    /** The radius this pose's camera was computed from, or -1 before it is. */
    private static double t166PoseCameraRadius = -1.0D;
    /**
     * Where the pose asked the camera to be, and how close the player has to
     * get before a cell may run.
     *
     * <p>{@code T135_SETTLE} counted frames, which is not the same as arriving.
     * A structural pose teleports the camera one to two thousand blocks, and
     * until the server has moved the player and the client has the position
     * back, the frame being timed is the <em>previous</em> pose's - or, for the
     * first pose after the spawn, a camera standing inside the storm, which
     * takes the in-cloud fast path and reports a cost unrelated to the pose.
     *
     * <p>This was the actual cause of the FAR anomaly. Two earlier guesses -
     * that the storm was still growing, and that its radius had drifted from
     * the one the camera was built from - were both wrong: the radius was
     * already 663 and stable, and the camera was computed from it correctly.
     * What differed between the cheap cells and the rest was only whether the
     * player had got there, which shows up as `nearestDistance` jumping from
     * 24.4 to 1060.8 between two consecutive arms with no teleport between
     * them.
     */
    private static double t166PoseTargetX;
    private static double t166PoseTargetY;
    private static double t166PoseTargetZ;
    private static boolean t166PoseTargetValid;
    private static int t166ArrivalWaitFrames;
    private static final double T166_ARRIVAL_TOLERANCE_BLOCKS = 2.0D;
    private static final int T166_ARRIVAL_TIMEOUT_FRAMES = 1_200;

    /**
     * The arms whose recommendation depends on what they do to the picture.
     *
     * <p>Every one of these is supposed to change the image, so bit-identity is
     * the wrong test; what matters is whether the silhouette, the thin material
     * and the openings between lobes survive. `t166_nolight` is included as the
     * harness self-check: it must show a large difference, and a run where it
     * does not has captured the same program twice - the exact defect that
     * invalidated the first T162 image attempt.
     */
    private static final CoreCostDiagnosticProgram[] T166_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T166_DISTANCE_STEP,
            CoreCostDiagnosticProgram.T166_DISTANCE_LOD,
            CoreCostDiagnosticProgram.T166_LIGHT_CHEAP,
            CoreCostDiagnosticProgram.T166_EARLY_TERM,
            CoreCostDiagnosticProgram.T166_EMPTY_JUMP,
            CoreCostDiagnosticProgram.T166_STACK,
            CoreCostDiagnosticProgram.T166_NO_LIGHT
    };
    private static int t166ImageStep;
    private static StormReferenceImageComparison.Reference t166AnchorImage;
    private static double t166LastRadius = -1.0D;
    private static int t166StableTicks;
    private static int t166GrowthWaitTicks;

    /**
     * One T166 cell: which linked program, at which internal resolution, and
     * which optimization mode the renderer must upload for it.
     *
     * <p>The mode matters only for the oracle arms. Those bake the same value
     * as a constant, but the renderer reads the Java-side mode to decide
     * whether to run the untimed ground-truth capture pass before the timed
     * draw, so the two have to agree.
     */
    private record T166Arm(
            CoreCostDiagnosticProgram program,
            float resolutionScale,
            StormOptimizationDiagnosticMode mode,
            int descriptorLimit,
            String tag,
            int sampleFrames) {
        T166Arm(CoreCostDiagnosticProgram program) {
            this(program, Float.NaN, StormOptimizationDiagnosticMode.NORMAL_PRODUCTION, -1,
                    "", 0);
        }

        T166Arm(CoreCostDiagnosticProgram program, float resolutionScale,
                StormOptimizationDiagnosticMode mode) {
            this(program, resolutionScale, mode, -1, "", 0);
        }

        T166Arm(CoreCostDiagnosticProgram program, float resolutionScale,
                StormOptimizationDiagnosticMode mode, int descriptorLimit) {
            this(program, resolutionScale, mode, descriptorLimit, "", 0);
        }

        /**
         * T171. A repeat of the same program is a distinct cell and must have a
         * distinct label, or the results map collapses the repeats onto one
         * entry - the failure that silently erased T169's five T149 arms.
         */
        T166Arm(CoreCostDiagnosticProgram program, String tag, int sampleFrames) {
            this(program, Float.NaN, StormOptimizationDiagnosticMode.NORMAL_PRODUCTION, -1,
                    tag, sampleFrames);
        }

        /**
         * A descriptor-capped arm is labelled by its cap, because capping
         * residency changes what is drawn: it is a ceiling measurement, not a
         * variant of the same picture, and the label has to say so.
         */
        String label() {
            String base = Float.isNaN(resolutionScale)
                    ? program.serializedName()
                    : String.format(Locale.ROOT, "%s@r%.4f", program.serializedName(),
                            resolutionScale);
            // T169 measured five runtime-mode arms and reported the anchor's
            // row five times, because the mode was not part of the label and
            // every one of them collided with the anchor in the results map.
            // A cell is keyed by this string; anything that changes what is
            // measured has to appear in it.
            if (mode != StormOptimizationDiagnosticMode.NORMAL_PRODUCTION) {
                base = base + "@m" + mode.serializedName();
            }
            if (descriptorLimit >= 0) {
                base = base + "@d" + descriptorLimit;
            }
            return tag == null || tag.isEmpty() ? base : base + "#" + tag;
        }
    }

    /**
     * The campaign matrix, in the order it must survive a truncated run.
     *
     * <p>The 0.25 anchor and the attribution arms come first, because those are
     * what the task banks. The anchor is then repeated once mid-matrix: it is
     * the drift control, and a run whose two anchors disagree materially has
     * measured a moving fixture rather than a set of programs. The fixed-work
     * ladder, the oracle re-derivation and the optional resolution curve follow.
     */
    private static final T166Arm[] T166_ARMS = buildT166Arms();

    private static T166Arm[] buildT166Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL));
        for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                CoreCostDiagnosticProgram.T166_NO_LIGHT,
                CoreCostDiagnosticProgram.T166_NO_DETAIL,
                CoreCostDiagnosticProgram.T166_LIGHT_NO_DETAIL,
                CoreCostDiagnosticProgram.T166_LIGHT_STEPS2,
                CoreCostDiagnosticProgram.T166_LIGHT_WIDE,
                CoreCostDiagnosticProgram.T166_LIGHT_EARLY_OUT,
                CoreCostDiagnosticProgram.T166_LIGHT_CHEAP,
                CoreCostDiagnosticProgram.T166_DISTANCE_STEP,
                CoreCostDiagnosticProgram.T166_EMPTY_JUMP,
                CoreCostDiagnosticProgram.T166_DISTANCE_LOD,
                CoreCostDiagnosticProgram.T166_EARLY_TERM,
                CoreCostDiagnosticProgram.T166_NO_SCENE_LIMIT,
                CoreCostDiagnosticProgram.T166_STACK}) {
            arms.add(new T166Arm(arm));
        }
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL, 0.2500F,
                StormOptimizationDiagnosticMode.NORMAL_PRODUCTION));
        for (CoreCostDiagnosticProgram rung : new CoreCostDiagnosticProgram[] {
                CoreCostDiagnosticProgram.T166_FW1_ADDRESS,
                CoreCostDiagnosticProgram.T166_FW2_CANDIDATE,
                CoreCostDiagnosticProgram.T166_FW3_DESCRIPTOR,
                CoreCostDiagnosticProgram.T166_FW4_SHAPE,
                CoreCostDiagnosticProgram.T166_FW5_NODETAIL,
                CoreCostDiagnosticProgram.T166_FW6_DENSITY}) {
            arms.add(new T166Arm(rung));
        }
        arms.add(new T166Arm(CoreCostDiagnosticProgram.T166_ORACLE_EMPTY, Float.NaN,
                StormOptimizationDiagnosticMode.T153_PERFECT_EMPTY_SKIP));
        arms.add(new T166Arm(CoreCostDiagnosticProgram.T166_ORACLE_INTERVALS, Float.NaN,
                StormOptimizationDiagnosticMode.T153_PERFECT_OCCUPIED_INTERVALS));
        arms.add(new T166Arm(CoreCostDiagnosticProgram.T166_ORACLE_COMBINED, Float.NaN,
                StormOptimizationDiagnosticMode.T153_COMBINED));
        // Secondary: what the raw internal resolution actually costs. Last, so
        // it can never delay or displace the 0.25 attribution above.
        for (float scale : new float[] {0.1250F, 0.1875F, 0.3750F, 0.5000F}) {
            arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL, scale,
                    StormOptimizationDiagnosticMode.NORMAL_PRODUCTION));
        }
        return arms.toArray(new T166Arm[0]);
    }

    /**
     * The T167 matrix. Anchor first, then the three questions measured
     * independently, then the anchor again as the drift control.
     */
    private static final T166Arm[] T167_ARMS = buildT167Arms();

    private static T166Arm[] buildT167Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL));
        // The T166 arm this campaign is trying to improve on, re-measured in
        // this run so the graded curves are compared against it directly rather
        // than against a remembered number.
        arms.add(new T166Arm(CoreCostDiagnosticProgram.T166_DISTANCE_STEP));
        for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                CoreCostDiagnosticProgram.T167_CURVE_A_LATE,
                CoreCostDiagnosticProgram.T167_CURVE_B_SMOOTH,
                CoreCostDiagnosticProgram.T167_CURVE_C_CAPPED,
                CoreCostDiagnosticProgram.T167_CURVE_D_FOOTPRINT,
                CoreCostDiagnosticProgram.T167_CURVE_D_SCANFIXED,
                CoreCostDiagnosticProgram.T167_K1,
                CoreCostDiagnosticProgram.T167_K2,
                CoreCostDiagnosticProgram.T167_K3,
                CoreCostDiagnosticProgram.T167_K4,
                CoreCostDiagnosticProgram.T167_K6,
                CoreCostDiagnosticProgram.T167_TERM030,
                CoreCostDiagnosticProgram.T167_TERM045,
                CoreCostDiagnosticProgram.T167_STACK_BALANCED,
                CoreCostDiagnosticProgram.T167_STACK_SAFE}) {
            arms.add(new T166Arm(arm));
        }
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL, 0.2500F,
                StormOptimizationDiagnosticMode.NORMAL_PRODUCTION));
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t167Arm() {
        return T167_ARMS[Math.max(0, Math.min(T167_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T167_OPTIMIZATION_ARMS =
            buildT167OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT167OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T167_ARMS.length];
        java.util.Arrays.fill(modes, StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
        return modes;
    }

    /**
     * Every arm that changes the picture is captured, because the descriptor
     * question cannot be decided on timing at all - the whole point is whether
     * a smaller owner set still draws the same storm.
     */
    private static final CoreCostDiagnosticProgram[] T167_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T166_DISTANCE_STEP,
            CoreCostDiagnosticProgram.T167_CURVE_A_LATE,
            CoreCostDiagnosticProgram.T167_CURVE_B_SMOOTH,
            CoreCostDiagnosticProgram.T167_CURVE_C_CAPPED,
            CoreCostDiagnosticProgram.T167_CURVE_D_FOOTPRINT,
            CoreCostDiagnosticProgram.T167_CURVE_D_SCANFIXED,
            CoreCostDiagnosticProgram.T167_K1,
            CoreCostDiagnosticProgram.T167_K2,
            CoreCostDiagnosticProgram.T167_K3,
            CoreCostDiagnosticProgram.T167_K4,
            CoreCostDiagnosticProgram.T167_K6,
            CoreCostDiagnosticProgram.T167_TERM045,
            CoreCostDiagnosticProgram.T167_STACK_BALANCED,
            CoreCostDiagnosticProgram.T167_STACK_SAFE
    };

    private static boolean t167Run;
    private static boolean t167OriginalHistoryEnabled;
    private static boolean t168Run;
    private static boolean t168OriginalHistoryEnabled;
    private static boolean t169Run;
    private static boolean t169OriginalHistoryEnabled;

    /**
     * The T169 matrix. Anchor, the lighting arms, the detail arms, the two
     * stacks, then the anchor again as the drift control.
     *
     * <p>The runtime-graded T149 modes are measured alongside the compile-time
     * arms because they already implement adaptive tap count, contribution
     * gating and distance grading - the shapes Task 2 asks for. Reimplementing
     * them as new compile-time arms would measure the same idea twice and
     * leave the existing implementation unexplained.
     */
    private static final T166Arm[] T169_ARMS = buildT169Arms();

    private static T166Arm[] buildT169Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL));
        for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                CoreCostDiagnosticProgram.T169_LIGHT_STEPS5,
                CoreCostDiagnosticProgram.T169_LIGHT_STEPS4,
                CoreCostDiagnosticProgram.T169_LIGHT_EARLY_OUT2,
                CoreCostDiagnosticProgram.T166_LIGHT_STEPS2,
                CoreCostDiagnosticProgram.T166_NO_LIGHT,
                CoreCostDiagnosticProgram.T169_DETAIL_FP_CONSERVATIVE,
                CoreCostDiagnosticProgram.T169_DETAIL_FP_BALANCED,
                CoreCostDiagnosticProgram.T169_DETAIL_FP_AGGRESSIVE,
                CoreCostDiagnosticProgram.T169_NO_DETAIL,
                CoreCostDiagnosticProgram.T169_STACK_SAFE,
                CoreCostDiagnosticProgram.T169_STACK_FAST}) {
            arms.add(new T166Arm(arm));
        }
        // The T149 graded policies, measured on the anchor program through the
        // optimization-mode uniform they are already gated by.
        for (StormOptimizationDiagnosticMode mode : new StormOptimizationDiagnosticMode[] {
                StormOptimizationDiagnosticMode.T149_LIGHT_CONTRIBUTION,
                StormOptimizationDiagnosticMode.T149_LIGHT_DISTANCE,
                StormOptimizationDiagnosticMode.T149_LIGHT_GRADED,
                StormOptimizationDiagnosticMode.T149_DETAIL_GRADED,
                StormOptimizationDiagnosticMode.T149_GRADED}) {
            arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL, Float.NaN, mode));
        }
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL, 0.2500F,
                StormOptimizationDiagnosticMode.NORMAL_PRODUCTION));
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t169Arm() {
        return T169_ARMS[Math.max(0, Math.min(T169_ARMS.length - 1, t141ArmIndex))];
    }

    /**
     * The T170 matrix. Anchor, the footprint-clamp sweep, the per-descriptor
     * attribution arms, the two candidate stacks, then the anchor again as the
     * drift control.
     */
    private static final T166Arm[] T170_ARMS = buildT170Arms();

    private static T166Arm[] buildT170Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL));
        for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                // The banked T169 configuration, measured inside this run.
                // Run 1 compared it across runs and the two disagreed; a
                // carried-forward configuration has to be a control, not a
                // remembered number.
                CoreCostDiagnosticProgram.T169_STACK_FAST,
                CoreCostDiagnosticProgram.T170_FPMAX3,
                CoreCostDiagnosticProgram.T170_STACK3,
                CoreCostDiagnosticProgram.T170_FPMAX4,
                CoreCostDiagnosticProgram.T170_FPMAX5,
                CoreCostDiagnosticProgram.T170_FPMAX6,
                CoreCostDiagnosticProgram.T170_FPMAX8,
                CoreCostDiagnosticProgram.T170_DESC_CONST_FETCH,
                CoreCostDiagnosticProgram.T170_DESC_CONST_EDGE,
                CoreCostDiagnosticProgram.T170_DESC_CHEAP_OWNERSHIP,
                CoreCostDiagnosticProgram.T170_DESC_NO_EXACT_SDF,
                CoreCostDiagnosticProgram.T170_DESC_HARD_UNION,
                CoreCostDiagnosticProgram.T170_DESC_HOIST,
                CoreCostDiagnosticProgram.T170_STACK,
                CoreCostDiagnosticProgram.T170_STACK8,
                CoreCostDiagnosticProgram.T170_STACK_HOIST}) {
            arms.add(new T166Arm(arm));
        }
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL, 0.2500F,
                StormOptimizationDiagnosticMode.NORMAL_PRODUCTION));
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t170Arm() {
        return T170_ARMS[Math.max(0, Math.min(T170_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T170_OPTIMIZATION_ARMS =
            buildT170OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT170OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T170_ARMS.length];
        for (int i = 0; i < T170_ARMS.length; i++) {
            modes[i] = T170_ARMS[i].mode();
        }
        return modes;
    }

    /**
     * The T170 arms worth an image comparison. The attribution arms are
     * visually invalid by construction, so they are captured too: the campaign
     * has to be able to say how invalid, not assume it.
     */
    private static final CoreCostDiagnosticProgram[] T170_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T169_STACK_FAST,
            CoreCostDiagnosticProgram.T170_FPMAX3,
            CoreCostDiagnosticProgram.T170_STACK3,
            CoreCostDiagnosticProgram.T170_FPMAX4,
            CoreCostDiagnosticProgram.T170_FPMAX5,
            CoreCostDiagnosticProgram.T170_FPMAX6,
            CoreCostDiagnosticProgram.T170_FPMAX8,
            CoreCostDiagnosticProgram.T170_DESC_HOIST,
            CoreCostDiagnosticProgram.T170_STACK,
            CoreCostDiagnosticProgram.T170_STACK8,
            CoreCostDiagnosticProgram.T170_STACK_HOIST
    };

    /** SIDE is the binding pose; FAR guards against a regression. */
    private static final String[] T170_POSES = {"FAR", "SIDE"};

    /** T171 measures the harness, so SIDE - the pose the budget binds on - leads. */
    private static final String[] T171_POSES = {"SIDE", "FAR"};

    /** T172 keeps T171's ordering: SIDE binds the budget. */
    private static final String[] T172_POSES = {"SIDE", "FAR"};

    /**
     * T173 measures SIDE and nothing else. Adding FAR would double the run for
     * a pose that is already stable and would put a long gap in the middle of
     * the sequence under test.
     */
    private static final String[] T173_POSES = {"SIDE"};

    private static boolean t173Run;
    private static boolean t173OriginalHistoryEnabled;

    /**
     * Twenty identical cells. One program, one pose, one fixture, no arm
     * switching, no compile-time variant, no descriptor-layout difference -
     * every cell is the production anchor.
     *
     * <p>T172's per-cell counters showed the SIDE workload dropping 31% partway
     * through a sweep and never recovering, with the storm, camera, descriptor
     * count, governor, resolution and render target all constant. If that
     * happens here, it is not caused by anything the campaign varies, because
     * this campaign varies nothing.
     */
    private static final T166Arm[] T173_ARMS = buildT173Arms();

    private static T166Arm[] buildT173Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        int anchor = 0;
        // Phase 2. Strict anchor bracketing: every measured arm sits between two
        // anchor cells, so its ratio is taken against anchors measured seconds
        // either side of it rather than against a block average.
        //
        // Phase 1 established that twenty consecutive anchor cells agree to
        // CV 0.57% when nothing switches, and T172 established that a
        // block-then-block layout does not. Bracketing is what turns the first
        // fact into a usable protocol: an arm whose two anchors disagree is
        // rejected before its own number is read, so drift is caught rather
        // than absorbed.
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                String.format(Locale.ROOT, "a%02d", ++anchor), 60));
        for (int repeat = 1; repeat <= 2; repeat++) {
            for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                    CoreCostDiagnosticProgram.T173_BOX_BOUND,
                    CoreCostDiagnosticProgram.T173_BOX_PRE,
                    CoreCostDiagnosticProgram.T169_STACK_FAST,
                    CoreCostDiagnosticProgram.T172_STACK_PRE,
                    CoreCostDiagnosticProgram.T173_STACK_BOX_PRE}) {
                arms.add(new T166Arm(arm, "r" + repeat, 60));
                arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                        String.format(Locale.ROOT, "a%02d", ++anchor), 60));
            }
        }
        return arms.toArray(new T166Arm[0]);
    }

    /** SIDE binds the budget; FAR must not regress. */
    private static final String[] T174_POSES = {"SIDE", "FAR"};

    private static boolean t174Run;
    private static boolean t174OriginalHistoryEnabled;

    /**
     * The T174 matrix, anchor-bracketed exactly as T173 phase 2 was.
     *
     * <p>The anchor is now the productionized FINAL, so `t174_no_precompute` is
     * the control that proves the shipped program consumes the precomputed
     * fields: the two must render the same image and differ only in speed.
     *
     * <p>The two ceilings price the group-entry line before any group metadata
     * is designed, because the group loop currently has none to reject on.
     */
    private static final T166Arm[] T174_ARMS = buildT174Arms();

    private static T166Arm[] buildT174Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        int anchor = 0;
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                String.format(Locale.ROOT, "a%02d", ++anchor), 60));
        for (int repeat = 1; repeat <= 2; repeat++) {
            for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                    CoreCostDiagnosticProgram.T174_NO_PRECOMPUTE,
                    CoreCostDiagnosticProgram.T174_FIRST_GROUP_ONLY,
                    CoreCostDiagnosticProgram.T174_GROUP2_NO_SDF,
                    CoreCostDiagnosticProgram.T172_STACK_PRE}) {
                arms.add(new T166Arm(arm, "r" + repeat, 60));
                arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                        String.format(Locale.ROOT, "a%02d", ++anchor), 60));
            }
        }
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t174Arm() {
        return T174_ARMS[Math.max(0, Math.min(T174_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T174_OPTIMIZATION_ARMS =
            buildT174OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT174OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T174_ARMS.length];
        for (int i = 0; i < T174_ARMS.length; i++) {
            modes[i] = T174_ARMS[i].mode();
        }
        return modes;
    }

    /**
     * The control must be image-identical to the anchor - that is the proof of
     * consumption. The ceilings are captured too, so how invalid they are is
     * measured rather than asserted.
     */
    private static final CoreCostDiagnosticProgram[] T174_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T174_NO_PRECOMPUTE,
            CoreCostDiagnosticProgram.T174_FIRST_GROUP_ONLY,
            CoreCostDiagnosticProgram.T174_GROUP2_NO_SDF
    };

    /** SIDE binds the budget; FAR must not regress. */
    private static final String[] T175_POSES = {"SIDE", "FAR"};

    private static boolean t175Run;
    private static boolean t175OriginalHistoryEnabled;

    /** The T175 matrix, anchor-bracketed as T173/T174 were. */
    private static final T166Arm[] T175_ARMS = buildT175Arms();

    private static T166Arm[] buildT175Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        int anchor = 0;
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                String.format(Locale.ROOT, "a%02d", ++anchor), 60));
        for (int repeat = 1; repeat <= 2; repeat++) {
            for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                    CoreCostDiagnosticProgram.T175_DENSITY_EVERY_2,
                    CoreCostDiagnosticProgram.T175_CLEARANCE_FIRST_GROUP,
                    CoreCostDiagnosticProgram.T172_STACK_PRE}) {
                arms.add(new T166Arm(arm, "r" + repeat, 60));
                arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                        String.format(Locale.ROOT, "a%02d", ++anchor), 60));
            }
        }
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t175Arm() {
        return T175_ARMS[Math.max(0, Math.min(T175_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T175_OPTIMIZATION_ARMS =
            buildT175OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT175OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T175_ARMS.length];
        for (int i = 0; i < T175_ARMS.length; i++) {
            modes[i] = T175_ARMS[i].mode();
        }
        return modes;
    }

    /** Both oracles are captured, so how invalid they are is measured. */
    private static final CoreCostDiagnosticProgram[] T175_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T175_DENSITY_EVERY_2,
            CoreCostDiagnosticProgram.T175_CLEARANCE_FIRST_GROUP
    };

    /** SIDE binds the budget; FAR must not regress. */
    private static final String[] T176_POSES = {"SIDE", "FAR"};

    private static boolean t176Run;
    private static boolean t176OriginalHistoryEnabled;

    /** SIDE binds the budget; FAR must not regress. */
    private static final String[] T177_POSES = {"SIDE", "FAR"};

    private static boolean t177Run;
    private static boolean t177OriginalHistoryEnabled;

    /** SIDE binds the budget; FAR must not regress. */
    private static final String[] T178_POSES = {"SIDE", "FAR"};

    private static boolean t178Run;
    private static boolean t178OriginalHistoryEnabled;

    /** SIDE binds the budget; FAR must not regress. */
    private static final String[] T179_POSES = {"SIDE", "FAR"};

    private static boolean t179Run;
    private static boolean t179OriginalHistoryEnabled;

    /** SIDE binds the budget; FAR must not regress. */
    private static final String[] T180_POSES = {"SIDE", "FAR"};

    private static boolean t180Run;
    private static boolean t180OriginalHistoryEnabled;

    /** SIDE binds the budget; FAR must not regress. */
    private static final String[] T181_POSES = {"SIDE", "FAR"};

    private static boolean t181Run;
    private static boolean t181OriginalHistoryEnabled;

    /**
     * The T181 matrix. Every arm is a uniform compile-time change, which is the
     * only shape T179 and T180 together showed can pay on this loop.
     */
    private static final T166Arm[] T181_ARMS = buildT181Arms();

    private static T166Arm[] buildT181Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        int anchor = 0;
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                String.format(Locale.ROOT, "a%02d", ++anchor), 60));
        for (int repeat = 1; repeat <= 3; repeat++) {
            for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                    CoreCostDiagnosticProgram.T181_PROBE8,
                    CoreCostDiagnosticProgram.T181_PROBE4,
                    CoreCostDiagnosticProgram.T181_PROBE2,
                    CoreCostDiagnosticProgram.T181_NO_REFINE,
                    CoreCostDiagnosticProgram.T172_STACK_PRE,
                    CoreCostDiagnosticProgram.T181_STACK_PROBE8}) {
                arms.add(new T166Arm(arm, "r" + repeat, 60));
                arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                        String.format(Locale.ROOT, "a%02d", ++anchor), 60));
            }
        }
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t181Arm() {
        return T181_ARMS[Math.max(0, Math.min(T181_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T181_OPTIMIZATION_ARMS =
            buildT181OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT181OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T181_ARMS.length];
        for (int i = 0; i < T181_ARMS.length; i++) {
            modes[i] = T181_ARMS[i].mode();
        }
        return modes;
    }

    /**
     * The scan exists to stop the ray starving, so every cap needs its control
     * damage measured, not just its timing.
     */
    private static final CoreCostDiagnosticProgram[] T181_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T181_PROBE8,
            CoreCostDiagnosticProgram.T181_PROBE4,
            CoreCostDiagnosticProgram.T181_PROBE2,
            CoreCostDiagnosticProgram.T181_NO_REFINE,
            CoreCostDiagnosticProgram.T181_STACK_PROBE8
    };

    /** True for a T181 bracketing anchor cell. */
    private static boolean t181IsAnchor(T166Arm arm) {
        return arm.program() == CoreCostDiagnosticProgram.LEAN_FINAL;
    }

    /** T181 marker predicate. */
    private static boolean probeRefineRunRequested() {
        return Files.exists(T181_MARKER);
    }

    /**
     * The T180 matrix. Uniform whole-class removals first, because T179's
     * evidence is that those are the only shape that pays, then the cheaper
     * query that keeps the consumer but drops work inside it.
     */
    private static final T166Arm[] T180_ARMS = buildT180Arms();

    private static T166Arm[] buildT180Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        int anchor = 0;
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                String.format(Locale.ROOT, "a%02d", ++anchor), 60));
        for (int repeat = 1; repeat <= 3; repeat++) {
            for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                    CoreCostDiagnosticProgram.T180_NO_PROBE,
                    CoreCostDiagnosticProgram.T180_NO_BRACKET,
                    CoreCostDiagnosticProgram.T180_PROBE_NO_DETAIL,
                    CoreCostDiagnosticProgram.T172_STACK_PRE,
                    CoreCostDiagnosticProgram.T180_STACK_PROBE}) {
                arms.add(new T166Arm(arm, "r" + repeat, 60));
                arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                        String.format(Locale.ROOT, "a%02d", ++anchor), 60));
            }
        }
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t180Arm() {
        return T180_ARMS[Math.max(0, Math.min(T180_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T180_OPTIMIZATION_ARMS =
            buildT180OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT180OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T180_ARMS.length];
        for (int i = 0; i < T180_ARMS.length; i++) {
            modes[i] = T180_ARMS[i].mode();
        }
        return modes;
    }

    /**
     * The removal arms are expected to damage the image and are bounds only;
     * the probe candidate is expected to be conservative, so its damage is the
     * measurement that decides whether it can ship.
     */
    private static final CoreCostDiagnosticProgram[] T180_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T180_NO_PROBE,
            CoreCostDiagnosticProgram.T180_NO_BRACKET,
            CoreCostDiagnosticProgram.T180_PROBE_NO_DETAIL,
            CoreCostDiagnosticProgram.T180_STACK_PROBE
    };

    /** True for a T180 bracketing anchor cell. */
    private static boolean t180IsAnchor(T166Arm arm) {
        return arm.program() == CoreCostDiagnosticProgram.LEAN_FINAL;
    }

    /** T180 marker predicate. */
    private static boolean consumerAttributionRunRequested() {
        return Files.exists(T180_MARKER);
    }

    /**
     * The T179 matrix. Three paired blocks per arm, with the stack control
     * adjacent to the stack arm so the within-block ratio T177 validated is
     * available for the composition question.
     */
    private static final T166Arm[] T179_ARMS = buildT179Arms();

    private static T166Arm[] buildT179Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        int anchor = 0;
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                String.format(Locale.ROOT, "a%02d", ++anchor), 60));
        for (int repeat = 1; repeat <= 3; repeat++) {
            for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                    CoreCostDiagnosticProgram.T179_DOM_EXACT,
                    CoreCostDiagnosticProgram.T179_DOM_AGGRESSIVE,
                    CoreCostDiagnosticProgram.T172_STACK_PRE,
                    CoreCostDiagnosticProgram.T179_STACK_DOM}) {
                arms.add(new T166Arm(arm, "r" + repeat, 60));
                arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                        String.format(Locale.ROOT, "a%02d", ++anchor), 60));
            }
        }
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t179Arm() {
        return T179_ARMS[Math.max(0, Math.min(T179_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T179_OPTIMIZATION_ARMS =
            buildT179OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT179OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T179_ARMS.length];
        for (int i = 0; i < T179_ARMS.length; i++) {
            modes[i] = T179_ARMS[i].mode();
        }
        return modes;
    }

    /**
     * The exact arm must be bit-identical - its extra rejections are lobes the
     * union provably could not have used - and the aggressive arm must not be,
     * or the ceiling it claims to bound is not real.
     */
    private static final CoreCostDiagnosticProgram[] T179_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T179_DOM_EXACT,
            CoreCostDiagnosticProgram.T179_DOM_AGGRESSIVE,
            CoreCostDiagnosticProgram.T179_STACK_DOM
    };

    /** True for a T179 bracketing anchor cell. */
    private static boolean t179IsAnchor(T166Arm arm) {
        return arm.program() == CoreCostDiagnosticProgram.LEAN_FINAL;
    }

    /** T179 marker predicate. */
    private static boolean dominancePruningRunRequested() {
        return Files.exists(T179_MARKER);
    }

    /**
     * The T178 matrix. Same three-block paired structure T177 established, with
     * the stack control adjacent to every stack arm so the within-block ratio
     * that survived T177's mode swings is available for each of them.
     */
    private static final T166Arm[] T178_ARMS = buildT178Arms();

    private static T166Arm[] buildT178Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        int anchor = 0;
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                String.format(Locale.ROOT, "a%02d", ++anchor), 60));
        for (int repeat = 1; repeat <= 3; repeat++) {
            for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                    CoreCostDiagnosticProgram.T176_LIGHT3,
                    CoreCostDiagnosticProgram.T172_STACK_PRE}) {
                arms.add(new T166Arm(arm, "r" + repeat, 60));
                arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                        String.format(Locale.ROOT, "a%02d", ++anchor), 60));
            }
        }
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t178Arm() {
        return T178_ARMS[Math.max(0, Math.min(T178_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T178_OPTIMIZATION_ARMS =
            buildT178OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT178OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T178_ARMS.length];
        for (int i = 0; i < T178_ARMS.length; i++) {
            modes[i] = T178_ARMS[i].mode();
        }
        return modes;
    }

    /**
     * `light3` is here for Task 0: its quality against the four-tap reference is
     * the measurement T176 and T177 could not make. The support arms are here
     * because a conservative bound must be shown image-exact, not assumed.
     */
    private static final CoreCostDiagnosticProgram[] T178_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T176_LIGHT3
    };

    /** True for a T178 bracketing anchor cell. */
    private static boolean t178IsAnchor(T166Arm arm) {
        return arm.program() == CoreCostDiagnosticProgram.LEAN_FINAL;
    }

    /** T178 marker predicate. */
    private static boolean lobeSupportRunRequested() {
        return Files.exists(T178_MARKER);
    }

    /**
     * The T177 matrix, built as three short paired blocks per arm rather than
     * one long run.
     *
     * <p>T176 showed two repeats can agree to 0.13% while both sit in a timing
     * mode that is 70% wrong, so absolute agreement is not a sufficient
     * acceptance test. Acceptance here is agreement between the three LOCAL
     * RATIOS instead: a ratio is taken against an anchor measured seconds away,
     * so a mode change that moves the whole machine moves anchor and arm
     * together and leaves the ratio intact.
     */
    private static final T166Arm[] T177_ARMS = buildT177Arms();

    private static T166Arm[] buildT177Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        int anchor = 0;
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                String.format(Locale.ROOT, "a%02d", ++anchor), 60));
        for (int repeat = 1; repeat <= 3; repeat++) {
            for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                    CoreCostDiagnosticProgram.T177_REUSE_HARD,
                    CoreCostDiagnosticProgram.T176_LIGHT3,
                    CoreCostDiagnosticProgram.T176_LIGHT2,
                    CoreCostDiagnosticProgram.T172_STACK_PRE,
                    CoreCostDiagnosticProgram.T176_STACK_LIGHT3,
                    CoreCostDiagnosticProgram.T177_STACK_REUSE_HARD}) {
                arms.add(new T166Arm(arm, "r" + repeat, 60));
                arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                        String.format(Locale.ROOT, "a%02d", ++anchor), 60));
            }
        }
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t177Arm() {
        return T177_ARMS[Math.max(0, Math.min(T177_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T177_OPTIMIZATION_ARMS =
            buildT177OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT177OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T177_ARMS.length];
        for (int i = 0; i < T177_ARMS.length; i++) {
            modes[i] = T177_ARMS[i].mode();
        }
        return modes;
    }

    /**
     * Quality is the whole question for the tap arms and the damage bound for
     * the reuse arms, so every non-anchor program is captured.
     */
    private static final CoreCostDiagnosticProgram[] T177_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T177_REUSE_HARD,
            CoreCostDiagnosticProgram.T176_LIGHT3,
            CoreCostDiagnosticProgram.T176_LIGHT2,
            CoreCostDiagnosticProgram.T176_STACK_LIGHT3,
            CoreCostDiagnosticProgram.T177_STACK_REUSE_HARD
    };

    /** True for a T177 bracketing anchor cell. */
    private static boolean t177IsAnchor(T166Arm arm) {
        return arm.program() == CoreCostDiagnosticProgram.LEAN_FINAL;
    }

    /** T177 marker predicate. */
    private static boolean lightReuseRunRequested() {
        return Files.exists(T177_MARKER);
    }

    /**
     * The T176 matrix. `nolight` leads because it bounds every other arm here:
     * tap reduction, group reuse and a cheaper shadow density are all subsets of
     * removing lighting altogether, so if it does not clear the gap the line
     * closes without building them.
     */
    private static final T166Arm[] T176_ARMS = buildT176Arms();

    private static T166Arm[] buildT176Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        int anchor = 0;
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                String.format(Locale.ROOT, "a%02d", ++anchor), 60));
        for (int repeat = 1; repeat <= 2; repeat++) {
            for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                    CoreCostDiagnosticProgram.T176_NO_LIGHT,
                    CoreCostDiagnosticProgram.T176_LIGHT3,
                    CoreCostDiagnosticProgram.T176_LIGHT2,
                    CoreCostDiagnosticProgram.T172_STACK_PRE,
                    CoreCostDiagnosticProgram.T176_STACK_NO_LIGHT,
                    CoreCostDiagnosticProgram.T176_STACK_LIGHT3,
                    CoreCostDiagnosticProgram.T176_STACK_LIGHT2}) {
                arms.add(new T166Arm(arm, "r" + repeat, 60));
                arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL,
                        String.format(Locale.ROOT, "a%02d", ++anchor), 60));
            }
        }
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t176Arm() {
        return T176_ARMS[Math.max(0, Math.min(T176_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T176_OPTIMIZATION_ARMS =
            buildT176OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT176OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T176_ARMS.length];
        for (int i = 0; i < T176_ARMS.length; i++) {
            modes[i] = T176_ARMS[i].mode();
        }
        return modes;
    }

    /** Self-shadow damage is the whole question for the tap arms, so all are captured. */
    private static final CoreCostDiagnosticProgram[] T176_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T176_NO_LIGHT,
            CoreCostDiagnosticProgram.T176_LIGHT3,
            CoreCostDiagnosticProgram.T176_LIGHT2,
            CoreCostDiagnosticProgram.T176_STACK_LIGHT3,
            CoreCostDiagnosticProgram.T176_STACK_LIGHT2
    };

    /** True for a T176 bracketing anchor cell. */
    private static boolean t176IsAnchor(T166Arm arm) {
        return arm.program() == CoreCostDiagnosticProgram.LEAN_FINAL
                && arm.tag().startsWith("a");
    }

    /** True for a T175 bracketing anchor cell. */
    private static boolean t175IsAnchor(T166Arm arm) {
        return arm.program() == CoreCostDiagnosticProgram.LEAN_FINAL
                && arm.tag().startsWith("a");
    }

    /** True for a T174 bracketing anchor cell. */
    private static boolean t174IsAnchor(T166Arm arm) {
        return arm.program() == CoreCostDiagnosticProgram.LEAN_FINAL
                && arm.tag().startsWith("a");
    }

    /** True for a T173 bracketing anchor cell. */
    private static boolean t173IsAnchor(T166Arm arm) {
        return arm.program() == CoreCostDiagnosticProgram.LEAN_FINAL
                && arm.tag().startsWith("a");
    }

    private static T166Arm t173Arm() {
        return T173_ARMS[Math.max(0, Math.min(T173_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T173_OPTIMIZATION_ARMS =
            buildT173OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT173OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T173_ARMS.length];
        for (int i = 0; i < T173_ARMS.length; i++) {
            modes[i] = T173_ARMS[i].mode();
        }
        return modes;
    }

    /**
     * The bound arms claim to be exact, so every one of them is captured. A
     * valid lower bound cannot cull a descriptor that could contribute, so
     * anything other than zero changed pixels is a proof error, not a tradeoff.
     */
    private static final CoreCostDiagnosticProgram[] T173_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T173_BOX_BOUND,
            CoreCostDiagnosticProgram.T173_BOX_PRE,
            CoreCostDiagnosticProgram.T173_STACK_BOX_PRE
    };

    private static boolean t172Run;
    private static boolean t172OriginalHistoryEnabled;

    /**
     * The T172 matrix, built under T171's rule: every arm measured twice, and a
     * pair disagreeing by more than 3% is rejected rather than averaged.
     *
     * <p>Each real precompute arm sits next to the T170 ceiling that estimated
     * it, so "how much of the ceiling does the real architecture retain" is a
     * within-run comparison rather than a cross-campaign one.
     */
    private static final T166Arm[] T172_ARMS = buildT172Arms();

    private static T166Arm[] buildT172Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        for (int repeat = 1; repeat <= 2; repeat++) {
            String tag = "r" + repeat;
            for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                    // A: the production anchor.
                    CoreCostDiagnosticProgram.LEAN_FINAL,
                    // B, C, D: the real precompute, isolated then combined.
                    CoreCostDiagnosticProgram.T172_PRE_EDGE,
                    CoreCostDiagnosticProgram.T172_PRE_OWNER,
                    CoreCostDiagnosticProgram.T172_PRE_BOTH,
                    // The T170 ceilings the real arms are measured against.
                    CoreCostDiagnosticProgram.T170_DESC_CONST_EDGE,
                    CoreCostDiagnosticProgram.T170_DESC_HOIST,
                    // E: the shippable stack, its baseline, and the ceiling.
                    CoreCostDiagnosticProgram.T169_STACK_FAST,
                    CoreCostDiagnosticProgram.T172_STACK_PRE,
                    CoreCostDiagnosticProgram.T170_STACK_HOIST}) {
                arms.add(new T166Arm(arm, tag, 60));
            }
        }
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t172Arm() {
        return T172_ARMS[Math.max(0, Math.min(T172_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T172_OPTIMIZATION_ARMS =
            buildT172OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT172OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T172_ARMS.length];
        for (int i = 0; i < T172_ARMS.length; i++) {
            modes[i] = T172_ARMS[i].mode();
        }
        return modes;
    }

    /**
     * The precompute arms claim to be image-equivalent, not merely fast, so
     * every one of them is captured. The T170 ceilings are not: they are known
     * to be visually wrong and T170 already recorded by how much.
     */
    private static final CoreCostDiagnosticProgram[] T172_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T172_PRE_EDGE,
            CoreCostDiagnosticProgram.T172_PRE_OWNER,
            CoreCostDiagnosticProgram.T172_PRE_BOTH,
            CoreCostDiagnosticProgram.T172_STACK_PRE
    };

    private static boolean t171Run;
    private static boolean t171OriginalHistoryEnabled;

    /**
     * The T171 matrix. Every arm renders the same image; the campaign exists to
     * measure how much the harness disagrees with itself about that.
     *
     * <p>Blocks, in execution order, so ordering effects are visible as position
     * in the arm list rather than having to be inferred:
     *
     * <ul>
     *   <li>A - the same GL program measured four times in a row. This is the
     *       floor: whatever spread appears here is not attributable to shader
     *       logic, program identity or ordering.</li>
     *   <li>B - anchor and the two duplicate programs interleaved, so a
     *       program switch happens between every pair of cells.</li>
     *   <li>C - block B's programs in reverse order.</li>
     *   <li>D - the same program at 60, 120 and 240 sampled frames.</li>
     *   <li>E - the T170 fpmax arms already proven byte-identical to each
     *       other, which is where the 13% was first seen.</li>
     *   <li>F - the T170 points worth re-measuring, twice each.</li>
     * </ul>
     */
    private static final T166Arm[] T171_ARMS = buildT171Arms();

    private static T166Arm[] buildT171Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        CoreCostDiagnosticProgram lean = CoreCostDiagnosticProgram.LEAN_FINAL;

        // A. Same program, no switch between cells.
        for (int i = 1; i <= 4; i++) {
            arms.add(new T166Arm(lean, "a" + i, 60));
        }
        // B. Interleaved anchor / duplicate-program, fixed order.
        for (int i = 1; i <= 2; i++) {
            arms.add(new T166Arm(lean, "b" + i, 60));
            arms.add(new T166Arm(CoreCostDiagnosticProgram.T171_DUP_A, "b" + i, 60));
            arms.add(new T166Arm(CoreCostDiagnosticProgram.T171_DUP_B, "b" + i, 60));
        }
        // C. The same three programs, reversed.
        for (int i = 1; i <= 2; i++) {
            arms.add(new T166Arm(CoreCostDiagnosticProgram.T171_DUP_B, "c" + i, 60));
            arms.add(new T166Arm(CoreCostDiagnosticProgram.T171_DUP_A, "c" + i, 60));
            arms.add(new T166Arm(lean, "c" + i, 60));
        }
        // D. Sample-count sweep, same program.
        arms.add(new T166Arm(lean, "d060", 60));
        arms.add(new T166Arm(lean, "d120", 120));
        arms.add(new T166Arm(lean, "d240", 240));
        // E. The byte-identical T170 clamp arms that produced the 13%.
        for (int i = 1; i <= 2; i++) {
            arms.add(new T166Arm(CoreCostDiagnosticProgram.T170_FPMAX5, "e" + i, 60));
            arms.add(new T166Arm(CoreCostDiagnosticProgram.T170_FPMAX6, "e" + i, 60));
            arms.add(new T166Arm(CoreCostDiagnosticProgram.T170_FPMAX8, "e" + i, 60));
        }
        // F. The T170 points to re-measure once the floor is known.
        for (int i = 1; i <= 2; i++) {
            for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                    CoreCostDiagnosticProgram.T169_STACK_FAST,
                    CoreCostDiagnosticProgram.T170_DESC_NO_EXACT_SDF,
                    CoreCostDiagnosticProgram.T170_DESC_CONST_EDGE,
                    CoreCostDiagnosticProgram.T170_DESC_HOIST,
                    CoreCostDiagnosticProgram.T170_STACK_HOIST}) {
                arms.add(new T166Arm(arm, "f" + i, 60));
            }
        }
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t171Arm() {
        return T171_ARMS[Math.max(0, Math.min(T171_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T171_OPTIMIZATION_ARMS =
            buildT171OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT171OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T171_ARMS.length];
        for (int i = 0; i < T171_ARMS.length; i++) {
            modes[i] = T171_ARMS[i].mode();
        }
        return modes;
    }

    /**
     * T171 captures no reference images. Every arm in blocks A-E is already
     * proven identical to the anchor, and block F's arms were captured in T170;
     * re-capturing would add program switches inside the very measurement the
     * campaign exists to characterise.
     */
    private static final CoreCostDiagnosticProgram[] T171_IMAGE_ARMS = {};

    private static boolean t170Run;
    private static boolean t170OriginalHistoryEnabled;

    /**
     * True for any campaign whose arms are whole compiled programs selected
     * through {@link T166Arm}. Written once rather than repeated: the same
     * four-way disjunction appeared at six sites, and T169 was added to five of
     * them.
     */
    private static boolean programArmCampaign() {
        return t166Run || t167Run || t168Run || t169Run || t170Run || t171Run || t172Run
                || t173Run || t174Run || t175Run || t176Run || t177Run
                || t178Run || t179Run || t180Run || t181Run;
    }

    private static final StormOptimizationDiagnosticMode[] T169_OPTIMIZATION_ARMS =
            buildT169OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT169OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T169_ARMS.length];
        for (int i = 0; i < T169_ARMS.length; i++) {
            modes[i] = T169_ARMS[i].mode();
        }
        return modes;
    }

    private static final CoreCostDiagnosticProgram[] T169_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T169_LIGHT_STEPS5,
            CoreCostDiagnosticProgram.T169_LIGHT_STEPS4,
            CoreCostDiagnosticProgram.T166_LIGHT_STEPS2,
            CoreCostDiagnosticProgram.T169_DETAIL_FP_BALANCED,
            CoreCostDiagnosticProgram.T169_DETAIL_FP_AGGRESSIVE,
            CoreCostDiagnosticProgram.T169_STACK_SAFE,
            CoreCostDiagnosticProgram.T169_STACK_FAST
    };

    /**
     * FAR and SIDE only. T168 measured PLAY_VIS_NEAR drifting 9.2% across its
     * matrix, and this campaign is deciding between arms separated by less
     * than that, so carrying the pose would add a column no conclusion could
     * rest on.
     */
    private static final String[] T169_POSES = {"FAR", "SIDE"};

    /** Descriptor residency caps, measured on the anchor program. */
    private static final int[] T168_DESCRIPTOR_CAPS = {1, 2, 4, 6, 8};

    /**
     * The T168 matrix. Anchor, the three footprint curves, the T167 arms they
     * have to beat, the termination reconfirmation, the combined stack, then the
     * anchor again as the drift control.
     *
     * <p>The descriptor-count arms at the end are the upstream-binning ceiling:
     * capping how many descriptors are resident is the only way to measure what
     * a perfect candidate filter could return, because it removes them before
     * the loop rather than inside it. They change what is drawn, which is why
     * they are reported as a ceiling and never as a shippable speedup.
     */
    private static final T166Arm[] T168_ARMS = buildT168Arms();

    private static T166Arm[] buildT168Arms() {
        java.util.List<T166Arm> arms = new java.util.ArrayList<>();
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL));
        for (CoreCostDiagnosticProgram arm : new CoreCostDiagnosticProgram[] {
                CoreCostDiagnosticProgram.T168_FOOTPRINT_CONSERVATIVE,
                CoreCostDiagnosticProgram.T168_FOOTPRINT_BALANCED,
                CoreCostDiagnosticProgram.T168_FOOTPRINT_AGGRESSIVE,
                CoreCostDiagnosticProgram.T167_CURVE_A_LATE,
                CoreCostDiagnosticProgram.T166_DISTANCE_STEP,
                CoreCostDiagnosticProgram.T167_TERM045,
                CoreCostDiagnosticProgram.T168_STACK,
                CoreCostDiagnosticProgram.T168_STACK_BALANCED}) {
            arms.add(new T166Arm(arm));
        }
        arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL, 0.2500F,
                StormOptimizationDiagnosticMode.NORMAL_PRODUCTION));
        // The upstream-binning ceiling. Capping residency removes descriptors
        // BEFORE the loop, which is the thing a candidate structure would do and
        // the thing nearest-K could not.
        for (int cap : T168_DESCRIPTOR_CAPS) {
            arms.add(new T166Arm(CoreCostDiagnosticProgram.LEAN_FINAL, Float.NaN,
                    StormOptimizationDiagnosticMode.NORMAL_PRODUCTION, cap));
        }
        return arms.toArray(new T166Arm[0]);
    }

    private static T166Arm t168Arm() {
        return T168_ARMS[Math.max(0, Math.min(T168_ARMS.length - 1, t141ArmIndex))];
    }

    private static final StormOptimizationDiagnosticMode[] T168_OPTIMIZATION_ARMS =
            buildT168OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT168OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T168_ARMS.length];
        java.util.Arrays.fill(modes, StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
        return modes;
    }

    private static final CoreCostDiagnosticProgram[] T168_IMAGE_ARMS = {
            CoreCostDiagnosticProgram.T168_FOOTPRINT_CONSERVATIVE,
            CoreCostDiagnosticProgram.T168_FOOTPRINT_BALANCED,
            CoreCostDiagnosticProgram.T168_FOOTPRINT_AGGRESSIVE,
            CoreCostDiagnosticProgram.T167_CURVE_A_LATE,
            CoreCostDiagnosticProgram.T167_TERM045,
            CoreCostDiagnosticProgram.T168_STACK,
            CoreCostDiagnosticProgram.T168_STACK_BALANCED
    };

    private static final String[] T168_POSES = {"FAR", "SIDE", "PLAY_VIS_NEAR"};

    /**
     * FAR and SIDE are the poses the baseline is owed at. PLAY_VIS_NEAR is
     * carried as the continuity control: T163 measured it at 24.517 ms on this
     * ladder, so a run that reproduces that has a comparable fixture and a run
     * that does not has something else wrong before any arm is interpreted.
     */
    private static final String[] T166_POSES = {"FAR", "SIDE", "PLAY_VIS_NEAR"};
    private static final String[] T167_POSES = {"FAR", "SIDE", "PLAY_VIS_NEAR"};
    private static final StormOptimizationDiagnosticMode[] T166_OPTIMIZATION_ARMS =
            buildT166OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT166OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T166_ARMS.length];
        for (int index = 0; index < T166_ARMS.length; index++) {
            modes[index] = T166_ARMS[index].mode();
        }
        return modes;
    }

    private static T166Arm t166Arm() {
        return T166_ARMS[Math.max(0, Math.min(T166_ARMS.length - 1, t141ArmIndex))];
    }

    private static boolean t162Run;
    private static boolean t162OriginalHistoryEnabled;
    private static int t162ExpectedDescriptors;
    private static int t162ImageStep;
    private static StormReferenceImageComparison.Reference t162AnchorImage;

    /** One T162 cell: which linked program, and how many descriptors it may see. */
    private record T162Arm(CoreCostDiagnosticProgram program, int descriptorLimit) {
        String label() {
            return descriptorLimit < 0
                    ? program.serializedName()
                    : program.serializedName() + "@d" + descriptorLimit;
        }
    }

    /**
     * The attribution matrix. The first three arms run the real raymarch; the
     * rest are the fixed-work ladder, whose consecutive differences are the
     * attribution. The trailing entries repeat two ladder rungs at capped
     * descriptor counts to answer whether cost scales with descriptor count -
     * the evidence that decides for or against descriptor binning.
     */
    private static final T162Arm[] T162_ARMS = buildT162Arms();

    private static T162Arm[] buildT162Arms() {
        java.util.List<T162Arm> arms = new java.util.ArrayList<>();
        arms.add(new T162Arm(CoreCostDiagnosticProgram.LEAN_FINAL, -1));
        arms.add(new T162Arm(CoreCostDiagnosticProgram.T162_NO_LIGHT, -1));
        arms.add(new T162Arm(CoreCostDiagnosticProgram.T162_NO_RAIN, -1));
        for (CoreCostDiagnosticProgram rung : new CoreCostDiagnosticProgram[] {
                CoreCostDiagnosticProgram.T162_FW1_ADDRESS,
                CoreCostDiagnosticProgram.T162_FW2_CANDIDATE,
                CoreCostDiagnosticProgram.T162_FW3_DESCRIPTOR,
                CoreCostDiagnosticProgram.T162_FW4_SHAPE,
                CoreCostDiagnosticProgram.T162_FW5_NODETAIL,
                CoreCostDiagnosticProgram.T162_FW6_NORAIN,
                CoreCostDiagnosticProgram.T162_FW7_DENSITY}) {
            arms.add(new T162Arm(rung, -1));
        }
        for (int limit : new int[] {1, 2, 4, 6, 8, 10}) {
            arms.add(new T162Arm(CoreCostDiagnosticProgram.T162_FW3_DESCRIPTOR, limit));
            arms.add(new T162Arm(CoreCostDiagnosticProgram.T162_FW4_SHAPE, limit));
        }
        return arms.toArray(new T162Arm[0]);
    }

    private static final String[] T162_POSES = {"PLAY_VIS_NEAR"};
    private static final StormOptimizationDiagnosticMode[] T162_OPTIMIZATION_ARMS =
            buildT162OptimizationArms();

    private static StormOptimizationDiagnosticMode[] buildT162OptimizationArms() {
        StormOptimizationDiagnosticMode[] modes =
                new StormOptimizationDiagnosticMode[T162_ARMS.length];
        java.util.Arrays.fill(modes, StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
        return modes;
    }

    private static T162Arm t162Arm() {
        return T162_ARMS[Math.max(0, Math.min(T162_ARMS.length - 1, t141ArmIndex))];
    }
    private static final String[] T140_MODE_POSES = {"PLAY_VIS_NEAR"};
    /** 0 request mask, 1 collect mask, 2 request final, 3 collect final, 4 done. */
    private static int t140ImageStep;
    private static final java.util.Map<String, T140Coverage> T140_COVERAGE =
            new java.util.LinkedHashMap<>();

    /** Per-pose screen-coverage census, counted from two captured images. */
    private record T140Coverage(
            int totalPixels, int potentialPixels, int contributingPixels,
            int width, int height) {
        double potentialPercent() {
            return totalPixels <= 0 ? 0.0D : 100.0D * potentialPixels / totalPixels;
        }

        double contributingPercent() {
            return totalPixels <= 0 ? 0.0D : 100.0D * contributingPixels / totalPixels;
        }
    }
    private static final StormOptimizationDiagnosticMode[] T161_OPTIMIZATION_ARMS = {
            StormOptimizationDiagnosticMode.NORMAL_PRODUCTION,
            StormOptimizationDiagnosticMode.NORMAL_PRODUCTION
    };
    private static boolean t161Run;
    private static boolean t161ImageRequested;
    private static boolean t161ArmImageDone;
    private static final java.util.Map<String, StormReferenceImageComparison.Reference>
            T161_IMAGES = new java.util.LinkedHashMap<>();

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
        if (programArmCampaign()) {
            T166Arm arm = t181Run ? t181Arm()
                    : t180Run ? t180Arm()
                    : t179Run ? t179Arm()
                    : t178Run ? t178Arm()
                    : t177Run ? t177Arm()
                    : t176Run ? t176Arm()
                    : t175Run ? t175Arm()
                    : t174Run ? t174Arm()
                    : t173Run ? t173Arm()
                    : t172Run ? t172Arm()
                    : t171Run ? t171Arm()
                    : t170Run ? t170Arm()
                    : t169Run ? t169Arm()
                    : t168Run ? t168Arm()
                    : t167Run ? t167Arm() : t166Arm();
            VolumetricCloudDebugConfig.setFinalProgramOverride(arm.program());
            VolumetricCloudDebugConfig.setFixedResolutionScale(arm.resolutionScale());
            VolumetricCloudDebugConfig.setDescriptorCountLimit(arm.descriptorLimit());
            VolumetricCloudDebugConfig.setT136ConstantLighting(false);
            // The oracle arms need their capture pass; everything else must
            // upload exactly the production mode its program bakes.
            VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(arm.mode());
            // T171 varies the sampled-frame count per arm to find the minimum
            // count that yields a stable percentile. Every other campaign keeps
            // the budget its own begin() installed.
            if (arm.sampleFrames() > 0) {
                StormT135PerformanceProfile.setCellBudget(30, arm.sampleFrames());
            }
            // One temporal state for the whole matrix, or the comparison is
            // between history situations rather than between programs.
            VolumetricCloudDebugConfig.setHistoryEnabled(false);
            return;
        }
        if (t163Run) {
            T163Arm arm = t163Arm();
            VolumetricCloudDebugConfig.setFinalProgramOverride(arm.program());
            VolumetricCloudDebugConfig.setFixedResolutionScale(arm.resolutionScale());
            VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
            VolumetricCloudDebugConfig.setT136ConstantLighting(false);
            VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(
                    StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
            VolumetricCloudDebugConfig.setHistoryEnabled(false);
            return;
        }
        if (t162Run) {
            T162Arm arm = t162Arm();
            VolumetricCloudDebugConfig.setFinalProgramOverride(arm.program());
            VolumetricCloudDebugConfig.setDescriptorCountLimit(arm.descriptorLimit());
            VolumetricCloudDebugConfig.setT136ConstantLighting(false);
            VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(
                    StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
            // Every arm must share one temporal state or the comparison is
            // between different history situations rather than between programs.
            VolumetricCloudDebugConfig.setHistoryEnabled(false);
            return;
        }
        if (t140Run) {
            // Pin the program under test. Every arm is pinned, including the
            // lean baseline, so a selection bug surfaces as a failed arm rather
            // than as two arms quietly measuring the same program.
            VolumetricCloudDebugConfig.setFinalProgramOverride(t140Arm());
            VolumetricCloudDebugConfig.setT136ConstantLighting(false);
            VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(
                    StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
            return;
        }
        if (t161Run) {
            // Pin the program under test. The lean arm is pinned too rather
            // than left on automatic, so a selection bug shows up as a failed
            // arm instead of silently measuring the monolith twice.
            VolumetricCloudDebugConfig.setFinalProgramOverride(t161Arm());
            VolumetricCloudDebugConfig.setT136ConstantLighting(false);
            VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(
                    StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
            return;
        }
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
        if (t181Run) {
            return t181Arm().label();
        }
        if (t180Run) {
            return t180Arm().label();
        }
        if (t179Run) {
            return t179Arm().label();
        }
        if (t178Run) {
            return t178Arm().label();
        }
        if (t177Run) {
            return t177Arm().label();
        }
        if (t176Run) {
            return t176Arm().label();
        }
        if (t175Run) {
            return t175Arm().label();
        }
        if (t174Run) {
            return t174Arm().label();
        }
        if (t173Run) {
            return t173Arm().label();
        }
        if (t172Run) {
            return t172Arm().label();
        }
        if (t171Run) {
            return t171Arm().label();
        }
        if (t170Run) {
            return t170Arm().label();
        }
        if (t169Run) {
            return t169Arm().label();
        }
        if (t168Run) {
            return t168Arm().label();
        }
        if (t167Run) {
            return t167Arm().label();
        }
        if (t166Run) {
            return t166Arm().label();
        }
        if (t163Run) {
            return t163Arm().label();
        }
        if (t162Run) {
            return t162Arm().label();
        }
        if (t140Run) {
            return t140Arm().serializedName();
        }
        if (t161Run) {
            return t161Arm().serializedName();
        }
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
        if (t181Run) {
            return T181_OPTIMIZATION_ARMS;
        }
        if (t180Run) {
            return T180_OPTIMIZATION_ARMS;
        }
        if (t179Run) {
            return T179_OPTIMIZATION_ARMS;
        }
        if (t178Run) {
            return T178_OPTIMIZATION_ARMS;
        }
        if (t177Run) {
            return T177_OPTIMIZATION_ARMS;
        }
        if (t176Run) {
            return T176_OPTIMIZATION_ARMS;
        }
        if (t175Run) {
            return T175_OPTIMIZATION_ARMS;
        }
        if (t174Run) {
            return T174_OPTIMIZATION_ARMS;
        }
        if (t173Run) {
            return T173_OPTIMIZATION_ARMS;
        }
        if (t172Run) {
            return T172_OPTIMIZATION_ARMS;
        }
        if (t171Run) {
            return T171_OPTIMIZATION_ARMS;
        }
        if (t170Run) {
            return T170_OPTIMIZATION_ARMS;
        }
        if (t169Run) {
            return T169_OPTIMIZATION_ARMS;
        }
        if (t168Run) {
            return T168_OPTIMIZATION_ARMS;
        }
        if (t167Run) {
            return T167_OPTIMIZATION_ARMS;
        }
        if (t166Run) {
            return T166_OPTIMIZATION_ARMS;
        }
        if (t163Run) {
            return T163_OPTIMIZATION_ARMS;
        }
        if (t162Run) {
            return T162_OPTIMIZATION_ARMS;
        }
        if (t140Run) {
            return T140_OPTIMIZATION_ARMS;
        }
        if (t161Run) {
            return T161_OPTIMIZATION_ARMS;
        }
        return t153OracleRun ? T153_ARMS : T141_ARMS;
    }

    private static CoreCostDiagnosticProgram t140Arm() {
        return T140_ARMS[Math.max(0, Math.min(T140_ARMS.length - 1, t141ArmIndex))];
    }

    private static CoreCostDiagnosticProgram t161Arm() {
        return T161_ARMS[Math.max(0, Math.min(T161_ARMS.length - 1, t141ArmIndex))];
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
        if (pose.startsWith("T140_")) {
            // The coverage series deliberately contains views this guard exists
            // to reject - looking away from the storm is the measurement, not a
            // fault. Record the geometric verdict as evidence and continue.
            // What was actually on screen is established far more directly by
            // the per-pose mask and FINAL pixel counts than by this heuristic.
            ProjectAtmosphere.LOGGER.info("T140_VISIBILITY_GEOMETRY pose={} {}",
                    pose,
                    StormFixtureVisibility.evaluate(
                            StormGeometryBuildCoordinator.lobeCount(),
                            fixture.centerX(), fixture.centerZ(),
                            fixture.baseY(), fixture.topY(), fixture.horizontalRadius(),
                            player.getX(), player.getEyeY(), player.getZ(),
                            player.getYRot(), player.getXRot(),
                            minecraft.options.fov().get(),
                            AtmoCommonConfig.CLOUD_RENDER_DISTANCE.get()).format());
            t150Attempts = 0;
            t150CaptureRequested = false;
            advance(Phase.T135_SAMPLE);
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
        if (t181Run) {
            return T181_POSES;
        }
        if (t180Run) {
            return T180_POSES;
        }
        if (t179Run) {
            return T179_POSES;
        }
        if (t178Run) {
            return T178_POSES;
        }
        if (t177Run) {
            return T177_POSES;
        }
        if (t176Run) {
            return T176_POSES;
        }
        if (t175Run) {
            return T175_POSES;
        }
        if (t174Run) {
            return T174_POSES;
        }
        if (t173Run) {
            return T173_POSES;
        }
        if (t172Run) {
            return T172_POSES;
        }
        if (t171Run) {
            return T171_POSES;
        }
        if (t170Run) {
            return T170_POSES;
        }
        if (t169Run) {
            return T169_POSES;
        }
        if (t168Run) {
            return T168_POSES;
        }
        if (t167Run) {
            return T167_POSES;
        }
        if (t166Run) {
            return T166_POSES;
        }
        if (t163Run) {
            return T163_POSES;
        }
        if (t162Run) {
            return T162_POSES;
        }
        if (t140Run) {
            return T140_POSES;
        }
        if (t140ModeRun) {
            return T140_MODE_POSES;
        }
        if (t161Run) {
            return T161_POSES;
        }
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
                t140Run = coverageRunRequested();
                t140ModeRun = fiveModeRunRequested();
                t168Run = footprintRunRequested();
                t169Run = lightingDetailRunRequested();
                t170Run = primaryMarchRunRequested();
                t170OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t171Run = harnessStabilityRunRequested();
                t171OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t172Run = precomputeRunRequested();
                t172OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t173Run = sideStabilityRunRequested();
                t173OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t174Run = groupEntryRunRequested();
                t174OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t175Run = densityNecessityRunRequested();
                t175OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t176Run = lightMarchRunRequested();
                t176OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t177Run = lightReuseRunRequested();
                t177OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t178Run = lobeSupportRunRequested();
                t178OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t179Run = dominancePruningRunRequested();
                t179OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t180Run = consumerAttributionRunRequested();
                t180OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t181Run = probeRefineRunRequested();
                t181OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t168OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t167Run = refinementRunRequested();
                t167OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t166Run = baselineRunRequested();
                t166OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t166LastRadius = -1.0D;
                t166StableTicks = 0;
                t166GrowthWaitTicks = 0;
                t166PoseCameraRadius = -1.0D;
                t166PoseTargetValid = false;
                t166ArrivalWaitFrames = 0;
                t166ImageStep = 0;
                t166AnchorImage = null;
                t163Run = precipitationRunRequested();
                t163OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t163ImageStep = 0;
                t163AnchorImage = null;
                t162Run = attributionRunRequested();
                t162OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t162ExpectedDescriptors = 0;
                t162ImageStep = 0;
                t162AnchorImage = null;
                T162_REJECTED.clear();
                t140ImageStep = 0;
                T140_COVERAGE.clear();
                t161Run = specializationRunRequested();
                t161ImageRequested = false;
                t161ArmImageDone = false;
                T161_IMAGES.clear();
                StormReferenceImageCapture.cancel();
                t153OracleRun = oracleRunRequested();
                t153OriginalHistoryEnabled = VolumetricCloudDebugConfig.historyEnabled();
                t141EvaluationRun =
                        evaluationRunRequested() || t153OracleRun || t161Run
                                || t140Run || t162Run || t163Run || t166Run || t167Run
                                || t168Run
                                || t169Run
                                || t170Run
                                || t171Run
                                || t172Run
                                || t173Run
                                || t174Run
                                || t175Run
                                || t176Run
                                || t177Run
                                || t178Run
                                || t179Run
                                || t180Run
                                || t181Run;
                t141ArmIndex = 0;
                t141ArmAttempts = 0;
                t141CellPending = false;
                t153LastCounterInvalid = false;
                T153_COUNTERS.clear();
                T153_FIXTURES.clear();
                t153PoseAttempts = 0;
                if (t141EvaluationRun) {
                    if (!t153OracleRun && !t161Run && !t140Run && !t162Run && !t163Run
                            && !t166Run && !t167Run && !t168Run && !t169Run && !t170Run
                            && !t171Run && !t172Run && !t173Run && !t174Run
                            && !t175Run && !t176Run && !t177Run && !t178Run
                            && !t179Run && !t180Run && !t181Run) {
                        resolveT141Poses();
                    }
                    StormT135PerformanceProfile.setCellBudget(30, 60);
                    AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.set(
                            AtmoCommonConfig.CloudRaymarchQuality.ULTRA);
                    applyT141Arm();
                    ProjectAtmosphere.LOGGER.info(
                            "{}_BEGIN poses={} arms={} mode=ULTRA steps=96"
                                    + " resolutionScale={} target={}x{}",
                            t181Run ? "T181_PROBE_REFINE"
                                    : t180Run ? "T180_CONSUMERS"
                                    : t179Run ? "T179_DOMINANCE"
                                    : t178Run ? "T178_LOBE_SUPPORT"
                                    : t177Run ? "T177_LIGHT_REUSE"
                                    : t176Run ? "T176_LIGHT_MARCH"
                                    : t175Run ? "T175_DENSITY_NECESSITY"
                                    : t174Run ? "T174_GROUP_ENTRY"
                                    : t173Run ? "T173_SIDE_STABILITY"
                                    : t172Run ? "T172_DESCRIPTOR_PRECOMPUTE"
                                    : t171Run ? "T171_HARNESS_STABILITY"
                                    : t170Run ? "T170_PRIMARY_MARCH"
                                    : t169Run ? "T169_LIGHTING_DETAIL"
                                    : t168Run ? "T168_FOOTPRINT"
                                    : t167Run ? "T167_REFINE"
                                    : t166Run ? "T166_BASELINE"
                                    : t163Run ? "T163_PRECIPITATION"
                                    : t162Run ? "T162_ATTRIBUTION"
                                    : t140Run ? "T140_COVERAGE"
                                    : (t161Run ? "T161_SPECIALIZATION"
                                        : (t153OracleRun ? "T153_ORACLE" : "T141_EVAL")),
                            sweepPoses().length, activeEvaluationArms().length,
                            fmt(T141_RESOLUTION_SCALE), T135_WIDTH, T135_HEIGHT);
                    // Printed before any cell so a run's guard state is a fact in
                    // the log rather than an assumption about the code.
                    ProjectAtmosphere.LOGGER.info(
                            "POSE_GUARDS armed={} arrivalCheck={} radiusRebuild={}"
                                    + " fixtureIdentity={} driftControl={}"
                                    + " arrivalToleranceBlocks={} radiusTolerance={}",
                            poseGuardsArmed(), poseGuardsArmed(),
                            poseGuardsArmed() && !t153OracleRun,
                            poseGuardsArmed(),
                            programArmCampaign(),
                            fmt((float) T166_ARRIVAL_TOLERANCE_BLOCKS),
                            fmt((float) T166_POSE_RADIUS_TOLERANCE));
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
                            sweepPoses().length, T135_MODES.length, T135_WIDTH, T135_HEIGHT);
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
                // Every sweep campaign needs this as much as T153 does: its
                // arms are compared against an anchor measured earlier in the
                // same pose, and a failed cell respawns the storm. Without an
                // identity check the anchor and the arm can describe two
                // different storms, and the ratio between them is then a fixture
                // difference reported as a speedup. Descriptor count alone does
                // not catch it - the replacement also carries ten.
                if (poseGuardsArmed()) {
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
                            finish((t166Run ? "t166" : "t153")
                                    + "_pose_fixture_unstable:" + pose);
                            return;
                        }
                        advance(StormGeometryBuildCoordinator.lobeCount() >= 10
                                ? Phase.T135_MOVE : Phase.T135_RESPAWN);
                        return;
                    }
                }
                if (t166Run && !t166FixtureFinishedGrowing(fixture)) {
                    return;
                }
                double radius = fixture.horizontalRadius();
                if (t166Run) {
                    t166PoseCameraRadius = radius;
                }
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
                    // T140 screen-coverage series. Every one of these stands
                    // exactly where PLAY_VIS_NEAR stands and differs only in
                    // where it looks, so cost differences between them are
                    // attributable to how much cloud is on screen and to
                    // nothing else - not distance, altitude, or fixture.
                    case "T140_PARTIAL", "T140_EDGE", "T140_AWAY_180", "T140_AWAY_DOWN" -> {
                        x = fixture.centerX() + radius * 1.6D;
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
                        || sweepPose().startsWith("PLAY")
                        || sweepPose().startsWith("T140_")) {
                    double dy = midY - y;
                    double horizontal = Math.hypot(fixture.centerX() - x, fixture.centerZ() - z);
                    pitch = horizontal < 1.0D
                            ? (dy >= 0.0D ? -89.0F : 89.0F)
                            : (float) -Math.toDegrees(Math.atan2(dy, horizontal));
                }
                // The coverage series turns the camera away from the storm by a
                // fixed amount rather than moving it. AWAY_DOWN additionally
                // pitches at the ground, whose rays leave the cloud slab almost
                // immediately: it is the strongest available zero-coverage case.
                switch (sweepPose()) {
                    case "T140_PARTIAL" -> yaw += 30.0F;
                    case "T140_EDGE" -> yaw += 75.0F;
                    case "T140_AWAY_180" -> yaw += 180.0F;
                    case "T140_AWAY_DOWN" -> {
                        yaw += 180.0F;
                        pitch = 88.0F;
                    }
                    default -> { }
                }
                player.connection.sendCommand(String.format(Locale.ROOT,
                        "tp @s %.5f %.5f %.5f %.3f %.3f", x, y, z, yaw, pitch));
                t166PoseTargetX = x;
                t166PoseTargetY = y;
                t166PoseTargetZ = z;
                t166PoseTargetValid = poseGuardsArmed();
                t166ArrivalWaitFrames = 0;
                t135SettleFrames = 0;
                advance(Phase.T135_SETTLE);
            }
            case T135_SETTLE -> {
                if (++t135SettleFrames < T135_POSE_SETTLE_FRAMES) {
                    return;
                }
                if (t166PoseTargetValid && !t166CameraHasArrived(player)) {
                    return;
                }
                advance(Phase.T135_VERIFY);
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
                                sweepPose(),
                                T135_MODES[Math.max(0, t135ModeIndex - 1)], t135CellRetries);
                        t135CellRetries = 0;
                        // Fall through to the next mode rather than looping.
                    } else {
                        ProjectAtmosphere.LOGGER.info(
                                "T136_PROFILE respawning fixture before retrying {}/{}",
                                sweepPose(),
                                T135_MODES[Math.max(0, t135ModeIndex - 1)]);
                        t135ModeIndex = Math.max(0, t135ModeIndex - 1);
                        advance(Phase.T135_RESPAWN);
                        return;
                    }
                }
                t135CellRetries = 0;
                if (t135ModeIndex >= T135_MODES.length) {
                    boolean attributionPose = "SIDE".equals(sweepPose())
                            || "PLAY_NEAR".equals(sweepPose());
                    if (attributionPose && t135Arm < 2) {
                        t135Arm++;
                        t135ModeIndex = 0;
                        applyT135Arm();
                        ProjectAtmosphere.LOGGER.info(
                                "T136_PROFILE attribution arm {} begins at {}",
                                t135ArmName(), sweepPose());
                        return;
                    }
                    t135Arm = 0;
                    applyT135Arm();
                    t135LightingArm = false;
                    t135ModeIndex = 0;
                    t135PoseIndex++;
                    if (t135PoseIndex >= sweepPoses().length) {
                        advance(Phase.T135_REPORT);
                    } else {
                        advance(Phase.T135_MOVE);
                    }
                    return;
                }
                AtmoCommonConfig.CloudRaymarchQuality quality = T135_MODES[t135ModeIndex];
                AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.set(quality);
                if (!StormT135PerformanceProfile.begin(sweepPose(), quality,
                        t135ArmName())) {
                    // A refusal consumes a retry. Without this the sweep loops
                    // between refusing and respawning forever, which is exactly
                    // how PLAY_MID/Ultra, PLAY_HIGH and CLEAR were lost.
                    if (++t135CellRetries > T135_MAX_CELL_RETRIES) {
                        ProjectAtmosphere.LOGGER.warn(
                                "T136_PROFILE abandoning {}/{}: fixture never became"
                                        + " measurable after {} attempts",
                                sweepPose(), quality, t135CellRetries);
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
                if ((t162Run && t162Arm().program().fixedWork())
                        || (t166Run && t166Arm().program().fixedWork())
                        || (t167Run && t167Arm().program().fixedWork())
                        || (t168Run && t168Arm().program().fixedWork())
                        || (t169Run && t169Arm().program().fixedWork())
                        || (t170Run && t170Arm().program().fixedWork())
                        || (t171Run && t171Arm().program().fixedWork())
                        || (t172Run && t172Arm().program().fixedWork())
                        || (t173Run && t173Arm().program().fixedWork())
                        || (t174Run && t174Arm().program().fixedWork())
                        || (t175Run && t175Arm().program().fixedWork())
                        || (t176Run && t176Arm().program().fixedWork())
                        || (t177Run && t177Arm().program().fixedWork())
                        || (t178Run && t178Arm().program().fixedWork())
                        || (t179Run && t179Arm().program().fixedWork())
                        || (t180Run && t180Arm().program().fixedWork())
                        || (t181Run && t181Arm().program().fixedWork())) {
                    // A fixed-work arm renders a checksum, not the production
                    // scene, so production workload counters captured beside it
                    // would describe a different program. The production-context
                    // arms carry the counters for this campaign.
                    advance(Phase.T135_SAMPLE);
                    return;
                }
                if (!t135CountersRequested) {
                    t135CountersRequested = true;
                    // The workload counters live behind debug views 22/23, which
                    // every lean and oracle program bakes off. Releasing the pin
                    // lets the capture link the diagnostic monolith, so the
                    // counters describe the production work for this pose rather
                    // than returning zeroes from a program that cannot count.
                    if (t140Run || t162Run || t163Run || programArmCampaign()) {
                        VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    }
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
                        if (t140Run || t162Run || t163Run || programArmCampaign()) {
                            applyT141Arm();
                        }
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
                if (t140Run || t162Run || t163Run || programArmCampaign()) {
                    applyT141Arm();
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
                if (t181Run) {
                    ProjectAtmosphere.LOGGER.info(buildT181ProbeRefineReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t181OriginalHistoryEnabled);
                }
                if (t180Run) {
                    ProjectAtmosphere.LOGGER.info(buildT180ConsumerReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t180OriginalHistoryEnabled);
                }
                if (t179Run) {
                    ProjectAtmosphere.LOGGER.info(buildT179DominanceReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t179OriginalHistoryEnabled);
                }
                if (t178Run) {
                    ProjectAtmosphere.LOGGER.info(buildT178LobeSupportReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t178OriginalHistoryEnabled);
                }
                if (t177Run) {
                    ProjectAtmosphere.LOGGER.info(buildT177LightReuseReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t177OriginalHistoryEnabled);
                }
                if (t176Run) {
                    ProjectAtmosphere.LOGGER.info(buildT176LightMarchReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t176OriginalHistoryEnabled);
                }
                if (t175Run) {
                    ProjectAtmosphere.LOGGER.info(buildT175DensityNecessityReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t175OriginalHistoryEnabled);
                }
                if (t174Run) {
                    ProjectAtmosphere.LOGGER.info(buildT174GroupEntryReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t174OriginalHistoryEnabled);
                }
                if (t173Run) {
                    ProjectAtmosphere.LOGGER.info(buildT173SideStabilityReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t173OriginalHistoryEnabled);
                }
                if (t172Run) {
                    ProjectAtmosphere.LOGGER.info(buildT172PrecomputeReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t172OriginalHistoryEnabled);
                }
                if (t171Run) {
                    ProjectAtmosphere.LOGGER.info(buildT171HarnessStabilityReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t171OriginalHistoryEnabled);
                }
                if (t170Run) {
                    ProjectAtmosphere.LOGGER.info(buildT170PrimaryMarchReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t170OriginalHistoryEnabled);
                }
                if (t169Run) {
                    ProjectAtmosphere.LOGGER.info(buildT169LightingDetailReport());
                }
                if (t168Run) {
                    ProjectAtmosphere.LOGGER.info(buildT168FootprintReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t168OriginalHistoryEnabled);
                }
                if (t167Run) {
                    ProjectAtmosphere.LOGGER.info(buildT167RefinementReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t167OriginalHistoryEnabled);
                }
                if (t166Run) {
                    ProjectAtmosphere.LOGGER.info(buildT166BaselineReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setOptimizationDiagnosticMode(
                            StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t166OriginalHistoryEnabled);
                }
                if (t163Run) {
                    ProjectAtmosphere.LOGGER.info(buildT163PrecipitationReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t163OriginalHistoryEnabled);
                }
                if (t162Run) {
                    ProjectAtmosphere.LOGGER.info(buildT162AttributionReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                    VolumetricCloudDebugConfig.setDescriptorCountLimit(-1);
                    VolumetricCloudDebugConfig.setHistoryEnabled(t162OriginalHistoryEnabled);
                }
                if (t140Run) {
                    ProjectAtmosphere.LOGGER.info(buildT140CoverageReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                }
                if (t161Run) {
                    ProjectAtmosphere.LOGGER.info(buildT161SpecializationReport());
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null);
                }
                if (t153OracleRun) {
                    ProjectAtmosphere.LOGGER.info(buildT153DecisionReport());
                    VolumetricCloudDebugConfig.setHistoryEnabled(t153OriginalHistoryEnabled);
                    finish("t153_complete");
                } else if (t168Run) {
                    finish("t168_complete");
                } else if (t167Run) {
                    finish("t167_complete");
                } else if (t166Run) {
                    finish("t166_complete");
                } else if (t163Run) {
                    finish("t163_complete");
                } else if (t162Run) {
                    finish("t162_complete");
                } else if (t140Run) {
                    finish("t140_complete");
                } else if (t161Run) {
                    finish("t161_complete");
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
                    || ((t162Run || t163Run || t166Run || t167Run
                            || (t168Run && t168Arm().descriptorLimit() < 0))
                        && !t162QualifyFixture(pose, t141ArmName(), "after"))
                    || (!pose.startsWith("CLEAR")
                        && StormGeometryBuildCoordinator.lobeCount() <= 0);
            if ((t162Run || t163Run || programArmCampaign())
                    && failed) {
                // A cell whose fixture moved is discarded outright rather than
                // averaged in. This is the hazard the Ultra recovery sweep hit:
                // it qualified the pose once and kept measuring after the storm
                // stopped contributing.
                StormT135PerformanceProfile.discardLastCell("t162_fixture_changed");
                T162_REJECTED.add(t141ArmName() + ":fixture_changed");
                ProjectAtmosphere.LOGGER.warn(
                        "T162_REJECT arm={} reason=fixture_changed descriptors={} expected={}",
                        t141ArmName(), StormGeometryBuildCoordinator.lobeCount(),
                        t162ExpectedDescriptors);
            }
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
            t161ArmImageDone = false;
            t161ImageRequested = false;
        }
        if (t141ArmIndex >= activeEvaluationArms().length) {
            t141ArmIndex = 0;
            t141ArmAttempts = 0;
            t153PoseAttempts = 0;
            t140ImageStep = 0;
            t140FinalImage = null;
            t162ImageStep = 0;
            t162AnchorImage = null;
            t163ImageStep = 0;
            t163AnchorImage = null;
            t166ImageStep = 0;
            t166AnchorImage = null;
            t162ExpectedDescriptors = 0;
            applyT141Arm();
            t135PoseIndex++;
            if (t135PoseIndex >= sweepPoses().length) {
                advance(Phase.T135_REPORT);
            } else {
                advance(Phase.T135_MOVE);
            }
            return;
        }
        if (t140Run && t140ImageStep < 6) {
            tickT140Coverage(pose);
            return;
        }
        // T153 runs its own equivalent pose reset, so it is excluded here
        // rather than having both fire on the same fixture change.
        if (poseGuardsArmed() && !t153OracleRun && t166PoseCameraRebuildNeeded(pose)) {
            return;
        }
        if (programArmCampaign() && t141ArmIndex == 0
                && t166ImageStep < t166ImageArms().length * 2 + 2) {
            tickT166Images(pose);
            return;
        }
        if (t163Run && t141ArmIndex == 0 && t163ImageStep < 6) {
            tickT163Images(pose);
            return;
        }
        if (t162Run && t141ArmIndex == 0 && t162ImageStep < 6) {
            // Captured before applyT141Arm(), which re-pins the override to the
            // current arm on every tick: running after it meant every capture
            // silently rendered the anchor program, and all three frames then
            // compared identical - including the lighting arm, which cannot be.
            // The three frames are also taken back to back, because an earlier
            // attempt captured them minutes apart and the fixture drifted
            // between them.
            tickT162Images();
            return;
        }
        applyT141Arm();
        String arm = t141ArmName();
        if (t161Run && !t161ArmImageDone) {
            // The image A/B must precede the timing cell: the capture bypasses
            // history and pins the world clock, so both arms are compared on
            // the same deterministic frame rather than on whatever temporal
            // state each arm happened to accumulate.
            if (StormReferenceImageCapture.active()) {
                return;
            }
            if (t161ImageRequested) {
                StormReferenceImageComparison.Reference captured =
                        StormReferenceImageCapture.latestResult();
                if (captured == null) {
                    if (++t141ArmAttempts < T138_MAX_ARM_ATTEMPTS) {
                        t161ImageRequested = false;
                        return;
                    }
                    finish("t161_image_capture_failed:" + arm);
                    return;
                }
                T161_IMAGES.put(arm, captured);
                ProjectAtmosphere.LOGGER.info(
                        "T161_IMAGE arm={} boundProgram={} {}",
                        arm, VolumetricCloudRenderer.lastProgram().serializedName(),
                        captured.format());
                t161ImageRequested = false;
                t161ArmImageDone = true;
                t141ArmAttempts = 0;
            } else {
                String requested = StormReferenceImageCapture.request("t161_" + arm);
                if (requested.startsWith("acquiring")) {
                    t161ImageRequested = true;
                }
                return;
            }
        }
        // A residency-capped arm deliberately reduces the descriptor count, so
        // the qualifier - which exists to catch a fixture that decayed - would
        // reject every one of them. The cap is the measurement, not a decay.
        boolean capped = t168Run && t168Arm().descriptorLimit() > 0;
        if (programArmCampaign() && !capped
                && !t162QualifyFixture(pose, arm, "before")) {
            if (++t141ArmAttempts < T138_MAX_ARM_ATTEMPTS) {
                advance(Phase.T135_RESPAWN);
                return;
            }
            T162_REJECTED.add(arm + ":unqualified_before");
            ProjectAtmosphere.LOGGER.warn(
                    "T166_REJECT arm={} reason=unqualified_before attempts={}",
                    arm, t141ArmAttempts);
            t141ArmAttempts = 0;
            t141ArmIndex++;
            return;
        }
        if (t163Run && !t162QualifyFixture(pose, arm, "before")) {
            if (++t141ArmAttempts < T138_MAX_ARM_ATTEMPTS) {
                advance(Phase.T135_RESPAWN);
                return;
            }
            T162_REJECTED.add(arm + ":unqualified_before");
            ProjectAtmosphere.LOGGER.warn(
                    "T163_REJECT arm={} reason=unqualified_before attempts={}",
                    arm, t141ArmAttempts);
            t141ArmAttempts = 0;
            t141ArmIndex++;
            return;
        }
        if (t162Run && !t162QualifyFixture(pose, arm, "before")) {
            if (++t141ArmAttempts < T138_MAX_ARM_ATTEMPTS) {
                advance(Phase.T135_RESPAWN);
                return;
            }
            T162_REJECTED.add(arm + ":unqualified_before");
            ProjectAtmosphere.LOGGER.warn(
                    "T162_REJECT arm={} reason=unqualified_before attempts={}",
                    arm, t141ArmAttempts);
            t141ArmAttempts = 0;
            t141ArmIndex++;
            return;
        }
        logCellState(pose, arm);
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
     * Whether the pose guards are armed.
     *
     * <p>These are deliberately NOT keyed to a per-campaign flag. T167 lost the
     * arrival guard because it was armed by {@code t166PoseTargetValid =
     * t166Run} and wiring a new campaign through the rest of the harness did
     * not extend that one assignment - a guard that existed, worked, and was
     * simply not switched on. Every campaign that drives poses through the
     * evaluation sweep gets them, so a new campaign inherits them by default
     * instead of having to remember.
     *
     * <p>Arming them everywhere is safe. The arrival guard only waits until the
     * camera has reached the position the pose asked for, which is a no-op
     * whenever the teleport already landed inside the settle window. The rebuild
     * guard only fires when the fixture radius moves more than 2% from the value
     * the camera was computed from, which a stable fixture never does.
     */
    private static boolean poseGuardsArmed() {
        return t141EvaluationRun;
    }

    /**
     * True once the player is standing where the pose asked it to stand.
     *
     * <p>Bounded, and on expiry it proceeds and says so: a pose that cannot be
     * reached should produce a logged, measurable cell rather than a hung
     * campaign, and the drift control will catch it downstream either way.
     */
    private static boolean t166CameraHasArrived(LocalPlayer player) {
        double dx = player.getX() - t166PoseTargetX;
        double dy = player.getY() - t166PoseTargetY;
        double dz = player.getZ() - t166PoseTargetZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= T166_ARRIVAL_TOLERANCE_BLOCKS) {
            if (t166ArrivalWaitFrames > 0) {
                ProjectAtmosphere.LOGGER.info(
                        "T166_ARRIVAL pose={} reached after {} extra frames residual={}",
                        sweepPose(), t166ArrivalWaitFrames, fmt((float) distance));
            }
            t166ArrivalWaitFrames = 0;
            return true;
        }
        if (++t166ArrivalWaitFrames > T166_ARRIVAL_TIMEOUT_FRAMES) {
            ProjectAtmosphere.LOGGER.warn(
                    "T166_ARRIVAL pose={} never reached its camera; residual={} blocks"
                            + " after {} frames - proceeding and recording it",
                    sweepPose(), fmt((float) distance), t166ArrivalWaitFrames);
            t166ArrivalWaitFrames = 0;
            return true;
        }
        return false;
    }

    /**
     * Rebuilds the pose when the storm has outgrown the camera.
     *
     * <p>Every structural pose is a multiple of the fixture's horizontal
     * radius, so a radius that moves after the camera was placed silently
     * changes what the pose means. The arms measured before the change and
     * those measured after then describe two different framings, and the ratio
     * between them is a scene difference reported as a speedup.
     *
     * <p>Discarding the pose and recomputing the camera is the whole response.
     * No respawn: the storm is fine, it is the camera that is stale.
     */
    private static boolean t166PoseCameraRebuildNeeded(String pose) {
        if (t166PoseCameraRadius <= 0.0D) {
            return false;
        }
        StormPerformanceBaseline.SuiteFixture fixture =
                StormPerformanceBaseline.suiteFixture();
        if (fixture == null) {
            return false;
        }
        double radius = fixture.horizontalRadius();
        if (radius <= 0.0D
                || Math.abs(radius - t166PoseCameraRadius)
                    <= T166_POSE_RADIUS_TOLERANCE * t166PoseCameraRadius) {
            return false;
        }
        ProjectAtmosphere.LOGGER.warn(
                "T166_POSE_REBUILD pose={} cameraRadius={} currentRadius={};"
                        + " discarding this pose and replacing its camera",
                pose, fmt((float) t166PoseCameraRadius), fmt((float) radius));
        resetT153Pose(pose, "fixture_radius_changed cameraRadius="
                + fmt((float) t166PoseCameraRadius)
                + " currentRadius=" + fmt((float) radius));
        t166ImageStep = 0;
        t166AnchorImage = null;
        advance(Phase.T135_MOVE);
        return true;
    }

    /**
     * True once the fixture's horizontal radius has stopped changing.
     *
     * <p>Every structural pose is a multiple of that radius, so the camera may
     * not be computed until it settles. Waiting on descriptor count is not
     * equivalent: ten descriptors are adopted long before the storm reaches its
     * final extent.
     *
     * <p>Deliberately a stability test rather than an absolute threshold. The
     * fixture's final size depends on the severe scale in force, so any fixed
     * radius would either pass too early at large scales or hang forever at
     * small ones. The wait is bounded; on expiry the pose proceeds and the log
     * says the radius was still moving, so a cell measured against a growing
     * storm is visible in the record rather than silent.
     */
    private static boolean t166FixtureFinishedGrowing(
            StormPerformanceBaseline.SuiteFixture fixture) {
        double radius = fixture.horizontalRadius();
        if (radius <= 0.0D) {
            t166StableTicks = 0;
        } else if (t166LastRadius > 0.0D
                && Math.abs(radius - t166LastRadius)
                    <= T166_GROWTH_TOLERANCE * Math.max(t166LastRadius, 1.0D)) {
            t166StableTicks++;
        } else {
            t166StableTicks = 0;
        }
        t166LastRadius = radius;
        if (t166StableTicks >= T166_GROWTH_STABLE_TICKS) {
            if (t166GrowthWaitTicks > 0) {
                ProjectAtmosphere.LOGGER.info(
                        "T166_GROWTH pose={} settled radius={} afterTicks={}",
                        sweepPose(), fmt((float) radius), t166GrowthWaitTicks);
            }
            t166GrowthWaitTicks = 0;
            return true;
        }
        if (++t166GrowthWaitTicks > T166_GROWTH_TIMEOUT_TICKS) {
            ProjectAtmosphere.LOGGER.warn(
                    "T166_GROWTH pose={} radius still moving after {} ticks"
                            + " (radius={}); proceeding and recording it",
                    sweepPose(), t166GrowthWaitTicks, fmt((float) radius));
            t166GrowthWaitTicks = 0;
            t166StableTicks = T166_GROWTH_STABLE_TICKS;
            return true;
        }
        return false;
    }

    /**
     * Invalidates every cell already measured at one pose, so the pose restarts
     * from its anchor.
     *
     * <p>Retaining the earlier arms after a severe group expires would compare
     * two different density fixtures and manufacture a speedup or a regression
     * out of the difference between them. T153 needs this for its oracle arms
     * and T166 needs it for the same reason: both compare later arms against an
     * anchor measured earlier at the same pose.
     */
    private static void resetT153Pose(String pose, String reason) {
        StormT135PerformanceProfile.discardPose(pose, reason);
        T153_COUNTERS.removeIf(cell -> pose.equals(cell.pose()));
        T153_FIXTURES.remove(pose);
        t162ExpectedDescriptors = 0;
        t166PoseCameraRadius = -1.0D;
        t141ArmIndex = 0;
        t141ArmAttempts = 0;
        t141CellPending = false;
        t153LastCounterInvalid = false;
        applyT141Arm();
        ProjectAtmosphere.LOGGER.warn(
                "{} restarting complete pose={} reason={}",
                t166Run ? "T166_BASELINE" : "T153_ORACLE", pose, reason);
    }

    /**
     * Counts, once per pose, how much of the cloud target could contain cloud
     * at all and how much actually does.
     *
     * <p>Three images are captured at the same pinned clock with history
     * bypassed. The mask program renders the oracle verdict, so its opaque
     * pixels are exactly those whose ray can reach the conservative cloud
     * bound. The lean FINAL image supplies the pixels that actually carried
     * cloud, because the renderer emits a fully transparent pixel for every ray
     * that ends below the composite threshold. The gap between the two is work
     * spent inside the bound that produced nothing; everything outside the mask
     * is work spent on pixels that could never have produced anything at all.
     *
     * <p>The third capture is the oracle rendering the scene. Comparing it
     * against the lean FINAL image is what makes the whole campaign
     * trustworthy: a rejection that changed even one pixel would mean the bound
     * was not conservative and every speedup measured against it would be
     * meaningless. It is checked per pose rather than assumed.
     */
    private static void tickT140Coverage(String pose) {
        if (StormReferenceImageCapture.active()) {
            return;
        }
        switch (t140ImageStep) {
            case 0 -> {
                VolumetricCloudDebugConfig.setFinalProgramOverride(
                        CoreCostDiagnosticProgram.T140_MASK);
                if (StormReferenceImageCapture.request("t140_mask_" + pose)
                        .startsWith("acquiring")) {
                    t140ImageStep = 1;
                }
            }
            case 1 -> {
                StormReferenceImageComparison.Reference mask =
                        StormReferenceImageCapture.latestResult();
                if (mask == null) {
                    t140ImageStep = 0;
                    return;
                }
                t140PotentialPixels = countOpaquePixels(mask, 0.5D);
                t140MaskWidth = mask.width();
                t140MaskHeight = mask.height();
                t140ImageStep = 2;
            }
            case 2 -> {
                VolumetricCloudDebugConfig.setFinalProgramOverride(
                        CoreCostDiagnosticProgram.LEAN_FINAL);
                if (StormReferenceImageCapture.request("t140_final_" + pose)
                        .startsWith("acquiring")) {
                    t140ImageStep = 3;
                }
            }
            case 3 -> {
                StormReferenceImageComparison.Reference rendered =
                        StormReferenceImageCapture.latestResult();
                if (rendered == null) {
                    t140ImageStep = 2;
                    return;
                }
                // 0.002 is the composite threshold main() itself uses: below it
                // the pixel is emitted fully transparent and no cloud reaches
                // the frame.
                t140FinalImage = rendered;
                int contributing = countOpaquePixels(rendered, 0.002D);
                T140Coverage coverage = new T140Coverage(
                        t140MaskWidth * t140MaskHeight, t140PotentialPixels, contributing,
                        t140MaskWidth, t140MaskHeight);
                T140_COVERAGE.put(pose, coverage);
                ProjectAtmosphere.LOGGER.info(String.format(Locale.ROOT,
                        "T140_COVERAGE pose=%s target=%dx%d totalPixels=%d"
                                + " potentialPixels=%d (%.3f%%)"
                                + " contributingPixels=%d (%.3f%%)"
                                + " provablyIrrelevantPixels=%d (%.3f%%)"
                                + " insideBoundButEmptyPixels=%d",
                        pose, coverage.width(), coverage.height(), coverage.totalPixels(),
                        coverage.potentialPixels(), coverage.potentialPercent(),
                        coverage.contributingPixels(), coverage.contributingPercent(),
                        coverage.totalPixels() - coverage.potentialPixels(),
                        100.0D - coverage.potentialPercent(),
                        Math.max(0, coverage.potentialPixels()
                                - coverage.contributingPixels())));
                t140ImageStep = 4;
            }
            case 4 -> {
                VolumetricCloudDebugConfig.setFinalProgramOverride(
                        CoreCostDiagnosticProgram.T140_PIXEL_ORACLE);
                if (StormReferenceImageCapture.request("t140_oracle_" + pose)
                        .startsWith("acquiring")) {
                    t140ImageStep = 5;
                }
            }
            default -> {
                StormReferenceImageComparison.Reference oracle =
                        StormReferenceImageCapture.latestResult();
                if (oracle == null) {
                    t140ImageStep = 4;
                    return;
                }
                if (t140FinalImage == null) {
                    ProjectAtmosphere.LOGGER.warn(
                            "T140_EXACTNESS pose={} evaluated=false reason=missing_final", pose);
                } else {
                    StormReferenceImageComparison.Comparison comparison =
                            StormReferenceImageComparison.compare(t140FinalImage, oracle);
                    ProjectAtmosphere.LOGGER.info(
                            "T140_EXACTNESS pose={} a=lean_final b=t140_pixel_oracle {}"
                                    + " digestsEqual={}",
                            pose, comparison.format(),
                            t140FinalImage.digest().equals(oracle.digest()));
                }
                t140FinalImage = null;
                t140ImageStep = 6;
                applyT141Arm();
            }
        }
    }

    private static StormReferenceImageComparison.Reference t140FinalImage;
    private static int t140PotentialPixels;
    private static int t140MaskWidth;
    private static int t140MaskHeight;

    /** Counts pixels whose alpha reaches the threshold in a captured frame. */
    private static int countOpaquePixels(
            StormReferenceImageComparison.Reference reference, double threshold) {
        float[] pixels = reference.pixels();
        if (pixels == null) {
            return 0;
        }
        int count = 0;
        for (int index = 3; index < pixels.length; index += 4) {
            if (pixels[index] >= threshold) {
                count++;
            }
        }
        return count;
    }

    /**
     * Captures the three production-context frames back to back and compares
     * the two arms against the anchor.
     *
     * <p>Adjacency is the whole point. The rain arm removes a code path that
     * every production call site reaches with {@code includePrecipitation}
     * false, so it should be numerically identical; a difference therefore
     * either falsifies that reading or means the fixture moved between the two
     * captures. Only adjacent captures can tell those apart.
     */
    private static void tickT162Images() {
        if (StormReferenceImageCapture.active()) {
            return;
        }
        switch (t162ImageStep) {
            case 0 -> {
                VolumetricCloudDebugConfig.setFinalProgramOverride(
                        CoreCostDiagnosticProgram.LEAN_FINAL);
                if (StormReferenceImageCapture.request("t162_anchor")
                        .startsWith("acquiring")) {
                    t162ImageStep = 1;
                }
            }
            case 1 -> {
                StormReferenceImageComparison.Reference captured =
                        StormReferenceImageCapture.latestResult();
                if (captured == null) {
                    t162ImageStep = 0;
                    return;
                }
                t162AnchorImage = captured;
                ProjectAtmosphere.LOGGER.info("T162_IMAGE anchor {}", captured.format());
                t162ImageStep = 2;
            }
            case 2 -> {
                VolumetricCloudDebugConfig.setFinalProgramOverride(
                        CoreCostDiagnosticProgram.T162_NO_RAIN);
                if (StormReferenceImageCapture.request("t162_norain")
                        .startsWith("acquiring")) {
                    t162ImageStep = 3;
                }
            }
            case 3 -> {
                t162CompareAgainstAnchor("t162_norain");
                t162ImageStep = 4;
            }
            case 4 -> {
                VolumetricCloudDebugConfig.setFinalProgramOverride(
                        CoreCostDiagnosticProgram.T162_NO_LIGHT);
                if (StormReferenceImageCapture.request("t162_nolight")
                        .startsWith("acquiring")) {
                    t162ImageStep = 5;
                }
            }
            default -> {
                t162CompareAgainstAnchor("t162_nolight");
                t162ImageStep = 6;
                applyT141Arm();
            }
        }
    }

    /**
     * Captures the anchor and each quality-affecting arm back to back, per pose.
     *
     * <p>Back to back matters: T162 first captured its arms minutes apart, the
     * fixture drifted between them, and both arms then reported the same
     * maximum error - the signature of a moving fixture rather than of the code
     * under test. The captures also run before {@code applyT141Arm()} re-pins
     * the override, which is the second defect that campaign hit.
     */
    /** The image set in force: T166's arms, or T167's. */
    private static CoreCostDiagnosticProgram[] t166ImageArms() {
        if (t181Run) {
            return T181_IMAGE_ARMS;
        }
        if (t180Run) {
            return T180_IMAGE_ARMS;
        }
        if (t179Run) {
            return T179_IMAGE_ARMS;
        }
        if (t178Run) {
            return T178_IMAGE_ARMS;
        }
        if (t177Run) {
            return T177_IMAGE_ARMS;
        }
        if (t176Run) {
            return T176_IMAGE_ARMS;
        }
        if (t175Run) {
            return T175_IMAGE_ARMS;
        }
        if (t174Run) {
            return T174_IMAGE_ARMS;
        }
        if (t173Run) {
            return T173_IMAGE_ARMS;
        }
        if (t172Run) {
            return T172_IMAGE_ARMS;
        }
        if (t171Run) {
            return T171_IMAGE_ARMS;
        }
        if (t170Run) {
            return T170_IMAGE_ARMS;
        }
        if (t169Run) {
            return T169_IMAGE_ARMS;
        }
        if (t168Run) {
            return T168_IMAGE_ARMS;
        }
        return t167Run ? T167_IMAGE_ARMS : T166_IMAGE_ARMS;
    }

    private static void tickT166Images(String pose) {
        if (StormReferenceImageCapture.active()) {
            return;
        }
        if (t166ImageStep == 0) {
            if (!t162QualifyFixture(pose, "t166_image_anchor", "before")) {
                return;
            }
            VolumetricCloudDebugConfig.setFinalProgramOverride(
                    CoreCostDiagnosticProgram.LEAN_FINAL);
            if (StormReferenceImageCapture.request("t166_anchor_" + pose)
                    .startsWith("acquiring")) {
                t166ImageStep = 1;
            }
            return;
        }
        if (t166ImageStep == 1) {
            StormReferenceImageComparison.Reference captured =
                    StormReferenceImageCapture.latestResult();
            if (captured == null) {
                t166ImageStep = 0;
                return;
            }
            t166AnchorImage = captured;
            ProjectAtmosphere.LOGGER.info(
                    "T166_IMAGE pose={} anchor boundProgram={} {}",
                    pose, VolumetricCloudRenderer.lastProgram().serializedName(),
                    captured.format());
            t166ImageStep = 2;
            return;
        }
        int index = (t166ImageStep - 2) / 2;
        if (index >= t166ImageArms().length) {
            t166ImageStep = t166ImageArms().length * 2 + 1;
            applyT141Arm();
            return;
        }
        CoreCostDiagnosticProgram arm = t166ImageArms()[index];
        if ((t166ImageStep - 2) % 2 == 0) {
            VolumetricCloudDebugConfig.setFinalProgramOverride(arm);
            if (StormReferenceImageCapture.request("t166_" + arm.serializedName() + "_" + pose)
                    .startsWith("acquiring")) {
                t166ImageStep++;
            }
            return;
        }
        StormReferenceImageComparison.Reference captured =
                StormReferenceImageCapture.latestResult();
        if (captured == null || t166AnchorImage == null) {
            ProjectAtmosphere.LOGGER.warn(
                    "T166_IMAGE_AB pose={} arm={} evaluated=false reason=missing_capture",
                    pose, arm.serializedName());
        } else {
            ProjectAtmosphere.LOGGER.info(
                    "T166_IMAGE_AB pose={} arm={} a=lean_final {} {} digestsEqual={}",
                    pose, arm.serializedName(),
                    StormReferenceImageComparison.compare(t166AnchorImage, captured).format(),
                    StormArmQualityMetrics.compare(t166AnchorImage, captured).format(),
                    t166AnchorImage.digest().equals(captured.digest()));
        }
        t166ImageStep++;
    }

    private static void t162CompareAgainstAnchor(String arm) {
        StormReferenceImageComparison.Reference captured =
                StormReferenceImageCapture.latestResult();
        if (captured == null || t162AnchorImage == null) {
            ProjectAtmosphere.LOGGER.warn(
                    "T162_IMAGE_AB arm={} evaluated=false reason=missing_capture", arm);
            return;
        }
        ProjectAtmosphere.LOGGER.info(
                "T162_IMAGE_AB arm={} a=lean_final {} digestsEqual={}",
                arm,
                StormReferenceImageComparison.compare(t162AnchorImage, captured).format(),
                t162AnchorImage.digest().equals(captured.digest()));
    }

    /**
     * Captures old FINAL, new FINAL and the lighting arm back to back, per pose.
     *
     * <p>The required result is exact: the precipitation branch is unreachable
     * in production, so specializing it away must change nothing at all.
     * Epsilon equivalence is not accepted here, because T162 already
     * demonstrated exact equivalence for the same substitution.
     *
     * <p>The lighting arm is the harness self-check. It must NOT compare equal.
     * A run where it does is a broken run, not a passing one - that is exactly
     * how the T162 capture-ordering defect was caught, and the check is kept for
     * the same reason.
     */
    private static void tickT163Images(String pose) {
        if (StormReferenceImageCapture.active()) {
            return;
        }
        switch (t163ImageStep) {
            case 0 -> {
                if (!t162QualifyFixture(pose, "t163_image_anchor", "before")) {
                    return;
                }
                VolumetricCloudDebugConfig.setFinalProgramOverride(
                        CoreCostDiagnosticProgram.T163_WITH_RAIN);
                VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN);
                if (StormReferenceImageCapture.request("t163_anchor_" + pose)
                        .startsWith("acquiring")) {
                    t163ImageStep = 1;
                }
            }
            case 1 -> {
                StormReferenceImageComparison.Reference captured =
                        StormReferenceImageCapture.latestResult();
                if (captured == null) {
                    t163ImageStep = 0;
                    return;
                }
                t163AnchorImage = captured;
                ProjectAtmosphere.LOGGER.info(
                        "T163_IMAGE pose={} anchor=t163_withrain boundProgram={} {}",
                        pose, VolumetricCloudRenderer.lastProgram().serializedName(),
                        captured.format());
                t163ImageStep = 2;
            }
            case 2 -> {
                VolumetricCloudDebugConfig.setFinalProgramOverride(
                        CoreCostDiagnosticProgram.LEAN_FINAL);
                if (StormReferenceImageCapture.request("t163_final_" + pose)
                        .startsWith("acquiring")) {
                    t163ImageStep = 3;
                }
            }
            case 3 -> {
                t163CompareAgainstAnchor(pose, "lean_final", true);
                t163ImageStep = 4;
            }
            case 4 -> {
                VolumetricCloudDebugConfig.setFinalProgramOverride(
                        CoreCostDiagnosticProgram.T162_NO_LIGHT);
                if (StormReferenceImageCapture.request("t163_selfcheck_" + pose)
                        .startsWith("acquiring")) {
                    t163ImageStep = 5;
                }
            }
            default -> {
                t163CompareAgainstAnchor(pose, "t162_nolight_selfcheck", false);
                t162QualifyFixture(pose, "t163_image_anchor", "after");
                t163ImageStep = 6;
                applyT141Arm();
            }
        }
    }

    /**
     * @param mustBeIdentical true for the production comparison, false for the
     *                        self-check arm which is required to differ
     */
    private static void t163CompareAgainstAnchor(
            String pose, String arm, boolean mustBeIdentical) {
        StormReferenceImageComparison.Reference captured =
                StormReferenceImageCapture.latestResult();
        if (captured == null || t163AnchorImage == null) {
            ProjectAtmosphere.LOGGER.warn(
                    "T163_IMAGE_AB pose={} arm={} evaluated=false reason=missing_capture",
                    pose, arm);
            return;
        }
        StormReferenceImageComparison.Comparison comparison =
                StormReferenceImageComparison.compare(t163AnchorImage, captured);
        boolean identical = comparison.evaluated()
                && comparison.changedPixelCountAboveEpsilon() == 0
                && comparison.maxAbsRGBA() == 0.0D;
        String verdict = mustBeIdentical
                ? (identical ? "PASS_IDENTICAL" : "FAIL_CHANGED")
                : (identical ? "FAIL_SELFCHECK_IDENTICAL" : "PASS_DIFFERS");
        ProjectAtmosphere.LOGGER.info(
                "T163_IMAGE_AB pose={} arm={} a=t163_withrain verdict={} {} digestsEqual={}",
                pose, arm, verdict, comparison.format(),
                t163AnchorImage.digest().equals(captured.digest()));
    }

    /** Builds the T163 old-versus-new record, including the resolution frontier. */
    private static String buildT163PrecipitationReport() {
        StringBuilder out = new StringBuilder("T163_PRECIPITATION_DECISION");
        for (String pose : T163_POSES) {
            for (int index = 0; index + 1 < T163_ARMS.length; index += 2) {
                T163Arm oldArm = T163_ARMS[index];
                T163Arm newArm = T163_ARMS[index + 1];
                StormT135PerformanceProfile.Cell oldCell = t163Cell(pose, oldArm.label());
                StormT135PerformanceProfile.Cell newCell = t163Cell(pose, newArm.label());
                if (oldCell == null || newCell == null) {
                    out.append(String.format(Locale.ROOT,
                            "%nT163_PAIR pose=%s scale=%s evaluated=false old=%s new=%s",
                            pose,
                            Float.isNaN(oldArm.resolutionScale())
                                    ? "mode" : String.format(Locale.ROOT, "%.4f",
                                            oldArm.resolutionScale()),
                            oldCell != null, newCell != null));
                    continue;
                }
                out.append(String.format(Locale.ROOT,
                        "%nT163_PAIR pose=%s scale=%s target=%dx%d"
                                + " oldCloudP50=%.4f oldCloudP95=%.4f"
                                + " newCloudP50=%.4f newCloudP95=%.4f"
                                + " speedupP50=%.4fx speedupP95=%.4fx"
                                + " oldFrameP50=%.4f newFrameP50=%.4f",
                        pose,
                        Float.isNaN(oldArm.resolutionScale())
                                ? "mode" : String.format(Locale.ROOT, "%.4f",
                                        oldArm.resolutionScale()),
                        newCell.cloudWidth(), newCell.cloudHeight(),
                        oldCell.cloudP50(), oldCell.cloudP95(),
                        newCell.cloudP50(), newCell.cloudP95(),
                        newCell.cloudP50() <= 0.0D
                                ? Double.NaN : oldCell.cloudP50() / newCell.cloudP50(),
                        newCell.cloudP95() <= 0.0D
                                ? Double.NaN : oldCell.cloudP95() / newCell.cloudP95(),
                        oldCell.frameP50(), newCell.frameP50()));
            }
        }
        out.append(String.format(Locale.ROOT, "%nT163_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static StormT135PerformanceProfile.Cell t163Cell(String pose, String arm) {
        for (StormT135PerformanceProfile.Cell cell : StormT135PerformanceProfile.results()) {
            if (pose.equals(cell.pose()) && arm.equals(cell.arm())) {
                return cell;
            }
        }
        return null;
    }

    /** Arms whose cells were discarded, so the report can name them. */
    private static final java.util.List<String> T162_REJECTED = new java.util.ArrayList<>();

    /**
     * Qualifies the fixture immediately before and immediately after every
     * single timing cell.
     *
     * <p>This exists because of a specific failure: the Ultra resolution
     * recovery sweep confirmed pose visibility once and then ran eight arms
     * without rechecking, and the last five became roughly nine times too cheap
     * after the storm stopped contributing. Descriptor count alone did not
     * catch it - the count stayed at ten. So this checks the count against the
     * one the pose started with AND re-evaluates the geometric visibility
     * verdict, at both ends of the cell.
     */
    private static boolean t162QualifyFixture(String pose, String arm, String phase) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }
        int descriptors = StormGeometryBuildCoordinator.lobeCount();
        if (t162ExpectedDescriptors == 0) {
            t162ExpectedDescriptors = descriptors;
        }
        if (descriptors <= 0 || descriptors != t162ExpectedDescriptors) {
            ProjectAtmosphere.LOGGER.warn(
                    "T162_QUALIFY {} arm={} descriptors={} expected={} verdict=fail",
                    phase, arm, descriptors, t162ExpectedDescriptors);
            return false;
        }
        StormPerformanceBaseline.SuiteFixture fixture = StormPerformanceBaseline.suiteFixture();
        if (fixture == null) {
            ProjectAtmosphere.LOGGER.warn(
                    "T162_QUALIFY {} arm={} verdict=fail reason=fixture_missing", phase, arm);
            return false;
        }
        StormFixtureVisibility.Verdict verdict = StormFixtureVisibility.evaluate(
                descriptors,
                fixture.centerX(), fixture.centerZ(), fixture.baseY(), fixture.topY(),
                fixture.horizontalRadius(),
                player.getX(), player.getEyeY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                minecraft.options.fov().get(),
                AtmoCommonConfig.CLOUD_RENDER_DISTANCE.get());
        if (!verdict.valid()) {
            ProjectAtmosphere.LOGGER.warn(
                    "T162_QUALIFY {} arm={} verdict=fail {}", phase, arm, verdict.format());
            return false;
        }
        ProjectAtmosphere.LOGGER.info(
                "T162_QUALIFY {} arm={} descriptors={} {}", phase, arm, descriptors,
                verdict.format());
        return true;
    }

    /**
     * Builds the attribution record.
     *
     * <p>The fixed-work rungs are cumulative, so the report prints each rung
     * and the delta over the rung below it. That delta is the cost of the one
     * class the rung adds, measured with control flow held identical.
     */
    /**
     * The T166 record: every cell, then the per-pose deltas against that pose's
     * own anchor, then the fixed-work ladder and the oracle re-derivation.
     *
     * <p>Speedups are computed against the anchor measured at the same pose in
     * the same run. Nothing here is compared against a number from an earlier
     * session, which is the mistake the T162 and T136 shares both turned out to
     * embody once the program underneath them changed.
     */
    /**
     * The T167 record. Every arm is expressed against its own pose's anchor, and
     * the distance-step arm T166 already measured is carried in the same matrix
     * so the graded curves are compared against it inside one run.
     */
    /** The T168 record: every arm against its own pose's anchor. */
    /**
     * The T169 summary. Lighting and detail are reported per density call as
     * well as per pixel: T168's pose gap is stated per pixel, and per pixel
     * cannot distinguish "more lighting per sample" from "more samples", which
     * are different problems with different fixes.
     */
    /**
     * T171's report. Unlike every other campaign this one reports dispersion
     * rather than a speedup: the question is not which arm is faster but how
     * much the harness disagrees with itself about arms that are the same.
     */
    /**
     * T172's report applies T171's rule directly: each program appears twice, and
     * the report states whether the pair agrees inside the 3% floor. A pair that
     * does not agree is marked REJECTED and carries no speedup, because
     * averaging a contradictory pair is exactly what made T170's clamp sweep
     * unusable.
     */
    /**
     * T173. The renderer and fixture state a cell is about to be measured
     * under, logged immediately before the timed cell begins.
     *
     * <p>Everything here is something a previous campaign either assumed
     * constant or checked only once. T172 proved that assumption wrong for the
     * workload counters, so the state that could explain it is now recorded per
     * cell rather than reconstructed afterwards from status lines that happen to
     * be logged every five seconds.
     */
    private static void logCellState(String pose, String arm) {
        if (!t173Run) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        com.mojang.blaze3d.pipeline.RenderTarget cloudTarget =
                VolumetricCloudRenderTargets.currentCloudTarget();
        com.mojang.blaze3d.pipeline.RenderTarget main = minecraft.getMainRenderTarget();
        CoreCostDiagnosticProgram override = VolumetricCloudDebugConfig.finalProgramOverride();
        ProjectAtmosphere.LOGGER.info(
                "T173_STATE pose={} arm={} descriptorSignature={} lobes={}"
                        + " camera=({},{},{}) yaw={} pitch={}"
                        + " cloudTarget={}x{} framebuffer={}x{}"
                        + " fixedScale={} governorScale={} program={} history={}"
                        + " descriptorLimit={} gameTime={} dayTime={}",
                pose, arm,
                String.format(Locale.ROOT, "%016x",
                        PuffLobeSpatialIndex.descriptorSignatureForDiagnostics()),
                StormGeometryBuildCoordinator.lobeCount(),
                player == null ? "n/a" : fmt((float) player.getX()),
                player == null ? "n/a" : fmt((float) player.getY()),
                player == null ? "n/a" : fmt((float) player.getZ()),
                player == null ? "n/a" : fmt(player.getYRot()),
                player == null ? "n/a" : fmt(player.getXRot()),
                cloudTarget == null ? 0 : cloudTarget.width,
                cloudTarget == null ? 0 : cloudTarget.height,
                main == null ? 0 : main.width,
                main == null ? 0 : main.height,
                fmt(VolumetricCloudDebugConfig.fixedResolutionScale()),
                fmt(VolumetricCloudRenderer.governorStepScale()),
                override == null ? "auto" : override.serializedName(),
                VolumetricCloudDebugConfig.historyEnabled(),
                VolumetricCloudDebugConfig.descriptorCountLimit(),
                minecraft.level == null ? -1L : minecraft.level.getGameTime(),
                minecraft.level == null ? -1L : minecraft.level.getDayTime());
    }

    /**
     * T173's report. Every cell is the same arm, so there is no speedup to
     * state: the report is the sequence itself, in execution order, with the
     * dispersion that decides whether SIDE is measurable.
     */
    /**
     * T174's report. Anchor-bracketed like T173's: each arm is divided by the
     * mean of the two anchors either side of it, and rejected outright if those
     * two disagree by more than the floor.
     */
    /** T175's report: anchor-bracketed, same rule as T173/T174. */
    /** T176's report: anchor-bracketed, same rule as T173/T174/T175. */
    /**
     * T177. The paired-ratio protocol.
     *
     * <p>T176 accepted a FAR arm whose two repeats agreed to 0.13% at 8.54 ms
     * while sibling arms of the same family straddled 4.3 and 8.5 ms, meaning
     * both repeats sat in the same wrong mode. Absolute agreement cannot detect
     * that; it is precisely what a sticky mode produces.
     *
     * <p>Acceptance here is therefore agreement between the LOCAL RATIOS. Each
     * repeat contributes anchor/arm measured seconds apart, so a mode change
     * that moves the whole machine moves the anchor with the arm and leaves the
     * ratio intact, while one that affects only the arm shows up as ratio
     * spread. Absolute p50/p95 are still reported and labelled session-local,
     * because the target is stated in milliseconds - but they do not decide
     * acceptance.
     */
    /**
     * T178. T177's paired-ratio report, plus the within-block stack-relative
     * ratio that campaign identified as the only form that survives a mode
     * swing: comparing an arm against the stack control measured seconds away
     * in the same block held to 0.48% while each arm's absolute value moved 11%.
     */
    /** T179. The paired-ratio protocol, with the within-block stack control. */
    /** T180. The paired-ratio protocol with the within-block stack control. */
    /** T181. The paired-ratio protocol with the within-block stack control. */
    private static String buildT181ProbeRefineReport() {
        StringBuilder out = new StringBuilder("T181_PROBE_REFINE_DECISION");
        for (String pose : T181_POSES) {
            java.util.Map<String, java.util.List<double[]>> byProgram =
                    new java.util.LinkedHashMap<>();
            java.util.Map<Integer, java.util.Map<String, Double>> byBlock =
                    new java.util.LinkedHashMap<>();
            int block = 0;
            for (int i = 0; i < T181_ARMS.length; i++) {
                T166Arm arm = T181_ARMS[i];
                if (t181IsAnchor(arm) || i == 0 || i + 1 >= T181_ARMS.length) {
                    continue;
                }
                if (arm.program() == CoreCostDiagnosticProgram.T181_PROBE8) {
                    block++;
                }
                StormT135PerformanceProfile.Cell before =
                        t162Cell(pose, T181_ARMS[i - 1].label());
                StormT135PerformanceProfile.Cell after =
                        t162Cell(pose, T181_ARMS[i + 1].label());
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (before == null || after == null || cell == null
                        || cell.cloudP50() <= 0.0D) {
                    out.append(String.format(Locale.ROOT,
                            "%nT181_BLOCK pose=%s arm=%s evaluated=false", pose,
                            arm.label()));
                    continue;
                }
                double baseline = (before.cloudP50() + after.cloudP50()) * 0.5D;
                double drift = Math.abs(before.cloudP50() - after.cloudP50())
                        / Math.max(1.0e-6D, baseline);
                double ratio = baseline / cell.cloudP50();
                out.append(String.format(Locale.ROOT,
                        "%nT181_BLOCK pose=%s block=%d arm=%s anchorBefore=%.4f"
                                + " anchorAfter=%.4f anchorDrift=%.4f cloudP50=%.4f"
                                + " cloudP95=%.4f localRatio=%.4f blockVerdict=%s",
                        pose, block, arm.label(), before.cloudP50(), after.cloudP50(),
                        drift, cell.cloudP50(), cell.cloudP95(), ratio,
                        drift <= T171_REPEAT_TOLERANCE ? "accepted"
                                : "REJECTED_anchor_drift"));
                byBlock.computeIfAbsent(block, k -> new java.util.LinkedHashMap<>())
                        .put(arm.program().serializedName(), cell.cloudP50());
                if (drift > T171_REPEAT_TOLERANCE) {
                    continue;
                }
                byProgram.computeIfAbsent(arm.program().serializedName(),
                        key -> new java.util.ArrayList<>())
                        .add(new double[] {ratio, cell.cloudP50(), cell.cloudP95()});
            }
            for (java.util.Map.Entry<String, java.util.List<double[]>> entry
                    : byProgram.entrySet()) {
                java.util.List<double[]> blocks = entry.getValue();
                double minRatio = Double.MAX_VALUE;
                double maxRatio = 0.0D;
                double sumRatio = 0.0D;
                double sumP50 = 0.0D;
                double maxP95 = 0.0D;
                for (double[] b : blocks) {
                    minRatio = Math.min(minRatio, b[0]);
                    maxRatio = Math.max(maxRatio, b[0]);
                    sumRatio += b[0];
                    sumP50 += b[1];
                    maxP95 = Math.max(maxP95, b[2]);
                }
                double meanRatio = sumRatio / blocks.size();
                double spread = blocks.size() < 2 ? 1.0D
                        : (maxRatio - minRatio) / Math.max(1.0e-6D, meanRatio);
                boolean accepted = blocks.size() >= 2
                        && spread <= T171_REPEAT_TOLERANCE;
                out.append(String.format(Locale.ROOT,
                        "%nT181_ARM pose=%s arm=%s blocks=%d ratioMin=%.4f"
                                + " ratioMax=%.4f ratioMean=%.4f ratioSpread=%.4f"
                                + " sessionLocalP50=%.4f sessionLocalP95=%.4f"
                                + " verdict=%s",
                        pose, entry.getKey(), blocks.size(), minRatio, maxRatio,
                        meanRatio, spread, sumP50 / blocks.size(), maxP95,
                        accepted ? "accepted"
                                : blocks.size() < 2 ? "REJECTED_insufficient_blocks"
                                : "REJECTED_ratio_spread"));
            }
            String control = CoreCostDiagnosticProgram.T172_STACK_PRE.serializedName();
            String target = CoreCostDiagnosticProgram.T181_STACK_PROBE8.serializedName();
            java.util.List<Double> paired = new java.util.ArrayList<>();
            for (java.util.Map<String, Double> cells : byBlock.values()) {
                Double base = cells.get(control);
                Double arm = cells.get(target);
                if (base != null && arm != null && arm > 0.0D) {
                    paired.add(base / arm);
                }
            }
            if (paired.size() >= 2) {
                double min = Double.MAX_VALUE;
                double max = 0.0D;
                double sum = 0.0D;
                for (double v : paired) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                    sum += v;
                }
                double mean = sum / paired.size();
                double spread = (max - min) / Math.max(1.0e-6D, mean);
                out.append(String.format(Locale.ROOT,
                        "%nT181_WITHIN_BLOCK pose=%s arm=%s vsControl=%s blocks=%d"
                                + " ratioMin=%.4f ratioMax=%.4f ratioMean=%.4f"
                                + " ratioSpread=%.4f verdict=%s",
                        pose, target, control, paired.size(), min, max, mean, spread,
                        spread <= T171_REPEAT_TOLERANCE ? "accepted"
                                : "REJECTED_ratio_spread"));
            }
        }
        out.append(String.format(Locale.ROOT, "%nT181_FLOOR ratioTolerance=%.3f",
                T171_REPEAT_TOLERANCE));
        out.append(String.format(Locale.ROOT, "%nT181_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT180ConsumerReport() {
        StringBuilder out = new StringBuilder("T180_CONSUMER_DECISION");
        for (String pose : T180_POSES) {
            java.util.Map<String, java.util.List<double[]>> byProgram =
                    new java.util.LinkedHashMap<>();
            java.util.Map<Integer, java.util.Map<String, Double>> byBlock =
                    new java.util.LinkedHashMap<>();
            int block = 0;
            for (int i = 0; i < T180_ARMS.length; i++) {
                T166Arm arm = T180_ARMS[i];
                if (t180IsAnchor(arm) || i == 0 || i + 1 >= T180_ARMS.length) {
                    continue;
                }
                if (arm.program() == CoreCostDiagnosticProgram.T180_NO_PROBE) {
                    block++;
                }
                StormT135PerformanceProfile.Cell before =
                        t162Cell(pose, T180_ARMS[i - 1].label());
                StormT135PerformanceProfile.Cell after =
                        t162Cell(pose, T180_ARMS[i + 1].label());
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (before == null || after == null || cell == null
                        || cell.cloudP50() <= 0.0D) {
                    out.append(String.format(Locale.ROOT,
                            "%nT180_BLOCK pose=%s arm=%s evaluated=false", pose,
                            arm.label()));
                    continue;
                }
                double baseline = (before.cloudP50() + after.cloudP50()) * 0.5D;
                double drift = Math.abs(before.cloudP50() - after.cloudP50())
                        / Math.max(1.0e-6D, baseline);
                double ratio = baseline / cell.cloudP50();
                out.append(String.format(Locale.ROOT,
                        "%nT180_BLOCK pose=%s block=%d arm=%s anchorBefore=%.4f"
                                + " anchorAfter=%.4f anchorDrift=%.4f cloudP50=%.4f"
                                + " cloudP95=%.4f localRatio=%.4f blockVerdict=%s",
                        pose, block, arm.label(), before.cloudP50(), after.cloudP50(),
                        drift, cell.cloudP50(), cell.cloudP95(), ratio,
                        drift <= T171_REPEAT_TOLERANCE ? "accepted"
                                : "REJECTED_anchor_drift"));
                byBlock.computeIfAbsent(block, k -> new java.util.LinkedHashMap<>())
                        .put(arm.program().serializedName(), cell.cloudP50());
                if (drift > T171_REPEAT_TOLERANCE) {
                    continue;
                }
                byProgram.computeIfAbsent(arm.program().serializedName(),
                        key -> new java.util.ArrayList<>())
                        .add(new double[] {ratio, cell.cloudP50(), cell.cloudP95()});
            }
            for (java.util.Map.Entry<String, java.util.List<double[]>> entry
                    : byProgram.entrySet()) {
                java.util.List<double[]> blocks = entry.getValue();
                double minRatio = Double.MAX_VALUE;
                double maxRatio = 0.0D;
                double sumRatio = 0.0D;
                double sumP50 = 0.0D;
                double maxP95 = 0.0D;
                for (double[] b : blocks) {
                    minRatio = Math.min(minRatio, b[0]);
                    maxRatio = Math.max(maxRatio, b[0]);
                    sumRatio += b[0];
                    sumP50 += b[1];
                    maxP95 = Math.max(maxP95, b[2]);
                }
                double meanRatio = sumRatio / blocks.size();
                double spread = blocks.size() < 2 ? 1.0D
                        : (maxRatio - minRatio) / Math.max(1.0e-6D, meanRatio);
                boolean accepted = blocks.size() >= 2
                        && spread <= T171_REPEAT_TOLERANCE;
                out.append(String.format(Locale.ROOT,
                        "%nT180_ARM pose=%s arm=%s blocks=%d ratioMin=%.4f"
                                + " ratioMax=%.4f ratioMean=%.4f ratioSpread=%.4f"
                                + " sessionLocalP50=%.4f sessionLocalP95=%.4f"
                                + " verdict=%s",
                        pose, entry.getKey(), blocks.size(), minRatio, maxRatio,
                        meanRatio, spread, sumP50 / blocks.size(), maxP95,
                        accepted ? "accepted"
                                : blocks.size() < 2 ? "REJECTED_insufficient_blocks"
                                : "REJECTED_ratio_spread"));
            }
            String control = CoreCostDiagnosticProgram.T172_STACK_PRE.serializedName();
            String target = CoreCostDiagnosticProgram.T180_STACK_PROBE.serializedName();
            java.util.List<Double> paired = new java.util.ArrayList<>();
            for (java.util.Map<String, Double> cells : byBlock.values()) {
                Double base = cells.get(control);
                Double arm = cells.get(target);
                if (base != null && arm != null && arm > 0.0D) {
                    paired.add(base / arm);
                }
            }
            if (paired.size() >= 2) {
                double min = Double.MAX_VALUE;
                double max = 0.0D;
                double sum = 0.0D;
                for (double v : paired) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                    sum += v;
                }
                double mean = sum / paired.size();
                double spread = (max - min) / Math.max(1.0e-6D, mean);
                out.append(String.format(Locale.ROOT,
                        "%nT180_WITHIN_BLOCK pose=%s arm=%s vsControl=%s blocks=%d"
                                + " ratioMin=%.4f ratioMax=%.4f ratioMean=%.4f"
                                + " ratioSpread=%.4f verdict=%s",
                        pose, target, control, paired.size(), min, max, mean, spread,
                        spread <= T171_REPEAT_TOLERANCE ? "accepted"
                                : "REJECTED_ratio_spread"));
            }
        }
        out.append(String.format(Locale.ROOT, "%nT180_FLOOR ratioTolerance=%.3f",
                T171_REPEAT_TOLERANCE));
        out.append(String.format(Locale.ROOT, "%nT180_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT179DominanceReport() {
        StringBuilder out = new StringBuilder("T179_DOMINANCE_DECISION");
        for (String pose : T179_POSES) {
            java.util.Map<String, java.util.List<double[]>> byProgram =
                    new java.util.LinkedHashMap<>();
            java.util.Map<Integer, java.util.Map<String, Double>> byBlock =
                    new java.util.LinkedHashMap<>();
            int block = 0;
            for (int i = 0; i < T179_ARMS.length; i++) {
                T166Arm arm = T179_ARMS[i];
                if (t179IsAnchor(arm) || i == 0 || i + 1 >= T179_ARMS.length) {
                    continue;
                }
                if (arm.program() == CoreCostDiagnosticProgram.T179_DOM_EXACT) {
                    block++;
                }
                StormT135PerformanceProfile.Cell before =
                        t162Cell(pose, T179_ARMS[i - 1].label());
                StormT135PerformanceProfile.Cell after =
                        t162Cell(pose, T179_ARMS[i + 1].label());
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (before == null || after == null || cell == null
                        || cell.cloudP50() <= 0.0D) {
                    out.append(String.format(Locale.ROOT,
                            "%nT179_BLOCK pose=%s arm=%s evaluated=false", pose,
                            arm.label()));
                    continue;
                }
                double baseline = (before.cloudP50() + after.cloudP50()) * 0.5D;
                double drift = Math.abs(before.cloudP50() - after.cloudP50())
                        / Math.max(1.0e-6D, baseline);
                double ratio = baseline / cell.cloudP50();
                out.append(String.format(Locale.ROOT,
                        "%nT179_BLOCK pose=%s block=%d arm=%s anchorBefore=%.4f"
                                + " anchorAfter=%.4f anchorDrift=%.4f cloudP50=%.4f"
                                + " cloudP95=%.4f localRatio=%.4f blockVerdict=%s",
                        pose, block, arm.label(), before.cloudP50(), after.cloudP50(),
                        drift, cell.cloudP50(), cell.cloudP95(), ratio,
                        drift <= T171_REPEAT_TOLERANCE ? "accepted"
                                : "REJECTED_anchor_drift"));
                byBlock.computeIfAbsent(block, k -> new java.util.LinkedHashMap<>())
                        .put(arm.program().serializedName(), cell.cloudP50());
                if (drift > T171_REPEAT_TOLERANCE) {
                    continue;
                }
                byProgram.computeIfAbsent(arm.program().serializedName(),
                        key -> new java.util.ArrayList<>())
                        .add(new double[] {ratio, cell.cloudP50(), cell.cloudP95()});
            }
            for (java.util.Map.Entry<String, java.util.List<double[]>> entry
                    : byProgram.entrySet()) {
                java.util.List<double[]> blocks = entry.getValue();
                double minRatio = Double.MAX_VALUE;
                double maxRatio = 0.0D;
                double sumRatio = 0.0D;
                double sumP50 = 0.0D;
                double maxP95 = 0.0D;
                for (double[] b : blocks) {
                    minRatio = Math.min(minRatio, b[0]);
                    maxRatio = Math.max(maxRatio, b[0]);
                    sumRatio += b[0];
                    sumP50 += b[1];
                    maxP95 = Math.max(maxP95, b[2]);
                }
                double meanRatio = sumRatio / blocks.size();
                double spread = blocks.size() < 2 ? 1.0D
                        : (maxRatio - minRatio) / Math.max(1.0e-6D, meanRatio);
                boolean accepted = blocks.size() >= 2
                        && spread <= T171_REPEAT_TOLERANCE;
                out.append(String.format(Locale.ROOT,
                        "%nT179_ARM pose=%s arm=%s blocks=%d ratioMin=%.4f"
                                + " ratioMax=%.4f ratioMean=%.4f ratioSpread=%.4f"
                                + " sessionLocalP50=%.4f sessionLocalP95=%.4f"
                                + " verdict=%s",
                        pose, entry.getKey(), blocks.size(), minRatio, maxRatio,
                        meanRatio, spread, sumP50 / blocks.size(), maxP95,
                        accepted ? "accepted"
                                : blocks.size() < 2 ? "REJECTED_insufficient_blocks"
                                : "REJECTED_ratio_spread"));
            }
            String control = CoreCostDiagnosticProgram.T172_STACK_PRE.serializedName();
            String target = CoreCostDiagnosticProgram.T179_STACK_DOM.serializedName();
            java.util.List<Double> paired = new java.util.ArrayList<>();
            for (java.util.Map<String, Double> cells : byBlock.values()) {
                Double base = cells.get(control);
                Double arm = cells.get(target);
                if (base != null && arm != null && arm > 0.0D) {
                    paired.add(base / arm);
                }
            }
            if (paired.size() >= 2) {
                double min = Double.MAX_VALUE;
                double max = 0.0D;
                double sum = 0.0D;
                for (double v : paired) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                    sum += v;
                }
                double mean = sum / paired.size();
                double spread = (max - min) / Math.max(1.0e-6D, mean);
                out.append(String.format(Locale.ROOT,
                        "%nT179_WITHIN_BLOCK pose=%s arm=%s vsControl=%s blocks=%d"
                                + " ratioMin=%.4f ratioMax=%.4f ratioMean=%.4f"
                                + " ratioSpread=%.4f verdict=%s",
                        pose, target, control, paired.size(), min, max, mean, spread,
                        spread <= T171_REPEAT_TOLERANCE ? "accepted"
                                : "REJECTED_ratio_spread"));
            }
        }
        out.append(String.format(Locale.ROOT, "%nT179_FLOOR ratioTolerance=%.3f",
                T171_REPEAT_TOLERANCE));
        out.append(String.format(Locale.ROOT, "%nT179_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT178LobeSupportReport() {
        StringBuilder out = new StringBuilder("T178_LOBE_SUPPORT_DECISION");
        for (String pose : T178_POSES) {
            java.util.Map<String, java.util.List<double[]>> byProgram =
                    new java.util.LinkedHashMap<>();
            java.util.Map<Integer, java.util.Map<String, Double>> byBlock =
                    new java.util.LinkedHashMap<>();
            int block = 0;
            for (int i = 0; i < T178_ARMS.length; i++) {
                T166Arm arm = T178_ARMS[i];
                if (t178IsAnchor(arm) || i == 0 || i + 1 >= T178_ARMS.length) {
                    continue;
                }
                if (arm.program() == CoreCostDiagnosticProgram.T176_LIGHT3) {
                    block++;
                }
                StormT135PerformanceProfile.Cell before =
                        t162Cell(pose, T178_ARMS[i - 1].label());
                StormT135PerformanceProfile.Cell after =
                        t162Cell(pose, T178_ARMS[i + 1].label());
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (before == null || after == null || cell == null
                        || cell.cloudP50() <= 0.0D) {
                    out.append(String.format(Locale.ROOT,
                            "%nT178_BLOCK pose=%s arm=%s evaluated=false", pose,
                            arm.label()));
                    continue;
                }
                double baseline = (before.cloudP50() + after.cloudP50()) * 0.5D;
                double drift = Math.abs(before.cloudP50() - after.cloudP50())
                        / Math.max(1.0e-6D, baseline);
                double ratio = baseline / cell.cloudP50();
                out.append(String.format(Locale.ROOT,
                        "%nT178_BLOCK pose=%s block=%d arm=%s anchorBefore=%.4f"
                                + " anchorAfter=%.4f anchorDrift=%.4f cloudP50=%.4f"
                                + " cloudP95=%.4f localRatio=%.4f blockVerdict=%s",
                        pose, block, arm.label(), before.cloudP50(), after.cloudP50(),
                        drift, cell.cloudP50(), cell.cloudP95(), ratio,
                        drift <= T171_REPEAT_TOLERANCE ? "accepted"
                                : "REJECTED_anchor_drift"));
                byBlock.computeIfAbsent(block, k -> new java.util.LinkedHashMap<>())
                        .put(arm.program().serializedName(), cell.cloudP50());
                if (drift > T171_REPEAT_TOLERANCE) {
                    continue;
                }
                byProgram.computeIfAbsent(arm.program().serializedName(),
                        key -> new java.util.ArrayList<>())
                        .add(new double[] {ratio, cell.cloudP50(), cell.cloudP95()});
            }
            for (java.util.Map.Entry<String, java.util.List<double[]>> entry
                    : byProgram.entrySet()) {
                java.util.List<double[]> blocks = entry.getValue();
                double minRatio = Double.MAX_VALUE;
                double maxRatio = 0.0D;
                double sumRatio = 0.0D;
                double sumP50 = 0.0D;
                double maxP95 = 0.0D;
                for (double[] b : blocks) {
                    minRatio = Math.min(minRatio, b[0]);
                    maxRatio = Math.max(maxRatio, b[0]);
                    sumRatio += b[0];
                    sumP50 += b[1];
                    maxP95 = Math.max(maxP95, b[2]);
                }
                double meanRatio = sumRatio / blocks.size();
                double spread = blocks.size() < 2 ? 1.0D
                        : (maxRatio - minRatio) / Math.max(1.0e-6D, meanRatio);
                boolean accepted = blocks.size() >= 2
                        && spread <= T171_REPEAT_TOLERANCE;
                out.append(String.format(Locale.ROOT,
                        "%nT178_ARM pose=%s arm=%s blocks=%d ratioMin=%.4f"
                                + " ratioMax=%.4f ratioMean=%.4f ratioSpread=%.4f"
                                + " sessionLocalP50=%.4f sessionLocalP95=%.4f"
                                + " verdict=%s",
                        pose, entry.getKey(), blocks.size(), minRatio, maxRatio,
                        meanRatio, spread, sumP50 / blocks.size(), maxP95,
                        accepted ? "accepted"
                                : blocks.size() < 2 ? "REJECTED_insufficient_blocks"
                                : "REJECTED_ratio_spread"));
            }
            // Within-block, against the stack control measured in the same
            // block. T177 showed this is the only ratio that survives a mode
            // swing, because the swing moves control and arm together.
            String control = CoreCostDiagnosticProgram.T172_STACK_PRE.serializedName();
            String target = CoreCostDiagnosticProgram.T172_STACK_PRE.serializedName();
            java.util.List<Double> paired = new java.util.ArrayList<>();
            for (java.util.Map<String, Double> cells : byBlock.values()) {
                Double base = cells.get(control);
                Double arm = cells.get(target);
                if (base != null && arm != null && arm > 0.0D) {
                    paired.add(base / arm);
                }
            }
            if (paired.size() >= 2) {
                double min = Double.MAX_VALUE;
                double max = 0.0D;
                double sum = 0.0D;
                for (double v : paired) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                    sum += v;
                }
                double mean = sum / paired.size();
                double spread = (max - min) / Math.max(1.0e-6D, mean);
                out.append(String.format(Locale.ROOT,
                        "%nT178_WITHIN_BLOCK pose=%s arm=%s vsControl=%s blocks=%d"
                                + " ratioMin=%.4f ratioMax=%.4f ratioMean=%.4f"
                                + " ratioSpread=%.4f verdict=%s",
                        pose, target, control, paired.size(), min, max, mean, spread,
                        spread <= T171_REPEAT_TOLERANCE ? "accepted"
                                : "REJECTED_ratio_spread"));
            }
        }
        out.append(String.format(Locale.ROOT, "%nT178_FLOOR ratioTolerance=%.3f",
                T171_REPEAT_TOLERANCE));
        out.append(String.format(Locale.ROOT, "%nT178_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT177LightReuseReport() {
        StringBuilder out = new StringBuilder("T177_LIGHT_REUSE_DECISION");
        for (String pose : T177_POSES) {
            java.util.Map<String, java.util.List<double[]>> byProgram =
                    new java.util.LinkedHashMap<>();
            for (int i = 0; i < T177_ARMS.length; i++) {
                T166Arm arm = T177_ARMS[i];
                if (t177IsAnchor(arm) || i == 0 || i + 1 >= T177_ARMS.length) {
                    continue;
                }
                StormT135PerformanceProfile.Cell before =
                        t162Cell(pose, T177_ARMS[i - 1].label());
                StormT135PerformanceProfile.Cell after =
                        t162Cell(pose, T177_ARMS[i + 1].label());
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (before == null || after == null || cell == null
                        || cell.cloudP50() <= 0.0D) {
                    out.append(String.format(Locale.ROOT,
                            "%nT177_BLOCK pose=%s arm=%s evaluated=false", pose,
                            arm.label()));
                    continue;
                }
                double baseline = (before.cloudP50() + after.cloudP50()) * 0.5D;
                double drift = Math.abs(before.cloudP50() - after.cloudP50())
                        / Math.max(1.0e-6D, baseline);
                double ratio = baseline / cell.cloudP50();
                out.append(String.format(Locale.ROOT,
                        "%nT177_BLOCK pose=%s arm=%s anchorBefore=%.4f anchorAfter=%.4f"
                                + " anchorDrift=%.4f cloudP50=%.4f cloudP95=%.4f"
                                + " localRatio=%.4f blockVerdict=%s",
                        pose, arm.label(), before.cloudP50(), after.cloudP50(), drift,
                        cell.cloudP50(), cell.cloudP95(), ratio,
                        drift <= T171_REPEAT_TOLERANCE ? "accepted"
                                : "REJECTED_anchor_drift"));
                if (drift > T171_REPEAT_TOLERANCE) {
                    continue;
                }
                byProgram.computeIfAbsent(arm.program().serializedName(),
                        key -> new java.util.ArrayList<>())
                        .add(new double[] {ratio, cell.cloudP50(), cell.cloudP95()});
            }
            for (java.util.Map.Entry<String, java.util.List<double[]>> entry
                    : byProgram.entrySet()) {
                java.util.List<double[]> blocks = entry.getValue();
                double minRatio = Double.MAX_VALUE;
                double maxRatio = 0.0D;
                double sumRatio = 0.0D;
                double sumP50 = 0.0D;
                double maxP95 = 0.0D;
                for (double[] block : blocks) {
                    minRatio = Math.min(minRatio, block[0]);
                    maxRatio = Math.max(maxRatio, block[0]);
                    sumRatio += block[0];
                    sumP50 += block[1];
                    maxP95 = Math.max(maxP95, block[2]);
                }
                double meanRatio = sumRatio / blocks.size();
                double spread = blocks.size() < 2 ? 1.0D
                        : (maxRatio - minRatio) / Math.max(1.0e-6D, meanRatio);
                boolean accepted = blocks.size() >= 2
                        && spread <= T171_REPEAT_TOLERANCE;
                out.append(String.format(Locale.ROOT,
                        "%nT177_ARM pose=%s arm=%s blocks=%d ratioMin=%.4f"
                                + " ratioMax=%.4f ratioMean=%.4f ratioSpread=%.4f"
                                + " sessionLocalP50=%.4f sessionLocalP95=%.4f"
                                + " verdict=%s",
                        pose, entry.getKey(), blocks.size(), minRatio, maxRatio,
                        meanRatio, spread, sumP50 / blocks.size(), maxP95,
                        accepted ? "accepted"
                                : blocks.size() < 2 ? "REJECTED_insufficient_blocks"
                                : "REJECTED_ratio_spread"));
            }
        }
        out.append(String.format(Locale.ROOT, "%nT177_FLOOR ratioTolerance=%.3f",
                T171_REPEAT_TOLERANCE));
        out.append(String.format(Locale.ROOT, "%nT177_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT176LightMarchReport() {
        StringBuilder out = new StringBuilder("T176_LIGHT_MARCH_DECISION");
        for (String pose : T176_POSES) {
            for (int i = 0; i < T176_ARMS.length; i++) {
                T166Arm arm = T176_ARMS[i];
                if (t176IsAnchor(arm) || i == 0 || i + 1 >= T176_ARMS.length) {
                    continue;
                }
                StormT135PerformanceProfile.Cell before =
                        t162Cell(pose, T176_ARMS[i - 1].label());
                StormT135PerformanceProfile.Cell after =
                        t162Cell(pose, T176_ARMS[i + 1].label());
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (before == null || after == null || cell == null
                        || cell.cloudP50() <= 0.0D) {
                    out.append(String.format(Locale.ROOT,
                            "%nT176_ARM pose=%s arm=%s evaluated=false", pose, arm.label()));
                    continue;
                }
                double baseline = (before.cloudP50() + after.cloudP50()) * 0.5D;
                double drift = Math.abs(before.cloudP50() - after.cloudP50())
                        / Math.max(1.0e-6D, baseline);
                boolean accepted = drift <= T171_REPEAT_TOLERANCE;
                out.append(String.format(Locale.ROOT,
                        "%nT176_ARM pose=%s arm=%s anchorBefore=%.4f anchorAfter=%.4f"
                                + " anchorDrift=%.4f cloudP50=%.4f cloudP95=%.4f"
                                + " speedup=%s verdict=%s",
                        pose, arm.label(), before.cloudP50(), after.cloudP50(), drift,
                        cell.cloudP50(), cell.cloudP95(),
                        accepted
                                ? String.format(Locale.ROOT, "%.4f",
                                        baseline / cell.cloudP50())
                                : "n/a",
                        accepted ? "accepted" : "REJECTED_anchor_drift"));
            }
        }
        out.append(String.format(Locale.ROOT, "%nT176_FLOOR repeatTolerance=%.3f",
                T171_REPEAT_TOLERANCE));
        out.append(String.format(Locale.ROOT, "%nT176_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT175DensityNecessityReport() {
        StringBuilder out = new StringBuilder("T175_DENSITY_NECESSITY_DECISION");
        for (String pose : T175_POSES) {
            for (int i = 0; i < T175_ARMS.length; i++) {
                T166Arm arm = T175_ARMS[i];
                if (t175IsAnchor(arm) || i == 0 || i + 1 >= T175_ARMS.length) {
                    continue;
                }
                StormT135PerformanceProfile.Cell before =
                        t162Cell(pose, T175_ARMS[i - 1].label());
                StormT135PerformanceProfile.Cell after =
                        t162Cell(pose, T175_ARMS[i + 1].label());
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (before == null || after == null || cell == null
                        || cell.cloudP50() <= 0.0D) {
                    out.append(String.format(Locale.ROOT,
                            "%nT175_ARM pose=%s arm=%s evaluated=false", pose, arm.label()));
                    continue;
                }
                double baseline = (before.cloudP50() + after.cloudP50()) * 0.5D;
                double drift = Math.abs(before.cloudP50() - after.cloudP50())
                        / Math.max(1.0e-6D, baseline);
                boolean accepted = drift <= T171_REPEAT_TOLERANCE;
                out.append(String.format(Locale.ROOT,
                        "%nT175_ARM pose=%s arm=%s anchorBefore=%.4f anchorAfter=%.4f"
                                + " anchorDrift=%.4f cloudP50=%.4f cloudP95=%.4f"
                                + " speedup=%s verdict=%s",
                        pose, arm.label(), before.cloudP50(), after.cloudP50(), drift,
                        cell.cloudP50(), cell.cloudP95(),
                        accepted
                                ? String.format(Locale.ROOT, "%.4f",
                                        baseline / cell.cloudP50())
                                : "n/a",
                        accepted ? "accepted" : "REJECTED_anchor_drift"));
            }
        }
        out.append(String.format(Locale.ROOT, "%nT175_FLOOR repeatTolerance=%.3f",
                T171_REPEAT_TOLERANCE));
        out.append(String.format(Locale.ROOT, "%nT175_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT174GroupEntryReport() {
        StringBuilder out = new StringBuilder("T174_GROUP_ENTRY_DECISION");
        for (String pose : T174_POSES) {
            for (int i = 0; i < T174_ARMS.length; i++) {
                T166Arm arm = T174_ARMS[i];
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (cell == null) {
                    out.append(String.format(Locale.ROOT,
                            "%nT174_CELL pose=%s arm=%s evaluated=false", pose, arm.label()));
                    continue;
                }
                out.append(String.format(Locale.ROOT,
                        "%nT174_CELL pose=%s arm=%s cloudP50=%.4f cloudP95=%.4f"
                                + " cloudCv=%.5f",
                        pose, arm.label(), cell.cloudP50(), cell.cloudP95(), cell.cloudCv()));
            }
            for (int i = 0; i < T174_ARMS.length; i++) {
                T166Arm arm = T174_ARMS[i];
                if (t174IsAnchor(arm) || i == 0 || i + 1 >= T174_ARMS.length) {
                    continue;
                }
                StormT135PerformanceProfile.Cell before =
                        t162Cell(pose, T174_ARMS[i - 1].label());
                StormT135PerformanceProfile.Cell after =
                        t162Cell(pose, T174_ARMS[i + 1].label());
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (before == null || after == null || cell == null
                        || cell.cloudP50() <= 0.0D) {
                    out.append(String.format(Locale.ROOT,
                            "%nT174_ARM pose=%s arm=%s evaluated=false", pose, arm.label()));
                    continue;
                }
                double baseline = (before.cloudP50() + after.cloudP50()) * 0.5D;
                double drift = Math.abs(before.cloudP50() - after.cloudP50())
                        / Math.max(1.0e-6D, baseline);
                boolean accepted = drift <= T171_REPEAT_TOLERANCE;
                out.append(String.format(Locale.ROOT,
                        "%nT174_ARM pose=%s arm=%s anchorBefore=%.4f anchorAfter=%.4f"
                                + " anchorDrift=%.4f cloudP50=%.4f cloudP95=%.4f"
                                + " speedup=%s verdict=%s",
                        pose, arm.label(), before.cloudP50(), after.cloudP50(), drift,
                        cell.cloudP50(), cell.cloudP95(),
                        accepted
                                ? String.format(Locale.ROOT, "%.4f",
                                        baseline / cell.cloudP50())
                                : "n/a",
                        accepted ? "accepted" : "REJECTED_anchor_drift"));
            }
        }
        out.append(String.format(Locale.ROOT, "%nT174_FLOOR repeatTolerance=%.3f",
                T171_REPEAT_TOLERANCE));
        out.append(String.format(Locale.ROOT, "%nT174_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT173SideStabilityReport() {
        StringBuilder out = new StringBuilder("T173_SIDE_STABILITY_DECISION");
        java.util.List<Double> medians = new java.util.ArrayList<>();
        for (T166Arm arm : T173_ARMS) {
            StormT135PerformanceProfile.Cell cell = t162Cell("SIDE", arm.label());
            if (cell == null) {
                out.append(String.format(Locale.ROOT,
                        "%nT173_CELL arm=%s evaluated=false", arm.label()));
                continue;
            }
            if (t173IsAnchor(arm)) {
                medians.add(cell.cloudP50());
            }
            out.append(String.format(Locale.ROOT,
                    "%nT173_CELL arm=%s samples=%d duplicatesRejected=%d cloudP50=%.4f"
                            + " cloudP95=%.4f cloudMean=%.4f cloudSd=%.4f cloudCv=%.5f"
                            + " cloudMin=%.4f cloudMax=%.4f",
                    arm.label(), cell.samples(), cell.duplicateSamplesRejected(),
                    cell.cloudP50(), cell.cloudP95(), cell.cloudMean(), cell.cloudSd(),
                    cell.cloudCv(), cell.cloudMin(), cell.cloudMax()));
        }

        // Anchor-bracketed ratios. Each measured arm is divided by the mean of
        // the two anchors immediately either side of it, and is rejected if
        // those two anchors disagree by more than the floor - the arm's own
        // number is not interpreted when its baseline moved under it.
        for (int i = 0; i < T173_ARMS.length; i++) {
            T166Arm arm = T173_ARMS[i];
            if (t173IsAnchor(arm) || i == 0 || i + 1 >= T173_ARMS.length) {
                continue;
            }
            StormT135PerformanceProfile.Cell before = t162Cell("SIDE", T173_ARMS[i - 1].label());
            StormT135PerformanceProfile.Cell after = t162Cell("SIDE", T173_ARMS[i + 1].label());
            StormT135PerformanceProfile.Cell cell = t162Cell("SIDE", arm.label());
            if (before == null || after == null || cell == null || cell.cloudP50() <= 0.0D) {
                out.append(String.format(Locale.ROOT,
                        "%nT173_ARM arm=%s evaluated=false", arm.label()));
                continue;
            }
            double baseline = (before.cloudP50() + after.cloudP50()) * 0.5D;
            double anchorDrift = Math.abs(before.cloudP50() - after.cloudP50())
                    / Math.max(1.0e-6D, baseline);
            boolean accepted = anchorDrift <= T171_REPEAT_TOLERANCE;
            out.append(String.format(Locale.ROOT,
                    "%nT173_ARM arm=%s anchorBefore=%.4f anchorAfter=%.4f anchorDrift=%.4f"
                            + " cloudP50=%.4f cloudP95=%.4f speedup=%s verdict=%s",
                    arm.label(), before.cloudP50(), after.cloudP50(), anchorDrift,
                    cell.cloudP50(), cell.cloudP95(),
                    accepted ? String.format(Locale.ROOT, "%.4f", baseline / cell.cloudP50())
                            : "n/a",
                    accepted ? "accepted" : "REJECTED_anchor_drift"));
        }

        if (medians.size() >= 2) {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            double sum = 0.0D;
            for (double v : medians) {
                min = Math.min(min, v);
                max = Math.max(max, v);
                sum += v;
            }
            double mean = sum / medians.size();
            double variance = 0.0D;
            for (double v : medians) {
                variance += (v - mean) * (v - mean);
            }
            double sd = Math.sqrt(variance / (medians.size() - 1));
            // Consecutive-pair agreement is what the acceptance rule is stated
            // in, so it is computed here rather than left to be eyeballed.
            int accepted = 0;
            int rejected = 0;
            for (int i = 1; i < medians.size(); i++) {
                double a = medians.get(i - 1);
                double b = medians.get(i);
                double disagreement = Math.abs(a - b) / Math.max(1.0e-6D, (a + b) * 0.5D);
                if (disagreement <= T171_REPEAT_TOLERANCE) {
                    accepted++;
                } else {
                    rejected++;
                    out.append(String.format(Locale.ROOT,
                            "%nT173_BREAK between=%d and=%d a=%.4f b=%.4f disagreement=%.4f",
                            i - 1, i, a, b, disagreement));
                }
            }
            out.append(String.format(Locale.ROOT,
                    "%nT173_SUMMARY anchors=%d mean=%.4f sd=%.4f cv=%.5f min=%.4f max=%.4f"
                            + " range=%.4f consecutivePairsAccepted=%d rejected=%d"
                            + " verdict=%s",
                    medians.size(), mean, sd, sd / mean, min, max, (max - min) / mean,
                    accepted, rejected,
                    rejected == 0 && accepted >= 10 ? "SIDE_MEASURABLE" : "SIDE_NOT_MEASURABLE"));
        }
        return out.toString();
    }

    private static String buildT172PrecomputeReport() {
        StringBuilder out = new StringBuilder("T172_PRECOMPUTE_DECISION");
        for (String pose : T172_POSES) {
            StormT135PerformanceProfile.Cell anchor1 = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName() + "#r1");
            StormT135PerformanceProfile.Cell anchor2 = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName() + "#r2");
            double anchorMean = anchor1 == null || anchor2 == null
                    ? Double.NaN
                    : (anchor1.cloudP50() + anchor2.cloudP50()) * 0.5D;
            java.util.LinkedHashSet<String> programs = new java.util.LinkedHashSet<>();
            for (T166Arm arm : T172_ARMS) {
                programs.add(arm.program().serializedName());
            }
            for (String program : programs) {
                StormT135PerformanceProfile.Cell first = t162Cell(pose, program + "#r1");
                StormT135PerformanceProfile.Cell second = t162Cell(pose, program + "#r2");
                if (first == null || second == null
                        || first.cloudP50() <= 0.0D || second.cloudP50() <= 0.0D) {
                    out.append(String.format(Locale.ROOT,
                            "%nT172_ARM pose=%s arm=%s evaluated=false", pose, program));
                    continue;
                }
                double mean = (first.cloudP50() + second.cloudP50()) * 0.5D;
                double disagreement = Math.abs(first.cloudP50() - second.cloudP50())
                        / Math.max(1.0e-6D, mean);
                boolean accepted = disagreement <= T171_REPEAT_TOLERANCE;
                out.append(String.format(Locale.ROOT,
                        "%nT172_ARM pose=%s arm=%s run1=%.4f run2=%.4f mean=%.4f"
                                + " disagreement=%.4f verdict=%s p95run1=%.4f p95run2=%.4f"
                                + " cv1=%.5f cv2=%.5f speedup=%s",
                        pose, program, first.cloudP50(), second.cloudP50(), mean,
                        disagreement, accepted ? "accepted" : "REJECTED",
                        first.cloudP95(), second.cloudP95(),
                        first.cloudCv(), second.cloudCv(),
                        accepted && anchorMean > 0.0D && !Double.isNaN(anchorMean)
                                ? String.format(Locale.ROOT, "%.4f", anchorMean / mean)
                                : "n/a"));
            }
        }
        out.append(String.format(Locale.ROOT, "%nT172_FLOOR repeatTolerance=%.3f",
                T171_REPEAT_TOLERANCE));
        out.append(String.format(Locale.ROOT, "%nT172_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    /**
     * T171's measured floor. Repeats disagreeing by more than this are rejected
     * rather than averaged; effects smaller than this are below the floor.
     */
    private static final double T171_REPEAT_TOLERANCE = 0.03D;

    private static String buildT171HarnessStabilityReport() {
        StringBuilder out = new StringBuilder("T171_HARNESS_STABILITY_DECISION");
        for (String pose : T171_POSES) {
            for (T166Arm arm : T171_ARMS) {
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (cell == null) {
                    out.append(String.format(Locale.ROOT,
                            "%nT171_CELL pose=%s arm=%s evaluated=false", pose, arm.label()));
                    continue;
                }
                out.append(String.format(Locale.ROOT,
                        "%nT171_CELL pose=%s arm=%s block=%s program=%s samples=%d"
                                + " duplicatesRejected=%d cloudP50=%.4f cloudP95=%.4f"
                                + " cloudMean=%.4f cloudSd=%.4f cloudCv=%.5f"
                                + " cloudMin=%.4f cloudMax=%.4f",
                        pose, arm.label(),
                        arm.tag().isEmpty() ? "-" : arm.tag().substring(0, 1),
                        arm.program().serializedName(), cell.samples(),
                        cell.duplicateSamplesRejected(), cell.cloudP50(), cell.cloudP95(),
                        cell.cloudMean(), cell.cloudSd(), cell.cloudCv(),
                        cell.cloudMin(), cell.cloudMax()));
            }
        }
        out.append(String.format(Locale.ROOT, "%nT171_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT170PrimaryMarchReport() {
        StringBuilder out = new StringBuilder("T170_PRIMARY_MARCH_DECISION");
        for (String pose : T170_POSES) {
            StormT135PerformanceProfile.Cell anchor = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName());
            for (T166Arm arm : T170_ARMS) {
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (cell == null) {
                    out.append(String.format(Locale.ROOT,
                            "%nT170_ARM pose=%s arm=%s evaluated=false", pose, arm.label()));
                    continue;
                }
                boolean comparable = anchor != null
                        && cell.cloudWidth() == anchor.cloudWidth()
                        && cell.cloudHeight() == anchor.cloudHeight()
                        && cell.cloudP50() > 0.0F;
                out.append(String.format(Locale.ROOT,
                        "%nT170_ARM pose=%s arm=%s target=%dx%d cloudP50=%.4f cloudP95=%.4f"
                                + " frameP50=%.4f frameP95=%.4f speedupP50=%s savedMsP50=%s",
                        pose, arm.label(), cell.cloudWidth(), cell.cloudHeight(),
                        cell.cloudP50(), cell.cloudP95(), cell.frameP50(), cell.frameP95(),
                        comparable ? String.format(Locale.ROOT, "%.4f",
                                anchor.cloudP50() / cell.cloudP50()) : "n/a",
                        comparable ? String.format(Locale.ROOT, "%.4f",
                                anchor.cloudP50() - cell.cloudP50()) : "n/a"));
            }
        }
        for (String pose : T170_POSES) {
            StormT135PerformanceProfile.Cell first = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName());
            StormT135PerformanceProfile.Cell repeat = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName() + "@r0.2500");
            if (first == null || repeat == null || first.cloudP50() <= 0.0F) {
                out.append(String.format(Locale.ROOT,
                        "%nT170_DRIFT pose=%s evaluated=false", pose));
                continue;
            }
            double ratio = repeat.cloudP50() / first.cloudP50();
            out.append(String.format(Locale.ROOT,
                    "%nT170_DRIFT pose=%s firstAnchorP50=%.4f repeatAnchorP50=%.4f"
                            + " ratio=%.4f verdict=%s",
                    pose, first.cloudP50(), repeat.cloudP50(), ratio,
                    Math.abs(ratio - 1.0) <= 0.05 ? "stable" : "DRIFTED"));
        }
        out.append(String.format(Locale.ROOT, "%nT170_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT169LightingDetailReport() {
        StringBuilder out = new StringBuilder("T169_LIGHTING_DETAIL_DECISION");
        for (String pose : T169_POSES) {
            StormT135PerformanceProfile.Cell anchor = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName());
            for (T166Arm arm : T169_ARMS) {
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (cell == null) {
                    out.append(String.format(Locale.ROOT,
                            "%nT169_ARM pose=%s arm=%s evaluated=false", pose, arm.label()));
                    continue;
                }
                boolean comparable = anchor != null
                        && cell.cloudWidth() == anchor.cloudWidth()
                        && cell.cloudHeight() == anchor.cloudHeight()
                        && cell.cloudP50() > 0.0F;
                out.append(String.format(Locale.ROOT,
                        "%nT169_ARM pose=%s arm=%s target=%dx%d cloudP50=%.4f cloudP95=%.4f"
                                + " frameP50=%.4f frameP95=%.4f speedupP50=%s savedMsP50=%s",
                        pose, arm.label(), cell.cloudWidth(), cell.cloudHeight(),
                        cell.cloudP50(), cell.cloudP95(), cell.frameP50(), cell.frameP95(),
                        comparable ? String.format(Locale.ROOT, "%.4f",
                                anchor.cloudP50() / cell.cloudP50()) : "n/a",
                        comparable ? String.format(Locale.ROOT, "%.4f",
                                anchor.cloudP50() - cell.cloudP50()) : "n/a"));
            }
        }
        for (String pose : T169_POSES) {
            StormT135PerformanceProfile.Cell first = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName());
            StormT135PerformanceProfile.Cell repeat = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName() + "@r0.2500");
            if (first == null || repeat == null || first.cloudP50() <= 0.0F) {
                out.append(String.format(Locale.ROOT,
                        "%nT169_DRIFT pose=%s evaluated=false", pose));
                continue;
            }
            double ratio = repeat.cloudP50() / first.cloudP50();
            out.append(String.format(Locale.ROOT,
                    "%nT169_DRIFT pose=%s firstAnchorP50=%.4f repeatAnchorP50=%.4f"
                            + " ratio=%.4f verdict=%s",
                    pose, first.cloudP50(), repeat.cloudP50(), ratio,
                    Math.abs(ratio - 1.0) <= 0.05 ? "stable" : "DRIFTED"));
        }
        out.append(String.format(Locale.ROOT, "%nT169_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT168FootprintReport() {
        StringBuilder out = new StringBuilder("T168_FOOTPRINT_DECISION");
        for (String pose : T168_POSES) {
            StormT135PerformanceProfile.Cell anchor = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName());
            for (T166Arm arm : T168_ARMS) {
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (cell == null) {
                    out.append(String.format(Locale.ROOT,
                            "%nT168_ARM pose=%s arm=%s evaluated=false", pose, arm.label()));
                    continue;
                }
                boolean comparable = anchor != null
                        && cell.cloudWidth() == anchor.cloudWidth()
                        && cell.cloudHeight() == anchor.cloudHeight()
                        && cell.cloudP50() > 0.0F;
                out.append(String.format(Locale.ROOT,
                        "%nT168_ARM pose=%s arm=%s target=%dx%d cloudP50=%.4f cloudP95=%.4f"
                                + " compositeP50=%.4f compositeP95=%.4f frameP50=%.4f"
                                + " frameP95=%.4f speedupP50=%s savedMsP50=%s",
                        pose, arm.label(), cell.cloudWidth(), cell.cloudHeight(),
                        cell.cloudP50(), cell.cloudP95(), cell.compositeP50(),
                        cell.compositeP95(), cell.frameP50(), cell.frameP95(),
                        comparable ? String.format(Locale.ROOT, "%.4f",
                                anchor.cloudP50() / cell.cloudP50()) : "n/a",
                        comparable ? String.format(Locale.ROOT, "%.4f",
                                anchor.cloudP50() - cell.cloudP50()) : "n/a"));
            }
            // The upstream-binning ceiling.
            for (int cap : T168_DESCRIPTOR_CAPS) {
                StormT135PerformanceProfile.Cell cell = t162Cell(
                        pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName()
                                + "@d" + cap);
                if (cell == null) {
                    continue;
                }
                out.append(String.format(Locale.ROOT,
                        "%nT168_DESCRIPTOR_CEILING pose=%s residentCap=%d cloudP50=%.4f"
                                + " speedupVsFull=%s",
                        pose, cap, cell.cloudP50(),
                        anchor == null || cell.cloudP50() <= 0.0F ? "n/a"
                                : String.format(Locale.ROOT, "%.4f",
                                    anchor.cloudP50() / cell.cloudP50())));
            }
        }
        for (String pose : T168_POSES) {
            StormT135PerformanceProfile.Cell first = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName());
            StormT135PerformanceProfile.Cell repeat = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName() + "@r0.2500");
            if (first == null || repeat == null || first.cloudP50() <= 0.0F) {
                out.append(String.format(Locale.ROOT,
                        "%nT168_DRIFT pose=%s evaluated=false", pose));
                continue;
            }
            double ratio = repeat.cloudP50() / first.cloudP50();
            out.append(String.format(Locale.ROOT,
                    "%nT168_DRIFT pose=%s firstAnchorP50=%.4f repeatAnchorP50=%.4f"
                            + " ratio=%.4f verdict=%s",
                    pose, first.cloudP50(), repeat.cloudP50(), ratio,
                    Math.abs(ratio - 1.0) <= 0.05 ? "stable" : "DRIFTED"));
        }
        out.append(String.format(Locale.ROOT, "%nT168_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT167RefinementReport() {
        StringBuilder out = new StringBuilder("T167_REFINE_DECISION");
        for (String pose : T167_POSES) {
            StormT135PerformanceProfile.Cell anchor = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName());
            StormT135PerformanceProfile.Cell aggressive = t162Cell(
                    pose, CoreCostDiagnosticProgram.T166_DISTANCE_STEP.serializedName());
            for (T166Arm arm : T167_ARMS) {
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (cell == null) {
                    out.append(String.format(Locale.ROOT,
                            "%nT167_ARM pose=%s arm=%s evaluated=false", pose, arm.label()));
                    continue;
                }
                boolean comparable = anchor != null
                        && cell.cloudWidth() == anchor.cloudWidth()
                        && cell.cloudHeight() == anchor.cloudHeight()
                        && cell.cloudP50() > 0.0F;
                out.append(String.format(Locale.ROOT,
                        "%nT167_ARM pose=%s arm=%s target=%dx%d cloudP50=%.4f cloudP95=%.4f"
                                + " compositeP50=%.4f frameP50=%.4f frameP95=%.4f"
                                + " speedupP50=%s savedMsP50=%s shareOfAggressive=%s",
                        pose, arm.label(), cell.cloudWidth(), cell.cloudHeight(),
                        cell.cloudP50(), cell.cloudP95(), cell.compositeP50(),
                        cell.frameP50(), cell.frameP95(),
                        comparable ? String.format(Locale.ROOT, "%.4f",
                                anchor.cloudP50() / cell.cloudP50()) : "n/a",
                        comparable ? String.format(Locale.ROOT, "%.4f",
                                anchor.cloudP50() - cell.cloudP50()) : "n/a",
                        // How much of the aggressive arm's saving this curve keeps.
                        comparable && aggressive != null
                                && anchor.cloudP50() - aggressive.cloudP50() > 0.0001F
                                ? String.format(Locale.ROOT, "%.4f",
                                    (anchor.cloudP50() - cell.cloudP50())
                                        / (anchor.cloudP50() - aggressive.cloudP50()))
                                : "n/a"));
            }
        }
        for (String pose : T167_POSES) {
            StormT135PerformanceProfile.Cell first = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName());
            StormT135PerformanceProfile.Cell repeat = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName() + "@r0.2500");
            if (first == null || repeat == null || first.cloudP50() <= 0.0F) {
                out.append(String.format(Locale.ROOT,
                        "%nT167_DRIFT pose=%s evaluated=false", pose));
                continue;
            }
            double ratio = repeat.cloudP50() / first.cloudP50();
            out.append(String.format(Locale.ROOT,
                    "%nT167_DRIFT pose=%s firstAnchorP50=%.4f repeatAnchorP50=%.4f"
                            + " ratio=%.4f verdict=%s",
                    pose, first.cloudP50(), repeat.cloudP50(), ratio,
                    Math.abs(ratio - 1.0) <= 0.05 ? "stable" : "DRIFTED"));
        }
        out.append(String.format(Locale.ROOT, "%nT167_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT166BaselineReport() {
        StringBuilder out = new StringBuilder("T166_BASELINE_DECISION");
        for (String pose : T166_POSES) {
            StormT135PerformanceProfile.Cell anchor = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName());
            for (T166Arm arm : T166_ARMS) {
                StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
                if (cell == null) {
                    out.append(String.format(Locale.ROOT,
                            "%nT166_ARM pose=%s arm=%s evaluated=false", pose, arm.label()));
                    continue;
                }
                // Only an arm at the anchor's own resolution can be expressed
                // as a speedup over it; a resolution-curve cell is a different
                // amount of work by construction.
                // The scale alone is not enough: two cells can report one scale
                // and different targets if the framebuffer changed between them,
                // and dividing across that would publish a resize as a speedup.
                boolean comparable = anchor != null
                        && Math.abs(cell.effectiveResolutionScale()
                            - anchor.effectiveResolutionScale()) < 0.0005F
                        && cell.cloudWidth() == anchor.cloudWidth()
                        && cell.cloudHeight() == anchor.cloudHeight()
                        && cell.frameWidth() == anchor.frameWidth()
                        && cell.frameHeight() == anchor.frameHeight()
                        && cell.cloudP50() > 0.0F;
                out.append(String.format(Locale.ROOT,
                        "%nT166_ARM pose=%s arm=%s target=%dx%d scale=%.4f"
                                + " cloudP50=%.4f cloudP95=%.4f compositeP50=%.4f"
                                + " compositeP95=%.4f frameP50=%.4f frameP95=%.4f"
                                + " speedupP50=%s savedMsP50=%s",
                        pose, arm.label(), cell.cloudWidth(), cell.cloudHeight(),
                        cell.effectiveResolutionScale(),
                        cell.cloudP50(), cell.cloudP95(),
                        cell.compositeP50(), cell.compositeP95(),
                        cell.frameP50(), cell.frameP95(),
                        comparable ? String.format(Locale.ROOT, "%.4f",
                                anchor.cloudP50() / cell.cloudP50()) : "n/a",
                        comparable ? String.format(Locale.ROOT, "%.4f",
                                anchor.cloudP50() - cell.cloudP50()) : "n/a"));
            }
        }

        CoreCostDiagnosticProgram[] ladder = {
                CoreCostDiagnosticProgram.T166_FW1_ADDRESS,
                CoreCostDiagnosticProgram.T166_FW2_CANDIDATE,
                CoreCostDiagnosticProgram.T166_FW3_DESCRIPTOR,
                CoreCostDiagnosticProgram.T166_FW4_SHAPE,
                CoreCostDiagnosticProgram.T166_FW5_NODETAIL,
                CoreCostDiagnosticProgram.T166_FW6_DENSITY};
        String[] classNames = {
                "control_floor", "candidate_traversal", "descriptor_fetch",
                "shape_profile_sdf", "weather_basenoise", "detail_octaves_erosion"};
        for (String pose : T166_POSES) {
            double previous = Double.NaN;
            double full = Double.NaN;
            StormT135PerformanceProfile.Cell fullCell = t162Cell(
                    pose, CoreCostDiagnosticProgram.T166_FW6_DENSITY.serializedName());
            if (fullCell != null) {
                full = fullCell.cloudP50();
            }
            for (int rung = 0; rung < ladder.length; rung++) {
                StormT135PerformanceProfile.Cell cell =
                        t162Cell(pose, ladder[rung].serializedName());
                if (cell == null) {
                    continue;
                }
                double delta = Double.isNaN(previous)
                        ? cell.cloudP50() : cell.cloudP50() - previous;
                out.append(String.format(Locale.ROOT,
                        "%nT166_LADDER pose=%s rung=%d arm=%s class=%s cloudP50=%.4f"
                                + " deltaP50=%.4f shareOfDensityCall=%s",
                        pose, rung + 1, ladder[rung].serializedName(), classNames[rung],
                        cell.cloudP50(), delta,
                        Double.isNaN(full) || full <= 0.0
                                ? "n/a"
                                : String.format(Locale.ROOT, "%.4f", delta / full)));
                previous = cell.cloudP50();
            }
        }

        // The repeated anchor is only a drift control if something compares it.
        // Printing it beside the arms leaves a drifting fixture looking like an
        // ordinary speedup, which is the failure this row exists to catch.
        for (String pose : T166_POSES) {
            StormT135PerformanceProfile.Cell first = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName());
            StormT135PerformanceProfile.Cell repeat = t162Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName() + "@r0.2500");
            if (first == null || repeat == null || first.cloudP50() <= 0.0F) {
                out.append(String.format(Locale.ROOT,
                        "%nT166_DRIFT pose=%s evaluated=false", pose));
                continue;
            }
            double ratio = repeat.cloudP50() / first.cloudP50();
            out.append(String.format(Locale.ROOT,
                    "%nT166_DRIFT pose=%s firstAnchorP50=%.4f repeatAnchorP50=%.4f"
                            + " ratio=%.4f verdict=%s",
                    pose, first.cloudP50(), repeat.cloudP50(), ratio,
                    Math.abs(ratio - 1.0) <= 0.05 ? "stable" : "DRIFTED"));
        }

        out.append(String.format(Locale.ROOT, "%nT166_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static String buildT162AttributionReport() {
        StringBuilder out = new StringBuilder("T162_ATTRIBUTION_DECISION");
        String pose = T162_POSES[0];
        for (T162Arm arm : T162_ARMS) {
            StormT135PerformanceProfile.Cell cell = t162Cell(pose, arm.label());
            if (cell == null) {
                out.append(String.format(Locale.ROOT,
                        "%nT162_ARM arm=%s evaluated=false", arm.label()));
                continue;
            }
            out.append(String.format(Locale.ROOT,
                    "%nT162_ARM arm=%s descriptorLimit=%d target=%dx%d cloudP50=%.4f"
                            + " cloudP95=%.4f frameP50=%.4f frameP95=%.4f",
                    arm.label(), arm.descriptorLimit(),
                    cell.cloudWidth(), cell.cloudHeight(),
                    cell.cloudP50(), cell.cloudP95(), cell.frameP50(), cell.frameP95()));
        }

        CoreCostDiagnosticProgram[] ladder = {
                CoreCostDiagnosticProgram.T162_FW1_ADDRESS,
                CoreCostDiagnosticProgram.T162_FW2_CANDIDATE,
                CoreCostDiagnosticProgram.T162_FW3_DESCRIPTOR,
                CoreCostDiagnosticProgram.T162_FW4_SHAPE,
                CoreCostDiagnosticProgram.T162_FW5_NODETAIL,
                CoreCostDiagnosticProgram.T162_FW6_NORAIN,
                CoreCostDiagnosticProgram.T162_FW7_DENSITY};
        String[] classNames = {
                "control_floor", "candidate_traversal", "descriptor_fetch",
                "shape_profile_sdf", "weather_basenoise_erosion", "detail_octaves",
                "precipitation"};
        double previous = Double.NaN;
        for (int rung = 0; rung < ladder.length; rung++) {
            StormT135PerformanceProfile.Cell cell =
                    t162Cell(pose, ladder[rung].serializedName());
            if (cell == null) {
                continue;
            }
            double delta = Double.isNaN(previous) ? cell.cloudP50() : cell.cloudP50() - previous;
            out.append(String.format(Locale.ROOT,
                    "%nT162_LADDER rung=%d arm=%s class=%s cloudP50=%.4f deltaP50=%.4f",
                    rung + 1, ladder[rung].serializedName(), classNames[rung],
                    cell.cloudP50(), delta));
            previous = cell.cloudP50();
        }

        for (int limit : new int[] {1, 2, 4, 6, 8, 10}) {
            StormT135PerformanceProfile.Cell descriptorCell = t162Cell(pose,
                    CoreCostDiagnosticProgram.T162_FW3_DESCRIPTOR.serializedName() + "@d" + limit);
            StormT135PerformanceProfile.Cell shapeCell = t162Cell(pose,
                    CoreCostDiagnosticProgram.T162_FW4_SHAPE.serializedName() + "@d" + limit);
            out.append(String.format(Locale.ROOT,
                    "%nT162_SCALING descriptors=%d descriptorArmP50=%s shapeArmP50=%s",
                    limit,
                    descriptorCell == null ? "n/a"
                            : String.format(Locale.ROOT, "%.4f", descriptorCell.cloudP50()),
                    shapeCell == null ? "n/a"
                            : String.format(Locale.ROOT, "%.4f", shapeCell.cloudP50())));
        }

        out.append(String.format(Locale.ROOT, "%nT162_REJECTED count=%d %s",
                T162_REJECTED.size(),
                T162_REJECTED.isEmpty() ? "none" : String.join(",", T162_REJECTED)));
        return out.toString();
    }

    private static StormT135PerformanceProfile.Cell t162Cell(String pose, String arm) {
        for (StormT135PerformanceProfile.Cell cell : StormT135PerformanceProfile.results()) {
            if (pose.equals(cell.pose()) && arm.equals(cell.arm())) {
                return cell;
            }
        }
        return null;
    }

    /**
     * Builds the T140 cost-versus-coverage record.
     *
     * <p>One row per pose per arm, plus the coverage census, so the question
     * the campaign exists to answer can be read directly: does cloud cost track
     * the size of the render target, or the part of it that can contain cloud?
     */
    private static String buildT140CoverageReport() {
        StringBuilder out = new StringBuilder("T140_COVERAGE_DECISION");
        for (String pose : T140_POSES) {
            T140Coverage coverage = T140_COVERAGE.get(pose);
            StormT135PerformanceProfile.Cell baseline = t140Cell(
                    pose, CoreCostDiagnosticProgram.LEAN_FINAL.serializedName());
            if (coverage == null || baseline == null) {
                out.append(String.format(Locale.ROOT,
                        "%nT140_ROW pose=%s evaluated=false coverage=%s baseline=%s",
                        pose, coverage != null, baseline != null));
                continue;
            }
            out.append(String.format(Locale.ROOT,
                    "%nT140_ROW pose=%s target=%dx%d potentialPercent=%.3f"
                            + " contributingPercent=%.3f baselineCloudP50=%.4f"
                            + " baselineCloudP95=%.4f",
                    pose, coverage.width(), coverage.height(),
                    coverage.potentialPercent(), coverage.contributingPercent(),
                    baseline.cloudP50(), baseline.cloudP95()));
            for (CoreCostDiagnosticProgram arm : T140_ARMS) {
                if (arm == CoreCostDiagnosticProgram.LEAN_FINAL) {
                    continue;
                }
                StormT135PerformanceProfile.Cell cell = t140Cell(pose, arm.serializedName());
                if (cell == null) {
                    out.append(String.format(Locale.ROOT,
                            "%nT140_ARM pose=%s arm=%s evaluated=false", pose, arm.serializedName()));
                    continue;
                }
                out.append(String.format(Locale.ROOT,
                        "%nT140_ARM pose=%s arm=%s cloudP50=%.4f cloudP95=%.4f"
                                + " speedupP50=%.4fx speedupP95=%.4fx",
                        pose, arm.serializedName(), cell.cloudP50(), cell.cloudP95(),
                        cell.cloudP50() <= 0.0D
                                ? Double.NaN : baseline.cloudP50() / cell.cloudP50(),
                        cell.cloudP95() <= 0.0D
                                ? Double.NaN : baseline.cloudP95() / cell.cloudP95()));
            }
        }
        return out.toString();
    }

    private static StormT135PerformanceProfile.Cell t140Cell(String pose, String arm) {
        for (StormT135PerformanceProfile.Cell cell : StormT135PerformanceProfile.results()) {
            if (pose.equals(cell.pose()) && arm.equals(cell.arm())) {
                return cell;
            }
        }
        return null;
    }

    /**
     * Builds the T161 decision record: the same-fixture image comparison
     * between the two linked programs, and the GPU time each one took.
     *
     * <p>Both halves have to hold. A speedup with a changed image is a broken
     * translation of the experiment, and an identical image with no speedup
     * means the specialization never reached the linked program.
     */
    private static String buildT161SpecializationReport() {
        String leanArm = CoreCostDiagnosticProgram.LEAN_FINAL.serializedName();
        String monolithArm = CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH.serializedName();
        StormReferenceImageComparison.Reference leanImage = T161_IMAGES.get(leanArm);
        StormReferenceImageComparison.Reference monolithImage = T161_IMAGES.get(monolithArm);
        StringBuilder out = new StringBuilder("T161_SPECIALIZATION_DECISION");
        if (leanImage == null || monolithImage == null) {
            out.append(String.format(Locale.ROOT,
                    "%nT161_IMAGE_AB evaluated=false reason=missing_capture lean=%s monolith=%s",
                    leanImage != null, monolithImage != null));
        } else {
            StormReferenceImageComparison.Comparison comparison =
                    StormReferenceImageComparison.compare(monolithImage, leanImage);
            out.append(String.format(Locale.ROOT,
                    "%nT161_IMAGE_AB a=%s b=%s %s digestsEqual=%s",
                    monolithArm, leanArm, comparison.format(),
                    monolithImage.digest().equals(leanImage.digest())));
        }
        StormT135PerformanceProfile.Cell lean = t161Cell(leanArm);
        StormT135PerformanceProfile.Cell monolith = t161Cell(monolithArm);
        if (lean == null || monolith == null) {
            out.append(String.format(Locale.ROOT,
                    "%nT161_PERF evaluated=false reason=missing_cell lean=%s monolith=%s",
                    lean != null, monolith != null));
            return out.toString();
        }
        out.append(String.format(Locale.ROOT,
                "%nT161_PERF pose=%s mode=%s descriptors=%d target=%dx%d cloud=%dx%d"
                        + " resolutionScale=%.4f"
                        + "%nT161_PERF old=%s cloudP50=%.4f cloudP95=%.4f"
                        + "%nT161_PERF new=%s cloudP50=%.4f cloudP95=%.4f"
                        + "%nT161_PERF speedupP50=%.4fx speedupP95=%.4fx",
                lean.pose(), lean.mode(), lean.descriptors(),
                lean.frameWidth(), lean.frameHeight(),
                lean.cloudWidth(), lean.cloudHeight(), lean.effectiveResolutionScale(),
                monolithArm, monolith.cloudP50(), monolith.cloudP95(),
                leanArm, lean.cloudP50(), lean.cloudP95(),
                lean.cloudP50() <= 0.0D ? Double.NaN : monolith.cloudP50() / lean.cloudP50(),
                lean.cloudP95() <= 0.0D ? Double.NaN : monolith.cloudP95() / lean.cloudP95()));
        return out.toString();
    }

    private static StormT135PerformanceProfile.Cell t161Cell(String arm) {
        for (StormT135PerformanceProfile.Cell cell : StormT135PerformanceProfile.results()) {
            if (arm.equals(cell.arm())) {
                return cell;
            }
        }
        return null;
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
