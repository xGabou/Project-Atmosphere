package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2606;
import net.Gabou.projectatmosphere.clouds.morphology.StormAnatomyCandidateC;
import net.Gabou.projectatmosphere.clouds.morphology.StormGeometryProgram;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;
import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T260 native evidence (experimental fixture only; PA_T260_CAPTURE=1). PA_T260_PHASE selects the arm list:
 * "spike" = sparse-brick feasibility on shapeless placeholders (+ T258 C3 same-harness control),
 * "gallery" = frozen calvus systems (3 seeds) from every view, isolated primary, close-ups, update cost.
 * Every view of a seed uses ONE frozen program and ONE framing box (the complete system), so isolated-primary
 * shots share the complete system's cameras exactly.
 */
final class ConvectiveSystemCapture {
    private ConvectiveSystemCapture() {}

    static final long[] SEEDS = {20261015L, 20262741L, 20264467L};
    /** T260.3: the three frozen seeds plus three new ones. */
    static final long[] LAYOUT_SEEDS = {20261015L, 20262741L, 20264467L, 20265193L, 20266607L, 20268011L};
    static final List<String> GALLERY_VIEWS = List.of("side", "opposite_side", "front", "back", "above", "below", "low_angle",
            "close", "far", "upwind", "downwind", "crosswind_left", "crosswind_right");
    static final List<String> ISO_VIEWS = List.of("side", "opposite_side", "front", "back", "above", "low_angle");
    /** T260.5: the T260.4 views, the isolated primary and the crown close-ups (z4). */
    static final List<String> T2605_VIEWS = List.of("side", "opposite_side", "front", "back", "above", "below", "low_angle", "close",
            "crosswind_left", "crosswind_right", "iso_side", "iso_opposite_side", "iso_front", "iso_back", "iso_above", "iso_low_angle",
            "z4_side", "z4_front", "z4_above");
    /** T260.6 arms: D (frozen baseline, through the reconstruction framework), A merge, B growth, C density, ABC combined. */
    static final List<String> T2606_ARMS = List.of("D", "A", "B", "C", "ABC");
    static final List<String> T2606_FLAT_SUBSET = List.of("side", "front", "above", "below", "iso_side", "z4_side");
    static final List<String> T2605_DIAG_VIEWS = List.of("side", "front", "back", "above", "below", "crosswind_left", "iso_side", "z4_side");

    /** T260.5 lighting mode of a shot type (PaDiagnosticLightingMode): 1 flat, 20 neutral volumetric, 21-24 diagnostics, 25 reference. */
    static int lightingMode(String type) {
        if (type.startsWith("t2606_")) type = "t2605_" + type.split("_")[2]; // T260.6: t2606_<arm>_<mode>[_probe]
        if (!type.startsWith("t2605_")) return 1;
        String m = type.substring(6).replace("_probe", "");
        return switch (m) { case "vol" -> 20; case "density" -> 21; case "direct" -> 22; case "ambient" -> 23; case "od" -> 24; case "ref" -> 25; default -> 1; };
    }

