# Risky Cluster: Atmosphere Runtime

This cluster covers runtime atmosphere state, registry, sync, update scheduling, cyclone state, and the server lifecycle coordinator that ties them together.

## Cluster Files

| File | Risk cluster | Why risky | Can be safely refactored now | Allowed safe refactor | Forbidden refactor | Expected benefit | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphereStatusSyncManager.java` | Atmosphere runtime risk | Sync packet orchestration depends on live atmosphere state and forecast snapshot access. | yes | Leave as-is or add small helper extraction only if packet flow stays identical. | Changing sync behavior or packet contents. | None required in this pass. | Medium |
| `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericStateRegistry.java` | Atmosphere runtime risk | Central registry for region states, legacy lookup, and active-state tracking. | no | Future split into state storage and lookup helpers if needed. | Changing region resolution or active-state semantics. | Cleaner boundary for runtime state access. | High |
| `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericUpdateScheduler.java` | Atmosphere runtime risk | Schedules active/passive updates and applies telemetry-heavy deltas. | no | Future split into snapshot, delta, and application helpers only if behavior stays identical. | Changing update cadence or delta math. | Clarer runtime boundary for future snapshots. | High |
| `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java` | Atmosphere runtime risk | Owns cyclone lifecycle, snapshots, and direct atmosphere mutation. | no | Future helper extraction only if drift/update behavior remains identical. | Changing cyclone motion, lifecycle, or snapshot output. | Tighter cyclone-state boundary. | High |
| `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/RegionAtmosphereState.java` | Atmosphere runtime risk | Holds live atmospheric values, forecast-derived baselines, and legacy compatibility accessors. | no | Future split of legacy compatibility helpers only if public behavior stays identical. | Changing target interpolation, clamp behavior, or save/snapshot semantics. | Clearer separation of runtime state vs legacy access. | High |
| `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java` | Atmosphere runtime risk | Top-level lifecycle coordinator still owns player sync, tick ordering, season transitions, and cloud-region bookkeeping. | yes | Extracted cloud-region tracking into a collaborator; further split only if lifecycle flow stays obvious. | Changing tick order, sync order, or regeneration behavior. | Reduce orchestration density and isolate cloud-region bookkeeping. | Highest |

## Result Summary

- `AtmosphereManager` now delegates cloud-region tracking to `AtmosphereCloudRegionTracker`.
- `AtmosphereStatusSyncManager`, `AtmosphericStateRegistry`, `AtmosphericUpdateScheduler`, `CycloneManager`, and `RegionAtmosphereState` remain manually risky and were not forcibly reshaped.
