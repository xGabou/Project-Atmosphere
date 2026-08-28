# Derived Morphology Thresholds

**Feature**: `001-native-storm-rendering`
**Created**: 2026-08-19
**Measured**: 2026-08-19 (T100)
**Purpose**: Satisfy FR-026 and SC-016 by deriving every positive-morphology threshold from the
rendering model instead of choosing numbers that make a test pass.

**Status**: MEASURED. The estimates originally drafted here were replaced by direct measurement of
the production noise volumes. `stormDensityThresholdSandbox` re-bakes the noise on every run,
re-derives every constant below, and fails if a recorded value drifts by more than ±0.010. A drift
is a defect in this document, not a reason to retune a threshold.

## How the measurement is taken

`CloudNoiseFieldModel` was extracted from `CloudNoiseTextureManager` so the tiling Perlin/Worley
math is GL-free and has exactly one implementation. The sandbox bakes the real 128³ base volume and
32³ detail volume, then samples them through the same domain transforms the shader uses
(`baseNoiseDomain` at the storm scale, `detailNoiseDomain`), with the same trilinear/repeat filtering
and 8-bit quantization.

```powershell
.\gradlew.bat stormDensityThresholdSandbox --console=plain
```

Sample set: 262 144 deterministic points (seed `0x57011CE5`) across a 2048 × 320 × 2048 block volume
— larger than one noise tile on every axis.

## Model constants read from the shader

| Symbol | Meaning | Value |
|---|---|---|
| `E` | `STORM_EROSION`, erosion amplitude for descriptor-owned storms | `0.44` |
| `w1, w2, w3` | detail FBM octave weights | `0.625`, `0.25`, `0.125` |
| `baseScale` | storm base-noise domain scale | `0.0025` block⁻¹ |
| `detailScale` | detail-noise domain scale | `0.022` block⁻¹ |
| base octave periods | Worley FBM periods in the base volume | `8, 16, 32` |
| detail octave periods | Worley FBM periods in the detail volume | `2, 4, 8` |

Derived wavelengths: one base tile spans `1/0.0025 = 400` blocks, so the base octaves have
**50.0 / 25.0 / 12.5** block wavelengths. One detail tile spans `1/0.022 = 45.45` blocks, so the
detail octaves have **22.7 / 11.4 / 5.7** block wavelengths, each itself a three-octave FBM reaching
down to **1.4** blocks. The final density therefore carries primary base billows at 50 blocks,
secondary base billows at 25 and 12.5 blocks, and surface breakup from 22.7 down to 1.4 blocks.

## Measured noise statistics

| Statistic | Measured | Used by |
|---|---:|---|
| detail FBM standard deviation | **0.0742** | SC-012 |
| detail FBM 5th percentile | **0.3568** | `CORE_FILL` derivation |
| Perlin-Worley carrier p05 | **0.7128** | base field normalization |
| Perlin-Worley carrier p95 | **0.8451** | base field normalization |
| storm base field standard deviation (after normalization) | **0.3294** | SC-012 |
| base Worley FBM standard deviation | **0.0788** | reference |
| detail variance share, octave R | **0.8520** | SC-014 |
| detail variance share, octave G | **0.1225** | SC-014 |
| detail variance share, octave B | **0.0255** | SC-014 |

The raw carrier spans only `0.7128 … 0.8451` — it is strongly high-biased, which confirms the
earlier archived observation. Feeding it straight into a coverage remap would push the whole storm
toward full density, so the storm base field is the carrier normalized onto its own measured
percentile range (`smoothstep(p05, p95, carrier)`), giving the well-distributed field whose
standard deviation is 0.3294. This is the same convention the PUFF path already uses.

Measured detail band shares track the nominal weight-squared shares (`0.833 / 0.133 / 0.033`)
closely, confirming the octaves have comparable per-channel variance.

### T124 correction re-measurement (base scale `0.0025`)

