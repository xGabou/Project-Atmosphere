# Module By Module Audit

## `src/main/java/net/Gabou/projectatmosphere/manager/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `manager/` | Server orchestration, loading, spawning, and top-level climate coordination | `AtmosphereManager`, `ForecastOrchestrator`, `ForecastGenerator`, `ForecastDataStorage`, `SimpleCloudSpawner`, `AtmosphereWorldEffectsManager`, `CropStressManager`, `SandStormManager` | `modules/*`, `compat/*`, `network/*`, `util/*`, `api/*` | Client sync, modules, bootstrap hooks | `ForecastDataStorage` and some coordinator functions are reasonably clear | `AtmosphereManager`, `ForecastOrchestrator`, `ForecastGenerator`, `SimpleCloudSpawner` | Too many orchestration layers share overlapping responsibilities | Very high | High | Document first; do not refactor broadly yet |

### Notes

- `AtmosphereManager` is a broad bootstrap coordinator, not just an atmosphere manager.
- `ForecastOrchestrator` is the main source of “what is the forecast right now?” but also owns load/save and sync coordination.
- `ForecastGenerator` is broader than its name suggests because it builds, groups, and caches forecast state.
- `SimpleCloudSpawner` is policy-heavy and should be documented before any cleanup.

## `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `modules/atmosphere/` | Runtime atmosphere state application and helper logic | `AtmosphericStateRegistry`, `AtmosphericUpdateScheduler`, `CloudManager`, `CloudWaterExchange`, `CloudWaterService`, `CycloneManager`, `HumidityBudget`, `HumidityBudgetService`, `HumiditySourceProfile`, `RainSystem`, `SunlightController`, `RegionAtmosphereState`, `AtmosphereStatusSyncManager` | `modules/region`, `modules/wind`, `modules/weather`, `api`, `util`, `network` | Managers, client caches, storm systems, compat | `CloudWaterService`, `CloudWaterExchange`, `AtmosphericStateRegistry` are cleaner than the rest | `CloudManager`, `CycloneManager`, `AtmosphericUpdateScheduler` | Simulation, spawn behavior, and world-effect logic overlap here | High | High | Keep as backend-owned, but document ownership boundaries before cloud work |

### Notes

- `RegionAtmosphereState` is one of the main backend state objects and is important for future renderer contracts.
- `CloudManager` mixes atmospheric projection with cloud lifecycle and telemetry-like behavior.
- `AtmosphericUpdateScheduler` is useful, but it is a broad dispatcher rather than a tiny scheduler.

## `src/main/java/net/Gabou/projectatmosphere/modules/weather/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `modules/weather/` | Coarse weather resolution and storm classification | `ServerWeatherStateResolver`, `RegionalWeatherPhase`, `StormMotionModel`, `StormLifecyclePhase`, `StormSeverityScale`, `StormShieldManager` | `modules/atmosphere`, `modules/region`, `modules/storm`, `api`, `util` | Managers, client state, cloud selection, compatibility | `ServerWeatherStateResolver` is fairly clear | `StormShieldManager` and the broader storm classification layer need more documentation | Weather phases and storm severity can blur together | High | Medium | Document this module before future cloud renderer work |

## `src/main/java/net/Gabou/projectatmosphere/modules/wind/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `modules/wind/` | Wind forecasting, runtime state, and wind modeling | `WindEngine`, `WindGenerator`, `WindRuntimeState`, `WindForecast`, `WindForces`, `WindForecastApi`, `RegionWindForecastApi`, `TornadoWindModel`, `HighWindModel`, `LowWindModel`, `WindMath`, `WindConfig`, `WindCommand`, `FloatRange` | `modules/region`, `modules/temperature`, `modules/pressure`, `api`, `util` | Tornado, hurricane, cloud integration, client sync | `WindEngine` is the cleanest centralized owner | `WindGenerator` and the collection of model helpers can feel fragmented | Mostly clean, but there is a lot of model surface area | Very high | Medium | Leave core wind ownership alone; document subcomponents carefully |

