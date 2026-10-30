# Cloud rendering code map

A "where is what" guide to the Project Atmosphere cloud renderer. It was written during the root-cause investigation
of 26 Oct 2026 (`specs/001-native-storm-rendering/validation/root-cause-investigation/`).

- Each entry says what a file does in plain terms and whether it runs in a normal game.
- Line numbers are approximate. They drift as the files change; search for the function name instead.
- The weather simulation (`modules/`, `clouds/state`, `clouds/simulation`, `clouds/cell/sim`) and the Simple Clouds
  compat (`compat/simpleclouds`, the `SimpleClouds*` mixins) are only listed. They were out of scope for this audit.

Status tags:

| Tag | Meaning |
|---|---|
| **PROD** | Runs in a normal game with the native renderer |
| **DIAG** | Diagnostic / campaign tooling that ships in `main` but runs only behind a marker, command or env var |
| **EXP** | T255–T260 experimental morphology / preview, never production |
| **LEGACY** | Reachable only through a dev switch; superseded |

---

## 1. One frame, top to bottom (production path)

```
RenderLevelStageEvent (AFTER_WEATHER)
  VolumetricCloudRenderHook.onRenderLevel                     [PROD] orchestrates the whole pass
   |- ClientCloudRenderOwnership.resolve                      who owns clouds: PA volumetric / field fallback / vanilla / Simple Clouds
   |- gather render cells (CloudFieldSnapshot cloudlets or CloudCell)  -> VolumetricRenderCell
   |- VolumetricQualityProfile.forQuality(config)             steps, RESOLUTION SCALE (0.125-0.25), temporal on/off
   |- VolumetricCloudLighting.resolve                         sun dir/colour, ambient top/bottom from MC sky colour
   |- SceneDepthResolver.resolve                              copies scene depth for the cloud pass
   |- StormGeometryBuildCoordinator.update                    storm descriptors (worker thread) -> descriptor texture
   |- CloudWeatherMapRenderer.render                          2D weather map (coverage/base/top/precip) splat, 512^2
   |- VolumetricCloudShadowRenderer                           ground shadow map + apply
   |- VolumetricCloudRenderer.render                          THE RAYMARCH (below)
   |   |- program = CoreCostDiagnosticProgram (LEAN_FINAL in production)
   |   |- CloudFrameTimeGovernor.update                       step-scale 0.5-1.0 from GPU time (budget 4.2 ms)
   |   |- storm field atlas build (FINAL)                     shared 3D storm field
   |   |- rain gate build + rain pass (FINAL)                 rain marched separately, composited later
   |   '- FullscreenQuad.draw(cloud_atmosphere_volume_lean_final) -> phase target (0.25 x screen)
   |- CloudTemporalResolveRenderer.resolve                    4 jittered phases -> lattice target at 2x phase res (0.5 x screen)
   |- CloudSpatialUpscaleRenderer.upscale                     OFF in production (PRODUCTION_SPATIAL_UPSCALE = -1)
   '- CloudFieldCompositeRenderer.composite                   depth-aware bilinear upsample + alpha blend into the MC framebuffer
```

When `/cloudpreview` (EXP) is shown, `ExperimentalStormMorphologyState.enabled()` is true. That changes three things:

- the march reads the baked sparse-brick volume through `StormDescriptorSampler`;
- **temporal reconstruction is OFF**, so the composite upsamples the raw 0.25 target 4x;
- the lighting is the T260.5 neutral model (modes 20–44).

## 2. Files by pipeline stage

### 2.1 Orchestration and lifecycle

