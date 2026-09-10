# T160 - Bounded upper-cloud root-cause experiment

**Root cause: 2. PROFILE SHAPE.** The upper morphology is not truncated. It reaches **104.6%**
of the width its own descriptors and role profiles intend, and its support extends **28 blocks
above** the role envelope top rather than being clipped at it. The rounded cap is produced by
the anvil's own radius profile, which **peaks at height fraction 0.65 and then declines** while
the vertical shape fades from 0.76 to zero at 1.00.

Measured headlessly on the real production density path with
`./gradlew stormT160UpperEnvelopeSandbox`, against the measured ten-member severe fixture and
the real baked noise volumes. `StormDensityModel` is the CPU authority the shader mirrors, so
these are final production densities, not descriptor geometry.

---

## 1. Current upper extent constraints

| expression | role | value | what it limits |
|---|---|---|---|
| `profileRadius` lerp knee | ANVIL | **0.62** | radius stops growing above this height fraction |
| `profileRadius` lerp endpoint | ANVIL | **2.10** | maximum radius multiplier |
| `profileRadius` sin term | ANVIL | `0.08 * sin(pi v)^0.55` | adds at mid-height, zero at both caps |
| `profileRadius` top taper | ANVIL | `-0.10 * smoothstep(0.88, 1.0, v)` | narrows the last 12% |
| `profileRadius` lerp | TOWER | 1.25 -> 0.60 | tower narrows monotonically with height |
| `verticalShape` fade start | ANVIL | **0.76** | density decreases above this fraction |
| `verticalShape` fade end | ANVIL | 1.00 | density exactly zero at the role top |
| `verticalShape` fade start | TOWER | 0.72 | density decreases above this fraction |
| minor widening | ANVIL | x1.56 | anvil minor radius only |
| `maximumProfileRadius` | ANVIL | 2.18 | conservative bound feeding `horizontalReachBlocks` |
| `roleTopY` extension | ANVIL | +16 blocks | role envelope top above descriptor top |
| `roleTopY` extension | CORE | +32 blocks | role envelope top above descriptor top |
| `roleBaseY` extension | ANVIL / TOWER | -12 / -28 blocks | role envelope base below descriptor base |
| `edgeWidthBlocks` | ANVIL | `max(0.12, softness * 1.65)` | envelope fade half-width |
| `horizontalShape` | all | `1 - smoothstep(1 - edgeWidth, 1, radial)` | radial cutoff at `radial = 1` |

Fixture upper descriptors:

| role | member | centre | descriptor Y | role Y | major | minor | density | max profile half-width |
|---|---|---|---|---|---|---|---|---|
| TOWER | 4 | (-6, 0) | 300..448 | 272..448 | 58 | 48 | 0.9700 | 83 x 69 |
| TOWER | 5 | (16, -6) | 308..456 | 280..456 | 54 | 44 | 0.9539 | 77 x 63 |
| ANVIL | 6 | (10, 0) | 396..504 | 384..520 | 206 | 82 | 0.8222 | 449 x 279 |
| ANVIL | 7 | (44, -8) | 404..508 | 392..524 | 184 | 74 | 0.7231 | 401 x 252 |
| ANVIL | 8 | (-30, 6) | 400..500 | 388..516 | 176 | 70 | 0.7851 | 384 x 238 |
| ANVIL | 9 | (-58, -12) | 390..498 | 378..514 | 168 | 68 | 0.7992 | 366 x 231 |

**The profile turns over on its own.** `profileRadius(ANVIL, v)`:

| v | 0.50 | 0.55 | **0.60** | **0.65** | 0.70 | 0.80 | 0.90 | 0.95 | 1.00 |
|---|---|---|---|---|---|---|---|---|---|
| profileRadius | 2.043 | 2.130 | 2.174 | **2.175** | 2.171 | 2.160 | 2.135 | 2.067 | 2.000 |
| verticalShape | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 0.926 | 0.376 | 0.112 | 0.000 |

