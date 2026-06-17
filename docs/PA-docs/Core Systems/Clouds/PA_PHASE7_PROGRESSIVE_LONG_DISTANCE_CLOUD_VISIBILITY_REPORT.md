# Project Atmosphere Phase 7 - Progressive Long Distance Cloud Visibility

Scope: cloud visibility, client-side cloud LOD management, render budgeting, and render scalability only.

No weather simulation, forecast generation, atmosphere simulation, cloud simulation, cloud lifecycle, cloud persistence, cloud evolution, tornado, hurricane, blizzard, shader source, or Distant Horizons rendering code was modified.

## 1. Files Created

- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudRenderLodTier.java`
  - Defines the four progressive render tiers: NEAR, MEDIUM, FAR, HORIZON.
- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudRenderLodPlan.java`
  - Immutable per-cloud render plan containing LOD tier, adjusted snapshot, adjusted profile, priority, distance, and fade alpha.
- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudRenderLodManager.java`
  - Builds render plans from existing `CloudRenderSnapshot` data.
  - Applies distance tiering, cloud importance scoring, storm preservation, per-tier budgets, global budget, and render-distance culling.
- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudRenderLodSnapshotFactory.java`
  - Produces render-only reduced-detail copies of existing cloud snapshots.
  - Does not mutate cloud state.
- `PA_PHASE7_PROGRESSIVE_LONG_DISTANCE_CLOUD_VISIBILITY_REPORT.md`
  - This implementation report.

## 2. Files Modified

- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudRenderProfile.java`
  - Replaced the hardcoded `512.0F` max render distance with existing `AtmoCommonConfig.CLOUD_RENDER_DISTANCE`.
  - Added `withLod(...)` to create per-cloud reduced raymarch profiles without exceeding the user-selected quality.
- `src/main/java/net/Gabou/projectatmosphere/clouds/client/CloudRenderFrameContext.java`
  - Added `withRenderProfile(...)` so each LOD plan can render with its own reduced profile while preserving the same camera/frame matrices.
- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudRenderer.java`
  - Routes renderable cloud snapshots through `CloudRenderLodManager`.
  - Renders planned LOD snapshots with per-cloud render profiles.
  - Leaves shadow/fallback-darkening inputs on the existing renderable snapshot path.

Note: the workspace already had unrelated modified/untracked files before this pass. This report lists only Phase 7 changes.

## 3. Existing Systems Reused

- `CloudRenderer`
  - Existing raymarch submission path remains the draw path.
- `CloudRenderSnapshot`
  - Existing client-side immutable render data remains the LOD source.
- `CloudRenderProfile`
  - Existing raymarch quality settings remain the near-cloud quality cap.
- `CloudRenderFrameContext`
  - Existing frame/camera/matrix data remains the render context.
- `CloudDensityProvider`
  - Existing effective density/coverage logic is used for visibility filtering.
- `CloudVisualState`
  - LOD priority now builds a render-facing visual state from each snapshot.
- `CloudVisualMetrics.lodPriority(...)`
  - Reused for central visual importance weighting instead of creating a completely separate importance model.
- Existing `AtmoCommonConfig.CLOUD_RENDER_DISTANCE`
  - Reused as the max cloud visibility cap.
- Existing `AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY`
  - Reused as the maximum near-cloud quality cap.

## 4. Existing Systems Removed From Consideration

- Distant Horizons render APIs
  - Not used because Phase 7 requires long-distance clouds to work without DH.
- Shader source changes
  - Not used. The implementation uses existing uniforms and render snapshots.
- Cloud morphology generation
  - Not touched. LOD simplification only modifies render-only snapshot copies.
- Cloud simulation/state/persistence
  - Not touched. LOD operates entirely on client render snapshots.
- Tornado/hurricane/blizzard systems
  - Not touched. Storm preservation is based on existing cloud snapshot storm/morphology metadata.

## 5. Visibility Pipeline Findings

Inspected current path:

1. `CloudRenderHook.onRenderLevel(...)`
2. `CloudRenderStateUpdater.updateCurrentSnapshots(...)`
3. `CloudRenderController.getRenderableLiveSnapshots()`
4. `CloudRenderer.render(...)`
5. `CloudRaymarchRenderer.renderSnapshot(...)`
6. `CloudUniformUploader.apply(...)`
7. `cloud_volume.fsh`

Findings:

- The PA native cloud renderer had a hard max distance of `512.0F` in `CloudRenderProfile.createDefault()`.
- `CloudUniformUploader` passed that value into `MaxDistance`, `FogStart`, and `FogEnd`.
- `cloud_volume.fsh` uses `MaxDistance` to clamp raymarch range.
- `CloudRenderController` only filtered enabled/valid/dense clouds; it did not perform distance tiering, LOD selection, or render budgeting.
- Existing config already had `cloudRenderDistance` with default `2000`, but PA raymarch rendering was not using it.
- Existing DH metadata and pipeline adapter code existed, but PA cloud long-distance visibility did not depend on DH.

## 6. LOD Implementation Details

Implemented tiers:

- Tier 3 - NEAR
  - Approximate range: 0 to 250 blocks.
  - Uses full user-selected raymarch steps.
  - Preserves normal shape, density, and detail.
