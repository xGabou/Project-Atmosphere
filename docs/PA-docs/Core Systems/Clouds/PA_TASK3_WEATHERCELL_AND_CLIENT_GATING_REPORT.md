# Project Atmosphere Task 3 Report

## Scope

Implemented the WeatherCell foundation with only `RAIN_CELL` automatic formation and conservative rain feedback.

Audited PA cloud server/common paths and new WeatherCell classes for client-only references.

No tornadoes, hurricanes, blizzards, final cloud morphology, cloud rendering visuals, forecast rewrite, Simple Clouds behavior changes, or seasonal drift changes were implemented.

## New Files

1. `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellType.java`
   - Defines future cell types: `RAIN_CELL`, `THUNDERSTORM`, `SUPERCELL`, `CYCLONE`, `BLIZZARD`.
   - Only `RAIN_CELL` is activated by formation logic.

2. `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellState.java`
   - Server-authoritative mutable cell state.
   - Stores id, type, source region, center, radius, intensity, moisture, instability, pressure anomaly, wind influence, cloud water, rain intensity, age, lifetime, active flag, and optional linked native cloud ids.
   - Includes NBT save/load.

3. `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellSavedData.java`
   - Independent Minecraft `SavedData` layer for WeatherCells.
   - Stores active/persisted cells under `project_atmosphere_weather_cells`.
   - Does not store forecast arrays or duplicate forecast data.

4. `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellManager.java`
   - Main tick entry point.
   - Loads `WeatherCellSavedData`, ticks active cells, removes inactive cells, and triggers periodic formation.

5. `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellFormationController.java`
   - Forms only `RAIN_CELL` from live `RegionAtmosphereState`.
   - Uses humidity, cloud water, pressure, wind convergence, existing rain intensity, existing nearby cell coverage, and cloud cover.

6. `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellLifecycleController.java`
   - Ages cells, adjusts intensity gradually, decays unsupported cells, removes expired cells, and applies conservative local rain/cloud-water feedback.

7. `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellMotionController.java`
   - Moves cells with Project Atmosphere wind through `ForecastOrchestrator.getWind`.

8. `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellSupport.java`
   - Shared support math for wind convergence and nearby WeatherCell coverage.

9. `src/main/java/net/Gabou/projectatmosphere/clouds/network/CloudRegionPacketDispatcher.java`
   - Common-safe indirection for PA cloud client cache access.
   - Client cache sink/supplier is registered from `ClientOnlyRegistrar`.

## Modified Files

1. `src/main/java/net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java`
   - Added `WeatherCellManager.tick(level)` to the normal gameplay tick path after live atmosphere updates.

2. `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java`
   - Resets WeatherCell runtime scheduling state during server lifecycle reset.

3. `src/main/java/net/Gabou/projectatmosphere/clouds/network/SyncCloudRegionsPacket.java`
   - Removed direct import of `ClientCloudRegionDataCache`.
   - Packet now calls common-safe `CloudRegionPacketDispatcher` behind `Dist.CLIENT` execution.

4. `src/main/java/net/Gabou/projectatmosphere/clouds/WeatherCloudQueries.java`
   - Removed direct import of `ClientCloudRegionDataCache`.
   - Server queries still use `CloudRegionStateStore`.
   - Client queries now use the common-safe dispatcher supplier.

5. `src/main/java/net/Gabou/projectatmosphere/registry/ClientOnlyRegistrar.java`
   - Registers client cloud cache sink/supplier into `CloudRegionPacketDispatcher`.
   - This file is already `@OnlyIn(Dist.CLIENT)`.

## WeatherCell Architecture Summary

Runtime path:

`Forecast` -> `Persistent Atmosphere` -> `WeatherCellManager` -> `WeatherCellState` -> conservative atmosphere feedback -> existing clouds/effects can consume later.

Current tick path:

`EventHandler.onLevelTick` -> `AtmosphereManager.tick` -> `ForecastOrchestrator.tick` -> `AtmosphericUpdateScheduler.tick` -> `WeatherCellManager.tick`.

WeatherCells are server authoritative.

The client does not simulate WeatherCells.

## RAIN_CELL Formation Rules

Automatic formation is handled by `WeatherCellFormationController`.

Candidate regions come from active atmosphere regions, falling back to player regions when active state is empty.

Formation requires:

1. Humidity at least `0.72`.
2. Cloud water at least `0.12`.
3. Nearby WeatherCell coverage below `0.70`.
4. Combined support score at least `0.58`.
5. Player proximity within `1100` blocks.

Score inputs:

1. Humidity score from live region humidity.
2. Cloud water score from live region cloud water.
3. Low pressure support from live region pressure.
4. Wind convergence from neighboring live atmosphere wind.
5. Existing rain support from live region rain intensity.
6. Cloud cover support from live region cloud cover.
7. High pressure penalty.
8. Nearby WeatherCell coverage penalty.

Limits:

1. Only one new cell per formation attempt.
2. Formation attempts run every `600` ticks.
3. Maximum active rain cells is `48`.
4. Only `WeatherCellType.RAIN_CELL` is created automatically.

## RAIN_CELL Lifecycle Rules

Lifecycle is handled by `WeatherCellLifecycleController`.

RAIN_CELL behavior:

1. Ages by `20` ticks per WeatherCell tick.
2. Expires when `ageTicks >= lifetimeTicks`.
3. Moves with wind through `WeatherCellMotionController`.
4. Recomputes support from live humidity, cloud water, pressure, wind influence, and existing rain.
5. Moves intensity gradually toward a support-based target.
6. Decays faster when support falls below threshold.
7. Removes itself when intensity is very low after initial lifetime warmup.

