package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.simulation.CloudGroupSpawner;
import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Player-equivalent production capture (stabilisation S0; diagnostic, OFF unless {@code PA_PROD_CAPTURE_SET} is set,
 * e.g. {@code ./gradlew runClient -PpaProdCapture=s1}).
 *
 * <p>Spawns production cloud types in the joined singleplayer world, frames named views around each one, and shoots
 * paired A/B arms inside the same session (storms differ between launches, so arms are only comparable within one).
 * Captures use the PLAYER configuration: production temporal path, the configured quality, and the step scale the
 * frame-time governor actually settled to at that view (held for the arms of that view so they stay comparable and
 * logged as {@code stepScale}); an optional arm re-shoots with the governor pinned at 1.0 (what older campaigns did).
 *
 * <p>Frames: {@code run/screenshots/prodcapture/<set>/<type>__<view>__t<time>__<arm>.png}; every shot logs one
 * {@code PROD_CAPTURE_SHOT} line. The client stops after the last shot ({@code PROD_CAPTURE_DONE}).
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE, modid = ProjectAtmosphere.MODID)
public final class ProductionCloudCapture {
    private static final String SET = System.getenv("PA_PROD_CAPTURE_SET");
    private static final boolean ON = SET != null && !SET.isBlank();

    /** One A/B arm: its name, how to apply it, and whether it re-shoots with the governor pinned at 1.0. */
    record Arm(String name, Runnable apply, boolean pinFull) {}

    record Plan(List<String> types, List<String> stormViews, List<String> otherViews, int[] times, List<Arm> arms) {}

    private static Arm arm(String name, int colorPipeline, int octaveNormalize, boolean pinFull) {
        return arm(name, colorPipeline, octaveNormalize, 0, 0, pinFull);
    }

    /** Lighting arm: S1 colour pipeline, S2 octave normalisation, S1b ambient transmission (nearField: S4, removed after E3). */
    private static Arm arm(String name, int colorPipeline, int octaveNormalize, int nearField, int ambientTransmission,
                           boolean pinFull) {
        return new Arm(name, () -> {
            VolumetricCloudDebugConfig.setColorPipeline(colorPipeline);
            VolumetricCloudDebugConfig.setOctaveNormalize(octaveNormalize);
            VolumetricCloudDebugConfig.setAmbientTransmission(ambientTransmission);
            // E16: the storm fix and the thickness gain are production now; a lighting arm is pre-E16 without them
            // (storm() / thick() set their own values after this).
            VolumetricCloudDebugConfig.setStormMaterialFix(0);
            VolumetricCloudDebugConfig.setOpticalThicknessGain(0.0F);
        }, pinFull);
    }

    /** S9: the candidate lighting plus the billow hierarchy at {@code amplitude}. */
    private static Arm shape(String name, float amplitude) {
        Arm lighting = arm(name, 1, 0, 0, 1, false);
        return new Arm(name, () -> {
            lighting.apply().run();
            VolumetricCloudDebugConfig.setShapeHierarchy(amplitude);
        }, false);
    }

    /** Candidate lighting on the diagnostic monolith with explicit storm-field consumer bits and lighting mode. */
    private static Arm mono(String name, int fieldBits, int lightingMode) {
        Arm lighting = arm(name, 1, 0, 0, 1, false);
        return new Arm(name, () -> {
            lighting.apply().run();
            VolumetricCloudDebugConfig.setFinalProgramOverride(CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH);
            VolumetricCloudDebugConfig.setStormFieldForcedOnMonolith(fieldBits);
            VolumetricCloudDebugConfig.setDiagnosticLightingMode(lightingMode);
        }, false);
    }

    /** Candidate lighting + the E10 field coverage fix (+ the S9 hierarchy at {@code hierarchy}). */
    private static Arm fix(String name, float hierarchy) {
        Arm lighting = arm(name, 1, 0, 0, 1, false);
        return new Arm(name, () -> {
            lighting.apply().run();
            VolumetricCloudDebugConfig.setFieldCoverageFix(1);
            VolumetricCloudDebugConfig.setShapeHierarchy(hierarchy);
        }, false);
    }

    /** Candidate lighting + the E12 optical-thickness gain. */
    private static Arm thick(String name, float gain) {
        Arm lighting = arm(name, 1, 0, 0, 1, false);
        return new Arm(name, () -> {
            lighting.apply().run();
            VolumetricCloudDebugConfig.setOpticalThicknessGain(gain);
        }, false);
    }

    /** Candidate lighting + the E13 storm material fix (+ the E12 thickness gain). */
    private static Arm storm(String name, float gain) {
        Arm lighting = arm(name, 1, 0, 0, 1, false);
        return new Arm(name, () -> {
            lighting.apply().run();
            VolumetricCloudDebugConfig.setStormMaterialFix(1);
            VolumetricCloudDebugConfig.setOpticalThicknessGain(gain);
        }, false);
    }

    /** S11: the storm structure arm on top of production (E16 lighting + storm material fix + gain 2). */
    private static Arm structure(String name, int mode) {
        return new Arm(name, () -> VolumetricCloudDebugConfig.setStormStructure(mode), false);
    }

    /**
     * L-TEMP (lighting investigation): the accepted S11 storm on the diagnostic monolith with lighting mode
     * {@code mode} (0 = the production image on the monolith). Diagnostic values are read with the colour pipeline
     * OFF so the composite passes them unencoded; mode 0 keeps the production pipeline.
     */
    private static Arm diag(String name, int mode) {
        return new Arm(name, () -> {
            VolumetricCloudDebugConfig.setStormStructure(15);
            VolumetricCloudDebugConfig.setColorPipeline(mode == 0 ? 1 : 0);
            VolumetricCloudDebugConfig.setFinalProgramOverride(CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH);
            VolumetricCloudDebugConfig.setStormFieldForcedOnMonolith(StormFieldGeometry.PRODUCTION_CONSUMER_BITS);
            VolumetricCloudDebugConfig.setDiagnosticLightingMode(mode);
        }, false);
    }

    /** P3: the empty-space jump. */
    private static Arm skip(String name, int mode) {
        return new Arm(name, () -> VolumetricCloudDebugConfig.setEmptySkip(mode), false);
    }

    /** (b) light resolution: production settings on the monolith with the given storm-field consumer bits. */
    private static Arm monoBits(String name, int bits) {
        return new Arm(name, () -> {
            VolumetricCloudDebugConfig.setFinalProgramOverride(CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH);
            VolumetricCloudDebugConfig.setStormFieldForcedOnMonolith(bits);
        }, false);
    }

    /** S26-TEMP: one-body cumulus on lean FINAL (live uniforms). */
    private static Arm body(String name, float support, float threshold, float shell, float coreDepth) {
        return new Arm(name,
                () -> VolumetricCloudDebugConfig.setPuffBody(1, support, threshold, shell, coreDepth), false);
    }

    /** S29-TEMP: extinction multiplier with N near-field light samples. */
    private static Arm ext(String name, float mul, int nearSamples) {
        return new Arm(name, () -> {
            VolumetricCloudDebugConfig.setExtinctionMul(mul);
            VolumetricCloudDebugConfig.setNearLight(nearSamples);
        }, false);
    }

    /** E33: N near-field light samples (overrides the Cloud Surface Detail menu setting). */
    private static Arm near(String name, int samples) {
        return new Arm(name, () -> VolumetricCloudDebugConfig.setNearLight(samples), false);
    }

    /** E33: S26 body with N near-field light samples. */
    private static Arm bodyNear(String name, int mode) {
        return new Arm(name, () -> {
            detail("b", 0.8F, 0.6F).apply().run();
            VolumetricCloudDebugConfig.setNearLight(mode);
        }, false);
    }

    /** S27-TEMP: lighting decomposition arm. */
    private static Arm split(String name, int mode) {
        return new Arm(name, () -> VolumetricCloudDebugConfig.setLightSplit(mode), false);
    }

    /** S26-TEMP: one-body cumulus (relief 1) with turret lattice strength and fringe erosion. */
    private static Arm detail(String name, float turrets, float erode) {
        return new Arm(name, () -> {
            VolumetricCloudDebugConfig.setPuffBody(1, 1.15F, 0.09F, 0.45F, 0.6F);
            VolumetricCloudDebugConfig.setPuffBodyRelief(1.0F);
            VolumetricCloudDebugConfig.setPuffBodyDetail(turrets, erode);
        }, false);
    }

    /** S26-TEMP: one-body cumulus with a cauliflower relief strength. */
    private static Arm relief(String name, float strength) {
        return new Arm(name, () -> {
            VolumetricCloudDebugConfig.setPuffBody(1, 1.15F, 0.09F, 0.45F, 0.6F);
            VolumetricCloudDebugConfig.setPuffBodyRelief(strength);
        }, false);
    }

    /** S13: smooth lobe union width k and lobe warp on lean FINAL. */
    private static Arm blend(String name, float k, float warp) {
        return new Arm(name, () -> {
            VolumetricCloudDebugConfig.setPuffBlend(k);
            VolumetricCloudDebugConfig.setPuffWarp(warp);
        }, false);
    }

    /** S12b: lobe warp + S9 hierarchy, on the monolith (the hierarchy is baked in FINAL). */
    private static Arm warpHier(String name, float warp, float hierarchy) {
        return new Arm(name, () -> {
            monoBits(name, StormFieldGeometry.PRODUCTION_CONSUMER_BITS).apply().run();
            VolumetricCloudDebugConfig.setPuffWarp(warp);
            VolumetricCloudDebugConfig.setShapeHierarchy(hierarchy);
        }, false);
    }

