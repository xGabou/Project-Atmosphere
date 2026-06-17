# Project Atmosphere Task 2B Implementation Report

## Scope

Implemented seasonal drift as a persistent live-atmosphere layer and removed forecast regeneration from the normal season-change gameplay path.

No storm cells, tornadoes, hurricanes, blizzards, rendering, shaders, cloud spawning, or cloud evolution work was started.

## Modified Files

1. `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/SeasonalAtmosphericDrift.java`
   - New PA-native atmosphere service.
   - Owns seasonal modifier state.
   - Reads season data through `SeasonTimeHelper.snapshot(level)` only.
   - Smoothly nudges live `RegionAtmosphereState` values toward season-adjusted targets.
   - Saves and restores its own modifier state.

2. `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericStateSavedData.java`
   - Added `SeasonalDrift` to the live-atmosphere saved payload.
   - Drift state is saved beside live atmosphere, scheduler, cyclone, ocean, and wind runtime state.
   - Forecast data is not duplicated.

3. `src/main/java/net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java`
   - Changed `regenerateForSeason(ServerLevel)` so season changes no longer clear or regenerate forecasts.
   - The legacy method now routes season changes to `SeasonalAtmosphericDrift.onSeasonChanged(level)`.
   - Added `SeasonalAtmosphericDrift.tick(level)` to the normal gameplay tick path before atmospheric updates.

4. `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java`
   - Changed `onSeasonChange(ServerLevel)` to call the lightweight seasonal drift path directly instead of scheduling forecast regeneration.
   - Added seasonal drift reset to runtime reset.

5. `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericUpdateScheduler.java`
   - Replaced the old hardcoded 96-day `seasonalTilt(dayTime)` sunlight factor with `SeasonalAtmosphericDrift.sunlightMultiplier()`.
   - Existing live atmosphere update logic still handles ocean influence, wind transport, humidity budget, cloud water exchange, rain cooling, and forecast-target restoration.

## Regeneration Paths Found

1. Server start with saved forecast data
   - Path: `ForecastOrchestrator.onServerStart`.
   - Behavior: loads forecast from `ForecastDataStorage`.
   - Status: remains.
   - Reason: not regeneration during normal season change.

2. Server start with missing forecast data
   - Path: `ForecastOrchestrator.onServerStart`.
   - Behavior: generates forecast from saved player centers or spawn.
   - Status: remains.
   - Reason: allowed by Task 2B as missing-data bootstrap.

3. Server start with corrupt forecast data
   - Path: `ForecastOrchestrator.onServerStart` catch block.
   - Behavior: clears corrupt forecast data and regenerates from spawn.
   - Status: remains.
   - Reason: allowed as recovery from invalid saved forecast data.

4. Explicit admin regeneration
   - Path: `/pa forecast regenerate` -> `CommandForecastService.regenerateForecast` -> `AtmosphereManager.onRegenerate` -> `ForecastOrchestrator.clearAndRegenerate`.
   - Status: remains.
   - Reason: explicit admin/debug regeneration is allowed.

5. Legacy debug regeneration
   - Path: `/weatherdebug forecast regenerate` -> `DebugAtmoCommand` -> `AtmosphereManager.onRegenerate`.
   - Status: remains.
   - Reason: explicit debug/admin regeneration is allowed.

6. Region update/load generation
   - Paths: `ForecastOrchestrator.updateForecast`, `ensureForecastRegionLoaded`, `RegionForecastOrchestrator.loadOrGenerate`, and `ForecastDataStorage` legacy migration repair.
   - Status: remains.
   - Reason: these paths load, repair, or explicitly update region forecast data. They are not season-change regeneration.

