# Cloud Renderer Integration Points

This document identifies where a future cloud renderer should connect in the current codebase.

## 1. Server-side data generation

### Where to derive the snapshot

- `manager/ForecastOrchestrator.java`
- `manager/ForecastGenerator.java`
- `modules/atmosphere/AtmosphericStateRegistry.java`
- `modules/atmosphere/RegionAtmosphereState.java`
- `modules/atmosphere/CloudManager.java`
- `modules/wind/WindEngine.java`
- `modules/weather/ServerWeatherStateResolver.java`

### Why here

These classes already own the weather simulation and the live region atmosphere state.
A future cloud render snapshot should be derived after these systems finish updating, not while rendering.

### Suggested boundary

- After region forecasts are built or updated.
- After `AtmosphericStateRegistry.initializeState(...)`.
- After `WindEngine.tick(...)`.
- After `CloudManager.update(...)`.

## 2. Server persistence and restore

### Where to preserve cloud-facing state

- `manager/ForecastDataStorage.java`
- `data/TornadoStorageManager.java`
- `modules/hurricane/HurricaneManager.java`
- `modules/tornado/TornadoManager.java`

### Why here

Cloud render state should survive world reloads whenever the underlying storm or forecast state survives.

## 3. Server-to-client sync

### Where client render data is already sent

- `modules/atmosphere/AtmosphereStatusSyncManager.java`
- `network/SyncAtmosphereStatusPacket.java`
- `network/SyncTornadoesPacket.java`
- `network/SyncHurricaneStatePacket.java`
- `modules/hurricane/HurricaneManager.syncToPlayer(...)`
- `modules/tornado/TornadoManager.syncToPlayer(...)`

### Why here

This is the transport layer for render-relevant state.
The future renderer should read from client cache objects filled by these packets rather than from server classes directly.

## 4. Client cache and interpolation

### Existing client-side state classes

- `client/ClientPacketHandlers.java`
- `client/atmosphere/AtmosphereClientState.java`
- `client/fog/AtmosphereFogState.java`
- `client/hurricane/ClientHurricaneStateCache.java`
- `modules/tornado/TornadoManager.java` client-side list access
- `client/ClientTickHandler.java`

### Why here

This is the correct place to smooth values, age out stale packets, and prepare a renderable snapshot.

### Recommended future connection

- Build a client-side `CloudRenderSnapshot` in or alongside `ClientTickHandler`.
- Keep the snapshot immutable for the frame.
- Feed the snapshot into the renderer on the render thread only.

## 5. Render submission hooks

### Current rendering touch points

- `client/render/SimpleCloudsTornadoRenderer.java`
- `client/render/SimpleCloudsHurricaneRenderer.java`
- `client/render/SimpleCloudsDhPipelineSelector.java`
- `client/render/SimpleCloudsRenderDiagnostics.java`
- `client/SimpleCloudsWhiteoutFogHandler.java`
- `client/render/SkyEffectState.java`

### Existing mixin hook areas

- `mixin/client/DefaultPipelineTornadoMixin.java`
- `mixin/client/DefaultPipelineHurricaneMixin.java`
- `mixin/client/ShaderSupportPipelineTornadoMixin.java`
- `mixin/client/ShaderSupportPipelineHurricaneMixin.java`
- `mixin/client/SimpleCloudsRendererDhFallbackMixin.java`
- `mixin/client/SimpleCloudsRendererDiagnosticsMixin.java`

### Why here

The future cloud renderer should submit at the same rendering stage that currently owns cloud rendering, so the framebuffer and depth behavior stays consistent with the rest of the mod.

## 6. Lighting, shadows, uniforms, and fallback darkening

### Existing places to extend

- `client/render/TornadoShaders.java`
- `client/render/HurricaneShaders.java`
- `assets/projectatmosphere/shaders/core/tornado_round.fsh`
- `assets/projectatmosphere/shaders/core/tornado_composite.fsh`
- `assets/projectatmosphere/shaders/core/hurricane_clouds.fsh`
- `assets/projectatmosphere/shaders/core/hurricane_clouds_transparency.fsh`
- `client/SimpleCloudsWhiteoutFogHandler.java`
- `client/fog/AtmosphereFogState.java`

### Why here

These are the current uniform and post-processing touch points.
A future cloud renderer can add lighting, shadow, and fallback-darkening inputs here without altering simulation code.

## 7. What The Renderer Should Not Do

- It should not query `ForecastGenerator` directly during draw calls.
- It should not recompute weather simulation on the render thread.
- It should not own persistence for cloud state.
- It should not replace the server or client sync model.

## 8. Recommended Future Flow

1. Server updates weather and cloud state.
2. Server persists or syncs the relevant state.
3. Client packet handlers update client caches.
4. Client tick builds a render snapshot.
5. Renderer reads the snapshot and submits visuals.
6. Fog and darkening use the same snapshot so the scene stays coherent.

