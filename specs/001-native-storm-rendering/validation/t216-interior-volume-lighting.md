# T216 - Interior Volume / Lighting Structure

Branch: `quality/t216-interior-volume` (from `quality/t215-lower-tower-structure` @ `6e7d163`).
Production constants at entry, all locked and unchanged by this task:
`PRODUCTION_TEMPORAL_MODE=1`, `PRODUCTION_SPATIAL_UPSCALE=-1`, `PRODUCTION_RAIN_LATTICE_MODE=0`,
`PRODUCTION_ORIGIN_JITTER_MODE=0`, `PRODUCTION_MACRO_ENVELOPE_MODE=3` (T213),
`PRODUCTION_LOWER_BODY_MODE=1` (T215). `campaignGate` PASS at entry.

## RECOVERY

1. Working tree at entry carried only the preserved `PAchangelog.md` user modification
   ("Fixed a biome config issue"), left uncommitted throughout, plus the historical loose
   campaign/gate logs. No validation evidence was deleted.
2. `run/t132-autorun-exit.txt` stayed armed for every run. Exactly one campaign marker was armed
   at a time (`run/t211-captures.txt` for the static capture routes). All clients were launched
   from the PowerShell tool via `launch-t216*.cmd`, never from Bash.
3. No Gradle build was run while a client was running.

## ATTRIBUTION

The brief's premise is that the cloud interior reads too flat and featureless. **Attribution was
run before any shading or density change, and it does not support the premise as stated.** The
flatness is real in exactly one of the captured poses, and that pose is flat for a physically
correct reason.

### What "interior" measures

4. `t216-interior.py` decomposes a frame into spatial bands by successive box blurs
   (fine <=5 px, mid ~17 px, low ~65 px, macro >65 px), reports the sd of each band, the mean sd
   inside a 17 px window (`localSD`), the fraction of pixels more than 6 levels below/above their
   local mean (dark/bright pockets), and the autocorrelation half-width of the residual
   (pocket scale). Banding matters here: "flat" has to name a missing *scale*, otherwise the only
   available fix is a global contrast increase, which the brief rules out.

### Measurement 1 - the INSIDE pose is flat; nothing else is

5. Baseline, T215 production, one run, same storm:

   | view | mean | sd | localSD | fine | mid | low | macro | dark% | bright% |
   |---|---|---|---|---|---|---|---|---|---|
   | `4_INSIDE` | 83.96 | 2.453 | 0.105 | 0.088 | 0.047 | 0.098 | 2.430 | 0.00 | 0.00 |
   | `2_SIDE` (whole frame) | 185.77 | 32.03 | 1.886 | 1.815 | 3.129 | 5.453 | 30.289 | 2.73 | 2.71 |

   `4_INSIDE` is not merely low-contrast, it is *degenerate*: `localSD` 0.105 on a 0-255 scale,
   zero pixels in either pocket class, and essentially all of its (tiny) variance sitting in the
   macro band. That is a wash, not a volume.

6. The same pose measured on the T211-era build gives `localSD` 0.100 / sd 1.660 - so the INSIDE
   wash is **not a regression introduced by T213/T214/T215**. It predates the accepted morphology
   work.

7. But the whole-frame `2_SIDE` number is contaminated by sky and by the silhouette edge. Re-measured
   over **through-body regions only** (the silhouette mask eroded 12 px inward, so every sampled
   pixel is cloud with cloud behind it):

   | region | localSD | dark% | bright% |
   |---|---|---|---|
   | `2_SIDE` body | **8.328** | 10.70 | 12.95 |
   | `6_LATERAL` body | **8.911** | 13.98 | 14.33 |
   | `4_INSIDE` (buried) | 0.105 | 0.00 | 0.00 |

   Through-body interiors carry `localSD` ~8.3-8.9 with 11-14% of pixels in each pocket class.
   That is abundant interior structure, ~80x the INSIDE pose. **The renderer is not globally
   failing to produce interior variation.** Whatever is wrong is specific to the deep pose.

### Measurement 2 - eliminating the hypotheses

8. **A - density itself lacks internal variance. DISPROVED.** In the core the body remap is
   `body = 0.284 + 0.716 * baseField` with `baseField` sd 0.319, i.e. the density field entering
   erosion has sd ~0.23 about a mean ~0.63 - a 36% relative spread before `STORM_EROSION` even
   bites. Density is not flat; the through-body `localSD` of 8.3-8.9 is that variance arriving
   in the image.

9. **C - transmittance saturates too quickly. TRUE, and it is the whole explanation for the
   INSIDE pose.** With `Tuning.FIELDS` (`densityMul` 1.45, `extinctionScale` 0.115) and the core
   post-erosion density ~0.407, extinction is **0.0679 per block**: the 1/e depth is **14.7
   blocks** and T=0.05 is reached at **44 blocks**. The `4_INSIDE` camera sits at 0.25 radius
   inside a storm whose BASE lobes have R 448 (T215 trace), so *every* ray in *every* direction
   terminates inside core material within ~15 blocks. A 15-block sphere of statistically
   homogeneous core is uniform by construction - a featureless wash there is the physically
   correct answer, not a rendering defect. Confirmed by the shader's own behaviour: the inside
   path early-outs at `opticalDepth * ExtinctionScale >= 28.0`.