    /** S12: cumulus lobe warp amplitude. */
    private static Arm warp(String name, float amplitude) {
        return new Arm(name, () -> VolumetricCloudDebugConfig.setPuffWarp(amplitude), false);
    }

    /** L6: a Storm Shadow Detail level on lean FINAL (overrides the menu setting). */
    private static Arm shadow(String name, int taps) {
        return new Arm(name, () -> VolumetricCloudDebugConfig.setExactLightTaps(taps), false);
    }

    /** L5: exact light taps over storms, on the monolith (production field bits). */
    private static Arm exactTaps(String name, int taps) {
        return new Arm(name, () -> {
            monoBits(name, StormFieldGeometry.PRODUCTION_CONSUMER_BITS).apply().run();
            VolumetricCloudDebugConfig.setExactLightTaps(taps);
        }, false);
    }

    /** P2: field atlas bands per frame. */
    private static Arm slices(String name, int slices) {
        return new Arm(name, () -> VolumetricCloudDebugConfig.setFieldSlices(slices), false);
    }

    /** PERF-TEMP: attribution arm (see PaPerfArm). */
    private static Arm perf(String name, int arm) {
        return new Arm(name, () -> VolumetricCloudDebugConfig.setPerfArm(arm), false);
    }

    /** P1: the per-step puff occupancy reject on / off. */
    private static Arm occupancy(String name, int mode) {
        return new Arm(name, () -> VolumetricCloudDebugConfig.setPuffOccupancy(mode), false);
    }

    /** E20: the accepted S11 storm in production (lean FINAL) with storm material fix level {@code fix}. */
    private static Arm lit(String name, int fix) {
        return new Arm(name, () -> {
            VolumetricCloudDebugConfig.setStormStructure(15);
            VolumetricCloudDebugConfig.setStormMaterialFix(fix);
        }, false);
    }

    /** E20: {@link #diag} with storm material fix level {@code fix}. */
    private static Arm diagFix(String name, int mode, int fix) {
        Arm base = diag(name, mode);
        return new Arm(name, () -> {
            base.apply().run();
            VolumetricCloudDebugConfig.setStormMaterialFix(fix);
        }, false);
    }

    /** A plan entry "type#n" spawns another instance (its own random seed) of "type"; the file keeps "type#n". */
    private static String typeName(int t) {
        String entry = plan.types().get(t);
        int cut = entry.length();
        if (entry.indexOf('#') >= 0) cut = Math.min(cut, entry.indexOf('#'));
        if (entry.indexOf('*') >= 0) cut = Math.min(cut, entry.indexOf('*'));
        return entry.substring(0, cut);
    }

    /** Perf: a plan entry "type*N" spawns N instances on a square grid (gridSpacing() apart) around its site. */
    private static int gridCount(int t) {
        String entry = plan.types().get(t);
        int star = entry.indexOf('*');
        return star < 0 ? 1 : Integer.parseInt(entry.substring(star + 1));
    }

    private static double gridSpacing() {
        return SET.startsWith("p8") || SET.startsWith("p9") ? 300.0D : 650.0D; // p8: a dense cumulus field (user target ~100 in 1-2 km)
    }