Conservative feedback:

1. Raises local `RegionAtmosphereState.rainIntensity` only toward a capped rain target.
2. Drains a small amount of local cloud water, capped at `0.006` per lifecycle tick.
3. Does not create thunder, supercells, tornadoes, hurricanes, blizzards, or severe storm feedback.

## Persistence Design

WeatherCells use their own saved data:

`project_atmosphere_weather_cells`

Persisted cell fields:

1. UUID id.
2. Cell type.
3. Source region.
4. Center position.
5. Radius.
6. Intensity.
7. Moisture.
8. Instability.
9. Pressure anomaly.
10. Wind influence.
11. Cloud water.
12. Rain intensity.
13. Age ticks.
14. Lifetime ticks.
15. Active flag.
16. Linked native cloud region ids.

Forecast data is not duplicated.

Cloud region persistence remains separate.

Live atmosphere persistence remains separate.

## Sync Decision

WeatherCell sync was deferred.

Reason:

No current HUD, audio, particle, or rendering feature consumes WeatherCells yet. Adding a network packet now would create an unused client state path.

The WeatherCell implementation remains server authoritative and ready for a future lightweight DTO/sync layer.

## Cloud Integration

WeatherCells do not force cloud morphology changes.

WeatherCells do not modify cloud renderer behavior.

WeatherCells do not modify Simple Clouds rendering or spawning behavior.

`WeatherCellState` includes optional linked native cloud region ids for future integration, but Task 3 leaves linking inactive to avoid invasive cloud lifecycle changes.

Existing native cloud regions still tick through `AtmosphereCloudServices.get().tick(level, count)` and `CloudRegionManager.tickCloudRegions`.

## Client Gating Audit Results

Audited server/common PA cloud paths:

1. `CloudRegionManager`
2. `CloudRegionState`
3. `CloudRegionStateStore`
4. `CloudGroupSpawner`
5. `CloudRegionEvolutionController`
6. `CloudRegionMotionController`
7. `CloudRegionMergeController`
8. `CloudRegionSavedData`
9. `NativeAtmosphereCloudService`
10. `CloudRegionSyncManager`
11. `SyncCloudRegionsPacket`
12. `WeatherCloudQueries`
13. All new `modules.weathercell` classes

Audit command used:

```powershell
rg -n "net\.minecraft\.client|com\.mojang\.blaze3d|Minecraft\.getInstance|RenderSystem|ShaderInstance|VertexBuffer|PoseStack|projectatmosphere\.client|clouds\.client" src/main/java/net/Gabou/projectatmosphere/clouds/simulation src/main/java/net/Gabou/projectatmosphere/clouds/state src/main/java/net/Gabou/projectatmosphere/clouds/service src/main/java/net/Gabou/projectatmosphere/clouds/network src/main/java/net/Gabou/projectatmosphere/modules/weathercell
```

Result:

No matches after fixes.

## Client-Only References Found And Fixed

1. `SyncCloudRegionsPacket`
   - Problem: directly imported `clouds.client.ClientCloudRegionDataCache`.
   - Fix: packet now uses `CloudRegionPacketDispatcher.handleClientRegions` behind `Dist.CLIENT`.
   - Client cache sink is registered by `ClientOnlyRegistrar`.

2. `WeatherCloudQueries`
   - Problem: common query class directly imported `clouds.client.ClientCloudRegionDataCache`.
   - Risk: this class is used from server/common paths including API, instruments, telemetry, precipitation block updates, and mixins.
   - Fix: server path remains `CloudRegionStateStore`; client path now uses `CloudRegionPacketDispatcher.getClientRegions`.

## Client-Only References Intentionally Left

Client-only references remain under:

1. `src/main/java/net/Gabou/projectatmosphere/clouds/client/**`
2. `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/**`
3. `src/main/java/net/Gabou/projectatmosphere/clouds/client/debug/**`
4. `src/main/java/net/Gabou/projectatmosphere/registry/ClientOnlyRegistrar.java`

Justification:

These are client-side rendering/debug/cache classes or the existing client-only registrar. `ClientOnlyRegistrar` is annotated `@OnlyIn(Dist.CLIENT)` and registers cloud client hooks only on the client.

## Validation

Commands run:

```powershell
./gradlew compileJava
./gradlew build
```

Results:

1. `compileJava` passed.
2. `build` passed.
3. Existing warnings remain from deprecated `ResourceLocation` constructors and mixin target declarations.
4. No new compile failure from WeatherCells or cloud gating changes.

Validated by code inspection:

1. No new client-only imports in WeatherCell classes.
2. No server/common PA cloud manager/state/service/network client-only imports after fixes.
3. WeatherCells save and load through `WeatherCellSavedData`.
4. `RAIN_CELL` forms from atmosphere support, not random unsupported spawning.
5. `RAIN_CELL` decays when support is gone.
6. `RAIN_CELL` does not create thunderstorms or supercells.
7. Existing native cloud regions still tick through the existing cloud service path.
8. Simple Clouds behavior was not modified.
9. Forecast generation and seasonal drift were not modified.

## Deferred

1. WeatherCell client sync.
2. HUD/audio/particle consumers for WeatherCells.
3. Native cloud linking.
4. Thunderstorm behavior.
5. Supercell behavior.
6. Cyclone behavior.
7. Blizzard behavior.
8. Tornado and hurricane migration.
9. Final cloud morphology.
10. Cloud rendering visual changes.