The scale and warp correction was remeasured rather than assumed statistically unchanged. The fresh
262,144-sample run produced detail FBM SD **0.0742**, detail p05 **0.3568**, carrier p05/p95
**0.7121 / 0.8453**, normalized base-field SD **0.3287**, low-FBM SD **0.0790**, and detail shares
**0.8520 / 0.1225 / 0.0255**. All remain inside the recorded ±0.010 measurement tolerance; no
statistical validation threshold was manually retuned. The change is spatial: the measured dominant
base feature increased from **52.6** to **109.4 blocks**.

## Derived model constants

### Strength-aware `CORE_FILL`

The coverage remap lower bound remains monotonic in coverage, but its fill is selected from the
smooth-union envelope strength rather than assuming every descriptor reaches 1.0. The live T124
fixture has strengths `0.7832/0.8792` (BASE), `0.9485/1.0000` (CORE), `0.9700/0.9539` (TOWER), and
`0.8222/0.7231/0.7851/0.7992` (ANVIL). At a descriptor's own coverage ceiling `s`, the weakest
base-noise point is retained when:

```text
bite      = E * (1 - detailFbm_p05) = 0.44 * (1 - 0.3568) = 0.2830
body(0,s) = 1 - 1 / ((1 + fill(s)) * s)
require     body(0,s) > bite
=>        fill(s) > 1 / (s * (1 - bite)) - 1
```

The full-strength floor is `CORE_FILL = 0.45`. The remap uses the larger of that floor and the
strength-specific bound plus `0.021` headroom. The weakest live ANVIL (`s = 0.7231`) therefore uses
`0.9498`, just above its derived `0.9289` minimum; a full-strength CORE remains at `0.45`. This
retains authoritative role differences in coverage and avoids globally increasing `cell.density`
or flattening all descriptors to one strength. T100 verifies every role remains positive after the
p05 erosion bite and that no effective fill exceeds its derived minimum by 0.10.

### T131 embedded-convective retention

T128 measured a separate failure mode at Y=272: BASE, CORE, and TOWER coverage was continuous and
increased, but the normalized base field reached zero and drove final density below the 0.10
silhouette occupancy level. The strength-only derivation protects a descriptor at its own coverage
ceiling; it does not prove that a deep, multi-role overlap retains visible mass at its actual,
already-strength-weighted coverage.

For a simultaneous BASE+CORE+TOWER overlap only, the remap therefore also derives:

```text
required body = 0.10 + p05 erosion bite
fill >= 1 / (coverage * (1 - required body)) - 1
```

It engages continuously only above coverage 0.82 and strength 0.84. It does not change descriptor
coverage authority, the base-noise derivative, erosion, or any outer BASE/ANVIL sample. The
complete before/after trace is recorded in `validation/t128-t131-material-continuity.md`.

### Macro scale and proportional domain warp (T124)

The old `0.0052` base scale measured a **52.6-block** dominant carrier feature. That is less than
half of the narrowest live tower's 116-block width, so the base field re-carved the macro envelope.
At `0.0025`, the measured feature is **109.4 blocks**. T124 requires at least 85% of that tower width
(98.6 blocks), so the failure is reproducible and the correction is not accepted by a threshold
change.

The fixed 0.31-tile warp induced about 13.9% directional distortion at the old scale. The storm-only
warp is now `min(0.31, 0.08 * scale / 0.00233)`: `0.0858` tiles at the new scale, holding the intended
8% maximum while leaving the legacy warp unchanged for non-storm cloud families. Java and GLSL use
the same rotation, warp vectors, scale, cap, and amplitude equation.

### `MIN_OCCUPIED_REGION_SD = 0.1148` (SC-012)

Over an unsaturated full-coverage region there are two independent noise contributions: the remapped
base field with sensitivity `1 / (1 + CORE_FILL)`, and detail erosion with sensitivity `E`.

```text
sd = sqrt( (0.3294 / 1.45)^2 + (0.44 * 0.0742)^2 )
   = sqrt( 0.2272^2 + 0.0327^2 )
   = 0.2295
```

Half of the analytic value is required. The 50 % allowance covers ray-integration smoothing along
the sample interval, partial saturation near the top of the body, and texture filtering. It is a
tolerance on a derived value, not a target picked to pass.

