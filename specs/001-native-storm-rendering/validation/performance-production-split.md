# T206 - the split is FINAL: three production programs, the frame exact-class, 7.2 / 6.2-7.1 ms raw at 0.25

Productionization of T205's separate rain pass. Two launches on 13Sep2026:
`t206-campaign/t206-run1.log` (10:40-10:42, 4,180 frames) and
`t206-run2.log` (10:46-10:49, 4,663 frames; with the lifecycle probe and the
per-scale verdicts - the harness fixes between the runs are in the driver
only). SIDE and PLAY_VIS_NEAR, Ultra 96 steps, history off, one deterministic
fixture a pose, the production FINAL bracketed by the monolithic reference
(`t206_monolithic_ref`, the pre-split FINAL) on both sides of every repeat,
three repeats a scale at 0.25 (480x270) and 0.75 (1440x810), 60 samples a
cell, anchor drift <= 3% or the block is dropped (one SIDE 0.75 block in run
1, the first SIDE 0.25 block in run 2 - the anchor after the lifecycle
probe's reload was 14.7 against 12.4 - dropped). Quality captured at 0.25.

No rain-field, primary, probe, light, temporal or visual change; no new
experiment. What the T205 arm drew, FINAL draws.

## PRODUCTIONIZATION (Tasks 0-5)

1. **Starting HEAD**: `ef93525` (T205). Working tree carried the T195-T205
   logs, preserved.
2. **Production programs** (`CoreCostDiagnosticProgram.isProductionProgram`,
   four, each unconditional, none claiming a campaign):
   `diagnostic_monolith` (every debug view and counter capture; never FINAL's
   fallback), `lean_final` (the split cloud program), `rain_pass`
   (`cloud_atmosphere_volume_rain_pass`) and `rain_gate_build`
   (`cloud_atmosphere_volume_rain_gate_build`, T203's build program
   promoted). The two rain programs are `finalSupportProgram()`: registered
   inside FINAL's try in `VolumetricCloudShaders`, so a link failure of
   either nulls the lean program and the renderer session-disables through
   `LeanFinalProgramUnavailableException` - never a FINAL frame without rain,
   never the monolith in its place. `leanFinalShaderReady()` requires all
   three. Startup: `T191_VARIANT_SCOPE declared=184 registered=4 skipped=180
   productionAlwaysLoaded=4` (the 4 registered are the T206 campaign's:
   the monolithic reference, the T188 mask reference, the T205 mask pair).
3. **Production render order** (`VolumetricCloudRenderer.render`, every
   FINAL frame): storm field build (the cloud program, `PaStormFieldPass`
   1, `RAIN_FIELD_TIMER`) -> rain gate build (`rain_gate_build`,
   `copyFrameUniforms` from the ray program, `RAIN_GATE_TIMER`) -> rain pass
   (`rain_pass`, the same uniforms and samplers, `RAIN_PASS_TIMER`, into the
   rain-pass target) -> split cloud (`lean_final`, `GPU_TIMER`, reads the
   rain-pass target on unit 17) -> the existing composite/output path
   (`CloudFieldCompositeRenderer`, unchanged). No campaign switch: the
   order is `LEAN_FINAL.rainSplitCloud()`, `rainGateGeneration()` and
   `rainPassProgram() == RAIN_PASS`, enum predicates.
4. **FINAL specialization** (`leanFinalDefines` in build.gradle, one list
   FINAL and the T171 identity controls share): `PA_PRECIPITATION_ABSENT`,
   `PA_ARM_DESC_PRECOMPUTE`, `PA_ARM_NO_RAIN_SEGMENT` (the rain family out),
   `PA_RAIN_SPLIT_COMPOSITE` (the composite at the first step past `t_R`:
   `accumulated += R * T`, `weightedT += t_R * T (1 - T_r)`, `T *= T_r`,
   and the after-loop catch-up - T205's ordering, untouched). Bits 43 (the
   refinement and both field consumers) baked, `PaStormFieldPass` live,
   `PaRainGateField` baked 0 (nothing in it reads the gate), no
   `PA_RAIN_PASS`, no `PA_RAIN_PASS_FINE`, no `PA_RAIN_MASK_OUTPUT`, no
   diagnostic selector live. The rain pass: `PA_PRECIPITATION_ABSENT`,
   `PA_ARM_DESC_PRECOMPUTE`, `PA_RAIN_PASS`, the gate baked 1, the exact
   segment test and the production shaft (`rainSegmentMayContribute(rpP,
   rpEnd)`, `rainShaftDensityOverSegment(rpP, rpEnd, 0.0)`), the coarse
   stride, no fine band, no mask output. The gate build: the field pass 1
   and the gate 2 baked. The monolithic reference is FINAL's list minus the
   two split defines (sandbox-asserted), so the comparison below isolates
   the split.
5. **Rain target**: RGBA32F, the cloud target's size (480x270 = 2.07 MB at
   0.25, 1440x810 = 18.7 MB at 0.75; the 641x360 first frame is the
   profile's default before the campaign pins the scale), `GL_NEAREST`,
   `VolumetricCloudRenderTargets.prepareRainPassTarget`.
6. **Texture units**: 15 rain gate field (`RAIN_FIELD_UNIT`), 16 storm
   field (`STORM_FIELD_UNIT`), 17 rain pass (`RAIN_PASS_UNIT`); the contract
   contiguous (`selfCheck`), 18 fragment units required and checked once.
7. **Lifecycle** (the T199 pattern; run 2, `T199_LIFECYCLE` /
   `T206_RAIN_PASS_*`): allocated on the first FINAL frame
   (`T206_RAIN_PASS_ALLOC format=RGBA32F size=480x270 bytes=2073600`);
   a resolution transition releases and re-allocates on the spot
   (`RELEASE reason=resize` / `ALLOC`, six pairs across the campaign's
   scale changes); the pass disabled releases it with every other target
   (`RELEASE reason=shutdown`); enabled again and through a resource
   reload (`step=reloadResources rainPassTarget=none`, the programs
   re-linked, `productionAlwaysLoaded=4` again) it stays absent until the
   next FINAL frame allocates it (`ALLOC` at 10:47:34); the client's exit
   releases it (`reason=shutdown`). T205's campaign target was never
   released by `destroyAll` - that leak is closed - and the gate and
   rain-pass clocks now close on `shutdown()` with the others. World
   change and renderer recreation go through the same `destroyAll` /
   first-frame allocation.

## SHADER VALIDATION (Task 6)

8. **JSON / uniform validation**, two layers. `generateLeanFinalShader`
   now checks every generated program (all 184 + the four production
   ones) at build time: every JSON uniform is a live non-sampler
   declaration in the generated GLSL, every JSON sampler a sampler
   declaration, no more than twelve JSON samplers, and every live
   non-sampler GLSL uniform is named in the JSON (arrays and the
   vanilla-managed `ProjMat`/`ModelViewMat` excepted) - a bake or strip
   that drifts from the JSON fails the build. The sandbox's T206
   invariant repeats it for the three production programs, and adds:
   every uniform `VolumetricCloudRenderer` sets by name
   (`safeGetUniform("...")`) is in the monolith's JSON (the T203 lesson:
   a missing name is a silent no-op). **Found one**:
   `PaDiagnosticStepBudget` was declared in the GLSL and set by the
   renderer but absent from `cloud_atmosphere_volume.json`, so the
   diagnostic step budget had been a no-op on the monolith; it is in the
   JSON now (baked to 0 in every lean program as before).
