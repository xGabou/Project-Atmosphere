# T217 - Deep-Interior Brightness / Multiple-Scattering Approximation

## RECOVERY

1. Starting HEAD: `dc12e54` (T216, "interior flatness disproved; it was the capture pose").
2. Branch: `quality/t216-interior-volume`, continued into `quality/t217-deep-interior-lighting`.
3. Production state verified at entry and unchanged by attribution: `PRODUCTION_TEMPORAL_MODE=1`,
   `PRODUCTION_SPATIAL_UPSCALE=-1`, `PRODUCTION_RAIN_LATTICE_MODE=0`,
   `PRODUCTION_ORIGIN_JITTER_MODE=0`, `PRODUCTION_MACRO_ENVELOPE_MODE=3` (T213),
   `PRODUCTION_LOWER_BODY_MODE=1` (T215). New `PRODUCTION_DEEP_SCATTER_MODE=0`.
   Working tree carried only the preserved `PAchangelog.md` user modification.
4. Harness: `run/t211-captures.txt` the only campaign marker, `run/t132-autorun-exit.txt` armed,
   client launched from the PowerShell tool via `launch-t217.cmd`. `campaignGate` PASS before any
   client work (`gate-t217.log`). No Gradle build ran while a client was running.

## ATTRIBUTION

### A correction to T216 carried into this task

T216 stated that the deep interior is ambient-dominated "because the inside early-out at optical
depth 28 fires". **That is wrong, and the correction is load-bearing here.** The early-out is
gated on `cameraStartsInsideSlab`, but the same branch caps the light cone to 4 taps, and the
cone's `stepLength *= 1.42` growth means those 4 taps reach only 102.2 blocks:

| taps | march reach | od x ExtinctionScale | early-out |
|---|---|---|---|
| 4 (inside camera, production) | 102.2 blocks | **6.93** | never fires |
| 6 | 239.9 blocks | 16.28 | never fires |
| 8 (uncapped) | 517.7 blocks | 35.13 | fires |

The 28.0 threshold needs `opticalDepth >= 243`, i.e. ~413 blocks of march at core density 0.590
(post-erosion 0.407 x `DensityMul` 1.45). **The early-out is unreachable for exactly the camera
class it is gated on** - it is dead code there. The deep interior is ambient-dominated because
`exp(-6.93) = 9.8e-4`, not because integration is truncated.

### The controlling cause

5. `4_INSIDE` light-cone optical depth: **od x ExtinctionScale = 6.93** (60.30 raw over a 102.2
   block cone at density 0.590).
6. Final direct transmittance at that depth: `exp(-6.93) =` **9.78e-4**. Single scattering is
   extinguished.
7. Direct-light contribution: the multi-scattering octave chain (Hillaire, 3 octaves, extinction
   scaled by a = 1, 0.42, 0.1764 and weights b = 1, 0.52, 0.2704, normalized by their sum 1.7904).
   At od 6.93 the octave terms are 7.82e-5 / 2.26e-3 / 6.34e-3 - **octave 2 alone carries 73.1% of
   all scatter energy**, which is the approximation working as designed. It is still not enough:
   the whole direct term lands at 0.047 of a 0.967 unattenuated value.
8. Ambient contribution: 0.125, i.e. **72.7%** of total radiance deep inside. The ambient is itself
   cut by depth - `ambient *= mix(ambientRetention, 1.0, directTransmission)` with
   `ambientRetention = mix(0.74, 0.58, materialDarkness)` - so deep material retains 64.4% of an
   ambient that three stacked storm/underside factors have already reduced.
9. Early-out state: **never entered** at any inside pose (see the table above). The counter path
   `paLightConeEarlyOuts` can only increment on a cone that is allowed more than 4 taps.
10. **Controlling cause = E, another term: there is no term anywhere in the model that grows with
    optical depth.** Swept over od x ext = 0 -> 50 in 0.5 steps, every component is monotonically
    non-increasing:

    | od x ext | direct | ambient | silver | total | 8-bit |
    |---|---|---|---|---|---|
    | 0.00 | 0.773 | 0.194 | 0.000 | 0.967 | 182.5 |
    | 1.00 | 0.404 | 0.151 | 0.000 | 0.555 | 131.0 |
    | 3.00 | 0.154 | 0.129 | 0.000 | 0.282 | 78.3 |
    | 6.93 | 0.047 | 0.125 | 0.000 | 0.172 | 51.1 |
    | 20.0 | 0.003 | 0.125 | 0.000 | 0.129 | 39.3 |
    | 50.0 | 0.000 | 0.125 | 0.000 | 0.125 | **38.3** |

    Direct decays, all three scatter octaves decay, the silver shell is gated off by density, and
    the ambient is *reduced* by depth rather than raised. The model therefore converges to an
    ambient floor of ~38/255. Physically, multiple scattering in an optically thick medium does the
    opposite: it builds an increasingly isotropic diffusion glow that approaches a bounded albedo
    limit. **The missing quantity is a term that increases with optical depth and saturates** -
    which is exactly the approximation Task 3 specifies, so the candidate is justified by
    attribution rather than chosen by guesswork.

