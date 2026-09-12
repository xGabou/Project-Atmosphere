# T203 - the rain gate: traversal leaves the segment test, and the family's cost was never mostly traversal

Architecture campaign on the rain segment test, the binding constraint T202
priced at 7.7-9.2 ms of a 12.7-13.8 ms ray. Five launches on 12Sep2026:
`t203-campaign/t203-run1.log` (17:25, the gate carried in the ray program -
codegen-invalid timing, valid Task 1 counters), `t203-run2.log` (17:36, the
build in its own program), `t203-run3.log` (17:44, the residual decomposed
by three bound arms), `t203-run4.log` (17:52, the attach bound's warp term
and the oracle's excess channels), `t203-run5.log` (17:58, the bounds'
rounding pad; the oracle clean). SIDE and PLAY_VIS_NEAR at 0.25 (480x270),
Ultra 96 steps, history off, one deterministic fixture a pose, every timed
arm bracketed by fresh FINAL anchors, three repeats of the candidate, 60
samples a cell, anchor drift <= 3% or the block is dropped.

No primary-field, probe-floor, light, temporal, resolution, micro-predicate
or per-lane change. FINAL is untouched: the gate is a campaign arm
(`t203_rain_gate`, FINAL's specialization with `PaRainGateField` baked 1)
and FINAL bakes it 0; the storm field (bits 43) is read exactly as before.

## RECOVERY

1. **Starting HEAD**: `0223802` (T202). Working tree carried the T195-T202
   logs, preserved.
2. **Current FINAL bits**: 43 (light 1, probe 2, exact-noise tap 8,
   refinement clearance 32). FINAL's ray this session: **12.34-12.73 ms**
   SIDE, **12.12-12.44** PLAY_VIS_NEAR (anchors of runs 2-4), field build
   1.65-2.0.
3. **Surviving rain-field infrastructure** (T188-T192, all still in source):
   `RainFieldSampler` on unit 15, `prepareRainFieldTarget` (weather-map-sized
   RGBA32F, 512^2 = 4 MB), the `PaRainFieldPass` build branch above main's
   march, `paRainFieldSupportAt` (the T188 lookup, `PaRainFieldEnabled`),
   the T190 corner classifier, the T192 closed-form ownership bound
   `paCellProvablyUnowned`, the census views 61-66, the T188 rain-mask
   harness (`t188_rain_mask_ref`, `PA_RAIN_MASK_OUTPUT`, `StormRainMaskMetrics`:
   IoU, false/missed rain, thin retention, continuity, onset/termination),
   `rainFieldForcedOnMonolith`, the rain-field GPU clock. Reused: the
   target, the unit, the pass, the closed-form bound, the harness, the
   clock (a second clock added for the gate). Not reused: the T188 lookup
   and the T190/T192 classifiers - the gate stores no rain answer.

## CURRENT RAIN COST (Task 1) - MEASURED, monolith at bits 43, gate off

Per frame at 480x270 (129,600 px), run 2's `|final` capture; run 1 agrees
within 5% (a different storm realization).

| | SIDE | PLAY_VIS_NEAR |
|---|---|---|
| 4. `rainSegmentMayContribute` calls / px | 4,310,346 = **33.3** | 2,760,958 = **21.3** |
| sample opportunities (2 a call) | 8,620,692 | 5,521,916 |
| rejected by the T145 height prune (no walk) | 6,009,928 = 69.7% | 1,889,671 = 34.2% |
| 5. `localRainSupportAt` calls / px (segment test) | 2,578,596 = **19.9** | 3,616,460 = **27.9** |
| pruned before traversal (T145 circle) | 273,857 = 10.6% | 146,483 = 4.1% |
| **walks** (`directStormRainSupportAt`) | **2,304,741** | **3,471,929** |
| walk: unowned column | 1,305,788 = 56.7% | 2,735,778 = 78.8% |
| walk: owned, no BASE lobe supports it | 521,857 = 22.6% | 406,166 = 11.7% |
| walk: owned, supported, union empty at the support height | **0** | **0** |
| walk: owned, coverage present, noise eroded it under the cutoff | 355,782 = 15.4% | 221,800 = 6.4% |
| walk: owned, supported above the cutoff (incl. the shaft's) | 253,616 = 11.0% | 159,774 = 4.6% |
| supported samples in the segment test | 129,389 | 99,762 |
| of which above the attach height (the window rejects) | **95,712 = 74%** | **83,638 = 84%** |
| of which below the window | 0 | 0 |
| 9. segment tests returning true | 33,705 = **0.78%** of calls | 16,514 = **0.60%** |
| 6. group walks (whole frame) | 2,503,726 | 1,694,249 |
| 7. lobe visits | 25,037,120 | 16,942,262 |
| 8. exact SDFs | 15,469,056 | 9,933,172 |
| shape calls tagged rain segment / rain shaft | 2,304,741 / 132,469 | 3,471,929 / 64,917 |
| 10. current ms ceiling (`t182_norainseg`, this session) | **7.53 / 7.57** | **7.85 / 8.26** |

Three facts shaped the gate. The "ring" - an owned column with a BASE
support whose union carries no coverage at the support height - **does not
exist**: a BASE lobe supports a column exactly when its envelope is non-zero
there, so the two conditions coincide and proof 3 below never fires. The
provable classes are therefore *unowned* and *no BASE support*, 79% (SIDE)
and 90% (PLAY_VIS_NEAR) of walks. The eroded class - coverage present, noise
took it under the cutoff - is 6-15% of walks and no geometric proof reaches
it. And of the supported samples 74-84% fail the height window *above* the
attach height, which a per-cell attach bound can reject without a walk where
the global T145 bound (max BASE base height) cannot.

## THE FIELD (Tasks 2-4)

**Column invariance (Task 2), reconfirmed on the current FINAL.**
`directStormRainSupportAt` is a function of worldXZ and frame uniforms: the
BASE walk at each lobe's own support height, the union at the
support-weighted height, no Y, no segment, no jitter (T184). The T202
consumers (bits 16/32) live in `cloudDensity` and the refinement; neither
touches `directStormShape` as the rain support calls it. The three
descriptor-derived outputs are *owned*, *directSupport*, *attachY*; the
raster terms (weather, morphology, precipitation) are fetched at the sample
and never come from any field.

**Representation (11-12).** The historical triple is not stored. One
RGBA32F texel per 8-block column cell (512^2, 4 MB, T188's target):

| channel | value |
|---|---|
| R | flag bits: 1 the cell is provably free of descriptor rain; 2 the raster precipitation may exceed 0.02 somewhere in the cell (any of the 3x3 morphology texels a point of the cell filters from) |
| G | a lower bound on the descriptor attach height over the cell, minus the segment test's 184-block window |
| B | an upper bound on that attach height |
| A | census: why the cell was proved dry (1 unowned, 2 no BASE support, 3 union empty), 0 not proved |

**The certainty rule (16) is one-sided, and that is the whole design.**
T188-T192 stored the column's rain answer and asked the ray to trust it; a
0.1-0.2% per-column error became 3-12% false rain through an OR over ~60
samples, and proving a cell *uniform* cost the gain. The gate never answers
"rain". A cell is marked dry only by a proof that no column in it can carry
descriptor rain, and the attach bounds are sound bounds; everything the
field cannot reject takes **production's own exact test**, unchanged. So
the fallback is not an added cost - it is today's cost on the samples the
field could not prove - and false rain is zero by construction, not by
tuning.

The proofs, per cell, in the build pass (`paRainGateBuildCell`):
1. *unowned*: T192's closed-form ownership bound, one squared inequality
   per lobe (0 false-safe on 40,000 trials);
2. *no BASE support*: a lobe supports a column iff its pseudo-distance at
   (column, its own support height) is under its softness. The distance at
   one fixed height is Lipschitz in XZ - a numeric sweep of the
   gradient-normalised ellipse form gives <= 1.03 outside the surface at
   aspect 2, and the domain warp adds at most 0.0008 blocks per block per
   block of radius - so a lobe whose distance at the cell centre clears
   softness + half-diagonal * (aspect + warp) * 1.2 supports nothing in the
   cell. The complement is the candidate set, and since the attach height is
   the support-weighted mean of the supporting lobes' base heights, its
   [min, max] over the candidate set bounds the attach everywhere in the
   cell (the convexity argument).
3. *union empty*: one build-mode walk at the cell centre and its support
   height, the storm field's own dilation over the half-diagonal and the
   candidate set's support-height span, the floor test of T196. Sound,
   validated by that field's oracle, and - by the Task 1 finding - never
   the deciding proof on this fixture.

The raster branch is deferred, not proved: bit 2 hands the cell to the
exact path whatever the descriptor proofs say. On this fixture no cell sets
it (`rainGateCensusPrecip=0`).

**Uncertainty test cost (Task 4).** The build is one closed-form loop per
cell (10 lobes x 3 texels), one BASE loop (4 texels and one lobe distance
per BASE lobe), and one walk only on cells both loops leave open:
`rainGateCellShapeCalls` 10,841 of 129,600 sampled cells (8.4%) walk, 29k
exact SDFs. The ray's test is one `texelFetch` and three comparisons.

## CLASSIFICATION (Tasks 5, 16-19) - MEASURED

| | SIDE | PLAY_VIS_NEAR |
|---|---|---|
| 13. build dimensions | 512 x 512 cells, 8 blocks, RGBA32F | same |
| 15. VRAM | 4 MB (T188's target, reused) | same |
| descriptor walks during build (sampled cells) | 10,841 / 129,600 = 8.4% | 10,832 = 8.4% |
| exact SDFs during build (sampled) | 29,125 (0.22 / cell) | 29,081 |
| 14. **build p50 / p95** (`RAIN_GATE_TIMER`, own program) | **0.119 / 0.123 ms** | **0.120 / 0.123 ms** |
| build's frame cost (`t203_gate_generate_only`: FINAL's ray + the build draw, net of the anchor) | -0.15 / -0.29 ms (inside anchor noise) | -0.17 / +0.11 |
| 17. safe (provably dry) cells | 118,759 of 129,600 = **91.6%** | 118,768 = 91.6% |
| by proof: unowned / no BASE support / union empty | 107,151 / 11,608 / 0 | 106,856 / 11,912 / 0 |
| 18. uncertain (not proved) cells | 10,841 = 8.4% | 10,832 = 8.4% |
| 19. **false-safe (dry claim violated), 17 exact samples a cell** | **0** | **0** |
| attach-bound misses, runs 2-3 (aspect-only dilation) | 117 / 81 samples | 126 / 88 |
| attach-bound misses, run 4 (warp term added) | 231, excess < 0.5 block in total | 239, same |
| **attach-bound misses, run 5 (0.002-block rounding pad)** | **0** | **0** |

Building the whole field costs 0.12 ms against a 7.1-8.3 ms family: the
brief's stop condition (the build consuming most of the ceiling) is nowhere
near. Runs 2-4 found the attach bound missed 0.05-0.13% of the maybe-cell
samples. Two causes, both closed. The lobe distance's domain warp adds a
horizontal slope that scales with the lobe's radius (0.3 blocks per block
on this fixture's several-hundred-block lobes), above the aspect-only
dilation on an aspect-1 lobe; the warp's wave numbers bound it in closed
form (`PA_RAIN_GATE_WARP_SLOPE`). And the attach height is a
support-weighted mean of the candidates' base heights, which in float lands
an ulp past the maximum of equal values: run 4's 231-239 misses summed to
less than half a block of excess in total. The bounds now carry a
0.002-block pad (`PA_RAIN_GATE_BOUND_PAD`) and run 5's oracle is clean at
both poses - 0 false-dry, 0 attach misses, 0 excess.

## QUALITY (Task 6) - MEASURED, every run, both poses

| | SIDE | PLAY_VIS_NEAR |
|---|---|---|
| 20. false rain pixels | **0** | **0** |
| 21. missed rain pixels | **0** | **0** |
| 22. rain IoU (`t203_rain_mask_gate` vs `t188_rain_mask_ref`) | **1.000000** (1105 / 1105 px) | **1.000000** (469 / 469) |
| 23. thin retention / 24. continuity / 25. attach-termination error | 1.0 / 1.0 / 0 (identical masks, same digest class) | same |
| full frame `t203_rain_gate` vs FINAL | **byte-identical**: 0 changed px, maxAbs 0 | byte-identical |

Exact by construction and measured exact: the gate rejects only by proof
and defers everything else to the test production runs.

## WORKLOAD (Task 8) - MEASURED, run 2 (run 3 within 2%)

| | SIDE | PLAY_VIS_NEAR |
|---|---|---|
| gate fetches (sample opportunities, all of them) | 8,588,486 | 5,482,378 |
| rejected by the dry flag | 7,543,057 = 87.8% | 4,801,065 = 87.6% |
| rejected by the attach window | 849,647 = 9.9% | 584,027 = 10.7% |
| 28. **fallback to the exact test** | **195,782 = 2.28%** | **97,286 = 1.77%** |
| out of domain / raster deferrals | 0 / 0 | 0 / 0 |
| fallback walks, of which supported above the cutoff | 195,787 / 35,584 (precision 18%) | 97,288 / 19,793 (20%) |
| 26. old segment-test walks | 2,304,741 | 3,471,929 |
| 27. new segment-test walks | **195,782 (-91.5%)** | **97,485 (-97.2%)** |
| segment tests still true (rain placement unchanged) | 33,686 | 16,093 |
| 29. `directStormShapeCalls` whole frame | 3,351,234 -> **1,242,894** | 4,099,469 -> **678,361** |
| lobe visits / exact SDFs | 25.0M -> 12.4M / 15.5M -> 7.8M | 16.9M -> 6.8M / 9.9M -> 4.3M |
| remaining walk classes (fallback) | eroded 163k, supported 160k (incl. shaft 132k), no-base 5k | eroded 79k, supported 80k, no-base 2k |

The descriptor work behind the segment test left the ray: 91.5-97.2% of
its walks, half the frame's lobe visits and exact SDFs. What remains is the
eroded class (coverage present at the support height, noise took the
support under the cutoff) and the columns that carry rain - the two
classes no field of geometry can prove, and the shaft's own walks.

## PERFORMANCE (Task 9) - MEASURED, paired against bracketing FINAL anchors

Run 1 is excluded: it carried the build inside the ray program, and the
ray paid **+2.5-4.0 ms for code it never executed** (`t203_gate_generate_only`
16.9 vs 12.9 ms at SIDE with the gate compiled *out* of the ray) - the T199
codegen lesson again. The build is its own program from run 2 on
(`t203_gate_build`, the pass baked on, receiving the ray program's uniforms
and samplers), and the ray programs bake the pass off.

| pose | run | control ray | gate ray (p50, 3 repeats) | delta ms | field: control -> arm | **net ms saved** | ratio | spread |
|---|---|---|---|---|---|---|---|---|
| SIDE | 2 | 12.34 | 10.86 | 1.48 | 1.66 -> 1.77 | **1.36** | 1.136 | 0.2% |
| SIDE | 3 | 12.73 | 10.37 | 2.36 | 1.65 -> 1.78 | **2.22** | 1.227 | 1.7% |
| SIDE | 4 | 12.33 | 10.90 | 1.43 | 1.86 -> 2.08 | **1.21** | 1.131 | 0.4% |
| SIDE | 5 | 12.39 | 10.80 | 1.60 | 1.66 -> 1.78 | **1.48** | 1.148 | 1.7% |
| PLAY_VIS_NEAR | 2 | 12.12 | 9.29 | 2.83 | 1.71 -> 2.13 | **2.41** | 1.305 | 1.6% |
| PLAY_VIS_NEAR | 3 | 12.21 | 10.17 | 2.04 | 1.99 -> 2.12 | **1.91** | 1.200 | 0.4% |
| PLAY_VIS_NEAR | 4 | 11.10 | 9.67 | 1.44 | 1.92 -> 2.09 | **1.27** | 1.149 | 0.8% |
| PLAY_VIS_NEAR | 5 | 11.63 | 10.33 | 1.31 | 1.96 -> 2.14 | **1.13** | 1.127 | 1.3% |

30. **SIDE net: 1.2-2.2 ms**, mean 1.57 over four runs. 31. **PLAY_VIS_NEAR
net: 1.1-2.4 ms**, mean 1.68. 32. Paired ratios 1.13-1.23 / 1.13-1.31.
33. Spreads 0.2-1.7% within a run (every block accepted); the between-run
spread (1.2 to 2.4) is the session variance T202 documented, on the anchor
side as much as the arm's - the ceiling itself moved 7.06-7.64 SIDE and
7.70-8.26 PLAY_VIS_NEAR across the same runs.

**Where the rest of the family is (run 3, SIDE, one block each, anchors
12.4-12.7).** The residual above the ceiling was decomposed by three bound
arms, each the gate program with one define:

| arm | ray ms | over the T182 ceiling (5.03) | reads as |
|---|---|---|---|
| `t182_norainseg` - the segment test absent | 5.03 | 0 | the family's ceiling: **7.57** (7.06-7.64 across runs) |
| `t203_gate_reject_all` - the test present, every sample rejected before the fetch | 6.50 | **+1.46** | the test's own structure and the shaft code's presence |
| `t203_gate_no_fallback` - the fetch and the gate's decisions paid, no walk | 7.02 | +1.99 | **the field read: +0.52** |
| `t203_gate_no_shaft` - the gate with fallbacks, the shaft's samples off | 7.21 | +2.18 | **the fallback walks: +0.2** |
| `t203_rain_gate` - the gate | 10.37 | +5.34 | **the shaft path: +3.2** (T201 priced it 2.49 on FINAL) |
| FINAL | 12.73 | +7.70 | the segment test's walks: **2.36** |

PLAY_VIS_NEAR: ceiling 4.19, reject-all 5.45 (+1.26), no-shaft 6.33, gate
10.17, FINAL 12.21 - the same shape.

So the walks the gate removed were worth 2.0-2.4 ms, the walks it left
(200k, clustered under the storm) 0.2, and the field read 0.5. The largest
piece of the family is the **shaft path** at 2.5-3.2 ms for 132k walks -
25 ns a walk, which is not traversal: it is the path's code presence in the
march loop and the divergence of a long per-lane branch that fires on 0.8%
of steps. The T182 "ceiling" priced the family's *removal*, and most of what
removal buys is the shaft code and the test's skeleton, not descriptor
traversal. T201's 2.49 ms shaft price was the same measurement.

## POST-T203 (Task 10)

Not baked. With the gate, this session's raw combined cost at 0.25 would be:
34. SIDE **10.4-10.9 ray + 1.8-2.1 field = 12.2-13.0 ms**; 35. PLAY_VIS_NEAR
**9.3-10.3 + 2.1 = 11.4-12.5 ms** (runs 2-5). Against the temporal reopen
rule (<= 8 ms, preferably ~6) that is 4-5 ms short, and the only rain
piece left of that size is the shaft path at 2.5-3.2, itself not traversal.

## DECISION

36. **T203: FAIL against the >= 4 ms target, and CLOSE by the brief's own
    scale** - net 1.1-2.4 ms, mean 1.6, of a 7.1-8.3 ms ceiling, against
    >= 4 for a PASS, 2-3 borderline and < 2 "close the rain architecture";
    two of eight paired blocks sit above 2. On every other axis it is a
    clean result: rain IoU 1.000, zero false and zero missed rain, frames
    byte-identical to FINAL, false-safe 0 and attach misses 0, a 0.12 ms
    build, 4 MB, 91.5-97% of the segment test's traversal gone.
37. **Production candidate: NOT by this brief's bar; YES as an exact
    minor-band gain if the bar is T202's** - the class T202's refinement
    was banked in (0.5-0.9 ms). It changes no pixel, costs 0.12 ms and
    4 MB, and its fallback is production's own code. Not baked into FINAL
    in this commit: the brief closes the line below 2 ms, so whether
    1.1-2.4 exact milliseconds are worth a second field draw is the user's
    call. Baking it is the T202 recipe: `leanFinalConstants`
    row 1, `LEAN_FINAL` in `rainGateGeneration`/`rainGateLookup`,
    `T203_GATE_BUILD` a production program, the T203 invariant's FINAL
    assertions flipped.
38. **Temporal ready: NO.** 12-13 ms raw combined at 0.25 with the gate.
39. **Exact next task.** The rain *traversal* question the brief asked -
    can most rain-segment descriptor traversal leave the ray - is answered
    yes, and closed: what leaves is worth ~2 ms, and the remaining 5.5 ms
    of the family is the shaft path (2.5-3.2, code presence and divergent
    execution on 0.8% of steps), the test's skeleton (1.3-1.5) and the field
    read (0.5). None of it is descriptor traversal, so no further rain
    *architecture* reaches it. The next measurable lever is the shaft
    path's structure - whether its 2.5-3.2 ms is presence (a codegen fact,
    testable by an arm that keeps the code and never calls it through a
    uniform branch the compiler cannot fold) or divergence (testable by
    counting warps with any true lane) - and that is a codegen/scheduling
    task, not a field. Fresh attribution of the *whole* frame under the
    gate before choosing it: with rain traversal gone, the 10.4 ms ray is
    5.0 ms of non-rain march (T201's 3.8-4.0 ms budget plus march control)
    and 5.4 ms of rain-family structure.

## VALIDATION

40. `campaignGate` PASS x6 (each edit round). 41. `check build` PASS (49 invariants incl. the new T203 one, jar).
42. Rain soundness: false-safe 0 on 2 x ~119k sampled dry cells x 17 exact
    samples in every run; attach-bound misses 0 in run 5 (231-239 sub-
    milliblock rounding misses in run 4, padded); frames and rain masks
    byte-identical in every run. 43. Consumer accounting: shape-call tags close
    (`shapeAccountingClosed=true`) in every capture; the gate adds no
    consumer, it removes calls from the rain-segment tag. 44. Workload
    views: seven new (71-77, stages 48-54, `STORM_WORKLOAD_RAIN_GATE_A-G`),
    the capture predicate extended to 77, the T175 view invariant covers
    them. 45. Campaign registry: `T203` (`t203-rain-gate.txt`,
    `rainGateRunRequested`, `t203Run`, `T203_OPTIMIZATION_ARMS`), and a
    `_SUPPORT_PROGRAMS` table the registry now resolves for programs a
    campaign draws with but never selects. 46. Variant scope: seven T203
    programs (`t203_rain_gate`, `t203_gate_generate_only`,
    `t203_rain_mask_gate`, `t203_gate_build`, three bounds), all
    campaign-scoped; FINAL byte-unchanged in behaviour (bakes
    `PaRainGateField = 0`, `PaRainFieldPass = 0`). 47. Marker retired to
    `.done`. 48. git status clean of task files (logs preserved).
49. Commit: the T203 commit on `experiment/cloud-descriptor-k` (parent `0223802`), not merged.

Harness notes for the next campaign: a uniform the monolith is told at
runtime must be declared in `cloud_atmosphere_volume.json` or
`safeGetUniform().set()` is a silent no-op (run 1's monolith never had the
gate on, and its build draw ran T188's branch); and a build pass carried in
a ray program costs the ray 2.5-4 ms of codegen whether or not it runs -
build passes get their own program (`copyFrameUniforms`).