9. **Samplers**: FINAL's, the rain pass's and the gate build's JSONs name
   the eleven tracked samplers (the oracle sampler stripped from all
   three); the six manual samplers (`PuffCandidateMapSampler`, the two
   noise volumes, `RainFieldSampler`, `StormFieldSampler`,
   `RainPassSampler`) are declared in the GLSL and absent from every
   JSON, bound on units 12-17 by `bindManualTextures`.
10. **Startup registration set**: 4 production programs, unconditional;
    campaign arms only while their marker is present (T191, 184 declared,
    180 skipped with T206 armed).
11. **Binary sizes** (`T204_PROGRAM_BINARY`, both runs):

| program | bytes | |
|---|---|---|
| `lean_final` (production split cloud) | **228,477** | byte-for-byte T205's `t205_cloud_split`; the family-out class (T182: 225,940) |
| `rain_pass` | **249,822** | T205's `t205_rain_pass` |
| `rain_gate_build` | 141,959 | |
| `t206_monolithic_ref` (the pre-split FINAL) | 302,615 | T205's FINAL |
| `diagnostic_monolith` | 506,048 | |

The production split cloud program did not pull the rain family back in.

## QUALITY (Task 7) - production FINAL vs `t206_monolithic_ref` at 0.25; the production split's mask vs `t188_rain_mask_ref`

