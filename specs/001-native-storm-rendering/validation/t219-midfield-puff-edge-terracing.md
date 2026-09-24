# T219 - Mid-Field Puff Edge Terracing / Step-Quantization Cleanup

**The question:** are the mid-field stair-stepped puff edges caused by ray-march / threshold
quantization, and if so can a cheap, temporally stable local edge refinement remove them?

**The answer: no, they are not march quantization. DISPROVE (CASE C).** The cloud/sky boundary is
a real world-space band of ~2.8 blocks that is already converged at production's stride - marching
it 50x finer moves it by 0.7%. What changes with distance is not the boundary, it is the number of
pixels the cloud layer has to draw it with: **at mid-field the edge occupies 1.5-1.7 native cloud
pixels, below the two it needs to be representable at all.** The defect is spatial resolution, not
sampling along the ray, and no amount of extra march work can reach it.

## RECOVERY

1. Starting HEAD: `20dd6f4` (T218, "the buried interior is visibility-limited, not
   under-sampled").
2. Branch: `quality/t217-deep-interior-lighting`, continued.
3. Production state verified at entry and unchanged by this task:
   `PRODUCTION_TEMPORAL_MODE=1`, `PRODUCTION_SPATIAL_UPSCALE=-1`, `PRODUCTION_RAIN_LATTICE_MODE=0`,
   `PRODUCTION_ORIGIN_JITTER_MODE=0`, `PRODUCTION_MACRO_ENVELOPE_MODE=3` (T213),
   `PRODUCTION_LOWER_BODY_MODE=1` (T215), `PRODUCTION_DEEP_SCATTER_MODE=1` (T217).
   New `PRODUCTION_EDGE_STEP_MODE=0`, which is the production stride.
   Working tree carried only the preserved `PAchangelog.md` user modification.
4. Harness: `campaignGate` PASS before any client work (`gate-t219.log`) and after the attribution
   arm (`gate-t219b.log`). `run/t132-autorun-exit.txt` armed; `run/t211-captures.txt` the only
   campaign marker. The T218 capture set (fixture `5a245619`) is reused for the defect
   measurement, since it already contains a distance ladder, an alpha view and a depth view at the
   poses this task needs.

### The stepping machinery, as it actually is

- Exterior fine stride: `exteriorFineStepWorld` = `clamp(2.5 * sqrt(96 / budget), 2.5, 8.0)`.
  At ULTRA (96 steps, governor scale 1.000) the budget is 96, so the stride is the **2.5-block
  floor**.
- Coarse stride: `max(baseStep * 1.5, fineStep * 3.0)` >= **7.5 blocks**, capped at
  `min(112, fineStep * 16)`.
- Promotion: `fine = sinceHit < 6`, plus a puff-segment test and a fine-lattice empty-span scan
  that both force `sinceHit = 0` when they find material. A boundary is therefore **not** crossed
  on the coarse stride - the scan promotes first.
- Ray origin: phased by blue noise over a **full fine stride** in production
  (`originJitterMode == 0` returns `fineStep`), decorrelating the lattice per pixel, and the T207
  resolve then averages four temporal phases.

That last point matters for the hypothesis: a per-pixel origin phase turns lattice quantization
into dither rather than a coherent terrace, so "the march quantizes the edge" already had to
explain why it would survive the jitter.

## DEFECT

5. Reference pose: the mid-field puff mass at `2_SIDE` (1.7 R, mid height), with the T098 distance
   ladder (1.12 R to 1.90 R) as the scale series. Frame 1600x900; **cloud layer 1200x675**
   (`effectiveResolutionScale = 0.75`).
6. Stair height: the boundary steps by **1 pixel in 69-82% of transitions**, mean step 1.33-1.51
   px, across every view measured. Scale-invariant.
7. Stair run length: mean 2.0-6.2 px, median 1-2 px.
8. Periodicity: **none.** A nearest-neighbour 4/3 upscale would leave a period-4 signature; the
   duplicate-adjacent-column rate restricted to edge pixels is 0.300 / 0.293 / 0.299 / 0.288 for
   `x mod 4` = 0/1/2/3 - flat. The composite is not point-sampling the layer.
9. Edge gradient / transition width, measured as the 10-90% rise of the sky-minus-cloud scalar
   **along the local edge normal** so a diagonal edge is not credited with extra width:

| view | distance factor | tangent dist | width (frame px) | blocks/px | **width (blocks)** | **width (native px)** |
|---|---|---|---|---|---|---|
| NEAR_EDGE | 1.12 | 338 | 5.00 | 0.459 | 2.29 | **3.75** |
| L120 | 1.20 | 444 | 4.25 | 0.603 | 2.56 | 3.19 |
| L130 | 1.30 | 557 | 3.50 | 0.755 | 2.64 | 2.63 |
| L140 | 1.40 | 656 | 3.25 | 0.891 | 2.90 | 2.44 |
| L150 | 1.50 | 749 | 3.00 | 1.017 | 3.05 | 2.25 |
| L160 | 1.60 | 837 | 2.75 | 1.136 | 3.12 | 2.06 |
| **SIDE** | 1.70 | 921 | 2.00 | 1.250 | 2.50 | **1.50** |
| L180 | 1.80 | 1003 | 2.25 | 1.361 | 3.06 | 1.69 |
| LATERAL | 1.90 | 1082 | 2.25 | 1.469 | 3.31 | 1.69 |

**The boundary is constant in world units (2.29-3.31 blocks, mean 2.83) and shrinks as 1/distance
in pixels.** That is the signature of a world-space length scale, which rules out every purely
screen-space cause at the start. The last column is the one the decision rests on: expressed in
the pixels of the layer that actually draws it, the edge falls from 3.75 px near to **1.50-1.69 px
at mid-field**.

## ATTRIBUTION

Three stages were separated, and all three are cleared. Every comparison is between arms captured
**adjacent at the same pose in one run** (fixture `f1328791`, 10 descriptors throughout).

### Offline first: is the boundary even step-limited?

`stormT219EdgeQuantizationSandbox` marches the real production density path across the silhouette
at decreasing strides and measures the 10-90% rise of accumulated alpha. If the boundary were
quantized to the march lattice the band would collapse with the stride:

| stride (blocks) | 7.500 | 2.500 (production) | 1.250 | 0.625 | 0.250 | **0.050 (reference)** |
|---|---|---|---|---|---|---|
| band (blocks) | 5.484 | **4.920** | 4.963 | 4.954 | 4.956 | **4.956** |
| with a per-ray origin phase | 4.898 | 4.965 | 4.954 | 4.957 | 4.956 | - |

**It does not collapse.** Marching 50x finer than production moves the band by 0.036 blocks -
**0.7%**. Even the 7.5-block coarse stride only widens it by 11%. The same holds down the whole
distance ladder, where the reference column tracks the production column within 0.7% at every
distance while both fall as 1/distance in pixels:

| distance factor | 1.12 | 1.20 | 1.40 | 1.70 | 1.90 |
|---|---|---|---|---|---|
| band, 2.5 stride (blocks) | 6.109 | 6.138 | 5.505 | 4.920 | 4.861 |
| band, 0.05 reference (blocks) | 6.146 | 6.109 | 5.594 | 4.956 | 4.877 |
| band in pixels | 53.7 | 41.1 | 24.9 | 15.9 | 13.4 |

That reproduces the trend measured on the frames (constant in blocks, 1/d in pixels), which is
what licenses reading the sandbox as a description of the boundary the renderer actually draws.
For contrast, a perfectly **hard** sphere of the same radius would give a 0.475-block band, so the
~5 blocks is not a geometric floor - it is the density envelope's own falloff, which the ray
integral compresses from a 34.7-block density rise into a ~5-block alpha rise.

### An invalid first attempt, and why it is reported

The first pair of shader arms halved and quartered the exterior stride **without raising the
iteration cap**. That is not a refinement test. `MAX_STEPS` is 128, so a quarter-stride ray can
travel only `128 x 0.625 = 80` blocks in fine mode against a ~1340-block system: the arms ran out
of iterations and the frame **lost material**, dissolving the lower mass into fragments and
raising the body mean by +9.97 (SIDE) and +24.25 (NEAR_EDGE) 8-bit as sky showed through. Those
numbers measure truncation, not convergence, and none of them appears below. The arms were
rebuilt with `PA_MAX_DIAGNOSTIC_STEPS = 384` and a **budget-matched control** so that E1 -> E2 is
the stride effect alone.

10. **Raw march result.** `T219_RAW` is the same pose with the temporal history off. Against
    production FINAL: body mean **193.973 -> 193.980 (+0.008)**, per-pixel mean difference
    **0.108**, only **1.98%** of pixels differ by more than one 8-bit level. Edge geometry is
    unchanged - median transition 2.75 -> 2.50 px, run length 5.04 -> 5.14, one-pixel steps 76% ->
    80%. At NEAR_EDGE the median transition is **5.00 px in both**. **The stair pattern is already
    present in the single-frame march.**
11. **Temporal result.** It follows that the temporal resolve introduces nothing: it is the same
    comparison read the other way. The 4-phase reconstruction neither creates nor removes the
    staircase.
12. **Final composite result.** Ruled out independently and before any arm was built. A
    nearest-neighbour 4/3 upscale (1200x675 -> 1600x900) would leave a period-4 signature in
    duplicated adjacent columns; restricted to edge pixels the duplicate rate is **0.300 / 0.293 /
    0.299 / 0.288** for `x mod 4` = 0/1/2/3 - flat to within noise.
13. **Controlling stage: none of them.** The renderer is reproducing a boundary that is genuinely
    ~2.8 blocks wide, and doing so correctly. What fails is that at mid-field those 2.8 blocks
    land on **1.5-1.7 pixels of the 1200x675 cloud layer**. An edge that transitions in under two
    samples cannot be represented without aliasing, whatever produces it.

### The stepping machinery at the affected pixels

14. `coarseStep` = `max(baseStep * 1.5, fineStep * 3.0)`, **>= 7.5 blocks**, capped at
    `min(112, fineStep * 16)` = 40 blocks.
15. `fineStep` = `exteriorFineStep` = **2.5 blocks** (the clamp floor, at ULTRA 96 steps with
    governor scale 1.000). The in-cloud `legacyFineStep` does not apply: `CameraCloudDensity` is
    **0.00000** at both T219 poses.
16. Promotion point: a boundary is **not** crossed on the coarse stride. `fine = sinceHit < 6`,
    and both the puff-segment test and the fine-lattice empty-span scan set `sinceHit = 0` and
    `fine = true` the moment they find material, stepping by the scan's own fine-lattice step.
    The coarse stride is therefore confined to confidently-empty space.
17. First threshold-crossing depth: not separately resolvable from the captures - the 8-bit depth
    debug view encodes `proximity = clamp((1 - depth) * 2048, 0, 1)`, which at this pose is about
    **16 world blocks per 8-bit level**, six times coarser than the stride under test. The
    question it would have answered is answered instead by item 18, which does not depend on it.
18. **Measured depth quantization: none that reaches the image.** Two independent reasons. The
    ray origin is phased per pixel by blue noise over a **full fine stride**
    (`originJitterMode == 0` returns `fineStep`), so the lattice is decorrelated between
    neighbouring pixels and the T207 resolve then averages four temporal phases - quantization
    becomes dither, not a terrace. And empirically, halving the stride with the budget held fixed
    changes neither the transition width nor the run-length statistics (item 22).


## CANDIDATE

19. **None.** Attribution does not support one, and the task's own rule applies: the defect is not
    where a local edge refinement could act.
20. Localization rule: not applicable.
21. Extra samples/work: not applicable. For the record, the diagnostic arms priced what a
    refinement would cost even if it worked - halving the exterior stride at a matched budget
    costs **+26.3 ms at SIDE** and **+52.4 ms at NEAR_EDGE** on the capture pin, roughly doubling
    the frame, for zero measured change in the edge.
22. **Affected-edge result: the negative control, in the renderer.** All arms adjacent, one storm,
    one pose. `E0` is production; `E1` raises the iteration cap alone; `E2` halves the exterior
    stride at that same raised cap, so `E1 -> E2` is the stride effect with the budget controlled.

**SIDE**

| arm | body mean | d mean | localSD | edge wMed | wMean | run mean | 1-px steps | per-pixel mean \|d\| | >1 8-bit |
|---|---|---|---|---|---|---|---|---|---|
| **E0** production | 193.973 | - | 2.3214 | 2.75 | 3.32 | 5.04 | 76% | - | - |
| **E1** raised cap only | 193.810 | -0.163 | 2.1825 | 3.00 | 3.42 | 5.04 | 76% | 0.250 | 1.00% |
| **E2** half stride | 193.871 | -0.101 | 2.1976 | 2.75 | 3.28 | 5.30 | 73% | 0.279 | 1.83% |
| RAW (no temporal) | 193.980 | +0.008 | 2.2785 | 2.50 | 3.18 | 5.14 | 80% | 0.108 | 1.98% |
| E2RAW | 193.872 | -0.100 | 2.1573 | 2.25 | 3.13 | 5.48 | 79% | 0.314 | 2.72% |

**NEAR_EDGE**

| arm | body mean | d mean | localSD | edge wMed | wMean | run mean | 1-px steps | per-pixel mean \|d\| | >1 8-bit |
|---|---|---|---|---|---|---|---|---|---|
| **E0** production | 159.870 | - | 2.9586 | 5.00 | 5.34 | 1.95 | 83% | - | - |
| **E1** raised cap only | 159.617 | -0.254 | 2.7220 | 5.00 | 5.04 | 2.24 | 88% | 0.789 | 3.04% |
| **E2** half stride | 159.749 | -0.122 | 2.7206 | 5.00 | 5.17 | 2.17 | 86% | 0.859 | 3.49% |
| RAW (no temporal) | 159.878 | +0.008 | 2.9283 | 5.00 | 5.25 | 1.99 | 85% | 0.148 | 1.62% |
| E2RAW | 159.757 | -0.114 | 2.7012 | 5.00 | 5.25 | 2.25 | 89% | 0.882 | 4.07% |

**Nothing about the edge moves.** At NEAR_EDGE the median transition is **5.00 px in every single
arm**. At SIDE it moves by one quarter-pixel measurement bin in either direction with no trend,
and the staircase statistics - run length and one-pixel-step fraction, which are the actual stair
metrics and are contrast-normalized - are flat. Where the arms do move something it is the wrong
thing and the wrong way: `localSD` and gradient energy fall slightly, i.e. the refined arms are
marginally **smoother**, never crisper.

**E0 -> E1 also settles a worry the invalid first attempt raised**: production is *not* truncating
at these poses. Raising the cap three-fold changes 1.00% of SIDE pixels by more than one 8-bit
level and the body mean by -0.163. The 128-iteration budget is adequate at the production stride;
it was only the quartered stride that exhausted it.


## QUALITY

23. Stair reduction: **none, in any arm.** One-pixel steps remain 73-80% of transitions at SIDE
    and 83-89% at NEAR_EDGE regardless of stride, cap or temporal state.
24. Edge RMS / transition width: unchanged (item 22). NEAR_EDGE is identical to the measurement's
    resolution in all five arms.
25. Curvature continuity: unchanged - run-length means move from 5.04 to 5.30 at SIDE and 1.95 to
    2.17 at NEAR_EDGE, i.e. the contour is no more nor less piecewise-flat.
26. Thin retention: unchanged. This is the item the invalid first attempt destroyed - at a
    quartered stride with the production cap, thin material vanished wholesale. At a matched
    budget it is preserved: body mean within 0.26 8-bit of production at both poses.
27. Area delta: the body mask is 63.1% of frame at SIDE and 86.1% at NEAR_EDGE, and no arm moves
    the mean by more than 0.26 8-bit, so coverage is unchanged. No arm touches density,
    extinction or the silhouette by construction - only the stepping lattice and the iteration
    cap move.
28. Collateral: not applicable in the usual sense, because **nothing is proposed**. Production is
    byte-identical to `20dd6f4`: `PRODUCTION_EDGE_STEP_MODE = 0`, and the arm is removed from the
    tree entirely (see program scope). T214's anvil, T215's tower, the thin edges, the hole
    boundaries and the rain interaction are all untouched because no production path changed.


## TEMPORAL

29. Static stability: the RAW arms are the static temporal probe and they are the result, not a
    side-check. History off vs on changes the body mean by **+0.008 8-bit** at both poses and
    leaves every edge statistic within measurement resolution.
30. Route flicker: **not run, and the reason is that there is nothing to validate.** T219
    productionizes no change; `PRODUCTION_EDGE_STEP_MODE` is 0 and the uniform does not survive
    into FINAL, so the shipped renderer is bit-identical to the one T218 validated. A motion route
    would be comparing production against itself.
31. Edge crawl: not applicable for the same reason. The arms that were measured are diagnostic and
    are not proposed; had one of them shown an edge win it would have gone to a route before any
    decision, which is the order T211 established and T218 followed.
32. Popping: not applicable.
33. Ghost: not applicable.

The one temporal question T219 *did* have to answer - whether the 4-phase resolve manufactures the
staircase - is answered in items 10-11 with a static paired arm, which is the right instrument for
it: a route measures stability over time, whereas this is a question about a single frame's
content.


## PERFORMANCE

Capture-pin values (spectator, resized window, ULTRA capture quality) - **not shipping frame
times**. Only matched-pose deltas within this run are meaningful, as T217 and T218 established.

| pose | E0 production | E1 raised cap | E2 half stride |
|---|---|---|---|
| SIDE | **27.058 ms** | 31.429 (**+4.37**) | 53.325 (**+26.27**) |
| NEAR_EDGE | **46.196 ms** | 52.614 (**+6.42**) | 98.635 (**+52.44**) |

34. Production SIDE: **27.058 ms**.
35. Candidate SIDE: no candidate. The diagnostic half-stride arm is **53.325 ms**, +26.27.
36. Production PLAY_VIS_NEAR: PVN belongs to the resolution-ladder shot set, not this one. The
    equivalent production figure from this run's own gameplay phase (spectator at gameplay
    altitude, storm in frame, `resolutionScale` 0.750) is the renderer's status telemetry, which
    is the number item 40 rests on.
