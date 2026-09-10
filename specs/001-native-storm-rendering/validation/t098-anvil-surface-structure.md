# T098 ANVIL surface structure — the contrast is lost in integration, not in the density field

**Feature**: `001-native-storm-rendering`
**Task**: T098 (US1 morphology acceptance gate)
**Date**: 2026-09-01
**Follows**: `t098-production-ray-trace.md`, `t098-promotion-budget.md`
**Result**: **CASE C.** No production change made.

---

## 0. The question

The two march defects are fixed and the connecting column renders on 5/5 fresh
fixtures. T098 still fails because the anvil reads as a large smooth balloon
with a uniform interior, failing FR-024 #1 and #2. The question was where the
anvil's detail amplitude is lost.

The premise "the anvil's density is uniform" turns out to be **false**, and the
answer is further downstream than the density field.

---

## 1. Density-stage distributions

Five deterministic realizations of the shipped T134 severe fixture, each placed
at a different world origin so the noise each samples is independent. Interior
only (`coverage >= 0.60`), which is what "uniform interior" is a claim about.
CORE and TOWER are the controls. Representative fixture, all five agree to
within 2 per cent:

| stage | role | n | mean | p05 | p50 | p95 | variance | CV | ≥0.90 | ≥0.99 | =0 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| envelope | ANVIL | 218245 | 0.7694 | 0.6088 | 0.8055 | 0.8063 | **0.0036** | **0.078** | 0.00 | 0.00 | 0.00 |
| baseField | ANVIL | 218245 | 0.5258 | 0.0000 | 0.5426 | 1.0000 | 0.1087 | 0.627 | 18.17 | 7.81 | 5.41 |
| bodyAfterRemap | ANVIL | 218245 | 0.6455 | 0.2678 | 0.6602 | 1.0000 | 0.0624 | 0.387 | 21.47 | 8.36 | **0.00** |
| detailFbm | ANVIL | 218245 | 0.4780 | 0.3561 | 0.4762 | 0.6069 | **0.0055** | **0.156** | 0.00 | 0.00 | 0.00 |
| finalDensity | ANVIL | 218245 | 0.4186 | 0.0234 | 0.4306 | 0.7726 | **0.0608** | **0.589** | 0.00 | 0.00 | 3.64 |
| envelope | CORE | 19098 | 0.9056 | 0.7506 | 0.9183 | 0.9842 | 0.0049 | 0.077 | 56.97 | 3.34 | 0.00 |
| finalDensity | CORE | 19098 | 0.4696 | 0.1376 | 0.4830 | 0.7756 | 0.0467 | 0.460 | 0.00 | 0.00 | 1.28 |
| envelope | TOWER | 18229 | 0.8713 | 0.6786 | 0.8966 | 0.9681 | 0.0095 | 0.112 | 49.60 | 0.00 | 0.00 |
| finalDensity | TOWER | 18229 | 0.4628 | 0.0235 | 0.4903 | 0.7799 | 0.0561 | 0.512 | 0.00 | 0.00 | 4.31 |

Mean density-gradient magnitude, ANVIL: **0.0106 per block** — the field changes
by roughly half its range over 45 blocks.

**The ANVIL density field is not uniform and not saturated.** Its coefficient of
variation (0.589) is *higher* than CORE's (0.460) and TOWER's (0.512), and it
never reaches 0.99. Whatever makes the anvil look like a balloon, it is not a
flat density field.

Two stages are nonetheless flat, and both matter later:

- **`envelope` — CV 0.078.** The anvil's coverage envelope is a plateau at
  0.61–0.81 across its whole interior.
- **`detailFbm` — CV 0.156**, spanning only 0.356–0.607. The erosion term is
  `(1 - detailFbm) * 0.44`, so the bite is a near-constant **0.22** rather than
  a texture. The existing erosion report agrees: mean erosion is 0.2295 for
  ANVIL, 0.2297 for BASE and 0.2298 for CORE/TOWER — identical to three decimal
  places across roles, which is what a DC offset looks like.

Consequence of the two together: with the envelope on a 0.77 plateau,
`stormBody`'s lower bound is `1 - 0.77(1 + fill)`, so the body has a floor near
0.10 and **reaches zero in 0.00 per cent of the anvil interior**. Only the
erosion's constant bite produces holes, and only where `baseField` is very low —
3.64 per cent of samples.

## 2. Uniform interior, objectively

Answering PHASE 2 directly: the interior is **neither saturated nor uniform**.