**Region size**: at least three wavelengths of the lowest detail octave, i.e. **68.2 blocks** per
edge (`3 × 22.73`).

**Occupancy filter**: only samples with final density in `(0.05, 0.95)` count, so a clamped region
cannot disguise a uniform interior as "low variance by saturation".

### Interior sensitivity (SC-013)

Analytic sensitivities of the final density, wherever the result is unsaturated:

```text
d(density)/d(baseField)  = 1 / (1 - coverageLowerBound(coverage))   -> 1/1.45 = 0.690 at full coverage
d(density)/d(detailFbm)  = E                                        =  0.44
```

**Applied threshold**: a perturbation must produce at least **half** the analytic derivative at 95 %
or more of sampled unsaturated interior points. The pre-correction path yields exactly **zero** here,
because `edgeExposure = 1 - smoothstep(0.26, 0.72, cloud)` reaches zero across a covered storm
interior, so the check fails decisively rather than marginally.

**Interior definition**: coverage ≥ `0.75` and at least one lowest-octave wavelength (22.7 blocks)
inside the coverage boundary. `0.75` sits just above the coverage at which the remap lower bound
crosses zero (`coverageLowerBound(0.69) = 0`), i.e. the geometric point where a sample is inside the
body rather than on its boundary.

### Per-band contribution (SC-014)

Each octave must retain at least **half its measured share** of the detail field's variance in the
final density:

| Band | Wavelengths (blocks) | Measured share | Required |
|---|---|---:|---:|
| R | 22.7 → 5.7 | 0.8520 | 0.4260 |
| G | 11.4 → 2.8 | 0.1225 | 0.0613 |
| B | 5.7 → 1.4 | 0.0255 | 0.0128 |

A band measuring below its threshold means that spatial frequency is not reaching the visible
result.

## FR-023 structural measurements

Geometric, not noise-derived, so expressed as relations. Absolute block values depend on the storm's
authoritative development state and are not hard-coded.

| Feature | Measurement | Threshold |
|---|---|---|
| Broad continuous lower base | Occupied horizontal section at base altitude, and its connectivity | One connected component; broader than the tower section |
| Dense convective core | Mean final density in the core region vs. the whole-body mean | Core mean strictly exceeds body mean |
| Tower emerging from base | Occupied cross-section area at tower vs. base altitude | Tower strictly narrower than base, and vertically connected to it |
| Progressive vertical narrowing | Cross-section area between base and anvil root | Non-increasing within a tolerance permitting noise-driven local widening |
| Broad upper anvil | Cross-section area at anvil vs. tower altitude | Anvil strictly wider than tower |
| Continuous transitions | Density along vertical transects crossing region boundaries | No step exceeding the per-block bound derived from the erosion amplitude |
| Irregular but coherent silhouette | Radius variation along the silhouette | Above the balloon-rejection minimum and below the fragmentation maximum |

## FR-024 rejection measurements

| Rejected form | Proxy |
|---|---|
| Large smooth balloon surface | Silhouette radius variation below the derived minimum over a large arc |
| Large uniform-density region | A connected occupied region ≥ the minimum region size whose SD falls below `MIN_OCCUPIED_REGION_SD` |
| Visible ellipsoid or sphere primitive | A section that fits a single analytic ellipse within a residual below the derived noise amplitude |
| Isolated ear or bulb | More than one connected component in an occupied section |
| Descriptor seam | A density discontinuity co-located with a lobe boundary |
| Rectangular or vertical wall | A planar silhouette run longer than the derived multiple of the lowest octave wavelength |
| Flat slab | A horizontal underside plane of the same extent |
| Uniformly smooth silhouette | The balloon measure applied to the whole visible outline |

## Change policy

When any constant in "Model constants read from the shader" changes, re-run
`stormDensityThresholdSandbox`, update this document from its output, and only then update the
tests. Adjusting a threshold to accommodate an observed result, without a corresponding model change
recorded here, violates FR-026 — and the sandbox will fail the next time it re-derives the value.
