# T140 - Post-T161 five-mode baseline and screen-coverage bottleneck localization

Status: **COMPLETE** 2026-09-04

/ Outcome: **CASE B** - whole-pixel culling is already effectively solved and is
  measured closed at a ~1.09x ceiling. The dominant remaining cost is density
  evaluation inside cloud-relevant rays.
/ `./gradlew check build`: BUILD SUCCESSFUL; T111, T161 and the new T140 shader
  invariants all PASSED.

/ Feature: 001-native-storm-rendering
/ Starting commit: `a59ce46` (T161 banked at `4844cc8`)
/ Branch: `worktree-t098-production-ray-trace`
/ Banking commit: `122a566`

T140 is a measurement and bottleneck-localization task. It implements no
optimization. It answers two questions that T153 did not:

- **A.** What does a cloud-relevant ray still cost after T161?
- **B.** How much of the pass is spent on pixels that could have been rejected
  before the expensive volumetric traversal ever started?

T153 measured a **1.63x** ceiling for empty-space and tail skipping *within rays
already participating in cloud rendering*. It never bounded whole-pixel or
whole-tile rejection. Those are separate quantities and are reported separately.

## 0. A contamination finding in the Ultra recovery sweep

The Ultra resolution recovery evidence is in
`ultra-recovery-*.out.log`. The shipped ladder quoted from it is sound, but one
of its three runs is partly contaminated and must not be reused.

In `ultra-recovery-primary-run.out.log` a single eight-arm sweep produced:

| scale | target | cloud p50 |
|---|---|---|
| 1.000 | 1920x1080 | 275.36 ms |
| 0.750 | 1440x810 | 158.02 ms |
| 0.500 | 960x540 | 93.45 ms |
| 0.375 | 720x405 | **7.47 ms** |
| 0.250 | 480x270 | **3.82 ms** |
| 0.188 | 360x203 | **2.41 ms** |
| 0.125 | 240x135 | **1.43 ms** |
| 0.177 | 340x191 | **2.31 ms** |

The cost collapses by roughly 9x between the 0.500 and 0.375 arms of the same
sweep, at the same pose, with `descriptors=10` reported throughout. The two
independent runs measured 0.250 at 33.40 / 34.96 ms and 0.375 at 60.91 / 67.88
ms, agreeing with each other and with this run **before** the collapse
(0.500: 93.45 / 98.97 / 102.83 ms across all three). The five cheap cells are
therefore not a resolution effect.

Cause: `T138` verifies pose visibility **once**, at the start of the pose, and
then runs every scale arm without re-verifying. The single
`T150_VISIBILITY_CONFIRMED` in that run is at 23:10:05; all eight arms complete
by 23:11:17.

The ladder the task quotes draws only from valid cells, so it stands unchanged.
But the cheap cells are a live hazard in the same logs, and the same
single-verification weakness applies to any future multi-arm sweep.

They are also a hint at what T140 set out to measure: post-sweep status lines
show the same 480x270 target costing ~3.2 ms with ten descriptors still
resident, against 33.40 ms when the storm is actually in frame - a ~10x spread
at identical pixel count. T140 measures that properly rather than inferring it.

## 1. Method

### Why the existing evidence could not answer question B

Production has essentially **no whole-pixel rejection**. The one mechanism that
existed - the coverage pre-test - is disabled:

```java
private static volatile boolean coveragePretestEnabled = false;
```

It was turned off because uniform ray probes missed narrow cloud footprints and
cut horizontal bands out of otherwise valid volumes. With it off the shader
takes `anyCoverage = (CoveragePretestEnabled == 0)` -> true, so **every ray that
intersects the cloud slab marches**. The slab is the full vertical cloud extent
(130..1012 blocks on these fixtures), so most of the sky qualifies.

### The diagnostic oracle

T140 adds a diagnostic-only oracle that may know, for free, whether a pixel can
reach cloud at all. It is compiled into **separate programs**; FINAL never
defines `PA_T140_ORACLE`, so none of it exists in the shipped renderer and the
T161 specialization is unchanged. A build-time gate asserts that separation.

