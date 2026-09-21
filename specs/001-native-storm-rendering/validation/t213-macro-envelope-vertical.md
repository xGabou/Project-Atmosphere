# T213 - Macro-envelope vertical displacement

One question: does the anvil's vertical envelope / half-width explain why the
correct low-frequency macro band failed to produce silhouette billowing in
T212?

**Answer: YES, in two measurable steps - and the fix that follows from it is
KEPT.** The vertical clamp is a real 2.69x multiplier on displacement, and the
coverage channel T212 modulated was a second, hidden bottleneck that clipped
the compensation into erosion. Moving the same displacement into the
envelope's own falloff parameter resolves both. **This is the first banked
visual change of the T211-T213 line.**

## RECOVERY

**1. Starting HEAD.** `ce95bd6` on `quality/t212-anvil-morphology`.

**2. Branch.** Work committed to `quality/t213-macro-envelope-vertical`. Not
merged.

**3. Production state at entry.** `PRODUCTION_TEMPORAL_MODE = 1`,
`PRODUCTION_SPATIAL_UPSCALE = -1`, `PRODUCTION_RAIN_LATTICE_MODE = 0`,
`PRODUCTION_ORIGIN_JITTER_MODE = 0`, `PRODUCTION_MACRO_ENVELOPE_MODE = 0`.
`PAchangelog.md` preserved and uncommitted throughout.

**4. Harness state.** T211/T212 machinery reused unchanged: the captures-only
route (still deferring to any campaign marker), the fixture bootstrap, paired
same-storm arms, the motion candidate arm set, and the silhouette
harmonic-decomposition metrics. `campaignGate` was run before any client
launched and passed.

## GEOMETRY

Measured from the real fixture descriptors via the T212 `T212_EDGE_WIDTH`
trace - the CPU transcription point in
`StormLobeDescriptor.writePrecomputedInvariants`.

| role | **5. horizontal half-width** | **6. vertical half-height** | vertical bound (x0.75) | actual edgeWidth | bound by |
|---|---|---|---|---|---|
| 0 BASE | 141.1 - 145.3 | 223.8 | 167.8 | 141.1 - 145.3 | horizontal |
| 1 CORE | 63.0 - 72.0 | 156.4 - 164.2 | 117.3 - 123.1 | 63.0 - 72.0 | horizontal |
| 2 TOWER | 37.5 - 44.5 | 181.7 - 197.3 | 136.3 - 148.0 | 37.5 - 44.5 | horizontal |
| **3 ANVIL** | **240.9 - 242.4** | **119.3** | **89.475** | **89.475** | **VERTICAL** |

**7. The clamp relationship.** The anvil is the **only** role bound by the
T098 vertical limit, and it is bound hard: 240.9 blocks of horizontally
derived width become 89.475, because it is the flattest role (major radius
450 against a half-height of 119). Every other role keeps its horizontal
width. The clamp exists for a reason - the T098 comment records that an
unclamped anvil boundary hung ~150 blocks of low-coverage haze below its own
base - so it was not removed here.

**8. Baseline effective displacement.** T212's algebra, re-verified:
`displacement ~ 0.78 x edgeWidth` for +-1 sd of base noise, and for a coverage
shift, `displacement ~ (dc / strength) x 2w`. Measured empirically at
0.16 coverage -> ~25 blocks at the anvil.

**9. Vertical multiplier: 2.69x.** Displacement scales linearly with the
envelope half-width, so the clamp attenuates every macro displacement at the
deck by exactly 240.9 / 89.475 = 2.69, while leaving all other roles at 1.0.

**10. Hypothesis quantified: YES.** The vertical clamp is a real, measured
multiplier acting precisely on the deck whose silhouette reads as an ellipse.

## MACRO BAND

**11/12. Measured channel statistics** (200k samples of the baked 128^3 base
volume, sampled as the shader samples it): channel `g` (Worley period 8, the
~400-block octave on the macro domain) **mean 0.4794, sd 0.1163**. For
reference r = 0.6693/0.0588, b = 0.4799/0.1114, a = 0.4777/0.0988.

**13. Remapping used.** `macroSigned = (g - 0.4794) / 0.1163`, clamped to
+-2.5 sd. Centred on the measured mean, never on an assumed 0-1 range - T212
established that an uncentred term carried only 0.051 coverage sd and a
shrink bias.

**14-16. Displacement statistics**, sampled around the anvil rim with the real
noise (4096 samples, 0.00% clipped at 2.5 sd):

