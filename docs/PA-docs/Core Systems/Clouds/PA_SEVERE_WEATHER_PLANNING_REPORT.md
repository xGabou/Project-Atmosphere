# Project Atmosphere Severe Weather Planning Pass

Scope: planning and audit only. No tornado, hurricane, blizzard, weather simulation, cloud simulation, rendering, shader, Distant Horizons, config, or behavior changes were made.

Inspection basis: repository code under `src/main/java` and `src/main/resources`, focused on tornadoes, hurricanes, cyclones, blizzards/snowstorms, wind/world effects, precipitation, commands, persistence, networking, and Simple Clouds dependencies.

## 1. Existing Severe Weather Code Inventory

### Runtime Entry Points

| Path | Inspected code | Current responsibility | Classification |
| --- | --- | --- | --- |
| Server start | `ForecastOrchestrator.onServerStart(ServerLevel)` | Loads forecast data, bootstraps region orchestrator, initializes dynamic systems, restores atmosphere saved data, loads `TornadoStorageManager` only when Simple Clouds is loaded. | Reusable with adapter |
| Server stop | `ForecastOrchestrator.onServerStop(ServerLevel)` | Saves atmospheric state, forecast data, and Simple Clouds-gated tornado/hurricane runtime storm data. | Reusable with adapter |
| Main atmosphere tick | `AtmosphereManager.tick(ServerLevel)` | Calls `ForecastOrchestrator.tick`, `SnowstormManager.tick`, and Simple Clouds-gated `TornadoManager.tick` / `HurricaneManager.tick`. | Reusable with adapter |
| Weather/cell tick | `ForecastOrchestrator.tick(ServerLevel)` | Ticks glass damage, seasonal drift, atmospheric scheduler, WeatherCells, ocean basins, cyclones, wind, Simple Clouds cloud manager, WindEngine, and scheduled tornado checks. | Reusable with adapter |
| Player login sync | `AtmosphereManager.syncPlayerRuntimeState(ServerPlayer)` | Syncs atmosphere status, native cloud regions, and Simple Clouds-gated tornado/hurricane snapshots. | Reusable with adapter |
| Precipitation block update | `ServerLevelWeatherCycleMixin.tickChunk` -> `LocalizedPrecipitationBlockUpdater.tickChunk` | Applies localized precipitation and snow accumulation using PA cloud weather samples. | Reusable as-is |

### Tornado Classes

| Class | Evidence | Current behavior | Classification |
| --- | --- | --- | --- |
| `modules/tornado/TornadoProbabilityManager` | `onScheduledCheck`, `computeRisk`, `isStormy` | Automatic tornado check runs from `ForecastOrchestrator.tick` only when Simple Clouds is loaded. Formation requires storm-capable forecast phase, severity >= 6, and `AtmosphereCloudServices.get().hasSevereCloudNearby(...)`. | Simulation logic reusable, cloud attachment not reusable |
| `modules/tornado/TornadoSpawner` | `spawn`, `spawnInternal`, `pickSpawnPosNear` | Converts risk/intensity into a world position and calls `TornadoManager.spawnServer(...)`. It samples surface wind via `WindVectorApi`. | Reusable with adapter |
| `modules/tornado/TornadoManager` | `spawnServer`, `tick`, `findIntersectingCloud`, `computeGeometry`, `loadPersistentTornadoes` | Owns tornado lists, sync, cloud attachment, Simple Clouds `CloudRegion` lookup, descriptor attachment, persistence load/save bridge, client snapshot application. Normal spawn requires intersecting Simple Clouds cloud unless using `spawnServerWithoutCloud`. | Simple Clouds only for PA-native severe weather ownership |
| `modules/tornado/TornadoInstance` | `tickServer`, `applyAmbientWind`, `demolishBlocks`, `applyTornadoForces`, `toPersistentTag`, `fromPersistentTag` | Contains useful lifecycle, movement, entity dragging, block destruction, debris, glass damage, snapshots, and persistence. Also stores `CloudRegion`, pushes descriptors into `ITornadoRegion`, derives storm level partly from Simple Clouds cloud type, and samples wind from `ForecastOrchestrator`. | Simulation logic reusable, cloud attachment not reusable |
| `modules/tornado/TornadoSpawnScheduler` | `isSlotAvailable`, `recordSpawn` | Static 3-slot global scheduler with initial delays and cooldowns. | Reusable with adapter |
| `data/TornadoStorageManager` | `load`, `save`, `RuntimeStormData` | Saves cooldowns, tornado tags, and hurricane tags in `project_atmosphere_runtime_storms`, but load/save returns early when Simple Clouds is not loaded. | Simple Clouds only for PA-native severe persistence |
| `modules/tornado/GlassDamageManager` | `damageGlass`, `tick` | Tracks glass damage, destruction, and timed repair based on config. No Simple Clouds dependency. | Reusable as-is |
| `modules/wind/TornadoWindModel` | `compute` | Computes pull, rotation, and lift from active `TornadoManager` tornadoes, but returns null when Simple Clouds is not loaded. | Reusable with adapter |
| `api/common/cloud/region/ITornadoRegion` | interface methods | Capability-like tornado descriptor container for a cloud region. No direct Simple Clouds import, but current implementation is through `CloudRegionMixin` on Simple Clouds `CloudRegion`. | Reusable with adapter |
| `api/common/cloud/region/TornadoDescriptor` | NBT/network DTO | Stores tornado attachment offsets, velocity, radius, height, bottom. Independent DTO, but semantics are cloud-region-relative. | Reusable with adapter |
| `network/SyncTornadoesPacket` | `handle` | Updates client tornado snapshots by calling `TornadoManager.applyClientSnapshots`. Current handler directly references manager client method. | Reusable with adapter |
| `client/render/SimpleCloudsTornadoRenderer`, tornado shaders, tornado client effects/audio | file inventory and names | Client visual/audio implementation around current tornado snapshots and Simple Clouds render integration. Rendering is out of current planning scope. | Out of scope |

