# Project Atmosphere Cloud Renderer Phase 1-4 Design

## Purpose

This document freezes the first design pass before more shader or renderer code is changed.

The goal is realistic PA cloud volume:

- soft cumulus mass
- layered cloud fields
- towering storm clouds
- bright sun-facing edges
- gray readable undersides
- smooth erosion
- visible depth
- color reacting to sun angle

The immediate scope is Phase 1 to Phase 4:

- Phase 1: snapshot contract
- Phase 2: morphology-first density
- Phase 3: multi-scale density
- Phase 4: vertical thickness

Lighting, shadows, fallback darkening, and performance are designed here only where they affect the density contract. They should be implemented after the density model is stable.

## Non Negotiable Rules

- PA owns weather and cloud state.
- The renderer owns visual interpretation only.
- The renderer reads immutable PA render snapshots only.
- The renderer must not query live PA weather systems during draw.
- The renderer must not read `CloudRegionState`, forecast managers, storm managers, or live weather systems during draw.
- The same density model must drive visible clouds, shadows, lighting, and fallback darkening.
- Cloud shapes must be built from connected mass and lobes, not one large sphere with holes carved into it.

The existing boundary docs remain the contract:

- `docs/PA-docs/Core Systems/Renderer/phase0_cloud_boundary/phase0_contract.md`
- `docs/PA-docs/Core Systems/Renderer/phase0_cloud_boundary/client_cache_boundary.md`

## Current Audit Result

Keep these systems:

- `CloudRegionState` and `CloudClusterState` as backend cloud truth.
- `CloudRegionRenderData` as transport data.
- `ClientCloudRegionDataCache` as client-side received data storage.
- `CloudRenderSnapshot` as immutable renderer input.
- `CloudRenderSnapshotBuilder` as the client conversion layer.
- `CloudShapeProfile`, `CloudVisualProfile`, and `CloudMaterialProfile` as type-driven visual metadata.
- `CloudRenderer`, `CloudRaymarchRenderer`, and `CloudUniformUploader` as GPU submission path.

Fix these systems before adding features:

- CPU density and GPU density currently do not represent one shared model.
- `CloudDensityProvider.sampleDensity()` is a simple circular approximation, while `cloud_volume.fsh` has a separate density field.
- Cloud shadows use a circular footprint instead of visible density.
- Lighting uses density after the fact instead of sampling from the same density model.
- `CloudVerticalThickness` exists in snapshot/uniform data but is not the primary driver of the shader vertical envelope.
- `CloudShapeProfile` contains useful fields, but the renderer does not yet consume all of them as the canonical body/lobe contract.
- `CloudLightingBridge` currently samples live client sky state directly; that should be moved into an immutable frame lighting snapshot before cloud draw.
- Fullscreen per-cloud raymarching is expensive, but bounded/proxy rendering should come after density correctness.

Remove or demote these ideas from the core renderer path:

- hard erosion masks that punch black holes
- one procedural ellipsoid pretending to be a full cloud
- separate shadow density, fallback density, and visible density formulas
- culling/debug systems that hide the real renderer problem
- feature patches that do not pass through the snapshot and density contract

## Phase 1: Snapshot Contract

### Snapshot Types

The renderer should consume two immutable inputs:

- `CloudRenderSnapshot`
- `CloudRenderFrameSnapshot`

`CloudRenderSnapshot` is cloud state. It is built from PA cloud data and published through the client cache.

`CloudRenderFrameSnapshot` is frame state. It is built once per render frame from the current render context and contains camera, matrices, sun, sky, fog, and render quality.

This separation avoids republishing cloud state every frame only because the camera moved.

### CloudRenderSnapshot Ownership

Owner:

- `CloudRenderSnapshotBuilder`

Source:

- `CloudRegionRenderData`

Stored by:

- `CloudRenderStateHolder`
- `ClientCloudRegionDataCache`

Read by:

- `CloudRenderer`
- `CloudRaymarchRenderer`
- `CloudUniformUploader`
- `CloudDensityProvider`
- `CloudShadowRenderer`
- `CloudLightingBridge`
- `FallbackDarkeningPass`

Must not read:

- live `CloudRegionState`
- live storm managers
- live weather managers
- forecast systems
- Simple Clouds runtime state

### Required Cloud Fields

Identity:

- `regionId`
- `clusterId`
- `dimension`
- `cloudTypeId`
- `previousCloudTypeId`
- `morphologyFamily`
- `cloudSeed`

