# Project Atmosphere Cloud Shape Research And Visual Rework Report

Date: 2026-06-15

## 1. Files Created

- `docs/PA-docs/Core Systems/Clouds/PA_CLOUD_SHAPE_RESEARCH_VISUAL_REWORK_REPORT.md`

## 2. Files Modified

- `src/main/resources/assets/projectatmosphere/shaders/core/cloud_volume.fsh`
  - Added original PA morphology-specific structural density fields for PUFF, TOWER, STORM_ANVIL, SHEET, CELLULAR_SHEET, FILAMENT, and SPIRAL_STORM.
  - Added stronger edge breakup and morphology masking inside the existing raymarch density path.
- `src/main/resources/assets/projectatmosphere/shaders/core/cloud_volume.json`
  - Added the `CloudMorphologyFamily` shader uniform.
- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudUniformUploader.java`
  - Uploads `snapshot.getMorphologyFamily().ordinal()` to the cloud volume shader.
- `src/main/java/net/Gabou/projectatmosphere/clouds/state/CloudRegionRegistry.java`
  - Changed active PA native sync from one render datum per active cluster to one composite render datum per active cloud region.
- `src/main/java/net/Gabou/projectatmosphere/clouds/transport/CloudRegionRenderDataFactory.java`
  - Uses aggregate region center, previous center, velocity, radius, base/top bounds, density, coverage, and edge softness for normal region render data.
  - Keeps dominant active cluster identity for cloud type, previous type, morphology, material, shape profile, lifecycle, and seed.
  - Leaves the legacy per-cluster factory method available for explicit callers.
- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudRenderLodManager.java`
  - Keeps the existing LOD candidate priority and render-budget selection, then sorts selected render plans back-to-front before drawing.
- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudRenderLodSnapshotFactory.java`
  - Preserves minimum shape detail floors by morphology family so far LOD does not collapse towers, storms, cellular sheets, filaments, or cumulus/puff clouds into smooth primitives.

No forecast, atmosphere, WeatherCell, cloud lifecycle, cloud persistence, cloud evolution, wind, season, tornado, hurricane, blizzard, Distant Horizons, Iris, Oculus, or shader auto-patching systems were modified.

## 3. PM Weather Findings

Inspected local PM Weather references:

- `docs/decompiled-mods/pm-weather/shaders/program/clouds.fsh`
- `docs/decompiled-mods/pm-weather/shaders/program/blur.fsh`
- `docs/decompiled-mods/pm-weather/shaders/program/smoothing.fsh`
- `docs/decompiled-mods/pm-weather/shaders/include/noise.glsl`

Findings:

- PM Weather uses a noise-driven cloud pass rather than hard primitive cloud meshes.
- Cloud softness is heavily influenced by layered noise, alpha accumulation, downsampled rendering, blur, and temporal smoothing.
- Edge degradation comes from density/noise thresholds and post blending, not from copying a sharp mesh outline.
- It uses separate noise samplers and multiple density terms to avoid uniform hard borders.
- The useful design lesson for PA is: keep dense interiors, erode edges with noise, and blend clouds softly into the scene.

Implementation note:

- No PM Weather source, textures, shaders, or assets were copied or ported.
- The repository already had Modrinth Maven configured and an inspection-only PM Weather compileOnly dependency: `maven.modrinth:protomanlys-weather:0.16.4-1.20.1-alpha`.
- I used the existing local references and dependency for inspection; PM Weather is not bundled.

## 4. Simple Clouds Findings

Inspected local Simple Clouds references:

- `src/main/resources/assets/simpleclouds/shaders/compute/cube_mesh.comp`
- `src/main/resources/assets/simpleclouds/shaders/compute/cloud_regions.comp`
- `src/main/resources/data/simpleclouds/cloud_types/cumulus_congestus.json`
- `src/main/resources/data/simpleclouds/cloud_types/severe_cumulonimbus.json`
- `src/main/resources/data/simpleclouds/cloud_types/stratocumulus_opacus.json`
- `src/main/resources/data/simpleclouds/cloud_types/pathway.json`
- `docs/decompiled-mods/Simple Clouds/dev/nonamecrackers2/simpleclouds/client/mesh/generator/MultiRegionCloudMeshGenerator.java`

Findings:

- Simple Clouds gets interesting silhouettes from layered noise settings and thresholded occupancy.
- Holes and gaps are a natural result of density samples failing the occupancy threshold.
- Cloud type JSON files define multiple noise layers with different height offsets, scales, value offsets, and fade distances.
- Its region generator stores cloud type and edge fade in region textures, then LOD generation preserves large region identity.
- The useful design lesson for PA is: cloud shape should be a structured density field with invalid/low-density areas, not a single uniformly faded ellipsoid.

Implementation note:

- No Simple Clouds source, textures, shaders, or assets were copied or ported.
- PA now uses an original raymarch density model inspired by the general design lesson of layered structural breakup.

## 5. PA Visual Pipeline Findings

Inspected PA native cloud render path:

- `CloudMorphologyGenerators`
- `CloudRegionTypeGeometry`
- `CloudRenderSnapshot`
- `CloudUniformUploader`
- `cloud_volume.fsh`
- `cloud_volume.json`

Findings:

- PA already creates morphology data and assigns a `CloudMorphologyFamily`.
- `CloudRenderSnapshot` already carries the morphology family and shape profile.
- `CloudUniformUploader` uploaded shape scalar controls, but did not upload the morphology family to the shader.
- The shader had lobe counts, lobe strength, tower narrowing, anvil spread, cell split, and edge raggedness uniforms.
- Despite those uniforms, `sampleCloudField` still sampled each cloud volume through one central radial density model.
- Existing lobe logic mostly warped radius and edge fade; it did not create true stacked lobes, sheet cells, filaments, spiral bands, or structural holes.
- `CloudRegionRegistry.createRenderDataForActiveRegions` flattened each active region into separate per-cluster render data, so one cumulonimbus region could render as multiple independent radial volumes.
- `CloudRenderer` submits each selected snapshot one-by-one with transparent blending; without final back-to-front ordering, a farther cloud could be drawn after and appear over a nearer cloud.

## 6. Root Cause Of Cylinder/Capsule Clouds

Root cause:

- The renderer treated each PA cloud volume as a single radial primitive.
- The backend sync path sent each active cluster as its own render datum, so a multi-cluster storm rendered as several independent volumes.
- Transparent cloud snapshots were selected by render priority, but the final draw order was not sorted for blending.
- TOWER used a vertical radius curve, but still resolved into a smooth center-aligned capsule/tower.
- Lobe counts affected edge sinusoidal radius warp, not actual multi-lobed density.
- Holes and gaps were not represented as family-specific structural fields.
- Edge erosion existed, but was not strong enough to break the silhouette.

Concrete path:

- `CloudRegionTypeGeometry` and `CloudMorphologyGenerators` create cloud state and morphology metadata.
- `CloudRenderSnapshot` carries morphology and shape profile.
- `CloudUniformUploader` previously sent shape profile values but not `CloudMorphologyFamily`.
- `cloud_volume.fsh::sampleCloudField` rendered the cloud using `CloudCenter`, `CloudRadius`, vertical falloff, radial falloff, and noise multipliers.
- `CloudRegionRegistry.createRenderDataForActiveRegions` called `CloudRegionRenderDataFactory.createForCluster(...)` for each active cluster.
- `CloudRenderLodManager.createPlans(...)` selected good candidates but returned them in budget-priority order rather than draw order.

## 7. Region Composite Render Data Implementation

Implemented for PA native cloud rendering only:

- Active region sync now emits one `CloudRegionRenderData` per active `CloudRegionState`.
- Composite render data uses region aggregate geometry:
  - aggregate center;
  - aggregate previous center;
  - aggregate velocity;
  - aggregate radius;
  - lowest base;
  - highest top;
  - weighted density;
  - weighted coverage;
  - weighted edge softness.
- Dominant active cluster still supplies identity and profile data:
  - cloud type;
  - previous cloud type;
  - morphology family;
  - transition ticks;
  - shape profile;
  - material profile;
  - lifecycle fields;
  - cloud seed.
- Simulation-side clusters, lifecycle, movement, merging, persistence, and ownership were not changed.
- The existing `CloudRegionRenderData` wire shape is unchanged; no new packet fields were added.

## 8. Render Ordering Implementation

Implemented in `CloudRenderLodManager`:

- Candidate collection, tier priority, global budget, and per-tier budget logic are unchanged.
- After budget selection and LOD snapshot creation, selected `CloudRenderLodPlan` entries are sorted by descending camera distance.
- Farther transparent volumes now draw first and nearer volumes draw after them.
- Scene-depth compositing and terrain depth rejection are unchanged.

## 9. Shape Rework Implementation Details

Added `CloudMorphologyFamily` to the shader path and implemented original family-specific density fields:

- PUFF
  - Uses several deterministic ellipsoid lobes around a softer central mass.
  - Breaks the perfect sphere/blob silhouette.
- TOWER
  - Uses stacked vertical lobes with jittered centers.
  - Upper lobes widen while the base remains narrower.
  - Reduces the smooth vertical capsule/cylinder look.
- STORM_ANVIL
  - Combines a tower core with a horizontally stretched upper anvil field.
  - Adds storm-wall contribution through existing storm wall strength.
- SHEET
  - Uses a flattened, anisotropic sheet field with ragged density variation.
  - Avoids a circular disk impression.
- CELLULAR_SHEET
  - Uses sheet structure plus cellular occupancy breakup.
  - Creates internal breaks and gaps.
- FILAMENT
  - Uses wind-sheared streak fields and thin trail masks.
  - Avoids rendering cirrus-like clouds as blobs.
- SPIRAL_STORM
  - Uses spiral band masks with an organized broad envelope.
  - Supports an eye-like central reduction where appropriate.

## 10. Edge Degradation Implementation Details

Changes in `cloud_volume.fsh`:

- Increased edge tolerance from a hard radial cutoff at `normalizedHorizontal >= 1.0` to `>= 1.16`, then relies on fade/erosion for softer disappearance.
- Extended horizontal edge fade from `smoothstep(..., 1.0)` to `smoothstep(..., 1.10)`.
- Added `edgeBreakup`, combining erosion noise and silhouette noise.
- Increased edge-raggedness contribution to erosion weighting.
- Added morphology masking so silhouette breakup is structural, not only alpha fade.

Result intended:

- Dense center.
- Noisy soft transition.
- Broken transparent edge.

## 11. Hole/Gap Implementation Details

Implemented holes/gaps where appropriate:

- `CELLULAR_SHEET` uses cellular occupancy noise to reduce internal density down to near-empty areas.
- `SPIRAL_STORM` uses band masks and envelope/eye shaping to avoid a filled disk.
- `FILAMENT` uses separated streak masks, leaving clear gaps between wisps.
- `PUFF` and `TOWER` use lobe unions and edge erosion rather than full continuous primitives.

Dense cores are preserved by:

- Existing `CloudPrecipitationCoreStrength`.
- Center-weight preservation in the morphology mask.
- Existing density, coverage, and lifecycle factors.

## 12. LOD Compatibility Details

The rework remains inside the existing raymarch shader and uses existing render profile data:

- Near clouds get the strongest morphology masking when `RaymarchSteps` is high.
- Low raymarch settings reduce structural strength through `qualityFactor`.
- Far clouds still use the same family-specific fields, so they should not collapse into primitive capsules.
- `CloudRenderLodSnapshotFactory` now applies morphology-specific minimum detail floors:
  - STORM_ANVIL and SPIRAL_STORM keep the highest far-detail floor.
  - TOWER keeps stacked silhouette detail.
  - CELLULAR_SHEET and FILAMENT keep gap/streak structure.
  - PUFF and cumulus types keep enough lobe detail to avoid perfect balls.
- No Distant Horizons integration was added.
- LOD budgets and candidate priority remain unchanged; only final draw order and shape simplification floors changed.

## 13. Performance Considerations

Performance protection:

- The work stays inside the existing raymarch path.
- No additional render passes were added.
- No new textures or buffers were added.
- No simulation-side work was added.
- Complexity scales down at low `RaymarchSteps`.

Cost risk:

- The shader now evaluates a small fixed number of additional ellipsoid/noise terms per density sample.
- TOWER and PUFF are the heaviest new family fields because they evaluate multiple lobes.
- This should be tested in-game with the existing profiler target, especially around large clusters of TOWER/STORM_ANVIL clouds.

## 14. Gameplay Systems Confirmed Untouched

Untouched:

- Forecast generation.
- Atmosphere simulation.
- WeatherCell simulation.
- Cloud spawning.
- Cloud lifecycle.
- Cloud movement.
- Cloud merging.
- Cloud evolution.
- Cloud persistence format.
- Seasonal drift.
- Wind simulation.
- Tornado systems.
- Hurricane systems.
- Blizzard systems.
- Shader auto-patching.
- Iris/Oculus internals.
- Distant Horizons internals.
- Simple Clouds behavior.

## 15. Build Results

Commands run:

```powershell
.\gradlew compileJava
.\gradlew build
```

Results:

- `compileJava`: PASS.
- `build`: PASS.

Notes:

- Existing warnings remain: mixin target warnings and deprecated `ResourceLocation` API warnings.
- No new Java compile errors were introduced.
- Gradle build does not fully validate runtime GLSL compilation; manual in-game shader load testing is still required.

## 16. Manual Testing Checklist

Test with PA native clouds enabled:

- PUFF
  - Confirm small cumulus/vapor clouds are multi-lobed.
  - Confirm they no longer look like perfect spheres.
- TOWER
  - Spawn or observe `cumulus_congestus`.
  - Confirm stacked cauliflower lobes.
  - Confirm narrower base and broken sides.
  - Confirm it no longer appears as a smooth cylinder/capsule.
- STORM_ANVIL
  - Confirm vertical core plus wider upper anvil.
  - Confirm dark underside still works.
  - Confirm outer storm wall is irregular.
- SHEET
  - Confirm broad flat layer with ragged edges.
  - Confirm it does not appear as a circular disk.
- CELLULAR_SHEET
  - Confirm visible breaks/gaps.
  - Confirm it does not render as one continuous smooth layer.
- FILAMENT
  - Confirm thin wispy trails.
  - Confirm it does not render as a blob or sheet.
- SPIRAL_STORM
  - Confirm broad organized spiral/band structure.
  - Confirm it is not a cylinder or flat disk.
- Far LOD
  - Move 1500+ blocks from major cloud systems.
  - Confirm recognizable silhouette remains.
  - Confirm far clouds do not reduce to primitive capsules.
- Composite storm rendering
  - Run without Simple Clouds installed.
  - Use `/pa cloud spawn cumulonimbus`.
  - Confirm command output says `Result: native PA region created`.
  - Confirm the spawned type resolves to `cumulonimbus_calvus`.
  - Confirm the PA native cumulonimbus renders as one coherent storm mass instead of separate vertical capsules.
- Transparent overlap
  - Place or observe near and far PA native cloud regions in the same view.
  - Confirm nearer clouds visually cover farther clouds instead of farther clouds drawing on top.
- Near LOD
  - Confirm highest configured quality uses stronger details.
  - Confirm low quality remains stable and not overly expensive.
- Shaderpack inactive
  - Test vanilla/no shaderpack cloud render path.
- Shaderpack active
  - Test with shaderpack enabled.
  - Confirm no cloud shader load crash.
- Performance
  - Re-check render profiler around `CloudRenderHook` and `CloudRaymarchRenderer.renderSnapshot`.
  - Compare against previous approximate target: CloudRenderHook around 7 percent, raymarch around 2.4 percent.

## Success Criteria Status

- TOWER smooth cylinder issue: addressed in shader by stacked lobe density.
- PUFF smooth blob issue: addressed by multi-lobed density.
- SHEET circular disk issue: addressed by anisotropic flattened sheet field.
- FILAMENT blob issue: addressed by streak/trail density.
- STORM_ANVIL structure: addressed by tower core plus upper anvil field.
- Edge degradation: addressed by stronger edge erosion and breakup.
- Holes/gaps: addressed for cellular sheets, spiral storms, filaments, and edge-eroded large clouds.
- Far cloud recognizability: addressed inside the shared raymarch density path.
- Multi-cluster storm fragmentation: addressed by composite region render data.
- Near/far transparent overlap: addressed by back-to-front render plan ordering.
- Performance: compile/build pass; runtime profiling still required.
- Simulation behavior changes: none intentionally made.