### Hurricane and Cyclone Classes

| Class | Evidence | Current behavior | Classification |
| --- | --- | --- | --- |
| `modules/atmosphere/CycloneManager` | `initialize`, `update`, `savePersistentState`, `loadPersistentState` | PA-native mutable cyclone state. Spawns initial/random cyclones near player-active atmospheric states, ticks with wind drift, persists cyclone fields, emits `CycloneSnapshot`. | Reusable with adapter |
| `modules/atmosphere/CycloneImpactApplier` | `apply` | Applies cyclone deltas to live `RegionAtmosphereState`: temperature, humidity, pressure, cyclone visual floors, rain, cloud cover, cloud water. | Reusable as-is |
| `modules/hurricane/HurricaneManager` | `tick`, `projectatmosphere$syncCycloneHurricanes`, `applyAtmosphereAmplification`, `reconcileReservedCloudSpace`, reservation methods | Forms hurricanes from `CycloneManager` snapshots plus environment support. Applies atmosphere amplification and lightning. Creates/removes Simple Clouds reservation regions and removes colliding Simple Clouds clouds. | Simulation logic reusable, cloud attachment not reusable |
| `modules/hurricane/HurricaneInstance` | `fromCyclone`, `updateFromCyclone`, `tick`, `toPersistentTag`, `fromPersistentTag` | Stores hurricane state, lifecycle, category, wind, cyclone link, persistence, render snapshot. Ticks wind field and destruction. Uses `CloudManager.getCloudHeight()` to set visual anchor Y. | Simulation logic reusable, cloud attachment not reusable |
| `modules/hurricane/HurricaneEnvironmentAnalyzer` | `analyzeCyclone`, `sampleConvectiveCoverage`, `formationEligible`, `sustainEligible` | Uses PA atmosphere states, ocean biome checks, cyclone snapshots, and Simple Clouds convective cloud coverage to determine formation/sustain/category. | Simulation logic reusable, cloud attachment not reusable |
| `modules/hurricane/HurricaneSemantics` | `sampleBest`, `createReservationRegion`, `getReservationRegionAt`, `resolveLevel` | Computes hurricane eye/eyewall/bands semantic samples. Also imports client cache in common class and creates Simple Clouds `CloudRegion` reservation objects. | Simple Clouds only for current cloud semantics |
| `modules/hurricane/HurricaneWindField` | `apply` | Applies hurricane tangential/inward/lift forces to entities using storm position/intensity and storm shield protection. No Simple Clouds dependency. | Reusable as-is |
| `modules/hurricane/HurricaneDestructionManager` | `apply` | Samples surface blocks in hurricane bands and breaks fragile/tree blocks based on config and category/intensity. No Simple Clouds dependency. | Reusable as-is |
| `modules/hurricane/HurricaneBlockBreakRules` | tag checks and break chance methods | Encapsulates hurricane fragile/tree/never-break tags and shield checks. | Reusable as-is |
| `modules/hurricane/HurricaneCategory` | enum | Saffir-Simpson category constants and strength mapping. | Reusable as-is |
| `mixin/CloudGeneratorHurricaneReservationMixin` | injections into `CloudGenerator` | Blocks Simple Clouds region creation/addition inside hurricane reservation space and returns reservation region from Simple Clouds generator queries. | Simple Clouds only |
| `network/SyncHurricaneStatePacket` | `handle` uses `DistExecutor` and `ClientHurricaneStateCache` | Syncs hurricane render snapshots to client cache. Client update is Dist-gated, but the packet class imports a client cache directly. | Reusable with adapter |
| `client/render/SimpleCloudsHurricaneRenderer`, hurricane shaders, hurricane client cache | file inventory and names | Current hurricane client visualization and Simple Clouds render integration. Rendering is out of current planning scope. | Out of scope |

### Blizzard, Snowstorm, Snow, and Local Precipitation Classes

| Class | Evidence | Current behavior | Classification |
| --- | --- | --- | --- |
| `modules/snowstorm/SnowstormManager` | `startSnowstorm`, `getSnowStormIntensity`, `getSnowTier`, `tick` | Stores `SnowStorm` objects backed by Simple Clouds `CloudRegion`; checks intersection using Simple Clouds `SpawnRegion`; applies blindness/slowness/message to players. | Simple Clouds only |
| `modules/snowstorm/SnowStorm` | constructor and fields | Immutable snowstorm intensity/tier wrapper around Simple Clouds `CloudRegion`. | Simple Clouds only |
| `mixin/ServerLevelSnowStormMixin` | implements `ISnowStormLevel` | Exposes `SnowstormManager` storm state to external snowstorm API/interface. | Reusable with adapter |
| `manager/LocalizedPrecipitationBlockUpdater` | `tickChunk` | PA-native localized precipitation/snow accumulation using `WeatherCloudQueries.sampleAt`. It respects biome precipitation and snow accumulation gamerule. | Reusable as-is |
| `modules/weather/SnowTier` | `resolve` | Maps temperature, humidity, wind speed, and precipitation strength to NONE/SNOWY_DAY/SNOWSTORM/BLIZZARD. | Reusable as-is |
| `clouds/client/render/PrecipitationVisualState` | inventory and `SnowTier.BLIZZARD` reference | Client precipitation visuals know about blizzard tier. Rendering is out of current planning scope. | Out of scope |

### Shared Weather and World-Effect Classes

