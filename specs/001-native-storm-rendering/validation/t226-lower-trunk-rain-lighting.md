# T226 - Storm lower-trunk seam: overhead-shadowed rain lighting (closed)

Branch `quality/t217-deep-interior-lighting`, from HEAD `38c9d5e0`. Lighting only. Density, extinction,
alpha, storm and rain geometry, temporal reconstruction, the T243 shapes and T244 lighting at and above
the cloud base are unchanged. The investigation history (T245 attribution, rejected candidates and
experiment captures) is in `t245-lower-trunk-seam.md` and `t245-campaign/`; this report is the
production result. The original T226 per-descriptor precipitation work stays parked on
`quality/t226-local-precipitation` (untouched).

## 1. Root cause

1. The descriptor storm body reaches below the weather-map cloud base (`baseY`).
2. The primary march classifies every non-puff sample below `baseY` as rain:
   `rainFraction = (precipitationSample || p.y < baseY) ? 0.25 + ... : 0`.
3. Rain-classified samples skip the light march; their optical depth was the local-only
   `localDensity * 8 * ExtinctionScale`, which ignores the storm above them, and they take the full rain
   darkening (sun x0.80, ambient x0.68, rain tint).
4. Result: below the base the opaque trunk became a light, density-mottled "stone" block with a hard
   horizontal seam (+21..+30 luma against the trunk above it, sun-lit SIDE).
5. A transition band cannot fix it: the light march under the anvil is near black while the local-only
   rain depth is bright, so any blend between the two models is non-monotonic (dark stripe, then the same
   bright block).

## 2. Rejected steps (all same-storm A/B, see `t245-lower-trunk-seam.md`)

| step | result |
|---|---|
| C1: body below the base keeps normal lighting | near-black blotchy step - rejected |
| S1: 160-block transition band (rain fraction + optical depth blended) | new dark band, block unchanged - rejected |
| R1 at full strength (overhead column x0.25 density) | seam -22..-24, lower trunk brown-grey |
| R1 + D1 (rain darkening x0.25) | seam -22..-24 still; brown cast |
| R1 capped at od 2.0 | seam -8..-10 (target +-5), still warm - rejected |
| underside shading zeroed (attribution only) | explains ~4 levels (~16 %) |
| T244 low-cloud weighting removed (attribution only) | ~1 level (~5 %) of brightness, but the source of the warm cast |

**Attribution.** R1 strength explains essentially the whole remaining seam, monotonically: R1 0 % / 50 % /
100 % gave +39 / -5.6 / -24 (cumulonimbus) and +42 / -3.8 / -22 (supercell). Mode 9 (light-path optical
depth) showed od ~19 above the base and a flat ~4.1 below it (SD 0.15-0.22) with the discontinuity
exactly at the base: the lower trunk is not deeper in shadow - the rain path turns its optical depth
into luminance differently and sits in the steep od 1-4 band of the transfer (T227).

## 3. Final solution (`cloud_atmosphere_volume.fsh`)

**R1 - overhead shadow for rain-classified material** (`rainOverheadOpticalDepth`, added to the rain
branch of `sampleLighting` and of the mode-9 / mode-2 diagnostic views):

```
overhead = max(columnTop - max(columnBase, p.y), 0) * 0.25 * DensityMul * ExtinctionScale
           (columnBase / columnTop = weather-map interval at p.xz)
od_rain  = localDensity * 8 * ExtinctionScale + 2.2 * (1 - exp(-overhead / 2.2))
```

Saturating and bounded: the added optical depth never exceeds 2.2, so tall columns are not driven to the
full-shadow floor (a fixed cap of 2.0 was too dark; this matches the 50 % response on the ~790-block
storms). One extra weather-map fetch, only inside the `rainFraction > 0.05` branch (GLSL `?:` evaluates
one operand).

**D1 - rain-only darkening at a quarter strength** (`evaluateLightingComponents`):

```
rainShadeWeight = rainFraction * 0.25
sunTerm  *= mix(1, 0.80, rainShadeWeight)
ambient  *= mix(1, 0.68, rainShadeWeight)
radiance *= mix(1, rainTint, rainShadeWeight * 0.50)
```