| Program | Granularity | Purpose |
|---|---|---|
| `cloud_atmosphere_volume_final` | - | shipped lean FINAL, the baseline |
| `..._t140_pixel` | per pixel | perfect whole-pixel rejection |
| `..._t140_tile8` | 8x8 tile | how much survives coarse classification |
| `..._t140_tile16` | 16x16 tile | as above, coarser |
| `..._t140_mask` | per pixel | renders the verdict, for counting coverage |

The bound is a conservative vertical cylinder around the resident storm
descriptors: the XZ radius reuses `StormLobeEvaluator.horizontalReachBlocks`,
the expression `paBuildStormReachability` uses and whose zero-false-negative
sweep is its authority, and the Y extent is the cloud slab that already clips
`t0`/`t1`. Funnels and resident puff lobes are not covered by that disc, so
their presence disables rejection rather than risking a false one.

A rejected ray emits `fragColor = vec4(0.0); gl_FragDepth = 1.0` - exactly what
`main()` itself emits whenever `result.a < 0.002`, and history is only consumed
when the ray actually hit cloud, so a provably empty ray produces that and
nothing else.

Two honesty constraints on the numbers below:

1. The bound is rebuilt per fragment and its test is **included** in every
   measurement. The measured gain is therefore a **lower** bound on the true
   ceiling, not an optimistic one.
2. Bit-identity is **verified per pose** by image A/B against the lean FINAL
   frame, not assumed. A rejection that changed one pixel would invalidate the
   speedup measured against it.

### Coverage fixtures

All five stand at the same point - PLAY_VIS_NEAR's position, 1.6 storm radii
out at y=120 - and differ only in where the camera looks, so cost differences
are attributable to on-screen cloud and to nothing else.

| Fixture | Aim | Intent |
|---|---|---|
| `PLAY_VIS_NEAR` | at the storm | storm-heavy reference |
| `T140_PARTIAL` | yaw +30 deg | cloud a minority of the viewport |
| `T140_EDGE` | yaw +75 deg | only a sliver of cloud in frame |
| `T140_AWAY_180` | yaw +180 deg | no cloud can contribute |
| `T140_AWAY_DOWN` | yaw +180, pitch +88 deg | rays leave the slab immediately |

The T150 visibility guard is deliberately bypassed for the `T140_*` poses: it
exists to reject empty-sky cells, and here the empty sky is the measurement.
Its geometric verdict is still logged. What was actually on screen is
established directly by the per-pose pixel census rather than by that heuristic.

## 2. Five-mode post-T161 baseline

One pose (PLAY_VIS_NEAR), each mode at its own shipped ladder resolution, on the
banked lean FINAL program. 120 sampled frames per cell, ten descriptors. No
pre-T161 measurement is reused.

| Mode | raymarch steps | scale | target | cloud p50 | cloud p95 | frame p50 | frame p95 |
|---|---|---|---|---|---|---|---|
| Low | 24 | 0.125 | 240x135 | **8.798** | 9.158 | 9.501 | 10.813 |
| Low 24 | 32 | 0.125 | 240x135 | **11.371** | 12.194 | 12.025 | 13.972 |
| Medium | 40 | 0.125 | 240x135 | **12.101** | 21.470 | 12.887 | 21.765 |
| High | 64 | 0.1875 | 360x203 | **24.620** | 26.639 | 25.325 | 28.112 |
| Ultra | 96 | 0.250 | 480x270 | **38.394** | 41.525 | 39.443 | 42.793 |

Per-mode traversal counters, same cells:

| Mode | px | primary steps | density calls | light-march evals | descriptor evals | descriptor fetches | empty rejects | early terms |
|---|---|---|---|---|---|---|---|---|
| Low | 32,400 | 429,532 | 204,990 | 64,467 | 4,005,808 | 42,122,068 | 386,333 | 4,266 |
| Low 24 | 32,400 | 516,542 | 336,428 | 146,904 | 5,580,761 | 53,912,719 | 450,251 | 4,172 |
| Medium | 32,400 | 577,747 | 389,284 | 165,064 | 6,362,959 | 61,010,232 | 502,264 | 4,243 |
| High | 73,080 | 1,679,895 | 1,162,341 | 476,672 | 18,616,299 | 177,782,683 | 1,462,211 | 9,900 |
| Ultra | 129,600 | 3,760,786 | 2,499,896 | 1,033,928 | 40,761,150 | 390,623,949 | 3,305,688 | 17,624 |

