# T215 - Lower mass / tower structure

One job: make the lower storm read as a coherent convective mass with a real
tower, without turning it into a blob or damaging the T214 anvil result.

**Outcome: a PARTIAL success, banked. The perforation half of the defect is
attributed, fixed and kept (`PRODUCTION_LOWER_BODY_MODE = 1`). The tower half
is DISPROVED as a rendering problem - it is descriptor geometry, and no shader
term can reach it.**

## RECOVERY

**1. Starting HEAD.** `3c172c6` on `quality/t214-anvil-macro-compensation`.

**2. Branch.** Committed to `quality/t215-lower-tower-structure`. Not merged.

**3. Production state at entry.** `PRODUCTION_TEMPORAL_MODE = 1`,
`PRODUCTION_SPATIAL_UPSCALE = -1`, `PRODUCTION_RAIN_LATTICE_MODE = 0`,
`PRODUCTION_ORIGIN_JITTER_MODE = 0`, `PRODUCTION_MACRO_ENVELOPE_MODE = 3`.
`PAchangelog.md` preserved and uncommitted throughout.

**4. Harness state.** T211-T214 machinery reused: the captures-only route, the
fixture bootstrap, paired same-storm arms, the motion arm set and the
silhouette harmonic metrics. A new lower-body structure metric was added
(occupancy, components, largest-component fraction, holes by size, neck width,
vertical continuity). `campaignGate` passed before any client launched.

## ATTRIBUTION

Done first, from measurement, before any constant was touched. The T212
`T212_EDGE_WIDTH` trace was extended with a `T215_GEOMETRY` line carrying each
descriptor's centre, vertical extent, radii, density weight and vertical
development - marker-gated and budget-limited, inert in production.

**The real descriptor set** (max density weight per lobe = `envelopeStrength`,
since `lobeStrength = saturate(shearMedia.z)` = texel offset+10):

| role | y span | radii | strength |
|---|---|---|---|
| BASE x2 | 136 - 599 | 448/403, 458/412 | 0.0786 |
| CORE x2 | 351 - 703 | 260/234, 236/212 | 0.0854, 0.0917 |
| TOWER x2 | 433 - 929 | 197/161, 166/136 | 0.0856, 0.0843 |
| ANVIL x4 | 772 - 1000 | 422-434 / 245-252 | 0.0538 - 0.0719 |

**5. The controlling stage: (A) the envelope, specifically the width of its
fade.** Measured radial occupancy of the lower mass, centre to rim:

| r/rmax | 0-0.17 | 0.17-0.33 | 0.33-0.50 | 0.50-0.67 | 0.67-0.83 | 0.83-1.00 |
|---|---|---|---|---|---|---|
| SIDE | 1.000 | 0.985 | 0.933 | **0.667** | 0.459 | 0.103 |
| LATERAL | 1.000 | 1.000 | 0.920 | 0.764 | 0.436 | 0.194 |

The lower mass is **solid in its core** and perforates progressively outward,
with the break starting where the envelope fade starts. BASE carries the
widest fade of any role - **141-145 blocks on a 448 radius, roughly 53% of its
projected area**, against ~37% for ANVIL and ~35% for TOWER. The lower mass is
therefore a large perforated annulus by construction.

**6. Vertical profile contribution: not the cause.** The profile shapes radius
against height, but the occupancy collapse tracks radial distance into the
fade, not height.

**7. Core fill: ruled out by measurement.** `coverage * (1 + fill)` is
**1.397 at BASE against 1.396 at ANVIL** - `stormCoreFillForStrength`
compensates strength almost exactly, so the body-versus-noise response is the
same function in both regions. This is the same invariance T212 found across
strengths. A separate finding: the structural-continuity fill
(`stormCoreFillForCoverageAndStrength`) requires `embeddedConvectiveOverlap`,
i.e. BASE+CORE+TOWER simultaneously - but **below y = 351 only BASE exists**,
so that path can never engage in the lower mass at all.

**8. Erosion: not too strong at full coverage.** With strength 0.0786 and fill
16.77, `body = 0.284 + 0.716 * baseField`, while the erosion bite
`0.44 * (1 - detailFbm)` is 0.22 at median detail and 0.283 at its p05. Body
clears the bite wherever coverage is near strength. Holes require coverage
below roughly half of strength - which happens only inside the fade.

