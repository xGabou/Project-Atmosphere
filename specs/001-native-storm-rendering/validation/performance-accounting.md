# T182 - The residual is rain reachability, and it is the largest consumer there is

Branch `experiment/cloud-descriptor-k`, parent `624d7f0` (T181). Nothing merged.
One campaign 08Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose. 50 cells, `T182_REJECTED count=0` on anchor drift.

## 1. Headline

**The accounting closes. `shapeUntagged = 0` at both poses.**

**The consumer T180 mis-named and T181 could not identify is the rain-segment
reachability test**, and it is the largest single consumer of descriptor work in
the shader: **36.26% of `directStormShape` calls at SIDE and 56.67% at FAR** -
more than the light march.

It runs on **every coarse step**, and past its gate it performs **two complete
storm traversals**. The shader's own file header says so and has since T098; no
campaign had ever measured it.

**Removing it uniformly measures 1.2139x at FAR** (accepted, 2.64% spread) with
**105 changed pixels and meanAbs 1.05e-05**.

> **CORRECTION (T183).** This section originally called that a "clean ceiling...
> nearly free of image cost, because `PA_PRECIPITATION_ABSENT` already removes
> the rain this test exists to find." That reasoning was wrong.
> `PA_PRECIPITATION_ABSENT` removes precipitation from `cloudDensity` only; rain
> still renders through `rainShaftDensityOverSegment`. `t182_norainseg` deletes
> rain, so its 1.2139x is the cost of a shipped feature and not a ceiling on dead
> work. See `performance-rain-reachability.md`.

**And cap 4 does not survive composition.** On the stack it measures **0.9900x**
at FAR (accepted, 0.46% spread). T181's standalone 1.1056x is not a stack gain.
**Do not productionize it.**

## 2. Tasks 1-3 (items 3-10) - the call-site registry and the accounting

Every production-reachable path that reaches `directStormShape`, tagged at the
site. Tag 0 is counted explicitly so a residual cannot hide.

| Tag | Consumer | Source path | Production |
|---|---|---|---|
| 1 | primary body | `main` march -> `cloudDensity` | yes |
| 2 | light tap | `lightMarchOpticalDepth` -> `cloudDensity` | yes |
| 3 | empty-span probe | `main` scan -> `cloudDensity` | yes |
| 4 | bracket bisection | `main` -> `cloudDensity` | yes |
| 5 | march refinement | `main` -> `directStormShape` | yes |
| 6 | **rain segment reachability** | `main` -> `rainSegmentMayContribute` -> `localRainSupportAt` -> `directStormRainSupportAt` -> `directStormFinalDensity` | **yes** |
| 7 | rain shaft density | `rainShaftDensityAt` -> `localRainSupportAt` -> ... | yes |
| 8 | camera-inside test | `main` -> `cloudDensity` | yes, never reaches shape |
| 9 | light cheap forward probe | light path -> `cloudDensity` | yes, never reaches shape |
| - | `stormMaterialTraceAt` | debug capture | **no** - DebugView only |
| - | `paT162ShapePayload` | T162 diagnostic | **no** |

### The counts, and why they differ (item 8)

| SIDE | value |
|---|---|
| `directStormShapeCalls` | 5,739,705 |
| `cloudDensityCalls` | 3,370,120 |
| `groupFieldCalls` | 4,776,726 |
| **tagged sum** | **5,736,404** |
| **untagged (`shapeUntagged`)** | **0** |

`directStormShapeCalls` exceeds `cloudDensityCalls` by 2,369,585 because tags 5,
6 and 7 reach `directStormShape` **without going through `cloudDensity`** - the
refinement calls it directly, and the rain paths reach it via
`directStormFinalDensity`. Their sum is 2,368,447, which accounts for the excess.
`groupFieldCalls` is lower than shape calls because a shape call enters a group
only when a candidate survives, and this fixture holds one group.

**The 3,301-call gap between the tagged sum and the total (0.058%; 176 and 0.012%
at FAR) is not an unattributed consumer.** Each debug view is a separate rendered
frame, so views 52, 53 and 54 sample marginally different frames. `shapeUntagged`
is captured in the same frame as the light-forward counter and reads exactly
zero. The `shapeAccountingClosed` flag prints `false` only because I set its
tolerance at half a call rather than at cross-frame variance - the flag is too
strict, not the accounting.