| arm | coverage mean | coverage sd | blocks mean | blocks sd | blocks range | swing |
|---|---|---|---|---|---|---|
| T212 flat gain 1.00 | +0.0163 | 0.1395 | +2.55 | 21.79 | -49.9 … +35.2 | +-0.32 |
| M1 deck gain 2.69 (coverage) | +0.0439 | 0.3752 | +6.85 | 58.62 | -134.2 … +94.6 | **+-0.86** |
| **M2 deck-weighted, block space** | n/a - shift is in blocks | | ~0 | 38 per sd | bounded by +-edgeWidth (~89) | n/a |

**17. Projected-area bias.** M1 carried a measurable shrink: area -2.1% SIDE,
-1.8% LATERAL, -2.1% ABOVE. **M2 is effectively unbiased: +0.1% SIDE, -0.7%
LATERAL, -2.1% ABOVE.** The candidate multiplies a zero-mean term by a
positive weight, so it introduces no bias of its own; what bias M1 showed came
from asymmetric clipping, which M2's channel removes.

## VISUAL

Paired same-storm arms throughout. M1 = coverage modulation at deck gain 2.69;
M2 = the same band and weight displaced in block space.

**18. SIDE.** M1: subtle, the shoulders slightly cut back. **M2: the crown
gains broader undulations and the outline is less lens-like**, with the macro
band rising for the first time.

**19. LATERAL.** M1: the crown is visibly **shaved** - flatter, cut back on
the upper-left, material removed rather than pushed out. **M2: a distinct
notch on the left, a pronounced recessed dip left-of-centre and a raised
section right**, reading as coherent large-scale undulation rather than
erosion.

**20. ABOVE.** M1 near-neutral. M2: the edge arc gains a few small scallops
and the surface mottling is slightly coarser (`fine` +33%), but the silhouette
edge stays clean - **no furriness, no detached fragments**.

**21. NEAR_EDGE.** Underside gains recesses; no new wisps.

**22. Lower-body collateral: no material damage.** Occupancy below the deck
53901 -> 53242 (-1.2%) at SIDE and 34261 -> 34195 (-0.2%) at LATERAL; neck
mean 30 -> 26 and 125 -> 122. Small and within the run-to-run band; the deck
weight localises the term above the tower by construction.

## SILHOUETTE

M2 against production, same storm:

| metric | SIDE | LATERAL | ABOVE |
|---|---|---|---|
| **23. IoU** | 0.9690 | 0.9666 | 0.9671 |
| **24. area** | 80717 -> 80822 (**+0.1%**) | 42314 -> 42007 (-0.7%) | -2.1% |
| **25. perimeter** | 3766 -> 3794 | 2958 -> 2872 | 21407 -> 23523 |
| **26. ellipse harmonic** | 22.92 -> 23.48 | 30.73 -> **29.29 (-4.7%)** | 82.34 -> 81.71 |
| **27. macro edge energy (3-8)** | 8.84 -> **9.35 (+5.8%)** | 7.48 -> **9.26 (+23.8%)** | 53.97 -> 54.33 |
| **28. mid-frequency (9-32)** | 6.73 -> 6.65 | 6.46 -> 6.02 | 11.93 -> 12.97 |
| ellipse / macro | 2.59 -> 2.51 | 4.11 -> **3.16 (-23%)** | 1.53 -> 1.50 |
| **29. protrusion / recession** | broader undulations | notch + recessed dip | small scallops |
| **30. holes / components** | 5304 -> **3404 (-36%)** / 149 -> 136 | 1333 -> **867 (-35%)** / 95 -> 110 | 238 -> 250 |

This is the shift the task asked for: **less energy in the ellipse component,
more in low-frequency irregular macro curvature**, with coherence improving
rather than degrading (holes down ~35%, components stable). It is the first
arm across T212-T213 to raise macro energy at **both** silhouette poses.

**Reproducibility, stated honestly.** A third paired run on the final banked
build (a different storm, `5fd3e32a`) reproduces the effect **strongly at
LATERAL and weakly at SIDE**:

| run | LATERAL macro | LATERAL ellipse | SIDE macro | SIDE ellipse |
|---|---|---|---|---|
| M2 (`3dfb46ef`) | +23.8% | -4.7% | +5.8% | +2.4% |
| verification (`5fd3e32a`) | **+24.0%** | -3.9% | +1.6% | **+10.5%** |

The LATERAL macro gain is the robust, reproducible signal (+23.8% then
+24.0%). The SIDE numbers move between storms in both directions, so **the
improvement should be claimed at LATERAL and treated as unproven at SIDE**.
Since the storm realisation changes every launch, these are three different
storms; the consistency of the LATERAL delta across two of them is what
carries weight, and the SIDE variance is a reason not to overstate the
result.