Normalized per pixel:

| Mode | steps/px | density/px | descriptor fetches/px | ms per M density |
|---|---|---|---|---|
| Low | 13.3 | 6.3 | 1,300 | 42.9 |
| Low 24 | 15.9 | 10.4 | 1,664 | 33.8 |
| Medium | 17.8 | 12.0 | 1,883 | 31.1 |
| High | 23.0 | 15.9 | 2,433 | 21.2 |
| Ultra | 29.0 | 19.3 | 3,014 | 15.4 |

An independent repeat of the same five cells (mislabeled pose, same camera)
gave 7.963 / 10.604 / 11.031 / 24.802 / 36.849 ms - within about 10%.

**Against SC-006 (8 ms cloud budget), no mode passes.** Low is closest at 8.80
ms; Ultra is 38.39 ms, **4.8x** the budget. SC-006 is not rescoped here - it is
recorded as unmet, as it was at T139.

Note also that cost per density call *falls* as resolution rises (42.9 to 15.4
ms/M): the pass is under-occupied at small targets, which is part of why the
resolution ladder scales sublinearly in section 8.

## 3. Screen-coverage census

Counted from two captured images per pose, at one pinned clock with history
bypassed. `potential` is the oracle verdict; `contributing` is the pixels the
lean FINAL frame actually delivered cloud in.

| Fixture | target | total px | potential px | potential % | contributing px | contributing % | provably irrelevant |
|---|---|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 480x270 | 129,600 | 129,467 | 99.897 | 17,979 | **13.873** | 133 (0.103%) |
| T140_PARTIAL | 480x270 | 129,600 | 129,600 | 100.000 | 30,045 | **23.183** | 0 |
| T140_EDGE | 480x270 | 129,600 | 129,600 | 100.000 | 39,696 | **30.630** | 0 |
| T140_AWAY_180 | 480x270 | 129,600 | 129,600 | 100.000 | 0 | **0.000** | 0 |
| T140_AWAY_DOWN | 480x270 | 129,600 | 129,600 | 100.000 | 0 | **0.000** | 0 |

**A conservative volume bound rejects essentially nothing, even looking directly
away from the storm.** That is a result, not a defect in the oracle. At gameplay
altitude the camera stands *inside* the storm footprint inflated by its guard
band, so the ray starts inside the bound and trivially intersects it whatever
direction it points. The first attempt used a single union disc and rejected
0.000% everywhere; tightening it to ten per-descriptor cylinders, each with its
own height range, moved that only to 0.103% at one pose.

The direct consequence for architecture: **projected conservative descriptor
bounds - the most obvious screen-space culling design - would cull nothing at
exactly the poses whose cost matters.** Any workable culling would have to be
tighter than the conservative reach the density field itself uses, which is a
far harder proposition than a bounding volume.

## 4. Cost versus coverage

Same target size for all five - only the aim changes.

| Fixture | contributing % | cloud p50 | cloud p95 | run-1 p50 (repeat) |
|---|---|---|---|---|
| PLAY_VIS_NEAR | 13.873 | 37.117 ms | 40.964 ms | 39.611 ms |
| T140_PARTIAL | 23.183 | 46.389 ms | 50.668 ms | 50.885 ms |
| T140_EDGE | 30.630 | 39.938 ms | 44.246 ms | 43.308 ms |
| T140_AWAY_180 | 0.000 | **3.588 ms** | 3.633 ms | 3.637 ms |
| T140_AWAY_DOWN | 0.000 | **2.766 ms** | 2.841 ms | 2.791 ms |

Both runs agree within about 6%. Cost does **not** follow render-target area,
which is identical across all five. It does not follow contributing coverage
either: EDGE has more than twice the contributing pixels of PLAY_VIS_NEAR and
costs slightly less, because PLAY_VIS_NEAR looks along the thickest chord of the
storm. Cost follows **the work done inside cloud-relevant rays** - depth times
per-sample cost - not the count of covered pixels.