**9. Noise: not the cause.** Base and detail statistics are unchanged from the
morphology document; what varies is the coverage they are remapped against.

**10. Hypothesis result.** The suspected causes - global erosion strength,
core fill, noise level - are all **DISPROVED**. The mechanism is the
**width of the BASE envelope fade**, with erosion doing the cutting inside it.

## BASELINE STRUCTURE

Production, same storm (`t215-campaign/baseline/`, metrics on the paired run):

| metric | SIDE | LATERAL |
|---|---|---|
| **11. lower-body occupancy** | 50865 px | 38657 px |
| **12. hole count** | 121 | 55 |
| **13. hole area** | 2327 px | 592 px |
| **14. connected components** | 122 | 95 |
| **15. largest-component fraction** | **0.7982** | 0.8652 |
| **16. tower neck width** | min 1, mean 85 | min 8, mean 173 |
| **17. vertical connected span** | 0.9881 | 1.0000 |

An earlier baseline capture of a different storm gave a largest-component
fraction of **0.618** at SIDE - i.e. 38% of the storm's pixels in fragments
detached from the main mass. Median hole size is 2 px in every case: the
defect is **speckle**, not large voids.

## CANDIDATE

**18. The exact change.** A lift applied to the envelope falloff value
immediately before `stormBody`, on the primary march:

```glsl
float lowerWeight = 1.0 - smoothstep(0.35, 0.55, directStormHeight01);
float uLow    = clamp(cloud / envStrengthLow, 0.0, 1.0);
float uLifted = uLow + STORM_LOWER_FADE_LIFT * uLow * (1.0 - uLow);   // lift = 0.50
cloud = saturate(mix(cloud, uLifted * envStrengthLow, lowerWeight));
```

`u` is 1 at the solid core and 0 at the outer edge. The lift `u + A*u*(1-u)`
is a **bump that vanishes at both ends**: it does nothing in the core, nothing
at the far fringe - so the silhouette is not simply inflated - and is largest
in mid-fade, exactly where measured occupancy collapses from 0.93 to 0.46.

The obvious alternative, a gamma on `u`, was rejected by calculation before
being built: it lifts the far fringe proportionally *most* and would have
moved the visible boundary ~30 blocks, about +14% area, which Task 7 rules out
as excessive widening.

**19. Localisation rule.** By the descriptor's own normalised height
(`directStormHeight01`, not the weather-modulated layer height - the T213
lesson), faded out by 0.55, which is exactly where T213's anvil deck weight
*begins*. The two terms therefore do not overlap. Smooth, bounded,
deterministic, world-space stable, no hard band.

**20-22. Results, paired same-storm arms** (`t215-campaign/candidate/`):

| metric | SIDE L0 -> L1 | LATERAL L0 -> L1 |
|---|---|---|
| lower occupancy | 50865 -> 52027 (+2.3%) | 38657 -> 39276 (+1.6%) |
| hole count | 121 -> 117 | 55 -> 52 |
| **hole area** | 2327 -> **1823 (-21.7%)** | 592 -> **478 (-19.3%)** |
| large holes (>=50 px) | 6 -> **4** | 4 -> **2** |
| components | 122 -> 124 | 95 -> 95 |
| largest-component fraction | 0.7982 -> 0.8016 | 0.8652 -> 0.8640 |
| neck min / mean | 1 -> 1, 85 -> 82 | 8 -> 8, 173 -> 173 |
| vertical continuity | 0.9881 -> 0.9921 | 1.0000 -> 1.0000 |

Visually the perforation is clearly reduced: the large void right of centre in
the lower mass shrinks, the mass reads more continuous, and the rim is less
moth-eaten - consistent with hole area down ~20% and large holes halved.

**23. ABOVE collateral: none.** IoU **0.9999**, ellipse 78.90 -> 78.90, macro
45.11 -> 45.11. The height localisation is exact.

## STRUCTURE RESULT

