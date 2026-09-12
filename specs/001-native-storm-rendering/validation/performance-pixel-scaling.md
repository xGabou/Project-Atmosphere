# T200 - FINAL's pixel-scaling exponent after the shared field, and the temporal budget

Measurement only. Nothing was optimized, and no probe-floor, rain, field
resolution, far-tap, temporal or reconstruction code was touched.

## BASELINE

1. **Starting HEAD**: `a406c22` (T199), branch `experiment/cloud-descriptor-k`,
   nothing merged.
2. **Field production state**: as T199 banked it - FINAL builds the
   256x256x32 RGBA16F field before every timed draw and reads it on both
   consumers (bits 11), the build on its own GPU clock; unchanged here.
3. **Poses**: SIDE (severe), **PLAY_VIS_NEAR** (representative gameplay, 1.6x
   the storm radius at y = 120 - the pose the T135 budget is quoted at) and
   FAR. The brief named PLAY_NEAR; at 4x the radius the storm sits ~1.9 km
   from the camera, beyond the 2000-block cloud render distance, and the
   T150 visibility guard rejected the pose as empty sky after three attempts
   (`t200-campaign/t200-run1-play-near-empty.log`: `rendered no storm:
   cloudDensityCalls=228 marchedPixels=129600`). The harness's own comment
   on PLAY_VIS_NEAR says why it exists. Run 2 (`t200-run.log`, 10:34-10:42 on
   12Sep2026) is the record; run 1's SIDE and FAR agree with it within the
   session spread and are kept as evidence.