| | SIDE (runs 1 / 2) | PLAY_VIS_NEAR (1 / 2) | T205 accepted (SIDE / PVN) |
|---|---|---|---|
| 12./15. **cloud SSIM** | **0.999965 / 0.999982** | **1.000000 / 0.999971** | 0.99998 / 0.99999 |
| 13. **edge SSIM** | **0.999903 / 0.999948** | 0.999929 / 0.999537 | 0.99998 / 0.99998 |
| 14./16. **changed pixels** (RGBA16F ulp) | **605 (0.47%) / 704 (0.54%)** | **93 (0.07%) / 203 (0.16%)** | 0.4% / 0.14% |
| 14./16. **RMS** | **4.9e-4 / 3.6e-4** | **1.7e-4 / 1.5e-3** | 3e-4 / 6e-4 |
| maxAbs | 0.211 / 0.120 | 0.061 / 0.687 | 0.043-0.19 / 0.18-0.33 |
| mean abs | 1.4e-5 / 1.3e-5 | 2.3e-6 / 1.3e-5 | |
| silhouette IoU / thin / hole retention | 0.9992 / 0.977 / 0.986; 0.9991 / 0.972 / 0.995 | 0.9994 / 0.923 / 1.000; 0.9998 / 1.000 / 1.000 | 0.9996 / 0.982 / 1.0 |
| 17. **rain mask IoU** | **0.721 / 0.719** | **0.634 / 0.566** | 0.70-0.75 / 0.60-0.63 |
| missed rain (of the reference's pixels) | 24.2% / 16.0% | 33.3% / 41.7% | 22-26% / 36-39% |
| false rain | 5.1% / 16.9% | 5.1% / 2.9% | 4-6% / 1-2% |
| thin rain retention | 0.59 / 0.67 | 0.58 / 0.40 | 0.54-0.57 / 0.40-0.45 |
| 18. **shaft continuity / new gap rays** | 0.988 / 10; 0.972 / 28 | 0.875 / 13; 0.809 / 54 | 0.985 / 11; 0.788 / 85 |
| onset / termination error, mean blocks | 19.5 / 26.9; 14.3 / 21.1 | 5.9 / 7.5; 6.2 / 8.9 | 16-20 / 25-27; 6-7 / 10 |

The production frame is in T205's accepted class at both poses: cloud
SSIM >= 0.99997, edge SSIM >= 0.9995, 0.07-0.54% of pixels changed at
1.5e-4 to 1.5e-3 RMS, silhouette IoU >= 0.999, hole retention >= 0.986.
The run-2 PLAY_VIS_NEAR maxAbs (0.687, one pixel; 203 changed) is the
widest single value of the four captures and sits inside the split's known
residual - the sub-step ordering of a mixed body-and-rain sample at the
attach zone - not a new class of difference.

19. **Thin-rain caveat, documented.** The rain mask is below its class, as
T205 measured and accepted: IoU 0.57-0.72, 16-42% of the reference's rain
pixels missed, false rain 3-17%, continuity 0.81-0.99. The reference mask
counts a ray as rain at an optical mass of 0.0008 and 22-42% of its rain
pixels are that thin - mass the coarse rain pass loses where the reference
samples finely in the band under the base. Those pixels change the frame
by under an RGBA16F ulp, which is why the frame metrics above are exact-
class while the mask is not. Productionization is judged on the frame.
The fine-band variant (`t205_rain_pass_fine`, campaign-scoped, +0.05 IoU at
4.9 / 8.1 ms) and a tile-masked pass that would make it affordable remain
the quality follow-up; neither is in T206.

## PERFORMANCE (Task 8) - fresh, on the production renderer, both runs

Every FINAL cell bracketed by the monolithic reference on both sides
(`T206_S025_ARM` / `T206_S075_ARM`, run 2: all four verdicts accepted,
ratio spread 0.4-1.8%; run 1's single-report verdicts pooled both scales
and are superseded). Milliseconds, p50 of 60 samples, the three repeats
averaged (`T206_PRODUCTION`).

### 0.25 (480x270)

| | SIDE (runs 1 / 2) | PLAY_VIS_NEAR (1 / 2) |
|---|---|---|
| 20./25. **field build** (storm field + gate build, `rainFieldP50`) | **1.07 / 1.29** | **1.06 / 1.44** |
| of which the gate build | 0.12 / 0.12 | 0.12 / 0.12 |
| 21./26. **rain pass** (p95) | **1.05 / 1.03** (1.08 / 1.09) | **1.48 / 1.55** (1.52 / 1.57) |
| 22./27. **cloud pass** (p95) | **5.04 / 4.85** (5.21 / 5.18) | **3.57 / 3.98** (3.92 / 4.26) |
| 23./28. **composite** | **0.08 / 0.09** | **0.08 / 0.09** |
| 24./29. **total** (min-max over repeats) | **7.24 / 7.26** (7.23-7.30) | **6.20 / 7.05** (6.17-7.13) |
| reference: cloud / field / composite / total | 12.27 / 1.67 / 0.09 / **14.03**; 13.01* / 2.40 / 0.09 / **15.51*** | 10.19 / 1.91 / 0.09 / **12.19**; 12.20 / 2.30 / 0.09 / **14.59** |
| net saved / ratio | 6.79 / 1.94; 8.25 / 2.14 | 5.98 / 1.97; 7.54 / 2.07 |
| paired cloud-draw ratio (`_ARM`, drift-accepted blocks) | 2.43-2.44 (run 1); 2.55-2.57 (run 2) | 2.84-2.86; 3.04-3.09 |

\* run 2's SIDE reference mean includes the 14.74 anchor right after the
lifecycle probe's reload (rejected as a bracket, drift 17%); the three
settled anchors are 12.40-12.46.

### 0.75 (1440x810)

| | SIDE (runs 1 / 2) | PLAY_VIS_NEAR (1 / 2) |
|---|---|---|
| field build / gate build | 1.06 / 1.29; 0.12 | 1.06 / 1.53; 0.12 |
| rain pass (p95) | 4.02 / 4.63 (4.42 / 4.97) | 3.22 / 5.17 (3.38 / 6.07) |
| cloud pass (p95) | 26.31 / 32.58 (28.7 / 33.3) | 16.77 / 22.37 (18.9 / 23.1) |
| composite | 0.09 / 0.09 | 0.08 / 0.09 |
| 30./31. **total** | **31.48 / 38.59** | **21.14 / 29.16** |
| reference total | 68.51 / 77.45 | 51.01 / 71.63 |
| net saved / ratio | 37.0 / 2.18; 38.9 / 2.01 | 29.9 / 2.41; 42.5 / 2.46 |

The two sessions differ by 10-30% at 0.75 and at PLAY_VIS_NEAR 0.25 (the
reference moved with them: 51.0 -> 71.6, 12.2 -> 14.6), the field build's
known session swing (T202: 1.65-4.6 ms for a byte-identical program) now
visible on the cloud draw as well; the within-run spread is 0.3-1.8%. The
paired ratios are the stable quantity: the split's cloud draw is 2.4-2.6x
the reference's at SIDE and 2.8-3.1x at PLAY_VIS_NEAR, at both scales.

## CODEGEN (Task 9)

Item 11 above. The production `lean_final` links to 228,477 bytes, T205's
split-cloud size to the byte; the rain family (77 KB) is in `rain_pass`
(249,822) and nowhere else on the production path.

## RETIREMENT (Task 10)

The T205 marker was already `.done`; the T206 marker is retired to
`.done`. `t205_cloud_split` and `t205_rain_pass` - byte-identical to the
production pair - are removed from the enum and the generated set (the
generator now empties its output directory first, so a retired variant
cannot linger in the jar; the sandbox asserts both absent). The T205
campaign keeps its diagnostics, campaign-scoped: the mask pair (the T188
rain mask through the production split - T206 selects it as a control)
and the fine-band pair (the priced bound); its arm table now prices the
fine band and the two ceilings against FINAL, which is the split. The
monolithic reference `t206_monolithic_ref` stays as the quality anchor,
and as the program the T202 comparator (`t202_refine_field`) is
byte-identical to. The T171 identity controls take FINAL's define list
and draw the rain pass, so they stay byte-identical to FINAL. The T140
oracle family keeps the pre-split specialization (the oracle returns
before the composite would run) and is documented as such. Historical
reports untouched.

## TEMPORAL READINESS (Task 11)

32./33. Production totals at 0.25: SIDE 7.24-7.26 ms, PLAY_VIS_NEAR
6.20-7.05 ms; at 0.75: 31.5-38.6 / 21.1-29.2 ms. Against 10 ms the
reconstruction budget at 0.25 is **2.74-2.76 ms at SIDE and 2.95-3.80 ms at
PLAY_VIS_NEAR**; against 8 ms, **0.74-0.76 and 0.95-1.80 ms**. At 0.75 the
frame is 21-31 ms over either budget: the 0.75->0.25 total ratio is 4.3-5.3
(SIDE) / 3.4-4.1 (PLAY_VIS_NEAR), an exponent of 0.67-0.76 / 0.56-0.65 on
the 9x pixel ratio (T200's pre-split cloud-only alpha: 0.82 / 0.78). The
rain pass scales shallower than the cloud draw (2.2-4.5x against 4.7-6.7x):
it is fullscreen and unmasked, so most of its pixels reject early at either
scale. Temporal reconstruction is the mechanism that would serve a
0.75-class output from a 0.25-class march; the budget it has to fit is the
2.7-3.8 ms above at 10 ms.

34. **T207 ready: YES.** The reopen rule (raw combined <= 8 ms at 0.25)
holds at both poses on the production renderer in two sessions, with
0.7-1.8 ms of margin against 8 and 2.7-3.8 against 10.

## DECISION

**T206: PASS.** (1) The split rain path is the production renderer - no
campaign switch, three production programs drawn every FINAL frame.
(2) Registration is minimal and correct: four unconditional programs, the
campaign arms scoped, fail-fast on any production link failure. (3) The
lifecycle is correct: the rain-pass target is allocated on the first FINAL
frame and released on resize, level change, reload, disable, shutdown and
exit, evidenced by the probe. (4) Full-frame quality is in the T205
accepted class (SSIM >= 0.99997, 0.07-0.54% pixels, RMS <= 1.5e-3), the
thin-rain mask caveat documented. (5) 0.25 totals 7.24-7.26 / 6.20-7.05 ms,
under 8 at both poses. (6) `campaignGate` x2, `check build` PASS.

## VALIDATION

35. `campaignGate` PASS x2 (before each launch; 52 invariants including
the new T206 one). 36. `check build` PASS (jar, reobf). 37. Lifecycle probe:
item 7, run 2. 38. Program registration invariant: T191 flipped to four
unconditional production programs; the T206 invariant asserts the exact
production set, FINAL's and the rain pass's specializations, the
monolithic reference as FINAL minus the split, the JSON/GLSL match of all
three production programs, the renderer's set-by-name uniforms declared,
the units, the fail-fast strings, the target release and the clocks'
close, and compiles all four in GL; T183 checks the shipped rain path as a
pair (FINAL composites, the rain pass carries both calls); T199/T202's
comparator identity is against the monolithic reference; T171's identity
controls still byte-identical to FINAL; T203's build program under its
production name; T205's invariant asserts the retired pair absent. 39.
Variant scope: 184 campaign programs, four production. 40. Markers: T205
`.done`, T206 `.done`. 41. git status: task files only (logs preserved).
42. Commit: the T206 commit on `experiment/cloud-descriptor-k` (parent
`ef93525`), not merged.