Lifecycle:

- `enabled`
- `worldTime`
- `ageTicks`
- `lifetimeTicks`
- `growth`
- `decay`
- `cloudTypeTicks`

Transform:

- `regionCenter`
- `previousRegionCenter`
- `velocity`
- `regionRadius`
- `cloudBaseY`
- `cloudTopY`
- `windOffsetX`
- `windOffsetZ`

Weather-derived visual state:

- `density`
- `coverage`
- `targetDensity`
- `targetCoverage`
- `precipitationTier`
- `stormVisualTier`
- `precipitationCoreStrength`
- `shadowContribution`
- `lightningInfluence`

Shape profile:

- `shapeId`
- `baseRadius`
- `baseOffset`
- `topOffset`
- `lobeCountMin`
- `lobeCountMax`
- `lobeStrength`
- `verticalTilt`
- `windShearStrength`
- `cellSplitStrength`
- `towerNarrowing`
- `anvilSpread`
- `baseFlattening`
- `edgeRaggedness`
- `stormWallStrength`

Visual profile:

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

Material profile:

- `materialId`
- `textureId`
- `darkness`
- `precipitationTint`
- `opacityBias`
- `undersideDarkness`
- `edgeErosion`
- `stormCoreDarkening`
- `materialShadowContribution`
- `lightningResponse`

Derived density hints:

- `effectiveDensity`
- `effectiveCoverage`
- `lifecycleFactor`
- `opticalDepth`
- `extinctionScale`
- `scatterScale`

These derived values should be computed once by the snapshot builder or a deterministic snapshot helper. The shader and CPU density code should not each invent their own different version.

### Required Frame Fields

Camera and matrices:

- `cameraPosition`
- `modelViewMatrix`
- `projectionMatrix`
- `inverseModelViewMatrix`
- `inverseProjectionMatrix`
- `viewportWidth`
- `viewportHeight`

Time:

- `worldTime`
- `partialTick`
- `animationTime`

Lighting:

- `sunDirection`
- `sunColor`
- `ambientSkyColor`
- `fogColor`
- `sunElevation`
- `sunsetStrength`
- `horizonGlowStrength`
- `edgeLightStrength`
- `ambientFillStrength`

Render quality:

- `quality`
- `raymarchSteps`
- `renderScale`
- `maxRenderDistance`
- `jitterFrame`
- `jitterStrength`
- `temporalStrength`

The frame snapshot may read Minecraft render state once before cloud draw. Per-cloud rendering and per-sample density must only consume this immutable frame snapshot.

### Uniform Contract

Every shader uniform must map to one snapshot or frame field.

Required rule:

- If a uniform exists, it must be declared, uploaded, and used.
- If a field affects density on CPU, it must affect density on GPU the same way.
- If a field only affects debug, it must be named as debug.

Current specific gap:

- `CloudShapeProfile.baseRadius`
- `CloudShapeProfile.baseOffset`
- `CloudShapeProfile.topOffset`

These are part of the shape contract and must drive body dimensions, not sit unused in Java metadata.

### Immutability Rule

Cloud snapshots are immutable after publication.

Valid mutation points:

- backend simulation before building transport data
- transport data construction
- snapshot construction before cache publication

Invalid mutation points:

- during render
- inside shader upload
- inside shadow rendering
- inside fallback darkening
- inside lighting bridge

## Phase 2: Morphology First

### Core Design

Cloud density must be built in this order:

```text
primary connected mass
-> secondary lobes
-> tertiary puffs
-> soft edge erosion
-> final density
```

The primary mass keeps the cloud coherent.

Lobes create cloud identity.

Erosion only softens the silhouette and local density.

The renderer must not start with one full sphere and then carve holes into it.

### Density Function Contract

Define one canonical density function:

```text
CloudDensitySample sampleCloudDensity(
    CloudRenderSnapshot cloud,
    CloudRenderFrameSnapshot frame,
    vec3 worldPosition
)
```

The function returns:

- `density`
- `baseDensity`
- `edgeFactor`
- `heightFactor`
- `coreFactor`
- `erosionFactor`
- `opticalDepthHint`

The GPU implementation should live in a shared GLSL include, for example:

- `cloud_density.glsl`

The CPU implementation should live in Java and mirror the same stages:

- `CloudDensityProvider`

The CPU version does not need every micro-noise octave, but it must preserve the same body, vertical envelope, lobe layout, and erosion semantics.