## 3. Tasks 4 and 5 (items 11-17) - the complete consumer tables

### SIDE, sorted by share

| Consumer | calls/px | % of shape calls |
|---|---|---|
| **rain segment reachability** | **16.06** | **36.26%** |
| light tap | 14.97 | 33.81% |
| empty-span probe | 6.13 | 13.84% |
| primary body | 4.88 | 11.02% |
| march refinement | 1.88 | 4.24% |
| rain shaft density | 0.34 | 0.77% |
| bracket bisection | 0.002 | 0.004% |
| camera-inside / light forward | 0 | 0% |
| **untagged** | **0** | **0%** |

### FAR

| Consumer | calls/px | % of shape calls |
|---|---|---|
| **rain segment reachability** | **6.22** | **56.67%** |
| light tap | 1.83 | 16.64% |
| empty-span probe | 1.66 | 15.15% |
| primary body | 0.78 | 7.11% |
| march refinement | 0.35 | 3.22% |
| rain shaft density | 0.13 | 1.19% |

**The SIDE/FAR comparison is the striking part.** Every rendering consumer
shrinks with distance - light 33.8% -> 16.6%, primary 11.0% -> 7.1% - while rain
reachability *grows* to become 57% of all descriptor work. It is paid per coarse
step at any distance, so the further the camera, the larger its share of a
frame that renders less.

Tags 8 and 9 read exactly zero: the camera-inside test bails at the reach gate
before `directStormShape` counts, and the cheap forward probe sits inside a path
`PA_PRECIPITATION_ABSENT` removes.

## 4. Task 6 (items 18-20) - semantics and execution shape

**Identity:** `rainSegmentMayContribute`, called from `main` as
`localRainSegment = PuffDensityStage == 0 && rainSegmentMayContribute(p, segmentEnd)`.
It samples two points along the coarse segment and asks, for each, whether rain
could attach there - via `localRainSupportAt`, which walks every descriptor in
`directStormLocalBaseAt` and then runs a full candidate/group union in
`directStormShape`.

**Semantics: occupancy.** The function returns a `bool`. It needs "could rain
contribute in this segment", never a density. Its own gate is
`MaxPrecipitation <= 0.02` - a **runtime uniform**, not the compile-time
`PA_PRECIPITATION_ABSENT` that FINAL bakes.

> **CORRECTION (T183).** The conclusion drawn here - that the test "runs at full
> cost for a feature that cannot contribute to the frame" - was wrong. Rain does
> contribute: the march calls `rainShaftDensityOverSegment` directly, gated on
> the very flag this test computes. The runtime gate is correct as written.

**Execution shape: uniform and compile-time removable.** It is a fixed
two-sample loop on every coarse step, not data-dependent per lane in the way
T179's pruning was. That is exactly the shape T179 and T180 showed pays.

## 5. Task 7 (items 21-22) - the uniform removal ceiling

| Pose | Arm | blocks | ratioMean | spread | verdict |
|---|---|---|---|---|---|
| FAR | **`t182_norainseg`** | 3 | **1.2139** | 2.64% | **accepted** |
| SIDE | `t182_norainseg` | 0 accepted | 1.198 (see below) | - | REJECTED_anchor_drift |

**SIDE is not measurable this session.** The anchor swung 20.34-22.01 and drifted
4.3-7.2% in eleven of twelve blocks, so nearly every SIDE arm was rejected before
its ratio was considered. That is the acceptance rule working, and I am not
banking a SIDE number.

Worth recording without banking: `norainseg`'s three SIDE local ratios are
**1.1848, 1.2074, 1.1988** - agreeing to 1.9% with each other while each was
rejected because *the anchor* moved, not the arm. They centre on 1.198 and match
FAR's accepted 1.2139. Corroborating, not accepted.

**Image damage is near zero:** SIDE meanAbs 5.322e-05 with 369 changed pixels,
FAR 1.046e-05 with 105.