## `src/main/java/net/Gabou/projectatmosphere/modules/temperature/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `modules/temperature/` | Temperature generation, spikes, and commands | `core/*`, `config/*`, `spike/*`, `util/*`, `command/*` | `modules/region`, `modules/atmosphere`, `modules/weather`, `api`, `util` | Weather resolution, seasonal systems, client readouts | `TemperatureGenerator` and `TemperatureUtils` are conceptually clear | `SpikeManager` and the spike command set are broad but understandable | Some temperature logic is split across core, util, command, and spike packages | Medium | Medium | Mostly good enough; document before moving anything |

## `src/main/java/net/Gabou/projectatmosphere/modules/humidity/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `modules/humidity/` | Humidity generation and humidity commands | `HumidityGenerator`, `HumidityCommand` | `modules/region`, `modules/atmosphere`, `modules/weather`, `api` | Atmosphere runtime, weather resolution, cloud integration | Fairly clean and compact | Very little besides the fact that humidity is mirrored elsewhere | Low; mostly fine | Medium | Low | Good enough; document rather than refactor |

## `src/main/java/net/Gabou/projectatmosphere/modules/pressure/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `modules/pressure/` | Pressure curve generation and pressure command support | `PressureCurveGenerator`, `PressureGenerator`, `PressureCommand` | `modules/region`, `modules/weather`, `api`, `util` | Wind, storm, forecast, atmosphere runtime | The generation responsibilities are reasonably clear | Pressure values are used in many places, so the boundary is important | The module is fine, but pressure concepts are duplicated elsewhere | Medium | Low | Document and preserve the current backend ownership |

## `src/main/java/net/Gabou/projectatmosphere/modules/storm/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `modules/storm/` | Storm chance and global storm history | `StormChanceAdjuster`, `StormGenerator`, `StormLullHook`, `GlobalStormHistoryData` | `modules/weather`, `modules/region`, `modules/wind`, `api`, `util` | Tornado, hurricane, cloud integration, managers | `StormChanceAdjuster` is fairly small and clear | `GlobalStormHistoryData` may need documentation to keep source-of-truth boundaries obvious | Storm state is a connector layer between weather and storm entities | High | Medium | Keep it backend-owned and document it before renderer work |

## `src/main/java/net/Gabou/projectatmosphere/modules/tornado/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `modules/tornado/` | Tornado spawning, lifecycle, destruction, persistence, and transport | `TornadoManager`, `TornadoInstance`, `TornadoSpawner`, `TornadoSpawnScheduler`, `TornadoProbabilityManager`, `TornadoLevel`, `TornadoSnapshot`, `TornadoDebug`, `TornadoCommand`, `GlassDamageManager` | `modules/storm`, `modules/wind`, `modules/atmosphere`, `compat`, `network`, `client` | Client renderer, packets, compat hooks | `TornadoSnapshot` is a good transport object | `TornadoManager` and `TornadoInstance` are too broad | Render-adjacent logic is still mixed into simulation and management | Very high | High | Do not reorganize aggressively yet; document heavily first |

## `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `modules/hurricane/` | Hurricane spawning, lifecycle, persistence, destruction, and render metadata | `HurricaneManager`, `HurricaneInstance`, `HurricaneSnapshot`, `HurricaneRenderSnapshot`, `HurricaneRenderDescriptor`, `HurricaneCloudVolume`, `HurricaneBlockBreakRules`, `HurricaneDestructionManager`, `HurricaneWindField`, `HurricaneSemantics`, `HurricaneSemanticSample`, `HurricaneCategory`, `HurricaneCommand` | `modules/storm`, `modules/atmosphere`, `modules/wind`, `compat`, `network`, `client` | Client renderer, fog, packets, compat hooks | `HurricaneRenderSnapshot` is a good immutable boundary | `HurricaneManager`, `HurricaneInstance`, `HurricaneCloudVolume` are broad and slightly overlapping | Rendering, simulation, and descriptive metadata are still too close together | Very high | High | Preserve snapshots; document the broader classes before touching them |

