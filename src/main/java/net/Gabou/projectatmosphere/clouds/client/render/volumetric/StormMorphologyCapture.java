package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype;
import net.Gabou.projectatmosphere.clouds.morphology.StormGeometryProgram;
import net.Gabou.projectatmosphere.clouds.morphology.GrowthDefinedStormPrototype;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveGrowthFieldPrototype;
import net.Gabou.projectatmosphere.clouds.morphology.StormAnatomyCandidateC;
import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.opengl.GL11;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Default-off T255 native evidence fixture using the existing capture/gallery harness. */
final class StormMorphologyCapture {
    record Shot(String type, long seed, String view) {
        String name() { return campaign() + "_" + type + "__" + seed + "__" + view; }
    }
    static final List<Shot> SHOTS = new ArrayList<>();
    private static final List<Float> TIMES = new ArrayList<>();
    private static final List<Float> PIPELINE_TIMES = new ArrayList<>();
    private static long lastTimingSerial;
    private static long lastPipelineSerial;
    private static StormGeometryProgram current;
    private static float[] intervalPair;
    private static long intervalPairSignature;
    static final List<String> FULL_VIEWS = List.of("side", "opposite_side", "front", "back", "above", "below", "low_angle", "far", "close", "upwind", "downwind", "crosswind_left", "crosswind_right", "tower", "transition", "canopy_anvil", "updraft_oblique");
    static {
        if (systemCampaign()) {
            // T260: frozen convective systems (see ConvectiveSystemCapture; PA_T260_PHASE spike|gallery).
            SHOTS.addAll(ConvectiveSystemCapture.shots());
        } else
        if (anatomyCampaign()) {
            // T258: B v3 and candidate C0/C1/C2/C3 at the identical fitted camera (effective lens), all 19
            // views, then native 4x close views of B v3 and C3. Prefixes: b3_, c0_, c1_, c2_, (none) = C3, z4_.
            SHOTS.add(new Shot("supercell", 20261008L, "attribution_intervals"));
            SHOTS.add(new Shot("supercell", 20261008L, "attribution_exact"));
            for (String type : List.of("cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell")) {
                for (long seed : new long[]{20261008L, 20268927L, 20276846L}) {
                    for (String view : StormAnatomyCapturePlan.FULL_VIEWS) {
                        for (String prefix : List.of("b3_", "c0_", "c1_", "c2_", "")) SHOTS.add(new Shot(type, seed, prefix + view));
                    }
                    for (String view : StormAnatomyCapturePlan.ZOOM_VIEWS) {
                        SHOTS.add(new Shot(type, seed, "b3_z4_" + view));
                        SHOTS.add(new Shot(type, seed, "z4_" + view));
                    }
                }
            }
            if ("1".equals(System.getenv("PA_T258_SMOKE"))) {
                var views = List.of("b3_side", "c0_side", "c1_side", "c2_side", "side", "b3_above", "above", "b3_z4_side", "z4_side", "attribution_intervals", "attribution_exact");
                SHOTS.removeIf(shot -> shot.seed != 20261008L || !views.contains(shot.view));
            }
        } else if (convectiveCampaign() && diagnosticCampaign()) {
            // T257 D0/D1/D2: core, core + mesoscale heads, complete B v3 at the identical seed and pose in
            // one session ("d0_"/"d1_" arms). D3 is a CPU exact-field analysis of the D2 geometry.
            SHOTS.add(new Shot("supercell", 20261008L, "attribution_intervals"));
            SHOTS.add(new Shot("supercell", 20261008L, "attribution_exact"));
            for (String type : List.of("cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell")) {
                for (long seed : new long[]{20261008L, 20268927L, 20276846L}) {
                    var views = new ArrayList<>(FULL_VIEWS);
                    views.add("portrait");
                    views.add("portrait_front");
                    for (String view : views) {
                        SHOTS.add(new Shot(type, seed, "d0_" + view));
                        SHOTS.add(new Shot(type, seed, "d1_" + view));
                        SHOTS.add(new Shot(type, seed, view));
                    }
                }
            }
            if ("1".equals(System.getenv("PA_T257_SMOKE"))) {
                var views = List.of("d0_side", "d1_side", "side", "d0_above", "d1_above", "above", "attribution_intervals", "attribution_exact");
                SHOTS.removeIf(shot -> shot.seed != 20261008L || !views.contains(shot.view));
            }
        } else if (convectiveCampaign()) {
            // T257: every candidate-B pose is followed by rejected candidate A at the identical
            // seed/pose in the same session ("a_" arms). "portrait" is a closer side pose for review.
            SHOTS.add(new Shot("supercell", 20261008L, "attribution_intervals"));
            SHOTS.add(new Shot("supercell", 20261008L, "attribution_exact"));
            for (String type : List.of("cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell")) {
                for (long seed : new long[]{20261008L, 20268927L, 20276846L}) {
                    for (String view : FULL_VIEWS) {
                        SHOTS.add(new Shot(type, seed, view));
                        SHOTS.add(new Shot(type, seed, "a_" + view));
                    }
                    for (String view : List.of("portrait", "portrait_front")) {
                        SHOTS.add(new Shot(type, seed, view));
                        SHOTS.add(new Shot(type, seed, "a_" + view));
                    }
                }
            }
            for (String arm : List.of("neutral", "tilt", "shear", "split", "narrowing", "wall")) {
                SHOTS.add(new Shot("supercell", 20261008L, "control_" + arm));
            }
            if ("1".equals(System.getenv("PA_T257_SMOKE"))) {
                var views = List.of("side", "close", "below", "portrait", "a_side", "a_close", "a_portrait", "attribution_intervals", "attribution_exact");
                SHOTS.removeIf(shot -> shot.seed != 20261008L || !views.contains(shot.view));
            }
            String resume=System.getenv("PA_T257_SHOTS_FILE");
            if(resume!=null&&!resume.isBlank()) {
                try {
                    var selected=StormMorphologyCapturePlan.select(SHOTS.stream().map(Shot::name).toList(),java.nio.file.Files.readAllLines(Path.of(resume)));
                    SHOTS.removeIf(shot->!selected.contains(shot.name()));
                    ProjectAtmosphere.LOGGER.info("T257_RESUME selection={} arms={} geometry=frozen",resume,SHOTS.size());
                }catch(java.io.IOException failure){throw new IllegalArgumentException("Cannot read T257 resume list",failure);}
            }
        } else {
        SHOTS.add(new Shot("supercell", 20261008L, "attribution_intervals"));
        SHOTS.add(new Shot("supercell", 20261008L, "attribution_exact"));
        for (String type : List.of("cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell")) {
            for (long seed : new long[]{20261008L, 20268927L, 20276846L}) {
                for (String view : growthCampaign()
                        ? List.of("side", "opposite_side", "front", "back", "above", "below", "low_angle", "far", "close", "upwind", "downwind", "crosswind_left", "crosswind_right", "tower", "transition", "canopy_anvil", "updraft_oblique")
                        : List.of("side", "opposite_side", "front", "back", "above", "below", "low_angle", "far", "close", "updraft_oblique")) {
                    SHOTS.add(new Shot(type, seed, view));
                }
            }
        }
        for (String arm : List.of("neutral", "tilt", "shear", "split", "narrowing", "wall")) {
            SHOTS.add(new Shot("supercell", 20261008L, "control_" + arm));
        }
        if (growthCampaign()) for (String type : List.of("cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell")) {
            for (String view : List.of("side", "above", "close")) {
                SHOTS.add(new Shot(type, 20261008L, "baseline_T255_" + view));
                SHOTS.add(new Shot(type, 20261008L, "envelope_A_" + view));
                SHOTS.add(new Shot(type, 20261008L, "envelope_B_" + view));
            }
        }
        if (growthCampaign()) for (String arm : List.of("full", "counts", "no_canopy", "no_ice", "hard_merge")) {
            SHOTS.add(new Shot("supercell",20261008L,"cost_"+arm));
        }
        if ("1".equals(System.getenv("PA_T255_SMOKE"))) {
            // Iteration evidence only, never a substitute for the full three-seed acceptance gallery.
            var views = List.of("side", "front", "above", "below", "close", "attribution_intervals", "attribution_exact", "control_wall", "control_neutral", "control_split");
            SHOTS.removeIf(shot -> shot.seed != 20261008L || !views.contains(shot.view));
        }
        if ("1".equals(System.getenv("PA_T255_LIFECYCLE"))) {
            SHOTS.removeIf(shot -> shot.seed != 20261008L || !shot.view.equals("side"));
        }
        if ("1".equals(System.getenv("PA_T256_SMOKE"))) {
            var views=List.of("side","above","below","close","envelope_A_side","envelope_B_side","baseline_T255_side","attribution_intervals","attribution_exact","cost_full","cost_counts","cost_no_canopy","cost_no_ice","cost_hard_merge");
            SHOTS.removeIf(shot -> shot.seed != 20261008L || !views.contains(shot.view));
        }
        }
    }
    static boolean growthCampaign() { return "1".equals(System.getenv("PA_T256_CAPTURE")); }
    static boolean convectiveCampaign() { return "1".equals(System.getenv("PA_T257_CAPTURE")); }
    static boolean diagnosticCampaign() { return "1".equals(System.getenv("PA_T257_DIAGNOSTIC")); }
    static boolean anatomyCampaign() { return "1".equals(System.getenv("PA_T258_CAPTURE")); }
    static boolean systemCampaign() { return "1".equals(System.getenv("PA_T260_CAPTURE")); }
    static String campaign() { return systemCampaign() ? "T260" : anatomyCampaign() ? "T258" : convectiveCampaign() ? "T257" : growthCampaign() ? "T256" : "T255"; }
    static boolean enabled() { return systemCampaign() || anatomyCampaign() || convectiveCampaign() || growthCampaign() || "1".equals(System.getenv("PA_T255_CAPTURE")); }
    /** T258 compiled-program cache (capture only): one type/seed's B v3 and C levels stay resident. */
    private static final java.util.LinkedHashMap<String, StormGeometryProgram> T258_CACHE = new java.util.LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, StormGeometryProgram> eldest) { return size() > 6; }
    };
    private static Integer t258SavedFov;
    private static StormGeometryProgram t258Program(String key, java.util.function.Supplier<StormGeometryProgram> build) {
        StormGeometryProgram p = T258_CACHE.get(key);
        if (p == null) { p = build.get(); T258_CACHE.put(key, p); }
        return p;
    }
    private static void t258RestoreFov() {
        if (t258SavedFov != null) { Minecraft.getInstance().options.fov().set(t258SavedFov); t258SavedFov = null; }
    }
    static int version() { return current == null ? 0 : current.version(); }

    static void releaseReadbacks() {
        if (anatomyCampaign()) t258RestoreFov();
        if (systemCampaign()) ConvectiveSystemCapture.release();
        intervalPair = null;
        intervalPairSignature = 0;
        current = null;
        TIMES.clear();
        PIPELINE_TIMES.clear();
    }

    private static Shot currentShot;
    static String currentView() { return currentShot == null ? null : currentShot.view(); }
    static void current(StormGeometryProgram program) { current = program; }
    static void resetTiming() {
        TIMES.clear();
        PIPELINE_TIMES.clear();
        lastPipelineSerial = ExperimentalStormMorphologyState.pipelineSerial();
        lastTimingSerial = VolumetricCloudRenderer.lastGpuTimingSample();
    }

    static double[] move(int index) {
        Shot shot = SHOTS.get(index);
        currentShot = shot;
        var fixture = StormPerformanceBaseline.suiteFixture();
        if (fixture == null) throw new IllegalStateException("T255 needs the isolated integrated harness fixture");
        var shape = CloudShapeProfile.defaultFor(shot.type, CloudFamily.CUMULONIMBUS, null);
        if (shot.view.startsWith("control_")) {
            String arm = shot.view.substring(8);
            shape = new CloudShapeProfile("prototype/control", shape.getBaseRadius(), shape.getBaseOffset(),
                    shape.getTopOffset(), shape.getLobeCountMin(), shape.getLobeCountMax(), shape.getLobeStrength(),
                    arm.equals("tilt") ? .8f : 0, arm.equals("shear") ? .8f : 0,
                    arm.equals("split") ? 1 : 0, arm.equals("narrowing") ? 1 : 0,
                    shape.getAnvilSpread(), shape.getBaseFlattening(), shape.getEdgeRaggedness(), arm.equals("wall") ? 1 : 0);
        }
        if (systemCampaign()) return ConvectiveSystemCapture.move(shot, fixture);
        if (anatomyCampaign()) return moveAnatomy(shot, shape, fixture);
        if (convectiveCampaign()) {
            var detail = shot.view.startsWith("d0_") ? ConvectiveGrowthFieldPrototype.Detail.CORE
                    : shot.view.startsWith("d1_") ? ConvectiveGrowthFieldPrototype.Detail.MESO : ConvectiveGrowthFieldPrototype.Detail.FULL;
            current = shot.view.startsWith("a_")
                    ? net.Gabou.projectatmosphere.clouds.morphology.ConvectiveGrowthFieldA.compile(shot.type, shape, shot.seed)
                    : ConvectiveGrowthFieldPrototype.compile(shot.type, shape, shot.seed, detail);
        } else
        current = !growthCampaign() || shot.view.startsWith("baseline_T255_")
                ? StormGeometryProgram.legacy(StormMorphologyPrototype.compile(shot.type, shape, shot.seed))
                : GrowthDefinedStormPrototype.compile(shot.type, shape, shot.seed).withEnvelope(shot.view.startsWith("envelope_A_"));
        if (current instanceof GrowthDefinedStormPrototype.Program p && shot.view.startsWith("cost_")) {
            current=p.withInspectionMask(switch(shot.view) {case "cost_no_canopy" -> 1; case "cost_no_ice" -> 2; case "cost_hard_merge" -> 4; case "cost_counts" -> 8; default -> 0;});
        }
        double originX = fixture.centerX(), originZ = fixture.centerZ(), baseY = 1400;
        ExperimentalStormMorphologyState.install(current, (float) originX, (float) baseY, (float) originZ);
        ExperimentalStormMorphologyState.intervals(!shot.view.equals("attribution_exact"));
        double cx = originX + 650, cy = baseY + 390, cz = originZ, d = shot.view.startsWith("attribution_") ? 3200 : 2200;
        double x = cx, y = cy, z = cz + d;
        String poseView=(shot.view.startsWith("a_") || shot.view.startsWith("d0_") || shot.view.startsWith("d1_") ? shot.view.substring(shot.view.indexOf('_') + 1) : shot.view).replace("baseline_T255_", "").replace("envelope_A_", "").replace("envelope_B_", "");
        if (poseView.startsWith("cost_")) poseView="close";
        switch (poseView) {
            case "opposite_side" -> z = cz - d;
            case "front" -> { x = cx - d; z = cz; }
            // Common downwind framing must clear the complete mature canopy/ice reach.
            // At 2200 the supercell's nearest fibres cross the perspective viewport.
            case "back" -> { x = cx + 2 * d; z = cz; }
            case "above" -> { y = cy + d; z = cz + .2 * d; }
            case "below" -> { y = 650; z = cz + .4 * d; }
            case "low_angle" -> { x = cx - .707 * d; y = 650; z = cz + .707 * d; }
            case "far" -> z = cz + 2 * d;
            case "portrait" -> z = cz + .55 * d;
            case "portrait_front" -> { x = cx - .55 * d; z = cz; }
            case "close" -> z = cz + .75 * d; // Common closer framing, outside the widest canopy to avoid perspective clipping.
            case "attribution_intervals", "attribution_exact" -> { x = cx - d; z = cz; }
            case "updraft_oblique" -> { x = cx - .707 * d; y = cy + .3 * d; z = cz + .707 * d; }
            case "upwind" -> { x=cx-d; y=cy+.12*d; z=cz; }
            case "downwind" -> { x=cx+2*d; y=cy+.12*d; z=cz; }
            case "crosswind_left" -> { y=cy+.15*d; z=cz-d; }
            case "crosswind_right" -> { y=cy+.15*d; z=cz+d; }
            case "tower" -> { cx=originX+180; cy=baseY+350; x=cx-.50*d; y=cy+.08*d; z=cz+.866*d; }
            case "transition" -> { cy=baseY+650; x=cx-.707*d; y=cy+.15*d; z=cz+.707*d; }
            case "canopy_anvil" -> { cy=baseY+700; x=cx+.707*2*d; y=cy+.4*d; z=cz+.707*2*d; }
        }
        if(convectiveCampaign()) {
            // Fit BOTH frozen recipes to one camera, never the currently selected candidate alone.
            // The containment boxes affect camera placement only, not visible density support.
            var a=net.Gabou.projectatmosphere.clouds.morphology.ConvectiveGrowthFieldA.compile(shot.type,shape,shot.seed);
            var b=ConvectiveGrowthFieldPrototype.compile(shot.type,shape,shot.seed);
            var mc=Minecraft.getInstance();
            double[] framed=StormMorphologyCapturePlan.fit(new double[]{x,y,z,cx,cy,cz},a.bounds(),b.bounds(),
                    originX,baseY,originZ,mc.options.fov().get(),(double)mc.getMainRenderTarget().width/mc.getMainRenderTarget().height,650);
            x=framed[0];y=framed[1];z=framed[2];
            ProjectAtmosphere.LOGGER.info("T257_LENS shot={} fov={} fovEffectScale={} boundsFit=both_candidates margin=0.88",
                    shot.name(),mc.options.fov().get(),mc.options.fovEffectScale().get());
        }
        TIMES.clear();
        PIPELINE_TIMES.clear();
        lastPipelineSerial = ExperimentalStormMorphologyState.pipelineSerial();
        lastTimingSerial = VolumetricCloudRenderer.lastGpuTimingSample();
        ProjectAtmosphere.LOGGER.info(campaign() + "_POSE shot={} version={} signature={} origin=({},{},{}) bounds={} camera=({},{},{}) aim=({},{},{}) packedBytes={} macro=no_noise micro=off flat=1",
                shot.name(), current.version(), Long.toUnsignedString(current.signature(), 16),
                originX, baseY, originZ, current.bounds(), x, y, z, cx, cy, cz, current.packed().length * 4);
        if (current instanceof ConvectiveGrowthFieldPrototype.Program c) ProjectAtmosphere.LOGGER.info(
                "T257_FIELD shot={} turrets={} canopy={} bodyBounds={} canopyBounds={} packedTexels={} structures=1_body_1_canopy detail={}",
                shot.name(), c.turrets().size(), c.hasCanopy(), c.bodyBounds(), c.canopyBounds(), current.packed().length / 4, c.detail());
        if(current instanceof GrowthDefinedStormPrototype.Program p) ProjectAtmosphere.LOGGER.info(
                "T256_GROWTH shot={} growthSignature={} growthCount={} canopyCount={} iceCount={} envelopeContribution={}",
                shot.name(),Long.toUnsignedString(p.growthSignature(),16),p.growth().size(),p.canopies().size(),p.ice().size(),p.envelopeContributes());
        return new double[]{x, y, z, cx, cy, cz};
    }

    /** T258 pose and program; the fit contains B v3 and C3 with the EFFECTIVE lens (options FOV x player modifier). */
    private static double[] moveAnatomy(Shot shot, CloudShapeProfile shape, StormPerformanceBaseline.SuiteFixture fixture) {
        String view = shot.view;
        boolean b3 = view.startsWith("b3_");
        if (b3) view = view.substring(3);
        var level = StormAnatomyCandidateC.Level.C3;
        for (var l : StormAnatomyCandidateC.Level.values()) {
            String prefix = l.name().toLowerCase(java.util.Locale.ROOT) + "_";
            if (view.startsWith(prefix)) { level = l; view = view.substring(3); }
        }
        boolean zoom = view.startsWith("z4_");
        if (zoom) view = view.substring(3);
        String type = shot.type;
        long seed = shot.seed;
        var bProgram = (ConvectiveGrowthFieldPrototype.Program) t258Program("b3/" + type + "/" + seed, () -> ConvectiveGrowthFieldPrototype.compile(type, shape, seed));
        var c3 = (StormAnatomyCandidateC.Program) t258Program("C3/" + type + "/" + seed, () -> StormAnatomyCandidateC.compile(type, shape, seed));
        final var lv = level;
        current = b3 ? bProgram : level == StormAnatomyCandidateC.Level.C3 ? c3
                : t258Program(level + "/" + type + "/" + seed, () -> StormAnatomyCandidateC.compile(type, shape, seed, lv));
        double originX = fixture.centerX(), originZ = fixture.centerZ(), baseY = 1400;
        ExperimentalStormMorphologyState.install(current, (float) originX, (float) baseY, (float) originZ);
        ExperimentalStormMorphologyState.intervals(!view.equals("attribution_exact"));
        var mc = Minecraft.getInstance();
        if (t258SavedFov == null) t258SavedFov = mc.options.fov().get();
        int optionFov = t258SavedFov;
        double modifier = mc.player == null ? 1.0 : mc.player.getFieldOfViewModifier();
        double effective = StormAnatomyCapturePlan.effectiveFov(optionFov, modifier);
        double aspect = (double) mc.getMainRenderTarget().width / mc.getMainRenderTarget().height;
        double[] rel = zoom ? StormAnatomyCapturePlan.zoomPose(view, bProgram.bounds(), c3.bounds(), effective, aspect)
                : StormAnatomyCapturePlan.pose(view, bProgram.bounds(), c3.bounds(), effective, aspect);
        int shotFov = zoom ? StormAnatomyCapturePlan.zoomOptionFov(effective, modifier, StormAnatomyCapturePlan.ZOOM) : optionFov;
        mc.options.fov().set(shotFov);
        double achieved = zoom ? StormAnatomyCapturePlan.achievedZoom(effective, modifier, shotFov) : 1;
        ProjectAtmosphere.LOGGER.info("T258_LENS shot={} optionFov={} shotFov={} fovModifier={} effectiveFov={} zoom={} boundsFit=b3_and_c3 margin=0.88 fovEffectScale={}",
                shot.name(), optionFov, shotFov, modifier, shotFov * modifier, achieved, mc.options.fovEffectScale().get());
        double x = originX + rel[0], y = baseY + rel[1], z = originZ + rel[2], cx = originX + rel[3], cy = baseY + rel[4], cz = originZ + rel[5];
        TIMES.clear();
        PIPELINE_TIMES.clear();
        lastPipelineSerial = ExperimentalStormMorphologyState.pipelineSerial();
        lastTimingSerial = VolumetricCloudRenderer.lastGpuTimingSample();
        ProjectAtmosphere.LOGGER.info(campaign() + "_POSE shot={} version={} signature={} origin=({},{},{}) bounds={} camera=({},{},{}) aim=({},{},{}) packedBytes={} macro=no_noise micro=off flat=1",
                shot.name(), current.version(), Long.toUnsignedString(current.signature(), 16),
                originX, baseY, originZ, current.bounds(), x, y, z, cx, cy, cz, (long) current.textureWidth() * current.textureHeight() * 16);
        if (current instanceof StormAnatomyCandidateC.Program c) {
            var roles = new java.util.TreeMap<String, Integer>();
            for (var e : c.elements()) if (e.level().ordinal() <= c.level().ordinal()) roles.merge(e.role().name(), 1, Integer::sum);
            ProjectAtmosphere.LOGGER.info("T258_FIELD shot={} candidate=C level={} voxel={} dims={} feather={} bodyBox={} canopyBox={} texture={}x{} elements={}",
                    shot.name(), c.level(), c.voxel(), java.util.Arrays.toString(c.dims()), c.feather(), c.bodyBox(), c.canopyBox(),
                    c.textureWidth(), c.textureHeight(), roles);
        } else {
            ProjectAtmosphere.LOGGER.info("T258_FIELD shot={} candidate=B_v3 detail={}", shot.name(), bProgram.detail());
        }
        return new double[]{x, y, z, cx, cy, cz};
    }

    static void sample(int elapsedFrames) {
        if (elapsedFrames < 32) return; // warm-up and pending-query drain, not timed samples.
        if (systemCampaign()) ConvectiveSystemCapture.tick(elapsedFrames);
        long serial = VolumetricCloudRenderer.lastGpuTimingSample();
        if (serial != lastTimingSerial) {
            lastTimingSerial = serial;
            float ms = VolumetricCloudRenderer.currentGpuQueryMilliseconds();
            if (Float.isFinite(ms) && ms > 0 && VolumetricCloudRenderer.currentGpuQueryAge() <= 32) TIMES.add(ms);
        }
        long pipelineSerial = ExperimentalStormMorphologyState.pipelineSerial();
        if (pipelineSerial != lastPipelineSerial) {
            lastPipelineSerial = pipelineSerial;
            float ms = ExperimentalStormMorphologyState.pipelineMs();
            if (Float.isFinite(ms) && ms > 0 && ExperimentalStormMorphologyState.pipelineAge() <= 32) PIPELINE_TIMES.add(ms);
        }
    }

    static void captured(int index, Path folder) throws java.io.IOException {
        Shot shot = SHOTS.get(index);
        if (!ExperimentalStormMorphologyState.enabled()
                || ClientCloudVisualDensity.source() != ClientCloudVisualDensity.Source.MORPHOLOGY_PROTOTYPE
                || VolumetricCloudRenderer.lastProgram() != ExperimentalStormMorphologyState.program()
                || TIMES.size() < 30 || PIPELINE_TIMES.size() < 30) {
            throw new IllegalStateException("No successfully presented prototype authority or insufficient fresh GPU timing samples");
        }
        var minecraft = Minecraft.getInstance();
        var fixture = StormPerformanceBaseline.suiteFixture();
        if (minecraft.level == null || fixture == null) throw new IllegalStateException("Missing fixture dimension");
        String dimension = minecraft.level.dimension().location().toString();
        float originX = (float) fixture.centerX(), originZ = (float) fixture.centerZ();
        float cameraError = 0;
        for (int i = 0; i < 17; i++) {
            float t = .10f + i * .05f;
            var probe = new net.minecraft.world.phys.Vec3(originX + current.axisX(t), 1400 + current.height() * t, originZ);
            float expected = current.density((float) (probe.x - originX), (float) (probe.y - 1400),
                    (float) (probe.z - originZ), StormMorphologyPrototype.Scale.MESO);
            cameraError = Math.max(cameraError, Math.abs(expected - ClientCloudVisualDensity.densityAt(dimension, probe)));
            if (ClientCloudVisualDensity.densityAt("projectatmosphere:t255_wrong_dimension", probe) != 0) {
                throw new IllegalStateException("Prototype camera authority leaked into another dimension");
            }
        }
        ProjectAtmosphere.LOGGER.info(campaign() + "_CAMERA_AUTHORITY shot={} probes=17 maxAbsError={} wrongDimensionDensity=0 source={} pass={}",
                shot.name(), cameraError, ClientCloudVisualDensity.source(), cameraError <= .002f);
        if (!Float.isFinite(cameraError) || cameraError > .002f) throw new IllegalStateException("Camera source differs from geometry program");
        // Read actual cloud target RGBA, not a CPU rerender or independent browser scene.
        var target = VolumetricCloudRenderTargets.currentCloudTarget();
        if (shot.view.equals("cost_counts")) {
            var counts=org.lwjgl.BufferUtils.createFloatBuffer(target.width*target.height*4);
            com.mojang.blaze3d.systems.RenderSystem.bindTexture(target.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,counts);
            double[] totals=new double[4];float[] maxima=new float[4];
            for(int i=0;i<counts.limit();i++) {
                float value=counts.get(i);
                if (!Float.isFinite(value)) throw new IllegalStateException("GPU counter target overflowed");
                totals[i%4]+=value;maxima[i%4]=Math.max(maxima[i%4],value);
            }
            ProjectAtmosphere.LOGGER.info("T256_COST_COUNTS shot={} queryCalls={} growthEvaluations={} canopyEvaluations={} iceEvaluations={} mergeCalls={} maxPerPixel={} scope=instrumented_march_not_baseline_timing",
                    shot.name(),totals[0],totals[1],totals[2],totals[3],totals[1]+totals[2]+totals[3],java.util.Arrays.toString(maxima));
        }
        if (shot.view.equals("attribution_intervals") || shot.view.equals("attribution_exact")) {
            var pixels = org.lwjgl.BufferUtils.createFloatBuffer(target.width * target.height * 4);
            com.mojang.blaze3d.systems.RenderSystem.bindTexture(target.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            if (shot.view.equals("attribution_intervals")) {
                intervalPair = new float[pixels.remaining()];
                pixels.get(intervalPair);
                intervalPairSignature = current.signature();
            } else {
                if (intervalPair == null || intervalPair.length != pixels.remaining() || intervalPairSignature != current.signature()) {
                    throw new IllegalStateException("Unmatched interval/exact geometry or readback dimensions");
                }
                float error = 0;
                for (float value : intervalPair) error = Math.max(error, Math.abs(value - pixels.get()));
                int differing = 0; // T260.4: bit-identity, not only the 0.002 tolerance
                for (int k = 0; k < intervalPair.length; k++) if (Float.floatToRawIntBits(intervalPair[k]) != Float.floatToRawIntBits(pixels.get(k))) differing++;
                ProjectAtmosphere.LOGGER.info(campaign() + "_TRAVERSAL_BITS shot={} differingChannels={} bitIdentical={}", shot.name(), differing, differing == 0);
                intervalPair = null;
                ProjectAtmosphere.LOGGER.info(campaign() + "_TRAVERSAL_PAIR signature={} channels={} maxAbsRGBA={} tolerance=0.002 pass={}",
                        Long.toUnsignedString(current.signature(), 16), target.width * target.height * 4, error, error <= .002f);
                if ((!Float.isFinite(error) || error > .002f) && !shot.type().endsWith("_legacy")) throw new IllegalStateException("Interval traversal changed the reference integration");
            }
        }
        if (systemCampaign()) ConvectiveSystemCapture.hashTarget(shot.name(), shot.type(), target.getColorTextureId(), target.width, target.height); // T260.5
        try (NativeImage raw = new NativeImage(target.width, target.height, false)) {
            com.mojang.blaze3d.systems.RenderSystem.bindTexture(target.getColorTextureId());
            raw.downloadTexture(0, false);
            raw.flipY();
            raw.writeToFile(folder.resolve(shot.name() + "__raw.png").toFile());
            int occupiedEdge = 0;
            for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                if (x != 0 && y != 0 && x != target.width - 1 && y != target.height - 1) continue;
                // NativeImage RGBA ints are ABGR; alpha is the high byte in this mapping.
                if (((raw.getPixelRGBA(x, y) >>> 24) & 255) > 2) occupiedEdge++;
            }
            ProjectAtmosphere.LOGGER.info(campaign() + "_FRAMING shot={} occupiedEdgePixels={} alphaThreshold=0.01 pass={}", shot.name(), occupiedEdge, occupiedEdge == 0);
            // T257 logs framing (exporter marks it) instead of aborting the whole comparison run.
            if (occupiedEdge != 0 && !shot.view.startsWith("cost_") && !convectiveCampaign() && !anatomyCampaign() && !systemCampaign()) throw new IllegalStateException("Morphology crosses the viewport edge; framing rejected");
        }
        TIMES.sort(Float::compare);
        float median = TIMES.isEmpty() ? -1 : TIMES.get(TIMES.size() / 2);
        float p95 = TIMES.isEmpty() ? -1 : TIMES.get(Math.min(TIMES.size() - 1, (int) Math.ceil(TIMES.size() * .95) - 1));
        float max = TIMES.isEmpty() ? -1 : TIMES.get(TIMES.size() - 1);
        ProjectAtmosphere.LOGGER.info(campaign() + "_GPU shot={} samples={} warmup=32 timer=GL_TIMESTAMP medianMs={} p95Ms={} maxMs={} target={}x{} hardware={} renderer={} prototype=exact_no_old_atlas passScope=raymarch_only program={}",
                shot.name(), TIMES.size(), median, p95, max, target.width, target.height,
                GL11.glGetString(GL11.GL_VENDOR), GL11.glGetString(GL11.GL_RENDERER), VolumetricCloudRenderer.lastProgram().serializedName());
        PIPELINE_TIMES.sort(Float::compare);
        if (systemCampaign()) ConvectiveSystemCapture.captured(shot.name(), shot.view);
        ProjectAtmosphere.LOGGER.info(campaign() + "_PIPELINE shot={} samples={} warmup=32 maxQueryAge=32 timer=GL_TIMESTAMP medianMs={} p95Ms={} maxMs={} scope=weather_shadow_march_composite",
                shot.name(), PIPELINE_TIMES.size(), percentile(PIPELINE_TIMES, .5), percentile(PIPELINE_TIMES, .95), percentile(PIPELINE_TIMES, 1));
    }

    private static float percentile(List<Float> values, double fraction) {
        return values.isEmpty() ? -1 : values.get(Math.max(0, Math.min(values.size() - 1, (int) Math.ceil(values.size() * fraction) - 1)));
    }

    static boolean wantsParity(int index) {
        String view = SHOTS.get(index).view;
        if (systemCampaign() && SHOTS.get(index).type().startsWith("t2605_") && ConvectiveSystemCapture.lightingMode(SHOTS.get(index).type()) > 20) return false; // T260.5: parity on flat/vol only
        if (systemCampaign() && SHOTS.get(index).type().startsWith("t2606_") && ConvectiveSystemCapture.lightingMode(SHOTS.get(index).type()) > 20) return false; // T260.6: parity on flat/vol only
        if (systemCampaign()) return ConvectiveSystemCapture.wantsParity(view);
        return view.equals("side") || view.equals("d0_side") || view.equals("d1_side") || view.equals("b3_side") || view.equals("c0_side") || view.equals("c1_side") || view.equals("c2_side") || view.startsWith("control_") || view.startsWith("envelope_") || view.startsWith("baseline_T255_") || view.equals("attribution_intervals");
    }

    static void uploadState(int rowLength, int skipPixels, int skipRows, int alignment) {
        ProjectAtmosphere.LOGGER.info("T258_UPLOAD_STATE inheritedRowLength={} skipPixels={} skipRows={} alignment={} reset=0/0/0/4", rowLength, skipPixels, skipRows, alignment);
    }

    /** T258: the uploaded fixture texture read back from the GPU must equal the CPU texture bit for bit. */
    private static void verifyUpload(int index) {
        int id = ExperimentalStormMorphologyState.textureId();
        float[] expected = current.textureData();
        var buffer = org.lwjgl.BufferUtils.createFloatBuffer(expected.length);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int packRow = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH), packAlign = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, buffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, packRow);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, packAlign);
        int mismatched = 0, first = -1;
        for (int i = 0; i < expected.length; i++) if (Float.floatToRawIntBits(buffer.get(i)) != Float.floatToRawIntBits(expected[i])) { mismatched++; if (first < 0) first = i; }
        ProjectAtmosphere.LOGGER.info("T258_UPLOAD_VERIFY shot={} floats={} mismatched={} firstMismatch={} texture={}x{} pass={}",
                SHOTS.get(index).name(), expected.length, mismatched, first, current.textureWidth(), current.textureHeight(), mismatched == 0);
    }

    static void captureParity(int index) {
        if (anatomyCampaign()) verifyUpload(index);
        if (systemCampaign()) verifyUpload(index);
        var target = VolumetricCloudRenderTargets.currentCloudTarget();
        var pixels = org.lwjgl.BufferUtils.createFloatBuffer(target.width * target.height * 4);
        com.mojang.blaze3d.systems.RenderSystem.bindTexture(target.getColorTextureId());
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
        var b = current.bounds();
        int checked = 0, occupied = 0;
        float maxError = 0;
        float[] worst = new float[5];
        for (int iy = 0; iy < target.height; iy += 7) for (int ix = 0; ix < target.width; ix += 11) {
            float x = b.minX() + (b.maxX() - b.minX()) * ((ix + .5f) / target.width);
            float y = b.minY() + (b.maxY() - b.minY()) * (((iy % 64) + .5f) / 64f);
            float z = b.minZ() + (b.maxZ() - b.minZ()) * (((iy / 64) + .5f) / (float) Math.ceil(target.height / 64.0));
            float cpu = current.density(x, y, z, StormMorphologyPrototype.Scale.MESO);
            float gpu = pixels.get((iy * target.width + ix) * 4);
            if (!Float.isFinite(gpu)) throw new IllegalStateException("Non-finite GPU density");
            if (Math.abs(cpu - gpu) > maxError) {
                maxError = Math.abs(cpu - gpu);
                worst = new float[]{x, y, z, cpu, gpu};
            }
            if (cpu > .01f || gpu > .01f) occupied++;
            checked++;
        }
        ProjectAtmosphere.LOGGER.info(campaign() + "_PARITY shot={} samples={} occupied={} maxAbsError={} tolerance=0.002 pass={}",
                SHOTS.get(index).name(), checked, occupied, maxError, maxError <= .002f && occupied > 0);
        ProjectAtmosphere.LOGGER.info(campaign() + "_PARITY_WORST local=({},{},{}) cpu={} gpu={}", worst[0], worst[1], worst[2], worst[3], worst[4]);
        if (maxError > .002f || occupied == 0) throw new IllegalStateException("T255 CPU/GPU parity failed");
    }
}
