# T098 ANVIL optical surface — the surface is structured; the self-shadow pins it flat

**Feature**: `001-native-storm-rendering`
**Task**: T098 (US1 morphology acceptance gate)
**Date**: 2026-09-01
**Follows**: `t098-anvil-surface-structure.md`
**Result**: **CASE C.** No production change.

---

## 0. The reframing that this pass tested

The previous pass established that the anvil is opaque after ~75 blocks of a
1015-block chord, so interior density variation cannot reach the image. The
right question became: what *does* control the visible surface, and is that
surface smooth?

The answer is that **the optical surface is not smooth at all**. It carries tens
of blocks of relief at every scale. What is flat is the *shading* on it: 83.6
per cent of the visible surface is fully self-shadowed and therefore pinned to
an ambient floor that has no spatial variation.

---

## 1. Alpha-threshold surface profiles

Rays marched with the production step, extinction and thresholds. SIDE looks
along −X through the canopy; ABOVE looks down −Y. 6-block ray spacing.

| view | arm | rays | mean t(α=0.5) | relief RMS | p05 | p95 | neighbour Δ | ramp t₁₀→t₉₀ |
|---|---|---|---|---|---|---|---|---|
| SIDE | production | 9398 | 193.6 | **58.375** | −82.34 | +108.61 | 11.290 | 41.74 |
| SIDE | erosion off | 9407 | 178.4 | 50.628 | −69.18 | +92.12 | 8.914 | 28.00 |
| ABOVE | production | 27556 | 262.2 | **27.724** | −44.32 | +47.66 | 9.166 | 47.66 |
| ABOVE | erosion off | 27556 | 249.9 | 22.727 | −37.77 | +38.71 | 7.498 | 35.46 |

All depths and reliefs in blocks. `erosion off` sets `detailFbm = 1`, which is
exactly "no erosion" in the production expression
`max(body − (1 − detailFbm) · 0.44, 0)`.

## 2. Where surface displacement collapses — it does not

Relief RMS of each successive surface, SIDE:

| surface | relief RMS (blocks) |
|---|---|
| geometric envelope (coverage crossing 0.5) | 116.006 |
| first non-zero density | 49.812 |
| α = 0.10 | 53.645 |
| α = 0.50 | **58.375** |
| α = 0.90 | 80.502 |

ABOVE: envelope 82.223, first density 22.703, α₁₀ 25.046, α₅₀ 27.724, α₉₀
29.104.

There is **no stage at which surface displacement collapses.** The alpha
surfaces carry displacement of the same order as the geometry that generates
them. PHASE 2's first branch ("A–E all smooth → boundary pipeline is the
blocker") and third branch ("first-density detailed but α=0.5 smooth → the ramp
is too thick") are both falsified. The near-surface ramp is 41.7 blocks
(SIDE) — thick, but not thick enough to erase 58 blocks of relief.

## 3. Relief by scale, and what erosion contributes

Canopy interior only, so silhouette curvature cannot masquerade as relief.
Detrended at increasing windows: a wide window keeps large features in the
residual, a narrow one keeps only fine ones.

| window (blocks) | relief RMS production | erosion off | **erosion contribution** | projected px at SIDE |
|---|---|---|---|---|
| 12 | 20.021 | 16.130 | **3.891** | 2.71 |
| 24 | 32.313 | 27.578 | **4.735** | 5.42 |
| 48 | 58.674 | 54.375 | **4.300** | 10.83 |
| 96 | 92.340 | 86.184 | **6.156** | 21.67 |
| 192 | 111.690 | 104.958 | **6.733** | 43.33 |

Two results:

- **The surface has ample relief at readable scales.** 58 blocks RMS at the
  48-block scale is about 11 output pixels at the SIDE pose; 92 blocks at the
  96-block scale is about 22 pixels. This is not a balloon in geometry.
- **Erosion barely moves it.** Its contribution is 3.9–6.7 blocks at *every*
  scale — roughly 1 output pixel — while it changes interior density
  substantially (`finalDensity` CV 0.589). PHASE 5's hypothesis is confirmed
  exactly: production erosion varies interior density and does not move the
  visible surface.

## 4. Per-role comparison

One SIDE sweep of the whole storm, points attributed to the role owning the
α=0.5 point:

| role | points | relief RMS | neighbour Δ |
|---|---|---|---|
| BASE | 13106 | 50.216 | 17.336 |
| TOWER | 616 | 21.734 | 8.459 |
| **ANVIL** | 7663 | **43.264** | 10.445 |

The anvil's optical surface is nearly as rough as the base's, and rougher than
the tower's. The base *reads* billowy and the anvil does not, at comparable
surface relief — which means the difference is not in the surface.

## 5. Self-shadow response — the actual blocker

The production light cone, modelled exactly: eight taps, 14-block first step
growing by 1.42 (≈520-block reach), fixed golden-angle cone offsets, detail
erosion on the first two taps only, light direction (−0.60, 0.79, 0.12) from the
frozen noon fixture.

