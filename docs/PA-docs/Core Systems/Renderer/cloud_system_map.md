# Project Atmosphere Cloud System Map

This document maps the current ownership boundaries so a future cloud renderer can consume Project Atmosphere data without taking over simulation responsibilities.

## Core Rule

Project Atmosphere owns weather and cloud state.
The renderer only turns PA cloud data into visuals, shadows, and lighting data.

## System Map

| System | Main Files | Owns | Notes |
| --- | --- | --- | --- |
| Forecast generation | `src/main/java/net/Gabou/projectatmosphere/manager/ForecastGenerator.java`, `ForecastOrchestrator.java`, `ForecastDataStorage.java`, `modules/region/ForecastRegion.java`, `modules/atmosphere/AtmosphericStateRegistry.java`, `modules/atmosphere/RegionAtmosphereState.java` | Region forecasts, region runtime atmosphere, persistence, loaded state indexes | This is the authoritative server-side weather pipeline. It already computes temperature, humidity, pressure, wind, and storm chance at region scope. |
| Weather primitives | `src/main/java/net/Gabou/projectatmosphere/api/WeatherSnapshot.java`, `api/AtmoApi.java`, `api/WindVectorApi.java`, `modules/core/WindVector.java`, `modules/weather/ServerWeatherStateResolver.java`, `modules/storm/StormChanceAdjuster.java` | Public weather snapshot, wind access, severity resolution, weather state reads | `AtmoApi` is the current public entry point for current weather at a position. |
| Temperature / humidity / pressure | `modules/temperature/util/TemperatureGenerator.java`, `modules/humidity/HumidityGenerator.java`, `modules/pressure/PressureGenerator.java`, `modules/temperature/config/BiomeTempConfig.java`, `modules/temperature/config/BiomeTempUserConfig.java` | Forecast curves and biome climate inputs | These systems feed the region forecast layer, not the renderer directly. |
| Wind runtime | `modules/wind/WindEngine.java`, `modules/wind/WindGenerator.java`, `modules/wind/HighWindModel.java`, `modules/wind/LowWindModel.java`, `modules/wind/TornadoWindModel.java`, `modules/wind/WindRuntimeState.java` | Dynamic wind vectors and gust state | `WindEngine` is the live wind runtime that updates state in `RegionAtmosphereState` and `WindVector`. |
| Cloud selection and spawning | `manager/SimpleCloudSpawner.java`, `modules/core/CloudLibrary.java`, `compat/SimpleCloudsCompat.java`, `manager/AtmosphereManager.java`, `util/CloudSpawnScheduler.java`, `util/CloudRegionQueue.java` | Cloud type selection, spawn weighting, cloud region queueing | This is where PA chooses which Simple Clouds types appear in the world. |
| Server cloud integration | `modules/atmosphere/CloudManager.java`, `modules/atmosphere/AtmosphericUpdateScheduler.java`, `modules/atmosphere/CycloneManager.java`, `modules/atmosphere/CloudWaterService.java`, `modules/atmosphere/CloudWaterExchange.java` | Live cloud-state updates inside the atmospheric runtime | This is the best place to derive a future render snapshot from already-owned cloud state. |
| Tornado simulation | `modules/tornado/TornadoManager.java`, `TornadoInstance.java`, `TornadoSnapshot.java`, `TornadoSpawner.java`, `TornadoSpawnScheduler.java`, `TornadoProbabilityManager.java`, `GlassDamageManager.java`, `TornadoLevel.java` | Tornado lifecycle, movement, destruction, server snapshots | Tornado logic is already separated from rendering data through snapshots. |
| Hurricane simulation | `modules/hurricane/HurricaneManager.java`, `HurricaneInstance.java`, `HurricaneSnapshot.java`, `HurricaneRenderSnapshot.java`, `HurricaneRenderDescriptor.java`, `HurricaneCloudVolume.java`, `HurricaneSemantics.java` | Hurricane lifecycle, render snapshots, descriptor data, semantics | Hurricane rendering already has a clean snapshot boundary that a future cloud renderer can mirror. |
| Client weather state | `client/ClientPacketHandlers.java`, `client/atmosphere/AtmosphereClientState.java`, `client/fog/AtmosphereFogState.java`, `client/render/SkyEffectState.java`, `client/hurricane/ClientHurricaneStateCache.java` | Smoothed client-side weather/fog state and hurricane render cache | These classes are the current client cache layer and are the natural source for render-time snapshots. |
| Client render and hooks | `client/ClientTickHandler.java`, `client/render/SimpleCloudsTornadoRenderer.java`, `client/render/SimpleCloudsHurricaneRenderer.java`, `client/render/SimpleCloudsDhPipelineSelector.java`, `client/render/SimpleCloudsRenderDiagnostics.java`, `client/SimpleCloudsWhiteoutFogHandler.java` | Render preparation, per-frame cache updates, shader submission, fog side effects | These are the current rendering connection points for cloud-adjacent visuals. |
| Shader wrappers | `client/render/TornadoShaders.java`, `client/render/HurricaneShaders.java`, `assets/projectatmosphere/shaders/core/*`, `assets/projectatmosphere/shaders/compute/*` | Shader registration and GPU inputs | Future cloud rendering should connect to the same wrapper pattern instead of reading raw world data in the shader. |
| Network sync | `network/SyncAtmosphereStatusPacket.java`, `network/SyncTornadoesPacket.java`, `network/SyncHurricaneStatePacket.java`, `modules/atmosphere/AtmosphereStatusSyncManager.java`, `modules/tornado/TornadoManager.java`, `modules/hurricane/HurricaneManager.java` | Server-to-client state transfer | This is the transport layer for future cloud render snapshots. |

## Current Data Ownership

### Server-owned data

- Forecast curves for temperature, humidity, pressure, and wind.
- Live region atmosphere state.
- Weather phase and storm severity.
- Tornado and hurricane lifecycle, movement, destruction, and persistence.
- Cloud spawning weights and cloud-type selection.

### Client-owned data

- Smoothed humidity, rain, and cloud cover values.
- Fog and whiteout state.
- Tornado and hurricane render caches.
- Per-frame render diagnostics and DH pipeline selection.
- Visual-only state such as sky effect flags.

### Renderer-owned data

- GPU shaders, uniforms, render targets, and visibility decisions.
- Draw order, culling, depth handling, and compositing.
- Fallback darkening and lighting response.

## Future Cloud Renderer Boundary

The future renderer should not query `ForecastGenerator` or `RegionAtmosphereState` during draw calls.

Instead, it should consume a derived immutable snapshot built from:

- `RegionAtmosphereState`
- `WeatherSnapshot`
- `ForecastRegion`
- `WindVector`
- `AtmosphereClientState`
- `AtmosphereFogState`
- `ClientHurricaneStateCache`
- `TornadoManager.getClientTornadoes()`

## Practical Placement

- Server snapshot generation should happen after forecast and atmospheric state updates are finalized.
- Client snapshot assembly should happen in the client tick and packet handlers.
- GPU submission should happen in the existing render hook layer or a new dedicated renderer layer that plugs into the same hook points.

## Current Resource Areas Relevant To Clouds

- `src/main/resources/data/simpleclouds/cloud_types/`
- `src/main/resources/data/simpleclouds/cloud_spawning/`
- `src/main/resources/data/projectatmosphere/cloud_types/hurricane.json`
- `src/main/resources/assets/projectatmosphere/shaders/core/`
- `src/main/resources/assets/projectatmosphere/shaders/compute/`
- `src/main/resources/assets/simpleclouds/shaders/compute/`

