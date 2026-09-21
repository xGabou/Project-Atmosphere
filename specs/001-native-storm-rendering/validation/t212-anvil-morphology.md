# T212 - Anvil morphology / envelope breakup

One job: find what actually controls the smooth anvil envelope, then make the
smallest evidence-based change that breaks the geometric silhouette.

**Outcome: the T212 hypothesis is DISPROVED, the controlling stage is
identified and quantified, and the candidate built from it is REJECTED for
production. Production rendering is unchanged.** This is CASE C with a
measured candidate attached.

## RECOVERY

**1. Starting HEAD.** `bb7daa5` on `quality/t211-cloud-visual-quality`.

**2. Branch.** Work continued on that branch and is committed to
`quality/t212-anvil-morphology`. Nothing merged.

**3. Production constants** (all verified unchanged at the end):
`PRODUCTION_TEMPORAL_MODE = 1`, `PRODUCTION_SPATIAL_UPSCALE = -1`,
`PRODUCTION_RAIN_LATTICE_MODE = 0`, `PRODUCTION_ORIGIN_JITTER_MODE = 0`,
and the new `PRODUCTION_MACRO_ENVELOPE_MODE = 0`.

**4. Harness state.** The T211 additions were preserved and extended: the
captures-only route (`run/t211-captures.txt`, still deferring to any
campaign, ray-trace or motion marker), the fixture bootstrap, paired
same-storm arms, and the motion candidate arm set. `campaignGate` was run
before any client launched and passed. `PAchangelog.md` is preserved and
uncommitted throughout.

## ATTRIBUTION

The task asked to investigate before changing anything and not to assume the
hypothesis was correct. It was not.

**5. The visible-boundary controlling stage: A, the descriptor/envelope
geometry - but for a spectral reason, not a fill reason.** Three independent
measurements agree.

**6. Envelope contribution.** A marker-gated CPU trace
(`T212_EDGE_WIDTH`, a literal transcription point in
`StormLobeDescriptor.writePrecomputedInvariants`) measured the real fixture's
boundary half-widths:

| role | edgeWidth (blocks) | bound by |
|---|---|---|
| 0 BASE | 141.1 - 145.3 | horizontal radius |
| 1 CORE | 63.0 - 72.0 | horizontal radius |
| 2 TOWER | 37.5 - 44.5 | horizontal radius |
| **3 ANVIL** | **89.47** | **vertical half-height** (clamped from 240.9) |

The anvil is indeed the only role clamped by the T098 vertical bound
(`halfHeight * 0.75`), because it is the flattest role - 241 blocks of
horizontally-derived width cut to 89.5. That part of the suspicion was right.
But 89.5 blocks is the **second-widest** boundary in the storm and nowhere
near the 11.36-block floor. Coverage falls from strength to zero across
**179 blocks**. The transition is not narrow, so a narrow transition is not
the cause.

