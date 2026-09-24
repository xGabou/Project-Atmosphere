# T222 - Pre-Erosion Shadow Form

**The question:** can shadowed lighting use pre-erosion form to show large billows without
importing the fine vein network that made T221's local-density candidate fail?

**The answer: no - the premise does not hold. CASE C, DISPROVE.** Measured directly, the
pre-erosion body carries the **same** vein structure as the post-erosion density T221 rejected:
the same correlation with production's veins, the same thin-line energy, the same billow-to-vein
ratio, the same fine-band share of variance - to within 0.1 in ratio at every pose. Erosion is too
small a term to be what separates them: it moves density by `STORM_EROSION x SD(detailFbm) =
0.44 x 0.074 = 0.033`, against ~0.15 of visible stage variation. **The veins are in the base
field, and "pre-erosion" still contains the base field.** T221's elimination (base field) was
right; the inference drawn from it here - that pre-erosion means large-scale only - was not.

Task 4 builds a candidate only if Task 1 confirms the hypothesis. It does not, so there is **no
candidate, no motion run and no production change.** Three new composition-stage views remain as
instruments.

## RECOVERY

1. Starting HEAD: `aa61733` (T221, "the puff cells are lighting, not shape; the one candidate is
   rejected").
2. Branch: **`quality/t217-deep-interior-lighting`**. The brief names `quality/t221-lighting-cells`;
   no such branch exists - T218-T221 were all committed here - so this task continues here rather
   than creating one the brief assumed.
3. Production state, verified and unchanged at exit: `PRODUCTION_TEMPORAL_MODE=1`,
   `PRODUCTION_SPATIAL_UPSCALE=-1`, `PRODUCTION_RAIN_LATTICE_MODE=0`,
   `PRODUCTION_ORIGIN_JITTER_MODE=0`, `PRODUCTION_MACRO_ENVELOPE_MODE=3`,
   `PRODUCTION_LOWER_BODY_MODE=1`, `PRODUCTION_DEEP_SCATTER_MODE=1`, field bits 43.
   `PAchangelog.md` the only working-tree change.
4. Harness: `campaignGate` PASS before client work (`gate-t222.log`). One run, fixture
   `37c75cd2` (radius 665.6), all arms adjacent per pose, every lighting-view arm confirmed on the
   monolith and every production arm on lean FINAL from the CONTROLS audit.
   **The fixture decayed at `T222_PRE_NEAREDGE`; NEAR_EDGE is excluded from that frame on and not
   salvaged**, per the brief. SIDE, LOWSIDE and LATERAL are complete.

### The instrument

The storm density is composed as: the macro coverage envelope (with the T213 macro and T215 lower-
body terms) -> `stormBody(envelope, strength, baseField)`, the **pre-erosion** body -> subtractive
detail erosion, the **post-erosion** body -> smooth height/material and `DensityMul` factors. All
three stages exist inside `cloudDensity` already, so exposing them costs no evaluation.

They are recorded **together, with the position they belong to, in one `cloudDensity` call**, so
they cannot be torn across calls; a stage view checks the position before trusting them, because
the light cone calls `cloudDensity` again and overwrites them. A view that cannot match its sample
paints magenta. Measured leakage: **0.0%** - every lit pixel in every region matched its own
stages.

New views on T136's `PaDiagnosticLightingMode`: **4** pre-erosion body, **5** post-erosion body,
**6** macro envelope - beside T221's **2** light path and **3** final local density.

## ATTRIBUTION (Task 1)

Regions split at the neck (T221's rule). Bands: **vein** = Hessian ridge response at sigma 1.5 px;
**billow** = band-pass between Gaussian sigma 4 and 24 px.

5. **Pre-erosion density** (stage value, 0-1, dome / lower mass):

| pose | dome mean | dome SD | dome p05 / p50 / p95 | lower mean | lower SD |
|---|---|---|---|---|---|
| SIDE | 0.687 | 0.148 | 0.482 / 0.667 / **0.977** | 0.756 | 0.127 |
| LOWSIDE | 0.696 | 0.151 | 0.463 / 0.690 / **0.966** | 0.730 | 0.140 |
| LATERAL | 0.730 | 0.160 | 0.480 / 0.726 / **0.988** | 0.761 | 0.126 |

   The upper tail sits against saturation (p95 0.97-0.99): `stormBody` clamps the core at 1.

6. **Post-erosion density**: dome mean **0.465 / 0.472 / 0.506**, SD **0.147 / 0.149 / 0.159**;
   lower mean 0.532 / 0.504 / 0.537, SD 0.126 / 0.137 / 0.126. Erosion shifts the **mean** by
   ~0.22 - its mean bite - and leaves the **SD** within 0.002 of the pre-erosion value.

7. **Pre-erosion vs production's veins** (ridge-map correlation, lower mass - where the veins
   are): **+0.197 / +0.772 / +0.227** (SIDE / LOWSIDE / LATERAL).
8. **Post-erosion vs production's veins**: **+0.202 / +0.811 / +0.218**. Indistinguishable from
   item 7. The macro envelope: +0.078 / +0.398 / +0.277.

   The decisive measurement is each stage's **own** structure - what a candidate fed by it would
   import:

| region | stage | billow / vein ratio | fine-band share of variance |
|---|---|---|---|
| SIDE dome | local density (T221's input) | 3.79 | 0.9% |
| SIDE dome | **pre-erosion** | **3.85** | **0.8%** |
| SIDE dome | post-erosion | 3.78 | 0.9% |
| SIDE dome | macro envelope | 4.03 | 0.6% |
| LOWSIDE dome | local density | 2.83 | 1.4% |
| LOWSIDE dome | **pre-erosion** | **2.87** | **1.4%** |
| LOWSIDE dome | post-erosion | 2.84 | 1.5% |
| LOWSIDE dome | macro envelope | 3.21 | 0.8% |
| LATERAL dome | local density | 2.22 | 5.4% |
| LATERAL dome | **pre-erosion** | **2.31** | **4.9%** |
| LATERAL dome | post-erosion | 2.30 | 5.1% |
| LATERAL dome | macro envelope | 1.98 | 2.8% |

   **Pre-erosion density is not a cleaner signal than the one T221 rejected.** Its billow-to-vein
   ratio is within 0.07 of the local density's at every pose, and its fine-band share is the same.
   The lower mass tells the same story (SIDE 1.79 vs 1.76, LOWSIDE 2.14 vs 2.11, LATERAL 1.51 vs
   1.40). The stage sheet (`t222-campaign/T222_SIDE_stage_sheet_...png`) shows it without numbers:
   the pre- and post-erosion views are near-copies, with the same thin dark lines along the lower
   dome.

9. **Pre-erosion vs macro form** (billow-band correlation with production, dome):
   **-0.298 / -0.300 / -0.295**. Consistent and negative - denser material renders darker, the
   self-shadowing signature - so the pre-erosion body *does* carry the dome's large form. It is
   not uniquely so: post-erosion -0.185 / -0.177 / -0.190, local density -0.214 / -0.235 /
   -0.244. The macro envelope does not: **+0.041 / +0.113 / -0.011**.

10. **Hypothesis: DISPROVED.** The hypothesis had two parts. "Pre-erosion density carries the
    large form" holds (item 9). "...without the fine erosion veins" does not (items 7, 8 and the
    table): the veins are in the base field, which the pre-erosion body is built from, and erosion
    - at an SD contribution of 0.033 against ~0.15 - was never what put them there. The one stage
    without the veins, the macro envelope, does not carry the dome's form. **No density stage is
    both.**

## BASELINE (Task 3)

Production, per region, SIDE / LOWSIDE / LATERAL:

| | flat | luminance SD | local contrast | billow | vein (norm. / abs) | sparsity | light-path grad corr |
|---|---|---|---|---|---|---|---|
| **DOME** | 71.1 / 62.9 / 44.9% | 13.49 / 8.67 / 9.80 | 2.42 / 2.62 / 3.93 | 5.67 / 5.68 / 6.70 | 0.080 / 0.110 / 0.160 (1.08 / 0.95 / 1.57) | 0.895 / 0.844 / 0.814 | 0.900 / 0.906 / 0.935 |
| **LOWER MASS** | 28.0 / 21.4 / 7.9% | 18.49 / 8.58 / 20.07 | 7.33 / 4.24 / 10.08 | 11.69 / 8.39 / 13.72 | 0.350 / 0.247 / 0.406 (6.47 / 2.12 / 8.15) | 0.820 / 0.569 / 0.755 | 0.960 / 0.905 / 0.950 |

11. Dome flat fraction: **71.1 / 62.9 / 44.9%**.
12. Dome local contrast: **2.42 / 2.62 / 3.93**.
13. Dome billow score: **5.67 / 5.68 / 6.70**.
14. Dome vein score: **0.080 / 0.110 / 0.160** (absolute 1.08 / 0.95 / 1.57 levels).
15. Lower-mass flat fraction: **28.0 / 21.4 / 7.9%**.
16. Lower-mass local contrast: **7.33 / 4.24 / 10.08**.
17. Lower-mass billow score: **11.69 / 8.39 / 13.72**.
18. Lower-mass vein score: **0.350 / 0.247 / 0.406** (absolute 6.47 / 2.12 / 8.15).

These reproduce T221's split on a fourth storm: the dome flat and on the light path, the lower
mass lit and veined.

## CANDIDATE, RESULTS, INVARIANCE, TEMPORAL, PERFORMANCE

**Not built.** The brief conditions Task 4 on Task 1 confirming the hypothesis; Task 1 disproved
it. Building the pre-erosion candidate anyway would be building T221's rejected candidate with an
input measured to be statistically the same.

19. Exact equation: none.
20. Centering / scaling: none applied. The measured statistics a candidate would have been centred
    on are items 5-6 (dome pre-erosion mean 0.687-0.730, SD 0.148-0.160, p95 at saturation) -
    recorded so the next task does not assume a range.
21. Shadow localization: none.
22. Bounds: none.
23. Dome flat-fraction delta: not applicable (no candidate).
24. Dome local-contrast delta: not applicable.
25. Dome billow delta: not applicable.
26. Dome vein delta: not applicable.
27. Dome visual result: not applicable - the stage sheet is the visual evidence for the decision.
28. Lower-mass flat-fraction delta: not applicable.
29. Lower-mass billow delta: not applicable.
30. Lower-mass vein delta: not applicable.
31. Lower-mass visual result: not applicable.
32. Silhouette IoU: not applicable to a candidate; the production repeat P0B reproduces P0 exactly
    at every valid pose.
33. Alpha / area: unchanged - nothing that could move them was built. The stage writes are gated
    behind the stage views and never run in production.
34. Components: unchanged.
35. Route flicker: **not run** - there is no candidate to move; the brief runs motion only after
    stills pass.
36. Billow crawl: not run, same reason.
37. Brightness popping: not run, same reason.
38. Vein crawl: not run, same reason.
39. Ghost: not run, same reason.
40. Production SIDE: capture pin **34.05-38.91 ms** across the P0 temporal-pair frames, **35.72
    ms** on the repeat.
41. Candidate SIDE: none.
42. Production PVN: not in this shot set; production is unchanged, so T218's gameplay-phase
    figure stands (`gpuMs` p50 1.541).
43. Candidate PVN: none.
44. Delta: **zero by construction** - the only production-reachable change is three uniform
    branches around assignments that execute only while a stage view is selected.
45. Whole renderer <= 10 ms: **yes, unchanged.**

## DECISION

46. **DISPROVE (CASE C).** No candidate. No production constant changes.

47. **Mechanism result.** The dome's shadow floor and the lower mass's veins remain exactly as T221
    attributed them. What T222 adds is where the veins live and what that forbids:

    - **The veins are a property of the base field** - they are equally present before and after
      erosion, and erosion's whole contribution to density variation is ~0.033 against ~0.15.
    - **Every density stage that carries the dome's form also carries the veins** (pre-erosion,
      post-erosion, final local density: billow-to-vein ratios within 0.1 of each other).
    - **The one stage without veins - the macro envelope - does not carry the dome's form**
      (billow correlation with production +0.04 / +0.11 / -0.01).

    So "feed shadowed ambient a density stage" cannot work as a family: no stage that exists
    inside `cloudDensity` today separates form from veins. Any fix needs a signal that does not
    exist yet.