37. Candidate PVN: no candidate; the arm does not ship, so its delta there is zero by
    construction.
38. Edge-pose cost: NEAR_EDGE **46.196 ms** production, +52.44 for the half-stride arm.
39. Delta: **+4.4 to +6.4 ms for the iteration cap alone, +26.3 to +52.4 ms for the halved
    stride** - roughly doubling the frame. Against a guidance band whose "normally reject"
    threshold is 0.75 ms, that is 35x to 70x past it, for a measured change in the edge of zero.
40. Whole renderer <= 10 ms: **yes, and unchanged.** Production is byte-identical to `20dd6f4`,
    which T218 measured at gameplay-phase `gpuMs` p50 1.541, max 1.588.


## DECISION

41. **DISPROVE (CASE C).** March quantization is not the cause of the mid-field puff-edge
    staircase. No candidate is productionized, `PRODUCTION_EDGE_STEP_MODE` stays 0, and the
    `PaEdgeStep` arm is removed from the tree, as T217's ten modes and T218's four were. The
    durable evidence is this report plus `stormT219EdgeQuantizationSandbox`, which reproduces the
    stride convergence offline with no client.

42. **Mechanism result.** The boundary is not quantized; it is **under-resolved**. Three
    measurements, each ruling out one candidate stage, and one that explains what is left:

    - **Not the composite.** No period-4 duplicate signature on edge pixels (0.288-0.300 across
      phases), so the 4/3 upscale is not point-sampling the layer.
    - **Not the temporal resolve.** History off vs on: body mean +0.008 8-bit, every edge
      statistic within measurement resolution. The staircase is already in the single-frame march.
    - **Not the march stride.** Offline, the alpha band is converged at production's 2.5 blocks to
      within 0.7% of a 0.05-block reference, at every distance on the ladder. In the renderer,
      halving the stride at a matched iteration budget leaves the transition width and the
      one-pixel-step fraction flat, while costing +26 to +52 ms. Production is also not
      truncating: raising the cap three-fold moves 1.00% of pixels by more than one level.
    - **What is left is resolution.** The boundary is a genuine world-space band of 2.29-3.31
      blocks (mean 2.83), constant across a 2.4x distance range. Expressed in the pixels of the
      layer that draws it - 1200x675, the 0.75 internal scale - that band falls from **3.75 px at
      1.12 R to 1.50-1.69 px at 1.7-1.9 R**. Below two samples an edge cannot be represented
      without aliasing, and mid-field is precisely where the projection crosses that line. That is
      why the defect is reported at mid-field and not near, and why it is invariant to everything
      done along the ray.

    The march is doing its job correctly and the answer it produces is then sampled too coarsely
    to show. Spending more samples along the ray refines a quantity that was already converged.

