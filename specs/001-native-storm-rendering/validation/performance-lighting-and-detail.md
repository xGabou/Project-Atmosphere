# T169 - Lighting and detail cost reduction

Branch `experiment/cloud-descriptor-k`, parent `11f9ee4`. Nothing merged.
Campaign run 05Sep2026 09:26:07-09:31:49, Ultra, raw scale 0.2500, cloud target
480x270, framebuffer 1920x1080, 96 steps, 60 samples per cell, 18 arms x 2 poses
= 36 cells. `T169_REJECTED count=0`. History disabled across the matrix.

## 0. Harness verification (required before any timing)

Drift controls (anchor measured twice per pose):

| Pose | first anchor P50 | repeat anchor P50 | ratio | verdict |
|---|---|---|---|---|
| FAR | 14.4108 | 14.3237 | 0.9940 | stable |
| SIDE | 22.6079 | 22.4164 | 0.9915 | stable |

Both poses are banked. The `resolutionScale=NaN` header artifact noted in T166,
T167 and T168 is still present and still cosmetic; every cell reports
`resolutionScale=0.2500`.

### 0.1 Two driver wiring gaps had to be fixed before the campaign could run

The first three launch attempts produced no T169 numbers. The cause was not
memory, and not the shader:

1. `performanceRunRequested()` enumerated every campaign marker *except*
   `lightingDetailRunRequested()`. With only `t169-lighting-detail.txt` present
   the predicate was false, so `RAYTRACE_FIXTURE` routed to `BEGIN_T098` and the
   driver ran the default suite plus t098 captures, finishing
   `outcome=complete`. The T169 matrix was unreachable.
2. `activeEvaluationArms()` had no `t169Run` branch, so `T169_OPTIMIZATION_ARMS`
   was declared and never referenced. A T169 run would have driven the default
   `T141_ARMS`.

