# Project Atmosphere Cloud Renderer Design Map

## Contract

Project Atmosphere owns weather and cloud state.
The renderer consumes immutable PA snapshots only.
The renderer does not query live weather systems during draw.
Visual density, lighting, shadows, and fallback darkening are renderer responsibilities, but they must all derive from the same shared snapshot data.

## 1. Snapshot Data

Each cloud snapshot should contain:

- `regionId`
- `clusterId`
- `dimensionId`
- `worldTime`
- `partialTick`
- `cameraPosition`
- `regionCenter`
- `previousRegionCenter`
- `velocity`
- `regionRadius`
- `cloudBaseY`
- `cloudTopY`
- `density`
- `coverage`
- `edgeSoftness`
- `cloudTypeId`
- `previousCloudTypeId`
- `morphologyFamily`
- `cloudTypeTicks`
- `cloudSeed`
- `verticalThickness`
- `edgeErosionStrength`
- `topSoftness`
- `baseSoftness`
- `baseDarkness`
- `noiseScale`
- `detailNoiseScale`
- `erosionNoiseScale`
- `densityMultiplier`
- `coverageMultiplier`
- `heightSquash`
- `towerStrength`
- `anvilStrength`
- `precipitationCoreStrength`
- `materialProfile`
- `shapeProfile`
- `stormVisualTier`
- `precipitationTier`
- `shadowContribution`
- `lightningInfluence`

Optional but useful:

- `sunDirection`
- `sunElevation`
- `cloudThicknessHint`
- `opticalDepthHint`
- `lightingBlendHint`
- `fallbackDarkeningHint`
- `lodHint`

## 2. CloudShapeProfile To Density

`CloudShapeProfile` must drive actual density, not just metadata.
The shader should use it for:

- body radius
- vertical offsets
- lobe count and lobe strength
- vertical tilt
- wind shear
- cell splitting
- tower narrowing
- anvil spread
- base flattening
- edge raggedness
- storm wall strength

The shape profile should shape the cloud body before lighting or color is applied.
If the shape profile only affects a few edge terms, the cloud will still look like a single blob.

## 3. Vertical Thickness

`CloudVerticalThickness` must affect the vertical density envelope directly.

It should control:

- how much of `cloudBaseY..cloudTopY` becomes dense volume
- how soft the top and bottom transitions are
- how stratus becomes a thick layer instead of a thin sheet
- how cumulonimbus grows into a deeper body without losing the underside

It should not only flatten the volume.
It should make the cloud body thicker where the type needs mass and thinner where the type needs layering.

## 4. Layered Puffs

Cloud density should be built as layered volume, not a single rounded shell.

The density model should combine:

- a primary connected body
- medium puff lobes around the body
- smaller breakup around the edges
- soft internal erosion
- a preserved core

The important rule is that the core remains connected.
Erosion should subtract density gently from the silhouette and between lobes.
It should not punch hard holes through the entire cloud.

The result should read like puffy, stacked, overlapping mass with depth.

## 5. Lighting And Sun Direction

Lighting should react to sun direction.

The lighting model should produce:

- brighter sun-facing edges
- softer gray undersides
- warmer light near sunrise and sunset
- cooler ambient fill from sky light
- stronger contrast only where the cloud body can support it

Lighting should come from the same snapshot density interpretation used by the visible cloud.
If visible density and lighting density diverge, the cloud will look fake even if the shapes are better.

## 6. Storm Darkening

Storm darkening must not kill volume readability.

Storm clouds should be darker because they are denser and wetter, but they still need:

- readable silhouette
- readable upper volume
- readable lit edges
- visible depth gradients

Storm darkening should bias:

- the underside
- the inner core
- the low-light ambient tone

It should not crush the entire cloud to near-black.
If darkening is too aggressive, holes become black spots and the cloud reads as a shaded sphere instead of a cloud mass.

## 7. Erosion

Erosion should create soft breakup, not hard holes.

Use erosion for:

- silhouette breakup
- soft edge recession
- cellular texture
- subtle interior variation

Do not use erosion as a hard cutout mask.
The core should survive even when the edges break apart.

The best result is a cloud that has air in it, but still feels cohesive.

## 8. Bounded Or Proxy Rendering

Fullscreen raymarching is too expensive for the current renderer path.

The long-term replacement should be:

- projected safe bounds
- proxy volume rendering
- or a bounded draw region per cloud

The renderer should only shade pixels that can actually hit cloud volume.
The bound must be conservative.
It should never cull a cloud that should be visible.

The current direction should be:

- keep the visual density model stable first
- then replace fullscreen passes with bounded passes

## 9. Ownership

Backend state:

- `CloudRegionState`
- `CloudClusterState`
- `CloudRegionRegistry`
- `CloudRegionRenderDataFactory`

Client snapshot and cache:

- `ClientCloudRegionDataCache`
- `CloudRenderStateHolder`
- `CloudRenderSnapshotBuilder`
- `CloudRenderStateUpdater`

Density interpretation:

- `CloudDensityProvider`
- `cloud_volume.fsh`

Lighting:

- `CloudLightingBridge`
- `CloudLightingEvaluation`

Shadows:

- `CloudShadowRenderer`
- `CloudTerrainShadowRenderer`

Fallback darkening:

- `FallbackDarkeningPass`
- `CloudLightingManager`

GPU submission:

- `CloudRenderer`
- `CloudRaymarchRenderer`
- `CloudRenderTargetManager`
- `CloudUniformUploader`

## 10. Final Flow

Target flow:

1. Server updates weather and cloud simulation.
2. Backend builds `CloudRegionRenderData`.
3. Data sync updates the client cache.
4. `CloudRenderSnapshotBuilder` creates immutable snapshots.
5. `CloudRenderStateHolder` publishes frame-stable snapshots.
6. `CloudRenderer` builds render plans from snapshots only.
7. `CloudDensityProvider` and the shader interpret the same density rules.
8. `CloudShadowRenderer`, `CloudLightingBridge`, and `FallbackDarkeningPass` consume the same snapshot-derived state.
9. `CloudRaymarchRenderer` submits the GPU work.

## Implementation Sequence

1. Extend the snapshot/uniform contract for the missing shape fields.
2. Make `CloudShapeProfile` and `CloudVerticalThickness` drive the density field.
3. Rework lighting so storm clouds keep readable volume.
4. Make erosion softer and more volumetric.
5. Replace fullscreen raymarching with bounded or proxy rendering.
6. Verify the same density drives clouds, shadows, lighting, and fallback darkening.