## 5. The zero-cloud floor

`T140_AWAY_180` is the critical measurement: a full 480x270 target, ten
descriptors resident, camera pointed 180 degrees away, **zero** contributing
pixels.

| Quantity | AWAY_180 | AWAY_DOWN | PLAY_VIS_NEAR |
|---|---|---|---|
| cloud p50 / p95 | 3.588 / 3.633 ms | 2.766 / 2.841 ms | 37.117 / 40.964 ms |
| target pixels | 129,600 | 129,600 | 129,600 |
| primary ray steps | 3,483,127 | 1,542,957 | 3,769,924 |
| **cloud density calls** | **0** | **0** | **2,645,590** |
| light-march density evals | 0 | 0 | 1,042,160 |
| descriptor evaluations | 6,944,204 | 6,171,828 | 40,333,375 |
| descriptor texture fetches | 102,402,856 | 91,589,592 | 384,552,241 |
| empty-space rejects | 3,483,127 | 1,542,957 | 3,302,783 |
| early terminations | 0 | 0 | 18,079 |
| steps per pixel | 26.9 | 11.9 | 29.1 |

**The floor is 3.588 ms - 9.7% of the storm-heavy frame.** The reason is visible
in the counters: looking away still marches **3.48M primary steps, 92% as many
as looking straight at the storm**, but performs **zero** density evaluations.
Every one of those steps is rejected as empty.

March steps are therefore close to free. The entire difference between 3.6 ms
and 37 ms is density evaluation and the lighting it drives.

## 6. Perfect whole-pixel rejection

| Fixture | baseline p50 | oracle p50 | speedup p50 | speedup p95 |
|---|---|---|---|---|
| PLAY_VIS_NEAR | 37.117 | 37.780 | 0.983x | 0.998x |
| T140_PARTIAL | 46.389 | 45.910 | 1.010x | 1.038x |
| T140_EDGE | 39.938 | 41.236 | 0.969x | 0.965x |
| T140_AWAY_180 | 3.588 | 3.598 | 0.997x | 0.991x |
| T140_AWAY_DOWN | 2.766 | 2.779 | 0.995x | 0.999x |

**No gain anywhere: 0.97x to 1.04x, which is noise.** The oracle rejects almost
nothing (section 3), and its own bound test costs a little, which is why several
arms sit just below 1.0.

Every arm was verified bit-identical to the lean FINAL frame -
`maxAbsRGBA = 0.000000e+00`, `passed=true`, at all five poses in both runs - so
the rejection mechanism is correct. It simply has nothing to reject.

### The ceiling, independent of the oracle

A measured oracle is only as good as its bound, so it is not by itself proof
that culling cannot help. The floor measurement is. Rejecting a pixel can save
at most what an empty pixel costs, and `AWAY_180` prices a full screen of empty
pixels at 3.588 ms, or **27.7 ns per empty pixel**.

| Fixture | empty px | max saving | cloud p50 | best possible | ceiling |
|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 111,621 | 3.09 ms | 37.117 | 34.03 | **1.091x** |
| T140_PARTIAL | 99,555 | 2.76 ms | 46.389 | 43.63 | **1.063x** |
| T140_EDGE | 89,904 | 2.49 ms | 39.938 | 37.45 | **1.066x** |

This assumes rejection is both perfect and free, so it is a hard upper bound.
**Whole-pixel screen-space culling is worth at most about 1.09x**, and the
oracle measured no gain, which is consistent.

## 7. Tile oracle

| Fixture | pixel | 8x8 tile | 16x16 tile |
|---|---|---|---|
| PLAY_VIS_NEAR | 0.983x | 0.980x | 0.989x |
| T140_PARTIAL | 1.010x | 0.993x | 0.995x |
| T140_EDGE | 0.969x | 0.986x | 0.990x |
| T140_AWAY_180 | 0.997x | 0.996x | 0.996x |
| T140_AWAY_DOWN | 0.995x | 0.987x | 0.987x |

Coarsening changes nothing, because there is no per-pixel benefit to lose. The
tile arms confirm the pixel result rather than adding to it.

