# Project Atmosphere Phase 6/7 Shared Foundation Report

Date: 2026-06-15

Scope completed: shared render-facing cloud visual metadata foundation only.

No full shader integration, Distant Horizons integration, cloud shadows, shader auto-patching, cloud simulation changes, weather simulation changes, cloud evolution changes, cloud spawning changes, morphology generation changes, or persistence behavior changes were implemented.

## Files Created

`src/main/java/net/Gabou/projectatmosphere/clouds/visual/CloudVisualState.java`

Read-only cloud visual state DTO for shared future render consumers.

`src/main/java/net/Gabou/projectatmosphere/clouds/visual/CloudVisualMetrics.java`

Centralized reusable visual metric calculations.

`src/main/java/net/Gabou/projectatmosphere/clouds/visual/CloudShaderMetadata.java`

Shader-facing metadata structure only. No uniforms or shader hooks.

`src/main/java/net/Gabou/projectatmosphere/clouds/visual/CloudDistantHorizonMetadata.java`

Compact LOD-ready metadata structure only. No DH dependency or DH API calls.

`src/main/java/net/Gabou/projectatmosphere/clouds/visual/CloudVisualStateFactory.java`

Read-only conversion from existing `CloudRegionRenderData` into `CloudVisualState`.

`src/main/java/net/Gabou/projectatmosphere/clouds/visual/CloudVisualStateManager.java`

Read-only accessor for active, nearby, distant-important, shadow-candidate, storm-candidate, shader metadata, and DH metadata queries.

## Files Modified

None.

This task added a new `clouds.visual` package only.

## Metadata Structures Added

### CloudVisualState

Exposes:

Cloud type.

Morphology family.

Position.

Previous position.

Velocity.

Radius.

Height range.

Density.

Coverage.

Cloud water.

Precipitation strength.

Storm strength.

Visual darkness.

Shadow potential.

Opacity.

Vertical development.

Visibility importance.

Storm visual tier.

Precipitation tier.

Cloud seed.

### CloudShaderMetadata

Prepared fields for future shader consumers:

Cloud identity.

Cloud type.

Morphology family.

Position and velocity.

Radius and height range.

Opacity.

Density and coverage.

Cloud water.

Precipitation strength.

Storm strength.

Visual darkness.

Shadow potential.

Vertical development.

Cloud seed.

No uniforms were added.

No shader files were modified.

No render passes were changed.

### CloudDistantHorizonMetadata

Prepared compact LOD metadata:

Cloud identity.

Dimension id.

Simplified position.

Effective radius.

Effective height range.

Morphology family.

Visual importance.

Storm importance.

Shadow potential.

LOD priority.

No DH APIs are called.

No DH dependency was added.

No DH rendering was implemented.

## Visual Metrics Added

`CloudVisualMetrics.lifecycleFactor`

Uses cloud growth and decay from existing render data.

`CloudVisualMetrics.opacity`

Combines density, density multiplier, coverage, coverage multiplier, material opacity bias, and lifecycle factor.

`CloudVisualMetrics.precipitationStrength`

Uses density, coverage, precipitation core strength, lifecycle factor, and existing precipitation tier intensity.

`CloudVisualMetrics.stormStrength`

Combines storm visual tier darkness, shadow bias, precipitation strength, tower strength, and anvil strength.

`CloudVisualMetrics.visualDarkness`

Combines material darkness, storm core darkening, base darkness, storm visual darkness, and precipitation strength.

`CloudVisualMetrics.verticalDevelopment`

Combines height range, vertical thickness, tower strength, and anvil strength.

`CloudVisualMetrics.shadowPotential`

Combines existing shadow contribution, opacity, visual darkness, and storm shadow bias.

`CloudVisualMetrics.longDistanceVisibilityImportance`

Combines radius, opacity, vertical development, storm strength, and shadow potential.

`CloudVisualMetrics.lodPriority`

Combines visibility importance, storm strength, shadow potential, and effective radius.

`CloudVisualMetrics.cloudWaterProxy`

