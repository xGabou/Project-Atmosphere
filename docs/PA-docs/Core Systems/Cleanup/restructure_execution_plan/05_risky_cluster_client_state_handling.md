# Client State Or Tick Lifecycle Risk

## Files in this cluster

| File | Risk cluster | Why risky | Can be safely refactored now | Allowed safe refactor | Forbidden refactor | Expected benefit | Priority |
|---|---|---|---|---|---|---|---|
| `src/main/java/net/Gabou/projectatmosphere/client/ClientTickHandler.java` | client state or tick lifecycle risk | Broad tick coordinator; order-sensitive client flow touches many subsystems. | no | Extract very small helpers only if tick order remains unchanged. | Do not change tick order, client sync sequence, or particle/audio timing. | Cleaner tick flow and easier ownership boundaries. | 1 |
| `src/main/java/net/Gabou/projectatmosphere/client/atmosphere/AtmosphereClientState.java` | client state or tick lifecycle risk | Client-synced atmosphere interpolation state; smoothing logic is timing-sensitive. | yes | Safe helper extraction and internal method grouping. | Do not change smoothing factors, interpolation behavior, or fallback rules. | Clearer lifecycle and separation of target updates from visual smoothing. | 2 |
| `src/main/java/net/Gabou/projectatmosphere/client/fog/AtmosphereFogState.java` | client state or tick lifecycle risk | Fog interpolation and debug override state; affects visual fog behavior. | yes | Safe helper extraction and internal method grouping. | Do not change fog behavior, tracking factors, or debug override semantics. | Easier to audit client fog lifecycle. | 2 |
| `src/main/java/net/Gabou/projectatmosphere/client/hurricane/ClientHurricaneStateCache.java` | client state or tick lifecycle risk | Interpolates hurricane snapshots and caches semantic data; stateful and timing-sensitive. | no | None beyond comments; keep structure conservative. | Do not change snapshot interpolation, cache invalidation, or reservation handling. | Better readability only if a future split is planned. | 3 |
| `src/main/java/net/Gabou/projectatmosphere/client/crash/ProjectAtmosphereCrashHandler.java` | client lifecycle risk | Crash capture and recovery flow is reflective and failure-sensitive. | no | None beyond comments or safe method grouping. | Do not change crash capture, report enrichment, or screen transition behavior. | Readability only; behavior is too sensitive for automatic refactor. | 4 |

## Refactor decision
- Lowest-risk actionable files were `AtmosphereClientState` and `AtmosphereFogState`.
- `ClientTickHandler` remains risky and broad because it coordinates multiple client systems in a fixed execution order.
- `ClientHurricaneStateCache` and `ProjectAtmosphereCrashHandler` remain manual review only.
