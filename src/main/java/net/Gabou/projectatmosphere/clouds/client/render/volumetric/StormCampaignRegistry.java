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
                    "t181Run", "T181_OPTIMIZATION_ARMS", Routing.EVALUATION_SWEEP));

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
            "t098-raytrace.txt");

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