| File | Tag | What it does |
|---|---|---|
| `clouds/client/render/volumetric/VolumetricCloudRenderHook.java` | PROD | The frame driver shown above. Also feeds `ClientCloudVisualDensity` (camera-in-cloud density) and records diagnostics. |
| `clouds/client/render/volumetric/VolumetricCloudRenderer.java` | PROD | Sets the ~150 uniforms, chooses the program, runs the field / rain / cloud draws, picks the history or phase targets. `Tuning.FIELDS` (density 1.45, coverage 1.25, extinction 0.115) and `Tuning.CELLS` (1.05 / 1.25 / 0.055) live here. |
| `clouds/client/render/volumetric/VolumetricCloudRenderTargets.java` | PROD | Owns every offscreen target: cloud ping-pong, phases, lattice, rain, weather/morphology maps, storm field, upscale. Formats: RGBA16F colour + depth. |
| `clouds/client/render/volumetric/VolumetricCloudClientLifecycle.java` | PROD | World / dimension / resource generations; invalidates history on change. |
| `clouds/client/render/volumetric/VolumetricHistoryValidity.java` | PROD | Pure key deciding whether history may be reused. |
| `clouds/client/render/ClientCloudRenderOwnership.java` | PROD | The single "who draws clouds" decision. The field fallback needs a JVM property. |
| `clouds/client/render/CloudRenderStateGuard.java` | PROD | Saves and restores GL state around PA passes. |
| `clouds/client/render/CloudTextureUnitContract.java` | PROD | Which texture units PA may touch. |
| `client/render/shader/VolumetricCloudShaders.java` | PROD | Registers shader programs. Campaign variants load only when their campaign is armed. |
| `clouds/client/render/volumetric/CoreCostDiagnosticProgram.java` | PROD+DIAG | **217** program identities. One is production (`LEAN_FINAL` + `RAIN_PASS` + `RAIN_GATE_BUILD`); the rest are campaign, diagnostic or T260 variants generated by `build.gradle` (`generateLeanFinalShader`). |
| `clouds/client/render/volumetric/VolumetricQualityProfile.java` | PROD | Quality presets. **Internal resolution scale: LOW / LOW_24 / MEDIUM 0.125, HIGH 0.1875, ULTRA 0.25** (T146, 2 Sep 2026; before that 0.5 / 0.75). |
| `clouds/client/render/volumetric/CloudFrameTimeGovernor.java` | PROD | Lowers the STEP budget (never the resolution) when GPU time exceeds 4.2 ms for 40 frames; floor 0.5. |
| `clouds/client/render/volumetric/VolumetricCloudDebugConfig.java` | PROD+DIAG | Every runtime switch with its `PRODUCTION_*` default: temporal mode 1, spatial upscale -1 (off), macro envelope 3, lower body 1, deep scatter 1, storm geometry 1, body macro 2, lower-deck relief 1, relief shade 0.12. |

### 2.2 Inputs: cells, fields, weather map, storm descriptors

| File | Tag | What it does |
|---|---|---|
| `clouds/client/ClientCloudFieldCache.java` | PROD | Client copy of the server's synced `CloudField` snapshots. |
| `clouds/field/CloudletLayout.java` | PROD | Deterministic cloudlet slots inside a field. These become render cells. |
| `clouds/client/render/volumetric/VolumetricRenderCell.java` | PROD | The render-side view of one cell or cloudlet (position, radius, base, top, type). |
| `clouds/client/render/volumetric/CloudWeatherMapRenderer.java` | PROD | Splats cells into the camera-following 2D weather map (`cloud_weather_splat`, `cloud_weather_morphology`, `cloud_weather_cumulus_layers`, `cloud_coverage_blur`). Channels: coverage, base, top, precipitation. |
| `clouds/client/render/volumetric/StormGeometryBuildCoordinator.java` + `StormGeometryBuild*.java` | PROD | Builds severe-storm descriptors (lobes, roles, groups) on a worker thread and uploads the descriptor texture. |
| `clouds/client/render/volumetric/StormLobeDescriptor.java`, `StormLobeSpatialIndex.java`, `StormLobeEvaluator.java`, `StormBodySkeleton.java`, `StormBodyEvaluator.java`, `StormDensityModel.java`, `StormFieldSampler.java`, `StormFieldGeometry.java` | PROD | CPU mirrors and authorities of the storm shape the shader evaluates (anvil / tower / base roles, T240 continuous body, T196 storm field atlas). |
| `clouds/client/render/volumetric/PuffLobeSpatialIndex.java` | PROD | Spatial index of cumulus "puff" lobes uploaded per draw. |
| `clouds/client/render/volumetric/CloudNoiseTextureManager.java`, `CloudNoiseFieldModel.java` | PROD | The 3D base / detail noise textures (no mipmaps) and blue noise. |
| `clouds/client/render/volumetric/VolumetricMaterialAdvectionTracker.java` | PROD | Wind advection offset of the noise domain. |
| `clouds/client/render/volumetric/ClientCloudVisualDensity.java`, `CameraCloudDensityTracker.java` | PROD | CPU density at the camera, used for in-cloud whiteout and gating. |

