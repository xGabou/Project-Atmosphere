# Risky Batch 20+ Resolution

Step goal
- Process a 20-file high-value risky batch and resolve the clearly separable lifecycle-manager seams first.

Files changed
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneImpactApplier.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneEnvironmentAnalyzer.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoClientSnapshotLogger.java`

Files moved
- None

Classes renamed
- None

Classes split
- `CycloneManager` -> `CycloneImpactApplier`
- `HurricaneManager` -> `HurricaneEnvironmentAnalyzer`
- `TornadoManager` -> `TornadoClientSnapshotLogger`

New helper classes created
- `CycloneImpactApplier`
- `HurricaneEnvironmentAnalyzer`
- `TornadoClientSnapshotLogger`

Classes deleted because empty
- None

Facades kept
- None

Methods reordered
- None

Legacy/debug code moved
- Tornado client snapshot logging moved out of `TornadoManager`

Call sites updated
- `CycloneManager`
- `HurricaneManager`
- `TornadoManager`

Imports updated
- Yes, for the three manager files and the helper collaborators

Build checkpoints run
- 2

Build result
- `.\gradlew.bat build` succeeded

Behavior risk review
- Tick order preserved
- Spawn rates unchanged
- Destruction behavior unchanged
- Sync behavior unchanged
- Persistence unchanged
- Packet formats unchanged
- Snapshot formats unchanged

Rollback notes
- No rollback was needed. The only failure was a missing reference update, which was fixed directly before the successful build.

Remaining manual-only files
- Broad atmosphere registry/scheduler and compat coordination
- Tornado/hurricane manager and instance classes
- Render helpers and mixin surfaces

Why remaining files were not refactored
- They are either already structurally acceptable or still too behavior-sensitive for automatic restructuring in this batch.

Next recommended step
- Continue with the remaining high-value risky files in a 20-file batch, prioritizing the atmosphere registry/scheduler and then the tornado/hurricane instance surfaces.
