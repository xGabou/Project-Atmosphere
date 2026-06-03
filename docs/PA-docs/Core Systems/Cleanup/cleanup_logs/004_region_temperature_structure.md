# 004 Region / Temperature Structure

## Target Scope
- `src/main/java/net/Gabou/projectatmosphere/modules/region/`
- `src/main/java/net/Gabou/projectatmosphere/modules/temperature/`

## Files Reviewed
- 33 files

## Files Changed
- 3 files

## Exact Changes Made
- Added small section comments and improved readability ordering in:
  - `TemperatureCommands`
  - `TemperatureCommandHelper`
  - `SpikeStateStorage`
- No behavior, packet, config, command, or forecast logic changed.

## Classes Marked GOOD_AS_IS
- `ForecastRegionId`
- `RegionIdCodec`
- `RegionIndex`
- `RegionPersistence`
- `RegionAdapters`
- `RegionCurves`
- `BiomeForecastSnapshot`
- `BiomeForecastGenerator`
- `BiomeFallbackSnapshot`
- `DefaultRegionCurves`
- `FileRegionPersistence`
- `WeightedCurve`
- `WeightedWindCurve`
- `TemperatureProvider`
- `SpikeProvider`
- `BiomeTempUserConfig`
- `VariationGenerator`
- `SpikeType`
- `SpikeState`
- `SpikeData`
- `StartNewSpikeCommand`
- `ApplyRandomJoltCommand`
- `ApplyOngoingSpikeCommand`
- `SpikeCommands`

## Classes Marked REORGANIZED
- `TemperatureCommands`
- `TemperatureCommandHelper`
- `SpikeStateStorage`

## Classes Marked NEEDS_RENAME_LATER
- `ForecastRegion`
- `LegacyBiomeForecastGenerator`
- `TemperatureUtils`

## Classes Marked NEEDS_MOVE_LATER
- `TemperatureUtils`
- `ForecastRegion`
- `RegionForecastOrchestrator`
- `LegacyBiomeForecastGenerator`

## Classes Marked NEEDS_SPLIT_LATER
- `ForecastRegion`
- `RegionForecastOrchestrator`
- `BiomeTempConfig`
- `TemperatureGenerator`
- `SpikeManager`

## Classes Marked COULD_MERGE_LATER
- `TemperatureCommandHelper` and `TemperatureCommands`
- `SpikeState`, `SpikeData`, and storage helpers

## Classes Marked RISKY_LEAVE_AS_IS
- `BiomeTempConfig`
- `ForecastRegion`
- `RegionForecastOrchestrator`
- `TemperatureGenerator`
- `SpikeManager`

## Legacy/Debug/Rarely Used Code Moved
- None.

## Files Skipped and Why
- Broader or denser classes were reviewed but left unchanged because moving methods would have been noisy or risky.
- `BiomeTempConfig` was left alone because it is a large static mapping table.
- `TemperatureGenerator` was left alone because its flow was already clear enough.

## Build Result
- `.\gradlew.bat build` succeeded.

## Remaining Risks
- `ForecastRegion` and `RegionForecastOrchestrator` remain broad and are likely future split candidates.
- `BiomeTempConfig` is large but stable.

## Recommended Next Batch
- `src/main/java/net/Gabou/projectatmosphere/modules/region/`
