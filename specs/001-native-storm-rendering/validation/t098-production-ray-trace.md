# T098 production ray trace — instrument, first divergence, and correction

**Feature**: `001-native-storm-rendering`
**Task**: T098 (US1 morphology acceptance gate)
**Date**: 2026-08-31
**Base commit**: `c2a2f8e`

---

## 0. Why this instrument exists

The T128 centre-line trace (`stormMaterialTraceAt`, DebugView 21) evaluates
`directStormShape` and the material stages directly. It never executes
`cloudDensity`, never applies the outer weather-gated empty-space skip, and
never reproduces a camera ray. Its numbers therefore prove that a
descriptor/material field exists, not that the production marcher integrates
it, and the earlier claim that "the production shader carries density 0.81–0.91
at the waist" was not evidence about the marcher. That claim is retracted.

This document records an instrument that closes exactly that gap, the first
production divergence it found, and the one correction that divergence
authorises.

---

## 1. The instrument

`StormProductionRayTrace` + the `PaRayTraceMode` path in
`cloud_atmosphere_volume.fsh`.

- The **real** production march is instrumented in place. There is no second
  marcher. Every recorded value is written by a statement guarded by
  `paTraceCapture`, which cannot become true while `PaRayTraceMode == 0`.
- One ray is fixed by `PaRayTraceNdc` / `PaRayTraceFragCoord`. Every fragment
  of the trace pass marches *that* ray; a fragment's own `gl_FragCoord` selects
  which record it publishes — **column = march iteration (0..127 = MAX_STEPS),
  row = field group (0..20)**. The record is a fixed 128×21 corner of the
  existing cloud target: no new allocation, no unbounded log, and no dependence
  on how many iterations actually ran.
- Because the traced ray is fixed by uniform rather than by the fragment,
  `sceneRayLimit` and the blue-noise search phase are also taken from the
  traced pixel, so all fragments run one identical march.
- Arms: **0** = ordinary production frame, read back at the traced texel only;
  **1** = unmodified production march; **2** = the outer weather-gated
  empty-space skip disabled for that pass only, with every `cloudDensity` gate
  including its own weather coverage cut left intact.
- A pass is rejected unless it proves it was a trace pass: row 15 echoes each
  fragment's own row and column, and row 12 echoes the arm. This exists because
  the first attempt silently read back an ordinary rendered frame — the trace
  uniforms had not been declared in `cloud_atmosphere_volume.json`, so
  `safeGetUniform` was a no-op and the all-zero result would otherwise have
  been reported as a trace.

### Ray identity

Arm 0 reads the production alpha out of the traced texel on an ordinary frame.
The trace is taken **inside** the T098 capture set, immediately after the
`A_SIDE_CURRENT_ONLY` grab, at that shot's pose, render target and debug view,
so the traced ray and the captured pixel cannot differ in configuration.

Measured, every ray, every run: `productionTexel.a == tracedAlpha` to five
decimals — `identity=AGREES`. The waist ray traced below **is** the ray that
rendered the waist pixel.

---

## 2. Controlled ray selection

Fixed severe fixture, controlled SIDE pose (`centre + 1.7 × horizontalRadius`,
mid-height, looking at the centre) — the pose the T098 capture set already
uses. Pixels are chosen by projecting the fixture's own geometry through the
transform the cloud shader was last drawn with, not by hard-coded screen
coordinates.

| | world target | ndc | framebuffer | pixel | render target | fragCoord |
|---|---|---|---|---|---|---|
| **WAIST** | centre, baseY + 0.55·span | (0.00000, 0.02069) | 1600×900 | (800, 440) | 1200×675 | (600.5, 344.5) |
| BASE (control) | centre, baseY + 0.10·span | (0.00000, −0.17185) | 1600×900 | (800, 527) | 1200×675 | (600.5, 279.5) |
| ANVIL (control) | centre, baseY + 0.90·span | (0.00000, 0.17401) | 1600×900 | (800, 371) | 1200×675 | (600.5, 395.5) |

Camera `(1138.0, 570.5, −84.8)`, yaw 90, pitch 0. Reconstructed waist ray
direction `(−0.99902, 0.03662, −0.00262)`, `t0 = 0`, `t1 = 2000`.