7. Season-change regeneration
   - Previous path: `AtmosphereManager.onSeasonChange` -> async `ForecastOrchestrator.regenerateForSeason` -> `ForecastGenerator.clearForecasts` -> forecast regeneration.
   - New path: `AtmosphereManager.onSeasonChange` -> `ForecastOrchestrator.regenerateForSeason` -> `SeasonalAtmosphericDrift.onSeasonChanged`.
   - Status: replaced.
   - Result: no forecast clear, no forecast generation, no wind forecast rebuild, no dynamic system reinitialization from season changes.

## Season Delegate Architecture Used

Seasonal drift uses:

`SeasonTimeHelper` -> active `SeasonTimeDelegate` -> `SeasonSnapshot`

The drift system only consumes:

1. `SeasonSnapshot.providerId`
2. `SeasonSnapshot.stage`
3. `SeasonSnapshot.progress`
4. `SeasonSnapshot.temperatureOffset`

No Serene Seasons or Ecliptic Seasons classes are imported by the new drift logic.

## Season Providers Verified

Verified from inspected code:

1. Ecliptic Seasons
   - `SeasonBootstrap` installs `EclipticSeasonsSeasonDelegate` when `eclipticseasons` is loaded.
   - `EclipticSeasonsSeasonDelegate` returns `SeasonSnapshot` with stage and progress.
   - `EclipticTracker.onSolarTermChange` calls `AtmosphereManager.onSeasonChange` when the season changes.

2. Serene Seasons
   - `SeasonBootstrap` installs `SereneSeasonsSeasonDelegate` when `sereneseasons` is loaded.
   - `SereneSeasonsSeasonDelegate` returns `SeasonSnapshot` with stage and progress.
   - `SereneSeasonsEventBridge` calls `AtmosphereManager.onSeasonChange` for standard and tropical season changes.

3. Project Atmosphere for TFC
   - `SeasonBootstrap` installs `TfcSeasonDelegate` when `projectatmospherefortfc` is loaded.
   - Its implementation was not modified by Task 2B.
   - Drift compatibility depends on that delegate returning a valid `SeasonSnapshot`.

4. Neutral fallback
   - `SeasonTimeHelper` has an internal neutral delegate.
   - Drift treats neutral as no seasonal modifier.

`SeasonProviderRegistry` also exists, but the active runtime season path used by current atmosphere code is `SeasonTimeHelper` and `SeasonTimeDelegate`.

## Delegate Extensions Required

No delegate extension was required.

Existing `SeasonSnapshot` already provides the required fields:

1. Current season stage.
2. Seasonal progress.
3. Provider id.
4. Temperature offset hook.

## Seasonal Modifier Model

The modifier layer is stored in `SeasonalAtmosphericDrift`.

Per season, it defines:

1. Temperature offset.
2. Humidity multiplier.
3. Humidity offset.
4. Pressure offset.
5. Cloud water capacity.
6. Cloud water bias.
7. Sunlight multiplier.

Seasonal progress is used to scale the modifier strength with a smooth in-season curve.

Winter:

1. Lower temperature target.
2. Lower humidity capacity.
3. Higher pressure tendency.
4. Lower cloud water capacity.
5. Lower sunlight multiplier.

Summer:

1. Higher temperature target.
2. Higher humidity capacity.
3. Lower pressure tendency.
4. Higher cloud water capacity.
5. Higher sunlight multiplier.

Spring:

1. Moderate warming.
2. Increased moisture.
3. Slightly higher cloud water capacity.

Autumn:

1. Moderate cooling.
2. Reduced moisture.
3. Slightly higher pressure tendency.

## Atmospheric Transition Behavior

Season changes do not immediately replace live atmospheric values.

Every 200 ticks, drift moves live region state toward season-adjusted targets using bounded steps.

Active region rates:

1. Temperature: 2.5 percent of target gap, max 0.25 C per interval.
2. Humidity: 1.8 percent of target gap, max 0.006 normalized humidity per interval.
3. Pressure: 1.4 percent of target gap, max 0.20 hPa per interval.
4. Cloud water: 1.2 percent of target gap, max 0.004 normalized cloud water per interval.

