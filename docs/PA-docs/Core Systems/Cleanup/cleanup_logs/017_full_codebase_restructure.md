# Full Codebase Restructure

## Step goal
Apply the larger architecture restructure using the existing audit data, while leaving the protected `net.Gabou.projectatmosphere.clouds` package untouched except for a minimal typo-level build fix that was required to keep the tree compiling.

## Files reviewed
- Remaining unreviewed Java source areas under `src/main/java/`
- Focused inspection of:
  - `command/`
  - `client/`
  - `client/render/`
  - `modules/core/`
  - `modules/weather/`
  - `modules/tornado/`
  - `manager/`
  - `util/`
  - `compat/`
  - `mixin/`
- Cross-package references were rechecked for moved classes and shared helpers.

## Files changed
- `src/main/java/net/Gabou/projectatmosphere/command/ProjectAtmosphereCommands.java`
- `src/main/java/net/Gabou/projectatmosphere/command/TornadoDebug.java`
- `src/main/java/net/Gabou/projectatmosphere/command/TornadoRenderDebugClientCommand.java`
- `src/main/java/net/Gabou/projectatmosphere/client/ClientTickHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsTornadoRenderer.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/CloudDebugSnapshotFactory.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/SimpleCloudsCompat.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/CloudRegionQueue.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/CloudSpawnScheduler.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/SimpleCloudSpawner.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/CloudMeshGeneratorDiagnosticsMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/CloudMeshGeneratorShaderMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/DhSupportPipelineDiagnosticsMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/InstanceableMeshDiagnosticsMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/SimpleCloudsRendererDhFallbackMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/SimpleCloudsRendererDiagnosticsMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CloudManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/core/WeatherType.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/weather/WeatherSampler.java`
- `src/main/java/net/Gabou/projectatmosphere/tools/debug/HudRenderTest.java`
- `src/main/java/net/Gabou/projectatmosphere/tools/debug/SimpleCloudsRenderDiagnostics.java`
- `src/main/java/net/Gabou/projectatmosphere/tools/debug/TornadoLateRenderDiagnostics.java`
- `src/main/java/net/Gabou/projectatmosphere/tools/debug/TornadoRenderDebugState.java`