**Colour continuity below the base** (T244 block): for rain-classified samples
(`rainFraction > 0.05`) `lowerWeight = 0.75` (T244's top-of-cloud value) instead of
`mix(0.75, 1, 1 - h01)`; every other sample keeps T244 unchanged.

Invariants by construction: every change is gated on `rainFraction` (zero at and above the base for
cloud material), and no density, extinction, alpha, geometry or temporal code is touched.

## 4. Validation

**Static.** `campaignGate` 72/72; `check build` passed; `cloudMorphologyTopologySandbox`,
`stormVolumetricGeometrySandbox`, `stormMorphologySandbox` pass; offline NVIDIA compile of the monolith,
lean FINAL, rain pass and rain-gate build. Lean FINAL, rain pass and rain-gate sources contain the
production logic.

**Production, six storms** (lean FINAL, sun-lit SIDE, upper trunk rows 380-440, lower 500-565; A = pre-T226
build, B = T226 build, separate sessions per build; `t226-campaign/t226_production_six_storms.png`):

| storm | A seam | B seam | B lower R-B | blotch SD A -> B | upper trunk A / B |
|---|---|---|---|---|---|
| original, cumulonimbus | +24.8 | -5.0 | -0.2 | 5.7 -> 2.7 | 98.3 / 98.5 |
| original, supercell | +22.1 | -3.3 | +0.0 | 5.4 -> 2.7 | 95.4 / 95.6 |
| +3000, cumulonimbus | +30.0 | -3.0 | +0.2 | 5.8 -> 2.6 | 96.4 / 96.5 |
| +3000, supercell | +20.9 | -3.2 | +0.2 | 6.9 -> 3.3 | 94.3 / 94.3 |
| +6000, cumulonimbus | +28.7 | -4.6 | -0.4 | 6.7 -> 4.9 | 99.0 / 99.1 |
| +6000, supercell | +21.6 | -3.8 | +0.0 | 6.2 -> 2.9 | 95.5 / 95.5 |

Production reproduces the accepted temporary candidate (codes 15/16: -5.0 / -3.1 / -2.9 / -2.9 / -5.0 /
-3.8). The bright block is gone, no dark band appears (the base-ring band sits between trunk and lower
body), the lower trunk is neutral grey, and the upper trunk matches the pre-T226 build within 0.2 luma and
0.1 R-B (upper R-B -11.4..-14.8 in both).

**Geometry / above-base pixels.** Same-storm A/B is only possible inside one session; across sessions the
storm realization changes (neutral silhouettes differ by 30-62 k px over the whole storm height, including
regions T226 cannot touch). The same-session candidate runs showed **0 changed pixels above the base and
identical neutral silhouettes** on every storm; the production diff touches only `rainFraction`-gated
lighting.

**Forced heavy rain** (dedicated rain camera: below the base, 30 blocks above terrain, level at the shaft;
`t226-campaign/t226_production_rain_camera.png`, `t245-campaign/t226_combined_heavy_rain.png`). Same-session
candidate check: the same rain streaks stayed visible, 2-4 luma darker (192.8 -> 188.7, 114.0 -> 112.0),
tint unchanged, no new line at the base. Production lean FINAL: pure rain is not resolvable at the rain
camera in either build (no visible shafts with or without forcing); what forcing changes is mainly the
rain-classified lower body, whose darkening is smaller with D1. **Limited**: no regression is visible,
but the scene cannot show strong rain shafts.

## 5. Performance (build against build)

Two real builds, one client session each, alternating in one sitting: A = pre-T226 volume shader
(`38c9d5e0`), B = T226 (only that file differs). `t208` marker (lean FINAL, 0.25 phase scale, ULTRA, 96
steps, 1920x1080). Log: `t226-campaign/t226-timing.log`. Runs A1 B1 A2 B2 A3 B3; A2 and B3 lost the
fixture before PLAY_VIS_NEAR (NaN, harness), so PVN has two valid runs per build.

| | A1 | A2 | A3 | A mean | B1 | B2 | B3 | B mean | delta |
|---|---|---|---|---|---|---|---|---|---|
| SIDE total (ms) | 5.495 | 5.588 | 5.475 | 5.519 | 5.389 | 5.509 | 5.470 | 5.456 | -0.06 |
| SIDE raw p50 (ms) | 3.763 | 3.842 | 3.736 | 3.780 | 3.642 | 3.765 | 3.715 | 3.707 | -0.07 |
| SIDE raw p95 (ms) | 3.983 | 4.049 | 3.977 | 4.003 | 3.816 | 3.936 | 3.915 | 3.889 | -0.11 |
| SIDE rain pass (ms) | 0.688 | 0.700 | 0.691 | 0.693 | 0.701 | 0.710 | 0.721 | 0.711 | +0.018 |
| SIDE primary steps (M) | 4.67 | 4.66 | 4.64 | | 4.62 | 4.64 | 4.66 | | |
| PVN total (ms) | 4.266 | - | 4.449 | 4.357 | 4.279 | 4.588 | - | 4.434 | +0.08 |
| PVN primary steps (M) | 1.36 | - | 1.54 | | 1.35 | 1.43 | - | | |

* SIDE workload is steady (4.62-4.67 M steps): T226 is not slower; the spread is run-to-run noise
  (A1 vs A2 alone differ 0.09 ms).
* The rain pass carries the one extra fetch: +0.018 ms (+2.6 %) at SIDE.
* PVN workload varies with the storm realization; at matched workload (A1 1.36 M vs B1 1.35 M steps)
  4.266 vs 4.279 ms (+0.013). The other pair (A3 1.54 M 4.449 vs B2 1.43 M 4.588) is noisier.
* Registers / code (offline NVIDIA compile, NV intermediate): lean FINAL 116 -> 117 temporaries,
  6821 -> 6857 instructions, texture instructions 28 -> 29, subroutine calls 49 / 49; rain pass 152 / 152
  temporaries, 8178 -> 8220 instructions, texture 30 -> 31, calls 91 / 91; rain-gate build byte-identical.
  Rain-shaded sample counts are not exported by the counters.

**Conclusion: negligible.** Totals ~5.5 ms SIDE / ~4.4 ms PVN, far below the 10 ms ceiling.

## 6. Limitations

* The spawner makes every gallery storm ~790 blocks tall (bounds 136..925), so storm-height variation is
  weak; the saturating response is designed for it but was exercised on one column height.
* Rain shafts in the validation scene are short and faint; the rain check is weaker than the seam check.
* The upper trunk stays cooler (R-B about -12) than the neutral lower trunk, as in the pre-T226 build
  (lower R-B about -2); the base ring grades between them.

## 7. Evidence

`t226-campaign/`: production six-storm sheet, production rain-camera sheet, `t226-timing.log`.
`t245-campaign/`: every experiment capture (C1, S1, R1, D1, attribution, cap 2.0, combined static,
realizations and heavy rain).
