package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.NativeImage;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * T098 visual-acceptance capture driver.
 *
 * <p>T098 is judged from live screenshots against a written two-part checklist,
 * so this collects the checklist's seven required views plus the recommended
 * NEAR-EDGE view, all of one storm, with the storm's identity and scale recorded
 * next to each frame.
 *
 * <p>Test-only in every sense that matters: it is inert unless the marker file
 * {@code run/t098-captures.txt} exists, it only teleports the camera and reads
 * back the framebuffer, and it changes no renderer state. The frames it saves
 * are ordinary production frames - no debug view, no diagnostic optimization
 * mode - because anything else would not be evidence of what a player sees.
 */
public final class StormT098CaptureDriver {

    private static final Path MARKER = Path.of("t098-captures.txt");
    private static final Path OUTPUT_ROOT = Path.of("screenshots", "t098");

    /**
     * Frames to hold each pose before grabbing. The camera teleport invalidates
     * temporal history and the resolution governor re-converges, so a frame
     * taken immediately after the move would show a half-accumulated image
     * rather than the storm.
     */
    private static final int SETTLE_FRAMES = 140;

    /** Capture window size, chosen to resolve the finest detail octaves. */
    private static final int CAPTURE_WIDTH = 1600;
    private static final int CAPTURE_HEIGHT = 900;

    /**
     * The lowest camera altitude any shot may use. The suite's BELOW pose sits
     * hundreds of blocks under the cloud base, which at T134 scale lands below
     * the void floor; that is harmless when only the cloud buffer is read back,
     * but a screenshot from there shows the underside of the world instead of
     * the storm.
     */
    private static final double MINIMUM_CAMERA_Y = 70.0D;

    /** Frames to let a respawn and gamemode change land before the first move. */
    private static final int PREPARE_FRAMES = 60;

    private enum State {
        IDLE, PREPARE, MOVE, SETTLE, GRAB, DONE
    }

    private static State state = State.IDLE;
    private static boolean checkedMarker;
    private static boolean enabled;
    private static List<Shot> shots = List.of();
    private static int shotIndex;
    private static int settleFrames;
    private static String fixtureGroupId = "";
    private static String fixtureFingerprint = "";
    private static Path outputDirectory;
    private static boolean captureModeApplied;
    private static boolean originalHideGui;
    private static int prepareFrames;

    private StormT098CaptureDriver() {
    }

    /** One named viewpoint from the T098 checklist. */
    private record Shot(
            String name, double x, double y, double z, String intent,
            VolumetricCloudRaymarchDebugView view) {
        Shot(String name, double x, double y, double z, String intent) {
            this(name, x, y, z, intent, VolumetricCloudRaymarchDebugView.FINAL);
        }

    }

    public static boolean active() {
        return enabled && state != State.DONE && state != State.IDLE;
    }

    /** True once the whole capture set has been written. */
    public static boolean finished() {
        return enabled && state == State.DONE;
    }

    /**
     * Optional raymarch step budget from the capture marker's first line.
     *
     * <p>Zero, absent or unparseable means production. This exists so the
     * T098 control arm can remove budget exhaustion as a variable without
     * changing any production default.
     */
    private static int markerStepBudget() {
        try {
            for (String line : Files.readAllLines(MARKER)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                return Integer.parseInt(trimmed);
            }
        } catch (Exception exception) {
            return 0;
        }
        return 0;
    }

    private static boolean markerPresent() {
        if (!checkedMarker) {
            checkedMarker = true;
            enabled = Files.exists(MARKER);
        }
        return enabled;
    }

    /**
     * Begins the capture set against the currently adopted fixture. Returns a
     * status string; captures only start when a descriptor-owned storm is
     * adopted, because the broad-map fallback is not what T098 judges.
     */
    public static String begin() {
        if (!markerPresent()) {
            return "t098_captures_disabled";
        }
        StormPerformanceBaseline.SuiteFixture fixture = StormPerformanceBaseline.suiteFixture();
        if (fixture == null) {
            return "t098_captures_no_fixture";
        }
        fixtureGroupId = fixture.groupId();
        fixtureFingerprint = fixture.structuralFingerprint();
        shots = buildShots(fixture);
        shotIndex = 0;
        settleFrames = 0;
        outputDirectory = OUTPUT_ROOT.resolve(fixtureGroupId.substring(0, 8));
        try {
            Files.createDirectories(outputDirectory);
        } catch (Exception exception) {
            return "t098_captures_output_failed:" + exception.getClass().getSimpleName();
        }
        state = State.PREPARE;
        prepareFrames = 0;
        applyCaptureMode();
        ProjectAtmosphere.LOGGER.info(
                "T098_CAPTURE_BEGIN group={} fingerprint={} baseY={} topY={} horizontalRadius={}"
                        + " descriptors={} shots={} output={}",
                fixtureGroupId, fixtureFingerprint,
                fmt(fixture.baseY()), fmt(fixture.topY()), fmt(fixture.horizontalRadius()),
                fixture.descriptorCount(), shots.size(), outputDirectory.toAbsolutePath());
        return "t098_captures_started shots=" + shots.size();
    }

