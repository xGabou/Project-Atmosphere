package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/**
 * Selects which separately linked build of the volumetric cloud raymarch a
 * frame binds.
 *
 * <p>T161 splits one monolithic program into two. Both are compiled from the
 * same {@code cloud_atmosphere_volume.fsh}; they differ only in whether the
 * dormant diagnostic selectors are uniforms or compile-time constants.
 *
 * <p>{@link #LEAN_FINAL} is what ordinary rendering uses. Its diagnostic,
 * oracle, trace, legacy and alternate-output paths are statically unreachable,
 * so the driver eliminates them before allocating registers instead of keeping
 * their control flow live inside the march loop. It can only be bound on frames
 * whose uploads would have matched those constants exactly - see
 * {@code VolumetricCloudRenderer.leanFinalEligible()}.
 *
 * <p>{@link #DIAGNOSTIC_MONOLITH} is the unmodified program. Nothing was
 * removed from it: every historical diagnostic campaign still selects it and
 * still drives the same uniforms it always did. It is deliberately never the
 * silent fallback for a FINAL frame, because binding it there would restore the
 * full cost this task exists to remove without changing the image.
 *
 * <p>The experimental evidence for the split is {@code e301494} on
 * {@code experiment/core-cost}: on one PLAY_VIS_NEAR/Ultra fixture the
 * specialized program rendered a pixel-identical image at 2.984x the speed of
 * the monolith. That branch is evidence, not an implementation to merge.
 */
public enum CoreCostDiagnosticProgram {
    /** The unmodified program retaining every dormant diagnostic path. */
    DIAGNOSTIC_MONOLITH("diagnostic_monolith"),
    /** The compile-time-specialized normal renderer used by FINAL frames. */
    LEAN_FINAL("lean_final"),
    /**
     * T140 diagnostic: the lean renderer plus a conservative whole-pixel
     * rejection oracle. Rays that provably cannot reach cloud return the
     * renderer's own no-cloud result instead of marching. Diagnostic only - the
     * oracle is guarded by PA_T140_ORACLE, which FINAL never defines.
     */
    T140_PIXEL_ORACLE("t140_pixel_oracle"),
    /**
     * T140 diagnostic: renders the oracle's verdict rather than the scene, so
     * potential cloud coverage can be counted per fixture from the captured
     * image. Opaque where the pixel could reach cloud, transparent where it
     * provably could not.
     */
    T140_MASK("t140_mask"),
    /** T140 diagnostic: the oracle applied per 8x8 tile instead of per pixel. */
    T140_TILE8("t140_tile8"),
    /** T140 diagnostic: the oracle applied per 16x16 tile instead of per pixel. */
    T140_TILE16("t140_tile16"),
    /**
     * T162 production-context arm: the real raymarch with the light cone and
     * scatter chain compiled out (the T136 constant-lighting arm, specialized
     * rather than branched).
     */
    T162_NO_LIGHT("t162_nolight"),
    /** T162 production-context arm: the real raymarch with rain shafts compiled out. */
    T162_NO_RAIN("t162_norain"),
    /**
     * T162 fixed-work attribution ladder. Every arm evaluates the same 64
     * points per fragment, so control flow is identical and the deltas between
     * consecutive arms are attributable to the one cost class each adds.
     */
    T162_FW1_ADDRESS("t162_fw1_address"),
    T162_FW2_CANDIDATE("t162_fw2_candidate"),
    T162_FW3_DESCRIPTOR("t162_fw3_descriptor"),
    T162_FW4_SHAPE("t162_fw4_shape"),
    T162_FW5_NODETAIL("t162_fw5_nodetail"),
    T162_FW6_NORAIN("t162_fw6_norain"),
    T162_FW7_DENSITY("t162_fw7_density"),
    /**
     * T163 measurement baseline: the FINAL program as it was before the
     * precipitation specialization, kept so the optimization can be compared
     * back to back against the program it replaced rather than against a
     * remembered number from an earlier session.
     */
    T163_WITH_RAIN("t163_withrain"),

    /**
     * T166 production-context attribution arms. Each is the shipped FINAL
     * program - precipitation specialization included - with exactly one cost
     * class compiled out, so a delta against FINAL is attributable to that
     * class alone.
     *
     * <p>They exist separately from the T162 arms above because those predate
     * T163 and still carry the unreachable precipitation branch. Measuring one
     * of them against today's FINAL would charge the removed rain-carry cost to
     * whatever else the arm changed.
     */
    T166_NO_LIGHT("t166_nolight"),
    T166_NO_DETAIL("t166_nodetail"),
    /** PM idea C, one lighting lever per arm. */
    T166_LIGHT_NO_DETAIL("t166_lightnodetail"),
    T166_LIGHT_STEPS2("t166_lightsteps2"),
    T166_LIGHT_WIDE("t166_lightwide"),
    T166_LIGHT_EARLY_OUT("t166_lightearlyout"),
    T166_LIGHT_CHEAP("t166_lightcheap"),
    /** PM ideas A, B, D, E and F. */
    T166_DISTANCE_STEP("t166_diststep"),
    T166_EMPTY_JUMP("t166_emptyjump"),
    T166_DISTANCE_LOD("t166_distlod"),
    T166_EARLY_TERM("t166_earlyterm"),
    T166_NO_SCENE_LIMIT("t166_noscenelimit"),
    /** The three top-ranked levers together, so overlap is measured. */
    T166_STACK("t166_stack"),
    /**
     * T166 fixed-work ladder: the T162 ladder rebuilt on the post-T163 density
     * call. Every arm evaluates the same 64 points per fragment, so the deltas
     * between consecutive rungs are attributable to the one class each adds.
     */
    T166_FW1_ADDRESS("t166_fw1_address"),
    T166_FW2_CANDIDATE("t166_fw2_candidate"),
    T166_FW3_DESCRIPTOR("t166_fw3_descriptor"),
    T166_FW4_SHAPE("t166_fw4_shape"),
    T166_FW5_NODETAIL("t166_fw5_nodetail"),
    T166_FW6_DENSITY("t166_fw6_density"),
    /**
     * T166 re-derivation of the T153 empty-space oracle on a lean program. The
     * historical 1.63x was measured on the pre-T161 monolith and is not a
     * current number; these arms make it one. Each keeps {@code PaOraclePass},
     * {@code PaOracleBaseSize} and the interval sampler, because one program
     * runs both the untimed capture pass and the timed replay.
     */
    T166_ORACLE_EMPTY("t166_oracle_empty"),
    T166_ORACLE_INTERVALS("t166_oracle_intervals"),
    T166_ORACLE_COMBINED("t166_oracle_combined"),