| Class | Evidence | Current behavior | Classification |
| --- | --- | --- | --- |
| `modules/weathercell/WeatherCellManager` | `tick`, saved data access | Server-authoritative WeatherCell tick every 20 ticks and formation every 600 ticks. Persists through `WeatherCellSavedData`. | Reusable as-is |
| `modules/weathercell/WeatherCellState` | fields/save/load | Stores type, source region, center, radius, intensity, moisture, instability, pressure anomaly, wind influence, cloud water, rain intensity, evolution scores, age, lifetime, active flag, linked cloud ids. | Reusable as-is |
| `modules/weathercell/WeatherCellType` | enum | Defines RAIN_CELL, THUNDERSTORM, SUPERCELL, CYCLONE, BLIZZARD. Only rain/thunder/supercell currently have active lifecycle behavior. | Reusable as-is |
| `modules/weathercell/WeatherCellLifecycleController` | `tick`, type transitions | Atmosphere-driven RAIN_CELL -> THUNDERSTORM -> SUPERCELL and weakening. CYCLONE/BLIZZARD currently get inert profile. | Reusable with adapter |
| `modules/weathercell/WeatherCellFormationController` | `evaluate`, `createRainCell` | Forms only RAIN_CELL from live atmosphere with global/regional/player-local budgets. | Reusable as-is |
| `modules/weathercell/WeatherCellMotionController` | `tick` | Moves cells using current position region wind sampled via `ForecastOrchestrator.getWind`. | Reusable as-is |
| `modules/weathercell/WeatherCellSupport` | current-region/current-atmosphere helpers | Anchors active cell simulation to current cell position, not only source region. | Reusable as-is |
| `modules/atmosphere/AtmosphericSupportEvaluator` | shared support metrics | Central source for humidity, cloud water, pressure, temperature, wind convergence, rain, cloud cover, thunderstorm, supercell support. | Reusable as-is |
| `modules/weather/StormSeverityScale` | `resolve`, `sampleCloudLevel` | Blends forecast phase, live atmosphere, and Simple Clouds cloud severity. `sampleCloudLevel` depends on Simple Clouds `CloudManager`. | Reusable with adapter |
| `modules/weather/StormMotionModel` | tornado/hurricane movement methods | Contains tornado route planning, water/surface avoidance, storm shield avoidance, hurricane drift. No direct Simple Clouds imports. | Reusable as-is |
| `modules/weather/StormShieldManager` | shield index and protection checks | Server-side storm shield spatial index and protection/avoidance API. | Reusable as-is |
| `modules/wind/WindForces` | `applyToPlayer`, `applyToEntity` | General wind gust/push logic using `ForecastOrchestrator.getWind`. Tornado forces are not wired here; comment says combined but implementation only applies wind/gusts. | Reusable with adapter |

## 2. Tornado Reuse Plan

### Current Tornado Spawn Path

Normal scheduled path:

1. `AtmosphereManager.tick(ServerLevel)`
2. `ForecastOrchestrator.tick(ServerLevel)`
3. Simple Clouds gate and tornado interval check
4. `AsyncAtmosphereService.runStorm(() -> TornadoProbabilityManager.onScheduledCheck(level))`
5. `TornadoProbabilityManager.onScheduledCheck`
6. `TornadoProbabilityManager.isStormy`
7. `AtmosphereCloudServices.get().hasSevereCloudNearby(...)`
8. `TornadoSpawner.spawn`
9. `TornadoManager.spawnServer`
10. `TornadoManager.findIntersectingCloud`
11. `TornadoManager.spawnServerInternal`
12. `TornadoInstance`
13. `TornadoManager.attachDescriptor` to `ITornadoRegion` on Simple Clouds `CloudRegion`

Command/debug paths:

- `CommandTornadoService.spawnTornado` can use Simple Clouds cumulonimbus seeding and delayed attachment.
- `CommandTornadoService.spawnStandaloneTornado` and `TornadoCommand` can call `TornadoManager.spawnServerWithoutCloud`.
- `TornadoDebug` can call `TornadoSpawner.spawn`.

### Tornado Reuse Map

Reusable as-is:

- `GlassDamageManager` for glass damage/repair.
- `StormShieldManager` for storm protection and path avoidance.
- `StormMotionModel` movement primitives, after passing a PA-native anchor.
- `TornadoSnapshot` as a network DTO, if future packets preserve equivalent fields.
- `TornadoLevel` and damage/intensity scale constants if future PA-native wind scale remains compatible. This class was identified in inventory but not deeply inspected; classification is unverified beyond file existence.

Reusable with adapter:

- `TornadoSpawner` surface-position selection and wind conversion, but formation input must come from SUPERCELL/Mesocyclone state instead of forecast phase plus Simple Clouds severe cloud lookup.
- `TornadoSpawnScheduler`, but global 3-slot scheduling should be replaced or wrapped by WeatherCell/SevereWeatherEvent regional budgets.
- `TornadoWindModel`, if it reads a PA-native severe event manager instead of `TornadoManager` and removes the Simple Clouds loaded gate.
- `ITornadoRegion` / `TornadoDescriptor`, if adapted into a generic `SevereWeatherCloudAnchor` DTO for PA-native cloud regions. Current offset-based design assumes a parent cloud region.
- `SyncTornadoesPacket`, if client update is moved behind a dedicated client handler and future server code does not import client-only classes.

Simulation logic reusable, cloud attachment not reusable:

- `TornadoInstance` entity dragging, force field, block demolition, water penalty, lifecycle, movement, terrain sampling, and persistence logic. The same class currently stores `CloudRegion`, calls `ITornadoRegion`, derives cloud severity from `cloudRegion.getCloudTypeId`, and uses descriptor offsets. A PA-native tornado should extract or wrap the world-effect logic around a PA-native event anchor.
- `TornadoManager` runtime list/snapshot/persistence ideas are reusable, but current ownership and normal spawn path are Simple Clouds-bound.
- `TornadoProbabilityManager.computeRisk` thermal/pressure/shear ideas are reusable, but `isStormy` uses forecast phase, Simple Clouds severe cloud proximity, and Simple Clouds gating.

