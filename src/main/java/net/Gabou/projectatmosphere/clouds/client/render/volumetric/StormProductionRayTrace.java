package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * T098 production ray trace: one exact view ray, marched by the real
 * production loop, published iteration by iteration.
 *
 * <p>This is the instrument the T098 investigation lacked. The existing T128
 * centre-line trace evaluates {@code directStormShape} and the material stages
 * directly; it never executes {@code cloudDensity}, never applies the outer
 * weather-gated empty-space skip, and never reproduces a camera ray. Its
 * numbers therefore prove that a descriptor/material field exists, not that
 * the production marcher integrates it. This trace closes exactly that gap:
 * the shader records the production loop's own branch decisions and the gates
 * inside the production {@code cloudDensity}, for one ray fixed by NDC.
 *
 * <p><b>Transport.</b> Every fragment of the trace pass marches the same
 * traced ray, and its own {@code gl_FragCoord} selects which record it
 * publishes: column = march iteration (0..127, the production MAX_STEPS), row
 * = field group (0..15). The record is a fixed 128x16 corner of the existing
 * cloud target, so there is no new allocation, no unbounded log and no
 * dependence on how many iterations actually ran.
 *
 * <p><b>Marker gate.</b> The shader's {@code PaRayTraceMode} is 0 for every
 * ordinary frame, and no recording write in the shader is reachable at 0. Arm
 * 1 is unmodified production. Arm 2 additionally disables the outer
 * weather-gated empty-space skip for that pass only, which is the A/B the
 * investigation needs; every gate inside {@code cloudDensity}, its weather
 * coverage cut included, is untouched in both arms.
 */
final class StormProductionRayTrace {

    /** Must match MAX_STEPS in cloud_atmosphere_volume.fsh. */
    static final int MAX_STEPS = 128;
    /** Must match PA_TRACE_STAGES in cloud_atmosphere_volume.fsh. */
    static final int STAGES = 22;
    /** Row that echoes the fragment's own column and row; see the shader. */
    private static final int ECHO_STAGE = 15;

    /**
     * Arm 0 renders nothing special: it is an ordinary production frame, read
     * back at the traced texel only. It is what proves the traced ray is the
     * ray that rendered the pixel - the production alpha at that texel and the
     * trace's own alpha for the same NDC are then measured one frame apart, at
     * one pose, on one render target.
     */
    static final int ARM_OBSERVE = 0;
    static final int ARM_PRODUCTION = 1;
    static final int ARM_NO_WEATHER_SKIP = 2;

    private static final double DENSITY_THRESHOLD = 0.0008D;
    /**
     * Below this the descriptor envelope is the fade at a lobe's outer edge,
     * where stormBody mapping it to nothing is correct rather than a defect.
     * Classifying such a sample as the first production loss would bury the
     * real one, which is what an earlier revision of this report did.
     */
    private static final float MATERIAL_ENVELOPE_FLOOR = 0.05F;
    private static final Path OUTPUT_ROOT = Path.of("t098-raytrace");

    // Per-iteration march flags. Mirrors PA_MR_* in the shader.
    private static final int MR_EXECUTED = 1;
    private static final int MR_FINE_AT_ENTRY = 2;
    private static final int MR_FINE_FINAL = 4;
    private static final int MR_PUFF_PROMOTED = 8;
    private static final int MR_STORM_SEGMENT_MAY_INTERSECT = 16;
    private static final int MR_STORM_SAFE_ADVANCE = 32;
    private static final int MR_STORM_FORCED_FINE = 64;
    private static final int MR_WEATHER_SKIP_ELIGIBLE = 128;
    private static final int MR_WEATHER_SKIP_TAKEN = 256;
    private static final int MR_CLOUD_DENSITY_CALLED = 512;
    private static final int MR_LOCAL_RAIN_SEGMENT = 1024;
    private static final int MR_DENSITY_ABOVE_THRESHOLD = 2048;
    private static final int MR_BRACKET_REFINED = 4096;
    private static final int MR_INTEGRATED = 8192;
    private static final int MR_CAMERA_INSIDE_CLOUD = 16384;
    private static final int MR_PRECIPITATION_SAMPLE = 32768;

    // cloudDensity gate flags. Mirrors PA_CD_* in the shader.
    private static final int CD_ENTERED = 1;
    private static final int CD_OWNS_DESCRIPTOR_GROUP = 2;
    private static final int CD_EARLY_COVERAGE_REJECT = 4;
    private static final int CD_INSIDE_SHAPE_BOUNDS = 8;
    private static final int CD_BODY_BLOCK_ENTERED = 16;
    private static final int CD_MORPHOLOGY_CATEGORY_VALID = 32;
    private static final int CD_STORM_PROFILE = 64;
    private static final int CD_DIRECT_STORM_COVERAGE_POSITIVE = 128;
    private static final int CD_WEATHER_COVERAGE_POSITIVE = 256;
    private static final int CD_EROSION_APPLIED = 512;
    private static final int CD_EMBEDDED_CONVECTIVE_OVERLAP = 1024;
    private static final int CD_DESCRIPTOR_CANDIDATE_FOUND = 2048;

    private static volatile Request active;
    private static volatile String latest = "not_captured";

    private StormProductionRayTrace() {
    }

    /** One ray to trace, addressed the way the screenshot addresses it. */
    record Pixel(String label, int screenX, int screenY) {
    }

    /**
     * Queues a trace of every supplied pixel under both arms.
     *
     * @param frameWidth  framebuffer width the pixel coordinates refer to
     * @param frameHeight framebuffer height the pixel coordinates refer to
     */
    static synchronized String request(
            String setLabel, List<Pixel> pixels, int frameWidth, int frameHeight) {
        if (active != null) {
            return "busy:" + active.progress();
        }
        if (pixels == null || pixels.isEmpty()) {
            return "no_pixels_requested";
        }
        if (frameWidth <= 0 || frameHeight <= 0) {
            return "invalid_frame_dimensions";
        }
        active = new Request(setLabel, List.copyOf(pixels), frameWidth, frameHeight);
        latest = "acquiring " + active.progress();
        return latest;
    }

    static boolean active() {
        return active != null;
    }