---

## 3. What the trace found

### 3.1 The outer weather skip is falsified

Arm A and arm B are identical on every ray, every fixture: same
`cloudDensity` samples, same peak density, same final alpha, same termination.

| ray | A skips | A cloudDensity calls | B calls | max density A | max density B | alpha A | alpha B |
|---|---|---|---|---|---|---|---|
| WAIST | 18 | 110 | 128 | 0.66797 | 0.66797 | 0.78516 | 0.78516 |
| BASE | 15 | 83 | 98 | 1.00293 | 1.00293 | 0.98584 | 0.98584 |
| ANVIL | 17 | 52 | 69 | 1.28613 | 1.28613 | 0.98828 | 0.98828 |

Disabling the skip adds samples in empty space and changes nothing. **The
outer weather-gated empty-space skip is not the mechanism.** Phase 7 is not
entered.

### 3.2 `cloudDensity` is healthy

On the waist ray `cloudDensity` is called on 110 of 128 iterations,
`ownsDescriptorGroup` is true throughout the storm interior, `envelopeCoverage`
is 1.0 as expected for a descriptor-owned storm, the weather coverage gate
never rejects a descriptor-owned sample, and the returned density reaches
0.668. Classes B, C, D and the `cloudDensity` interior conditions are all
falsified. This is class **F**: the density function is fine and the loss is
after it.

### 3.3 First production loss: the depth sentinel

Measured on one frame, three rays, with the same instrument:

| ray | representativeT | march alpha | `resultDepth` | composite | on screen |
|---|---|---|---|---|---|
| ANVIL | 665.5 | 0.98730 | 0.99998683 | kept | cloud |
| BASE | 736.0 | 0.98633 | 0.99999416 | kept | cloud |
| **WAIST** | **912.0** | **0.63184** | **1.00000000** | **DISCARDED** | **sky** |

`cloudProjectionNear = 0.05`, **`cloudProjectionFar = 768.23761`** (render
distance 12), read from the transform the cloud pass was drawn with.

The chain:

1. The volume is marched to `MaxRenderDistance = 2000`. That is a cloud
   setting; it has nothing to do with the scene projection.
2. `resultDepth = currentCloudHit ? depthAt(relRepresentative) : 1.0`, and
   `depthAt` clamps to `[0, 1]`. The waist ray's alpha-weighted representative
   point is 912 blocks away, past the 768-block far plane, so the clamp
   returns **exactly 1.0**.
3. `cloud_field_composite.fsh` reads 1.0 as *absence of cloud*:
   `bool hasDepth = depths[i] < 1.0;` and then
   `if (selectedScore < 0.0 || selectedDepth >= 1.0) { discard; }`.
4. The alpha the marcher had integrated is discarded whatever its value. The
   pixel renders as clear sky.

The BASE and ANVIL controls are the contrast: same frame, same storm, same
code, representative points inside the frustum, depth below the sentinel,
composited normally and visible.

This is not an exotic case. It is every storm whose alpha-weighted material
centroid lies beyond the render distance — which is why the loss is
view-distance dependent, why NEAR_EDGE at 1.12× radius shows a column that
SIDE at 1.7× and LATERAL at 1.9× do not, and why FAR is missing as well.

### 3.4 Why the earlier stage isolation pointed the wrong way

The T098 stage isolation compared FINAL against CURRENT_ONLY and found them
identical, and concluded the column was "already missing in the raw
current-frame march before any temporal or composite stage runs". Both of
those images are post-composite. The comparison exonerates **history**, which
still stands; it never separated the march from the composite. The march
result at the waist was alpha 0.63–0.79, not zero.

---

## 4. The correction

`cloud_atmosphere_volume.fsh`, one expression:

```glsl
float resultDepth = currentCloudHit
    ? min(depthAt(relRepresentative), PA_CLOUD_HIT_MAX_DEPTH)
    : 1.0;
```

with `const float PA_CLOUD_HIT_MAX_DEPTH = 0.999999;`.

A hit never publishes the composite's miss sentinel; only a miss may write 1.0.

Deliberately **not** changed: morphology, density, role strengths, MAX_STEPS,
`exteriorFineStep`, history, composition rules, blend.

