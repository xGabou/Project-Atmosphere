# Project Atmosphere Phase 1 / Phase 2A Verification Audit

Scope: verification only. No implementation or refactor was performed.

This audit inspects the current code paths for:
- Phase 1: automatic PA-native cloud spawning.
- Phase 2A: live atmospheric state persistence.

The audit does not assume correctness from compilation.

## Phase 1: Automatic PA-Native Cloud Spawning

Expected path:

`Live atmosphere state -> cloud birth -> native cloud regions -> lifecycle/movement/merging/evolution`

### 1. Trace every call path from world tick to spawn attempt

Status: PASS

Evidence:
- `EventHandler.onLevelTick` runs on server level tick end and exits for client/non-server levels, missing initial generation, or empty player list: `src/main/java/net/Gabou/projectatmosphere/event/EventHandler.java:47`.
- Native cloud regions tick first when Simple Clouds is absent: `EventHandler.java:58-60`.
- The cloud service is resolved from `AtmosphereCloudServices.get()`: `EventHandler.java:65`.
- If events are enabled, the tick calls `cloudService.shouldTrySpawn(...)`, then `cloudService.trySpawnClouds(...)`: `EventHandler.java:66-77`.
- `AtmosphereCloudServices.createService()` returns `NativeAtmosphereCloudService` only when Simple Clouds is absent: `src/main/java/net/Gabou/projectatmosphere/clouds/service/AtmosphereCloudServices.java:39-46`.

Runtime path:

`TickEvent.LevelTickEvent END -> EventHandler.onLevelTick -> AtmosphereCloudServices.get -> NativeAtmosphereCloudService.shouldTrySpawn -> NativeAtmosphereCloudService.trySpawnClouds -> CloudGroupSpawner.spawnRequestedCloud -> CloudRegionManager.createCloudRegion -> CloudRegionStateStore.add`

### 2. Show the exact runtime path

Status: PASS

Evidence:
- `EventHandler` calls `shouldTrySpawn` and `trySpawnClouds`: `EventHandler.java:76-77`.
- `NativeAtmosphereCloudService.trySpawnClouds` collects candidates, chooses a position, and calls `CloudGroupSpawner.spawnRequestedCloud`: `src/main/java/net/Gabou/projectatmosphere/clouds/service/NativeAtmosphereCloudService.java:64-90`.
- `CloudGroupSpawner.spawnRequestedCloud` creates a native cloud through `CloudRegionManager.getInstance().createCloudRegion`: `src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudGroupSpawner.java:28-43`.
- `CloudRegionManager.createCloudRegion` stores the state through `CloudRegionStateStore.add`: `src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudRegionManager.java:62-82`.

### 3. Verify cloud birth actually occurs automatically

Status: PASS

Evidence:
- No command is required in the tick path. `EventHandler` calls the service during normal world tick: `EventHandler.java:76-77`.
- `NativeAtmosphereCloudService.shouldTrySpawn` returns true when the global cooldown expires or after regeneration: `NativeAtmosphereCloudService.java:51-60`.
- `NativeAtmosphereCloudService.trySpawnClouds` can create clouds from scored atmospheric candidates: `NativeAtmosphereCloudService.java:64-90`.

Important condition:
- Birth is probabilistic and conditional. If no candidate passes `score >= 0.42`, or random chance fails, no cloud is spawned that attempt: `NativeAtmosphereCloudService.java:158-168` and `NativeAtmosphereCloudService.java:82-84`.

### 4. Verify cloud water is consumed or explain why it is not

Status: FAIL

Evidence:
- Cloud water is read into the spawn score: `NativeAtmosphereCloudService.java:133`.
- `cloudWaterScore` contributes to birth scoring: `NativeAtmosphereCloudService.java:143`.
- No call in `NativeAtmosphereCloudService` adjusts or drains `RegionAtmosphereState.cloudWater`. There is no `state.adjustCloudWater(...)` or `state.setCloudWater(...)` call in the service.

Result:
- Cloud birth uses cloud water as a condition but does not consume it.
- This allows the same cloud-water reservoir to keep supporting future births until other systems naturally alter it.

### 5. Verify atmospheric state changes after cloud birth if intended

Status: UNVERIFIED

Evidence:
- Cloud birth creates a `CloudRegionState` through `CloudGroupSpawner`: `NativeAtmosphereCloudService.java:85`.
- There is no mutation of `RegionAtmosphereState` after a successful birth in `trySpawnClouds`: `NativeAtmosphereCloudService.java:64-90`.

