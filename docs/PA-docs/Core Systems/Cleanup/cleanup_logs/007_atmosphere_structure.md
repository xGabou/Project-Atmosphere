# 007 Atmosphere Structure

## Target Scope
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/`

## Files Reviewed
- 14 files

## Files Changed
- 1 file

## Exact Changes Made
- Moved deprecated compatibility methods in `RegionAtmosphereState` into a bottom legacy section:
  - `fromForecast(BiomeInstanceKey, BiomeForecast)`
  - `getKey()`
- Added a legacy section marker.
- No sync, spawn, update, or persistence behavior changed.

## Classes Marked GOOD_AS_IS
- `AtmosphereStatusSyncManager`
- `CloudWaterExchange`
- `CloudWaterService`
- `CycloneSnapshot`
- `HumidityBudget`
- `HumidityBudgetService`
- `HumiditySourceProfile`
- `RainSystem`
- `SunlightController`

## Classes Marked REORGANIZED
- `RegionAtmosphereState`

## Classes Marked NEEDS_RENAME_LATER
- `RegionAtmosphereState`
- `AtmosphericStateRegistry`
- `AtmosphericUpdateScheduler`
- `AtmosphereStatusSyncManager`
- `CloudManager`
- `CloudWaterService`
- `CloudWaterExchange`
- `HumidityBudgetService`
- `CycloneManager`

## Classes Marked NEEDS_MOVE_LATER
- `AtmosphericStatusSyncManager`
- `CloudWaterExchange`
- `CloudWaterService`
- `HumidityBudget`
- `HumidityBudgetService`
- `CycloneSnapshot`

## Classes Marked NEEDS_SPLIT_LATER
- `RegionAtmosphereState`
- `AtmosphericStateRegistry`
- `AtmosphericUpdateScheduler`
- `CloudManager`
- `CycloneManager`
- `RainSystem`

## Classes Marked COULD_MERGE_LATER
- `CloudWaterService` and `CloudWaterExchange`
- `HumidityBudget` and `HumidityBudgetService`

## Classes Marked RISKY_LEAVE_AS_IS
- `AtmosphericStateRegistry`
- `AtmosphericUpdateScheduler`
- `CloudManager`
- `CycloneManager`
- `RegionAtmosphereState`

## Legacy/Debug/Rarely Used Code Moved
- `RegionAtmosphereState.fromForecast(BiomeInstanceKey, BiomeForecast)`
- `RegionAtmosphereState.getKey()`

## Files Skipped and Why
- `AtmosphericStateRegistry`, `AtmosphericUpdateScheduler`, `CloudManager`, and `CycloneManager` were reviewed but left unchanged because they are central runtime classes and higher-risk than the payoff warranted.
- Helper classes were already well-contained enough to leave alone.

## Build Result
- `.\gradlew.bat build` succeeded.

## Remaining Risks
- The atmosphere runtime layer is still broad and will need future split work.
- The state registry and cloud manager are the main future architecture risks.

## Recommended Next Batch
- `src/main/java/net/Gabou/projectatmosphere/client/`
