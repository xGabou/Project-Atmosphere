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
    T167_STACK_SAFE("t167_stack_safe"),

    /**
     * T168 footprint step LOD. Grades the exterior fine step by the projected
     * size of a sample rather than by distance / MaxRenderDistance, which is
     * what made the T167 curves swing by pose. The growth is linear in ray
     * distance once the projection algebra is done, so the march loop pays one
     * multiply and one clamp - not the division that cost T167 27% of a frame.
     */
    T168_FOOTPRINT_CONSERVATIVE("t168_fp_conservative"),
    T168_FOOTPRINT_BALANCED("t168_fp_balanced"),
    T168_FOOTPRINT_AGGRESSIVE("t168_fp_aggressive"),
    /**
     * T168 combined stacks: a footprint curve plus termination 0.045. Two are
     * built so the winning curve does not have to be guessed before the run.
     */
    T168_STACK("t168_stack"),
    T168_STACK_BALANCED("t168_stack_balanced"),

    /** T169 lighting arms: conservative tap reduction and a real cone early-out. */
    T169_LIGHT_STEPS5("t169_lightsteps5"),
    T169_LIGHT_STEPS4("t169_lightsteps4"),
    T169_LIGHT_EARLY_OUT2("t169_lightearlyout2"),
    /** T169 detail arms: footprint-gated detail, and the all-detail-off ceiling. */
    T169_DETAIL_FP_CONSERVATIVE("t169_detailfp_conservative"),
    T169_DETAIL_FP_BALANCED("t169_detailfp_balanced"),
    T169_DETAIL_FP_AGGRESSIVE("t169_detailfp_aggressive"),
    T169_NO_DETAIL("t169_nodetail"),
    /** T169 Task 5 stacks: T168 winners plus the lighting and detail candidates. */
    T169_STACK_SAFE("t169_stack_safe"),
    T169_STACK_FAST("t169_stack_fast"),

    // -----------------------------------------------------------------------
    // T170. The primary march. T169 bounded lighting at 19.5% and detail at
    // 15.2% of SIDE cloud cost; with both at zero SIDE would still cost
    // ~14.7 ms against a 10 ms budget, so the march and the descriptor work
    // each primary density sample pays are the only remaining class large
    // enough to close it.
    // -----------------------------------------------------------------------

    /**
     * T170 Task 1, run 2: the clamp tightened below the shipped 4.
     *
     * <p>Run 1 measured the sweep upward and found the opposite of what the
     * task expected. fpmax5, fpmax6 and fpmax8 render byte-identical images, so
     * the clamp stops binding above ~5 and there is nothing left to release;
     * and fpmax4, where it does bind, was 41% faster than all three at FAR.
     * Larger steps cost time here rather than saving it, so run 2 sweeps down.
     */
    T170_FPMAX3("t170_fpmax3"),
    /**
     * T170 Task 6, run 2: the candidate stack plus the descriptor hoist.
     *
     * <p>A ceiling, not a candidate. The hoist arm substitutes a constant edge
     * width, so this renders an invalid image; a real hoist would compute the
     * same edge width once per descriptor per frame instead of once per
     * descriptor per sample and would be image-identical. This arm answers the
     * only question that matters before building that: whether the stack plus a
     * perfect hoist would reach the SIDE budget at all.
     */
    T170_STACK_HOIST("t170_stack_hoist"),

    /** T170 Task 6, run 2: the candidate stack with the clamp at 3. */
    T170_STACK3("t170_stack3"),

    /** T170 Task 1: the T169 stack_fast footprint with the step clamp at 4. */
    T170_FPMAX4("t170_fpmax4"),
    /** T170 Task 1: the same, clamp raised to 5. */
    T170_FPMAX5("t170_fpmax5"),
    /** T170 Task 1: the same, clamp raised to 6. */
    T170_FPMAX6("t170_fpmax6"),
    /** T170 Task 1: the same, clamp raised to 8. */
    T170_FPMAX8("t170_fpmax8"),

    /**
     * T170 Task 3 oracle A. Descriptor payload fetches replaced by one cached
     * read hoisted out of the group walk. Iteration count, control shape and
     * every downstream arithmetic operation are preserved, so the delta is
     * descriptor texture traffic and nothing else.
     *
     * <p>Visually invalid by construction. This is a ceiling, never a
     * production candidate.
     */
    T170_DESC_CONST_FETCH("t170_desc_constfetch"),
    /**
     * T170 Task 2/4. Edge width replaced by a constant. The shipped form,
     * {@code stormEdgeWidthBlocksFromData}, takes no sample position - it is a
     * pure function of the descriptor and its role - yet it is recomputed for
     * every descriptor at every density sample. This arm bounds what hoisting
     * it to once per descriptor per frame could return.
     */
    T170_DESC_CONST_EDGE("t170_desc_constedge"),
    /**
     * T170 Task 2. The ownership ellipse's two {@code length()} extent terms
     * replaced by the raw radii. Also descriptor-invariant, also recomputed per
     * sample.
     */
    T170_DESC_CHEAP_OWNERSHIP("t170_desc_cheapowner"),
    /**
     * T170 Task 2. The exact descriptor SDF replaced by the conservative lower
     * bound already computed for the T121 rejection test. Bounds the exact-SDF
     * share of a primary density sample.
     */
    T170_DESC_NO_EXACT_SDF("t170_desc_nosdf"),
    /**
     * T170 Task 2. The ordered smooth union replaced by a hard minimum, so the
     * blend-radius and blend-factor chain is removed but every distance is
     * still evaluated.
     */
    T170_DESC_HARD_UNION("t170_desc_hardunion"),
    /**
     * T170 Task 4. Both descriptor-invariant terms removed at once - edge width
     * and the ownership extents. The realistic hoist target, and the sum whose
     * parts {@link #T170_DESC_CONST_EDGE} and {@link #T170_DESC_CHEAP_OWNERSHIP}
     * measure separately.
     */
    T170_DESC_HOIST("t170_desc_hoist"),

    /** T170 Task 6: the T169 stack_fast carried forward with the clamp at 6. */
    T170_STACK("t170_stack"),
    /** T170 Task 6: the same stack with the clamp at 8. */
    T170_STACK8("t170_stack8"),

    // -----------------------------------------------------------------------
    // T171. Harness stability. T170 measured a 13% spread across arms that
    // render byte-identical images, which is larger than most of the effects
    // the line is now trying to resolve. These two are generated from the same
    // source with the same defines as FINAL - byte-identical GLSL, separately
    // linked GL programs - so any difference between them and LEAN_FINAL is
    // attributable to linking and program identity, not to shader logic.
    // -----------------------------------------------------------------------

    /** T171 program-identity control A: byte-identical to FINAL, separately linked. */
    T171_DUP_A("t171_dup_a"),
    /** T171 program-identity control B: byte-identical to FINAL, separately linked. */
    T171_DUP_B("t171_dup_b"),

    // -----------------------------------------------------------------------
    // T172. The real descriptor-invariant precompute, as opposed to T170's
    // compile-time ceilings which approximated the values away. These consume
    // the same numbers the walk used to derive, computed once when the
    // descriptor was built and carried in texel 3's previously unused channels,
    // so unlike the ceilings they are image-equivalent.
    // -----------------------------------------------------------------------

    /** T172 B: edge width read from the descriptor instead of recomputed. */
    T172_PRE_EDGE("t172_pre_edge"),
    /** T172 C: ownership radii read from the descriptor instead of recomputed. */
    T172_PRE_OWNER("t172_pre_owner"),
    /** T172 D: both invariants read from the descriptor. */
    T172_PRE_BOTH("t172_pre_both"),
    /** T172 E: the validated T169 stack plus the real precompute. */
    T172_STACK_PRE("t172_stack_pre"),

    // -----------------------------------------------------------------------
    // T173. The T121 conservative bound. FINAL bakes the diagnostic mode to 0,
    // so production has always used the vertical-only lower bound and the
    // tighter box bound T141 built has been dead code in the shipped program.
    // These arms make it reachable at compile time. It is exact: a valid lower
    // bound cannot cull a descriptor that could contribute, so the image must
    // be bit-identical and only the exact-SDF count may fall.
    // -----------------------------------------------------------------------

    /** T173 B: the tighter T141 box bound, alone. */
    T173_BOX_BOUND("t173_boxbound"),
    /** T173 D: the tighter bound plus the T172 descriptor precompute. */
    T173_BOX_PRE("t173_boxbound_pre"),
    /** T173 E: the shipping stack plus both. */
    T173_STACK_BOX_PRE("t173_stack_boxbound_pre"),

    /**
     * T174 control: FINAL with the descriptor precompute explicitly compiled
     * out.
     *
     * <p>Once the precompute is in FINAL there is no other way to measure what
     * it is worth, and no other way to prove the shipped program actually
     * consumes the precomputed fields: this arm and FINAL must render the same
     * image and differ only in speed.
     */
    T174_NO_PRECOMPUTE("t174_no_precompute"),

    /**
     * T174 ceiling A: only the first entered descriptor group is processed.
     *
     * <p>Removes every cost of groups 2+ at once - candidate scan, ten-descriptor
     * walk, bounds, exact SDFs and the group union. This is the whole
     * group-entry prize, and Task 4's gate: below ~1.10x at SIDE the line closes
     * without a structure being designed.
     */
    T174_FIRST_GROUP_ONLY("t174_first_group_only"),
    /**
     * T174 ceiling B: groups 2+ are entered and unioned, but their exact SDF is
     * replaced by the bound. The gap to ceiling A separates what entering a
     * group costs from what the exact SDFs inside it cost.
     */
    T174_GROUP2_NO_SDF("t174_group2_no_sdf"),

    // -----------------------------------------------------------------------
    // T175. Whether the primary march's density calls are necessary.
    //
    // T174's 25.37 calls per pixel at SIDE counted light-march taps as well;
    // the primary march makes 11.28, on 36.6% of its steps. These two arms
    // price the only two ways that number can fall: sample occupied material
    // less often, or stop letting irrelevant groups shorten the safe advance.
    // -----------------------------------------------------------------------

    /**
     * T175 ceiling: every second primary step reuses the previous body density.
     *
     * <p>Halves primary density calls while leaving step sizes, the march
     * structure and the light march untouched, so it prices occupied-material
     * sampling reduction on its own. A nearest-neighbour hold, not the
     * interpolation a real design would use - visually invalid, oracle only.
     */
    T175_DENSITY_EVERY_2("t175_density_every2"),
    /**
     * T175 oracle: only the first entered group may constrain the safe advance,
     * while every group's density contribution is still evaluated.
     *
     * <p>T174 proved groups 2+ never change the density result on this fixture.
     * They may still shorten the march. Unsafe by construction - a real ray
     * could step over material a suppressed group owns - so this measures the
     * prize without being a candidate.
     */
    T175_CLEARANCE_FIRST_GROUP("t175_clearance_first_group"),

    // -----------------------------------------------------------------------
    // T176. The light march, measured fresh.
    //
    // T175 found views 35/36 dead since T169, so no T169 tap-count conclusion
    // is reused here. The cap is now understood from the code: steps =
    // clamp(LightSteps, 2, 8) then min(steps, 4) when the camera starts inside
    // the slab, and the tap loop has no transmittance early-out in production.
    // Four taps is a hard cap reached every time, not convergence - which is
    // also why T169's 6 -> 5 -> 4 arms were bit-identical at SIDE.
    // -----------------------------------------------------------------------

    /**
     * T176 absolute ceiling: lighting removed entirely.
     *
     * <p>Every other light-march optimisation - tap reduction, primary-group
     * reuse, a cheaper shadow density - is a strict subset of this. If this does
     * not clear the gap, none of them can, and the line closes by arithmetic
     * rather than by building each one.
     */
    T176_NO_LIGHT("t176_nolight"),
    /** T176 tap ceiling: 4 -> 3. */
    T176_LIGHT3("t176_light3"),
    /** T176 tap ceiling: 4 -> 2, the floor that still keeps a light direction. */
    T176_LIGHT2("t176_light2"),

    // T176 Task 8. The validated stack bakes PA_ARM_LIGHT_STEPS 4, and the
    // inside-slab cap already forces 4, so the stack carries no light
    // reduction whatsoever. Anchor-relative light ratios cannot be multiplied
    // onto it; these measure the light march on the stack directly.

    /** T176 stack ceiling: the whole light march removed, on the stack. */
    T176_STACK_NO_LIGHT("t176_stack_nolight"),

    /** T176 stack, four taps down to three. */
    T176_STACK_LIGHT3("t176_stack_light3"),

    /** T176 stack, four taps down to two. */
    T176_STACK_LIGHT2("t176_stack_light2"),

    // -----------------------------------------------------------------------
    // T177. Primary-to-light group reuse. A light tap samples 28 units along
    // LightDir from a point whose descriptor group the primary sample just
    // resolved. These arms price skipping the tap's own candidate resolution
    // and walking that group directly.
    //
    // Reuse cannot remove the ten-lobe group field evaluation itself - only the
    // candidate texel fetch, the four-rank scan and any second group - so the
    // hard arm bounds the whole idea. Whether it is ALSO correct is a separate
    // question the reuse-validity counters answer.
    // -----------------------------------------------------------------------

    /** T177 Task 3: every light tap forced to reuse the primary group. */
    T177_REUSE_HARD("t177_reuse_hard"),

    /** T177 Task 7: the validated stack, flat light3, and hard reuse. */
    T177_STACK_REUSE_HARD("t177_stack_reuse_hard"),

    // -----------------------------------------------------------------------
    // T179. Dominance pruning. stormSmoothMinimum is exactly unchanged once the
    // incoming distance reaches d_cur + blend, and production has been testing
    // that condition against the 48-block global cap instead of this pair's
    // actual blend radius. Tightening it costs no new data and no division.
    // -----------------------------------------------------------------------

    /** T179 candidate: the exact per-pair dominance threshold. */
    T179_DOM_EXACT("t179_dom_exact"),

    /** T179 ceiling: no blend margin, image-invalid, bounds the family. */
    T179_DOM_AGGRESSIVE("t179_dom_aggressive"),

    /** T179 Task 9: the fresh stack with dominance pruning, no light3. */
    T179_STACK_DOM("t179_stack_dom"),

    // -----------------------------------------------------------------------
    // T180. Decomposing T175's "48% segment/probe/quadrature". The production
    // consumers turn out to be the empty-span probe scan and the bracket
    // bisection, both of which only compare density against 0.0008.
    // -----------------------------------------------------------------------

    /** T180 ceiling: the whole empty-span probe scan removed uniformly. */
    T180_NO_PROBE("t180_noprobe"),

    /** T180 ceiling: the four bracket bisections removed uniformly. */
    T180_NO_BRACKET("t180_nobracket"),

    /** T180 candidate: probes keep running without subtractive detail. */
    T180_PROBE_NO_DETAIL("t180_probe_nodetail"),

    /** T180 Task 9: the quality-approved stack plus the probe candidate. */
    T180_STACK_PROBE("t180_stack_probe"),

    // -----------------------------------------------------------------------
    // T181. Uniform caps on the empty-span scan, and the first price on the
    // march's union-distance refinement - the larger consumer, unmeasured
    // until now.
    // -----------------------------------------------------------------------

    /** T181: empty-span scan capped at 8 probes. */
    T181_PROBE8("t181_probe8"),

    /** T181: capped at 4. */
    T181_PROBE4("t181_probe4"),

    /** T181: capped at 2. */
    T181_PROBE2("t181_probe2"),

    /** T181 ceiling: the union-distance refinement removed uniformly. */
    T181_NO_REFINE("t181_norefine"),

    /** T181 Task 10: the quality-safe stack with the scan capped at 8. */
    T181_STACK_PROBE8("t181_stack_probe8"),

    // -----------------------------------------------------------------------
    // T182. Closing the attribution. The residual T180 mis-assigned and T181
    // could not name is the rain-segment reachability test, which runs every
    // coarse step regardless of whether any rain is rendered.
    // -----------------------------------------------------------------------

    /** T182 ceiling: the rain-segment reachability test removed uniformly. */
    T182_NO_RAIN_SEGMENT("t182_norainseg"),

    /** T182 Task 8: the stack with T181's best probe cap. */
    T182_STACK_PROBE4("t182_stack_probe4"),

    /** T182: the stack with cap 4 and the rain-segment test removed. */
    T182_STACK_PROBE4_NORAINSEG("t182_stack_probe4_norainseg"),

    // -----------------------------------------------------------------------
    // T184. Rain-support recomputation. localRainSupportAt is exactly
    // column-invariant within a frame, so an identical XZ can reuse the
    // previous result without approximating anything. Rain still renders.
    // -----------------------------------------------------------------------

    /** T184: one-entry exact-XZ reuse on the rain support query. */
    T184_REUSE_EXACT("t184_reuse_exact"),

    /** T184: the quality-approved stack with exact rain-support reuse. */
    T184_STACK_REUSE("t184_stack_reuse"),

    // -----------------------------------------------------------------------
    // T185. Rain ownership pruning. The question is whether the shipped
    // envelope is too coarse - and, first, whether its geometric half is even
    // the half that fails.
    // -----------------------------------------------------------------------

    /** T185 candidate: per-axis extents and a box test, both inflations removed. */
    T185_TIGHT_PRUNE("t185_tight_prune"),

    /** T185 ceiling: the exact per-ellipse ownership test as the prune. */
    T185_EXACT_PRUNE("t185_exact_prune"),

    /** T185 Task 10: the quality-approved stack with the tightened prune. */
    T185_STACK_TIGHT("t185_stack_tight"),

    // -----------------------------------------------------------------------
    // T186. One rain-support sample per coarse segment instead of two. This is
    // the last rain experiment: a uniform trip-count reduction rather than
    // another conditional skip.
    // -----------------------------------------------------------------------

    /** T186: single sample at the segment midpoint. */
    T186_ONE_MID("t186_one_mid"),

    /** T186: single sample at the first Gauss node. */
    T186_ONE_A("t186_one_a"),

    /** T186: single sample at the second Gauss node. */
    T186_ONE_B("t186_one_b"),

    /** T186: the quality-approved stack with the midpoint sample. */
    T186_STACK_ONE_MID("t186_stack_one_mid"),

    // -----------------------------------------------------------------------
    // T187. The precomputed rain-support field. This arm prices the ceiling
    // before any field is built: it makes the descriptor traversal free, which
    // no real lookup can beat.
    // -----------------------------------------------------------------------

    /** T187 ceiling: the rain-support descriptor traversal costs nothing. */
    T187_FIELD_ORACLE("t187_field_oracle"),

    /** T187: the quality-approved stack with the same ceiling applied. */
    T187_STACK_FIELD("t187_stack_field"),

    // -----------------------------------------------------------------------
    // T188. The REAL rain-support field. Generation and lookup are the same
    // program, so the stored triple is what the ray would have computed rather
    // than a reimplementation that has to be proven equal to it.
    // -----------------------------------------------------------------------

    /** T188: the field generated every frame and read by the march. */
    T188_FIELD_REAL("t188_field_real"),

    /**
     * T188 exactness arm: generates the field and still marches the exact
     * descriptor path, so one frame carries both the ground truth and the
     * field that has to reproduce it.
     */
    T188_FIELD_GENERATE_ONLY("t188_field_generate_only"),

    /** T188: the quality-approved stack with the real field composed in. */
    T188_STACK_FIELD("t188_stack_field"),

    /**
     * T188 Task 0 reference: the rain-only capture from the exact descriptor
     * path. Renders rain optical mass, onset and termination height and the
     * run count instead of the composited frame.
     */
    T188_RAIN_MASK_REF("t188_rain_mask_ref"),

    /** T188 Task 0 candidate: the same rain-only capture, read from the field. */
    T188_RAIN_MASK_FIELD("t188_rain_mask_field"),

    // -----------------------------------------------------------------------
    // T189. Ownership is discrete, so it is fetched discretely. The T188 arms
    // above keep the bilinear ownership that produced 10.6-15.6% false rain,
    // because a fix needs the defect it is measured against.
    // -----------------------------------------------------------------------

    /** T189: the field with texelFetch ownership. The candidate. */
    T189_FIELD_NEAREST("t189_field_nearest"),

    /** T189: the quality-approved stack with the corrected field. */
    T189_STACK_NEAREST("t189_stack_nearest"),

    /** T189: rain-only capture with discrete ownership. */
    T189_RAIN_MASK_NEAREST("t189_rain_mask_nearest"),

    /** T189 Task 5 diagnostic: the erosion side, a strict bilinear threshold. */
    T189_RAIN_MASK_STRICT("t189_rain_mask_strict"),

    // -----------------------------------------------------------------------
    // T190. The conservative field. Cells the build cannot prove uniform are
    // answered by the exact evaluation, so field approximation stops being a
    // source of rain error at all rather than being made smaller - which is the
    // only fix that survives being amplified sixty-fold by an existence test.
    // -----------------------------------------------------------------------

    /** T190: the conservative field with an exact mixed-cell fallback. */
    T190_FIELD_SAFE("t190_field_safe"),

    /** T190: the quality-approved stack with the conservative field. */
    T190_STACK_SAFE("t190_stack_safe"),

    /** T190: rain-only capture through the conservative field. */
    T190_RAIN_MASK_SAFE("t190_rain_mask_safe"),

    // -----------------------------------------------------------------------
    // T192. The closed-form ownership bound triages classification: cells
    // provably outside every ownership ellipse are proven dry by one squared
    // inequality per lobe instead of four exact evaluations.
    // -----------------------------------------------------------------------

    /** T192: the conservative field with closed-form triage. */
    T192_FIELD_BOUND("t192_field_bound"),

    /** T192: the quality-approved stack with the triaged field. */
    T192_STACK_BOUND("t192_stack_bound"),

    /** T192: rain-only capture through the triaged field. */
    T192_RAIN_MASK_BOUND("t192_rain_mask_bound"),

    // -----------------------------------------------------------------------
    // T194. Pricing a shared 3D storm field for light and probe. Nothing is
    // built and nothing is wired into the renderer: one arm measures the whole
    // budget available to pay for a field, three price what generating one of
    // a given size would cost.
    // -----------------------------------------------------------------------

    /** T194: light AND probe descriptor traversal removed together. */
    T194_NO_BOTH("t194_noboth"),

    /** T194 build oracle: 512x512x16 voxel evaluations per frame. */
    T194_SLICES16("t194_slices16"),

    /** T194 build oracle: 512x512x32. */
    T194_SLICES32("t194_slices32"),

    /** T194 build oracle: 512x512x64. */
    T194_SLICES64("t194_slices64"),

    // -----------------------------------------------------------------------
    // T195. The lookup side of the shared field. Light and probe traversal
    // removed as T194_NO_BOTH removes them, and each consumer then pays one
    // stand-in volume fetch per density it would have computed, so the
    // ratio against the anchor is the net ray-side gain a field can keep.
    // -----------------------------------------------------------------------

    /** T195: light AND probe traversal removed, stand-in field fetches paid. */
    T195_LOOKUP("t195_lookup");


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
    /**
     * True for the arms whose program keeps {@code PaRainFieldPass} live. The
     * renderer issues the untimed generation draw only for these; every other
     * program bakes the uniform to 0 and has no generation branch compiled in,
     * so the extra pass would render into a target nothing reads.
     */
    public boolean rainFieldGeneration() {
        return this == T188_FIELD_REAL || this == T188_FIELD_GENERATE_ONLY
                || this == T188_STACK_FIELD || this == T188_RAIN_MASK_FIELD
                || this == T189_FIELD_NEAREST || this == T189_STACK_NEAREST
                || this == T189_RAIN_MASK_NEAREST
                || this == T189_RAIN_MASK_STRICT
                || this == T190_FIELD_SAFE || this == T190_STACK_SAFE
                || this == T190_RAIN_MASK_SAFE
                || this == T192_FIELD_BOUND || this == T192_STACK_BOUND
                || this == T192_RAIN_MASK_BOUND
                // T194 oracles generate but never read: the pass exists
                // to be timed, and its output is deliberately discarded.
                || this == T194_SLICES16 || this == T194_SLICES32
                || this == T194_SLICES64;
    }

    /**
     * True for the arms that also READ the field. GENERATE_ONLY deliberately
     * does not: it pays the build and still walks the descriptors, which is
     * what makes it the ground truth the field is measured against.
     */
    public boolean rainFieldLookup() {
        return this == T188_FIELD_REAL || this == T188_STACK_FIELD
                || this == T188_RAIN_MASK_FIELD
                || this == T189_FIELD_NEAREST || this == T189_STACK_NEAREST
                || this == T189_RAIN_MASK_NEAREST
                || this == T189_RAIN_MASK_STRICT
                || this == T190_FIELD_SAFE || this == T190_STACK_SAFE
                || this == T190_RAIN_MASK_SAFE
                || this == T192_FIELD_BOUND || this == T192_STACK_BOUND
                || this == T192_RAIN_MASK_BOUND;
    }

    /**
     * True for the arms whose certainty flag is written by the build and
     * honoured by the lookup, so a cell whose rain-existence decision could not
     * be proven uniform is answered by the exact evaluation instead.
     */
    public boolean rainFieldConservative() {
        return this == T190_FIELD_SAFE || this == T190_STACK_SAFE
                || this == T190_RAIN_MASK_SAFE
                || this == T192_FIELD_BOUND || this == T192_STACK_BOUND
                || this == T192_RAIN_MASK_BOUND;
    }

    /**
     * True for the arms that triage classification with the closed-form
     * ownership bound before falling back to corner sampling.
     */
    public boolean rainFieldClosedForm() {
        return this == T192_FIELD_BOUND || this == T192_STACK_BOUND
                || this == T192_RAIN_MASK_BOUND;
    }

    /**
     * True for the arms whose fragment output is the rain-only capture rather
     * than a rendered frame. Their images are comparable to each other and to
     * nothing else, so the harness must not measure them against the RGB
     * anchor every other arm uses.
     */
    public boolean rainMaskCapture() {
        return this == T188_RAIN_MASK_REF || this == T188_RAIN_MASK_FIELD
                || this == T189_RAIN_MASK_NEAREST
                || this == T189_RAIN_MASK_STRICT
                || this == T190_RAIN_MASK_SAFE
                || this == T192_RAIN_MASK_BOUND;
    }

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

    /**
     * T191. The campaign this program belongs to, or null for the two
     * production programs.
     *
     * <p>Derived from the serialized name's {@code tNNN_} prefix and resolved
     * against {@link StormCampaignRegistry}, so a new arm inherits its campaign
     * from its own name and there is no second table to keep in step. A program
     * whose prefix matches no registered campaign returns the prefix itself,
     * which the sandbox rejects - an arm no campaign can activate is an arm
     * that would never load.
     */
    public String campaignId() {
        int underscore = serializedName.indexOf('_');
        if (underscore <= 1 || serializedName.charAt(0) != 't') {
            return null;
        }
        String prefix = serializedName.substring(0, underscore);
        for (int index = 1; index < prefix.length(); index++) {
            if (!Character.isDigit(prefix.charAt(index))) {
                return null;
            }
        }
        for (StormCampaignRegistry.Campaign campaign
                : StormCampaignRegistry.CAMPAIGNS) {
            String id = campaign.id().toLowerCase(java.util.Locale.ROOT);
            if (id.equals(prefix) || id.startsWith(prefix + "_")) {
                return campaign.id();
            }
        }
        return prefix;
    }

    /**
     * True for the two programs ordinary rendering needs. Everything else is a
     * campaign arm and is loaded only while its campaign is active.
     */
    public boolean isProductionProgram() {
        return this == DIAGNOSTIC_MONOLITH || this == LEAN_FINAL;
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