### 2.3 The raymarch shader: `shaders/core/cloud_atmosphere_volume.fsh` (13,600 lines)

One source file compiled into 217 programs through `#define` arms and string `overrides` in `build.gradle`. Production
is `cloud_atmosphere_volume_lean_final` (generated).

| Function (approx. line) | What it does |
|---|---|
| `sampleWeather` (~2018) | Reads the 2D weather map. |
| `directStormLobeDistance*`, `directStormShape`, `directStormFinalDensity` (~2400-4200) | Severe-storm density from the descriptors: role SDFs, macro envelope (T212), lower deck relief (T230), body macro (T240). |
| `cloudDensityImpl` / `cloudDensity` (~6593 / ~7543) | **The density function**: weather map + puffs + storm descriptors + noise erosion, to a scalar density. Compiles to a subroutine; carrying extra outputs out of it costs ~2.4 ms (T232). |
| `henyeyGreenstein`, `dualLobePhase` (~8140) | Phase function: 0.72 x HG(0.62) + 0.28 x HG(-0.18). |
| `lightMarchOpticalDepth` (~8179) | Light-cone march toward the sun (4 taps, 14 blocks x1.42 growth); a buried camera uses a 1-probe shortcut. |
| `evaluateLightingComponents` (~8706) | **Production lighting**: Hillaire octaves (normalised, see the investigation), powder, underside shade, ambient retention, T231 relief shade, T244 neutral shadow ambient, T217 deep scatter, silver lining, then **per-sample filmic `1 - exp(-1.3 r)`**. |
| `rainOverheadOpticalDepth` (~8918) | T226 rain-path optical depth from the weather column. |
| `#ifdef PA_CONVECTIVE_SYSTEM_T260` block (~8930-9293) | EXP neutral lighting for the T260 fixture: modes 20-41 (`paNeutralLighting`, `paT16*`, `paT17*`). The baked light lattice comes from `NeutralLightVolume`. |
| `sampleLighting` (~9295) | Dispatch: diagnostic modes, T260 modes, otherwise the light march plus `evaluateLightingComponents`. |
| `main` (~9982) | Ray setup, jitter, interval / skip logic, the primary march loop (`accumulated += transmittance * radiance * (1 - stepTrans)`), the rain split, depth and alpha output. Output: premultiplied RGB, alpha = 1 - T, depth = weighted representative t. |

Includes in `shaders/include/`:

- `cloud_convective_system_t260.glsl` (EXP): sparse-brick lookup `paMorphDensity`. Density = `clamp(0.5 - d/feather, 0, 1)`.
- `cloud_ray_origin_jitter.glsl`: blue-noise ray origin.
- `cloud_weather_footprint.glsl`, `cloud_storm_tower_*`.
- `cloud_growth_defined_storm.glsl`, `cloud_convective_growth_field*.glsl`, `cloud_storm_morphology_prototype.glsl`, `cloud_storm_anatomy_c.glsl`: T255-T258 experiments.

`src/main/shader-experiments/*.glsl` (EXP) hold T260.18 / L-family lighting injected into generated variants only.

### 2.4 After the march: temporal, upscale, composite