Interpretation:
- If birth is intended to only create cloud regions, this is acceptable.
- If birth is intended to feed back into live atmosphere by reducing cloud water, increasing cloud cover, or marking local condensation, that feedback is missing.
- The code does not document which behavior is intended.

### 6. Verify cooldown behavior

Status: PASS

Evidence:
- Cooldown constant is `600` ticks: `NativeAtmosphereCloudService.java:31`.
- `nextSpawnAttemptTick` is reset on server start/stop: `NativeAtmosphereCloudService.java:39-47`.
- `shouldTrySpawn` compares `level.getGameTime()` against `nextSpawnAttemptTick`: `NativeAtmosphereCloudService.java:54-60`.
- `trySpawnClouds` advances `nextSpawnAttemptTick` before candidate evaluation: `NativeAtmosphereCloudService.java:68-70`.

### 7. Verify cooldown scope

Status: PASS

Evidence:
- Cooldown is stored as one field on `NativeAtmosphereCloudService`: `NativeAtmosphereCloudService.java:37`.
- There is no per-region or per-player cooldown map in the service.

Scope:
- Global per native cloud service instance.
- Not per region.
- Not per player.

### 8. Verify cloud coverage suppression logic

Status: PASS

Evidence:
- Existing coverage is computed from `CloudRegionStateStore.getActiveRegions(level)`: `NativeAtmosphereCloudService.java:228`.
- Regions in the same source/current atmospheric region add coverage penalty: `NativeAtmosphereCloudService.java:232-234`.
- Nearby active regions add proximity-weighted coverage: `NativeAtmosphereCloudService.java:239-243`.
- Candidate rejection occurs if `coveragePenalty >= 1.0F`: `NativeAtmosphereCloudService.java:158-160`.
- Coverage also subtracts from score: `NativeAtmosphereCloudService.java:154`.

### 9. Verify ocean influence is not double counted

Status: FAIL

Evidence:
- Atmospheric update already uses ocean flux to update humidity: `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericUpdateScheduler.java:317`.
- Native spawn scoring separately calls `OceanBasinManager.estimateHumidityFlux(...)`: `NativeAtmosphereCloudService.java:136`.
- That direct ocean flux becomes `oceanScore` and contributes to birth score: `NativeAtmosphereCloudService.java:146` and `NativeAtmosphereCloudService.java:152`.

Result:
- Ocean influence can affect cloud birth indirectly through live humidity/cloud water and directly through `oceanScore`.
- There is no code preventing double weighting.

### 10. Verify spawn rules cannot create infinite cloud growth

Status: PASS

Evidence:
- Spawn attempts are globally cooldown-gated: `NativeAtmosphereCloudService.java:31` and `NativeAtmosphereCloudService.java:68-70`.
- At most two clouds can spawn per attempt: `NativeAtmosphereCloudService.java:32` and `NativeAtmosphereCloudService.java:75-77`.
- At most twenty-four candidates are evaluated: `NativeAtmosphereCloudService.java:33` and `NativeAtmosphereCloudService.java:115-117`.
- Nearby coverage suppresses repeat spawning: `NativeAtmosphereCloudService.java:150-160`.
- Existing lifecycle removes inactive cloud regions: `src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudRegionManager.java:247`.

Caveat:
- There is no global maximum cloud count. The current safeguards prevent obvious rapid runaway growth, but long-running worlds still depend on lifecycle and coverage suppression.

### 11. Verify existing evolution system remains active

Status: PASS

Evidence:
- `EventHandler` ticks native cloud regions before spawn handling when Simple Clouds is absent: `EventHandler.java:58-60`.
- `CloudRegionManager.tickCloudRegions` invokes movement, merging, lifecycle, and evolution: `CloudRegionManager.java:224-247`.
- `CloudRegionEvolutionController` can change cloud types using evolution targets: `src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudRegionEvolutionController.java:52-82`.
- Low-level types have evolution targets in `CloudTypeRegistry`: `src/main/java/net/Gabou/projectatmosphere/clouds/type/CloudTypeRegistry.java:48-72`.

### 12. Verify command spawning and automatic spawning do not conflict

Status: PASS

Evidence:
- Command spawning uses `CommandCloudService.spawnNativeCloud`, which calls `CloudGroupSpawner.spawnRequestedCloud`: `src/main/java/net/Gabou/projectatmosphere/command/tree/service/CommandCloudService.java:301-307`.
- Automatic spawning also calls `CloudGroupSpawner.spawnRequestedCloud`: `NativeAtmosphereCloudService.java:85`.
- Both paths create native `CloudRegionState` through the same backend.
- Automatic coverage suppression reads all active native cloud regions, including command-created ones: `NativeAtmosphereCloudService.java:228-243`.

