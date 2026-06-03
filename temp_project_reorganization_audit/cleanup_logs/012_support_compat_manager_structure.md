# 012_support_compat_manager_structure

## Target Scope
- `src/main/java/net/Gabou/projectatmosphere/util/`
- `src/main/java/net/Gabou/projectatmosphere/tools/debug/`
- `src/main/java/net/Gabou/projectatmosphere/async/`
- `src/main/java/net/Gabou/projectatmosphere/compat/`
- `src/main/java/net/Gabou/projectatmosphere/manager/`

## Files Reviewed
- `src/main/java/net/Gabou/projectatmosphere/util/AsyncAtmosphereService.java`
- `src/main/java/net/Gabou/projectatmosphere/util/AtmosphereUtils.java`
- `src/main/java/net/Gabou/projectatmosphere/util/AtmosphericPhysics.java`
- `src/main/java/net/Gabou/projectatmosphere/util/BiomeInstanceKey.java`
- `src/main/java/net/Gabou/projectatmosphere/util/CloudRegionQueue.java`
- `src/main/java/net/Gabou/projectatmosphere/util/CloudSpawnScheduler.java`
- `src/main/java/net/Gabou/projectatmosphere/util/DelayedTaskScheduler.java`
- `src/main/java/net/Gabou/projectatmosphere/util/HumidityGuard.java`
- `src/main/java/net/Gabou/projectatmosphere/util/HurricaneUpload.java`
- `src/main/java/net/Gabou/projectatmosphere/util/ICloudRegionId.java`
- `src/main/java/net/Gabou/projectatmosphere/util/InstrumentUtils.java`
- `src/main/java/net/Gabou/projectatmosphere/util/ParticleAtlasDebugger.java`
- `src/main/java/net/Gabou/projectatmosphere/util/RegionInstanceKey.java`
- `src/main/java/net/Gabou/projectatmosphere/util/RegionUpload.java`
- `src/main/java/net/Gabou/projectatmosphere/util/StorageUtils.java`
- `src/main/java/net/Gabou/projectatmosphere/util/TickCounter.java`
- `src/main/java/net/Gabou/projectatmosphere/util/TornadoUpload.java`
- `src/main/java/net/Gabou/projectatmosphere/util/UnitFormatter.java`
- `src/main/java/net/Gabou/projectatmosphere/util/WeatherSampler.java`
- `src/main/java/net/Gabou/projectatmosphere/util/WeatherType.java`
- `src/main/java/net/Gabou/projectatmosphere/tools/debug/ShaderSnapshot.java`
- `src/main/java/net/Gabou/projectatmosphere/async/BiomeSampler.java`
- `src/main/java/net/Gabou/projectatmosphere/async/PoolType.java`
- `src/main/java/net/Gabou/projectatmosphere/async/ThreadingDetector.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/ColdSweatCompat.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/CompatHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/LegendarySurvivalCompat.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/SimpleCloudsCompat.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/TemperatureMod.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/ToughAsNailsCompat.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/auroras/AuroraCompatController.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/auroras/AuroraSeasonHelper.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/rainbows/RainbowWeatherTracker.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/sky/AtmosphereSkyEffectController.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/sky/AtmosphereSkySample.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/sky/AtmosphereSkySampler.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/sky/SkyConditionMath.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/temperature/ClientTemperatureResolver.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereWorldEffectsManager.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/CropStressManager.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/ForecastDataStorage.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/ForecastGenerator.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/SandStormManager.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/SimpleCloudSpawner.java`

## Files Changed
- `src/main/java/net/Gabou/projectatmosphere/util/AsyncAtmosphereService.java`
- `src/main/java/net/Gabou/projectatmosphere/util/CloudRegionQueue.java`
- `src/main/java/net/Gabou/projectatmosphere/util/StorageUtils.java`
- `src/main/java/net/Gabou/projectatmosphere/util/WeatherSampler.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/CompatHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/SimpleCloudsCompat.java`

## Exact Changes Made
- `AsyncAtmosphereService.java`
  - Added section markers for bootstrap, public API, and helpers.
  - No async behavior changed.
- `CloudRegionQueue.java`
  - Added section markers for queue transfer, enqueue operations, queue state, and data carriers.
  - No queue behavior changed.
- `StorageUtils.java`
  - Added section markers for loading, saving, and path helpers.
  - No persistence behavior changed.
- `WeatherSampler.java`
  - Added section markers for region sampling, weather aggregation, fallback resolution, and output contract.
  - No sampling behavior changed.
- `CompatHandler.java`
  - Added section markers for module detection, temperature-mod selection, and initialization logging.
  - No compatibility behavior changed.
- `SimpleCloudsCompat.java`
  - Added section markers for initialization, spawn entry points, region creation, cloud region factory, and misc helpers.
  - No Simple Clouds behavior changed.