    /** T260.5: float-exact hashes of the cloud target (all channels, alpha only) for the identity checks. */
    static void hashTarget(String shotName, String type, int texture, int w, int h) {
        if (!type.startsWith("t2605_") && !type.startsWith("t2606_")) return;
        var px = BufferUtils.createFloatBuffer(w * h * 4);
        com.mojang.blaze3d.systems.RenderSystem.bindTexture(texture);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, px);
        long all = 0xcbf29ce484222325L, alpha = all;
        int occupied = 0;
        for (int i = 0; i < w * h * 4; i++) {
            int bits = Float.floatToRawIntBits(px.get(i));
            all = (all ^ bits) * 0x100000001b3L;
            if ((i & 3) == 3) { alpha = (alpha ^ bits) * 0x100000001b3L; if (px.get(i) > 0) occupied++; }
        }
        ProjectAtmosphere.LOGGER.info("T260_HASH shot={} rgba={} alpha={} occupied={} lighting={}", shotName, Long.toUnsignedString(all, 16),
                Long.toUnsignedString(alpha, 16), occupied, lightingMode(type));
    }

    private static final Map<String, StormGeometryProgram> CACHE = new LinkedHashMap<>(8, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, StormGeometryProgram> eldest) { return size() > 4; }
    };
    private static Integer savedFov;
    private static ConvectiveSystemT260.Program developing;
    private static ConvectiveSystemT260.Recipe developingRecipe;
    private static int developStep;
    // T260.3: off-thread incremental rebake (removes the render-thread stall that inflated the developing-arm frames).
    private static java.util.concurrent.CompletableFuture<Object[]> inFlight;
    private static final java.util.concurrent.ExecutorService REBAKE = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "T260-rebake");
        t.setDaemon(true);
        return t;
    });
    private static final List<Double> STALL_MS = new ArrayList<>();
    private static int syncSteps;
    private static double[] devOrigin = new double[3];
    private static int lastStepFrame = Integer.MAX_VALUE;
    private static final List<Double> UPDATE_MS = new ArrayList<>(), UPLOAD_MS = new ArrayList<>();
    private static final List<Long> UPLOAD_BYTES = new ArrayList<>();
    private static final List<Integer> BRICKS_WRITTEN = new ArrayList<>();

    static String phase() { String p = System.getenv("PA_T260_PHASE"); return p == null || p.isBlank() ? "spike" : p; }

    static List<StormMorphologyCapture.Shot> shots() {
        List<StormMorphologyCapture.Shot> s = new ArrayList<>();
        if (phase().equals("all")) {
            // One session: sparse-brick spike first (feasibility evidence), then the calvus gallery.
            s.addAll(shots("spike"));
            s.addAll(shots("gallery"));
            return filterSmoke(s);
        }
        return filterSmoke(shots(phase()));
    }

    private static List<StormMorphologyCapture.Shot> shots(String phase) {
        List<StormMorphologyCapture.Shot> s = new ArrayList<>();
        if (phase.equals("refine")) {
            // T260.2: B (refinement) and A (frozen T260.1, prefix "a_") at identical cameras (framed by A and B).
            s.add(new StormMorphologyCapture.Shot("calvus_system", SEEDS[0], "attribution_intervals"));
            s.add(new StormMorphologyCapture.Shot("calvus_system", SEEDS[0], "attribution_exact"));
            for (long seed : SEEDS) {
                for (String prefix : List.of("", "a_")) {
                    for (String v : GALLERY_VIEWS) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + v));
                    for (String v : ISO_VIEWS) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + "iso_" + v));
                    for (String v : List.of("side", "front", "above")) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + "z4_" + v));
                }
            }
            s.add(new StormMorphologyCapture.Shot("c3_calvus", 20261008L, "ctl_side"));
            return s;
        }
        if (phase.equals("layout")) {
            // T260.3: C (layout candidate, no prefix) and B (frozen T260.2, prefix "b_") at identical cameras (framed by B and C).
            s.add(new StormMorphologyCapture.Shot("calvus_system", LAYOUT_SEEDS[0], "attribution_intervals"));
            s.add(new StormMorphologyCapture.Shot("calvus_system", LAYOUT_SEEDS[0], "attribution_exact"));
            for (long seed : LAYOUT_SEEDS) {
                for (String prefix : List.of("", "b_")) {
                    for (String v : GALLERY_VIEWS) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + v));
                    for (String v : ISO_VIEWS) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + "iso_" + v));
                    for (String v : List.of("side", "front", "above")) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + "z4_" + v));
                }
            }
            s.add(new StormMorphologyCapture.Shot("calvus_system", LAYOUT_SEEDS[0], "dev_update"));
            s.add(new StormMorphologyCapture.Shot("calvus_system", LAYOUT_SEEDS[0], "dev_verify"));
            s.add(new StormMorphologyCapture.Shot("c3_calvus", 20261008L, "ctl_side"));
            return s;
        }
        if (phase.equals("broad")) {
            // T260.3 broader alternative (C26, prefix "w_") vs B ("b_") at identical cameras (framed by B and C26). The
            // fast/exact traversal pair is not repeated: C26 fails it in the base-streak region (documented in the report).
            for (long seed : LAYOUT_SEEDS) {
                for (String prefix : List.of("w_", "b_")) {
                    for (String v : GALLERY_VIEWS) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + v));
                    for (String v : ISO_VIEWS) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + "iso_" + v));
                    for (String v : List.of("side", "front", "above")) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + "z4_" + v));
                }
            }
            for (long seed : List.of(LAYOUT_SEEDS[0], LAYOUT_SEEDS[1])) for (String prefix : List.of("b_", "w_")) {
                s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + "dev_update"));
                for (int m = 1; m <= 7; m++) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + "dev_more" + m));
                s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + "dev_verify"));
            }
            s.add(new StormMorphologyCapture.Shot("c3_calvus", 20261008L, "ctl_side"));
            return s;
        }
        if (phase.equals("t2604")) {
            // T260.4: per seed, identical cameras (framed by C25 and D): C25 under the T260.3 renderer (before), C25 under
            // the fixed renderer (renderer attribution) and D under the fixed renderer (after); then fast/exact pairs for
            // every arm (C26 = rejected T260.3 control); then D's developing-system chain. Legacy C26 pairs come last: they
            // are expected to differ (the T260.3 failure), logged without aborting.
            List<String> views = List.of("side", "opposite_side", "front", "back", "above", "below", "low_angle", "close",
                    "crosswind_left", "crosswind_right");
            for (long seed : LAYOUT_SEEDS) for (String type : List.of("t2604_c25_legacy", "t2604_c25", "t2604_d"))
                for (String v : views) s.add(new StormMorphologyCapture.Shot(type, seed, v));
            for (long seed : LAYOUT_SEEDS) for (String type : List.of("t2604_c25_legacy", "t2604_c25", "t2604_d", "t2604_c26")) {
                s.add(new StormMorphologyCapture.Shot(type, seed, "attribution_intervals"));
                s.add(new StormMorphologyCapture.Shot(type, seed, "attribution_exact"));
            }
            for (long seed : List.of(LAYOUT_SEEDS[0], LAYOUT_SEEDS[1])) {
                s.add(new StormMorphologyCapture.Shot("t2604_d", seed, "dev_update"));
                for (int m = 1; m <= 7; m++) s.add(new StormMorphologyCapture.Shot("t2604_d", seed, "dev_more" + m));
                s.add(new StormMorphologyCapture.Shot("t2604_d", seed, "dev_verify"));
            }
            s.add(new StormMorphologyCapture.Shot("c3_calvus", 20261008L, "ctl_side"));
            for (long seed : LAYOUT_SEEDS) {
                s.add(new StormMorphologyCapture.Shot("t2604_c26_legacy", seed, "attribution_intervals"));
                s.add(new StormMorphologyCapture.Shot("t2604_c26_legacy", seed, "attribution_exact"));
            }
            return s;
        }
        if (phase.equals("t2605")) {
            // T260.5: frozen D (the T260.4 cameras, framed by C25 and D) under flat (A) and neutral volumetric (B)
            // lighting at every established view, isolated primary and crown close-up; then the per-sample diagnostics;
            // fast/exact pairs under volumetric lighting; contamination probes (world maps mirrored, world lighting
            // uniforms scrambled) next to their unprobed controls.
            for (long seed : LAYOUT_SEEDS) for (String type : List.of("t2605_flat", "t2605_vol"))
                for (String v : T2605_VIEWS) s.add(new StormMorphologyCapture.Shot(type, seed, v));
            for (long seed : LAYOUT_SEEDS) for (String type : List.of("t2605_density", "t2605_direct", "t2605_ambient", "t2605_od"))
                for (String v : T2605_DIAG_VIEWS) s.add(new StormMorphologyCapture.Shot(type, seed, v));
            for (long seed : LAYOUT_SEEDS) for (String v : List.of("side", "below", "iso_side", "z4_side"))
                s.add(new StormMorphologyCapture.Shot("t2605_ref", seed, v)); // full-rate reference lighting (shortcut error)
            for (long seed : LAYOUT_SEEDS) {
                s.add(new StormMorphologyCapture.Shot("t2605_vol", seed, "attribution_intervals"));
                s.add(new StormMorphologyCapture.Shot("t2605_vol", seed, "attribution_exact"));
            }
            for (long seed : LAYOUT_SEEDS) {
                for (String v : List.of("side", "below")) s.add(new StormMorphologyCapture.Shot("t2605_vol_probe", seed, v));
                s.add(new StormMorphologyCapture.Shot("t2605_flat_probe", seed, "side"));
            }
            s.add(new StormMorphologyCapture.Shot("c3_calvus", 20261008L, "ctl_side"));
            if ("1".equals(System.getenv("PA_T2605_PERF"))) // cost pass: volumetric at every view of every seed
                s.removeIf(x -> !x.type().equals("t2605_vol") || !T2605_VIEWS.contains(x.view()));
            if ("1".equals(System.getenv("PA_T2605_HEAVY"))) // cost pass: flat and volumetric at the costliest views
                s.removeIf(x -> !(x.type().equals("t2605_vol") || x.type().equals("t2605_flat")) || !List.of("z4_above", "z4_front", "front").contains(x.view()));
            if ("1".equals(System.getenv("PA_T2605_SMOKE"))) // shader/cost smoke: one seed, few views
                s.removeIf(x -> x.seed() != LAYOUT_SEEDS[0] || !List.of("side", "below", "z4_side", "attribution_intervals", "attribution_exact").contains(x.view())
                        || x.type().equals("t2605_density") || x.type().equals("t2605_od"));
            return s;
        }
        if (phase.equals("t2606")) {
            // T260.6: per seed and arm (the arm's programs stay resident: full, isolated and their lit wrappers), the
            // neutral volumetric view at every T260.5 camera, flat silhouettes (every view for D and ABC, a subset for
            // A/B/C) and the density / direct / ambient / optical-depth diagnostics; then fast/exact pairs under
            // volumetric lighting, contamination probes next to unprobed controls, and the same-harness control.
            for (long seed : LAYOUT_SEEDS) for (String arm : T2606_ARMS) {
                for (String v : T2605_VIEWS) s.add(new StormMorphologyCapture.Shot("t2606_" + arm + "_vol", seed, v));
                boolean all = arm.equals("D") || arm.equals("ABC");
                for (String v : all ? T2605_VIEWS : T2606_FLAT_SUBSET) s.add(new StormMorphologyCapture.Shot("t2606_" + arm + "_flat", seed, v));
                List<String> dv = all ? List.of("side", "below", "z4_side") : arm.equals("A") ? List.of("side") : List.of("side", "z4_side");
                for (String m : List.of("density", "direct", "ambient", "od")) for (String v : dv) s.add(new StormMorphologyCapture.Shot("t2606_" + arm + "_" + m, seed, v));
            }
            for (long seed : LAYOUT_SEEDS) for (String arm : List.of("ABC", "C")) {
                s.add(new StormMorphologyCapture.Shot("t2606_" + arm + "_vol", seed, "attribution_intervals"));
                s.add(new StormMorphologyCapture.Shot("t2606_" + arm + "_vol", seed, "attribution_exact"));
            }
            for (long seed : LAYOUT_SEEDS) {
                s.add(new StormMorphologyCapture.Shot("t2606_ABC_vol_probe", seed, "side"));
                s.add(new StormMorphologyCapture.Shot("t2606_ABC_vol", seed, "side_ctl"));
            }
            s.add(new StormMorphologyCapture.Shot("t2606_ABC_flat_probe", LAYOUT_SEEDS[0], "side"));
            s.add(new StormMorphologyCapture.Shot("c3_calvus", 20261008L, "ctl_side"));
            if ("1".equals(System.getenv("PA_T2606_PERF"))) // cost pass: volumetric at every view, every arm and seed
                s.removeIf(x -> !x.type().endsWith("_vol") || !T2605_VIEWS.contains(x.view()));
            // Cost pass (the costliest views, flat and volumetric) and/or retakes ("type:seed:view" list) of rejected frames.
            String retake = System.getenv("PA_T2606_RETAKE");
            boolean heavy = "1".equals(System.getenv("PA_T2606_HEAVY"));
            if (heavy || retake != null) {
                List<String> again = retake == null ? List.of() : List.of(retake.split(","));
                s.removeIf(x -> !(again.contains(x.type() + ":" + x.seed() + ":" + x.view())
                        || heavy && (x.type().endsWith("_vol") || x.type().endsWith("_flat")) && List.of("z4_above", "z4_front", "front", "close", "below").contains(x.view())));
            }
            if ("1".equals(System.getenv("PA_T2606_SMOKE"))) // smoke: one seed, D and ABC, a few views
                s.removeIf(x -> x.seed() != LAYOUT_SEEDS[0] || !(x.type().startsWith("t2606_D_") || x.type().startsWith("t2606_ABC_"))
                        || !List.of("side", "z4_side", "attribution_intervals", "attribution_exact", "side_ctl").contains(x.view()));
            return s;
        }
        if (phase.equals("t2606b")) {
            // T260.6 validation gaps: B vs ABC (and AB, BC: what A and C add beyond B) at identical cameras, exposure,
            // lighting and renderer settings: every T260.5 view plus a distant photographic view and a 3 km ground-level
            // player-lens view; full flat and diagnostic sets for B and ABC (ABC re-baked after the audit fix); fast/exact
            // pairs for every arm; contamination probes; D as the same-session reference.
            List<String> views = new ArrayList<>(T2605_VIEWS);
            views.add("far");
            views.add("gameplay");
            for (long seed : LAYOUT_SEEDS) for (String arm : List.of("D", "B", "AB", "BC", "ABC")) {
                for (String v : views) s.add(new StormMorphologyCapture.Shot("t2606_" + arm + "_vol", seed, v));
                if (arm.equals("B") || arm.equals("ABC")) {
                    for (String v : T2605_VIEWS) s.add(new StormMorphologyCapture.Shot("t2606_" + arm + "_flat", seed, v));
                    for (String m : List.of("density", "direct", "ambient", "od")) for (String v : List.of("side", "below", "z4_side"))
                        s.add(new StormMorphologyCapture.Shot("t2606_" + arm + "_" + m, seed, v));
                }
            }
            for (long seed : LAYOUT_SEEDS) for (String arm : List.of("B", "AB", "BC", "ABC")) {
                s.add(new StormMorphologyCapture.Shot("t2606_" + arm + "_vol", seed, "attribution_intervals"));
                s.add(new StormMorphologyCapture.Shot("t2606_" + arm + "_vol", seed, "attribution_exact"));
            }
            for (long seed : LAYOUT_SEEDS) for (String arm : List.of("B", "ABC")) {
                s.add(new StormMorphologyCapture.Shot("t2606_" + arm + "_vol_probe", seed, "side"));
                s.add(new StormMorphologyCapture.Shot("t2606_" + arm + "_vol", seed, "side_ctl"));
            }
            s.add(new StormMorphologyCapture.Shot("c3_calvus", 20261008L, "ctl_side"));
            if ("1".equals(System.getenv("PA_T2606_SMOKE")))
                s.removeIf(x -> x.seed() != LAYOUT_SEEDS[0] || !List.of("gameplay", "far", "side").contains(x.view()) || !x.type().endsWith("_vol"));
            String retake = System.getenv("PA_T2606_RETAKE"); // "type:seed:view" list: frames rejected by the cross-mode alpha check
            if (retake != null) { List<String> again = List.of(retake.split(",")); s.removeIf(x -> !again.contains(x.type() + ":" + x.seed() + ":" + x.view())); }
            return s;
        }
        if (phase.equals("devstall")) {
            // T260.3 diagnosis: is the developing-arm GPU spike caused by the render-thread stall itself (GPU clocks
            // dropping during the idle gap) or by the upload? dev_stall = same stall cadence, no update, no upload.
            for (String prefix : List.of("b_", "")) {
                s.add(new StormMorphologyCapture.Shot("calvus_system", LAYOUT_SEEDS[1], prefix + "dev_stall"));
                s.add(new StormMorphologyCapture.Shot("calvus_system", LAYOUT_SEEDS[1], prefix + "dev_update"));
                s.add(new StormMorphologyCapture.Shot("calvus_system", LAYOUT_SEEDS[1], prefix + "dev_verify"));
            }
            return s;
        }
        if (phase.equals("devab")) {
            // T260.3: developing-system update arm for B (frozen T260.2) and C in one session, two seeds.
            // dev_more1..7 continue growing the same developing system (no reset): with the off-thread rebake each shot
            // completes 1-2 steps, so the chain times 8+ steps (and their sub-upload frames) per variant and seed.
            for (long seed : List.of(LAYOUT_SEEDS[0], LAYOUT_SEEDS[1])) for (String prefix : List.of("b_", "")) {
                s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + "dev_update"));
                for (int m = 1; m <= 7; m++) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + "dev_more" + m));
                s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + "dev_verify"));
            }
            return s;
        }
        if (phase.equals("close")) {
            for (long seed : SEEDS) for (String prefix : List.of("", "a_")) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, prefix + "close"));
            return s;
        }
                if (phase.equals("dev")) {
            // Developing-system update cost only (re-run with the elapsed-frame step trigger).
            s.add(new StormMorphologyCapture.Shot("placeholder_48", 20260L, "dev_update"));
            s.add(new StormMorphologyCapture.Shot("placeholder_48", 20260L, "dev_verify"));
            s.add(new StormMorphologyCapture.Shot("calvus_system", SEEDS[0], "dev_update"));
            s.add(new StormMorphologyCapture.Shot("calvus_system", SEEDS[0], "dev_verify"));
            s.add(new StormMorphologyCapture.Shot("calvus_system", SEEDS[0], "side"));
            return s;
        }
        if (phase.equals("gallery")) {
            s.add(new StormMorphologyCapture.Shot("calvus_system", SEEDS[0], "attribution_intervals"));
            s.add(new StormMorphologyCapture.Shot("calvus_system", SEEDS[0], "attribution_exact"));
            for (long seed : SEEDS) {
                for (String v : GALLERY_VIEWS) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, v));
                for (String v : ISO_VIEWS) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, "iso_" + v));
                for (String v : List.of("side", "front", "above")) s.add(new StormMorphologyCapture.Shot("calvus_system", seed, "z4_" + v));
            }
            s.add(new StormMorphologyCapture.Shot("calvus_system", SEEDS[0], "dev_update"));
            s.add(new StormMorphologyCapture.Shot("calvus_system", SEEDS[0], "dev_verify"));
            s.add(new StormMorphologyCapture.Shot("c3_calvus", 20261008L, "ctl_side"));
        } else {
            for (String type : List.of("placeholder_8", "placeholder_48", "placeholder_96", "placeholder_260")) {
                for (String v : List.of("side", "above", "low_angle", "close", "far")) s.add(new StormMorphologyCapture.Shot(type, 20260L, "spike_" + v));
            }
            for (String type : List.of("placeholder_48", "placeholder_260")) {
                s.add(new StormMorphologyCapture.Shot(type, 20260L, "attribution_intervals"));
                s.add(new StormMorphologyCapture.Shot(type, 20260L, "attribution_exact"));
            }
            s.add(new StormMorphologyCapture.Shot("placeholder_48", 20260L, "dev_update"));
            s.add(new StormMorphologyCapture.Shot("placeholder_48", 20260L, "dev_verify"));
            s.add(new StormMorphologyCapture.Shot("c3_calvus", 20261008L, "ctl_side"));
            s.add(new StormMorphologyCapture.Shot("c3_calvus", 20261008L, "ctl_close"));
        }
        return s;
    }

    private static List<StormMorphologyCapture.Shot> filterSmoke(List<StormMorphologyCapture.Shot> s) {
        if ("1".equals(System.getenv("PA_T260_SMOKE"))) {
            var keep = List.of("spike_side", "side", "iso_side", "attribution_intervals", "attribution_exact", "dev_update", "dev_verify", "ctl_side");
            s.removeIf(shot -> !keep.contains(shot.view()) || (shot.type().startsWith("placeholder_") && !shot.type().equals("placeholder_48")) || (shot.seed() != SEEDS[0] && shot.type().equals("calvus_system")));
        }
        return s;
    }

    static boolean wantsParity(String view) {
        if (view.startsWith("a_") || view.startsWith("b_") || view.startsWith("w_")) view = view.substring(2);
        return view.equals("side") || view.equals("spike_side") || view.equals("iso_side") || view.equals("dev_verify")
                || view.equals("attribution_intervals") || view.equals("ctl_side");
    }

    private static int placeholderCount(String type) { return Integer.parseInt(type.substring("placeholder_".length())); }

    private static StormGeometryProgram program(String key, java.util.function.Supplier<StormGeometryProgram> build) {
        StormGeometryProgram p = CACHE.get(key);
        if (p == null) {
            long t0 = System.nanoTime();
            p = build.get();
            ProjectAtmosphere.LOGGER.info("T260_BAKE key={} ms={} {}", key, (System.nanoTime() - t0) / 1e6,
                    p instanceof ConvectiveSystemT260.Program c ? c.volume().stats()
                            : p instanceof ConvectiveVolumeT2606.Program t ? t.volume().stats() + " fieldMs=" + t.fieldMs() + " arms=" + t.arms().name() : "control");
            CACHE.put(key, p);
        }
        return p;
    }

    static ConvectiveSystemT260.Recipe recipe(String type, long seed) {
        if (type.startsWith("placeholder_")) {
            int n = placeholderCount(type);
            int[] r = switch (n) { case 8 -> new int[]{120, 200}; case 48 -> new int[]{140, 240}; case 96 -> new int[]{140, 260}; default -> new int[]{160, 300}; };
            return ConvectiveSystemT260.placeholder(n, r[0], r[1], 20260 + n);
        }
        if (type.startsWith("t2605_")) return ConvectiveSystemT260.calvusD(seed); // T260.5: frozen D only
        if (type.startsWith("t2604_")) {
            // T260.4 arms: geometry from the type ("_legacy" only selects the pre-T260.4 renderer).
            String g = type.replace("_legacy", "");
            return g.equals("t2604_d") ? ConvectiveSystemT260.calvusD(seed) : g.equals("t2604_c26") ? ConvectiveSystemT260.calvusBroad(seed)
                    : ConvectiveSystemT260.calvus(seed);
        }
        return ConvectiveSystemT260.calvus(seed);
    }

    /**
     * Calvus seeds are framed by the union of the two compared variants so before/after share cameras: B and C in the
     * T260.3 "layout" phase, A and (frozen) B in the T260.2 phases; placeholders by their own extremes.
     */
    static float[] framePoints(String type, long seed) {
        if (type.startsWith("t2605_") || type.startsWith("t2606_")) type = "t2604_d"; // T260.5/T260.6 keep the T260.4 cameras exactly
        if (type.startsWith("t2604_")) {
            // T260.4: C25/D arms share the C25 and D framing; the C26 control keeps the T260.3 B and C26 framing.
            boolean c26 = type.startsWith("t2604_c26");
            float[] a = ConvectiveSystemCapturePlan.extremes(c26 ? ConvectiveSystemT260.calvusB(seed) : ConvectiveSystemT260.calvus(seed));
            float[] b = ConvectiveSystemCapturePlan.extremes(c26 ? ConvectiveSystemT260.calvusBroad(seed) : ConvectiveSystemT260.calvusD(seed));
            float[] u = java.util.Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, u, a.length, b.length);
            return u;
        }
        if (!type.equals("calvus_system")) return ConvectiveSystemCapturePlan.extremes(recipe(type, seed));
        boolean layout = phase().equals("layout") || phase().equals("devab") || phase().equals("devstall");
        boolean broad = phase().equals("broad");
        float[] a = ConvectiveSystemCapturePlan.extremes(layout || broad ? ConvectiveSystemT260.calvusB(seed) : ConvectiveSystemT260.calvusA(seed));
        float[] b = ConvectiveSystemCapturePlan.extremes(broad ? ConvectiveSystemT260.calvusBroad(seed)
                : layout ? ConvectiveSystemT260.calvus(seed) : ConvectiveSystemT260.calvusB(seed));
        float[] u = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, u, a.length, b.length);
        return u;
    }

    static double[] move(StormMorphologyCapture.Shot shot, StormPerformanceBaseline.SuiteFixture fixture) {
        String view = shot.view();
        boolean variantA = view.startsWith("a_"), variantB = view.startsWith("b_"), variantW = view.startsWith("w_");
        if (variantA || variantB || variantW) view = view.substring(2);
        boolean iso = view.startsWith("iso_"), zoom = view.startsWith("z4_"), develop = view.startsWith("dev_");
        String pose = view.replace("iso_", "").replace("z4_", "").replace("spike_", "").replace("ctl_", "");
        if (develop || pose.startsWith("attribution_") || view.equals("side_ctl")) pose = "side";
        String type = shot.type();
        long seed = shot.seed();
        StormGeometryProgram full, current;
        if (type.startsWith("t2606_")) {
            // T260.6: every arm is derived from the frozen D recipe; cameras and crown targets are D's.
            String arm = type.split("_")[1];
            var rec = ConvectiveSystemT260.calvusD(seed);
            var arms = ConvectiveVolumeT2606.Arms.parse(arm);
            full = program("t2606/" + arm + "/" + seed, () -> new ConvectiveVolumeT2606.Program(rec, arms, null, 6, 12, .25f));
            if (iso) {
                boolean[] only = new boolean[rec.components().size()];
                for (int c = 0; c < only.length; c++) only[c] = rec.components().get(c).role() == ConvectiveSystemT260.Role.PRIMARY;
                current = program("t2606/" + arm + "/" + seed + "/iso", () -> new ConvectiveVolumeT2606.Program(rec, arms, only, 6, 12, .25f));
            } else current = full;
        } else if (type.equals("c3_calvus")) {
            var shape = CloudShapeProfile.defaultFor("cumulonimbus_calvus", CloudFamily.CUMULONIMBUS, null);
            full = current = program("c3/" + seed, () -> StormAnatomyCandidateC.compile("cumulonimbus_calvus", shape, seed));
        } else {
            // T260.2 phases: the unprefixed calvus was B, now frozen as calvusB (identical recipe and cache key).
            boolean legacy = !phase().equals("layout") && !phase().equals("devab") && !phase().equals("devstall") && !phase().equals("broad")
                    && type.equals("calvus_system");
            var rec = variantA ? ConvectiveSystemT260.calvusA(seed) : variantW ? ConvectiveSystemT260.calvusBroad(seed)
                    : variantB || legacy ? ConvectiveSystemT260.calvusB(seed) : recipe(type, seed);
            String key = (type.startsWith("t2605_") ? "t2604_d" : type.replace("_legacy", "")) + "/" + seed + (variantA ? "/A" : variantW ? "/W" : variantB || legacy ? "/B" : "");
            full = program(key, () -> new ConvectiveSystemT260.Program(rec, null, 6, 12, .25f));
            if (iso) {
                boolean[] only = new boolean[rec.components().size()];
                for (int c = 0; c < only.length; c++) only[c] = rec.components().get(c).role() == ConvectiveSystemT260.Role.PRIMARY;
                current = program(key + "/iso", () -> new ConvectiveSystemT260.Program(rec, only, 6, 12, .25f));
            } else if (view.equals("dev_stall")) {
                lastStepFrame = Integer.MAX_VALUE; // stall cadence restarts per shot (no update, no upload)
                current = full;
            } else if (view.equals("dev_update")) {
                // Developing system: a private copy that the tick mutates in place (incremental bricks + sub-uploads).
                developingRecipe = rec;
                developing = new ConvectiveSystemT260.Program(rec, null, 6, 12, .25f);
                developStep = 0;
                lastStepFrame = Integer.MAX_VALUE;
                awaitRebake();
                UPDATE_MS.clear(); UPLOAD_MS.clear(); UPLOAD_BYTES.clear(); BRICKS_WRITTEN.clear(); STALL_MS.clear();
                syncSteps = 0;
                current = developing;
            } else if (view.startsWith("dev_more")) {
                if (developing == null) throw new IllegalStateException("dev_more without dev_update");
                lastStepFrame = Integer.MAX_VALUE; // keep growing the same volume; the cadence restarts per shot
                current = developing;
            } else if (view.equals("dev_verify")) {
                if (developing == null) throw new IllegalStateException("dev_verify without dev_update");
                awaitRebake(); // parity and readback compare the finished volume
                current = developing; // the incrementally updated volume: parity + bit-exact upload readback
            } else current = full;
        }
        StormGeometryProgram geometry = current; // T260.5: cameras follow the geometry, never the light wrapper
        if (lightingMode(type) >= 20 && current instanceof ConvectiveVolumeT2606.Program t) {
            // T260.6: the identical, frozen T260.5 light bake over the arm's own volume.
            String litKey = "t2606lit/" + t.arms().name() + "/" + seed + (iso ? "/iso" : "");
            current = program(litKey, () -> new NeutralLitProgram(t, t.volume(), VolumetricCloudRenderer.Tuning.CELLS.extinctionScale()));
            var lit = (NeutralLitProgram) current;
            ProjectAtmosphere.LOGGER.info("T260_LIGHT shot={} baseSignature={} litSignature={} {}", shot.name(),
                    Long.toUnsignedString(t.signature(), 16), Long.toUnsignedString(lit.signature(), 16), lit.light.stats());
        } else if (lightingMode(type) >= 20 && current instanceof ConvectiveSystemT260.Program c) {
            String litKey = "t2605lit/" + seed + (iso ? "/iso" : "");
            current = program(litKey, () -> new NeutralLitProgram(c, VolumetricCloudRenderer.Tuning.CELLS.extinctionScale()));
            var lit = (NeutralLitProgram) current;
            ProjectAtmosphere.LOGGER.info("T260_LIGHT shot={} baseSignature={} litSignature={} {}", shot.name(),
                    Long.toUnsignedString(c.signature(), 16), Long.toUnsignedString(lit.signature(), 16), lit.light.stats());
        }
        double originX = fixture.centerX(), originZ = fixture.centerZ(), baseY = 1400;
        devOrigin = new double[]{originX, baseY, originZ};
        StormMorphologyCapture.current(current);
        ExperimentalStormMorphologyState.install(current, (float) originX, (float) baseY, (float) originZ);
        ExperimentalStormMorphologyState.intervals(!view.equals("attribution_exact"));
        ExperimentalStormMorphologyState.legacy(type.endsWith("_legacy")); // T260.4: pre-fix renderer arm
        ExperimentalStormMorphologyState.lighting(lightingMode(type)); // T260.5
        ExperimentalStormMorphologyState.poison(type.endsWith("_probe")); // T260.5 contamination probe
        var mc = Minecraft.getInstance();
        if (savedFov == null) savedFov = mc.options.fov().get();
        // Close-ups (z4) narrow the PLAYER lens by 4x; whole-system views use the photographic lens.
        int optionFov = type.equals("c3_calvus") || type.startsWith("placeholder_") || zoom ? savedFov : ConvectiveSystemCapturePlan.optionFov(pose, savedFov);
        double modifier = mc.player == null ? 1.0 : mc.player.getFieldOfViewModifier();
        double effective = optionFov * modifier;
        double aspect = (double) mc.getMainRenderTarget().width / mc.getMainRenderTarget().height;
        Bounds frame = full.bounds();
        boolean system = full instanceof ConvectiveSystemT260.Program || full instanceof ConvectiveVolumeT2606.Program;
        double[] rel = ConvectiveSystemCapturePlan.pose(pose, frame, system ? framePoints(type, seed) : null, effective, aspect);
        int shotFov = optionFov;
        if (zoom) {
            rel = ConvectiveSystemCapturePlan.zoomPose(pose, frame, rel, geometry instanceof ConvectiveSystemT260.Program c ? c.recipe()
                    : geometry instanceof ConvectiveVolumeT2606.Program t ? t.recipe() : null);
            shotFov = StormAnatomyCapturePlan.zoomOptionFov(effective, modifier, StormAnatomyCapturePlan.ZOOM);
        }
        mc.options.fov().set(shotFov);
        ProjectAtmosphere.LOGGER.info("T260_LENS shot={} optionFov={} shotFov={} fovModifier={} effectiveFov={} frame=complete_system margin=0.88",
                shot.name(), optionFov, shotFov, modifier, shotFov * modifier);
        double x = originX + rel[0], y = baseY + rel[1], z = originZ + rel[2], cx = originX + rel[3], cy = baseY + rel[4], cz = originZ + rel[5];
        StormMorphologyCapture.resetTiming();
        ProjectAtmosphere.LOGGER.info("T260_POSE shot={} signature={} origin=({},{},{}) bounds={} frameBounds={} camera=({},{},{}) aim=({},{},{}) textureBytes={}",
                shot.name(), Long.toUnsignedString(current.signature(), 16), originX, baseY, originZ, current.bounds(), frame,
                x, y, z, cx, cy, cz, (long) current.textureWidth() * current.textureHeight() * 16);
        if (geometry instanceof ConvectiveVolumeT2606.Program t) {
            var v = t.volume();
            int[] counts = v.counts();
            ProjectAtmosphere.LOGGER.info("T260_FIELD shot={} recipe={} arms={} components={} bubbles={} voxel={} feather={} grid={} empty={} full={} bricks={} capacity={} boxes={} texture={}x{}",
                    shot.name(), t.recipe().kind(), t.arms().name(), t.recipe().components().size(), t.recipe().bubbles().size(), v.voxel(), v.feather(),
                    java.util.Arrays.toString(v.grid()), counts[0], counts[1], counts[2], v.capacity(), v.boxCount(), v.textureWidth(), v.textureHeight());
        }
        if (current instanceof ConvectiveSystemT260.Program c) {
            var v = c.volume();
            int[] counts = v.counts();
            ProjectAtmosphere.LOGGER.info("T260_FIELD shot={} recipe={} components={} bubbles={} voxel={} feather={} grid={} empty={} full={} bricks={} capacity={} boxes={} texture={}x{}",
                    shot.name(), c.recipe().kind(), c.recipe().components().size(), c.recipe().bubbles().size(), v.voxel(), v.feather(),
                    java.util.Arrays.toString(v.grid()), counts[0], counts[1], counts[2], v.capacity(), v.boxCount(), v.textureWidth(), v.textureHeight());
        }
        return new double[]{x, y, z, cx, cy, cz};
    }

    /** Developing-system arm: every 8 presented frames the primary grows (or relaxes) one step, incrementally. */
    static void tick(int frames) {
        if (ExperimentalStormMorphologyState.parity() || frames < 40 || frames > 230) return;
        var shotView = StormMorphologyCapture.currentView();
        if (shotView != null && (shotView.equals("dev_stall") || shotView.equals("b_dev_stall"))) {
            if (frames - lastStepFrame < 8 && lastStepFrame <= frames) return;
            lastStepFrame = frames;
            try { Thread.sleep(350); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return;
        }
        if (developing == null || shotView == null) return;
        String dv = shotView.length() > 2 && shotView.charAt(1) == '_' ? shotView.substring(2) : shotView;
        if (!dv.equals("dev_update") && !dv.startsWith("dev_more")) return;
        // Presented-frame numbers can skip values: step whenever 8 or more frames passed since the last step.
        if (frames - lastStepFrame < 8 && lastStepFrame <= frames) return;
        lastStepFrame = frames;
        long s0 = System.nanoTime();
        if (inFlight != null) {
            if (!inFlight.isDone()) { lastStepFrame = frames - 8; return; } // previous step still rebaking: retry next frame
            adoptRebake();
        }
        var volume = developing.volume();
        if (developStep > 0 && (volume != residentVolume || residentSignature != volume.signature())) { lastStepFrame = frames - 8; return; } // last step not uploaded yet
        developStep++;
        var next = ConvectiveSystemT260.developStep(developingRecipe, developStep);
        var field = new ConvectiveSystemT260.Field(next.recipe(), null, 12 * 2.5f);
        if (cameraInside()) {
            // The per-frame camera density read touches the CPU volume inside its bounds: stay synchronous there.
            syncSteps++;
            Object[] done = rebake(volume, field, next);
            inFlight = java.util.concurrent.CompletableFuture.completedFuture(done);
            adoptRebake();
        } else {
            inFlight = java.util.concurrent.CompletableFuture.supplyAsync(() -> rebake(volume, field, next), REBAKE);
        }
        STALL_MS.add((System.nanoTime() - s0) / 1e6);
    }

    /** Runs one incremental update under the volume lock (subUpload takes the same lock). {stats, step, ms}. */
    private static Object[] rebake(net.Gabou.projectatmosphere.clouds.morphology.SparseBrickVolume volume,
                                   ConvectiveSystemT260.Field field, ConvectiveSystemT260.DevStep next) {
        synchronized (volume) {
            long t0 = System.nanoTime();
            var u = volume.update(field, next.region());
            return new Object[]{u, next, (System.nanoTime() - t0) / 1e6};
        }
    }

    private static void adoptRebake() {
        Object[] r = inFlight.join();
        inFlight = null;
        var u = (net.Gabou.projectatmosphere.clouds.morphology.SparseBrickVolume.UpdateStats) r[0];
        var next = (ConvectiveSystemT260.DevStep) r[1];
        UPDATE_MS.add((Double) r[2]);
        BRICKS_WRITTEN.add(u.bricksWritten());
        developingRecipe = next.recipe();
        developing.adopt(next.recipe());
    }

    private static void awaitRebake() { if (inFlight != null) adoptRebake(); }

    private static boolean cameraInside() {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var b = developing.bounds();
        double x = cam.x - devOrigin[0], y = cam.y - devOrigin[1], z = cam.z - devOrigin[2];
        return x >= b.minX() - 64 && x <= b.maxX() + 64 && y >= b.minY() - 64 && y <= b.maxY() + 64 && z >= b.minZ() - 64 && z <= b.maxZ() + 64;
    }

    private static net.Gabou.projectatmosphere.clouds.morphology.SparseBrickVolume residentVolume;
    private static long residentSignature;

    /**
     * Uploads only the dirty texel ranges of an incrementally updated system; false = caller does a full upload.
     * A sub-upload is valid only when the GL texture holds THIS volume at the signature it had after its last sync
     * (switching programs, or a reallocated pool, always takes the full upload).
     */
    static boolean subUpload(ConvectiveSystemT260.Program system, int texture, long textureSignature) {
        synchronized (system.volume()) { return subUploadLocked(system, texture, textureSignature); }
    }

    private static boolean subUploadLocked(ConvectiveSystemT260.Program system, int texture, long textureSignature) {
        var volume = system.volume();
        if (volume != residentVolume || textureSignature != residentSignature) {
            volume.drainDirty(); // the full upload that follows makes every pending range redundant
            residentVolume = volume;
            residentSignature = volume.signature();
            return false;
        }
        var ranges = volume.drainDirty();
        if (ranges == null) { residentSignature = volume.signature(); return false; }
        residentSignature = volume.signature();
        long t0 = System.nanoTime();
        float[] data = system.volume().texture();
        int w = system.volume().textureWidth();
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH), skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS), alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        long bytes = 0;
        var buffer = BufferUtils.createFloatBuffer(w * 4);
        for (int[] r : ranges) {
            int t = r[0];
            while (t < r[1]) {
                int row = t / w, col = t - row * w, n = Math.min(r[1] - t, w - col);
                buffer.clear();
                buffer.put(data, t * 4, n * 4).flip();
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, col, row, n, 1, GL11.GL_RGBA, GL11.GL_FLOAT, buffer);
                bytes += n * 16L;
                t += n;
            }
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
        UPLOAD_MS.add((System.nanoTime() - t0) / 1e6);
        UPLOAD_BYTES.add(bytes);
        return true;
    }

    static void captured(String shotName, String view) {
        String dv = view.length() > 2 && view.charAt(1) == '_' ? view.substring(2) : view;
        if (!dv.equals("dev_update") && !dv.startsWith("dev_more")) return; // cumulative over the chain
        ProjectAtmosphere.LOGGER.info("T260_UPDATE shot={} steps={} updateCpuMs={} uploadCpuMs={} uploadBytes={} bricksWritten={} scope=worker_thread renderThreadStallMs={} synchronousSteps={}",
                shotName, UPDATE_MS.size(), summary(UPDATE_MS), summary(UPLOAD_MS), UPLOAD_BYTES, BRICKS_WRITTEN, summary(STALL_MS), syncSteps);
    }

    private static String summary(List<Double> v) {
        if (v.isEmpty()) return "none";
        var s = new ArrayList<>(v);
        s.sort(Double::compare);
        return String.format(java.util.Locale.ROOT, "{n=%d,p50=%.2f,p95=%.2f,max=%.2f}", s.size(), s.get(s.size() / 2),
                s.get(Math.min(s.size() - 1, (int) Math.ceil(s.size() * .95) - 1)), s.get(s.size() - 1));
    }

    static void release() {
        if (savedFov != null) { Minecraft.getInstance().options.fov().set(savedFov); savedFov = null; }
    }
}