| File | Tag | What it does |
|---|---|---|
| `clouds/client/render/volumetric/CloudTemporalPhaseState.java` | PROD | 4 quarter-pixel jitter phases; records each phase's camera. |
| `clouds/client/render/field/CloudTemporalResolveRenderer.java` + `shaders/core/cloud_temporal_resolve.fsh` | PROD | Builds the 2x lattice. The current phase's own pixel is exact; the other 3 come from reprojected history phases validated against a 3x3 interval, else from a bilinear fallback. Current-frame rain is composited per block (nearest texel). |
| `clouds/client/render/field/CloudSpatialUpscaleRenderer.java` + `cloud_spatial_upscale.fsh` | DIAG | T209 EASU / Catmull-Rom upscale. Off in production. |
| `clouds/client/render/field/CloudFieldCompositeRenderer.java` + `cloud_field_composite.fsh` | PROD | Despite the "field" name this is the composite used by the volumetric renderer. It does a depth-paired bilinear upsample of the premultiplied target, divides by alpha, alpha-blends over the MC scene (`defaultBlendFunc`) and writes depth. **No colour-space conversion anywhere.** Debug modes: 1 raw colour (nearest), 2 depth, 3 alignment, 4 alpha, 6 selected neighbour, 7 scene rejection. |
| `clouds/client/render/volumetric/VolumetricCloudShadowRenderer.java` + `cloud_shadow_map/apply.fsh` | PROD | Ground shadows from the weather map. |
| `clouds/client/render/volumetric/VolumetricCloudWhiteoutFogHandler.java` | PROD | MC fog inside clouds. |

### 2.5 Lighting inputs

| File | Tag | What it does |
|---|---|---|
| `clouds/client/render/volumetric/VolumetricCloudLighting.java` | PROD | **Makes the cloud react to sunlight and sky.** Sun direction from world time; sun colour DAY_SUN (1, 0.97, 0.9) with sunset tints; `AmbientTop = max(MC sky colour, (0.30, 0.36, 0.46))` and `AmbientBottom` = ground tone mixed with sky. MC sky colour is a display-space value used here as linear radiance. |

### 2.6 Experimental morphology and preview (EXP; never production)

| File | What it does |
|---|---|
| `clouds/morphology/SparseBrickVolume.java` | Bakes a signed-distance field into 8^3 bricks of 12-bit distance codes. EMPTY / FULL cells are skipped. GPU mirror: `cloud_convective_system_t260.glsl`. |
| `clouds/morphology/ConvectiveSystemT260.java` | T260.1 system recipe; `ellipsoid` (approximate distance `k0(k0-1)/k1`), `smin` / `smax`. |
| `clouds/morphology/CalvusSystemB/C/D.java` | T260.2-T260.4 calvus layouts. |
| `clouds/morphology/ConvectiveVolumeT2606.java`, `ConvectiveVolumeT2607.java` | T260.6 growth columns + dome population; T260.7 seed variation. `noise()` is a quintic value noise. |
| `clouds/morphology/PersistentConvectionT2608.java` | T260.8 persistent cells: trunk column, fixed billow slots, pair merging, daughters. Amplitudes are in model METRES (for example `FINE_A` 17 m, `WARP2` 17 m / 72 m). |
| `clouds/morphology/SystemGrowthT2609.java`, `VerticalGrowthT26010.java` | Environment-driven initiation and plume depth. |
| `clouds/morphology/MulticellSystemT26011.java`, `ConnectionsT26012.java`, `LowerMassT26013.java`, `FoundationT26014.java` | Multicell groups, connections, lower mass, shared foundation (skirts / links / shelves). **Foundation base = `smax(d, yb - y, 120)`, a plane cut with ±50 m relief.** |
| `clouds/morphology/NeutralLightVolume.java` | CPU-baked light lattice for the preview: sun optical depth (cap 8), six-ray ambient, directional / grazing blocks; spacing 10-16 blocks. |
| `clouds/morphology/CloudPreviewModels.java` | Builds a candidate at Minecraft-compressed scale (default 0.085 blocks per model metre). **`Quality` voxel / feather are in compressed BLOCKS (NORMAL 6 / 12)**, so the model-space resolution is 70 m / 141 m. |
| `clouds/client/render/volumetric/ExperimentalCloudPreview.java` | `/cloudpreview` command, worker bake, HUD, the demo driver (`PA_CLOUD_PREVIEW_DEMO_SET=...`). The preview uses `Tuning.CELLS` extinction 0.055 per block regardless of scale. |
| `clouds/client/render/volumetric/ExperimentalStormMorphologyState.java` | Installs the fixture texture, picks the T260 program for the lighting mode, sets fixture uniforms. |
| `clouds/client/render/volumetric/NeutralLitProgram.java` | Fixture program + appended light lattice. |
| `clouds/morphology/StormMorphologyPrototype.java`, `GrowthDefinedStormPrototype.java`, `ConvectiveGrowthField*.java`, `StormAnatomyCandidateC.java` | T255-T258 rejected or frozen candidates, kept for comparison. |