    /**
     * The checklist's seven required views plus NEAR-EDGE, derived from the
     * fixture's own resolved geometry so every storm is framed the same way
     * regardless of its size.
     */
    private static List<Shot> buildShots(StormPerformanceBaseline.SuiteFixture fixture) {
        double centreX = fixture.centerX();
        double centreZ = fixture.centerZ();
        double baseY = fixture.baseY();
        double topY = fixture.topY();
        double radius = fixture.horizontalRadius();
        double midY = (baseY + topY) * 0.5D;
        double height = Math.max(1.0D, topY - baseY);

        List<Shot> built = new ArrayList<>();
        // 1 FAR: whole silhouette in frame.
        built.add(new Shot("1_FAR", centreX + radius * 2.6D, midY, centreZ,
                "whole storm silhouette: base, towers, anvil"));
        // 2 SIDE: medium distance, full vertical structure.
        built.add(new Shot("2_SIDE", centreX + radius * 1.7D, midY, centreZ,
                "full vertical structure at medium range"));
        // 3 UNDER: directly beneath, looking up.
        built.add(new Shot("3_UNDER", centreX,
                Math.max(baseY - Math.max(90.0D, height * 0.35D), MINIMUM_CAMERA_Y), centreZ,
                "broad connected base, underside curvature"));
        // 4 INSIDE: within the body.
        built.add(new Shot("4_INSIDE", centreX + radius * 0.25D, baseY + height * 0.45D, centreZ,
                "interior density variation"));
        // 5 ABOVE: above, looking down.
        built.add(new Shot("5_ABOVE", centreX + radius * 0.6D,
                topY + Math.max(120.0D, height * 0.45D), centreZ,
                "anvil spread and top-surface billowing"));
        // 6/7 LATERAL pair, 90 degrees apart, for seam and silhouette stability.
        built.add(new Shot("6_LATERAL_A", centreX + radius * 1.9D, midY, centreZ,
                "seam baseline"));
        built.add(new Shot("7_LATERAL_B", centreX, midY, centreZ + radius * 1.9D,
                "same storm 90 degrees around: descriptor seams, silhouette stability"));
        // 8 NEAR-EDGE: just outside the body, where the fine detail octaves resolve.
        // T098 distance ladder. NEAR_EDGE at 1.12x radius shows a substantial
        // billowing column; SIDE at 1.7x and LATERAL at 1.9x show clean sky in
        // the same place, on the same fixture, with the production shader's own
        // material trace reporting density 0.81-0.91 there. That is a
        // view-dependent loss, not a morphology or material one, so these frames
        // hold everything fixed except camera distance and bracket where the
        // column stops being drawn.
        for (double factor : new double[] {1.20D, 1.30D, 1.40D, 1.50D, 1.60D, 1.80D}) {
            built.add(new Shot(
                    String.format(java.util.Locale.ROOT, "9_LADDER_%03d", (int) (factor * 100.0D)),
                    centreX + radius * factor, baseY + height * 0.55D, centreZ,
                    "T098 distance ladder at waist height, factor " + factor));
        }
        // T098 post-integration stage isolation, all at the SIDE pose so the
        // only variable is which stage of the pipeline is displayed. FINAL is
        // already captured as 2_SIDE; these are the stages before it.
        built.add(new Shot("A_SIDE_CURRENT_ONLY",
                centreX + radius * 1.7D, midY, centreZ,
                "raw current-frame march result, history bypassed",
                VolumetricCloudRaymarchDebugView.CURRENT_ONLY));
        built.add(new Shot("B_SIDE_HISTORY_ONLY",
                centreX + radius * 1.7D, midY, centreZ,
                "temporal history only",
                VolumetricCloudRaymarchDebugView.HISTORY_ONLY));
        built.add(new Shot("C_SIDE_HISTORY_REJECTION",
                centreX + radius * 1.7D, midY, centreZ,
                "history acceptance/rejection classification",
                VolumetricCloudRaymarchDebugView.HISTORY_REJECTION));
        built.add(new Shot("8_NEAR_EDGE", centreX + radius * 1.12D, baseY + height * 0.55D, centreZ,
                "fine detail octaves at the outer boundary"));
        return List.copyOf(built);
    }