- Tier 2 - MEDIUM
  - Approximate range: 250 to 750 blocks.
  - Reduces raymarch steps and procedural detail.
  - Keeps normal cloud shape recognizable.
- Tier 1 - FAR
  - Approximate range: 750 to 1500 blocks.
  - Uses fewer raymarch steps and simplified detail/noise/lobes.
  - Filters low-importance clouds.
- Tier 0 - HORIZON
  - Approximate range: 1500 blocks to configured render distance.
  - Uses minimal raymarch steps and strongly reduced detail.
  - Preserves high-importance storm silhouettes and major cloud masses.

Implementation location:

- `CloudRenderLodTier`
  - Stores tier ranges, raymarch scale, density/coverage scale, detail scale, and per-tier budget.
- `CloudRenderLodManager`
  - Assigns clouds to tiers based on distance to cloud edge.
  - Sorts clouds by tier and priority.
  - Applies global and per-tier budgets.
- `CloudRenderLodSnapshotFactory`
  - Creates render-only reduced snapshots.

## 7. Progressive Scaling Details

Progressive behavior:

- Distance is measured horizontally from camera to cloud edge, not just center, so large systems remain visible sooner and longer.
- Clouds fade near the configured maximum distance using a smooth fade factor.
- Far and horizon clouds reduce:
  - raymarch steps,
  - density,
  - edge erosion,
  - detail noise,
  - erosion noise,
  - lobe count,
  - lobe strength,
  - cell split strength,
  - edge raggedness.
- Major storms keep a higher minimum visibility floor so they do not vanish at the horizon as quickly as decorative clouds.
- Geometry transitions are approximated by render-only snapshot simplification and slight silhouette preservation for major storms.

No shader source was changed. Scaling is done through existing snapshot values and existing uniforms.

## 8. Storm Preservation Details

Priority order implemented through metadata weighting:

1. Hurricanes / cyclone-like systems
   - Detected by `SPIRAL_STORM`, `CYCLONE_CORE`, or cloud type id containing hurricane/cyclone.
2. Supercells / severe systems
   - Detected by `SEVERE_CORE`, `STORM_ANVIL`, cumulonimbus-like ids, or high storm visual metadata.
3. Storm anvils
   - Detected by `CloudMorphologyFamily.STORM_ANVIL`.
4. Blizzard systems
   - Reserved by cloud type id containing `blizzard`.
5. Major rain systems
   - Detected by `PrecipitationTier.HEAVY_RAIN`.
6. Normal clouds
   - Retained by size, opacity, and visibility priority when budget allows.
7. Small insignificant clouds
   - Filtered first in FAR/HORIZON tiers.

Important storm systems are allowed through FAR/HORIZON tier filtering even when normal clouds at the same distance would be dropped.

## 9. Performance Protections

Implemented protections:

- Existing `cloudRenderDistance` is now the hard render-distance cap for PA native raymarch clouds.
- Global render budget: 42 planned clouds per frame.
- Per-tier budgets:
  - NEAR: 12
  - MEDIUM: 12
  - FAR: 10
  - HORIZON: 8
- Per-cloud raymarch steps are reduced by tier and never exceed user quality.
- FAR/HORIZON clouds must pass importance thresholds unless they are major storms.
- Render-only snapshots reduce procedural noise and shape complexity at distance.
- Clouds with no effective visible density after LOD are skipped.

## 10. Distant Horizons Findings

Existing DH-related code found:

- `CloudDistantHorizonMetadata`
  - Compact LOD-ready metadata with no DH dependency.
- `CloudVisualStateManager.getDistantHorizonMetadata(...)`
  - Existing accessor for future DH consumers.
- `DistantHorizonsPipelineAdapter`
  - Existing render pipeline adapter availability check.
- `SimpleCloudsDhPipelineSelector`
  - Existing Simple Clouds DH pipeline selection.
- `DhSupportPipelineDiagnosticsMixin`
  - Existing diagnostics around Simple Clouds DH pass.
- `SimpleCloudsRendererDhFallbackMixin`
  - Existing fallback pipeline selection for Simple Clouds + DH.
- `mods.toml`
  - Optional `distanthorizons` dependency declared.

No DH rendering integration was implemented in this phase.

Future DH integration point:

- Use `CloudVisualStateManager.getDistantHorizonMetadata(...)` as the bridge for compact cloud LOD data.
- Keep DH integration optional; PA native visibility now works without DH.

## 11. Gameplay Systems Confirmed Untouched

No files were modified under:

- forecast generation,
- atmosphere simulation,
- WeatherCell simulation,
- wind simulation,
- cloud lifecycle,
- cloud movement,
- cloud evolution,
- cloud persistence,
- tornado systems,
- hurricane systems,
- blizzard/snowstorm systems,
- shader source files,
- Distant Horizons render integration.

The implementation is client render-facing only.

## 12. Build Results

Commands run:

```powershell
.\gradlew compileJava
.\gradlew build
```

Results:

- `compileJava`: PASS
- `build`: PASS

Warnings:

- Existing deprecation and mixin target warnings remain.
- No Phase 7 compile errors.

