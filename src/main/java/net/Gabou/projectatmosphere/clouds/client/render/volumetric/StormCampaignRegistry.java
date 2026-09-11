package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * T170 Task 0. The single declaration of every diagnostic campaign the
 * {@code StormT132AutoDriver} can run, and the routing each one requires.
 *
 * <p>This exists because the same defect occurred three times. A campaign was
 * added, wired into most of the driver, and silently missed at one dispatch
 * site:
 *
 * <ul>
 *   <li>T167 - the pose arrival guard existed and worked, but was armed by
 *       {@code t166PoseTargetValid = t166Run}, so extending the harness to a
 *       new campaign did not extend that one assignment.</li>
 *   <li>T169 - {@code lightingDetailRunRequested()} was missing from
 *       {@code performanceRunRequested()}, so the campaign marker routed the
 *       driver to the t098 capture path and the matrix was unreachable.</li>
 *   <li>T169 - {@code t169Run} was missing from
 *       {@code activeEvaluationArms()}, so {@code T169_OPTIMIZATION_ARMS} was
 *       declared and never referenced.</li>
 * </ul>
 *
 * <p>Every one of those was a hand-maintained condition chain that drifted from
 * a hand-maintained declaration. The fix is to make the chain derived rather
 * than written: {@link #performanceMarkerPresent()} replaces the disjunction
 * that broke twice, and the remaining sites that cannot be derived without
 * moving the arm tables out of the driver are enforced structurally by
 * {@code StormVolumetricGeometrySandbox}, which fails the build when a campaign
 * declared here is not wired there.
 *
 * <p>This class is deliberately Minecraft-free so the sandbox can load it
 * without a client. It carries no rendering behaviour.
 */
public final class StormCampaignRegistry {
    private StormCampaignRegistry() {
    }

    /**
     * How a campaign participates in driver routing.
     */
    public enum Routing {
        /**
         * Routes through {@code RAYTRACE_FIXTURE -> T135_PREPARE} and drives an
         * arm matrix through {@code activeEvaluationArms()}. Every campaign in
         * this class since T140 is one of these.
         */
        EVALUATION_SWEEP,
        /**
         * Routes through the performance path but drives its own phase machine
         * rather than the arm sweep - the resolution curve, the five-mode
         * ladder, the budget contract, the moving-camera route.
         */
        PERFORMANCE_ONLY
    }

    /**
     * One campaign's declaration.
     *
     * @param id              campaign identifier, e.g. {@code "T169"}
     * @param markerFileName  the marker file, resolved against the client
     *                        working directory ({@code run/})
     * @param predicateName   the {@code *RunRequested()} accessor in the driver
     * @param flagFieldName   the per-run boolean the driver latches in
     *                        {@code T135_PREPARE}, or {@code null} when the
     *                        campaign has none
     * @param armTableName    the arm table {@code activeEvaluationArms()} must
     *                        return for this campaign, or {@code null} when the
     *                        campaign declares no arm matrix
     * @param routing         how the campaign participates in routing
     */
    /**
     * T191. The campaigns whose marker file is present, by id.
     *
     * <p>Read from disk on demand rather than cached, and never from a static
     * initializer: the sandbox loads this class headless, and campaign markers
     * are created and removed between runs of the same client.
     */
    public static java.util.Set<String> activeCampaignIds() {
        java.util.Set<String> active = new java.util.LinkedHashSet<>();
        for (Campaign campaign : CAMPAIGNS) {
            try {
                if (java.nio.file.Files.exists(
                        java.nio.file.Path.of(campaign.markerFileName()))) {
                    active.add(campaign.id());
                }
            } catch (RuntimeException ignored) {
                // A marker that cannot be probed is treated as absent. Failing
                // shader registration over a filesystem hiccup would cost the
                // whole cloud renderer to save a diagnostic program.
            }
        }
        return active;
    }

    /**
     * T191 correction. Every program an armed campaign can actually select.
     *
     * <p>T191 scoped shader registration by the program's own name prefix, on
     * the assumption that a campaign only selects its own arms. That is false
     * and was false when it was written: arm matrices deliberately reuse
     * earlier campaigns' programs as controls - T192's matrix selects
     * T190_FIELD_SAFE as the classifier it must beat and T172_STACK_PRE as its
     * shared control. Neither campaign is armed, so under prefix scoping
     * neither program was registered, volumeShader returned null, and the
     * renderer session-disabled into a lost world connection twice before the
     * cause was found.
     *
     * <p>Resolved by reflection over the driver's own arm tables rather than a
     * hand-maintained map, because a map would be one more thing to forget when
     * the next campaign borrows a control.
     */
    public static java.util.Set<CoreCostDiagnosticProgram>
            programsSelectableByActiveCampaigns() {
        java.util.Set<CoreCostDiagnosticProgram> selectable =
                new java.util.LinkedHashSet<>();
        for (String id : StormCampaignRegistry.activeCampaignIds()) {
            collectSelectablePrograms(selectable, id + "_ARMS");
            collectSelectablePrograms(selectable, id + "_IMAGE_ARMS");
        }
        return selectable;
    }

    private static void collectSelectablePrograms(
            java.util.Set<CoreCostDiagnosticProgram> out, String fieldName) {
        try {
            java.lang.reflect.Field field =
                    StormT132AutoDriver.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof CoreCostDiagnosticProgram[] programs) {
                out.addAll(java.util.Arrays.asList(programs));
            } else if (value != null && value.getClass().isArray()) {
                // The arm record is private to the driver, so its program is
                // read generically rather than by type.
                int length = java.lang.reflect.Array.getLength(value);
                for (int index = 0; index < length; index++) {
                    Object element = java.lang.reflect.Array.get(value, index);
                    if (element == null) {
                        continue;
                    }
                    java.lang.reflect.Method program =
                            element.getClass().getDeclaredMethod("program");
                    program.setAccessible(true);
                    Object resolved = program.invoke(element);
                    if (resolved instanceof CoreCostDiagnosticProgram armProgram) {
                        out.add(armProgram);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException absent) {
            // A campaign without that table simply contributes nothing. Failing
            // shader registration here would cost the whole cloud renderer.
        }
    }

    public record Campaign(
            String id,
            String markerFileName,
            String predicateName,
            String flagFieldName,
            String armTableName,
            Routing routing) {

        /** True when this campaign drives the arm sweep. */
        public boolean drivesArmMatrix() {
            return armTableName != null;
        }
    }

    /**
     * Every campaign, in declaration order.
     *
     * <p>Adding a campaign means adding a row here. The sandbox invariant then
     * requires the driver to declare the marker, the predicate, the flag and -
     * for an {@link Routing#EVALUATION_SWEEP} campaign - the arm table wiring,
     * or the build fails.
     */
    public static final List<Campaign> CAMPAIGNS = List.of(
            new Campaign("T135", "t135-performance.txt", "budgetContractRunRequested",
                    null, null, Routing.PERFORMANCE_ONLY),
            new Campaign("T138", "t138-resolution.txt", "resolutionRunRequested",
                    "t138ResolutionRun", null, Routing.PERFORMANCE_ONLY),
            new Campaign("T140_MODES", "t140-modes.txt", "fiveModeRunRequested",
                    "t140ModeRun", null, Routing.PERFORMANCE_ONLY),
            new Campaign("T152", "t152-moving-camera.txt", "movingCameraRunRequested",
                    "t152Run", null, Routing.PERFORMANCE_ONLY),

            new Campaign("T141", "t141-descriptor-eval.txt", "evaluationRunRequested",
                    "t141EvaluationRun", "T141_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T153", "t153-visible-volume-oracle.txt", "oracleRunRequested",
                    "t153OracleRun", "T153_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T140", "t140-coverage.txt", "coverageRunRequested",
                    "t140Run", "T140_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T161", "t161-final-specialization.txt", "specializationRunRequested",
                    "t161Run", "T161_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T162", "t162-attribution.txt", "attributionRunRequested",
                    "t162Run", "T162_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T163", "t163-precipitation.txt", "precipitationRunRequested",
                    "t163Run", "T163_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T166", "t166-baseline.txt", "baselineRunRequested",
                    "t166Run", "T166_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T167", "t167-refine.txt", "refinementRunRequested",
                    "t167Run", "T167_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T168", "t168-footprint.txt", "footprintRunRequested",
                    "t168Run", "T168_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T169", "t169-lighting-detail.txt", "lightingDetailRunRequested",
                    "t169Run", "T169_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T170", "t170-primary-march.txt", "primaryMarchRunRequested",
                    "t170Run", "T170_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T171", "t171-harness-stability.txt", "harnessStabilityRunRequested",
                    "t171Run", "T171_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T172", "t172-descriptor-precompute.txt", "precomputeRunRequested",
                    "t172Run", "T172_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T173", "t173-side-stability.txt", "sideStabilityRunRequested",
                    "t173Run", "T173_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T174", "t174-group-entry.txt", "groupEntryRunRequested",
                    "t174Run", "T174_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T175", "t175-density-necessity.txt", "densityNecessityRunRequested",
                    "t175Run", "T175_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T176", "t176-light-march.txt", "lightMarchRunRequested",
                    "t176Run", "T176_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T177", "t177-light-reuse.txt", "lightReuseRunRequested",
                    "t177Run", "T177_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T178", "t178-lobe-support.txt", "lobeSupportRunRequested",
                    "t178Run", "T178_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T179", "t179-dominance.txt", "dominancePruningRunRequested",
                    "t179Run", "T179_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T180", "t180-consumers.txt", "consumerAttributionRunRequested",
                    "t180Run", "T180_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T181", "t181-probe-refine.txt", "probeRefineRunRequested",
                    "t181Run", "T181_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T182", "t182-accounting.txt", "walkAccountingRunRequested",
                    "t182Run", "T182_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T184", "t184-rain-reuse.txt", "rainReuseRunRequested",
                    "t184Run", "T184_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T185", "t185-rain-prune.txt", "rainPruneRunRequested",
                    "t185Run", "T185_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T186", "t186-rain-samples.txt", "rainSampleRunRequested",
                    "t186Run", "T186_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T187", "t187-rain-field.txt", "rainFieldRunRequested",
                    "t187Run", "T187_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T188", "t188-rain-field-real.txt",
                    "realRainFieldRunRequested", "t188Run",
                    "T188_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T189", "t189-rain-field-ownership.txt",
                    "rainFieldOwnershipRunRequested", "t189Run",
                    "T189_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T190", "t190-rain-field-conservative.txt",
                    "conservativeRainFieldRunRequested", "t190Run",
                    "T190_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T192", "t192-rain-field-bound.txt",
                    "closedFormRainFieldRunRequested", "t192Run",
                    "T192_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T193", "t193-production-attribution.txt",
                    "productionAttributionRunRequested", "t193Run",
                    "T193_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T194", "t194-shared-field-pricing.txt",
                    "sharedFieldPricingRunRequested", "t194Run",
                    "T194_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T195", "t195-shared-field-lookup.txt",
                    "sharedFieldLookupRunRequested", "t195Run",
                    "T195_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP),
            new Campaign("T196", "t196-shared-field.txt",
                    "sharedFieldRunRequested", "t196Run",
                    "T196_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP));

    /**
     * Markers that arm the harness itself rather than selecting a campaign.
     *
     * <p>These are deliberately enumerated rather than pattern-matched. The
     * wiring invariant treats every other marker declared in the driver as a
     * campaign that must be registered here, so a new marker is a build failure
     * until it is either registered or consciously listed as harness
     * machinery.
     */
    public static final List<String> HARNESS_MARKERS = List.of(
            // Arms the autorun driver at all; present for every campaign.
            "t132-autorun.txt",
            // Requests the standalone ray trace, which is not a timing sweep.
            "t098-raytrace.txt",
            // T195. Stops the client after T132_AUTORUN_FINISHED so the launch
            // returns when the campaign does; selects nothing.
            "t132-autorun-exit.txt");

    /**
     * The marker path for one campaign, resolved the way the driver resolves
     * every other marker: relative to the client working directory.
     */
    public static Path marker(String id) {
        for (Campaign campaign : CAMPAIGNS) {
            if (campaign.id().equals(id)) {
                return Path.of(campaign.markerFileName());
            }
        }
        throw new IllegalArgumentException("Unregistered campaign: " + id);
    }

    /**
     * True when any registered campaign's marker is present.
     *
     * <p>This is the derived replacement for the hand-written disjunction in
     * {@code performanceRunRequested()} that lost T169. A campaign added to
     * {@link #CAMPAIGNS} participates in performance routing immediately; there
     * is no second place to remember.
     */
    public static boolean performanceMarkerPresent() {
        for (Campaign campaign : CAMPAIGNS) {
            if (Files.exists(Path.of(campaign.markerFileName()))) {
                return true;
            }
        }
        return false;
    }

    /** Every campaign that drives the arm sweep. */
    public static List<Campaign> evaluationCampaigns() {
        return CAMPAIGNS.stream().filter(Campaign::drivesArmMatrix).toList();
    }
}