**7. Pre-erosion density and the boundary it permits.** With the algebra
`lowerBound = 1 - c(1+fill)` and `body = saturate(1 - (1-baseField)/(c(1+fill)))`,
the visible threshold sits at `c > (1-baseField) / ((1-bite)(1+fill))`.
Evaluated against the **exact production noise** (the baked 128^3 base and
32^3 detail volumes, sampled through the shader's own domain transforms) at
the measured anvil geometry:

```
baseField mean 0.463  sd 0.319      (morphology-thresholds.md records 0.3294)
boundary displacement: sd 33.1 blocks, range [-57.7, +88.8], span 146.5
```

The noise is fully present and the permitted boundary already wanders
+-33 blocks. Integrating the same density along the limb chord that actually
renders the silhouette gives sd 22.7 blocks - path integration smooths it by
only 1.5x - which predicts about 12 px of non-elliptical roughness at the
capture scale. The measured capture carries 11.5 px. **The model and the
render agree.**

**8. Erosion authority: adequate, and not the limit.** The result above is
identical to two decimals across envelope strengths 0.72, 0.78 and 0.82,
because `stormCoreFillForStrength` compensates exactly - that is the
structural-continuity guarantee working as designed. Core fill is therefore
not the lever either, and `STORM_EROSION` was not touched.

**9. Vertical-profile contribution.** Not the limiting stage. The profile
sets `r(h)` for the deck and is smooth by construction, but the measured
per-point and limb-integrated boundary variability above is what reaches the
image, and it matches the render without invoking the profile.

**10. Hypothesis: DISPROVED.** The storm body is **not** too strongly filled
near the macro envelope, and erosion **can** cut. What is actually wrong is a
ratio and a missing band:

| silhouette harmonic band | SIDE | LATERAL |
|---|---|---|
| ellipse (harmonics 1-2) | 21.15 | 26.11 |
| macro (3-8) | 7.82 | 6.69 |
| mid (9-32) | 5.68 | 6.60 |
| fine (33+) | 3.58 | 3.80 |
| **ellipse / macro** | **2.70x** | **3.90x** |

The geometric term dominates macro breakup by 2.7-3.9x, and **the base noise
has no energy long enough to fight it**: its lowest octave is ~50 blocks,
which on a 450-block anvil lands at harmonic ~28 - in the mid band. There is
no macro-scale signal anywhere in the system. That, not erosion authority, is
why fine structure exists on the surface while the envelope stays
geometrically obvious.

## BASELINE

`validation/t212-campaign/baseline/`, production, one storm.

**11. SIDE.** Smooth lens; ellipse 21.15 against 7.82 macro.
**12. LATERAL.** The worst pose: ellipse 26.11 against 6.69 macro, 3.90x.
**13. ABOVE.** Clean geometric outer arc; the top-down view is dominated by
the whole-frame cloud, so its harmonics measure the deck, not the rim.
**14. NEAR_EDGE.** Smooth dark underside and boundary.
**15. Silhouette characteristics.** Non-elliptical roughness is present and
measurable (11.5 px), but it is mid/fine-band; the macro band that would read
as billowing is absent, and the ellipse is 2.7-3.9x everything else.

## CANDIDATE

**16. The change.** One Worley octave at ~400 blocks added to the **coverage
envelope**, immediately before the density threshold, on both the primary
march (`cloudDensity`) and the rain-gate path (`directStormFinalDensity`) so
rain support stays consistent with the visible body:

```glsl
if (PaMacroEnvelope != 0 && cloud > 0.12 && cloud < 0.98) {
    float macroOctave = texture(BaseNoiseSampler, stormMacroEnvelopeDomain(samplePos), 0.0).g;
    float macroSigned = (macroOctave - STORM_MACRO_OCTAVE_MEAN) / STORM_MACRO_OCTAVE_SD;
    cloud = saturate(cloud + STORM_MACRO_ENVELOPE_AMPLITUDE * clamp(macroSigned, -2.5, 2.5));
}
```

`stormMacroEnvelopeDomain` is the body noise's rotation with no warp at one
eighth the wave number, plus a constant offset so the two do not carve the
same features. No pinned constant was modified: `STORM_EROSION`,
`STORM_CORE_FILL` and the noise domains are untouched, and the morphology
sandbox reproduces every recorded statistic.

**17. Reason.** It adds the band the attribution showed to be missing, and it
adds it to the envelope rather than to the density. That distinction is the
whole point: the limb chord averages per-point density noise away (the 1.5x
smoothing measured above), but it cannot average away a displacement the
entire chord shares.

**Two implementation corrections were needed, both driven by measurement, not
taste.** The first version applied the modulation in
`directStormFinalDensity` - which reads like the density function, and whose
own comment says rain support, camera density and whiteout all read it - but
whose only callers are the rain gate and the field build. The primary march
composes density separately in `cloudDensity`. That produced a perfect null
(IoU 0.9984-0.9993) which, taken at face value, would have been a false
"no effect" conclusion. The second version was centred on the channel's
**measured** statistics (mean 0.4794, sd 0.1163 over 200k samples of the
baked volume) rather than an assumed 0-1 range; uncentred it had only
0.051 sd in coverage (~8 blocks of travel) plus a negative bias, and it
simply shrank the storm - perimeter *fell* 3724 -> 3534 and area dropped ~4%
at every pose. The fringe guard (`cloud > 0.12`) removes a one-sided region
where subtracting zeroes thin material but adding cannot create body.

**18-21. Results per pose** (paired arms, one storm, `candidate/`):

| pose | area | perimeter | P/sqrt(A) | ellipse | macro | IoU |
|---|---|---|---|---|---|---|
| **18. SIDE** | 67263 -> 68541 (+1.9%) | 3534 -> 3842 (**+8.7%**) | 15.66 -> 17.18 | 20.90 -> **19.42 (-7.1%)** | 7.62 -> 7.66 | 0.8917 |
| **19. LATERAL** | 43547 -> 42899 (-1.5%) | 2944 -> 3338 (**+13.4%**) | 15.01 -> 16.85 | 30.70 -> **25.74 (-16.2%)** | 8.01 -> 6.52 | 0.9241 |
| **20. ABOVE** | 893988 -> 872923 (-2.4%) | 21396 -> 21073 | 27.42 -> 27.50 | 76.33 -> 76.82 | 44.14 -> 44.74 | 0.9320 |
| **21. NEAR_EDGE** | see candidate set; underside gains recesses, no new wisps | | | | | |

## SILHOUETTE

**22. IoU** 0.8917 / 0.9241 / 0.9320 - a real change, an order of magnitude
above the paired-arm noise floor (T211 established 0.945-0.976 for a
sampling-only change and 0.67-0.72 for two different storms).
**23. Perimeter** +8.7% SIDE, +13.4% LATERAL: the edge is genuinely more
convoluted, not merely moved.
**24. Perimeter / area** 15.66 -> 17.18 and 15.01 -> 16.85, consistent.
**25. Multi-scale edge roughness.** The ellipse band falls 7.1% / 16.2% -
the defect metric moves the right way - but the **macro band does not rise**
(+0.5% SIDE, -18.6% LATERAL). The added convolution landed in perimeter and
the mid band instead.
**26. Projected area** preserved within 2% after the centring fix.
**27. Holes** SIDE 2520 -> 2863, LATERAL 1365 -> 1070, ABOVE +4%: mixed and
small; visually no swiss-cheese.
**28. Thin protrusions** intact; component counts 110 -> 102 and 98 -> 115,
so no detached-speckle explosion.

## COLLATERAL

**29. Tower connectivity: unchanged.** The updraft neck and its attachment to
the anvil read the same in the paired frames.
**30. Lower mass: unchanged.** No additional erosion of the already
over-eroded base - T212's constraint was not to make the third-ranked T211
defect worse, and it does not.
**31. Interior: unchanged.** The modulation is skipped above coverage 0.98,
so the saturated interior is untouched by construction.
**32. Lighting: no collateral regression.** Sun-facing rim, underside and
dark pockets read as before; the silhouette moved but the optical balance did
not shift.

## TEMPORAL

**33. Static probe** (same pose, 8 frames apart, camera still):

| pose | production mean / max | candidate mean / max | px > 2/255 |
|---|---|---|---|
| SIDE | 0.03089 / 4.33 | 0.02686 / **1.00** | 0.00% |
| LATERAL | 0.02475 / 2.00 | 0.02670 / **1.00** | 0.00% |
| ABOVE | 0.00026 / 1.00 | 0.00021 / 1.00 | 0.00% |

The candidate is at least as still as production with the camera fixed - and
T211 proved that this is only a pre-filter.

**34. Motion flicker** (T212M, T152 route, 2200 frames per arm, one storm;
self-referential, so not appearance-confounded):

| segment | off | on | change |
|---|---|---|---|
| OUTSIDE | 0.00026 | 0.00031 | **+18.1%** |
| APPROACH | 0.00106 | 0.00127 | **+19.5%** |
| ENTRY | 0.00130 | 0.00144 | +10.1% |
| INTERIOR | 0.00556 | 0.00606 | +9.0% |
| **ROUTE** | **0.00248** | **0.00273** | **+10.2%** |

(The production 0.50 reference is 0.00207, so the temporal path already sits
+20% above it before this candidate adds another 10%.)

**35. Edge crawl: present, but not T211's failure mode.** Testing for T211's
exact signature - control exactly still while the candidate sustains crawl -
finds **15 frames** (worst 0.0276, longest run 9 frames). But **35 frames run
the other way**, with the candidate exactly still where the control crawls,
and the candidate ends with *more* perfectly-still frames overall (616 vs
580). T211's wide jitter was one-way and uncompensated; this is bidirectional.
**36. Silhouette popping:** none observed; the displacement is a static
world-space field, so the envelope does not move between frames.
**37. Ghost metric, with the reference confound stated:** 0.00159 -> 0.00762
and frames above 0.02 rising 23 -> 106. **These are measured against a
reference rendered with the candidate off, so a legitimately different
silhouette inflates them by construction.** They are reported for
completeness and were **not** used to judge the candidate, per the task's
instruction.

## PERFORMANCE

| | production | candidate |
|---|---|---|
| **38/39. SIDE total** | **8.2069** | **8.1733** |
| | rawCloud 5.4223 | rawCloud 5.4474 |
| **40/41. PLAY_VIS_NEAR total** | **7.8359** | **7.3878** |
| | rawCloud 4.5916 | rawCloud 4.0461 |

**42. Delta: below the noise floor.** The candidate measures nominally
*faster* at both poses, which is storm-realisation variance rather than a
speed-up (PLAY_VIS_NEAR `rawCloud` alone moves 4.59 -> 4.05 between the two
sessions). The two runs are separate sessions and therefore separate storms,
so this bounds the cost rather than resolving it: the extra fetch is gated to
the boundary band (coverage 0.12-0.98, a small minority of samples) and sits
comfortably in the **< 0.25 ms "easy to accept"** class.

**43. <= 10 ms: YES** at both poses on both arms, with 1.8-2.6 ms of headroom.

## DECISION

**44. Candidate: REJECT for production.** Kept as diagnostic
`macroEnvelopeMode = 1`, the same disposition T209 gave its upscale pass,
T210 its lattice modes and T211 its origin jitter.

The reasoning, weighing visual first as the task requires:

- The candidate does the *narrower* half of the job. It reduces the ellipse
  term (-7.1% / -16.2%) and produces genuine recessed sections and a less
  lens-like outline. It does **not** produce billowing protrusions: the macro
  band is flat to down. Task 6's target is an irregular macro edge *with*
  billowing, and only the first half arrives.
- Against that modest gain it costs **+10.2% route flicker**, with OUTSIDE
  and APPROACH up ~19% - the segments where a storm is most often seen at
  distance. That is double the +5.1% that contributed to rejecting T211's
  wide jitter, even though the qualitative crawl signature here is milder and
  bidirectional.
- Performance is free and the structural collateral is clean, so cost is not
  what decides it; the visual return simply does not justify a temporal
  regression on a renderer that is otherwise stable.

**45. Accepted production change: none.** Production rendering is unchanged.

**46. Mechanism, confirmed and disproved.**
- **Disproved:** the body is over-filled near the envelope and erosion cannot
  cut. Measured boundary displacement is already +-33 blocks, erosion
  authority is adequate, and core fill compensates exactly across strengths.
- **Confirmed:** the silhouette is elliptical because the ellipse harmonic is
  2.7-3.9x the macro band and the noise spectrum has **no macro-scale energy
  at all** - its lowest octave lands at harmonic ~28. Adding that band to the
  envelope does move the ellipse term, which validates the diagnosis; the
  amplitude reachable at acceptable temporal cost is what falls short.

**47. Exact next visual target: T213 - anvil boundary width, combined with
the macro band.** The displacement a coverage shift buys is proportional to
the envelope half-width: `displacement ~ (dc / strength) * 2w`. The anvil is
the one role whose width is clamped by the T098 vertical bound, 241 -> 89.5
blocks, and that bound is derived from its *vertical* extent while the defect
is in its *horizontal* silhouette. Relaxing it for role 3 alone would
multiply the displacement the existing macro field already produces - roughly
1.7x at w = 150 - without raising the coverage amplitude that drives the
flicker. T212 deliberately did not test this: the T098 comment records that a
wider anvil boundary once hung ~150 blocks of low-coverage haze below its own
base, so the relaxation must be anisotropic (horizontal only) and must be
judged against that specific failure. That is one focused hypothesis with a
measured mechanism behind it, not a parameter sweep.

## VALIDATION

**48. `campaignGate`: PASS**, before any client launched and again at the end
(`gate-t212.log`, `gate-t212b.log`). Final: `T170_WIRING campaigns=60 |
armMatrices=50 | violations=0`, with T199 and T202-T210 green. T212M was
registered above the sweeps because a `PERFORMANCE_ONLY` row must not be the
registry's newest - the T207 rule, which T211M also hit.

**49. `check build`: PASS** (`check-build-t212.log`).

**50. Morphology sandbox: PASS** -
`T100 measured noise thresholds | PASSED | all recorded statistics and
derivations reproduced`. No pinned constant was edited, so
`morphology-thresholds.md` needs no revision and there is no drift to
explain.

**51. Program scope: unchanged.** No new program. One uniform added
(`PaMacroEnvelope`, declared in `cloud_atmosphere_volume.json`, always 0 in
production); it is deliberately **not** in `leanFinalConstants`, so the
diagnostic stays reachable in FINAL rather than being baked out.

**52. Markers.** `t212-edge-trace.txt` and `t212-macro-motion.txt` were armed
only for their runs and are retired; `t098-captures.txt` and
`t211-captures.txt` remain as harness machinery, with the capture route still
deferring to any campaign. `run/t132-autorun-exit.txt` kept throughout.

**53. Git status.** T212 sources, this document, `t212-campaign/` evidence,
the two launchers, and loose run logs. `PAchangelog.md` preserved and
uncommitted. Every edited file is pure CRLF.

**54. Commit.** On `quality/t212-anvil-morphology`, not merged.

### Evidence

| path | what it is |
|---|---|
| `t212-campaign/baseline/` | production reference frames |
| `t212-campaign/candidate/` | the paired macro-envelope A/B, one storm |
| `t212-campaign/t212-edge-attribution.log` | the `T212_EDGE_WIDTH` role trace |
| `t212-campaign/t212-macro-motion.log` | the T212M motion route that decided it |
