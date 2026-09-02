package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.NativeImage;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldCompositeDebugMode;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderConfig;
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
    /**
     * T138 rank-1 marker. When present the set becomes the internal-resolution
     * ladder instead of the T098 checklist: the same five poses captured once
     * per internal resolution, so structural readability and reconstruction
     * artefacts can be graded against pixel count on one fixture.
     */
    private static final Path RESOLUTION_MARKER = Path.of("t138-captures.txt");
    private static final Path OUTPUT_ROOT = Path.of("screenshots", "t098");
    private static final Path RESOLUTION_OUTPUT_ROOT = Path.of("screenshots", "t138");

    /**
     * The internal-resolution arms, matching the T138 performance sweep exactly
     * so a frame and a millisecond figure describe the same configuration.
     */
    private static final float[] RESOLUTION_LADDER = {1.00F, 0.75F, 0.50F, 0.375F, 0.25F};

    /**
     * Frames held between a shot's two frames when it carries a temporal pair.
     * Long enough for the history blend to turn over several times at the
     * shipped 0.85 blend weight, so a shimmering pixel differs between them and
     * a stable one does not.
     */
    private static final int TEMPORAL_PAIR_FRAMES = 8;

    /**
     * Frames to hold each pose before grabbing. The camera teleport invalidates
     * temporal history and the resolution governor re-converges, so a frame
     * taken immediately after the move would show a half-accumulated image
     * rather than the storm.
     */
    private static final int SETTLE_FRAMES = 140;

    /**
     * The ladder's settle window, counted in presented frames rather than
     * client ticks. The shipped history blend is 0.85, so its accumulation time
     * constant is about seven frames; twenty-four is more than three of those.
     * The checklist set keeps its own 140-tick window unchanged.
     */
    private static final int LADDER_SETTLE_FRAMES = 16;

    /** Capture window size, chosen to resolve the finest detail octaves. */
    private static final int CAPTURE_WIDTH = 1600;
    private static final int CAPTURE_HEIGHT = 900;

    /**
     * The resolution ladder captures at the SC-006 reference resolution rather
     * than the checklist's 1600x900. Every arm then lands on integer cloud
     * target dimensions - 1920x1080, 1440x810, 960x540, 720x405, 480x270 - so a
     * measured artefact is the reconstruction's, not a rounding remainder's.
     */
    private static final int RESOLUTION_CAPTURE_WIDTH = 1920;
    private static final int RESOLUTION_CAPTURE_HEIGHT = 1080;

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

    /**
     * Frames the ray trace may occupy before the capture set gives up on it.
     * The trace itself needs one frame per arm per pixel; this only bounds a
     * failure.
     */
    private static final int RAYTRACE_TIMEOUT_FRAMES = 600;

    private enum State {
        IDLE, PREPARE, MOVE, SETTLE, GRAB, DONE, RAYTRACE, TEMPORAL_PAIR, AUX_VIEW,
        RECONSTRUCTION_ARM, RECONSTRUCTION_PAIR
    }

    /**
     * The paired diagnostic views grabbed after each ladder frame. ALPHA and
     * DEPTH show the marched low-resolution fields; ALIGNMENT classifies the
     * reconstruction's own footprint - paired colour+depth, colour without
     * depth, depth without colour, scene-rejected - so coverage, silhouette and
     * colour/depth disagreement are measured from the composite's decision
     * rather than inferred from a colour heuristic over sky, terrain and cloud.
     */
    private static final CloudFieldCompositeDebugMode[] LADDER_AUX_VIEWS = {
            CloudFieldCompositeDebugMode.ALPHA,
            CloudFieldCompositeDebugMode.DEPTH,
            CloudFieldCompositeDebugMode.ALIGNMENT
    };

    /** Frames held after switching composite view before its frame is grabbed. */
    private static final int AUX_VIEW_FRAMES = 3;

    private static State state = State.IDLE;
    private static boolean checkedMarker;
    private static boolean enabled;
    private static List<Shot> shots = List.of();
    private static int shotIndex;
    private static int settleFrames;
    private static String fixtureGroupId = "";
    private static int fixtureDescriptors;
    private static String fixtureFingerprint = "";
    private static Path outputDirectory;
    private static boolean captureModeApplied;
    private static boolean originalHideGui;
    private static int prepareFrames;
    private static int rayTraceFrames;
    private static boolean resolutionLadder;
    private static int temporalPairFrames;
    private static int auxViewIndex;
    private static int auxViewFrames;
    private static int reconstructionFrames;
    private static long lastPresentedFrame = -1L;
    private static int stalledTicks;
    private static float originalLadderResolutionScale = Float.NaN;

    private StormT098CaptureDriver() {
    }

    /**
     * One named viewpoint from the T098 checklist.
     *
     * @param rayTrace when true, the T098 production ray trace runs immediately
     *                 after this frame is grabbed, at this pose and on this
     *                 render target, so the trace and the image describe the
     *                 same rendered pixels.
     */
    private record Shot(
            String name, double x, double y, double z, String intent,
            VolumetricCloudRaymarchDebugView view, boolean rayTrace,
            boolean legacyHitDepth, boolean legacyFinePromotion,
            float resolutionScale, boolean temporalPair) {
        Shot(String name, double x, double y, double z, String intent) {
            this(name, x, y, z, intent, VolumetricCloudRaymarchDebugView.FINAL,
                    false, false, false);
        }

        Shot(String name, double x, double y, double z, String intent,
                VolumetricCloudRaymarchDebugView view) {
            this(name, x, y, z, intent, view, false, false, false);
        }

        Shot(String name, double x, double y, double z, String intent,
                VolumetricCloudRaymarchDebugView view, boolean rayTrace) {
            this(name, x, y, z, intent, view, rayTrace, false, false);
        }

        Shot(String name, double x, double y, double z, String intent,
                VolumetricCloudRaymarchDebugView view, boolean rayTrace,
                boolean legacyHitDepth) {
            this(name, x, y, z, intent, view, rayTrace, legacyHitDepth, false);
        }

        Shot(String name, double x, double y, double z, String intent,
                VolumetricCloudRaymarchDebugView view, boolean rayTrace,
                boolean legacyHitDepth, boolean legacyFinePromotion) {
            this(name, x, y, z, intent, view, rayTrace, legacyHitDepth,
                    legacyFinePromotion, Float.NaN, false);
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
            resolutionLadder = Files.exists(RESOLUTION_MARKER);
            enabled = resolutionLadder || Files.exists(MARKER);
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
        fixtureDescriptors = StormGeometryBuildCoordinator.lobeCount();
        shots = resolutionLadder ? buildResolutionLadderShots(fixture) : buildShots(fixture);
        shotIndex = 0;
        settleFrames = 0;
        outputDirectory = (resolutionLadder ? RESOLUTION_OUTPUT_ROOT : OUTPUT_ROOT)
                .resolve(fixtureGroupId.substring(0, 8));
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
     * The T138 internal-resolution ladder: five poses, each captured at every
     * internal resolution under test, on one fixture, with the step budget,
     * lighting and quality mode held constant.
     *
     * <p>Every shot carries a temporal pair - two frames of the same static
     * pose, {@value #TEMPORAL_PAIR_FRAMES} frames apart - because shimmer at a
     * reduced internal resolution is a difference between consecutive frames
     * and cannot be seen in a single one.
     *
     * <p>The pose set is the checklist's structural quartet plus the
     * representative gameplay pose, which is the one the performance budget
     * actually has to hold and which the T098 checklist does not include.
     */
    private static List<Shot> buildResolutionLadderShots(
            StormPerformanceBaseline.SuiteFixture fixture) {
        double centreX = fixture.centerX();
        double centreZ = fixture.centerZ();
        double baseY = fixture.baseY();
        double topY = fixture.topY();
        double radius = fixture.horizontalRadius();
        double midY = (baseY + topY) * 0.5D;
        double height = Math.max(1.0D, topY - baseY);

        record Pose(String name, double x, double y, double z, String intent) {
        }
        Pose[] poses = {
                new Pose("SIDE", centreX + radius * 1.7D, midY, centreZ,
                        "full vertical structure at medium range"),
                new Pose("FAR", centreX + radius * 2.6D, midY, centreZ,
                        "whole silhouette; the pose reconstruction can erase"),
                new Pose("ABOVE", centreX + radius * 0.6D,
                        topY + Math.max(120.0D, height * 0.45D), centreZ,
                        "anvil top surface detail"),
                new Pose("BELOW", centreX,
                        Math.max(baseY - Math.max(90.0D, height * 0.35D), MINIMUM_CAMERA_Y),
                        centreZ, "base underside and terrain/cloud edge"),
                // The shipped PLAY_NEAR pose puts the camera 4x the storm
                // radius away; at T134 scale that is past the 2000-block cloud
                // render distance, so it captures empty sky and, worse, moving
                // there unloads the storm's descriptors and the rest of the set
                // records a stormless scene. This keeps gameplay altitude and a
                // distance from which the storm is actually drawn.
                new Pose("PLAY_VIS_NEAR", centreX + radius * 1.6D, 120.0D, centreZ,
                        "representative gameplay altitude with the storm in frame")
        };

        // Pose-major, not scale-major. The comparison this set exists to make
        // is across scales at one pose, and a severe fixture does not reliably
        // survive the whole ladder; grouping by pose means a fixture that dies
        // costs whole poses rather than the low-resolution half of every one.
        // Non-comment lines of the ladder marker select which poses to capture,
        // so a set interrupted by a decaying fixture can be completed for the
        // poses it did not reach without recapturing the ones it did.
        java.util.Set<String> selected = new java.util.HashSet<>();
        try {
            for (String line : Files.readAllLines(RESOLUTION_MARKER)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    selected.add(trimmed.toUpperCase(Locale.ROOT));
                }
            }
        } catch (Exception exception) {
            selected.clear();
        }

        List<Shot> built = new ArrayList<>();
        for (Pose pose : poses) {
            if (!selected.isEmpty() && !selected.contains(pose.name())) {
                continue;
            }
            for (float scale : RESOLUTION_LADDER) {
                String scaleTag = String.format(Locale.ROOT, "%03d", Math.round(scale * 1000.0F));
                built.add(new Shot(
                        "R" + scaleTag + "_" + pose.name(),
                        pose.x(), pose.y(), pose.z(),
                        pose.intent() + " at internal scale " + scale,
                        VolumetricCloudRaymarchDebugView.FINAL,
                        false, false, false, scale, true));
            }
        }
        return List.copyOf(built);
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
                VolumetricCloudRaymarchDebugView.CURRENT_ONLY, true));
        built.add(new Shot("B_SIDE_HISTORY_ONLY",
                centreX + radius * 1.7D, midY, centreZ,
                "temporal history only",
                VolumetricCloudRaymarchDebugView.HISTORY_ONLY));
        built.add(new Shot("C_SIDE_HISTORY_REJECTION",
                centreX + radius * 1.7D, midY, centreZ,
                "history acceptance/rejection classification",
                VolumetricCloudRaymarchDebugView.HISTORY_REJECTION));
        // T098 depth-sentinel evidence pair. Same fixture, same poses, same
        // frame configuration as 2_SIDE and 1_FAR above; the only difference is
        // that a cloud hit is allowed to publish the composite's miss sentinel
        // again, as it did before the correction. D/E are therefore the direct
        // before-images for 2_SIDE and 1_FAR.
        built.add(new Shot("D_SIDE_LEGACY_HIT_DEPTH",
                centreX + radius * 1.7D, midY, centreZ,
                "SIDE with the pre-fix saturating hit depth restored",
                VolumetricCloudRaymarchDebugView.CURRENT_ONLY, false, true));
        built.add(new Shot("E_FAR_LEGACY_HIT_DEPTH",
                centreX + radius * 2.6D, midY, centreZ,
                "FAR with the pre-fix saturating hit depth restored",
                VolumetricCloudRaymarchDebugView.CURRENT_ONLY, false, true));
        built.add(new Shot("F_SIDE_CORRECTED",
                centreX + radius * 1.7D, midY, centreZ,
                "SIDE after-image paired with D, identical except for the correction",
                VolumetricCloudRaymarchDebugView.CURRENT_ONLY));
        built.add(new Shot("G_FAR_CORRECTED",
                centreX + radius * 2.6D, midY, centreZ,
                "FAR after-image paired with E, identical except for the correction",
                VolumetricCloudRaymarchDebugView.CURRENT_ONLY));
        // T098 promotion-policy cost pair. Four views, each captured twice on
        // one fixture with only the promotion policy differing, so the GPU time
        // and march work of the two policies are compared like for like.
        double[][] perfPoses = {
                {centreX + radius * 1.7D, midY, centreZ},
                {centreX + radius * 2.6D, midY, centreZ},
                {centreX + radius * 0.6D, topY + Math.max(120.0D, height * 0.45D), centreZ},
                {centreX, Math.max(baseY - Math.max(90.0D, height * 0.35D), MINIMUM_CAMERA_Y),
                        centreZ}
        };
        String[] perfNames = {"SIDE", "FAR", "ABOVE", "BELOW"};
        for (int index = 0; index < perfPoses.length; index++) {
            built.add(new Shot("P_" + perfNames[index] + "_LEGACY_PROMOTION",
                    perfPoses[index][0], perfPoses[index][1], perfPoses[index][2],
                    "cost of the pre-fix promotion policy",
                    VolumetricCloudRaymarchDebugView.FINAL, false, false, true));
            built.add(new Shot("P_" + perfNames[index] + "_CORRECTED_PROMOTION",
                    perfPoses[index][0], perfPoses[index][1], perfPoses[index][2],
                    "cost of the bounded empty-span scan",
                    VolumetricCloudRaymarchDebugView.FINAL));
        }
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
                    minecraft.getWindow().getWindow(),
                    resolutionLadder ? RESOLUTION_CAPTURE_WIDTH : CAPTURE_WIDTH,
                    resolutionLadder ? RESOLUTION_CAPTURE_HEIGHT : CAPTURE_HEIGHT);
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
        VolumetricCloudDebugConfig.setT098LegacyHitDepth(false);
        VolumetricCloudDebugConfig.setT098LegacyFinePromotion(false);
        VolumetricCloudDebugConfig.setT136ConstantLighting(false);
        CloudFieldVolumeRenderConfig.setCompositeDebugMode(CloudFieldCompositeDebugMode.FINAL);
        VolumetricCloudDebugConfig.setCoverageAlphaReconstruction(false);
        if (Float.isFinite(originalLadderResolutionScale) || resolutionLadder) {
            VolumetricCloudDebugConfig.setFixedResolutionScale(originalLadderResolutionScale);
            originalLadderResolutionScale = Float.NaN;
        }
        ProjectAtmosphere.LOGGER.info("T098_CAPTURE_MODE restored");
    }

    /** Client-tick driver. Safe to call every frame. */
    public static void tick() {
        if (!markerPresent() || state == State.IDLE || state == State.DONE) {
            return;
        }
        if (resolutionLadder && !consumePresentedFrame()) {
            // One driver step per presented frame. Counting client ticks here
            // grabs frames that were never drawn: at the ladder's most
            // expensive arm the render loop runs several ticks per frame, so a
            // three-tick wait after a composite-view change can elapse before
            // the new view has been rendered even once. That is exactly how the
            // first ladder attempt captured the ALIGNMENT view as if it were
            // the reconstruction arm.
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
                // Held for the whole settle window, not just the grab, so the
                // captured frame really is that arm's.
                VolumetricCloudDebugConfig.setT098LegacyHitDepth(shot.legacyHitDepth());
                VolumetricCloudDebugConfig.setT098LegacyFinePromotion(
                        shot.legacyFinePromotion());
                if (Float.isFinite(shot.resolutionScale())) {
                    // Held for the whole settle window. A scale change destroys
                    // and rebuilds the cloud targets and invalidates history, so
                    // applying it at the grab would capture a target mid-rebuild.
                    if (!Float.isFinite(originalLadderResolutionScale)) {
                        originalLadderResolutionScale =
                                VolumetricCloudDebugConfig.fixedResolutionScale();
                    }
                    VolumetricCloudDebugConfig.setFixedResolutionScale(shot.resolutionScale());
                }
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
                if (++settleFrames >= (resolutionLadder ? LADDER_SETTLE_FRAMES : SETTLE_FRAMES)) {
                    state = State.GRAB;
                }
            }
            case TEMPORAL_PAIR -> {
                if (++temporalPairFrames >= TEMPORAL_PAIR_FRAMES) {
                    grab(minecraft, shot, "_T" + TEMPORAL_PAIR_FRAMES);
                    auxViewIndex = 0;
                    auxViewFrames = 0;
                    CloudFieldVolumeRenderConfig.setCompositeDebugMode(
                            LADDER_AUX_VIEWS[0]);
                    state = State.AUX_VIEW;
                }
            }
            case AUX_VIEW -> {
                if (++auxViewFrames < AUX_VIEW_FRAMES) {
                    return;
                }
                grab(minecraft, shot,
                        "_" + LADDER_AUX_VIEWS[auxViewIndex].serializedName().toUpperCase(Locale.ROOT));
                auxViewIndex++;
                auxViewFrames = 0;
                if (auxViewIndex < LADDER_AUX_VIEWS.length) {
                    CloudFieldVolumeRenderConfig.setCompositeDebugMode(
                            LADDER_AUX_VIEWS[auxViewIndex]);
                    return;
                }
                CloudFieldVolumeRenderConfig.setCompositeDebugMode(
                        CloudFieldCompositeDebugMode.FINAL);
                // The candidate reconstruction is captured on the same pose,
                // the same scale and the same fixture as the shipped one, so
                // the pair differs only in the composite's alpha term.
                VolumetricCloudDebugConfig.setCoverageAlphaReconstruction(true);
                reconstructionFrames = 0;
                state = State.RECONSTRUCTION_ARM;
            }
            case RECONSTRUCTION_ARM -> {
                if (++reconstructionFrames < AUX_VIEW_FRAMES) {
                    return;
                }
                grab(minecraft, shot, "_RECON");
                reconstructionFrames = 0;
                state = State.RECONSTRUCTION_PAIR;
            }
            case RECONSTRUCTION_PAIR -> {
                if (++reconstructionFrames < TEMPORAL_PAIR_FRAMES) {
                    return;
                }
                grab(minecraft, shot, "_RECON_T" + TEMPORAL_PAIR_FRAMES);
                VolumetricCloudDebugConfig.setCoverageAlphaReconstruction(false);
                shotIndex++;
                state = shotIndex >= shots.size() ? State.DONE : State.MOVE;
                if (state == State.DONE) {
                    restoreCaptureMode();
                    ProjectAtmosphere.LOGGER.info(
                            "T098_CAPTURE_COMPLETE group={} shots={} output={}",
                            fixtureGroupId, shots.size(), outputDirectory.toAbsolutePath());
                }
            }
            case GRAB -> {
                grab(minecraft, shot);
                if (shot.temporalPair()) {
                    temporalPairFrames = 0;
                    state = State.TEMPORAL_PAIR;
                    return;
                }
                if (shot.rayTrace()) {
                    // The production ray trace runs here, immediately after the
                    // frame it explains, at the same pose, the same render
                    // target and the same debug view. Taking it anywhere else
                    // lets the traced ray and the captured pixel differ in
                    // configuration, which is exactly the confusion this
                    // investigation has to avoid.
                    String requested = StormProductionRayTrace.requestStormColumn(
                            minecraft,
                            StormPerformanceBaseline.suiteFixture(),
                            shot.name() + '-' + fixtureGroupId.substring(0, 8));
                    ProjectAtmosphere.LOGGER.info(
                            "T098_RAYTRACE requested at shot={}: {}", shot.name(), requested);
                    if (requested.startsWith("acquiring")) {
                        rayTraceFrames = 0;
                        state = State.RAYTRACE;
                        return;
                    }
                }
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
            case RAYTRACE -> {
                String latest = StormProductionRayTrace.latest();
                boolean finished = !StormProductionRayTrace.active();
                if (finished) {
                    ProjectAtmosphere.LOGGER.info("T098_RAYTRACE_REPORT\n{}", latest);
                }
                if (finished || ++rayTraceFrames > RAYTRACE_TIMEOUT_FRAMES) {
                    if (!finished) {
                        ProjectAtmosphere.LOGGER.warn(
                                "T098_RAYTRACE timed out: {}", latest);
                    }
                    shotIndex++;
                    state = shotIndex >= shots.size() ? State.DONE : State.MOVE;
                    if (state == State.DONE) {
                        restoreCaptureMode();
                        ProjectAtmosphere.LOGGER.info(
                                "T098_CAPTURE_COMPLETE group={} shots={} output={}",
                                fixtureGroupId, shots.size(), outputDirectory.toAbsolutePath());
                    }
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
        grab(minecraft, shot, "");
    }

    private static void grab(Minecraft minecraft, Shot shot, String suffix) {
        try (NativeImage image = takeScreenshot(minecraft)) {
            Path target = outputDirectory.resolve(shot.name() + suffix + ".png");
            image.writeToFile(target.toFile());
            ProjectAtmosphere.LOGGER.info(
                    "T098_CAPTURE shot={} group={} fingerprint={} camera=({},{},{}) intent={}"
                            + " mainTarget={}x{} cloudTarget={}x{} effectiveResolutionScale={}"
                            + " file={}",
                    shot.name() + suffix, fixtureGroupId, fixtureFingerprint,
                    fmt(shot.x()), fmt(shot.y()), fmt(shot.z()), shot.intent(),
                    minecraft.getMainRenderTarget().width,
                    minecraft.getMainRenderTarget().height,
                    VolumetricCloudRenderTargets.currentCloudTarget() == null
                            ? 0 : VolumetricCloudRenderTargets.currentCloudTarget().width,
                    VolumetricCloudRenderTargets.currentCloudTarget() == null
                            ? 0 : VolumetricCloudRenderTargets.currentCloudTarget().height,
                    fmt(VolumetricCloudRenderer.lastResolutionScale()),
                    target.toAbsolutePath());
            // A capture set whose storm decayed records a stormless sky under
            // the same file names as a valid arm, which is worse than a missing
            // frame. Every grab carries its descriptor count, and the first
            // drop is called out rather than left to be discovered in the
            // image statistics.
            int descriptors = StormGeometryBuildCoordinator.lobeCount();
            if (descriptors < fixtureDescriptors) {
                ProjectAtmosphere.LOGGER.warn(
                        "T098_CAPTURE_FIXTURE_LOST shot={} descriptors fell {} -> {};"
                                + " this frame and every later one are not evidence",
                        shot.name() + suffix, fixtureDescriptors, descriptors);
            }
            ProjectAtmosphere.LOGGER.info("T098_CAPTURE_FIXTURE shot={} descriptors={}",
                    shot.name() + suffix, descriptors);
            // The checklist wants the density calibration recorded with each
            // frame, taken from the same camera position as the shot.
            ProjectAtmosphere.LOGGER.info(
                    "T098_CAPTURE_CONTROLS shot={} stepScale={} diagnosticStepBudget={}"
                            + " legacyHitDepth={} legacyFinePromotion={} gpuMs={}",
                    shot.name(), VolumetricCloudRenderer.governorStepScale(),
                    VolumetricCloudRenderer.diagnosticStepBudget(),
                    VolumetricCloudDebugConfig.t098LegacyHitDepth(),
                    VolumetricCloudDebugConfig.t098LegacyFinePromotion(),
                    fmt(VolumetricCloudRenderer.lastGpuMilliseconds()));
            ProjectAtmosphere.LOGGER.info("T098_CAPTURE_DENSITY shot={} {}",
                    shot.name(),
                    StormDensityCalibrationReport.describe(shot.x(), shot.y(), shot.z()));
        } catch (Throwable throwable) {
            ProjectAtmosphere.LOGGER.warn("T098_CAPTURE_FAILED shot={} error={}",
                    shot.name(), throwable.toString());
        }
    }

    /**
     * True once per presented frame. A run in which the volumetric pass stops
     * presenting entirely would otherwise stall the set forever, so a long
     * drought releases one step anyway and the set fails visibly rather than
     * hanging.
     */
    private static boolean consumePresentedFrame() {
        long presented = VolumetricCloudRenderHook.presentedFrames();
        if (presented != lastPresentedFrame) {
            lastPresentedFrame = presented;
            stalledTicks = 0;
            return true;
        }
        if (++stalledTicks >= 200) {
            stalledTicks = 0;
            return true;
        }
        return false;
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