The radius maximum is at v = 0.65 and it is **declining** everywhere above. The top 35% of the
anvil is a constant-then-narrowing column whose density is simultaneously fading - which is
exactly a rounded cap.

---

## 2. Baseline cross-sections (production density)

System role envelope y = 224..524, span 300 blocks. Radii are from the system axis, in blocks.

| v | y | max X radius | max Z radius | occupied area | nonzero radius | dense radius | widening |
|---|---|---|---|---|---|---|---|
| 0.50 | 374 | 150 | 108 | 31,356 | 166 | 164 | - |
| 0.60 | 404 | 222 | 150 | 76,176 | 237 | 236 | yes |
| 0.70 | 434 | 372 | 246 | 228,708 | 380 | 378 | yes |
| 0.80 | 464 | 468 | 312 | 345,024 | 495 | 489 | yes |
| 0.90 | 494 | 474 | 318 | 351,324 | 494 | 474 | yes |
| 0.95 | 509 | 474 | 312 | 332,460 | 489 | 480 | **NO** |

- **Highest nonzero density Y: 552** - which is **28 blocks above** the role envelope top of 524.
- **Maximum final-density half-width: 480 blocks at y = 468** (v = 0.81).
- **Descriptor maximum intended half-width: 459 blocks.**
- **Realised fraction of intended width: 1.046.**
- Width/height ratio: **2.927** (960 wide against 328 tall).

**IS HORIZONTAL WIDTH STILL INCREASING WHEN FINAL DENSITY TERMINATES? NO.** Width peaks at
v ~ 0.81 and declines through the remaining 19% of the cloud.

---

## 3. Production vs relaxed extents (same fixture)

The relaxed arm moves only four upper limits - anvil radius knee 0.62 -> 0.98, endpoint
2.10 -> 4.20, anvil fade start 0.76 -> 0.94, tower fade start 0.72 -> 0.90, anvil role-top
extension +16 -> +120, and the matching `maximumProfileRadius` so the spatial index bound stays
sound. Render distance, renderer bounds, march budget, internal resolution, lighting, detail
noise, erosion, the 1.56 minor widening and every non-upper parameter are untouched.

| v | y | production maxX | production maxZ | relaxed maxX | relaxed maxZ | relaxed area |
|---|---|---|---|---|---|---|
| 0.50 | 374 / 426 | 150 | 108 | 228 | 162 | 97,416 |
| 0.60 | 404 / 466 | 222 | 150 | 402 | 252 | 239,508 |
| 0.70 | 434 / 507 | 372 | 246 | 594 | 384 | 526,284 |
| 0.80 | 464 / 547 | 468 | 312 | 768 | 516 | 922,752 |
| 0.90 | 494 / 588 | 474 | 318 | 882 | 612 | 1,275,912 |
| 0.95 | 509 / 608 | 474 | 312 | **894** | **630** | 1,334,448 |

| metric | production | relaxed | ratio |
|---|---|---|---|
| highest nonzero y | 552 | 656 | 1.188 |
| cloud height | 328 | 432 | 1.317 |
| max half-width | 480 | 894 | **1.863** |
| width/height ratio | 2.927 | 4.139 | 1.414 |

**CLASSIFICATION: A** - relaxation lets the cloud continue outward and form a substantially
broader anvil. The relaxed arm is **still widening at its own termination** (top/mid occupied
area ratio 13.7), so even 1.86x has not found the existing profile's natural maximum. Density
stays bounded and well-formed throughout, so this is not case D.

---

## 4. Where the width is lost: nowhere downstream

Identical cross-sections through descriptor envelope -> body after base-noise remap -> final
density after erosion:

| v | y | envelope radius | body radius | final radius | body/env | final/body |
|---|---|---|---|---|---|---|
| 0.50 | 374 | 175 | 169 | 164 | 0.969 | 0.967 |
| 0.60 | 404 | 236 | 240 | 237 | 1.016 | 0.991 |
| 0.70 | 434 | 387 | 383 | 380 | 0.988 | 0.993 |
| 0.80 | 464 | 507 | 495 | 489 | 0.976 | 0.989 |
| 0.90 | 494 | 507 | 497 | 488 | 0.981 | 0.982 |
| 0.95 | 509 | 499 | 490 | 489 | 0.982 | 0.997 |

Every stage preserves the width it is given to within 3%. **The first stage that "loses" width
is the descriptor envelope itself** - there is nothing downstream to blame. This positively
excludes root causes 3 (density remap / erosion) and, as far as final density goes, leaves the
envelope's own shape as the sole author of the silhouette.

---

## 5. Upper TOWER -> ANVIL transition

| y | tower radius | anvil radius | union radius | dTower/dy | dAnvil/dy | dUnion/dy |
|---|---|---|---|---|---|---|
| 380 | 84 | 172 | 172 | - | - | - |
| 412 | 82 | 271 | 271 | -0.203 | +4.131 | +4.131 |
| 444 | 80 | 428 | 428 | -0.097 | +4.667 | +4.667 |
| 460 | 76 | 489 | 489 | -0.260 | +3.808 | +3.808 |
| 476 | **0** | 499 | 499 | **-4.743** | +0.628 | +0.628 |
| 492 | 0 | 488 | 488 | 0.000 | -0.702 | -0.702 |
| 508 | 0 | 489 | 489 | 0.000 | +0.055 | +0.055 |
| 524 | 0 | 459 | 459 | 0.000 | -1.858 | -1.858 |

Findings:

- **The tower never widens.** Its radius is flat at 76-85 blocks across the whole transition and
  then terminates abruptly between y = 460 and y = 476 (dTower/dy = -4.743 per block). It does
  not "reach its intended width before fading" because its profile narrows monotonically with
  height by construction (`lerp(v, 1.25, 0.60)`).
- **The anvil begins broad enough and overlaps adequately.** At y = 380 the anvil is already
  172 blocks against the tower's 84, and the two overlap over ~96 blocks. The union radius is
  the anvil's everywhere above y = 380, so the tower's abrupt end is invisible - it is entirely
  enclosed. **The abrupt tower termination is not the cause of the cap.**
- **The radius derivative collapses at y ~ 476**, from +3.8 to +0.63 and then negative. That
  inflection - not the tower handoff - is where the silhouette stops expanding, and it is the
  anvil's own `profileRadius` turning over past v = 0.65.
- Density support does **not** terminate while radius is still increasing: radius peaks first
  (y ~ 468-476), density continues to y = 552.

---

## 6. Answers

1. **Current upper extent constraints** - table in section 1.
2. **Production highest nonzero Y** - 552, which is 28 blocks *above* the 524 role envelope top.
3. **Production maximum width** - 480 blocks half-width (960 full) at y = 468.
4. **Intended descriptor/envelope maximum width** - 459 blocks half-width.
5. **Width by upper-cloud height** - section 2 table; 150 -> 222 -> 372 -> 468 -> 474 -> 474.
6. **Still increasing at termination?** - **No.** Peaks at v ~ 0.81, declines above.
7. **Relaxed arm** - height 432 (1.32x), half-width 894 (1.86x), still widening at termination.
8. **ABOVE before/after** - footprint area at the top section 332,460 -> 1,334,448 block^2, a
   4.01x larger canopy; max radii 474x312 -> 894x630.
9. **SIDE before/after** - height 328 -> 432, width/height 2.93 -> 4.14; the silhouette changes
   from a cap that closes above v = 0.81 to one still opening at its top section.
10. **TOWER -> ANVIL transition** - section 5. Overlap is adequate and the tower is fully
    enclosed; the cap comes from the anvil's radius derivative collapsing at y ~ 476.
