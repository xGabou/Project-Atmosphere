# 005 Region Dedicated Structure

## Target Scope
- `src/main/java/net/Gabou/projectatmosphere/modules/region/`

## Files Reviewed
- 18 files

## Files Changed
- 1 file

## Exact Changes Made
- Added section comments to `ForecastRegion`.
- Moved legacy aggregation methods in `ForecastRegion` to a bottom legacy section:
  - `clearBiomeForecasts()`
  - `addBiomeForecast(...)`
  - `finalizeAggregation()`
- No behavior, serialization, persistence, or forecast logic changed.

## Classes Marked GOOD_AS_IS
- `BiomeForecastGenerator`
- `BiomeForecastSnapshot`
- `BiomeFallbackSnapshot`
- `DefaultRegionCurves`
- `FileRegionPersistence`
- `ForecastRegionId`
- `GridRegionIndex`
- `LegacyBiomeForecastGenerator`
- `RegionAdapters`
- `RegionCurves`
- `RegionIdCodec`
- `RegionIndex`
- `RegionOrchestratorBootstrap`
- `RegionPersistence`
- `WeightedCurve`
- `WeightedWindCurve`

## Classes Marked REORGANIZED
- `ForecastRegion`

## Classes Marked NEEDS_RENAME_LATER
- `ForecastRegion`
- `RegionForecastOrchestrator`
- `LegacyBiomeForecastGenerator`

## Classes Marked NEEDS_MOVE_LATER
- `ForecastRegion`
- `RegionForecastOrchestrator`
- `LegacyBiomeForecastGenerator`

## Classes Marked NEEDS_SPLIT_LATER
- `ForecastRegion`
- `RegionForecastOrchestrator`

## Classes Marked COULD_MERGE_LATER
- None recorded in the original batch summary.

## Classes Marked RISKY_LEAVE_AS_IS
- `FileRegionPersistence`
- `RegionForecastOrchestrator`

## Legacy/Debug/Rarely Used Code Moved
- `ForecastRegion.clearBiomeForecasts()`
- `ForecastRegion.addBiomeForecast(...)`
- `ForecastRegion.finalizeAggregation()`

## Files Skipped and Why
- All non-region modules were skipped by scope.
- `RegionForecastOrchestrator` and `FileRegionPersistence` were left unchanged due to centrality and persistence risk.

## Build Result
- `.\gradlew.bat build` succeeded.

## Remaining Risks
- `ForecastRegion` remains broad, but the legacy path is now separated.
- `RegionForecastOrchestrator` is still a future split candidate.

## Recommended Next Batch
- `src/main/java/net/Gabou/projectatmosphere/modules/temperature/spike/`
