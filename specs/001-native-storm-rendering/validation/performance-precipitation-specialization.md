# T163 - Productionized precipitation specialization

Status: **COMPLETE** 2026-09-04

/ Outcome: FINAL no longer compiles the unreachable precipitation branch.
  **1.517x mean cloud speedup across nine within-run pairs (1.458x-1.589x)**,
  **bit-identical at all three poses**, zero rejected cells.
/ Feature: 001-native-storm-rendering
/ Starting commit: `deae53f` (T162 banked at `330e786`)
/ Branch: `worktree-t098-production-ray-trace`

T162 measured that `cloudDensity` carries a precipitation branch no production
call site can reach, and that its mere presence costs about a third of the
frame. T163 turns that finding into the shipped renderer.

## 1. The production change

`cloudDensity` has two precipitation-only expressions. Both are now guarded by
`PA_PRECIPITATION_ABSENT`:

```glsl
#ifdef PA_PRECIPITATION_ABSENT
    float rainShaft = 0.0;
#else
    float rainShaft = includePrecipitation
        && PuffDensityStage != 5 && PuffDensityStage != 6
        ? rainShaftDensityAt(p, mipBias) : 0.0;
#endif
```

```glsl
#ifdef PA_PRECIPITATION_ABSENT
    bool precipitationCandidate = false;
#else
    bool precipitationCandidate = includePrecipitation
        && MaxPrecipitation > 0.02 && p.y < SlabBaseY + 48.0;
#endif
```

Both are provably no-ops in production: **all 19 production call sites pass
`includePrecipitation = false`** (T162 parsed every one), so `rainShaft` was
already `0.0` and its only consumer is `+ rainShaft`, and
`precipitationCandidate` was already `false`.

The point is not to skip work at runtime - nothing was being done at runtime.
The point is that a branch the compiler cannot fold still forces register
allocation and depresses occupancy for every call to a function that is invoked
2.55M times a frame. Making it compile-time dead is what removes that.

### Why this shape

It extends the T161 generation architecture rather than adding a second
mechanism. There is still one source of truth for the cloud equations: the
single `cloud_atmosphere_volume.fsh`, from which every program is generated.
Which programs define the macro is the only thing that changed.

| Program | Precipitation branch | Why |
|---|---|---|
| `cloud_atmosphere_volume_final` (FINAL) | **absent** | the optimization |
| `..._t140_pixel` / `_mask` / `_tile8` / `_tile16` | **absent** | must stay bit-identical to FINAL |
| `..._t162_norain` | absent | this specialization is what it always was |
| `cloud_atmosphere_volume` (monolith) | present | every diagnostic campaign links it |
| `..._t162_fw7_density` | **present** | the only arm that passes `true`; it measures executing rain |
| `..._t163_withrain` | present | the pre-T163 FINAL, kept as the measurement baseline |

## 2. Proof the path is absent from FINAL

A new build gate, `T163 FINAL is specialized against the dead precipitation
path`, asserts both directions and fails the build otherwise:

- FINAL and the four T140 oracle programs **must** contain
  `#define PA_PRECIPITATION_ABSENT`;
- `cloud_atmosphere_volume_t163_withrain` and `..._t162_fw7_density` **must not**
  contain it, so the capability is not lost globally;
- the baseline program compiles on a real GL context.

This gate exists for a specific regression. The branch is dead at runtime either
way, so restoring it to FINAL would change no pixel and pass every image check
while silently giving back 1.5x. Nothing else in the suite would notice.

## 3. Image A/B - exact, not epsilon

Captured back to back per pose, anchor first, with the fixture qualified
immediately before and after. T162 exposed two harness defects here (captures
taken minutes apart, and a capture running after the per-arm program re-pin);
both are avoided by construction, and the lighting arm is kept as the self-check.

| Pose | new FINAL vs old FINAL | changed pixels | max error | self-check (`t162_nolight`) |
|---|---|---|---|---|
| PLAY_VIS_NEAR | **PASS_IDENTICAL** | **0** / 129,600 | **0.000000e+00** | PASS_DIFFERS |
| PLAY_VIS_MID | **PASS_IDENTICAL** | **0** / 129,600 | **0.000000e+00** | PASS_DIFFERS |
| SIDE | **PASS_IDENTICAL** | **0** / 129,600 | **0.000000e+00** | PASS_DIFFERS |

Epsilon equivalence was not accepted. The requirement was exact, and the result
is exact.

The self-check row is what makes the zeroes meaningful: a harness that cannot
detect a difference would report identical for the lighting arm too, which is
precisely the failure T162 hit.

## 4. Rain still works

Rain renders through `rainShaftDensityOverSegment`, which the march calls
directly at one site - not through `cloudDensity`. T163 does not touch it.

The runtime evidence is the A/B itself: rain shafts are part of the rendered
frame, and all three poses are bit-identical across the change. Anything the
removed branch had contributed - shaft density, intensity, or the T145 rain
locality that feeds it - would have shown up as changed pixels. Nothing did.

The fixture is rain-bearing (`cloudCover=0.905`, `regionalCoverage=0.637` on the
storm), so this is not a comparison of two rain-free frames.

## 5. Performance

Nine pairs, each old and new measured back to back inside one run against one
fixture, 60 samples per cell, every cell qualified at both ends.

