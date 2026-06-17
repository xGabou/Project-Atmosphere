# Project Atmosphere Codebase Overview

This project is organized around a server-owned climate model, client-owned visual state, and a Simple Clouds integration layer that bridges the two. The overall structure is workable for a future cloud renderer, but several classes still mix simulation, sync, render hooks, and debug behavior.

## 1. Bootstrap And Integration

| Field | Content |
|---|---|
| Subsystem | Bootstrap and mod integration |
| Main classes | `ProjectAtmosphere`, `CompatHandler`, `NetworkHandler`, `ClientPacketHandlers`, `AtmosphereStatusSyncManager`, `ProjectAtmosphereCrashHandler` |
| What it currently owns | Mod lifecycle, config registration, platform hooks, packet registration, integration startup, and a few global event bridges |
| What it should own | Only top-level registration and wiring |
| What it should not own | Weather math, render logic, cloud geometry, or storm lifecycle details |
| Current quality | OK |
| Risk level | Medium |
| Notes | This layer is broad, but it is a normal place for the mod entrypoint to coordinate other systems. The important thing is to keep it from becoming a hidden place for gameplay logic. |

## 2. Forecast And Region Model

| Field | Content |
|---|---|
| Subsystem | Forecast and regional climate model |
| Main classes | `ForecastOrchestrator`, `ForecastGenerator`, `ForecastDataStorage`, `RegionForecastOrchestrator`, `BiomeForecastGenerator`, `ForecastRegion`, `RegionAtmosphereState`, `AtmosphericStateRegistry`, `RegionPersistence`, `RegionCurves` |
| What it currently owns | Forecast generation, forecast persistence, region lookup, live region climate state, and climate curve assembly |
| What it should own | Stable backend climate data and region state |
| What it should not own | Render pass logic, client smoothing, or Simple Clouds geometry decisions |
| Current quality | GOOD overall, but broad in the orchestrator layer |
| Risk level | Medium |
| Notes | This is one of the strongest parts of the codebase for future renderer work because it already gives a mostly server-owned view of climate state. |

## 3. Atmosphere Runtime

| Field | Content |
|---|---|
| Subsystem | Live atmosphere simulation |
| Main classes | `AtmosphereManager`, `AtmosphericUpdateScheduler`, `CloudManager`, `CloudWaterService`, `CloudWaterExchange`, `RainSystem`, `SunlightController`, `HumidityBudgetService`, `CycloneManager` |
| What it currently owns | Atmospheric updates, biome influence application, cloud lifecycle work, rain and humidity helpers, and some world-effect scheduling |
| What it should own | Runtime simulation and world effects |
| What it should not own | Client render state, shader configuration, or renderer-specific cloud snapshots |
| Current quality | MIXED |
| Risk level | High |
| Notes | This layer is functional, but several classes are broader than their names suggest. It is a likely place for future cleanup before a real cloud renderer depends on it. |

## 4. Wind And Weather Resolution

| Field | Content |
|---|---|
| Subsystem | Wind and coarse weather resolution |
| Main classes | `WindEngine`, `WindGenerator`, `WindRuntimeState`, `WindForecast`, `WindForces`, `ServerWeatherStateResolver`, `StormChanceAdjuster`, `WeatherSampler`, `WeatherType`, `CloudLibrary` |
| What it currently owns | Wind vectors, weather phase classification, cloud-type selection, and weather sampling for cloud spawning |
| What it should own | The backend weather state that future renderers can consume |
| What it should not own | Visual density, fog behavior, or renderer LOD decisions |
| Current quality | GOOD, with some duplication around cloud/weather labels |
| Risk level | Medium |
| Notes | Wind is already centralized enough to be a reliable backend input for future cloud visuals. |

## 5. Tornado And Hurricane Systems