| quantity | value |
|---|---|
| light optical depth at the α=0.5 surface | mean **17.040**, p05 0.740, p50 **16.425**, p95 37.891 |
| direct transmission `exp(−OD)` | mean 0.05583, p05 **0.00000**, p50 **0.00000**, p95 0.47694 |
| **fraction of surface with direct light < 0.01** | **83.57 %** |

The sun term is not raw `exp(−OD)` — `evaluateLightingComponents` uses a
three-octave scatter approximation whose slowest term is `exp(−0.1764·OD)`, so
it keeps responding well past single-scatter extinction. Modelling that term and
the full radiance, including the beer-powder term, ambient retention keyed on
direct transmission, and the filmic tone curve:

| quantity | value |
|---|---|
| third-octave scatter | mean 0.19055, CV **1.434** |
| surface luminance | mean 0.45911, p05 **0.40731**, p50 **0.41366**, p95 **0.75211** |
| luminance CV | 0.23109 |
| neighbour Δ (8 blocks apart) | 0.01660 (≈4 of 255) |
| p05→p95 range | **87.9 of 255** |

Read the percentiles rather than the CV. **p05 = 0.40731 and p50 = 0.41366**:
at least 45 per cent of the canopy lies within **0.007 luminance — under two
levels of 255 — of the same value.** That value is the ambient floor. The
distribution is not spread; it is a large flat majority at the floor plus a
small bright minority reaching 0.752.

The model reproduces the render. Sampled anvil pixels in the captured SIDE
frame sit at RGB (85, 107, 144), luminance **0.412** — the measured p50 of
0.414. The bright upper region measures 0.81 against the modelled p95 of 0.752.

So: **the surface carries 58 blocks of relief, and the shading cannot express
it, because over most of the canopy the light cone is saturated and the pixel is
pinned to an ambient term that has no spatial variation at all.**

*Assumption stated:* storm darkening, underside shading and rain are held at
neutral. Those scale absolute brightness (sun ×0.76, ambient ×0.68 at full storm
darkening) but not the spatial variation, which is what this measures.

## 6. Required projected feature scales

At the SIDE pose the storm's 864-block height spans about 195 output pixels, so
one pixel is 4.43 blocks. For features to read:

| feature class | needs (px) | needs (blocks) | present today |
|---|---|---|---|
| macro billows | 40–100 | 180–440 | surface relief 92–112 RMS: **yes** |
| meso billows | 12–40 | 55–180 | surface relief 58–92 RMS: **yes** |
| fine erosion | 3–12 | 13–55 | surface relief 20–32 RMS: **yes**, but erosion adds only ~4 blocks ≈ 1 px |

The geometry already supplies all three bands. The `detailFbm` decorrelation of
8 blocks (1.8 px) noted in the previous pass is below the readable range, but
that is now a secondary point: erosion's contribution to *surface position* is
about one pixel regardless of scale.

## 7. Candidate arms — none run

PHASE 8's classes are gated on identifying a surface-stage failure. There is
none:

- **A, increase low-frequency boundary displacement** — the surface already has
  92–112 blocks RMS at the 96–192-block scales. Not indicated.
- **B, rederive erosion so it moves the surface** — would raise erosion's ~4
  blocks toward the ~50 the geometry already provides; it would add a little
  where 58 blocks are already invisible. Not the blocker.
- **C, change the near-boundary ramp** — the ramp is 41.7 blocks and does not
  erase the relief that crosses it. Not indicated.
- **D, improve self-shadowing** — gated on "only if optical surface geometry is
  already sufficiently structured". It **is**, and this is where the loss is.
  PHASE 14 CASE C requires stopping before that change, so it is recorded as the
  next isolated blocker and not implemented.

No production code changed. `./gradlew check` and `./gradlew build` were run
against the added measurement code.

## 8. Verdict — CASE C

**T098 remains OPEN. T099 remains blocked.**

The optical surface is already strongly structured — 58.4 blocks RMS relief at
the SIDE α=0.5 surface, 43.3 for the anvil specifically against the base's 50.2,
with usable amplitude at macro, meso and fine scales — and the rendered result
is still smooth because the self-shadow term is saturated across it.

**The next isolated blocker is the lighting / self-shadow response**, with these
measurements as its starting point:

1. light optical depth at the visible surface is a median of **16.4**, where
   about 4.6 already gives one per cent transmission;
2. **83.6 per cent** of surface points receive less than one per cent direct
   light, and the resulting luminance p05→p50 spans under two levels of 255;
3. the multi-octave scatter is the only term still responding at that depth —
   third octave mean 0.191, CV 1.434 — so the response exists but is applied to
   a term that is already near its floor over most of the canopy;
4. surface relief is **not** the deficiency and should not be increased to
   compensate.

The 4-pixel reconstruction beat measured in the previous pass remains a separate
defect and is **not** yet the sole blocker: the self-shadow response is ahead of
it.
