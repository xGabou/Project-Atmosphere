# T205 - rain as a separate pass: the cloud program drops to the family-out class, the frame stays exact-class, and the raw cost clears the temporal gate

Architecture campaign: the rain family moved out of the cloud program into
a rain-only pass. Three launches on 12Sep2026: `t205-campaign/t205-run1.log`
(22:04-22:08, 3,034 frames), `t205-run2.log` (22:09-22:13, the split mask
under the reference's own termination rule) and `t205-run3.log`
(22:16-22:21, a fine-stride rain pass variant beside the coarse one). SIDE and PLAY_VIS_NEAR at
0.25 (480x270), Ultra 96 steps, history off, one deterministic fixture a
pose, every timed arm bracketed by fresh FINAL anchors (6 a pose), three
repeats of the split, one of each ceiling, 60 samples a cell, anchor drift
<= 3% or the block is dropped (one PLAY_VIS_NEAR split block dropped in
run 1). FINAL is untouched: the split is a pair of campaign programs.

No rain-field, primary, probe, light, temporal or visual change. The rain
pass runs the production shaft, the production segment test and T203's
gate (exact, campaign-scoped) on the production ray setup.

## PIPELINE (Tasks 0-2)

1. **Starting HEAD**: `792bea9` (T204). Working tree carried the T195-T204
   logs, preserved.
2. **Current rain integration order.** Inside the march, on every coarse
   step: `localRainSegment = rainSegmentMayContribute(p, segmentEnd)`
   (two Gauss-point column tests, T203's 2.3M walks), then
   `rainDensity = rainShaftDensityOverSegment(...) * DensityMul` when true
   (four walks, T204's 2.7 ms), then `density = bodyDensity + rainDensity`,
   `stepTrans = exp(-density * ExtinctionScale * stepLength)`,
   `radiance = sampleLighting(p, density, h01, ..., rainFraction, ...)`,
   `accumulated += transmittance * radiance * (1 - stepTrans)`,
   `transmittance *= stepTrans`. A pure precipitation sample (rain above
   0.0008, body under it) keeps the coarse stride (`sinceHit = 100`), does
   not resolve the first material, and takes `rainFraction =
   0.25 + 0.75 * max(morphology.a, MaxPrecipitation)`. `localRainSegment`
   also vetoes the empty-space skip. Depth is the alpha-weighted mean t,
   rain included.
3. **Values rain needs.** From the march: nothing but the ray (origin,
   direction, t0/t1 after the slab, padding and scene clip, the coverage
   pretest, the strides, the search jitter) and, at each sample, the ray's
   transmittance so far. Everything else - the column values, the shaft,
   the light cone (`sampleLighting` reads the storm field and the noise,
   not the march), the raster base/top for the ambient height, the storm
   and darkness scalars - is a function of the sample point.
4. **What the cloud pass exports: nothing.** The dependency runs the other
   way. The rain pass runs first, on the cloud march's own setup, and
   exports per pixel `rgb` = its rain radiance integrated against the
   rain's own transmittance, and one float packing two 12-bit values -
   the rain's total transmittance `T_r` and its mass-weighted depth
   `t_R / MaxRenderDistance` (exact in a 24-bit mantissa, RGBA32F). The
   cloud pass reads its own texel once.
5. **Compositing equation.** Extinction is linear in density, so the
   step transmittance factors: `exp(-(b + r) s) = exp(-b s) * exp(-r s)`;
   the combined transmittance is the product of the body's and the rain's
   along the ray, and the ordering matters only at the rain's depth. The
   split cloud march therefore composites at the first step whose end
   passes `t_R`: `accumulated += R * T`, `weightedT += t_R * T (1 - T_r)`,
   `T *= T_r`; a march that ends before `t_R` (opaque cloud, the step
   budget, the ray's end) composites after the loop with what transmittance
   it has left. Exact wherever rain and body do not share a step - rain is
   below the base, the body above - and second-order in the mixed samples
   at the attach zone; the frame comparison prices that residual below.

## QUALITY (Task 5) - split frame vs FINAL, split mask vs `t188_rain_mask_ref`

The coarse rain pass (the candidate), three runs; the fine-band variant of
run 3 in the last column pair.

| | SIDE (runs 1 / 2 / 3) | PLAY_VIS_NEAR (1 / 2 / 3) | fine band, run 3 (SIDE / PVN) |
|---|---|---|---|
| 7. changed pixels (RGBA16F ulp) | 1,054 / 824 / **523 (0.4%)** | 300 / 270 / **179 (0.14%)** | 454 / 104 |
| 8. RMS | 5.6e-4 / 5.2e-4 / **3.0e-4** | 1.4e-3 / 1.2e-3 / **6.3e-4** | 2.1e-4 / 9.6e-5 |
| 9. maxAbs | 0.188 / 0.073 / 0.043 | 0.318 / 0.326 / 0.180 | 0.037 / 0.023 |
| mean abs | 2.3e-5 / 2.2e-5 / 1.1e-5 | 1.9e-5 / 1.8e-5 / 7.3e-6 | 8.0e-6 / 1.7e-6 |
| 10. **cloud SSIM / edge SSIM** (run 3) | **0.99998 / 0.99998** | **0.99999 / 0.99998** | 0.99999 / 0.999999 |
| silhouette IoU / thin / hole retention (run 3) | 0.9996 / 0.982 / 1.000 | 0.9994 / 1.000 / 0.9998 | 0.9995 / 0.979 / 0.996; 0.9998 / 1.0 / 1.0 |
| 6. **rain mask IoU** (runs 2 / 3, comparable) | **0.746 / 0.702** | **0.632 / 0.602** | 0.756 / 0.659 |
| false rain (of the reference's pixels) | 3.9% / 5.9% | 1.4% / 1.8% | 5.0% / 1.8% |
| **missed rain** | **22.5% / 25.7%** | **35.9% / 38.7%** | 20.6% / 32.9% |
| thin rain retention | 0.54 / 0.57 | 0.40 / 0.45 | 0.63 / 0.48 |
| 11. shaft continuity / new gap rays (run 3) | 0.985 / 11 | 0.788 / 85 | 0.959 / 33; 0.763 / 104 |
| 12. onset / termination error, mean blocks (runs 2 / 3) | 16.4 / 19.9 and 25.5 / 26.7 | 7.4 / 6.3 and 10.7 / 10.2 | 10.8 and 20.1; 6.3 and 9.6 |
| 13. occlusion | the composite runs inside the march at the live transmittance: rain behind cloud is attenuated, cloud behind rain is attenuated; the frame's 0.1-0.4% changed pixels at 3e-4 RMS are the sub-step ordering residual | | |

**The frame is exact-class; the mask is not, and the two disagree about
rain the frame cannot see.** The reference mask counts a ray as rain at an
optical mass of 0.0008 - the march's own threshold - and 22-39% of its
rain pixels are that thin: their mass is what the coarse rain pass loses
where the reference happens to sample finely (six fine steps after every
body sample, the band right under the base where rain attaches). Those
pixels change the frame by under an RGBA16F ulp - the T178 harness that
judged T202 puts the split at cloud SSIM 0.99998, thin retention 0.98-1.0,
hole 1.0, against the accepted refinement's 0.995 / 0.98 / 0.98. False
rain stays 1-6% and continuity 0.79-0.99 with 11-85 new gap rays a pose.

The fine-band variant (`t205_rain_pass_fine`: the fine stride from 48
blocks under the gate cell's highest attach height to 8 above it) recovers
part of the mask - IoU +0.05, thin +0.03-0.09, onset error halved at SIDE,
the frame to 1e-4 RMS - and costs 4.9 / 8.1 ms, because that band is the
whole storm footprint's base and every fine step asks the segment test.
A priced bound, not a candidate; a tile-masked pass (Task 10) is what
would make a fine band affordable.

Run 1's mask was not comparable (IoU 0.36 / 0.78, 1,762 / 114 "false"
pixels): the reference march terminates at transmittance 0.015 and never
integrates the rain behind it, while the rain pass integrates the whole
ray and the split mask scaled that invisible mass by a ~0.01 transmittance
- above the mask's 0.0008 presence threshold, below anything the frame
shows (the frame changed 0.8% of pixels at 2e-5 mean). Run 2 applies the
reference's own rule to the split mask (mass counts only where the
composite's transmittance is at least 0.015).

The frame is exact-class by the harness that judged T202: cloud SSIM
0.99995 against the refinement's 0.995, thin retention 0.97 against 0.98.

## CODEGEN (Task 6) - `T204_PROGRAM_BINARY` at link

| program | bytes | |
|---|---|---|
| 14. FINAL (cloud with the rain family) | **302,615** | |
| 15. **split cloud** (`t205_cloud_split`) | **228,477** | **-74 KB; the family-out class (T182: 225,940) plus 2.5 KB of composite** |
| split cloud, mask mode | 225,591 | |
| 16. **rain pass** (`t205_rain_pass`) | **249,822** | the segment test, the gate, the shaft's four walks, the light cone |
| rain pass, mask mode | 191,068 | |
| 17. rain-family code removed from the cloud program | **77 KB = 25%** | the shaft's 57 KB and the test's 20 KB |
| combined | 478,299 | two programs, each smaller than FINAL |

## PERFORMANCE (Task 8) - three runs, paired against bracketing FINAL anchors

| | SIDE (runs 1 / 2 / 3) | PLAY_VIS_NEAR (1 / 2 / 3) |
|---|---|---|
| anchor: FINAL ray / field / total | 12.69 / 12.81 / 12.29; 1.66 / 2.20 / 1.66; **14.35 / 15.01 / 13.95** | 12.58 / 11.89 / 11.96; 1.66 / 2.15 / 1.83; **14.24 / 14.04 / 13.79** |
| 18. **split cloud draw** (3 repeats each) | **5.32 / 5.10 / 5.03** (p95 5.2-5.4) | **4.37 / 4.28 / 3.56** (p95 3.8-4.5) |
| 19. **rain pass** | **1.06 / 1.03 / 1.03** (p95 1.05-1.15) | **1.55 / 1.50 / 1.60** (p95 1.53-1.64) |
| field: storm field build + gate build | 0.94 + 0.12 = **1.06** (1.28 in run 2) | 1.06 (1.43) |
| 20. composite (`CloudFieldCompositeRenderer`, unchanged) | ~0.09 | ~0.09 |
| 21. **total** | **7.45 / 7.41 / 7.13** | **6.98 / 7.21 / 6.23** |
| 22./23. **net saved** | **6.90 / 7.60 / 6.82 ms** | **7.26 / 6.83 / 7.56 ms** |
| 24. paired ratio, cloud draw alone (`T205_CONSUMER`, run 3) | 2.446 (12.30 -> 5.03) | 3.359 (11.97 -> 3.56) |
| 25. spread (run 3) | 0.8% | 1.4% |
| the family-out ceiling in the same session (`t182_norainseg`) | 5.18 / - / 4.92 ray | 4.52 / - / 3.44 |
| the shaft-out ceiling (`t201_norain_shaft`) | 9.96 / - / 9.78 | 9.90 / - / 8.31 |
| the fine-band variant (run 3): cloud + rain pass + field | 4.99 + **4.89** + 1.06 = 10.94 | 3.51 + **8.10** + 1.06 = 12.68 |

The split cloud draw sits **0.1 ms above the family-out ceiling** (the
composite; within session noise at PLAY_VIS_NEAR): the cloud program has
lost the whole family, skeleton and gate fetch included (Task 9 - nothing
rain-shaped remains in it). The rain pass, fullscreen with no compaction,
costs 1.03-1.06 / 1.50-1.60 ms: it marches every pixel that passes the
cloud's own pretest at the coarse stride with one gate fetch a sample and
runs the shaft where the test is true. The storm field build
also dropped from 1.66 to 0.94 ms - the same build pass compiled in the
split program, one more instance of the build's codegen following its host
(T202's 1.65-4.6 ms swing); it is real for this pair and not a claim
about FINAL's build.

Against the brief's scale (>= 3.5 ms major PASS) the split saves **6.8-7.6
ms at both poses**, nearly the family-out ceiling itself (7.4-8.5): the
rain pass costs 1.0-1.6 ms and gives back 7.3-8.5.

## SPARSITY (Task 10)

26. Positive rain pixels 1.75% / 0.64% (T204's census). 27. Active 8x4
tiles 3.0% / 1.8%. 28. The fullscreen rain pass costs 1.06 / 1.55 ms with
the early rejections it inherits (the slab miss, the scene clip, the
coverage pretest) and the gate's proof on 98% of its samples; a
tile-masked pass would run the march on 3% of the tiles and the gate on
none of the rest. Its bound is the pass's cost less the gate-and-loop
fraction of it - roughly 0.5-1.0 ms - and it is not needed to reach the
gate below. Not implemented (Task 10: only if the fullscreen pass leaves
significant cost).

## POST-T205 (Task 11) - the split as measured, raw at 0.25

29. **SIDE: cloud 5.0-5.3 + rain 1.0-1.1 + field 1.06 + composite 0.09 =
7.1-7.5 ms** (three runs). 30. **PLAY_VIS_NEAR: 3.6-4.4 + 1.5-1.6 + 1.06 +
0.09 = 6.2-7.2 ms.** FINAL in the same sessions: 13.9-15.0 / 13.8-14.2. Not baked; these are the split
arms' numbers, which a productionized FINAL would reproduce (the split
cloud program is FINAL's specialization with the family compiled out).

## DECISION

31. **T205: PASS (major).** Net 6.8-7.6 ms at both poses against a
    >= 3.5 ms bar; the frame exact-class (cloud SSIM 0.99998, 0.1-0.4% of
    pixels at 3e-4 RMS, thin 0.98-1.0, hole 1.0); the cloud program at the
    family-out size. The rain mask is the one metric below its class (IoU
    0.60-0.75, 22-39% of the reference's sub-ulp thin pixels missed, false
    rain 1-6%): a sampling-position residual the frame does not show and
    the fine-band variant prices at 4-8 ms fullscreen.
32. **Separate rain pass production candidate: YES.** It is the first
    architecture since T199 whose gain is the size of the constraint it
    attacks. What it costs: a second cloud-sized RGBA32F target, one
    texture unit, the gate field (4 MB, 0.12 ms), a rain program compiled
    beside the cloud program, and the ~0.5% of pixels where the split
    orders a mixed sample differently, and the thin-mask residual above.
    Not baked in this commit: baking means FINAL becomes two programs and
    the renderer's production path draws the rain pass every frame - the
    T202 recipe does not cover it, so it is its own task.
33. **Temporal ready: YES by the reopen rule** - raw combined 6.2-7.5 ms
    against <= 8 (~6 preferred), on the split arms over three sessions;
    the field build's session swing (0.94-1.43 here, 1.66-2.2 on FINAL) is
    the widest term.
34. **Exact next task: T206 - productionize the split** (FINAL = the split
    cloud program + the rain pass, the gate built every frame, the rain
    program a production program registered beside the monolith, the
    invariants flipped, a fresh FINAL baseline at 0.25 and 0.75 for the
    pixel-scaling exponent), then **T207 - temporal reconstruction / history
    validity** on the 7 ms frame. The tile-masked rain pass (which would
    make the fine band affordable and close the thin mask) and the
    mixed-sample residual are follow-ups the numbers do not require.

## VALIDATION

35. `campaignGate` PASS x3. 36. `check build` PASS (51 invariants incl. the new T205 one, jar). 37. Rain quality:
above. 38. Consumer accounting: the split cloud program has no rain
consumer to account; the monolith's capture is unchanged (closed). 39.
Workload views: none added (T204's view 78 reused). 40. Campaign registry:
`T205` (`t205-rain-pass.txt`, `rainPassRunRequested`, `t205Run`,
`T205_OPTIMIZATION_ARMS`, `T205_SUPPORT_PROGRAMS` = the gate build and the
two rain programs). 41. Variant scope: eight T205 programs
(`t205_cloud_split`, `t205_cloud_split_mask`, `t205_rain_pass`,
`t205_rain_pass_mask`, and the `_fine` four), campaign-scoped, compiled in
GL by the T205 invariant; FINAL defines neither `PA_RAIN_PASS` nor
`PA_RAIN_SPLIT_COMPOSITE` (asserted). The storm field on unit 16 (T203),
the rain pass output on unit 17 (`RAIN_PASS_UNIT`), the contract
contiguous (`selfCheck`). The profile carries a `rainPassP50/P95` column.
42. Marker retired to `.done`. 43. git status clean of task files (logs
preserved). 44. Commit: the T205 commit on `experiment/cloud-descriptor-k`
(parent `792bea9`), not merged.