### 13. Verify native cloud regions continue to persist and sync correctly after automatic spawning

Status: PASS

Evidence:
- Automatic spawning creates regions through `CloudGroupSpawner`: `NativeAtmosphereCloudService.java:85`.
- `CloudGroupSpawner` creates a `CloudRegionState` through `CloudRegionManager`: `CloudGroupSpawner.java:40-53`.
- `CloudRegionStateStore.add` marks cloud saved data dirty: `src/main/java/net/Gabou/projectatmosphere/clouds/state/CloudRegionStateStore.java:23-28`.
- `CloudRegionSavedData` persists cloud regions through Minecraft `SavedData`: `src/main/java/net/Gabou/projectatmosphere/clouds/state/CloudRegionSavedData.java:33-64`.
- `EventHandler` syncs native cloud regions to players when Simple Clouds is absent: `EventHandler.java:84-86`.
- `CloudRegionSyncManager` sends `SyncCloudRegionsPacket` from active render data: `src/main/java/net/Gabou/projectatmosphere/clouds/network/CloudRegionSyncManager.java:46-59`.

## Phase 2A: Live Atmospheric State Persistence

Expected architecture:

`Forecast = immutable climate baseline`

`Atmosphere state = mutable weather layer`

### 1. Trace complete save path

Status: PASS

Evidence:
- `ForecastOrchestrator.onServerStop` calls `AtmosphericStateSavedData.snapshot(level)` before forecast save/clear: `src/main/java/net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java:182`.
- `AtmosphericStateSavedData.snapshot` gets the world saved data, captures current state, and marks it dirty: `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericStateSavedData.java:42-49`.
- `capture` saves region state, active regions, scheduler state, cyclone state, and ocean basin state: `AtmosphericStateSavedData.java:59-87`.
- `save` writes the payload under `LiveAtmosphere`: `AtmosphericStateSavedData.java:127-130`.

### 2. Trace complete load path

Status: PASS

Evidence:
- `ForecastOrchestrator.onServerStart` calls `AtmosphericStateSavedData.restore(level)` after saved forecast initialization: `ForecastOrchestrator.java:116`.
- The no-forecast/new-forecast path also calls restore after initialization: `ForecastOrchestrator.java:173`.
- `AtmosphericStateSavedData.restore` loads saved data and applies it: `AtmosphericStateSavedData.java:34-39`.
- `apply` overlays region state, active regions, scheduler state, cyclone state, and ocean basin state: `AtmosphericStateSavedData.java:91-123`.

### 3. Verify restore order

Status: PASS

Evidence:
- Saved forecast data loads first through `ForecastDataStorage.loadAll(level)`: `ForecastOrchestrator.java:81`.
- Wind forecasts are rebuilt before dynamic systems initialize: `ForecastOrchestrator.java:99-109`.
- `initializeDynamicSystems(level)` runs before `AtmosphericStateSavedData.restore(level)`: `ForecastOrchestrator.java:115-116`.

### 4. Verify forecast loads before atmosphere overlay

Status: PASS

Evidence:
- `ForecastDataStorage.loadAll(level)` runs at server start before any live atmosphere restore: `ForecastOrchestrator.java:81`.
- Forecast-backed dynamic systems initialize before restore: `ForecastOrchestrator.java:115-116`.

### 5. Verify atmosphere overlay does not replace forecast baseline

Status: PASS

Evidence:
- `RegionAtmosphereState.saveMutableState` saves mutable fields only: `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/RegionAtmosphereState.java:301-318`.
- `RegionAtmosphereState.applyMutableState` sets mutable fields only: `RegionAtmosphereState.java:321-361`.
- Final baseline fields such as `baseTemperature`, `baseHumidity`, `basePressure`, and forecast profiles are not assigned in `applyMutableState`.

### 6. Verify forecast data is not duplicated in atmosphere saves

Status: PASS

Evidence:
- Atmospheric save stores region keys plus mutable state compounds: `AtmosphericStateSavedData.java:66-75`.
- `RegionAtmosphereState.saveMutableState` saves current state and daily observed profiles, not forecast week arrays or `ForecastRegion` data: `RegionAtmosphereState.java:301-318`.
- Forecast persistence remains in `ForecastDataStorage`; cloud-region persistence remains in `CloudRegionSavedData`.

### 7. Verify all mutable fields listed in the report are actually saved

Status: FAIL

Evidence for saved fields:
- Per-region temperature/humidity/pressure/wind/cloud/rain/sunlight/cyclone floors/daily profiles are saved: `RegionAtmosphereState.java:301-318`.
- Scheduler timestamps and passive queue are saved: `AtmosphericUpdateScheduler.java:71-82`.
- Cyclone agents and timing are saved: `CycloneManager.java:50-61`.
- Ocean basin reservoirs and memory are saved: `src/main/java/net/Gabou/projectatmosphere/modules/ocean/OceanBasin.java:189-216`.