For contrast, the two rejected arms moved perimeter without moving macro:
T212's flat gain gave macro +0.5% / -18.6%; M1 gave +6.4% / **-29.5%** while
shaving 2% of the area off.

## TEMPORAL

**31. Static probe** (same pose, 8 frames apart, camera still):

| pose | production mean / max | candidate mean / max | px > 2/255 |
|---|---|---|---|
| SIDE | 0.00630 / 1.33 | **0.00534 / 1.00** | 0.00% |
| LATERAL | 0.02423 / 1.00 | **0.02313 / 1.00** | 0.00% |
| ABOVE | 0.00020 / 1.00 | 0.00022 / 1.00 | 0.00% |

Candidate is as still or stiller than production. As T211 established, this is
only a pre-filter.

**32. Route flicker** (T213M, T152 route, 2200 frames per arm, one storm;
self-referential, unconfounded):

| segment | off | on | change |
|---|---|---|---|
| OUTSIDE | 0.00033 | 0.00035 | +7.2% |
| APPROACH | 0.00098 | 0.00101 | +2.7% |
| ENTRY | 0.00128 | 0.00130 | +1.7% |
| INTERIOR | 0.00468 | 0.00480 | +2.6% |
| **ROUTE** | **0.00219** | **0.00224** | **+2.5%** |

(Production 0.50 reference 0.00195.) **T212's candidate cost +10.2%; this is a
4x improvement**, and the task's requirement to beat that figure is met.

**33. Edge crawl: net favourable.** Testing T211's signature - control exactly
still while the candidate crawls - finds 13 frames (worst 0.0266, longest run
7). **16 frames run the other way**, and the candidate ends with slightly more
perfectly-still frames (617 vs 613). There is no sustained one-way crawl of
the kind that disqualified T211's wide jitter.

**34. Silhouette popping: none observed.** The displacement is a static
world-space field evaluated per sample, so the envelope does not move between
frames; what changes is where the boundary sits, not when.

**35. Ghost, with the confound stated:** 0.00099 -> 0.00230 and disocclusion
0.03618 -> 0.05848. **Measured against a reference rendered with the candidate
off, so a legitimately different silhouette inflates both by construction.**
Reported for completeness; not used to judge the candidate, per the task.

## PERFORMANCE

| | production | candidate | post-bank |
|---|---|---|---|
| **36/37. SIDE total** | 7.7922 | 7.8136 | **7.8567** |
| | rawCloud 5.3417 | rawCloud 5.2027 | rawCloud 5.3842 |
| **38/39. PLAY_VIS_NEAR total** | 7.8349 | 7.6783 | **7.1112** |
| | rawCloud 4.5064 | rawCloud 4.4339 | rawCloud 3.8346 |

**40. Delta: below the storm-variance noise floor.** The candidate measures
+0.02 ms at SIDE and **-0.16 ms** at PLAY_VIS_NEAR - nominally faster, which
is variance rather than a speed-up, since `rawCloud` moves further between
sessions than the delta itself. The added work is one gated texture fetch plus
an `asin`/`sin` pair, confined to the boundary band (coverage 0.12-0.98), and
sits in the **< 0.25 ms "easy acceptance"** class.

**41. <= 10 ms: YES** at both poses on every measurement, with 2.1-2.9 ms of
headroom. The ~7-8 ms class is preserved.

## DECISION

**42. KEEP.** `PRODUCTION_MACRO_ENVELOPE_MODE = 3`.

CASE A: the vertical weighting, applied in the correct channel, produces
macro-scale silhouette improvement with stable motion and negligible cost.
Banked as the smallest successful implementation - the flat-gain (mode 1) and
coverage-space deck-weighted (mode 2) variants are retained as diagnostics,
the way T209-T212 retained theirs.

The judgement, visual first: it is a **real but modest** improvement. The
anvil reads as less lens-like, with genuine recessed sections and coherent
undulation, and for the first time the macro band grows rather than being
traded away. It is not a dramatic billowing canopy. What justifies banking it
is that the cost is genuinely small on every axis measured - +2.5% route
flicker, no area bias, fewer holes, no lower-structure damage, sub-noise-floor
performance - so the improvement is close to free rather than bought.

**43. Hypothesis: CONFIRMED, with an important refinement.** The anvil's
vertical envelope does explain T212's failure, but in two stages, and the
second was not in the original hypothesis:

1. **The clamp attenuates displacement 2.69x** at the deck and nowhere else.
   Confirmed by direct measurement of the fixture descriptors.