The bound is chosen so nothing else moves:

- It stays **above** the `0.99999` cutoff at which history already sets
  `depthConfidence = 0`, so temporal reprojection behaviour is unchanged.
- It stays **behind** any real scene depth, so the composite's occlusion test
  (`depths[i] <= sceneDepth + bias`, with sky short-circuited by
  `sceneDepth >= 0.99999`) still hides these clouds behind terrain exactly as
  before.
- It is ~17 quantization steps below 1.0 in a 24-bit depth buffer.

---

## 5. Regression

`StormVolumetricGeometrySandbox.validateT098CloudHitDepthNeverSaturates()`.

It reconstructs Minecraft's own projection depth mapping for render distances
8/12/16/24/32 chunks and sweeps representative distances 32..2000 blocks in
0.5-block steps, then asserts that a cloud hit is always composited.

```
T098_HIT_DEPTH|probes=19685|legacyDiscardedHits=8330|correctedDiscardedHits=0
T098_HIT_DEPTH_LEGACY_WITNESS|renderChunks=8 far=512.0 representativeT=512.0 legacyDepth=1.0
```

The guard **fails under the old behaviour** on two independent grounds: the
sweep requires the pre-fix expression to produce at least one discarded hit
(8330 of 19685 probes do), and the shader-source check requires the corrected
bound to be present. It also pins the composite's premise, so if
`cloud_field_composite.fsh` stops treating 1.0 as absence the guard demands the
reasoning be re-derived rather than silently passing.

A companion check keeps the before/after evidence arm (§6) diagnostic-only:
its uniform defaults to 0 in the shader JSON and its config flag defaults to
false.

---

## 6. Controlled before/after on one fixture

`PaLegacyHitDepth` is a diagnostic-only uniform that restores the pre-fix
saturating hit depth. The capture set takes `D_SIDE_LEGACY_HIT_DEPTH` /
`E_FAR_LEGACY_HIT_DEPTH` with it on and `F_SIDE_CORRECTED` /
`G_FAR_CORRECTED` with it off — one fixture, one run, identical poses, debug
view and frame configuration, differing only in the corrected expression.

Fixture `ecf2d27b`, non-sky pixel fraction:

| pose | legacy | corrected | delta |
|---|---|---|---|
| SIDE | 0.6845 | 0.6903 | +0.0058 |
| FAR | 0.5178 | 0.5366 | +0.0188 |

The recovered pixels are localised where the mechanism predicts: SIDE changes
confined to y 350–666 (x 558–1052) and FAR to y 311–565 (x 658–940), i.e. the
mid-storm band, with nothing changing at the near base or in the sky.

---

## 7. Second divergent branch — recorded, NOT fixed

The same trace measures a second, independent defect on the same ray, and it
is why the correction restores a faint band rather than a solid column.

Every traced waist ray terminates on **`step_cap`**, not on the transmittance
floor:

| fixture | waist iterations | termination | residual transmittance | alpha |
|---|---|---|---|---|
| `63b3e91c` | 128 | step_cap | 0.36792 | 0.63184 |
| `61d7f356` | 128 | step_cap | 0.43628 | 0.56348 |
| `66cafa20` | 128 | step_cap | 0.84277 | 0.15674 |
| `ecf2d27b` | 128 | step_cap | 0.92432 | 0.07532 |

The BASE and ANVIL controls on the same frames terminate on the transmittance
floor at 69–114 iterations with alpha ≈ 0.986.

From the iteration table, the waist ray is promoted to fine marching at
t ≈ 578 by the per-descriptor conservative clearance probe, and first
integrable material is at t ≈ 826. At `exteriorFineStep = 2.5` that is roughly
100 of the 128 available iterations spent crossing empty coverage envelope
before the first sample that can contribute, leaving too few to reach the
transmittance floor. The clearance probe is conservative and correct — it
reports `minClearance` going negative well before any material — but it is
loose by ~250 blocks on a tangential waist ray.

This is a different first divergent branch from the one corrected here. Per the
T098 instruction it is recorded and **not** investigated further in this pass.
Compensating for it with `MAX_STEPS` or `exteriorFineStep` is explicitly
forbidden and has not been done.

---

