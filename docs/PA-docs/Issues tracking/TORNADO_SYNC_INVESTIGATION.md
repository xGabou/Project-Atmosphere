# Tornado Sync Investigation

This log tracks tornado server/client position desync attempts. Check this file before trying another sync or render-position fix.

## Problem
- After roughly 20 to 30 seconds, the visual tornado can stay locked at an older position while server-side terrain destruction continues elsewhere.
- Expected behavior: the rendered tornado position should match the server-side tornado position used for block destruction.

## Initial Code Findings
- Server tornado snapshots are broadcast from `TornadoManager.tick(...)` every 5 server ticks while tornadoes are active.
- `SyncTornadoesPacket` applies received snapshots with `TornadoManager.applyClientSnapshots(...)`.
- `TornadoInstance.applySnapshot(...)` updates the authoritative client `position`, `clientTargetPosition`, shape, phase, and wind data.
- `SimpleCloudsTornadoRenderer.PreparedTornado.from(...)` reads `tornado.getRenderPosition(partialTick)`, so the renderer uses the interpolated client render position, not the spawn position directly.
- Client interpolation advances only in `TornadoManager.tick(mc.level)`, which calls `TornadoInstance.tickClient()`.
- `ClientTickHandler.onClientTick(...)` currently returns on `!ClientSyncLock.isReady()` before `TornadoManager.tick(mc.level)`.

## Current Suspect
- If forecast/cache readiness is temporarily false after a tornado has spawned, incoming packets can still update `clientTargetPosition`, but `tickClient()` stops advancing `clientRenderPosition`.
- That matches the observed symptom: server logic and destruction continue moving, while the visual render position appears frozen at the last advanced client render position.

## Next Fix
- Keep tornado client ticking independent from forecast-ready state.
- Move the null-level and pause checks before tornado ticking, then tick `TornadoManager` before the `ClientSyncLock.isReady()` return.
- Add low-rate tornado snapshot diagnostics behind `debugTornadoLogging` so future tests can confirm whether snapshots keep arriving and what position the client applies.