Fallback visual cloud-water estimate from density, coverage, precipitation core strength, and precipitation strength when live atmosphere cloud water is not available.

## Shader Metadata Design

Shader-facing metadata is intentionally isolated in `CloudShaderMetadata`.

It is created from `CloudVisualState`.

It does not register uniforms.

It does not modify shaders.

It does not change render stages.

It does not implement shaderpack compatibility.

Future shader work can consume `CloudVisualStateManager.getShaderMetadata(level)` and decide how to upload or map values.

## DH Metadata Design

DH-facing metadata is intentionally isolated in `CloudDistantHorizonMetadata`.

It is created from `CloudVisualState`.

It has no Distant Horizons import or dependency.

It does not call DH APIs.

It does not render into DH.

Future DH work can consume `CloudVisualStateManager.getDistantHorizonMetadata(level, center, nearRadius)` and map the compact LOD fields to DH integration code later.

## Accessor Design

`CloudVisualStateManager.getActiveCloudVisualStates(level)`

Returns all active cloud visual states.

`CloudVisualStateManager.getNearbyCloudVisualStates(level, center)`

Returns nearby cloud visual states using the default nearby radius.

`CloudVisualStateManager.getNearbyCloudVisualStates(level, center, radius)`

Returns nearby cloud visual states using an explicit radius.

`CloudVisualStateManager.getImportantDistantCloudVisualStates(level, center, nearRadius)`

Returns distant cloud states above the visibility importance threshold.

`CloudVisualStateManager.getCloudShadowCandidates(level)`

Returns cloud states with sufficient shadow potential.

`CloudVisualStateManager.getStormVisualCandidates(level)`

Returns cloud states with storm visual strength or storm morphology.

`CloudVisualStateManager.getShaderMetadata(level)`

Returns shader-facing metadata structures.

`CloudVisualStateManager.getDistantHorizonMetadata(level, center, nearRadius)`

Returns LOD-ready metadata structures for future DH integration.

All query results are returned as copied lists.

No method mutates cloud state.

## Data Sources Used

Primary source:

`CloudRegionRenderData`

Existing server data path:

`CloudRegionStateStore.createRenderDataForActiveRegions(serverLevel)`

Existing client-safe data path:

`CloudRegionPacketDispatcher.getClientRegions()`

Existing cloud type/profile fields:

`CloudVisualProfile`

`CloudMaterialProfile`

`CloudShapeProfile`

`StormVisualTier`

`PrecipitationTier`

`CloudMorphologyFamily`

Cloud water source:

On server, `CloudVisualStateFactory` samples live `RegionAtmosphereState.getCloudWater()` by the cloud center region.

If live atmosphere state is unavailable, it uses the visual fallback from `CloudVisualMetrics.cloudWaterProxy`.

## Behavior Confirmation

Gameplay behavior was not modified.

Cloud simulation behavior was not modified.

Cloud lifecycle behavior was not modified.

Cloud evolution behavior was not modified.

Cloud spawning behavior was not modified.

Cloud morphology generation was not modified.

WeatherCell behavior was not modified.

Forecast behavior was not modified.

Atmosphere behavior was not modified.

Persistence behavior was not modified.

Simple Clouds behavior was not modified.

## Shader Confirmation

No shader rendering was implemented.

No uniforms were added.

No shader JSON files were modified.

No shader source files were modified.

No render pass was changed.

No shader auto patching was implemented.

## Distant Horizons Confirmation

No DH rendering was implemented.

No DH API calls were added.

No DH dependency was added.

No DH package imports were added.

Only compact metadata for future DH integration was created.

## Validation

Command:

```powershell
.\gradlew compileJava
```

Result:

PASS.

`BUILD SUCCESSFUL in 5s`

Command:

```powershell
.\gradlew build
```

Result:

PASS.

`BUILD SUCCESSFUL in 4s`

Additional scan:

The new `clouds.visual` package was scanned for client-only renderer references and DH API/package references.

No client renderer imports were found.

No DH API/package imports were found.

Only comments and class names mention Distant Horizons.