Simple Clouds only:

- `TornadoManager.findIntersectingCloud`, `computeGeometry` cloud-base sampling through `CloudManager`, `attachDescriptor`, `ensureDescriptor`, `removeAttachedDescriptor`.
- `CloudRegionMixin` tornado descriptor storage on Simple Clouds `CloudRegion`.
- Simple Clouds render/mixin integration for tornado visuals.

Needs rewrite:

- Automatic tornado formation ownership. Future formation should be `SUPERCELL -> Mesocyclone -> Tornado`, not forecast storm phase + Simple Clouds severe cloud.
- PA-native tornado persistence. Current `TornadoStorageManager` is skipped when Simple Clouds is absent.
- Cloud lookup/attachment. PA-native tornado must attach to a WeatherCell/Mesocyclone and optionally PA-native cloud visual/morphology anchor, not Simple Clouds `CloudRegion`.

Out of scope for Phase 8 planning:

- Final tornado rendering.
- Shader integration.
- Audio polish.
- Cloud morphology generation.

### Required New Tornado Adapter Classes

Recommended adapters/classes:

- `SevereWeatherEventState`: persistent common state for severe events.
- `SevereWeatherEventType`: TORNADO, HURRICANE, BLIZZARD.
- `SevereWeatherEventManager`: server authoritative severe event collection, tick, save/load, sync.
- `MesocycloneState`: persistent/sub-state linked to a SUPERCELL WeatherCell.
- `TornadoFormationController`: evaluates SUPERCELL severity, rotation, pressure anomaly, humidity, wind shear, and cloud morphology metadata.
- `TornadoLifecycleController`: formation/active/dissipation transition ownership.
- `TornadoWorldEffects`: adapter around reusable `TornadoInstance` force/destruction code or extracted helper methods.
- `TornadoCloudAnchor`: PA-native anchor DTO using WeatherCell id, optional native cloud region ids, visual cloud metadata, and current position.
- `TornadoSyncPacket` or generic `SevereWeatherEventSyncPacket`.

### Future Tornado Implementation Order

1. Add common `SevereWeatherEvent` persistence and manager with no visual features.
2. Add `MesocycloneState` linked to existing SUPERCELL WeatherCells.
3. Add atmospheric rotation/shear support using existing `AtmosphericSupportEvaluator`, `WindEngine`, and WeatherCell fields.
4. Port tornado formation from Simple Clouds cloud lookup to `SUPERCELL/Mesocyclone` support.
5. Adapt `TornadoInstance` world-effect logic into PA-native event ticking.
6. Add server-safe sync snapshots for clients.
7. Only after simulation works, add future client render/sound hooks.

### Tornado Risks

- `TornadoManager` imports `net.minecraft.client.Minecraft` in a common class and exposes client-only methods in the same class. This is a dedicated-server safety risk if the class is loaded on a server without client classes.
- `TornadoStorageManager` being Simple Clouds-gated means current severe persistence is not a PA-native foundation.
- `StormSeverityScale.sampleCloudLevel` directly uses Simple Clouds `CloudManager`; future tornado severity must avoid this for PA-native storms.
- `TornadoInstance` mixes simulation, rendering interpolation, Simple Clouds descriptors, entity effects, block effects, and persistence in one class. Reusing it directly will carry Simple Clouds assumptions into PA-native tornadoes.

## 3. Hurricane Reuse Plan

### Current Hurricane Spawn/Formation Path

Automatic path:

1. `AtmosphereManager.tick(ServerLevel)`
2. Simple Clouds gate around `HurricaneManager.tick(level)`
3. `HurricaneManager.tick`
4. `HurricaneManager.projectatmosphere$syncCycloneHurricanes`
5. `CycloneManager.getActiveCycloneSnapshots`
6. `HurricaneEnvironmentAnalyzer.analyzeCyclone`
7. `FormationTracker.update`
8. `HurricaneInstance.fromCyclone` when eligible long enough
9. `HurricaneManager.projectatmosphere$syncManagedRegion`
10. `HurricaneSemantics.createReservationRegion`
11. `CloudGenerator.addCloud` Simple Clouds reservation

Command path:

- `CommandHurricaneService.spawnHurricane` checks config, player, Overworld, ocean biome, non-winter season, samples `ForecastOrchestrator.getWind`, then calls `HurricaneManager.spawnServer`.
- `HurricaneCommand` also calls `HurricaneManager.spawnServer`.

### Hurricane Reuse Map

Reusable as-is:

- `CycloneManager` state and snapshots, with the caveat that its current cyclone spawning is randomized and should be replaced or constrained for final hurricane formation.
- `CycloneImpactApplier` live-atmosphere forcing.
- `HurricaneWindField` entity wind effects.
- `HurricaneDestructionManager` world block/tree/fragile damage.
- `HurricaneBlockBreakRules` tags and shield checks.
- `HurricaneCategory`.
- `HurricaneRenderSnapshot` and render descriptors as DTO concepts. Client rendering remains out of scope.

Reusable with adapter:

- `HurricaneInstance` lifecycle, category, persistence, and state fields. Replace cloud-height anchor and Simple Clouds visual assumptions with PA-native visual metadata anchors.
- `HurricaneManager` cyclone-linked formation tracker, atmosphere amplification, lightning scheduling, sync cadence. Replace reservation regions and Simple Clouds cloud generator interactions.
- `HurricaneEnvironmentAnalyzer` ocean/humidity/storm-signal analysis. Replace `sampleConvectiveCoverage` Simple Clouds scanning with PA-native cloud visual metadata and WeatherCell coverage.
- `HurricaneSemantics` eye/eyewall/band math can become a PA-native semantic sampler, but the current class also owns Simple Clouds reservation region creation and imports a client cache.

