# Class Responsibility Matrix

| Class | Current responsibility | Actual observed responsibility | Should own | Should not own | Naming quality | Cohesion | Coupling | Renderer relevance | Refactor priority | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `ProjectAtmosphere` | Mod bootstrap | Top-level registration and platform wiring | Lifecycle hooks and registration | Simulation details | GOOD | Good | Medium | Medium | Low | `DO_NOT_TOUCH_NOW` |
| `AtmosphereManager` | Atmosphere orchestration | Server lifecycle, login sync, cloud region queueing, regen control, command wiring | High-level coordination only | Forecast internals and renderer policy | CONFUSING | Low | High | Medium | High | `TOO_BROAD` |
| `ForecastOrchestrator` | Forecast control | Storage, regeneration, login sync, region lookup, weather access, tick scheduling | Runtime forecast coordination | Render-facing logic | CONFUSING | Low | High | Low | High | `TOO_BROAD` |
| `ForecastGenerator` | Forecast generation | Forecast building, caching, grouping, averaging, fallback assembly | Pure forecast construction | Sync and orchestration | MISNAMED | Medium | High | Low | High | `TOO_BROAD` |
| `ForecastDataStorage` | Data storage | Persistence only | Save/load forecasts | Runtime orchestration | GOOD | High | Low | Low | Low | `GOOD` |
| `AtmosphericStateRegistry` | State registry | Live region state registry and neighbor map | Region state lookup | Render logic | GOOD | High | Medium | Medium | Low | `GOOD` |
| `RegionAtmosphereState` | Region atmosphere data | Live climate state plus forecast curves and helpers | Region climate state | Renderer decisions | OK | Medium | Medium | Medium | Medium | `OK` |
| `CloudManager` | Cloud management | Atmospheric projection, spawn attempts, telemetry, region scanning | Cloud lifecycle state | Render path selection | CONFUSING | Low | High | High | High | `TOO_BROAD` |
| `AtmosphericUpdateScheduler` | Update scheduling | Active/passive atmosphere updates and telemetry | Update dispatch only | Simulation policy | OK | Medium | Medium | Low | Medium | `OK` |
| `CloudWaterService` | Cloud water service | Moisture exchange helper | Moisture transfer helper | Orchestration | GOOD | High | Low | Low | Low | `GOOD` |
| `ServerWeatherStateResolver` | Weather resolution | Maps climate state into coarse weather phase | Weather phase classification | Render specifics | GOOD | High | Medium | Low | Low | `GOOD` |
| `WindEngine` | Wind engine | Central wind forecast/runtime rebuild and vector generation | Wind model ownership | Renderer policy | GOOD | High | Medium | Low | Low | `GOOD` |
| `CloudLibrary` | Cloud library | Cloud-id selection by severity and weather category sets | Cloud-id catalog and selection helper | Simulation state | CONFUSING | Medium | Medium | High | Medium | `OK` |
| `SimpleCloudSpawner` | Simple cloud spawning | Weather sampling, spawn search, cloud-id choice, telemetry | Spawn strategy helper | Core climate ownership | CONFUSING | Low | High | High | High | `TOO_BROAD` |
| `SimpleCloudsCompat` | Simple Clouds compatibility | Region spawn bridge, radius policy, wind coupling, cloud creation | Adapter only | Core weather policy | MISNAMED | Medium | High | Very high | High | `TOO_BROAD` |
| `TornadoManager` | Tornado management | Simulation, sync, persistence, cloud attachment, snapshot distribution | Tornado lifecycle coordination | Render pass logic | OK | Low | High | High | High | `TOO_BROAD` |
| `TornadoInstance` | Tornado instance | Tornado state, movement, destruction, interpolation, render geometry helpers | Tornado simulation state | Render pipeline policy | CONFUSING | Low | High | Very high | High | `TOO_BROAD` |
| `TornadoSnapshot` | Tornado snapshot | Immutable transport snapshot | Snapshot data only | Mutation logic | GOOD | High | Low | High | Low | `GOOD` |
| `HurricaneManager` | Hurricane management | Simulation, sync, persistence, debug state, region reservation, atmosphere effects | Hurricane lifecycle coordination | Render pipeline logic | OK | Low | High | High | High | `TOO_BROAD` |
| `HurricaneInstance` | Hurricane instance | Lifecycle, phase logic, render descriptor, persistence, anchor logic | Hurricane simulation state | Render-pass details | CONFUSING | Low | High | Very high | High | `TOO_BROAD` |
| `HurricaneRenderSnapshot` | Render snapshot | Immutable render transport | Render data boundary | Simulation mutation | GOOD | High | Low | Very high | Low | `GOOD` |
| `HurricaneRenderDescriptor` | Render descriptor | Render parameters and derived values | Render parameter contract | Storm simulation | OK | Medium | Medium | Very high | Medium | `OK` |
| `HurricaneCloudVolume` | Cloud volume helper | Converts render descriptor into volume-scale values | Volume mapping helper | Storm logic | CONFUSING | Medium | Medium | Very high | Medium | `OK` |
| `AtmosphereClientState` | Client atmosphere state | Client smoothing for humidity, rain, cloud cover | Client-only smoothed state | Server simulation | GOOD | High | Medium | Medium | Low | `GOOD` |
| `AtmosphereFogState` | Fog state | Client fog smoothing and debug override state | Fog cache only | Simulation | GOOD | High | Medium | High | Low | `GOOD` |
| `ClientHurricaneStateCache` | Hurricane cache | Client interpolation, fallback, and cached render state | Client hurricane render cache | Server state ownership | GOOD | High | Medium | Very high | Low | `GOOD` |
| `ClientTickHandler` | Client ticking | Fog, tornado, audio, sky, particles, culling, smoothing | Client tick orchestration only | Simulation mutation | CONFUSING | Low | High | High | High | `TOO_BROAD` |
| `SimpleCloudsTornadoRenderer` | Tornado renderer | Render path, depth handling, debug, uniforms, DH routing | Tornado visual translation | Simulation ownership | CONFUSING | Low | High | Very high | High | `TOO_BROAD` |
| `SimpleCloudsHurricaneRenderer` | Hurricane renderer | Render passes, masks, uniforms, scratch targets | Hurricane visual translation | Simulation ownership | CONFUSING | Low | High | Very high | High | `TOO_BROAD` |
| `TornadoRenderDebugState` | Debug state | Debug mode and diagnostic selection | Debug control only | Production state | GOOD | High | Medium | High | Medium | `OK` |
| `SimpleCloudsWhiteoutFogHandler` | Whiteout fog | Fog plane, cloud tint, whiteout blending | Fog composition | Simulation | MISNAMED | Medium | Medium | High | Medium | `OK` |
| `SyncTornadoesPacket` | Tornado sync packet | Snapshot transport | Packet transport only | Business logic | GOOD | High | Low | High | Low | `GOOD` |
| `SyncHurricaneStatePacket` | Hurricane sync packet | Snapshot transport | Packet transport only | Business logic | GOOD | High | Low | High | Low | `GOOD` |
| `SyncAtmosphereStatusPacket` | Atmosphere status sync | Atmosphere status transport | Packet transport only | Business logic | GOOD | High | Low | Medium | Low | `GOOD` |