**Honest limit on the CPU mirror.** `t217-light.py` reproduces `evaluateLightingFromOpticalDepth`
exactly, but it must guess the per-sample inputs the shader derives from geometry (`h01ForAmbient`,
`combinedStorm`, `materialDarkness`, `cosTheta`). Across a 27-point sweep of those it predicts
43.8-60.3 8-bit, while `4_INSIDE` measures 84.5 - **no parameter set reproduces the measurement**.
The load-bearing results are therefore the ones that are invariant across that whole sweep: the
ambient share (70.2-75.3%, mean 72.8%) and the monotonicity. Absolute levels are NOT taken from
this model, which is why the decomposition below is measured in the shader instead.

### Measured decomposition (the shader, not the model)

New live uniform `PaDeepScatter`. Modes 1/2/3 are attribution-only: 1 zeroes every direct term,
2 zeroes ambient, 3 lets an inside light cone march its full tap budget. Captured against
production at both interior poses in one run. Converting 8-bit back through the tone curve
(`radiance = -ln(1 - v/255)/1.30`) the arms are **additive within 2%**, which is the check that
they are clean:

| pose | production | ambient only | direct only | sum | uncapped cone |
|---|---|---|---|---|---|
| `4_INSIDE` | 0.31468 | 0.25330 (**80.5%**) | 0.06437 (20.5%) | 101.0% | **0.31468 - bit-identical** |
| `4B_THROUGH_BODY` | 0.65209 | 0.27030 (41.5%) | 0.39555 (**60.7%**) | 102.2% | 0.50099 (76.8%) |

The interior inverts: through-body is direct-dominated, buried is ambient-dominated.

## NEGATIVE CONTROL

11. Uncapping the inside light cone changed `4_INSIDE` **not at all** - the frame is bit-identical
    to production (85.60 mean, 2.125 sd, 0.113 localSD in both). At `4B_THROUGH_BODY` the same arm
    made the view **darker** (100% -> 76.8%), which is the predicted direction: a longer light path
    only accumulates more optical depth.
12. Performance impact: not productionized. The arm exists only to answer the question and is
    removed with the other attribution modes.
13. **Early-out / truncation as the cause: NO.** Two independent reasons, one measured and one
    structural. Measured: the uncapped arm is bit-identical at the pose in question. Structural:
    `lightMarchOpticalDepth` begins `if (cameraInsideCloud) { ... return localDensity * 18.0 +
    forwardDensity * 82.0; }` - a buried camera takes ONE probe 28 blocks along `LightDir` and an
    analytic extension, and **never enters the march loop at all**, so the tap cap, the 1.42 growth
    and the 28.0 early-out are all unreachable there. There is no integration to truncate. CASE C
    is closed and temporal/integration architecture was not reopened.

## CANDIDATE

14. Exact equation, added to `radiance` only - density, extinction and alpha are untouched:

    ```glsl
    float camGate  = smoothstep(0.05, 0.16, CameraCloudDensity);
    float msFill   = 1.0 - exp(-max(opticalDepth - 2.0, 0.0) * STORM_MS_RATE);  // rate 0.35
    vec3  msSource = skyAmbient + LightColor * sunTint * STORM_MS_SUN_SHARE;    // share 0.12
    vec3  indirect = msSource * (STORM_MS_STRENGTH * msFill * camGate)          // strength 0.46
                   * mix(1.0, 0.78, combinedStorm)
                   * mix(1.0, 0.72, rainFraction);
    ```

    `skyAmbient` is the sky energy BEFORE the storm/underside/retention darkening, because the
    retention factor is precisely the term that removes the sky a buried sample cannot see.