### CloudShapeProfile Responsibility

`CloudShapeProfile` drives morphology.

It should define:

- where the body begins
- how wide the base is
- how far the puffy top grows
- how many lobes exist
- how strong lobes are
- how much wind shear tilts the cloud
- how much cellular splitting appears
- how towers narrow upward
- how anvils spread
- how flat the base is
- how ragged the edges are
- how storm walls organize the mass

It should not define:

- final lighting color
- frame sun direction
- render pass count
- backend weather truth

### Primary Mass

Primary mass is the connected cloud body.

Inputs:

- `regionCenter`
- `regionRadius`
- `cloudBaseY`
- `cloudTopY`
- `baseRadius`
- `baseOffset`
- `topOffset`
- `verticalThickness`
- `heightSquash`
- `towerStrength`
- `anvilStrength`
- `baseFlattening`

Purpose:

- provide readable volume
- prevent disconnected noisy clouds
- preserve underside structure
- avoid sphere-only silhouettes

The primary mass should be different per family:

- Stratus: broad connected slab with soft top and flat underside.
- Stratocumulus: connected layer with repeating cells.
- Cumulus: lower connected base with puffy rising lobes.
- Cumulonimbus: connected deep column with lower shelf, vertical tower, and possible anvil.
- Cirrus: thin sheared streaks, not dense cotton puffs.

### Secondary Lobes

Secondary lobes are large puffs attached to the primary mass.

Inputs:

- `cloudSeed`
- `lobeCountMin`
- `lobeCountMax`
- `lobeStrength`
- `cellSplitStrength`
- `towerNarrowing`
- `anvilSpread`
- `windShearStrength`

Rules:

- lobes must overlap the primary mass
- lobes may push the silhouette outward
- lobes must not become separate black balls
- lobe count must be deterministic from seed and shape profile
- lobe centers must be stable between frames

### Tertiary Puffs

Tertiary puffs add cotton detail.

Rules:

- affect mostly edges and upper forms
- do not destroy the core
- use lower amplitude than secondary lobes
- scale by cloud type

Examples:

- cumulus gets strong upper tertiary puffs
- stratus gets subtle layered waves
- cumulonimbus gets large vertical boiling forms, not small black spots everywhere

### Family Mapping

Stratus:

- large horizontal body
- low lobe strength
- high base flattening
- medium vertical thickness
- soft edge erosion
- minimal tower behavior

Stratocumulus:

- connected layer
- repeated cellular lobes
- visible gaps between cells only at edges or shallow density zones
- underside remains coherent

Cumulus:

- connected base
- strong upper lobes
- puffy top
- soft internal gradients
- blue/gray underside retained

Cumulonimbus:

- deep vertical connected storm body
- strong tower
- organized vertical lobes
- darker underside and core
- readable sunlit top and rim
- anvil as upper spread, not a separate flat disk

## Phase 3: Multi Scale Density

### Density Layers

The density model has four scales:

```text
large body
medium lobes
small puffs
micro erosion
```

Each scale has a separate responsibility.

Large body:

- controls main connected cloud mass
- determines depth and silhouette
- prevents separated blobs

Medium lobes:

- creates puffy cells
- creates cumulus and stratocumulus structure
- creates storm tower organization

Small puffs:

- adds soft cotton detail
- adds edge softness
- adds layered variation

Micro erosion:

- breaks only the thin edge
- avoids full cutout holes
- adds vapor softness

### Density Composition

Recommended composition:

```text
body = primaryBody(worldPosition)
lobes = max(secondaryLobes(worldPosition))
puffs = weightedSmallPuffs(worldPosition)
preErosion = max(body, lobes * lobeStrength) + puffs * puffStrength
erosion = edgeWeightedSoftErosion(worldPosition)
finalDensity = preserveCore(preErosion, erosion) * verticalEnvelope * effectiveDensity
```

Important:

- use additive/max blending for cloud mass
- use erosion as a soft subtractive bias
- clamp erosion by edge factor
- preserve core density
- do not use erosion as a binary alpha cutout

### Core Preservation

The core is the part of the cloud that should remain connected.

Core preservation should be based on:

- distance from silhouette
- vertical envelope
- density before erosion
- cloud family
- storm/precipitation intensity

Rules:

- cumulus core should stay bright/readable
- storm core may be darker but still visible
- stratus core should stay smooth and layered
- erosion can reduce density but not punch full holes through the core

### Noise Rules