| Pose | Scale | Target | old p50 | new p50 | **p50 speedup** | old p95 | new p95 | p95 speedup |
|---|---|---|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 0.25 | 480x270 | 37.208 | **24.517** | **1.518x** | 40.863 | 26.549 | 1.539x |
| PLAY_VIS_NEAR | 0.375 | 720x405 | 63.689 | **42.660** | **1.493x** | 70.530 | 45.665 | 1.545x |
| PLAY_VIS_NEAR | 0.50 | 960x540 | 99.867 | **64.799** | **1.541x** | 106.242 | 68.811 | 1.544x |
| PLAY_VIS_MID | 0.25 | 480x270 | 25.468 | **16.697** | **1.525x** | 30.982 | 19.788 | 1.566x |
| PLAY_VIS_MID | 0.375 | 720x405 | 39.577 | **24.908** | **1.589x** | 42.093 | 27.217 | 1.547x |
| PLAY_VIS_MID | 0.50 | 960x540 | 58.873 | **40.369** | **1.458x** | 63.427 | 43.736 | 1.450x |
| SIDE | 0.25 | 480x270 | 34.590 | **23.127** | **1.496x** | 36.141 | 24.226 | 1.492x |
| SIDE | 0.375 | 720x405 | 63.142 | **41.259** | **1.530x** | 67.289 | 43.533 | 1.546x |
| SIDE | 0.50 | 960x540 | 95.826 | **63.861** | **1.501x** | 101.662 | 68.383 | 1.487x |

**Mean p50 speedup 1.517x, range 1.458x-1.589x.** T162 predicted 1.486x-1.581x
from the attribution arm; the productionized change retains essentially all of
it (1.517 against a ~1.53 attribution mean, about 99%). It is not a one-pose
artifact: three geometries and three resolutions all land in the same band.

Frame p50 at PLAY_VIS_NEAR / 0.25 falls 38.049 -> 25.389 ms.

### Fixture workload

Production counters for this fixture, at 480x270 (captured on the diagnostic
monolith, which is the only program carrying the counters - **they describe the
fixture, and cannot distinguish the two arms**; the arms are distinguished by
the image A/B and by the timings above):

| Counter | Value | Per cloud pixel | Per density call |
|---|---|---|---|
| primary ray steps | 3,749,640 | 28.93 | 1.47 |
| cloud density calls | 2,551,783 | 19.69 | 1.00 |
| light-march density evaluations | 1,127,128 | 8.70 | 0.442 |
| descriptor texture fetches | 369,758,161 | 2,853 | 144.9 |
| descriptor evaluations | 40,044,306 | 309.0 | 15.69 |
| **rain density calls** | **0** | 0 | 0 |
| early terminations | 18,092 | 0.14 | - |

The zero rain density calls is the counter form of the whole finding.

## 6. Resolution frontier, re-measured

PLAY_VIS_NEAR, new FINAL, fresh qualified cells. Old-arm figures are from the
same run, so this is not a comparison against remembered numbers.

| Scale | Target | cloud p50 | cloud p95 | frame p50 | pre-T163 p50 | scaling vs new 0.25 |
|---|---|---|---|---|---|---|
| 0.25 | 480x270 | **24.517** | 26.549 | 25.389 | 37.208 | 1.00x |
| 0.375 | 720x405 | **42.660** | 45.665 | 43.743 | 63.689 | 1.74x |
| 0.50 | 960x540 | **64.799** | 68.811 | 65.800 | 99.867 | 2.64x |

Pixel ratios are 2.25x and 4.00x, so scaling stays sublinear (exponent ~0.68).

### Did 0.375 become materially more practical?

**Materially closer, but not yet shippable.** 0.375 now costs 42.66 ms against
the 37.21 ms that 0.25 cost before this change - so **0.375 today sits roughly
where 0.25 sat yesterday**, a real shift: it was 1.71x the old 0.25 and is now
1.15x of it. Against the *current* 0.25 it is still 1.74x, and against SC-006 it
is 5.3x the budget.

### 0.50?

**No.** 64.80 ms is 2.64x the current 0.25 and 8.1x the budget. It moved from
99.87 to 64.80 ms, which is progress, but it is not close.

### Remaining gap to SC-006

SC-006 is 8 ms and remains unmet and unrescoped. Ultra 0.25 goes from **4.65x**
the budget to **3.06x** - the absolute gap closes from 29.2 ms to 16.5 ms. This
is the largest single step taken toward it since T161, and it cost no image
quality at all.

## 7. Validation

| Invariant | Result |
|---|---|
| T111 production storm shader compiles | PASSED |
| T161 lean FINAL shader specializes and compiles | PASSED |
| T140 oracle variants compile and stay out of FINAL | PASSED |
| T162 attribution arms compile and stay out of FINAL | PASSED |
| **T163 FINAL is specialized against the dead precipitation path** | **PASSED** |
| `./gradlew check build` | **BUILD SUCCESSFUL** |

Evidence hygiene: 42 fixture qualifications across 18 timing cells and 3 image
sequences, **0 rejected**. No other Minecraft or Java benchmark instance was
running at launch. Every speedup is a within-run pair, so steady background load
cancels rather than biasing the comparison.

Nothing about the shipping quality ladder, Ultra scale, morphology or lighting
was changed by this task.