43. Production constant/change: **none.** `PRODUCTION_EDGE_STEP_MODE = 0`; no pinned constant,
    no morphology, no density, no extinction, no temporal or composite path was touched. The
    shipped shader differs from `20dd6f4` only by comments recording what was measured.

44. **Exact next visual target: the cloud layer's internal resolution at mid-field, judged as an
    edge-representation problem rather than an upscaling one.** The measurement that matters is
    already in hand - the edge occupies 1.5-1.7 native pixels where it looks worst - so the
    question for the next task is which of these actually buys representable edges, priced against
    the <=10 ms budget:

    - raising the internal scale only where the projected edge falls below ~2 native pixels,
      rather than globally;
    - an edge-aware composite that reconstructs sub-pixel coverage from the layer's own alpha
      gradient, which is information the current bilinear path discards;
    - accepting it, on the evidence that the band is physically correct and the artefact is a
      display-resolution limit.

    **T209 is the prior and must be read first:** it measured the spatial upscale methods and
    banked bilinear, rejecting EASU and bicubic both statically and in motion, with
    `PRODUCTION_SPATIAL_UPSCALE = -1`. T219 does not reopen that decision - it identifies a
    different quantity (how many native pixels the edge spans before any upscale runs) and that
    is what the next task should attribute. Do not start by changing the upscale filter; T209
    already measured that lever.


