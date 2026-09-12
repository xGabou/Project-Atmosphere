# T204 - the shaft path costs what its four inlined walks cost: 1.7-1.8 ms of execution in 3% of warps, and 1.0 ms of code presence

Attribution only, no rain behaviour or visual change. One launch on
12Sep2026, `t204-campaign/t204-run1.log` (18:41-18:46, 4,119 frames). SIDE
and PLAY_VIS_NEAR at 0.25 (480x270), Ultra 96 steps, history off, one
deterministic fixture a pose, every arm bracketed by fresh FINAL anchors
(15 a pose), three repeats of the two decisive arms, one of each other, 60
samples a cell, anchor drift <= 3% or the block is dropped (one block
dropped: `t204_skeleton_shaft_out` at PLAY_VIS_NEAR). Every arm is a bound,
image-invalid by construction; no image captures.

## BASELINE (Task 1)

1. **Starting HEAD**: `360ac71` (T203). Working tree carried the T195-T203
   logs, preserved.
2. **SIDE control** (15 anchors): ray **12.48 ms** p50 / 12.89 p95, field
   1.66 / 1.87, combined **14.14** (14.05-14.26), spread 1.7%.
3. **PLAY_VIS_NEAR control**: ray **12.19** / 12.80, field 2.01 / 2.04,
   combined **14.20** (14.01-14.42), spread 3.6%.

## SHAFT CEILING (Task 2) - `t201_norain_shaft`, the shaft compiled out

| pose | blocks | control ray | arm ray | 4. delta ms | net | 5. ratio | 6. spread |
|---|---|---|---|---|---|---|---|
| SIDE | 3 | 12.45 | 9.73 | **2.72** | 2.88 | 1.280 | 4.3% |
| PLAY_VIS_NEAR | 3 | 12.20 | 9.35 | **2.85** | 3.24 | 1.305 | - |

Shaft calls removed: 77,784 / 44,670 samples a frame (`rainShaftDensityAt`),
152,501 / 87,683 walks (`shapeRainShaft`), from 38,919 / 22,343 true segment
tests. T201 priced the same arm at 2.49 / 2.46 on the bits-11 FINAL; the
program binary shrinks from 302,615 to 245,641 bytes - **the shaft is 57 KB,
19% of FINAL's machine code.**

## CODE PRESENCE (Task 3) - the shaft compiled in, gated off by a live uniform

`t204_shaft_runtime_false`: the call is conjoined with
`PaDiagnosticEvalEpsilon < -0.5`, a uniform the arm keeps live and the
renderer uploads as 0.0, so the compiler keeps the whole path (binary
302,896 bytes, FINAL's size) and no lane ever enters it.

| pose | 7. runtime-false delta | 4. compiled-out delta | 8. presence = out - runtime-false | execution = runtime-false |
|---|---|---|---|---|
| SIDE | 1.65 (3 blocks, ratio 1.153, spread 3.3%) | 2.72 | **1.07 ms** | **1.65 ms** |
| PLAY_VIS_NEAR | 1.84 (3 blocks, ratio 1.177) | 2.85 | **1.01 ms** | **1.84 ms** |

9. **Neither pattern the brief named: both mechanisms are real and
execution is the larger.** Compiled-out (2.7-2.9) is well below
runtime-false (1.7-1.8), which is well below production (0): the code's
presence costs ~1.0 ms and its execution ~1.7 ms, 38% / 62% of the shaft.

## DIVERGENCE (Task 4) - view 78, censused in warp-shaped tiles on the CPU

`#version 150` has no subgroup operations; the per-pixel count of true
segment tests is read back as an image and tiled at 8x4, 4x8 and 16x16.

| | SIDE | PLAY_VIS_NEAR |
|---|---|---|
| true segment tests (the shaft's gate) | 38,892 = 0.90% of 4.35M calls | 22,335 = 1.04% of 2.15M |
| shaft samples / positive segments | 77,784 / 8,060 (21% of true) | 44,670 / 7,170 (32%) |
| 10. **shaft-positive pixels** | **2,269 = 1.75%** | **833 = 0.64%** |
| true segments per positive pixel (mean / max) | 17 / 63 | 27 / 91 |
| shaft walks per positive pixel | 67 | 105 |
| 12. 8x4 tiles with any positive lane | **122 of 4,080 = 3.0%** | 72 = 1.8% |
| 8x4 tiles with every lane positive | 27 = 0.66% | 0 |
| 4x8 any / all | 117 / 32 | 91 / 0 |
| 16x16 tiles with any positive | 25 of 510 = 4.9% | 16 = 3.1% |
| positive lanes per active 8x4 tile | 18.6 of 32 (58%) | 11.6 of 32 (36%) |

11. **Clustered, not scattered**: the positives sit in 3% of warp-shaped
tiles and fill 36-58% of each. 13. **Divergence is not the mechanism.**
The shaft runs in 3% of warps, and inside them it is reasonably coherent.
What those warps do is the mechanism: each active lane runs 17-27 true
segments, four full column walks each - 67-105 walks per pixel, ten times
the primary's 7 - and the walks are the expensive kind (a column under the
storm evaluates every lobe exactly with no early reject). 1.65 ms over
122 active warps x 67 walks is ~200 ns a warp-walk, the cost of a full
storm walk plus the noise fetch; idle lanes inside those warps waste
42-64% of it, which is divergence's whole contribution.

## DECOMPOSITION (Task 5) - one block each, SIDE / PLAY_VIS_NEAR delta ms

| chunk | arm | delta | reads as |
|---|---|---|---|
| 14. predicate only (body present, never entered) | `t204_shaft_runtime_false` | 1.65 / 1.84 below FINAL | the predicate is free; this delta is the executed body |
| 17. **descriptor walks** (the two `localRainSupportAt` calls replaced by constants, the rest executed) | `t204_shaft_no_walk` | **2.64 / 2.57** | **the walks are the shaft: 97% / 90% of the compiled-out ceiling** |
| 15. setup math + 16. shaft body minus walks (walks kept, everything after dropped) | `t204_shaft_walk_only` | -0.07 / -0.13 | zero |
| noise fetch (the 3D texture replaced by a constant) | `t204_shaft_no_noise` | -0.06 / +0.10 | zero |
| 18. accumulation / composition | (folded with the body: a multiply-add on `density`) | - | zero by elimination |
| 19. test skeleton (below) | | | |

Binary sizes agree: the walks are 42 KB of the shaft's 57 KB (`no_walk`
260,436 bytes); the post-walk math 6 KB (`walk_only` 296,842); the noise
fetch 0 (`no_noise` 302,647).