15. **Localization, and the failed first attempt.** The first design localized on optical depth
    alone. It worked at the buried pose and **failed on collateral**, for a reason that is physical
    rather than a tuning miss: the anvil underside has the whole anvil slab above it along a
    near-vertical `LightDir`, so it carries the HIGHEST light-cone optical depth in the scene -
    above a buried camera's. Splitting dark-cloud pixels by image region, every onset lifted the
    anvil 2.2-2.5x harder than the body:

    | onset | anvil (upper) lift | body (lower) lift | ratio |
    |---|---|---|---|
    | 1.5 | +31.89 | +12.69 | 2.5x |
    | 3.0 | +22.40 | +8.39 | 2.7x |
    | 4.0 | +14.53 | +5.89 | 2.5x |
    | 5.0 | +8.20 | +3.80 | 2.2x |

    Raising the onset bought anvil protection by giving back interior gain, one for one, with no
    clean win anywhere on the curve. **Optical depth cannot separate "deeply buried" from "deeply
    shadowed" because to that quantity they are the same condition.** The quantity that does
    separate them is how much cloud the CAMERA is in - and the negative control had already proved
    it discriminates these two poses, since the uncapped-cone arm moved at `4B` and was
    bit-identical at `4_INSIDE`, which is exactly `cameraInsideCloud` (`cameraStartsInsideSlab &&
    CameraCloudDensity > 0.08`) telling them apart. The gate is a smoothstep over 0.05-0.16 rather
    than that boolean so entering cloud ramps instead of snapping.
16. Strength and bounds: `msFill` is bounded above by 1 and `camGate` by 1, so `indirect` can never
    exceed `msStrength` (0.46) of the incoming sky+sun energy. C1 = 0.46, C2 = 0.28 restrained.
17. Energy constraint: the term is additive radiance inside the existing filmic tone curve
    (`1 - exp(-radiance * toneExposure)`), so it saturates rather than clipping, and it carries the
    same storm and rain darkening the direct and ambient terms already carry.

## QUALITY

**Method correction that governs every number below.** Arms were first grouped by arm (all C's,
then all O's, then all L's) inside one run. That is not sufficient: in a ~120-shot capture set a
**production** shot late in the sequence measured **113.12** at `4_INSIDE` where two production
shots of the same pose earlier in the same run measured **86.75 and 86.76**. Arms compared across
blocks are confounded by sequence position. The numbers below come from a re-run in which each pose
captures **L0/C1/C2 back to back**. (This does not disturb the attribution, whose arms were one
contiguous block and additive within 2%, nor the L0/L1 pairs, which were already adjacent.)

18. `4_INSIDE` mean: **87.23 -> 122.15** (C1), 109.63 (C2). Linear radiance 0.324 -> 0.503, +55.2%.
19. `4_INSIDE` sd: 4.6 -> 3.9; `localSD` 0.367 -> 0.334 (C1), 0.242 (C2). Both are far below one
    8-bit level: this pose has essentially no resolvable structure in any arm (T216 established
    why), so the change is not a meaningful contrast loss.
20. `4B_THROUGH_BODY` mean: **154.19 -> 154.19**. Unchanged.
21. `4B_THROUGH_BODY` `localSD`: **5.012 -> 5.013**. Unchanged.
22. SIDE level: 187.60 -> 187.59. Unchanged.
23. LATERAL level: 191.51 -> 191.51. Unchanged.
24. Dark pockets at `4B`: 3.70% -> 3.70%. At NEAR_EDGE 3.99% -> 3.99%.
25. Bright pockets at `4B`: 3.30% -> 3.29%. At NEAR_EDGE 4.32% -> 4.33%.
26. Local contrast: unchanged everywhere but the buried pose (item 19). Every difference at the
    five non-buried views is **<= 0.01 8-bit, below quantization**.

    | view | L0 | C1 (0.46) | C2 (0.28) |
    |---|---|---|---|
    | `4_INSIDE` | 87.23 | **122.15** | 109.63 |
    | `4B_THROUGH_BODY` | 154.19 | 154.19 | 154.19 |
    | SIDE | 187.60 | 187.59 | 187.59 |
    | LATERAL | 191.51 | 191.51 | 191.51 |
    | NEAR_EDGE | 134.26 | 134.25 | 134.26 |
    | ABOVE | 133.02 | 133.02 | 133.02 |

27. Visual result: `4_INSIDE` reads as softly illuminated fog with its faint wisp still legible,
    instead of a dark slate room. At 122 it stays well below the exterior body (154-191), so it
    does not glow. The earlier optical-depth candidate was rejected on sight at this step - its
    anvil overhang had lost its dark mass and merged into the sky - which is the check that caught
    what the body-mask metric missed.