10. **D - light sample spacing misses useful variation. NOT THE CAUSE.** The light march is
    `stepLength = 14.0` with `steps = clamp(LightSteps, 2, MAX_LIGHT_STEPS)`, capped to 4 when
    `cameraStartsInsideSlab`, i.e. a 56-block light ray at 4 taps for inside views. That is a real
    reduction, but it cannot explain the INSIDE wash: the primary march terminates in ~15 blocks,
    so the lit/unlit contrast it could resolve has no depth over which to develop. The same 4-tap
    cap applies to the through-body views that measure `localSD` 8.9.

11. **F - temporal reconstruction suppresses subtle structure. DISPROVED by direct measurement.**
    Paired arms in one run, same storm: `2_SIDE` at full temporal reconstruction against
    `A_SIDE_CURRENT_ONLY` with history bypassed.

    | arm | mean | sd | localSD | fine | mid | low | macro | dark% | bright% | pocket |
    |---|---|---|---|---|---|---|---|---|---|---|
    | FINAL (temporal) | 185.77 | 32.030 | **1.886** | **1.815** | 3.129 | 5.453 | 30.289 | 2.73 | 2.71 | ~15px |
    | CURRENT_ONLY | 185.77 | 32.012 | 1.852 | 1.599 | 3.080 | 5.454 | 30.291 | 2.66 | 2.64 | ~26px |

    The temporal arm is **not** the one losing detail - it carries *more* fine-band energy
    (1.815 vs 1.599) and a *smaller* pocket scale (15 px vs 26 px) than the history-bypassed arm,
    which is what the 4-phase 0.25 -> 0.50 effective lattice is supposed to buy. This satisfies
    the brief's CASE C bar ("prove it before touching temporal code"): the proof came back
    negative, so **temporal architecture is not reopened.**

12. **B / E - lighting integration or phase/extinction response compresses contrast. NOT
    SUPPORTED, and not separately testable on the buried pose.** Density variance (8) survives to
    the image at `localSD` 8.3-8.9 through the body, which is what B and E would have to be
    destroying. On the INSIDE pose there is no surviving optical path for them to compress (9), so
    a lighting change there is untestable rather than merely unhelpful.

13. **G - another term.** The residual, honest observation is structural rather than a bug: in
    through-body views the pocket-scale bands (mid 3.13, low 5.45) are dominated roughly **10:1**
    by the macro lit/shadow gradient (30.29). The interior therefore reads primarily as one large
    soft light ramp with finer structure layered on top at a tenth the amplitude. Whether that
    ratio is wrong is a judgement call, not a defect measurement, and the brief explicitly
    forbids optimizing for maximum contrast.

### Measurement 3 - the decisive test: re-pose, do not re-shade

14. Task 7 permits adding a deep-interior pose "if the existing INSIDE pose does not exercise
    enough optical depth". The attribution above says the opposite - it exercises far too much - so
    the test is a pose that is still *inside* the storm but has genuine optical-depth variation.
    Added permanently to `StormT098CaptureDriver` as **`4B_THROUGH_BODY`** at 0.82 radius /
    0.62 height (the existing `4_INSIDE` at 0.25 radius is kept: it is the extreme-depth check and
    the historical baseline).

15. Both poses, **one run, one storm** (`6cd9be91`, roles base=2 core=2 tower=2 anvil=4), whole frame:

    | pose | mean | sd | localSD | fine | mid | low | macro | dark% | bright% | pocket |
    |---|---|---|---|---|---|---|---|---|---|---|
    | `4_INSIDE` (0.25 R, buried) | 84.50 | 5.967 | **0.197** | 0.087 | 0.082 | 0.386 | 5.844 | 0.00 | 0.00 | ~36px |
    | `4B_THROUGH_BODY` (0.82 R) | 145.73 | 23.655 | **6.367** | 1.088 | 3.793 | 9.363 | 18.209 | 5.80 | 6.75 | **~10px** |

    **`localSD` 0.197 -> 6.367, a 32x increase, from moving the camera and changing nothing else.**
    Pocket classes go 0.00%/0.00% -> 5.80%/6.75%. The pocket scale drops to ~10 px, the finest of
    any captured view. This is a paired within-run comparison, so it is not confounded by the
    storm-realization change that invalidates cross-run image comparisons.

16. `4B_THROUGH_BODY` also beats the exterior views on every structural band while carrying *less*
    macro gradient - mid 3.793 vs `2_SIDE`'s 3.035, low 9.363 vs 5.232, macro 18.2 vs 28.4. Its
    macro:mid ratio is **4.8:1** against `2_SIDE`'s 9.3:1, i.e. from inside the storm the image is
    *less* dominated by the big light ramp than from outside, which is the correct behaviour and
    the direct answer to the concern raised in item 13.