Simulation logic reusable, cloud attachment not reusable:

- Current hurricane formation from `CycloneSnapshot` and live atmosphere.
- `applyAtmosphereAmplification` forcing of humidity, pressure, temperature, rain, cloud cover, cloud water.
- Eye/eyewall/band semantic calculations in `HurricaneSemantics.sample(...)`.

Simple Clouds only:

- `HurricaneManager.RESERVATION_REGIONS`, `projectatmosphere$syncManagedRegion`, `projectatmosphere$addManagedRegion`, `projectatmosphere$removeManagedRegion`, `reconcileReservedCloudSpace`.
- `HurricaneSemantics.createReservationRegion`, `getReservationRegionAt`, `resolveLevel`.
- `CloudGeneratorHurricaneReservationMixin`.
- Simple Clouds hurricane renderer/mixins/shaders.

Needs rewrite:

- PA-native hurricane cloud ownership. It must be represented by WeatherCell/Cyclone/SevereWeatherEvent state plus native cloud morphology (`SPIRAL_STORM`) rather than Simple Clouds reservation regions.
- Convective coverage source. Replace Simple Clouds `CloudManager.getClouds()` with PA-native `CloudVisualStateManager.getStormVisualCandidates(...)` or native cloud region data.
- Hurricane persistence location. Current hurricane tags are stored inside `TornadoStorageManager` and are Simple Clouds-gated.

### Required New Hurricane Adapter Classes

Recommended adapters/classes:

- `HurricaneEventState` or generic `SevereWeatherEventState` with hurricane subtype fields.
- `CycloneWeatherCellBridge`: links ocean basin/cyclone state to large-scale WeatherCell or future `WeatherCellType.CYCLONE`.
- `HurricaneFormationController`: evaluates ocean influence, warm ocean coverage, pressure anomaly, circulation, humidity, WeatherCell/CYCLONE support.
- `HurricaneCloudStructureController`: future PA-native cloud morphology coordinator for `SPIRAL_STORM`, not rendering.
- `HurricaneAtmosphereFeedbackController`: adapter around `applyAtmosphereAmplification`.
- `HurricaneWorldEffectsController`: adapter around `HurricaneWindField` and `HurricaneDestructionManager`.
- `HurricaneSemanticSampler`: common-safe semantic math without Simple Clouds reservation APIs or client cache imports.

### Future Hurricane Implementation Order

1. Move hurricane persistence out of `TornadoStorageManager` into severe event persistence.
2. Add WeatherCell/Cyclone bridge using existing `CycloneSnapshot`, `OceanBasinManager`, and live atmosphere.
3. Replace Simple Clouds convective coverage with PA-native cloud visual metadata and WeatherCell storm strength.
4. Form hurricane event from sustained ocean/cyclone support.
5. Reuse/adapt `HurricaneInstance` lifecycle and category logic.
6. Reuse `HurricaneWindField`, `HurricaneDestructionManager`, and `HurricaneBlockBreakRules`.
7. Add future PA-native SPIRAL_STORM cloud morphology integration only after event simulation is stable.

### Hurricane Risks

- `HurricaneManager.tick` is only called from `AtmosphereManager.tick` when Simple Clouds is loaded. PA-native hurricanes would not run in a PA-native-only cloud mode until this gate is replaced.
- `HurricaneSemantics` imports `ClientHurricaneStateCache` in a common package class. This is a client/server separation risk.
- Hurricane automatic formation currently depends partly on Simple Clouds convective cloud coverage.
- Existing cyclone spawning is random near active player regions and may not satisfy the final requirement of atmosphere-driven hurricanes unless replaced by ocean/pressure/circulation-driven formation.

## 4. Blizzard Reuse Plan

### Current Snowstorm Path

Legacy snowstorm path:

1. `SnowstormManager.startSnowstorm(int, CloudRegion)` creates `SnowStorm`.
2. `SnowstormManager.tick(ServerLevel)` iterates players.
3. Each storm checks `snow.getCloudRegion().intersects(new SpawnRegion(...))`.
4. `applyEffects` applies blindness/slowness/message according to `SnowTier`.

Snow accumulation path:

1. `ServerLevelWeatherCycleMixin.tickChunk`
2. `LocalizedPrecipitationBlockUpdater.tickChunk`
3. `WeatherCloudQueries.sampleAt`
4. biome precipitation handling
5. snow layer placement/increment when sample is snowing and biome should snow

### Blizzard Reuse Map

Reusable as-is:

- `SnowTier.resolve(float temperatureCelsius, float humidity, float windSpeedMps, float precipitationStrength)`.
- `LocalizedPrecipitationBlockUpdater` for localized snow accumulation.
- Temperature-mod compatibility idea in `SnowstormManager.triggerTemperatureEffect`; implementation should be moved behind a PA-native event effect controller.

Reusable with adapter:

- `ServerLevelSnowStormMixin` if external API compatibility is still desired. It should query PA-native blizzard events, not Simple Clouds-backed `SnowstormManager`.
- `SnowstormManager.applyEffects` logic for blindness/slowness can be adapted, but actionbar debug message should not be part of final gameplay effect without config.

Simple Clouds only:

- `SnowstormManager` storage and all region intersection logic.
- `SnowStorm` because it stores Simple Clouds `CloudRegion`.

Needs rewrite:

- Blizzard formation and persistence. Current snowstorms do not have independent saved state, weather-cell ownership, atmosphere-driven lifecycle, or PA-native cloud attachment.
- Blizzard world effect ownership. It should be a severe event linked to cold/moist/windy live atmosphere and snowfall, not a Simple Clouds cloud region.