## COLLATERAL

28. Exterior rim: unchanged (SIDE 187.60 -> 187.59, LATERAL 191.51 -> 191.51).
29. Underside: NEAR_EDGE 134.26 -> 134.25, pockets 3.99/4.32 -> 3.99/4.33.
30. Silhouette: untouched. The term adds radiance only; density, extinction and alpha are not
    modified, so coverage and the silhouette cannot move.
31. Anvil: ABOVE 133.02 -> 133.02 to five significant figures, `localSD` 1.159 -> 1.159. This is
    the collateral the first candidate failed; the camera gate removes it by construction, because
    an anvil viewed from clear air has `camGate = 0` whatever its optical depth.
32. Tower: `4B_THROUGH_BODY` unchanged at every statistic (items 20, 21, 24, 25).

## PROGRAM SCOPE NOTE

The exploration used one live `PaDeepScatter` uniform with ten modes: 1/2/3 attribution
(ambient only, direct only, uncapped cone), 4-8 the optical-depth candidate at five onsets,
9/10 the camera-gated candidate at two strengths. **None of that is what ships.** On a KEEP the
modes collapse to a single production path (`0` off, `1` the accepted term) and the constants
bake the accepted configuration, so FINAL contains the term and no arm-selection branching.
`PRODUCTION_DEEP_SCATTER_MODE` stayed 0 for the whole investigation, so production was never
altered while the arms were being measured.

## HARNESS NOTE

A first T217M attempt reported `T132_AUTORUN_FINISHED outcome=complete frames=45145` with **zero
`t152` lines** - a clean-looking success that measured nothing. Registering a motion campaign takes
three edits, not two: the `Campaign` row in `StormCampaignRegistry`, the `ArmSet` selection in
`StormT132AutoDriver`, and **the OR-chain that gates entry to `Phase.T152_BEGIN`**. The third was
missing, so the marker routed nowhere and the run fell through to the default autorun. Verified
with `grep -c t152` before trusting any motion result thereafter.

## TEMPORAL

The route is `T217M` (new): `production_050` reference, then `deep_scatter_off` and
`deep_scatter_on` - the same temporal configuration differing only in the indirect term, so the
off/on pair is the unconfounded comparison. 2200 frames per arm. The route genuinely exercises the
gate: `cameraDensity` reaches **0.83** (mean 0.12), 23% of frames sit above the gate's lower edge
and ~20% above its upper edge, and the segment breakdown is OUTSIDE 287 / APPROACH 413 /
**ENTRY 794** / INTERIOR 706 - so 794 frames pass through exactly the cloud-entry transition where
a camera-state gate would pop.

33. Static stability: the static probe is the paired-arm capture set above; every non-buried view
    is identical below quantization (item 26).
34. Route flicker (self-referential, the unconfounded metric): **0.0023 off -> 0.0023 on**, means
    identical; max 0.1162 -> 0.1164.
35. Brightness popping: **none.** In the ENTRY segment, where the gate ramps 0 -> 1, flicker is
    **0.0017 off -> 0.0017 on** with max 0.0047 -> 0.0048. The smoothstep over 0.05-0.16 does what
    it was put there to do; the boolean `cameraInsideCloud` threshold would not have.
36. Pocket crawl: no increase - flicker is the metric that counts threshold-crossing alternation
    against the arm's own previous frame, and it did not move.
37. Ghost: `dMean` 0.0034 -> 0.0035, `dMax` 0.6751 -> 0.6762 against the `production_050`
    reference. **Reference-confounded by construction** - this candidate changes interior
    luminance legitimately, so a difference against a reference that lacks the term is expected,
    not a defect. It is reported for completeness; item 34 is the metric the decision rests on.

## PERFORMANCE

38. Production SIDE: 27.537 ms at the capture pin (spectator, resized window, capture quality -
    NOT the shipping configuration; these numbers are for A/B only).