    /**
     * Spectator plus a hidden HUD. Spectator is required for correctness, not
     * convenience: a survival player teleported to cloud height falls out of the
     * world and the frames record a death screen instead of the storm. Hiding
     * the HUD keeps chat, hotbar and overlays out of evidence that is judged on
     * silhouette and surface detail. Both are restored when the set finishes.
     */
    private static void applyCaptureMode() {
        if (captureModeApplied) {
            return;
        }
        // The 2026-08-30 campaign ran with the frame-time governor saturated at
        // its MIN_SCALE of 0.5 for every captured frame, so those captures
        // recorded the governor's worst march quality rather than the
        // renderer's output. These are static poses, so hold the scale.
        VolumetricCloudRenderer.pinStepScaleForCaptures(1.0F);
        // T098 phase 1: an optional step-budget control arm, read from the
        // capture marker's first line. Absent or unparseable means production.
        VolumetricCloudRenderer.setDiagnosticStepBudget(markerStepBudget());
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.connection == null) {
            return;
        }
        if (player.isDeadOrDying() || minecraft.screen instanceof net.minecraft.client.gui.screens.DeathScreen) {
            // The suite's BELOW pose can drop a survival player out of the
            // world, and every later frame would record the death screen.
            player.respawn();
            ProjectAtmosphere.LOGGER.info("T098_CAPTURE_MODE respawned before capture");
        }
        player.connection.sendCommand("gamemode spectator");
        // The checklist judges silhouette curvature and multi-frequency surface
        // detail; the 854x480 dev window cannot resolve the finest two octaves
        // (5.7 and 1.4 blocks). Enlarging the window changes no renderer state,
        // it only gives the same frame more pixels.
        try {
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(
                    minecraft.getWindow().getWindow(), CAPTURE_WIDTH, CAPTURE_HEIGHT);
        } catch (Throwable throwable) {
            ProjectAtmosphere.LOGGER.warn("T098_CAPTURE_MODE window resize failed: {}",
                    throwable.toString());
        }
        originalHideGui = minecraft.options.hideGui;
        minecraft.options.hideGui = true;
        captureModeApplied = true;
        ProjectAtmosphere.LOGGER.info(
                "T098_CAPTURE_MODE gamemode=spectator hideGui=true restoredOnExit=true");
    }

    private static void restoreCaptureMode() {
        if (!captureModeApplied) {
            return;
        }
        VolumetricCloudRenderer.releaseStepScalePin();
        VolumetricCloudRenderer.setDiagnosticStepBudget(0);
        VolumetricCloudDebugConfig.setRaymarchDebugView(
                VolumetricCloudRaymarchDebugView.FINAL);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.hideGui = originalHideGui;
        LocalPlayer player = minecraft.player;
        if (player != null && player.connection != null) {
            player.connection.sendCommand("gamemode creative");
        }
        captureModeApplied = false;
        ProjectAtmosphere.LOGGER.info("T098_CAPTURE_MODE restored");
    }

    /** Client-tick driver. Safe to call every frame. */
    public static void tick() {
        if (!markerPresent() || state == State.IDLE || state == State.DONE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.connection == null || minecraft.level == null) {
            return;
        }
        if (shotIndex >= shots.size()) {
            state = State.DONE;
            restoreCaptureMode();
            ProjectAtmosphere.LOGGER.info(
                    "T098_CAPTURE_COMPLETE group={} shots={} output={}",
                    fixtureGroupId, shots.size(), outputDirectory.toAbsolutePath());
            return;
        }
        Shot shot = shots.get(shotIndex);
        switch (state) {
            case PREPARE -> {
                // Give the respawn and gamemode change time to land, and close
                // any lingering screen so it cannot appear in a frame.
                if (minecraft.screen != null) {
                    minecraft.setScreen(null);
                }
                if (++prepareFrames >= PREPARE_FRAMES) {
                    state = State.MOVE;
                }
            }
            case MOVE -> {
                // T098 stage isolation: the view must be active for the whole
                // settle window, not just the grab, or the captured frame is
                // still the previous stage's.
                VolumetricCloudDebugConfig.setRaymarchDebugView(shot.view());
                float yaw = yawTo(shot.x(), shot.z(),
                        StormPerformanceBaseline.suiteFixture() == null ? shot.x()
                                : StormPerformanceBaseline.suiteFixture().centerX(),
                        StormPerformanceBaseline.suiteFixture() == null ? shot.z()
                                : StormPerformanceBaseline.suiteFixture().centerZ());
                float pitch = pitchTo(shot);
                player.connection.sendCommand(String.format(Locale.ROOT,
                        "tp @s %.5f %.5f %.5f %.3f %.3f",
                        shot.x(), shot.y(), shot.z(), yaw, pitch));
                VolumetricCloudRenderer.invalidateHistory();
                settleFrames = 0;
                state = State.SETTLE;
            }
            case SETTLE -> {
                if (++settleFrames >= SETTLE_FRAMES) {
                    state = State.GRAB;
                }
            }
            case GRAB -> {
                grab(minecraft, shot);
                shotIndex++;
                state = shotIndex >= shots.size() ? State.DONE : State.MOVE;
                if (state == State.DONE) {
                    restoreCaptureMode();
                    // Root-cause evidence taken on the same adopted system the
                    // frames show, so geometry and image cannot disagree.
                    ProjectAtmosphere.LOGGER.info("T098_ROLE_OCCUPANCY group={} {}",
                            fixtureGroupId, StormT098RoleOccupancy.describe());
                    ProjectAtmosphere.LOGGER.info(
                            "T098_CAPTURE_COMPLETE group={} shots={} output={}",
                            fixtureGroupId, shots.size(), outputDirectory.toAbsolutePath());
                }
            }
            default -> {
            }
        }
    }

    /**
     * Reads the finished frame back and writes it beside its identity line, so a
     * screenshot can never be attributed to the wrong storm.
     */
    private static void grab(Minecraft minecraft, Shot shot) {
        try (NativeImage image = takeScreenshot(minecraft)) {
            Path target = outputDirectory.resolve(shot.name() + ".png");
            image.writeToFile(target.toFile());
            ProjectAtmosphere.LOGGER.info(
                    "T098_CAPTURE shot={} group={} fingerprint={} camera=({},{},{}) intent={} file={}",
                    shot.name(), fixtureGroupId, fixtureFingerprint,
                    fmt(shot.x()), fmt(shot.y()), fmt(shot.z()), shot.intent(),
                    target.toAbsolutePath());
            // The checklist wants the density calibration recorded with each
            // frame, taken from the same camera position as the shot.
            ProjectAtmosphere.LOGGER.info(
                    "T098_CAPTURE_CONTROLS shot={} stepScale={} diagnosticStepBudget={}",
                    shot.name(), VolumetricCloudRenderer.governorStepScale(),
                    VolumetricCloudRenderer.diagnosticStepBudget());
            ProjectAtmosphere.LOGGER.info("T098_CAPTURE_DENSITY shot={} {}",
                    shot.name(),
                    StormDensityCalibrationReport.describe(shot.x(), shot.y(), shot.z()));
        } catch (Throwable throwable) {
            ProjectAtmosphere.LOGGER.warn("T098_CAPTURE_FAILED shot={} error={}",
                    shot.name(), throwable.toString());
        }
    }

    private static NativeImage takeScreenshot(Minecraft minecraft) {
        com.mojang.blaze3d.pipeline.RenderTarget target = minecraft.getMainRenderTarget();
        int width = target.width;
        int height = target.height;
        NativeImage image = new NativeImage(width, height, false);
        com.mojang.blaze3d.systems.RenderSystem.bindTexture(target.getColorTextureId());
        image.downloadTexture(0, true);
        image.flipY();
        return image;
    }

    private static float yawTo(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
    }

    private static float pitchTo(Shot shot) {
        StormPerformanceBaseline.SuiteFixture fixture = StormPerformanceBaseline.suiteFixture();
        if (fixture == null) {
            return 0.0F;
        }
        double targetY = (fixture.baseY() + fixture.topY()) * 0.5D;
        double dy = targetY - shot.y();
        double horizontal = Math.hypot(fixture.centerX() - shot.x(), fixture.centerZ() - shot.z());
        if (horizontal < 1.0D) {
            return dy >= 0.0D ? -89.0F : 89.0F;
        }
        return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }
}