11. **First stage where width is lost** - none downstream; the descriptor envelope is already
    the limiting shape (every later stage preserves it to within 3%).
12. **Root-cause classification** - **2. PROFILE SHAPE.** Not 1: nothing terminates a
    still-growing profile, the profile turns over by itself at v = 0.65. Not 3: remap and
    erosion preserve width to within 3%. See the caveat in point 13 for 4.
13. **Is the apparent cut-off real?** - **No, not as a truncation.** The cloud reaches 104.6% of
    its intended width and its density fades 28 blocks past the role envelope top rather than
    being clipped. What reads as a cut-off is the radius profile peaking at 65% height and
    declining while the vertical shape fades - a shape, not a clip.
14. **Can the existing profile form a proper anvil when unconstrained?** - **Yes.** Relaxing only
    the knee, endpoint and fade starts yields 1.86x width and a 4.14 width/height ratio while
    still widening at termination, with bounded well-formed density. The profile's *form* is
    capable of an anvil; its constants stop it at 62% height.
15. **Recommendation for T098b** - the lever is the anvil radius **knee at 0.62**, not any
    extent clamp, and not the tower handoff. Moving the knee up lets the existing lerp keep
    expanding across the anvil's height instead of saturating in its lower third; the endpoint
    2.10 sets how wide, the knee sets *where it stops widening*. The `-0.10` top taper and the
    0.76 vertical fade shape the last 12-24% and are secondary. **No value here is a shipping
    proposal** - the relaxed constants were chosen large enough to make the behaviour
    unambiguous, which is the opposite of a tuning candidate.
16. **check/build** - see section 8.

---

## 7. What this run does not establish

**Root cause 4 (renderer / reconstruction) is not positively excluded.** This experiment
measures through final `cloudDensity`; it does not measure rendered occupancy, which would need
a client run. Two things are worth carrying into T098b:

- The production density field from above is **not circular**: max X 468 against max Z 312 at
  v = 0.80, a 1.5:1 ellipse, following the anvil descriptors' 206/82 major/minor with the 1.56
  widening. If the rendered ABOVE view reads as *circular* rather than as a 1.5:1 ellipse, that
  discrepancy is downstream of final density and points at renderer/reconstruction, which this
  run cannot see.
- Equally, the density field is already 2.93 width/height. A rendered view that reads much
  narrower than that is also a downstream question.

A rendered A/B at ABOVE and SIDE would close this, and belongs with T098b's regrade rather than
with a bounded diagnostic.

Also single-fixture: one measured ten-member severe system, one noise bake. The profile
constants are global, so the shape conclusion generalises; the exact widths do not.

---

## 8. Production safety and gate

The relaxed arm lives in `StormT160UpperExtentArm`, is **off by default**, is enabled only
inside the sandbox around a single measurement and switched back before the process ends, and
is never referenced by production code. With it off, every accessor returns the shipped
constant and `StormLobeEvaluator` evaluates the expressions it evaluated before the class
existed. **Nothing is banked; no morphology value is changed.**

`./gradlew check build`: **BUILD SUCCESSFUL in 2m 39s**, 23 actionable tasks (17 executed,
6 up-to-date), 0 failures. `check` ran `architectureBoundaryCheck`,
`cloudMorphologyTopologySandbox`, `cloudRegionMotionSandbox`, `materialAdvectionSandbox`,
`cloudFieldSandbox`, `cloudRenderOwnershipSandbox`, `stormVolumetricGeometrySandbox` and
`volumetricStabilityDiagnosticsSandbox`; `build` produced the jar. The evaluator edits are
therefore production-neutral against the existing regression set, as the default-off arm
requires.

Independent corroboration from the same `check` run: `T098_ANVIL_SCALE` reports
`anvilMajorRadiusBlocks=507.6` and `anvilChordBlocks=1015.2`, consistent with the 480-499
half-width measured here by a different code path.