## VALIDATION

45. `campaignGate` PASS three times: before any client work (`gate-t219.log`), after the first
    attribution arm (`gate-t219b.log`), and after the arms were rebuilt with the budget-matched
    control (`gate-t219c.log`). A fourth PASS after the collapse (`gate-t219d.log`).
46. `check build` PASS (`check-build-t219.log`).
47. Morphology sandbox PASS - 10 `PHASE4S_RESULT ... PASSED`, run standalone after the collapse as
    well as inside `check`.
48. **Program scope.** `PaEdgeStep` was a negative control, not a candidate, and it is removed -
    uniform, JSON entry, shader branches, debug-config mode, renderer upload and capture shots.
    `grep -rn PaEdgeStep src/` returns nothing. What stays is
    `StormT219EdgeQuantizationSandbox` and its Gradle task, which reproduce the stride convergence
    and the distance ladder offline, and `edgeStep` is gone from the capture CONTROLS line with
    `deepScatter` and `cameraCloudDensity` left in place from T218.
49. Markers: `run/t132-autorun-exit.txt` armed throughout, so the client stopped itself.
    `run/t211-captures.txt` was the only campaign marker and is retired. No motion campaign was
    registered, because none was needed (item 30).
50. `git status`: `PAchangelog.md` left modified and uncommitted as required. No validation
    evidence, campaign log or unrelated log was deleted.