- `finalDensity` variance 0.0608, CV 0.589
- fraction ≥ 0.80: **1.72 %**; ≥ 0.90: **0.00 %**; ≥ 0.99: **0.00 %**
- fraction at zero: 3.64 %

There is no numerical clamping anywhere in the anvil's final density. The
"uniform interior" is a property of the *image*, not of the field.

## 3. Density versus integration — the decisive measurement

A grid of parallel rays marched through the anvil with the production step,
extinction and transmittance floor, **with no lighting at all**, so anything
flat here is integration rather than shading:

| view | rays | mean alpha | alpha variance | alpha CV | mean neighbour delta | alpha > 0.97 |
|---|---|---|---|---|---|---|
| SIDE | 4608 | 0.9995 | **0.000000** | **0.0000** | 0.000033 | **100.00 %** |
| ABOVE | 4608 | 0.9995 | 0.000002 | 0.0014 | 0.000095 | 99.96 % |

**Density CV 0.589 in, alpha CV 0.0000 out.** Every ray through the anvil is
opaque. This is PHASE 3's third outcome exactly: *alpha is smooth while density
is not, so integration is flattening the detail.*

Live confirmation, independent of this model: across the five-fixture campaign
every traced ANVIL ray terminated on the transmittance floor at 38–94
iterations with final alpha 0.98486–0.98730.

### Why

| quantity | value |
|---|---|
| mean depth to the transmittance floor | **74.8 blocks** (p05 40.0, p50 62.5, p95 140.0) |
| anvil chord | **1015.2 blocks** |
| anvil thickness | 210.6 blocks |
| visible skin as a fraction of the chord | **7.4 %** |

A ray sees the first ~75 blocks of a 1015-block crossing. The remaining 93 per
cent of the density variation cannot reach the image. The anvil's zeros are
sparse (3.6 %) and small (40-block correlation length), so no ray finds a clear
path through.

### How far from the regime where structure would show

Optical depth scaled, with the density field, its variance and its feature scale
held exactly fixed. Measurement only — **not a proposal**:

| optical scale | mean alpha | alpha variance | alpha CV | alpha > 0.97 | saturation depth |
|---|---|---|---|---|---|
| **1.000 (production)** | 0.9864 | 0.000001 | 0.0010 | 100.00 % | 87 blocks |
| 0.500 | 0.9857 | 0.000000 | 0.0005 | 100.00 % | 157 |
| 0.250 | 0.9854 | 0.000001 | 0.0009 | 99.93 % | 292 |
| 0.120 | 0.9843 | 0.000067 | 0.0083 | 98.55 % | 587 |
| 0.060 | 0.9762 | 0.001207 | 0.0356 | 89.95 % | 1088 |
| 0.030 | 0.9010 | 0.006272 | 0.0879 | 0.82 % | never saturates |

At a **quarter** of production optical depth the alpha field is still flat.
Structure only begins to appear below about 0.12x and is only substantial near
0.03x. The anvil is roughly **8x to 30x** too optically thick for the structure
it already has to reach the image.

That is a regime statement, not a tuning target. Dropping optical depth 30x
would make the storm translucent and would break every other criterion; it is
recorded to show how far the current state is from "interior detail is visible",
and therefore that interior detail is the wrong lever.

## 4. Detail hierarchy against the anvil's scale

Production values for a descriptor-owned storm:

| quantity | value | tile / feature |
|---|---|---|
| base-noise domain | `baseNoiseDomain(p, STORM_BASE_NOISE_SCALE = 0.0025)` | 400-block tile |
| base-noise warp | `lowFrequencyDomainWarp(p) * 0.31` | fixed 0.31 tile = 124 blocks |
| detail-noise domain | `detailNoiseDomain(p)`, scale 0.022 | 45.45-block tile |
| detail-noise warp | `lowFrequencyDomainWarp(p * 1.731) * 0.43` | 19.5 blocks |
| detail octave weights | 0.625 / 0.25 / 0.125 | — |
| erosion amplitude | `STORM_EROSION = 0.44`, subtractive | — |
| role-specific multipliers | none for ANVIL | — |
| saturation / clamping | `saturate` in `stormBody` only | — |

Measured decorrelation length along horizontal transects through the anvil
(125 transects), against the anvil's own major radius of 507.6 blocks:

| stage | decorrelation | features across the anvil |
|---|---|---|
| baseField | 40.0 blocks | 25.4 |
| detailFbm | **8.0 blocks** | **126.9** |
| finalDensity | 40.0 blocks | 25.4 |