## 8. What the cost actually tracks

Two independent axes were measured.

**Target area, at fixed coverage** (Ultra recovery sweep, PLAY_VIS_NEAR):

| scale | pixels | relative pixels | cloud p50 | relative time |
|---|---|---|---|---|
| 0.250 | 129,600 | 1.00x | 33.40 ms | 1.00x |
| 0.375 | 291,600 | 2.25x | 60.91 ms | 1.82x |
| 0.500 | 518,400 | 4.00x | 98.97 ms | 2.96x |
| 0.750 | 1,166,400 | 9.00x | 158.02 ms | 4.73x |
| 1.000 | 2,073,600 | 16.00x | 275.36 ms | 8.24x |

Sublinear: a log-log fit gives an exponent of about **0.76**, so doubling the
pixels costs about 1.7x rather than 2x.

**Coverage, at fixed area** (section 4): 2.77 ms to 46.39 ms, a **16.8x** range
with pixel count held constant.

Cost therefore tracks neither pixels nor covered pixels on their own. The best
predictor available here is **density evaluations, weighted by the light-march
work they trigger**:

| Fixture | density calls | light-march evals | light per density | (p50 - floor) per M density |
|---|---|---|---|---|
| PLAY_VIS_NEAR | 2,645,590 | 1,042,160 | 0.394 | 12.67 ms |
| T140_PARTIAL | 4,956,714 | 1,365,640 | 0.276 | 8.63 ms |
| T140_EDGE | 4,462,555 | 1,143,568 | 0.256 | 8.14 ms |
| T140_AWAY_180 | 0 | 0 | - | - |

The residual ordering follows the lighting ratio: PLAY_VIS_NEAR pays about 50%
more per density call than EDGE, and has about 50% more light-march work per
density call. Lighting was not isolated in this campaign, so this is a
correlation across three poses, not an attribution.

One counter stands out on its own. At PLAY_VIS_NEAR the frame issues **384.6M
descriptor texture fetches** against 2.65M density calls with only **ten
resident descriptors** - roughly 145 texel fetches per density evaluation, or
about 104 per density-plus-light evaluation. Even the zero-density AWAY_180
frame issues 102.4M of them. Descriptor fetch volume, not pixel count, is where
the traversal spends its bandwidth.

## 9. Whole-ray and whole-pixel classification

The classes T140 was asked to separate, at PLAY_VIS_NEAR / Ultra / 480x270:

| Class | Definition | Pixels | Share | Cost |
|---|---|---|---|---|
| **A** | ray cannot intersect any cloud-relevant volume (provably, by a conservative bound) | 133 | **0.10%** | negligible |
| **B** | ray intersects the bound but never reaches material | 111,488 | **86.03%** | ~3.09 ms (8.3%) |
| **C** | ray reaches cloud material | 17,979 | **13.87%** | ~34.0 ms (91.7%) |
| **D** | reaches material and terminates through transmittance | 18,079 early terminations | ~all of C | included in C |
| **E** | exits material and spends further work on a tail | small at these poses | - | see below |

Class **A** is the quantity T140 was created to measure, and it is essentially
**zero**. That is the headline: the pixels that could be rejected before
marching are not the pixels that cost anything.

Class **B** is large in pixel count but cheap: those rays march the slab and
have every step rejected as empty, which section 5 prices at 27.7 ns per pixel.

Class **D** covers effectively all of class C - early terminations (18,079)
match contributing pixels (17,979) almost exactly, so nearly every ray that
reaches material also hits the transmittance cutoff rather than running to the
far plane. Class **E** is correspondingly small at these poses, which is
consistent with T153 having found its skippable distance mostly *after* leaving
material at other poses rather than here.

## 10. The two optimization ceilings, kept separate

### Opportunity 1 - whole-pixel / screen-space culling

**Ceiling: about 1.09x. Measured gain: none (0.97x-1.04x).**

Derived independently of the oracle from the zero-cloud floor, and confirmed by
a bit-identical oracle at three granularities. The reason is structural: 86% of
pixels are empty but they collectively cost only ~8% of the frame, and a
conservative bound cannot even identify them because the camera stands inside
the storm footprint at gameplay range.