    /**
     * T167 graded exterior fine-step curves. T166's distance-step arm was the
     * fastest thing measured and also the only one that lost thin material;
     * these trade parts of its growth back for that material.
     */
    T167_CURVE_A_LATE("t167_curve_a_late"),
    T167_CURVE_B_SMOOTH("t167_curve_b_smooth"),
    T167_CURVE_C_CAPPED("t167_curve_c_capped"),
    T167_CURVE_D_FOOTPRINT("t167_curve_d_footprint"),
    /**
     * The footprint curve with the empty-span scan held at production spacing.
     * The scan's safety argument is that it probes the lattice the fine march
     * would have sampled; widening the fine step widens that lattice too, so
     * this separates scan-lattice misses from coarser integration.
     */
    T167_CURVE_D_SCANFIXED("t167_curve_d_scanfixed"),
    /**
     * T167 nearest-K descriptor owner cap. Skipped descriptors still feed the
     * conservative clearance, so the march stays safe; only the exact SDF and
     * the ordered union are avoided.
     */
    T167_K1("t167_k1"),
    T167_K2("t167_k2"),
    T167_K3("t167_k3"),
    T167_K4("t167_k4"),
    T167_K6("t167_k6"),
    /** T167 early-termination thresholds between production's 0.015 and T166's 0.06. */
    T167_TERM030("t167_term030"),
    T167_TERM045("t167_term045"),
    /**
     * The combined stacks, composed only of arms that actually passed their own
     * measurement. Nearest-K is absent from both: it was slower than FULL at
     * every K and its seam index rose monotonically as owners were dropped.
     */
    T167_STACK_BALANCED("t167_stack_balanced"),
    T167_STACK_SAFE("t167_stack_safe");

    private final String serializedName;

    CoreCostDiagnosticProgram(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    /**
     * True for the programs that produce a normal cloud frame, and so may
     * publish this frame as the next frame's history.
     *
     * <p>The pixel and tile oracles qualify: they reject only rays that
     * provably find no density, and emit exactly the no-cloud result the
     * renderer itself emits for such a ray, so their output is the production
     * image. Keeping them here also keeps the temporal state identical across
     * the T140 arms, which is what makes their timings comparable. The mask
     * program renders a classification, not a scene, and must never become
     * history.
     */
    public boolean normalProductionOutput() {
        return this == LEAN_FINAL || this == T140_PIXEL_ORACLE
                || this == T140_TILE8 || this == T140_TILE16;
    }

    /** True for a fixed-work ladder arm, which renders a checksum, not a scene. */
    public boolean fixedWork() {
        return name().startsWith("T162_FW") || name().startsWith("T166_FW");
    }

    /**
     * True for the T166 arms that replay the T153 ground-truth oracle. These
     * need the renderer's untimed capture pass to run before the timed draw,
     * which the optimization-mode upload still drives.
     */
    public boolean t153OracleReplay() {
        return this == T166_ORACLE_EMPTY || this == T166_ORACLE_INTERVALS
                || this == T166_ORACLE_COMBINED;
    }

    /**
     * The generated program this arm binds. Every diagnostic build is emitted
     * from the one production source as {@code cloud_atmosphere_volume_<name>},
     * except the monolith itself and the lean FINAL program, whose resource
     * name predates the serialized labels.
     */
    public String resourceName() {
        return switch (this) {
            case DIAGNOSTIC_MONOLITH -> "cloud_atmosphere_volume";
            case LEAN_FINAL -> "cloud_atmosphere_volume_final";
            case T140_PIXEL_ORACLE -> "cloud_atmosphere_volume_t140_pixel";
            case T140_MASK -> "cloud_atmosphere_volume_t140_mask";
            case T140_TILE8 -> "cloud_atmosphere_volume_t140_tile8";
            case T140_TILE16 -> "cloud_atmosphere_volume_t140_tile16";
            default -> "cloud_atmosphere_volume_" + serializedName;
        };
    }

    public static CoreCostDiagnosticProgram parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        for (CoreCostDiagnosticProgram program : values()) {
            if (program.serializedName.equals(normalized)) {
                return program;
            }
        }
        return null;
    }
}