So the answer to PHASE 4's question is **yes**: the detail hierarchy is fixed
while the anvil is 1015 blocks across. At the SIDE pose an 8-block detail
feature subtends about **1.8 output pixels** — below the 4-pixel reconstruction
lattice measured in section 6. Even at full contrast the detail octaves could
not read as billows at these viewing distances; they would alias.

This is a real finding, but it is **downstream of the saturation** in section 3:
interior detail at any wavelength cannot reach an image whose alpha is already
1.0 everywhere.

## 5. Saturation

PHASE 5 asked whether the anvil enters erosion already too high. It does not, in
the numerical sense:

- `bodyAfterRemap` ≥ 0.99 for 8.36 % of samples, and `finalDensity` ≥ 0.99 for
  **0.00 %**.
- `erosion >= body` for 13.8 % of anvil samples, against 46.5 % for BASE.

Nothing clamps. The saturation is **optical**, not numerical, and the term
responsible is the **envelope plateau** (CV 0.078), which gives `stormBody` a
floor near 0.10 so the body never reaches zero, combined with the erosion bite
being a near-constant 0.22 because `detailFbm` has CV 0.156.

The contrast with BASE is the control that makes this legible: BASE has
`erosion >= body` on 46.5 % of samples and only 52.6 % of its samples visible,
so it is a broken, sparse field and reads as billowy. The anvil is 96.4 % filled
and reads as solid.

## 6. Stair-step banding — measured and classified

Vertical luminance profile of the anvil canopy, autocorrelated, on ten images
(five fixtures x SIDE and FAR):

| pose | dominant period | period in world blocks |
|---|---|---|
| SIDE (1.7x radius) | **4.00 px** | 17.7 |
| FAR (2.6x radius) | **4.00 px** | 28.8 |

The pixel period is **identical at both poses** (ratio 1.00) while the block
period changes by 0.62. The SIDE/FAR screen-scale ratio is 1.62. A world-space
lattice would have produced a pixel ratio of 1.62; a screen-space artifact
produces 1.00, which is what was measured.

The cloud target is 1200x675 and the framebuffer 1600x900 — exactly 4:3 on both
axes, so the reconstruction pattern repeats every **4 output pixels**.

**Classification: E, reconstruction / upscaling.** Not raymarch sampling (A),
not density quantization (B), not 3-D texture interpolation (C), not
geometry (D). For reference the world-space candidates would have appeared at:
fineStep 2.5 blocks = 0.6 px, coarseStep 31.25 = 7.1 px, coarseStepCap 40 = 9.0
px, detail tile 45.45 = 10.3 px — none of which is 4 px at both poses.

The banding is therefore **independent of the anvil density question** and is a
separate defect.

## 7–12. Not entered

PHASE 7 gates candidate arms on the first failing stage being identified, and
PHASE 13 CASE C requires stopping before another fix. The first failing stage is
integration, not a detail or material stage, so none of the candidate classes
(A rederive detail wavelength, B raise detail contrast, C reduce saturation,
D fix sampling quantization) addresses it: A and B act on interior detail that
cannot reach the image, C is a 30x change, and D targets a banding source that
the measurement has now excluded.

No candidate was implemented, so the same-fixture A/B (PHASE 11) and the fresh
campaign (PHASE 12) have nothing to compare and were not run. No production code
changed; `./gradlew check` and `./gradlew build` were run against the added
measurement code only.

## 13. Verdict — CASE C

**T098 remains OPEN. T099 remains blocked.**

The first rendering stage that removes contrast is the **extinction
integration**. The anvil's density field carries substantial structure — CV
0.589, 40-block features, 25 across the canopy, 3.6 per cent true zeros — and
the unlit accumulated alpha carries none of it: variance 0.000000, 100 per cent
of rays above 0.97, saturating after 7.4 per cent of the chord.

The next investigation is therefore **not** the anvil's interior density, its
detail wavelength or its erosion amplitude. On an optically thick body the
visible contrast can only come from the shape of its boundary and from
self-shadowing across that boundary. The measurements that hand that
investigation its starting point:

1. the anvil coverage envelope is a plateau, CV 0.078, so the boundary is smooth
   by construction and the body never reaches zero inside it;
2. `detailFbm` has CV 0.156 and an 8-block feature size, so erosion is a
   constant offset and its texture is sub-pixel at these distances;
3. the upper-surface banding is a 4-pixel reconstruction beat and is unrelated
   to both.
