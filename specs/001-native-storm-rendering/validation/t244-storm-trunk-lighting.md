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

## 4. Cost

A few ALU operations per lit sample, no fetches, no new values carried across `cloudDensity`. Not
measured build-vs-build; flag for the next performance pass.

## 5. Validation

`campaignGate` 72/72; `cloudMorphologyTopologySandbox` and `stormVolumetricGeometrySandbox` pass;
offline NVIDIA compile of the monolith, lean FINAL, rain pass and rain-gate build; production gallery
captured without any experiment plumbing.