## `src/main/java/net/Gabou/projectatmosphere/client/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `client/` | Client tick coordination, HUD/effects, caches, and client-only helpers | `ClientTickHandler`, `ClientPacketHandlers`, `ClientSyncLock`, `ClientRenderHook`, `BiomeClientTemperatureCache`, `AtmosphereClientState`, `SimpleCloudsWhiteoutFogHandler`, `TornadoClientEffects`, `HUDOverlayRenderer`, `ClientCrashHandler`, `client/*` subpackages | `network`, `modules/*`, `compat`, `client/render`, `client/fog`, `client/hurricane` | Renderers, HUD, audio, sky effects | `AtmosphereClientState` and the cache layers are relatively clean | `ClientTickHandler` is a catch-all and `SimpleCloudsWhiteoutFogHandler` is broader than its name implies | Several client-only concerns are funneling through a single tick path | Very high | High | Document first; split later, not now |

## `src/main/java/net/Gabou/projectatmosphere/client/render/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `client/render/` | Shader wrappers, render pipelines, debug visualization, and DH-aware hooks | `SimpleCloudsTornadoRenderer`, `SimpleCloudsHurricaneRenderer`, `TornadoShaders`, `HurricaneShaders`, `SimpleCloudsRenderDiagnostics`, `SimpleCloudsDhPipelineSelector`, `TornadoRenderDebugState`, `TornadoLateRenderDiagnostics`, `SkyEffectState`, `VolumeBoxMesh`, `SideInfo`, `HudRenderTest` | `mixin/client`, `client/fog`, `client`, `modules/tornado`, `modules/hurricane`, `resources/assets/projectatmosphere/shaders` | Render hooks, diagnostics, client tick, mixins | `TornadoShaders`, `HurricaneShaders`, `SimpleCloudsDhPipelineSelector` are reasonably focused | `SimpleCloudsTornadoRenderer`, `SimpleCloudsHurricaneRenderer`, `TornadoLateRenderDiagnostics`, `HudRenderTest` are debug-heavy or pipeline-heavy | Render pipeline, diagnostics, and compatibility are mixed together | Extremely high | Very high | Keep as reference until a future snapshot boundary exists |

## `src/main/java/net/Gabou/projectatmosphere/client/fog/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `client/fog/` | Fog state and biome-driven fog classification | `AtmosphereFogState`, `FogBiomeClassifier` | `modules/atmosphere`, `client`, `resources/shaders`, `api` | Client renderers, whiteout fog handler, client tick | `AtmosphereFogState` is a good cache object | Some fog composition responsibilities also live in `SimpleCloudsWhiteoutFogHandler` | Fog state and fog composition are not fully separated | Very high | Medium | Document before any cloud shadow or fog refactor |

## `src/main/java/net/Gabou/projectatmosphere/network/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `network/` | Packet transport and sync plumbing | `SyncWindPacket`, `SyncTornadoesPacket`, `SyncHurricaneStatePacket`, `SyncAtmosphereStatusPacket`, `SpawnTornadoPacket`, `RemoveTornadoPacket`, `ForecastLoadingStatusPacket`, `FogDebugOverridePacket`, `BiomeDayTemperaturePacket`, `AuthChallengePacket`, `AuthChallengeReplyPacket`, `InstrumentReadoutPacket`, `NetworkHandler` | `api`, `modules/tornado`, `modules/hurricane`, `client`, `modules/atmosphere`, `util` | Client cache, client tick, sync managers | Packet classes are mostly clean transport objects | Debug packets and spawning packets can blur transport with behavior | Transport is mostly fine, but some packets imply more policy than they should | High | Medium | Keep packets stable; document what each packet is authoritative for |

## `src/main/java/net/Gabou/projectatmosphere/api/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `api/` | Stable shared contracts and value objects | `WeatherSnapshot`, `WindVectorApi`, `ForecastSampling`, `AtmoApi`, `AtmosphereWorldEffect`, `CropStressEvent`, `CropStressType`, `event/*`, `common/*` | Mostly `minecraft` types and internal value contracts | Almost everything | This is one of the cleanest areas | Very little, though some event names should be documented more clearly | API should stay conservative and stable | Very high | Low | Good enough; document, then leave alone |

## `src/main/java/net/Gabou/projectatmosphere/compat/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `compat/` | External mod adapters and compatibility helpers | `CompatHandler`, `SimpleCloudsCompat`, `ToughAsNailsCompat`, `ColdSweatCompat`, `LegendarySurvivalCompat`, `TemperatureMod`, `temperature/*`, `auroras/*`, `sky/*`, `rainbows/*` | `modules/*`, `client`, `network`, external mod APIs | Client systems, spawners, weather adapters | The adapter intent is clear at the package level | `SimpleCloudsCompat` is too policy-heavy to be a pure adapter | Compatibility logic is sometimes carrying gameplay policy | Very high | High | Document first, especially Simple Clouds and sky compatibility |