Missing mutable atmospheric state:
- `WindEngine` maintains its own mutable `Map<RegionInstanceKey, WindRuntimeState> STATES`: `src/main/java/net/Gabou/projectatmosphere/modules/wind/WindEngine.java:22`.
- `WindRuntimeState` contains smoothed high/low wind and gust state: `src/main/java/net/Gabou/projectatmosphere/modules/wind/WindRuntimeState.java:3-57`.
- This runtime wind state is not saved by `AtmosphericStateSavedData`.

Result:
- The report says wind is persisted at `RegionAtmosphereState`, but the wind engine's mutable runtime driver is not persisted.

### 8. Verify all mutable fields are actually restored

Status: FAIL

Evidence:
- `RegionAtmosphereState.wind` is restored by `applyMutableState`: `RegionAtmosphereState.java:354-356`.
- Normal runtime later calls `WindEngine.tick`: `ForecastOrchestrator.java:619`.
- `WindEngine.tick` samples from its unsaved `WindRuntimeState` and overwrites `RegionAtmosphereState.wind`: `WindEngine.java:41-50`.

Result:
- Restored wind can be overwritten by a fresh default wind runtime state on the next active wind tick.
- Other per-region fields are restored, but wind continuity is incomplete.

### 9. Verify scheduler queues restore safely

Status: PASS

Evidence:
- Load clears in-flight flags and the existing passive queue: `AtmosphericUpdateScheduler.java:85-88`.
- It reloads queued region keys from NBT: `AtmosphericUpdateScheduler.java:94-100`.
- `pollBatch` only yields keys not currently active: `AtmosphericUpdateScheduler.java:255-262`.

### 10. Verify stale queue entries cannot corrupt runtime state

Status: PASS

Evidence:
- `snapshotStates` looks up each queued key in `AtmosphericStateRegistry.getStatesAsMap()` and skips missing states: `AtmosphericUpdateScheduler.java:267-274`.
- `applyDeltas` also checks `AtmosphericStateRegistry.getState(delta.key())` and skips null states: `AtmosphericUpdateScheduler.java:396-399`.

Result:
- Stale queue entries can waste a slot but do not corrupt live state.

### 11. Verify cyclone restoration cannot duplicate cyclones

Status: PASS

Evidence:
- `CycloneManager.initialize` can create initial random cyclones: `CycloneManager.java:35-40`.
- `CycloneManager.loadPersistentState` clears `ACTIVE_CYCLONES` and `ACTIVE_SNAPSHOTS` before loading saved cyclones: `CycloneManager.java:64-66`.
- It then loads each saved cyclone once and puts one snapshot per cyclone id: `CycloneManager.java:74-79`.

### 12. Verify ocean basin restoration cannot be overwritten by later initialization

Status: PASS

Evidence:
- `OceanBasinManager.initialize` starts async detection with a version id: `src/main/java/net/Gabou/projectatmosphere/modules/ocean/OceanBasinManager.java:47-56`.
- The async completion exits if its version is stale: `OceanBasinManager.java:56-58`.
- `OceanBasinManager.loadPersistentState` increments the detection version, cancels the task, clears basins, and loads saved basins: `OceanBasinManager.java:88-108`.

Result:
- If restore happens while the initialization detection task is still running, the version guard prevents late overwrite.

### 13. Verify missing save data works for old worlds

Status: PASS

Evidence:
- New saved data starts with an empty payload: `AtmosphericStateSavedData.java:23-31`.
- `apply` returns immediately for empty payloads: `AtmosphericStateSavedData.java:91-94`.

Result:
- Old worlds without `project_atmosphere_live_atmosphere` continue using initialized forecast-backed live state.

### 14. Verify no code path triggers forecast regeneration during restore

Status: PASS

Evidence:
- `AtmosphericStateSavedData.restore/apply` only reads saved data and mutates existing atmosphere managers: `AtmosphericStateSavedData.java:34-39` and `AtmosphericStateSavedData.java:91-123`.
- No `ForecastGenerator.generate...`, `ForecastDataStorage.clearAll`, or `ForecastOrchestrator.regenerate...` call exists inside the restore path.

Clarification:
- `ForecastOrchestrator.onServerStart` may generate forecast data when forecast data is missing. That is outside the atmosphere restore method and is pre-existing behavior.

### 15. Verify cloud persistence remains independent from atmosphere persistence