39. Candidate SIDE: 27.607 ms.
40. Production, other matched poses: INSIDE 17.873, THROUGH 53.820, ABOVE 28.140 ms.
41. Candidate, same poses: INSIDE 17.804, THROUGH 53.574, ABOVE 28.250 ms.
42. Candidate added cost: **below the measurement floor.** The four matched-pose deltas straddle
    zero (-0.069, -0.246, +0.070, +0.110 ms; mean -0.034 ms), which is the signature of a change
    too small to resolve rather than a measured saving. Structurally it is one `exp`, one
    `smoothstep`, one `max` and a few multiply-adds per lighting sample, with **no new texture
    fetches, no new loop iterations and no new branch in the march**, so it cannot plausibly be
    expensive. Comfortably inside the <=0.20 ms preferred band.
    *Rejected measurement:* the motion route's own per-arm gpuMs gave +0.638 ms, discarded as
    noise - 6 samples spanning 3.2-10.2 ms within a single arm, because a moving camera changes
    cost far more than this term does. The same trap as T215's first perf sample.
43. Whole renderer <=10 ms: **yes.** No new fetches or iterations, and the matched-pose deltas are
    at the noise floor, so the renderer class established by T215 (max 7.93 ms) is unchanged.

## DECISION

44. **KEEP.** `PRODUCTION_DEEP_SCATTER_MODE = 1`.
45. **Mechanism CONFIRMED.** The deep interior was dark because no term in the model grows with
    optical depth (items 5-10), and adding a bounded one that does fixes it (+34.9 8-bit at
    `4_INSIDE`) without touching anything else (item 26). Both halves were measured, not assumed:
    the cause with term-zeroing arms that are additive within 2%, and the rejected alternatives
    with a negative control that came back bit-identical.
46. Production constant/change: one live uniform `PaDeepScatter` (0 off, 1 production) and the
    block in `evaluateLightingFromOpticalDepth`, plus `STORM_MS_OPTICAL_ONSET = 2.0`,
    `STORM_MS_RATE = 0.35`, `STORM_MS_STRENGTH = 0.46`, `STORM_MS_SUN_SHARE = 0.12`,
    `STORM_MS_CAMERA_GATE_LO/HI = 0.05/0.16`. No pinned constant was touched: `STORM_EROSION`,
    `STORM_CORE_FILL`, the noise-domain constants and both banked morphology modes are unchanged,
    and the term adds radiance only, so density, extinction, alpha and the silhouette cannot move.
47. **Exact next visual target: the deep interior is now bright but still STRUCTURELESS.** T216
    established that `4_INSIDE` carries `localSD` ~0.1-0.4 against 5-9 through-body, and T217
    changed its brightness, not that. The remaining question is whether a buried view should show
    any structure at all - at 14.7-block visibility through statistically homogeneous core the
    honest answer may be no, in which case the next target is not the interior at all but the
    **stair-stepped puff edges in the mid-field** (march step size), noted and deferred in T216.
    Attribute before building either.

## HONEST LIMITS

- **The candidate improves one pathological pose.** Five of six captured views are unchanged below
  quantization - that is the point, but it also means T217 buys a narrow win. A player only sees it
  while buried in dense cloud.
- **The gate is camera state.** That is what makes the localization exact, and it is also the one
  property that could not be validated in stills. It was validated on 794 ENTRY frames (item 35);
  it has not been validated against a camera that oscillates across the gate edge faster than the
  route does.
- **The onset is no longer load-bearing** and should not be read as the protection mechanism. Two
  quantities could have localized this; only one works, and the report keeps the failed one
  (item 15) because the reason it fails is the most transferable thing here.
- **Absolute timing numbers in items 38-41 are capture-pin values**, not shipping frame times. Only
  the matched-pose deltas are meaningful.

## VALIDATION

48. `campaignGate` PASS - before client work (`gate-t217.log`), before each arm run
    (`gate-t217b/c/d/e/f/g.log`) and after the final collapse (`gate-t217h.log`).
49. `check build` PASS (`check-build-t217.log`).
50. Morphology sandbox PASS as part of `check`.
51. Program scope: the ten exploratory modes are gone. The shipped shader has **one**
    `PaDeepScatter` uniform and **one** `if (PaDeepScatter != 0)` branch; no attribution mode, no
    onset bracket and no strength variant survives into FINAL.
52. Markers: `run/t132-autorun-exit.txt` armed throughout; exactly one campaign marker at a time
    (`run/t211-captures.txt` for the static runs, swapped for `run/t217-deep-motion.txt` for the
    route). `T217M` is registered in `StormCampaignRegistry` above the sweeps.
53. `git status`: `PAchangelog.md` left modified and uncommitted as required. No validation
    evidence, campaign log or unrelated log was deleted.
54. Committed on `quality/t217-deep-interior-lighting`. **Not merged.**