## `src/main/java/net/Gabou/projectatmosphere/util/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `util/` | Shared helpers, sampling, scheduling, storage, and cross-cutting utilities | `WeatherSampler`, `AtmosphericPhysics`, `CloudRegionQueue`, `CloudSpawnScheduler`, `AsyncAtmosphereService`, `DelayedTaskScheduler`, `ParticleAtlasDebugger`, `StorageUtils`, `TickCounter`, `WeatherType`, `HurricaneUpload`, `TornadoUpload`, `RegionUpload`, `HumidityGuard`, `UnitFormatter`, `InstrumentUtils`, `AtmosphereUtils`, `BiomeInstanceKey`, `RegionInstanceKey`, `ICloudRegionId` | Many modules and client helpers | Managers, compat, client, network, modules | Several helpers are fine as shared utilities | `ParticleAtlasDebugger` and some upload/debug helpers feel like utility-category leftovers | Utilities can become a dumping ground | Medium | Medium | Good candidate for later cleanup documentation, not immediate refactor |

## `src/main/java/net/Gabou/projectatmosphere/mixin/client/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `mixin/client/` | Client-side engine and Simple Clouds hooks | `DefaultPipelineTornadoMixin`, `DefaultPipelineHurricaneMixin`, `SimpleCloudsRendererLightningBufferMixin`, `SimpleCloudsRendererDiagnosticsMixin`, `SimpleCloudsRendererDhFallbackMixin`, `ShaderSupportPipelineTornadoMixin`, `ShaderSupportPipelineHurricaneMixin`, `DhSupportPipelineDiagnosticsMixin`, `BindingManagerAccessor`, `InstanceableMeshDiagnosticsMixin`, `LoadingOverlayMixin`, `LoadingScreenMixin`, `MinecraftCrashHandlerMixin`, `particle/*` | Minecraft client internals, Simple Clouds internals, render classes | Renderers, diagnostics, client bootstrap | Some accessors are expected and fine | Diagnostics mixins and fallback hooks are easy to confuse with real renderer ownership | Mixins should stay thin, but they currently carry a lot of render policy | Extremely high | High | Keep behavior stable and document before any cleanup |

## `src/main/resources/assets/projectatmosphere/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `resources/assets/projectatmosphere/` | Shader and asset definitions | `shaders/core/*`, `shaders/compute/*` | Client renderers, fog handlers, mixins | Renderers, diagnostics, shader wrappers | Assets are cleanly separated from code | Some shader names encode policy or debug behavior | Asset organization is fine, but some shader contracts are implicit rather than documented | Extremely high | Medium | Document shader intent before cloud renderer work |

## `src/main/resources/data/projectatmosphere/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `resources/data/projectatmosphere/` | Gameplay data, tags, recipes, and cloud-related JSON definitions | `cloud_types/*`, `tags/blocks/*`, `recipes/*` | Cloud selection logic, weather resolution, compat, block systems | Managers, compat, Simple Clouds integration | Data-driven boundaries are good here | Some cloud-type naming choices can be hard to trace back to logic | Cloud data is partly asset-driven and partly code-driven | High | Low | Document mapping rules, do not refactor yet |

## `src/main/resources/data/simpleclouds/`

| Module | Main responsibility | Classes inside | External classes it depends on | Classes that depend on it | Clean dependencies | Suspicious dependencies | Wrong ownership concerns | Cloud renderer relevance | Risk level | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `resources/data/simpleclouds/` | Simple Clouds cloud types and spawn configurations | `cloud_types/*`, `cloud_spawning/*`, helper script | Simple Clouds runtime, compat layer, weather sampling | `SimpleCloudsCompat`, `CloudLibrary`, `SimpleCloudSpawner` | Data-driven resource definitions are fine | The set of cloud types can obscure what is simulation policy vs visual selection | Simple Clouds data is a separate contract that needs documentation, not refactoring | Very high | Medium | Document the mapping rules before cloud renderer work |