**This opportunity is closed.** It should not be built.

### Opportunity 2 - within-ray traversal

T153 measured a **1.63x** ceiling for empty-space and tail skipping inside rays
already participating in cloud rendering, and found most skippable distance was
after leaving storm material.

**That number carries an important caveat here: it was measured before T161.**
T161 removed roughly two thirds of the frame cost without changing a single
sample, so the *composition* of what remains is different. The 1.63x ceiling
should be re-derived on the lean program before it is used to size any work.

### Do they stack?

Only in the trivial sense. They act on disjoint pixels - Opportunity 1 on empty
pixels, Opportunity 2 on cloud-relevant ones - so their savings are additive
rather than multiplicative, and Opportunity 1 contributes at most 3.1 ms of 37.
An optimistic combined figure is about 1.09 x 1.63 = **1.78x**, but that
multiplies a measured ceiling by a stale one and should not be planned against.

## 11. Dominant remaining cost class

**Density evaluation inside cloud-relevant rays, and the light march it drives.**

Three lines of evidence agree:

1. Looking away marches 92% as many primary steps but performs **zero** density
   calls, and costs 9.7% as much.
2. Removing every empty pixel could recover at most 8.3% of the frame.
3. Across poses, cost after subtracting the floor scales with density calls
   weighted by their light-march ratio.

Within that, one counter is anomalous enough to name: at Ultra the frame issues
**390.6M descriptor texture fetches** for 2.50M density calls, with **ten**
resident descriptors - about **156 texel fetches per density evaluation**, or
~110 per density-plus-light evaluation. Ten descriptors are four texels each,
so a sample that touched every resident descriptor once would need 40. The
traversal is doing several times that.

## 12. Recommended next step

**Do not build screen-space culling.** Opportunity 1 is measured closed.

**Recommended next task: an attribution experiment isolating per-sample
descriptor traversal cost inside a density evaluation** - the same shape as
T136 did for lighting. It should answer how much of a density call is
descriptor fetch and traversal versus noise, profile and SDF arithmetic. Only
then commit to an architecture.

The architecture that evidence currently points at, pending that attribution:
**reduce descriptors examined per sample** by binning the resident descriptors
spatially once per frame - a coarse world-space voxel or froxel list - so a
sample tests the one to three descriptors that can own it rather than
traversing the candidate structure repeatedly. T122 already reuses fetches
within a sample; the fetch-per-density ratio suggests that reuse is not
surviving across samples or across the light march.

**Expected upper bound.** Any within-ray optimization is bounded below by the
empty-march floor: if density evaluation became free at Ultra, the frame would
fall from 38.4 ms to about 3.6 ms, so the theoretical ceiling for this class is
roughly **10x**. That is a bound, not a forecast - what fraction is reachable
depends on the attribution above, which has not been measured.

### Resolution retesting

| Question | Answer |
|---|---|
| Retest 0.375 after screen-space culling? | **No.** At 1.09x, 0.375 would go 60.91 -> 55.9 ms. The verdict would not change. |
| Retest 0.375 after the next optimization? | Only once a measured **>=1.6x** on cloud-relevant rays lands - that is what brings 0.375 to what 0.25 costs today. |
| Retest 0.50? | Requires about **2.7x-2.9x**. Not before then. |

Ultra stays at 0.25 / 480x270. T140 changed no ladder value, no morphology, and
no rendering semantics.

## 13. Validation

- **T161 specialization invariant**: `T161 lean FINAL shader specializes and
  compiles` PASSED. The oracle is guarded by `PA_T140_ORACLE`, which FINAL never
  defines; a dedicated gate, `T140 oracle variants compile and stay out of
  FINAL`, asserts that separation and compiles all four variants on a real GL
  context. It caught one real defect during development (a function used before
  declaration).
- **Semantic isolation**: every oracle arm rendered bit-identically to lean
  FINAL at all five poses in both campaign runs
  (`maxAbsRGBA = 0.000000e+00`, `passed=true`).
- **Normal rendering untouched**: the oracle programs are reachable only through
  an explicit diagnostic override; automatic selection still returns
  `lean_final` for ordinary frames.