**24. Occupancy delta** +2.3% / +1.6% - modest, not inflation.
**25. Hole-count delta** -4 / -3.
**26. Hole-area delta** **-21.7% / -19.3%** - the headline result.
**27. Component delta** 122 -> 124, 95 -> 95 - **unchanged**.
**28. Neck-width delta** 1 -> 1, 8 -> 8 - **unchanged**.
**29. Connectivity delta** largest-component fraction 0.7982 -> 0.8016 and
0.8652 -> 0.8640 - **unchanged**; vertical continuity 0.9881 -> 0.9921.
**30. Anvil collateral** ABOVE IoU 0.9999; SIDE ellipse 22.55 -> 22.31, macro
8.32 -> 8.13; LATERAL ellipse 27.30 -> 27.15, macro 8.89 -> 8.81. The T214
result is preserved.

**So the candidate fixes perforation and does not fix fragmentation.** Hole
area falls by a fifth while component count and largest-component fraction do
not move. Those are different defects with different causes, and this change
addresses only the first.

## LIGHTING

No optical-depth regression - the lift adds material in the fade, not in the
core, so it does not thicken the body a ray passes through:

| pose | arm | lower-mass luminance mean | p05 | p95 | range |
|---|---|---|---|---|---|
| SIDE | production | 135.97 | 115.67 | 187.27 | 71.60 |
| SIDE | candidate | 135.98 | 116.00 | 186.67 | 70.67 |
| LATERAL | production | 132.95 | 116.00 | 177.00 | 61.00 |
| LATERAL | candidate | 133.01 | 116.00 | 176.67 | 60.67 |

**31. Base darkness** unchanged (p05 identical). **32. Interior brightness**
unchanged (mean within 0.06). **33. Shadow pockets** preserved (dynamic range
71.6 -> 70.7 and 61.0 -> 60.7). No black wall, no flat grey slab.

## TEMPORAL

**34. Static probe** (same pose, 8 frames apart, camera still):

| pose | production mean / max | candidate mean / max | px > 2/255 |
|---|---|---|---|
| SIDE | 0.02578 / 1.00 | 0.02523 / 1.33 | 0.00% |
| LATERAL | 0.03006 / **4.67** | 0.02387 / **1.33** | 0.02% -> **0.00%** |

The candidate is **stiller** than production.

**35. Route flicker** (T215M, T152 route, 2200 frames per arm, one storm,
self-referential):

| segment | off | on | change |
|---|---|---|---|
| OUTSIDE | 0.00032 | 0.00033 | +5.7% |
| APPROACH | 0.00077 | 0.00070 | **-9.3%** |
| ENTRY | 0.00157 | 0.00150 | **-4.9%** |
| INTERIOR | 0.00515 | 0.00520 | +1.0% |
| **ROUTE** | **0.00240** | **0.00238** | **-1.0%** |

(Production 0.50 reference 0.00218.) The candidate **improves** route flicker.
That follows from the mechanism: flicker counts threshold-crossing pixels, and
filling holes removes exactly those.

**36. Edge crawl: zero in both directions.** No frame where one arm is still
and the other crawls; perfectly-still frames identical at **674 vs 674**.

**37. Hole / tower popping: none.** The lift is a static world-space function
of position, so it introduces no per-frame variation of its own.

**38. Ghost, with the confound stated:** 0.00217 -> 0.00354, disocclusion
0.06463 -> 0.11764. Measured against a reference rendered with the candidate
off, so a legitimately different lower mass inflates both by construction.
Reported for completeness; not used to judge the candidate.

## PERFORMANCE

Two samples per arm, because one sample was misleading:

| | SIDE | PLAY_VIS_NEAR |
|---|---|---|
| **39/41. production** | 7.8431, 7.4818 -> **7.662** | 7.3888, 6.9468 -> **7.168** |
| **40/42. candidate** | 7.8548, 7.9340 -> **7.894** | 7.7832, 7.0567 -> **7.420** |
| **43. delta** | **+0.232** | **+0.252** |
| **44. <= 10 ms** | **YES** (max 7.934) | **YES** (max 7.783) |

The first candidate sample alone suggested +0.373 SIDE and **+0.836** PVN,
which would have fallen in the "requires exceptional gain" band. Production
itself varies 0.36-0.44 ms between sessions, so a second sample was taken; it
put PVN at 7.0567 and the averaged delta at **~0.25 ms** - the top of the
"easy acceptance" band. There is a real mechanism for a small cost beyond the
arithmetic: filling holes means rays accumulate further before terminating.

