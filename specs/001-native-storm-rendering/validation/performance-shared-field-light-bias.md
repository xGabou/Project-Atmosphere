# T198 - the light field's bias was erosion and aliased noise; the tap now evaluates both, and the pockets return

Branch `experiment/cloud-descriptor-k`, base `a5e81d4` (T197). Nothing merged. Two
launches on 11Sep2026, both Ultra, raw scale 0.2500, cloud target 480x270, 96
steps, 60 samples per cell, self-exiting: launch 1 (16:17-16:24) the tap-level
audit alone on the T196 field; launch 2 (16:28-16:35) the audit of both
lookups, the correction arms timed against the T196 control with three
anchor-bracketed blocks per pose, the quality captures, and the soundness
oracle on the corrected build. The decision point between them was taken on
launch 1's numbers.

## 1. Baseline and what the field replaces (T198a)

The field stays 256x256x32 (`StormFieldGeometry.DEFAULT_XZ`, reset at campaign
start; no T197 marker). The production light cone is four taps at 14, 33.9,
62.1 and 102.2 blocks along `LightDir`, each `cloudDensity(pos + offset, 0.6 i,
i < 2, false, false)`: taps 0-1 carry the detail stage, taps 2-3 do not, and
every tap carries a mip bias of 0.6 i on the base-noise fetch. For a
descriptor-owned storm the density is formed in this order: the descriptor walk
gives a coverage envelope, a strength and a height; STAGE 5 forms the body as
`saturate((stormBaseField(noise) - L) / (1 - L))` with `L =
stormCoverageLowerBound(coverage, strength, overlap)` from one base-noise fetch
at the point; the detail stage (taps 0-1 only) fetches the detail noise and
subtracts `(1 - detailFbm) * 0.44`; the material terms scale by height with
descriptor constants; then x0.73 x DensityMul. Weather and morphology rasters
are read but, for a descriptor storm, decide nothing.

T196's R channel was `cloudDensity(node, 0, false, false, false)` at the node
centre - the whole chain, detail-free, at mip 0 - read trilinearly. **The T196
hybrid** (`PaStormFieldEnabled` bit 4) put taps 0-1 back on the exact walk with
their detail and left taps 2-3 on the field.

## 2. Tap-level error (T198b) - launch 1, SIDE, field minus exact, density units

The audit program (`t198_light_audit`, one program, `PA_LIGHT_AUDIT`) marches
as `t196_field_light` and evaluates the exact production density beside every
field tap, accumulating per pixel and per tap; eight accumulators x eight
decomposition variants = 64 captures per pose, joined on the CPU with the
anchor frame's pixel classes (`StormLightTapErrorReport`). Means, RMS and the
over/under fractions are exact over the light samples; medians and p95 are
over per-pixel means.

| tap | region | samples | mean | median (px) | RMS | p95 |err| (px) | over | under | mean exact | mean field |
|---|---|---|---|---|---|---|---|---|---|---|
| 0 | pocket | 98,421 | **+0.252** | +0.254 | 0.296 | 0.380 | **95.0%** | 4.6% | 0.572 | 0.824 |
| 1 | pocket | 98,421 | **+0.244** | +0.261 | 0.300 | 0.393 | 94.4% | 3.5% | 0.569 | 0.814 |
| 2 | pocket | 98,421 | -0.043 | -0.014 | **0.212** | 0.296 | 36.8% | 56.3% | 0.779 | 0.737 |
| 3 | pocket | 98,421 | -0.074 | -0.028 | **0.253** | 0.390 | 30.6% | 54.4% | 0.671 | 0.597 |
| 0 | normal interior | 332,081 | +0.266 | +0.265 | 0.305 | 0.382 | 96.4% | 3.2% | 0.479 | 0.745 |
| 0 | dark interior | 77,221 | +0.262 | +0.262 | 0.291 | 0.365 | 97.1% | 2.9% | 0.748 | 1.011 |
| 0 | edge | 216 | +0.082 | +0.013 | 0.255 | 0.411 | 65.3% | 19.9% | 0.234 | 0.315 |
| 2 | normal interior | 332,081 | -0.062 | -0.008 | 0.256 | 0.489 | 42.7% | 49.8% | 0.651 | 0.589 |
| 3 | normal interior | 332,081 | -0.091 | -0.014 | 0.295 | 0.581 | 35.8% | 46.7% | 0.520 | 0.429 |