### Required New Blizzard Adapter Classes

Recommended adapters/classes:

- `BlizzardEventState` or generic `SevereWeatherEventState` with blizzard subtype fields.
- `BlizzardFormationController`: evaluates temperature, humidity, wind speed/gusts, cloud water, snowing precipitation, snow cover if available.
- `BlizzardLifecycleController`: handles strengthening/weakening/dissipation.
- `BlizzardWorldEffectsController`: applies visibility/status effects and calls/reuses localized snow accumulation.
- `BlizzardExternalApiAdapter`: replaces `SnowstormManager` queries for `ISnowStormLevel` consumers.

### Future Blizzard Implementation Order

1. Add PA-native blizzard event state under severe event persistence.
2. Use `SnowTier.resolve` as the first severity classifier.
3. Form blizzards from cold live atmosphere, humidity/cloud water, strong wind, and precipitation/snow signals.
4. Reuse `LocalizedPrecipitationBlockUpdater` for snow accumulation rather than duplicating block logic.
5. Replace `ServerLevelSnowStormMixin` query source with PA-native blizzard event lookup.
6. Add future client visibility/audio hooks only after simulation and persistence are stable.

### Blizzard Risks

- Current Simple Clouds snowstorm state is only in memory and has no inspected persistence.
- Existing `SnowstormManager.tick` sends a player message every effect application; this should not be reused directly as final gameplay behavior.
- Current block snow accumulation depends on `WeatherCloudQueries.sampleAt`; future blizzard effects must ensure that PA-native cloud/weather samples report snowing precipitation correctly.

## 5. Simple Clouds Dependency Map

Direct Simple Clouds dependencies found in severe-weather relevant code:

- Tornado:
  - `TornadoManager`: `CloudRegion`, `CloudManager`, `SpawnRegion`.
  - `TornadoInstance`: `CloudRegion`, `CloudManager`, `ITornadoRegion` descriptor push.
  - `TornadoProbabilityManager`: Simple Clouds loaded gate and severe-cloud lookup through `AtmosphereCloudServices`.
  - `CommandTornadoService`: `CloudRegion`, `CloudManager`, `SpawnRegion`, Simple Clouds cumulonimbus lookup/seed.
  - `TornadoStorageManager`: load/save returns early when Simple Clouds is not loaded.
  - `CloudRegionMixin`: attaches tornado descriptors to Simple Clouds `CloudRegion`.
- Hurricane:
  - `HurricaneManager`: `CloudRegion`, `CloudManager`, `CloudGenerator`, `SimpleCloudsConstants`.
  - `HurricaneInstance`: `CloudManager.getCloudHeight`.
  - `HurricaneEnvironmentAnalyzer`: `CloudManager.getClouds` and `CloudRegion`.
  - `HurricaneSemantics`: `CloudRegion`, `CloudManager`, `SimpleCloudsConstants`.
  - `CloudGeneratorHurricaneReservationMixin`: Simple Clouds generator reservation hooks.
- Blizzard/snowstorm:
  - `SnowstormManager`: `CloudRegion`, `SpawnRegion`.
  - `SnowStorm`: `CloudRegion`.
- Shared severity:
  - `StormSeverityScale.sampleCloudLevel`: `CloudManager.get(level).getClouds()`.
- Rendering/client:
  - Simple Clouds tornado/hurricane renderers and Simple Clouds renderer pipeline mixins are client/out-of-scope for this planning task.

## 6. PA-Native Replacement Map

| Current Simple Clouds concept | PA-native replacement |
| --- | --- |
| Simple Clouds `CloudRegion` as tornado/hurricane/blizzard owner | `WeatherCellState` plus `SevereWeatherEventState`, optionally linked to PA native cloud region ids |
| `ITornadoRegion` descriptor list on Simple Clouds cloud | PA-native event anchor DTO keyed by event id and WeatherCell/native cloud ids |
| `CloudManager.getClouds()` severe cloud lookup | `WeatherCellManager.getCells`, `CloudVisualStateManager.getStormVisualCandidates`, native cloud region manager/state |
| Simple Clouds cumulonimbus requirement for tornado spawn | SUPERCELL WeatherCell plus future Mesocyclone state |
| Hurricane reservation `CloudRegion` inserted into Simple Clouds generator | PA-native hurricane event plus future SPIRAL_STORM cloud morphology control |
| Simple Clouds convective coverage in hurricane analyzer | WeatherCell storm support plus native cloud visual metadata storm strength/coverage |
| Simple Clouds snowstorm region intersections | Blizzard severe event radius/coverage and live atmosphere sample |
| Simple Clouds-gated `TornadoStorageManager` | Common severe event saved data independent of Simple Clouds |
| Client render mixins as simulation source | Server authoritative event snapshots; client render/sound consumes snapshots only |

## 7. Shared Severe Weather Architecture Proposal

Recommended ownership model: hybrid model.

```
Forecast baseline
-> Persistent live atmosphere
-> WeatherCell
-> SevereWeatherEvent
-> Tornado / Hurricane / Blizzard world-effect controllers
-> Client snapshots for rendering/sound later
```

### Ownership

Formation:

- WeatherCell remains the atmospheric precursor owner.
- `TornadoFormationController` forms Mesocyclone/Tornado from SUPERCELL WeatherCells.
- `HurricaneFormationController` forms Hurricane from ocean/cyclone/large WeatherCell support.
- `BlizzardFormationController` forms Blizzard from cold, moist, windy, snowy live atmosphere.

Ticking:

- `SevereWeatherEventManager` owns common event tick cadence.
- Subtype controllers own phenomenon-specific lifecycle and world effects.

Persistence:

- `SevereWeatherEventSavedData` should persist all active severe events, subtype state, source WeatherCell id, current position, age, intensity, lifecycle, and relevant atmospheric metrics.
- Do not store forecast arrays or duplicate atmosphere state.
- Existing `WeatherCellSavedData` remains separate.

World effects:

- Tornado world effects adapt `TornadoInstance` force/destruction/glass logic.
- Hurricane world effects reuse `HurricaneWindField`, `HurricaneDestructionManager`, and `HurricaneBlockBreakRules`.
- Blizzard world effects reuse `LocalizedPrecipitationBlockUpdater` and `SnowTier`.

Rendering hooks:

- Out of scope for Phases 8-10 implementation foundation.
- Future client systems should consume severe event snapshots and cloud visual metadata.

Sound:

- Future client-only sound controllers should consume severe event snapshots.
- No client sound classes should be imported in common/server event managers.

Config:

- Keep existing tornado/hurricane config where behavior-compatible.
- Add PA-native severe weather config keys only when behavior cannot reuse existing keys cleanly.
- Blizzard config appears incomplete; final blizzard phase likely needs explicit enable/intensity/effects toggles.

Networking:

- Use lightweight server-to-client snapshots.
- Client handling must be `Dist.CLIENT` gated or delegated to client-only packet handlers.

## 8. Phase 8 Implementation Plan - Tornadoes

Prerequisites:

- WeatherCell SUPERCELL evolution remains stable and persisted.
- Shared severe event persistence exists or is added at phase start.
- PA-native cloud region ids/visual metadata are available for optional attachment.

Likely modified files:

- `modules/weathercell/WeatherCellState.java` if adding Mesocyclone link fields.
- `modules/weathercell/WeatherCellManager.java` if severe event tick is wired near WeatherCell tick.
- `manager/ForecastOrchestrator.java` or `AtmosphereManager.java` for severe event tick entry.
- `modules/tornado/TornadoInstance.java` only if extracting reusable world-effect logic is chosen.
- `modules/tornado/TornadoManager.java` only as legacy Simple Clouds manager, not as PA-native owner.
- `network/NetworkHandler.java` for packet registration.

Likely new systems:

- `modules/severe/SevereWeatherEventState`.
- `modules/severe/SevereWeatherEventType`.
- `modules/severe/SevereWeatherEventManager`.
- `modules/severe/SevereWeatherEventSavedData`.
- `modules/tornado/native/TornadoFormationController`.
- `modules/tornado/native/MesocycloneState`.
- `modules/tornado/native/TornadoWorldEffectsController`.
- `network/SyncSevereWeatherEventsPacket` or tornado-specific packet.

Existing systems reused:

- `AtmosphericSupportEvaluator`.
- `WeatherCellManager`, `WeatherCellState`, `WeatherCellType.SUPERCELL`.
- `StormMotionModel`.
- `StormShieldManager`.
- `GlassDamageManager`.
- `TornadoInstance` force/destruction logic by extraction or adapter.

Systems explicitly not touched:

- Hurricane implementation.
- Blizzard implementation.
- Rendering/shaders.
- Simple Clouds behavior except crash-proof gating if required.
- Forecast regeneration.
- Cloud morphology final visuals.

Validation checklist:

- SUPERCELL can produce Mesocyclone only from atmosphere-driven support.
- Tornado can form only from Mesocyclone/SUPERCELL support.
- No Simple Clouds `CloudRegion` or `CloudManager` import exists in PA-native tornado manager/state/controllers.
- Entity dragging and block effects occur from PA-native event position.
- Tornado persists and resumes after restart.
- Tornado sync reaches client without common code importing client packages.
- Legacy Simple Clouds tornado command path still works or is explicitly marked legacy.

## 9. Phase 9 Implementation Plan - Hurricanes

Prerequisites:

- Severe event manager/persistence from Phase 8.
- Ocean basin and cyclone state persistence verified.
- Native cloud visual metadata can describe storm candidates.

Likely modified files:

- `modules/atmosphere/CycloneManager.java` if cyclone formation needs less random, more ocean/pressure-driven criteria.
- `modules/hurricane/HurricaneManager.java` if splitting legacy Simple Clouds manager from PA-native manager.
- `modules/hurricane/HurricaneInstance.java` if removing cloud-height anchor dependency from PA-native state.
- `modules/hurricane/HurricaneEnvironmentAnalyzer.java` if replacing Simple Clouds convective coverage with PA-native cloud/WeatherCell support.
- `manager/ForecastOrchestrator.java` or `AtmosphereManager.java` to tick PA-native hurricane severe events.
- `network/NetworkHandler.java` for event sync.

Likely new systems:

- `modules/hurricane/native/HurricaneFormationController`.
- `modules/hurricane/native/CycloneWeatherCellBridge`.
- `modules/hurricane/native/HurricaneAtmosphereFeedbackController`.
- `modules/hurricane/native/HurricaneWorldEffectsController`.
- `modules/hurricane/native/HurricaneSemanticSampler`.
- Optional `WeatherCellType.CYCLONE` activation controller.

Existing systems reused:

- `CycloneManager` and `CycloneSnapshot`.
- `CycloneImpactApplier`.
- `OceanBasinManager` and ocean influence state.
- `HurricaneCategory`.
- `HurricaneWindField`.
- `HurricaneDestructionManager`.
- `HurricaneBlockBreakRules`.
- `HurricaneInstance` lifecycle/persistence fields by adapter.
- `CloudVisualStateManager` storm candidates.

Systems explicitly not touched:

- Tornado implementation.
- Blizzard implementation.
- Rendering/shaders.
- Distant Horizons.
- Simple Clouds hurricane renderer except legacy compatibility.

Validation checklist:

- Hurricane formation depends on ocean moisture, low pressure, circulation/cyclone support, storm strength, and sustained support.
- No PA-native hurricane formation depends on Simple Clouds `CloudRegion`.
- Hurricane event persists and resumes after restart.
- Atmosphere feedback affects live atmosphere at current hurricane position.
- Existing Simple Clouds reservation path remains isolated as legacy.
- No forecast data is duplicated in severe event saves.

## 10. Phase 10 Implementation Plan - Blizzards

Prerequisites:

- Severe event manager/persistence from Phase 8.
- Snow/rain precipitation sampling remains localized through `WeatherCloudQueries`.
- Temperature, humidity, wind, cloud water, and precipitation state are persistent.

Likely modified files:

- `modules/snowstorm/SnowstormManager.java` if retained as a compatibility facade.
- `mixin/ServerLevelSnowStormMixin.java` to query PA-native blizzard events.
- `manager/LocalizedPrecipitationBlockUpdater.java` only if blizzard-specific snow accumulation needs an additional event influence; otherwise leave as-is.
- `modules/weather/SnowTier.java` only if thresholds require tuning from actual ranges.
- `manager/ForecastOrchestrator.java` or `AtmosphereManager.java` to tick blizzard severe events if not already generic.

Likely new systems:

- `modules/blizzard/BlizzardFormationController`.
- `modules/blizzard/BlizzardLifecycleController`.
- `modules/blizzard/BlizzardWorldEffectsController`.
- `modules/blizzard/BlizzardEventState` if not represented by generic severe event subtype fields.
- `modules/blizzard/BlizzardCompatibilityAdapter`.
- Optional `WeatherCellType.BLIZZARD` activation controller.

Existing systems reused:

- `SnowTier`.
- `LocalizedPrecipitationBlockUpdater`.
- `AtmosphericSupportEvaluator` plus direct temperature/wind/precipitation checks.
- `WeatherCellState` if using BLIZZARD cells as precursors.
- `StormShieldManager` if protection should reduce blizzard effects.

Systems explicitly not touched:

- Tornado implementation.
- Hurricane implementation.
- Rendering/shaders.
- Simple Clouds snowstorm behavior unless adapting compatibility queries.

Validation checklist:

- Blizzard formation requires cold temperature, humidity/cloud water, precipitation/snow signal, and strong wind.
- Blizzard does not require Simple Clouds `CloudRegion`.
- Blizzard event persists and resumes after restart.
- Snow accumulation still goes through localized precipitation block update or a controlled adapter.
- Player effects are rate-limited and configurable.
- External `ISnowStormLevel` query compatibility works through PA-native event lookup.

## 11. Risks

Critical risks:

- Current tornado and hurricane normal tick paths are gated by `AtmosphereCloudServices.isSimpleCloudsLoaded()` in `AtmosphereManager.tick`. PA-native severe events must not live behind that gate.
- Current severe runtime persistence for tornadoes/hurricanes is in `TornadoStorageManager`, which also returns early when Simple Clouds is absent.
- Common/server classes import or reference client classes in some existing paths, notably `TornadoManager` importing `net.minecraft.client.Minecraft` and `HurricaneSemantics` importing `ClientHurricaneStateCache`. Future PA-native managers must not copy this pattern.
- `StormSeverityScale.sampleCloudLevel` directly depends on Simple Clouds. Any PA-native severe threshold using `StormSeverityScale.resolve` inherits a Simple Clouds dependency unless adapted.

Medium risks:

- `TornadoInstance` and `HurricaneInstance` mix simulation, visual state, persistence, and cloud attachment. Reuse by inheritance or direct ownership will preserve unwanted coupling.
- Hurricane semantic math is valuable but bundled with Simple Clouds reservation creation and client cache access.
- Cyclone spawning is currently randomized near players, not fully ocean/pressure-system driven.
- Legacy snowstorms lack persistence and are purely Simple Clouds cloud-region based.
- Current tornado scheduler is global and may not fit WeatherCell-local severe evolution.

Low risks:

- Existing command paths may remain useful as debug/admin tools if clearly separated from gameplay formation.
- Existing client renderers may continue to serve Simple Clouds compatibility while PA-native severe simulation is built separately.
- Existing config keys can be reused for effect toggles, but semantic names may become misleading if PA-native severe weather diverges.

## 12. Unknowns

Unverified items:

- `TornadoLevel` exact wind/damage scale behavior was not deeply inspected.
- Client audio classes (`TornadoAudioClient`, `TornadoRoarLoop`, `WeatherAudioClient`) were inventoried but not method-audited because sound implementation is out of planning scope.
- Full native cloud morphology evolution into `SPIRAL_STORM` was not method-audited; only file references and morphology family support were inspected.
- All packet registration paths were not fully inspected; packet DTOs and handlers for tornado/hurricane were inspected.
- External snowstorm API expectations from `GabouLibs` were not inspected beyond `ServerLevelSnowStormMixin` implementation.

## 13. Recommended Next Steps

1. Implement Phase 8 foundation first by adding a Simple Clouds-independent `SevereWeatherEvent` manager and persistence layer.
2. Move tornado formation to `SUPERCELL -> Mesocyclone -> Tornado` using `WeatherCellState`, `AtmosphericSupportEvaluator`, and live wind/pressure/humidity fields.
3. Extract or adapt tornado world-effect logic from `TornadoInstance` without carrying `CloudRegion` fields or `ITornadoRegion` descriptor ownership.
4. After PA-native tornadoes work, migrate hurricanes by bridging `CycloneManager` and ocean influence into severe events and replacing Simple Clouds convective coverage with native cloud visual metadata.
5. Implement blizzards last because current reusable pieces are mostly precipitation/snow effects, not a persisted PA-native storm simulation.
6. Keep legacy Simple Clouds severe systems isolated as compatibility/debug paths until PA-native systems fully replace them.