    /** Arms always end with the production values restored by {@link #restoreProduction()}. */
    private static Plan plan(String set) {
        return switch (set) {
            // S0 + S1 + S2: legacy vs colour-managed vs colour-managed without octave normalisation, and the legacy
            // image with the governor pinned at 1.0 (the configuration every pre-S0 campaign reviewed).
            case "s1" -> new Plan(
                    List.of("cumulonimbus_calvus", "supercell", "cumulus_mediocris"),
                    List.of("side", "below", "anvil", "far"),
                    List.of("side", "below", "close"),
                    new int[] {6000, 1500},
                    List.of(arm("legacy", 0, 1, false), arm("legacy_pin100", 0, 1, true),
                            arm("cm", 1, 1, false), arm("cm_noctnorm", 1, 0, false)));
            // S1b / S2 / S4 on top of the colour-managed pipeline (log E3).
            case "s2" -> new Plan(
                    List.of("cumulonimbus_calvus", "supercell", "cumulus_mediocris"),
                    List.of("side", "below", "anvil"),
                    List.of("side", "close"),
                    new int[] {6000, 1500},
                    List.of(arm("legacy", 0, 1, 0, 0, false), arm("cm", 1, 1, 0, 0, false),
                            arm("cm_at", 1, 1, 0, 1, false), arm("cm_at_mx", 1, 0, 0, 1, false)));
                            // E3 also ran arm cm_at_mx_nf (+ the S4 near-field tap, rejected and removed)
            // Confirmation of the S1 + S1b + S2 candidate (with the S1b rain-cap fix) at noon, low sun, sunset, night.
            case "s3" -> new Plan(
                    List.of("cumulonimbus_calvus", "supercell", "cumulus_mediocris"),
                    List.of("side", "below", "anvil"),
                    List.of("side", "close"),
                    new int[] {6000, 1500, 12300, 18000},
                    List.of(arm("legacy", 0, 1, 0, 0, false), arm("cand", 1, 0, 0, 1, false)));
            // Review gallery for the user (log E6): every production family, legacy vs the candidate.
            case "s4" -> new Plan(
                    List.of("cumulonimbus_calvus", "supercell", "cumulus_humilis", "cumulus_mediocris", "cumulus_congestus",
                            "stratus_nebulosus", "stratocumulus", "nimbostratus", "cirrus"),
                    List.of("side", "below", "anvil"),
                    List.of("side", "below", "close"),
                    new int[] {6000, 1500},
                    List.of(arm("legacy", 0, 1, 0, 0, false), arm("cand", 1, 0, 0, 1, false)));
            // S9 shape root cause: the cumulus billow hierarchy at two amplitudes, on the candidate lighting.
            case "s9" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_humilis", "cumulus_congestus"),
                    List.of(),
                    List.of("side", "close", "below"),
                    new int[] {6000, 1500},
                    List.of(arm("cand", 1, 0, 0, 1, false), shape("cand_h1", 1.0F), shape("cand_h2", 2.0F)));
            // Codex T2 (log E9): is the box-shaped dark slab in cumulus a light-field artefact? FINAL vs the monolith with
            // production field bits (43) vs the monolith without the field LIGHT consumer (42), plus the od view (mode 9).
            case "t2" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus"),
                    List.of(),
                    List.of("side", "close"),
                    new int[] {1500},
                    List.of(arm("cand", 1, 0, 0, 1, false), mono("mono43", 43, 0), mono("mono42", 42, 0),
                            mono("mono43_od", 43, 9), mono("mono42_od", 42, 9)));
            // E10: the field light coverage fix on FINAL, cumulus (where the boxes were found) and a storm (regression).
            case "t2b" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus", "stratocumulus", "supercell"),
                    List.of("side", "below"),
                    List.of("side", "close"),
                    new int[] {1500, 6000},
                    List.of(arm("cand", 1, 0, 0, 1, false), fix("cand_fix", 0.0F), fix("cand_fix_h1", 1.0F)));
            // E11: the exact no-storm coverage rule, on the clouds that showed the box / residual line.
            case "t2c" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus"),
                    List.of(),
                    List.of("side", "close"),
                    new int[] {1500},
                    List.of(arm("cand", 1, 0, 0, 1, false), fix("cand_fix", 0.0F), mono("mono42", 42, 0)));
            // Codex T5 (log E12): which candidate term brightens the nimbostratus underside? One switch at a time.
            case "t5" -> new Plan(
                    List.of("nimbostratus", "cumulus_mediocris"),
                    List.of(),
                    List.of("below", "side"),
                    new int[] {6000, 1500},
                    List.of(arm("legacy", 0, 1, 0, 0, false), arm("cm", 1, 1, 0, 0, false),
                            arm("cm_at", 1, 1, 0, 1, false), arm("cm_mx", 1, 0, 0, 0, false),
                            arm("cand", 1, 0, 0, 1, false)));
            // E12: optical-thickness gain for dark / thick families on the candidate lighting.
            case "t6" -> new Plan(
                    List.of("nimbostratus", "supercell", "cumulus_mediocris"),
                    List.of("side", "below", "anvil"),
                    List.of("below", "side"),
                    new int[] {6000, 1500},
                    List.of(arm("legacy", 0, 1, 0, 0, false), arm("cand", 1, 0, 0, 1, false),
                            thick("cand_g4", 4.0F), thick("cand_g10", 10.0F)));
            // E13: storm material from the walk (+ the E12 thickness gain) on both storm types.
            case "t7" -> new Plan(
                    List.of("supercell", "cumulonimbus_calvus"),
                    List.of("side", "below", "anvil"),
                    List.of(),
                    new int[] {6000, 1500},
                    List.of(arm("legacy", 0, 1, 0, 0, false), arm("cand", 1, 0, 0, 1, false),
                            storm("cand_sm", 0.0F), storm("cand_sm_g4", 4.0F)));
            // E14 review gallery for the user: every family, legacy vs everything found so far (candidate lighting, field
            // coverage fix [default on], storm material fix, optical-thickness gain 2 and 4).
            case "s10" -> new Plan(
                    List.of("cumulonimbus_calvus", "supercell", "cumulus_humilis", "cumulus_mediocris", "cumulus_congestus",
                            "stratus_nebulosus", "stratocumulus", "nimbostratus", "cirrus"),
                    List.of("side", "below", "anvil"),
                    List.of("side", "below", "close"),
                    new int[] {6000, 1500},
                    List.of(arm("legacy", 0, 1, 0, 0, false), storm("full_g2", 2.0F), storm("full_g4", 4.0F)));
            // S11: seeded storm structure vs production, several instances of each storm type (variety).
            case "s11" -> new Plan(
                    List.of("cumulonimbus_calvus#1", "cumulonimbus_calvus#2", "supercell#1", "supercell#2",
                            "supercell#3"),
                    List.of("side", "far", "below", "anvil"),
                    List.of(),
                    new int[] {6000},
                    List.of(structure("prod", 0), structure("struct", 15)));
            // L-TEMP lighting investigation: what the storm lighting receives (modes 40 / 41, 9 = od before the gain).
            case "l1" -> new Plan(
                    List.of("supercell#1", "cumulonimbus_calvus#1"),
                    List.of("side", "below"),
                    List.of(),
                    new int[] {6000, 1500},
                    List.of(diag("img", 0), diag("m40", 40), diag("m41", 41), diag("m9", 9)));
            // Perf 3: does cost scale with the number of clouds? 1 / 16 / 49 cumulus mediocris on a 650-block grid.
            case "p3" -> new Plan(
                    List.of("cumulus_mediocris*1", "cumulus_mediocris*16", "cumulus_mediocris*49"),
                    List.of(),
                    List.of("side", "field_low", "field_high"),
                    new int[] {6000},
                    List.of(structure("prod", 0)));
            // Seam hunt 2: more instances (seams were seed-dependent), every toggle in one session.
            // s51: the vertical light stripes on cumulus bodies - production, every light tap exact (no shared
            // field), near light off, and the monolith (its own field build).
            case "s51" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus"),
                    List.of(),
                    List.of("close"),
                    new int[] {1500, 6000},
                    List.of(new Arm("prod", () -> { }, false), shadow("exact8", 8), near("nearoff", 0),
                            diag("mono", 0), diag("lightpath", 2)));
            // s50 (E45): hierarchical one-body cumulus - production (lobe union) vs the S26 body without heads vs
            // with heads at strength 1 / 1.5. Full frames, normal FOV, close + side, morning.
            case "s50" -> new Plan(
                    List.of("cumulus_humilis", "cumulus_mediocris", "cumulus_congestus"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {1500},
                    List.of(new Arm("prod", () -> { }, false), detail("heads1", 1.0F, 0.6F),
                            detail("dense1", 2.0F, 0.6F), detail("dense15", 2.5F, 0.6F)));
            // s49: full-frame density / near-light sweep for the user: x1 (0.115) without and with near light, x1.5,
            // x2, x3 (production); E36/E39/E41 stay on in every arm.
            case "s49" -> new Plan(
                    List.of("cumulus_humilis", "cumulus_mediocris", "cumulus_congestus", "stratocumulus", "nimbostratus"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {1500},
                    List.of(ext("x1nonear", 1.0F / 3.0F, 0), ext("x1", 1.0F / 3.0F, 1), ext("x15", 0.5F, 1),
                            ext("x2", 2.0F / 3.0F, 1), new Arm("x3", () -> { }, false)));
            // s48: honest full-frame evaluation at the normal FOV - the pre-E33 look (E33-E41 switched off as far as
            // switches reach; detail mips stay), production, production + S33 sharp heads, and production with the
            // two T209 upscalers (pixelation).
            case "s48" -> new Plan(
                    List.of("cumulus_humilis", "cumulus_mediocris", "cumulus_congestus", "stratocumulus"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {1500},
                    List.of(new Arm("old", () -> {
                                VolumetricCloudDebugConfig.setExtinctionMul(1.0F / 3.0F);
                                VolumetricCloudDebugConfig.setNearLight(0);
                                VolumetricCloudDebugConfig.setPhaseAccumulate(0, 0.125F, 2.0F);
                                VolumetricCloudDebugConfig.setMotionHistoryWeight(0.0F);
                            }, false),
                            new Arm("prod", () -> { }, false),
                            new Arm("heads", () -> { VolumetricCloudDebugConfig.setPuffBubbles(3.0F);
                                VolumetricCloudDebugConfig.setPuffBubbleSharp(1.0F); }, false),
                            new Arm("easu", () -> VolumetricCloudDebugConfig.setSpatialUpscaleMethod(1), false),
                            new Arm("catmull", () -> VolumetricCloudDebugConfig.setSpatialUpscaleMethod(2), false)));
            // s47 (S33-TEMP): lobe-surface cauliflower creases 0 / 1 / 2 on humilis, mediocris, congestus.
            case "s47" -> new Plan(
                    List.of("cumulus_humilis", "cumulus_mediocris", "cumulus_congestus"),
                    List.of(),
                    List.of("zoom", "close"),
                    new int[] {1500},
                    List.of(new Arm("prod", () -> { }, false),
                            new Arm("bub2", () -> VolumetricCloudDebugConfig.setPuffBubbles(2.0F), false),
                            new Arm("sharp2", () -> { VolumetricCloudDebugConfig.setPuffBubbles(2.0F);
                                VolumetricCloudDebugConfig.setPuffBubbleSharp(1.0F); }, false),
                            new Arm("sharp3", () -> { VolumetricCloudDebugConfig.setPuffBubbles(3.0F);
                                VolumetricCloudDebugConfig.setPuffBubbleSharp(1.0F); }, false)));
            // s46 (E39): the accumulation under camera motion - accumulation off vs on (production), panning slowly
            // and fast at 30 degrees FOV, plus the static zoom for reference.
            case "s46" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus"),
                    List.of(),
                    List.of("zoom", "pan", "panfast"),
                    new int[] {1500},
                    List.of(new Arm("accoff", () -> VolumetricCloudDebugConfig.setPhaseAccumulate(0, 0.125F, 2.0F), false),
                            new Arm("nomotion", () -> VolumetricCloudDebugConfig.setMotionHistoryWeight(0.0F), false),
                            new Arm("prod", () -> { }, false),
                            new Arm("m09", () -> VolumetricCloudDebugConfig.setMotionHistoryWeight(0.9F), false)));
            // s45 (E39): per-phase accumulation vs production raw phases, telephoto + close.
            case "s45" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus", "stratocumulus"),
                    List.of(),
                    List.of("zoom", "close"),
                    new int[] {1500},
                    List.of(new Arm("prod", () -> { }, false),
                            new Arm("acc8", () -> VolumetricCloudDebugConfig.setPhaseAccumulate(1, 0.125F, 0.5F), false),
                            new Arm("acc16", () -> VolumetricCloudDebugConfig.setPhaseAccumulate(1, 0.0625F, 0.5F), false),
                            new Arm("step05", () -> VolumetricCloudDebugConfig.setFineStepMul(0.5F), false)));
            // s44: near light without detail noise (E37) vs near light off, telephoto + close, plus side for cost.
            case "s44" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus", "stratocumulus"),
                    List.of(),
                    List.of("zoom", "close"),
                    new int[] {1500},
                    List.of(new Arm("prod", () -> { }, false),
                            new Arm("nearoff", () -> VolumetricCloudDebugConfig.setNearLight(0), false)));
            // s43 (S32-TEMP): grain attribution - no blue noise in the march at all, near light off, flat colour.
            case "s43" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus"),
                    List.of(),
                    List.of("zoom"),
                    new int[] {1500},
                    List.of(new Arm("prod", () -> { }, false),
                            new Arm("nojitter", () -> VolumetricCloudDebugConfig.setIntegrationPhase(2.0F), false),
                            new Arm("nearoff", () -> VolumetricCloudDebugConfig.setNearLight(0), false),
                            diag("flat", 1), diag("density", 3)));
            // s42 (S32-TEMP): fixed surface-bracket integration phase (0.5 / 1.0) vs production and the half step.
            case "s42" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus"),
                    List.of(),
                    List.of("zoom", "close"),
                    new int[] {1500},
                    List.of(new Arm("prod", () -> { }, false),
                            new Arm("phase05", () -> VolumetricCloudDebugConfig.setIntegrationPhase(0.5F), false),
                            new Arm("phase10", () -> VolumetricCloudDebugConfig.setIntegrationPhase(1.0F), false),
                            new Arm("step05", () -> VolumetricCloudDebugConfig.setFineStepMul(0.5F), false)));
            // s41 (S31-TEMP): the detail noise filtered to the march step (0 = production, 0.5, 1) vs the half step.
            case "s41" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus"),
                    List.of(),
                    List.of("zoom", "close"),
                    new int[] {1500},
                    List.of(new Arm("prod", () -> { }, false),
                            new Arm("dsf05", () -> VolumetricCloudDebugConfig.setDetailStepFilter(0.5F), false),
                            new Arm("dsf10", () -> VolumetricCloudDebugConfig.setDetailStepFilter(1.0F), false),
                            new Arm("step05", () -> VolumetricCloudDebugConfig.setFineStepMul(0.5F), false)));
            // s40 (S30-TEMP): the pixel grain vs the fine step - production, half step, quarter step, and the old
            // extinction (x1/3) at the production step. Telephoto + close.
            case "s40" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus"),
                    List.of(),
                    List.of("zoom", "close"),
                    new int[] {1500},
                    List.of(new Arm("prod", () -> { }, false),
                            new Arm("step05", () -> VolumetricCloudDebugConfig.setFineStepMul(0.5F), false),
                            new Arm("step025", () -> VolumetricCloudDebugConfig.setFineStepMul(0.25F), false),
                            new Arm("oldext", () -> VolumetricCloudDebugConfig.setExtinctionMul(1.0F / 3.0F), false)));
            // s39: S9 cloud-relative billow hierarchy re-tested under the E33-E36 lighting (monolith: the hierarchy
            // is baked off in FINAL). mono = monolith baseline.
            case "s39" -> new Plan(
                    List.of("cumulus_humilis", "cumulus_mediocris", "cumulus_congestus"),
                    List.of(),
                    List.of("zoom", "close"),
                    new int[] {1500},
                    List.of(warpHier("mono", 0.0F, 0.0F), warpHier("hier1", 0.0F, 1.0F), warpHier("hier2", 0.0F, 2.0F)));
            // s38: telephoto (30-degree) views of every non-storm family for the flatness metric.
            case "s38" -> new Plan(
                    List.of("cumulus_humilis", "cumulus_humilis#2", "cumulus_mediocris", "cumulus_congestus",
                            "stratus_nebulosus", "stratocumulus", "nimbostratus", "cirrus"),
                    List.of(),
                    List.of("zoom"),
                    new int[] {1500},
                    List.of(new Arm("prod", () -> { }, false)));
            // s37: production (E33-E36 stack) on every non-storm family, two instances each, for the flatness
            // metric against the reference photographs.
            case "s37" -> new Plan(
                    List.of("cumulus_humilis", "cumulus_humilis#2", "cumulus_mediocris", "cumulus_mediocris#2",
                            "cumulus_congestus", "cumulus_congestus#2", "stratus_nebulosus", "stratocumulus",
                            "nimbostratus", "cirrus"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {1500, 6000},
                    List.of(new Arm("prod", () -> { }, false)));
            // s36: where does the checkerboard speckle on lit faces come from? temporal lattice off (one-frame
            // path), history off, full resolution + one-frame, the density stage without detail erosion.
            case "s36" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus"),
                    List.of(),
                    List.of("close"),
                    new int[] {1500},
                    List.of(new Arm("prod", () -> { }, false),
                            new Arm("temporal0", () -> VolumetricCloudDebugConfig.setTemporalMode(0), false),
                            new Arm("nohistory", () -> VolumetricCloudDebugConfig.setHistoryEnabled(false), false),
                            new Arm("res100t0", () -> {
                                VolumetricCloudDebugConfig.setTemporalMode(0);
                                VolumetricCloudDebugConfig.setFixedResolutionScale(1.0F);
                            }, false),
                            new Arm("jitter1", () -> VolumetricCloudDebugConfig.setOriginJitterMode(1), false),
                            new Arm("preerosion", () -> VolumetricCloudDebugConfig.setPuffDensityStage(
                                    VolumetricPuffDensityStage.PRE_EROSION), false)));
            // s34 (S29-TEMP): the same extinction multipliers on storms (separate client: storm fixtures).
            case "s34" -> new Plan(
                    List.of("supercell#1", "cumulonimbus_calvus#1"),
                    List.of("side", "below", "far"),
                    List.of(),
                    new int[] {6000, 1500},
                    List.of(near("ext1", 1), ext("ext2", 2.0F, 1), ext("ext3", 3.0F, 1)));
            // s35 (S29-TEMP): x2 / x3 on every non-storm family, close + side, morning and noon.
            case "s35" -> new Plan(
                    List.of("cumulus_humilis", "cumulus_mediocris", "cumulus_congestus", "stratus_nebulosus",
                            "stratocumulus", "nimbostratus", "cirrus"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {6000, 1500},
                    List.of(near("ext1", 1), ext("ext2", 2.0F, 1), ext("ext3", 3.0F, 1)));
            // s33 (S29-TEMP): is the fine scale lost because bumps are too few mean free paths thick? Extinction x1 / x2
            // / x4 with Cloud Surface Detail Low, and x2 with High.
            case "s33" -> new Plan(
                    List.of("cumulus_congestus", "cumulus_mediocris", "stratocumulus"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {1500},
                    List.of(near("ext1", 1), ext("ext2", 2.0F, 1), ext("ext4", 4.0F, 1), ext("ext2high", 2.0F, 3)));
            // E33 Cloud Surface Detail on every family (lean FINAL, in-game cost): Off / Low / Medium / High.
            case "s31" -> new Plan(
                    List.of("cumulus_humilis", "cumulus_mediocris", "cumulus_congestus", "stratus_nebulosus",
                            "stratocumulus", "nimbostratus", "cirrus"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {6000, 1500},
                    List.of(near("off", 0), near("low", 1), near("medium", 2), near("high", 3)));
            // E33 on storms (separate client: a long teleport loses the storm fixture).
            case "s32" -> new Plan(
                    List.of("supercell#1", "cumulonimbus_calvus#1"),
                    List.of("side", "below", "far"),
                    List.of(),
                    new int[] {6000, 1500},
                    List.of(near("off", 0), near("low", 1), near("medium", 2), near("high", 3)));
            // s30 (S28-TEMP): near-field light with 3 / 2 / 1 exact samples, on the S26 body (cost vs form).
            case "s30" -> new Plan(
                    List.of("cumulus_congestus#v1", "cumulus_mediocris#v2"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {6000, 1500},
                    List.of(detail("body", 0.8F, 0.6F), bodyNear("near3", 3), bodyNear("near2", 2),
                            bodyNear("near1", 1)));
            // s29 (S28-TEMP): does near-field light (0-12 blocks toward the sun) give surfaces their form? Lobe
            // union and the S26 body, noon and morning.
            case "s29" -> new Plan(
                    List.of("cumulus_congestus#v1", "cumulus_mediocris#v2"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {6000, 1500},
                    List.of(new Arm("prod", () -> { }, false),
                            new Arm("near", () -> VolumetricCloudDebugConfig.setNearLight(3), false),
                            detail("body", 0.8F, 0.6F),
                            new Arm("bodynear", () -> {
                                detail("b", 0.8F, 0.6F).apply().run();
                                VolumetricCloudDebugConfig.setNearLight(3);
                            }, false)));
            // s28 (S27-TEMP): which lighting term flattens the surface - sun only, ambient only, one octave, no
            // powder - at noon and in the morning.
            case "s28" -> new Plan(
                    List.of("cumulus_congestus#v1", "cumulus_mediocris#v2"),
                    List.of(),
                    List.of("close"),
                    new int[] {6000, 1500},
                    List.of(new Arm("prod", () -> { }, false),
                            split("sunonly", 1), split("ambonly", 2), split("oct1", 3), split("nopowder", 4)));
            // s27: why are cloud surfaces flat (every family)? render resolution (prod 0.25 / 0.5 / 1.0), exact light
            // taps at full resolution, and the density alone at full resolution (is the detail there at all?).
            case "s27" -> new Plan(
                    List.of("cumulus_congestus#v1", "cumulus_mediocris#v2", "supercell#1"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {6000},
                    List.of(new Arm("prod", () -> { }, false),
                            new Arm("res50", () -> VolumetricCloudDebugConfig.setFixedResolutionScale(0.5F), false),
                            new Arm("res100", () -> VolumetricCloudDebugConfig.setFixedResolutionScale(1.0F), false),
                            new Arm("res100exact", () -> {
                                VolumetricCloudDebugConfig.setFixedResolutionScale(1.0F);
                                VolumetricCloudDebugConfig.setExactLightTaps(8);
                            }, false),
                            new Arm("res100density", () -> {
                                diag("m3", 3).apply().run();
                                VolumetricCloudDebugConfig.setFixedResolutionScale(1.0F);
                            }, false)));
            // S26-TEMP: one-body cumulus vs the lobe union, same instances; body arms vary union width / shell.
            case "s26" -> new Plan(
                    List.of("cumulus_congestus#v1", "cumulus_congestus#v2", "cumulus_congestus#v2b",
                            "cumulus_mediocris#v2", "cumulus_humilis#v2"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {6000},
                    List.of(new Arm("prod", () -> { }, false),
                            detail("tur08", 0.8F, 0.0F), detail("tur08ero", 0.8F, 0.6F),
                            detail("tur12ero", 1.2F, 0.6F)));
            // S25-TEMP: which light taps draw the cumulus rings - prod (tap 0 exact, rest field), tap 0 on the field
            // like storms, every tap exact. Lean FINAL (live uniforms), noon.
            case "s25" -> new Plan(
                    List.of("cumulus_congestus#v1", "cumulus_congestus#v2", "cumulus_congestus#v2b",
                            "cumulus_mediocris#v2"),
                    List.of(),
                    List.of("close"),
                    new int[] {6000},
                    List.of(new Arm("prod", () -> { }, false),
                            new Arm("tap0field", () -> VolumetricCloudDebugConfig.setLightTap0Field(1), false),
                            shadow("allexact", 8)));
            // s24: are the congestus rings geometry or lighting? flat colour (1), local density (3), light path (2).
            case "s24" -> new Plan(
                    List.of("cumulus_congestus#v1", "cumulus_congestus#v2", "cumulus_congestus#v2b"),
                    List.of(),
                    List.of("close"),
                    new int[] {6000},
                    List.of(new Arm("prod", () -> { }, false), diag("m0_mono", 0), diag("m1_flat", 1), diag("m3_density", 3),
                            diag("m2_lightpath", 2)));
            // s23: congestus tower without overhangs (v2) vs tiered (v1), noon and low sun.
            case "s23" -> new Plan(
                    List.of("cumulus_congestus#v1", "cumulus_congestus#v2", "cumulus_congestus#v2b",
                            "cumulus_congestus#v2c", "cumulus_congestus#v2d"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {6000, 1500},
                    List.of(new Arm("prod", () -> { }, false)));
            // S20 (rejected): was the 16-vs-8 candidate A/B; kept as a congestus seam set on production.
            case "s21" -> new Plan(
                    List.of("cumulus_congestus#v1", "cumulus_congestus#v2", "cumulus_congestus#v2b",
                            "cumulus_congestus#v2c"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {6000},
                    List.of(new Arm("prod", () -> { }, false)));
            // S20: 16 candidates per puff tile - the s19 seam instances on production, close + side.
            case "s20" -> new Plan(
                    List.of("cumulus_congestus#v1", "cumulus_congestus#v1b", "cumulus_congestus#v2",
                            "cumulus_congestus#v2b", "cumulus_congestus#v2c", "cumulus_mediocris#v2"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {6000},
                    List.of(new Arm("prod", () -> { }, false)));
            case "s19" -> new Plan(
                    List.of("cumulus_congestus#v1", "cumulus_congestus#v1b", "cumulus_congestus#v2",
                            "cumulus_congestus#v2b", "cumulus_congestus#v2c", "cumulus_mediocris#v2"),
                    List.of(),
                    List.of("close"),
                    new int[] {6000},
                    List.of(new Arm("prod", () -> { }, false), slices("bands1", 1),
                            monoBits("mono", StormFieldGeometry.PRODUCTION_CONSUMER_BITS),
                            monoBits("exactlight", StormFieldGeometry.PRODUCTION_CONSUMER_BITS & ~1),
                            new Arm("noskip", () -> {
                                monoBits("noskip", StormFieldGeometry.PRODUCTION_CONSUMER_BITS).apply().run();
                                VolumetricCloudDebugConfig.setPuffOccupancy(0);
                                VolumetricCloudDebugConfig.setEmptySkip(0);
                            }, false)));
            // Seam hunt: field bands (P2) and field light, same instances.
            case "s18" -> new Plan(
                    List.of("cumulus_congestus#v1", "cumulus_congestus#v2", "cumulus_mediocris#v2"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {6000},
                    List.of(new Arm("prod", () -> { }, false), slices("bands1", 1),
                            monoBits("mono", StormFieldGeometry.PRODUCTION_CONSUMER_BITS),
                            monoBits("exactlight", StormFieldGeometry.PRODUCTION_CONSUMER_BITS & ~1)));
            // S14 congestus iteration: v1 vs three v2 instances, plus a v2 mediocris control.
            case "s17d" -> new Plan(
                    List.of("cumulus_congestus#v1", "cumulus_congestus#v2", "cumulus_congestus#v2b",
                            "cumulus_congestus#v2c", "cumulus_mediocris#v2"),
                    List.of(),
                    List.of("side", "close"),
                    new int[] {6000},
                    List.of(new Arm("prod", () -> { }, false)));
            // S14: cumulus layout v1 (tiered) vs v2 (clustered), two instances each, production renderer.
            case "s17" -> new Plan(
                    List.of("cumulus_humilis#v1", "cumulus_humilis#v2", "cumulus_mediocris#v1", "cumulus_mediocris#v2",
                            "cumulus_mediocris#v2b", "cumulus_congestus#v1", "cumulus_congestus#v2",
                            "cumulus_congestus#v2b"),
                    List.of(),
                    List.of("side", "close", "below"),
                    new int[] {6000, 1500},
                    List.of(new Arm("prod", () -> { }, false)));
            // Shelf check: hybrid (production; weather-map shape outside lobe tiles) vs direct lobes only vs the
            // weather-map fallback only, same instances.
            case "s16" -> new Plan(
                    List.of("cumulus_congestus#1", "cumulus_congestus#2", "cumulus_mediocris#1", "cumulus_mediocris#2"),
                    List.of(),
                    List.of("close", "side", "below"),
                    new int[] {6000},
                    List.of(new Arm("hybrid", () -> { }, false),
                            new Arm("direct", () -> VolumetricCloudDebugConfig.setPuffShapeMode(VolumetricPuffShapeMode.DIRECT_ONLY), false),
                            new Arm("fallback", () -> VolumetricCloudDebugConfig.setPuffShapeMode(VolumetricPuffShapeMode.FALLBACK_ONLY), false)));
            // Box hunt: 6 congestus, every arm in one session (same instances): production (lean), monolith baseline,
            // exact light taps, direct puffs only, no occupancy / jump.
            case "s15" -> new Plan(
                    List.of("cumulus_congestus#1", "cumulus_congestus#2", "cumulus_congestus#3",
                            "cumulus_congestus#4", "cumulus_congestus#5", "cumulus_congestus#6"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {6000},
                    List.of(new Arm("prod", () -> { }, false),
                            monoBits("mono", StormFieldGeometry.PRODUCTION_CONSUMER_BITS),
                            monoBits("exactlight", StormFieldGeometry.PRODUCTION_CONSUMER_BITS & ~1),
                            new Arm("puffonly", () -> {
                                monoBits("puffonly", StormFieldGeometry.PRODUCTION_CONSUMER_BITS).apply().run();
                                VolumetricCloudDebugConfig.setPerfArm(5);
                            }, false),
                            new Arm("noskip", () -> {
                                monoBits("noskip", StormFieldGeometry.PRODUCTION_CONSUMER_BITS).apply().run();
                                VolumetricCloudDebugConfig.setPuffOccupancy(0);
                                VolumetricCloudDebugConfig.setEmptySkip(0);
                            }, false)));
            // Seam check: congestus with the cumulus-structure path (production) vs direct puffs only (PERF-TEMP 5).
            case "s14" -> new Plan(
                    List.of("cumulus_congestus#1", "cumulus_congestus#2", "cumulus_mediocris"),
                    List.of(),
                    List.of("side", "close"),
                    new int[] {6000},
                    List.of(monoBits("prod", StormFieldGeometry.PRODUCTION_CONSUMER_BITS),
                            new Arm("puffonly", () -> {
                                monoBits("puffonly", StormFieldGeometry.PRODUCTION_CONSUMER_BITS).apply().run();
                                VolumetricCloudDebugConfig.setPerfArm(5);
                            }, false)));
            // S13: smooth lobe union (k) with the lobe warp, on lean FINAL (both live uniforms).
            case "s13" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus#1", "cumulus_humilis"),
                    List.of(),
                    List.of("side", "close", "below"),
                    new int[] {6000},
                    List.of(blend("k0", 0.0F, 0.0F), blend("k15", 0.15F, 0.25F), blend("k30", 0.30F, 0.25F),
                            blend("k50", 0.50F, 0.25F)));
            // S12b: warp + the S9 billow hierarchy under today's lighting (hierarchy is baked 0 in FINAL -> monolith).
            case "s12b" -> new Plan(
                    List.of("cumulus_mediocris", "cumulus_congestus#1"),
                    List.of(),
                    List.of("side", "close"),
                    new int[] {6000},
                    List.of(warpHier("w0h0", 0.0F, 0.0F), warpHier("w25h0", 0.25F, 0.0F),
                            warpHier("w25h1", 0.25F, 1.0F), warpHier("w25h2", 0.25F, 2.0F)));
            // S12: cumulus lobe warp 0 / 0.12 / 0.20 / 0.25 on lean FINAL (live uniform), every puff family.
            case "s12" -> new Plan(
                    List.of("cumulus_humilis", "cumulus_mediocris", "cumulus_congestus#1", "cumulus_congestus#2"),
                    List.of(),
                    List.of("side", "close", "below"),
                    new int[] {6000},
                    List.of(warp("w0", 0.0F), warp("w12", 0.12F), warp("w20", 0.20F), warp("w25", 0.25F)));
            // L6: Storm Shadow Detail levels on lean FINAL (the in-game cost): Fast 0, Balanced 1, High 2, Ultra 8.
            case "l6" -> new Plan(
                    List.of("supercell#1", "cumulonimbus_calvus#1"),
                    List.of("side", "below", "far"),
                    List.of(),
                    new int[] {6000, 1500},
                    List.of(shadow("fast", 0), shadow("balanced", 1), shadow("high", 2), shadow("ultra", 8),
                            shadow("fast2", 0)));
            // L5: exact light taps over storms - 0 (production), first, first two, all (field light bit off).
            case "l5" -> new Plan(
                    List.of("supercell#1", "cumulonimbus_calvus#1"),
                    List.of("side", "below"),
                    List.of(),
                    new int[] {6000, 1500},
                    List.of(exactTaps("t0", 0), exactTaps("t1", 1), exactTaps("t2", 2),
                            monoBits("all", StormFieldGeometry.PRODUCTION_CONSUMER_BITS & ~1)));
            // Box check: the congestus lower block with the P1 occupancy / C2 lists and the P3 jump on vs off.
            case "l4" -> new Plan(
                    List.of("cumulus_congestus#1", "cumulus_congestus#2"),
                    List.of(),
                    List.of("close", "side"),
                    new int[] {6000},
                    List.of(monoBits("on", StormFieldGeometry.PRODUCTION_CONSUMER_BITS),
                            new Arm("off", () -> {
                                monoBits("off", StormFieldGeometry.PRODUCTION_CONSUMER_BITS).apply().run();
                                VolumetricCloudDebugConfig.setPuffOccupancy(0);
                                VolumetricCloudDebugConfig.setEmptySkip(0);
                            }, false)));
            // (b) light resolution: field light taps (bit 1, production) vs exact-density light taps, on the monolith.
            case "l3" -> new Plan(
                    List.of("cumulus_congestus", "supercell#1"),
                    List.of("side", "below"),
                    List.of("side", "close"),
                    new int[] {6000, 1500},
                    List.of(monoBits("field", StormFieldGeometry.PRODUCTION_CONSUMER_BITS),
                            monoBits("exact", StormFieldGeometry.PRODUCTION_CONSUMER_BITS & ~1)));
            // C2: 256 detailed puff lobes - one cloud (look unchanged) and the 100-cumulus field (nearest ~32 detailed).
            case "p9" -> new Plan(
                    List.of("cumulus_mediocris*1", "cumulus_mediocris*100", "supercell#1"),
                    List.of("side", "below"),
                    List.of("side", "close", "field_low", "field_high", "far"),
                    new int[] {6000},
                    List.of(new Arm("prod", () -> { }, false))); // production as configured (lean FINAL)
            // C1 + P3: a 100-cumulus field (300-block grid, ~3 km): capacity and the empty-space jump.
            case "p8" -> new Plan(
                    List.of("cumulus_mediocris*100"),
                    List.of(),
                    List.of("side", "field_low", "field_high", "far"),
                    new int[] {6000},
                    List.of(skip("skip0", 0), skip("skip1", 1)));
            // P3 empty-space jump: off / on, storm + cumulus field + the empty view (image must hold).
            case "p7" -> new Plan(
                    List.of("supercell#1", "cumulus_mediocris*16"),
                    List.of("side", "far", "away", "below"),
                    List.of("side", "field_low", "field_high"),
                    new int[] {6000},
                    List.of(skip("skip0", 0), skip("skip1", 1), skip("skip0b", 0)));
            // P2 field bands: whole / 2 / 4 bands per frame (field ms; image and field audit must hold).
            case "p6" -> new Plan(
                    List.of("supercell#1", "cumulus_mediocris*16"),
                    List.of("side", "below"),
                    List.of("side", "field_low"),
                    new int[] {6000},
                    List.of(slices("band1", 1), slices("band2", 2), slices("band4", 4)));
            // PERF-TEMP attribution on lean FINAL: production, the covered-step ceiling, the tight cumulus bounds.
            case "p5" -> new Plan(
                    List.of("cumulus_mediocris*1", "cumulus_mediocris*49"),
                    List.of(),
                    List.of("side", "field_low", "field_high"),
                    new int[] {6000},
                    List.of(perf("base", 0), perf("nopromo", 3), perf("nopuff", 4), perf("ceiling", 1), perf("base2", 0)));
            // P1 occupancy reject: the p3 cloud grids, legacy loop vs occupancy bits (image must be identical).
            case "p4" -> new Plan(
                    List.of("cumulus_mediocris*1", "cumulus_mediocris*49"),
                    List.of(),
                    List.of("side", "field_low", "field_high"),
                    new int[] {6000},
                    List.of(occupancy("occ0", 0), occupancy("occ1", 1)));
            // Perf 2: full pass timings (march, field build, rain) + counters; "away" = the floor with ~no cloud.
            case "p2" -> new Plan(
                    List.of("supercell#1", "cumulonimbus_calvus#1"),
                    List.of("side", "far", "away", "below"),
                    List.of(),
                    new int[] {6000},
                    List.of(structure("prod", 0), structure("struct", 15)));
            // E20: storm lighting height - fix 1 (weather-map h01, a step at the slab base) vs fix 2 (storm height).
            case "l2" -> new Plan(
                    List.of("supercell#1", "cumulonimbus_calvus#1"),
                    List.of("side", "below", "far"),
                    List.of(),
                    new int[] {6000, 1500},
                    List.of(lit("fix1", 1), lit("fix2", 2), diagFix("fix2_m40", 40, 2)));
            // Perf: S11 parts with the T123 workload counters (steps, density calls, light evaluations) per shot.
            case "p1" -> new Plan(
                    List.of("supercell#1", "supercell#2"),
                    List.of("side", "far"),
                    List.of(),
                    new int[] {6000},
                    List.of(structure("prod", 0), structure("turrets", 1), structure("rim", 2), structure("flank", 4),
                            structure("billows", 8), structure("struct", 15)));
            // S11 cost attribution: each part alone, and all (same build, same poses).
            case "s11c" -> new Plan(
                    List.of("supercell#1", "supercell#2"),
                    List.of("side", "below"),
                    List.of(),
                    new int[] {6000},
                    List.of(structure("prod", 0), structure("turrets", 1), structure("rim", 2), structure("flank", 4),
                            structure("billows", 8), structure("struct", 15)));
            default -> throw new IllegalArgumentException("unknown PA_PROD_CAPTURE_SET " + set);
        };
    }

    private record Shot(int type, String view, int time, Arm arm) {}

    private static Plan plan;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static CloudRegionState[] regions;
    private static double anchorX, anchorZ;
    private static int phase = -1, index, typeSpawned = -1, wait;
    private static long frameMark;
    private static double[] pose;
    private static int savedFov = -1;
    private static float poseYaw;
    private static volatile boolean panActive;
    private static volatile float panRate;
    private static int panFrames;
    private static String lastPoseKey = "";
    private static int lastTime = -1;
    private static float settledStepScale = Float.NaN;
    private static final List<Float> GPU = new ArrayList<>();
    /** Per-frame GPU ms of the passes outside the march timer: storm/rain field build (+ gate), rain pass, lattice. */
    private static final List<Float> FIELD = new ArrayList<>(), RAIN = new ArrayList<>();

    private ProductionCloudCapture() {}

    public static boolean enabled() {
        return ON;
    }

    /** E41: a pan view turns the camera on every rendered frame, like a mouse (degrees per frame ~ per tick / 3). */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.START || !panActive) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        panFrames++;
        float travel = panFrames * panRate;
        float phase = travel % 12.0F; // triangle wave: 0 -> +3 -> -3 -> 0 degrees
        float offset = phase < 3.0F ? phase : phase < 9.0F ? 6.0F - phase : phase - 12.0F;
        float yaw = poseYaw + offset;
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        try {
            tick(mc);
        } catch (RuntimeException failure) {
            ProjectAtmosphere.LOGGER.error("PROD_CAPTURE_FAILED", failure);
            finish(mc);
        }
    }

    private static void tick(Minecraft mc) {
        wait++;
        long frames = VolumetricCloudRenderHook.presentedFrames();
        if (phase == -1) { // setup, once the native renderer runs
            if (!VolumetricCloudRenderHook.isActive() || wait < 100) return;
            plan = plan(SET);
            for (int t = 0; t < plan.types().size(); t++) {
                boolean storm = typeName(t).startsWith("cumulonimbus") || typeName(t).equals("supercell");
                for (String v : storm ? plan.stormViews() : plan.otherViews())
                    for (int time : plan.times())
                        for (Arm a : plan.arms()) SHOTS.add(new Shot(t, v, time, a));
            }
            regions = new CloudRegionState[plan.types().size()];
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1600, 900);
            command(mc, "gamerule sendCommandFeedback false");
            command(mc, "gamemode spectator");
            command(mc, "gamerule doDaylightCycle false");
            command(mc, "weather clear");
            AtmoCommonConfig.FREEZE_CLOUD_MOVEMENT.set(true); // in memory only; restored in finish()
            mc.options.hideGui = true; // no HUD, FPS overlay or toasts in the frames
            anchorX = mc.player.getX();
            anchorZ = mc.player.getZ();
            ProjectAtmosphere.LOGGER.info("PROD_CAPTURE_START set={} shots={} quality={} anchor=({}, {})", SET, SHOTS.size(),
                    AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.get(), fmt(anchorX), fmt(anchorZ));
            phase = 0; wait = 0;
            return;
        }
        if (index >= SHOTS.size()) { finish(mc); return; }
        Shot s = SHOTS.get(index);
        switch (phase) {
            case 0 -> { // spawn the shot's cloud type (once per type)
                if (typeSpawned == s.type()) { phase = 1; wait = 0; return; }
                MinecraftServer server = mc.getSingleplayerServer();
                if (server == null) throw new IllegalStateException("PROD_CAPTURE needs a singleplayer world");
                int t = s.type();
                // Grids (perf p3) need room: 15000 blocks between sites, so one grid is out of render range of the next.
                double x = anchorX + (SET.startsWith("p3") ? 15000.0D : 6000.0D) * (t + 1), z = anchorZ;
                String type = typeName(t);
                int count = gridCount(t);
                int side = (int) Math.ceil(Math.sqrt(count));
                // S14: a "#v1" / "#v2" suffix picks the cumulus layout for this spawn (server-side generator).
                String entry = plan.types().get(t);
                int layout = entry.contains("#v2") ? 2 : entry.contains("#v1") ? 1
                        : CloudGroupSpawner.puffLayout();
                server.execute(() -> {
                    int previousLayout = CloudGroupSpawner.puffLayout();
                    CloudGroupSpawner.setPuffLayout(layout);
                    int spawned = 0;
                    for (int gz = 0; gz < side && spawned < count; gz++) {
                        for (int gx = 0; gx < side && spawned < count; gx++, spawned++) {
                            double px = x + (gx - (side - 1) / 2.0D) * gridSpacing();
                            double pz = z + (gz - (side - 1) / 2.0D) * gridSpacing();
                            CloudRegionState r = CloudGroupSpawner.spawnRequestedCloud(server.overworld(), BlockPos.containing(px, 0.0D, pz), type);
                            // The pose frames the grid's centre cloud (the first one for a single spawn).
                            if (spawned == 0 || (gx == side / 2 && gz == side / 2)) regions[t] = r;
                        }
                    }
                    CloudGroupSpawner.setPuffLayout(previousLayout);
                });
                command(mc, String.format(Locale.ROOT, "tp @s %.1f 300 %.1f", x + 1500.0D, z));
                typeSpawned = t; phase = 5; wait = 0;
            }
            case 5 -> { // let the spawned cloud sync and settle near the player
                if (wait < 20 * 45) return;
                if (regions[s.type()] == null) throw new IllegalStateException("spawn failed for " + plan.types().get(s.type()));
                ProjectAtmosphere.LOGGER.info("PROD_CAPTURE_SPAWN type={} region={}", plan.types().get(s.type()), regions[s.type()].getRegionId());
                // S11-TEMP: the storm skeleton dimensions (remove with S11).
                ProjectAtmosphere.LOGGER.info("PROD_CAPTURE_SKELETON type={}{}", plan.types().get(s.type()), StormBodySkeleton.describeLast());
                phase = 1; wait = 0;
            }
            case 1 -> { // move to the view (only when the pose or time changes)
                String key = s.type() + "|" + s.view();
                if (!key.equals(lastPoseKey)) {
                    VolumetricCloudRenderer.releaseStepScalePin();
                    pose = pose(mc, regions[s.type()], s.view(), typeName(s.type()));
                    // s38: "zoom" = the side pose at a 30-degree field of view (telephoto, as cloud photographs
                    // are taken), so a small cloud fills enough pixels for the flatness metric.
                    if (savedFov < 0) {
                        savedFov = mc.options.fov().get();
                    }
                    mc.options.fov().set("zoom".equals(s.view()) || s.view().startsWith("pan") ? 30 : savedFov);
                    float yaw = (float) Math.toDegrees(Math.atan2(-(pose[3] - pose[0]), pose[5] - pose[2]));
                    poseYaw = yaw;
                    float pitch = (float) -Math.toDegrees(Math.atan2(pose[4] - pose[1], Math.max(Math.hypot(pose[3] - pose[0], pose[5] - pose[2]), 1.0D)));
                    command(mc, String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.3f %.3f", pose[0], pose[1], pose[2], yaw, pitch));
                    lastPoseKey = key; lastTime = -1; settledStepScale = Float.NaN;
                    phase = 2; wait = 0; frameMark = frames;
                    return;
                }
                phase = 3; wait = 0;
            }
            case 2 -> { // arrival + settle with the governor live (it reaches its floor within ~40 slow frames)
                boolean arrived = Math.abs(mc.player.getX() - pose[0]) < 3 && Math.abs(mc.player.getY() - pose[1]) < 3
                        && Math.abs(mc.player.getZ() - pose[2]) < 3;
                if (!arrived && wait < 20 * 120) return;
                if (wait < 20 * 12 || frames - frameMark < 240) return;
                phase = 3; wait = 0;
            }
            case 3 -> { // time of day, then hold the governor at the step scale it settled to
                if (s.time() != lastTime) {
                    command(mc, "time set " + s.time());
                    lastTime = s.time();
                    frameMark = frames; wait = 0;
                    phase = 6;
                    return;
                }
                phase = 4; wait = 0; frameMark = frames;
                applyArm(s);
            }
            case 6 -> { // the light changed: settle again before reading the governor
                // The mod's sky / weather tint keeps changing for several seconds after a teleport or a time change,
                // and the cloud ambient reads the sky colour: settle 10 s (s1 lost its first frame per view to this).
                if (wait < 200 || frames - frameMark < 300) return;
                if (Float.isNaN(settledStepScale)) settledStepScale = VolumetricCloudRenderer.governorStepScale();
                phase = 4; wait = 0; frameMark = frames;
                applyArm(s);
            }
            case 4 -> { // the arm renders: temporal phases + history refill, then shoot
                // E39 / E41: the pan turns every rendered frame (onRenderTick), back and forth within +-3 degrees
                // at a constant speed, restarted for every arm so every arm sees the same sky while moving.
                if (!panActive && s.view().startsWith("pan")) {
                    panFrames = 0;
                }
                panActive = s.view().startsWith("pan");
                panRate = "panfast".equals(s.view()) ? 0.15F : 0.03F;
                if (frames - frameMark >= 16) { // GPU ms per pass, one per tick
                    GPU.add(VolumetricCloudRenderer.lastGpuMilliseconds());
                    FIELD.add(VolumetricCloudRenderer.lastRainFieldGpuMilliseconds());
                    RAIN.add(Math.max(0.0F, VolumetricCloudRenderer.lastRainPassGpuMilliseconds())
                            + Math.max(0.0F, VolumetricCloudRenderer.lastRainLatticeGpuMilliseconds()));
                }
                if (frames - frameMark < 48 || wait < 30) return;
                shoot(mc, s);
                if (SET.startsWith("p")) {
                    // Perf sets: the T123 counter readback (56 diagnostic frames) under the same arm and pose.
                    VolumetricCloudFrameDiagnostics.requestStormWorkloadCapture(s.view());
                    phase = 7;
                    wait = 0;
                    return;
                }
                nextShot(s);
            }
            case 7 -> { // perf sets: wait for the workload readback, then log it next to the shot's GPU time
                if (VolumetricCloudFrameDiagnostics.stormWorkloadActive() && wait < 20 * 60) return;
                String result = VolumetricCloudFrameDiagnostics.stormWorkloadResultLine();
                if (result == null) VolumetricCloudFrameDiagnostics.abortStormWorkloadCapture();
                ProjectAtmosphere.LOGGER.info("PROD_CAPTURE_WORKLOAD name={} {}", shotName(s),
                        result == null ? VolumetricCloudFrameDiagnostics.stormWorkloadLatest() : result);
                nextShot(s);
            }
            default -> { }
        }
    }

    private static void applyArm(Shot s) {
        restoreProduction();
        s.arm().apply().run();
        VolumetricCloudRenderer.pinStepScaleForCaptures(s.arm().pinFull() ? 1.0F
                : (Float.isNaN(settledStepScale) ? VolumetricCloudRenderer.governorStepScale() : settledStepScale));
        VolumetricCloudRenderer.invalidateHistory();
        GPU.clear();
        FIELD.clear();
        RAIN.clear();
    }

    private static void restoreProduction() {
        VolumetricCloudDebugConfig.setColorPipeline(VolumetricCloudDebugConfig.PRODUCTION_COLOR_PIPELINE);
        VolumetricCloudDebugConfig.setOctaveNormalize(VolumetricCloudDebugConfig.PRODUCTION_OCTAVE_NORMALIZE);
        VolumetricCloudDebugConfig.setAmbientTransmission(VolumetricCloudDebugConfig.PRODUCTION_AMBIENT_TRANSMISSION);
        VolumetricCloudDebugConfig.setShapeHierarchy(VolumetricCloudDebugConfig.PRODUCTION_SHAPE_HIERARCHY);
        VolumetricCloudDebugConfig.setFinalProgramOverride(null);
        VolumetricCloudDebugConfig.setStormMaterialFix(VolumetricCloudDebugConfig.PRODUCTION_STORM_MATERIAL_FIX);
        VolumetricCloudDebugConfig.setStormStructure(VolumetricCloudDebugConfig.PRODUCTION_STORM_STRUCTURE);
        VolumetricCloudDebugConfig.setPuffOccupancy(VolumetricCloudDebugConfig.PRODUCTION_PUFF_OCCUPANCY);
        VolumetricCloudDebugConfig.setPerfArm(0);
        VolumetricCloudDebugConfig.setFieldSlices(VolumetricCloudDebugConfig.PRODUCTION_FIELD_SLICES);
        VolumetricCloudDebugConfig.setEmptySkip(VolumetricCloudDebugConfig.PRODUCTION_EMPTY_SKIP);
        VolumetricCloudDebugConfig.setExactLightTaps(VolumetricCloudDebugConfig.PRODUCTION_EXACT_LIGHT_TAPS);
        VolumetricCloudDebugConfig.setPuffWarp(VolumetricCloudDebugConfig.PRODUCTION_PUFF_WARP);
        VolumetricCloudDebugConfig.setPuffBlend(VolumetricCloudDebugConfig.PRODUCTION_PUFF_BLEND);
        VolumetricCloudDebugConfig.setLightTap0Field(0); // S25-TEMP
        VolumetricCloudDebugConfig.setLightSplit(0); // S27-TEMP
        VolumetricCloudDebugConfig.setExtinctionMul(1.0F); // S29-TEMP
        VolumetricCloudDebugConfig.setFineStepMul(1.0F); // S30-TEMP
        VolumetricCloudDebugConfig.setDetailStepFilter(0.0F); // S31-TEMP
        VolumetricCloudDebugConfig.setIntegrationPhase(-1.0F); // S32-TEMP
        VolumetricCloudDebugConfig.setPhaseAccumulate(VolumetricCloudDebugConfig.PRODUCTION_PHASE_ACCUMULATE,
                VolumetricCloudDebugConfig.PRODUCTION_PHASE_ACCUMULATE_MIN_WEIGHT,
                VolumetricCloudDebugConfig.PRODUCTION_PHASE_ACCUMULATE_MAX_DRIFT); // E39
        CloudTemporalPhaseState.invalidateAll(); // E39: an arm never averages into another arm's phases
        VolumetricCloudDebugConfig.setMotionHistoryWeight(VolumetricCloudDebugConfig.PRODUCTION_MOTION_HISTORY_WEIGHT); // E41
        VolumetricCloudDebugConfig.setPuffBubbles(0.0F); // S33-TEMP
        VolumetricCloudDebugConfig.setSpatialUpscaleMethod(VolumetricCloudDebugConfig.PRODUCTION_SPATIAL_UPSCALE); // s48
        VolumetricCloudDebugConfig.setPuffBubbleSharp(0.0F);
        VolumetricCloudDebugConfig.setNearLight(VolumetricCloudDebugConfig.PRODUCTION_NEAR_LIGHT); // E33 menu
        VolumetricCloudDebugConfig.setPuffBody(0, 1.15F, 0.09F, 0.45F, 0.6F); // S26-TEMP
        VolumetricCloudDebugConfig.setPuffBodyRelief(1.0F);
        VolumetricCloudDebugConfig.setPuffBodyDetail(0.0F, 0.0F);
        VolumetricCloudDebugConfig.setPuffShapeMode(VolumetricPuffShapeMode.HYBRID);
        VolumetricCloudDebugConfig.setOpticalThicknessGain(VolumetricCloudDebugConfig.PRODUCTION_OPTICAL_THICKNESS_GAIN);
        VolumetricCloudDebugConfig.setFieldCoverageFix(VolumetricCloudDebugConfig.PRODUCTION_FIELD_COVERAGE_FIX);
        VolumetricCloudDebugConfig.resetStormFieldMonolithBits();
        VolumetricCloudDebugConfig.setDiagnosticLightingMode(0);
        VolumetricCloudDebugConfig.setFixedResolutionScale(Float.NaN); // s27: resolution arms
        VolumetricCloudDebugConfig.setTemporalMode(VolumetricCloudDebugConfig.PRODUCTION_TEMPORAL_MODE); // s36
        VolumetricCloudDebugConfig.setHistoryEnabled(true); // s36
        VolumetricCloudDebugConfig.setPuffDensityStage(VolumetricPuffDensityStage.FINAL); // s36
        VolumetricCloudDebugConfig.setOriginJitterMode(VolumetricCloudDebugConfig.PRODUCTION_ORIGIN_JITTER_MODE); // s36
    }

    private static void nextShot(Shot s) {
        panActive = false;
        index++;
        phase = index < SHOTS.size() && SHOTS.get(index).type() != s.type() ? 0 : 1;
        wait = 0;
    }

    private static String shotName(Shot s) {
        return String.format(Locale.ROOT, "%s__%s__t%d__%s", plan.types().get(s.type()).replace('*', 'x'), s.view(), s.time(), s.arm().name());
    }

    private static void shoot(Minecraft mc, Shot s) {
        String dir = "prodcapture/" + SET;
        new File(mc.gameDirectory, "screenshots/" + dir).mkdirs();
        String name = String.format(Locale.ROOT, "%s__%s__t%d__%s", plan.types().get(s.type()).replace('*', 'x'), s.view(), s.time(), s.arm().name());
        mc.gui.getChat().clearMessages(false);
        Screenshot.grab(mc.gameDirectory, dir + "/" + name + ".png", mc.getMainRenderTarget(), msg -> { });
        List<Float> gpu = new ArrayList<>(GPU);
        gpu.sort(null);
        float gpuMedian = gpu.isEmpty() ? Float.NaN : gpu.get(gpu.size() / 2);
        ProjectAtmosphere.LOGGER.info("PROD_CAPTURE_SHOT name={} pos=({}, {}, {}) stepScale={} settledStepScale={} gpuMsMedian={} fieldMsMedian={} rainMsMedian={} gpuSamples={} resolution={} temporal={} program={}",
                name, fmt(mc.player.getX()), fmt(mc.player.getY()), fmt(mc.player.getZ()),
                VolumetricCloudRenderer.governorStepScale(), settledStepScale, gpuMedian, median(FIELD), median(RAIN), gpu.size(),
                VolumetricCloudRenderer.lastResolutionScale(), VolumetricCloudRenderer.temporalPhaseActive(),
                VolumetricCloudRenderer.lastProgram() == null ? "-" : VolumetricCloudRenderer.lastProgram().serializedName());
    }

    private static float median(List<Float> values) {
        List<Float> sorted = new ArrayList<>(values);
        sorted.sort(null);
        return sorted.isEmpty() ? Float.NaN : sorted.get(sorted.size() / 2);
    }

    private static void finish(Minecraft mc) {
        restoreProduction();
        VolumetricCloudRenderer.releaseStepScalePin();
        AtmoCommonConfig.FREEZE_CLOUD_MOVEMENT.set(false);
        mc.options.hideGui = false;
        command(mc, "gamerule sendCommandFeedback true");
        command(mc, "gamerule doDaylightCycle true");
        ProjectAtmosphere.LOGGER.info("PROD_CAPTURE_DONE set={} shots={}", SET, index);
        phase = 99;
        mc.stop();
    }

    /** Camera {x, y, z, aimX, aimY, aimZ} for a named view around the region's live clusters (ported from the cloud gallery). */
    private static double[] pose(Minecraft mc, CloudRegionState region, String view, String type) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        double base = Double.MAX_VALUE, top = -Double.MAX_VALUE;
        for (CloudClusterState c : region.getClusters()) {
            minX = Math.min(minX, c.getCenter().x - c.getRadius()); maxX = Math.max(maxX, c.getCenter().x + c.getRadius());
            minZ = Math.min(minZ, c.getCenter().z - c.getRadius()); maxZ = Math.max(maxZ, c.getCenter().z + c.getRadius());
            base = Math.min(base, c.getBaseY()); top = Math.max(top, c.getTopY());
        }
        if (minX > maxX) throw new IllegalStateException("region " + region.getRegionId() + " has no clusters");
        double cx = 0.5D * (minX + maxX), cz = 0.5D * (minZ + maxZ);
        double ext = 0.5D * Math.max(maxX - minX, maxZ - minZ), h = Math.max(top - base, 1.0D), midY = base + 0.5D * h;
        double d = Math.max(0.55D * ext + 0.8D * h, ext + 25.0D) + 10.0D;
        boolean storm = h > 300.0D;
        double camX, camY, camZ, aimX = cx, aimY = midY, aimZ = cz;
        switch (view) {
            case "below" -> {
                camX = cx + (storm ? 0.9D : 0.3D) * ext; camZ = cz + 0.2D * ext;
                double g = ground(mc, camX, camZ);
                camY = Math.max(g + 12.0D, Math.min(base - 15.0D, g + 0.5D * Math.max(base - g, 0.0D)));
                aimY = base + 0.25D * h;
            }
            case "anvil" -> { camX = cx; camZ = cz + 1.2D * d; camY = top + 0.2D * d; aimY = top - 0.1D * h; }
            case "far" -> { camX = cx + 0.3D * 2.0D * d; camZ = cz + 2.0D * d; camY = midY; }
            case "close" -> { double cd = Math.max(0.6D * d, ext + 20.0D); camX = cx; camZ = cz + cd; camY = midY; }
            case "zoom" -> { camX = cx; camZ = cz + d; camY = midY; } // side pose; s38 narrows the FOV
            // E39 motion check: the zoom pose, panning while the arm renders (0.05 / 0.4 degrees per tick).
            case "pan", "panfast" -> { camX = cx; camZ = cz + d; camY = midY; }
            // Perf p3 (cloud grids): inside the field below the bases, and above the tops, both looking along +Z.
            case "field_low" -> { camX = cx + 0.5D * gridSpacing(); camZ = cz + 0.5D * gridSpacing(); camY = base - 60.0D;
                aimX = camX; aimZ = camZ + 3000.0D; aimY = base + 0.3D * h; }
            case "field_high" -> { camX = cx + 0.5D * gridSpacing(); camZ = cz + 0.5D * gridSpacing(); camY = top + 200.0D;
                aimX = camX; aimZ = camZ + 3000.0D; aimY = top - 300.0D; }
            // Perf floor: the side camera looking away from the storm (horizon sky, almost no cloud on screen).
            case "away" -> { camX = cx; camZ = cz + d; camY = midY; aimZ = cz + 3.0D * d; }
            default -> { camX = cx; camZ = cz + d; camY = midY; } // side
        }
        double g = ground(mc, camX, camZ);
        if (!"below".equals(view) && camY < g + 15.0D) camY = g + 15.0D;
        ProjectAtmosphere.LOGGER.info("PROD_CAPTURE_POSE type={} view={} region={} centre=({}, {}) ext={} base={} top={} cam=({}, {}, {})",
                type, view, region.getRegionId(), fmt(cx), fmt(cz), fmt(ext), fmt(base), fmt(top), fmt(camX), fmt(camY), fmt(camZ));
        return new double[] {camX, camY, camZ, aimX, aimY, aimZ};
    }

    private static double ground(Minecraft mc, double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (mc.level == null || !mc.level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) return 64.0D;
        return Math.max(64.0D, mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz));
    }

    private static void command(Minecraft mc, String cmd) {
        if (mc.player != null) mc.player.connection.sendCommand(cmd);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
