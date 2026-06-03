# Risky Cluster: Tornado Lifecycle

This cluster covers tornado lifecycle, instance state, probability, spawning, and spawn scheduling.

## Cluster Files

| File | Risk cluster | Why risky | Can be safely refactored now | Allowed safe refactor | Forbidden refactor | Expected benefit | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoManager.java` | Tornado lifecycle risk | Owns spawn, sync, tick, persistence, and client/server tornado lists. | no | Future helper extraction only if lifecycle order stays identical. | Changing spawn, tick, sync, snapshot, or persistence behavior. | Reduce orchestration density later. | Highest |
| `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoInstance.java` | Tornado lifecycle risk | Large per-tornado lifecycle/state machine with render snapshot and destruction logic. | no | Future method grouping or legacy-section cleanup only if no behavior changes. | Changing tornado physics, destruction, persistence, or snapshot behavior. | Clearer separation between lifecycle phases later. | Highest |
| `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoLevel.java` | Tornado lifecycle risk | Small but coupled to tornado severity classification. | yes | Leave as-is. | Changing level thresholds or damage values. | None required in this pass. | Low |
| `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoProbabilityManager.java` | Tornado lifecycle risk | Computes tornado risk and coordinates scheduled tornado spawning. | no | Future split of risk computation vs spawn trigger only if behavior remains identical. | Changing risk math, spawn chance, or scheduled cadence. | Cleaner probability boundary later. | High |
| `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoSpawner.java` | Tornado lifecycle risk | Owns spawn positioning and spawn application helpers. | no | Future helper extraction only if spawn selection remains identical. | Changing spawn position selection or tornado spawn output. | Better separation of spawn decision and application later. | High |
| `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoSpawnScheduler.java` | Tornado lifecycle risk | Manages spawn slots and cooldowns. | yes | Moved to clearer `modules/tornado/scheduling` package. | Changing slot timing or cooldown behavior. | Clearer scheduler ownership. | Medium |

## Result Summary

- `TornadoSpawnScheduler` was moved into `modules/tornado/scheduling` to separate scheduling from the rest of the tornado lifecycle package.
- The broader tornado lifecycle classes remain manual review candidates for a later pass because they are behavior-sensitive and tightly coupled to spawn/sync/persistence logic.