17. **Visual confirmation** (`validation/t216-campaign/`): `4B_THROUGH_BODY.png` shows billowing
    cauliflower masses with dark creases between them, lit crowns, and clear front-to-back depth
    ordering - it reads as a real 3D volume. `4_INSIDE.png` is a near-uniform slate wash with a
    single faint wisp, exactly what a ~15-block visibility fog bank looks like. The metric and the
    eye agree.

## DECISION

18. **The brief's premise is DISPROVED, and T216 ships no shading or density change.** The
    interior already reads as a real 3D volume with coherent internal light and density structure
    wherever there is optical depth over which structure can develop. The "flat, featureless
    interior" impression came from a single capture pose buried so deep in core material that a
    uniform wash is the physically correct result.

19. Under the brief's decision framework: **CASE C is closed by explicit negative proof** (item 11
    - the temporal arm carries *more* fine detail than the history-bypassed arm), so temporal
    architecture is not reopened. CASE A/B/D/E are all ruled out by items 8-12. No candidate was
    built, because attribution never produced a defect for one to fix, and the brief is explicit
    that the first result should be attribution rather than a visual tweak.

20. **No change was made to the shader.** The entire diff is 8 lines in
    `StormT098CaptureDriver.java` adding the `4B_THROUGH_BODY` pose. All six production constants
    are untouched, so T213's macro envelope and T215's lower-body lift are bit-identical - there is
    no exterior collateral to measure, and no performance delta to report, because no rendering
    code changed. Tasks 4-6 and 8-13 (candidate, localization, paired arms, lighting balance,
    static probe, motion route, performance) are therefore not merely skipped but **inapplicable**:
    each one measures a candidate, and there is no candidate.

21. **What the capture set gains.** `4B_THROUGH_BODY` is permanent. The T098 set previously had no
    pose that exercised interior structure at usable optical depth - `4_INSIDE` was saturated and
    every other view was exterior. Any future interior work now has a pose that can actually
    show a regression.

## HONEST LIMITS AND RESIDUAL OBSERVATIONS

22. **The one real observation this task surfaced is a level defect, not a structure defect, and
    it is deliberately left unfixed.** `4_INSIDE` sits at mean luminance 84.5 while the exterior
    body sits at 132-187. Being inside a storm is genuinely dim, but a dark slate wash is dimmer
    than a real cloud interior, which reads as luminous fog. The cause is that the march is
    single-scattering with multi-scattering octaves that still require a direct-light path: at
    optical depth >= 28 the inside early-out fires and the result is ambient-dominated. Fixing it
    means a depth-dependent multi-scatter/ambient term - i.e. changing lighting constants, which
    the brief forbids doing first, and which by construction would make the wash *lighter* without
    making it *structured*. It would not satisfy T216's success definition. Recommended as its own
    task with its own attribution.

23. **`4_INSIDE` is an unrepresentative pose for gameplay.** A camera at 0.25 radius inside a
    448-radius BASE lobe is an extreme. A player flying through a storm is at a `4B`-like position
    far more often. The pose is kept for coverage, not because it represents what players see.

24. **This is not a regression check that passed by luck.** The same `4_INSIDE` wash measures
    `localSD` 0.100 on the T211-era build and 0.105/0.197 on T215 across two storms, so the
    behaviour predates T213/T214/T215 and none of the accepted morphology work caused it.

25. **Scope honesty.** `4B_THROUGH_BODY` shows some chunky, stair-stepped puff edges in the
    mid-field, which is a march step-size artifact rather than an interior-structure problem. It is
    outside T216's subject and is not addressed here; noting it so it is not lost.

## VALIDATION

26. `campaignGate` PASS (see `gate-t216b.log`). `check build` PASS (see `check-build-t216.log`).
    Morphology sandbox PASS as part of `check`.
27. `PAchangelog.md` left modified and uncommitted throughout, as required. No validation evidence,
    campaign log, or unrelated log was deleted.
28. One campaign marker armed at a time (`run/t211-captures.txt`); `run/t132-autorun-exit.txt`
    left armed. The run exited on its own (`T132_AUTORUN_FINISHED outcome=complete frames=7357`,
    `BUILD SUCCESSFUL in 7m 15s`) - no idle client, no polling.
29. Client launched from the PowerShell tool via `launch-t216.cmd`, never from Bash. No Gradle
    build ran while the client was running.
30. Committed on `quality/t216-interior-volume`. **Not merged.**

### Evidence

- `validation/t216-campaign/4B_THROUGH_BODY.png` - the new pose; interior reads as a 3D volume
- `validation/t216-campaign/4_INSIDE.png` - the buried pose; physically correct uniform wash
- `validation/t216-campaign/2_SIDE.png` - exterior reference
- `validation/t216-campaign/t216-run.log` - run identity and completion
- Analysis: `t216-interior.py` (multi-scale bands, whole frame), `t216-body.py` (same statistics
  restricted to the silhouette mask eroded 12 px inward, so only cloud-behind-cloud pixels count)
