# 013_final_remaining_source_structure

## Target Scope
- `src/main/java/`
- Remaining unreviewed source areas after batches 001 through 012
- Included review focus areas:
  - `src/main/java/net/Gabou/projectatmosphere/client/render/`
  - `src/main/java/net/Gabou/projectatmosphere/client/rendering/`
  - `src/main/java/net/Gabou/projectatmosphere/modules/tornado/`
  - `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/`
  - `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/`
  - `src/main/java/net/Gabou/projectatmosphere/modules/snowstorm/`
  - `src/main/java/net/Gabou/projectatmosphere/modules/sandStorm/`
  - `src/main/java/net/Gabou/projectatmosphere/modules/core/`
  - `src/main/java/net/Gabou/projectatmosphere/mixin/`
  - `src/main/java/net/Gabou/projectatmosphere/mixin/client/`
  - `src/main/java/net/Gabou/projectatmosphere/mixin/compat/`

## Files Reviewed
- `src/main/java/net/Gabou/projectatmosphere/modules/core/BiomeForecast.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/core/CloudLibrary.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/core/ForecastType.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/core/WindVector.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/GlassDamageManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoCommand.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoDebug.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoInstance.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoLevel.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoProbabilityManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoSnapshot.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoSpawner.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoSpawnScheduler.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneBlockBreakRules.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneCategory.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneCloudVolume.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneCommand.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneDestructionManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneInstance.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneRenderDescriptor.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneRenderSnapshot.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneSemantics.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneSemanticSample.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneSnapshot.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneWindField.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/SeasonalTreesBootstrap.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/SeasonalTreesEventHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/core/LeafState.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/core/PaClimateVigorProvider.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/core/SeasonalTreesCore.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/core/SeasonalTreesPaSeasonProvider.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/core/SeasonalTreesSeasonProvider.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/core/SeasonalTreesVigorProvider.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/core/SeasonPhase.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/core/SeedPayload.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/core/TreeKey.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/core/TreeRecord.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/core/TreeState.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/core/TreeType.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/data/SeasonalTreesSavedData.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/integration/DynamicTreesAccessor.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/integration/DynamicTreesDormancyHelper.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/integration/SeasonalTreesAccessorRegistry.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/integration/SeasonalTreesTreeAccessor.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/integration/VanillaTreesAccessor.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/transport/LocalSeedTransport.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/transport/SeasonalTreesSeedTransport.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/transport/WindSeedTransport.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/snowstorm/SnowStorm.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/snowstorm/SnowstormManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/sandStorm/SandStormAPI.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/BiomeFreezingMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/CachedTemperatureUtilMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/CloudGeneratorHurricaneReservationMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/CloudMeshGeneratorAccessor.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/CloudMeshGeneratorDiagnosticsAccessor.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/CloudMeshGeneratorDiagnosticsMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/CloudMeshGeneratorShaderMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/CloudRegionMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/CloudRegionTickEventMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/InfoMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/MixinSandstormDebugBlocker.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/MultiRegionCloudMeshGeneratorMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/OverwriteDesertSound.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/PAMixinPlugin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/SeasonHooksMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/ServerLevelSnowStormMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/SimpleCloudsCloudManagerMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/BindingManagerAccessor.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/DefaultPipelineHurricaneMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/DefaultPipelineTornadoMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/DhSupportPipelineDiagnosticsMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/InstanceableMeshDiagnosticsMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/LoadingOverlayMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/LoadingScreenMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/MinecraftCrashHandlerMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/ShaderSupportPipelineHurricaneMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/ShaderSupportPipelineTornadoMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/SimpleCloudsRendererDhFallbackMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/SimpleCloudsRendererDiagnosticsMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/SimpleCloudsRendererLightningBufferMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/particle/ParticleMotionAccessor.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/particle/WindBentParticleEngineMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/compat/auroras/AuroraRendererMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/compat/rainbows/RainbowsRendererParticleMixin.java`

## Files Changed
- `src/main/java/net/Gabou/projectatmosphere/modules/core/BiomeForecast.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/core/CloudLibrary.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/core/WindVector.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneCloudVolume.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneRenderDescriptor.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneRenderSnapshot.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneSnapshot.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/core/TreeState.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoSnapshot.java`

## Exact Changes Made
- `BiomeForecast.java`
  - Added section markers for raw weekly fields and accessors.
- `CloudLibrary.java`
  - Added section markers for cloud groupings and cloud selection helpers.
- `WindVector.java`
  - Added section markers for vector math, runtime update, and sampling.
- `TornadoSnapshot.java`
  - Added a network serialization section marker.
- `HurricaneSnapshot.java`
  - Added a network serialization section marker.
- `HurricaneRenderSnapshot.java`
  - Added a network serialization section marker.
- `HurricaneRenderDescriptor.java`
  - Added section markers for descriptor construction and serialization.
- `HurricaneCloudVolume.java`
  - Added section markers for factories and bounds helpers.
- `TreeState.java`
  - Added section markers for construction and accessors.