## Files moved
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoDebug.java` -> `src/main/java/net/Gabou/projectatmosphere/command/TornadoDebug.java`
- `src/main/java/net/Gabou/projectatmosphere/util/WeatherType.java` -> `src/main/java/net/Gabou/projectatmosphere/modules/core/WeatherType.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/TornadoRenderDebugState.java` -> `src/main/java/net/Gabou/projectatmosphere/tools/debug/TornadoRenderDebugState.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsRenderDiagnostics.java` -> `src/main/java/net/Gabou/projectatmosphere/tools/debug/SimpleCloudsRenderDiagnostics.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/TornadoLateRenderDiagnostics.java` -> `src/main/java/net/Gabou/projectatmosphere/tools/debug/TornadoLateRenderDiagnostics.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/HudRenderTest.java` -> `src/main/java/net/Gabou/projectatmosphere/tools/debug/HudRenderTest.java`
- `src/main/java/net/Gabou/projectatmosphere/util/CloudRegionQueue.java` -> `src/main/java/net/Gabou/projectatmosphere/manager/CloudRegionQueue.java`
- `src/main/java/net/Gabou/projectatmosphere/util/CloudSpawnScheduler.java` -> `src/main/java/net/Gabou/projectatmosphere/manager/CloudSpawnScheduler.java`
- `src/main/java/net/Gabou/projectatmosphere/util/WeatherSampler.java` -> `src/main/java/net/Gabou/projectatmosphere/modules/weather/WeatherSampler.java`

## Classes renamed
- None

## Classes split
- None

## Classes merged
- None

## New helper classes created
- None

## Methods reordered
- None in this pass. The primary changes were package ownership moves and import rewiring.

## Legacy/debug/diagnostic code moved
- `ParticleAtlasDebugger` had already been moved in Step 1 and remained in `tools/debug/`.
- `HudRenderTest`
- `TornadoLateRenderDiagnostics`
- `SimpleCloudsRenderDiagnostics`
- `TornadoRenderDebugState`
- `TornadoDebug`

## Packages changed
- `net.Gabou.projectatmosphere.util` -> `net.Gabou.projectatmosphere.tools.debug`
- `net.Gabou.projectatmosphere.util` -> `net.Gabou.projectatmosphere.modules.core`
- `net.Gabou.projectatmosphere.util` -> `net.Gabou.projectatmosphere.manager`
- `net.Gabou.projectatmosphere.util` -> `net.Gabou.projectatmosphere.modules.weather`
- `net.Gabou.projectatmosphere.modules.tornado` -> `net.Gabou.projectatmosphere.command`
- `net.Gabou.projectatmosphere.client.render` -> `net.Gabou.projectatmosphere.tools.debug`

## Call sites updated
- `ProjectAtmosphereCommands`
- `TornadoRenderDebugClientCommand`
- `ClientTickHandler`
- `SimpleCloudsTornadoRenderer`
- `CloudMeshGeneratorDiagnosticsMixin`
- `CloudMeshGeneratorShaderMixin`
- `DhSupportPipelineDiagnosticsMixin`
- `InstanceableMeshDiagnosticsMixin`
- `SimpleCloudsRendererDhFallbackMixin`
- `SimpleCloudsRendererDiagnosticsMixin`
- `AtmoApi`
- `AtmosphereManager`
- `SimpleCloudSpawner`
- `SimpleCloudsCompat`
- `CloudManager`

## Imports updated
- Updated all call-site imports for moved classes.
- Removed stale imports from the files above where package ownership changed.

## Build checkpoints
- Checkpoint 1: build failed because `TornadoLateRenderDiagnostics` still needed an import for `SimpleCloudsTornadoRenderer`.
- Checkpoint 2: build failed because `CloudDebugSnapshotFactory` in the protected cloud scaffold had a spelling mismatch in its factory method names.
- Checkpoint 3: build succeeded after the import fix and the minimal typo fix.

## Failed or reverted attempts
- No refactor group was reverted.
- One build failure was fixed by adding the missing renderer import to `TornadoLateRenderDiagnostics`.
- One build failure was fixed by correcting the protected cloud scaffold factory method spelling so the existing scaffold compiled again.

## Behavior risk review
- Low to medium risk overall.
- The refactor was package ownership and call-site rewiring only.
- The debug utilities remained debug utilities.
- The weather mapping enum remained a pure lookup contract.
- The cloud queue and spawn scheduler moved to clearer ownership packages without logic change.
- The protected cloud scaffold fix was a typo correction only, not a renderer implementation.

## Remaining classes intentionally not refactored
- `AtmosphereManager`
- `ForecastOrchestrator`
- `ForecastGenerator`
- `SimpleCloudsCompat`
- `SimpleCloudSpawner`
- `CloudManager`
- `ClientTickHandler`
- `SimpleCloudsTornadoRenderer`
- `SimpleCloudsHurricaneRenderer`
- `TornadoManager`
- `HurricaneManager`
- `RegionForecastOrchestrator`
- `ForecastRegion`
- `RegionAtmosphereState`
- `WeatherSampler` was moved, but its internal logic was not split.

## Why each remaining class was not refactored
- `AtmosphereManager`: still too central and broad for a safe split in the same pass.
- `ForecastOrchestrator`: broad coordination logic; better handled after boundary stabilization.
- `ForecastGenerator`: broad generation logic; would need a multi-step split.
- `SimpleCloudsCompat`: adapter and policy are still entangled; splitting it would be higher risk.
- `SimpleCloudSpawner`: policy-heavy spawn orchestration; safe package move would not be enough for a larger split yet.
- `CloudManager`: atmosphere runtime coordinator; changes here risk cloud behavior.
- `ClientTickHandler`: execution order and side effects are tightly coupled.
- `SimpleCloudsTornadoRenderer`: render behavior is fragile and intentionally deferred.
- `SimpleCloudsHurricaneRenderer`: same as tornado renderer.
- `TornadoManager`: broad lifecycle coordinator and render snapshot source.
- `HurricaneManager`: broad lifecycle coordinator and render snapshot source.
- `RegionForecastOrchestrator`: broad and central to forecast ownership.
- `ForecastRegion`: already partially reorganized; deeper split would be a separate step.
- `RegionAtmosphereState`: still contains compatibility and runtime state boundaries that need a separate pass.

## Remaining risks
- Broad lifecycle managers remain the main architectural risk.
- Renderer boundary classes under `client/render/` still need a dedicated pass.
- The protected `net.Gabou.projectatmosphere.clouds` package still needs a proper boundary strategy later, but was not part of this refactor step.

## Next recommended step
- Start a dedicated renderer-boundary pass around `client/render/` and the snapshot/caching contract, using the newly cleaned debug/helper package layout as the foundation.