48. Production constant/change: **none.**

49. **Exact next task.** Two routes follow from the measurements; they should be chosen between,
    not combined:

    - **Build the missing signal: a LOW-PASS of the base field.** The dome's form lives in the
      billow band (sigma 4-24 px); the veins in the thin-line band. The base noise is a 3-D
      texture, so a mip-biased fetch of the base carrier at the sample - a few mip levels up - is
      a low-passed base field at the cost of **one extra texture fetch per lit sample**, not a
      density evaluation. It is a new signal, not a density stage, so it needs its own attribution
      first: measure its billow-to-vein ratio against items 5-9 **before** any candidate uses it.
      This is the direct continuation, and the one to try first.
    - **Or: T223, the camera-altitude light reach**, which the brief already queued. T222 did not
      touch it. For the record: this task's poses all sit inside the slab, so every frame ran the
      102-block, 4-tap cone; the dome's shadow floor at od 6.7-7.9 is deep enough that a longer
      reach would only deepen it, whereas the lower mass - lit because 102 blocks never reaches the
      anvil - is where the reach decides what the image is.

## VALIDATION

50. `campaignGate` PASS: before client work (`gate-t222.log`), with the arms (`gate-t222b.log`),
    after the collapse (`gate-t222c.log`).
51. `check build` PASS (`check-build-t222.log`).
52. Morphology sandbox PASS - 10 `PHASE4S_RESULT ... PASSED`, standalone after the collapse.
53. **Program scope.** The T222 shot block and its per-shot arm plumbing are removed (the capture
    driver is byte-identical to `aa61733`). Kept, as instruments in T221's pattern: views 4-6 on
    `PaDiagnosticLightingMode`, the stage globals with their position, and the mode range raised to
    6. **Production never runs the stage writes** - they sit behind `PaDiagnosticLightingMode >= 4`,
    a uniform branch that is false in production - and the per-sample snapshot a candidate would
    have needed was removed with the candidate that was never built.