2. **The coverage channel cannot carry the compensation.** Pushing 2.69x
   through coverage drives a +-0.86 swing against a bounded [0,1] channel that
   clips asymmetrically - negative excursions zero the coverage and delete
   material, positive ones clamp at 1.0 and push nothing outward. Measured and
   then seen: M1 shaved the crown (macro -29.5% at LATERAL, area -2%) instead
   of billowing. The same displacement expressed in the envelope's falloff
   parameter is symmetric and bounded by the band (+-89 blocks at the anvil)
   rather than by the coverage range, and it works.

So T212's band was the right scale, its weighting target was right, and its
**channel** was wrong. Both the clamp and the channel had to be addressed.

**44. Production constant:** `PRODUCTION_MACRO_ENVELOPE_MODE = 3`. All other
production constants unchanged. No pinned morphology constant was touched -
`STORM_EROSION`, `STORM_CORE_FILL` and the noise domains are untouched, and
the density-threshold sandbox reproduces every recorded statistic, so
`morphology-thresholds.md` needs no revision.

**45. Exact next visual target: T214 - the anvil underside and cloud base.**
With the outer silhouette improved, the strongest remaining anvil defect from
the T211 audit is the underside: `8_NEAR_EDGE` shows a uniform dark sheet with
a smooth boundary and no mammatus. It is a different surface from the one
T212-T213 worked (the deck's lower face rather than its rim), the same
block-space displacement channel is now available to test on it, and the T211
third-ranked defect (over-eroded lower mass / weak tower) sits directly below
it and should be assessed in the same pass.

## VALIDATION

**46. `campaignGate`: PASS**, before any client launched and again after
banking (`gate-t213.log`, `gate-t213b.log`): `T170_WIRING campaigns=60 |
armMatrices=50 | violations=0`, with `T206_PRODUCTION_SPLIT`
(`jsonMatchesGlsl=true`, `rendererSetUniformsDeclared=true`) and
`T207_TEMPORAL` (`rainCurrentFrame=true`, `productionTemporalMode=1`) green -
confirming the gate-path mirror broke neither the rain nor the temporal
contract.

**47. `check build`: PASS** (`check-build-t213.log`).

**48. Morphology sandbox: PASS** - `T100 measured noise thresholds | PASSED |
all recorded statistics and derivations reproduced`.

**49. Program scope: unchanged.** No new program. `PaMacroEnvelope` was
already declared in `cloud_atmosphere_volume.json` by T212 and is deliberately
not in `leanFinalConstants`, so modes 1 and 2 stay reachable in FINAL as
diagnostics.

**50. Marker state.** `t212-macro-motion.txt` armed only for its route and
retired; `t098-captures.txt` and `t211-captures.txt` remain harness machinery;
`run/t132-autorun-exit.txt` kept throughout. No campaign marker left armed.

**51. Git status.** T213 sources, this document, `t213-campaign/` evidence,
the launchers, and loose run logs. `PAchangelog.md` preserved and uncommitted.
Every edited file is pure CRLF.

**52. Commit.** On `quality/t213-macro-envelope-vertical`, not merged.

### Two implementation faults found and fixed

Both produced near-perfect nulls that would have read as clean negative
results:

- **The deck weight was computed on the wrong vertical coordinate.** `h01` in
  `cloudDensity` is height within the weather-modulated cloud layer;
  the anvil band is defined on `directStormHeight01`, the descriptor's own
  normalised height returned by the walk. Mode 2 hid the error because its
  gain is `mix(1.0, 2.69, deckWeight)` and still modulates at full base
  strength where the weight is zero; mode 3 multiplies by the weight directly
  and went to exactly zero (IoU 0.9955-0.9993). **M1's numbers are therefore a
  measurement of ungated coverage modulation at elevated gain, not a test of
  deck-localised weighting**, and are reported as such above.
- **The gate path was left on the old channel.** The block-space displacement
  was initially implemented only in `cloudDensity`, leaving
  `directStormFinalDensity` - the rain gate and field build - applying the
  coverage-add branch for mode 3. Banking that would have shipped a renderer
  whose rain support believed in a different envelope from the one being
  drawn. It produces no artifact in a still frame, so no capture metric would
  have caught it; only reading the second call site did.

### Evidence

| path | what it is |
|---|---|
| `t213-campaign/m1-coverage/` | M1, coverage-channel deck gain (the shaving arm) |
| `t213-campaign/m2-blocks/` | M2, block-space deck-weighted displacement (banked) |
| `t213-campaign/t213-macro-motion.log` | the T213M route: +2.5% route flicker |
