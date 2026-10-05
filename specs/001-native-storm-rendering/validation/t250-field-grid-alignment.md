# T250 - Non-storm lighting-grid alignment

Branch `quality/t217-deep-interior-lighting`, from `6482a691`. Grid setup only (Java): no shader, lighting,
density, opacity or morphology change. Evidence: `validation/t250-campaign/`.

## Defect

The weather map origin snapped to whole map texels (8 blocks at the production map size), while the T199 storm
field and the puff candidate index tile the same extent with 256 cells of 16 blocks hung off that origin. Every
other re-centre therefore moved the field lattice by half a node: the non-storm light taps (T247: all but the first,
which is exact) re-sampled the field at shifted nodes and the shading popped as the camera crossed texel lines.

## Change

`CloudWeatherMapRenderer.originSnap(mapSize) = max(texel, WEATHER_EXTENT / StormFieldGeometry.DEFAULT_XZ)`: the
origin snaps to a whole number of texels AND field nodes / puff tiles (16 blocks in production). Used by both the
weather-map origin and the storm-descriptor origin in `VolumetricCloudRenderHook` (they must match). Texel centres
stay on the same world lattice, so the weather-map content per world position is unchanged; the map re-centres
half as often (at most 16 blocks off-centre on a 4096-block extent). The ground-shadow map keeps its own origin.

## Verification (fixed seed `CG_SEED=4242`, nimbostratus, stratocumulus, humilis, cumulonimbus, motion views)

* **Phase** (production lean FINAL, origin logged per pose, `t250-metrics.txt`): HEAD origins (`t250a`) took all
  four phases (x mod 16, z mod 16) in {0, 8}; T250 (`t250b`) only (0, 0) across 79 poses.
* **Visual identity on the same cloud** (same-session arms, monolith: M / M2 = old snap, S = new snap, `t250c`,
  sheet `t250_arms`): the clouds are visually identical in every view; the numeric outliers (stratocumulus front /
  edge_profile / opposite_side) are terrain chunks streaming differently between shots, which pass the cloud mask.
  Silhouette change 0.3 % (noise 0.1 %).
* **Motion** (`motion0..5`, 4-block camera steps across snaps, max per-step p99 |dL|, same cloud): stratocumulus
  38.1 -> 17.4 (the old snap's pop gone, every step flat), cumulonimbus 11.1 -> 9.6, nimbostratus 19.8 -> 19.2
  (noise), humilis identical (its sequence crossed no phase-changing snap). Production sessions: nimbostratus
  22.7 -> 10.0, stratocumulus 22.5..26.3 -> 18.1.
* **No panel regression**: T247's covered-node exclusion and exact first tap are untouched; the shading of every
  family matches the old-snap arms.

## Performance

Shader unchanged - no codegen change. Lean FINAL sessions, mean of per-view median cloud-pass gpuMs over 79 views:
HEAD 9.33 / HEAD repeat 9.32 / T250 9.26 ms (storm views included) - no cost.

## Validation

`campaignGate` 0, `check` 0, `stormMorphologySandbox` 0 (`gate-t250.log`, `check-build-t250.log`,
`sandbox-t250.log`).
