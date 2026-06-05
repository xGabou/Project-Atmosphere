# Existing Cloud Data Inventory

Research only. No source changes were made for this pass.

This inventory lists the main source classes that expose cloud-relevant values or cloud-adjacent object state in the current codebase.

| Class | Package | Data provided | Is it weather data | Is it cloud object data | Server side or client side | Can renderer use it directly | Should snapshot builder use it later | Risk | Notes |
|---|---|---|---|---|---|---|---|---|---|
| `RegionAtmosphereState` | `net.Gabou.projectatmosphere.modules.atmosphere` | Temperature, humidity, pressure, wind, cloud cover, cloud water, cyclone cloud/rain floors, sunlight, rain intensity | Yes | No | Server | No | Yes | Medium | This is the core PA weather state object for a region. It is not a cloud region model. |
| `AtmosphericStateRegistry` | `net.Gabou.projectatmosphere.modules.atmosphere` | Registry, neighbor map, active set, legacy index for region weather state | Yes, indirectly | No | Server | No | Yes, as lookup support | Medium | Stores and resolves `RegionAtmosphereState`, but does not model clouds. |
| `AtmosphericUpdateScheduler` | `net.Gabou.projectatmosphere.modules.atmosphere` | Update scheduling and state update orchestration for atmosphere regions | Yes, indirectly | No | Server | No | Yes, as orchestration support | Medium | Controls when atmospheric state is updated. |
| `AtmosphereStatusSyncManager` | `net.Gabou.projectatmosphere.modules.atmosphere` | Synced humidity, rain intensity, cloud cover for clients | Yes | No | Server | Not directly | Yes | Low | Client-facing weather sync only. |
| `WeatherSampler` | `net.Gabou.projectatmosphere.modules.weather` | Aggregated humidity, temperature, pressure, wind, storm factor, dominant region, dominant biome, anchor | Yes | No | Server | No | Yes | Medium | Produces the weather aggregate used by spawn policy and weather logic. |
| `WeatherSnapshot` | `net.Gabou.projectatmosphere.api` | Immutable current weather snapshot: cloud cover, rain, temperature, wind, storming, snowing | Yes | No | Shared | Not as a cloud object | Yes | Low | Good weather snapshot, but not a cloud-region model. |
| `ForecastSampling` | `net.Gabou.projectatmosphere.api` | Read-only temperature, humidity, pressure helpers | Yes | No | Server | No | Yes | Low | Query surface only. |
| `WindVectorApi` | `net.Gabou.projectatmosphere.api` | Wind speed and direction samples | Yes | No | Server | No | Yes | Low | Read-only wind facade. |
| `WindVector` | `net.Gabou.projectatmosphere.modules.core` | Wind speed, angle, gust, wind mixing and humidity transport helpers | Yes | No | Server | No | Yes | Medium | Dynamic wind model, not cloud region state. |
| `WeatherType` | `net.Gabou.projectatmosphere.modules.core` | Cloud ID to weather category mapping | Indirectly | No | Shared | No | Yes | Low | Catalog/category mapping only. |
| `CloudLibrary` | `net.Gabou.projectatmosphere.modules.core` | Cloud ID selection by severity and cloud-category lookup helpers | Indirectly | No | Shared | No | Yes | Medium | Cloud catalog helper; not an object state container. |
| `ForecastOrchestrator` | `net.Gabou.projectatmosphere.manager` | Current temperature, humidity, pressure, storm chance, wind, region forecast access | Yes | No | Server | No | Yes | High | Broad orchestration layer, but still weather-state only. |
| `AtmosphereManager` | `net.Gabou.projectatmosphere.manager` | Startup, sync, queueing, and weather system orchestration | Yes, indirectly | No | Server | No | Yes | High | Broad manager; not a cloud-region model. |
| `CloudManager` | `net.Gabou.projectatmosphere.modules.atmosphere` | Reads `CloudRegion` objects, samples their footprint, tracks per-cloud sample history and cloud types | Partly | Yes, but via SimpleClouds objects | Server | Not directly | Yes, as a bridge layer | High | Important bridge, but it still does not define a PA-owned cloud region simulation type. |
| `SimpleCloudSpawner` | `net.Gabou.projectatmosphere.manager` | Weather-driven cloud spawn selection and request creation | Partly | No | Server | No | Yes | High | Spawn policy only; final object creation still happens through SimpleClouds. |
| `SimpleCloudsCompat` | `net.Gabou.projectatmosphere.compat` | Creates/configures `CloudRegion`, sets movement direction, rotation, max speed, acceleration, radius | Partly | Yes, via SimpleClouds `CloudRegion` | Server | Not directly | Yes, as a bridge | High | This is the main bridge into SimpleClouds region objects. |
| `CloudRenderSnapshot` | `net.Gabou.projectatmosphere.clouds` | Debug render snapshot: region center, radius, base/top Y, density, coverage, edge softness, wind offsets, tint, camera position | No | Render snapshot only | Client | Yes, for debug rendering | Yes, but only as render input | Low | This is not a simulation layer. It is renderer input. |
| `CloudDebugSnapshotFactory` | `net.Gabou.projectatmosphere.clouds` | Creates fake debug render snapshots | No | Render snapshot factory only | Client | Yes, for debug rendering | No | Low | Useful for validation, not for the backend layer itself. |
| `CloudRenderStateCache` | `net.Gabou.projectatmosphere.clouds` | Current and debug render snapshot references | No | No | Client | No | No | Low | Plumbing only. |
| `CloudRegionMixin` | `net.Gabou.projectatmosphere.mixin` | Adds PA ID, tornado descriptors, radius multiplier, lifetime adjustment, previous position tracking to SimpleClouds `CloudRegion` | Indirectly | Yes, on a SimpleClouds object | Shared | Not directly | Yes | High | Strong evidence that lifecycle state is still attached to SimpleClouds `CloudRegion`. |
| `HurricaneInstance` | `net.Gabou.projectatmosphere.modules.hurricane` | Hurricane position, radius, wind, category, age, persistence, cloud attachment | Indirectly | Cloud-adjacent storm object | Server | Not directly | Maybe, for storm render snapshot paths | Medium | Storm object, not a general cloud region model. |
| `TornadoInstance` | `net.Gabou.projectatmosphere.modules.tornado` | Tornado position, radius, wind, age, persistence, cloud attachment | Indirectly | Cloud-adjacent storm object | Server | Not directly | Maybe, for storm render snapshot paths | Medium | Storm object, not a general cloud region model. |
| `HurricaneRenderSnapshot` | `net.Gabou.projectatmosphere.modules.hurricane` | Render snapshot for hurricane presentation | No | Render snapshot only | Client | Yes, for hurricane rendering | Maybe, for hurricane renderer | Medium | Renderer-facing state, not backend cloud simulation. |
| `TornadoSnapshot` | `net.Gabou.projectatmosphere.modules.tornado` | Render/sync snapshot for tornado presentation | No | Render snapshot only | Shared | Yes, for tornado rendering | Maybe, for tornado renderer | Medium | Renderer-facing state, not backend cloud simulation. |
| `HurricaneCloudVolume` | `net.Gabou.projectatmosphere.modules.hurricane` | Hurricane-specific cloud volume / SimpleClouds bridge state | Indirectly | Yes, cloud-adjacent object state | Server | Not directly | Yes | Medium | Cloud-adjacent bridge, not the missing general cloud-region layer. |

## Resource-backed cloud data

These resources are not classes, but they strongly confirm current ownership boundaries:

- `src/main/resources/data/simpleclouds/cloud_spawning/*.json` defines `exist_ticks`, `grow_ticks`, `radius`, `speed`, `stretch_factor`, `moves_to_player`, `order_weight`, and `weight` for SimpleClouds cloud spawning.
- `src/main/resources/data/simpleclouds/cloud_types/*.json` defines `weather_type`, `storminess`, `storm_start`, `storm_fade_distance`, `transparency_fade`, and noise settings for cloud catalog behavior.
- `src/main/resources/data/simpleclouds/cloud_types/generate_weather_enum.py` generates the `WeatherType` mapping from cloud type JSONs.
- `src/main/resources/data/projectatmosphere/cloud_types/hurricane.json` shows PA-side cloud-category metadata, but it is still category/profile data rather than cloud-region runtime state.