    /**
     * Requests the T098 column: the waist pixel and its BASE and ANVIL
     * controls, chosen by projecting the fixture's own geometry through the
     * transform the cloud shader was last drawn with. Addressing the storm by
     * world position rather than by a hard-coded screen coordinate is what
     * makes the traced ray provably the ray that rendered that part of the
     * image, at whatever resolution the frame happens to use.
     */
    static String requestStormColumn(
            net.minecraft.client.Minecraft minecraft,
            StormPerformanceBaseline.SuiteFixture fixture,
            String setLabel) {
        if (fixture == null) {
            return "raytrace_fixture_missing";
        }
        if (!VolumetricCloudRenderer.lastTransformValid()) {
            return "raytrace_no_cloud_draw_yet";
        }
        int width = minecraft.getMainRenderTarget().width;
        int height = minecraft.getMainRenderTarget().height;
        double span = fixture.topY() - fixture.baseY();
        double[][] targets = {
                // The visual gap between the base mass and the anvil.
                {fixture.centerX(), fixture.baseY() + span * 0.55D, fixture.centerZ()},
                // Controls: parts of the same storm that do render.
                {fixture.centerX(), fixture.baseY() + span * 0.10D, fixture.centerZ()},
                {fixture.centerX(), fixture.baseY() + span * 0.90D, fixture.centerZ()}
        };
        String[] labels = {"WAIST", "BASE", "ANVIL"};
        List<Pixel> pixels = new ArrayList<>();
        for (int index = 0; index < targets.length; index++) {
            org.joml.Vector3f ndc = VolumetricCloudRenderer.projectWorldToNdc(
                    targets[index][0], targets[index][1], targets[index][2]);
            if (ndc == null || ndc.x < -1.0F || ndc.x > 1.0F
                    || ndc.y < -1.0F || ndc.y > 1.0F) {
                ProjectAtmosphere.LOGGER.warn(
                        "T098_RAYTRACE target {} does not project on screen; skipped",
                        labels[index]);
                continue;
            }
            int pixelX = (int) Math.round((ndc.x * 0.5D + 0.5D) * width - 0.5D);
            int pixelY = (int) Math.round((1.0D - (ndc.y * 0.5D + 0.5D)) * height - 0.5D);
            pixels.add(new Pixel(labels[index], pixelX, pixelY));
            ProjectAtmosphere.LOGGER.info(
                    "T098_RAYTRACE_TARGET label={} world=({}, {}, {}) ndc=({}, {})"
                            + " framebuffer={}x{} pixel=({}, {})",
                    labels[index], fmt(targets[index][0]), fmt(targets[index][1]),
                    fmt(targets[index][2]), fmt(ndc.x), fmt(ndc.y),
                    width, height, pixelX, pixelY);
        }
        if (pixels.isEmpty()) {
            return "raytrace_no_target_on_screen";
        }
        return request(setLabel, pixels, width, height);
    }

    /** Shader {@code PaRayTraceMode}: 0 whenever no trace is in flight. */
    static int shaderMode() {
        Request request = active;
        return request == null ? 0 : request.currentArm();
    }

    static float ndcX() {
        Request request = active;
        return request == null ? 0.0F : request.ndcX;
    }

    static float ndcY() {
        Request request = active;
        return request == null ? 0.0F : request.ndcY;
    }

    static float fragCoordX() {
        Request request = active;
        return request == null ? 0.0F : request.fragCoordX;
    }

    static float fragCoordY() {
        Request request = active;
        return request == null ? 0.0F : request.fragCoordY;
    }

    static String latest() {
        return latest;
    }

    /**
     * Resolves the traced pixel against the live cloud target. Called on the
     * render thread immediately before the uniforms are uploaded, so the NDC
     * the shader marches and the render-target texel it corresponds to are
     * derived from the target that frame actually renders.
     */
    static void resolveAgainst(RenderTarget cloudTarget) {
        Request request = active;
        if (request == null || cloudTarget == null
                || cloudTarget.width <= 0 || cloudTarget.height <= 0) {
            return;
        }
        request.resolve(cloudTarget.width, cloudTarget.height);
    }