This is the third instance of the pattern T168 recorded ("T167 lost the arrival
guard because `t166PoseTargetValid = t166Run` was never extended to the new
campaign"). A new campaign gets wired into most sites and missed at one or two.
**A sandbox invariant asserting that every `*RunRequested()` marker predicate
appears in `performanceRunRequested()` would close the class. It is not built.**

### 0.2 GC did not contaminate the banked metric

The dev client was run with `-Xms1G -Xmx4G` (stock ergonomics on this 32 GB host
are Xms 508 MB / Xmx 7.93 GB) and `-Xlog:gc*`. Inside the 342 s timing window:

- **0 Full GCs**; 254 young pauses; max 75.332 ms; total 2422.8 ms = 0.708% of wall.
- Peak committed heap 3914 MB against the 4096 cap; live set after collection
  1.0-1.3 GB.

`cloudP50`/`cloudP95` come from GPU timer queries and are unaffected by a CPU
pause, so they are banked. **`frameP95` is not banked**: 12 pauses above 20 ms
across 36 cells can each spike one frame of 60, which is enough to move a P95.
Frame numbers below are advisory only.

One further caution: `cloudP95` repeats exactly across unrelated arms in two
places (FAR `t166_lightsteps2` and `t166_nolight` both 15.2279; SIDE
`detailfp_conservative` and `detailfp_balanced` both 24.5156). Treat P95 as
coarsely quantized and do not read fine distinctions from it.

### 0.3 Repeatability, and what counts as significant

Five arms in the matrix turned out to be anchor repeats (section 5), which
yields seven anchor-equivalent cells per pose and an unusually good noise
estimate:

| Pose | n | mean P50 | sd | sd as % | 2 sigma band |
|---|---|---|---|---|---|
| FAR | 7 | 14.3274 | 0.1372 | 0.96% | +/-0.274 ms |
| SIDE | 7 | 22.4685 | 0.2017 | 0.90% | +/-0.403 ms |

**Any delta smaller than ~1.9% of the anchor is not a measurement.** That
threshold is applied throughout.

## 1. Headline

**The lighting arms fail, the detail arm works only at FAR, and the two stacks
succeed - but SIDE still misses both budgets, and the ceiling measurement proves
lighting and detail together cannot close it.**

The conservative tap reductions this task was built around (6 -> 5, 6 -> 4) buy
nothing at either pose. At SIDE they are *bit-identical to the anchor*, which is
direct proof that the cone early-out already terminates before tap 5. The task's
premise - that tap count was the lever - is wrong.

## 2. Fresh FAR/SIDE anchors

| Pose | cloudP50 | cloudP95 | frameP50 | frameP95 |
|---|---|---|---|---|
| FAR | 14.4108 | 15.7665 | 14.6926 | 17.7848 |
| SIDE | 22.6079 | 25.1423 | 23.3975 | 25.7589 |

Against T168 (13.8783 FAR / 22.2331 SIDE) these are +3.8% and +1.7%. FAR is
outside the within-run 2 sigma band, so **cross-run absolute comparisons with
T168 are not safe**; only within-run ratios are used below.

## 3. Lighting attribution

Anchor-relative, `-` = faster. Significance against the 2 sigma band from 0.3.

| Arm | FAR P50 | delta ms | speedup | SIDE P50 | delta ms | speedup | sig |
|---|---|---|---|---|---|---|---|
| anchor (6 taps) | 14.4108 | - | 1.000 | 22.6079 | - | 1.000 | - |
| `t169_lightsteps5` | 14.5725 | +0.162 | 0.989 | 22.4297 | -0.178 | 1.008 | **no** |
| `t169_lightsteps4` | 14.3688 | -0.042 | 1.003 | 22.4932 | -0.115 | 1.005 | **no** |
| `t169_lightearlyout2` | 13.9530 | -0.458 | 1.033 | 21.8542 | -0.754 | 1.035 | yes |
| `t166_lightsteps2` | 13.0662 | -1.345 | 1.103 | 19.9127 | -2.695 | 1.135 | yes |
| `t166_nolight` (ceiling) | 11.9910 | -2.420 | 1.202 | 18.1914 | -4.417 | 1.243 | yes |

**Lighting ceiling: 16.8% of cloud time at FAR, 19.5% at SIDE.**

The cost is not linear in tap count. Cutting 6 -> 5 (-17% of taps) and 6 -> 4
(-33%) return nothing; 6 -> 2 (-67%) returns 10-13%; removing lighting entirely
returns 17-20%. The image metrics explain why: at SIDE, `lightsteps5` and
`lightsteps4` are **bit-identical to the anchor** (`maxAbsRGBA=0.0`,
`changedPixelCountAboveEpsilon=0`, `passed=true`). The cones never reach taps 5
and 6, so capping there is a no-op. Raising the transmittance early-out floor
instead - which changes *when* cones stop rather than how many taps they may
use - is the only lighting arm that returns anything at both poses, and it
captures 19% (FAR) and 17% (SIDE) of the ceiling for a 3.3-3.5% gain.

**Conclusion: tap-count reduction is closed. The remaining lighting headroom is
in the early-out threshold, and it is worth ~3.5%, not the 17-20% ceiling.**

## 4. Detail attribution

`PA_ARM_DETAIL_FOOTPRINT` gates the detail octaves on projected footprint,
applying the T168 relation to the detail wavelength.

| Arm | FAR P50 | delta ms | speedup | SIDE P50 | delta ms | speedup | sig |
|---|---|---|---|---|---|---|---|
| anchor | 14.4108 | - | 1.000 | 22.6079 | - | 1.000 | - |
| `detailfp_conservative` (0.5) | 14.3432 | -0.068 | 1.005 | 22.9550 | +0.347 | 0.985 | **no** |
| `detailfp_balanced` (1.0) | 14.1527 | -0.258 | 1.018 | 22.4891 | -0.119 | 1.005 | borderline / **no** |
| `detailfp_aggressive` (1.5) | 12.2757 | -2.135 | 1.174 | 21.8788 | -0.729 | 1.033 | yes |
| `t169_nodetail` (ceiling) | 11.8907 | -2.520 | 1.212 | 19.1611 | -3.447 | 1.180 | yes |

**Detail ceiling: 17.5% of cloud time at FAR, 15.2% at SIDE.**

Share of the ceiling captured by the aggressive gate: **84.7% at FAR, 21.2% at
SIDE.** That asymmetry is the mechanism working correctly, not failing. SIDE is
close to the storm, so detail octaves genuinely project above the pixel
threshold and the gate declines to remove them - the SIDE image changes by only
3 pixels of 129600 at `balanced` and 633 at `aggressive`. The detail work at
SIDE is resolvable and therefore not removable by a footprint test.

### 4.1 Squared-distance detail cutoff

Implemented as T169 Task 4A. Per-fragment the shader derives two cutoffs from
the detail wavelengths (22.7 and 8.4 blocks):

```
baseCutoff = paDetailFootprintCoefficient(22.7) / PA_ARM_DETAIL_FOOTPRINT
fineCutoff = paDetailFootprintCoefficient(8.4)  / PA_ARM_DETAIL_FOOTPRINT
paDetailCutoffDistSq     = baseCutoff * baseCutoff
paDetailFineCutoffDistSq = fineCutoff * fineCutoff
```

and the march compares `distSq > paDetailCutoffDistSq` /
`> paDetailFineCutoffDistSq`. Squaring the cutoff once per fragment rather than
taking a square root per sample is what makes the gate affordable; the two-tier
form lets the fine octave drop out before the base octave. **The mechanism is
sound and costs nothing when it does not fire** (`conservative` at FAR is
-0.068 ms, i.e. free). Its value is entirely bounded by how much detail is
below screen scale, which is a pose property.

## 5. T149 arms - NOT MEASURED

The five runtime-graded T149 modes (`T149_LIGHT_CONTRIBUTION`,
`T149_LIGHT_DISTANCE`, `T149_LIGHT_GRADED`, `T149_DETAIL_GRADED`, `T149_GRADED`)
**produced no valid measurement, and could not have.**

They are defined as `new T166Arm(LEAN_FINAL, NaN, mode)` and driven through
`setOptimizationDiagnosticMode()`. But the lean specialization bakes the uniform
away - `leanFinalConstants` in `build.gradle:271` maps
`uniform int PaDiagnosticOptimizationMode;` to
`const int PaDiagnosticOptimizationMode = 0;`, and `lean_final` declares
`keepUniforms: []`. The generated program contains
`const int PaDiagnosticOptimizationMode = 0;` at line 234 and its JSON declares
no such uniform. The upload goes nowhere.

Two independent confirmations: `T166Arm.label()` omits `mode`, so all five
report as `arm=lean_final` and collide in the results map (the decision block
prints the anchor's row five times); and their measured P50s sit inside the
anchor's own noise band at both poses.

**The five cells are anchor repeats.** They were used as such in section 0.3 -
that is the only value recovered. **Measuring T149 requires either a variant
that keeps `PaDiagnosticOptimizationMode` live, or compile-time arms baking each
policy.** Neither was in this matrix, and per the campaign constraints the
matrix was not altered mid-run.

## 6. The stacks

`stack_safe` = footprint P=0.35, max 4.0, term 0.045, detailFP 1.0, light 5.
`stack_fast` = footprint P=0.75, max 4.0, term 0.045, detailFP 1.0, light 4.

| Arm | FAR P50 | FAR P95 | speedup | SIDE P50 | SIDE P95 | speedup |
|---|---|---|---|---|---|---|
| anchor | 14.4108 | 15.7665 | 1.000 | 22.6079 | 25.1423 | 1.000 |
| `t169_stack_safe` | **7.9217** | 8.5412 | **1.819** | **17.6794** | 18.7914 | **1.279** |
| `t169_stack_fast` | **5.3371** | 5.8532 | **2.700** | **10.7305** | 11.5999 | **2.107** |

### 6.1 Component attribution

Footprint and termination were measured in T168, so this composes across runs
and is approximate.

| Stack | Pose | product of parts | measured | ratio |
|---|---|---|---|---|
| safe | FAR | 1.459 | 1.819 | 1.25 (superadditive) |
| safe | SIDE | 1.311 | 1.279 | 0.98 (additive) |
| fast | FAR | 1.670 | 2.700 | 1.62 (superadditive) |
| fast | SIDE | 2.069 | 2.107 | 1.02 (additive) |

**At SIDE the components compose almost exactly multiplicatively.** At FAR they
appear to compound strongly, but T168's FAR footprint arms were non-monotonic in
P (P=0.50 measured 2.07x, faster than both P=0.35 at 1.30x and P=0.75 at 1.47x,
with cloudP95 9.90 against cloudP50 6.70). **The FAR superadditivity is more
likely an artifact of that instability than a real interaction, and should not
be banked.** The footprint step LOD is the dominant term in both stacks either
way; the T169 additions (detailFP 1.0, light 5/4) contribute 0.5-1.8% between
them.

## 7. Quality metrics

Reference image comparison against `lean_final`, 129600 compared pixels,
`epsilon=4.882813e-04`, `epsilonBasis=rgba16f_storage_ulp`.

| Pose | Arm | passed | maxAbs | meanAbs | rms | changed px |
|---|---|---|---|---|---|---|
| FAR | `lightsteps5` | false | 1.22e-2 | 1.31e-4 | 1.02e-3 | 4111 (3.2%) |
| FAR | `lightsteps4` | false | 1.81e-2 | 1.99e-4 | 1.54e-3 | 4125 (3.2%) |
| FAR | `lightsteps2` | false | 3.97e-1 | 1.82e-3 | 1.69e-2 | 4127 (3.2%) |
| FAR | `detailfp_balanced` | false | 4.67e-1 | 2.34e-4 | 2.70e-3 | 4131 (3.2%) |
| FAR | `detailfp_aggressive` | false | 6.69e-1 | 5.56e-4 | 6.50e-3 | 4155 (3.2%) |
| FAR | `stack_safe` | false | 9.56e-1 | 3.68e-4 | 4.21e-3 | 4162 (3.2%) |
| FAR | `stack_fast` | false | 7.35e-1 | 4.67e-4 | 5.47e-3 | 4163 (3.2%) |
| SIDE | `lightsteps5` | **true** | 0.0 | 0.0 | 0.0 | **0** |
| SIDE | `lightsteps4` | **true** | 0.0 | 0.0 | 0.0 | **0** |
| SIDE | `lightsteps2` | false | 4.21e-1 | 1.06e-2 | 4.11e-2 | 14995 (11.6%) |
| SIDE | `detailfp_balanced` | false | 4.54e-1 | 2.93e-6 | 8.74e-4 | **3** (0.002%) |
| SIDE | `detailfp_aggressive` | false | 9.89e-1 | 1.03e-4 | 4.50e-3 | 633 (0.5%) |
| SIDE | `stack_safe` | false | 9.89e-1 | 2.29e-3 | 7.50e-3 | 19257 (14.9%) |
| SIDE | `stack_fast` | false | 9.89e-1 | 2.28e-3 | 9.66e-3 | 20765 (16.0%) |

The ~4100 changed pixels at FAR is the storm's whole on-screen footprint - at
that distance essentially every cloud pixel moves slightly for any arm, so
`changed px` does not discriminate at FAR and `meanAbs`/`rms` should be read
instead.

**The stacks are not visually free at SIDE**: 15-16% of pixels change with
meanAbs 2.3e-3 and rms 7.5-9.7e-3, an order of magnitude above their own detail
and lighting components (`detailfp_balanced` 2.9e-6, `lightsteps5` 0.0). The
error is contributed by the footprint step LOD and the 0.045 termination floor,
not by anything T169 added. `lightsteps2`, the only lighting arm with a real
gain short of the ceiling, is the single worst arm on meanAbs at SIDE (1.06e-2)
and flattens self-shadowing - the same failure T166 recorded.

## 8. Budget status

Targets: <=10 ms and <=8 ms cloud time.

| Pose | Arm | P50 | P95 | <=10 ms | <=8 ms |
|---|---|---|---|---|---|
| FAR | anchor | 14.4108 | 15.7665 | no | no |
| FAR | `stack_safe` | 7.9217 | 8.5412 | **yes** | **P50 yes**, P95 8.54 (6.3% over) |
| FAR | `stack_fast` | 5.3371 | 5.8532 | **yes** | **yes (P50 and P95)** |
| SIDE | anchor | 22.6079 | 25.1423 | no | no |
| SIDE | `stack_safe` | 17.6794 | 18.7914 | no | no |
| SIDE | `stack_fast` | 10.7305 | 11.5999 | **no** (7.3% over) | no (34% over) |

**FAR is solved.** `stack_fast` clears both budgets on P50 and P95 with margin.

**SIDE is not, and this campaign proves lighting and detail cannot solve it.**
Removing *all* lighting saves 4.417 ms and removing *all* detail saves 3.447 ms;
if those composed perfectly the residual would still be 14.74 ms - 47% above the
10 ms budget and 84% above 8 ms. Lighting plus detail is 34.8% of SIDE cloud
cost. The other 65.2% is the march itself.

## 9. Next bottleneck

**The primary march's per-sample descriptor work at close range.**

Section 8 bounds it arithmetically: with lighting and detail both at zero, SIDE
still costs ~14.7 ms. `stack_fast` reaches 10.73 ms only because the footprint
step LOD removes primary samples outright - which is the mechanism that is
actually working, and the one with headroom left.

The run's own workload counters put SIDE at 9.66 light evaluations and 24.61
detail octave evaluations per pixel against FAR's 1.73 and 4.69 - a 5.6x and
5.2x ratio that tracks the timing almost exactly, confirming the pose gap is
workload, not a fixed overhead. T168 already closed the static route to reducing
it: the group walk visits exactly 10.000 lobes per group entered, groups are
entered whole, and the non-contributing majority is sample-dependent, so no
static binning removes it.

That leaves two candidates, in order of expected return:

1. **Push the footprint step LOD further at close range.** It is the only
   mechanism in the line that has scaled with the SIDE workload (P=0.75 alone
   gave 1.84x at SIDE against 1.47x at FAR). `PA_ARM_FOOTPRINT_MAX 4.0` is
   currently the clamp; whether it binds at SIDE was not measured.
2. **Reduce descriptor texture fetches per primary sample** - a per-sample
   concern rather than a per-group one, so it is not the question T168 closed.

Neither is a lighting or detail question, so both are out of T169's scope.

## 10. Status

Not merged, not committed. Working tree carries the T169 shader arms, the two
driver wiring fixes from 0.1, and the opt-in benchmark heap flags.
