# Backend To Snapshot Flow

This flow is the future path for real PA data, but it should not be wired in yet.

## Intended Flow

1. `RegionAtmosphereState` holds live region climate values.
2. `WeatherSnapshot` provides a stable weather-facing view of those values.
3. `WindVector` supplies backend wind direction and strength.
4. `AtmosphereClientState` and `AtmosphereFogState` hold client-smoothed visual state.
5. `ClientTickHandler` would eventually read the synced client cache and prepare render-visible state.
6. A future snapshot builder would combine those values into a cloud render snapshot.
7. A future render cache would hand that snapshot to the renderer.

## What Should Happen Before Rendering

- Backend values should be transformed into render-safe fields.
- The renderer should receive a compact, immutable object.
- Any smoothing or interpolation should already be complete or explicitly owned by the client cache.
- The renderer should not call back into forecast, region, or storm simulation during draw.

## What Should Not Happen

- The renderer should not compute backend weather rules itself.
- The renderer should not discover region climate by walking simulation structures.
- The renderer should not be responsible for making the snapshot authoritative.

## Phase 0 Interpretation

For phase 0, the real flow is only a design note. The implementation should still use a hardcoded or debug-only snapshot, but the final ownership path should already be documented so the fake cloud can be replaced later without changing the renderer contract.

