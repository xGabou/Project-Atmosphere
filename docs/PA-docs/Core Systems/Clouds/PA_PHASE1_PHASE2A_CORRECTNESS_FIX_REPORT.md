# Phase 1 / Phase 2A Correctness Fix Report

Scope: fixed only the correctness issues identified in `PA_PHASE1_PHASE2A_VERIFICATION_AUDIT.md`.

Not started:
- Phase 2B
- Forecast regeneration changes
- Seasonal drift
- Storm cells
- Tornado migration
- Hurricane migration
- Blizzard work
- Rendering
- Shaders
- Precipitation rendering

## Modified Files

### `src/main/java/net/Gabou/projectatmosphere/modules/wind/WindRuntimeState.java`
Reason: added NBT save/load support for the mutable wind runtime state.

Persisted fields:
- Current high wind speed
- Current high wind direction
- Current low wind speed
- Current low wind direction
- Current gust bonus
- Gust active flag
- Gust end tick

### `src/main/java/net/Gabou/projectatmosphere/modules/wind/WindEngine.java`
Reason: added map-level persistence for `WindEngine` runtime states.

Changes:
- Added `savePersistentState()`.
- Added `loadPersistentState(...)`.
- Saves runtime wind state by `RegionInstanceKey`.
- Restores only entries whose region still has a forecast-backed `WindForecast`.
- Does not save or duplicate forecast wind data.

### `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericStateSavedData.java`
Reason: integrated `WindEngine` runtime persistence into the live atmosphere saved data.

Changes:
- Saves wind runtime state under `WindEngine`.
- Restores wind runtime state during live atmosphere restore.
- Restore happens after forecast-backed wind forecasts have been rebuilt, preserving the existing load order.

### `src/main/java/net/Gabou/projectatmosphere/clouds/service/NativeAtmosphereCloudService.java`
Reason: fixed automatic cloud birth accounting.

Changes:
- Drains cloud water only after `CloudGroupSpawner.spawnRequestedCloud(...)` returns a created native cloud region.
- Does not drain cloud water for command-created clouds.
- Removed direct ocean-flux scoring from cloud birth.

## Issue 1: WindEngine Runtime State

Problem:
`RegionAtmosphereState.wind` was saved and restored, but `WindEngine` kept its own mutable `WindRuntimeState` map. On the next `WindEngine.tick`, fresh runtime state could overwrite restored region wind.

Fix:
`WindEngine` now persists its runtime map through the live atmosphere save.

Continuity path:
1. Forecast data loads as before.
2. `WindEngine.rebuildFromRegions(...)` rebuilds immutable forecast wind sources.
3. `AtmosphericStateSavedData.restore(...)` restores `RegionAtmosphereState`.
4. `AtmosphericStateSavedData.restore(...)` restores `WindEngine` runtime state.
5. The first later `WindEngine.tick` continues from restored smoothing/gust runtime instead of starting from empty runtime state.

Forecast duplication:
- No forecast arrays are saved.
- Only mutable wind runtime values are saved.
- Restore ignores stale wind runtime entries whose forecast key no longer exists.

## Issue 2: Cloud Water Consumption

Problem:
Automatic cloud birth used `cloudWater` in scoring but did not drain it after a cloud was born.

Fix:
Automatic birth now drains cloud water only after successful native cloud creation.

Drain amounts:
- `vapor_cluster`: `0.01`
- `cumulus_humilis`: `0.025`
- `cumulus_mediocris`: `0.05`

Safety:
- Drain is bounded and conservative.
- Drain uses `Math.max(0.0F, current - drain)`.
- Command spawning does not call this drain path.

## Issue 3: Ocean Influence Double Counting

Problem:
Ocean flux already modifies humidity and cloud water through `AtmosphericUpdateScheduler`, but cloud birth also scored `OceanBasinManager.estimateHumidityFlux(...)` directly.

Fix:
Direct `oceanScore` was removed from `NativeAtmosphereCloudService`.

Result:
- Ocean influence now affects cloud birth through live humidity and cloud water.
- The spawn score no longer applies a second direct ocean-flux boost.

## Validation

Command run:

```powershell
.\gradlew.bat compileJava
```

Result:
- Build succeeded.
- Existing project warnings remain, unrelated to this correction pass.

Additional check:

```powershell
git diff --check
```

Result:
- No whitespace errors.
- Git reported existing line-ending normalization warnings only.

## Confirmation Of Scope

No code was changed for:
- Forecast generation
- Forecast regeneration
- Season handling
- Storm cells
- Tornadoes
- Hurricanes
- Blizzards
- Rendering
- Shaders
- Precipitation rendering

Stopped after the requested correctness fixes.