## Classes Marked GOOD_AS_IS
- `AtmosphereUtils`
- `AtmosphericPhysics`
- `BiomeInstanceKey`
- `DelayedTaskScheduler`
- `HumidityGuard`
- `HurricaneUpload`
- `ICloudRegionId`
- `InstrumentUtils`
- `ParticleAtlasDebugger`
- `RegionInstanceKey`
- `RegionUpload`
- `TickCounter`
- `TornadoUpload`
- `UnitFormatter`
- `WeatherType`
- `ShaderSnapshot`
- `BiomeSampler`
- `PoolType`
- `ThreadingDetector`
- `ColdSweatCompat`
- `LegendarySurvivalCompat`
- `TemperatureMod`
- `ToughAsNailsCompat`
- `AuroraCompatController`
- `AuroraSeasonHelper`
- `RainbowWeatherTracker`
- `AtmosphereSkyEffectController`
- `AtmosphereSkySample`
- `AtmosphereSkySampler`
- `SkyConditionMath`
- `ClientTemperatureResolver`
- `AtmosphereWorldEffectsManager`
- `CropStressManager`
- `ForecastDataStorage`
- `SandStormManager`

## Classes Marked REORGANIZED
- `AsyncAtmosphereService`
- `CloudRegionQueue`
- `StorageUtils`
- `WeatherSampler`
- `CompatHandler`
- `SimpleCloudsCompat`

## Classes Marked NEEDS_RENAME_LATER
- `CloudRegionQueue`
- `CloudSpawnScheduler`
- `ForecastGenerator`
- `SimpleCloudSpawner`
- `CompatHandler`

## Classes Marked NEEDS_MOVE_LATER
- `ParticleAtlasDebugger`
- `ShaderSnapshot`
- `CloudRegionQueue`

## Classes Marked NEEDS_SPLIT_LATER
- `AsyncAtmosphereService`
- `WeatherSampler`
- `SimpleCloudsCompat`
- `ForecastOrchestrator`
- `ForecastGenerator`
- `AtmosphereManager`
- `SimpleCloudSpawner`

## Classes Marked COULD_MERGE_LATER
- `CloudRegionQueue` and `CloudSpawnScheduler` only if scheduled cloud work is later centralized
- `AtmosphereSkySampler` and `AtmosphereSkySample` only if sky compatibility is simplified later
- `ColdSweatCompat`, `LegendarySurvivalCompat`, and `ToughAsNailsCompat` only if temperature-mod adapters are later flattened

## Classes Marked RISKY_LEAVE_AS_IS
- `ForecastOrchestrator`
- `ForecastGenerator`
- `AtmosphereManager`
- `SimpleCloudSpawner`
- `SimpleCloudsCompat`
- `CompatHandler`
- `AsyncAtmosphereService`

## Legacy, Fallback, Diagnostic, or Rarely Used Code Moved
- None.

## Files Skipped and Why
- `ForecastOrchestrator`, `ForecastGenerator`, `AtmosphereManager`, `SimpleCloudSpawner`, `SimpleCloudsCompat`, `CompatHandler`, and `AsyncAtmosphereService` were reviewed but not structurally reorganized beyond section comments because they are central orchestration classes and a deeper refactor would be risky.
- `Tool/debug` and many utility classes were reviewed and left untouched because they are already structurally readable enough or too low-value for further rearrangement.
- No manager lifecycle or async scheduling behavior was changed.

## Build Result
- `.\gradlew.bat build` succeeded

## Cleanup Log Files Updated
- `temp_project_reorganization_audit/cleanup_logs/cleanup_index.md`
- `temp_project_reorganization_audit/cleanup_logs/012_support_compat_manager_structure.md`

## Concrete Future Split Plan for Broad Classes If Needed
- `AsyncAtmosphereService`
  - Split bootstrap, scheduler selection, and thread execution helpers if a future task scheduling service emerges.
- `WeatherSampler`
  - Split sampling from fallback resolution if a renderer-facing weather snapshot path is introduced.
- `SimpleCloudsCompat`
  - Split initialization, region spawning, and region creation helpers into narrower adapters if cloud render or cloud spawn boundaries become stable.
- `ForecastOrchestrator`
  - Split startup/bootstrap, regeneration, sampling, and active-region tracking into separate services later.
- `ForecastGenerator`
  - Split region generation, daily averaging, packet broadcasting, and biome aggregation helpers if the generator continues to grow.
- `AtmosphereManager`
  - Split runtime update, initialization, and forecast-triggered refresh logic later.
- `SimpleCloudSpawner`
  - Split cloud severity calculation, async sampling, and spawn application later.

## Recommended Next Batch
- `src/main/java/net/Gabou/projectatmosphere/modules/seasonaltrees/`