## 7b. Live campaign — five fresh severe fixtures

Five fresh worlds (seeds 776001..776005), one severe fixture each, each run
capturing the same-fixture before/after pair described in §6. Measured on the
centre column between the storm's own projected ANVIL and BASE rows, so terrain
and framing cannot contribute.

| seed | group | centre-column cloud share | longest inner sky run (px) | 400px band fill |
|---|---|---|---|---|
| 776001 | `32123d75` | 0.6981 → **0.9811** | 48 → **3** | 0.6519 → 0.7751 |
| 776002 | `4cb7d540` | 0.7707 → **0.8854** | 36 → **18** | 0.6496 → 0.7842 |
| 776003 | `9b30c698` | 0.7677 → **0.8903** | 36 → **17** | 0.6529 → 0.7673 |
| 776004 | `044d01de` | 0.7320 → **0.8824** | 39 → **9** | 0.6424 → 0.7570 |
| 776005 | `87c5c9de` | 1.0000 → 1.0000 | 0 → 0 | 1.0000 → 1.0000 |
| **mean** | | **0.7937 → 0.9278 (+0.1341)** | **31.8 → 9.4 (−22.4)** | **0.7193 → 0.8167 (+0.0974)** |

`87c5c9de` is the control the campaign happened to supply: a fixture whose waist
material already sat inside the far plane, unchanged by the correction, exactly
as the mechanism predicts.

Fixtures still showing an inner sky run of 10 px or more after the correction:
**2 of 5**, down from 4 of 5 at 36 px or more.

### T098 checklist grading

The correction removes the depth-sentinel loss and the connecting column now
composites, but the positive half of the checklist is not satisfied:

- FR-023 #1 broad continuous lower cloud base — **present**
- FR-023 #2 dense convective/core region — **present**
- FR-023 #3 vertical tower development from the base — **partial**; the column
  is present but thin where the waist ray starves (§7)
- FR-023 #4 progressive vertical narrowing — **partial**
- FR-023 #5 broad upper anvil — **present**
- FR-023 #6 multi-scale billowing across the visible body — **absent in the
  waist band**, which integrates only 0.08–0.63 alpha
- FR-023 #7 surface variation at multiple spatial frequencies — **partial**
- FR-023 #8 irregular but coherent silhouette curvature — **present**
- FR-023 #9 continuous transitions base→tower→core→anvil — **not satisfied on
  2 of 5 fixtures**, which retain a 17–18 px sky run on the centre column

The negative half is not re-graded here; this pass changed no morphology.

## 8. Regression results

- `./gradlew check` — BUILD SUCCESSFUL
- `./gradlew build` — BUILD SUCCESSFUL

Retained suites passing in that run include `StormVolumetricGeometrySandbox`
(T074–T079, T111 production shader compilation, T121/T122 guards, T133
optimization-neutrality, all retained T098 guards and the per-descriptor
conservative advance validation `T098_MARCH_GUARD`), the morphology and density
sandboxes, `MaterialAdvectionSandbox`,
`StormMaterialContinuityDiagnosticsSandbox`,
`VolumetricStabilityDiagnosticsSandbox` (T078/T079), and the new
`T098 cloud hit depth never saturates` guard.

---

## 9. Verdict

**T098 remains OPEN.** One proven production defect is corrected and guarded,
and the effect is real and measured: across five fresh severe fixtures the
centre-column cloud share rises 0.794 -> 0.928 and the longest inner sky run
falls 31.8 px -> 9.4 px, on a same-fixture A/B whose only difference is the
corrected expression. The trace and the image agree at the traced waist pixel.

It is not enough to pass. Two of five fixtures keep a 17-18 px sky run on the
centre column, and the waist band that does appear integrates only 0.08-0.63
alpha because of the second divergent branch in section 7. FR-023 #6 is absent
in that band and #9 is not satisfied on those two fixtures, so the positive half
of the two-part checklist fails. **T099 remains blocked.**

The next blocker is now named and measured, and it is not the depth sentinel,
not cloudDensity, and not the outer weather skip: it is that a tangential waist
ray spends roughly 100 of its 128 iterations in fine mode crossing empty
coverage envelope between the conservative clearance promotion at t~578 and
first integrable material at t~826.