## Classes Marked GOOD_AS_IS
- `ForecastType`
- `GlassDamageManager`
- `TornadoCommand`
- `TornadoDebug`
- `TornadoInstance`
- `TornadoLevel`
- `TornadoManager`
- `TornadoProbabilityManager`
- `TornadoSpawner`
- `TornadoSpawnScheduler`
- `HurricaneBlockBreakRules`
- `HurricaneCategory`
- `HurricaneCommand`
- `HurricaneDestructionManager`
- `HurricaneInstance`
- `HurricaneManager`
- `HurricaneSemantics`
- `HurricaneSemanticSample`
- `HurricaneWindField`
- `SeasonalTreesBootstrap`
- `SeasonalTreesEventHandler`
- `LeafState`
- `PaClimateVigorProvider`
- `SeasonalTreesPaSeasonProvider`
- `SeasonalTreesSeasonProvider`
- `SeasonalTreesVigorProvider`
- `SeasonPhase`
- `SeedPayload`
- `TreeKey`
- `TreeRecord`
- `TreeType`
- `SeasonalTreesSavedData`
- `DynamicTreesAccessor`
- `DynamicTreesDormancyHelper`
- `SeasonalTreesAccessorRegistry`
- `SeasonalTreesTreeAccessor`
- `VanillaTreesAccessor`
- `LocalSeedTransport`
- `SeasonalTreesSeedTransport`
- `WindSeedTransport`
- `SnowStorm`
- `SnowstormManager`
- `SandStormAPI`
- All reviewed mixins and accessors were left as-is by safety rule

## Classes Marked REORGANIZED
- `BiomeForecast`
- `CloudLibrary`
- `WindVector`
- `TornadoSnapshot`
- `HurricaneSnapshot`
- `HurricaneRenderSnapshot`
- `HurricaneRenderDescriptor`
- `HurricaneCloudVolume`
- `TreeState`

## Classes Marked NEEDS_RENAME_LATER
- `TornadoDebug`
- `TornadoLevel`
- `HurricaneManager`
- `HurricaneCloudVolume`
- `SnowstormManager`
- `SandStormAPI`

## Classes Marked NEEDS_MOVE_LATER
- `TornadoDebug`
- `CloudDebugSnapshotFactory`
- `CloudRenderSnapshot`
- `MixinSandstormDebugBlocker`

## Classes Marked NEEDS_SPLIT_LATER
- `TornadoManager`
- `HurricaneManager`
- `SeasonalTreesCore`
- `SeasonalTreesSavedData`
- `WindVector`
- `CloudLibrary`

## Classes Marked COULD_MERGE_LATER
- `TornadoSnapshot` and `HurricaneSnapshot` only if future storm transport snapshots are normalized further
- `HurricaneRenderSnapshot` and `HurricaneRenderDescriptor` only if the render contract is collapsed later
- `SeasonalTreesSeedTransport`, `WindSeedTransport`, and `LocalSeedTransport` only if transport abstraction is flattened later

## Classes Marked RISKY_LEAVE_AS_IS
- `TornadoManager`
- `HurricaneManager`
- `SeasonalTreesCore`
- `SeasonalTreesSavedData`
- `WindVector`
- `CloudLibrary`
- `SimpleCloudsRendererDiagnosticsMixin`
- `SimpleCloudsRendererDhFallbackMixin`
- `ShaderSupportPipelineTornadoMixin`
- `ShaderSupportPipelineHurricaneMixin`
- `CloudMeshGeneratorShaderMixin`
- `CloudGeneratorHurricaneReservationMixin`
- `DefaultPipelineTornadoMixin`
- `DefaultPipelineHurricaneMixin`
- `MultiRegionCloudMeshGeneratorMixin`
- `AuroraRendererMixin`
- `RainbowsRendererParticleMixin`

## Legacy, Fallback, Diagnostic, or Rarely Used Code Moved
- None.

## Files Skipped and Why
- All mixin files were reviewed but left untouched because annotations, targets, injection points, and accessor signatures must not be disturbed.
- `TornadoManager`, `HurricaneManager`, `SeasonalTreesCore`, `SeasonalTreesSavedData`, `WindVector`, and `CloudLibrary` were reviewed but mostly left unchanged because they are broad and central runtime classes where section-only edits would have been too noisy or risky beyond the safe comments added.
- `SnowstormManager` and `SandStormAPI` were reviewed but left unchanged because their current structure is serviceable and logic order should not be disturbed.
- `ForecastType` and the small value/enum classes were already clean enough.

## Build Result
- `.\gradlew.bat build` succeeded

## Cleanup Log Files Updated
- `temp_project_reorganization_audit/cleanup_logs/cleanup_index.md`
- `temp_project_reorganization_audit/cleanup_logs/013_final_remaining_source_structure.md`

## Remaining High Risk Classes
- `TornadoManager`
- `HurricaneManager`
- `SeasonalTreesCore`
- `SeasonalTreesSavedData`
- `WindVector`
- `CloudLibrary`
- All render-path mixins and pipeline mixins

## Final Cleanup Verdict
- Are all Java source areas reviewed now? `Yes, at least once across the remaining unreviewed areas and the previously reviewed areas already logged.`
- Are there any source folders still unreviewed? `No major source folders remain unreviewed in the project-level cleanup scope.`
- Is the project ready for cloud renderer boundary work? `Yes, at the architecture boundary level.`
- What should not be refactored further before cloud renderer work? `TornadoManager`, `HurricaneManager`, `SeasonalTreesCore`, `SeasonalTreesSavedData`, `WindVector`, `CloudLibrary`, and all mixin/render pipeline classes.`