## TEST SKELETON (Task 8) - on the T203 gate program, every sample rejected before the fetch, SIDE / PLAY_VIS_NEAR ray ms

| arm | ray | above the family-out ceiling (5.12 / 4.04) | reads as |
|---|---|---|---|
| `t182_norainseg` - the family absent | 5.12 / 4.04 | 0 | ceiling: family = **7.39 / 8.02** |
| `t204_skeleton_shaft_out` - reject-all, shaft compiled out | 6.09 / (dropped) | **+0.96** | the repeated call and its loop, nothing else (binary +9 KB over t182) |
| `t204_skeleton_runtime_false` - reject-all, shaft compiled, never entered | 6.58 / 5.13 | +1.46 / +1.09 | + shaft presence 0.5 |
| `t203_gate_reject_all` - reject-all, shaft compiled and callable | 6.64 / 5.15 | +1.52 / +1.11 | same (reject-all never yields true) |
| `t203_gate_no_fallback` - + the gate fetch and decisions | 7.10 / 5.62 | +1.98 / +1.58 | + **fetch 0.46 / 0.47** |

19. The skeleton - `rainSegmentMayContribute` called on every coarse step
with both samples rejected at the top of the loop - costs **~1.0 ms** on
its own (one block; `MaxPrecipitation` is a uniform, so the call does not
fold to a constant the way T182's literal `false` does), the shaft's
presence adds 0.5 on top of it, and the field read 0.5. Loop/control is
the larger half of the skeleton; the fetch is the smaller.

## CODEGEN (Tasks 6-7)

20. **Generated source.** GLSL inlines every call. In FINAL's generated
source `rainShaftDensityOverSegment` is called once from the march and
calls `rainShaftDensityAt` twice, each calling `localRainSupportAt` twice
(the column and the wind-offset source column): **four inlined copies of
the full column walk** - `directStormRainSupportAt` = the BASE walk over
every lobe + `directStormFinalDensity` = candidate scan, group field, lobe
SDFs, smooth union, base and detail noise - inside the march loop, beside
the segment test's own copies (its two-sample loop) and the primary's.
21. **Instruction/register evidence.** No vendor tooling; the one
characteristic the driver exposes is the linked binary's length
(`GL_PROGRAM_BINARY_LENGTH`, logged at link as `T204_PROGRAM_BINARY`):

| program | bytes | delta vs FINAL |
|---|---|---|
| diagnostic monolith | 506,048 | +203 KB |
| **FINAL** | **302,615** | |
| shaft runtime-false | 302,896 | +0.3 KB (the presence arm is FINAL's code) |
| shaft no-noise / walk-only | 302,647 / 296,842 | 0 / -6 KB |
| **shaft no-walk** | **260,436** | **-42 KB** |
| **shaft compiled out** | **245,641** | **-57 KB** |
| skeleton + shaft runtime-false / callable | 288,574 / 288,293 | -14 KB |
| skeleton + shaft out | 235,022 | -68 KB |
| **family out (T182)** | **225,940** | **-77 KB: the rain family is 25% of FINAL** |
| the gate build program alone | 141,959 | |

Register counts and spills are not observable here; the claim this
supports is code size, not pressure: the shaft is 57 KB of a 303 KB
program and its presence costs 1.0 ms at both poses.
22. **Duplication.** The four walk copies are the duplication: 42 of the
shaft's 57 KB. Two of them are exact repeats of work the segment test
already did on the same frame - the shaft's first sample of a true segment
is the Gauss point the test returned true on, so its preliminary walk
recomputes that column - and all four are column-invariant values
(T184) a smaller form could receive instead of recompute. A separate pass
would remove all 57 KB and the 1.0 ms with it.

## DECISION (Task 9)

23. **Dominant mechanism: the shaft's descriptor walks - their execution
(1.65 / 1.84 ms, in 3% of warps at 36-58% lane occupancy, 67-105 walks a
positive pixel) and their inlined presence (1.0 ms, 42 of 57 KB).**
Divergence contributes only the idle lanes inside those warps; the noise,
the setup math and the composition contribute nothing measurable. The
skeleton is a second, smaller mechanism: ~1.0 ms of repeated call/loop
structure plus 0.5 of field read.
24. **Removable ms** (SIDE / PLAY_VIS_NEAR, no visual change, by
architecture): shaft walks **2.6 / 2.6** (the no-walk bound: 2.64 / 2.57
of the 2.72 / 2.85 ceiling); with them the shaft's presence. The rain
family's whole ceiling is 7.4 / 8.0: T203's gate takes 1.2-2.4 of the
segment-test walks, this takes ~2.6, the skeleton and fetch (1.5) go only
if the cloud program stops asking the question on every step.
25. **Selected next architecture: CASE A/B - a separate rain pass, with the
walks removed from the cloud program.** Concretely, in order of what the
evidence supports:
    1. The cloud program stops carrying the shaft: the shaft's inlined
       copies (57 KB, 1.0 ms presence) and their execution (1.7-1.8) leave
       it. The rain interval and the column values the segment test
       already computed at the true Gauss point (support, attach,
       precipitation, family, weather, morphology - all column-invariant)
       are what the shaft needs at its first sample; the source column's
       values are the one new walk a segment.
    2. A rain-only pass marches only the pixels the cloud pass marked
       (1.75% / 0.64%), compacted by the early exit, in a program a
       fraction of FINAL's size. Its execution is the same 67-105 walks a
       positive pixel with no idle-lane cost across 97% of the screen and
       far more occupancy: expected 0.5-1.0 ms.
    3. Compositing is the design risk: rain is added to the march's density
       today, so it is attenuated by and attenuates the cloud in the
       ordered integral; the pass needs the cloud's transmittance at the
       rain interval - the value to export per pixel - and the rain-mask
       harness (IoU, false/missed, thin, continuity, attach/termination)
       plus the frame comparison decide whether a two-layer composite is
       exact enough. That is T205's quality question, not a settled fact.
26. **Expected realistic gain: 2.0-2.6 ms** from the shaft (its 2.7-2.9
ceiling less the rain pass's own cost), **3.5-5.0 ms with T203's gate
productionized** in the same pass design (the pass can own the segment
test too, which also removes the 1.0-1.5 ms skeleton from the cloud
program - the only way that piece leaves).
27. **Temporal likely after the next architecture: NO on the shaft alone
(14.1 - 2.3 = ~11.8 ms raw combined), BORDERLINE-NO with the whole rain
family moved out (14.1 - 5 = ~9 ms, above the 8 ms floor).** The family
out entirely is 5.1 / 4.0 ray + 1.7-2.0 field = 6.8-7.1 ms - the temporal
class - so the question T205 must answer is what a rain pass costs on top
of that.

## VALIDATION

28. `campaignGate` PASS x3 (each edit round, the T204 invariant included).
29. `check build` PASS (50 invariants incl. the new T204 one, jar). 30. Consumer accounting: shape-call tags
close in both captures (`shapeAccountingClosed=true`); the shaft's walks
are the `shapeRainShaft` tag, 152,501 / 87,683. 31. Workload views: one
new (78, stage 55, `STORM_WORKLOAD_RAIN_SHAFT_LANES`), the capture
predicate extended to 78, the capture censuses its image on the CPU
(`ShaftLanes`). 32. Campaign registry: `T204` (`t204-shaft-attribution.txt`,
`shaftAttributionRunRequested`, `t204Run`, `T204_OPTIMIZATION_ARMS`,
`T204_SUPPORT_PROGRAMS`). 33. Variant scope: six T204 programs
(`t204_shaft_runtime_false`, `t204_shaft_no_walk`, `t204_shaft_no_noise`,
`t204_shaft_walk_only`, `t204_skeleton_shaft_out`,
`t204_skeleton_runtime_false`), campaign-scoped; FINAL defines none of the
four shaft arms (sandbox-asserted); the runtime-false arms keep the live
uniform (asserted). Program binary sizes are now logged at link for every
registered program (`T204_PROGRAM_BINARY`). 34. Marker retired to `.done`.
35. git status clean of task files (logs preserved). 36. Commit: the T204
commit on `experiment/cloud-descriptor-k` (parent `360ac71`), not merged.