> **CORRECTION (T183).** The sentence that stood here - that "the rain it gates
> cannot render in this build" - was wrong. `PA_PRECIPITATION_ABSENT` removes
> precipitation from `cloudDensity`'s internal term only; rain still renders
> through `rainShaftDensityOverSegment`, which the march calls directly and
> gates on `localRainSegment`. Those changed pixels are rain being deleted, not
> a perturbed step pattern. `t182_norainseg` is therefore not a ceiling on dead
> work - it removes a shipped feature, and its 1.2139x is not available without
> losing rain. See `performance-rain-reachability.md`.

**At >=1.20x this clears the brief's major-architecture-target bar.**

## 6. Tasks 8 and 9 (items 23-28) - cap 4 on the stack

| Pose | Comparison | blocks | ratioMean | spread | verdict |
|---|---|---|---|---|---|
| FAR | `stack_probe4` vs control | 3 | **0.9900** | 0.46% | **accepted** |
| FAR | `stack_probe4_norainseg` vs control | 3 | **1.2915** | 2.68% | **accepted** |
| SIDE | `stack_probe4` vs control | 3 | 1.0356 | 25.33% | REJECTED_ratio_spread |
| SIDE | `stack_probe4_norainseg` vs control | 3 | 1.4076 | 14.11% | REJECTED_ratio_spread |

Session-local FAR p50: control 4.967, `stack_probe4` 5.018,
**`stack_probe4_norainseg` 3.847**.

**Cap 4 verdict: DO NOT PRODUCTIONIZE.** Its stack-level gain is **0.9900x**,
accepted at 0.46% spread - a null result, not an inconclusive one. It fails Task
9's ">=3% stack-level gain" test outright. T181's standalone 1.0850x SIDE /
1.1056x FAR did not survive composition, which is precisely why the brief
required this test.

The quality case for cap 4 was never the problem - T181 measured cloud SSIM
0.9969 - but quality is irrelevant to a change that buys nothing.

## 7. Decision (items 29-32)

**29. Dominant remaining workload: rain-segment reachability**, 36.26% of shape
calls at SIDE and 56.67% at FAR, running every coarse step for a feature this
build compiles out.

**30. Largest trustworthy uniform ceiling: 1.2139x at FAR, accepted.** SIDE
corroborates at ~1.198 across three internally-consistent blocks but is rejected
on anchor drift and is not banked.

**31. Recommended T183.** Gate `rainSegmentMayContribute` on the **same
compile-time condition that already removes precipitation**. When
`PA_PRECIPITATION_ABSENT` is defined, rain cannot contribute to the frame at all,
so the reachability test is pure control work for an impossible outcome. That is
uniform, compile-time, and semantically exact for the build that ships it -
unlike `t182_norainseg`, which removes the test unconditionally and would break a
precipitation build.

The measurement to make is whether the compile-time-gated version reproduces the
1.21x, and what the residual 369-pixel step-pattern difference does to the
quality metrics. Also worth asking: whether the runtime
`MaxPrecipitation <= 0.02` gate can be hoisted out of the per-step call entirely,
which would help builds that *do* render rain.

**32. Cap 4: no.** 0.9900x on the stack.

## 8. Harness notes (items 33-36)

- **New: `validateConsumerTagsAreCounted`.** Fails the build if a tag is assigned
  with no counter reading it, if nothing reads tag 0, or if `paShapeUntagged` is
  removed. This is the structural answer to two campaigns of attribution by
  elimination. `T182_CONSUMER_TAGS assigned=9|counted=9|untaggedCounterPresent=true`.
- `T175_WORKLOAD_VIEWS declared=32|allEnabled=true` covers views 52-54.
- `T170_WIRING campaigns=27|violations=0`, negative proof detects all five
  mutations.
- The `shapeAccountingClosed` flag's tolerance is half a call, which is stricter
  than cross-frame capture variance permits; it prints `false` on a 0.058% gap.
  The flag should compare against a fraction, not an absolute - noting it rather
  than quietly loosening it after seeing the result.

## 9. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`, T175 `3cfd1f1`, T176 `b047bc3`,
T177 `f868325`, T178 `cb1197b`, T179 cleanup `42e655d`, T179 `655bec1`,
T180 `738b5d7`, T181 `624d7f0`.