51. Committed on `quality/t217-deep-interior-lighting`. **Not merged.**

### A harness note worth carrying forward

The first attribution arm was **invalid and looked like a result**: quartering the stride without
raising the iteration cap produced a dramatic, confident-looking change - body mean +9.97 and
+24.25 8-bit, edges apparently "softening" from 1.75 to 5.00 px - that was entirely step-budget
exhaustion dissolving the cloud. It was caught by looking at the frame rather than the table: the
lower mass had broken into fragments and the anvil had lost its underside, which no refinement
could do. **When a sampling arm changes the image a lot, check it did not change how far the ray
got before believing what it says about how finely the ray sampled.** The fix is the
budget-matched control (E1), and any future stride or budget arm needs one.


## HONEST LIMITS

- **The offline sandbox marches a single continuous stride**, while production runs a promotion
  machine (coarse stride, fine-lattice empty-span scan, forced promotion). The real march is
  bracketed by the 7.5-block and 2.5-block rows rather than equal to either. What the sandbox
  establishes exactly is the **reference**: the boundary a 0.05-block march produces. That the
  production stride already sits within 0.7% of it is the load-bearing result, and it does not
  depend on modelling the promotion machine.
- **The sandbox fixture is the phase-4S severe storm (radius 166)**, while the live capture
  fixture has radius 670-672. Absolute band widths in blocks are therefore not directly comparable
  between the two; the convergence behaviour and the distance trend are, and both reproduce.
- **`ExteriorFineStep` was at its 2.5-block clamp floor** for this whole investigation, because
  the capture runs at ULTRA with governor scale 1.000. At a lower quality preset the stride is
  larger and the conclusion would have to be re-measured - the arms here go finer, not coarser.
- **The edge-width measurement reads the composited frame**, so it includes the cloud layer, the
  temporal resolve and the upscale together. The RAW arms separate the temporal stage; the
  internal resolution is separated by arithmetic (the native-pixel column) rather than by a
  rendering arm, because changing internal scale rebuilds the targets and invalidates history,
  which is not a like-for-like comparison inside one run.