| Field | Content |
|---|---|
| Subsystem | Tornado and hurricane simulation |
| Main classes | `TornadoManager`, `TornadoInstance`, `TornadoSpawner`, `TornadoSpawnScheduler`, `TornadoProbabilityManager`, `HurricaneManager`, `HurricaneInstance`, `HurricaneSnapshot`, `TornadoSnapshot`, `HurricaneRenderSnapshot`, `HurricaneRenderDescriptor`, `HurricaneCloudVolume` |
| What it currently owns | Storm lifecycle, destruction, cloud attachment, persistence, snapshot transport, and render-adjacent metadata |
| What it should own | Server-authoritative storm state and compact snapshot data |
| What it should not own | Actual draw calls, shader uniform setup, or render-pass-specific behavior |
| Current quality | MIXED |
| Risk level | High |
| Notes | The storm systems already have a useful split between simulation and snapshot data, but the managers and instances are still broader than ideal. |

## 6. Client State And Sync

| Field | Content |
|---|---|
| Subsystem | Client state, sync, and smoothing |
| Main classes | `AtmosphereClientState`, `AtmosphereFogState`, `ClientHurricaneStateCache`, `ClientPacketHandlers`, `ClientTickHandler`, `ClientSyncLock`, `SyncAtmosphereStatusPacket`, `SyncTornadoesPacket`, `SyncHurricaneStatePacket`, `SyncWindPacket` |
| What it currently owns | Client-side smoothing, state caching, packet application, and several visual side effects |
| What it should own | A stable cache of server-authored state plus client-only interpolation |
| What it should not own | Game simulation, server logic, or renderer ownership of the source of truth |
| Current quality | OK, but too much happens in `ClientTickHandler` |
| Risk level | High |
| Notes | This layer is the most likely place for hidden coupling because multiple systems mutate or consume the same client-visible values. |

## 7. Renderer And Shader Bridge

| Field | Content |
|---|---|
| Subsystem | Renderer hooks and shader-facing code |
| Main classes | `SimpleCloudsTornadoRenderer`, `SimpleCloudsHurricaneRenderer`, `TornadoShaders`, `HurricaneShaders`, `SimpleCloudsRenderDiagnostics`, `TornadoRenderDebugState`, `TornadoLateRenderDiagnostics`, `SimpleCloudsDhPipelineSelector`, `VolumeBoxMesh`, `SkyEffectState` |
| What it currently owns | Render hooks, shader wrappers, diagnostics, depth behavior, and debug paths |
| What it should own | The translation from PA cloud data into visuals, shadows, lighting hints, and fallback paths |
| What it should not own | Sim state, cloud spawning policy, or persistence |
| Current quality | MIXED |
| Risk level | Very high |
| Notes | This is the main area that should remain isolated from the backend. The future renderer should connect here, not inside simulation classes. |

## 8. Compatibility And Simple Clouds

| Field | Content |
|---|---|
| Subsystem | Compatibility and Simple Clouds integration |
| Main classes | `SimpleCloudsCompat`, `SimpleCloudSpawner`, `CloudRegionQueue`, `CloudSpawnScheduler`, `TornadoUpload`, `HurricaneUpload`, `RegionUpload`, `WeatherSampler`, `WeatherType`, `CloudLibrary` |
| What it currently owns | Mapping PA climate into Simple Clouds regions and cloud ids, plus some transport and spawn policy |
| What it should own | Adapter behavior and compatibility boundaries |
| What it should not own | Core climate rules or renderer internals |
| Current quality | MIXED |
| Risk level | High |
| Notes | This is one of the most important integration points for future renderer work, but it currently contains more policy than a pure adapter should. |

## 9. Shared APIs And Utilities

| Field | Content |
|---|---|
| Subsystem | Shared API and utilities |
| Main classes | `WeatherSnapshot`, `WindVectorApi`, `ForecastSampling`, `AtmoApi`, `AtmosphereWorldEffect`, `CloudWaterExchange`, `AtmosphericPhysics`, `UnitFormatter`, `DelayedTaskScheduler`, `StorageUtils` |
| What it currently owns | Lightweight contracts and helper utilities |
| What it should own | Small, stable value objects and reusable helpers |
| What it should not own | Gameplay orchestration or renderer-specific decisions |
| Current quality | GOOD overall |
| Risk level | Low to medium |
| Notes | This area is mostly healthy. It should stay small and explicit because future renderer boundaries will likely rely on these contracts. |

