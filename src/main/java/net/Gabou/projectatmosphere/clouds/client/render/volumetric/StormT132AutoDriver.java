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
    /** SC-006 states its total-frame budget at this resolution. */
    private static final int T135_WIDTH = 1920;
    private static final int T135_HEIGHT = 1080;
    /** Frames held after a teleport before the first mode is sampled. */
    private static final int T135_POSE_SETTLE_FRAMES = 120;
    private static final Pattern BASE_TOP =
            Pattern.compile("baseTop=(-?[0-9.]+)\\.\\.(-?[0-9.]+)");

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
        T135_PREPARE, T135_MOVE, T135_SETTLE, T135_SAMPLE, T135_REPORT,
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
        return Files.exists(T135_MARKER);
    }

    /** The five shipped quality modes, in ascending cost order. */
    private static final AtmoCommonConfig.CloudRaymarchQuality[] T135_MODES = {
            AtmoCommonConfig.CloudRaymarchQuality.LOW,
            AtmoCommonConfig.CloudRaymarchQuality.LOW_24,
            AtmoCommonConfig.CloudRaymarchQuality.MEDIUM,
            AtmoCommonConfig.CloudRaymarchQuality.HIGH,
            AtmoCommonConfig.CloudRaymarchQuality.ULTRA
    };
    private static final String[] T135_POSES = {"SIDE", "FAR", "BELOW", "ABOVE", "CLEAR"};
    private static int t135PoseIndex;
    private static int t135ModeIndex;
    private static int t135SettleFrames;

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
                    advance(rayTraceRunRequested() ? Phase.RAYTRACE_FIXTURE : Phase.BEGIN_SUITE);
                }
                if (stageFrames > MATURE_TIMEOUT_FRAMES) {
                    ProjectAtmosphere.LOGGER.info(
                            "T132_AUTORUN storm never matured; proceeding at generation {}", generation);
                    advance(rayTraceRunRequested() ? Phase.RAYTRACE_FIXTURE : Phase.BEGIN_SUITE);
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
                    advance(performanceRunRequested() ? Phase.T135_PREPARE : Phase.BEGIN_T098);
                } else if (stageFrames > ADOPT_TIMEOUT_FRAMES) {
                    finish("raytrace_fixture_failed:" + begun);
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
                ProjectAtmosphere.LOGGER.info(
                        "T135_PROFILE_BEGIN poses={} modes={} target={}x{}",
                        T135_POSES.length, T135_MODES.length, T135_WIDTH, T135_HEIGHT);
                advance(Phase.T135_MOVE);
            }
            case T135_MOVE -> {
                StormPerformanceBaseline.SuiteFixture fixture =
                        StormPerformanceBaseline.suiteFixture();
                if (fixture == null) {
                    finish("t135_fixture_lost");
                    return;
                }
                double radius = fixture.horizontalRadius();
                double midY = (fixture.baseY() + fixture.topY()) * 0.5D;
                double height = Math.max(1.0D, fixture.topY() - fixture.baseY());
                double x = fixture.centerX();
                double y = midY;
                double z = fixture.centerZ();
                switch (T135_POSES[t135PoseIndex]) {
                    case "SIDE" -> x = fixture.centerX() + radius * 1.7D;
                    case "FAR" -> x = fixture.centerX() + radius * 2.6D;
                    case "BELOW" -> {
                        y = Math.max(fixture.baseY() - Math.max(90.0D, height * 0.35D), 70.0D);
                    }
                    case "ABOVE" -> {
                        x = fixture.centerX() + radius * 0.6D;
                        y = fixture.topY() + Math.max(120.0D, height * 0.45D);
                    }
                    default -> {
                        // Clear-weather context: far enough that the storm is not
                        // in frame, so the non-cloud remainder is measurable
                        // against a scene the storm does not dominate.
                        x = fixture.centerX() + radius * 12.0D;
                        y = midY;
                    }
                }
                float yaw = (float) (Math.toDegrees(Math.atan2(
                        fixture.centerZ() - z, fixture.centerX() - x)) - 90.0D);
                float pitch = 0.0F;
                if ("ABOVE".equals(T135_POSES[t135PoseIndex])
                        || "BELOW".equals(T135_POSES[t135PoseIndex])) {
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
                    advance(Phase.T135_SAMPLE);
                }
            }
            case T135_SAMPLE -> {
                if (StormT135PerformanceProfile.active()) {
                    return;
                }
                if (t135ModeIndex >= T135_MODES.length) {
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
                StormT135PerformanceProfile.begin(T135_POSES[t135PoseIndex], quality);
                t135ModeIndex++;
            }
            case T135_REPORT -> {
                StringBuilder out = new StringBuilder("T135_PROFILE_COMPLETE cells="
                        + StormT135PerformanceProfile.results().size());
                for (StormT135PerformanceProfile.Cell cell
                        : StormT135PerformanceProfile.results()) {
                    out.append(String.format(Locale.ROOT,
                            "%nT135_CELL|%s|%s|%d|%.3f|%dx%d|%dx%d|%d"
                                    + "|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f",
                            cell.pose(), cell.mode(), cell.raymarchSteps(),
                            cell.resolutionScale(), cell.frameWidth(), cell.frameHeight(),
                            cell.cloudWidth(), cell.cloudHeight(),
                            cell.samples(), cell.cloudP50(), cell.cloudP95(),
                            cell.frameP50(), cell.frameP95(), cell.remainderP50(),
                            cell.cloudMean()));
                }
                ProjectAtmosphere.LOGGER.info(out.toString());
                // Restore the acceptance configuration for the capture set.
                AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.set(
                        AtmoCommonConfig.CloudRaymarchQuality.ULTRA);
                applyFixtureResolutionControl();
                advance(Phase.BEGIN_T098);
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