54. Markers: `run/t132-autorun-exit.txt` armed throughout; `run/t211-captures.txt` the only
    campaign marker, retired. No motion marker created.
55. `git status`: `PAchangelog.md` left modified and uncommitted as required. No evidence, campaign
    log or unrelated log deleted.
56. Committed on `quality/t217-deep-interior-lighting`. **Not merged.**

## HONEST LIMITS

- **One storm, three poses.** NEAR_EDGE was lost to fixture decay and not re-run, because the
  answer did not depend on it: the three valid poses agree to within 0.1 in every ratio that
  decides the case. A re-run is the right call if a future task needs NEAR_EDGE's numbers.
- **The stage views are transmittance-weighted along the view ray**, like every lighting view: a
  pixel shows the alpha-weighted mix of its samples' stage values. That is exactly the quantity a
  lighting candidate would have produced, so it is the right thing to measure for this question -
  but it is not a single-sample field slice.
- **The vein and billow bands are defined in screen pixels** (sigma 1.5 and 4-24 px) at one capture
  scale. They separate the two structures here; a pose at very different range would put the same
  world features in different bands.
- **The low-pass route in item 49 is a proposal, not a measurement.** Whether a mip-biased base
  fetch actually removes the thin-line band without flattening the billows is the first thing the
  next task has to show.