Cone optical depth (each tap's mean error times its step): pockets **+4.20 of
68.2 (+6.2%)**, normal interior +3.12 of 55.1 (+5.7%), dark interior +8.12 of
91.9 (+8.8%), edge +2.94 of 18.7. FAR pockets +2.09 of 62.9 (+3.3%). The
pocket bias is not distinct from the interior's: the field over-extincts
everywhere by the same two mechanisms, and pockets are where a 6% denser cone
turns a local minimum into a plateau.

**The luminance link** (T198d): per pixel, the cone optical-depth error
against the light-only frame's luminance error, inside the pocket regions:
**r = -0.63 at SIDE, -0.67 at FAR** (launch 1; -0.69 / -0.62 in launch 2).
More field optical depth, darker pixel; the pocket darkening T197 measured
(-0.0066 here, -0.0065 there) is the field's over-extinction.

## 3. Decomposition (T198c) - launch 1, SIDE, pocket region, cone optical depth

Each variant changes what the exact side evaluates; the error it takes with it
is that term's share of the field's error.

| term removed from the exact side | error before | error after | bias removed | note |
|---|---|---|---|---|
| detail noise at its mean (erosion keeps its mean bite) | +4.20 | +3.82 | +0.37 | the noise part of the erosion |
| **erosion stage off** | +4.20 | **-4.99** | **+9.18** | taps 0-1: +0.280 / +0.265 a tap; taps 2-3: 0 |
| mip bias 0 | +4.20 | +4.20 | **0.000** | the base-noise sampler carries no mip effect at these biases |
| weather neutral | +4.20 | +4.20 | 0.000 | a descriptor storm reads nothing from the raster |
| morphology neutral | +4.20 | +4.20 | 0.000 | likewise |
| erosion off + mip 0 | +4.20 | -4.99 | +9.18 | = erosion alone |
| **the field's own node equation at the tap** (build-mode walk, mip 0, no detail) | +4.20 | **-4.99** | +9.18 | = erosion off + mip 0: the build-mode walk contributes **0.000** |

So the split is exact: **representation +9.18** (the erosion the node
density never applied, on the two near taps), **interpolation -4.99** (the
node density read trilinearly against the same equation evaluated at the tap),
net +4.20. FAR pockets: representation +8.74, interpolation -6.65, net +2.09.

The interpolation term is not smoothing of a smooth field. The base noise is a
128^3 texture over 400 blocks - a **3.1-block texel** - and the node's density
is one point sample of it at 16-block spacing; interpolating that is aliasing.
That is why it has a small mean (-0.04 / -0.07 on taps 2-3) and a large RMS
(0.21 / 0.25 on a mean density of 0.7), why per pixel it is uncorrelated with
the erosion term (r = -0.15; the erosion explains the mean, not the
variance), and why T197 found the pocket loss invariant from 16 to 8 blocks a
node: the noise is finer than any grid tested.

**Identified root cause (T198d).** Two terms, both measured: (1) the near taps
miss the erosion stage - a systematic +0.26 density a tap, 95% of samples
over, +9.2 cone optical depth; (2) every tap reads a point sample of a
3-block noise interpolated over 16-block nodes - a -5.0 cone optical depth
mean with 25-35% RMS per tap that erases the fine structure the pockets are.
Mip bias, weather, morphology and the build-mode walk contribute nothing. The
"detail-free R" hypothesis is confirmed for the mean (the erosion) and
incomplete for the structure (the aliasing); either alone would not restore
the pockets.

## 4. The T196 hybrid, and why T198 differs (T198e)

The hybrid re-ran the whole walk on taps 0-1 with their detail and left taps
2-3 on the node density. It therefore removed term (1) entirely - +9.2 - and
left term (2) on the far taps: the cone went from +4.2 to about -4.2 (brighter:
dark-interior retention fell to 0.899, contrast rose to 1.19) while the far
taps' 0.21-0.25 RMS aliasing kept the pockets smeared (0.723). It fixed the
mean and not the variance, at half the light gain.

**T198's correction never re-runs the walk and never reads an aliased density.**
The field's R channel becomes the storm body's exact threshold `L` at the node
and B its height01 - two envelope quantities, smooth at the node scale,
published by the one walk the build already does - and the light tap
interpolates those two and evaluates the base noise at its own point (one 3D
fetch), forms the body exactly as STAGE 5 does, applies the detail fetch and
the erosion on taps 0-1, and the material terms. It is the probe's own
architecture (a threshold from the field, the exact noise at the point)
applied to the light. The T196 density moves to A so the T196 arms still run;
the field is RGBA16F, 16 MiB at 256. Consumer bit 8 selects it;
`t198_field_light_exact` (9) and `t198_field_both_exact` (11) are the two
programs added, the audit program keeps its consumer bits live and measured
both lookups.

## 5. Quality (Task 5), launch 2, against the LEAN_FINAL anchor

| pose | arm | **pockets** | regions | dark interior | contrast | valley | cloud SSIM | edge SSIM | IoU | changed px | RMS | maxAbs |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| SIDE | t196_field_light (control) | 0.644 | 0.661 | 0.986 | 0.974 | 0.798 | 0.925 | 0.997 | 1.000 | 24,089 | 0.0155 | 0.370 |
| SIDE | **t198_field_light_exact** | **0.880** | **0.901** | 0.974 | 1.257 | 1.020 | 0.941 | 0.997 | 1.000 | **10,908** | 0.0200 | 0.454 |
| SIDE | t196_field_both (control) | 0.625 | 0.644 | 0.986 | 1.078 | 0.841 | 0.899 | 0.943 | 0.994 | 24,380 | 0.0277 | 0.990 |
| SIDE | **t198_field_both_exact** | **0.861** | **0.884** | 0.974 | 1.346 | 1.063 | 0.914 | 0.944 | 0.994 | 11,401 | 0.0304 | 0.990 |
| FAR | t196_field_light (control) | 0.739 | 0.719 | 0.930 | 0.897 | 0.772 | 0.945 | 0.978 | 1.000 | 3,056 | 0.0066 | 0.394 |
| FAR | **t198_field_light_exact** | **0.923** | **0.956** | 0.913 | 1.086 | 0.942 | 0.961 | 0.977 | 1.000 | **1,531** | 0.0067 | 0.394 |
| FAR | t196_field_both (control) | 0.705 | 0.711 | 0.930 | 1.091 | 0.837 | 0.879 | 0.960 | 0.990 | 3,176 | 0.0141 | 0.990 |
| FAR | **t198_field_both_exact** | **0.874** | **0.939** | 0.913 | 1.260 | 1.003 | 0.893 | 0.959 | 0.990 | 1,738 | 0.0141 | 0.990 |

This launch's control reads 0.644 at SIDE (T197's 0.705; the fixture's storm
drifts between launches - the audit's production numbers moved 5% too), so the
comparisons are within the launch.

Pocket diagnostic (light alone): SIDE pocket luminance error -0.0032 -> +0.0081
(the sign flips: the cone is now slightly under-extincted), error over depth
0.228 -> 0.157, well depth ratio 0.880 -> 1.074, **wall displacement 1.01 ->
0.13 px** (signed +0.95 -> -0.09), walls beyond reach **701 -> 14** of 2,500,
**211 of the control's 279 lost pockets recovered, 26 newly lost**. FAR: 0.46 ->
0.12 px, 84 -> 4 walls beyond reach, 40 of 60 recovered, 2 newly lost. The
mask images (`t198-campaign/*_mask.png`) show the recovered pockets across the
whole anvil interior; the difference images show the interior neutral and the
residual (red, brighter) confined to the anvil's top rim.

**The remaining error, from the corrected audit** (launch 2, lookup
`t198_exact`, production variant, SIDE pockets): taps 0-1 collapse to mean
-0.009 / -0.015, |mean| 0.013 / 0.018, RMS **0.066 / 0.105** (from 0.296 /
0.300); taps 2-3 stay at mean -0.037 / -0.064, |mean| 0.042 / 0.071, RMS 0.183
/ 0.241 (from 0.211 / 0.262) - **one-sided** (mean |error| equals the mean:
almost every far-tap error is an under-estimate) and concentrated (RMS four
times the mean: a few taps read near zero where production has material).
Cone: **-4.0 of 68.4 (-5.9%)** at SIDE, -5.3 of 63.9 at FAR, against +4.2 /
+2.1 before. That signature - far taps, one-sided, at the storm's top where the
difference image is red - is the envelope's *vertical* representation: taps
62-102 blocks up the cone reach the anvil's upper boundary, where a node above
it holds `L = 1` (or lies above the slab) and the interpolated threshold, or
the fallback to zero, under-states the body. It is the axis T197 held constant
and the vertical term T197 found dominating the probe dilation; it is not
horizontal (T197) and not the noise (removed). It costs the preferred 0.90 at
SIDE (0.880) and pushes the self-shadow contrast ratio above 1 (1.26 / 1.35).

## 6. Performance (Task 6), launch 2, paired against the LEAN_FINAL anchors

| pose | arm | anchor p50 | march p50 / p95 | build p50 / p95 | combined | march ratio | **net** | spread | blocks |
|---|---|---|---|---|---|---|---|---|---|
| SIDE | t196_field_both (control) | 32.59 | 13.14 / 13.90 | 1.65 / 2.26 | 14.79 | 2.481 | 2.204x | 5.69% | 3, spread-rejected (blocks 2.15-2.27) |
| SIDE | **t198_field_both_exact** | 32.40 | 13.40 / 14.22 | 1.66 / 1.87 | **15.06** | 2.417 | **2.151x** | 2.32% | 2 accepted (one anchor drift 4.3%) |
| FAR | t196_field_both (control) | 19.58 | 10.86 / 11.14 | 1.55 / 1.91 | 12.41 | 1.804 | 1.579x | 0.96% | 3 accepted |
| FAR | **t198_field_both_exact** | 19.59 | 10.85 / 11.17 | 1.55 / 1.93 | **12.40** | 1.806 | **1.580x** | 1.66% | 3 accepted |

`T198_REJECTED count=0`. The correction costs **+0.27 ms on the SIDE march
(+2%)** and nothing at FAR; the build is unchanged (+0.004 ms: the two extra
channels come from the walk it already does); memory doubles to 16 MiB
(RGBA16F). Added per tap: one base-noise fetch (13.0 field fetch pairs / px at
SIDE, unchanged, plus 13.0 noise fetches) and, on taps 0-1, one detail fetch
(`detailFetchLight` 894,827, 6.9 / px; detail octave evaluations 13.7 -> 34.4
/ px, production 36.5). Net SIDE 2.151x is the brief's excellent band
(>= 1.50x) and T197's 2.151x to the third decimal.

## 7. Probe unchanged (Task 7)

The G channel and the probe lookup are untouched; the oracle ran on the
corrected build at both poses: **underestimates 0**, binary misses 0,
useful-empty 98.57% / 99.10%, overestimate mean 0.308 / 0.307 (identical to the
control's oracle in the same launch). On the ray, `stormFieldProbeFalseEmpty=0`
under both lookups; false-occupied 79,708 (control) / 79,230 (corrected),
probe fetches 3.94 / 3.94 per px. Attribution under the corrected field, SIDE:
rain segment 62.3%, primary 26.5%, refine 7.3%, shaft 3.8%, light 0, probe 0,
untagged 0, residual -169 of 3.78M, closed - the same distribution as the
control to a tenth of a percent.

## 8. Decision (Task 9)

1. Pocket retention materially recovers: light alone **0.644 -> 0.880 SIDE**,
   **0.739 -> 0.923 FAR**; both consumers 0.625 -> 0.861, 0.705 -> 0.874.
   FAR light-only meets the preferred 0.90; SIDE light-only is in the
   acceptable band (>= 0.85) with the visual support the brief asks for: the
   recovered pockets cover the anvil interior and the residual is a top-rim
   brightening, not a smear.
2. Dark interiors: 0.974 SIDE (control 0.986), 0.913 FAR (0.930) - kept, with
   a small loss to the far-tap under-extinction.
3. Other quality: cloud SSIM up (0.925 -> 0.941, 0.945 -> 0.961), edge SSIM
   and IoU unchanged, changed pixels halved; the self-shadow contrast ratio
   overshoots (1.26 / 1.09 light-only) for the same reason as item 2.
4. Probe soundness unchanged: 0 underestimates, 0 false-empty on the
   corrected build.
5. Net SIDE 2.151x >= 1.20x; FAR 1.580x.

**LIGHT QUALITY: PASS** (acceptable band at SIDE, preferred band at FAR).
**SHARED FIELD PRODUCTIONIZATION READY: YES**, with one named residual: the
far taps' one-sided under-estimate at the storm's upper boundary (section 5),
the vertical axis, which the fresh FINAL baseline should re-measure with the
audit before any further light work. **Exact next task: productionize the
shared field into FINAL** - a FINAL variant with `PaStormFieldPass` live and
consumer bits 11 baked, the dev-client `leanFinalEligible` contract, the
dirty-state rebuild measured on a moving fixture (T196 Task 11), campaign-only
plumbing removed where appropriate, then a fresh FINAL performance and quality
baseline; the tighter probe floor stays deferred behind that.

## 9. Infrastructure

- `campaignGate` 41 s (`gate-t198-campaign.log`); `check build` in section 10.
- Variants: declared 155, T198 registered 5 (`t196_field_light`,
  `t196_field_both`, `t198_light_audit`, `t198_field_light_exact`,
  `t198_field_both_exact`), skipped 150, cross-campaign controls 6,
  production always loaded 2. Three programs added (audit + two arms).
- The audit's per-pixel accumulators travel through the reference-image
  capture (T188's rain-mask path), not the workload views, because the
  workload readback sums and clamps negatives; `T175_WORKLOAD_VIEWS` stays 47.
- `T198_LIGHT_AUDIT` sandbox invariant: the audit and the two arms compile in
  GL, FINAL and the T196 arms define no audit and bake both selectors to 0,
  every decomposition toggle exists in the source.
- Launch 1: 6 min 40 s end to end, 2 min 33 s inside the client, 64 audit
  captures per pose in 17 s. Launch 2: 6 min 50 s, 13 timed arms, 132
  captures per pose.

## 10. Validation

- `./gradlew.bat campaignGate` PASS (41 s).
- `./gradlew.bat check build` PASS (`gate-t198.log`, 86 s).
- Markers: `run/t198-light-field-bias.txt` retired; `run/t132-autorun.txt`
  and `run/t132-autorun-exit.txt` remain.

## 11. Status

Not merged. T197 `a5e81d4`.