Passive region rates:

1. Temperature: 0.8 percent of target gap, max 0.08 C per interval.
2. Humidity: 0.6 percent of target gap, max 0.002 normalized humidity per interval.
3. Pressure: 0.4 percent of target gap, max 0.06 hPa per interval.
4. Cloud water: 0.4 percent of target gap, max 0.0015 normalized cloud water per interval.

This produces gradual convergence like `20 C -> 19.75 C -> 19.50 C`, not an immediate jump to the new seasonal target.

## Persistence Changes

Seasonal drift is saved in the live-atmosphere saved data under:

`LiveAtmosphere.SeasonalDrift`

Saved fields include:

1. Version.
2. Initialized flag.
3. Provider id.
4. Season stage.
5. Season progress.
6. Snapshot temperature offset.
7. Last drift tick.
8. Last transition game time.
9. Current seasonal modifier fields.

Forecast data is not stored in the seasonal drift payload.

Cloud region persistence remains independent.

## Save And Load Flow

Save:

1. `ForecastOrchestrator.onServerStop`.
2. `AtmosphericStateSavedData.snapshot(level)`.
3. Captures region atmosphere states.
4. Captures scheduler state.
5. Captures cyclone state.
6. Captures ocean basin state.
7. Captures wind runtime state.
8. Captures seasonal drift state.
9. Forecast persistence still runs separately through `ForecastDataStorage.saveAll(level)`.

Load:

1. `ForecastOrchestrator.onServerStart`.
2. Forecast data loads or is generated only if missing/corrupt.
3. Dynamic systems initialize from forecast-backed regions.
4. `AtmosphericStateSavedData.restore(level)` overlays mutable live atmosphere.
5. Seasonal drift state restores from `LiveAtmosphere.SeasonalDrift`.
6. On the next normal tick, drift continues from the restored modifier state.

## Season Change Handling

Natural progression:

1. `AtmosphereManager.tick` calls `checkSeasonTransition`.
2. `checkSeasonTransition` uses `SeasonTimeHelper.stage(level)`.
3. A stage change calls `AtmosphereManager.onSeasonChange`.
4. Drift target updates without forecast regeneration.

Serene Seasons transitions and commands:

1. Existing Serene event bridge calls `AtmosphereManager.onSeasonChange`.
2. Manual commands that change Serene season should trigger the same Serene event path if the provider fires its standard event.
3. If an event is missed, the normal tick stage comparison still catches the changed delegate snapshot.

Ecliptic Seasons transitions:

1. Existing Ecliptic tracker calls `AtmosphereManager.onSeasonChange` when solar term season changes.
2. Normal tick stage comparison also catches changed delegate snapshots.

Project Atmosphere debug/admin season override:

1. No PA-native season override command was found in inspected command paths.
2. Any future override that changes the active delegate snapshot will be detected by the normal tick comparison.
3. Any future override that calls `AtmosphereManager.onSeasonChange` will use the same drift path.

## Validation

`./gradlew compileJava` passed.

Verified by code inspection:

1. Season changes no longer call forecast clear or generation.
2. Explicit `/pa forecast regenerate` still calls full regeneration.
3. Legacy `/weatherdebug forecast regenerate` still calls full regeneration.
4. Missing/corrupt forecast bootstrap paths still generate forecast data.
5. Drift uses only `SeasonTimeHelper`/`SeasonSnapshot`/`SeasonStage`.
6. No direct dependency on Serene Seasons or Ecliptic Seasons was introduced in new drift code.
7. Seasonal drift is persisted in live-atmosphere data, not forecast data.
8. Cloud region persistence was not modified.

## Notes

The method name `ForecastOrchestrator.regenerateForSeason` remains for compatibility with existing callers, but its behavior has changed. It no longer regenerates forecasts. It now applies a seasonal drift target to the mutable atmosphere layer.

