# T244 - Storm trunk lighting: the see-through blue trunk (accepted)

Branch `quality/t217-deep-interior-lighting`, from HEAD `2bc464b0` (T243). Lighting only: geometry,
density, extinction and alpha are unchanged. The user accepted L3 (`t244-campaign/t244_l3.png`).

## 1. Cause (measured, not assumed)

The trunk under the anvil read as translucent sky-blue in every sun-lit capture.

* **Not density.** T243 raised the base / trunk / tower density x1.8 and x4 (anvil untouched): only the
  rain base darkened; the trunk looked the same (`t243-campaign/t243_cb5_density_probe.png`).
* **Not transparency.** With the ambient term zeroed the trunk renders BLACK and fully opaque - no sky
  shows through it (`t244-campaign/t244_probe.png`, right). Its whole colour is the ambient.
* **The ambient is sky-blue in shadow.** `ambient = mix(AmbientBottom, AmbientTop, h)` is darkened by
  storm, underside, retention and rain factors but never changes hue, so a shadowed trunk takes the
  colour of the sky behind it and reads as see-through.
* Two gates that do NOT identify storm body samples: `localStorm` is <= 0.15 there (a probe painting
  `localStorm > 0.15` red painted nothing), and every primary sample passes `localStorm + 4` (the T232
  form flag), so neither separates storm from non-storm lighting. A storm flag of its own would be a
  new per-sample value (~2.4 ms by T232's measurement) - not needed, see below.

## 2. What ships (`evaluateLightingComponents`, after the rain ambient factor)

Shadowed cloud does not see the blue zenith - the tower and anvil above occlude it - and is lit by the
horizon sky and grey cloud / ground bounce, more so the lower it sits:

```
shadowed    = 1 - directTransmission
lowerWeight = mix(0.75, 1.0, 1 - h01)
neutral     = luma(ambient) * (1.03, 1.00, 0.95)
ambient     = mix(ambient, neutral, shadowed * lowerWeight)
ambient    *= 1 - 0.12 * shadowed * lowerWeight
```

Luminance-preserving desaturation plus a 12 % occluded-zenith share, both only in shadow and weighted
low in the cloud. Physically general (not storm-gated): cumulus shadow sides become grey instead of
sky-blue too.

## 3. Results (same build, same seeded storms, production lighting, SIDE)

| arm | calvus trunk RGB | trunk luma | B - R | whole-cloud luma |
|---|---|---|---|---|
| current | 118 / 144 / 168 | 140.4 | 50 | 173.5 |
| L2 (0.75 chroma, no occlusion) | 128 / 143 / 158 | 140.9 | 30 | 173.7 |
| **L3 (shipped)** | 130 / 137 / 143 | 136.2 | 13 | 171.2 |

Supercell: B - R 40 -> 13, trunk luma 151 -> 148. Whole-cloud luminance -1.3 % (no global darkening);
silhouettes identical (cloud-vs-sky mask XOR < 0.1 %, colour at the edge only). The lean FINAL
production build reproduces it (calvus trunk B - R 12.5, luma 134.6; `t244_production.png`).
L2 alone left the trunk bluish (`t244_l2.png`). The user's direction: keep this neutral-grey level - no
darker, no more blue.

## 4. Cost - measured, negligible (T244 closed)

Build against build, ABAB in one session (29 Sep 2026): A = pre-T244 volume shader (`2bc464b0`),
B = T244 (`67507cbf`); only that file differs. One client per run, back to back, `t208` marker
(lean FINAL, 0.25 phase scale, ULTRA, 96 steps, 1920x1080). Log: `t244-campaign/t244-timing.log`.

| | A1 | B1 | A2 | B2 | A mean | B mean | delta |
|---|---|---|---|---|---|---|---|
| SIDE total (ms) | 5.357 | 5.439 | 5.498 | 5.441 | 5.428 | 5.440 | **+0.012** |
| SIDE raw p50 (ms) | 3.616 | 3.656 | 3.752 | 3.712 | 3.684 | 3.684 | **0.000** |
| SIDE raw p95 (ms) | 3.842 | 3.873 | 3.958 | 3.970 | 3.900 | 3.922 | **+0.022** |
| PVN total (ms) | 4.320 | 4.510 | 4.693 | 4.176 | 4.506 | 4.343 | -0.163 |
| SIDE primary steps (M) | 4.60 | 4.63 | 4.64 | 4.65 | | | |
| PVN primary steps (M) | 1.44 | 1.97 | 2.02 | 1.55 | | | |

* SIDE workload is steady (4.60-4.65 M steps): the SIDE delta is +0.2 % total, 0 % raw p50 - inside
  the run-to-run spread (A1 vs A2 alone differ 0.14 ms).
* PVN workload varies with the storm realization (1.44-2.02 M steps), so its totals are compared at
  matched workload: B1 (1.97 M) 4.51 vs A2 (2.02 M) 4.69; B2 (1.55 M) 4.18 vs A1 (1.44 M) 4.32 - T244
  is not slower in either pair.
* Registers (offline NVIDIA compile of the generated lean FINAL): 116 temporaries in both builds,
  instructions 6813 -> 6821 (+8), texture instructions 28 / 28, subroutine calls 49 / 49. The
  intermediate can hide back-end register cost (T232), but the paired timings show none.

**Decision: negligible - T244 closed, L3 unchanged.**

## 5. Validation

`campaignGate` 72/72; `cloudMorphologyTopologySandbox` and `stormVolumetricGeometrySandbox` pass;
offline NVIDIA compile of the monolith, lean FINAL, rain pass and rain-gate build; production gallery
captured without any experiment plumbing.