Noise is not the cloud shape.

Noise should only modulate a body/lobe structure.

Allowed noise use:

- edge breakup
- small puffs
- soft layer variation
- slow wind drift
- storm wall texture

Bad noise use:

- replacing body/lobes with raw noise
- hard cutouts
- black speckles
- high-frequency detail across the whole cloud
- noise animated differently for shadows and visible clouds

### Density Output For Other Systems

The same density function must support:

- visible alpha
- optical depth
- shadow occlusion
- light absorption
- fallback darkening
- precipitation masking

This means the density function must expose intermediate values instead of only final alpha.

Required intermediate outputs:

- `finalDensity`
- `unlitDensity`
- `edgeFactor`
- `coreFactor`
- `heightFactor`
- `opticalDepthHint`
- `precipitationCoreFactor`

## Phase 4: Vertical Thickness

### Design Rule

`CloudVerticalThickness` must control the actual vertical volume.

It must not be only an LOD value, debug display value, or minor multiplier.

It affects:

- base-to-top envelope
- dense body band
- top puff growth
- underside flatness
- storm depth
- tower height
- layer thickness

### Vertical Coordinate

All cloud density should derive from a normalized vertical coordinate:

```text
heightRange = cloudTopY - cloudBaseY
y01 = (worldY - cloudBaseY) / heightRange
```

The density model should then derive:

- `baseFeather`
- `topFeather`
- `denseBand`
- `puffTopBand`
- `towerBand`
- `anvilBand`

### Vertical Envelope

Recommended envelope stages:

```text
base = smoothstep(-basePadding, baseSoftness, y01)
top = 1 - smoothstep(1 - topSoftness, 1 + topPadding, y01)
denseBand = smoothstep(0.04, 0.22, y01) * (1 - smoothstep(0.72, 1.02, y01))
puffyTop = smoothstep(0.38, 0.82, y01)
verticalEnvelope = base * top * mix(denseBand, 1, verticalThicknessWeight)
```

This is conceptual, not a required exact formula.

The important rule is that thickness changes where density exists, not just how tall the AABB is.

### Shape-Specific Thickness

Stratus:

- wide layer
- coherent underside
- low puffy top
- visible thickness, not paper-thin
- vertical density should fill a broad band

Stratocumulus:

- thick layer with cellular puffs
- underside mostly coherent
- cell gaps are shallow density differences, not full holes

Cumulus:

- flat-ish lower base
- thick rising body
- top lobes grow upward from connected mass
- puffy top gets the highest lobe strength

Cumulonimbus:

- deep body from base to high top
- tower uses full height range
- anvil spreads at high y only
- storm wall and precipitation core should not erase upper volume readability

Cirrus:

- thin vertical band
- strong wind shear
- weak opacity
- no heavy cotton mass

### Base And Top Behavior

Base:

- can be flatter than the top
- should be darker and more uniform
- should not collapse into a hard plane
- should still have shallow density variation

Top:

- should be softer and puffier
- should catch stronger sun lighting later
- should have larger lobe amplitude for cumulus/storm types

### Storm Thickness

Storm clouds should be thick because the density model gives them a deep connected body.

Do not make storms thick by simply increasing opacity.

Storm thickness should come from:

- larger vertical envelope
- strong tower mass
- precipitation core factor
- organized wall cloud or shelf structure
- controlled absorption

Storm darkening comes later in lighting. It must not be baked into density as black opacity.

## Backend To Frontend Flow

Target flow:

```text
Server weather update
-> CloudRegionState / CloudClusterState
-> CloudRegionRenderData
-> client packet cache
-> CloudRenderSnapshotBuilder
-> immutable CloudRenderSnapshot
-> CloudRenderStateHolder
-> CloudRenderer
-> CloudDensityProvider / cloud_density.glsl
-> visible cloud, shadows, lighting, fallback darkening
```

Frame flow:

```text
Render event
-> CloudRenderFrameSnapshot
-> CloudRenderer
-> CloudUniformUploader
-> CloudRaymarchRenderer
```

No renderer class should jump backward into backend weather state.

## Acceptance Criteria For Phase 1-4

Phase 1 is ready when:

- every renderer input is either a cloud snapshot field or frame snapshot field
- `CloudShapeProfile` fields are present in the renderer contract
- derived density values have one owner
- no renderer draw path queries live PA weather
- unused uniforms are removed or wired

Phase 2 is ready when:

- cloud bodies are connected before erosion
- `CloudShapeProfile` changes visible shape in obvious ways
- cumulus, stratus, stratocumulus, and cumulonimbus have distinct body structures
- no cloud type is represented as one simple sphere

Phase 3 is ready when:

- large, medium, small, and erosion scales are separated
- erosion does not create hard holes or black spots
- the same density function feeds visible rendering and shadow/fallback sampling
- CPU and GPU density agree on body, vertical envelope, and lobe placement

Phase 4 is ready when:

- `CloudVerticalThickness` changes real vertical volume
- stratus is a thick readable layer
- cumulus has puffy depth
- cumulonimbus is a deep connected tower/storm mass
- vertical density does not collapse into a flat 2D texture

## Density Ownership Map

This map prevents split density behavior.

The rule is simple: each stage has one Java owner and one GLSL owner. Java and GLSL may differ in precision and octave count, but they must preserve the same stage order, inputs, and meaning.

| Stage | Java owner | GLSL owner | Rule |
|---|---|---|---|
| Snapshot derived values | `CloudRenderSnapshotBuilder` for snapshot construction, `CloudDensityProvider.deriveInputs()` for render-time derived density inputs | `deriveDensityInputs()` in `cloud_density.glsl` or `cloud_volume.fsh` until the include exists | Compute lifecycle, effective density, effective coverage, optical depth, and profile weights once from snapshot fields. Do not recompute different formulas in shadows or lighting. |
| Primary mass | `CloudDensityProvider.samplePrimaryMass()` | `samplePrimaryMass()` | Builds the connected body from center, radius, base/top, shape profile, family, and vertical thickness. This is the cloud body, not noise. |
| Secondary lobes | `CloudDensityProvider.sampleSecondaryLobes()` | `sampleSecondaryLobes()` | Adds deterministic attached lobes using `cloudSeed`, lobe counts, lobe strength, tower/anvil/cell profile fields, and wind shear. Lobes must overlap the body. |
| Tertiary puffs | `CloudDensityProvider.sampleTertiaryPuffs()` | `sampleTertiaryPuffs()` | Adds smaller soft puff variation mostly near edges and tops. It must not create disconnected balls or black holes. |
| Vertical envelope | `CloudDensityProvider.sampleVerticalEnvelope()` | `sampleVerticalEnvelope()` | Applies base feather, top feather, dense body band, puffy top band, tower band, and anvil band. `CloudVerticalThickness` must change real vertical density here. |
| Soft erosion | `CloudDensityProvider.sampleSoftErosion()` | `sampleSoftErosion()` | Softly reduces edge density only. It must be edge-weighted and core-preserving. No binary cutout masks. |
| Final density | `CloudDensityProvider.sampleDensity()` returning `CloudDensitySample` | `sampleCloudDensity()` returning final density/sample fields | Combines mass, lobes, puffs, vertical envelope, erosion, lifecycle, density, and coverage. This is the single visible-density source. |
| Shadow density | `CloudShadowRenderer`, but only through `CloudDensityProvider.sampleShadowDensity()` | Future `cloud_shadow.glsl` must call the same density stages | Shadow opacity is integrated from canonical density. It must not use a separate circular footprint. |
| Lighting density | `CloudLightingBridge`, but only through `CloudDensityProvider.sampleLightingDensity()` or shader sample fields | `computeSampleLighting()` consumes `CloudDensitySample` fields from `sampleCloudDensity()` | Lighting uses final density, core factor, edge factor, and height factor from the same sample. It must not invent a second shape. |
| Fallback darkening density | `FallbackDarkeningPass` / `CloudLightingManager`, but only through snapshot-derived `CloudVisualState` produced from canonical density | Future fallback shader/pass consumes canonical density or its published summary | Fallback darkening uses the same cloud footprint and optical depth summary as visible clouds. It must not use a third radius-only approximation. |

Implementation priority:

- build the Java functions first in `CloudDensityProvider`
- mirror the same function names and stage order in GLSL
- make shadows call Java density summaries
- make lighting consume shader density sample fields
- make fallback consume the canonical published density summary

## Implementation Gate

Do not continue lighting, shadow, performance, or tornado funnel work until Phase 1-4 pass the acceptance criteria.

The first code pass after this document should only do these things:

- freeze cloud/frame snapshot inputs
- build the canonical density model
- connect shape profile fields to body and lobe generation
- make vertical thickness control vertical density
- remove density paths that disagree with the canonical model
