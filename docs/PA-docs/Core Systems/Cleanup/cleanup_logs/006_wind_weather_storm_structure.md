# 006 Wind / Weather / Storm Structure

## Target Scope
- `src/main/java/net/Gabou/projectatmosphere/modules/wind/`
- `src/main/java/net/Gabou/projectatmosphere/modules/weather/`
- `src/main/java/net/Gabou/projectatmosphere/modules/storm/`

## Files Reviewed
- 25 files

## Files Changed
- 2 files

## Exact Changes Made
- Added section comments to:
  - `WindCommand`
  - `GlobalStormHistoryData`
- No behavior changes were made.

## Classes Marked GOOD_AS_IS
- `FloatRange`
- `HighWindModel`
- `LowWindModel`
- `RegionWindForecastApi`
- `TornadoWindModel`
- `WindConfig`
- `WindForces`
- `WindForecast`
- `WindForecastApi`
- `WindForecastPart`
- `WindMath`
- `WindRuntimeState`
- `RegionalWeatherPhase`
- `StormLifecyclePhase`
- `StormMotionModel`
- `StormSeverityScale`
- `StormShieldManager`
- `StormChanceAdjuster`
- `StormGenerator`
- `StormLullHook`

## Classes Marked REORGANIZED
- `WindCommand`
- `GlobalStormHistoryData`

## Classes Marked NEEDS_RENAME_LATER
- `WindCommand`
- `WindForecastPart`
- `WindForecastApi`
- `RegionWindForecastApi`
- `ServerWeatherStateResolver`
- `StormChanceAdjuster`
- `GlobalStormHistoryData`

## Classes Marked NEEDS_MOVE_LATER
- `RegionWindForecastApi`
- `ServerWeatherStateResolver`
- `GlobalStormHistoryData`

## Classes Marked NEEDS_SPLIT_LATER
- `WindEngine`
- `WindGenerator`
- `ServerWeatherStateResolver`
- `StormShieldManager`
- `StormMotionModel`

## Classes Marked COULD_MERGE_LATER
- `WindForecastApi` and `RegionWindForecastApi`
- `RegionalWeatherPhase` and `StormLifecyclePhase` only if the weather model is later simplified

## Classes Marked RISKY_LEAVE_AS_IS
- `WindEngine`
- `WindGenerator`
- `ServerWeatherStateResolver`
- `StormShieldManager`
- `StormMotionModel`

## Legacy/Debug/Rarely Used Code Moved
- None.

## Files Skipped and Why
- `WindEngine`, `WindGenerator`, `ServerWeatherStateResolver`, `StormShieldManager`, and `StormMotionModel` were reviewed but left untouched because they are central and behavior-sensitive.
- Smaller helper/DTO classes were already clean enough.

## Build Result
- `.\gradlew.bat build` succeeded.

## Remaining Risks
- The central resolver and wind engine remain broad.
- The storm and weather modules should stay stable until later split work.

## Recommended Next Batch
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/`
