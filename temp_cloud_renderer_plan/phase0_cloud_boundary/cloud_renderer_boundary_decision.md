# Cloud Renderer Boundary Decision

## What The Future Cloud Renderer Should Read

- A dedicated future cloud render snapshot.
- A client-side render cache that already holds a frozen or interpolated view of that snapshot.
- Backend-owned climate inputs that have already been transformed into render-safe values.
- Client-only visual helpers such as camera-relative LOD, downsample state, and debug toggles.

## What It Should Never Read Directly

- Live simulation internals from `TornadoInstance`, `HurricaneInstance`, `CloudManager`, or other broad simulation classes.
- Forecast generation internals from `ForecastGenerator`.
- Orchestration and persistence details from `AtmosphereManager` or `ForecastOrchestrator`.
- Raw compatibility policy from `SimpleCloudsCompat` or `SimpleCloudSpawner`.
- Packet state as the primary render source, because packets are transport, not render authority.

## Which Existing Classes Are Safe Sources

- `RegionAtmosphereState`
- `WeatherSnapshot`
- `WindVector`
- `AtmosphereClientState`
- `AtmosphereFogState`
- `ClientHurricaneStateCache`
- `TornadoSnapshot`
- `HurricaneRenderSnapshot`
- `SyncAtmosphereStatusPacket`
- `SyncTornadoesPacket`
- `SyncHurricaneStatePacket`

## Which Existing Classes Are Risky Sources

- `AtmosphereManager`
- `ForecastOrchestrator`
- `ForecastGenerator`
- `CloudManager`
- `SimpleCloudSpawner`
- `SimpleCloudsCompat`
- `ClientTickHandler`
- `SimpleCloudsTornadoRenderer`
- `SimpleCloudsHurricaneRenderer`
- `TornadoInstance`
- `HurricaneInstance`

## Where The Boundary Should Be

The boundary should sit between **simulation ownership** and **render ownership**.

- On the simulation side: climate, forecast, storm lifecycle, wind, and compatibility decisions.
- On the renderer side: frozen snapshot interpretation, interpolation, visual density, pass setup, shading inputs, and fallback darkening.

The cleanest boundary for phase 0 is a future `CloudRenderSnapshot` plus a future `CloudRenderStateCache`. The renderer should consume those objects, not reach back into the live simulation graph.