### 2.7 Diagnostics and campaign harness that ship in `main` (DIAG)

`StormT132AutoDriver` (13,726 lines), `StormT098CaptureDriver`, `StormT152MovingCameraFixture`, `StormPerformanceSuite`,
`StormPerformanceBaseline`, `StormT135PerformanceProfile`, `StormProductionRayTrace`, `StormWorkloadRuntimeCapture`,
`StormReferenceImage*`, `StormImageQualityMetrics`, `StormArmQualityMetrics`, `StormSceneStability`,
`StormLightTapErrorReport`, `StormCampaignRegistry`, `StormMaterialRuntimeTrace`, `VolumetricStabilityDiagnostics`
(3,265), `VolumetricCloudFrameDiagnostics`, `CumulusStageMapDiagnostics`, `ConvectiveSystemCapture*`,
`StormMorphologyCapture*`, `StormAnatomyCapturePlan`, `StormT098RoleOccupancy`, `StormT160UpperExtentArm`,
`StormOptimizationDiagnosticMode`, `StormTopologyMode`, `VolumetricPuff*` selectors, `VolumetricCloudRaymarchDebugView`
and `CloudDiagnosticsOverlay`.

Together they are about 35,000 lines of the 54,000 in the volumetric package. They are marker-gated (`run/t*.txt`) or
command-gated. See `06_DEAD_CODE_AND_COMPLEXITY.md` of the investigation.

### 2.8 Legacy second renderer (LEGACY)

`clouds/client/render/field/CloudFieldVolumeRenderer.java`, `CloudFieldVolumeRenderHook.java`,
`CloudFieldVolumeUniformUploader.java`, `CloudFieldVolumeRenderConfig.java` (partly shared), `CloudFieldRenderTargetManager.java`,
`CloudFieldVolumeTune*.java`, `CloudFieldVolumeRenderMode/Filter/Stats.java` and `shaders/core/cloud_field_volume.*`.

This is the older per-field bounded volume renderer. It runs only with `-Dprojectatmosphere.dev.enableFieldRendererFallback=true`
(`ClientCloudRenderOwnership.FIELD_FALLBACK_PROPERTY`). `CloudFieldCompositeRenderer` / `cloud_field_composite.fsh` in the
same package ARE live and must not be confused with it.

## 3. Quick "where do I change X" index

| I want to change ... | Look at |
|---|---|
| How bright, dark or white clouds are (production) | `evaluateLightingComponents` in `cloud_atmosphere_volume.fsh`, `VolumetricCloudLighting` |
| How bright the T260 preview is | `paNeutralLighting` (`PA_NEUTRAL_EXPOSURE` 3.2), `NeutralLightVolume` |
| Internal cloud resolution / pixelation | `VolumetricQualityProfile`, the temporal resolve, `cloud_field_composite.fsh` |
| Step counts under load | `CloudFrameTimeGovernor`, `RaymarchSteps` / `StepScale` uniforms |
| Storm shape (production) | `directStorm*` functions, `StormLobeEvaluator`, `StormBodySkeleton` |
| Cumulus shape (production) | `CloudWeatherMapRenderer` maps, `PuffLobeSpatialIndex`, the puff branch of `cloudDensityImpl` |
| T260 shape | `PersistentConvectionT2608`, `VerticalGrowthT26010`, `FoundationT26014`, then `CloudPreviewModels` (scale / quality) |
| Which shader program a frame uses | `VolumetricCloudRenderer.selectProgram`, `CoreCostDiagnosticProgram`, `ExperimentalStormMorphologyState.program()` |
| A new shader variant | `build.gradle` `generateLeanFinalShader` list + a `CoreCostDiagnosticProgram` entry |
