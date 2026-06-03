# Client Sync And Cache Readiness

## Which Values Are Already Synced Cleanly

- Atmosphere status data through `SyncAtmosphereStatusPacket`.
- Tornado snapshots through `SyncTornadoesPacket`.
- Hurricane snapshots through `SyncHurricaneStatePacket`.
- Wind data through `SyncWindPacket`.
- Forecast loading status through the loading packets and work queue.

## Which Values Are Cached Cleanly

- `AtmosphereClientState` for humidity, rain, and cloud cover smoothing.
- `AtmosphereFogState` for fog smoothing and override handling.
- `ClientHurricaneStateCache` for hurricane render state and interpolation.
- `ClientSyncLock` for client sync coordination.
- `BiomeClientTemperatureCache` for localized temperature lookup.

## Which Values Are Smoothed Cleanly

- Humidity and rain intensity.
- Cloud cover.
- Fog intensity and fog color.
- Hurricane visual interpolation.

## Which Values Are Mutated From Too Many Places

- `ClientTickHandler` is driving many client behaviors at once.
- Fog composition is touched by both state classes and fog handlers.
- Tornado and hurricane visual state can be influenced by packet handlers, tick logic, and render hooks.
- Debug state can influence render routing in ways that are hard to trace.

## Which Values Are Server-Only But Needed By The Client

- Tornado and hurricane render positions.
- Storm lifecycle phase.
- Region cloud state.
- Renderable intensity and envelope hints.
- Compatibility flags for different render pipelines.

## What Should Be In A Future `CloudRenderStateCache`

- Per-dimension cloud render snapshot.
- Active storm cloud descriptors.
- Last known cloud lighting and shadow hints.
- Render pipeline compatibility state.
- A small amount of interpolation state for position and density.

## What Should Not Be Synced Because It Can Be Derived Client-Side

- Camera-relative LOD.
- Per-frame frustum decisions.
- Screen-space downsample and upscale decisions.
- Debug overlay state.
- Render pass bookkeeping that depends on the current framebuffer.

## Readiness Verdict

The client side is **partially ready**. It already has useful caches and smoothing, but it does not yet have one obvious place where all future cloud renderer state should live. That boundary should be designed before the renderer grows.