4. **Scales**: 0.25, 0.375, 0.50, 0.75, 1.00 of the harness window, three
   repeats a scale in ascending order, 60 samples a cell, Ultra 96 steps,
   history off (every campaign's condition), one deterministic fixture per
   pose (`T150_VISIBILITY_CONFIRMED` at each: SIDE 28,406 density calls at
   0.25, PLAY_VIS_NEAR 9,540, FAR 4,134).
5. **Framebuffer**: window 1920x1080; cloud targets **480x270 (129,600 px),
   720x405 (291,600), 960x540 (518,400), 1440x810 (1,166,400), 1920x1080
   (2,073,600)** - the cell's recorded `cloudWidth x cloudHeight` matched the
   expected `ceil(1920 s) x ceil(1080 s)` at every scale.

Infrastructure: campaign `T200` (`run/t200-pixel-scaling.txt`) in
`StormCampaignRegistry` and `StormT132AutoDriver` - `LEAN_FINAL` at each scale
through `T166Arm.resolutionScale`, a runtime setting, **no shader variant**
(`T191_VARIANT_SCOPE ... registered=0`); no image arms; `buildT200Report()`
prints every line below as MEASURED or DERIVED.

## SIDE (MEASURED)

| scale | target | pixels | 6. field p50 / p95 | 7. ray p50 / p95 | 8. combined p50 / p95 | composite p50 | ray spread (3 repeats) |
|---|---|---|---|---|---|---|---|
| 0.25 | 480x270 | 129,600 | 2.18 / 2.55 | **13.17** / 13.92 | **15.35** / 16.47 | 0.086 | 0.5% |
| 0.375 | 720x405 | 291,600 | 2.23 / 2.57 | 26.99 / 28.57 | 29.23 / 31.13 | 0.088 | 1.1% |
| 0.50 | 960x540 | 518,400 | 2.14 / 2.59 | 40.82 / 42.56 | 42.96 / 45.15 | 0.089 | 0.9% |
| 0.75 | 1440x810 | 1,166,400 | 2.19 / 2.59 | **80.36** / 83.38 | **82.54** / 85.96 | 0.088 | 1.2% |
| 1.00 | 1920x1080 | 2,073,600 | 2.15 / 2.58 | 131.73 / 136.88 | 133.87 / 139.46 | 0.093 | 6.6% |

9. Pixel counts as above; 0.75 / 0.25 = **9.00x**.
10. **Measured ratios 0.75 / 0.25**: ray **6.10x**, combined 5.38x.
11. **Derived alpha**: two-point (0.25-0.75) **0.823** ray, 0.766 combined;
    log-log fit over five scales **0.823** ray (r² 0.9993, k = 8.31e-4),
    0.775 combined-without-fixed (r² 0.9990).

## PLAY_VIS_NEAR (MEASURED)

| scale | target | pixels | 12. field p50 / p95 | 13. ray p50 / p95 | 14. combined p50 / p95 | composite p50 | ray spread |
|---|---|---|---|---|---|---|---|
| 0.25 | 480x270 | 129,600 | 2.22 / 2.73 | **12.21** / 13.20 | **14.43** / 15.93 | 0.083 | 1.5% |
| 0.375 | 720x405 | 291,600 | 2.21 / 2.64 | 23.58 / 25.85 | 25.79 / 28.49 | 0.084 | 2.2% |
| 0.50 | 960x540 | 518,400 | 2.05 / 2.47 | 36.22 / 39.67 | 38.28 / 42.15 | 0.084 | 2.2% |
| 0.75 | 1440x810 | 1,166,400 | 2.05 / 2.42 | **67.81** / 72.98 | **69.86** / 75.40 | 0.082 | 11.0% |
| 1.00 | 1920x1080 | 2,073,600 | 2.07 / 2.43 | 107.18 / 119.67 | 109.24 / 122.10 | 0.087 | 5.8% |

15. **Measured ratios 0.75 / 0.25**: ray **5.55x**, combined 4.84x.
16. **Derived alpha**: two-point **0.780** ray, 0.718 combined; log-log fit
    **0.779** ray (r² 0.9999, k = 1.28e-3), 0.728 combined (r² 0.9996).

FAR, for completeness (MEASURED): ray 10.88 / 16.38 / 23.23 / 38.88 / 67.81 ms
at the five scales, field 2.10-2.19, combined at 0.25 **12.98 ms**; ratio
0.75 / 0.25 ray 3.57x; alpha two-point 0.580, log-log **0.650** (r² 0.986 -
the weakest fit; FAR's 0.375 and 0.50 cells sit above a straight line, the
pose where the storm covers the least of the frame).

## MODEL (DERIVED)

`time = fixed + k * pixels^alpha`, with the field build as the fixed term
(it is resolution-independent by construction and measured so: SIDE
2.14-2.23 ms across the five scales with no trend, spread 20%;
PLAY_VIS_NEAR 2.05-2.22, spread 24%; FAR 2.04-2.19, spread 10% - session
noise, not a scale effect) and the ray as the pixel-dependent term.

| pose | 17. fixed (ms) | 18. k, alpha | predicted 0.25 / 0.375 / 0.50 / 0.75 / 1.00 | measured | 19. fit |
|---|---|---|---|---|---|
| SIDE | 2.18 | 8.31e-4, **0.823** | 15.5 / 28.2 / 43.9 / 83.5 / 132.7 | 15.3 / 29.2 / 43.0 / 82.5 / 133.9 | r² 0.9993, worst residual 3.5% (0.375) |
| PLAY_VIS_NEAR | 2.12 | 1.28e-3, **0.779** | 14.4 / 25.3 / 38.4 / 70.4 / 109.1 | 14.4 / 25.8 / 38.3 / 69.9 / 109.2 | r² 0.9999, worst residual 1.9% |
| FAR | 2.12 | 4.80e-3, 0.650 | 12.2 / 19.2 / 26.9 / 44.1 / 63.1 | 13.0 / 18.5 / 25.3 / 41.1 / 69.9 | r² 0.986, worst residual 10% (1.00) |

Confidence: five scales, three repeats each, repeat spreads 0.5-2.2% at
0.25-0.50 and 6-11% at 0.75-1.00 (the first cell after a scale jump runs
slow - the same warm-up T199 saw after its reload; the reported p50 is the
mean of three). The two-point and five-point alphas agree to 0.001 at SIDE
and PLAY_VIS_NEAR, so the two trustworthy endpoints alone would have given
the same answer; the fit adds that the curve is a single power law over the
16x pixel range (r² >= 0.999). FAR is the outlier and the least important
pose.

**Against T138 (pre-field, same harness, `performance-internal-resolution.md`
section 3.1)**: SIDE n = 0.760 -> **0.823**, PLAY_VIS_NEAR 0.761 -> **0.779**,
FAR 0.711 -> 0.650; the 0.75 -> 0.25 ray speed-up went from 4.92x -> **6.10x**
at SIDE and 4.75x -> **5.55x** at PLAY_VIS_NEAR. Removing the per-sample
light and probe walks made the ray cost *more* linear in pixels at the two
poses that matter, not less - what remains per pixel is the primary march,
which does scale with pixel count. T151's "pixels^0.49-0.75" band is
superseded at these poses: it is 0.78-0.82 now.

## TEMPORAL BUDGET (DERIVED)

Raw FINAL cloud cost = ray + field build at that scale (composite 0.08-0.09
ms is included in "frame", not in "cloud"; there is no reconstruction pass
to time).

| 20. candidate raw scale | pose | 21. raw cloud (ray + field) | 22. budget left to 10 ms | 23. budget left to 8 ms |
|---|---|---|---|---|
| **0.25** (480x270) | SIDE | **15.35** (13.17 + 2.18) | **-5.35** | **-7.35** |
| 0.25 | PLAY_VIS_NEAR | **14.43** (12.21 + 2.22) | **-4.43** | **-6.43** |
| 0.25 | FAR | 12.98 (10.88 + 2.10) | -2.98 | -4.98 |
| 0.375 (720x405) | SIDE | 29.23 | -19.23 | -21.23 |
| 0.375 | PLAY_VIS_NEAR | 25.79 | -15.79 | -17.79 |
| 0.50 (960x540) | SIDE | 42.96 | -32.96 | -34.96 |
| 0.50 | PLAY_VIS_NEAR | 38.28 | -28.28 | -30.28 |

**At every candidate raw scale the raw cost alone exceeds the 10 ms target,
with nothing left for reconstruction or history validation.** At 0.25 - the
lowest scale the brief lists and the one the whole T13x-T19x line has been
measured at - the ray alone is 12.2-13.2 ms and the field build another 2.1-2.2.
From the model, a raw cost of 6 ms (leaving 2-4 ms for reconstruction under
the two targets) would need roughly **0.12 scale** at both poses (about 225x125, ~28,000-30,000
px: SIDE px^0.823 = (6 - 2.18) / 8.31e-4, PLAY_VIS_NEAR px^0.779 = (6 - 2.12) /
1.28e-3), i.e. reconstructing 1440x810 from a ~1/40th-pixel image - ten
times the 4-phase ratio the historical
reconstruction evidence (SSIM 0.9835, 86.7% gap closure) was measured at.
The field build is a fixed 2.1-2.2 ms this session (1.65 in T199's) -
27% of the 8 ms budget before a ray is marched.

24. **Recommended starting raw scale: none meets the test.** If temporal work
    were pursued regardless, 0.25 is the only scale whose raw cost is within
    2x of the target, and it starts 4.4-5.4 ms over it.

## DECISION

25. **TEMPORAL READY: NO.**
    1. Does reducing raw resolution now produce a meaningful ray reduction?
       **Yes** - 5.55-6.10x for 9x fewer pixels (alpha 0.78-0.82), better than
       the 4.75-4.92x of T138 and well above the 1.42x-for-half that closed
       T151; reconstruction's *ceiling* has genuinely risen. This criterion
       passes.
    2. Does enough budget remain for reconstruction and history validation?
       **No** - at the lowest candidate raw scale the budget left is -4.4 ms
       (PLAY_VIS_NEAR) and -5.3 ms (SIDE) against 10 ms, -6.4 / -7.3 against
       8 ms. There is no budget; there is a deficit before the first
       reconstruction instruction.
    3. Can the total plausibly approach 8-10 ms? **No** from any listed raw
       scale; only from ~0.12, which is outside the quality evidence.
    A better exponent with a raw cost this high is a scaling result, not a
    readiness result: the per-pixel cost at 480x270 is 13 ms / 129,600 px ~
    100 ns a pixel, and that, not the pixel count, is what stands between
    FINAL and the budget.
26. Not applicable.
27. **Next measured optimization target: a fresh *time* attribution of the
    field FINAL** - the T166/T193-style production-context ceiling arms
    (`t166_nodetail`, `t166_lightnodetail`, `t166_earlyterm`, the primary-body
    and refinement ceilings; they are lean variants of the same source and
    already inherit the field) re-timed against FINAL at 0.25 at SIDE and
    PLAY_VIS_NEAR, to rank the non-rain consumers by **milliseconds**, which
    the T199 shape-call shares (rain segment 66%, primary 24%, refine 6%,
    shaft 4-6%, light and probe 0) cannot do: a call count says nothing about
    what a call costs. The candidates in order of the evidence so far: the
    primary body march (24% of calls, and the component whose scaling the
    0.82 exponent now reflects), the refinement pass (6%), and the fixed 2.1
    ms field build (the empty-sky early-out T199 named is exact and cheap but
    only helps clear sky; for a storm frame the build is what it is). Rain
    stays closed by the brief, so its 66% of calls is reported and not
    ranked. That attribution is T201; the largest measured non-rain consumer
    it finds is T202's target. Temporal reconstruction is revisited when the
    raw 0.25 cost is under ~6 ms - at the current 14.4-15.3 that is a 2.4-2.6x
    per-pixel gain away.

## VALIDATION

28. `./gradlew.bat campaignGate`: `gate-t200-campaign.log` (wiring: `T170_WIRING
    campaigns=42 violations=0`, T191 reachability PASS) and
    `gate-t200-campaign-2.log` (after the pose change), both PASS.
29. `./gradlew.bat check build`: PASS (`gate-t200.log`, 47 invariants, jar).
30. **Variant scope**: no shader variant added; `T191_VARIANT_SCOPE
    declared=155 activeCampaigns=T200 registered=0 skipped=155
    productionAlwaysLoaded=2` - the campaign ran FINAL and the monolith only.
31. **Markers**: `run/t200-pixel-scaling.txt` retired to `.done`;
    `run/t132-autorun.txt` and `run/t132-autorun-exit.txt` remain.
32. `git status`: `StormCampaignRegistry.java`, `StormT132AutoDriver.java`,
    `tasks.md` modified; this report and `validation/t200-campaign/` new;
    unrelated untracked logs preserved.
33. Committed on `experiment/cloud-descriptor-k`; not merged.

Two launches of ~5 min each (run 1 lost PLAY_NEAR to the render distance,
run 2 is the record); campaign frames 7,182.