    /** Reads back the finished trace pass and advances to the next arm/pixel. */
    static synchronized void capture(RenderTarget target) {
        Request request = active;
        if (request == null || !RenderSystem.isOnRenderThread() || target == null
                || target.getColorTextureId() <= 0
                || target.width < MAX_STEPS || target.height < STAGES) {
            if (request != null && target != null
                    && (target.width < MAX_STEPS || target.height < STAGES)) {
                latest = "cloud_target_too_small width=" + target.width
                        + " height=" + target.height
                        + " required=" + MAX_STEPS + "x" + STAGES;
                active = null;
                VolumetricCloudRenderer.invalidateHistory();
            }
            return;
        }
        if (!request.resolved) {
            return;
        }
        FloatBuffer pixels = BufferUtils.createFloatBuffer(target.width * target.height * 4);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            float[][][] values = new float[STAGES][MAX_STEPS][4];
            for (int stage = 0; stage < STAGES; stage++) {
                for (int iteration = 0; iteration < MAX_STEPS; iteration++) {
                    int offset = (stage * target.width + iteration) * 4;
                    for (int channel = 0; channel < 4; channel++) {
                        values[stage][iteration][channel] = pixels.get(offset + channel);
                    }
                }
            }
            if (request.currentArm() == ARM_OBSERVE) {
                // Ordinary production frame. Record only what the renderer put
                // in the traced texel.
                int texelX = clamp((int) request.fragCoordX, 0, target.width - 1);
                int texelY = clamp((int) request.fragCoordY, 0, target.height - 1);
                int offset = (texelY * target.width + texelX) * 4;
                request.observed.put(request.currentPixel().label(), new float[] {
                        pixels.get(offset), pixels.get(offset + 1),
                        pixels.get(offset + 2), pixels.get(offset + 3)
                });
                request.advanceArm();
                latest = "acquiring " + request.progress();
                return;
            }
            // The pass must prove it was a trace pass before its numbers are
            // believed. Row 15 echoes the fragment's own column and row, and
            // row 12 echoes the arm the shader ran; an ordinary rendered frame
            // cannot satisfy either. Without this check a frame drawn before
            // the uniform reached the shader reads back as an all-zero trace
            // and would be reported as a result.
            // Row ECHO_STAGE publishes each fragment's own row and column, so
            // only a real trace pass can produce the identity pattern.
            boolean echoValid = Math.round(values[ECHO_STAGE][0][3]) == ECHO_STAGE
                    && Math.round(values[ECHO_STAGE][7][2]) == 7
                    && Math.round(values[12][0][3]) == request.currentArm();
            if (!echoValid) {
                if (++request.rejectedFrames > 60) {
                    latest = "trace_pass_never_ran"
                            + " expectedArm=" + request.currentArm()
                            + " observedArm=" + Math.round(values[12][0][3])
                            + " stageEcho=" + Math.round(values[ECHO_STAGE][0][3])
                            + " columnEcho=" + Math.round(values[ECHO_STAGE][7][2]);
                    active = null;
                    VolumetricCloudRenderer.invalidateHistory();
                }
                return;
            }
            request.rejectedFrames = 0;
            request.accept(new Trace(
                    request.currentPixel(), request.currentArm(), request.ndcX, request.ndcY,
                    request.fragCoordX, request.fragCoordY,
                    request.targetWidth, request.targetHeight, values));
            if (request.complete()) {
                latest = request.finish();
                active = null;
            } else {
                latest = "acquiring " + request.progress();
            }
            VolumetricCloudRenderer.invalidateHistory();
        } catch (RuntimeException exception) {
            latest = "capture_failed:" + exception.getClass().getSimpleName();
            active = null;
            VolumetricCloudRenderer.invalidateHistory();
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    // ------------------------------------------------------------------
    // Request sequencing
    // ------------------------------------------------------------------

    private static final class Request {
        private final String setLabel;
        private final List<Pixel> pixels;
        private final int frameWidth;
        private final int frameHeight;
        private final List<Trace> traces = new ArrayList<>();
        private final int[] arms = {ARM_OBSERVE, ARM_PRODUCTION, ARM_NO_WEATHER_SKIP};
        private final java.util.Map<String, float[]> observed = new java.util.LinkedHashMap<>();
        private int pixelIndex;
        private int rejectedFrames;
        private int armIndex;
        private boolean resolved;
        private int targetWidth;
        private int targetHeight;
        private float ndcX;
        private float ndcY;
        private float fragCoordX;
        private float fragCoordY;

        private Request(String setLabel, List<Pixel> pixels, int frameWidth, int frameHeight) {
            this.setLabel = setLabel;
            this.pixels = pixels;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
        }

        private Pixel currentPixel() {
            return pixels.get(pixelIndex);
        }

        private int currentArm() {
            return arms[armIndex];
        }

        private boolean complete() {
            return pixelIndex >= pixels.size();
        }

        private String progress() {
            return "pixel=" + (pixelIndex + 1) + "/" + pixels.size()
                    + " arm=" + (armIndex + 1) + "/" + arms.length;
        }

        /**
         * Screenshot pixel to render-target texel to NDC. The screenshot is
         * top-down at the framebuffer resolution; the cloud target is
         * bottom-up at the render scale. Resolving through the target's own
         * texel centre means the traced ray is the ray the production fragment
         * for that screenshot pixel actually marched, not an interpolated one.
         */
        private void resolve(int width, int height) {
            if (resolved && targetWidth == width && targetHeight == height) {
                return;
            }
            targetWidth = width;
            targetHeight = height;
            Pixel pixel = currentPixel();
            double u = (pixel.screenX() + 0.5D) / frameWidth;
            double v = 1.0D - (pixel.screenY() + 0.5D) / frameHeight;
            int texelX = clamp((int) Math.floor(u * width), 0, width - 1);
            int texelY = clamp((int) Math.floor(v * height), 0, height - 1);
            fragCoordX = texelX + 0.5F;
            fragCoordY = texelY + 0.5F;
            ndcX = (float) (fragCoordX / width * 2.0D - 1.0D);
            ndcY = (float) (fragCoordY / height * 2.0D - 1.0D);
            resolved = true;
        }

        private void accept(Trace trace) {
            traces.add(trace);
            advanceArm();
        }

        private void advanceArm() {
            armIndex++;
            if (armIndex >= arms.length) {
                armIndex = 0;
                pixelIndex++;
                resolved = false;
            }
        }

        private String finish() {
            String report = Report.build(setLabel, frameWidth, frameHeight, traces, observed);
            writeArtifact(setLabel, report, traces);
            return report;
        }
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    // ------------------------------------------------------------------
    // Decoding
    // ------------------------------------------------------------------

    /** One decoded march of one ray under one arm. */
    record Trace(
            Pixel pixel,
            int arm,
            float ndcX,
            float ndcY,
            float fragCoordX,
            float fragCoordY,
            int targetWidth,
            int targetHeight,
            float[][][] values
    ) {
        private float at(int stage, int iteration, int channel) {
            return values[stage][iteration][channel];
        }

        int flags(int iteration) {
            return Math.round(at(0, iteration, 3))
                    | (Math.round(at(8, iteration, 3)) << 8);
        }

        int cdFlags(int iteration) {
            return Math.round(at(7, iteration, 3))
                    | (Math.round(at(14, iteration, 3)) << 8);
        }

        boolean executed(int iteration) {
            return (flags(iteration) & MR_EXECUTED) != 0;
        }

        float tBefore(int i) {
            return at(0, i, 0);
        }

        float tAfter(int i) {
            return at(0, i, 1);
        }

        float stepLength(int i) {
            return at(0, i, 2);
        }

        float relX(int i) {
            return at(1, i, 0);
        }

        float relY(int i) {
            return at(1, i, 1);
        }

        float relZ(int i) {
            return at(1, i, 2);
        }

        float sinceHit(int i) {
            return at(1, i, 3);
        }

        float unionDistance(int i) {
            return at(2, i, 0);
        }

        float minClearance(int i) {
            return at(2, i, 1);
        }

        float safeAdvance(int i) {
            return at(2, i, 2);
        }

        float outerCoverageSignal(int i) {
            return at(2, i, 3);
        }

        float directStormShape(int i) {
            return at(3, i, 0);
        }

        float directStormStrength(int i) {
            return at(3, i, 1);
        }

        float directStormHeight01(int i) {
            return at(3, i, 2);
        }

        float directStormRoleMask(int i) {
            return at(3, i, 3);
        }

        float cdCoverageSignal(int i) {
            return at(4, i, 0);
        }

        float cdCoverage(int i) {
            return at(4, i, 1);
        }

        float cdEnvelopeCoverage(int i) {
            return at(4, i, 2);
        }

        float cdMacroShape(int i) {
            return at(4, i, 3);
        }

        float afterEnvelope(int i) {
            return at(5, i, 0);
        }

        float afterStormBody(int i) {
            return at(5, i, 1);
        }

        float afterErosion(int i) {
            return at(5, i, 2);
        }

        float afterMaterial(int i) {
            return at(5, i, 3);
        }

        float bodyDensity(int i) {
            return at(6, i, 0);
        }

        float rainDensity(int i) {
            return at(6, i, 1);
        }

        float density(int i) {
            return at(6, i, 2);
        }

        float extinction(int i) {
            return at(6, i, 3);
        }

        float stepTrans(int i) {
            return at(7, i, 0);
        }

        float transmittanceBefore(int i) {
            return at(7, i, 1);
        }

        float transmittanceAfter(int i) {
            return at(7, i, 2);
        }

        int iterationsExecuted() {
            return Math.round(at(9, 0, 0));
        }

        int terminationReason() {
            return Math.round(at(9, 0, 1));
        }

        float finalAlpha() {
            return at(9, 0, 2);
        }

        float finalTransmittance() {
            return at(9, 0, 3);
        }

        float rayDirX() {
            return at(10, 0, 0);
        }

        float rayDirY() {
            return at(10, 0, 1);
        }

        float rayDirZ() {
            return at(10, 0, 2);
        }

        float t0() {
            return at(10, 0, 3);
        }

        float t1() {
            return at(11, 0, 0);
        }

        float fineStep() {
            return at(11, 0, 1);
        }

        float coarseStep() {
            return at(11, 0, 2);
        }

        float coarseStepCap() {
            return at(11, 0, 3);
        }

        float baseStep() {
            return at(12, 0, 0);
        }

        float originJitter() {
            return at(12, 0, 1);
        }

        int stormLobeCount() {
            return Math.round(at(12, 0, 2));
        }

        int reportedArm() {
            return Math.round(at(12, 0, 3));
        }

        int stepCap() {
            return Math.round(at(16, 0, 0));
        }

        int stepBudget() {
            return Math.round(at(16, 0, 1));
        }

        int raymarchSteps() {
            return Math.round(at(16, 0, 2));
        }

        float stepScale() {
            return at(16, 0, 3);
        }

        float exteriorFineStepUniform() {
            return at(17, 0, 0);
        }

        float maxRenderDistance() {
            return at(17, 0, 1);
        }

        int detailQuality() {
            return Math.round(at(17, 0, 2));
        }

        float cameraX() {
            return at(18, 0, 0);
        }

        float cameraY() {
            return at(18, 0, 1);
        }

        float cameraZ() {
            return at(18, 0, 2);
        }

        boolean historyValid() {
            return Math.round(at(18, 0, 3)) == 1;
        }

        float extinctionScale() {
            return at(19, 0, 0);
        }

        float densityMul() {
            return at(19, 0, 1);
        }

        float representativeT() {
            return at(20, 0, 0);
        }

        /**
         * The depth the cloud pass hands to the composite. The composite reads a
         * texel whose depth is 1.0 as "no cloud" whatever its alpha, so this is
         * the value that decides whether an opaque march survives composition.
         * Carried as one minus the depth, which half float resolves exactly.
         */
        float resultDepth() {
            return 1.0F - at(20, 0, 1);
        }

        boolean depthSaturated() {
            return at(20, 0, 1) <= 0.0F;
        }

        boolean currentCloudHit() {
            return at(20, 0, 2) > 0.5F;
        }

        /** Probes taken by the bounded empty-span scan on this iteration. */
        float scanProbes(int i) {
            return values[21][i][0];
        }

        /** Distance the scan proved empty at the fine march's own resolution. */
        float scanAdvance(int i) {
            return values[21][i][1];
        }

        boolean scanFoundMaterial(int i) {
            return values[21][i][2] > 0.5F;
        }

        /**
         * How far the representative point's NDC depth overshoots the far
         * plane. Positive means the point is outside the frustum, which is what
         * drives depthAt's clamp to the composite's miss sentinel.
         */
        float ndcDepthExcess() {
            return at(20, 0, 3);
        }

        String terminationText() {
            return switch (terminationReason()) {
                case 0 -> "step_cap";
                case 1 -> "t_reached_t1";
                case 2 -> "transmittance_floor";
                case 3 -> "slab_miss";
                case 4 -> "scene_depth_closed_interval";
                case 5 -> "coverage_pretest_rejected_ray";
                default -> "unknown_" + terminationReason();
            };
        }
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    private static final class Report {

        private static String build(
                String setLabel, int frameWidth, int frameHeight, List<Trace> traces,
                java.util.Map<String, float[]> observed) {
            StringBuilder out = new StringBuilder("T098 production ray trace");
            float[] nearFar = VolumetricCloudRenderer.lastProjectionNearFar();
            out.append("\nset=").append(setLabel)
                    .append(" framebuffer=").append(frameWidth).append('x').append(frameHeight)
                    .append(" cloudProjectionNear=").append(fmt(nearFar[0]))
                    .append(" cloudProjectionFar=").append(fmt(nearFar[1]));
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.player != null) {
                out.append("\ncamera=(")
                        .append(fmt(minecraft.player.getX())).append(", ")
                        .append(fmt(minecraft.player.getEyeY())).append(", ")
                        .append(fmt(minecraft.player.getZ())).append(')')
                        .append(" yaw=").append(fmt(minecraft.player.getYRot()))
                        .append(" pitch=").append(fmt(minecraft.player.getXRot()));
            }
            for (int index = 0; index < traces.size(); index += 2) {
                Trace armA = traces.get(index);
                Trace armB = index + 1 < traces.size() ? traces.get(index + 1) : null;
                out.append("\n\n").append(describeRay(armA));
                float[] production = observed.get(armA.pixel().label());
                if (production != null) {
                    out.append("\n").append(describeProductionPixel(armA, production));
                }
                out.append("\n\n").append(describeArm(armA));
                if (armB != null) {
                    out.append("\n\n").append(describeArm(armB));
                    out.append("\n\n").append(compareArms(armA, armB));
                }
                out.append("\n\n").append(classify(armA, armB));
            }
            return out.toString();
        }

        private static String describeRay(Trace trace) {
            return "=== RAY " + trace.pixel().label()
                    + "\nscreenPixel=(" + trace.pixel().screenX() + ", " + trace.pixel().screenY() + ")"
                    + " renderTarget=" + trace.targetWidth() + "x" + trace.targetHeight()
                    + " renderTargetFragCoord=(" + fmt(trace.fragCoordX()) + ", "
                    + fmt(trace.fragCoordY()) + ")"
                    + "\nndc=(" + fmt(trace.ndcX()) + ", " + fmt(trace.ndcY()) + ")"
                    + " rayDir=(" + fmt(trace.rayDirX()) + ", " + fmt(trace.rayDirY()) + ", "
                    + fmt(trace.rayDirZ()) + ")"
                    + "\nt0=" + fmt(trace.t0()) + " t1=" + fmt(trace.t1())
                    + " originJitter=" + fmt(trace.originJitter())
                    + " fineStep=" + fmt(trace.fineStep())
                    + " coarseStep=" + fmt(trace.coarseStep())
                    + " coarseStepCap=" + fmt(trace.coarseStepCap())
                    + " baseStep=" + fmt(trace.baseStep())
                    + " stormLobeCount=" + trace.stormLobeCount()
                    + "\nstepCap=" + trace.stepCap()
                    + " stepBudget=" + trace.stepBudget()
                    + " raymarchSteps=" + trace.raymarchSteps()
                    + " stepScale=" + fmt(trace.stepScale())
                    + " exteriorFineStep=" + fmt(trace.exteriorFineStepUniform())
                    + " maxRenderDistance=" + fmt(trace.maxRenderDistance())
                    + " detailQuality=" + trace.detailQuality()
                    + " historyValid=" + trace.historyValid()
                    + " extinctionScale=" + fmt(trace.extinctionScale())
                    + " densityMul=" + fmt(trace.densityMul())
                    + "\nshaderCamera=(" + fmt(trace.cameraX()) + ", " + fmt(trace.cameraY())
                    + ", " + fmt(trace.cameraZ()) + ")";
        }

        /**
         * The identity proof PHASE 1 requires: the production alpha read out of
         * the traced texel on an ordinary frame, beside the alpha the traced ray
         * produces, at the same pose on the same render target.
         */
        private static String describeProductionPixel(Trace trace, float[] production) {
            float productionAlpha = production[3];
            float tracedAlpha = trace.finalAlpha();
            boolean agree = Math.abs(productionAlpha - tracedAlpha) <= 0.02F;
            return "productionTexel rgba=(" + fmt(production[0]) + ", " + fmt(production[1])
                    + ", " + fmt(production[2]) + ", " + fmt(productionAlpha) + ")"
                    + " tracedAlpha=" + fmt(tracedAlpha)
                    + " identity=" + (agree ? "AGREES" : "DISAGREES")
                    + (agree ? "" : "  <- the traced ray is not reproducing this pixel;"
                        + " the march numbers below describe a different ray");
        }

        private static String describeArm(Trace trace) {
            StringBuilder out = new StringBuilder("--- ARM ")
                    .append(armName(trace.arm()))
                    .append(" (shader reported mode ").append(trace.reportedArm()).append(')')
                    .append("\niterations=").append(trace.iterationsExecuted())
                    .append(" termination=").append(trace.terminationText())
                    .append(" finalAlpha=").append(fmt(trace.finalAlpha()))
                    .append(" finalTransmittance=").append(fmt(trace.finalTransmittance()))
                    .append("\nrepresentativeT=").append(fmt(trace.representativeT()))
                    .append(" currentCloudHit=").append(trace.currentCloudHit())
                    .append(" ndcDepthExcess=")
                    .append(String.format(Locale.ROOT, "%.6f", trace.ndcDepthExcess()))
                    .append(trace.ndcDepthExcess() > 0.0F
                        ? " (BEYOND the projection far plane)"
                        : " (inside the frustum)")
                    .append(" resultDepth=")
                    .append(String.format(Locale.ROOT, "%.8f", trace.resultDepth()))
                    .append(" compositeVerdict=")
                    .append(trace.depthSaturated()
                        ? "DISCARDED - the composite drops any cloud texel whose depth is 1.0,"
                            + " whatever its alpha, so this march result never reaches the screen"
                        : "kept");
            out.append("\ni|tBefore|tAfter|step|worldY|sinceHit|state|skip|cov|clear|adv"
                    + "|dss|owns|cdCov|env|macro|body|erode|matl|density|trans|flags");
            int previousState = Integer.MIN_VALUE;
            for (int i = 0; i < MAX_STEPS; i++) {
                if (!trace.executed(i)) {
                    continue;
                }
                if (!isInteresting(trace, i, previousState)) {
                    previousState = stateKey(trace, i);
                    continue;
                }
                previousState = stateKey(trace, i);
                out.append('\n').append(i)
                        .append('|').append(fmt(trace.tBefore(i)))
                        .append('|').append(fmt(trace.tAfter(i)))
                        .append('|').append(fmt(trace.stepLength(i)))
                        .append('|').append(fmt(trace.relY(i)))
                        .append('|').append((int) trace.sinceHit(i))
                        .append('|').append((trace.flags(i) & MR_FINE_FINAL) != 0 ? "fine" : "coarse")
                        .append('|').append((trace.flags(i) & MR_WEATHER_SKIP_TAKEN) != 0 ? "SKIP"
                                : (trace.flags(i) & MR_WEATHER_SKIP_ELIGIBLE) != 0 ? "elig" : "-")
                        .append('|').append(fmt(trace.outerCoverageSignal(i)))
                        .append('|').append(fmt(trace.minClearance(i)))
                        .append('|').append(fmt(trace.safeAdvance(i)))
                        .append('|').append(fmt(trace.directStormShape(i)))
                        .append('|').append((trace.cdFlags(i) & CD_OWNS_DESCRIPTOR_GROUP) != 0 ? 1 : 0)
                        .append('|').append(fmt(trace.cdCoverage(i)))
                        .append('|').append(fmt(trace.cdEnvelopeCoverage(i)))
                        .append('|').append(fmt(trace.cdMacroShape(i)))
                        .append('|').append(fmt(trace.afterStormBody(i)))
                        .append('|').append(fmt(trace.afterErosion(i)))
                        .append('|').append(fmt(trace.afterMaterial(i)))
                        .append('|').append(fmt(trace.density(i)))
                        .append('|').append(fmt(trace.transmittanceAfter(i)))
                        .append('|').append(describeFlags(trace, i));
            }
            return out.toString();
        }

        /**
         * The change filter T098 asked for: an iteration is reported when the
         * fine/coarse state, the weather-skip decision, descriptor ownership,
         * whether cloudDensity ran, whether the descriptor field became
         * non-zero, whether cloudDensity disagrees with it, or the
         * transmittance changed materially.
         */
        private static boolean isInteresting(Trace trace, int i, int previousState) {
            if (stateKey(trace, i) != previousState) {
                return true;
            }
            if (trace.directStormShape(i) > 0.001F || trace.density(i) > DENSITY_THRESHOLD) {
                return true;
            }
            return Math.abs(trace.transmittanceAfter(i) - trace.transmittanceBefore(i)) > 0.001F;
        }

        private static int stateKey(Trace trace, int i) {
            int flags = trace.flags(i);
            int cdFlags = trace.cdFlags(i);
            return (flags & (MR_FINE_FINAL | MR_WEATHER_SKIP_TAKEN | MR_WEATHER_SKIP_ELIGIBLE
                    | MR_CLOUD_DENSITY_CALLED | MR_STORM_SEGMENT_MAY_INTERSECT
                    | MR_STORM_SAFE_ADVANCE | MR_STORM_FORCED_FINE | MR_BRACKET_REFINED
                    | MR_INTEGRATED))
                    | ((cdFlags & (CD_OWNS_DESCRIPTOR_GROUP | CD_EARLY_COVERAGE_REJECT
                    | CD_BODY_BLOCK_ENTERED | CD_DESCRIPTOR_CANDIDATE_FOUND)) << 16);
        }

        private static String describeFlags(Trace trace, int i) {
            int flags = trace.flags(i);
            int cdFlags = trace.cdFlags(i);
            StringBuilder out = new StringBuilder();
            appendFlag(out, flags, MR_PUFF_PROMOTED, "puffPromote");
            appendFlag(out, flags, MR_STORM_SEGMENT_MAY_INTERSECT, "stormSeg");
            appendFlag(out, flags, MR_STORM_SAFE_ADVANCE, "safeAdvance");
            appendFlag(out, flags, MR_STORM_FORCED_FINE, "forcedFine");
            appendFlag(out, flags, MR_LOCAL_RAIN_SEGMENT, "rainSeg");
            appendFlag(out, flags, MR_CLOUD_DENSITY_CALLED, "cloudDensity");
            appendFlag(out, flags, MR_DENSITY_ABOVE_THRESHOLD, "aboveThreshold");
            appendFlag(out, flags, MR_BRACKET_REFINED, "bracketRefine");
            appendFlag(out, flags, MR_INTEGRATED, "integrated");
            appendFlag(out, flags, MR_PRECIPITATION_SAMPLE, "precipSample");
            appendFlag(out, cdFlags, CD_DESCRIPTOR_CANDIDATE_FOUND, "cd:candidate");
            appendFlag(out, cdFlags, CD_OWNS_DESCRIPTOR_GROUP, "cd:owns");
            appendFlag(out, cdFlags, CD_EARLY_COVERAGE_REJECT, "cd:earlyReject");
            appendFlag(out, cdFlags, CD_INSIDE_SHAPE_BOUNDS, "cd:inBounds");
            appendFlag(out, cdFlags, CD_BODY_BLOCK_ENTERED, "cd:body");
            appendFlag(out, cdFlags, CD_MORPHOLOGY_CATEGORY_VALID, "cd:morphValid");
            appendFlag(out, cdFlags, CD_STORM_PROFILE, "cd:stormProfile");
            appendFlag(out, cdFlags, CD_DIRECT_STORM_COVERAGE_POSITIVE, "cd:dssPositive");
            appendFlag(out, cdFlags, CD_WEATHER_COVERAGE_POSITIVE, "cd:weatherPositive");
            appendFlag(out, cdFlags, CD_EROSION_APPLIED, "cd:erosion");
            appendFlag(out, cdFlags, CD_EMBEDDED_CONVECTIVE_OVERLAP, "cd:embedded");
            return out.length() == 0 ? "-" : out.toString();
        }

        private static void appendFlag(StringBuilder out, int flags, int bit, String name) {
            if ((flags & bit) == 0) {
                return;
            }
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(name);
        }

        private static String compareArms(Trace armA, Trace armB) {
            int skipsA = 0;
            int callsA = 0;
            int callsB = 0;
            float maxDssA = 0.0F;
            float maxDssB = 0.0F;
            float maxDensityA = 0.0F;
            float maxDensityB = 0.0F;
            for (int i = 0; i < MAX_STEPS; i++) {
                if (armA.executed(i)) {
                    if ((armA.flags(i) & MR_WEATHER_SKIP_TAKEN) != 0) {
                        skipsA++;
                    }
                    if ((armA.flags(i) & MR_CLOUD_DENSITY_CALLED) != 0) {
                        callsA++;
                        maxDssA = Math.max(maxDssA, armA.directStormShape(i));
                        maxDensityA = Math.max(maxDensityA, armA.density(i));
                    }
                }
                if (armB.executed(i) && (armB.flags(i) & MR_CLOUD_DENSITY_CALLED) != 0) {
                    callsB++;
                    maxDssB = Math.max(maxDssB, armB.directStormShape(i));
                    maxDensityB = Math.max(maxDensityB, armB.density(i));
                }
            }
            String verdict;
            if (skipsA == 0) {
                verdict = "OUTER WEATHER SKIP NEVER FIRED on this ray; it cannot be the cause";
            } else if (maxDensityB > DENSITY_THRESHOLD && maxDensityA <= DENSITY_THRESHOLD) {
                verdict = "OUTER WEATHER SKIP IMPLICATED: arm B reaches density "
                        + fmt(maxDensityB) + " where arm A integrates nothing";
            } else if (maxDensityB > maxDensityA * 1.05F + DENSITY_THRESHOLD) {
                verdict = "OUTER WEATHER SKIP PARTIALLY IMPLICATED: arm B reaches "
                        + fmt(maxDensityB) + " against arm A " + fmt(maxDensityA);
            } else {
                verdict = "OUTER WEATHER SKIP FALSIFIED: both arms reach the same"
                        + " cloudDensity samples and the same peak density";
            }
            return "--- WEATHER SKIP A/B"
                    + "\narmA skips=" + skipsA + " cloudDensityCalls=" + callsA
                    + " maxDirectStormShape=" + fmt(maxDssA)
                    + " maxDensity=" + fmt(maxDensityA)
                    + " finalAlpha=" + fmt(armA.finalAlpha())
                    + "\narmB skips=0 (disabled) cloudDensityCalls=" + callsB
                    + " maxDirectStormShape=" + fmt(maxDssB)
                    + " maxDensity=" + fmt(maxDensityB)
                    + " finalAlpha=" + fmt(armB.finalAlpha())
                    + "\nverdict=" + verdict;
        }

        /**
         * The first production loss, expressed as one of the classes T098
         * enumerated. Derived from the trace only; no hypothesis is assumed.
         */
        private static String classify(Trace armA, Trace armB) {
            if (armA.terminationReason() >= 3) {
                return "--- FIRST LOSS\nclass=G ray never reaches the expected material interval"
                        + " (" + armA.terminationText() + ")";
            }
            // Class A: production skipped an interval that arm B shows carries
            // descriptor-owned material.
            if (armB != null) {
                for (int i = 0; i < MAX_STEPS; i++) {
                    if (!armB.executed(i)
                            || (armB.flags(i) & MR_CLOUD_DENSITY_CALLED) == 0
                            || armB.density(i) <= DENSITY_THRESHOLD) {
                        continue;
                    }
                    float t = armB.tBefore(i);
                    if (!sampledNear(armA, t)) {
                        return "--- FIRST LOSS"
                                + "\nclass=A outer weather empty-space skip"
                                + "\narm B integrates density " + fmt(armB.density(i))
                                + " at t=" + fmt(t) + " (iteration " + i + ")"
                                + " which arm A never sampled"
                                + "\ncoverageSignal that authorized the skip="
                                + fmt(skippingCoverage(armA, t))
                                + "\nskipped interval=" + describeSkip(armA, t);
                    }
                }
            }
            // Does this ray produce ANY material at all? That question has to
            // come before any per-sample gate scan. stormBody is a remap, not a
            // pass-through: over much of a descriptor envelope it correctly maps
            // a low envelope value to nothing, which is what makes the body
            // noise-formed instead of a balloon. Treating one such sample as
            // "the first production loss" reports a defect on every ray,
            // including the BASE and ANVIL controls that render perfectly - an
            // earlier revision of this classifier did exactly that.
            boolean anyMaterial = false;
            for (int i = 0; i < MAX_STEPS; i++) {
                if (armA.executed(i) && armA.density(i) > DENSITY_THRESHOLD) {
                    anyMaterial = true;
                    break;
                }
            }
            if (anyMaterial) {
                StringBuilder out = new StringBuilder("--- FIRST LOSS")
                        .append("\nclass=F cloudDensity returns healthy density;")
                        .append(" no gate inside it loses this ray's material")
                        .append("\nfinalAlpha=").append(fmt(armA.finalAlpha()))
                        .append(" peakDensity=").append(fmt(peakDensity(armA)));
                boolean lossFound = false;
                if (armA.depthSaturated() && armA.currentCloudHit()) {
                    lossFound = true;
                    out.append("\nLOSS IS AFTER THE MARCH: the alpha-weighted representative")
                            .append(" point is at t=").append(fmt(armA.representativeT()))
                            .append(", beyond the projection far plane, so its depth clamps to")
                            .append(" the composite's 1.0 miss sentinel and")
                            .append(" cloud_field_composite.fsh discards the texel whatever its")
                            .append(" alpha. This march contributes nothing to the image.");
                }
                if (armA.terminationReason() == 0 && armA.finalTransmittance() > 0.05F) {
                    lossFound = true;
                    out.append("\nMARCH BUDGET EXHAUSTED: the ray reached the ")
                            .append(armA.stepCap())
                            .append("-iteration cap with transmittance ")
                            .append(fmt(armA.finalTransmittance()))
                            .append(" still unabsorbed, so the alpha it did integrate (")
                            .append(fmt(armA.finalAlpha()))
                            .append(") understates the material along it.");
                }
                if (!lossFound) {
                    out.append("\nNo loss on this ray: it integrated to the transmittance")
                            .append(" floor and publishes a compositable depth.");
                }
                return out.toString();
            }

            // Only now, with no material anywhere on the ray, is a per-sample
            // gate scan meaningful.
            for (int i = 0; i < MAX_STEPS; i++) {
                if (!armA.executed(i) || (armA.flags(i) & MR_CLOUD_DENSITY_CALLED) == 0) {
                    continue;
                }
                if (armA.directStormShape(i) <= MATERIAL_ENVELOPE_FLOOR) {
                    continue;
                }
                int cdFlags = armA.cdFlags(i);
                String gate;
                String cls;
                if ((cdFlags & CD_OWNS_DESCRIPTOR_GROUP) == 0) {
                    cls = "C";
                    gate = "ownsDescriptorGroup false while directStormShape="
                            + fmt(armA.directStormShape(i));
                } else if ((cdFlags & CD_EARLY_COVERAGE_REJECT) != 0) {
                    cls = "D";
                    gate = "cloudDensity early coverage reject, coverage="
                            + fmt(armA.cdCoverage(i));
                } else if ((cdFlags & CD_INSIDE_SHAPE_BOUNDS) == 0) {
                    cls = "E";
                    gate = "insideShapeBounds false";
                } else if ((cdFlags & CD_BODY_BLOCK_ENTERED) == 0) {
                    cls = "E";
                    gate = "body block not entered; morphologyCategoryValid="
                            + ((cdFlags & CD_MORPHOLOGY_CATEGORY_VALID) != 0)
                            + " coverage=" + fmt(armA.cdCoverage(i));
                } else if (armA.afterEnvelope(i) <= DENSITY_THRESHOLD) {
                    cls = "E";
                    gate = "macroShape*envelopeCoverage collapsed: macroShape="
                            + fmt(armA.cdMacroShape(i))
                            + " envelopeCoverage=" + fmt(armA.cdEnvelopeCoverage(i));
                } else if (armA.afterStormBody(i) <= DENSITY_THRESHOLD) {
                    cls = "E";
                    gate = "stormBody remap zeroed the envelope everywhere on this ray:"
                            + " before=" + fmt(armA.afterEnvelope(i))
                            + " strength=" + fmt(armA.directStormStrength(i));
                } else if (armA.afterErosion(i) <= DENSITY_THRESHOLD) {
                    cls = "E";
                    gate = "detail erosion zeroed the body: before="
                            + fmt(armA.afterStormBody(i));
                } else {
                    cls = "E";
                    gate = "material stage or family scale zeroed the body: afterErosion="
                            + fmt(armA.afterErosion(i))
                            + " afterMaterial=" + fmt(armA.afterMaterial(i));
                }
                return "--- FIRST LOSS"
                        + "\nclass=" + cls + " cloudDensity gate"
                        + "\niteration=" + i + " t=" + fmt(armA.tBefore(i))
                        + " cameraRelative=(" + fmt(armA.relX(i)) + ", " + fmt(armA.relY(i))
                        + ", " + fmt(armA.relZ(i)) + ")"
                        + "\ngate=" + gate;
            }
            boolean anyDescriptorField = false;
            for (int i = 0; i < MAX_STEPS; i++) {
                if (armA.executed(i) && armA.directStormShape(i) > 0.001F) {
                    anyDescriptorField = true;
                    break;
                }
            }
            return "--- FIRST LOSS"
                    + "\nclass=" + (anyDescriptorField ? "E" : "G")
                    + (anyDescriptorField
                        ? " descriptor field present but no sample cleared the density threshold"
                        : " the marcher never sampled a point with a non-zero descriptor field")
                    + "\ncloudDensity was called on "
                    + countCalls(armA) + " of " + armA.iterationsExecuted() + " iterations";
        }

        private static float peakDensity(Trace trace) {
            float peak = 0.0F;
            for (int i = 0; i < MAX_STEPS; i++) {
                if (trace.executed(i)) {
                    peak = Math.max(peak, trace.density(i));
                }
            }
            return peak;
        }

        private static int countCalls(Trace trace) {
            int calls = 0;
            for (int i = 0; i < MAX_STEPS; i++) {
                if (trace.executed(i) && (trace.flags(i) & MR_CLOUD_DENSITY_CALLED) != 0) {
                    calls++;
                }
            }
            return calls;
        }

        /** True when arm A evaluated cloudDensity on an interval containing t. */
        private static boolean sampledNear(Trace armA, float t) {
            for (int i = 0; i < MAX_STEPS; i++) {
                if (!armA.executed(i) || (armA.flags(i) & MR_CLOUD_DENSITY_CALLED) == 0) {
                    continue;
                }
                float low = armA.tBefore(i);
                float high = Math.max(armA.tAfter(i), low + armA.stepLength(i));
                if (t >= low - 0.5F && t <= high + 0.5F) {
                    return true;
                }
            }
            return false;
        }

        private static float skippingCoverage(Trace armA, float t) {
            for (int i = 0; i < MAX_STEPS; i++) {
                if (!armA.executed(i) || (armA.flags(i) & MR_WEATHER_SKIP_TAKEN) == 0) {
                    continue;
                }
                if (t >= armA.tBefore(i) && t <= armA.tAfter(i)) {
                    return armA.outerCoverageSignal(i);
                }
            }
            return Float.NaN;
        }

        private static String describeSkip(Trace armA, float t) {
            for (int i = 0; i < MAX_STEPS; i++) {
                if (!armA.executed(i) || (armA.flags(i) & MR_WEATHER_SKIP_TAKEN) == 0) {
                    continue;
                }
                if (t >= armA.tBefore(i) && t <= armA.tAfter(i)) {
                    return "iteration " + i + " t " + fmt(armA.tBefore(i)) + ".."
                            + fmt(armA.tAfter(i)) + " advance "
                            + fmt(armA.stepLength(i));
                }
            }
            return "no single skip covers t=" + fmt(t)
                    + "; the interval was never entered at all";
        }

        private static String armName(int arm) {
            return arm == ARM_NO_WEATHER_SKIP ? "B outer-weather-skip-disabled" : "A production";
        }
    }

    /** Keeps the complete raw record beside the report for later re-reading. */
    private static void writeArtifact(String setLabel, String report, List<Trace> traces) {
        try {
            Files.createDirectories(OUTPUT_ROOT);
            String stamp = String.valueOf(System.currentTimeMillis());
            Path base = OUTPUT_ROOT.resolve(sanitize(setLabel) + '-' + stamp);
            Files.writeString(Path.of(base + ".txt"), report, StandardCharsets.UTF_8);
            StringBuilder raw = new StringBuilder("label,arm,stage,iteration,r,g,b,a\n");
            for (Trace trace : traces) {
                for (int stage = 0; stage < STAGES; stage++) {
                    for (int i = 0; i < MAX_STEPS; i++) {
                        raw.append(trace.pixel().label()).append(',')
                                .append(trace.arm()).append(',')
                                .append(stage).append(',')
                                .append(i);
                        for (int channel = 0; channel < 4; channel++) {
                            raw.append(',').append(
                                    String.format(Locale.ROOT, "%.6f",
                                            trace.values()[stage][i][channel]));
                        }
                        raw.append('\n');
                    }
                }
            }
            Files.writeString(Path.of(base + ".csv"), raw.toString(), StandardCharsets.UTF_8);
            ProjectAtmosphere.LOGGER.info("T098_RAYTRACE_ARTIFACT {}", base.toAbsolutePath());
        } catch (Exception exception) {
            ProjectAtmosphere.LOGGER.warn("T098_RAYTRACE_ARTIFACT_FAILED {}", exception.toString());
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String fmt(double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "+inf" : "-inf";
        }
        return String.format(Locale.ROOT, "%.5f", value);
    }
}
