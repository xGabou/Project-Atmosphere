# 027 Risky Batch 20+ Restructure

Risky files selected
- `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericStateRegistry.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericUpdateScheduler.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/RegionAtmosphereState.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/CompatHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoInstance.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoProbabilityManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoSpawner.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneInstance.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsTornadoRenderer.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsHurricaneRenderer.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/SimpleCloudsRendererDiagnosticsMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/DefaultPipelineHurricaneMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/ShaderSupportPipelineHurricaneMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/MultiRegionCloudMeshGeneratorMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/compat/auroras/AuroraRendererMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/compat/rainbows/RainbowsRendererParticleMixin.java`

Total selected count
- 20

Files changed
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneImpactApplier.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneEnvironmentAnalyzer.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoClientSnapshotLogger.java`

Files moved
- None

Classes split
- `CycloneManager` -> `CycloneImpactApplier`
- `HurricaneManager` -> `HurricaneEnvironmentAnalyzer`
- `TornadoManager` -> `TornadoClientSnapshotLogger`

New helper classes created
- `CycloneImpactApplier`
- `HurricaneEnvironmentAnalyzer`
- `TornadoClientSnapshotLogger`

Classes deleted because empty
- None

Files resolved by category
- `TRUE_HANDLED_RESTRUCTURED`
  - `CycloneManager`
  - `HurricaneManager`
  - `TornadoManager`
- `TRUE_HANDLED_GOOD_AS_IS`
  - `TornadoInstance`
  - `TornadoSpawner`
  - `HurricaneInstance`
  - `SimpleCloudsRendererDiagnosticsMixin`
- `STILL_RISKY_MANUAL_REVIEW`
  - `AtmosphereManager`
  - `AtmosphericStateRegistry`
  - `AtmosphericUpdateScheduler`
  - `RegionAtmosphereState`
  - `CompatHandler`
  - `TornadoProbabilityManager`
  - `SimpleCloudsTornadoRenderer`
  - `SimpleCloudsHurricaneRenderer`
  - `DefaultPipelineHurricaneMixin`
  - `ShaderSupportPipelineHurricaneMixin`
  - `MultiRegionCloudMeshGeneratorMixin`
  - `AuroraRendererMixin`
  - `RainbowsRendererParticleMixin`

Files still risky from this batch
- `13`

Total risky files before this pass
- `58`

Total risky files resolved in this pass
- `7`

Total risky files remaining after this pass
- `51`

Build checkpoints run
- `2`

Build result
- `.\gradlew.bat build` succeeded

Remaining manual-only files
- `AtmosphereManager`
- `AtmosphericStateRegistry`
- `AtmosphericUpdateScheduler`
- `RegionAtmosphereState`
- `CompatHandler`
- `TornadoProbabilityManager`
- `SimpleCloudsTornadoRenderer`
- `SimpleCloudsHurricaneRenderer`
- `DefaultPipelineHurricaneMixin`
- `ShaderSupportPipelineHurricaneMixin`
- `MultiRegionCloudMeshGeneratorMixin`
- `AuroraRendererMixin`
- `RainbowsRendererParticleMixin`

Next recommended 20 file batch
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericStateRegistry.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericUpdateScheduler.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/CompatHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoProbabilityManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoSpawner.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoInstance.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneInstance.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/RegionAtmosphereState.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsTornadoRenderer.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsHurricaneRenderer.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/SideInfo.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/VolumeBoxMesh.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/DefaultPipelineHurricaneMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/ShaderSupportPipelineHurricaneMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/SimpleCloudsRendererDiagnosticsMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/MultiRegionCloudMeshGeneratorMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/compat/auroras/AuroraRendererMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/compat/rainbows/RainbowsRendererParticleMixin.java`
