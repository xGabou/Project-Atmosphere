# T220 - Adaptive Edge Representation / Internal Resolution

**The question:** can we give mid-field cloud edges enough effective native samples to stay above
the ~2-pixel representation limit without paying for full-res cloud rendering?

**The answer: the question does not apply. CASE C - stop and re-attribute.** The edges were never
below that limit. T219 counted the samples an edge is drawn with against the per-frame phase
target and missed that the temporal resolve builds its lattice at **twice** the phase size, so the
composite's source was already **1.5x the frame**, not 0.75x it. Tested directly, doubling the
internal scale to 1.0 leaves the edge transition width at **5.00 px at NEAR_EDGE in every arm**
and moves SIDE by one measurement bin - less than two captures of the *same* configuration differ
from each other. Nothing is productionized, `T219`'s residual diagnosis is corrected in place, and
the residual cause is re-opened.

A second correction comes out of this task and applies retroactively: **the staircase run-length
and step-height statistics used in T219 measure a contour's slope, not aliasing.** A perfectly
antialiased edge reproduces them exactly (demonstrated below). Only the transition width is
diagnostic, and by that measure these edges are resolved.

## RECOVERY

1. Starting HEAD: `4c2f92d` (T219, "the mid-field puff edge is under-resolved, not
   under-marched"). Branch `quality/t217-deep-interior-lighting`. Working tree carried only the
   preserved `PAchangelog.md` user modification. `campaignGate` PASS before any client work
   (`gate-t220.log`).

2. **T209 constraints, read in full before anything was built** (`performance-spatial-upscale.md`):

   - **Why bilinear won:** motion stability first - route flicker 0.00209 against native's
     0.00198 and interior 0.00390 against 0.00393, with **6** outlier frames against EASU's 19 and
     the bicubic's 18; then edge/silhouette SSIM at SIDE (0.833 against 0.774 / 0.721); then thin
     retention (0.964 against 0.930 / 0.932) and dark interiors.
   - **Why EASU and the depth-guided bicubic failed, and the reason that binds T220:** *"the
     volumetric silhouette in the native frame is SOFT - a density falloff, not a step - and an
     edge-adaptive kernel that STEEPENS it moves away from native at the pose where the silhouette
     dominates."* The ground truth for a cloud edge is soft. **Sharpening is the wrong direction**,
     which rules out the whole sharpen/reconstruct-a-step family before it is proposed.
   - **Thin rain:** the bilinear is gentlest (8.9% thin rain lost against 11.3% / 14.1%); every
     kernel thickens rain and bridges shaft gaps.
   - **Motion:** the edge-adaptive kernels "re-decide their direction as the openings move", which
     is what tripled their in-cloud outliers.
   - **Current composite path:** a 4-tap depth-aware bilinear with scene-occlusion rejection, PA
     depth selection by weight x alpha, a same-surface test with an opaque-volume relaxation,
     premultiplied accumulation and one straight-colour conversion; `CoverageAlphaReconstruction`
     off; `PRODUCTION_SPATIAL_UPSCALE = -1`, so the spatial upscale pass exists as a diagnostic
     and the composite reads the lattice directly. Cost 0.08-0.09 ms.
   - **Full native 1.0 at shipping scale**, from T209's own table: cloud **51.1 ms** / total
     **59.7 ms** at SIDE against production's 7.47 ms total, with a 2.53 ms budget to 10 ms.

   Also read: `performance-internal-resolution.md`, which already measured the resampling beat
   (ratio 1.28x at 0.75, growing to 3.13x at 0.50) **and already rejected the composite-side fix**
   - `CoverageAlphaReconstruction` changes 0.0000-0.0033% of pixels and moves the beat ratio by
   under 5%, because the same-surface test already accepts essentially every tap. That route is
   closed and was not reopened.

## THE PREMISE WAS WRONG, AND HOW

3. **Under-resolved criterion.** An edge is under-resolved when its intensity transition spans
   fewer than about two samples of the grid that draws it. Two quantities are needed: the
   transition width, and the grid.

   T219 used the **per-frame phase target** as the grid. That is not what the composite reads.
   `CloudTemporalResolveRenderer` allocates the resolve target as `phase0.width * 2,
   phase0.height * 2` - the four quarter-pixel phases place one ray at each of the 2x2 lattice
   sites a phase texel covers - and the composite's source on a temporal frame is that resolved
   target. Logged directly this run (`T220_CAPTURE_LATTICE`):

| arm | internal scale | phase target | **resolved lattice** | frame | lattice vs frame |
|---|---|---|---|---|---|
| **R0 (production)** | 0.75 | 1200x675 | **2400x1350** | 1600x900 | **1.50x** |
| R1 | 1.00 | 1600x900 | **3200x1800** | 1600x900 | **2.00x** |
| R2 | 0.50 | 800x450 | **1600x900** | 1600x900 | **1.00x** |

   **In every arm the lattice is at least the frame size.** The composite is minifying or
   one-to-one, never magnifying, and the cloud layer was never short of samples at display scale.
   T219's "1.50-1.69 native pixels per edge" is half the correct count; the edge spans **3.0-3.4
   lattice samples at mid-field and 7.5 at NEAR_EDGE**.

4. **Affected pixel fraction**, by the naive "<2 frame px transition" criterion, over edge
   profiles measured along the local edge normal:

| pose | arm | n | median width | **<=1 px** | **<=2 px** | <=3 px | median in world blocks |
|---|---|---|---|---|---|---|---|
| SIDE | R0 (0.75) | 936 | 2.75 px | 6.6% | **40.3%** | 56.5% | 3.38 |
| SIDE | R1 (1.00) | 1075 | 2.25 px | 5.4% | **48.3%** | 61.8% | 2.77 |
| SIDE | R2 (0.50) | 783 | 3.00 px | 5.7% | 33.3% | 52.5% | 3.69 |
| NEAR_EDGE | R0 (0.75) | 4085 | 5.00 px | 1.7% | **2.3%** | 3.5% | 2.26 |
| NEAR_EDGE | R1 (1.00) | 4094 | 5.00 px | 1.6% | **2.3%** | 3.7% | 2.26 |
| NEAR_EDGE | R2 (0.50) | 3762 | 5.00 px | 1.8% | 2.8% | 4.4% | 2.26 |

   Read the SIDE column carefully: **the "under-resolved" fraction goes UP with more resolution**,
   40.3% -> 48.3%, because R1 renders a *narrower* transition (2.75 -> 2.25 px). The criterion is
   counting sharpness as a defect. The world-block column says what is really happening - the
   rendered band converges toward the physical ~2.8 blocks from above (3.38 -> 2.77 at SIDE) and
   is already there at NEAR_EDGE (2.26 in all three arms). This is sub-pixel filtering, not
   aliasing.

5. **Affected tile fraction: not built.** A tile classifier was the intended Task 1 deliverable,
   and it was abandoned when the pixel-level answer above plus Task 3's null made it pointless -
   there is no under-resolved population for a classifier to find. Reported as not done rather
   than filled in.

## THE UPPER BOUND (Tasks 3, 4)

Four arms captured **adjacent at the same pose in one run** (fixture `7c34c912`, radius 659.5, 10
descriptors). `R0B` repeats the production scale as the last arm and is the repeatability control
that everything else is judged against. No shader change: the arms set the existing per-shot
resolution override, held across the whole 140-frame settle window because a scale change destroys
and rebuilds the cloud targets and invalidates history.

6. **0.75 baseline edge width:** SIDE median **2.75 px**, NEAR_EDGE **5.00 px**.
7. **1.0 edge width:** SIDE median **2.25 px**, NEAR_EDGE **5.00 px**.
8. **1.0 quality result: no change.**

| pose | arm | wMed | w10 | w90 | <=1px | body mean | localSD | gradE | per-pixel mean \|d\| vs R0 | >1 8-bit |
|---|---|---|---|---|---|---|---|---|---|---|
| SIDE | **R0** | 2.75 | 1.25 | 5.50 | 6.6% | 185.613 | 2.1912 | 0.6439 | - | - |
| SIDE | **R0B** (same as R0) | 2.75 | 1.25 | 5.50 | 6.4% | 185.537 | 2.1824 | 0.6456 | **0.363** | **6.07%** |
| SIDE | **R1** (1.00) | 2.25 | 1.25 | 5.50 | 5.4% | 185.614 | 2.1989 | 0.6543 | 0.115 | 1.87% |
| SIDE | R2 (0.50) | 3.00 | 1.50 | 5.75 | 5.7% | 185.604 | 2.2316 | 0.6782 | 0.142 | 2.31% |
| NEAR_EDGE | **R0** | 5.00 | 5.00 | 6.75 | 1.7% | 159.019 | 2.9143 | 0.7503 | - | - |
| NEAR_EDGE | R0B | 5.00 | 5.00 | 6.75 | 1.6% | 159.019 | 2.9145 | 0.7502 | 0.018 | 0.02% |
| NEAR_EDGE | **R1** (1.00) | 5.00 | 5.00 | 6.75 | 1.6% | 159.021 | 2.9191 | 0.7556 | 0.168 | 1.57% |
| NEAR_EDGE | R2 (0.50) | 5.00 | 4.75 | 6.75 | 1.8% | 158.999 | 2.9401 | 0.7716 | 0.201 | 1.98% |

   **The control settles it.** At SIDE, two captures of the *identical* configuration (R0 vs R0B)
   differ by **0.363 mean and 6.07% of pixels beyond one 8-bit level**, while doubling the
   internal resolution differs by **0.115 and 1.87%**. The resolution change is three times
   *smaller* than the harness's own repeat. At NEAR_EDGE the repeat is tight (0.018, 0.02%) and
   R1 moves 1.57% of pixels - real but confined to sub-level filtering, with **every edge
   statistic identical**: median 5.00 px, w10 5.00, w90 6.75, hard-edge fraction 1.7% -> 1.6%.

   Body mean moves by at most 0.02 8-bit across every arm at both poses. Visually, the 6x crops of
   `T220_R0_SIDE` and `T220_R1_SIDE` at the same silhouette are indistinguishable - the same
   blocky boundary, the same facets.

   **Full 1.0 internal resolution does not remove the staircase. CASE C.**

9. **1.0 cost.** Capture-pin values, not shipping frame times:

| pose | R0 (0.75) | **R1 (1.00)** | R2 (0.50) | R0B (0.75 repeat) |
|---|---|---|---|---|
| SIDE | 29.684 ms | **52.969 (+23.29)** | 15.238 (-14.45) | 34.513 |
| NEAR_EDGE | 48.821 ms | **86.609 (+37.79)** | 25.826 (-23.00) | 49.002 |

   The SIDE repeat spread (29.684 vs 34.513, **4.83 ms** between identical configurations) is the
   cost measurement floor here; R1's +23 ms is well above it and is a real cost. At shipping scale
   T209 measured full native at **51.1 ms of cloud draw against production's 7.47 ms total**, with
   2.53 ms of headroom to the 10 ms budget - so full resolution is roughly 20x over budget. That
   was never the reason to reject it; the reason is that it does not work.

## THE SECOND CORRECTION: THE STAIRCASE METRICS MEASURE SLOPE

T219 reported "one-pixel steps in 69-82% of transitions" and run lengths of 2-6 px as evidence of
terracing, and this task carried the same metrics forward. **They cannot detect aliasing.** Run on
a synthetic, perfectly antialiased straight edge - an exact linear intensity ramp, no
quantization, no sampling error:

| edge slope | ramp width | run mean | run median | step mean | **step == 1** |
|---|---|---|---|---|---|
| 0.35 | **1.0 px** | 2.86 | 3 | 1.00 | **100%** |
| 0.35 | **5.0 px** | 2.86 | 3 | 1.00 | **100%** |
| 0.05 | 5.0 px | 20.00 | 20 | 1.00 | **100%** |
| 1.00 | 5.0 px | 1.00 | 1 | 1.00 | **100%** |

The run length is exactly `1 / slope` and is **completely independent of the ramp width**: a 1-px
edge and a 5-px edge give identical numbers. A thresholded level-set contour steps by one pixel
because contours do, not because anything aliased. The measured values in T219 and here (runs
2.09-5.30, one-pixel steps 60-87%) are the distribution of edge orientations in the frame and
nothing more.

**Only the transition width is diagnostic**, and it says these edges are resolved: 5.00 px at
NEAR_EDGE, 2.25-3.00 px at SIDE, converging to the physical band. Any future edge task must drop
the run-length and step-height metrics or it will keep finding a defect that is not there.

## ITEMS TERMINATED BY CASE C

The brief routes CASE C to "stop and re-attribute", so the localized-candidate half of the task
was not built. Recording them as not done, with the reason, rather than leaving them blank:

10. Localized candidate architecture: **none.** There is no under-resolved population to localize
    work onto, and the upper bound that would have justified it came back null.
11. Localized affected coverage: not applicable.
12. Transition width result: not applicable - item 7 is the ceiling and it is the baseline.
13. Staircase reduction: not applicable, and the metric is withdrawn (see above).
14. Edge RMS: not applicable.
15. Thin retention: not applicable. For the record, no arm moved the body mean by more than 0.02
    8-bit, so nothing thin was lost or gained at any scale.
16. Silhouette area: unchanged across all arms at both poses (body mask 69.4% / 86.3% of frame,
    body mean within 0.02).
17. **Static stability: measured, and it is the control this decision rests on** - R0 vs R0B,
    0.363 mean / 6.07% of pixels at SIDE and 0.018 / 0.02% at NEAR_EDGE. The SIDE figure is
    itself a finding: at 0.75 the frame is **not deterministic at the one-level scale between
    identical arms**, because each arm rebuilds its targets and re-converges the four phases from
    a different parity. Any future edge A/B must carry a same-configuration repeat or it will
    read that as signal.
18. Motion flicker: **not run.** T220 productionizes nothing - production is byte-identical to
    `4c2f92d` - so a route would compare production against itself.
19. Edge crawl: not applicable, same reason.
20. Classification popping: not applicable; there is no classifier.

21. SIDE cost: production **29.684 ms** (capture pin), unchanged - no production path was touched.
22. PLAY_VIS_NEAR cost: PVN belongs to the resolution-ladder shot set, not this one; production is
    unchanged, so the T219 figure stands (gameplay-phase `gpuMs` p50 1.541).
23. 4B cost: not captured this run; the arms are resolution-only and 4B is a camera-in-clear-air
    pose like the others, so no arm could behave differently there.
24. Whole renderer <=10 ms: **yes, and unchanged.** Production is byte-identical to `4c2f92d`.

## DECISION

25. **CASE C - re-attribute. No candidate, no production change, no deferral of a working idea**
    (there is nothing to defer: the ceiling is null, not expensive).

26. **Mechanism result.** Two errors, both mine, and one surviving fact:

    - **The sample accounting was wrong.** The composite reads the temporal lattice at twice the
      phase size. The layer has been supplying 1.5x the frame's samples at production and 2x at
      full scale. There is no representation shortfall to fix.
    - **The staircase metric was wrong.** Run length and step height measure contour slope; a
      perfectly antialiased edge scores identically. The apparent "terracing signal" in T219 was
      an artefact of the instrument.
    - **What survives from T219 is the negative result**, and it is unaffected by either error:
      the march stride is converged (0.7% from a 50x finer reference, confirmed in the renderer at
      a matched iteration budget), the temporal resolve does not create the pattern, the composite
      is not point-sampling, and production is not truncating. T220 adds a fourth: internal
      resolution is not the lever either.

    So four stages are now cleared by measurement and the residual is **unattributed**. The
    honest position is that the reported visual complaint has not yet been reduced to a measured
    quantity, and the metric that was supposed to represent it has been withdrawn.

27. Production constant/change: **none.** No shader, no uniform, no constant, no morphology, no
    composite path. The only code that survives this task is one extra log line.

28. **Exact next target: establish what the complaint actually is, in a measurable quantity,
    before attributing anything further.** Concretely, and in this order:

    - **Define the defect against a reference.** Render one pose at a large supersample (the
      existing per-shot scale override reaches 1.0; a 2x-of-display arm would need the target
      allocator relaxed) and box-filter it down to the frame. That is the ground truth a cloud
      edge should converge to. The defect is then whatever differs between production and that
      reference - measured as a difference image, not as a contour statistic.
    - **Look at the surface, not the silhouette.** The 6x crops show the puff mass reading as
      flat-shaded polygonal *cells* whose boundaries the silhouette then follows. If that is the
      complaint, it is lobe/union geometry or the density field's Worley cellularity showing
      through the shading, and it lives in morphology or lighting - not in sampling, which is now
      excluded four ways.
    - **Do not reopen:** march stride or cap (T219), temporal reconstruction (T219), the composite
      filter family (T209, and the beat fix already measured inert in
      `performance-internal-resolution.md`), or internal resolution (this task).

## VALIDATION

29. `campaignGate` PASS twice: before any client work (`gate-t220.log`) and after the collapse
    (`gate-t220b.log`).
30. `check build` PASS (`check-build-t220.log`).
31. Morphology sandbox PASS - 10 `PHASE4S_RESULT ... PASSED`, standalone after the collapse.
32. Markers: `run/t132-autorun-exit.txt` armed, so the client stopped itself;
    `run/t211-captures.txt` the only campaign marker, retired afterwards. No motion campaign
    registered (item 18).
33. `git status`: `PAchangelog.md` left modified and uncommitted as required. No validation
    evidence, campaign log or unrelated log was deleted. The T219 report is **edited in place**
    with a CORRECTION banner and struck-through claims rather than rewritten, so the record of
    what was believed and why is preserved.
34. Committed on `quality/t217-deep-interior-lighting`. **Not merged.**

### Program scope

The diagnostic shot block is removed, as T218's and T219's were. What stays is the
`T220_CAPTURE_LATTICE` log line, which records the phase target **and** the resolved lattice with
every capture - the single line that would have prevented this task's premise from being wrong,
and which costs nothing.

## HONEST LIMITS

- **One fixture, one run, two poses.** The arms are paired inside that run, which is the only
  comparison this harness supports. The SIDE repeat spread (6.07% of pixels) means single-arm
  differences below that are not resolvable there; the NEAR_EDGE repeat (0.02%) is tight, and it
  is the pose where the null is strongest.
- **The 1.0 arm is not a true supersample.** It doubles the lattice to 2x the frame, which is
  1.4x linear supersampling after the composite's filter - enough to answer "does more resolution
  help", not enough to serve as a ground-truth reference. That reference is the first item of the
  next target.
- **The visual judgement is two 6x crops at one pose.** They are indistinguishable, which is
  consistent with every number, but a null established on paired statistics plus two crops is a
  null about *these* poses at *this* scale, not a proof that resolution can never matter anywhere.
- **`PaEdgeStep` and the T219 arms are gone**, so the stride results quoted here are read from the
  T219 report rather than re-measured.