Status: PASS

Evidence:
- Live atmosphere uses `project_atmosphere_live_atmosphere`: `AtmosphericStateSavedData.java:20`.
- Native cloud regions use `projectatmosphere_cloud_regions`: `src/main/java/net/Gabou/projectatmosphere/clouds/state/CloudRegionSavedData.java:12`.
- `AtmosphericStateSavedData` does not read or write `CloudRegionState`.
- `CloudRegionSavedData` does not read or write `RegionAtmosphereState`.

## Final Findings

### Critical Issues

1. Phase 2A does not persist `WindEngine` runtime state.

Evidence:
- `WindEngine.STATES` stores `WindRuntimeState`: `WindEngine.java:22`.
- `WindRuntimeState` contains smoothed wind and gust state: `WindRuntimeState.java:3-57`.
- `WindEngine.tick` overwrites restored `RegionAtmosphereState.wind`: `WindEngine.java:41-50`.

Impact:
- The saved `RegionAtmosphereState.wind` is not enough to preserve wind continuity after restart.
- The validation item "Atmosphere state remains identical" is false for wind once the normal wind tick runs.

### Medium Issues

1. Phase 1 cloud birth does not consume cloud water.

Evidence:
- Cloud water participates in scoring: `NativeAtmosphereCloudService.java:133` and `NativeAtmosphereCloudService.java:143`.
- The service never calls `adjustCloudWater` or `setCloudWater`.

Impact:
- Cloud water can repeatedly contribute to new cloud births without being drained by birth itself.

2. Phase 1 double counts ocean influence.

Evidence:
- Ocean flux contributes to atmospheric humidity updates: `AtmosphericUpdateScheduler.java:317`.
- Ocean flux is also directly scored for cloud birth: `NativeAtmosphereCloudService.java:136` and `NativeAtmosphereCloudService.java:152`.

Impact:
- Coastal/ocean-influenced regions may be overweighted for cloud birth.

3. Phase 1 does not update live atmosphere after successful birth.

Evidence:
- A successful birth only increments the local `spawned` counter after `CloudGroupSpawner.spawnRequestedCloud`: `NativeAtmosphereCloudService.java:85-88`.

Impact:
- There is no direct state feedback such as lower cloud water or higher cloud cover.

### Low Issues

1. Phase 1 has no global cloud count cap.

Evidence:
- The service caps per-attempt spawns and candidates, but no total active cloud count is checked: `NativeAtmosphereCloudService.java:31-33` and `NativeAtmosphereCloudService.java:75-77`.

Impact:
- Lifecycle and coverage suppression probably prevent immediate runaway growth, but long-running scenarios still rely on indirect controls.

2. Phase 2A saves root `SavedGameTime` and `SavedDayTime` but does not use them on restore.

Evidence:
- Saved at `AtmosphericStateSavedData.java:62-63`.
- No read of those keys exists in `apply`: `AtmosphericStateSavedData.java:91-123`.

Impact:
- This is harmless for current logic, but the timestamps currently serve as metadata only.

### Architecture Regressions Introduced

1. Phase 1 introduced cloud birth as a one-way operation from atmosphere to cloud regions.

Evidence:
- It reads atmospheric state but does not write any atmospheric state after cloud creation.

Impact:
- The intended "mutable weather layer" is not fully closed for cloud birth.

2. Phase 2A introduced a live-atmosphere persistence layer but omitted one active mutable atmosphere subsystem: `WindEngine.STATES`.

Impact:
- The mutable layer is only partially persistent.

### Runtime Risks

1. Restored wind can visibly or mechanically jump after restart once `WindEngine.tick` runs.

2. Automatic spawning may overproduce in moist ocean-influenced regions because ocean influence is both baked into humidity/cloud water and directly scored.

3. Cloud birth can repeatedly use the same cloud water reservoir because there is no birth-time drain.

4. In-game restart validation remains unverified. Code paths compile, but the stop/restart checklist has not been run in Minecraft.

### Recommended Fixes Before Phase 2B

1. Persist and restore `WindEngine` runtime state or seed it from restored `RegionAtmosphereState.wind` before the first `WindEngine.tick`.

2. Decide whether cloud birth should consume cloud water. If yes, drain a bounded amount only after successful cloud creation.

3. Decide whether ocean influence should be direct or indirect in spawn scoring. Avoid applying both unless the extra weighting is intentional and calibrated.

4. Add runtime validation for stop/restart invariants:
- Forecast data unchanged.
- `RegionAtmosphereState` values unchanged before first tick.
- Wind remains continuous after first wind tick.
- Cloud regions remain unchanged.
- No forecast regeneration occurs.