## DECISION

**45. KEEP**, as the smallest successful implementation.

The judgement, visual first: the candidate does exactly what its attribution
predicted and nothing more. It removes a fifth of the hole area and halves the
large voids in the lower mass, visibly reducing the moth-eaten look, at
+2% area, no anvil collateral, no lighting change, **improved** temporal
stability and ~0.25 ms. Nothing regresses. It is banked because the cost is
near zero on every axis, not because the improvement is dramatic.

What it does **not** do is consolidate the fragmented mass or widen the neck.
Those metrics are flat, and the report says so rather than averaging them into
the win.

**46. Production change.** `PRODUCTION_LOWER_BODY_MODE = 1`. Mode 2 (the
restrained lift, 0.28) is retained as a diagnostic, as T209-T214 retained
theirs. All other production constants unchanged. No pinned morphology
constant touched.

**47. Mechanism: CONFIRMED for perforation, DISPROVED for the tower.**

- **Confirmed:** the lower mass perforates in the BASE envelope fade, which is
  the widest in the storm. Lifting mid-fade coverage measurably closes it.
- **Disproved as a rendering problem:** the weak tower. The descriptor set
  places **TOWER at y 433-929 with radii 166-197**, while **BASE occupies
  136-599 at radius 448**. The lower half of the storm is a lone BASE pancake
  by construction, and the "neck" is the narrow CORE/TOWER region above it. No
  density or erosion term in the shader can render a tower the descriptors do
  not place there. This is why the neck metric did not move for any candidate:
  there is nothing in the lower half to thicken.

**48. Exact next visual target: T216 - the convective column at descriptor
level.** The tower defect lives in storm *generation*, not rendering. The
specific question is why `StormLobeDescriptor` / the role layout gives the
lower half a single wide BASE lobe (radius 448, y 136-599) with the TOWER
lobes starting only at y 433 and reaching radius 197 - so the storm has no
narrow, tall, high-density column below the deck. Fixing it means changing
role placement or extent at generation time, judged with the same paired-arm
metrics used here (largest-component fraction, neck width, vertical
continuity) plus storm-to-storm diversity, since descriptor changes affect
every storm's identity.

## VALIDATION

**49. `campaignGate`: PASS**, before any client launched and again after
banking (`gate-t215.log`, `gate-t215b.log`): `T170_WIRING campaigns=60 |
armMatrices=50 | violations=0`, with `T206_PRODUCTION_SPLIT`
(`jsonMatchesGlsl=true`, `rendererSetUniformsDeclared=true`) and
`T207_TEMPORAL` (`rainCurrentFrame=true`, `productionTemporalMode=1`) green.

**50. `check build`: PASS** (`check-build-t215.log`).

**51. Morphology sandbox: PASS** - `T100 measured noise thresholds | PASSED |
all recorded statistics and derivations reproduced`. No pinned constant edited,
so `morphology-thresholds.md` needs no revision.

**52. Program scope: unchanged.** No new program. One uniform added
(`PaLowerBodyFill`, declared in `cloud_atmosphere_volume.json`), deliberately
not in `leanFinalConstants` so the diagnostic mode stays reachable in FINAL.

**53. Markers.** `t212-edge-trace.txt` and `t212-macro-motion.txt` armed only
for their runs and retired; `t098-captures.txt` and `t211-captures.txt` remain
harness machinery; `run/t132-autorun-exit.txt` kept. No campaign marker armed
at rest.

**54. Git status.** T215 sources, this document, `t215-campaign/` evidence, the
launchers, and loose run logs. `PAchangelog.md` preserved and uncommitted; the
`check-build-*.log` transcripts deliberately untracked, per the T214
precedent. Every edited file is pure CRLF.

**55. Commit.** On `quality/t215-lower-tower-structure`, not merged.

### Evidence

| path | what it is |
|---|---|
| `t215-campaign/baseline/` | production lower-body reference frames |
| `t215-campaign/candidate/` | the paired L0/L1 A/B on one storm |
| `t215-campaign/t215-attribution.log` | the `T215_GEOMETRY` descriptor trace |
| `t215-campaign/t215-lower-motion.log` | the T215M route: route flicker -1.0%, zero crawl |
